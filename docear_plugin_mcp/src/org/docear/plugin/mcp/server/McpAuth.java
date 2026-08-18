package org.docear.plugin.mcp.server;

import java.security.SecureRandom;

import org.docear.plugin.mcp.DocearMcpConfig;

import com.sun.net.httpserver.HttpExchange;

/**
 * API-key gate for MCP HTTP and web API endpoints.
 */
public final class McpAuth {
	private static final SecureRandom RANDOM = new SecureRandom();

	private McpAuth() {
	}

	/**
	 * Auth is required when explicitly enabled, or when binding beyond loopback
	 * (public / LAN exposure).
	 */
	public static boolean isAuthRequired() {
		if (DocearMcpConfig.isAuthEnabled()) {
			return true;
		}
		return DocearMcpConfig.isPublicBind();
	}

	public static boolean hasConfiguredApiKey() {
		final String key = DocearMcpConfig.getApiKey();
		if (key != null && key.trim().length() > 0) {
			return true;
		}
		return McpAccessStore.get().hasAnyEnabledKey();
	}

	/** Validate start conditions: public bind / auth require a non-empty API key. */
	public static void validateServerStart() {
		if (isAuthRequired() && !hasConfiguredApiKey()) {
			if (DocearMcpConfig.isPublicBind()) {
				throw new IllegalStateException(
						"Public MCP bind (" + DocearMcpConfig.getHost()
								+ ") requires an API key (mcp.auth.apiKey or mcp-access.json).");
			}
			throw new IllegalStateException(
					"MCP authentication is enabled but no API key is set. Generate one in Product Settings → MCP.");
		}
	}

	public static boolean isAuthorized(final HttpExchange exchange) {
		return authenticate(exchange) != null;
	}

	/**
	 * Resolve the caller. Missing key is allowed only when auth is not required
	 * (loopback desktop). An unknown key is always rejected.
	 */
	public static McpPrincipal authenticate(final HttpExchange exchange) {
		final String provided = extractApiKey(exchange);
		if (provided.length() > 0) {
			if (provided.startsWith("mto_")) {
				return McpOAuthService.get().resolveAccessToken(provided);
			}
			return McpAccessStore.get().resolve(provided);
		}
		if (isAuthRequired()) {
			return null;
		}
		if (DocearMcpConfig.isReadOnly()) {
			return McpPrincipal.anonymousRead();
		}
		return McpPrincipal.localOwner();
	}

	public static String extractApiKey(final HttpExchange exchange) {
		if (exchange == null) {
			return "";
		}
		final String auth = header(exchange, "Authorization");
		if (auth != null && auth.length() > 0) {
			final String trimmed = auth.trim();
			if (trimmed.length() >= 7 && "bearer ".equalsIgnoreCase(trimmed.substring(0, 7))) {
				return trimmed.substring(7).trim();
			}
			return trimmed;
		}
		final String apiKey = header(exchange, "X-Api-Key");
		return apiKey != null ? apiKey.trim() : "";
	}

	private static String header(final HttpExchange exchange, final String name) {
		final java.util.List values = exchange.getRequestHeaders().get(name);
		if (values == null || values.isEmpty()) {
			return null;
		}
		final Object first = values.get(0);
		return first != null ? String.valueOf(first) : null;
	}

	/** Generate a URL-safe Twigmark MCP API key (tm_ + 48 hex chars). */
	public static String generateApiKey() {
		final byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		return "tm_" + toHex(bytes);
	}

	private static String toHex(final byte[] bytes) {
		final StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (int i = 0; i < bytes.length; i++) {
			final int v = bytes[i] & 0xff;
			if (v < 16) {
				sb.append('0');
			}
			sb.append(Integer.toHexString(v));
		}
		return sb.toString();
	}
}
