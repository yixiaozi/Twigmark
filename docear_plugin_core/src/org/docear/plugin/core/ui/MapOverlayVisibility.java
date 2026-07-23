package org.docear.plugin.core.ui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.main.application.DocumentTabSupport;
import org.freeplane.view.swing.map.MapView;

/**
 * Map chrome (tag filter / map activity) should only appear over a real mind-map
 * canvas — not calendar, reports, graphs, or other document tabs.
 */
public final class MapOverlayVisibility {

	public interface Listener {
		void onMapCanvasVisibilityMaybeChanged();
	}

	private static final List listeners = new ArrayList();
	private static boolean hookedToMapViewManager;
	private static final IMapViewManager.ViewportContentListener viewportHook = new IMapViewManager.ViewportContentListener() {
		public void viewportContentChanged() {
			notifyCanvasMaybeChanged();
		}
	};

	private MapOverlayVisibility() {
	}

	public static synchronized void addListener(final Listener listener) {
		if (listener != null && !listeners.contains(listener)) {
			listeners.add(listener);
		}
		ensureViewportHook();
	}

	private static void ensureViewportHook() {
		if (hookedToMapViewManager) {
			return;
		}
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null || controller.getMapViewManager() == null) {
				return;
			}
			controller.getMapViewManager().addViewportContentListener(viewportHook);
			hookedToMapViewManager = true;
		}
		catch (final Exception e) {
			// retry on next addListener / notify
		}
	}

	/** Call after calendar / report / graph / document-tab viewport swaps. */
	public static void notifyCanvasMaybeChanged() {
		ensureViewportHook();
		final Runnable run = new Runnable() {
			public void run() {
				final Listener[] snapshot;
				synchronized (MapOverlayVisibility.class) {
					snapshot = (Listener[]) listeners.toArray(new Listener[listeners.size()]);
				}
				for (int i = 0; i < snapshot.length; i++) {
					try {
						snapshot[i].onMapCanvasVisibilityMaybeChanged();
					}
					catch (final Exception e) {
						// keep other overlays updating
					}
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			run.run();
		}
		else {
			SwingUtilities.invokeLater(run);
		}
	}

	public static boolean isMindMapCanvasShowing() {
		try {
			if (DocumentTabSupport.getActiveDocumentView() != null) {
				return false;
			}
			final Controller controller = Controller.getCurrentController();
			if (controller == null || controller.getMapViewManager() == null) {
				return false;
			}
			final IMapViewManager mvm = controller.getMapViewManager();
			final IMapViewManager.ViewportOverride override = mvm.getViewportOverride();
			if (override != null && override.getViewportComponent() != null) {
				return false;
			}
			final JScrollPane scroll = mvm.getScrollPane();
			if (scroll == null) {
				return false;
			}
			final JViewport viewport = scroll.getViewport();
			if (viewport == null) {
				return false;
			}
			final Component view = viewport.getView();
			return view instanceof MapView;
		}
		catch (final Exception e) {
			return false;
		}
	}
}
