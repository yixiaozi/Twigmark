package org.docear.plugin.mcp.audit;

import org.docear.plugin.mcp.server.McpPrincipal;
import org.docear.plugin.mcp.server.McpRole;

import com.sun.net.httpserver.HttpExchange;

public final class McpRequestContext {

	private static final ThreadLocal<McpRequestContext> CURRENT = new ThreadLocal<McpRequestContext>();

	private final String sessionId;
	private final String remoteAddress;
	private final String headerCaller;
	private final String headerQuestionSummary;
	private final String userAgent;
	private final McpPrincipal principal;

	private McpRequestContext(final String sessionId, final String remoteAddress, final String headerCaller,
	    final String headerQuestionSummary, final String userAgent, final McpPrincipal principal) {
		this.sessionId = sessionId;
		this.remoteAddress = remoteAddress;
		this.headerCaller = headerCaller;
		this.headerQuestionSummary = headerQuestionSummary;
		this.userAgent = userAgent;
		this.principal = principal;
	}

	public static void begin(final HttpExchange exchange) {
		begin(exchange, null);
	}

	public static void begin(final HttpExchange exchange, final McpPrincipal principal) {
		final String sessionId = firstHeader(exchange, "Mcp-Session-Id");
		String remoteAddress = firstForwardedAddress(exchange);
		if (remoteAddress.length() == 0 && exchange != null && exchange.getRemoteAddress() != null
				&& exchange.getRemoteAddress().getAddress() != null) {
			remoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
		}
		if (remoteAddress.length() == 0) {
			remoteAddress = "unknown";
		}
		CURRENT.set(new McpRequestContext(sessionId, remoteAddress, firstHeader(exchange, "X-Docear-Audit-Caller"),
		    firstHeader(exchange, "X-Docear-Audit-Question"), firstHeader(exchange, "User-Agent"), principal));
	}

	public static void beginWeb(final String username, final McpRole role) {
		CURRENT.set(new McpRequestContext("", "web", username == null ? "" : username, "", "twigmark-web",
		    McpPrincipal.web(username, role)));
	}

	public static void end() {
		CURRENT.remove();
	}

	public static McpRequestContext current() {
		return CURRENT.get();
	}

	public static McpPrincipal currentPrincipal() {
		final McpRequestContext ctx = CURRENT.get();
		return ctx == null ? null : ctx.principal;
	}

	public String getSessionId() {
		return sessionId;
	}

	public String getRemoteAddress() {
		return remoteAddress;
	}

	public String getHeaderCaller() {
		return headerCaller;
	}

	public String getHeaderQuestionSummary() {
		return headerQuestionSummary;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public McpPrincipal getPrincipal() {
		return principal;
	}

	private static String firstHeader(final HttpExchange exchange, final String name) {
		if (exchange == null || exchange.getRequestHeaders() == null) {
			return "";
		}
		final String value = exchange.getRequestHeaders().getFirst(name);
		return value != null ? value.trim() : "";
	}

	private static String firstForwardedAddress(final HttpExchange exchange) {
		final String realIp = firstHeader(exchange, "X-Real-IP");
		if (realIp.length() > 0 && !"127.0.0.1".equals(realIp) && !"::1".equals(realIp)) {
			return realIp;
		}
		final String forwarded = firstHeader(exchange, "X-Forwarded-For");
		if (forwarded.length() == 0) {
			return realIp;
		}
		final int comma = forwarded.indexOf(',');
		final String hop = (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
		return hop;
	}
}
