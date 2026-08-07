package org.freeplane.view.swing.features.reports;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.usagestats.UsageStatsReportService;

/**
 * Shows report charts via {@link ReportDocumentService} (bottom tab + mind-map area).
 * Opens the tab immediately with a loading shell, then fills data when generation finishes.
 */
public final class ReportViewportService implements IExtension {
	private ReportViewportPanel viewportPanel;
	private volatile int loadGeneration;

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

	/**
	 * Open the report tab immediately with title / meta and a progress UI.
	 * Returns a generation token; later updates must pass the same token.
	 */
	public int beginReport(final ReportDefinition def, final String subtitle) {
		final UsageStatsReportService activity = UsageStatsReportService.get();
		if (activity != null) {
			activity.releaseSoftViewport();
		}
		final int generation = ++loadGeneration;
		final ReportViewportPanel panel = getViewportPanel();
		final String title = def == null || def.title == null || def.title.length() == 0 ? "报表"
		        : "报表 · " + def.title;
		final String decision = def == null || def.decision == null ? "" : def.decision;
		final String dataSource = def == null || def.dataSource == null ? "" : def.dataSource;
		final String sub = subtitle == null ? "" : subtitle;
		final Runnable open = new Runnable() {
			public void run() {
				if (generation != loadGeneration) {
					return;
				}
				panel.showLoading(title, sub, decision, dataSource);
				ReportDocumentService.showInTab(title + " · 加载中", panel);
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			open.run();
		}
		else {
			SwingUtilities.invokeLater(open);
		}
		return generation;
	}

	public boolean isCurrentGeneration(final int generation) {
		return generation == loadGeneration;
	}

	public void updateProgress(final int generation, final String message, final int percent) {
		if (generation != loadGeneration) {
			return;
		}
		final Runnable update = new Runnable() {
			public void run() {
				if (generation != loadGeneration) {
					return;
				}
				getViewportPanel().setLoadProgress(message, percent);
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			update.run();
		}
		else {
			SwingUtilities.invokeLater(update);
		}
	}

	public void showReport(final int generation, final ReportViewModel model, final ReportNodeSpec tree) {
		if (generation != loadGeneration) {
			return;
		}
		final Runnable show = new Runnable() {
			public void run() {
				if (generation != loadGeneration) {
					return;
				}
				showReport(model, tree);
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			show.run();
		}
		else {
			SwingUtilities.invokeLater(show);
		}
	}

	public void showError(final int generation, final String message) {
		if (generation != loadGeneration) {
			return;
		}
		final String msg = message == null ? "生成失败" : message;
		final Runnable show = new Runnable() {
			public void run() {
				if (generation != loadGeneration) {
					return;
				}
				getViewportPanel().showError("报表生成失败", msg);
				ReportDocumentService.showInTab("报表 · 失败", getViewportPanel());
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			show.run();
		}
		else {
			SwingUtilities.invokeLater(show);
		}
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
