package org.freeplane.features.map;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;

/**
 * Crash-safe session of currently open mind maps.
 * <p>
 * Stored as {@code {workingDirectory}/data/session-open-maps.properties} and rewritten on every
 * open / close / focus change so a crash still restores the full open set next launch.
 * During application quit the store is {@link #freeze() frozen} after the last good
 * snapshot so closing tabs one-by-one cannot wipe the file to {@code open.count=0}.
 * Does not store last-selected node ids (those live in each {@code .mm} as
 * {@code last_selected_id}).
 */
public final class SessionOpenMapsStore {

	private static final String FILE_NAME = "session-open-maps.properties";
	private static final String CHARSET = "UTF-8";
	private static final String KEY_LAST_MAP = "last.map";
	private static final String KEY_OPEN_COUNT = "open.count";
	private static final String PREFIX_OPEN = "open.";

	private static SessionOpenMapsStore instance;

	private final Properties properties = new Properties();
	private boolean loaded;
	/** Once true, refuse further writes so quit-time tab closes cannot wipe the list. */
	private volatile boolean frozen;

	private SessionOpenMapsStore() {
	}

	public static synchronized SessionOpenMapsStore getInstance() {
		if (instance == null) {
			instance = new SessionOpenMapsStore();
		}
		return instance;
	}

	/**
	 * Stop accepting updates. Call after the last good open-map snapshot is written
	 * during application shutdown, before tabs are closed one-by-one.
	 */
	public void freeze() {
		frozen = true;
	}

	public boolean isFrozen() {
		return frozen;
	}

	/** Replace the open-map list and optionally the focused map; writes to disk immediately. */
	public synchronized void saveOpenMaps(final List<String> restoreables, final String lastRestoreable) {
		if (frozen) {
			return;
		}
		ensureLoaded();
		final List<String> cleaned = cleanList(restoreables);
		final String last = lastRestoreable != null && lastRestoreable.trim().length() > 0
				? lastRestoreable.trim() : null;

		boolean dirty = false;
		final int oldCount = parseCount(properties.getProperty(KEY_OPEN_COUNT));
		if (oldCount != cleaned.size()) {
			dirty = true;
		}
		else {
			for (int i = 0; i < cleaned.size(); i++) {
				if (!cleaned.get(i).equals(properties.getProperty(PREFIX_OPEN + i))) {
					dirty = true;
					break;
				}
			}
		}
		final String oldLast = properties.getProperty(KEY_LAST_MAP);
		if (last == null ? oldLast != null : !last.equals(oldLast)) {
			dirty = true;
		}
		if (!dirty) {
			return;
		}

		clearOpenEntries();
		properties.setProperty(KEY_OPEN_COUNT, Integer.toString(cleaned.size()));
		for (int i = 0; i < cleaned.size(); i++) {
			properties.setProperty(PREFIX_OPEN + i, cleaned.get(i));
		}
		if (last != null) {
			properties.setProperty(KEY_LAST_MAP, last);
		}
		else {
			properties.remove(KEY_LAST_MAP);
		}
		save();
	}

	public synchronized List<String> getOpenMaps() {
		ensureLoaded();
		final int count = parseCount(properties.getProperty(KEY_OPEN_COUNT));
		if (count <= 0) {
			return Collections.emptyList();
		}
		final List<String> result = new ArrayList<String>(count);
		final LinkedHashSet<String> seen = new LinkedHashSet<String>();
		for (int i = 0; i < count; i++) {
			final String value = properties.getProperty(PREFIX_OPEN + i);
			if (value == null || value.trim().length() == 0) {
				continue;
			}
			final String trimmed = value.trim();
			if (seen.add(trimmed)) {
				result.add(trimmed);
			}
		}
		return result;
	}

	public synchronized String getLastMap() {
		ensureLoaded();
		final String last = properties.getProperty(KEY_LAST_MAP);
		if (last == null || last.trim().length() == 0) {
			return null;
		}
		return last.trim();
	}

	public synchronized boolean hasOpenMaps() {
		return !getOpenMaps().isEmpty();
	}

	private void clearOpenEntries() {
		final int oldCount = parseCount(properties.getProperty(KEY_OPEN_COUNT));
		for (int i = 0; i < Math.max(oldCount, 64); i++) {
			properties.remove(PREFIX_OPEN + i);
		}
		properties.remove(KEY_OPEN_COUNT);
	}

	private static List<String> cleanList(final List<String> restoreables) {
		final List<String> cleaned = new ArrayList<String>();
		if (restoreables == null) {
			return cleaned;
		}
		final LinkedHashSet<String> seen = new LinkedHashSet<String>();
		for (int i = 0; i < restoreables.size(); i++) {
			final String value = restoreables.get(i);
			if (value == null || value.trim().length() == 0) {
				continue;
			}
			final String trimmed = value.trim();
			if (seen.add(trimmed)) {
				cleaned.add(trimmed);
			}
		}
		return cleaned;
	}

	private static int parseCount(final String value) {
		if (value == null || value.trim().length() == 0) {
			return 0;
		}
		try {
			return Math.max(0, Integer.parseInt(value.trim()));
		}
		catch (NumberFormatException e) {
			return 0;
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
			LogUtils.warn("Could not create session-open-maps dir: " + dir.getAbsolutePath());
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
			LogUtils.warn("Could not load session-open-maps state", e);
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
			properties.store(writer, "Docear open maps session (crash-safe; rewritten on every map open/close/focus)");
		}
		catch (final Exception e) {
			LogUtils.warn("Could not save session-open-maps state", e);
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
