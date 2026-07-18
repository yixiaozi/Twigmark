package org.docear.plugin.mcp.audit;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.freeplane.core.util.LogUtils;

public final class McpAuditService {

	public static final class AuditMetadata {
		public final String caller;
		public final String questionSummary;
		public final String operationGoal;
		public final String traceId;
		public final String tenant;

		AuditMetadata(final String caller, final String questionSummary, final String operationGoal,
		    final String traceId, final String tenant) {
			this.caller = caller != null ? caller.trim() : "";
			this.questionSummary = questionSummary != null ? questionSummary.trim() : "";
			this.operationGoal = operationGoal != null ? operationGoal.trim() : "";
			this.traceId = traceId != null ? traceId.trim() : "";
			this.tenant = tenant != null && tenant.trim().length() > 0 ? tenant.trim() : "default";
		}

		static AuditMetadata empty() {
			return new AuditMetadata("", "", "", "", "default");
		}
	}

	private static final int MAX_SUMMARY_LENGTH = 240;
	private static final int MAX_GOAL_LENGTH = 160;

	private static final Map<String, String> SESSION_CLIENTS = new ConcurrentHashMap<String, String>();
	private static volatile McpAuditWriter WRITER;
	private static volatile boolean STARTED;

	private McpAuditService() {
	}

	public static synchronized void start() {
		if (!DocearMcpConfig.isAuditEnabled() || STARTED) {
			return;
		}
		final McpAuditDatabase database = McpAuditDatabase.getInstance();
		final File overflowFile = new File(DocearMcpConfig.getAuditDataDir(), "audit_overflow.jsonl");
		WRITER = new McpAuditWriter(database, overflowFile);
		WRITER.start();
		STARTED = true;
		LogUtils.info("Docear MCP audit writer started. db=" + database.getDbFile().getAbsolutePath());
	}

	public static synchronized void shutdown() {
		if (WRITER != null) {
			WRITER.shutdown();
			WRITER = null;
		}
		STARTED = false;
	}

	public static void registerClient(final String sessionId, final String clientName) {
		if (sessionId == null || sessionId.length() == 0 || clientName == null || clientName.length() == 0) {
			return;
		}
		SESSION_CLIENTS.put(sessionId, clientName);
	}

	public static AuditMetadata extractAuditMetadata(final Map<String, JsonValue> args) {
		if (args == null || !args.containsKey("_audit")) {
			return AuditMetadata.empty();
		}
		final Map<String, JsonValue> audit = args.get("_audit").asMap();
		return new AuditMetadata(argString(audit, "caller", ""), argString(audit, "questionSummary", ""),
		    argString(audit, "operationGoal", ""), argString(audit, "traceId", ""),
		    argString(audit, "tenant", "default"));
	}

	public static Map<String, JsonValue> stripAuditMetadata(final Map<String, JsonValue> args) {
		if (args == null || !args.containsKey("_audit")) {
			return args;
		}
		final Map<String, JsonValue> copy = new LinkedHashMap<String, JsonValue>(args);
		copy.remove("_audit");
		return copy;
	}

	public static String mapToJson(final Map<String, JsonValue> map) {
		if (map == null || map.isEmpty()) {
			return "{}";
		}
		return JsonWriter.write(JsonValue.ofMap(map));
	}

