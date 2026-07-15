package org.docear.plugin.core.todoist;

import java.awt.EventQueue;
import java.util.List;
import java.util.Map;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;

public final class TodoistImportService {
	private TodoistImportService() {
	}

	public static TodoistImportResult importAllTasks() {
		return importAllTasks(null);
	}

	/** Import only Todoist tasks that are not 1:1-linked to a source mind-map reminder. */
	public static TodoistImportResult importUnlinkedTasks(final TodoistSyncProgressCallback callback) {
		return importTasks(callback, true);
	}

	public static TodoistImportResult importAllTasks(final TodoistSyncProgressCallback callback) {
		return importTasks(callback, false);
	}

	private static TodoistImportResult importTasks(final TodoistSyncProgressCallback callback,
			final boolean unlinkedOnly) {
		TodoistSyncGuard.enter();
		try {
			return importTasksBody(callback, unlinkedOnly);
		}
		finally {
			TodoistSyncGuard.leave();
		}
	}

	private static TodoistImportResult importTasksBody(final TodoistSyncProgressCallback callback,
			final boolean unlinkedOnly) {
		final TodoistImportResult result = new TodoistImportResult();
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			result.failed = 1;
			result.errorMessage = "Todoist API token is not configured.";
			result.addFailed(result.errorMessage);
			if (!unlinkedOnly) {
				finish(callback, result);
			}
			return result;
		}
		status(callback, TextUtils.getText("todoist.import.status.fetching"));
		final TodoistApiClient client = new TodoistApiClient(token.trim());
		try {
			List tasks = client.fetchAllActiveTasks();
			final TodoistMappingStore store = TodoistMappingStore.get();
			if (unlinkedOnly) {
				final java.util.ArrayList filtered = new java.util.ArrayList();
				for (int i = 0; i < tasks.size(); i++) {
					TodoistImportTask task = (TodoistImportTask) tasks.get(i);
					if (store.isLinkedToSourceMap(task.id)) {
						continue;
					}
					String syncKey = store.getSyncKeyForTaskId(task.id);
					if (syncKey == null) {
						syncKey = TodoistApiClient.syncKeyFromDescription(task.description);
					}
					if (syncKey != null) {
						final int sep = syncKey.lastIndexOf('|');
						if (sep > 0) {
							final java.io.File file = new java.io.File(syncKey.substring(0, sep));
							if (!TodoistConfig.isImportTargetFile(file) && file.isFile()) {
								continue;
							}
						}
					}
					filtered.add(task);
				}
				tasks = filtered;
			}
			if (tasks.isEmpty()) {
				if (!unlinkedOnly) {
					String warning = TextUtils.getText("todoist.import.empty");
					result.addFailed(warning);
					if (callback != null) {
						callback.onFailed(warning);
					}
				}
			}
			final Map projectNames = client.fetchProjectNames();
			final Map sectionNames = client.fetchSectionNamesForTasks(tasks);
			result.totalFetched = tasks.size();
			status(callback, TextUtils.format("todoist.import.status.found", new Object[] { Integer.valueOf(tasks.size()) }));
			for (int i = 0; i < tasks.size(); i++) {
				TodoistImportTask task = (TodoistImportTask) tasks.get(i);
				if (callback != null) {
					callback.onProgress(i + 1, tasks.size());
					callback.onStatus(TextUtils.format("todoist.import.status.item", new Object[] { task.content }));
				}
			}
			status(callback, TextUtils.getText("todoist.import.status.writing"));
			final List tasksFinal = tasks;
			final java.io.File targetFile = TodoistConfig.getImportTargetFile();
			final TodoistImportResult[] writeResult = new TodoistImportResult[1];
			final Exception[] writeError = new Exception[1];
			final boolean mapAlreadyOpen = TodoistNodeLocator.findOpenMap(targetFile) != null;
			Runnable writeJob = new Runnable() {
				public void run() {
					try {
						writeResult[0] = new TodoistMindMapWriter().write(targetFile, tasksFinal, projectNames,
								sectionNames, unlinkedOnly);
					}
					catch (Exception e) {
						writeError[0] = e;
					}
				}
			};
			// Open maps must be mutated on the EDT; closed maps are written silently off-EDT.
			if (mapAlreadyOpen) {
				if (EventQueue.isDispatchThread()) {
					writeJob.run();
				}
				else {
					EventQueue.invokeAndWait(writeJob);
				}
			}
			else {
				writeJob.run();
			}
			if (writeError[0] != null) {
				throw writeError[0];
			}
			if (writeResult[0] != null) {
				mergeResults(result, writeResult[0]);
				notifyWriteLines(callback, writeResult[0]);
			}
		}
		catch (Exception e) {
			result.failed++;
			result.errorMessage = e.getMessage();
			result.addFailed(e.getMessage());
			if (callback != null) {
				callback.onFailed(e.getMessage());
			}
			LogUtils.warn("Todoist import failed", e);
		}
		if (!unlinkedOnly) {
			finish(callback, result);
		}
		return result;
	}

	private static void mergeResults(TodoistImportResult target, TodoistImportResult source) {
		target.targetFile = source.targetFile;
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

	private static void notifyWriteLines(TodoistSyncProgressCallback callback, TodoistImportResult writeResult) {
		if (callback == null) {
			return;
		}
		for (int i = 0; i < writeResult.createdLines.size(); i++) {
			callback.onCreated((String) writeResult.createdLines.get(i));
		}
		for (int i = 0; i < writeResult.failedLines.size(); i++) {
			callback.onFailed((String) writeResult.failedLines.get(i));
		}
	}

	private static void status(TodoistSyncProgressCallback callback, String message) {
		if (callback != null) {
			callback.onStatus(message);
		}
	}

	private static void finish(TodoistSyncProgressCallback callback, TodoistImportResult result) {
		if (callback != null) {
			callback.onFinished(toSyncResult(result));
		}
	}

	private static TodoistSyncResult toSyncResult(TodoistImportResult importResult) {
		TodoistSyncResult result = new TodoistSyncResult();
		result.totalScanned = importResult.totalFetched;
		result.created = importResult.created;
		result.updated = importResult.updated;
		result.skipped = importResult.skipped;
		result.failed = importResult.failed;
		result.errorMessage = importResult.errorMessage;
		result.projectName = importResult.targetFile;
		result.createdLines.addAll(importResult.createdLines);
		result.updatedLines.addAll(importResult.updatedLines);
		result.skippedLines.addAll(importResult.skippedLines);
		result.failedLines.addAll(importResult.failedLines);
		return result;
	}
}
