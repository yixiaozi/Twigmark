package org.docear.plugin.core.todoist;

import java.awt.EventQueue;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderCycleAttributes;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderHook;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderTaskAttributes;

/**
 * One-button bidirectional sync:
 * <ol>
 * <li>Push mind-map reminders → Todoist (create/update; stamp 1:1 task id on nodes)</li>
 * <li>Pull Todoist changes → linked nodes in their original maps</li>
 * <li>Import only unlinked Todoist tasks into the import target mind map</li>
 * </ol>
 */
public final class TodoistBidirectionalSyncService {
	private TodoistBidirectionalSyncService() {
	}

	public static TodoistSyncResult syncAll(final TodoistSyncProgressCallback callback) {
		TodoistSyncGuard.enter();
		try {
			return syncAllUnlocked(callback);
		}
		finally {
			TodoistSyncGuard.leave();
		}
	}

	private static TodoistSyncResult syncAllUnlocked(final TodoistSyncProgressCallback callback) {
		final TodoistSyncResult merged = new TodoistSyncResult();
		status(callback, TextUtils.getText("todoist.sync.status.connecting"));
		final TodoistSyncProgressCallback phase = phaseCallback(callback);

		final TodoistSyncResult push = TodoistSyncService.syncAllReminders(phase);
		merge(merged, push);
		try {
			TodoistNodeLocator.stampOpenMapsFromStore(TodoistMappingStore.get());
		}
		catch (Exception e) {
			LogUtils.warn("Todoist: could not stamp open maps", e);
		}

		status(callback, TextUtils.getText("todoist.sync.status.pull_linked"));
		final TodoistSyncResult pull = pullLinkedNodes(phase);
		merge(merged, pull);

		status(callback, TextUtils.getText("todoist.sync.status.import_unlinked"));
		final TodoistImportResult importResult = TodoistImportService.importUnlinkedTasks(phase);
		mergeImport(merged, importResult);

		if (callback != null) {
			callback.onFinished(merged);
		}
		return merged;
	}

	/** Forwards progress events but swallows nested {@code onFinished}. */
	private static TodoistSyncProgressCallback phaseCallback(final TodoistSyncProgressCallback callback) {
		if (callback == null) {
			return null;
		}
		return new TodoistSyncProgressCallback() {
			public void onStatus(String message) {
				callback.onStatus(message);
			}

			public void onProgress(int current, int total) {
				callback.onProgress(current, total);
			}

			public void onCreated(String line) {
				callback.onCreated(line);
			}

			public void onSkipped(String line) {
				callback.onSkipped(line);
			}

			public void onUpdated(String line) {
				callback.onUpdated(line);
			}

			public void onFailed(String line) {
				callback.onFailed(line);
			}

			public void onClosed(String line) {
				callback.onClosed(line);
			}

			public void onFinished(TodoistSyncResult result) {
				// Nested phase — unified sync finishes once at the end.
			}
		};
	}

