package org.freeplane.features.map;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Properties;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;

/**
 * Persists the last opened mind map and the last selected node id per map file under
 * {@code {dataRoot}/_data/session-view.properties}. Updated on every selection / map focus,
 * so restore works even when the {@code .mm} was not saved.
 */
public final class SessionViewStateStore {

	private static final String FILE_NAME = "session-view.properties";
	private static final String CHARSET = "UTF-8";
	private static final String KEY_LAST_MAP = "last.map";
	private static final String PREFIX_NODE = "node.";

	private static SessionViewStateStore instance;

	private final Properties properties = new Properties();
	private boolean loaded;

	private SessionViewStateStore() {
	}

	public static synchronized SessionViewStateStore getInstance() {
		if (instance == null) {
			instance = new SessionViewStateStore();
		}
		return instance;
	}

	public synchronized void setLastOpenedMap(final File mapFile) {
		final String path = normalizePath(mapFile);
		if (path == null) {
			return;
		}
		ensureLoaded();
		if (path.equals(properties.getProperty(KEY_LAST_MAP))) {
			return;
		}
		properties.setProperty(KEY_LAST_MAP, path);
		save();
	}

	public synchronized File getLastOpenedMap() {
		ensureLoaded();
		final String path = properties.getProperty(KEY_LAST_MAP);
		if (path == null || path.trim().length() == 0) {
			return null;
		}
		final File file = new File(path.trim());
		return file.isFile() ? file : null;
	}

	public synchronized void setLastSelectedNode(final File mapFile, final String nodeId) {
		final String path = normalizePath(mapFile);
		if (path == null || nodeId == null || nodeId.trim().length() == 0) {
			return;
		}
		ensureLoaded();
		final String key = PREFIX_NODE + path;
		final String value = nodeId.trim();
		if (value.equals(properties.getProperty(key))) {
			return;
		}
		properties.setProperty(key, value);
		save();
	}

	public synchronized String getLastSelectedNodeId(final File mapFile) {
		final String path = normalizePath(mapFile);
		if (path == null) {
			return null;
		}
		ensureLoaded();
		final String nodeId = properties.getProperty(PREFIX_NODE + path);
		if (nodeId == null || nodeId.trim().length() == 0) {
			return null;
		}
		return nodeId.trim();
	}

	/** Convenience: remember both last map and selection for the given node. */
	public void rememberSelection(final NodeModel node) {
		if (node == null || node.getMap() == null) {
			return;
		}
		final MapModel map = node.getMap();
		final File file = map.getFile();
		if (file == null) {
			return;
		}
		final String nodeId = node.createID();
		if (nodeId == null || nodeId.length() == 0) {
			return;
		}
		final String path = normalizePath(file);
		if (path == null) {
			return;
		}
		synchronized (this) {
			ensureLoaded();
			boolean dirty = false;
			if (!path.equals(properties.getProperty(KEY_LAST_MAP))) {
				properties.setProperty(KEY_LAST_MAP, path);
				dirty = true;
			}
			final String key = PREFIX_NODE + path;
			if (!nodeId.equals(properties.getProperty(key))) {
				properties.setProperty(key, nodeId);
				dirty = true;
			}
			if (dirty) {
				save();
			}
		}
	}

	public void rememberOpenedMap(final MapModel map) {
		if (map == null) {
			return;
		}
		setLastOpenedMap(map.getFile());
	}

	private void ensureLoaded() {
		if (loaded) {
			return;
		}
		load();
		loaded = true;
	}

	private File resolveFile() {
		File dir = MindMapDataRootResolver.getApplicationConfigDirectory();
		if (dir == null) {
			dir = new File(System.getProperty("user.home"), ".docear/_data");
		}
		if (!dir.exists() && !dir.mkdirs()) {
			LogUtils.warn("Could not create session-view dir: " + dir.getAbsolutePath());
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
			LogUtils.warn("Could not load session-view state", e);
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
			properties.store(writer, "Docear session view state (last map + last selected nodes)");
		}
		catch (final Exception e) {
			LogUtils.warn("Could not save session-view state", e);
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

	static String normalizePath(final File mapFile) {
		if (mapFile == null) {
			return null;
		}
		String path;
		try {
			path = mapFile.getCanonicalFile().getAbsolutePath();
		}
		catch (final Exception e) {
			path = mapFile.getAbsolutePath();
		}
		if (path == null || path.length() == 0) {
			return null;
		}
		path = path.replace('\\', '/');
		if (path.length() >= 2 && path.charAt(1) == ':') {
			path = Character.toLowerCase(path.charAt(0)) + path.substring(1);
		}
		return path;
	}
}
