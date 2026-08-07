package org.docear.plugin.mcp.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.docear.plugin.mcp.json.JsonValue;
import org.freeplane.core.util.MindMapWorkspaceContextScanner;
import org.freeplane.core.util.MindMapWorkspaceContextScanner.ReminderItem;
import org.freeplane.core.util.MindMapWorkspaceContextScanner.WorkspaceScanResult;
import org.freeplane.core.util.WorkspaceSideTabSnapshot;
import org.freeplane.core.util.WorkspaceSideTabSnapshot.ReminderEntry;
import org.freeplane.core.util.WorkspaceSideTabSnapshotRegistry;

public final class McpTaskService {

	private McpTaskService() {
	}

	public static String listTodos() {
		final WorkspaceSideTabSnapshot snapshot = WorkspaceSideTabSnapshotRegistry.getSnapshot();
		List todos = snapshot.getTodos();
		if (todos == null || todos.isEmpty()) {
			todos = MindMapWorkspaceContextScanner.scanAllTodos();
		}
		return JsonValue.ofList(McpContextService.todosToJson(todos)).toJson();
	}

	public static String listReminders(final boolean oneTimeOnly, final boolean recurringOnly) {
		final WorkspaceSideTabSnapshot snapshot = WorkspaceSideTabSnapshotRegistry.getSnapshot();
		List reminders;
		if (oneTimeOnly) {
			reminders = snapshot.getOneTimeReminders();
			if (reminders == null || reminders.isEmpty()) {
				reminders = MindMapWorkspaceContextScanner.scanOneTimeReminders();
			}
		}
		else if (recurringOnly) {
			reminders = snapshot.getRecurringReminders();
			if (reminders == null || reminders.isEmpty()) {
				reminders = MindMapWorkspaceContextScanner.scanRecurringReminders();
			}
		}
		else {
			reminders = mergeReminderSnapshots(snapshot);
			if (reminders.isEmpty()) {
				final WorkspaceScanResult result = MindMapWorkspaceContextScanner.scanAll();
				reminders = new ArrayList();
				reminders.addAll(result.oneTimeReminders);
				reminders.addAll(result.recurringReminders);
			}
		}
		return JsonValue.ofList(McpContextService.remindersToJson(reminders)).toJson();
	}

	public static String listOverdue() {
		final long now = System.currentTimeMillis();
		final WorkspaceSideTabSnapshot snapshot = WorkspaceSideTabSnapshotRegistry.getSnapshot();
		List source = snapshot.getOneTimeReminders();
		if (source == null || source.isEmpty()) {
			source = MindMapWorkspaceContextScanner.scanAllReminders();
			final List overdue = new ArrayList();
			for (final Iterator it = source.iterator(); it.hasNext();) {
				final ReminderItem item = (ReminderItem) it.next();
				if (!item.recurring && item.remindAt < now) {
					overdue.add(item);
				}
			}
			return JsonValue.ofList(McpContextService.remindersToJson(overdue)).toJson();
		}
		final List overdue = new ArrayList();
		for (final Iterator it = source.iterator(); it.hasNext();) {
			final Object raw = it.next();
			if (raw instanceof ReminderEntry) {
				final ReminderEntry item = (ReminderEntry) raw;
				if (!item.recurring && item.remindAt < now) {
					overdue.add(item);
				}
			}
			else if (raw instanceof ReminderItem) {
				final ReminderItem item = (ReminderItem) raw;
				if (!item.recurring && item.remindAt < now) {
					overdue.add(item);
				}
			}
		}
		return JsonValue.ofList(McpContextService.remindersToJson(overdue)).toJson();
	}

	private static List mergeReminderSnapshots(final WorkspaceSideTabSnapshot snapshot) {
		final List reminders = new ArrayList();
		final List oneTime = snapshot.getOneTimeReminders();
		final List recurring = snapshot.getRecurringReminders();
		if (oneTime != null) {
			reminders.addAll(oneTime);
		}
		if (recurring != null) {
			reminders.addAll(recurring);
		}
		return reminders;
	}
}
