package org.docear.plugin.mcp.service;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.docear.plugin.mcp.json.JsonValue;
import org.freeplane.core.util.MindMapWorkspaceContextScanner;
import org.freeplane.core.util.MindMapWorkspaceContextScanner.ReminderItem;
import org.freeplane.core.util.MindMapWorkspaceContextScanner.TodoItem;
import org.freeplane.core.util.MindMapWorkspaceContextScanner.WorkspaceScanResult;
import org.freeplane.core.util.WorkspaceSideTabSnapshot;
import org.freeplane.core.util.WorkspaceSideTabSnapshot.ModifiedEntry;
import org.freeplane.core.util.WorkspaceSideTabSnapshot.PinnedEntry;
import org.freeplane.core.util.WorkspaceSideTabSnapshot.ReminderEntry;
import org.freeplane.core.util.WorkspaceSideTabSnapshot.TodoEntry;
import org.freeplane.core.util.WorkspaceSideTabSnapshotRegistry;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.text.TextController;
public final class McpContextService {

	private static final DateFormat DATE_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

	private McpContextService() {
	}

	public static String getManifestJson() {
		final Map<String, JsonValue> manifest = new LinkedHashMap<String, JsonValue>();
		manifest.put("name", JsonValue.ofString("docear-mcp"));
		manifest.put("version", JsonValue.ofString("1.0.0"));
		manifest.put("description", JsonValue.ofString("Docear mind map and workspace task MCP server"));
		manifest.put("requiresDocearRunning", JsonValue.ofBoolean(true));
		manifest.put("dataModel", JsonValue.ofMap(buildDataModel()));
		manifest.put("capabilities", JsonValue.ofMap(buildCapabilities()));
		return JsonValue.ofMap(manifest).toJson();
	}

	public static String getWorkspacePlan() {
		final WorkspaceSideTabSnapshot snapshot = WorkspaceSideTabSnapshotRegistry.getSnapshot();
		if (snapshot.hasAnyItems()) {
			return formatSnapshotPlan(snapshot, 20);
		}
		return buildQuickWorkspacePlan();
	}

	private static String formatSnapshotPlan(final WorkspaceSideTabSnapshot snapshot, final int limit) {
		final StringBuilder sb = new StringBuilder();
		sb.append("=== 工作区安排（MCP 摘要）===\n\n");
		appendSnapshotReminderSection(sb, snapshot.getOneTimeReminders(), "一次性提醒", limit);
		appendSnapshotReminderSection(sb, snapshot.getRecurringReminders(), "周期提醒", limit);
		appendSnapshotTodoSection(sb, snapshot.getTodos(), limit);
		appendSnapshotPinnedSection(sb, snapshot.getPinnedEntries(), limit);
		if (sb.length() < 60) {
			return "（当前工作区暂无待办或提醒）";
		}
		return sb.toString();
	}

	private static void appendSnapshotTodoSection(final StringBuilder sb, final List todos, final int limit) {
		sb.append("【待办】").append(todos != null ? todos.size() : 0).append(" 项\n");
		if (todos == null) {
			return;
		}
		int count = 0;
		for (final Iterator it = todos.iterator(); it.hasNext() && count < limit; count++) {
			final TodoEntry item = (TodoEntry) it.next();
			sb.append("- ").append(item.nodeText).append(" (").append(pathOf(item.mapFile)).append(")\n");
		}
		if (todos.size() > limit) {
			sb.append("... 还有 ").append(todos.size() - limit).append(" 项\n");
		}
		sb.append('\n');
	}

	private static void appendSnapshotReminderSection(final StringBuilder sb, final List reminders, final String title,
			final int limit) {
		sb.append("【").append(title).append("】").append(reminders != null ? reminders.size() : 0).append(" 项\n");
		if (reminders == null) {
			return;
		}
		int count = 0;
		for (final Iterator it = reminders.iterator(); it.hasNext() && count < limit; count++) {
			final ReminderEntry item = (ReminderEntry) it.next();
			sb.append("- ").append(DATE_TIME.format(new Date(item.remindAt))).append(" ").append(item.nodeText)
					.append(" (").append(pathOf(item.mapFile)).append(")\n");
		}
		if (reminders.size() > limit) {
			sb.append("... 还有 ").append(reminders.size() - limit).append(" 项\n");
		}
		sb.append('\n');
	}

