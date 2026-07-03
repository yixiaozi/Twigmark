package org.docear.plugin.core.graph;

import java.awt.Component;
import java.awt.EventQueue;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.map.IMapSelectionListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.view.swing.map.MapView;
import org.freeplane.view.swing.map.MapViewController;

/**
 * Shows the relationship graph in the main mind-map viewport (replacing the canvas).
 */
public class RelationshipGraphService implements IExtension, IMapSelectionListener, IMapViewChangeListener {

	private RelationshipGraphCanvas canvas;
	private boolean graphInViewport;

	public RelationshipGraphService() {
	}

	public static RelationshipGraphService getService() {
		final MModeController modeController = (MModeController) Controller.getCurrentModeController();
		if (modeController == null) {
			return null;
		}
		return (RelationshipGraphService) modeController.getExtension(RelationshipGraphService.class);
	}

	public static void install(final MModeController modeController) {
		final RelationshipGraphService service = new RelationshipGraphService();
		modeController.addExtension(RelationshipGraphService.class, service);
		final Controller controller = Controller.getCurrentController();
		controller.getMapViewManager().addMapSelectionListener(service);
		controller.getMapViewManager().addMapViewChangeListener(service);
	}

	public RelationshipGraphCanvas getCanvas() {
		if (canvas == null) {
			canvas = new RelationshipGraphCanvas();
		}
		return canvas;
	}

	public boolean isGraphInViewport() {
		return graphInViewport;
	}

	public void showInViewport() {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				final MapViewController mapViewController = getMapViewController();
				final RelationshipGraphCanvas graphCanvas = getCanvas();
				mapViewController.getScrollPane().setViewportView(graphCanvas);
				graphInViewport = true;
				graphCanvas.revalidate();
				mapViewController.getScrollPane().validate();
			}
		});
	}

	public void hideFromViewport() {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				if (!graphInViewport) {
					return;
				}
				final MapViewController mapViewController = getMapViewController();
				final MapView mapView = mapViewController.getMapView();
				if (mapView != null) {
					mapViewController.getScrollPane().setViewportView(mapView);
				}
				markGraphExitedFromViewport();
			}
		});
	}

	/** Clears graph viewport state without changing the scroll pane (map tab switch restores the view). */
	public void markGraphExitedFromViewport() {
		graphInViewport = false;
		if (canvas != null) {
			canvas.stopLayout();
		}
	}

	public void loadGraph(final RelationshipGraphIndex index) {
		loadGraph(index, false);
	}

	public void loadGraph(final RelationshipGraphIndex index, final boolean preserveView) {
		getCanvas().setGraphIndex(index, preserveView);
		if (graphInViewport) {
			getCanvas().repaint();
		}
	}

	/** Applies a graph whose layout was prepared off the EDT. */
	public void loadPreparedGraph(final RelationshipGraphIndex index, final boolean preserveView) {
		getCanvas().setPreparedGraphIndex(index, preserveView);
		getCanvas().repaint();
	}

	private MapViewController getMapViewController() {
		return (MapViewController) Controller.getCurrentController().getMapViewManager();
	}

	private void ensureGraphStillInViewport() {
		if (!graphInViewport) {
			return;
		}
		final Component view = getMapViewController().getScrollPane().getViewport().getView();
		if (canvas != null && view != canvas) {
			showInViewport();
		}
	}

	public void beforeMapChange(final MapModel oldMap, final MapModel newMap) {
	}

	public void afterMapChange(final MapModel oldMap, final MapModel newMap) {
		if (!graphInViewport) {
			return;
		}
		if (newMap != null) {
			RelationshipGraphIntegration.exitGraphViewDueToMapSwitch();
			return;
		}
		ensureGraphStillInViewport();
	}

	public void afterViewChange(final Component oldView, final Component newView) {
		if (!graphInViewport) {
			return;
		}
		if (newView instanceof MapView) {
			RelationshipGraphIntegration.exitGraphViewDueToMapSwitch();
			return;
		}
		ensureGraphStillInViewport();
	}

	public void afterViewClose(final Component oldView) {
	}

	public void afterViewCreated(final Component mapView) {
	}

	public void beforeViewChange(final Component oldView, final Component newView) {
	}

	public void onSelect(final MapModel map) {
	}

	public void onDeselect(final MapModel map) {
	}
}
