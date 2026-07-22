package org.docear.plugin.core.ui;

import java.awt.Component;

import javax.swing.JScrollPane;
import javax.swing.JViewport;

import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.main.application.DocumentTabSupport;
import org.freeplane.view.swing.map.MapView;

/**
 * Map chrome (tag filter / map activity) should only appear over a real mind-map
 * canvas — not calendar, reports, graphs, or other document tabs.
 */
public final class MapOverlayVisibility {

	private MapOverlayVisibility() {
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
