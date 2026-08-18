package org.docear.plugin.mcp.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.audit.McpAuditLabels;
import org.docear.plugin.mcp.audit.McpAuditService;
import org.docear.plugin.mcp.audit.McpRequestContext;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.docear.plugin.mcp.service.McpContextService;
import org.docear.plugin.mcp.service.McpFinanceService;
import org.docear.plugin.mcp.service.McpGitService;
import org.docear.plugin.mcp.service.McpMindMapService;
import org.docear.plugin.mcp.service.McpNodeEditService;
import org.docear.plugin.mcp.service.McpNodeService;
import org.docear.plugin.mcp.service.McpPomodoroService;
import org.docear.plugin.mcp.service.McpRelationshipGraphService;
import org.docear.plugin.mcp.service.McpTagService;
import org.docear.plugin.mcp.service.McpTaskService;
import org.docear.plugin.mcp.service.McpWorkspaceService;

public final class McpProtocol {

	private static volatile List<JsonValue> cachedTools;
	private static volatile List<JsonValue> cachedResources;
	private static volatile List<JsonValue> cachedPrompts;

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
			return success(id, JsonValue.ofMap(singleEntry("tools",
					JsonValue.ofList(McpPermissions.filterTools(listTools(), currentPrincipal())))));
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

	/** Public tool catalog for the in-process web agent / OpenAI tool bridge. */
	public List<JsonValue> getToolDefinitions() {
		return listTools();
	}

	/** Web chat tool catalog — read-only subset when {@link DocearMcpConfig#isWebReadOnlyTools()}. */
	public List<JsonValue> getWebToolDefinitions() {
		final McpRole role = DocearMcpConfig.isWebReadOnlyTools() ? McpRole.READ : McpRole.WRITE;
		return McpPermissions.filterTools(getToolDefinitions(), role);
	}

	public static boolean isWriteTool(final String name) {
		return McpPermissions.isWriteTool(name);
	}

	/**
	 * Invoke a tool in-process (same path as MCP {@code tools/call}), returning the
	 * text payload from the tool result.
	 */
	public String invokeToolText(final String name, final Map<String, JsonValue> arguments) throws Exception {
		final Map<String, JsonValue> params = new LinkedHashMap<String, JsonValue>();
		params.put("name", JsonValue.ofString(name));
		params.put("arguments",
				JsonValue.ofMap(arguments != null ? arguments : new LinkedHashMap<String, JsonValue>()));
		final JsonValue result = callTool(JsonValue.ofMap(params));
		return extractToolText(result);
	}

