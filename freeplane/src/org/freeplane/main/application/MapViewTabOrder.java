/*
 *  Docear - preserve tab order when a mind map view is replaced (e.g. external reload).
 */
package org.freeplane.main.application;

import java.awt.Component;

import org.freeplane.features.mode.Controller;

public final class MapViewTabOrder {
	private MapViewTabOrder() {
	}

	public static int getIndexOfCurrentMapViewTab() {
		final MapViewTabs tabs = MapViewTabs.getInstance();
		if (tabs == null) {
			return -1;
		}
		final Component mapView = Controller.getCurrentController().getMapViewManager().getMapViewComponent();
		return tabs.getTabIndexForMapView(mapView);
	}

	public static void preserveIndexForNextOpenedMapView(final int index) {
		final MapViewTabs tabs = MapViewTabs.getInstance();
		if (tabs != null && index >= 0) {
			tabs.setNextTabInsertIndex(index);
		}
	}

	/** Docear: exit overlay views (e.g. relationship graph) when the current bottom tab is clicked again. */
	public interface SameTabClickListener {
		boolean onSameTabClicked(int tabIndex, Component mapView);
	}

	public static void setSameTabClickListener(final SameTabClickListener listener) {
		MapViewTabs.setSameTabClickListener(new MapViewTabs.SameTabClickListener() {
			public boolean onSameTabClicked(final int tabIndex, final Component mapView) {
				return listener != null && listener.onSameTabClicked(tabIndex, mapView);
			}
		});
	}
}
