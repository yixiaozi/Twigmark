package org.docear.plugin.core.todoist;

import java.awt.EventQueue;
import java.util.List;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderHook;

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
		final TodoistSyncResult merged = new TodoistSyncResult();
		status(callback, TextUtils.getText("todoist.sync.status.connecting"));
		final TodoistSyncProgressCallback phase = phaseCallback(callback);

		final TodoistSyncResult push = TodoistSyncService.syncAllReminders(phase);
		merge(merged, push);
		try {
			TodoistNodeLocator.stampOpenMapsFromStore(new TodoistMappingStore());
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
	 * text and reminder when remote content differs.
	 */
	static TodoistSyncResult pullLinkedNodes(final TodoistSyncProgressCallback callback) {
		final TodoistSyncResult result = new TodoistSyncResult();
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			return result;
		}
		try {
			final TodoistApiClient client = new TodoistApiClient(token.trim());
			final TodoistMappingStore store = new TodoistMappingStore();
			final List tasks = client.fetchAllActiveTasks();
			result.totalScanned = tasks.size();
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
				final java.io.File file = new java.io.File(syncKey.substring(0, sep));
				if (TodoistConfig.isImportTargetFile(file)) {
					continue;
				}
				try {
					final boolean changed = applyTaskToLinkedNode(task, syncKey, store);
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

	private static boolean applyTaskToLinkedNode(final TodoistImportTask task, final String syncKey,
			final TodoistMappingStore store) throws Exception {
		// Fast path: open map + local content already matches remote (compare facts, not
		// push/pull hash formats — those differ and would thrash).
		final NodeModel open = TodoistNodeLocator.findOpenNodeBySyncKey(syncKey);
		if (open != null && !needsPull(open, task)) {
			TodoistReminderFactory.setTaskId(open, task.id);
			refreshStoredHash(open, syncKey, task, store);
			return false;
		}

		final boolean[] changed = new boolean[1];
		final Exception[] error = new Exception[1];
		final Runnable job = new Runnable() {
			public void run() {
				final boolean previous = TodoistAutoSyncService.setSuppressOutgoing(true);
				try {
					final NodeModel node = TodoistNodeLocator.findOrOpenNodeBySyncKey(syncKey);
					if (node == null) {
						return;
					}
					TodoistReminderFactory.setTaskId(node, task.id);
					if (!needsPull(node, task)) {
						refreshStoredHash(node, syncKey, task, store);
						return;
					}
					final TodoistContentParser parsed = TodoistContentParser.parse(task.content);
					final String desired = parsed.nodeText;
					final String current = TodoistReminderFactory.plainText(node);
					if (!desired.equals(current)) {
						MTextController.getController().setNodeText(node, desired);
						changed[0] = true;
					}
					if (task.dueAtMillis > 0) {
						if (applyReminder(node, task)) {
							changed[0] = true;
						}
					}
					refreshStoredHash(node, syncKey, task, store);
					if (changed[0] && node.getMap() != null) {
						node.getMap().setSaved(false);
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

	/** True when Todoist content/due differs from the linked mind-map node. */
	private static boolean needsPull(final NodeModel node, final TodoistImportTask task) {
		final TodoistContentParser parsed = TodoistContentParser.parse(task.content);
		final String desired = parsed.nodeText;
		final String current = TodoistReminderFactory.plainText(node);
		if (!desired.equals(current)) {
			return true;
		}
		if (task.dueAtMillis <= 0) {
			return false;
		}
		final ReminderExtension existing = ReminderExtension.getExtension(node);
		final long existingAt = existing == null ? 0L : existing.getRemindUserAt();
		if (existingAt != task.dueAtMillis) {
			return true;
		}
		if (task.recurring) {
			final PeriodInfo period = resolvePeriod(task);
			if (existing == null) {
				return true;
			}
			if (existing.getPeriod() != period.period) {
				return true;
			}
			final String unit = existing.getPeriodUnitAsString();
			return unit == null || !period.unit.equalsIgnoreCase(unit);
		}
		return false;
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
			hash = Integer.toString((TodoistReminderFactory.plainText(node) + "|" + task.dueAtMillis).hashCode());
		}
		TodoistReminderFactory.setStoredContentHash(node, hash);
		store.putMapping(syncKey, task.id, remindAt, hash);
	}

	private static boolean applyReminder(final NodeModel node, final TodoistImportTask task) {
		final ModeController modeController = Controller.getCurrentModeController();
		final ReminderHook reminderHook = (ReminderHook) modeController.getExtension(ReminderHook.class);
		if (reminderHook == null) {
			return false;
		}
		final ReminderExtension existing = ReminderExtension.getExtension(node);
		final long existingAt = existing == null ? 0L : existing.getRemindUserAt();
		final PeriodInfo period = resolvePeriod(task);
		if (existing != null && existingAt == task.dueAtMillis && existing.getPeriod() == period.period
				&& period.unit.equalsIgnoreCase(String.valueOf(existing.getPeriodUnitAsString()))) {
			return false;
		}
		final ReminderExtension reminder = new ReminderExtension(node);
		reminder.setRemindUserAt(task.dueAtMillis);
		reminder.setPeriod(period.period);
		reminder.setPeriodUnitAsString(period.unit);
		reminderHook.undoableActivateHook(node, reminder);
		return true;
	}

	private static PeriodInfo resolvePeriod(TodoistImportTask task) {
		if (!task.recurring) {
			return new PeriodInfo(1, "DAY");
		}
		final String due = task.dueString == null ? "" : task.dueString.toLowerCase();
		int period = 1;
		String unit = "WEEK";
		if (due.indexOf("day") >= 0) {
			unit = "DAY";
		}
		else if (due.indexOf("month") >= 0) {
			unit = "MONTH";
		}
		else if (due.indexOf("year") >= 0) {
			unit = "YEAR";
		}
		final java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("every\\s+(\\d+)").matcher(due);
		if (matcher.find()) {
			try {
				period = Integer.parseInt(matcher.group(1));
			}
			catch (NumberFormatException e) {
			}
		}
		if (period <= 0) {
			period = 1;
		}
		return new PeriodInfo(period, unit);
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

	private static final class PeriodInfo {
		final int period;
		final String unit;

		PeriodInfo(int period, String unit) {
			this.period = period;
			this.unit = unit;
		}
	}
}
