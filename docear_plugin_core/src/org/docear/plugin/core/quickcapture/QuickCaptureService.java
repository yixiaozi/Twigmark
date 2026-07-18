package org.docear.plugin.core.quickcapture;

import java.awt.EventQueue;
import java.awt.Frame;
import javax.swing.KeyStroke;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.ribbon.RibbonBuilder;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;

public final class QuickCaptureService {
	private static final String PROP_INBOX_DIRECTORY = "quickcapture.inbox_directory";
	private static final String PROP_INBOX_FILENAME = "quickcapture.inbox_filename";
	private static final long DIALOG_DEBOUNCE_MS = 400L;
	private static long lastDialogOpenMs;

	private QuickCaptureService() {
	}

	public static void install(final ModeController modeController) {
		final ResourceController resources = ResourceController.getResourceController();
		// Empty directory = resolve from library / scan root at capture time (portable).
		resources.setDefaultProperty(PROP_INBOX_DIRECTORY, "");
		resources.setDefaultProperty(PROP_INBOX_FILENAME, "\u6536\u4ef6\u7bb1.mm");

		modeController.addAction(new QuickCaptureAction());
		Controller.getCurrentController().addAction(modeController.getAction(QuickCaptureAction.KEY));

		if (Compat.isWindowsOS()) {
			QuickCaptureHotkey.registerWhenReady();
		}
		scheduleInAppAcceleratorRegistration(modeController);
	}

	public static void showCaptureDialog() {
		final long now = System.currentTimeMillis();
		synchronized (QuickCaptureService.class) {
			if (now - lastDialogOpenMs < DIALOG_DEBOUNCE_MS) {
				return;
			}
			lastDialogOpenMs = now;
		}
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					QuickCaptureDialog.openDialog();
				}
				catch (Exception e) {
					LogUtils.warn("QuickCapture dialog failed.", e);
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
			final AFreeplaneAction action = modeController.getAction(QuickCaptureAction.KEY);
			if (action == null) {
				return;
			}
			final RibbonBuilder ribbonBuilder = modeController.getUserInputListenerFactory().getRibbonBuilder();
			if (ribbonBuilder == null) {
				return;
			}
			final AFreeplaneAction presentationAction = Controller.getCurrentController().getAction(
			        "NextPresentationItemAction");
			if (presentationAction != null) {
				ribbonBuilder.getAcceleratorManager().setAccelerator(presentationAction, null);
			}
			final KeyStroke keyStroke = UITools.getKeyStroke("control shift SPACE");
			if (keyStroke != null) {
				ribbonBuilder.getAcceleratorManager().setAccelerator(action, keyStroke);
			}
		}
		catch (Exception e) {
			LogUtils.warn("QuickCapture: could not bind in-app accelerator.", e);
		}
	}
}
