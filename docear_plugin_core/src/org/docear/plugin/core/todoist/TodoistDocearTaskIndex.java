package org.docear.plugin.core.todoist;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory index of Docear-linked Todoist tasks in one project.
 * Used to reuse existing tasks instead of creating duplicates, and to close extras.
 */
final class TodoistDocearTaskIndex {
	private final List tasks = new ArrayList();
	private final Map byIdentity = new HashMap();
	private final Map bySyncKey = new HashMap();

	private TodoistDocearTaskIndex() {
	}

	static TodoistDocearTaskIndex load(TodoistApiClient client, String projectId) throws IOException {
		final TodoistDocearTaskIndex index = new TodoistDocearTaskIndex();
		if (client == null || projectId == null || projectId.length() == 0) {
			return index;
		}
		final List remote = client.listDocearTasksInProject(projectId);
		for (int i = 0; i < remote.size(); i++) {
			index.add((TodoistImportTask) remote.get(i));
		}
		return index;
	}

	List allTasks() {
		return tasks;
	}

	void add(TodoistImportTask task) {
		if (task == null || task.id == null) {
			return;
		}
		tasks.add(task);
		final String syncKey = TodoistApiClient.syncKeyFromDescription(task.description);
		if (syncKey != null && syncKey.length() > 0) {
			addToListMap(bySyncKey, syncKey, task);
		}
		String identity = TodoistSyncKeys.identityKeyFromDescription(task.description);
		if (identity == null && syncKey != null) {
			identity = TodoistSyncKeys.identityKeyFromSyncKey(syncKey);
		}
		if (identity != null) {
			addToListMap(byIdentity, identity, task);
		}
	}

	void removeById(String taskId) {
		if (taskId == null) {
			return;
		}
		for (Iterator it = tasks.iterator(); it.hasNext();) {
			TodoistImportTask task = (TodoistImportTask) it.next();
			if (taskId.equals(task.id)) {
				it.remove();
			}
		}
		pruneListMap(byIdentity, taskId);
		pruneListMap(bySyncKey, taskId);
	}

	/**
	 * Prefer: store mapping → exact syncKey → identity (Map|NodeId) → null.
	 */
	String resolveExistingTaskId(String syncKey, String identityKey, String preferredFromStore) {
		if (preferredFromStore != null && preferredFromStore.length() > 0 && containsId(preferredFromStore)) {
			return preferredFromStore;
		}
		TodoistImportTask byExact = first(bySyncKey, syncKey);
		if (byExact != null) {
			return byExact.id;
		}
		TodoistImportTask byId = first(byIdentity, identityKey);
		if (byId != null) {
			return byId.id;
		}
		return null;
	}

	/**
	 * Close extras so each identity / syncKey group keeps one survivor.
	 *
	 * @return number of closed tasks
	 */
	int closeDuplicates(TodoistApiClient client, TodoistMappingStore store, Set activeIdentityKeys,
			TodoistSyncResult result, TodoistSyncProgressCallback callback) throws IOException {
		final Set closed = new HashSet();
		// Reload from Todoist so we dedupe what is actually on the calendar now
		// (covers twins created by an earlier race before this sync run indexed them).
		closed.addAll(closeGroups(client, store, byIdentity, result, callback));
		closed.addAll(closeGroups(client, store, bySyncKey, result, callback));
		return closed.size();
	}

	private Set closeGroups(TodoistApiClient client, TodoistMappingStore store, Map groups,
			TodoistSyncResult result, TodoistSyncProgressCallback callback) throws IOException {
		final Set closed = new HashSet();
		for (Iterator it = groups.entrySet().iterator(); it.hasNext();) {
			Map.Entry entry = (Map.Entry) it.next();
			final List group = (List) entry.getValue();
			if (group == null || group.size() <= 1) {
				continue;
			}
			final String keepId = pickSurvivor(group, store);
			for (int i = 0; i < group.size(); i++) {
				TodoistImportTask task = (TodoistImportTask) group.get(i);
				if (task.id.equals(keepId) || closed.contains(task.id)) {
					continue;
				}
				closeOne(client, store, task, keepId, result, callback);
				closed.add(task.id);
			}
		}
		return closed;
	}

	private void closeOne(TodoistApiClient client, TodoistMappingStore store, TodoistImportTask task,
			String keepId, TodoistSyncResult result, TodoistSyncProgressCallback callback) throws IOException {
		client.closeTask(task.id);
		if (store != null) {
			store.removeByTaskId(task.id);
		}
		removeById(task.id);
		final String line = TextUtilsSafe.formatClosedDuplicate(task, keepId);
		if (result != null) {
			result.addClosed(line);
		}
		if (callback != null) {
			callback.onClosed(org.freeplane.core.util.TextUtils.getText("todoist.sync.live.repaired") + " " + line);
		}
	}

	private String pickSurvivor(List group, TodoistMappingStore store) {
		if (store != null) {
			for (int i = 0; i < group.size(); i++) {
				TodoistImportTask task = (TodoistImportTask) group.get(i);
				final String mappedKey = store.getSyncKeyForTaskId(task.id);
				if (mappedKey != null) {
					return task.id;
				}
			}
		}
		return ((TodoistImportTask) group.get(0)).id;
	}

	private boolean containsId(String taskId) {
		for (int i = 0; i < tasks.size(); i++) {
			if (taskId.equals(((TodoistImportTask) tasks.get(i)).id)) {
				return true;
			}
		}
		return false;
	}

	private static TodoistImportTask first(Map map, String key) {
		if (key == null || map == null) {
			return null;
		}
		final List list = (List) map.get(key);
		if (list == null || list.isEmpty()) {
			return null;
		}
		return (TodoistImportTask) list.get(0);
	}

	private static void addToListMap(Map map, String key, TodoistImportTask task) {
		if (key == null || task == null) {
			return;
		}
		List list = (List) map.get(key);
		if (list == null) {
			list = new ArrayList();
			map.put(key, list);
		}
		for (int i = 0; i < list.size(); i++) {
			if (task.id.equals(((TodoistImportTask) list.get(i)).id)) {
				return;
			}
		}
		list.add(task);
	}

	private static void pruneListMap(Map map, String taskId) {
		for (Iterator it = map.values().iterator(); it.hasNext();) {
			List list = (List) it.next();
			for (Iterator lit = list.iterator(); lit.hasNext();) {
				TodoistImportTask task = (TodoistImportTask) lit.next();
				if (taskId.equals(task.id)) {
					lit.remove();
				}
			}
		}
	}

	/** Tiny helper to avoid pulling TextUtils into a tight formatting path without keys ready. */
	private static final class TextUtilsSafe {
		static String formatClosedDuplicate(TodoistImportTask task, String keepId) {
			final String content = task.content == null ? "" : task.content;
			return org.freeplane.core.util.TextUtils.format("todoist.sync.repair.closed_duplicate",
					new Object[] { content, task.id, keepId });
		}
	}
}
