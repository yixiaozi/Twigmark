package org.freeplane.features.map;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.SysUtils;

/**
 * Lightweight persistence for {@code last_selected_id} without dirtying / rewriting the
 * whole {@code .mm}. Clicking around nodes updates memory immediately and flushes this
 * sidecar after a short idle delay on a background timer.
 */
public final class LastSelectedNodeStore {

	private static final String FILE_NAME = "last-selected-nodes.properties";
	private static final String CHARSET = "UTF-8";
	private static final long FLUSH_DELAY_MS = 800L;

	private static LastSelectedNodeStore instance;

	private final Properties properties = new Properties();
	private boolean loaded;
	private Timer flushTimer;
	private String pendingMapKey;
	private String pendingNodeId;

	private LastSelectedNodeStore() {
	}

	public static synchronized LastSelectedNodeStore getInstance() {
		if (instance == null) {
			instance = new LastSelectedNodeStore();
		}
		return instance;
	}

	/** Schedule a debounced write; safe to call from the EDT. */
	public synchronized void rememberAsync(final MapModel map, final String nodeId) {
		if (map == null || map.getFile() == null || nodeId == null || nodeId.trim().length() == 0) {
			return;
		}
		final String key = mapKey(map.getFile());
		if (key == null) {
			return;
		}
		pendingMapKey = key;
		pendingNodeId = nodeId.trim();
		if (flushTimer != null) {
			flushTimer.cancel();
		}
		flushTimer = SysUtils.createTimer("LastSelectedNodeStore");
		flushTimer.schedule(new TimerTask() {
			public void run() {
				flushPending();
			}
		}, FLUSH_DELAY_MS);
	}

	public synchronized String get(final MapModel map) {
		if (map == null || map.getFile() == null) {
			return null;
		}
		ensureLoaded();
		final String key = mapKey(map.getFile());
		if (key == null) {
			return null;
		}
		final String id = properties.getProperty(key);
		if (id == null || id.trim().length() == 0) {
			return null;
		}
		return id.trim();
	}

	private synchronized void flushPending() {
		if (pendingMapKey == null || pendingNodeId == null) {
			return;
		}
		ensureLoaded();
		final String previous = properties.getProperty(pendingMapKey);
		if (pendingNodeId.equals(previous)) {
			pendingMapKey = null;
			pendingNodeId = null;
			return;
		}
		properties.setProperty(pendingMapKey, pendingNodeId);
		pendingMapKey = null;
		pendingNodeId = null;
		save();
	}

	private static String mapKey(final File file) {
		try {
			return file.getCanonicalPath();
		}
		catch (final Exception e) {
			return file.getAbsolutePath();
		}
	}

	private void ensureLoaded() {
		if (loaded) {
			return;
		}
		load();
		loaded = true;
	}

	private File resolveFile() {
		final File dir = MindMapDataRootResolver.getApplicationConfigDirectory();
		if (!dir.exists() && !dir.mkdirs()) {
			LogUtils.warn("Could not create last-selected-nodes dir: " + dir.getAbsolutePath());
			return null;
		}
		return new File(dir, FILE_NAME);
	}

	private void load() {
		properties.clear();
		final File file = resolveFile();
		if (file == null || !file.isFile()) {
			return;
		}
		InputStreamReader reader = null;
		try {
			reader = new InputStreamReader(new FileInputStream(file), CHARSET);
			properties.load(reader);
		}
		catch (final Exception e) {
			LogUtils.warn("Could not load last-selected-nodes state", e);
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (final Exception ignore) {
				}
			}
		}
	}

	private void save() {
		final File file = resolveFile();
		if (file == null) {
			return;
		}
		OutputStreamWriter writer = null;
		try {
			writer = new OutputStreamWriter(new FileOutputStream(file), CHARSET);
			properties.store(writer, "Docear last-selected node ids (sidecar; avoids rewriting .mm on every click)");
		}
		catch (final Exception e) {
			LogUtils.warn("Could not save last-selected-nodes state", e);
		}
		finally {
			if (writer != null) {
				try {
					writer.close();
				}
				catch (final Exception ignore) {
				}
			}
		}
	}
}
