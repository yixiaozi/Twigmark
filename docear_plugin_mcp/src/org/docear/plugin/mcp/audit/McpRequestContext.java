package org.docear.plugin.mcp.audit;

import com.sun.net.httpserver.HttpExchange;

public final class McpRequestContext {

	private static final ThreadLocal<McpRequestContext> CURRENT = new ThreadLocal<McpRequestContext>();

	private final String sessionId;
	private final String remoteAddress;
	private final String headerCaller;
	private final String headerQuestionSummary;

	private McpRequestContext(final String sessionId, final String remoteAddress, final String headerCaller,
	    final String headerQuestionSummary) {
		this.sessionId = sessionId;
		this.remoteAddress = remoteAddress;
		this.headerCaller = headerCaller;
		this.headerQuestionSummary = headerQuestionSummary;
	}

	public static void begin(final HttpExchange exchange) {
		final String sessionId = firstHeader(exchange, "Mcp-Session-Id");
		String remoteAddress = "unknown";
		if (exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
			remoteAddress = exchange.getRemoteAddress().getAddress().getHostAddress();
		}
		CURRENT.set(new McpRequestContext(sessionId, remoteAddress, firstHeader(exchange, "X-Docear-Audit-Caller"),
		    firstHeader(exchange, "X-Docear-Audit-Question")));
	}

	public static void end() {
		CURRENT.remove();
	}

	public static McpRequestContext current() {
		return CURRENT.get();
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

	private static String firstHeader(final HttpExchange exchange, final String name) {
		if (exchange == null || exchange.getRequestHeaders() == null) {
			return "";
		}
		final String value = exchange.getRequestHeaders().getFirst(name);
		return value != null ? value.trim() : "";
	}
}