	private static String extractToolText(final JsonValue result) {
		if (result == null || result.isNull()) {
			return "";
		}
		final Map<String, JsonValue> map = result.asMap();
		if (!map.containsKey("content")) {
			return result.toJson();
		}
		final List<JsonValue> content = map.get("content").asList();
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < content.size(); i++) {
			final Map<String, JsonValue> item = content.get(i).asMap();
			if (item.containsKey("text")) {
				if (sb.length() > 0) {
					sb.append('\n');
				}
				sb.append(item.get("text").asString());
			}
		}
		return sb.length() > 0 ? sb.toString() : result.toJson();
	}

	private List<JsonValue> listTools() {
		if (cachedTools != null) {
			return cachedTools;
		}
		synchronized (McpProtocol.class) {
			if (cachedTools != null) {
				return cachedTools;
			}
			cachedTools = buildTools();
			return cachedTools;
		}
	}

	private List<JsonValue> buildTools() {
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
				"Get full details for one node: note, link, icons, tags, reminders, privacy, cloud/style, imageUri, arrowLinks, encryption, parent path.",
				schema("filePath", "string", true), schema("nodeId", "string", true)));
		tools.add(tool("list_pinned", "List pinned nodes from the workspace sidebar.",
				schema("limit", "number", false)));
		tools.add(tool("list_published", "List nodes marked with the published icon.",
				schema("limit", "number", false)));
		tools.add(tool("list_tag_groups",
				"List sidebar tag groups: flat depth-first list plus nested tree (parentId/depth, unlimited nesting)."));
		tools.add(tool("list_tags",
				"List tags with counts/colors/group. scope: pins (default) | favorites | all. Optional groupId filter.",
				schema("scope", "string", false), schema("groupId", "string", false),
				schema("includeEmpty", "boolean", false)));
		tools.add(tool("list_nodes_by_tag",
				"List nodes/favorites that carry a tag. scope: pins (default) | favorites | all.",
				schema("tag", "string", true), schema("scope", "string", false), schema("limit", "number", false)));
		tools.add(tool("list_favorites",
				"List workspace favorites (sidebar). Optional tag filter.",
				schema("tag", "string", false), schema("limit", "number", false)));
		tools.add(tool("get_tag_catalog",
				"One-shot tag catalog: pin groups→tags→counts/colors, plus favorite tags."));
		tools.add(tool("create_tag_group",
				"Create a sidebar tag group. Optional parentId nests under another group (unlimited depth).",
				schema("name", "string", true), schema("parentId", "string", false)));
		tools.add(tool("rename_tag_group", "Rename a custom tag group (not ungrouped).",
				schema("groupId", "string", true), schema("name", "string", true)));
		tools.add(tool("move_tag_group",
				"Reparent a custom tag group. Omit/empty parentId for top level. Rejects cycles.",
				schema("groupId", "string", true), schema("parentId", "string", false)));
		tools.add(tool("delete_tag_group",
				"Delete a custom tag group and all nested subgroups; their tags move to ungrouped.",
				schema("groupId", "string", true)));
		tools.add(tool("set_tag_group", "Assign a tag to a group (use groupId=ungrouped to clear).",
				schema("tag", "string", true), schema("groupId", "string", true)));
		tools.add(tool("set_tag_color",
				"Set tag chip color (#RRGGBB) or clear=true to restore auto palette.",
				schema("tag", "string", true), schema("color", "string", false),
				schema("clear", "boolean", false)));
		tools.add(tool("get_selection_context",
				"Get current selection context in Docear (includes runningPomodoro when a focus session is active)."));
		tools.add(tool("get_running_pomodoro",
				"Get the currently running pomodoro / focus session (node, live times). Prefer this to learn what the user is doing now."));
		tools.add(tool("list_pomodoro_sessions",
				"List pomodoro-enabled nodes. allMaps=true scans all open maps (default false=current map). "
						+ "stateFilter: running|paused|idle (optional).",
				schema("allMaps", "boolean", false), schema("stateFilter", "string", false)));
		tools.add(tool("get_pomodoro_stats",
				"Pomodoro focus totals: today / week / total plus running/paused counts. allMaps defaults false.",
				schema("allMaps", "boolean", false)));
		tools.add(tool("get_pomodoro_history",
				"Completed focus session history from node POMODORO_LOG. Optional nodeId; omit for whole map. "
						+ "sinceMillis filters by end time; limit defaults 100 (latest).",
				schema("filePath", "string", false), schema("nodeId", "string", false),
				schema("sinceMillis", "number", false), schema("limit", "number", false)));
		tools.add(tool("start_pomodoro",
				"Start free-timing pomodoro on a node (auto-enables switch; pauses other running). "
						+ "Omit nodeId to use current selection; optional filePath for silent map.",
				schema("filePath", "string", false), schema("nodeId", "string", false)));
		tools.add(tool("pause_pomodoro",
				"Pause the running pomodoro on a node. Omit nodeId for current selection.",
				schema("filePath", "string", false), schema("nodeId", "string", false)));
		tools.add(tool("stop_pomodoro",
				"Stop/end the pomodoro session on a node (records history). Omit nodeId for current selection.",
				schema("filePath", "string", false), schema("nodeId", "string", false)));
		tools.add(tool("ensure_finance_map",
				"Ensure the personal finance ledger mind map (个人财务.mm) exists and is open; creates skeleton sections if missing."));
		tools.add(tool("get_finance_summary",
				"Month finance summary: income/expense/net, by-category expense, account/category catalogs. period=yyyy-MM (default this month).",
				schema("period", "string", false)));
		tools.add(tool("add_finance_transaction",
				"Add a ledger transaction into 个人财务.mm. amount as yuan string (e.g. 28.5). "
						+ "flow: expense|income|transfer|borrow|lend|credit. "
						+ "transfer REQUIRES account + accountTo. Optional date (yyyy-MM-dd), category, merchant, note. "
						+ "P&L income/expense are separate from borrow/lend/credit/transfer.",
				schema("amount", "string", true), schema("flow", "string", false), schema("date", "string", false),
				schema("category", "string", false), schema("account", "string", false),
				schema("accountTo", "string", false), schema("merchant", "string", false),
				schema("note", "string", false)));
		tools.add(tool("list_finance_transactions",
				"List finance transactions in date range (yyyy-MM-dd). limit defaults 200 (latest).",
				schema("from", "string", false), schema("to", "string", false), schema("limit", "number", false)));
		tools.add(tool("list_finance_categories",
				"List finance categories. flow: expense|income|all (default all = both folders).",
				schema("flow", "string", false)));
		tools.add(tool("add_finance_category",
				"Add a finance category under expense or income. flow defaults expense.",
				schema("name", "string", true), schema("flow", "string", false)));
		tools.add(tool("list_finance_accounts", "List finance accounts (cash/bank/credit card, etc.)."));
		tools.add(tool("add_finance_account", "Add a finance account node.", schema("name", "string", true)));
		tools.add(tool("set_finance_budget",
				"Set/update a monthly category budget. period=yyyy-MM; amount in yuan.",
				schema("category", "string", true), schema("amount", "string", true), schema("period", "string", false)));
		tools.add(tool("list_finance_budgets", "List budgets for a month (period=yyyy-MM, default this month).",
				schema("period", "string", false)));
		tools.add(tool("upsert_finance_subscription",
				"Create/update a recurring subscription. cycle: monthly|yearly|weekly. amount in yuan.",
				schema("name", "string", true), schema("amount", "string", true), schema("cycle", "string", false),
				schema("next", "string", false), schema("status", "string", false), schema("account", "string", false),
				schema("note", "string", false)));
		tools.add(tool("list_finance_subscriptions", "List recurring subscriptions / digital recurring charges."));
		tools.add(tool("upsert_finance_coupon",
				"Create/update an unused coupon / voucher digital asset. amount in yuan; expires=yyyy-MM-dd.",
				schema("name", "string", true), schema("amount", "string", true), schema("expires", "string", false),
				schema("status", "string", false), schema("merchant", "string", false), schema("note", "string", false)));
		tools.add(tool("list_finance_coupons", "List coupons / vouchers (digital assets)."));
		tools.add(tool("mark_finance_coupon_used",
				"Mark a coupon node used/unused. used defaults true.",
				schema("nodeId", "string", true), schema("used", "boolean", false)));
		tools.add(tool("delete_finance_node",
				"Delete a finance node (txn/budget/subscription/coupon/category/account) by nodeId from 个人财务.mm.",
				schema("nodeId", "string", true)));
		tools.add(tool("get_finance_report",
				"Build a finance report. reportId: month_overview|expense_by_category|income_by_category|trend|"
						+ "budget_status|subscriptions|coupons. Optional from/to (yyyy-MM-dd). "
						+ "showInViewport=true displays charts in the map viewport. Returns kpis+details.",
				schema("reportId", "string", false), schema("from", "string", false), schema("to", "string", false),
				schema("showInViewport", "boolean", false)));
		tools.add(tool("search_nodes",
				"Fast keyword search over node TEXT (indexed + disk spill). Default modifiedWithinDays=365. "
						+ "Pass 0 for unlimited. Prefer filePath/projectId on large workspaces.",
				schema("query", "string", false), schema("limit", "number", false),
				schema("modifiedWithinDays", "number", false), schema("filePath", "string", false),
				schema("projectId", "string", false)));
		tools.add(tool("list_recently_modified",
				"List recently modified nodes (node MODIFIED). Uses search index; default modifiedWithinDays=365.",
				schema("query", "string", false), schema("limit", "number", false),
				schema("modifiedWithinDays", "number", false)));
		tools.add(tool("get_relationship_graph",
				"Silent relationship graph. Modes: map_files (default, fast), favorites (fast), "
						+ "map_nodes/tags (slower full-library). Prefer map_files unless node-level edges needed. "
						+ "Cached ~10min unless refresh=true.",
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
		tools.add(tool("add_nodes",
				"Batch-create a node tree under parentNodeId in ONE save (prefer over many add_node calls). "
						+ "nodes: JSON array. Item = string OR {text, todo?, children?}. "
						+ "Flat example: [\"a\",\"b\"]. Nested via children. Max 300 nodes / depth 20.",
				schema("filePath", "string", false), schema("parentNodeId", "string", true),
				schema("nodes", "array", true)));
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
		tools.add(tool("encrypt_node",
				"Password-protect a node and lock its children (Freeplane ENCRYPTED_CONTENT). "
						+ "Optional password; if omitted, uses Encryption settings default. "
						+ "nodeId optional = current selection. Does not open UI.",
				schema("filePath", "string", false), schema("nodeId", "string", false),
				schema("password", "string", false)));
		tools.add(tool("decrypt_node",
				"Unlock a password-protected node in this session so children can be read. "
						+ "The .mm file stays encrypted until remove_node_encryption. "
						+ "Optional password; if omitted, uses Encryption settings default. Headless-safe (no dialog).",
				schema("filePath", "string", false), schema("nodeId", "string", false),
				schema("password", "string", false)));
		tools.add(tool("remove_node_encryption",
				"Permanently remove password protection after unlocking. Children stay as plaintext. "
						+ "Optional password; if omitted, uses Encryption settings default.",
				schema("filePath", "string", false), schema("nodeId", "string", false),
				schema("password", "string", false)));
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
				"Create a new .mm file on disk. Returns rootNodeId for subsequent add_node/add_nodes. "
						+ "Prefer add_nodes (one batch) after create. Optionally open it in Docear UI.",
				schema("filePath", "string", true), schema("rootText", "string", false),
				schema("openInUi", "boolean", false)));
		tools.add(tool("copy_nodes",
				"Copy a whole subtree (the node and all descendants) into the MCP clipboard. Does not change the map. "
						+ "Paste later with paste_nodes. IDs are regenerated on paste into the same map.",
				schema("filePath", "string", false), schema("nodeId", "string", true)));
		tools.add(tool("cut_nodes",
				"Cut a whole subtree into the MCP clipboard and delete it from the map (not the root). "
						+ "Paste with paste_nodes.",
				schema("filePath", "string", false), schema("nodeId", "string", true)));
		tools.add(tool("paste_nodes",
				"Paste the MCP clipboard subtree under parentNodeId. New node IDs are generated if they collide. "
						+ "Requires a prior copy_nodes or cut_nodes in this MCP process.",
				schema("filePath", "string", false), schema("parentNodeId", "string", true)));
		tools.add(tool("clone_nodes",
				"Clone a whole subtree under parentNodeId (default: the source parent). Source is kept. "
						+ "Pasted nodes get new IDs. Cannot clone a node onto its own descendants.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("parentNodeId", "string", false)));
		tools.add(tool("undo_map",
				"Undo the last actor on this map's undo stack, then save. filePath selects the map.",
				schema("filePath", "string", false)));
		tools.add(tool("redo_map",
				"Redo the last undone actor on this map, then save. filePath selects the map.",
				schema("filePath", "string", false)));
		tools.add(tool("add_arrow_link",
				"Create a Freeplane arrowlink (connector) from sourceNodeId to targetNodeId in the same map. "
						+ "This is not a hyperlink (use set_node_link for LINK). Optional middle label and #RRGGBB color.",
				schema("filePath", "string", false), schema("sourceNodeId", "string", true),
				schema("targetNodeId", "string", true), schema("label", "string", false),
				schema("color", "string", false)));
		tools.add(tool("remove_arrow_link",
				"Remove arrowlink connectors from sourceNodeId to targetNodeId in the same map.",
				schema("filePath", "string", false), schema("sourceNodeId", "string", true),
				schema("targetNodeId", "string", true)));
		tools.add(tool("set_node_cloud",
				"Set or clear the node cloud (color bubble around a branch). enabled=false removes it. "
						+ "color=#RRGGBB. shape: ARC|STAR|RECT|ROUND_RECT.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("enabled", "boolean", false), schema("color", "string", false),
				schema("shape", "string", false)));
		tools.add(tool("set_node_style",
				"Set node font/color/shape. Omit a field to leave it. color/backgroundColor: #RRGGBB or 'clear'. "
						+ "shape: fork|bubble|as_parent|combined. fontSize 1-96.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("color", "string", false), schema("backgroundColor", "string", false),
				schema("fontFamily", "string", false), schema("fontSize", "number", false),
				schema("bold", "boolean", false), schema("italic", "boolean", false),
				schema("shape", "string", false)));
		tools.add(tool("set_node_details",
				"Write node details (the Freeplane 'details' HTML under the node, not the note). "
						+ "Empty detailsHtml clears it. hidden=true collapses details.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("detailsHtml", "string", false), schema("hidden", "boolean", false)));
		tools.add(tool("set_node_privacy",
				"Set Docear node privacy: PUBLIC, DEMO, or PRIVATE.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("privacy", "string", true)));
		tools.add(tool("set_node_image",
				"Attach an image as ExternalObject on the node (inline preview). imagePath is a file path or URI.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("imagePath", "string", true)));
		tools.add(tool("clear_node_image",
				"Remove the ExternalObject image preview from a node (does not delete the file).",
				schema("filePath", "string", false), schema("nodeId", "string", true)));
		tools.add(tool("set_node_attachment",
				"Attach a file: set the node LINK to the file URI. Images also get an ExternalObject preview.",
				schema("filePath", "string", false), schema("nodeId", "string", true),
				schema("attachmentPath", "string", true)));
		tools.add(tool("clear_reminder",
				"Remove the reminder hook and cycle attrs from a node (one-time or recurring).",
				schema("filePath", "string", false), schema("nodeId", "string", true)));
		tools.add(tool("list_projects", "List workspace projects."));
		tools.add(tool("quick_capture", "Capture text into the inbox mind map.", schema("text", "string", true)));
		tools.add(tool("sync_todoist", "Sync reminders to Todoist."));
		tools.add(tool("export_workspace_snapshot", "Export workspace snapshot markdown files."));
		tools.add(tool("git_status",
				"Show git status for the mind-map repository (branch, porcelain, remotes).",
				schema("repoPath", "string", false)));
		tools.add(tool("git_sync",
				"Single-writer sync: pull --ff-only, stage .mm changes, commit if dirty, push. "
						+ "Use after a batch of edits; not on every node write.",
				schema("repoPath", "string", false), schema("message", "string", false),
				schema("push", "boolean", false), schema("pullFirst", "boolean", false)));
		tools.add(tool("list_audit_log",
				"List MCP audit detail rows from SQLite (data/audit.db): request/response JSON, question summary, operation goal.",
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
			if (!McpPermissions.canCall(currentPrincipal(), name)) {
				success = false;
				errorMessage = McpPermissions.denyMessage(currentPrincipal(), name);
				textResult = errorMessage;
				return toolError(errorMessage);
			}
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
		else if ("list_tag_groups".equals(name)) {
			textResult = McpTagService.listTagGroups();
		}
		else if ("list_tags".equals(name)) {
			textResult = McpTagService.listTags(argString(args, "scope", "pins"), argString(args, "groupId", ""),
					argBool(args, "includeEmpty", false));
		}
		else if ("list_nodes_by_tag".equals(name)) {
			textResult = McpTagService.listNodesByTag(required(args, "tag"), argString(args, "scope", "pins"),
					argInt(args, "limit", 200));
		}
		else if ("list_favorites".equals(name)) {
			textResult = McpTagService.listFavorites(argString(args, "tag", ""), argInt(args, "limit", 200));
		}
		else if ("get_tag_catalog".equals(name)) {
			textResult = McpTagService.getTagCatalog();
		}
		else if ("create_tag_group".equals(name)) {
			textResult = McpTagService.createTagGroup(required(args, "name"), argString(args, "parentId", ""));
		}
		else if ("rename_tag_group".equals(name)) {
			textResult = McpTagService.renameTagGroup(required(args, "groupId"), required(args, "name"));
		}
		else if ("move_tag_group".equals(name)) {
			textResult = McpTagService.moveTagGroup(required(args, "groupId"), argString(args, "parentId", ""));
		}
		else if ("delete_tag_group".equals(name)) {
			textResult = McpTagService.deleteTagGroup(required(args, "groupId"));
		}
		else if ("set_tag_group".equals(name)) {
			textResult = McpTagService.setTagGroup(required(args, "tag"), required(args, "groupId"));
		}
		else if ("set_tag_color".equals(name)) {
			textResult = McpTagService.setTagColor(required(args, "tag"), argString(args, "color", ""),
					argBool(args, "clear", false));
		}
		else if ("get_selection_context".equals(name)) {
			textResult = McpContextService.getSelectionContext();
		}
		else if ("get_running_pomodoro".equals(name)) {
			textResult = McpPomodoroService.getRunningPomodoro();
		}
		else if ("list_pomodoro_sessions".equals(name)) {
			textResult = McpPomodoroService.listPomodoroSessions(argBool(args, "allMaps", false),
					argString(args, "stateFilter", ""));
		}
		else if ("get_pomodoro_stats".equals(name)) {
			textResult = McpPomodoroService.getPomodoroStats(argBool(args, "allMaps", false));
		}
		else if ("get_pomodoro_history".equals(name)) {
			textResult = McpPomodoroService.getPomodoroHistory(argString(args, "filePath", ""),
					argString(args, "nodeId", ""), argLong(args, "sinceMillis", 0L), argInt(args, "limit", 100));
		}
		else if ("start_pomodoro".equals(name)) {
			textResult = McpPomodoroService.startPomodoro(argString(args, "filePath", ""),
					argString(args, "nodeId", ""));
		}
		else if ("pause_pomodoro".equals(name)) {
			textResult = McpPomodoroService.pausePomodoro(argString(args, "filePath", ""),
					argString(args, "nodeId", ""));
		}
		else if ("stop_pomodoro".equals(name)) {
			textResult = McpPomodoroService.stopPomodoro(argString(args, "filePath", ""),
					argString(args, "nodeId", ""));
		}
		else if ("ensure_finance_map".equals(name)) {
			textResult = McpFinanceService.ensureFinanceMap();
		}
		else if ("get_finance_summary".equals(name)) {
			textResult = McpFinanceService.getFinanceSummary(argString(args, "period", ""));
		}
		else if ("add_finance_transaction".equals(name)) {
			textResult = McpFinanceService.addFinanceTransaction(required(args, "amount"),
					argString(args, "flow", "expense"), argString(args, "date", ""),
					argString(args, "category", ""), argString(args, "account", ""),
					argString(args, "accountTo", ""), argString(args, "merchant", ""),
					argString(args, "note", ""));
		}
		else if ("list_finance_transactions".equals(name)) {
			textResult = McpFinanceService.listFinanceTransactions(argString(args, "from", ""),
					argString(args, "to", ""), argInt(args, "limit", 200));
		}
		else if ("list_finance_categories".equals(name)) {
			textResult = McpFinanceService.listFinanceCategories(argString(args, "flow", "all"));
		}
		else if ("add_finance_category".equals(name)) {
			textResult = McpFinanceService.addFinanceCategory(required(args, "name"),
					argString(args, "flow", "expense"));
		}
		else if ("list_finance_accounts".equals(name)) {
			textResult = McpFinanceService.listFinanceAccounts();
		}
		else if ("add_finance_account".equals(name)) {
			textResult = McpFinanceService.addFinanceAccount(required(args, "name"));
		}
		else if ("set_finance_budget".equals(name)) {
			textResult = McpFinanceService.setFinanceBudget(argString(args, "period", ""), required(args, "category"),
					required(args, "amount"));
		}
		else if ("list_finance_budgets".equals(name)) {
			textResult = McpFinanceService.listFinanceBudgets(argString(args, "period", ""));
		}
		else if ("upsert_finance_subscription".equals(name)) {
			textResult = McpFinanceService.upsertFinanceSubscription(required(args, "name"), required(args, "amount"),
					argString(args, "cycle", "monthly"), argString(args, "next", ""),
					argString(args, "status", "active"), argString(args, "account", ""),
					argString(args, "note", ""));
		}
		else if ("list_finance_subscriptions".equals(name)) {
			textResult = McpFinanceService.listFinanceSubscriptions();
		}
		else if ("upsert_finance_coupon".equals(name)) {
			textResult = McpFinanceService.upsertFinanceCoupon(required(args, "name"), required(args, "amount"),
					argString(args, "expires", ""), argString(args, "status", "active"),
					argString(args, "merchant", ""), argString(args, "note", ""));
		}
		else if ("list_finance_coupons".equals(name)) {
			textResult = McpFinanceService.listFinanceCoupons();
		}
		else if ("mark_finance_coupon_used".equals(name)) {
			textResult = McpFinanceService.markFinanceCouponUsed(required(args, "nodeId"),
					argBool(args, "used", true));
		}
		else if ("delete_finance_node".equals(name)) {
			textResult = McpFinanceService.deleteFinanceNode(required(args, "nodeId"));
		}
		else if ("get_finance_report".equals(name)) {
			textResult = McpFinanceService.getFinanceReport(argString(args, "reportId", "month_overview"),
					argString(args, "from", ""), argString(args, "to", ""),
					argBool(args, "showInViewport", false));
		}
		else if ("search_nodes".equals(name)) {
			final String filePath = argString(args, "filePath", "");
			final int defaultDays = (filePath != null && filePath.trim().length() > 0) ? 0 : 365;
			textResult = McpMindMapService.searchNodes(argString(args, "query", ""),
					argInt(args, "limit", 50), argInt(args, "modifiedWithinDays", defaultDays),
					filePath, argString(args, "projectId", ""));
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
		else if ("add_nodes".equals(name)) {
			if (!args.containsKey("nodes") || args.get("nodes").isNull()) {
				throw new IllegalArgumentException("Missing required argument: nodes");
			}
			textResult = McpMindMapService.addNodes(argString(args, "filePath", ""), required(args, "parentNodeId"),
					args.get("nodes"));
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
		else if ("encrypt_node".equals(name)) {
			textResult = McpNodeService.encryptNode(argString(args, "filePath", ""), argString(args, "nodeId", ""),
					argString(args, "password", ""));
		}
		else if ("decrypt_node".equals(name)) {
			textResult = McpNodeService.decryptNode(argString(args, "filePath", ""), argString(args, "nodeId", ""),
					argString(args, "password", ""));
		}
		else if ("remove_node_encryption".equals(name)) {
			textResult = McpNodeService.removeNodeEncryption(argString(args, "filePath", ""),
					argString(args, "nodeId", ""), argString(args, "password", ""));
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
		else if ("copy_nodes".equals(name)) {
			textResult = McpNodeEditService.copyNodes(argString(args, "filePath", ""), required(args, "nodeId"));
		}
		else if ("cut_nodes".equals(name)) {
			textResult = McpNodeEditService.cutNodes(argString(args, "filePath", ""), required(args, "nodeId"));
		}
		else if ("paste_nodes".equals(name)) {
			textResult = McpNodeEditService.pasteNodes(argString(args, "filePath", ""), required(args, "parentNodeId"));
		}
		else if ("clone_nodes".equals(name)) {
			textResult = McpNodeEditService.cloneNodes(argString(args, "filePath", ""), required(args, "nodeId"),
					argString(args, "parentNodeId", ""));
		}
		else if ("undo_map".equals(name)) {
			textResult = McpNodeEditService.undoMap(argString(args, "filePath", ""));
		}
		else if ("redo_map".equals(name)) {
			textResult = McpNodeEditService.redoMap(argString(args, "filePath", ""));
		}
		else if ("add_arrow_link".equals(name)) {
			textResult = McpNodeEditService.addArrowLink(argString(args, "filePath", ""), required(args, "sourceNodeId"),
					required(args, "targetNodeId"), argString(args, "label", ""), argString(args, "color", ""));
		}
		else if ("remove_arrow_link".equals(name)) {
			textResult = McpNodeEditService.removeArrowLink(argString(args, "filePath", ""),
					required(args, "sourceNodeId"), required(args, "targetNodeId"));
		}
		else if ("set_node_cloud".equals(name)) {
			textResult = McpNodeEditService.setNodeCloud(argString(args, "filePath", ""), required(args, "nodeId"),
					argBoolOptional(args, "enabled"), argOptional(args, "color"), argOptional(args, "shape"));
		}
		else if ("set_node_style".equals(name)) {
			textResult = McpNodeEditService.setNodeStyle(argString(args, "filePath", ""), required(args, "nodeId"),
					argOptional(args, "color"), argOptional(args, "backgroundColor"), argOptional(args, "fontFamily"),
					argIntOptional(args, "fontSize"), argBoolOptional(args, "bold"), argBoolOptional(args, "italic"),
					argOptional(args, "shape"));
		}
		else if ("set_node_details".equals(name)) {
			textResult = McpNodeEditService.setNodeDetails(argString(args, "filePath", ""), required(args, "nodeId"),
					argString(args, "detailsHtml", ""), argBoolOptional(args, "hidden"));
		}
		else if ("set_node_privacy".equals(name)) {
			textResult = McpNodeEditService.setNodePrivacy(argString(args, "filePath", ""), required(args, "nodeId"),
					required(args, "privacy"));
		}
		else if ("set_node_image".equals(name)) {
			textResult = McpNodeEditService.setNodeImage(argString(args, "filePath", ""), required(args, "nodeId"),
					required(args, "imagePath"));
		}
		else if ("clear_node_image".equals(name)) {
			textResult = McpNodeEditService.clearNodeImage(argString(args, "filePath", ""), required(args, "nodeId"));
		}
		else if ("set_node_attachment".equals(name)) {
			textResult = McpNodeEditService.setNodeAttachment(argString(args, "filePath", ""), required(args, "nodeId"),
					required(args, "attachmentPath"));
		}
		else if ("clear_reminder".equals(name)) {
			textResult = McpNodeEditService.clearReminder(argString(args, "filePath", ""), required(args, "nodeId"));
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
		else if ("git_status".equals(name)) {
			textResult = McpGitService.gitStatus(argString(args, "repoPath", ""));
		}
		else if ("git_sync".equals(name)) {
			textResult = McpGitService.gitSync(argString(args, "repoPath", ""), argString(args, "message", ""),
					argBool(args, "push", true), argBool(args, "pullFirst", true));
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
		if (cachedResources != null) {
			return cachedResources;
		}
		synchronized (McpProtocol.class) {
			if (cachedResources != null) {
				return cachedResources;
			}
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
			resources.add(resource("docear://graph/summary",
					"Relationship graph summary (map_files+favorites; heavy modes skipped unless cached)",
					"application/json"));
			resources.add(resource("docear://tags/catalog", "Tag groups + tags + favorite tags catalog",
					"application/json"));
			resources.add(resource("docear://pomodoro/running", "Currently running pomodoro / focus session",
					"application/json"));
			resources.add(resource("docear://pomodoro/stats",
					"Pomodoro today/week/total stats (all open maps)", "application/json"));
			resources.add(resource("docear://finance/summary", "Current-month personal finance summary",
					"application/json"));
			resources.add(resource("docear://inbox", "Inbox capture hint", "application/json"));
			cachedResources = resources;
			return cachedResources;
		}
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
		else if ("docear://tags/catalog".equals(uri)) {
			mimeType = "application/json";
			text = McpTagService.getTagCatalog();
		}
		else if ("docear://pomodoro/running".equals(uri)) {
			mimeType = "application/json";
			text = McpPomodoroService.getRunningPomodoro();
		}
		else if ("docear://pomodoro/stats".equals(uri)) {
			mimeType = "application/json";
			text = McpPomodoroService.getPomodoroStats(true);
		}
		else if ("docear://finance/summary".equals(uri)) {
			mimeType = "application/json";
			text = McpFinanceService.getFinanceSummary("");
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
		if (cachedPrompts != null) {
			return cachedPrompts;
		}
		synchronized (McpProtocol.class) {
			if (cachedPrompts != null) {
				return cachedPrompts;
			}
			final List<JsonValue> prompts = new ArrayList<JsonValue>();
			prompts.add(prompt("daily-review", "Review today's reminders, todos and overdue items."));
			prompts.add(prompt("plan-my-day", "Plan the day based on reminders, todos and priorities."));
			prompts.add(prompt("break-down-task", "Break the selected node into actionable subtasks."));
			prompts.add(prompt("project-status", "Summarize project progress from workspace mind maps."));
			prompts.add(prompt("inbox-triage", "Triage inbox captures into projects and todos."));
			prompts.add(prompt("weekly-review", "Weekly review of completed and pending work."));
			prompts.add(prompt("focus-status",
					"Summarize what the user is focusing on now and recent pomodoro history."));
			prompts.add(prompt("finance-review",
					"Review this month's personal finance: income, expense, budgets, subscriptions and coupons."));
			cachedPrompts = prompts;
			return cachedPrompts;
		}
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
			instructions = "Read docear://context/selection. Break the selected task into 3-7 concrete subtasks. Prefer add_nodes (one batch) or create_todo when the user agrees.";
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
		else if ("focus-status".equals(name)) {
			instructions = "Read docear://pomodoro/running and docear://pomodoro/stats. Optionally call get_pomodoro_history on the active map. "
					+ "Summarize what the user is focusing on now (node text + live time), today/week totals, and recent completed sessions with timestamps.";
		}
		else if ("finance-review".equals(name)) {
			instructions = "Read docear://finance/summary. Optionally call list_finance_transactions, list_finance_budgets, "
					+ "list_finance_subscriptions and list_finance_coupons. Summarize income vs expense, top spending categories, "
					+ "budget pressure, upcoming subscriptions, and coupons nearing expiry. Offer get_finance_report with showInViewport=true if helpful.";
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
		if (ctx == null) {
			return;
		}
		final Map<String, JsonValue> map = params.asMap();
		if (!map.containsKey("clientInfo")) {
			return;
		}
		final Map<String, JsonValue> clientInfo = map.get("clientInfo").asMap();
		if (!clientInfo.containsKey("name")) {
			return;
		}
		final String name = McpAuditLabels.inferClientName(clientInfo.get("name").asString());
		final String label = name.length() > 0 ? name : clientInfo.get("name").asString();
		if (label == null || label.trim().length() == 0) {
			return;
		}
		if (ctx.getSessionId().length() > 0) {
			McpAuditService.registerClient(ctx.getSessionId(), label);
		}
		if (ctx.getPrincipal() != null) {
			McpAuditService.registerClient(ctx.getPrincipal().getId(), label);
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

	private JsonValue toolError(final String text) {
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>(toolResult(text).asMap());
		result.put("isError", JsonValue.ofBoolean(true));
		return JsonValue.ofMap(result);
	}

	private static McpPrincipal currentPrincipal() {
		final McpPrincipal principal = McpRequestContext.currentPrincipal();
		if (principal != null) {
			return principal;
		}
		if (DocearMcpConfig.isReadOnly()) {
			return McpPrincipal.anonymousRead();
		}
		return McpPrincipal.localOwner();
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

	private static String argOptional(final Map<String, JsonValue> args, final String key) {
		if (!args.containsKey(key) || args.get(key) == null || args.get(key).isNull()) {
			return null;
		}
		return args.get(key).asString();
	}

	private static Boolean argBoolOptional(final Map<String, JsonValue> args, final String key) {
		if (!args.containsKey(key) || args.get(key) == null || args.get(key).isNull()) {
			return null;
		}
		return Boolean.valueOf(args.get(key).asBoolean());
	}

	private static Integer argIntOptional(final Map<String, JsonValue> args, final String key) {
		if (!args.containsKey(key) || args.get(key) == null || args.get(key).isNull()) {
			return null;
		}
		return Integer.valueOf(args.get(key).asInt(0));
	}

	private static long argLong(final Map<String, JsonValue> args, final String key, final long defaultValue) {
		return args.containsKey(key) ? args.get(key).asLong(defaultValue) : defaultValue;
	}
}
