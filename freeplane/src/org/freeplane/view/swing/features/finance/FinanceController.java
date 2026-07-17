package org.freeplane.view.swing.features.finance;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.ModeController;

/**
 * Installs personal-finance persistence and viewport helpers.
 */
public final class FinanceController implements IExtension {
	private FinanceController() {
	}

	public static void install(final ModeController modeController) {
		FinanceIO.install(modeController);
		try {
			FinanceViewportService.install(modeController);
		}
		catch (Throwable t) {
			LogUtils.warn("FinanceViewportService install skipped", t);
		}
		modeController.addExtension(FinanceController.class, new FinanceController());
		LogUtils.info("FinanceController installed");
	}
}
