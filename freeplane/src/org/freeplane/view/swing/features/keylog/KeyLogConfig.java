package org.freeplane.view.swing.features.keylog;

import java.io.File;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LocalMachineId;

/**
 * Settings for keystroke logging (SQLite under the working-directory data folder).
 */
public final class KeyLogConfig {
	private static final String PREFIX = "keylog.";

	private KeyLogConfig() {
	}

	public static boolean isEnabled() {
		return getBoolean("enabled", true);
	}

	/** Idle gap that starts a new session (ms). */
	public static int getSessionGapMs() {
		final int n = getInt("sessionGapMs", 30000);
		if (n < 3000) {
			return 3000;
		}
		if (n > 300000) {
			return 300000;
		}
		return n;
	}

	/** Flush pending keys to DB at least this often (ms). */
	public static int getFlushMs() {
		final int n = getInt("flushMs", 2000);
		if (n < 500) {
			return 500;
		}
		if (n > 15000) {
			return 15000;
		}
		return n;
	}

	/** Flush when this many keys are buffered. */
	public static int getFlushKeys() {
		final int n = getInt("flushKeys", 200);
		if (n < 20) {
			return 20;
		}
		if (n > 5000) {
			return 5000;
		}
		return n;
	}

	/** Rotate writable DB when at/over this size (MB). 0 = never. */
	public static int getMaxMb() {
		final int n = getInt("maxMb", 30);
		if (n <= 0) {
			return 0;
		}
		return n;
	}

	public static File getDataDir() {
		return new File(Compat.getApplicationUserDirectory());
	}

	/** Local writable DB for this PC. */
	public static File getDbFile() {
		final String configured = getString("dbPath", "");
		if (configured.length() > 0) {
			return new File(configured);
		}
		final File dir = getDataDir();
		if (!dir.exists()) {
			dir.mkdirs();
		}
		return LocalMachineId.migrateLegacyFile(dir, "keylog.db", "keylog", ".db");
	}

	/** This PC + import/archive shards. */
	public static File[] listDbFiles() {
		final String configured = getString("dbPath", "");
		if (configured.length() > 0) {
			return new File[] { new File(configured) };
		}
		return LocalMachineId.listMachineFiles(getDataDir(), "keylog", ".db", "keylog.db");
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
			return Integer.parseInt(value.trim());
		}
		catch (Exception e) {
			return defaultValue;
		}
	}
}
