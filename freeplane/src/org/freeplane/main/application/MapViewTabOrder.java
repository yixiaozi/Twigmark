/*
 *  Docear - preserve tab order when a mind map view is replaced (e.g. external reload).
 */
package org.freeplane.main.application;

import java.awt.Component;
import java.util.Collections;
import java.util.List;

import javax.swing.JPopupMenu;

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

	/** Docear: exit overlay views when a different bottom map tab becomes active. */
	public interface MapTabActivationListener {
		void onMapTabActivated(Component mapView);
	}

	public static void setMapTabActivationListener(final MapTabActivationListener listener) {
		MapViewTabs.setMapTabActivationListener(new MapViewTabs.MapTabActivationListener() {
			public void onMapTabActivated(final Component mapView) {
				if (listener != null) {
					listener.onMapTabActivated(mapView);
				}
			}
		});
	}

	public interface TabVisibilityFilter {
		boolean isVisible(Component tabKey);
	}

	public interface TabsChangedListener {
		void tabsChanged();
	}

	public interface TabPopupMenuProvider {
		JPopupMenu createPopup(Component tabKey, int visibleTabIndex);
	}

	public interface TabOutsideFilterHandler {
		boolean revealTab(Component tabKey);

		boolean revealAll();
	}

	public static void setTabGroupChrome(final Component chrome) {
		final MapViewTabs tabs = MapViewTabs.getInstance();
		if (tabs != null) {
			tabs.setTabGroupChrome(chrome);
		}
	}

	public static void setTabVisibilityFilter(final TabVisibilityFilter filter) {
		final MapViewTabs tabs = MapViewTabs.getInstance();
		if (tabs == null) {
			return;
		}
		if (filter == null) {
			tabs.setTabVisibilityFilter(null);
			return;
		}
		tabs.setTabVisibilityFilter(new MapViewTabs.TabVisibilityFilter() {
			public boolean isVisible(final Component tabKey) {
				return filter.isVisible(tabKey);
			}
		});
	}

	public static void setTabsChangedListener(final TabsChangedListener listener) {
		final MapViewTabs tabs = MapViewTabs.getInstance();
		if (tabs == null) {
			return;
		}
		if (listener == null) {
			tabs.setTabsChangedListener(null);
			return;
		}
		tabs.setTabsChangedListener(new MapViewTabs.TabsChangedListener() {
			public void tabsChanged() {
				listener.tabsChanged();
			}
		});
	}

	public static void setTabPopupMenuProvider(final TabPopupMenuProvider provider) {
		final MapViewTabs tabs = MapViewTabs.getInstance();
		if (tabs == null) {
			return;
		}
		if (provider == null) {
			tabs.setTabPopupMenuProvider(null);
			return;
		}
		tabs.setTabPopupMenuProvider(new MapViewTabs.TabPopupMenuProvider() {
			public JPopupMenu createPopup(final Component tabKey, final int visibleTabIndex) {
				return provider.createPopup(tabKey, visibleTabIndex);
			}
		});
	}

	public static void setTabOutsideFilterHandler(final TabOutsideFilterHandler handler) {
		final MapViewTabs tabs = MapViewTabs.getInstance();
		if (tabs == null) {
			return;
		}
		if (handler == null) {
			tabs.setTabOutsideFilterHandler(null);
			return;
		}
		tabs.setTabOutsideFilterHandler(new MapViewTabs.TabOutsideFilterHandler() {
			public boolean revealTab(final Component tabKey) {
				return handler.revealTab(tabKey);
			}

			public boolean revealAll() {
				return handler.revealAll();
			}
		});
	}

	public static void refreshVisibleTabs() {
		final MapViewTabs tabs = MapViewTabs.getInstance();
		if (tabs != null) {
			tabs.refreshVisibleTabs();
		}
	}

	@SuppressWarnings("unchecked")
	public static List getAllTabKeysInOrder() {
		final MapViewTabs tabs = MapViewTabs.getInstance();
		if (tabs == null) {
			return Collections.emptyList();
		}
		return tabs.getAllTabKeysInOrder();
	}
}
