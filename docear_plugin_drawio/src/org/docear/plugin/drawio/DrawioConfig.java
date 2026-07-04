package org.docear.plugin.drawio;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;

public final class DrawioConfig {

	public static final String PROP_ENABLED = "drawio.enabled";
	public static final String PROP_EMBED_URL = "drawio.embed.url";

	private static final Properties DEFAULTS = new Properties();

	static {
		loadDefaults();
	}

	private DrawioConfig() {
	}

	private static void loadDefaults() {
		InputStream in = DrawioConfig.class.getResourceAsStream("/drawio.properties");
		if (in != null) {
			try {
				DEFAULTS.load(in);
			}
			catch (IOException e) {
				LogUtils.warn(e);
			}
			finally {
				try {
					in.close();
				}
				catch (IOException e) {
					// ignore
				}
			}
		}
	}

	public static boolean isEnabled() {
		final String value = ResourceController.getResourceController().getProperty(PROP_ENABLED);
		if (value != null) {
			return Boolean.parseBoolean(value);
		}
		return Boolean.parseBoolean(DEFAULTS.getProperty(PROP_ENABLED, "true"));
	}

	public static String getEmbedUrl() {
		final String configured = ResourceController.getResourceController().getProperty(PROP_EMBED_URL);
		if (configured != null && configured.trim().length() > 0) {
			return configured.trim();
		}
		return DEFAULTS.getProperty(PROP_EMBED_URL, "https://embed.diagrams.net/");
	}
}
