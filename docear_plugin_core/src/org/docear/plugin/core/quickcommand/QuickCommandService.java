package org.docear.plugin.core.quickcommand;

import java.awt.EventQueue;
import java.awt.Frame;

import javax.swing.KeyStroke;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.ribbon.RibbonBuilder;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.McpHeadlessFlags;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;

public final class QuickCommandService {
	private static final long DIALOG_DEBOUNCE_MS = 400L;
	private static long lastDialogOpenMs;

	private QuickCommandService() {
	}

	public static void install(final ModeController modeController) {
		modeController.addAction(new QuickCommandAction());
		Controller.getCurrentController().addAction(modeController.getAction(QuickCommandAction.KEY));
		scheduleInAppAcceleratorRegistration(modeController);
		if (McpHeadlessFlags.isLeanMemory()) {
			LogUtils.info("QuickCommand: lean memory — skip full-library node/icon/file warmup");
			return;
		}
		// Warm map/launch indexes in background after startup.
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				final Thread warm = new Thread(new Runnable() {
					public void run() {
						try {
							QuickCommandIndex.getInstance().ensureMaps();
							QuickCommandIndex.getInstance().ensureLaunch();
							QuickCommandIndex.getInstance().ensureIconsAsync();
							QuickCommandIndex.getInstance().ensureFilesAsync();
							QuickCommandIndex.getInstance().ensureAllNodesAsync();
						}
						catch (Exception e) {
							LogUtils.warn("QuickCommand: warm index failed.", e);
						}
					}
				}, "QuickCommand-WarmIndex");
				warm.setDaemon(true);
				warm.start();
			}
		});
	}

	public static void showDialog() {
		final long now = System.currentTimeMillis();
		synchronized (QuickCommandService.class) {
			if (now - lastDialogOpenMs < DIALOG_DEBOUNCE_MS) {
				return;
			}
			lastDialogOpenMs = now;
		}
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					QuickCommandDialog.openDialog();
				}
				catch (Exception e) {
					LogUtils.warn("QuickCommand dialog failed.", e);
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
			final AFreeplaneAction action = modeController.getAction(QuickCommandAction.KEY);
			if (action == null) {
				return;
			}
			final RibbonBuilder ribbonBuilder = modeController.getUserInputListenerFactory().getRibbonBuilder();
			if (ribbonBuilder == null) {
				return;
			}
			// Reclaim Shift+Space from ShowNextChild (same pattern as QuickCapture vs presentation).
			final AFreeplaneAction nextChild = modeController.getAction("ShowNextChildAction");
			if (nextChild == null) {
				final AFreeplaneAction globalNextChild = Controller.getCurrentController()
				        .getAction("ShowNextChildAction");
				if (globalNextChild != null) {
					ribbonBuilder.getAcceleratorManager().setAccelerator(globalNextChild, null);
				}
			}
			else {
				ribbonBuilder.getAcceleratorManager().setAccelerator(nextChild, null);
			}
			final KeyStroke keyStroke = UITools.getKeyStroke("shift SPACE");
			if (keyStroke != null) {
				ribbonBuilder.getAcceleratorManager().setAccelerator(action, keyStroke);
			}
		}
		catch (Exception e) {
			LogUtils.warn("QuickCommand: could not bind in-app accelerator.", e);
		}
	}
}
