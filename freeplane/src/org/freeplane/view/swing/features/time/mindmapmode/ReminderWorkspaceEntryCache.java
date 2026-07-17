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
 * Shared in-memory reminder entry cache used by calendar + right-side timeline.
 * Keyed by file path + mtime/length so unchanged maps are not re-parsed.
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
	private static String filesSignature = "";
	private static volatile boolean warming;

	private ReminderWorkspaceEntryCache() {
	}

	/** Warm file list + parse entries once in background (no occurrence expansion). */
	static void warmAsync() {
		if (warming) {
			return;
		}
		warming = true;
		WorkspaceSideTabScanCache.schedulePreload();
		final Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					getAllEntries();
				}
				catch (Exception e) {
					LogUtils.warn(e);
				}
				finally {
					warming = false;
				}
			}
		}, "ReminderWorkspaceEntryCache-Warm");
		thread.setDaemon(true);
		thread.start();
	}

	static List getEntriesForFilePublic(final File file) {
		return getEntriesForFile(file);
	}

	static synchronized List getAllEntries() {
		final List openMaps = collectOpenMaps();
		final List files = collectMindmapFilesIncludingOpenMaps();
		// Open maps are read live (unsaved creates/edits). Include a dirty marker in the
		// signature so a pure in-memory change still forces a rebuild.
		final String signature = signatureOf(files) + "|open=" + openMapsSignature(openMaps);
		if (signature.equals(filesSignature) && allEntriesSnapshot != null && !allEntriesSnapshot.isEmpty()) {
			// Dirty open maps can gain/lose reminders without mtime or saved-flag changes
			// between two creates; never reuse the snapshot while any open map is dirty.
			boolean anyOpenDirty = false;
			for (int i = 0; i < openMaps.size(); i++) {
				if (!((MapModel) openMaps.get(i)).isSaved()) {
					anyOpenDirty = true;
					break;
				}
			}
			if (!anyOpenDirty) {
				boolean allFresh = true;
				for (int i = 0; i < files.size(); i++) {
					final File file = (File) files.get(i);
					if (findOpenMap(openMaps, file) != null) {
						continue;
					}
					final String path = canonicalPath(file);
					final CachedFileResult cached = (CachedFileResult) CACHE_BY_PATH.get(path);
					if (cached == null || cached.modified != file.lastModified() || cached.length != file.length()) {
						allFresh = false;
						break;
					}
				}
				if (allFresh) {
					return allEntriesSnapshot;
				}
			}
		}
		cleanupCache(files);
		final List all = new ArrayList();
		final Set coveredPaths = new HashSet();
		for (int i = 0; i < openMaps.size(); i++) {
			final MapModel map = (MapModel) openMaps.get(i);
			final File file = map.getFile();
			all.addAll(ReminderWorkspaceScanHelper.scanRemindersFromOpenMap(map, file));
			if (file != null) {
				coveredPaths.add(canonicalPath(file));
			}
		}
		for (int i = 0; i < files.size(); i++) {
			final File file = (File) files.get(i);
			if (coveredPaths.contains(canonicalPath(file))) {
				continue;
			}
			all.addAll(getEntriesForFileFromDisk(file));
		}
		allEntriesSnapshot = all;
		filesSignature = signature;
		return all;
	}

	static synchronized List getAllEntriesSnapshot() {
		return allEntriesSnapshot == null ? Collections.EMPTY_LIST : allEntriesSnapshot;
	}

	static synchronized void invalidateAll() {
		CACHE_BY_PATH.clear();
		allEntriesSnapshot = Collections.EMPTY_LIST;
		filesSignature = "";
	}

	static synchronized void invalidateFile(final File file) {
		if (file == null) {
			return;
		}
		CACHE_BY_PATH.remove(canonicalPath(file));
		allEntriesSnapshot = Collections.EMPTY_LIST;
		filesSignature = "";
	}

	/**
	 * Expand occurrences once for a range; also returns day counts for the same set.
	 * Result: Object[] { List&lt;TimelineOccurrence&gt;, Map&lt;Long,Integer&gt; dayCounts }
	 */
	static Object[] expandWithDayCounts(final long rangeStart, final long rangeEnd) {
		final List entries = getAllEntries();
		final List occurrences = ReminderWorkspaceScanHelper.buildTimelineOccurrences(entries, rangeStart, rangeEnd);
		final Map counts = new HashMap();
		for (int i = 0; i < occurrences.size(); i++) {
			final ReminderWorkspaceScanHelper.TimelineOccurrence occ = (ReminderWorkspaceScanHelper.TimelineOccurrence) occurrences
			        .get(i);
			final Long key = Long.valueOf(ReminderCycleScheduler.startOfDay(occ.occurrenceAt));
			final Integer prev = (Integer) counts.get(key);
			counts.put(key, Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
		}
		return new Object[] { occurrences, counts };
	}

	static Map buildDayCounts(final List entries, final long rangeStart, final long rangeEnd) {
		final Object[] pack = expandWithDayCounts(rangeStart, rangeEnd);
		return (Map) pack[1];
	}

	private static String signatureOf(final List files) {
		final StringBuilder sb = new StringBuilder(files.size() * 24);
		for (int i = 0; i < files.size(); i++) {
			final File f = (File) files.get(i);
			sb.append(canonicalPath(f)).append('|').append(f.lastModified()).append('|').append(f.length())
			        .append(';');
		}
		return sb.toString();
	}

	static List getEntriesForFile(final File file) {
		if (file == null) {
			return Collections.EMPTY_LIST;
		}
		final MapModel open = findOpenMapByFile(file);
		if (open != null) {
			return ReminderWorkspaceScanHelper.scanRemindersFromOpenMap(open, file);
		}
		return getEntriesForFileFromDisk(file);
	}

	private static List getEntriesForFileFromDisk(final File file) {
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

	private static List collectOpenMaps() {
		final List openMaps = new ArrayList();
		try {
			final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			final Map maps = mapViewManager.getMaps(MModeController.MODENAME);
			for (final Object mapObj : maps.values()) {
				final MapModel map = (MapModel) mapObj;
				if (map != null && map.getRootNode() != null) {
					openMaps.add(map);
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
		return openMaps;
	}

	private static String openMapsSignature(final List openMaps) {
		final StringBuilder sb = new StringBuilder(openMaps.size() * 24);
		for (int i = 0; i < openMaps.size(); i++) {
			final MapModel map = (MapModel) openMaps.get(i);
			final File file = map.getFile();
			sb.append(file == null ? "unsaved:" + System.identityHashCode(map) : canonicalPath(file));
			sb.append('|').append(map.isSaved() ? '1' : '0').append(';');
		}
		return sb.toString();
	}

	private static MapModel findOpenMap(final List openMaps, final File file) {
		if (file == null || openMaps == null) {
			return null;
		}
		final String path = canonicalPath(file);
		for (int i = 0; i < openMaps.size(); i++) {
			final MapModel map = (MapModel) openMaps.get(i);
			final File mapFile = map.getFile();
			if (mapFile != null && path.equals(canonicalPath(mapFile))) {
				return map;
			}
		}
		return null;
	}

	private static MapModel findOpenMapByFile(final File file) {
		return findOpenMap(collectOpenMaps(), file);
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
}
