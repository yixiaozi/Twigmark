package org.docear.plugin.core.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.workspace.components.mapactivity.MapActivityOverlayPanel;
import org.freeplane.plugin.workspace.features.mapactivity.MapActivityOverlayController;

/**
 * Hosts the current-map activity panel on the frame {@link JLayeredPane}
 * at the top-LEFT (tag filter stays top-RIGHT).
 */
public class MapActivityOverlay implements MapActivityOverlayPanel.LayoutListener, MapOverlayVisibility.Listener {

	private static MapActivityOverlay instance;

	private final MapActivityOverlayPanel panel;
	private JLayeredPane layeredPane;
	private JScrollPane scrollPane;
	private boolean installed;
	private boolean userPositioned;
	private Point lastLocation;

	private final ComponentAdapter repositionListener = new ComponentAdapter() {
		public void componentResized(final ComponentEvent e) {
			reposition(false);
		}

		public void componentMoved(final ComponentEvent e) {
			reposition(false);
		}

		public void componentShown(final ComponentEvent e) {
			reposition(false);
		}
	};

	private MapActivityOverlay() {
		panel = new MapActivityOverlayPanel();
		panel.setVisible(false);
		panel.setLayoutListener(this);
		MapActivityOverlayController.getInstance().setPanel(panel);
		MapOverlayVisibility.addListener(this);
	}

	public void onMapCanvasVisibilityMaybeChanged() {
		reposition(false);
	}

	public void onPanelLayoutChanged() {
		if (panel.takeHomeRequest()) {
			userPositioned = false;
			lastLocation = null;
		}
		else if (panel.takePositionDirty()) {
			userPositioned = true;
			lastLocation = new Point(panel.getX(), panel.getY());
		}
		reposition(true);
	}

	public static synchronized MapActivityOverlay getInstance() {
		if (instance == null) {
			instance = new MapActivityOverlay();
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
			LogUtils.warn("MapActivityOverlay install attempt failed: " + e.getMessage());
		}
		if (attempt >= 40) {
			LogUtils.warn("MapActivityOverlay could not install after retries");
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
			reposition(true);
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
		scrollPane.getViewport().addPropertyChangeListener("view", new PropertyChangeListener() {
			public void propertyChange(final PropertyChangeEvent evt) {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						reposition(false);
					}
				});
			}
		});
		scrollPane.addHierarchyListener(new HierarchyListener() {
			public void hierarchyChanged(final HierarchyEvent e) {
				if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
						|| (e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
					reposition(false);
				}
			}
		});
		Controller.getCurrentController().getMapViewManager()
				.addMapViewChangeListener(new org.freeplane.features.ui.IMapViewChangeListener() {
					public void afterViewChange(final Component oldView, final Component newView) {
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								userPositioned = false;
								lastLocation = null;
								MapActivityOverlayController.getInstance().refreshUi();
								reposition(true);
							}
						});
					}

					public void afterViewClose(final Component oldView) {
						reposition(false);
					}

					public void afterViewCreated(final Component mapView) {
						reposition(false);
					}

					public void beforeViewChange(final Component oldView, final Component newView) {
					}
				});

		installed = true;
		panel.setVisible(true);
		reposition(true);
		LogUtils.info("MapActivityOverlay installed on frame layered pane (top-left)");
		return true;
	}

	private void reposition(final boolean forceSize) {
		if (panel == null || layeredPane == null || scrollPane == null) {
			return;
		}
		if (!scrollPane.isShowing() || !layeredPane.isShowing() || !MapOverlayVisibility.isMindMapCanvasShowing()) {
			panel.setVisible(false);
			return;
		}
		try {
			final Dimension pref = panel.getPreferredSize();
			final int width = Math.max(1, pref.width);
			final int height = Math.max(1, pref.height);

			int x;
			int y;
			if (userPositioned && lastLocation != null) {
				x = lastLocation.x;
				y = lastLocation.y;
			}
			else if (userPositioned) {
				x = panel.getX();
				y = panel.getY();
			}
			else {
				final Point spOnLp = SwingUtilities.convertPoint(scrollPane, new Point(0, 0), layeredPane);
				// Top-LEFT mirror of the tag filter's top-RIGHT placement.
				x = spOnLp.x + 12;
				y = spOnLp.y + 12;
			}

			x = Math.max(0, Math.min(x, Math.max(0, layeredPane.getWidth() - width)));
			y = Math.max(0, Math.min(y, Math.max(0, layeredPane.getHeight() - height)));

			final Rectangle bounds = new Rectangle(x, y, width, height);
			final Rectangle old = panel.getBounds();
			final boolean sizeChanged = old.width != width || old.height != height;
			if (forceSize || sizeChanged || old.x != x || old.y != y) {
				panel.setBounds(bounds);
			}
			lastLocation = new Point(x, y);
			if (!panel.isVisible()) {
				panel.setVisible(true);
			}
			panel.revalidate();
			layeredPane.repaint();
		}
		catch (final Exception e) {
			// Component may not be displayable yet during startup.
		}
	}
}
