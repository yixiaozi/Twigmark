package org.docear.plugin.mcp.webchat;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.freeplane.core.util.LogUtils;

import com.sun.net.httpserver.HttpExchange;

/**
 * Rate limits, outbound URL validation, and safe error helpers for the web API.
 */
public final class WebSecurity {
	private static final long WINDOW_MS = 60000L;
	private static final Map BUCKETS = new LinkedHashMap();
	private static final Object BUCKET_LOCK = new Object();

	private WebSecurity() {
	}

	public static String clientIp(final HttpExchange exchange) {
		if (exchange == null) {
			return "unknown";
		}
		final String forwarded = firstHeader(exchange, "X-Forwarded-For");
		if (forwarded != null && forwarded.length() > 0) {
			final int comma = forwarded.indexOf(',');
			return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
		}
		final String realIp = firstHeader(exchange, "X-Real-IP");
		if (realIp != null && realIp.length() > 0) {
			return realIp.trim();
		}
		if (exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
			return exchange.getRemoteAddress().getAddress().getHostAddress();
		}
		return "unknown";
	}

	/**
	 * Simple per-IP sliding window limiter.
	 *
	 * @return true when the request is allowed
	 */
	public static boolean allowRequest(final String bucket, final String ip, final int maxPerMinute) {
		if (maxPerMinute < 1) {
			return true;
		}
		final String key = bucket + "|" + (ip == null ? "unknown" : ip);
		final long now = System.currentTimeMillis();
		synchronized (BUCKET_LOCK) {
			pruneOldBuckets(now);
			final List hits = (List) BUCKETS.get(key);
			if (hits == null) {
				final List fresh = new ArrayList();
				fresh.add(Long.valueOf(now));
				BUCKETS.put(key, fresh);
				return true;
			}
			int count = 0;
			for (int i = 0; i < hits.size(); i++) {
				if (now - ((Long) hits.get(i)).longValue() <= WINDOW_MS) {
					count++;
				}
			}
			if (count >= maxPerMinute) {
				return false;
			}
			hits.add(Long.valueOf(now));
			return true;
		}
	}

	public static void requireRateLimit(final String bucket, final HttpExchange exchange, final int maxPerMinute) {
		if (!allowRequest(bucket, clientIp(exchange), maxPerMinute)) {
			throw new IllegalArgumentException("too many requests, try again later");
		}
	}

	/** Reject private/link-local/metadata URLs for user-supplied LLM endpoints. */
	public static void validateLlmBaseUrl(final String baseUrlRaw) {
		if (baseUrlRaw == null || baseUrlRaw.trim().length() == 0) {
			throw new IllegalArgumentException("baseUrl is required");
		}
		String baseUrl = baseUrlRaw.trim();
		while (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		final URI uri;
		try {
			uri = new URI(baseUrl);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("invalid baseUrl");
		}
		final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
		if (!"https".equals(scheme) && !"http".equals(scheme)) {
			throw new IllegalArgumentException("baseUrl must use http or https");
		}
		final String host = uri.getHost();
		if (host == null || host.length() == 0) {
			throw new IllegalArgumentException("baseUrl host is required");
		}
		if (isBlockedHost(host)) {
			throw new IllegalArgumentException("baseUrl host is not allowed");
		}
		if (!isAllowedLlmHost(host)) {
			throw new IllegalArgumentException("baseUrl host is not in the allowlist");
		}
	}

	private static boolean isAllowedLlmHost(final String host) {
		final String h = host.toLowerCase();
		final String[] allowed = DocearMcpConfig.getWebLlmAllowedHosts();
		for (int i = 0; i < allowed.length; i++) {
			final String entry = allowed[i];
			if (entry.length() == 0) {
				continue;
			}
			if (h.equals(entry) || h.endsWith("." + entry)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isBlockedHost(final String host) {
		final String h = host.toLowerCase();
		if ("localhost".equals(h) || h.endsWith(".local")) {
			return true;
		}
		try {
			final InetAddress[] addresses = InetAddress.getAllByName(host);
			for (int i = 0; i < addresses.length; i++) {
				if (isBlockedAddress(addresses[i])) {
					return true;
				}
			}
		}
		catch (Exception e) {
			return true;
		}
		return false;
	}

	private static boolean isBlockedAddress(final InetAddress address) {
		if (address == null) {
			return true;
		}
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress()) {
			return true;
		}
		final byte[] bytes = address.getAddress();
		if (bytes == null) {
			return false;
		}
		if (bytes.length == 4) {
			final int b0 = bytes[0] & 0xff;
			final int b1 = bytes[1] & 0xff;
			if (b0 == 0) {
				return true;
			}
			if (b0 == 10) {
				return true;
			}
			if (b0 == 127) {
				return true;
			}
			if (b0 == 169 && b1 == 254) {
				return true;
			}
			if (b0 == 172 && b1 >= 16 && b1 <= 31) {
				return true;
			}
			if (b0 == 192 && b1 == 168) {
				return true;
			}
		}
		return false;
	}

	public static void validateRegistrationAllowed(final String registrationToken) {
		if (!DocearMcpConfig.isPublicBind()) {
			return;
		}
		final String expected = DocearMcpConfig.getWebRegistrationToken();
		if (expected.length() == 0) {
			throw new IllegalArgumentException("registration disabled on public deployments");
		}
		if (registrationToken == null || !expected.equals(registrationToken.trim())) {
			throw new IllegalArgumentException("invalid registration token");
		}
	}

	public static int minPasswordLength() {
		return DocearMcpConfig.isPublicBind() ? 12 : 4;
	}

	public static String safePublicError() {
		return "internal server error";
	}

	public static void logAndSanitize(final String context, final Exception e) {
		LogUtils.warn(context + ": " + (e != null && e.getMessage() != null ? e.getMessage() : "error"), e);
	}

	private static void pruneOldBuckets(final long now) {
		if (BUCKETS.size() < 500) {
			return;
		}
		final List stale = new ArrayList();
		for (final Object entryObj : BUCKETS.entrySet()) {
			final Map.Entry entry = (Map.Entry) entryObj;
			final List hits = (List) entry.getValue();
			boolean anyRecent = false;
			for (int i = 0; i < hits.size(); i++) {
				if (now - ((Long) hits.get(i)).longValue() <= WINDOW_MS) {
					anyRecent = true;
					break;
				}
			}
			if (!anyRecent) {
				stale.add(entry.getKey());
			}
		}
		for (int i = 0; i < stale.size(); i++) {
			BUCKETS.remove(stale.get(i));
		}
	}

	private static String firstHeader(final HttpExchange exchange, final String name) {
		final List values = exchange.getRequestHeaders().get(name);
		if (values == null || values.isEmpty()) {
			return null;
		}
		final Object first = values.get(0);
		return first != null ? String.valueOf(first) : null;
	}
}
