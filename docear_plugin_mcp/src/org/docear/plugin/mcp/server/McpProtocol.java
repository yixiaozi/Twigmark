package org.docear.plugin.mcp.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.audit.McpAuditService;
import org.docear.plugin.mcp.audit.McpRequestContext;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.docear.plugin.mcp.service.McpContextService;
import org.docear.plugin.mcp.service.McpMindMapService;
import org.docear.plugin.mcp.service.McpNodeService;
import org.docear.plugin.mcp.service.McpRelationshipGraphService;
import org.docear.plugin.mcp.service.McpTaskService;
import org.docear.plugin.mcp.service.McpWorkspaceService;

public final class McpProtocol {

	public String handle(final JsonValue request) throws Exception {
		final Map<String, JsonValue> map = request.asMap();
		final String method = map.containsKey("method") ? map.get("method").asString() : "";
		final JsonValue id = map.get("id");
		final JsonValue params = map.containsKey("params") ? map.get("params") : JsonValue.ofMap(new LinkedHashMap<String, JsonValue>());

		if ("initialize".equals(method)) {
			captureInitializeClient(params);
			return success(id, initializeResult());
		}
		if ("notifications/initialized".equals(method) || "initialized".equals(method)) {
			return success(id, JsonValue.ofMap(new LinkedHashMap<String, JsonValue>()));
		}
		if ("ping".equals(method)) {
			return success(id, JsonValue.ofMap(new LinkedHashMap<String, JsonValue>()));
		}
		if ("tools/list".equals(method)) {
			return success(id, JsonValue.ofMap(singleEntry("tools", JsonValue.ofList(listTools()))));
		}
		if ("tools/call".equals(method)) {
			return success(id, callTool(params));
		}
		if ("resources/list".equals(method)) {
			return success(id, JsonValue.ofMap(singleEntry("resources", JsonValue.ofList(listResources()))));
		}
		if ("resources/read".equals(method)) {
			return success(id, readResource(params));
		}
		if ("prompts/list".equals(method)) {
			return success(id, JsonValue.ofMap(singleEntry("prompts", JsonValue.ofList(listPrompts()))));
		}
		if ("prompts/get".equals(method)) {
			return success(id, getPrompt(params));
		}
		throw new IllegalArgumentException("Unsupported MCP method: " + method);
	}

	private JsonValue initializeResult() {
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("protocolVersion", JsonValue.ofString("2024-11-05"));
		final Map<String, JsonValue> capabilities = new LinkedHashMap<String, JsonValue>();
		capabilities.put("tools", JsonValue.ofMap(new LinkedHashMap<String, JsonValue>()));
		capabilities.put("resources", JsonValue.ofMap(singleEntry("subscribe", JsonValue.ofBoolean(false))));
		capabilities.put("prompts", JsonValue.ofMap(new LinkedHashMap<String, JsonValue>()));
		result.put("capabilities", JsonValue.ofMap(capabilities));
		final Map<String, JsonValue> serverInfo = new LinkedHashMap<String, JsonValue>();
		serverInfo.put("name", JsonValue.ofString("docear-mcp"));
		serverInfo.put("version", JsonValue.ofString("1.0.0"));
		result.put("serverInfo", JsonValue.ofMap(serverInfo));
		return JsonValue.ofMap(result);
	}

