package org.freeplane.main.application;

import java.awt.Component;

import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IDocumentTabView;

/**
 * Switches the main viewport between mind maps and alternate document tabs.
 */
public final class DocumentTabSupport {

	private static IDocumentTabView activeDocumentView;

	private DocumentTabSupport() {
	}

	public static IDocumentTabView getActiveDocumentView() {
		return activeDocumentView;
	}

	public static void openDocumentTab(final IDocumentTabView view) {
		final MapViewTabs tabs = MapViewTabs.getInstance();
		if (tabs == null || view == null) {
			return;
		}
		tabs.openDocumentTab(view);
	}

	public static void selectDocumentTab(final IDocumentTabView view) {
		final MapViewTabs tabs = MapViewTabs.getInstance();
		if (tabs == null || view == null) {
			return;
		}
		tabs.selectDocumentTab(view.getTabKey());
	}

	public static void closeDocumentTab(final IDocumentTabView view) {
		final MapViewTabs tabs = MapViewTabs.getInstance();
		if (tabs == null || view == null) {
			return;
		}
		if (activeDocumentView == view) {
			deactivateDocumentView();
		}
		tabs.closeDocumentTab(view.getTabKey());
	}

	public static void activateDocumentView(final IDocumentTabView view) {
		if (view == null) {
			return;
		}
		if (activeDocumentView != null && activeDocumentView != view) {
			activeDocumentView.onTabDeactivated();
		}
		activeDocumentView = view;
		view.onTabActivated();
		// Must go through refreshViewportView so ViewportOverride (关系图) is respected.
		Controller.getCurrentController().getMapViewManager()
		        .refreshViewportView(view.getViewportComponent());
	}

	public static void deactivateDocumentView() {
		if (activeDocumentView == null) {
			return;
		}
		activeDocumentView.onTabDeactivated();
		activeDocumentView = null;
		// Draw.io (and other document tabs) replace the scroll-pane viewport via
		// activateDocumentView → refreshViewportView. Switching back to a mind map
		// that is still the "current" MapView skips changeToMapView, so we must
		// restore the map (or an active ViewportOverride) here.
		final Component mapView = Controller.getCurrentController().getMapViewManager()
		        .getMapViewComponent();
		Controller.getCurrentController().getMapViewManager().refreshViewportView(mapView);
	}

	/**
	 * Clears document-tab ownership without touching the viewport. Use when a
	 * MapView is about to claim the scroll pane via {@code afterViewChange}.
	 */
	public static void clearActiveDocumentView() {
		if (activeDocumentView == null) {
			return;
		}
		try {
			activeDocumentView.onTabDeactivated();
		}
		catch (Throwable ignore) {
		}
		activeDocumentView = null;
	}

	public static void refreshTabTitles() {
		final MapViewTabs tabs = MapViewTabs.getInstance();
		if (tabs != null) {
			tabs.refreshTabTitles();
		}
	}
}
