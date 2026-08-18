package org.docear.plugin.mcp.server;

import java.net.URI;
import java.util.Locale;

import org.docear.plugin.mcp.DocearMcpConfig;

/** HTTPS redirect allowlist for Grok / xAI OAuth callbacks. */
public final class McpOAuthRedirects {
	private McpOAuthRedirects() {
	}

	public static boolean allowed(final String redirectUri) {
		if (redirectUri == null || redirectUri.trim().length() == 0) {
			return false;
		}
		final URI uri;
		try {
			uri = URI.create(redirectUri.trim());
		}
		catch (Exception e) {
			return false;
		}
		if (uri.getScheme() == null || uri.getHost() == null) {
			return false;
		}
		final String scheme = uri.getScheme().toLowerCase(Locale.US);
		final String host = uri.getHost().toLowerCase(Locale.US);
		if (isLoopback(host)) {
			return "http".equals(scheme) || "https".equals(scheme);
		}
		if (!"https".equals(scheme)) {
			return false;
		}
		if (hostEqualsOrSuffix(host, "grok.com") || hostEqualsOrSuffix(host, "x.ai")
				|| hostEqualsOrSuffix(host, "x.com")) {
			return true;
		}
		final String[] extra = DocearMcpConfig.getOauthRedirectHosts();
		for (int i = 0; i < extra.length; i++) {
			if (hostEqualsOrSuffix(host, extra[i])) {
				return true;
			}
		}
		return false;
	}

	public static String hostOf(final String redirectUri) {
		try {
			final URI uri = URI.create(redirectUri);
			return uri.getHost() == null ? "" : uri.getHost();
		}
		catch (Exception e) {
			return "";
		}
	}

	private static boolean isLoopback(final String host) {
		return "127.0.0.1".equals(host) || "localhost".equals(host) || "::1".equals(host)
				|| "[::1]".equals(host);
	}

	private static boolean hostEqualsOrSuffix(final String host, final String root) {
		if (root == null || root.length() == 0) {
			return false;
		}
		final String r = root.toLowerCase(Locale.US).trim();
		if (r.length() == 0) {
			return false;
		}
		return host.equals(r) || host.endsWith("." + r);
	}
}
