package org.docear.plugin.mcp.audit;

/**
 * Client / trace labels for MCP audit when the caller did not send {@code _audit}.
 */
public final class McpAuditLabels {
	static final String UNGROUPED_PREFIX = "ungrouped:";
	private static final long TRACE_BUCKET_MS = 30L * 60L * 1000L;

	private McpAuditLabels() {
	}

	public static String inferClientName(final String userAgent) {
		if (userAgent == null) {
			return "";
		}
		final String ua = userAgent.trim().toLowerCase();
		if (ua.length() == 0) {
			return "";
		}
		if (ua.indexOf("grok") >= 0 || ua.indexOf("x.ai") >= 0 || ua.indexOf("xai") >= 0) {
			return "Grok";
		}
		if (ua.indexOf("cursor") >= 0) {
			return "Cursor";
		}
		if (ua.indexOf("claude") >= 0 || ua.indexOf("anthropic") >= 0) {
			return "Claude";
		}
		if (ua.indexOf("chatgpt") >= 0 || ua.indexOf("openai") >= 0) {
			return "ChatGPT";
		}
		final int slash = userAgent.indexOf('/');
		if (slash > 0) {
			return userAgent.substring(0, slash).trim();
		}
		return userAgent.trim();
	}

	public static String fallbackQuestionSummary(final String clientName, final String actor) {
		final String who = notEmpty(clientName) ? clientName : (notEmpty(actor) ? actor : "MCP 客户端");
		return who + " 会话（未提供问题摘要）";
	}

	public static String syntheticTraceId(final String principalId, final String actor, final long ts) {
		final String who = sanitize(notEmpty(principalId) ? principalId : actor);
		final long bucket = ts > 0L ? ts / TRACE_BUCKET_MS : 0L;
		return "auto:" + who + ":" + bucket;
	}

	public static String ungroupedTraceId(final String actor) {
		return UNGROUPED_PREFIX + (notEmpty(actor) ? actor : "unknown");
	}

	public static boolean isUngroupedTraceId(final String traceId) {
		return traceId != null && traceId.startsWith(UNGROUPED_PREFIX);
	}

	public static String actorFromUngroupedTraceId(final String traceId) {
		if (!isUngroupedTraceId(traceId)) {
			return "";
		}
		return traceId.substring(UNGROUPED_PREFIX.length()).trim();
	}

	private static boolean notEmpty(final String value) {
		return value != null && value.trim().length() > 0;
	}

	private static String sanitize(final String value) {
		final String raw = value == null ? "" : value.trim();
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < raw.length() && sb.length() < 64; i++) {
			final char ch = raw.charAt(i);
			if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '-'
					|| ch == '_' || ch == '.' || ch == ':') {
				sb.append(ch);
			}
			else {
				sb.append('-');
			}
		}
		return sb.length() == 0 ? "unknown" : sb.toString();
	}
}