	private List<JsonValue> listTools() {
		final List<JsonValue> tools = new ArrayList<JsonValue>();
		tools.add(tool("list_todos", "List all todo items across the workspace."));
		tools.add(tool("list_reminders", "List reminders. Optional filters: oneTimeOnly, recurringOnly.",
				schema("oneTimeOnly", "boolean", false), schema("recurringOnly", "boolean", false)));
		tools.add(tool("list_overdue", "List overdue one-time reminders."));
		tools.add(tool("get_workspace_plan", "Get formatted workspace plan summary for AI."));
		tools.add(tool("get_active_map_json",
				"Get the currently open mind map as JSON. Set includeFolded=false to omit folded branches.",
				schema("includeFolded", "boolean", false)));
		tools.add(tool("get_mindmap_json",
				"Read a mind map file as JSON without opening it in Docear UI (silent). "
						+ "Includes note, link, icons, tags, MODIFIED, task attrs. "
						+ "Prefer partial path (dir/file.mm) when filenames are duplicated.",
				schema("filePath", "string", true), schema("maxDepth", "number", false),
				schema("includeFolded", "boolean", false)));
		tools.add(tool("get_node_details",
				"Get full details for one node: note, link, icons, tags, reminders, privacy, parent path.",
				schema("filePath", "string", true), schema("nodeId", "string", true)));
		tools.add(tool("list_pinned", "List pinned nodes from the workspace sidebar.",
				schema("limit", "number", false)));
		tools.add(tool("list_published", "List nodes marked with the published icon.",
				schema("limit", "number", false)));
		tools.add(tool("get_selection_context", "Get current selection context in Docear."));
		tools.add(tool("search_nodes",
				"Search nodes by keyword via silent SAX. Filters by node MODIFIED (not global Top-N). "
						+ "Use modifiedWithinDays, filePath, or projectId to narrow large workspaces.",
				schema("query", "string", false), schema("limit", "number", false),
				schema("modifiedWithinDays", "number", false), schema("filePath", "string", false),
				schema("projectId", "string", false)));
		tools.add(tool("list_recently_modified",
				"List recently modified nodes (node MODIFIED per file). Optional keyword filter.",
				schema("query", "string", false), schema("limit", "number", false),
				schema("modifiedWithinDays", "number", false)));
		tools.add(tool("get_relationship_graph",
				"Silent relationship graph across workspace .mm files (hyperlinks + arrow links). "
						+ "Modes: map_files (default, file-level) or map_nodes (node-level, slower). "
						+ "Use filePath/nodeId + hops for local neighborhood; query for label search. Cached ~10min unless refresh=true.",
				schema("mode", "string", false), schema("query", "string", false),
				schema("filePath", "string", false), schema("nodeId", "string", false),
				schema("hops", "number", false), schema("showIsolated", "boolean", false),
				schema("maxNodes", "number", false), schema("maxEdges", "number", false),
				schema("refresh", "boolean", false)));
		tools.add(tool("get_node_relationships",
				"Direct link neighbors of one mind map or node (ego network). Silent; does not open UI.",
				schema("filePath", "string", true), schema("nodeId", "string", false),
				schema("hops", "number", false), schema("mode", "string", false),
				schema("maxNodes", "number", false), schema("maxEdges", "number", false),
				schema("refresh", "boolean", false)));
		tools.add(tool("open_mindmap",
				"Open a mind map tab in Docear UI. Use only when the user asks to open/show a map.",
				schema("filePath", "string", true)));
		tools.add(tool("navigate_to_node",
				"Open a mind map in Docear UI and select a node. Use only when the user asks to navigate.",
				schema("filePath", "string", true), schema("nodeId", "string", true)));
		tools.add(tool("add_node",
				"Add a child node. Optional filePath targets any .mm without opening it in UI; omit to use the current map.",
				schema("filePath", "string", false), schema("parentNodeId", "string", true),
				schema("text", "string", true)));
		tools.add(tool("change_node_text",
				"Change node text. Optional filePath targets any .mm without opening it in UI.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("text", "string", true)));
		tools.add(tool("remove_node",
				"Remove a node. Optional filePath targets any .mm without opening it in UI.",
				schema("filePath", "string", false), schema("nodeId", "string", true)));
		tools.add(tool("create_todo",
				"Create a todo node with hourglass icon. Optional filePath targets any .mm without opening it in UI.",
				schema("filePath", "string", false), schema("parentNodeId", "string", true),
				schema("text", "string", true)));
		tools.add(tool("complete_todo",
				"Complete a todo by removing hourglass icon. Optional filePath targets any .mm without opening it in UI.",
				schema("filePath", "string", false), schema("nodeId", "string", true)));
		tools.add(tool("set_reminder",
				"Set a one-time reminder on a node. Optional filePath targets any .mm without opening it in UI.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("remindAtMillis", "number", true)));
		tools.add(tool("set_priority",
				"Set priority icon full-1..full-7 on a node. Optional filePath targets any .mm without opening it in UI.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("level", "number", true)));
		tools.add(tool("move_node",
				"Move a node under a new parent. Optional filePath targets any .mm without opening it in UI.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("newParentNodeId", "string", true), schema("index", "number", false)));
		tools.add(tool("set_node_folded",
				"Fold or unfold a node branch. Optional filePath targets any .mm without opening it in UI.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("folded", "boolean", true)));
		tools.add(tool("set_node_link",
				"Set or clear a node hyperlink / file link. Optional filePath targets any .mm without opening it in UI.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("link", "string", false)));
		tools.add(tool("set_node_note",
				"Set node note (HTML). Optional filePath targets any .mm without opening it in UI.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("noteHtml", "string", false)));
		tools.add(tool("set_node_tags",
				"Set user tags on a node (comma-separated). Use pinned=true to add pin tag.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("tags", "string", false), schema("pinned", "boolean", false)));
		tools.add(tool("toggle_pin",
				"Toggle pin tag on a node. Optional filePath targets any .mm without opening it in UI.",
				schema("filePath", "string", false), schema("nodeId", "string", true)));
		tools.add(tool("set_node_icon",
				"Add or remove a mind map icon by name (e.g. hourglass, full-3).",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("iconName", "string", true), schema("enabled", "boolean", false)));
		tools.add(tool("set_recurring_reminder",
				"Set a recurring reminder with Docear cycle attrs (day/week/month/year/eb).",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("remindAtMillis", "number", true), schema("remindType", "string", false),
				schema("interval", "number", false), schema("weekDays", "string", false),
				schema("taskLevel", "number", false), schema("jinji", "number", false)));
		tools.add(tool("create_mindmap",
				"Create a new .mm file on disk. Optionally open it in Docear UI.",
				schema("filePath", "string", true), schema("rootText", "string", false),
				schema("openInUi", "boolean", false)));
		tools.add(tool("list_projects", "List workspace projects."));
		tools.add(tool("quick_capture", "Capture text into the inbox mind map.", schema("text", "string", true)));
		tools.add(tool("sync_todoist", "Sync reminders to Todoist."));
		tools.add(tool("export_workspace_snapshot", "Export workspace snapshot markdown files."));
		tools.add(tool("list_audit_log",
				"List MCP audit detail rows from SQLite (_data/audit.db): request/response JSON, question summary, operation goal.",
				schema("limit", "number", false), schema("intent", "string", false), schema("traceId", "string", false),
				schema("questionQuery", "string", false), schema("action", "string", false),
				schema("sinceMillis", "number", false)));
		tools.add(tool("list_audit_traces",
				"List grouped user-question traces (traceId + questionSummary + actions invoked).",
				schema("limit", "number", false), schema("questionQuery", "string", false),
				schema("sinceMillis", "number", false)));
		tools.add(tool("get_audit_stats",
				"Read pre-aggregated MCP audit stats (minute/hour/day buckets) for reporting.",
				schema("granularity", "string", false), schema("limit", "number", false), schema("intent", "string", false),
				schema("action", "string", false), schema("sinceMillis", "number", false)));
		return tools;
	}

	private JsonValue callTool(final JsonValue params) throws Exception {
		final Map<String, JsonValue> rawArgs = params.asMap().containsKey("arguments")
				? params.asMap().get("arguments").asMap()
				: params.asMap();
		final McpAuditService.AuditMetadata auditMetadata = McpAuditService.extractAuditMetadata(rawArgs);
		final Map<String, JsonValue> args = McpAuditService.stripAuditMetadata(rawArgs);
		final String name = params.asMap().get("name").asString();
		final long startedAt = System.currentTimeMillis();
		boolean success = true;
		String errorMessage = null;
		String textResult = null;
		try {
			textResult = dispatchTool(name, args);
			return toolResult(textResult);
		}
		catch (Exception e) {
			success = false;
			errorMessage = e.getMessage();
			throw e;
		}
		finally {
			McpAuditService.recordToolCall(name, args, auditMetadata, success, errorMessage,
					System.currentTimeMillis() - startedAt, textResult);
		}
	}

	private String dispatchTool(final String name, final Map<String, JsonValue> args) throws Exception {
		final String textResult;
		if ("list_todos".equals(name)) {
			textResult = McpTaskService.listTodos();
		}
		else if ("list_reminders".equals(name)) {
			textResult = McpTaskService.listReminders(argBool(args, "oneTimeOnly", false),
					argBool(args, "recurringOnly", false));
		}
		else if ("list_overdue".equals(name)) {
			textResult = McpTaskService.listOverdue();
		}
		else if ("get_workspace_plan".equals(name)) {
			textResult = McpContextService.getWorkspacePlan();
		}
		else if ("get_active_map_json".equals(name)) {
			textResult = McpMindMapService.getActiveMapJson(argBool(args, "includeFolded", true));
		}
		else if ("get_mindmap_json".equals(name)) {
			textResult = McpMindMapService.getMindmapJson(required(args, "filePath"),
					argInt(args, "maxDepth", 0), argBool(args, "includeFolded", true));
		}
		else if ("get_node_details".equals(name)) {
			textResult = McpNodeService.getNodeDetails(required(args, "filePath"), required(args, "nodeId"));
		}
		else if ("list_pinned".equals(name)) {
			textResult = McpNodeService.listPinned(argInt(args, "limit", 100));
		}
		else if ("list_published".equals(name)) {
			textResult = McpNodeService.listPublished(argInt(args, "limit", 100));
		}
		else if ("get_selection_context".equals(name)) {
			textResult = McpContextService.getSelectionContext();
		}
		else if ("search_nodes".equals(name)) {
			textResult = McpMindMapService.searchNodes(argString(args, "query", ""),
					argInt(args, "limit", 50), argInt(args, "modifiedWithinDays", 0),
					argString(args, "filePath", ""), argString(args, "projectId", ""));
		}
		else if ("list_recently_modified".equals(name)) {
			textResult = McpMindMapService.listRecentlyModified(argString(args, "query", ""),
					argInt(args, "limit", 50), argInt(args, "modifiedWithinDays", 365));
		}
		else if ("get_relationship_graph".equals(name)) {
			textResult = McpRelationshipGraphService.getRelationshipGraph(argString(args, "mode", "map_files"),
					argString(args, "query", ""), argString(args, "filePath", ""), argString(args, "nodeId", ""),
					argInt(args, "hops", 1), argBool(args, "showIsolated", false), argInt(args, "maxNodes", 100),
					argInt(args, "maxEdges", 200), argBool(args, "refresh", false));
		}
		else if ("get_node_relationships".equals(name)) {
			textResult = McpRelationshipGraphService.getNodeRelationships(required(args, "filePath"),
					argString(args, "nodeId", ""), argInt(args, "hops", 1), argString(args, "mode", "map_files"),
					argInt(args, "maxNodes", 80), argInt(args, "maxEdges", 120), argBool(args, "refresh", false));
		}
		else if ("open_mindmap".equals(name)) {
			textResult = McpMindMapService.openMindmap(required(args, "filePath"));
		}
		else if ("navigate_to_node".equals(name)) {
			textResult = McpMindMapService.navigateToNode(required(args, "filePath"), required(args, "nodeId"));
		}
		else if ("add_node".equals(name)) {
			textResult = McpMindMapService.addNode(argString(args, "filePath", ""), required(args, "parentNodeId"),
					required(args, "text"));
		}
		else if ("change_node_text".equals(name)) {
			textResult = McpMindMapService.changeNodeText(argString(args, "filePath", ""), required(args, "nodeId"),
					required(args, "text"));
		}
		else if ("remove_node".equals(name)) {
			textResult = McpMindMapService.removeNode(argString(args, "filePath", ""), required(args, "nodeId"));
		}
		else if ("create_todo".equals(name)) {
			textResult = McpMindMapService.createTodo(argString(args, "filePath", ""), required(args, "parentNodeId"),
					required(args, "text"));
		}
		else if ("complete_todo".equals(name)) {
			textResult = McpMindMapService.completeTodo(argString(args, "filePath", ""), required(args, "nodeId"));
		}
		else if ("set_reminder".equals(name)) {
			textResult = McpMindMapService.setReminder(argString(args, "filePath", ""), required(args, "nodeId"),
					argLong(args, "remindAtMillis", System.currentTimeMillis()));
		}
		else if ("set_priority".equals(name)) {
			textResult = McpMindMapService.setPriority(argString(args, "filePath", ""), required(args, "nodeId"),
					argInt(args, "level", 3));
		}
		else if ("move_node".equals(name)) {
			textResult = McpNodeService.moveNode(argString(args, "filePath", ""), required(args, "nodeId"),
					required(args, "newParentNodeId"), argInt(args, "index", -1));
		}
		else if ("set_node_folded".equals(name)) {
			textResult = McpNodeService.setNodeFolded(argString(args, "filePath", ""), required(args, "nodeId"),
					argBool(args, "folded", true));
		}
		else if ("set_node_link".equals(name)) {
			textResult = McpNodeService.setNodeLink(argString(args, "filePath", ""), required(args, "nodeId"),
					argString(args, "link", ""));
		}
		else if ("set_node_note".equals(name)) {
			textResult = McpNodeService.setNodeNote(argString(args, "filePath", ""), required(args, "nodeId"),
					argString(args, "noteHtml", ""));
		}
		else if ("set_node_tags".equals(name)) {
			textResult = McpNodeService.setNodeTags(argString(args, "filePath", ""), required(args, "nodeId"),
					argString(args, "tags", ""), argBool(args, "pinned", false));
		}
		else if ("toggle_pin".equals(name)) {
			textResult = McpNodeService.togglePin(argString(args, "filePath", ""), required(args, "nodeId"));
		}
		else if ("set_node_icon".equals(name)) {
			textResult = McpNodeService.setNodeIcon(argString(args, "filePath", ""), required(args, "nodeId"),
					required(args, "iconName"), argBool(args, "enabled", true));
		}
		else if ("set_recurring_reminder".equals(name)) {
			textResult = McpNodeService.setRecurringReminder(argString(args, "filePath", ""), required(args, "nodeId"),
					argLong(args, "remindAtMillis", System.currentTimeMillis()),
					argString(args, "remindType", "day"), argInt(args, "interval", 1),
					argString(args, "weekDays", ""), argInt(args, "taskLevel", 0), argInt(args, "jinji", 0));
		}
		else if ("create_mindmap".equals(name)) {
			textResult = McpNodeService.createMindmap(required(args, "filePath"), argString(args, "rootText", ""),
					argBool(args, "openInUi", false));
		}
		else if ("list_projects".equals(name)) {
			textResult = McpWorkspaceService.listProjects();
		}
		else if ("quick_capture".equals(name)) {
			textResult = McpMindMapService.quickCapture(required(args, "text"));
		}
		else if ("sync_todoist".equals(name)) {
			textResult = McpMindMapService.syncTodoist();
		}
		else if ("export_workspace_snapshot".equals(name)) {
			textResult = McpMindMapService.exportWorkspaceSnapshot();
		}
		else if ("list_audit_log".equals(name)) {
			textResult = McpAuditService.listAuditLog(argInt(args, "limit", 50), argString(args, "intent", ""),
					argString(args, "traceId", ""), argString(args, "questionQuery", ""), argString(args, "action", ""),
					argLong(args, "sinceMillis", 0L));
		}
		else if ("list_audit_traces".equals(name)) {
			textResult = McpAuditService.listAuditTraces(argInt(args, "limit", 50), argString(args, "questionQuery", ""),
					argLong(args, "sinceMillis", 0L));
		}
		else if ("get_audit_stats".equals(name)) {
			textResult = McpAuditService.getAuditStats(argString(args, "granularity", "minute"),
					argInt(args, "limit", 100), argString(args, "intent", ""), argString(args, "action", ""),
					argLong(args, "sinceMillis", 0L));
		}
		else {
			throw new IllegalArgumentException("Unknown tool: " + name);
		}
		return textResult;
	}

	private List<JsonValue> listResources() {
		final List<JsonValue> resources = new ArrayList<JsonValue>();
		resources.add(resource("docear://manifest", "Capability manifest", "application/json"));
		resources.add(resource("docear://workspace/overview", "Workspace overview", "application/json"));
		resources.add(resource("docear://workspace/plan", "Workspace plan summary", "text/plain"));
		resources.add(resource("docear://tasks/today", "Today's reminders", "application/json"));
		resources.add(resource("docear://tasks/todos", "All todos", "application/json"));
		resources.add(resource("docear://tasks/reminders", "All reminders", "application/json"));
		resources.add(resource("docear://tasks/overdue", "Overdue reminders", "application/json"));
		resources.add(resource("docear://context/selection", "Current selection", "application/json"));
		resources.add(resource("docear://context/active-map", "Active mind map JSON", "application/json"));
		resources.add(resource("docear://context/recent", "Recently modified nodes", "application/json"));
		resources.add(resource("docear://graph/summary", "Relationship graph summary (file + node modes)", "application/json"));
		resources.add(resource("docear://inbox", "Inbox capture hint", "application/json"));
		return resources;
	}

	private JsonValue readResource(final JsonValue params) throws Exception {
		final String uri = params.asMap().get("uri").asString();
		final Map<String, JsonValue> requestParams = params.asMap();
		final McpAuditService.AuditMetadata auditMetadata = McpAuditService.extractAuditMetadata(requestParams);
		final long startedAt = System.currentTimeMillis();
		boolean success = true;
		String errorMessage = null;
		String text = null;
		String mimeType = "application/json";
		try {
			final ResourceReadResult read = readResourceBody(uri);
			mimeType = read.mimeType;
			text = read.text;
			return resourceResult(uri, mimeType, text);
		}
		catch (Exception e) {
			success = false;
			errorMessage = e.getMessage();
			throw e;
		}
		finally {
			McpAuditService.recordResourceRead(uri, requestParams, auditMetadata, success, errorMessage,
					System.currentTimeMillis() - startedAt, text);
		}
	}

	private static final class ResourceReadResult {
		final String mimeType;
		final String text;

		ResourceReadResult(final String mimeType, final String text) {
			this.mimeType = mimeType;
			this.text = text;
		}
	}

	private ResourceReadResult readResourceBody(final String uri) throws Exception {
		final String mimeType;
		final String text;
		if ("docear://manifest".equals(uri)) {
			mimeType = "application/json";
			text = McpContextService.getManifestJson();
		}
		else if ("docear://workspace/overview".equals(uri)) {
			mimeType = "application/json";
			text = McpWorkspaceService.getOverview();
		}
		else if ("docear://workspace/plan".equals(uri)) {
			mimeType = "text/plain";
			text = McpContextService.getWorkspacePlan();
		}
		else if ("docear://tasks/today".equals(uri)) {
			mimeType = "application/json";
			text = McpContextService.getTodayTimelineJson();
		}
		else if ("docear://tasks/todos".equals(uri)) {
			mimeType = "application/json";
			text = McpTaskService.listTodos();
		}
		else if ("docear://tasks/reminders".equals(uri)) {
			mimeType = "application/json";
			text = McpTaskService.listReminders(false, false);
		}
		else if ("docear://tasks/overdue".equals(uri)) {
			mimeType = "application/json";
			text = McpTaskService.listOverdue();
		}
		else if ("docear://context/selection".equals(uri)) {
			mimeType = "application/json";
			text = McpContextService.getSelectionContext();
		}
		else if ("docear://context/active-map".equals(uri)) {
			mimeType = "application/json";
			text = McpMindMapService.getActiveMapJson();
		}
		else if ("docear://context/recent".equals(uri)) {
			mimeType = "application/json";
			text = McpContextService.getRecentContext(50);
		}
		else if ("docear://graph/summary".equals(uri)) {
			mimeType = "application/json";
			text = McpRelationshipGraphService.getGraphSummary(false);
		}
		else if ("docear://inbox".equals(uri)) {
			mimeType = "application/json";
			text = McpContextService.getInboxContext();
		}
		else {
			throw new IllegalArgumentException("Unknown resource: " + uri);
		}
		return new ResourceReadResult(mimeType, text);
	}

	private List<JsonValue> listPrompts() {
		final List<JsonValue> prompts = new ArrayList<JsonValue>();
		prompts.add(prompt("daily-review", "Review today's reminders, todos and overdue items."));
		prompts.add(prompt("plan-my-day", "Plan the day based on reminders, todos and priorities."));
		prompts.add(prompt("break-down-task", "Break the selected node into actionable subtasks."));
		prompts.add(prompt("project-status", "Summarize project progress from workspace mind maps."));
		prompts.add(prompt("inbox-triage", "Triage inbox captures into projects and todos."));
		prompts.add(prompt("weekly-review", "Weekly review of completed and pending work."));
		return prompts;
	}

	private JsonValue getPrompt(final JsonValue params) throws Exception {
		final String name = params.asMap().get("name").asString();
		final Map<String, JsonValue> requestParams = params.asMap();
		final McpAuditService.AuditMetadata auditMetadata = McpAuditService.extractAuditMetadata(requestParams);
		final long startedAt = System.currentTimeMillis();
		boolean success = true;
		String errorMessage = null;
		JsonValue result = null;
		try {
			result = buildPromptResult(name);
			return result;
		}
		catch (Exception e) {
			success = false;
			errorMessage = e.getMessage();
			throw e;
		}
		finally {
			McpAuditService.recordPromptGet(name, requestParams, auditMetadata, success, errorMessage,
					System.currentTimeMillis() - startedAt, result != null ? JsonWriter.write(result) : "");
		}
	}

	private JsonValue buildPromptResult(final String name) throws Exception {
		final String instructions;
		if ("daily-review".equals(name)) {
			instructions = "Read docear://workspace/plan, docear://tasks/today, docear://tasks/overdue and docear://tasks/todos. Summarize what is due today, what is overdue, and suggest the top 3 actions.";
		}
		else if ("plan-my-day".equals(name)) {
			instructions = "Read docear://tasks/today and docear://tasks/todos. Propose a realistic schedule for today with time blocks. Offer to create todos or reminders via MCP tools if helpful.";
		}
		else if ("break-down-task".equals(name)) {
			instructions = "Read docear://context/selection. Break the selected task into 3-7 concrete subtasks. Use create_todo or add_node tools when the user agrees.";
		}
		else if ("project-status".equals(name)) {
			instructions = "Read docear://workspace/overview and search_nodes for the target project. Summarize open todos, reminders and recent changes.";
		}
		else if ("inbox-triage".equals(name)) {
			instructions = "Read docear://inbox and docear://workspace/plan. Suggest how to classify inbox items into projects, todos and reminders.";
		}
		else if ("weekly-review".equals(name)) {
			instructions = "Read docear://workspace/plan, docear://tasks/reminders, docear://tasks/todos and docear://context/recent. Produce a weekly summary with wins, blockers and next-week priorities.";
		}
		else {
			throw new IllegalArgumentException("Unknown prompt: " + name);
		}
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("description", JsonValue.ofString(name));
		final List<JsonValue> messages = new ArrayList<JsonValue>();
		final Map<String, JsonValue> message = new LinkedHashMap<String, JsonValue>();
		message.put("role", JsonValue.ofString("user"));
		final Map<String, JsonValue> content = new LinkedHashMap<String, JsonValue>();
		content.put("type", JsonValue.ofString("text"));
		content.put("text", JsonValue.ofString(instructions));
		message.put("content", JsonValue.ofMap(content));
		messages.add(JsonValue.ofMap(message));
		result.put("messages", JsonValue.ofList(messages));
		return JsonValue.ofMap(result);
	}

	private void captureInitializeClient(final JsonValue params) {
		final McpRequestContext ctx = McpRequestContext.current();
		if (ctx == null || ctx.getSessionId().length() == 0) {
			return;
		}
		final Map<String, JsonValue> map = params.asMap();
		if (!map.containsKey("clientInfo")) {
			return;
		}
		final Map<String, JsonValue> clientInfo = map.get("clientInfo").asMap();
		if (clientInfo.containsKey("name")) {
			McpAuditService.registerClient(ctx.getSessionId(), clientInfo.get("name").asString());
		}
	}

	private JsonValue tool(final String name, final String description) {
		return tool(name, description, new JsonValue[0]);
	}

	private JsonValue tool(final String name, final String description, final JsonValue... properties) {
		final Map<String, JsonValue> tool = new LinkedHashMap<String, JsonValue>();
		tool.put("name", JsonValue.ofString(name));
		tool.put("description", JsonValue.ofString(description));
		final Map<String, JsonValue> inputSchema = new LinkedHashMap<String, JsonValue>();
		inputSchema.put("type", JsonValue.ofString("object"));
		final Map<String, JsonValue> props = new LinkedHashMap<String, JsonValue>();
		final List<JsonValue> required = new ArrayList<JsonValue>();
		for (int i = 0; i < properties.length; i++) {
			final Map<String, JsonValue> property = properties[i].asMap();
			if (!property.containsKey("name") || !property.containsKey("schema")) {
				continue;
			}
			final String propName = property.get("name").asString();
			props.put(propName, property.get("schema"));
			if (property.containsKey("required") && property.get("required").asBoolean()) {
				required.add(JsonValue.ofString(propName));
			}
		}
		inputSchema.put("properties", JsonValue.ofMap(props));
		if (!required.isEmpty()) {
			inputSchema.put("required", JsonValue.ofList(required));
		}
		tool.put("inputSchema", JsonValue.ofMap(inputSchema));
		return JsonValue.ofMap(tool);
	}

	private JsonValue schema(final String name, final String type, final boolean required) {
		final Map<String, JsonValue> property = new LinkedHashMap<String, JsonValue>();
		property.put("name", JsonValue.ofString(name));
		final Map<String, JsonValue> schema = new LinkedHashMap<String, JsonValue>();
		schema.put("type", JsonValue.ofString(type));
		property.put("schema", JsonValue.ofMap(schema));
		property.put("required", JsonValue.ofBoolean(required));
		return JsonValue.ofMap(property);
	}

	private JsonValue resource(final String uri, final String name, final String mimeType) {
		final Map<String, JsonValue> resource = new LinkedHashMap<String, JsonValue>();
		resource.put("uri", JsonValue.ofString(uri));
		resource.put("name", JsonValue.ofString(name));
		resource.put("mimeType", JsonValue.ofString(mimeType));
		return JsonValue.ofMap(resource);
	}

	private JsonValue prompt(final String name, final String description) {
		final Map<String, JsonValue> prompt = new LinkedHashMap<String, JsonValue>();
		prompt.put("name", JsonValue.ofString(name));
		prompt.put("description", JsonValue.ofString(description));
		return JsonValue.ofMap(prompt);
	}

	private JsonValue toolResult(final String text) {
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		final List<JsonValue> content = new ArrayList<JsonValue>();
		final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
		item.put("type", JsonValue.ofString("text"));
		item.put("text", JsonValue.ofString(text));
		content.add(JsonValue.ofMap(item));
		result.put("content", JsonValue.ofList(content));
		return JsonValue.ofMap(result);
	}

	private JsonValue resourceResult(final String uri, final String mimeType, final String text) {
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		final List<JsonValue> contents = new ArrayList<JsonValue>();
		final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
		item.put("uri", JsonValue.ofString(uri));
		item.put("mimeType", JsonValue.ofString(mimeType));
		item.put("text", JsonValue.ofString(text));
		contents.add(JsonValue.ofMap(item));
		result.put("contents", JsonValue.ofList(contents));
		return JsonValue.ofMap(result);
	}

	private String success(final JsonValue id, final JsonValue result) {
		final Map<String, JsonValue> response = new LinkedHashMap<String, JsonValue>();
		response.put("jsonrpc", JsonValue.ofString("2.0"));
		if (id != null && !id.isNull()) {
			response.put("id", id);
		}
		response.put("result", result);
		return JsonWriter.write(JsonValue.ofMap(response));
	}

	private Map<String, JsonValue> singleEntry(final String key, final JsonValue value) {
		final Map<String, JsonValue> map = new LinkedHashMap<String, JsonValue>();
		map.put(key, value);
		return map;
	}

	private static String required(final Map<String, JsonValue> args, final String key) {
		if (!args.containsKey(key) || args.get(key).isNull()) {
			throw new IllegalArgumentException("Missing required argument: " + key);
		}
		return args.get(key).asString();
	}

	private static String argString(final Map<String, JsonValue> args, final String key, final String defaultValue) {
		return args.containsKey(key) ? args.get(key).asString() : defaultValue;
	}

	private static boolean argBool(final Map<String, JsonValue> args, final String key, final boolean defaultValue) {
		return args.containsKey(key) ? args.get(key).asBoolean() : defaultValue;
	}

	private static int argInt(final Map<String, JsonValue> args, final String key, final int defaultValue) {
		return args.containsKey(key) ? args.get(key).asInt(defaultValue) : defaultValue;
	}

	private static long argLong(final Map<String, JsonValue> args, final String key, final long defaultValue) {
		return args.containsKey(key) ? args.get(key).asLong(defaultValue) : defaultValue;
	}
}