	public static McpOperationIntent intentForTool(final String toolName) {
		if ("get_workspace_plan".equals(toolName) || "get_selection_context".equals(toolName)
		    || "list_audit_log".equals(toolName) || "get_audit_stats".equals(toolName)
		    || "list_audit_traces".equals(toolName)) {
			return McpOperationIntent.CONTEXT;
		}
		if ("list_todos".equals(toolName) || "list_reminders".equals(toolName) || "list_overdue".equals(toolName)) {
			return McpOperationIntent.TASK;
		}
		if ("get_node_details".equals(toolName) || "list_pinned".equals(toolName) || "list_published".equals(toolName)
		    || "move_node".equals(toolName) || "set_node_folded".equals(toolName) || "set_node_link".equals(toolName)
		    || "set_node_note".equals(toolName) || "set_node_tags".equals(toolName) || "toggle_pin".equals(toolName)
		    || "set_node_icon".equals(toolName) || "set_recurring_reminder".equals(toolName)
		    || "create_mindmap".equals(toolName)) {
			return McpOperationIntent.NODE;
		}
		if ("list_projects".equals(toolName)) {
			return McpOperationIntent.WORKSPACE;
		}
		if ("get_relationship_graph".equals(toolName) || "get_node_relationships".equals(toolName)) {
			return McpOperationIntent.GRAPH;
		}
		if ("list_tag_groups".equals(toolName) || "list_tags".equals(toolName) || "list_nodes_by_tag".equals(toolName)
		    || "list_favorites".equals(toolName) || "get_tag_catalog".equals(toolName)
		    || "create_tag_group".equals(toolName) || "rename_tag_group".equals(toolName)
		    || "move_tag_group".equals(toolName) || "delete_tag_group".equals(toolName)
		    || "set_tag_group".equals(toolName)
		    || "set_tag_color".equals(toolName)) {
			return McpOperationIntent.TAG;
		}
		if ("get_running_pomodoro".equals(toolName) || "list_pomodoro_sessions".equals(toolName)
		    || "get_pomodoro_stats".equals(toolName) || "get_pomodoro_history".equals(toolName)
		    || "start_pomodoro".equals(toolName) || "pause_pomodoro".equals(toolName)
		    || "stop_pomodoro".equals(toolName)) {
			return McpOperationIntent.POMODORO;
		}
		if ("ensure_finance_map".equals(toolName) || "get_finance_summary".equals(toolName)
		    || "add_finance_transaction".equals(toolName) || "list_finance_transactions".equals(toolName)
		    || "list_finance_categories".equals(toolName) || "add_finance_category".equals(toolName)
		    || "list_finance_accounts".equals(toolName) || "add_finance_account".equals(toolName)
		    || "set_finance_budget".equals(toolName) || "list_finance_budgets".equals(toolName)
		    || "upsert_finance_subscription".equals(toolName) || "list_finance_subscriptions".equals(toolName)
		    || "upsert_finance_coupon".equals(toolName) || "list_finance_coupons".equals(toolName)
		    || "mark_finance_coupon_used".equals(toolName) || "delete_finance_node".equals(toolName)
		    || "get_finance_report".equals(toolName)) {
			return McpOperationIntent.FINANCE;
		}
		if ("get_active_map_json".equals(toolName) || "get_mindmap_json".equals(toolName) || "search_nodes".equals(toolName)
		    || "list_recently_modified".equals(toolName) || "open_mindmap".equals(toolName)
		    || "navigate_to_node".equals(toolName) || "add_node".equals(toolName) || "add_nodes".equals(toolName)
		    || "change_node_text".equals(toolName) || "remove_node".equals(toolName) || "create_todo".equals(toolName)
		    || "complete_todo".equals(toolName) || "set_reminder".equals(toolName) || "set_priority".equals(toolName)
		    || "quick_capture".equals(toolName) || "sync_todoist".equals(toolName)
		    || "export_workspace_snapshot".equals(toolName)) {
			return McpOperationIntent.MINDMAP;
		}
		return McpOperationIntent.UNKNOWN;
	}

	public static McpOperationIntent intentForResource(final String uri) {
		if (uri == null || uri.length() == 0) {
			return McpOperationIntent.UNKNOWN;
		}
		if ("docear://manifest".equals(uri) || "docear://workspace/plan".equals(uri)
		    || "docear://tasks/today".equals(uri) || "docear://context/selection".equals(uri)
		    || "docear://context/recent".equals(uri) || "docear://inbox".equals(uri)) {
			return McpOperationIntent.CONTEXT;
		}
		if ("docear://workspace/overview".equals(uri)) {
			return McpOperationIntent.WORKSPACE;
		}
		if ("docear://tasks/todos".equals(uri) || "docear://tasks/reminders".equals(uri)
		    || "docear://tasks/overdue".equals(uri)) {
			return McpOperationIntent.TASK;
		}
		if ("docear://context/active-map".equals(uri)) {
			return McpOperationIntent.MINDMAP;
		}
		if ("docear://graph/summary".equals(uri)) {
			return McpOperationIntent.GRAPH;
		}
		if ("docear://tags/catalog".equals(uri)) {
			return McpOperationIntent.TAG;
		}
		if ("docear://pomodoro/running".equals(uri) || "docear://pomodoro/stats".equals(uri)) {
			return McpOperationIntent.POMODORO;
		}
		if ("docear://finance/summary".equals(uri)) {
			return McpOperationIntent.FINANCE;
		}
		return McpOperationIntent.RESOURCE;
	}

	public static void recordToolCall(final String toolName, final Map<String, JsonValue> args, final AuditMetadata metadata,
	    final boolean success, final String errorMessage, final long durationMs, final String responseText) {
		record("tool", toolName, intentForTool(toolName), mapToJson(args), metadata, success, errorMessage, durationMs,
		    responseText);
	}

	public static void recordResourceRead(final String uri, final Map<String, JsonValue> requestParams,
	    final AuditMetadata metadata, final boolean success, final String errorMessage, final long durationMs,
	    final String responseText) {
		record("resource", uri, intentForResource(uri), mapToJson(requestParams), metadata, success, errorMessage,
		    durationMs, responseText);
	}

