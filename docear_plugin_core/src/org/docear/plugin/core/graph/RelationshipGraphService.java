package org.docear.plugin.core.graph;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Timer;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.map.IMapSelectionListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.view.swing.map.MapView;
import org.freeplane.view.swing.map.MapViewController;

/**
 * Shows the relationship graph in the main mind-map viewport (replacing the canvas).
 * <p>
 * While the left 「关系图」tab is active we install a {@link IMapViewManager.ViewportOverride}
 * so every path that refreshes the scroll pane keeps the graph (MapViewController used to
 * always restore MapView and blank the graph). Leaving the side tab, clicking a bottom map
 * tab, or opening a node from the graph clears the override.
 * <p>
 * Important: do <b>not</b> exit from {@code afterMapChange} — incidental map-model events
 * (tab rebuilds, group-filter sync, focus) would bounce the user back to「工作区」and made
 *「打开失败」look intermittent after earlier viewport fixes.
 */
public class RelationshipGraphService implements IExtension, IMapSelectionListener, IMapViewChangeListener {

	private RelationshipGraphCanvas canvas;
	private boolean graphInViewport;
	/** True while the left graph side-tab wants the main viewport. */
	private boolean holdingViewport;
	private boolean reclaimScheduled;
	private boolean viewportWatchInstalled;
	private final IMapViewManager.ViewportOverride viewportOverride = new IMapViewManager.ViewportOverride() {
		public Component getViewportComponent() {
			return getCanvas();
		}
	};

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
			installViewportWatch(canvas);
		}
		return canvas;
	}

	public boolean isGraphInViewport() {
		return graphInViewport;
	}

	/** True when the scroll pane's view is actually our canvas (not just a flag). */
	public boolean isGraphActuallyVisible() {
		try {
			final MapViewController mapViewController = getMapViewController();
			final JViewport viewport = mapViewController.getScrollPane().getViewport();
			return canvas != null && viewport != null && viewport.getView() == canvas
			        && canvas.isDisplayable() && canvas.getWidth() >= 40 && canvas.getHeight() >= 40;
		}
		catch (final Exception e) {
			return false;
		}
	}

	public boolean isHoldingViewport() {
		return holdingViewport;
	}

	/** Called when the left graph tab is selected / deselected. */
	public void setHoldingViewport(final boolean hold) {
		holdingViewport = hold;
		if (!hold) {
			reclaimScheduled = false;
			clearViewportOverride();
		}
		else {
			installViewportOverride();
		}
	}

	public void showInViewport() {
		showInViewportInternal(true);
	}

	private void showInViewportInternal(final boolean scheduleFollowUp) {
		holdingViewport = true;
		graphInViewport = true;
		installViewportOverride();
		final Runnable swap = new Runnable() {
			public void run() {
				if (!holdingViewport) {
					return;
				}
				final MapViewController mapViewController = getMapViewController();
				final RelationshipGraphCanvas graphCanvas = getCanvas();
				sizeCanvasToViewport(mapViewController.getScrollPane(), graphCanvas);
				mapViewController.refreshViewportView(graphCanvas);
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
		if (scheduleFollowUp) {
			scheduleDelayedReclaim(50);
			scheduleDelayedReclaim(250);
		}
	}

	public void hideFromViewport() {
		holdingViewport = false;
		reclaimScheduled = false;
		clearViewportOverride();
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
				mapViewController.refreshViewportView(mapView);
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
		clearViewportOverride();
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

	private void installViewportOverride() {
		final IMapViewManager manager = Controller.getCurrentController().getMapViewManager();
		if (manager != null) {
			manager.setViewportOverride(viewportOverride);
		}
	}

	private void clearViewportOverride() {
		final Controller controller = Controller.getCurrentController();
		if (controller == null) {
			return;
		}
		final IMapViewManager manager = controller.getMapViewManager();
		if (manager != null && manager.getViewportOverride() == viewportOverride) {
			manager.setViewportOverride(null);
		}
	}

	private static void sizeCanvasToViewport(final JScrollPane scrollPane, final RelationshipGraphCanvas graphCanvas) {
		if (scrollPane == null || graphCanvas == null) {
			return;
		}
		final JViewport viewport = scrollPane.getViewport();
		int w = viewport != null ? viewport.getWidth() : 0;
		int h = viewport != null ? viewport.getHeight() : 0;
		if (w < 80) {
			w = Math.max(scrollPane.getWidth() - 24, 800);
		}
		if (h < 80) {
			h = Math.max(scrollPane.getHeight() - 24, 600);
		}
		graphCanvas.setPreferredSize(new Dimension(w, h));
		graphCanvas.setSize(w, h);
	}

	private void installViewportWatch(final RelationshipGraphCanvas graphCanvas) {
		if (viewportWatchInstalled || graphCanvas == null) {
			return;
		}
		viewportWatchInstalled = true;
		graphCanvas.addHierarchyListener(new HierarchyListener() {
			public void hierarchyChanged(final HierarchyEvent e) {
				if (!holdingViewport) {
					return;
				}
				if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0
				        || (e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
					scheduleReclaimViewport();
				}
			}
		});
	}

	private void scheduleReclaimViewport() {
		if (!holdingViewport || reclaimScheduled) {
			return;
		}
		reclaimScheduled = true;
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				reclaimScheduled = false;
				reclaimIfNeeded();
			}
		});
	}

	private void scheduleDelayedReclaim(final int delayMs) {
		final Timer timer = new Timer(delayMs, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				((Timer) e.getSource()).stop();
				reclaimIfNeeded();
			}
		});
		timer.setRepeats(false);
		timer.start();
	}

	private void reclaimIfNeeded() {
		if (!holdingViewport) {
			return;
		}
		final MapViewController mapViewController = getMapViewController();
		final Component view = mapViewController.getScrollPane().getViewport().getView();
		if (canvas == null || view != canvas) {
			showInViewportInternal(false);
		}
	}

	public void beforeMapChange(final MapModel oldMap, final MapModel newMap) {
	}

	public void afterMapChange(final MapModel oldMap, final MapModel newMap) {
		if (!holdingViewport) {
			return;
		}
		// Keep the graph: map-model events fire for tab rebuilds/filter sync and must not
		// exit the side tab. Bottom-map-tab switches exit via MapViewTabs/Integration.
		scheduleReclaimViewport();
	}

	public void afterViewChange(final Component oldView, final Component newView) {
		if (!holdingViewport) {
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
