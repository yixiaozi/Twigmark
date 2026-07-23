package org.docear.plugin.core.calendar;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;

import javax.swing.JScrollPane;
import javax.swing.JViewport;

import org.docear.plugin.core.graph.RelationshipGraphService;
import org.docear.plugin.core.ui.MapOverlayVisibility;
import org.freeplane.core.extension.IExtension;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.view.swing.map.MapView;
import org.freeplane.view.swing.map.MapViewController;

/**
 * Shows the DocearReminder-style DayView calendar in the main mind-map viewport.
 */
public final class CalendarViewportService implements IExtension {

	private CalendarViewportPanel panel;
	private boolean holdingViewport;
	private final IMapViewManager.ViewportOverride viewportOverride = new IMapViewManager.ViewportOverride() {
		public Component getViewportComponent() {
			return getPanel();
		}
	};

	public static CalendarViewportService getService() {
		final Controller controller = Controller.getCurrentController();
		if (controller == null || controller.getModeController() == null) {
			return null;
		}
		return (CalendarViewportService) controller.getModeController().getExtension(CalendarViewportService.class);
	}

	public static void install(final MModeController modeController) {
		final CalendarViewportService service = new CalendarViewportService();
		modeController.addExtension(CalendarViewportService.class, service);
	}

	public static void show() {
		final CalendarViewportService service = getService();
		if (service != null) {
			service.showInViewport();
		}
	}

	public static void hide() {
		final CalendarViewportService service = getService();
		if (service != null) {
			service.hideFromViewport();
		}
	}

	public static void toggle() {
		final CalendarViewportService service = getService();
		if (service == null) {
			return;
		}
		if (service.isHoldingViewport()) {
			service.hideFromViewport();
		}
		else {
			service.showInViewport();
		}
	}

	public CalendarViewportPanel getPanel() {
		if (panel == null) {
			panel = new CalendarViewportPanel();
		}
		return panel;
	}

	public boolean isHoldingViewport() {
		return holdingViewport;
	}

	public void showInViewport() {
		try {
			final RelationshipGraphService graph = RelationshipGraphService.getService();
			if (graph != null && graph.isHoldingViewport()) {
				graph.hideFromViewport();
			}
		}
		catch (Exception e) {
			LogUtils.warn("CalendarViewport: could not release graph viewport.", e);
		}
		holdingViewport = true;
		installViewportOverride();
		MapOverlayVisibility.notifyCanvasMaybeChanged();
		final Runnable swap = new Runnable() {
			public void run() {
				if (!holdingViewport) {
					return;
				}
				final MapViewController mapViewController = getMapViewController();
				if (mapViewController == null) {
					return;
				}
				final CalendarViewportPanel calendar = getPanel();
				sizeToViewport(mapViewController.getScrollPane(), calendar);
				calendar.refreshChrome();
				calendar.reloadTasksAsync();
				mapViewController.refreshViewportView(calendar);
				calendar.revalidate();
				mapViewController.getScrollPane().validate();
				calendar.repaint();
				MapOverlayVisibility.notifyCanvasMaybeChanged();
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
		clearViewportOverride();
		final Runnable restore = new Runnable() {
			public void run() {
				try {
					final MapViewController mapViewController = getMapViewController();
					if (mapViewController == null) {
						return;
					}
					final MapView mapView = mapViewController.getMapView();
					mapViewController.refreshViewportView(mapView);
				}
				catch (Exception e) {
					LogUtils.warn("CalendarViewport: restore map failed.", e);
				}
				finally {
					MapOverlayVisibility.notifyCanvasMaybeChanged();
				}
			}
		};
		if (EventQueue.isDispatchThread()) {
			restore.run();
		}
		else {
			EventQueue.invokeLater(restore);
		}
	}

	private MapViewController getMapViewController() {
		final Controller controller = Controller.getCurrentController();
		if (controller == null || controller.getMapViewManager() == null) {
			return null;
		}
		return (MapViewController) controller.getMapViewManager();
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

	private static void sizeToViewport(final JScrollPane scrollPane, final CalendarViewportPanel calendar) {
		if (scrollPane == null || calendar == null) {
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
		calendar.setPreferredSize(new Dimension(w, h));
		calendar.setSize(w, h);
	}
}
