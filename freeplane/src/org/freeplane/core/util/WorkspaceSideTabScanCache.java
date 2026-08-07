package org.freeplane.core.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Background scan cache for workspace side tabs (file search, activity analysis, etc.).
 * Started after a short delay so application startup is not blocked.
 */
public final class WorkspaceSideTabScanCache {

	private static final int PRELOAD_DELAY_MS = 3000;

	private static volatile List<File> mindMapFiles = Collections.emptyList();
	private static volatile List<File> allFiles = Collections.emptyList();
	private static volatile boolean mindMapScanComplete;
	private static volatile boolean allFilesScanComplete;
	private static volatile boolean preloadScheduled;

	private WorkspaceSideTabScanCache() {
	}

	public static void schedulePreload() {
		synchronized (WorkspaceSideTabScanCache.class) {
			if (preloadScheduled) {
				return;
			}
			preloadScheduled = true;
		}
		final javax.swing.Timer timer = new javax.swing.Timer(PRELOAD_DELAY_MS, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				((javax.swing.Timer) e.getSource()).stop();
				new Thread(new Runnable() {
					public void run() {
						preloadMindMapFiles();
						preloadAllFiles();
					}
				}, "WorkspaceSideTabScanCache").start();
			}
		});
		timer.setRepeats(false);
		timer.start();
	}

	public static boolean isMindMapScanComplete() {
		return mindMapScanComplete;
	}

	public static boolean isAllFilesScanComplete() {
		return allFilesScanComplete;
	}

	public static List<File> getMindMapFilesSnapshot() {
		if (!mindMapScanComplete) {
			return null;
		}
		synchronized (WorkspaceSideTabScanCache.class) {
			return new ArrayList<File>(mindMapFiles);
		}
	}

	/**
	 * Prefer the background preload snapshot; if not ready, scan synchronously and
	 * publish into the cache so subsequent MCP queries avoid another full walk.
	 */
	public static List<File> getMindMapFilesOrCollect() {
		final List<File> snapshot = getMindMapFilesSnapshot();
		if (snapshot != null) {
			return snapshot;
		}
		final List<File> files = new ArrayList<File>();
		MindMapDataRootResolver.collectMindmapFiles(files);
		sortByLastModifiedDesc(files);
		publishMindMapFiles(files);
		return new ArrayList<File>(files);
	}

	/**
	 * Publish a completed mind-map file walk into the shared cache so other callers
	 * (MCP tools, web library) avoid a second full directory scan.
	 */
	public static void publishMindMapFiles(final List<File> files) {
		final List<File> copy = files == null ? new ArrayList<File>() : new ArrayList<File>(files);
		synchronized (WorkspaceSideTabScanCache.class) {
			mindMapFiles = copy;
			mindMapScanComplete = true;
		}
	}

	/** Drop cached file lists so the next call rescans (e.g. after large imports). */
	public static void invalidate() {
		synchronized (WorkspaceSideTabScanCache.class) {
			mindMapFiles = Collections.emptyList();
			allFiles = Collections.emptyList();
			mindMapScanComplete = false;
			allFilesScanComplete = false;
		}
	}

	public static List<File> getAllFilesSnapshot() {
		if (!allFilesScanComplete) {
			return null;
		}
		synchronized (WorkspaceSideTabScanCache.class) {
			return new ArrayList<File>(allFiles);
		}
	}

	private static void preloadMindMapFiles() {
		try {
			final List<File> files = new ArrayList<File>();
			MindMapDataRootResolver.collectMindmapFiles(files);
			sortByLastModifiedDesc(files);
			synchronized (WorkspaceSideTabScanCache.class) {
				mindMapFiles = files;
			}
		}
		catch (final Exception e) {
			LogUtils.warn(e);
		}
		finally {
			mindMapScanComplete = true;
		}
	}

	private static void preloadAllFiles() {
		try {
			final List<File> files = new ArrayList<File>();
			final File[] scanRoots = MindMapDataRootResolver.getScanRoots();
			for (int i = 0; i < scanRoots.length; i++) {
				if (scanRoots[i] != null && scanRoots[i].exists()) {
					collectAllFiles(scanRoots[i], files);
				}
			}
			sortByLastModifiedDesc(files);
			synchronized (WorkspaceSideTabScanCache.class) {
				allFiles = files;
			}
		}
		catch (final Exception e) {
			LogUtils.warn(e);
		}
		finally {
			allFilesScanComplete = true;
		}
	}

	private static void collectAllFiles(final File dir, final List<File> resultList) {
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (int i = 0; i < children.length; i++) {
			final File child = children[i];
			if (child.isDirectory()) {
				final String name = child.getName();
				if (!name.startsWith(".") && !name.startsWith("_")) {
					collectAllFiles(child, resultList);
				}
			}
			else if (child.isFile()) {
				MindMapFileIdentity.addFileIfNew(child, resultList);
			}
		}
	}

	private static void sortByLastModifiedDesc(final List<File> files) {
		Collections.sort(files, new Comparator<File>() {
			public int compare(final File a, final File b) {
				return Long.compare(b.lastModified(), a.lastModified());
			}
		});
	}
}