	public static void recordPromptGet(final String promptName, final Map<String, JsonValue> requestParams,
	    final AuditMetadata metadata, final boolean success, final String errorMessage, final long durationMs,
	    final String responseText) {
		record("prompt", promptName, McpOperationIntent.PROMPT, mapToJson(requestParams), metadata, success, errorMessage,
		    durationMs, responseText);
	}

	public static String listAuditLog(final int limit, final String intentFilter, final String traceId,
	    final String questionQuery, final String actionFilter, final long sinceMillis) {
		try {
			ensureDatabase();
			return McpAuditDatabase.getInstance().listEvents(limit, intentFilter, traceId, questionQuery, actionFilter,
			    sinceMillis);
		}
		catch (Exception e) {
			return errorJson("list_audit_log failed", e);
		}
	}

	public static String listAuditTraces(final int limit, final String questionQuery, final long sinceMillis) {
		try {
			ensureDatabase();
			return McpAuditDatabase.getInstance().listTraces(limit, questionQuery, sinceMillis);
		}
		catch (Exception e) {
			return errorJson("list_audit_traces failed", e);
		}
	}

	public static String getAuditStats(final String granularity, final int limit, final String intentFilter,
	    final String actionFilter, final long sinceMillis) {
		try {
			ensureDatabase();
			return McpAuditDatabase.getInstance().getStats(granularity, limit, intentFilter, actionFilter, sinceMillis);
		}
		catch (Exception e) {
			return errorJson("get_audit_stats failed", e);
		}
	}

	/** UI helper: recent audit events as plain maps (string/number/boolean values). */
	public static List<Map<String, Object>> listAuditEventsForUi(final int limit) {
		try {
			ensureDatabase();
			return McpAuditDatabase.getInstance().listEventRows(limit);
		}
		catch (Exception e) {
			LogUtils.warn("listAuditEventsForUi failed: " + e.getMessage(), e);
			return Collections.emptyList();
		}
	}

	/** UI helper: recent traces as plain maps. */
	public static List<Map<String, Object>> listAuditTracesForUi(final int limit) {
		try {
			ensureDatabase();
			return McpAuditDatabase.getInstance().listTraceRows(limit);
		}
		catch (Exception e) {
			LogUtils.warn("listAuditTracesForUi failed: " + e.getMessage(), e);
			return Collections.emptyList();
		}
	}

	public static int countAuditEvents() {
		try {
			ensureDatabase();
			return McpAuditDatabase.getInstance().countEvents();
		}
		catch (Exception e) {
			return 0;
		}
	}

	public static int pendingAuditCount() {
		final McpAuditWriter writer = WRITER;
		return writer != null ? writer.pendingCount() : 0;
	}

	public static boolean isAuditWriterStarted() {
		return STARTED;
	}

	static Map<String, JsonValue> eventToMap(final McpAuditEvent event) {
		final Map<String, JsonValue> map = new LinkedHashMap<String, JsonValue>();
		map.put("ts", JsonValue.ofNumber(Long.valueOf(event.ts)));
		map.put("tenant", JsonValue.ofString(event.tenant));
		map.put("actor", JsonValue.ofString(event.actor));
		map.put("action", JsonValue.ofString(event.action));
		map.put("kind", JsonValue.ofString(event.kind));
		map.put("intent", JsonValue.ofString(event.intent.name()));
		map.put("traceId", JsonValue.ofString(event.traceId));
		map.put("sessionId", JsonValue.ofString(event.sessionId));
		map.put("clientName", JsonValue.ofString(event.clientName));
		map.put("osUser", JsonValue.ofString(event.osUser));
		map.put("remoteAddress", JsonValue.ofString(event.remoteAddress));
		map.put("questionSummary", JsonValue.ofString(event.questionSummary));
		map.put("operationGoal", JsonValue.ofString(event.operationGoal));
		map.put("requestJson", JsonValue.ofString(event.requestJson));
		map.put("responseJson", JsonValue.ofString(event.responseJson));
		map.put("responseBytes", JsonValue.ofNumber(Integer.valueOf(event.responseBytes)));
		map.put("responseTruncated", JsonValue.ofBoolean(event.responseTruncated));
		map.put("success", JsonValue.ofBoolean(event.success));
		map.put("durationMs", JsonValue.ofNumber(Long.valueOf(event.durationMs)));
		map.put("errorMessage", JsonValue.ofString(event.errorMessage));
		return map;
	}

