package org.freeplane.plugin.workspace.features.colors;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.plugin.workspace.io.IFileSystemRepresentation;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;

/**
 * Persists per-workspace-item text and folder colors under
 * {@code {dataRoot}/workspace-item-colors.properties}.
 */
public final class WorkspaceItemColorStore {

	private static final String FILE_NAME = "workspace-item-colors.properties";
	private static final String CHARSET = "UTF-8";
	private static final String PREFIX_TEXT = "text.";
	private static final String PREFIX_FOLDER = "folder.";

	private static WorkspaceItemColorStore instance;

	private final Map textColors = Collections.synchronizedMap(new LinkedHashMap());
	private final Map folderColors = Collections.synchronizedMap(new LinkedHashMap());
	private boolean loaded;
	private final List changeListeners = Collections.synchronizedList(new ArrayList());

	private WorkspaceItemColorStore() {
	}

	public static synchronized WorkspaceItemColorStore getInstance() {
		if (instance == null) {
			instance = new WorkspaceItemColorStore();
		}
		return instance;
	}

	/** Stable key for a tree node: absolute file URI when available, else tree path. */
	public static String keyFor(final AWorkspaceTreeNode node) {
		if (node == null) {
			return null;
		}
		if (node instanceof IFileSystemRepresentation) {
			final File file = ((IFileSystemRepresentation) node).getFile();
			if (file != null) {
				try {
					return file.getCanonicalFile().toURI().toString();
				}
				catch (final Exception e) {
					return file.getAbsoluteFile().toURI().toString();
				}
			}
		}
		return "node:" + node.getKey();
	}

	public Color getTextColor(final String key) {
		ensureLoaded();
		if (key == null || key.length() == 0) {
			return null;
		}
		synchronized (textColors) {
			return (Color) textColors.get(key);
		}
	}

	public Color getFolderColor(final String key) {
		ensureLoaded();
		if (key == null || key.length() == 0) {
			return null;
		}
		synchronized (folderColors) {
			return (Color) folderColors.get(key);
		}
	}

	public boolean hasAnyColor(final String key) {
		return getTextColor(key) != null || getFolderColor(key) != null;
	}

	public void setTextColor(final String key, final Color color) {
		if (key == null || key.length() == 0 || color == null) {
			return;
		}
		ensureLoaded();
		synchronized (textColors) {
			textColors.put(key, color);
			save();
		}
		fireChanged();
	}

	public void setFolderColor(final String key, final Color color) {
		if (key == null || key.length() == 0 || color == null) {
			return;
		}
		ensureLoaded();
		synchronized (folderColors) {
			folderColors.put(key, color);
			save();
		}
		fireChanged();
	}

	public void clearTextColor(final String key) {
		if (key == null) {
			return;
		}
		ensureLoaded();
		boolean removed = false;
		synchronized (textColors) {
			if (textColors.remove(key) != null) {
				save();
				removed = true;
			}
		}
		if (removed) {
			fireChanged();
		}
	}

	public void clearFolderColor(final String key) {
		if (key == null) {
			return;
		}
		ensureLoaded();
		boolean removed = false;
		synchronized (folderColors) {
			if (folderColors.remove(key) != null) {
				save();
				removed = true;
			}
		}
		if (removed) {
			fireChanged();
		}
	}

	public void clearAllColors(final String key) {
		if (key == null) {
			return;
		}
		ensureLoaded();
		boolean removed = false;
		synchronized (textColors) {
			if (textColors.remove(key) != null) {
				removed = true;
			}
		}
		synchronized (folderColors) {
			if (folderColors.remove(key) != null) {
				removed = true;
			}
		}
		if (removed) {
			save();
			fireChanged();
		}
	}

	public void addChangeListener(final Runnable listener) {
		if (listener != null) {
			changeListeners.add(listener);
		}
	}

