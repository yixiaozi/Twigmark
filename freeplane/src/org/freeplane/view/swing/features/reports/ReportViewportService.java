package org.freeplane.view.swing.features.reports;

import javax.swing.JOptionPane;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.usagestats.UsageStatsReportService;

/**
 * Shows report charts via {@link ReportDocumentService} (bottom tab + mind-map area).
 */
public final class ReportViewportService implements IExtension {
	private ReportViewportPanel viewportPanel;

	public static ReportViewportService get() {
		final Controller controller = Controller.getCurrentController();
		if (controller == null || controller.getModeController() == null) {
			return null;
		}
		ReportViewportService service = (ReportViewportService) controller.getModeController()
		        .getExtension(ReportViewportService.class);
		if (service == null && controller.getModeController() instanceof MModeController) {
			service = install((MModeController) controller.getModeController());
		}
		return service;
	}

	public static ReportViewportService install(final MModeController modeController) {
		ReportViewportService service = (ReportViewportService) modeController.getExtension(ReportViewportService.class);
		if (service != null) {
			return service;
		}
		service = new ReportViewportService();
		modeController.addExtension(ReportViewportService.class, service);
		return service;
	}

	private ReportViewportPanel getViewportPanel() {
		if (viewportPanel == null) {
			viewportPanel = new ReportViewportPanel();
			viewportPanel.setOnClose(new Runnable() {
				public void run() {
					ReportDocumentService.closeTab();
				}
			});
			viewportPanel.setOnWrite(new Runnable() {
				public void run() {
					writeCurrentToSelection();
				}
			});
		}
		return viewportPanel;
	}

	public void showReport(final ReportViewModel model, final ReportNodeSpec tree) {
		final UsageStatsReportService activity = UsageStatsReportService.get();
		if (activity != null) {
			activity.releaseSoftViewport();
		}
		final ReportViewportPanel panel = getViewportPanel();
		panel.showModel(model, tree);
		final String title = model == null || model.title == null || model.title.length() == 0 ? "报表"
		        : model.title;
		ReportDocumentService.showInTab(title, panel);
	}

	/** @deprecated soft viewport flag removed; kept for callers that hide charts before opening another view */
	public boolean isReportInViewport() {
		return ReportDocumentService.isOpen() && viewportPanel != null
		        && viewportPanel.getParent() != null;
	}

	public void hideFromMapViewport() {
		ReportDocumentService.closeTab();
	}

	/** Stop fighting document tabs / map view changes (no-op soft state). */
	public void releaseSoftViewport() {
		// Intentionally empty: reports now live in ReportDocumentView tabs.
	}

	private void writeCurrentToSelection() {
		final ReportNodeSpec tree = getViewportPanel().getCurrentTree();
		if (tree == null) {
			return;
		}
		try {
			final NodeModel written = ReportMindMapWriter.writeUnderSelection(tree);
			if (written == null) {
				JOptionPane.showMessageDialog(getViewportPanel(), "请先在导图中选中一个节点", "写入报表",
				        JOptionPane.WARNING_MESSAGE);
				return;
			}
			JOptionPane.showMessageDialog(getViewportPanel(), "已写入选中节点", "写入报表",
			        JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception e) {
			JOptionPane.showMessageDialog(getViewportPanel(), e.getMessage(), "写入报表",
			        JOptionPane.ERROR_MESSAGE);
		}
	}
}
