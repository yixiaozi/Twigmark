package org.docear.plugin.core.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.view.swing.map.MapView;

/**
 * Hosts the mind-map minimap on the frame {@link JLayeredPane} at bottom-right
 * (tag filter stays top-right; activity stays top-left).
 */
public class MapMinimapOverlay implements MapMinimapPanel.LayoutListener, MapOverlayVisibility.Listener {

	private static MapMinimapOverlay instance;

	private final MapMinimapPanel panel;
	private JLayeredPane layeredPane;
	private JScrollPane scrollPane;
	private boolean installed;
	private final AdjustmentListener scrollListener = new AdjustmentListener() {
		public void adjustmentValueChanged(final AdjustmentEvent e) {
			panel.requestRefresh();
		}
	};

	private final ComponentAdapter repositionListener = new ComponentAdapter() {
		public void componentResized(final ComponentEvent e) {
			reposition();
			panel.requestRefresh();
		}

		public void componentMoved(final ComponentEvent e) {
			reposition();
		}

		public void componentShown(final ComponentEvent e) {
			reposition();
			panel.requestRefresh();
		}
	};

	private MapMinimapOverlay() {
		panel = new MapMinimapPanel();
		panel.setVisible(false);
		panel.setLayoutListener(this);
		MapOverlayVisibility.addListener(this);
	}

	public void onMapCanvasVisibilityMaybeChanged() {
		reposition();
		panel.requestRefresh();
	}

	public void onPanelLayoutChanged() {
		reposition();
	}

	public static synchronized MapMinimapOverlay getInstance() {
		if (instance == null) {
			instance = new MapMinimapOverlay();
		}
		return instance;
	}

