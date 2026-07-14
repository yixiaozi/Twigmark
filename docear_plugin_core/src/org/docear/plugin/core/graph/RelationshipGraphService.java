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
 * <p>
 * {@link MapViewController#afterViewChange} always restores a {@link MapView} into the scroll
 * pane whenever a map-view event fires. While the left 「关系图」tab is active we reclaim the
 * viewport afterwards so the graph is not wiped blank. Leaving for a different mind map
 * ({@link #afterMapChange}) or intentional exit still restores the map view.
 */
public class RelationshipGraphService implements IExtension, IMapSelectionListener, IMapViewChangeListener {

	private RelationshipGraphCanvas canvas;
	private boolean graphInViewport;
	/** True while the left graph side-tab wants the main viewport. */
	private boolean holdingViewport;
	private boolean reclaimScheduled;

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

	public boolean isHoldingViewport() {
		return holdingViewport;
	}

	/** Called when the left graph tab is selected / deselected. */
	public void setHoldingViewport(final boolean hold) {
		holdingViewport = hold;
		if (!hold) {
			reclaimScheduled = false;
		}
	}

	public void showInViewport() {
		holdingViewport = true;
		graphInViewport = true;
		final Runnable swap = new Runnable() {
			public void run() {
				if (!holdingViewport) {
					return;
				}
				final MapViewController mapViewController = getMapViewController();
				final RelationshipGraphCanvas graphCanvas = getCanvas();
				mapViewController.getScrollPane().setViewportView(graphCanvas);
				graphInViewport = true;
				graphCanvas.revalidate();
				mapViewController.getScrollPane().validate();
				graphCanvas.repaint();
			}
		};
		if (EventQueue.isDispatchThread()) {
			swap.run();
		}
		else {
			EventQueue.invokeLater(swap);
		}
	}

	public void hideFromViewport() {
		holdingViewport = false;
		reclaimScheduled = false;
		final Runnable restore = new Runnable() {
			public void run() {
				if (holdingViewport) {
					return;
				}
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
		};
		if (EventQueue.isDispatchThread()) {
			restore.run();
		}
		else {
			EventQueue.invokeLater(restore);
		}
	}

	/** Clears graph viewport state without changing the scroll pane (map tab switch restores the view). */
	public void markGraphExitedFromViewport() {
		holdingViewport = false;
		reclaimScheduled = false;
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

	private void scheduleReclaimViewport() {
		if (!holdingViewport || reclaimScheduled) {
			return;
		}
		reclaimScheduled = true;
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				reclaimScheduled = false;
				if (!holdingViewport) {
					return;
				}
				final Component view = getMapViewController().getScrollPane().getViewport().getView();
				if (canvas == null || view != canvas) {
					showInViewport();
				}
			}
		});
	}

	public void beforeMapChange(final MapModel oldMap, final MapModel newMap) {
	}

	public void afterMapChange(final MapModel oldMap, final MapModel newMap) {
		if (!holdingViewport) {
			return;
		}
		// Different mind map became active → user left the graph for a document.
		if (newMap != null && newMap != oldMap) {
			RelationshipGraphIntegration.exitGraphViewDueToMapSwitch();
			return;
		}
		scheduleReclaimViewport();
	}

	public void afterViewChange(final Component oldView, final Component newView) {
		if (!holdingViewport) {
			return;
		}
		// MapViewController always restores MapView into the scroll pane on view events;
		// reclaim afterwards so the graph is not left blank.
		if (newView instanceof MapView) {
			scheduleReclaimViewport();
			return;
		}
		scheduleReclaimViewport();
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
