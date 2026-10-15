package org.docear.plugin.core.todoist;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderHook;

public final class TodoistSyncService {
	private TodoistSyncService() {
	}

	public static TodoistSyncResult syncAllReminders() {
		return syncAllReminders(null);
	}

	public static TodoistSyncResult syncAllReminders(TodoistSyncProgressCallback callback) {
		TodoistSyncGuard.enter();
		try {
			return syncAllRemindersBody(callback);
		}
		finally {
			TodoistSyncGuard.leave();
		}
	}

	private static TodoistSyncResult syncAllRemindersBody(TodoistSyncProgressCallback callback) {
		final TodoistSyncResult result = new TodoistSyncResult();
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			result.failed = 1;
			result.errorMessage = "Todoist API token is not configured.";
			result.failedLines.add(result.errorMessage);
			notifyFailed(callback, result.errorMessage);
			finish(callback, result);
			return result;
		}
		status(callback, TextUtils.getText("todoist.sync.status.connecting"));
		final TodoistApiClient client = new TodoistApiClient(token.trim());
		final TodoistMappingStore store = TodoistMappingStore.get();
		final TodoistSectionStore sectionStore = new TodoistSectionStore();
		final String projectName = TodoistConfig.getProjectName();
		result.projectName = projectName;
		try {
			status(callback, TextUtils.format("todoist.sync.status.project", new Object[] { projectName }));
			result.projectId = client.ensureProject(projectName);
		}
		catch (Exception e) {
			result.failed = 1;
			result.errorMessage = "Could not access Todoist project: " + e.getMessage();
			result.failedLines.add(result.errorMessage);
			notifyFailed(callback, result.errorMessage);
			LogUtils.warn("Todoist project setup failed", e);
			finish(callback, result);
			return result;
		}
		status(callback, TextUtils.getText("todoist.sync.status.scanning"));
		final MindMapReminderScanner reminderScanner = new MindMapReminderScanner();
		final List reminders = reminderScanner.scanAllReminders();
		result.totalScanned = reminders.size();
		status(callback, TextUtils.format("todoist.sync.status.found", new Object[] { Integer.valueOf(reminders.size()) }));
		TodoistDocearTaskIndex remoteIndex;
		try {
			status(callback, TextUtils.getText("todoist.sync.status.index"));
			remoteIndex = TodoistDocearTaskIndex.load(client, result.projectId);
		}
		catch (Exception e) {
			result.failed = 1;
			result.errorMessage = "Could not index Todoist tasks: " + e.getMessage();
			result.failedLines.add(result.errorMessage);
			notifyFailed(callback, result.errorMessage);
			LogUtils.warn("Todoist remote index failed", e);
			finish(callback, result);
			return result;
		}
		final Set activeKeys = new HashSet();
		final Set activeIdentities = new HashSet();
		final int total = reminders.size();
		for (int i = 0; i < reminders.size(); i++) {
			TodoistReminderRecord record = (TodoistReminderRecord) reminders.get(i);
			String key = record.syncKey();
			refreshFromOpenNode(record, key);
			String identity = record.identityKey();
			activeKeys.add(key);
			if (identity != null) {
				activeIdentities.add(identity);
			}
			String sectionName = TodoistApiClient.sectionNameForFile(record.file);
			String hash = contentHash(record, sectionName);
			String taskId = store.getTaskIdOnly(key);
			if (taskId == null || taskId.length() == 0) {
				taskId = store.findTaskIdByIdentity(identity);
			}
			if ((taskId == null || taskId.length() == 0) && record.linkedTaskId != null
					&& record.linkedTaskId.length() > 0) {
				taskId = record.linkedTaskId;
			}
			final String mappedTaskId = taskId;
			taskId = remoteIndex.resolveExistingTaskId(key, identity, taskId);
			String storedHash = storedHashFor(store, key, taskId);
			String line = TodoistSyncResult.formatLine(sectionName, record);
			if (mappedTaskId != null && mappedTaskId.length() > 0
					&& (taskId == null || !mappedTaskId.equals(taskId))) {
				try {
					final TodoistTaskLocation linkedLocation = client.getTaskLocation(mappedTaskId);
					if (record.userPlaced || isImportedMirror(TodoistNodeLocator.findOpenNodeBySyncKey(key), linkedLocation,
							result.projectId)) {
						applyClock(client, record, mappedTaskId, linkedLocation, hash, store, key, sectionName, line,
								result, callback);
						continue;
					}
					if (linkedLocation.exists && (taskId == null || taskId.length() == 0)) {
						taskId = mappedTaskId;
					}
				}
				catch (Exception e) {
					LogUtils.warn("Todoist: could not classify linked task " + mappedTaskId, e);
				}
			}
			progress(callback, i + 1, total);
			status(callback, TextUtils.format("todoist.sync.status.item", new Object[] { sectionName, record.nodeText }));
			try {
				String sectionId = client.ensureSection(result.projectId, sectionName, sectionStore);
				if (taskId != null && taskId.length() > 0) {
					TodoistTaskLocation location = client.getTaskLocation(taskId);
					if (location.exists) {
						if (record.userPlaced || isImportedMirror(TodoistNodeLocator.findOpenNodeBySyncKey(key), location,
								result.projectId)) {
							applyClock(client, record, taskId, location, hash, store, key, sectionName, line, result,
									callback);
							continue;
						}
						final int direction = TodoistSyncClock.choose(record.localModified, location.updatedAtMillis,
								record.syncedLocal, record.syncedRemote);
						if (direction == TodoistSyncClock.PULL) {
							pullRemoteOntoNode(client, TodoistNodeLocator.findOpenNodeBySyncKey(key), taskId, location);
							TodoistReminderFactory.stampWatermarks(TodoistNodeLocator.findOpenNodeBySyncKey(key),
									location.updatedAtMillis);
							store.putMapping(key, taskId, record.remindAt, hash);
							result.addUpdated(record, sectionName);
							if (callback != null) {
								callback.onUpdated(line);
							}
							continue;
						}
						if (direction == TodoistSyncClock.SKIP) {
							TodoistReminderFactory.stampWatermarks(TodoistNodeLocator.findOpenNodeBySyncKey(key),
									location.updatedAtMillis);
							store.putMapping(key, taskId, record.remindAt, hash);
							stampLiveNodeIfOpen(key, taskId, hash);
							result.addSkipped(record, sectionName);
							if (callback != null) {
								callback.onSkipped(line);
							}
							continue;
						}
					}
					if (location.exists && location.completed && hash.equals(storedHash)) {
						// Completed in Todoist and the mind map has not changed: leave it completed.
						store.putMapping(key, taskId, record.remindAt, hash);
						stampLiveNodeIfOpen(key, taskId, hash);
						result.addSkipped(record, sectionName);
						if (callback != null) {
							callback.onSkipped(line);
						}
						continue;
					}
					boolean reopened = false;
					if (location.exists && location.completed) {
						client.reopenTask(taskId);
						reopened = true;
						location = client.getTaskLocation(taskId);
					}
					boolean needsRelocate = !client.isTaskInLocation(location, result.projectId, sectionId);
					if (hash.equals(storedHash) && location.exists && !reopened && !needsRelocate) {
						store.putMapping(key, taskId, record.remindAt, hash);
						stampLiveNodeIfOpen(key, taskId, hash);
						result.addSkipped(record, sectionName);
						if (callback != null) {
							callback.onSkipped(line);
						}
					}
					else if (location.exists) {
						boolean needsContentUpdate = !hash.equals(storedHash) || reopened;
						if (needsRelocate) {
							client.relocateTaskTo(taskId, result.projectId, sectionId);
						}
						boolean wrote = false;
						if (needsContentUpdate) {
							wrote = client.updateTaskContent(taskId, record);
						}
						store.putMapping(key, taskId, record.remindAt, hash);
						stampLiveNodeIfOpen(key, taskId, hash);
						rememberRemoteClock(key, taskId, client, location);
						if (needsRelocate) {
							result.addMoved(record, sectionName);
							if (callback != null) {
								callback.onUpdated(TextUtils.getText("todoist.sync.live.moved") + " " + line);
							}
						}
						else if (wrote) {
							result.addUpdated(record, sectionName);
							if (callback != null) {
								callback.onUpdated(line);
							}
						}
						else {
							result.addSkipped(record, sectionName);
							if (callback != null) {
								callback.onSkipped(line);
							}
						}
					}
					else {
						// Mapped id gone — drop it from index then reuse a twin or create once.
						remoteIndex.removeById(taskId);
						String reused = remoteIndex.resolveExistingTaskId(key, identity, null);
						if (reused != null) {
							taskId = reused;
							client.ensureTaskActive(taskId);
							client.updateTaskContent(taskId, record);
							client.relocateTaskTo(taskId, result.projectId, sectionId);
							store.putMapping(key, taskId, record.remindAt, hash);
							stampLiveNodeIfOpen(key, taskId, hash);
							rememberRemoteClock(key, taskId, client, location);
							result.addUpdated(record, sectionName);
							if (callback != null) {
								callback.onUpdated(line);
							}
						}
						else {
							taskId = createAndIndex(client, remoteIndex, record, result.projectId, sectionId);
							store.putMapping(key, taskId, record.remindAt, hash);
							stampLiveNodeIfOpen(key, taskId, hash);
							markOpenOrigin(key, TodoistReminderFactory.ORIGIN_DOCEAR);
							rememberRemoteClock(key, taskId, client, location);
							result.addCreated(record, sectionName);
							if (callback != null) {
								callback.onCreated(line);
							}
						}
					}
				}
				else {
					taskId = createAndIndex(client, remoteIndex, record, result.projectId, sectionId);
					store.putMapping(key, taskId, record.remindAt, hash);
					stampLiveNodeIfOpen(key, taskId, hash);
					markOpenOrigin(key, TodoistReminderFactory.ORIGIN_DOCEAR);
					rememberRemoteClock(key, taskId, client, null);
					result.addCreated(record, sectionName);
					if (callback != null) {
						callback.onCreated(line);
					}
				}
			}
			catch (Exception e) {
				String failedLine = line + " — " + e.getMessage();
				result.addFailed(record, sectionName, e.getMessage());
				if (callback != null) {
					callback.onFailed(failedLine);
				}
				LogUtils.warn("Todoist sync failed for " + key, e);
			}
		}
		cleanupMisplacedTasks(client, result, store, callback, activeKeys, activeIdentities,
				reminderScanner.unreadableMapNames(), reminderScanner);
		status(callback, TextUtils.getText("todoist.sync.status.cleanup"));
		for (Iterator it = new java.util.ArrayList(store.keySet()).iterator(); it.hasNext();) {
			String key = (String) it.next();
			if (activeKeys.contains(key)) {
				continue;
			}
			final String identity = TodoistSyncKeys.identityKeyFromSyncKey(key);
			final String mapName = TodoistSyncKeys.mapNameFromSyncKey(key);
			if (mapName != null && reminderScanner.unreadableMapNames().contains(mapName)) {
				continue;
			}
			if (identity != null && activeIdentities.contains(identity)) {
				// Stale absolute-path alias for an active reminder — drop mapping only.
				store.removeMapping(key);
				continue;
			}
			String taskId = store.getTaskIdOnly(key);
			final String currentKey = taskId == null ? null : store.getSyncKeyForTaskId(taskId);
			if (taskId != null && currentKey != null && activeKeys.contains(currentKey)) {
				store.removeMapping(key);
				continue;
			}
			if (taskId != null && taskId.length() > 0) {
				try {
					final TodoistTaskLocation location = client.getTaskLocation(taskId);
					if (!location.exists) {
						// Already deleted in Todoist. Drop the stale id; do not create a replacement.
					}
					else if (isImportedMirror(null, location, result.projectId)) {
						continue;
					}
					else if (stillLinked(reminderScanner, taskId, location.description)) {
						continue;
					}
					else {
						client.closeTask(taskId);
					}
					result.addClosed(key);
					if (callback != null) {
						callback.onClosed(key);
					}
				}
				catch (Exception e) {
					result.failed++;
					String failedLine = "Close " + taskId + ": " + e.getMessage();
					result.failedLines.add(failedLine);
					if (callback != null) {
						callback.onFailed(failedLine);
					}
					LogUtils.warn("Todoist close failed for " + key, e);
					continue;
				}
			}
			store.removeMapping(key);
		}
		store.save();
		sectionStore.save();
		repairOpenNodeLinks(client, result.projectId, reminderScanner);
		finish(callback, result);
		return result;
	}

	/** Node id or task id is still stored on a mind-map node, so the task is not an orphan. */
	private static boolean stillLinked(final MindMapReminderScanner scanner, final String taskId,
			final String description) {
		if (scanner == null) {
			return false;
		}
		if (taskId != null && scanner.linkedTaskIds().contains(taskId)) {
			return true;
		}
		final String nodeId = TodoistSyncKeys.nodeIdFromDescription(description);
		return nodeId != null && scanner.linkedNodeIds().contains(nodeId);
	}

	private static String createAndIndex(TodoistApiClient client, TodoistDocearTaskIndex remoteIndex,
			TodoistReminderRecord record, String projectId, String sectionId) throws IOException {
		final String taskId = client.createTask(record, projectId, sectionId);
		if (remoteIndex != null) {
			remoteIndex.add(new TodoistImportTask(taskId, record.nodeText, TodoistApiClient.buildDescription(record),
					projectId, sectionId, record.remindAt, record.recurring, null,
					TodoistPriority.toTodoistApi(record.jinji), record.durationMinutes));
		}
		return taskId;
	}

	private static void cleanupMisplacedTasks(TodoistApiClient client, TodoistSyncResult result,
			TodoistMappingStore store, TodoistSyncProgressCallback callback, Set activeKeys, Set activeIdentities,
			Set unreadableMapNames, MindMapReminderScanner reminderScanner) {
		status(callback, TextUtils.getText("todoist.sync.status.repair"));
		try {
			status(callback, TextUtils.getText("todoist.sync.status.dedupe"));
			final TodoistDocearTaskIndex index = TodoistDocearTaskIndex.load(client, result.projectId);
			index.closeDuplicates(client, store, activeIdentities, result, callback);
			closeTasksAbsentFromMindMaps(client, result.projectId, result, store, callback, activeIdentities,
					unreadableMapNames, reminderScanner);
		}
		catch (Exception e) {
			result.failed++;
			String failedLine = TextUtils.getText("todoist.sync.repair.failed") + ": " + e.getMessage();
			result.failedLines.add(failedLine);
			if (callback != null) {
				callback.onFailed(failedLine);
			}
			LogUtils.warn("Todoist repair pass failed", e);
		}
	}

	/**
	 * Mind-map nodes are the source of truth. Complete a task in this project when its node id
	 * is no longer on a map. Tasks are closed, not deleted. Other Todoist projects are left alone.
	 */
	private static void closeTasksAbsentFromMindMaps(TodoistApiClient client, String projectId,
			TodoistSyncResult result, TodoistMappingStore store, TodoistSyncProgressCallback callback,
			Set activeIdentities, Set unreadableMapNames, MindMapReminderScanner scanner) throws IOException {
		final List tasks = client.fetchActiveTasksInProject(projectId);
		for (int t = 0; t < tasks.size(); t++) {
			TodoistImportTask task = (TodoistImportTask) tasks.get(t);
			String identity = TodoistSyncKeys.identityKeyFromDescription(task.description);
			if (identity != null && activeIdentities != null && activeIdentities.contains(identity)) {
				continue;
			}
			if (stillLinked(scanner, task.id, task.description)) {
				continue;
			}
			final String mapHint = TodoistSyncKeys.extractLinePrefix(task.description == null ? "" : task.description,
					"Map: ");
			if (mapHint != null && unreadableMapNames != null && unreadableMapNames.contains(mapHint)) {
				continue;
			}
			try {
				client.closeTask(task.id);
				if (store != null) {
					store.removeByTaskId(task.id);
				}
				final String line = (identity == null ? task.content : identity);
				result.addClosed(line);
				if (callback != null) {
					callback.onClosed(line);
				}
			}
			catch (Exception e) {
				result.failed++;
				final String failedLine = "Close " + task.id + ": " + e.getMessage();
				result.failedLines.add(failedLine);
				if (callback != null) {
					callback.onFailed(failedLine);
				}
				LogUtils.warn("Todoist close failed for " + task.id, e);
			}
		}
	}

	private static void closeOrphansInWrongProjects(TodoistApiClient client, TodoistSyncResult result,
			TodoistSyncProgressCallback callback, Set mappedTaskIds) throws IOException {
		final List projectIds = client.findAllProjectIdsByName(result.projectName);
		for (int p = 0; p < projectIds.size(); p++) {
			String projectId = (String) projectIds.get(p);
			if (result.projectId.equals(projectId)) {
				continue;
			}
			closeOrphanTasks(client, projectId, result.projectName, mappedTaskIds, result, callback, false, null);
		}
	}

	private static void closeOrphanTasks(TodoistApiClient client, String projectId, String projectName,
			Set mappedTaskIds, TodoistSyncResult result, TodoistSyncProgressCallback callback,
			boolean onlyActiveDuplicates, Set activeKeys) throws IOException {
		final List orphanTasks = client.listDocearTasksInProject(projectId);
		for (int t = 0; t < orphanTasks.size(); t++) {
			TodoistImportTask orphan = (TodoistImportTask) orphanTasks.get(t);
			final String orphanTaskId = orphan.id;
			if (mappedTaskIds.contains(orphanTaskId)) {
				continue;
			}
			if (onlyActiveDuplicates) {
				String syncKey = TodoistApiClient.syncKeyFromDescription(orphan.description);
				String identity = TodoistSyncKeys.identityKeyFromDescription(orphan.description);
				boolean matchesActive = syncKey != null && activeKeys != null && activeKeys.contains(syncKey);
				if (!matchesActive && identity != null && activeKeys != null) {
					for (Iterator it = activeKeys.iterator(); it.hasNext();) {
						String active = (String) it.next();
						if (identity.equals(TodoistSyncKeys.identityKeyFromSyncKey(active))) {
							matchesActive = true;
							break;
						}
					}
				}
				if (!matchesActive) {
					continue;
				}
			}
			try {
				client.closeTask(orphanTaskId);
				String line = TextUtils.format("todoist.sync.repair.closed_orphan",
						new Object[] { projectName, orphanTaskId });
				result.addClosed(line);
				if (callback != null) {
					callback.onClosed(TextUtils.getText("todoist.sync.live.repaired") + " " + line);
				}
			}
			catch (Exception e) {
				result.failed++;
				String failedLine = "Close orphan " + orphanTaskId + ": " + e.getMessage();
				result.failedLines.add(failedLine);
				if (callback != null) {
					callback.onFailed(failedLine);
				}
				LogUtils.warn("Todoist orphan cleanup failed for " + orphanTaskId, e);
			}
		}
	}

	private static void status(TodoistSyncProgressCallback callback, String message) {
		if (callback != null) {
			callback.onStatus(message);
		}
	}

	private static void progress(TodoistSyncProgressCallback callback, int current, int total) {
		if (callback != null) {
			callback.onProgress(current, total);
		}
	}

	private static void notifyFailed(TodoistSyncProgressCallback callback, String message) {
		if (callback != null) {
			callback.onFailed(message);
		}
	}

	private static void finish(TodoistSyncProgressCallback callback, TodoistSyncResult result) {
		if (callback != null) {
			callback.onFinished(result);
		}
	}

	/** Shared content fingerprint for create/update skip checks and live auto-sync. */
	static String contentHash(TodoistReminderRecord record, String sectionName) {
		StringBuilder sb = new StringBuilder();
		sb.append(sectionName).append('|');
		sb.append(record.nodeText).append('|');
		sb.append(record.remindAt).append('|');
		sb.append(record.recurring).append('|');
		sb.append(record.period).append('|');
		sb.append(record.periodUnit).append('|');
		sb.append(record.remindType).append('|');
		sb.append(record.weekDays).append('|');
		sb.append(record.durationMinutes).append('|');
		sb.append(TodoistPriority.toTodoistApi(record.jinji));
		return Integer.toString(sb.toString().hashCode());
	}

	/** Hash stored on this path, or on the older path that still owns the same task id. */
	private static String storedHashFor(final TodoistMappingStore store, final String key, final String taskId) {
		String storedHash = store.getStoredContentHash(key);
		if (storedHash != null && storedHash.length() > 0) {
			return storedHash;
		}
		if (taskId == null || taskId.length() == 0) {
			return storedHash;
		}
		final String mappedKey = store.getSyncKeyForTaskId(taskId);
		if (mappedKey == null) {
			return storedHash;
		}
		return store.getStoredContentHash(mappedKey);
	}

	/** Open-map text and clocks replace the last saved file, so a rename is not pushed as the old title. */
	private static void refreshFromOpenNode(final TodoistReminderRecord record, final String key) {
		final NodeModel open = TodoistNodeLocator.findOpenNodeBySyncKey(key);
		if (open == null || record == null) {
			return;
		}
		TodoistReminderFactory.copyClock(open, record);
		final String live = TodoistReminderFactory.plainText(open);
		if (live.length() > 0) {
			record.nodeText = live;
		}
	}

	private static final Set repairedNodeLinks = new HashSet();

	/**
	 * Mind-map tasks keep a node id in the description. Original Todoist tasks stay in their own
	 * project, and a stamp that was written onto them by mistake is removed.
	 */
	private static void repairOpenNodeLinks(final TodoistApiClient client, final String docearProjectId,
			final MindMapReminderScanner scanner) {
		final List nodes = TodoistNodeLocator.openLinkedNodes();
		for (int i = 0; i < nodes.size(); i++) {
			final NodeModel node = (NodeModel) nodes.get(i);
			repairOneLink(client, docearProjectId, TodoistReminderFactory.getTaskId(node), node);
		}
		if (scanner == null) {
			return;
		}
		for (Iterator it = scanner.importedTaskIds().iterator(); it.hasNext();) {
			repairOneLink(client, docearProjectId, (String) it.next(), null);
		}
	}

	private static void repairOneLink(final TodoistApiClient client, final String docearProjectId, final String taskId,
			final NodeModel node) {
		if (taskId == null || taskId.length() == 0 || repairedNodeLinks.contains(taskId)) {
			return;
		}
		try {
			final TodoistTaskLocation location = client.getTaskLocation(taskId);
			repairedNodeLinks.add(taskId);
			if (!location.exists) {
				return;
			}
			final boolean imported = isImportedMirror(node, location, docearProjectId);
			if (imported) {
				if (node != null) {
					TodoistReminderFactory.setOrigin(node, TodoistReminderFactory.ORIGIN_TODOIST);
				}
				if (!client.clearOwnedStamp(taskId, location.description)) {
					return;
				}
			}
			else if (node != null) {
				if (!TodoistReminderFactory.isTodoistOrigin(node)) {
					TodoistReminderFactory.setOrigin(node, TodoistReminderFactory.ORIGIN_DOCEAR);
				}
				if (!client.refreshNodeLink(taskId, node.getID(), location.description)) {
					return;
				}
			}
			else {
				return;
			}
			if (node == null) {
				return;
			}
			final TodoistTaskLocation after = client.getTaskLocation(taskId);
			TodoistReminderFactory.stampWatermarks(node,
					after.updatedAtMillis > 0 ? after.updatedAtMillis : location.updatedAtMillis);
		}
		catch (Exception e) {
			LogUtils.warn("Todoist: could not store node id on task " + taskId, e);
		}
	}

	private static void applyClock(final TodoistApiClient client, final TodoistReminderRecord record,
			final String taskId, final TodoistTaskLocation location, final String hash, final TodoistMappingStore store,
			final String key, final String sectionName, final String line, final TodoistSyncResult result,
			final TodoistSyncProgressCallback callback) throws Exception {
		final int direction = TodoistSyncClock.choose(record.localModified, location.updatedAtMillis, record.syncedLocal,
				record.syncedRemote);
		final NodeModel open = TodoistNodeLocator.findOpenNodeBySyncKey(key);
		if (direction == TodoistSyncClock.PUSH) {
			if (location.completed) {
				client.reopenTask(taskId);
			}
			client.updateTaskContent(taskId, record, true);
			TodoistReminderFactory.markLinkedNode(open, location.projectId, location.sectionId);
			rememberRemoteClock(key, taskId, client, location);
			store.putMapping(key, taskId, record.remindAt, hash);
			stampLiveNodeIfOpen(key, taskId, hash);
			result.addUpdated(record, sectionName);
			if (callback != null) {
				callback.onUpdated(line);
			}
			return;
		}
		if (direction == TodoistSyncClock.PULL) {
			pullRemoteOntoNode(client, open, taskId, location);
			TodoistReminderFactory.markLinkedNode(open, location.projectId, location.sectionId);
			TodoistReminderFactory.stampWatermarks(open, location.updatedAtMillis);
			store.putMapping(key, taskId, record.remindAt, hash);
			stampLiveNodeIfOpen(key, taskId, hash);
			result.addUpdated(record, sectionName);
			if (callback != null) {
				callback.onUpdated(line);
			}
			return;
		}
		TodoistReminderFactory.markLinkedNode(open, location.projectId, location.sectionId);
		TodoistReminderFactory.stampWatermarks(open, location.updatedAtMillis);
		store.putMapping(key, taskId, record.remindAt, hash);
		stampLiveNodeIfOpen(key, taskId, hash);
		result.addSkipped(record, sectionName);
		if (callback != null) {
			callback.onSkipped(line);
		}
	}

	private static void rememberRemoteClock(final String key, final String taskId, final TodoistApiClient client,
			final TodoistTaskLocation previous) {
		long remote = previous == null ? 0L : previous.updatedAtMillis;
		try {
			final TodoistTaskLocation after = client.getTaskLocation(taskId);
			if (after.updatedAtMillis > 0) {
				remote = after.updatedAtMillis;
			}
		}
		catch (Exception e) {
			LogUtils.warn("Todoist: could not read updated_at for " + taskId, e);
		}
		TodoistReminderFactory.stampWatermarks(TodoistNodeLocator.findOpenNodeBySyncKey(key), remote);
	}

	private static void pullRemoteOntoNode(final TodoistApiClient client, final NodeModel node, final String taskId,
			final TodoistTaskLocation location) throws Exception {
		if (node == null) {
			return;
		}
		final boolean previous = TodoistAutoSyncService.setSuppressOutgoing(true);
		try {
			if (location.completed) {
				clearReminder(node);
				return;
			}
			final TodoistImportTask task = client.fetchTask(taskId);
			if (task == null) {
				return;
			}
			final TodoistContentParser parsed = TodoistContentParser.parse(task.content);
			final String desired = parsed.nodeText;
			final String current = TodoistReminderFactory.plainText(node);
			final long localModified = TodoistReminderFactory.localModifiedMillis(node);
			if (desired.length() > 0 && !desired.equals(current) && localModified <= location.updatedAtMillis) {
				MTextController.getController().setNodeText(node, desired);
			}
			final ReminderExtension reminder = ReminderExtension.getExtension(node);
			if (reminder != null && task.dueAtMillis > 0 && reminder.getRemindUserAt() != task.dueAtMillis) {
				reminder.setRemindUserAt(task.dueAtMillis);
				Controller.getCurrentModeController().getMapController().nodeChanged(node, ReminderExtension.class, null,
						reminder);
			}
		}
		finally {
			TodoistAutoSyncService.setSuppressOutgoing(previous);
		}
	}

	private static void clearReminder(final NodeModel node) {
		if (ReminderExtension.getExtension(node) == null) {
			return;
		}
		final ModeController mode = Controller.getCurrentModeController();
		final ReminderHook hook = mode == null ? null : (ReminderHook) mode.getExtension(ReminderHook.class);
		if (hook != null) {
			hook.undoableDeactivateHook(node);
		}
	}

	/**
	 * A task that already lived outside the Docear project. Its description must not be rewritten
	 * into "Docear reminder", and it must not be moved into that project. A node already marked
	 * {@code todoist} stays an original Todoist task even if the remote project later changes.
	 */
	private static boolean isImportedMirror(final NodeModel node, final TodoistTaskLocation location,
			final String docearProjectId) {
		if (TodoistReminderFactory.isTodoistOrigin(node)) {
			return true;
		}
		if (location == null || !location.exists || docearProjectId == null || docearProjectId.length() == 0) {
			return false;
		}
		return !docearProjectId.equals(location.projectId);
	}

	private static boolean isImportTargetSyncKey(final String syncKey) {
		if (syncKey == null) {
			return false;
		}
		final int sep = syncKey.lastIndexOf('|');
		if (sep <= 0) {
			return false;
		}
		return TodoistConfig.isImportTargetFile(new File(syncKey.substring(0, sep)));
	}

	private static void markOpenOrigin(final String syncKey, final String origin) {
		try {
			TodoistReminderFactory.setOrigin(TodoistNodeLocator.findOpenNodeBySyncKey(syncKey), origin);
		}
		catch (Exception e) {
			LogUtils.warn("Todoist: could not remember task origin for " + syncKey, e);
		}
	}

	private static void stampLiveNodeIfOpen(final String syncKey, final String taskId, final String hash) {
		try {
			final NodeModel node = TodoistNodeLocator.findOpenNodeBySyncKey(syncKey);
			if (node == null) {
				return;
			}
			TodoistReminderFactory.setTaskId(node, taskId);
			TodoistReminderFactory.setStoredContentHash(node, hash);
		}
		catch (Exception e) {
			LogUtils.warn("Todoist: could not stamp open node for " + syncKey, e);
		}
	}

	/**
	 * Push one live reminder node to Todoist (create or update). Used by auto-sync listeners.
	 * @return true if a remote write happened
	 */
	static boolean syncLiveNode(final NodeModel node) throws Exception {
		if (node == null || node.getMap() == null || node.getMap().getFile() == null) {
			return false;
		}
		final File file = node.getMap().getFile();
		if (TodoistConfig.isImportTargetFile(file)) {
			TodoistReminderRecord record = TodoistReminderFactory.fromNode(node);
			if (record == null) {
				record = TodoistReminderFactory.fromImportMapNodeWithoutReminder(node);
			}
			if (record == null) {
				return false;
			}
			return syncImportMapNode(node, record);
		}
		final TodoistReminderRecord record = TodoistReminderFactory.fromNode(node);
		if (record == null) {
			return pushLinkedTitleLive(node);
		}
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			return false;
		}
		final TodoistApiClient client = new TodoistApiClient(token.trim());
		final TodoistMappingStore store = TodoistMappingStore.get();
		final TodoistSectionStore sectionStore = new TodoistSectionStore();
		final String projectId = client.ensureProject(TodoistConfig.getProjectName());
		final String sectionName = TodoistApiClient.sectionNameForFile(record.file);
		final String sectionId = client.ensureSection(projectId, sectionName, sectionStore);
		final String key = record.syncKey();
		final String identity = record.identityKey();
		final String hash = contentHash(record, sectionName);
		String taskId = store.getTaskIdOnly(key);
		if (taskId == null || taskId.length() == 0) {
			taskId = store.findTaskIdByIdentity(identity);
		}
		if (taskId == null || taskId.length() == 0) {
			taskId = TodoistReminderFactory.getTaskId(node);
		}
		final String storedHash = storedHashFor(store, key, taskId);
		if (taskId != null && taskId.length() == 0) {
			taskId = null;
		}
		TodoistReminderFactory.copyClock(node, record);
		if (taskId != null && taskId.length() > 0) {
			TodoistTaskLocation location = client.getTaskLocation(taskId);
			final int direction = TodoistSyncClock.choose(record.localModified, location.updatedAtMillis,
					record.syncedLocal, record.syncedRemote);
			final boolean keep = record.userPlaced || isImportedMirror(node, location, projectId);
			if (keep || direction == TodoistSyncClock.PULL || direction == TodoistSyncClock.SKIP) {
				if (direction == TodoistSyncClock.PUSH
						|| (keep && direction == TodoistSyncClock.UNKNOWN && !hash.equals(storedHash))) {
					if (location.completed && direction == TodoistSyncClock.PUSH) {
						client.reopenTask(taskId);
					}
					final boolean wrote = client.updateTaskContent(taskId, record, true);
					final TodoistTaskLocation after = client.getTaskLocation(taskId);
					store.putMapping(key, taskId, record.remindAt, hash);
					store.save();
					TodoistReminderFactory.setTaskId(node, taskId);
					TodoistReminderFactory.setStoredContentHash(node, hash);
					TodoistReminderFactory.markLinkedNode(node, location.projectId, location.sectionId);
					TodoistReminderFactory.stampWatermarks(node,
							after.updatedAtMillis > 0 ? after.updatedAtMillis : location.updatedAtMillis);
					return wrote;
				}
				if (direction == TodoistSyncClock.PULL) {
					pullRemoteOntoNode(client, node, taskId, location);
					store.putMapping(key, taskId, record.remindAt, hash);
					store.save();
					TodoistReminderFactory.markLinkedNode(node, location.projectId, location.sectionId);
					TodoistReminderFactory.stampWatermarks(node, location.updatedAtMillis);
					return false;
				}
				store.putMapping(key, taskId, record.remindAt, hash);
				store.save();
				TodoistReminderFactory.setTaskId(node, taskId);
				TodoistReminderFactory.setStoredContentHash(node, hash);
				TodoistReminderFactory.markLinkedNode(node, location.projectId, location.sectionId);
				TodoistReminderFactory.stampWatermarks(node, location.updatedAtMillis);
				return false;
			}
			if (location.exists && location.completed && hash.equals(storedHash)) {
				store.putMapping(key, taskId, record.remindAt, hash);
				store.save();
				TodoistReminderFactory.setTaskId(node, taskId);
				TodoistReminderFactory.setStoredContentHash(node, hash);
				return false;
			}
			boolean reopened = false;
			if (location.exists && location.completed) {
				client.reopenTask(taskId);
				reopened = true;
				location = client.getTaskLocation(taskId);
			}
			if (location.exists) {
				final boolean needsRelocate = !client.isTaskInLocation(location, projectId, sectionId);
				final boolean needsContentUpdate = !hash.equals(storedHash) || reopened;
				if (!needsRelocate && !needsContentUpdate) {
					store.putMapping(key, taskId, record.remindAt, hash);
					store.save();
					TodoistReminderFactory.setTaskId(node, taskId);
					TodoistReminderFactory.setStoredContentHash(node, hash);
					return false;
				}
				if (needsRelocate) {
					client.relocateTaskTo(taskId, projectId, sectionId);
				}
				boolean wrote = false;
				if (needsContentUpdate) {
					wrote = client.updateTaskContent(taskId, record);
				}
				store.putMapping(key, taskId, record.remindAt, hash);
				store.save();
				sectionStore.save();
				TodoistReminderFactory.setTaskId(node, taskId);
				TodoistReminderFactory.setStoredContentHash(node, hash);
				return needsRelocate || wrote;
			}
		}
		// No live id — look for an existing Docear twin before creating another calendar block.
		final TodoistDocearTaskIndex remoteIndex = TodoistDocearTaskIndex.load(client, projectId);
		final String existing = remoteIndex.resolveExistingTaskId(key, identity, null);
		if (existing != null) {
			client.ensureTaskActive(existing);
			client.updateTaskContent(existing, record);
			client.relocateTaskTo(existing, projectId, sectionId);
			store.putMapping(key, existing, record.remindAt, hash);
			store.save();
			sectionStore.save();
			TodoistReminderFactory.setTaskId(node, existing);
			TodoistReminderFactory.setStoredContentHash(node, hash);
			return true;
		}
		// Last resort: task id known but inactive/missing from project list — try reopen by id again
		// then create only if Todoist truly has no such task.
		if (taskId != null && taskId.length() > 0) {
			try {
				if (client.ensureTaskActive(taskId) || client.getTaskLocation(taskId).exists) {
					client.updateTaskContent(taskId, record);
					client.relocateTaskTo(taskId, projectId, sectionId);
					store.putMapping(key, taskId, record.remindAt, hash);
					store.save();
					sectionStore.save();
					TodoistReminderFactory.setTaskId(node, taskId);
					TodoistReminderFactory.setStoredContentHash(node, hash);
					return true;
				}
			}
			catch (Exception e) {
				LogUtils.warn("Todoist: could not revive task " + taskId, e);
			}
		}
		taskId = client.createTask(record, projectId, sectionId);
		store.putMapping(key, taskId, record.remindAt, hash);
		store.save();
		sectionStore.save();
		TodoistReminderFactory.setTaskId(node, taskId);
		TodoistReminderFactory.setOrigin(node, TodoistReminderFactory.ORIGIN_DOCEAR);
		TodoistReminderFactory.setStoredContentHash(node, hash);
		return true;
	}

	/**
	 * Mind-map title is newer than the linked Todoist task. Used for nodes that are not reminders:
	 * those never enter the reminder push, so a later import must not overwrite them.
	 */
	static boolean localTitleWins(final long localModified, final long remoteUpdated, final long storedLocal,
			final long storedRemote) {
		final int direction = TodoistSyncClock.choose(localModified, remoteUpdated, storedLocal, storedRemote);
		if (direction == TodoistSyncClock.PUSH) {
			return true;
		}
		if (direction == TodoistSyncClock.PULL || direction == TodoistSyncClock.SKIP) {
			return false;
		}
		return localModified > 0 && remoteUpdated > 0 && localModified > remoteUpdated;
	}

	/**
	 * Write the mind-map title onto the existing Todoist task. Does not change its project or
	 * description.
	 *
	 * @return Todoist {@code updated_at} after the write, or 0 when nothing was sent
	 */
	static long pushLinkedTitle(final String taskId, final String title) throws Exception {
		if (taskId == null || taskId.length() == 0 || title == null || title.trim().length() == 0) {
			return 0L;
		}
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			return 0L;
		}
		final TodoistApiClient client = new TodoistApiClient(token.trim());
		final TodoistReminderRecord record = new TodoistReminderRecord(new File("."), "", title.trim(), 0L, 1, "DAY",
				false);
		if (!client.updateTaskContent(taskId, record, true)) {
			final TodoistTaskLocation current = client.getTaskLocation(taskId);
			return current.updatedAtMillis;
		}
		final TodoistTaskLocation after = client.getTaskLocation(taskId);
		return after.updatedAtMillis;
	}

	/** Linked node with no reminder: push the title only, and never create or move the Todoist task. */
	private static boolean pushLinkedTitleLive(final NodeModel node) throws Exception {
		final String taskId = TodoistReminderFactory.getTaskId(node);
		final String title = TodoistReminderFactory.plainText(node);
		if (taskId == null || taskId.length() == 0 || title.length() == 0) {
			return false;
		}
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			return false;
		}
		final TodoistApiClient client = new TodoistApiClient(token.trim());
		TodoistTaskLocation location = client.getTaskLocation(taskId);
		if (!location.exists) {
			return false;
		}
		final boolean titleWins = localTitleWins(TodoistReminderFactory.localModifiedMillis(node),
				location.updatedAtMillis, TodoistReminderFactory.syncedLocalMillis(node),
				TodoistReminderFactory.syncedRemoteMillis(node));
		final boolean imported = isImportedMirror(node, location, TodoistConfig.getProjectId());
		boolean relabeled = false;
		if (imported) {
			TodoistReminderFactory.setOrigin(node, TodoistReminderFactory.ORIGIN_TODOIST);
			relabeled = client.clearOwnedStamp(taskId, location.description);
		}
		else if (!TodoistReminderFactory.isTodoistOrigin(node)) {
			relabeled = client.refreshNodeLink(taskId, node.getID(), location.description);
		}
		if (relabeled) {
			final TodoistTaskLocation linked = client.getTaskLocation(taskId);
			if (linked.updatedAtMillis > 0) {
				location = linked;
			}
			if (!titleWins) {
				TodoistReminderFactory.stampWatermarks(node, location.updatedAtMillis);
			}
		}
		if (!titleWins) {
			return false;
		}
		final long remote = pushLinkedTitle(taskId, title);
		if (remote > 0) {
			TodoistReminderFactory.stampWatermarks(node, remote);
		}
		TodoistReminderFactory.markLinkedNode(node, location.projectId, location.sectionId);
		return remote > 0;
	}

	/**
	 * The reminder hook was removed. A mind-map task is completed. An original Todoist task only
	 * loses its due date.
	 */
	static void pushReminderRemoved(final NodeModel node) throws Exception {
		if (node == null || ReminderExtension.getExtension(node) != null) {
			return;
		}
		final String taskId = TodoistReminderFactory.getTaskId(node);
		if (taskId == null || taskId.length() == 0) {
			return;
		}
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			return;
		}
		final TodoistApiClient client = new TodoistApiClient(token.trim());
		final TodoistTaskLocation location = client.getTaskLocation(taskId);
		if (!location.exists || location.completed) {
			return;
		}
		final boolean imported = isImportedMirror(node, location, TodoistConfig.getProjectId());
		if (imported) {
			TodoistReminderFactory.setOrigin(node, TodoistReminderFactory.ORIGIN_TODOIST);
			client.clearOwnedStamp(taskId, location.description);
			client.clearDueDate(taskId);
		}
		else {
			if (!TodoistReminderFactory.isTodoistOrigin(node)) {
				TodoistReminderFactory.setOrigin(node, TodoistReminderFactory.ORIGIN_DOCEAR);
			}
			client.closeTask(taskId);
		}
		final TodoistTaskLocation after = client.getTaskLocation(taskId);
		TodoistReminderFactory.markLinkedNode(node, location.projectId, location.sectionId);
		TodoistReminderFactory.stampWatermarks(node,
				after.updatedAtMillis > 0 ? after.updatedAtMillis : location.updatedAtMillis);
	}

	/** Import-map node already has hidden {@code TODOIST_TASK_ID}; update remote task content/due. */
	private static boolean syncImportMapNode(final NodeModel node, final TodoistReminderRecord record)
			throws Exception {
		final String taskId = TodoistReminderFactory.getTaskId(node);
		if (taskId == null || taskId.length() == 0) {
			return false;
		}
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			return false;
		}
		final String hash = TodoistReminderFactory.contentHash(record);
		final String stored = TodoistReminderFactory.getStoredContentHash(node);
		if (hash.equals(stored)) {
			return false;
		}
		final TodoistApiClient client = new TodoistApiClient(token.trim());
		client.updateTaskContent(taskId, record);
		TodoistReminderFactory.setStoredContentHash(node, hash);
		return true;
	}

	/** Close remote task when a live reminder node is deleted / reminder removed. */
	static void closeLiveNode(final NodeModel node) {
		if (node == null || node.getMap() == null || node.getMap().getFile() == null) {
			return;
		}
		final File file = node.getMap().getFile();
		String taskId = TodoistReminderFactory.getTaskId(node);
		final String key = TodoistSyncKeys.syncKey(file, node.getID());
		final String identity = TodoistSyncKeys.identityKey(file, node.getID());
		if (taskId == null || taskId.length() == 0) {
			final TodoistMappingStore store = TodoistMappingStore.get();
			taskId = store.getTaskIdOnly(key);
			if (taskId == null || taskId.length() == 0) {
				taskId = store.findTaskIdByIdentity(identity);
			}
		}
		closeByIdentity(identity, key, taskId);
	}

	/**
	 * Undo or paste put the node back. If delete already completed the Todoist task, open it again.
	 */
	static void reopenLinkedTask(final NodeModel node) throws Exception {
		final String taskId = node == null ? null : TodoistReminderFactory.getTaskId(node);
		if (taskId == null || taskId.length() == 0) {
			return;
		}
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			return;
		}
		final TodoistApiClient client = new TodoistApiClient(token.trim());
		final TodoistTaskLocation location = client.getTaskLocation(taskId);
		if (!location.exists || !location.completed) {
			return;
		}
		client.reopenTask(taskId);
		final TodoistTaskLocation after = client.getTaskLocation(taskId);
		TodoistReminderFactory.markLinkedNode(node, location.projectId, location.sectionId);
		TodoistReminderFactory.stampWatermarks(node,
				after.updatedAtMillis > 0 ? after.updatedAtMillis : location.updatedAtMillis);
	}

	/** Open a task again after a close that lost the race with undo. */
	static void reopenTaskIfCompleted(final String taskId) throws Exception {
		if (taskId == null || taskId.length() == 0) {
			return;
		}
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			return;
		}
		final TodoistApiClient client = new TodoistApiClient(token.trim());
		final TodoistTaskLocation location = client.getTaskLocation(taskId);
		if (!location.exists || !location.completed) {
			return;
		}
		client.reopenTask(taskId);
	}

	/**
	 * Complete the linked Todoist task after a real delete. Imported tasks in other projects are
	 * completed too. Cut-and-paste cancels this before it runs. Mapping is dropped afterwards.
	 */
	static void closeByIdentity(final String identity, final String syncKey, final String taskIdHint) {
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			return;
		}
		try {
			final TodoistMappingStore store = TodoistMappingStore.get();
			String taskId = taskIdHint;
			if (taskId == null || taskId.length() == 0) {
				if (syncKey != null) {
					taskId = store.getTaskIdOnly(syncKey);
				}
				if ((taskId == null || taskId.length() == 0) && identity != null) {
					taskId = store.findTaskIdByIdentity(identity);
				}
			}
			if (taskId == null || taskId.length() == 0) {
				return;
			}
			final TodoistApiClient client = new TodoistApiClient(token.trim());
			final TodoistTaskLocation location = client.getTaskLocation(taskId);
			if (location.exists && !location.completed) {
				client.closeTask(taskId);
			}
			if (syncKey != null) {
				store.removeMapping(syncKey);
			}
			store.removeByTaskId(taskId);
			store.save();
			LogUtils.info("Todoist: closed task " + taskId + " for identity " + identity);
		}
		catch (Exception e) {
			LogUtils.warn("Todoist auto-sync close failed for " + identity, e);
		}
	}
}
