package org.freeplane.view.swing.features.clipboardhistory;

import java.io.File;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;

/**
 * Settings for text-only clipboard history (SQLite under the working-directory data folder).
 */
public final class ClipboardHistoryConfig {
	private static final String PREFIX = "clipboard.history.";

	private ClipboardHistoryConfig() {
	}

	public static boolean isEnabled() {
		return getBoolean("enabled", true);
	}

	/**
	 * Max unique rows to keep. {@code 0} (default) means unlimited — never prune.
	 * Positive values still prune oldest by {@code last_ts}.
	 */
	public static int getMaxRows() {
		final int n = getInt("maxRows", 0);
		if (n <= 0) {
			return 0;
		}
		return n;
	}

	/** {@code true} when history is never pruned by row count. */
	public static boolean isUnlimitedRows() {
		return getMaxRows() <= 0;
	}

	public static int getMaxTextLength() {
		final int n = getInt("maxTextLength", 8000);
		if (n < 200) {
			return 200;
		}
		if (n > 100000) {
			return 100000;
		}
		return n;
	}

	public static int getPollMs() {
		final int n = getInt("pollMs", 800);
		if (n < 300) {
			return 300;
		}
		if (n > 5000) {
			return 5000;
		}
		return n;
	}

	public static File getDbFile() {
		final String configured = getString("dbPath", "");
		if (configured.length() > 0) {
			return new File(configured);
		}
		return new File(Compat.getApplicationUserDirectory(), "clipboard_history.db");
	}

	public static void setEnabled(final boolean enabled) {
		setProperty("enabled", enabled ? "true" : "false");
	}

	private static void setProperty(final String key, final String value) {
		try {
			ResourceController.getResourceController().setProperty(PREFIX + key, value == null ? "" : value);
		}
		catch (Exception e) {
		}
	}

	private static String getString(final String key, final String defaultValue) {
		try {
			final String value = ResourceController.getResourceController().getProperty(PREFIX + key, null);
			if (value != null && value.trim().length() > 0) {
				return value.trim();
			}
		}
		catch (Exception e) {
		}
		return defaultValue;
	}

	private static boolean getBoolean(final String key, final boolean defaultValue) {
		final String value = getString(key, null);
		if (value == null) {
			return defaultValue;
		}
		return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
	}

	private static int getInt(final String key, final int defaultValue) {
		final String value = getString(key, null);
		if (value == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value);
		}
		catch (Exception e) {
			return defaultValue;
		}
	}
}
