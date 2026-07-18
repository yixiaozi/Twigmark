package org.docear.plugin.mcp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;

public final class DocearMcpConfig {
	private static final String PREFIX = "mcp.";
	private static final String DEFAULT_HOST = "127.0.0.1";
	private static final int DEFAULT_PORT = 7720;

	private DocearMcpConfig() {
	}

	public static boolean isEnabled() {
		return getBoolean("enabled", true);
	}

	public static String getHost() {
		return getString("host", DEFAULT_HOST);
	}

	public static int getPort() {
		return getInt("port", DEFAULT_PORT);
	}

	public static boolean isReadOnly() {
		return getBoolean("readonly", false);
	}

	public static boolean isAuditEnabled() {
		return getBoolean("audit.enabled", true);
	}

	public static int getAuditMaxEntries() {
		return getInt("audit.maxEntries", 1000);
	}

	public static File getAuditDataDir() {
		final String configured = getString("audit.dataDir", "");
		if (configured.length() > 0) {
			return new File(configured);
		}
		// Profile is already {workingDirectory}/data — audit.db lives there.
		return new File(Compat.getApplicationUserDirectory());
	}

	public static File getAuditDbFile() {
		final String configured = getString("audit.dbPath", "");
		if (configured.length() > 0) {
			return new File(configured);
		}
		return new File(getAuditDataDir(), "audit.db");
	}

	public static int getAuditQueueSize() {
		return getInt("audit.queueSize", 5000);
	}

	public static int getAuditBatchSize() {
		final int batch = getInt("audit.batchSize", 200);
		if (batch < 10) {
			return 10;
		}
		if (batch > 500) {
			return 500;
		}
		return batch;
	}

	public static int getAuditMaxResponseBytes() {
		return getInt("audit.maxResponseBytes", 524288);
	}

	public static boolean isCursorPluginSyncEnabled() {
		return getBoolean("cursorPlugin.sync.enabled", true);
	}

	public static int getCursorPluginSyncDelayMs() {
		return getInt("cursorPlugin.sync.delayMs", 12000);
	}

	public static void setEnabled(final boolean enabled) {
		setProperty("enabled", enabled ? "true" : "false");
	}

	public static void setHost(final String host) {
		setProperty("host", host == null || host.trim().length() == 0 ? DEFAULT_HOST : host.trim());
	}

	public static void setPort(final int port) {
		final int safe = port < 1 || port > 65535 ? DEFAULT_PORT : port;
		setProperty("port", String.valueOf(safe));
	}

	public static void setReadOnly(final boolean readOnly) {
		setProperty("readonly", readOnly ? "true" : "false");
	}

	public static void setCursorPluginSyncEnabled(final boolean enabled) {
		setProperty("cursorPlugin.sync.enabled", enabled ? "true" : "false");
	}

	private static void setProperty(final String key, final String value) {
		try {
			ResourceController.getResourceController().setProperty(PREFIX + key, value == null ? "" : value);
		}
		catch (Exception e) {
			// ignore when controller unavailable
		}
	}

	private static String getString(final String key, final String defaultValue) {
		final String value = ResourceController.getResourceController().getProperty(PREFIX + key, null);
		if (value != null && value.trim().length() > 0) {
			return value.trim();
		}
		return loadDefaults().getProperty(key, defaultValue);
	}

	private static boolean getBoolean(final String key, final boolean defaultValue) {
		final String value = getString(key, defaultValue ? "true" : "false");
		return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
	}

	private static int getInt(final String key, final int defaultValue) {
		try {
			return Integer.parseInt(getString(key, String.valueOf(defaultValue)));
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static Properties loadDefaults() {
		final Properties properties = new Properties();
		InputStream stream = DocearMcpConfig.class.getClassLoader().getResourceAsStream("mcp.properties");
		if (stream != null) {
			try {
				properties.load(stream);
			}
			catch (IOException e) {
				// ignore
			}
			finally {
				try {
					stream.close();
				}
				catch (IOException e) {
					// ignore
				}
			}
		}
		return properties;
	}
}
