package org.docear.plugin.mcp.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonValue;

/**
 * Tool ACL: {@code read} &lt; {@code write} &lt; {@code owner}.
 * Global {@code mcp.readonly} blocks mutating tools even for owner.
 */
public final class McpPermissions {
	private static final Set WRITE_TOOLS = buildWriteTools();
	private static final Set OWNER_TOOLS = buildOwnerTools();
	private static final Set OWNER_MUTATING_TOOLS = buildOwnerMutatingTools();

	private McpPermissions() {
	}

	public static boolean isWriteTool(final String name) {
		return name != null && (WRITE_TOOLS.contains(name) || OWNER_MUTATING_TOOLS.contains(name));
	}

	public static boolean isOwnerTool(final String name) {
		return name != null && OWNER_TOOLS.contains(name);
	}

	public static McpRole requiredRole(final String name) {
		if (name == null || name.length() == 0) {
			return McpRole.OWNER;
		}
		if (OWNER_TOOLS.contains(name)) {
			return McpRole.OWNER;
		}
		if (WRITE_TOOLS.contains(name)) {
			return McpRole.WRITE;
		}
		return McpRole.READ;
	}

	public static boolean canCall(final McpPrincipal principal, final String toolName) {
		final McpRole role = principal == null ? McpRole.READ : principal.getRole();
		return canCall(role, toolName);
	}

	public static boolean canCall(final McpRole role, final String toolName) {
		final McpRole required = requiredRole(toolName);
		if (DocearMcpConfig.isReadOnly() && isMutating(toolName)) {
			return false;
		}
		return role != null && role.atLeast(required);
	}

	public static boolean isMutating(final String name) {
		return isWriteTool(name);
	}

	public static List filterTools(final List tools, final McpPrincipal principal) {
		final McpRole role = principal == null ? McpRole.READ : principal.getRole();
		return filterTools(tools, role);
	}

	public static List filterTools(final List tools, final McpRole role) {
		if (tools == null || tools.isEmpty()) {
			return tools == null ? Collections.emptyList() : tools;
		}
		final List filtered = new ArrayList();
		for (int i = 0; i < tools.size(); i++) {
			final Object item = tools.get(i);
			if (!(item instanceof JsonValue)) {
				continue;
			}
			final String name = toolName((JsonValue) item);
			if (canCall(role, name)) {
				filtered.add(item);
			}
		}
		return filtered;
	}

	public static String toolName(final JsonValue tool) {
		if (tool == null || tool.isNull()) {
			return "";
		}
		final Map map = tool.asMap();
		if (map.containsKey("name") && map.get("name") != null && !((JsonValue) map.get("name")).isNull()) {
			final String name = ((JsonValue) map.get("name")).asString();
			if (name != null && name.length() > 0) {
				return name;
			}
		}
		if (map.containsKey("function") && map.get("function") != null) {
			final Map fn = ((JsonValue) map.get("function")).asMap();
			if (fn.containsKey("name") && fn.get("name") != null) {
				return ((JsonValue) fn.get("name")).asString();
			}
		}
		return "";
	}

	public static String denyMessage(final McpPrincipal principal, final String toolName) {
		final McpRole have = principal == null ? McpRole.READ : principal.getRole();
		final McpRole need = requiredRole(toolName);
		if (DocearMcpConfig.isReadOnly() && isMutating(toolName)) {
			return "Permission denied: server is in read-only mode (" + toolName + ")";
		}
		return "Permission denied: role '" + have.getName() + "' cannot call '" + toolName
				+ "' (requires '" + need.getName() + "')";
	}

	private static Set buildWriteTools() {
		final Set set = new LinkedHashSet();
		set.add("create_tag_group");
		set.add("rename_tag_group");
		set.add("move_tag_group");
		set.add("delete_tag_group");
		set.add("set_tag_group");
		set.add("set_tag_color");
		set.add("start_pomodoro");
		set.add("pause_pomodoro");
		set.add("stop_pomodoro");
		set.add("ensure_finance_map");
		set.add("add_finance_transaction");
		set.add("add_finance_category");
		set.add("add_finance_account");
		set.add("set_finance_budget");
		set.add("upsert_finance_subscription");
		set.add("upsert_finance_coupon");
		set.add("mark_finance_coupon_used");
		set.add("delete_finance_node");
		set.add("open_mindmap");
		set.add("navigate_to_node");
		set.add("add_node");
		set.add("add_nodes");
		set.add("change_node_text");
		set.add("remove_node");
		set.add("create_todo");
		set.add("complete_todo");
		set.add("set_reminder");
		set.add("set_priority");
		set.add("move_node");
		set.add("set_node_folded");
		set.add("set_node_link");
		set.add("set_node_note");
		set.add("set_node_tags");
		set.add("toggle_pin");
		set.add("set_node_icon");
		set.add("set_recurring_reminder");
		set.add("create_mindmap");
		set.add("quick_capture");
		set.add("sync_todoist");
		set.add("copy_nodes");
		set.add("cut_nodes");
		set.add("paste_nodes");
		set.add("clone_nodes");
		set.add("undo_map");
		set.add("redo_map");
		set.add("add_arrow_link");
		set.add("remove_arrow_link");
		set.add("set_node_cloud");
		set.add("set_node_style");
		set.add("set_node_details");
		set.add("set_node_privacy");
		set.add("set_node_image");
		set.add("clear_node_image");
		set.add("set_node_attachment");
		set.add("clear_reminder");
		return set;
	}

	private static Set buildOwnerTools() {
		final Set set = new LinkedHashSet();
		set.add("encrypt_node");
		set.add("decrypt_node");
		set.add("remove_node_encryption");
		set.add("export_workspace_snapshot");
		set.add("git_status");
		set.add("git_sync");
		set.add("list_audit_log");
		set.add("list_audit_traces");
		set.add("get_audit_stats");
		return set;
	}

	private static Set buildOwnerMutatingTools() {
		final Set set = new LinkedHashSet();
		set.add("encrypt_node");
		set.add("decrypt_node");
		set.add("remove_node_encryption");
		set.add("export_workspace_snapshot");
		set.add("git_sync");
		return set;
	}
}