	/**
	 * For each active Todoist task that maps to a mind-map node (anywhere), update that node's
	 * text / due / duration / urgency. Open maps are updated in memory; closed maps are patched
	 * on disk silently (never opened in the UI).
	 */
	static TodoistSyncResult pullLinkedNodes(final TodoistSyncProgressCallback callback) {
		final TodoistSyncResult result = new TodoistSyncResult();
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			return result;
		}
		try {
			final TodoistApiClient client = new TodoistApiClient(token.trim());
			final TodoistMappingStore store = TodoistMappingStore.get();
			final List tasks = client.fetchAllActiveTasks();
			result.totalScanned = tasks.size();
			final Map silentByPath = new HashMap();
			for (int i = 0; i < tasks.size(); i++) {
				final TodoistImportTask task = (TodoistImportTask) tasks.get(i);
				progress(callback, i + 1, tasks.size());
				String syncKey = store.getSyncKeyForTaskId(task.id);
				if (syncKey == null || syncKey.length() == 0) {
					syncKey = TodoistApiClient.syncKeyFromDescription(task.description);
					if (syncKey != null && syncKey.length() > 0) {
						store.putMapping(syncKey, task.id, task.dueAtMillis, "");
					}
				}
				if (syncKey == null || syncKey.length() == 0) {
					continue;
				}
				final int sep = syncKey.lastIndexOf('|');
				if (sep <= 0) {
					continue;
				}
				final File file = new File(syncKey.substring(0, sep));
				final String nodeId = syncKey.substring(sep + 1);
				if (TodoistConfig.isImportTargetFile(file)) {
					continue;
				}
				try {
					final org.freeplane.features.map.MapModel openMap = TodoistNodeLocator.findOpenMap(file);
					final NodeModel open = openMap == null ? null : openMap.getNodeForID(nodeId);
					boolean changed;
					if (open != null) {
						changed = applyTaskToOpenNode(task, syncKey, store, open);
					}
					else if (openMap != null) {
						// Map is open in UI — never silently rewrite the open file on disk.
						result.skipped++;
						final String line = "[" + file.getName() + "] " + plainContent(task)
								+ " (open map, node missing)";
						result.skippedLines.add(line);
						if (callback != null) {
							callback.onSkipped(line);
						}
						LogUtils.warn("Todoist pull: skip silent update for open map missing node " + syncKey);
						continue;
					}
					else if (file.isFile()) {
						queueSilentPatch(silentByPath, file, nodeId, task, syncKey, store);
						continue;
					}
					else {
						result.skipped++;
						final String line = "[" + file.getName() + "] " + plainContent(task);
						result.skippedLines.add(line);
						if (callback != null) {
							callback.onSkipped(line);
						}
						continue;
					}
					final String line = "[" + file.getName() + "] " + plainContent(task);
					if (changed) {
						result.updated++;
						result.updatedLines.add(line);
						if (callback != null) {
							callback.onUpdated(line);
						}
					}
					else {
						result.skipped++;
						result.skippedLines.add(line);
						if (callback != null) {
							callback.onSkipped(line);
						}
					}
				}
				catch (Exception e) {
					result.failed++;
					final String failedLine = syncKey + " — " + e.getMessage();
					result.failedLines.add(failedLine);
					if (callback != null) {
						callback.onFailed(failedLine);
					}
					LogUtils.warn("Todoist pull to linked node failed: " + syncKey, e);
				}
			}
			flushSilentPatches(silentByPath, result, callback);
			store.save();
		}
		catch (Exception e) {
			result.failed++;
			result.errorMessage = e.getMessage();
			result.failedLines.add(e.getMessage());
			if (callback != null) {
				callback.onFailed(e.getMessage());
			}
			LogUtils.warn("Todoist pull linked nodes failed", e);
		}
		return result;
	}

	private static void queueSilentPatch(final Map silentByPath, final File file, final String nodeId,
			final TodoistImportTask task, final String syncKey, final TodoistMappingStore store) {
		final TodoistCycleMapper.Cycle cycle = resolveCycle(task);
		final boolean cycleTrusted = !TodoistCycleMapper.isWeakRecurringFallback(task.dueString, task.recurring);
		final String hash = contentHashForTask(task, file);
		store.putMapping(syncKey, task.id, task.dueAtMillis, hash);
		// jinji: pass local=0 here; applyPatch never clears local JINJI when remote is default.
		final int jinji = TodoistPriority.toJinji(task.priority, 0);
		final TodoistSilentMmUpdater.Patch patch = new TodoistSilentMmUpdater.Patch(nodeId, plainContent(task),
				task.dueAtMillis, task.durationMinutes, jinji, task.id, hash, cycle.recurring, cycle.interval,
				cycle.periodUnit(), cycle.remindType, cycle.weekDays, cycleTrusted, false);
		List list = (List) silentByPath.get(file.getAbsolutePath());
		if (list == null) {
			list = new ArrayList();
			silentByPath.put(file.getAbsolutePath(), list);
		}
		list.add(new Object[] { file, patch, plainContent(task) });
	}

	private static void flushSilentPatches(final Map silentByPath, final TodoistSyncResult result,
			final TodoistSyncProgressCallback callback) {
		for (Iterator it = silentByPath.entrySet().iterator(); it.hasNext();) {
			Map.Entry entry = (Map.Entry) it.next();
			List items = (List) entry.getValue();
			if (items == null || items.isEmpty()) {
				continue;
			}
			File file = (File) ((Object[]) items.get(0))[0];
			if (TodoistNodeLocator.findOpenMap(file) != null) {
				for (int i = 0; i < items.size(); i++) {
					final String content = (String) ((Object[]) items.get(i))[2];
					final String line = "[" + file.getName() + "] " + content + " (map opened during sync)";
					result.skipped++;
					result.skippedLines.add(line);
					if (callback != null) {
						callback.onSkipped(line);
					}
				}
				LogUtils.warn("Todoist: skip silent patches; map opened during sync: " + file.getPath());
				continue;
			}
			List patches = new ArrayList();
			for (int i = 0; i < items.size(); i++) {
				patches.add(((Object[]) items.get(i))[1]);
			}
			final int written = TodoistSilentMmUpdater.applyPatches(file, patches);
			for (int i = 0; i < items.size(); i++) {
				final String content = (String) ((Object[]) items.get(i))[2];
				final String line = "[" + file.getName() + "] " + content;
				if (written > 0) {
					result.updated++;
					result.updatedLines.add(line);
					if (callback != null) {
						callback.onUpdated(line);
					}
				}
				else {
					result.skipped++;
					result.skippedLines.add(line);
					if (callback != null) {
						callback.onSkipped(line);
					}
				}
			}
		}
	}

	/**
	 * Same fingerprint scheme as push ({@link TodoistSyncService#contentHash}) so a pull does not
	 * immediately schedule a re-push via auto-sync.
	 */
	private static String contentHashForTask(final TodoistImportTask task, final File file) {
		final TodoistCycleMapper.Cycle cycle = resolveCycle(task);
		final String sectionName = file == null ? "" : TodoistApiClient.sectionNameForFile(file);
		final TodoistReminderRecord record = new TodoistReminderRecord(file, "", plainContent(task), task.dueAtMillis,
				cycle.interval, cycle.periodUnit(), cycle.recurring, task.durationMinutes,
				TodoistPriority.toJinji(task.priority, 0), cycle.remindType, cycle.weekDays);
		return TodoistSyncService.contentHash(record, sectionName);
	}

	private static boolean applyTaskToOpenNode(final TodoistImportTask task, final String syncKey,
			final TodoistMappingStore store, final NodeModel openNode) throws Exception {
		if (!needsPull(openNode, task)) {
			TodoistReminderFactory.setTaskId(openNode, task.id);
			refreshStoredHash(openNode, syncKey, task, store);
			return false;
		}
		final boolean[] changed = new boolean[1];
		final Exception[] error = new Exception[1];
		final Runnable job = new Runnable() {
			public void run() {
				final boolean previous = TodoistAutoSyncService.setSuppressOutgoing(true);
				try {
					TodoistReminderFactory.setTaskId(openNode, task.id);
					final TodoistContentParser parsed = TodoistContentParser.parse(task.content);
					final String desired = parsed.nodeText;
					final String current = TodoistReminderFactory.plainText(openNode);
					if (!desired.equals(current)) {
						MTextController.getController().setNodeText(openNode, desired);
						changed[0] = true;
					}
					if (task.dueAtMillis > 0) {
						if (applyReminder(openNode, task)) {
							changed[0] = true;
						}
					}
					// Map-protect: never clear a local reminder just because Todoist has no due.
					if (applyDurationAndUrgency(openNode, task)) {
						changed[0] = true;
					}
					refreshStoredHash(openNode, syncKey, task, store);
					if (changed[0] && openNode.getMap() != null) {
						openNode.getMap().setSaved(false);
					}
				}
				catch (Exception e) {
					error[0] = e;
				}
				finally {
					TodoistAutoSyncService.setSuppressOutgoing(previous);
				}
			}
		};
		if (EventQueue.isDispatchThread()) {
			job.run();
		}
		else {
			EventQueue.invokeAndWait(job);
		}
		if (error[0] != null) {
			throw error[0];
		}
		return changed[0];
	}

	/**
	 * True when Todoist has <em>richer or clearly different</em> content than the linked node.
	 * Empty/default Todoist fields never force a destructive pull (map settings win).
	 */
	private static boolean needsPull(final NodeModel node, final TodoistImportTask task) {
		final TodoistContentParser parsed = TodoistContentParser.parse(task.content);
		final String desired = parsed.nodeText;
		final String current = TodoistReminderFactory.plainText(node);
		if (desired.length() > 0 && !desired.equals(current)) {
			return true;
		}
		final int localDuration = ReminderTaskAttributes.readTaskTimeFromNode(node);
		if (task.durationMinutes > 0 && task.durationMinutes != localDuration) {
			return true;
		}
		final int localJinji = ReminderTaskAttributes.readJinjiFromNode(node);
		final int desiredJinji = TodoistPriority.toJinji(task.priority, localJinji);
		if (desiredJinji != localJinji && desiredJinji > 0) {
			return true;
		}
		final ReminderExtension existing = ReminderExtension.getExtension(node);
		final long existingAt = existing == null ? 0L : existing.getRemindUserAt();
		if (task.dueAtMillis <= 0) {
			// Do not pull-to-clear: keep local reminder when Todoist has no due.
			return false;
		}
		final TodoistCycleMapper.Cycle remote = resolveCycle(task);
		final boolean cycleTrusted = !TodoistCycleMapper.isWeakRecurringFallback(task.dueString, task.recurring);
		if (existingAt <= 0) {
			return true;
		}
		if (remote.recurring) {
			if (!sameLocalTimeOfDay(existingAt, task.dueAtMillis)) {
				return true;
			}
		}
		else if (existingAt != task.dueAtMillis) {
			return true;
		}
		if (!cycleTrusted) {
			return false;
		}
		final String localType = ReminderCycleAttributes.readRemindTypeFromNode(node);
		final int localInterval = ReminderCycleAttributes.readIntervalFromNode(node);
		final String localWeekDays = ReminderCycleAttributes.readWeekDaysFromNode(node);
		final TodoistCycleMapper.Cycle local = TodoistCycleMapper.fromPublicNodeReaders(localType, localInterval,
				localWeekDays);
		if (remote.recurring != local.recurring) {
			return true;
		}
		if (!remote.recurring) {
			return false;
		}
		if (remote.interval != local.interval) {
			return true;
		}
		if (!remote.remindType.equalsIgnoreCase(local.remindType)) {
			return true;
		}
		return !sameWeekDays(remote.weekDays, local.weekDays);
	}

	/** Wall-clock hour:minute in the JVM default timezone (matches Freeplane reminder editor). */
	private static boolean sameLocalTimeOfDay(final long a, final long b) {
		if (a <= 0 || b <= 0) {
			return a == b;
		}
		final java.util.Calendar ca = java.util.Calendar.getInstance();
		ca.setTimeInMillis(a);
		final java.util.Calendar cb = java.util.Calendar.getInstance();
		cb.setTimeInMillis(b);
		return ca.get(java.util.Calendar.HOUR_OF_DAY) == cb.get(java.util.Calendar.HOUR_OF_DAY)
				&& ca.get(java.util.Calendar.MINUTE) == cb.get(java.util.Calendar.MINUTE);
	}

	private static boolean sameWeekDays(final String a, final String b) {
		final String left = a == null ? "" : a;
		final String right = b == null ? "" : b;
		if (left.equals(right)) {
			return true;
		}
		// Order-insensitive compare of weekday codes.
		if (left.length() != right.length()) {
			return false;
		}
		final char[] ca = left.toCharArray();
		final char[] cb = right.toCharArray();
		java.util.Arrays.sort(ca);
		java.util.Arrays.sort(cb);
		return new String(ca).equals(new String(cb));
	}

	private static boolean applyDurationAndUrgency(final NodeModel node, final TodoistImportTask task) {
		final int localDuration = ReminderTaskAttributes.readTaskTimeFromNode(node);
		final int localLevel = ReminderTaskAttributes.readTaskLevelFromNode(node);
		final int localJinji = ReminderTaskAttributes.readJinjiFromNode(node);
		final int desiredJinji = TodoistPriority.toJinji(task.priority, localJinji);
		// Never clear local duration with Todoist unset (0).
		final int desiredDuration = task.durationMinutes > 0 ? task.durationMinutes : localDuration;
		if (desiredDuration == localDuration && desiredJinji == localJinji) {
			return false;
		}
		ReminderTaskAttributes.writeFull(node, desiredDuration, localLevel, desiredJinji);
		return true;
	}

	/**
	 * Persist the same content-hash scheme as push ({@link TodoistSyncService#contentHash})
	 * so auto-sync does not immediately re-push after a pull.
	 */
	private static void refreshStoredHash(final NodeModel node, final String syncKey, final TodoistImportTask task,
			final TodoistMappingStore store) {
		final TodoistReminderRecord record = TodoistReminderFactory.fromNode(node);
		String hash;
		long remindAt = task.dueAtMillis;
		if (record != null) {
			hash = TodoistReminderFactory.contentHash(record);
			remindAt = record.remindAt;
		}
		else {
			final File file = node.getMap() == null ? null : node.getMap().getFile();
			hash = contentHashForTask(task, file);
		}
		TodoistReminderFactory.setStoredContentHash(node, hash);
		store.putMapping(syncKey, task.id, remindAt, hash);
	}

	private static boolean clearReminder(final NodeModel node) {
		final ReminderExtension existing = ReminderExtension.getExtension(node);
		if (existing == null) {
			ReminderCycleAttributes.writeOneTimeReminder(node);
			return false;
		}
		final ModeController modeController = Controller.getCurrentModeController();
		final ReminderHook reminderHook = (ReminderHook) modeController.getExtension(ReminderHook.class);
		if (reminderHook == null) {
			return false;
		}
		reminderHook.undoableDeactivateHook(node);
		ReminderCycleAttributes.writeOneTimeReminder(node);
		return true;
	}

	private static boolean applyReminder(final NodeModel node, final TodoistImportTask task) {
		final ModeController modeController = Controller.getCurrentModeController();
		final ReminderHook reminderHook = (ReminderHook) modeController.getExtension(ReminderHook.class);
		if (reminderHook == null) {
			return false;
		}
		final TodoistCycleMapper.Cycle cycle = resolveCycle(task);
		final boolean cycleTrusted = !TodoistCycleMapper.isWeakRecurringFallback(task.dueString, task.recurring);
		final ReminderExtension existing = ReminderExtension.getExtension(node);
		final long existingAt = existing == null ? 0L : existing.getRemindUserAt();
		final String localType = ReminderCycleAttributes.readRemindTypeFromNode(node);
		final int localInterval = ReminderCycleAttributes.readIntervalFromNode(node);
		final String localWeekDays = ReminderCycleAttributes.readWeekDaysFromNode(node);
		final boolean localRecurring = localType != null && localType.length() > 0
				&& !"onetime".equalsIgnoreCase(localType);
		final boolean sameCycle = !cycleTrusted || (cycle.recurring == localRecurring && (!cycle.recurring
				|| (cycle.interval == localInterval
						&& cycle.remindType.equalsIgnoreCase(localType == null ? "" : localType)
						&& sameWeekDays(cycle.weekDays, localWeekDays))));
		final boolean sameWhen = existing != null && (cycle.recurring || localRecurring
				? sameLocalTimeOfDay(existingAt, task.dueAtMillis)
				: existingAt == task.dueAtMillis);
		if (sameCycle && sameWhen) {
			return false;
		}
		final ReminderExtension reminder = new ReminderExtension(node);
		long remindAt = task.dueAtMillis;
		if ((cycle.recurring || localRecurring) && existing != null && existingAt > 0
				&& sameLocalTimeOfDay(existingAt, task.dueAtMillis)) {
			remindAt = existingAt;
		}
		else if ((cycle.recurring || localRecurring) && existing != null && existingAt > 0
				&& !sameLocalTimeOfDay(existingAt, task.dueAtMillis)) {
			remindAt = replaceLocalTimeOfDay(existingAt, task.dueAtMillis);
		}
		reminder.setRemindUserAt(remindAt);
		if (cycleTrusted && cycle.recurring) {
			reminder.setPeriod(cycle.interval);
			reminder.setPeriodUnitAsString(cycle.periodUnit());
		}
		else if (existing != null) {
			reminder.setPeriod(existing.getPeriod() <= 0 ? 1 : existing.getPeriod());
			final String unit = existing.getPeriodUnitAsString();
			reminder.setPeriodUnitAsString(unit == null || unit.length() == 0 ? "DAY" : unit);
		}
		else {
			reminder.setPeriod(1);
			reminder.setPeriodUnitAsString("DAY");
		}
		reminderHook.undoableActivateHook(node, reminder);
		if (cycleTrusted) {
			if (cycle.recurring) {
				ReminderCycleAttributes.writeRecurringCycle(node, cycle.remindType, cycle.interval, cycle.weekDays);
			}
			else if (!localRecurring) {
				ReminderCycleAttributes.writeOneTimeReminder(node);
			}
			// If remote is one-time but local was recurring and due.string was trusted empty —
			// only clear when cycleTrusted and !cycle.recurring.
		}
		return true;
	}

	private static long replaceLocalTimeOfDay(final long dateMillis, final long timeSourceMillis) {
		final java.util.Calendar date = java.util.Calendar.getInstance();
		date.setTimeInMillis(dateMillis);
		final java.util.Calendar time = java.util.Calendar.getInstance();
		time.setTimeInMillis(timeSourceMillis);
		date.set(java.util.Calendar.HOUR_OF_DAY, time.get(java.util.Calendar.HOUR_OF_DAY));
		date.set(java.util.Calendar.MINUTE, time.get(java.util.Calendar.MINUTE));
		date.set(java.util.Calendar.SECOND, 0);
		date.set(java.util.Calendar.MILLISECOND, 0);
		return date.getTimeInMillis();
	}

	private static TodoistCycleMapper.Cycle resolveCycle(final TodoistImportTask task) {
		return TodoistCycleMapper.fromTodoistDue(task.dueString, task.recurring);
	}

	private static String plainContent(TodoistImportTask task) {
		return TodoistContentParser.parse(task.content).nodeText;
	}

	private static void merge(TodoistSyncResult target, TodoistSyncResult source) {
		if (source == null) {
			return;
		}
		target.totalScanned += source.totalScanned;
		target.created += source.created;
		target.updated += source.updated;
		target.skipped += source.skipped;
		target.failed += source.failed;
		target.closed += source.closed;
		target.createdLines.addAll(source.createdLines);
		target.updatedLines.addAll(source.updatedLines);
		target.skippedLines.addAll(source.skippedLines);
		target.failedLines.addAll(source.failedLines);
		target.closedLines.addAll(source.closedLines);
		if (source.projectName != null) {
			target.projectName = source.projectName;
		}
		if (source.projectId != null) {
			target.projectId = source.projectId;
		}
		if (source.errorMessage != null) {
			target.errorMessage = source.errorMessage;
		}
	}

	private static void mergeImport(TodoistSyncResult target, TodoistImportResult source) {
		if (source == null) {
			return;
		}
		target.created += source.created;
		target.updated += source.updated;
		target.skipped += source.skipped;
		target.failed += source.failed;
		target.createdLines.addAll(source.createdLines);
		target.updatedLines.addAll(source.updatedLines);
		target.skippedLines.addAll(source.skippedLines);
		target.failedLines.addAll(source.failedLines);
		if (source.errorMessage != null) {
			target.errorMessage = source.errorMessage;
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
}