	private static void appendSnapshotPinnedSection(final StringBuilder sb, final List pinned, final int limit) {
		sb.append("【钉选】").append(pinned != null ? pinned.size() : 0).append(" 项\n");
		if (pinned == null || pinned.isEmpty()) {
			sb.append('\n');
			return;
		}
		int count = 0;
		for (final Iterator it = pinned.iterator(); it.hasNext() && count < limit; count++) {
			final PinnedEntry item = (PinnedEntry) it.next();
			sb.append("- ").append(item.nodeText).append(" (").append(pathOf(item.mapFile)).append(")\n");
		}
		if (pinned.size() > limit) {
			sb.append("... 还有 ").append(pinned.size() - limit).append(" 项\n");
		}
		sb.append('\n');
	}

	private static String buildQuickWorkspacePlan() {
		final StringBuilder sb = new StringBuilder();
		sb.append("=== 工作区安排（MCP 快速摘要）===\n\n");
		sb.append("（侧栏缓存未加载，已使用单次磁盘扫描）\n\n");
		final WorkspaceScanResult scan = MindMapWorkspaceContextScanner.scanAll();
		appendQuickTodoSection(sb, scan.todos, 20);
		appendQuickReminderSection(sb, scan.oneTimeReminders, "一次性提醒", 20);
		appendQuickReminderSection(sb, scan.recurringReminders, "周期提醒", 20);
		if (sb.length() < 80) {
			return "（当前工作区暂无待办或提醒）";
		}
		return sb.toString();
	}

	private static void appendQuickTodoSection(final StringBuilder sb, final List todos, final int limit) {
		sb.append("【待办】").append(todos != null ? todos.size() : 0).append(" 项\n");
		if (todos == null) {
			return;
		}
		int count = 0;
		for (final Iterator it = todos.iterator(); it.hasNext() && count < limit; count++) {
			final TodoItem item = (TodoItem) it.next();
			sb.append("- ").append(item.nodeText).append(" (").append(pathOf(item.mapFile)).append(")\n");
		}
		if (todos.size() > limit) {
			sb.append("... 还有 ").append(todos.size() - limit).append(" 项\n");
		}
		sb.append('\n');
	}

	private static void appendQuickReminderSection(final StringBuilder sb, final List reminders, final String title,
			final int limit) {
		sb.append("【").append(title).append("】").append(reminders != null ? reminders.size() : 0).append(" 项\n");
		if (reminders == null) {
			return;
		}
		int count = 0;
		for (final Iterator it = reminders.iterator(); it.hasNext() && count < limit; count++) {
			final ReminderItem item = (ReminderItem) it.next();
			sb.append("- ").append(DATE_TIME.format(new Date(item.remindAt))).append(" ").append(item.nodeText)
					.append(" (").append(pathOf(item.mapFile)).append(")\n");
		}
		if (reminders.size() > limit) {
			sb.append("... 还有 ").append(reminders.size() - limit).append(" 项\n");
		}
		sb.append('\n');
	}

	public static String getSelectionContext() {
		final MapModel map = Controller.getCurrentController().getMap();
		final NodeModel selected = Controller.getCurrentController().getSelection().getSelected();
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		if (map == null) {
			result.put("message", JsonValue.ofString("No mind map is currently open."));
			return JsonValue.ofMap(result).toJson();
		}
		result.put("mapFile", JsonValue.ofString(map.getFile() != null ? map.getFile().getAbsolutePath() : ""));
		if (selected != null) {
			result.put("nodeId", JsonValue.ofString(selected.getID()));
			result.put("nodeText", JsonValue.ofString(TextController.getController().getPlainTextContent(selected)));
			result.put("hasChildren", JsonValue.ofBoolean(selected.hasChildren()));
		}
		return JsonValue.ofMap(result).toJson();
	}

	public static String getRecentContext(final int limit) {
		final WorkspaceSideTabSnapshot snapshot = WorkspaceSideTabSnapshotRegistry.getSnapshot();
		List entries = snapshot.getRecentlyModifiedEntries();
		if (entries == null || entries.isEmpty()) {
			entries = MindMapWorkspaceContextScanner.scanRecentlyModified(limit > 0 ? limit : 50);
		}
		return JsonValue.ofList(toModifiedJson(entries, limit)).toJson();
	}

