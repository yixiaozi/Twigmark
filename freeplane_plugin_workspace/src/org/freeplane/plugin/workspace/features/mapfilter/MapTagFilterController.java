package org.freeplane.plugin.workspace.features.mapfilter;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.plugin.workspace.components.mapfilter.MapTagFilterPanel;

/**
 * Keeps the overlay panel and Freeplane filter in sync with the active map.
 */
public final class MapTagFilterController {

	private static final int REFRESH_DEBOUNCE_MS = 280;

	private static MapTagFilterController instance;
	private MapTagFilterPanel panel;
	private boolean applying;
	private Timer debounceTimer;
	private MapModel pendingRefreshMap;
	private boolean pendingApplyFilter;

	private MapTagFilterController() {
	}

	public static synchronized MapTagFilterController getInstance() {
		if (instance == null) {
			instance = new MapTagFilterController();
		}
		return instance;
	}

	public static void install(final ModeController modeController) {
		TagFilterMapExtensionIO.install(modeController);
		getInstance().bind(modeController);
	}

	public void setPanel(final MapTagFilterPanel panel) {
		this.panel = panel;
		refreshUi();
		applyCurrentMapFilter();
	}

	public MapTagFilterPanel getPanel() {
		return panel;
	}

	private void bind(final ModeController modeController) {
		modeController.getMapController().addMapLifeCycleListener(new IMapLifeCycleListener() {
			public void onCreate(final MapModel map) {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						if (isCurrentMap(map)) {
							applyMapFilter(map);
							refreshUi();
						}
					}
				});
			}

			public void onRemove(final MapModel map) {
			}

			public void onSavedAs(final MapModel map) {
			}

			public void onSaved(final MapModel map) {
			}
		});
		modeController.getMapController().addNodeChangeListener(new INodeChangeListener() {
			public void nodeChanged(final NodeChangeEvent event) {
				if (event == null || event.getProperty() == null) {
					return;
				}
				if (!NodeModel.NODE_TEXT.equals(event.getProperty())) {
					return;
				}
				final MapModel map = event.getNode() != null ? event.getNode().getMap() : null;
				if (!isCurrentMap(map)) {
					return;
				}
				scheduleNodeTextRefresh(map);
			}
		});
		Controller.getCurrentController().getMapViewManager().addMapViewChangeListener(new IMapViewChangeListener() {
			public void afterViewChange(final Component oldView, final Component newView) {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						applyCurrentMapFilter();
						refreshUi();
					}
				});
			}

			public void afterViewClose(final Component oldView) {
			}

			public void afterViewCreated(final Component mapView) {
			}

			public void beforeViewChange(final Component oldView, final Component newView) {
			}
		});
	}

	/**
	 * Coalesce rapid typing into one tag scan / filter apply — the hot edit path.
	 */
	private void scheduleNodeTextRefresh(final MapModel map) {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					scheduleNodeTextRefresh(map);
				}
			});
			return;
		}
		pendingRefreshMap = map;
		final TagFilterMapExtension extension = TagFilterMapExtension.get(map);
		if (extension != null && extension.hasActiveFilter()) {
			pendingApplyFilter = true;
		}
		if (debounceTimer == null) {
			debounceTimer = new Timer(REFRESH_DEBOUNCE_MS, new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					flushPendingNodeTextRefresh();
				}
			});
			debounceTimer.setRepeats(false);
		}
		debounceTimer.restart();
	}

	private void flushPendingNodeTextRefresh() {
		if (applying) {
			return;
		}
		final MapModel map = pendingRefreshMap;
		final boolean applyFilter = pendingApplyFilter;
		pendingRefreshMap = null;
		pendingApplyFilter = false;
		if (map == null || !isCurrentMap(map)) {
			return;
		}
		if (applyFilter) {
			applyMapFilter(map);
		}
		refreshUi();
	}

	public void applyCurrentMapFilter() {
		applyMapFilter(currentMap());
	}

	public void applyMapFilter(final MapModel map) {
		if (map == null) {
			return;
		}
		applying = true;
		try {
			TagFilterMapExtensionIO.ensureLoadedFromUnknownElements(map);
			final TagFilterMapExtension extension = TagFilterMapExtension.getOrCreate(map);
			if (extension.hasActiveFilter()) {
				MapTagFilterService.applyFromExtension(map);
			}
			else if (MapTagFilterService.isOurFilterActive(map)) {
				MapTagFilterService.clearFilter(map);
			}
		}
		finally {
			applying = false;
		}
	}

	public void refreshUi() {
		if (panel == null) {
			return;
		}
		panel.refreshFromCurrentMap();
	}

	private static MapModel currentMap() {
		try {
			return Controller.getCurrentController().getMap();
		}
		catch (final Exception e) {
			return null;
		}
	}

	private static boolean isCurrentMap(final MapModel map) {
		return map != null && map == currentMap();
	}
}
