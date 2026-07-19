package org.freeplane.features.usagestats;

import java.awt.Component;
import java.awt.EventQueue;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.map.IMapSelectionListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.view.swing.map.MapView;
import org.freeplane.view.swing.map.MapViewController;

/**
 * Shows usage statistics in the main map viewport (replacing the mind map canvas), toggled from Help ribbon.
 */
public class UsageStatsReportService implements IExtension, IMapSelectionListener, IMapViewChangeListener {
	private UsageStatsReportPanel viewportPanel;
	private boolean reportInViewport;

	public UsageStatsReportService() {
	}

	private UsageStatsReportPanel getViewportPanel() {
		if (viewportPanel == null) {
			viewportPanel = new UsageStatsReportPanel();
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
		final Controller controller = Controller.getCurrentController();
		controller.getMapViewManager().addMapSelectionListener(service);
		controller.getMapViewManager().addMapViewChangeListener(service);
		if (ResourceController.getResourceController().getBooleanProperty(ToggleUsageStatsReportAction.VISIBLE_PROPERTY)) {
			service.setReportVisible(true);
		}
	}

	public void setReportVisible(final boolean visible) {
		if (visible) {
			showInMapViewport();
		}
		else {
			hideFromMapViewport();
		}
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
		getViewportPanel().refresh();
	}

	private void hideFromMapViewport() {
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
		getViewportPanel().refresh();
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
				getViewportPanel().refresh();
			}
		});
	}

	public void afterViewClose(final Component oldView) {
	}

	public void afterViewCreated(final Component mapView) {
	}
}