	private void fireChanged() {
		final Object[] listeners = changeListeners.toArray();
		for (int i = 0; i < listeners.length; i++) {
			try {
				((Runnable) listeners[i]).run();
			}
			catch (final Exception ignore) {
			}
		}
	}

	/** Soft wash suitable as tree-row background. */
	public static Color washBackground(final Color color) {
		if (color == null) {
			return null;
		}
		return new Color(
				blendChannel(color.getRed(), 255, 0.78f),
				blendChannel(color.getGreen(), 255, 0.78f),
				blendChannel(color.getBlue(), 255, 0.78f));
	}

	private static int blendChannel(final int c, final int toward, final float amount) {
		return Math.max(0, Math.min(255, Math.round(c + (toward - c) * amount)));
	}

	private void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (this) {
			if (loaded) {
				return;
			}
			load();
			loaded = true;
		}
	}

	private File resolveFile() {
		final File dir = MindMapDataRootResolver.getApplicationConfigDirectory();
		if (dir == null) {
			return null;
		}
		if (!dir.exists() && !dir.mkdirs()) {
			LogUtils.warn("Could not create workspace item color dir: " + dir.getAbsolutePath());
			return null;
		}
		return new File(dir, FILE_NAME);
	}

	private void load() {
		textColors.clear();
		folderColors.clear();
		final File file = resolveFile();
		if (file == null || !file.isFile()) {
			return;
		}
		final Properties props = new Properties();
		InputStreamReader reader = null;
		try {
			reader = new InputStreamReader(new FileInputStream(file), CHARSET);
			props.load(reader);
			for (final Iterator it = props.keySet().iterator(); it.hasNext();) {
				final String propKey = (String) it.next();
				final Color color = parseHex(props.getProperty(propKey));
				if (color == null) {
					continue;
				}
				if (propKey.startsWith(PREFIX_TEXT)) {
					textColors.put(propKey.substring(PREFIX_TEXT.length()), color);
				}
				else if (propKey.startsWith(PREFIX_FOLDER)) {
					folderColors.put(propKey.substring(PREFIX_FOLDER.length()), color);
				}
			}
		}
		catch (final Exception e) {
			LogUtils.warn("Could not load workspace item colors from " + file.getAbsolutePath(), e);
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
		final Properties props = new Properties();
		synchronized (textColors) {
			for (final Iterator it = textColors.entrySet().iterator(); it.hasNext();) {
				final Map.Entry entry = (Map.Entry) it.next();
				final Color color = (Color) entry.getValue();
				if (color != null) {
					props.setProperty(PREFIX_TEXT + entry.getKey(), toHex(color));
				}
			}
		}
		synchronized (folderColors) {
			for (final Iterator it = folderColors.entrySet().iterator(); it.hasNext();) {
				final Map.Entry entry = (Map.Entry) it.next();
				final Color color = (Color) entry.getValue();
				if (color != null) {
					props.setProperty(PREFIX_FOLDER + entry.getKey(), toHex(color));
				}
			}
		}
		OutputStreamWriter writer = null;
		try {
			writer = new OutputStreamWriter(new FileOutputStream(file), CHARSET);
			props.store(writer, "Docear workspace item colors (text.|folder.<uri>=#RRGGBB)");
		}
		catch (final Exception e) {
			LogUtils.warn("Could not save workspace item colors to " + file.getAbsolutePath(), e);
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

	public static String toHex(final Color color) {
		final int rgb = color.getRGB() & 0xFFFFFF;
		String hex = Integer.toHexString(rgb).toUpperCase();
		while (hex.length() < 6) {
			hex = "0" + hex;
		}
		return "#" + hex;
	}

	public static Color parseHex(final String value) {
		if (value == null) {
			return null;
		}
		String hex = value.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		if (hex.length() != 6) {
			return null;
		}
		try {
			return new Color(Integer.parseInt(hex, 16));
		}
		catch (final NumberFormatException e) {
			return null;
		}
	}
}
