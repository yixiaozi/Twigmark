package org.docear.plugin.core.todoist;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.MindMapWorkspaceContextScanner;

/**
 * Mind-map file names are treated as stable identities. When a map moves
 * (for example {@code E:\...} to the current library) and the file name is unique,
 * the Todoist task stays linked to that file instead of being closed as deleted.
 */
final class TodoistMapRelocator {
	private TodoistMapRelocator() {
	}

	static void relocateMissingReminders(final TodoistMappingStore store, final TodoistApiClient client,
			final TodoistSectionStore sectionStore, final TodoistDocearTaskIndex remoteIndex,
			final TodoistSyncResult result, final Set activeKeys, final Set activeIdentities) {
		if (store == null) {
			return;
		}
		final Map uniqueByName = indexUniqueMapNames();
		final List keys = new ArrayList(store.keySet());
		for (int i = 0; i < keys.size(); i++) {
			final String key = (String) keys.get(i);
			relocateOne(store, client, sectionStore, remoteIndex, result, activeKeys, activeIdentities,
					uniqueByName, key);
		}
	}

	private static void relocateOne(final TodoistMappingStore store, final TodoistApiClient client,
			final TodoistSectionStore sectionStore, final TodoistDocearTaskIndex remoteIndex,
			final TodoistSyncResult result, final Set activeKeys, final Set activeIdentities,
			final Map uniqueByName, final String key) {
		if (key == null || activeKeys.contains(key) || isImportTarget(key)) {
			return;
		}
		final String identity = TodoistSyncKeys.identityKeyFromSyncKey(key);
		if (identity != null && activeIdentities.contains(identity)) {
			return;
		}
		final File storedFile = fileOf(key);
		if (storedFile.isFile()) {
			return;
		}
		final File target = (File) uniqueByName.get(storedFile.getName());
		if (target == null) {
			return;
		}
		final String nodeId = nodeIdOf(key);
		final String taskId = store.getTaskIdOnly(key);
		if (taskId == null || taskId.length() == 0 || nodeId.length() == 0) {
			return;
		}
		final TodoistImportTask task = remoteIndex == null ? null : remoteIndex.findById(taskId);
		if (task == null) {
			return;
		}
		final String sectionName = TodoistApiClient.sectionNameForFile(target);
		if (TodoistSilentMmUpdater.isEncryptedRoot(target) || TodoistNodeLocator.findOpenMap(target) != null) {
			// Keep the old key until the node can actually be written, so the next sync retries.
			activeKeys.add(key);
			if (identity != null) {
				activeIdentities.add(identity);
			}
			final String reason = TodoistSilentMmUpdater.isEncryptedRoot(target)
					? "导图已加密，任务保留在 Todoist，解锁后才会出现在提醒里"
					: "导图正打开，关闭该导图后再同步才会写进节点";
			result.skipped++;
			result.skippedLines.add("[" + target.getName() + "] " + plain(task) + " — " + reason);
			LogUtils.info("Todoist: held missing map " + storedFile.getName() + " -> " + target.getPath());
			return;
		}
		final TodoistReminderRecord record = recordFor(target, nodeId, task);
		final String hash = TodoistSyncService.contentHash(record, sectionName);
		final TodoistSilentMmUpdater.Patch patch = patchFor(record, task, hash);
		final String usedId = TodoistSilentMmUpdater.restoreReminderNode(target, nodeId, patch);
		if (usedId == null) {
			result.failed++;
			result.failedLines.add("[" + target.getName() + "] " + plain(task) + " — 无法写回导图");
			return;
		}
		final String newKey = TodoistSyncKeys.syncKey(target, usedId);
		final TodoistReminderRecord written = usedId.equals(nodeId) ? record
				: recordFor(target, usedId, task);
		final String writtenHash = usedId.equals(nodeId) ? hash
				: TodoistSyncService.contentHash(written, sectionName);
		retarget(store, key, newKey, taskId, task.dueAtMillis, writtenHash);
		activeKeys.add(newKey);
		final String writtenIdentity = TodoistSyncKeys.identityKey(target, usedId);
		if (writtenIdentity != null) {
			activeIdentities.add(writtenIdentity);
		}
		MindMapWorkspaceContextScanner.invalidateFileCache(target);
		result.updated++;
		result.updatedLines.add("[" + sectionName + "] " + plain(task) + " ← " + target.getName());
		LogUtils.info("Todoist: restored reminder " + usedId + " in " + target.getPath());
		refreshRemotePath(client, sectionStore, result, target, usedId, task, sectionName);
	}