	public static String getInboxContext() {
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("hint", JsonValue.ofString("Use quick_capture tool to append items to the inbox mind map."));
		return JsonValue.ofMap(result).toJson();
	}

	private static Map<String, JsonValue> buildDataModel() {
		final Map<String, JsonValue> model = new LinkedHashMap<String, JsonValue>();
		model.put("todo", JsonValue.ofString("node with hourglass icon"));
		model.put("reminder", JsonValue.ofString("ReminderExtension with REMINDUSERAT"));
		model.put("priority", JsonValue.ofString("full-1 to full-7 icons"));
		model.put("tags", JsonValue.ofString(
				"sidebar pin tags + TagGroupStore groups + favorites tags; node details tags string"));
		model.put("relationshipGraph", JsonValue.ofString("file-level .mm hyperlinks + node LINK/arrowlink edges"));
		return model;
	}

	private static Map<String, JsonValue> buildCapabilities() {
		final Map<String, JsonValue> caps = new LinkedHashMap<String, JsonValue>();
		caps.put("mindmap", JsonValue.ofList(stringList(new String[] { "read", "write", "batch_write", "search", "navigate" })));
		caps.put("tasks", JsonValue.ofList(stringList(new String[] { "list", "create", "complete", "prioritize" })));
		caps.put("reminders", JsonValue.ofList(stringList(new String[] { "list", "set", "timeline", "overdue" })));
		caps.put("workspace", JsonValue.ofList(stringList(new String[] { "projects", "plan", "snapshot", "inbox" })));
		caps.put("graph", JsonValue.ofList(stringList(new String[] { "map_files", "map_nodes", "tags", "favorites", "neighbors", "search" })));
		caps.put("tags", JsonValue.ofList(stringList(new String[] { "groups", "list", "nodes_by_tag", "favorites",
				"catalog", "set_group", "set_color" })));
		caps.put("integrations", JsonValue.ofList(stringList(new String[] { "todoist" })));
		return caps;
	}

	private static List<JsonValue> stringList(final String[] values) {
		final List<JsonValue> list = new ArrayList<JsonValue>();
		for (int i = 0; i < values.length; i++) {
			list.add(JsonValue.ofString(values[i]));
		}
		return list;
	}

	private static List<JsonValue> toModifiedJson(final List entries, final int limit) {
		final List<JsonValue> list = new ArrayList<JsonValue>();
		int count = 0;
		for (final Iterator it = entries.iterator(); it.hasNext();) {
			if (limit > 0 && count >= limit) {
				break;
			}
			final Object entry = it.next();
			final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
			if (entry instanceof ModifiedEntry) {
				final ModifiedEntry modified = (ModifiedEntry) entry;
				item.put("mapFile", JsonValue.ofString(pathOf(modified.mapFile)));
				item.put("nodeId", JsonValue.ofString(modified.nodeId));
				item.put("nodeText", JsonValue.ofString(modified.nodeText));
				item.put("modifiedAt", JsonValue.ofString(DATE_TIME.format(new Date(modified.modifiedAt))));
			}
			else {
				final org.freeplane.core.util.MindMapWorkspaceContextScanner.ModifiedItem modified =
						(org.freeplane.core.util.MindMapWorkspaceContextScanner.ModifiedItem) entry;
				item.put("mapFile", JsonValue.ofString(pathOf(modified.mapFile)));
				item.put("nodeId", JsonValue.ofString(modified.nodeId));
				item.put("nodeText", JsonValue.ofString(modified.nodeText));
				item.put("modifiedAt", JsonValue.ofString(DATE_TIME.format(new Date(modified.modifiedAt))));
			}
			list.add(JsonValue.ofMap(item));
			count++;
		}
		return list;
	}

