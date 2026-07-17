package org.docear.plugin.core.calendar;

import java.awt.EventQueue;
import java.awt.Frame;

import javax.swing.KeyStroke;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.ribbon.RibbonBuilder;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;

/**
 * Registers calendar action (map-tab exit is chained from RelationshipGraphIntegration).
 */
public final class CalendarViewportIntegration {
	private CalendarViewportIntegration() {
	}

	public static void install(final MModeController modeController) {
		CalendarViewportService.install(modeController);
		modeController.addAction(new CalendarViewportAction());
		Controller.getCurrentController().addAction(modeController.getAction(CalendarViewportAction.KEY));
		scheduleInAppAccelerator(modeController);
	}

	private static void scheduleInAppAccelerator(final MModeController modeController) {
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
				registerAccelerator(modeController);
			}

			private void retry() {
				if (attempts++ < 120) {
					EventQueue.invokeLater(this);
				}
			}
		});
	}

	private static void registerAccelerator(final MModeController modeController) {
		try {
			final AFreeplaneAction action = modeController.getAction(CalendarViewportAction.KEY);
			if (action == null) {
				return;
			}
			final RibbonBuilder ribbonBuilder = modeController.getUserInputListenerFactory().getRibbonBuilder();
			if (ribbonBuilder == null) {
				return;
			}
			final KeyStroke keyStroke = UITools.getKeyStroke("control shift C");
			if (keyStroke != null) {
				ribbonBuilder.getAcceleratorManager().setAccelerator(action, keyStroke);
			}
		}
		catch (Exception e) {
			LogUtils.warn("CalendarViewport: could not bind accelerator.", e);
		}
	}
}