	private static void refreshRemotePath(final TodoistApiClient client, final TodoistSectionStore sectionStore,
			final TodoistSyncResult result, final File target, final String nodeId, final TodoistImportTask task,
			final String sectionName) {
		if (client == null || result.projectId == null) {
			return;
		}
		try {
			final TodoistReminderRecord record = recordFor(target, nodeId, task);
			String sectionId = null;
			if (sectionStore != null) {
				sectionId = client.ensureSection(result.projectId, sectionName, sectionStore);
			}
			if (sectionId != null && sectionId.length() > 0) {
				client.relocateTaskTo(task.id, result.projectId, sectionId);
			}
			client.updateTaskContent(task.id, record);
		}
		catch (Exception e) {
			LogUtils.warn("Todoist: could not refresh path for relocated task " + task.id, e);
		}
	}

	private static TodoistReminderRecord recordFor(final File file, final String nodeId,
			final TodoistImportTask task) {
		final TodoistCycleMapper.Cycle cycle = TodoistCycleMapper.fromTodoistDue(task.dueString, task.recurring);
		return new TodoistReminderRecord(file, nodeId, plain(task), task.dueAtMillis, cycle.interval,
				cycle.periodUnit(), cycle.recurring, task.durationMinutes,
				TodoistPriority.toJinji(task.priority, 0), cycle.remindType, cycle.weekDays);
	}

	private static TodoistSilentMmUpdater.Patch patchFor(final TodoistReminderRecord record,
			final TodoistImportTask task, final String hash) {
		final boolean cycleTrusted = !TodoistCycleMapper.isWeakRecurringFallback(task.dueString, task.recurring);
		return new TodoistSilentMmUpdater.Patch(record.nodeId, record.nodeText, record.remindAt,
				record.durationMinutes, record.jinji, task.id, hash, record.recurring, record.period,
				record.periodUnit, record.remindType, record.weekDays, cycleTrusted, false);
	}

	private static void retarget(final TodoistMappingStore store, final String oldKey, final String newKey,
			final String taskId, final long remindAt, final String hash) {
		if (!newKey.equals(oldKey)) {
			store.removeMapping(oldKey);
		}
		store.putMapping(newKey, taskId, remindAt, hash == null ? "" : hash);
	}

	/** File name → file, only when exactly one non-conflict map uses that name. */
	private static Map indexUniqueMapNames() {
		final List files = new ArrayList();
		MindMapDataRootResolver.collectMindmapFiles(files);
		final Map counts = new HashMap();
		final Map first = new HashMap();
		for (int i = 0; i < files.size(); i++) {
			final File file = (File) files.get(i);
			if (!isRelocatableMap(file)) {
				continue;
			}
			final String name = file.getName();
			final Integer count = (Integer) counts.get(name);
			counts.put(name, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
			if (!first.containsKey(name)) {
				first.put(name, file);
			}
		}
		final Map unique = new HashMap();
		for (Iterator it = counts.entrySet().iterator(); it.hasNext();) {
			final Map.Entry entry = (Map.Entry) it.next();
			if (((Integer) entry.getValue()).intValue() == 1) {
				unique.put(entry.getKey(), first.get(entry.getKey()));
			}
		}
		return unique;
	}

	private static boolean isRelocatableMap(final File file) {
		if (file == null || !file.isFile()) {
			return false;
		}
		final String name = file.getName();
		if (!name.toLowerCase().endsWith(".mm") || name.startsWith("~") || name.startsWith(".")) {
			return false;
		}
		return name.indexOf("\u51b2\u7a81\u526f\u672c") < 0;
	}

	private static boolean isImportTarget(final String syncKey) {
		final File file = fileOf(syncKey);
		return TodoistConfig.isImportTargetFile(file);
	}

	private static File fileOf(final String syncKey) {
		final int sep = syncKey == null ? -1 : syncKey.lastIndexOf('|');
		if (sep <= 0) {
			return new File("");
		}
		return new File(syncKey.substring(0, sep));
	}

	private static String nodeIdOf(final String syncKey) {
		final int sep = syncKey.lastIndexOf('|');
		if (sep < 0 || sep >= syncKey.length() - 1) {
			return "";
		}
		return syncKey.substring(sep + 1);
	}

	private static String plain(final TodoistImportTask task) {
		return TodoistContentParser.parse(task.content).nodeText;
	}
}
