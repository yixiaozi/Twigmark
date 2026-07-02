package org.docear.plugin.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.freeplane.core.resources.ResourceController;

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
