package org.freeplane.features.usagestats;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.view.swing.features.reports.ReportDocumentService;
import org.freeplane.view.swing.features.reports.ReportViewportService;

/**
 * Shows usage statistics in a bottom document tab (mind-map area).
 */
public class UsageStatsReportService implements IExtension {
	private UsageStatsReportPanel viewportPanel;

	public UsageStatsReportService() {
	}

	private UsageStatsReportPanel getViewportPanel() {
		if (viewportPanel == null) {
			viewportPanel = new UsageStatsReportPanel();
			viewportPanel.setOnClose(new Runnable() {
				public void run() {
					setReportVisible(false);
				}
			});
		}
		return viewportPanel;
	}

	public static UsageStatsReportService get() {
		final Controller controller = Controller.getCurrentController();
		if (controller == null || !(controller.getModeController() instanceof MModeController)) {
			return null;
		}
		return controller.getModeController().getExtension(UsageStatsReportService.class);
	}

	public static void install(final MModeController modeController) {
		final UsageStatsReportService service = new UsageStatsReportService();
		modeController.addExtension(UsageStatsReportService.class, service);
		modeController.addAction(new ToggleUsageStatsReportAction());
		if (ResourceController.getResourceController().getBooleanProperty(ToggleUsageStatsReportAction.VISIBLE_PROPERTY)) {
			service.setReportVisible(true);
		}
	}

	public void setReportVisible(final boolean visible) {
		if (visible) {
			final ReportViewportService charts = ReportViewportService.get();
			if (charts != null) {
				charts.releaseSoftViewport();
			}
			final UsageStatsReportPanel panel = getViewportPanel();
			panel.refresh();
			ReportDocumentService.showInTab("活动报表", panel);
		}
		else {
			ReportDocumentService.closeTab();
		}
		ResourceController.getResourceController().setProperty(ToggleUsageStatsReportAction.VISIBLE_PROPERTY, visible);
		syncToggleAction(visible);
	}

	private void syncToggleAction(final boolean visible) {
		final Controller controller = Controller.getCurrentController();
		if (controller == null) {
			return;
		}
		final AFreeplaneAction action = controller.getAction(ToggleUsageStatsReportAction.KEY);
		if (action != null) {
			action.setSelected(visible);
		}
	}

	public boolean isReportInViewport() {
		return ReportDocumentService.isOpen() && viewportPanel != null && viewportPanel.getParent() != null;
	}

	/** Stop soft-viewport fighting when another report tab takes over. */
	public void releaseSoftViewport() {
		syncToggleAction(false);
		ResourceController.getResourceController().setProperty(ToggleUsageStatsReportAction.VISIBLE_PROPERTY, false);
	}
}
