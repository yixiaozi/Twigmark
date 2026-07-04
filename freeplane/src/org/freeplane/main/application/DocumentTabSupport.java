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
		Controller.getCurrentController().getMapViewManager().getScrollPane()
		        .setViewportView(view.getViewportComponent());
	}

	public static void deactivateDocumentView() {
		if (activeDocumentView != null) {
			activeDocumentView.onTabDeactivated();
			activeDocumentView = null;
		}
	}
}