	static McpAuditEvent eventFromMap(final Map<String, JsonValue> map) {
		McpOperationIntent intent = McpOperationIntent.UNKNOWN;
		try {
			intent = McpOperationIntent.valueOf(argString(map, "intent", "UNKNOWN"));
		}
		catch (Exception e) {
			intent = McpOperationIntent.UNKNOWN;
		}
		return new McpAuditEvent(map.containsKey("ts") ? map.get("ts").asLong(0L) : System.currentTimeMillis(),
		    argString(map, "tenant", "default"), argString(map, "actor", ""), argString(map, "action", ""),
		    argString(map, "kind", ""), intent,
		    argString(map, "traceId", ""), argString(map, "sessionId", ""), argString(map, "clientName", ""),
		    argString(map, "osUser", ""), argString(map, "remoteAddress", ""), argString(map, "questionSummary", ""),
		    argString(map, "operationGoal", ""), argString(map, "requestJson", "{}"),
		    argString(map, "responseJson", ""), map.containsKey("responseBytes") ? map.get("responseBytes").asInt(0) : 0,
		    map.containsKey("responseTruncated") && map.get("responseTruncated").asBoolean(),
		    !map.containsKey("success") || map.get("success").asBoolean(),
		    map.containsKey("durationMs") ? map.get("durationMs").asLong(0L) : 0L, argString(map, "errorMessage", ""));
	}

	private static void record(final String kind, final String operation, final McpOperationIntent intent,
	    final String requestJson, final AuditMetadata metadata, final boolean success, final String errorMessage,
	    final long durationMs, final String responseText) {
		if (!DocearMcpConfig.isAuditEnabled()) {
			return;
		}
		start();
		final McpRequestContext ctx = McpRequestContext.current();
		final String sessionId = ctx != null ? nullToEmpty(ctx.getSessionId()) : "";
		final String clientName = sessionId.length() > 0 ? nullToEmpty(SESSION_CLIENTS.get(sessionId)) : "";
		final String actor = firstNonEmpty(metadata.caller, ctx != null ? ctx.getHeaderCaller() : "", clientName,
		    System.getProperty("user.name", "unknown"));
		final String questionSummary = truncate(firstNonEmpty(metadata.questionSummary,
		    ctx != null ? ctx.getHeaderQuestionSummary() : ""), MAX_SUMMARY_LENGTH);
		final String operationGoal = metadata.operationGoal.length() > 0
		    ? truncate(metadata.operationGoal, MAX_GOAL_LENGTH)
		    : defaultOperationGoal(kind, operation, intent);
		final String traceId = metadata.traceId;
		final ResponsePayload response = normalizeResponse(responseText);

		final McpAuditEvent event = new McpAuditEvent(System.currentTimeMillis(), metadata.tenant, actor, operation, kind,
		    intent, traceId, sessionId, clientName, System.getProperty("user.name", "unknown"),
		    ctx != null ? nullToEmpty(ctx.getRemoteAddress()) : "unknown", questionSummary, operationGoal, requestJson,
		    response.text, response.originalBytes, response.truncated, success, durationMs, truncate(errorMessage, 500));

		if (WRITER != null) {
			WRITER.enqueue(event);
		}
	}

	private static void ensureDatabase() {
		if (!STARTED) {
			start();
		}
		McpAuditDatabase.getInstance();
	}

	private static ResponsePayload normalizeResponse(final String responseText) {
		final String text = responseText != null ? responseText : "";
		final int originalBytes = text.length();
		final int maxBytes = DocearMcpConfig.getAuditMaxResponseBytes();
		if (originalBytes <= maxBytes) {
			return new ResponsePayload(text, originalBytes, false);
		}
		return new ResponsePayload(text.substring(0, maxBytes) + "\n...[truncated " + originalBytes + " chars]", originalBytes,
		    true);
	}

	private static String defaultOperationGoal(final String kind, final String operation, final McpOperationIntent intent) {
		return intent.getServiceClass() + " / " + kind + ":" + operation;
	}

	private static String errorJson(final String message, final Exception e) {
		final Map<String, JsonValue> map = new LinkedHashMap<String, JsonValue>();
		map.put("error", JsonValue.ofString(message + ": " + e.getMessage()));
		map.put("dbPath", JsonValue.ofString(DocearMcpConfig.getAuditDbFile().getAbsolutePath()));
		return JsonWriter.write(JsonValue.ofMap(map));
	}

	private static String truncate(final String text, final int maxLength) {
		if (text == null) {
			return "";
		}
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength - 3) + "...";
	}

	private static String firstNonEmpty(final String... values) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] != null && values[i].trim().length() > 0) {
				return values[i].trim();
			}
		}
		return "";
	}

	private static String nullToEmpty(final String value) {
		return value != null ? value : "";
	}

	private static String argString(final Map<String, JsonValue> args, final String key, final String defaultValue) {
		return args.containsKey(key) ? args.get(key).asString() : defaultValue;
	}

	private static final class ResponsePayload {
		final String text;
		final int originalBytes;
		final boolean truncated;

		ResponsePayload(final String text, final int originalBytes, final boolean truncated) {
			this.text = text;
			this.originalBytes = originalBytes;
			this.truncated = truncated;
		}
	}
}
