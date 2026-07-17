package org.freeplane.view.swing.features.time.mindmapmode;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.WorkspaceSideTabScanCache;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;

/**
 * Per-file reminder entry cache (mtime/length keyed). Avoids re-parsing every .mm on each calendar refresh.
 */
final class ReminderWorkspaceEntryCache {
	private static final class CachedFileResult {
		private final long modified;
		private final long length;
		private final List entries;

		private CachedFileResult(final long modified, final long length, final List entries) {
			this.modified = modified;
			this.length = length;
			this.entries = entries;
		}
	}

	private static final Map CACHE_BY_PATH = new HashMap();
	private static List allEntriesSnapshot = Collections.EMPTY_LIST;

	private ReminderWorkspaceEntryCache() {
	}

	static List getEntriesForFilePublic(final File file) {
		return getEntriesForFile(file);
	}

	static synchronized List getAllEntries() {
		final List files = collectMindmapFilesIncludingOpenMaps();
		cleanupCache(files);
		final List all = new ArrayList();
		for (int i = 0; i < files.size(); i++) {
			all.addAll(getEntriesForFile((File) files.get(i)));
		}
		allEntriesSnapshot = all;
		return all;
	}

	static synchronized List getAllEntriesSnapshot() {
		return allEntriesSnapshot;
	}

	static synchronized void invalidateAll() {
		CACHE_BY_PATH.clear();
		allEntriesSnapshot = Collections.EMPTY_LIST;
	}

	static synchronized void invalidateFile(final File file) {
		if (file == null) {
			return;
		}
		try {
			CACHE_BY_PATH.remove(file.getCanonicalPath());
		}
		catch (Exception e) {
			CACHE_BY_PATH.remove(file.getAbsolutePath());
		}
		allEntriesSnapshot = Collections.EMPTY_LIST;
	}

	static Map buildDayCounts(final List entries, final long rangeStart, final long rangeEnd) {
		final Map counts = new HashMap();
		if (entries == null || entries.isEmpty()) {
			return counts;
		}
		final List occurrences = ReminderWorkspaceScanHelper.buildTimelineOccurrences(entries, rangeStart, rangeEnd);
		for (int i = 0; i < occurrences.size(); i++) {
			final ReminderWorkspaceScanHelper.TimelineOccurrence occ = (ReminderWorkspaceScanHelper.TimelineOccurrence) occurrences
			        .get(i);
			final long day = startOfDay(occ.occurrenceAt);
			final Long key = Long.valueOf(day);
			final Integer prev = (Integer) counts.get(key);
			counts.put(key, Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
		}
		return counts;
	}

	private static List getEntriesForFile(final File file) {
		if (file == null || !file.isFile()) {
			return Collections.EMPTY_LIST;
		}
		final String path = canonicalPath(file);
		final long modified = file.lastModified();
		final long length = file.length();
		final CachedFileResult cached = (CachedFileResult) CACHE_BY_PATH.get(path);
		if (cached != null && cached.modified == modified && cached.length == length) {
			return cached.entries;
		}
		final List entries = ReminderWorkspaceScanHelper.scanRemindersFromFile(file);
		CACHE_BY_PATH.put(path, new CachedFileResult(modified, length, entries));
		return entries;
	}

	private static void cleanupCache(final List currentFiles) {
		final Set currentPaths = new HashSet();
		for (int i = 0; i < currentFiles.size(); i++) {
			currentPaths.add(canonicalPath((File) currentFiles.get(i)));
		}
		final List toRemove = new ArrayList();
		for (final Object key : CACHE_BY_PATH.keySet()) {
			if (!currentPaths.contains(key)) {
				toRemove.add(key);
			}
		}
		for (int i = 0; i < toRemove.size(); i++) {
			CACHE_BY_PATH.remove(toRemove.get(i));
		}
	}

	private static List collectMindmapFilesIncludingOpenMaps() {
		final List files = new ArrayList();
		final Set seen = new HashSet();
		final List cached = WorkspaceSideTabScanCache.getMindMapFilesSnapshot();
		if (cached != null && !cached.isEmpty()) {
			for (int i = 0; i < cached.size(); i++) {
				addFile(files, seen, (File) cached.get(i));
			}
		}
		else {
			final List scanned = ReminderWorkspaceScanHelper.collectAllMindmapFiles();
			for (int i = 0; i < scanned.size(); i++) {
				addFile(files, seen, (File) scanned.get(i));
			}
		}
		try {
			final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			final Map maps = mapViewManager.getMaps(MModeController.MODENAME);
			for (final Object mapObj : maps.values()) {
				final MapModel map = (MapModel) mapObj;
				addFile(files, seen, map.getFile());
			}
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
		return files;
	}

	private static void addFile(final List files, final Set seen, final File file) {
		if (file == null || !file.isFile()) {
			return;
		}
		final String path = canonicalPath(file);
		if (seen.add(path)) {
			files.add(file);
		}
	}

	private static String canonicalPath(final File file) {
		try {
			return file.getCanonicalPath();
		}
		catch (Exception e) {
			return file.getAbsolutePath();
		}
	}

	private static long startOfDay(final long millis) {
		return ReminderCycleScheduler.startOfDay(millis);
	}
}
