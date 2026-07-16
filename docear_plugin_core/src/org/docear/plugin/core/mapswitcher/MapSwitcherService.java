package org.docear.plugin.core.mapswitcher;

import java.awt.EventQueue;
import java.awt.Frame;

import javax.swing.KeyStroke;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.ribbon.RibbonBuilder;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;

/**
 * Alt+Space open-map switcher (Windows system menu is overridden while Docear runs).
 */
public final class MapSwitcherService {
	private static final long DIALOG_DEBOUNCE_MS = 300L;
	private static long lastDialogOpenMs;

	private MapSwitcherService() {
	}

	public static void install(final ModeController modeController) {
		modeController.addAction(new MapSwitcherAction());
		Controller.getCurrentController().addAction(modeController.getAction(MapSwitcherAction.KEY));
		scheduleInAppAcceleratorRegistration(modeController);
	}

	public static void showDialog() {
		final long now = System.currentTimeMillis();
		synchronized (MapSwitcherService.class) {
			if (now - lastDialogOpenMs < DIALOG_DEBOUNCE_MS) {
				return;
			}
			lastDialogOpenMs = now;
		}
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MapSwitcherDialog.openDialog();
				}
				catch (Exception e) {
					LogUtils.warn("MapSwitcher dialog failed.", e);
				}
			}
		});
	}

	private static void scheduleInAppAcceleratorRegistration(final ModeController modeController) {
		EventQueue.invokeLater(new Runnable() {
			private int attempts;

			public void run() {
				final Controller controller = Controller.getCurrentController();
				if (controller == null || controller.getViewController() == null) {
					retry();
					return;
				}
				final Frame frame = controller.getViewController().getFrame();
				if (frame == null || !frame.isDisplayable()) {
					retry();
					return;
				}
				registerInAppAccelerator(modeController);
			}

			private void retry() {
				if (attempts++ < 120) {
					EventQueue.invokeLater(this);
				}
			}
		});
	}

	private static void registerInAppAccelerator(final ModeController modeController) {
		try {
			final AFreeplaneAction action = modeController.getAction(MapSwitcherAction.KEY);
			if (action == null) {
				return;
			}
			final RibbonBuilder ribbonBuilder = modeController.getUserInputListenerFactory().getRibbonBuilder();
			if (ribbonBuilder == null) {
				return;
			}
			final KeyStroke keyStroke = UITools.getKeyStroke("alt SPACE");
			if (keyStroke != null) {
				ribbonBuilder.getAcceleratorManager().setAccelerator(action, keyStroke);
			}
		}
		catch (Exception e) {
			LogUtils.warn("MapSwitcher: could not bind in-app accelerator.", e);
		}
	}
}
