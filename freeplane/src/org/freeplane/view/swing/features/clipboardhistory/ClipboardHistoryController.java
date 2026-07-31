package org.freeplane.view.swing.features.clipboardhistory;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.ModeController;

/**
 * Installs clipboard history monitor and default properties.
 */
public final class ClipboardHistoryController implements IExtension {
	private ClipboardHistoryController() {
	}

	public static void install(final ModeController modeController) {
		final ResourceController resources = ResourceController.getResourceController();
		resources.setDefaultProperty("clipboard.history.enabled", "true");
		// 0 = unlimited unique texts (no prune). Old default was 1000.
		resources.setDefaultProperty("clipboard.history.maxRows", "0");
		resources.setDefaultProperty("clipboard.history.maxTextLength", "8000");
		resources.setDefaultProperty("clipboard.history.pollMs", "800");
		migrateLegacyMaxRowsCap(resources);
		try {
			ClipboardHistoryMonitor.start();
		}
		catch (Throwable t) {
			LogUtils.warn("ClipboardHistoryMonitor failed to start", t);
		}
		modeController.addExtension(ClipboardHistoryController.class, new ClipboardHistoryController());
		LogUtils.info("ClipboardHistoryController installed");
	}

	/**
	 * Users who only ever had the old shipped default (1000) get unlimited storage.
	 * Explicit custom values are left alone.
	 */
	private static void migrateLegacyMaxRowsCap(final ResourceController resources) {
		try {
			final String raw = resources.getProperty("clipboard.history.maxRows", null);
			if (raw != null && "1000".equals(raw.trim())) {
				resources.setProperty("clipboard.history.maxRows", "0");
				LogUtils.info("Clipboard history maxRows migrated from 1000 → unlimited (0)");
			}
		}
		catch (Exception e) {
		}
	}
}