	static List<JsonValue> remindersToJson(final List reminders) {
		final List<JsonValue> list = new ArrayList<JsonValue>();
		for (final Iterator it = reminders.iterator(); it.hasNext();) {
			final Object entry = it.next();
			final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
			if (entry instanceof ReminderEntry) {
				final ReminderEntry reminder = (ReminderEntry) entry;
				fillReminder(item, reminder.mapFile, reminder.nodeId, reminder.nodeText, reminder.remindAt,
						reminder.recurring, reminder.remindType);
			}
			else {
				final ReminderItem reminder = (ReminderItem) entry;
				fillReminder(item, reminder.mapFile, reminder.nodeId, reminder.nodeText, reminder.remindAt,
						reminder.recurring, reminder.remindType);
			}
			list.add(JsonValue.ofMap(item));
		}
		return list;
	}

	static List<JsonValue> todosToJson(final List todos) {
		final List<JsonValue> list = new ArrayList<JsonValue>();
		for (final Iterator it = todos.iterator(); it.hasNext();) {
			final Object entry = it.next();
			final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
			if (entry instanceof TodoEntry) {
				final TodoEntry todo = (TodoEntry) entry;
				fillTodo(item, todo.mapFile, todo.nodeId, todo.nodeText);
			}
			else {
				final TodoItem todo = (TodoItem) entry;
				fillTodo(item, todo.mapFile, todo.nodeId, todo.nodeText);
			}
			list.add(JsonValue.ofMap(item));
		}
		return list;
	}

	static List<JsonValue> pinnedToJson(final List pinned) {
		final List<JsonValue> list = new ArrayList<JsonValue>();
		for (final Iterator it = pinned.iterator(); it.hasNext();) {
			final PinnedEntry entry = (PinnedEntry) it.next();
			final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
			item.put("mapFile", JsonValue.ofString(pathOf(entry.mapFile)));
			item.put("nodeId", JsonValue.ofString(entry.nodeId));
			item.put("nodeText", JsonValue.ofString(entry.nodeText));
			item.put("tags", JsonValue.ofString(entry.tags));
			list.add(JsonValue.ofMap(item));
		}
		return list;
	}

	private static void fillReminder(final Map<String, JsonValue> item, final File mapFile, final String nodeId,
			final String nodeText, final long remindAt, final boolean recurring, final String remindType) {
		item.put("mapFile", JsonValue.ofString(pathOf(mapFile)));
		item.put("nodeId", JsonValue.ofString(nodeId));
		item.put("nodeText", JsonValue.ofString(nodeText));
		item.put("remindAt", JsonValue.ofString(DATE_TIME.format(new Date(remindAt))));
		item.put("remindAtMillis", JsonValue.ofNumber(Long.valueOf(remindAt)));
		item.put("recurring", JsonValue.ofBoolean(recurring));
		item.put("remindType", JsonValue.ofString(remindType != null ? remindType : ""));
	}

	private static void fillTodo(final Map<String, JsonValue> item, final File mapFile, final String nodeId,
			final String nodeText) {
		item.put("mapFile", JsonValue.ofString(pathOf(mapFile)));
		item.put("nodeId", JsonValue.ofString(nodeId));
		item.put("nodeText", JsonValue.ofString(nodeText));
	}

	static String pathOf(final File file) {
		return file != null ? file.getAbsolutePath() : "";
	}

	public static long startOfToday() {
		final Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTimeInMillis();
	}

	public static long endOfToday() {
		return startOfToday() + 24L * 60L * 60L * 1000L;
	}

	public static String getTodayTimelineJson() {
		final long dayStart = startOfToday();
		final long dayEnd = endOfToday();
		final List reminders = MindMapWorkspaceContextScanner.scanAllReminders();
		final List<JsonValue> list = new ArrayList<JsonValue>();
		for (final Iterator it = reminders.iterator(); it.hasNext();) {
			final ReminderItem reminder = (ReminderItem) it.next();
			if (!reminder.recurring && (reminder.remindAt < dayStart || reminder.remindAt >= dayEnd)) {
				continue;
			}
			final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
			item.put("nodeText", JsonValue.ofString(reminder.nodeText));
			item.put("mapFile", JsonValue.ofString(pathOf(reminder.mapFile)));
			item.put("nodeId", JsonValue.ofString(reminder.nodeId));
			item.put("remindAt", JsonValue.ofString(DATE_TIME.format(new Date(reminder.remindAt))));
			item.put("recurring", JsonValue.ofBoolean(reminder.recurring));
			list.add(JsonValue.ofMap(item));
		}
		return JsonValue.ofList(list).toJson();
	}
}
