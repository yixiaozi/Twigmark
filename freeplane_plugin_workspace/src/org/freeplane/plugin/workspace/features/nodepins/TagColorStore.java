package org.freeplane.plugin.workspace.features.nodepins;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;

/**
 * Persists per-tag colors under {@code {dataRoot}/_data/tag-colors.properties}.
 * Unknown tags get a stable palette color derived from the tag name.
 */
public final class TagColorStore {

	private static final String FILE_NAME = "tag-colors.properties";
	private static final String CHARSET = "UTF-8";

	/** Soft pastel palette — readable with dark text, easy to tell apart. */
	private static final Color[] PALETTE = {
			new Color(0xEF9A9A),
			new Color(0xF48FB1),
			new Color(0xCE93D8),
			new Color(0xB39DDB),
			new Color(0x9FA8DA),
			new Color(0x81D4FA),
			new Color(0x80DEEA),
			new Color(0x80CBC4),
			new Color(0xA5D6A7),
			new Color(0xC5E1A5),
			new Color(0xFFE082),
			new Color(0xFFCC80),
			new Color(0xFFAB91),
			new Color(0xBCAAA4),
			new Color(0xB0BEC5)
	};

	private static final Color NEUTRAL = new Color(0xE0E0E0);
	private static final Color SELECTED_BORDER = new Color(0x455A64);

	private static TagColorStore instance;

	private final Map colors = Collections.synchronizedMap(new LinkedHashMap());
	private boolean loaded;

	private TagColorStore() {
	}

	public static synchronized TagColorStore getInstance() {
		if (instance == null) {
			instance = new TagColorStore();
		}
		return instance;
	}

	public Color getNeutralColor() {
		return NEUTRAL;
	}

	public Color getSelectedBorderColor() {
		return SELECTED_BORDER;
	}

	/** Returns a display color for the tag; auto-assigns and persists if missing. */
	public Color getColor(final String tag) {
		ensureLoaded();
		if (tag == null || tag.length() == 0) {
			return NEUTRAL;
		}
		synchronized (colors) {
			final Color existing = (Color) colors.get(tag);
			if (existing != null) {
				return existing;
			}
			final Color assigned = paletteColorFor(tag);
			colors.put(tag, assigned);
			save();
			return assigned;
		}
	}

	public void setColor(final String tag, final Color color) {
		if (tag == null || tag.length() == 0 || color == null) {
			return;
		}
		ensureLoaded();
		synchronized (colors) {
			colors.put(tag, color);
			save();
		}
	}

	public void clearColor(final String tag) {
		if (tag == null) {
			return;
		}
		ensureLoaded();
		synchronized (colors) {
			if (colors.remove(tag) != null) {
				save();
			}
		}
	}

	public static Color contrastingTextColor(final Color background) {
		if (background == null) {
			return Color.BLACK;
		}
		final double luminance = (0.299 * background.getRed() + 0.587 * background.getGreen()
				+ 0.114 * background.getBlue()) / 255.0;
		return luminance > 0.62 ? new Color(0x212121) : Color.WHITE;
	}

	public static Color darkerVariant(final Color color, final float factor) {
		if (color == null) {
			return NEUTRAL;
		}
		final float f = Math.max(0f, Math.min(1f, factor));
		return new Color(Math.max(0, (int) (color.getRed() * f)), Math.max(0, (int) (color.getGreen() * f)),
				Math.max(0, (int) (color.getBlue() * f)));
	}

	private static Color paletteColorFor(final String tag) {
		int hash = tag.hashCode();
		if (hash == Integer.MIN_VALUE) {
			hash = 0;
		}
		final int index = Math.abs(hash) % PALETTE.length;
		return PALETTE[index];
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
			LogUtils.warn("Could not create tag color dir: " + dir.getAbsolutePath());
			return null;
		}
		return new File(dir, FILE_NAME);
	}

	private void load() {
		colors.clear();
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
				final String tag = (String) it.next();
				final Color color = parseHex(props.getProperty(tag));
				if (color != null) {
					colors.put(tag, color);
				}
			}
		}
		catch (final Exception e) {
			LogUtils.warn("Could not load tag colors from " + file.getAbsolutePath(), e);
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
		synchronized (colors) {
			for (final Iterator it = colors.entrySet().iterator(); it.hasNext();) {
				final Map.Entry entry = (Map.Entry) it.next();
				final Color color = (Color) entry.getValue();
				if (color != null) {
					props.setProperty((String) entry.getKey(), toHex(color));
				}
			}
		}
		OutputStreamWriter writer = null;
		try {
			writer = new OutputStreamWriter(new FileOutputStream(file), CHARSET);
			props.store(writer, "Docear tag filter colors (tag=#RRGGBB)");
		}
		catch (final Exception e) {
			LogUtils.warn("Could not save tag colors to " + file.getAbsolutePath(), e);
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
