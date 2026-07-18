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
		resources.setDefaultProperty("clipboard.history.maxRows", "1000");
		resources.setDefaultProperty("clipboard.history.maxTextLength", "8000");
		resources.setDefaultProperty("clipboard.history.pollMs", "800");
		try {
			ClipboardHistoryMonitor.start();
		}
		catch (Throwable t) {
			LogUtils.warn("ClipboardHistoryMonitor failed to start", t);
		}
		modeController.addExtension(ClipboardHistoryController.class, new ClipboardHistoryController());
		LogUtils.info("ClipboardHistoryController installed");
	}
}