	public static void install() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				getInstance().installWithRetry(0);
			}
		});
	}

	private void installWithRetry(final int attempt) {
		try {
			if (tryInstall()) {
				return;
			}
		}
		catch (final Exception e) {
			LogUtils.warn("MapMinimapOverlay install attempt failed: " + e.getMessage());
		}
		if (attempt >= 40) {
			LogUtils.warn("MapMinimapOverlay could not install after retries");
			return;
		}
		final Timer timer = new Timer(250, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				installWithRetry(attempt + 1);
			}
		});
		timer.setRepeats(false);
		timer.start();
	}

	private boolean tryInstall() {
		if (installed && panel.getParent() == layeredPane) {
			reposition();
			return true;
		}
		final Frame rawFrame = UITools.getFrame();
		if (!(rawFrame instanceof JFrame) || !rawFrame.isDisplayable()) {
			return false;
		}
		final JFrame frame = (JFrame) rawFrame;
		final Controller controller = Controller.getCurrentController();
		if (controller == null || controller.getMapViewManager() == null) {
			return false;
		}
		final JScrollPane scroll = controller.getMapViewManager().getScrollPane();
		if (scroll == null || !scroll.isDisplayable()) {
			return false;
		}
		final JViewport viewport = scroll.getViewport();
		if (viewport instanceof OverlayViewport) {
			final OverlayViewport ov = (OverlayViewport) viewport;
			final IViewportOverlay[] existing = ov.getOverlays();
			for (int i = 0; i < existing.length; i++) {
				if (existing[i] != null && existing[i].getComponent() == panel) {
					ov.removeOverlay(existing[i]);
				}
			}
		}

		layeredPane = frame.getLayeredPane();
		scrollPane = scroll;
		if (panel.getParent() != layeredPane) {
			if (panel.getParent() != null) {
				panel.getParent().remove(panel);
			}
			layeredPane.add(panel, JLayeredPane.PALETTE_LAYER);
		}
		scrollPane.addComponentListener(repositionListener);
		frame.addComponentListener(repositionListener);
		attachScrollListeners(scrollPane);
		scrollPane.getViewport().addPropertyChangeListener("view", new PropertyChangeListener() {
			public void propertyChange(final PropertyChangeEvent evt) {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						attachScrollListeners(scrollPane);
						reposition();
						panel.requestRefresh();
					}
				});
			}
		});
		scrollPane.addHierarchyListener(new HierarchyListener() {
			public void hierarchyChanged(final HierarchyEvent e) {
				if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
						|| (e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
					reposition();
					panel.requestRefresh();
				}
			}
		});
		Controller.getCurrentController().getMapViewManager()
				.addMapViewChangeListener(new IMapViewChangeListener() {
					public void afterViewChange(final Component oldView, final Component newView) {
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								attachScrollListeners(scrollPane);
								hookMapListeners();
								reposition();
								panel.requestRefresh();
							}
						});
					}

					public void afterViewClose(final Component oldView) {
						reposition();
					}

					public void afterViewCreated(final Component mapView) {
						reposition();
						panel.requestRefresh();
					}

					public void beforeViewChange(final Component oldView, final Component newView) {
					}
				});
		hookMapListeners();

		installed = true;
		reposition();
		panel.requestRefresh();
		LogUtils.info("MapMinimapOverlay installed on frame layered pane (bottom-right)");
		return true;
	}

	private void attachScrollListeners(final JScrollPane scroll) {
		if (scroll == null) {
			return;
		}
		final JScrollBar h = scroll.getHorizontalScrollBar();
		final JScrollBar v = scroll.getVerticalScrollBar();
		if (h != null) {
			h.removeAdjustmentListener(scrollListener);
			h.addAdjustmentListener(scrollListener);
		}
		if (v != null) {
			v.removeAdjustmentListener(scrollListener);
			v.addAdjustmentListener(scrollListener);
		}
	}

	private boolean mapListenersHooked;

	private void hookMapListeners() {
		if (mapListenersHooked) {
			return;
		}
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null || controller.getModeController() == null) {
				return;
			}
			final org.freeplane.features.map.MapController mapController = controller.getModeController()
			        .getMapController();
			mapController.addMapChangeListener(new org.freeplane.features.map.IMapChangeListener() {
				public void mapChanged(final org.freeplane.features.map.MapChangeEvent event) {
					panel.requestRefresh();
				}

				public void onNodeDeleted(final org.freeplane.features.map.NodeModel parent,
				        final org.freeplane.features.map.NodeModel child, final int index) {
					panel.requestRefresh();
				}

				public void onNodeInserted(final org.freeplane.features.map.NodeModel parent,
				        final org.freeplane.features.map.NodeModel child, final int newIndex) {
					panel.requestRefresh();
				}

				public void onNodeMoved(final org.freeplane.features.map.NodeModel oldParent, final int oldIndex,
				        final org.freeplane.features.map.NodeModel newParent,
				        final org.freeplane.features.map.NodeModel child, final int newIndex) {
					panel.requestRefresh();
				}

				public void onPreNodeMoved(final org.freeplane.features.map.NodeModel oldParent, final int oldIndex,
				        final org.freeplane.features.map.NodeModel newParent,
				        final org.freeplane.features.map.NodeModel child, final int newIndex) {
				}

				public void onPreNodeDelete(final org.freeplane.features.map.NodeModel oldParent,
				        final org.freeplane.features.map.NodeModel selectedNode, final int index) {
				}
			});
			mapController.addNodeSelectionListener(new org.freeplane.features.map.INodeSelectionListener() {
				public void onDeselect(final org.freeplane.features.map.NodeModel node) {
				}

				public void onSelect(final org.freeplane.features.map.NodeModel node) {
					panel.requestRefresh();
				}
			});
			mapListenersHooked = true;
		}
		catch (final Exception e) {
			// Mode may not be ready yet; afterViewChange retries.
		}
	}

	private void reposition() {
		if (panel == null || layeredPane == null || scrollPane == null) {
			return;
		}
		final boolean enabled = !"false".equalsIgnoreCase(ResourceController.getResourceController()
		        .getProperty(MapMinimapPanel.PROP_VISIBLE, "true"));
		if (!enabled || !scrollPane.isShowing() || !layeredPane.isShowing()
				|| !MapOverlayVisibility.isMindMapCanvasShowing()) {
			panel.setVisible(false);
			return;
		}
		try {
			final Dimension pref = panel.getPreferredSize();
			final int width = Math.max(1, pref.width);
			final int height = Math.max(1, pref.height);
			final Point spOnLp = SwingUtilities.convertPoint(scrollPane, new Point(0, 0), layeredPane);
			int x = spOnLp.x + scrollPane.getWidth() - width - 12;
			int y = spOnLp.y + scrollPane.getHeight() - height - 12;
			x = Math.max(0, Math.min(x, Math.max(0, layeredPane.getWidth() - width)));
			y = Math.max(0, Math.min(y, Math.max(0, layeredPane.getHeight() - height)));

			final Rectangle bounds = new Rectangle(x, y, width, height);
			final Rectangle old = panel.getBounds();
			if (old.width != width || old.height != height || old.x != x || old.y != y) {
				panel.setBounds(bounds);
				panel.revalidate();
				layeredPane.repaint(bounds.union(old));
			}
			if (!panel.isVisible()) {
				panel.setVisible(true);
			}
			if (scrollPane.getViewport() != null && scrollPane.getViewport().getView() instanceof MapView) {
				panel.requestRefresh();
			}
		}
		catch (final Exception e) {
			// Component may not be displayable yet during startup.
		}
	}
}
