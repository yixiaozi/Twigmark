package org.freeplane.view.swing.features.reports;

import java.awt.Component;
import java.awt.EventQueue;

import javax.swing.JOptionPane;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.map.IMapSelectionListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.view.swing.map.MapView;
import org.freeplane.view.swing.map.MapViewController;

/**
 * Shows report charts in the main mind-map viewport (same place as the map canvas).
 */
public final class ReportViewportService implements IExtension, IMapSelectionListener, IMapViewChangeListener {
	private ReportViewportPanel viewportPanel;
	private boolean reportInViewport;
	private boolean listenersInstalled;

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
		service.ensureListeners();
		return service;
	}

	private void ensureListeners() {
		if (listenersInstalled) {
			return;
		}
		final Controller controller = Controller.getCurrentController();
		if (controller == null) {
			return;
		}
		controller.getMapViewManager().addMapSelectionListener(this);
		controller.getMapViewManager().addMapViewChangeListener(this);
		listenersInstalled = true;
	}

	private ReportViewportPanel getViewportPanel() {
		if (viewportPanel == null) {
			viewportPanel = new ReportViewportPanel();
			viewportPanel.setOnClose(new Runnable() {
				public void run() {
					hideFromMapViewport();
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
		ensureListeners();
		final ReportViewportPanel panel = getViewportPanel();
		panel.showModel(model, tree);
		showInMapViewport();
	}

	public boolean isReportInViewport() {
		return reportInViewport;
	}

	private MapViewController getMapViewController() {
		return (MapViewController) Controller.getCurrentController().getMapViewManager();
	}

	private void showInMapViewport() {
		final MapViewController mapViewController = getMapViewController();
		mapViewController.refreshViewportView(getViewportPanel());
		reportInViewport = true;
	}

	public void hideFromMapViewport() {
		if (!reportInViewport) {
			return;
		}
		final MapViewController mapViewController = getMapViewController();
		final MapView mapView = mapViewController.getMapView();
		if (mapView != null) {
			mapViewController.refreshViewportView(mapView);
		}
		reportInViewport = false;
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

	private void ensureReportStillInViewport() {
		if (!reportInViewport) {
			return;
		}
		final Component view = getMapViewController().getScrollPane().getViewport().getView();
		if (viewportPanel != null && view != viewportPanel) {
			showInMapViewport();
		}
	}

	public void beforeMapChange(final MapModel oldMap, final MapModel newMap) {
	}

	public void afterMapChange(final MapModel oldMap, final MapModel newMap) {
		if (!reportInViewport) {
			return;
		}
		ensureReportStillInViewport();
	}

	public void beforeViewChange(final Component oldView, final Component newView) {
	}

	public void afterViewChange(final Component oldView, final Component newView) {
		if (!reportInViewport) {
			return;
		}
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				ensureReportStillInViewport();
			}
		});
	}

	public void afterViewClose(final Component oldView) {
	}

	public void afterViewCreated(final Component mapView) {
	}
}
