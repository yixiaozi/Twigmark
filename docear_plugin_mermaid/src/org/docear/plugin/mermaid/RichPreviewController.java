package org.docear.plugin.mermaid;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.SwingUtilities;

import org.freeplane.core.ui.AMouseListener;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.view.swing.map.MainView;
import org.freeplane.view.swing.map.MapView;
import org.freeplane.view.swing.map.NodeView;
import org.freeplane.view.swing.map.ZoomableLabel;
import org.freeplane.view.swing.map.ZoomableLabelUI;

/**
 * Rich preview interaction: todo click-toggle, mouse-wheel scale, SE-corner drag resize.
 */
final class RichPreviewController {

	private static final String HOOK_KEY = "org.docear.richpreview.hook";
	private static final String MAP_HOOK_KEY = "org.docear.richpreview.map.hook";
	private static final int HANDLE = 10;
	private static volatile boolean installed;

	private static volatile boolean resizing;
	private static volatile MainView resizeView;
	private static volatile float resizeStartZoom;
	private static volatile int resizeStartWidth;

	private static final AMouseListener PREVIEW_LISTENER = new AMouseListener() {
		@Override
		public void mouseClicked(final MouseEvent e) {
			if (e.getButton() != MouseEvent.BUTTON1 || resizing) {
				return;
			}
			if (!(e.getComponent() instanceof MainView)) {
				return;
			}
			final MainView mainView = (MainView) e.getComponent();
			final ZoomableRichIcon icon = previewIcon(mainView);
			if (icon == null) {
				return;
			}
			final Rectangle previewR = getPreviewR(mainView);
			if (previewR == null || previewR.width <= 0 || previewR.height <= 0 || !previewR.contains(e.getPoint())) {
				return;
			}

			if (e.getClickCount() == 1 && icon instanceof InteractiveTodoIcon && !nearSeHandle(previewR, e.getX(), e.getY())) {
				final InteractiveTodoIcon todo = (InteractiveTodoIcon) icon;
				final int localX = e.getX() - previewR.x;
				final int localY = e.getY() - previewR.y;
				final int hit = todo.hitItem(localX, localY);
				if (hit >= 0) {
					e.consume();
					final TodoChecklistRenderer.Item item = todo.getItem(hit);
					final String next = TodoChecklistRenderer.toggleItem(todo.getSource(), item.sourceLineIndex);
					final NodeView nodeView = mainView.getNodeView();
					if (nodeView != null) {
						TodoNodeSync.applyAfterToggle(nodeView.getModel(), next);
					}
					return;
				}
			}
		}

		@Override
		public void mousePressed(final MouseEvent e) {
			if (e.getButton() != MouseEvent.BUTTON1 || !(e.getComponent() instanceof MainView)) {
				return;
			}
			final MainView mainView = (MainView) e.getComponent();
			if (previewIcon(mainView) == null) {
				return;
			}
			final Rectangle previewR = getPreviewR(mainView);
			if (previewR == null || !nearSeHandle(previewR, e.getX(), e.getY())) {
				return;
			}
			final NodeView nodeView = mainView.getNodeView();
			if (nodeView == null) {
				return;
			}
			resizing = true;
			resizeView = mainView;
			resizeStartZoom = RichPreviewScale.get(nodeView.getModel());
			resizeStartWidth = Math.max(1, previewR.width);
			mainView.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
			e.consume();
		}

		@Override
		public void mouseReleased(final MouseEvent e) {
			if (resizing) {
				resizing = false;
				final NodeView nodeView = resizeView != null ? resizeView.getNodeView() : null;
				resizeView = null;
				if (e.getComponent() != null) {
					e.getComponent().setCursor(Cursor.getDefaultCursor());
				}
				if (nodeView != null) {
					RichPreviewScale.commit(nodeView.getModel());
				}
				e.consume();
			}
		}

		@Override
		public void mouseDragged(final MouseEvent e) {
			if (!resizing || resizeView == null) {
				return;
			}
			final Rectangle previewR = getPreviewR(resizeView);
			if (previewR == null) {
				return;
			}
			final int newW = Math.max(40, e.getX() - previewR.x);
			final float zoom = RichPreviewScale.clamp(resizeStartZoom * newW / (float) resizeStartWidth);
			final NodeView nodeView = resizeView.getNodeView();
			if (nodeView != null) {
				RichPreviewScale.setInterim(nodeView.getModel(), zoom);
			}
			e.consume();
		}

		@Override
		public void mouseMoved(final MouseEvent e) {
			if (!(e.getComponent() instanceof MainView) || resizing) {
				return;
			}
			final MainView mainView = (MainView) e.getComponent();
			final Rectangle previewR = getPreviewR(mainView);
			if (previewR == null) {
				mainView.setCursor(Cursor.getDefaultCursor());
				return;
			}
			if (nearSeHandle(previewR, e.getX(), e.getY())) {
				mainView.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
			}
			else if (previewR.contains(e.getPoint())) {
				mainView.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
			else {
				mainView.setCursor(Cursor.getDefaultCursor());
			}
		}

		@Override
		public void mouseExited(final MouseEvent e) {
			if (!resizing && e.getComponent() != null) {
				e.getComponent().setCursor(Cursor.getDefaultCursor());
			}
		}
	};

	private static final MouseWheelListener WHEEL_LISTENER = new MouseWheelListener() {
		@Override
		public void mouseWheelMoved(final MouseWheelEvent e) {
			if (e.getComponent() instanceof MainView) {
				handlePreviewWheel((MainView) e.getComponent(), e);
			}
		}
	};

	private static final MouseWheelListener MAP_WHEEL_LISTENER = new MouseWheelListener() {
		@Override
		public void mouseWheelMoved(final MouseWheelEvent e) {
			if (!(e.getComponent() instanceof MapView)) {
				return;
			}
			final MapView mapView = (MapView) e.getComponent();
			final Point p = e.getPoint();
			final Component at = SwingUtilities.getDeepestComponentAt(mapView, p.x, p.y);
			if (at instanceof MainView) {
				handlePreviewWheel((MainView) at, e);
			}
		}
	};

	private static void handlePreviewWheel(final MainView mainView, final MouseWheelEvent e) {
		Point point = e.getPoint();
		if (e.getComponent() != mainView) {
			point = SwingUtilities.convertPoint(e.getComponent(), point, mainView);
		}
		if (previewIcon(mainView) == null) {
			return;
		}
		final Rectangle previewR = getPreviewR(mainView);
		if (previewR == null || !previewR.contains(point)) {
			return;
		}
		final NodeView nodeView = mainView.getNodeView();
		if (nodeView == null) {
			return;
		}
		final float current = RichPreviewScale.get(nodeView.getModel());
		final float factor = e.getPreciseWheelRotation() < 0 ? 1.1f : (1f / 1.1f);
		final float next = RichPreviewScale.clamp(current * factor);
		if (Math.abs(next - current) < 0.01f) {
			e.consume();
			return;
		}
		RichPreviewScale.setInterim(nodeView.getModel(), next);
		e.consume();
	}

	private static ZoomableRichIcon previewIcon(final MainView mainView) {
		final Object iconObj = mainView.getClientProperty(ZoomableLabel.TEXT_RENDERING_ICON);
		return iconObj instanceof ZoomableRichIcon ? (ZoomableRichIcon) iconObj : null;
	}

	private RichPreviewController() {
	}

	private static boolean nearSeHandle(final Rectangle previewR, final int x, final int y) {
		return x >= previewR.x + previewR.width - HANDLE && x <= previewR.x + previewR.width + 2
		        && y >= previewR.y + previewR.height - HANDLE && y <= previewR.y + previewR.height + 2;
	}

	/** Rich previews paint into {@code textR}, not {@code iconR} (left-side node icons). */
	private static Rectangle getPreviewR(final MainView mainView) {
		if (!(mainView.getUI() instanceof ZoomableLabelUI)) {
			return null;
		}
		final Object iconObj = mainView.getClientProperty(ZoomableLabel.TEXT_RENDERING_ICON);
		if (!(iconObj instanceof ZoomableRichIcon)) {
			return null;
		}
		return ((ZoomableLabelUI) mainView.getUI()).getTextR(mainView);
	}

	static void install(final ModeController modeController) {
		if (installed || modeController == null) {
			return;
		}
		installed = true;
		modeController.getMapController().addNodeChangeListener(new INodeChangeListener() {
			@Override
			public void nodeChanged(final NodeChangeEvent event) {
				scheduleAttach(event.getNode());
			}
		});
		try {
			final Controller controller = modeController.getController();
			if (controller != null) {
				controller.getMapViewManager().addMapViewChangeListener(new IMapViewChangeListener() {
					@Override
					public void afterViewChange(final java.awt.Component oldView,
							final java.awt.Component newView) {
						if (newView instanceof MapView) {
							attachAllMainViews((MapView) newView);
						}
					}

					@Override
					public void afterViewClose(final java.awt.Component oldView) {
					}

					@Override
					public void afterViewCreated(final java.awt.Component mapView) {
						if (mapView instanceof MapView) {
							attachAllMainViews((MapView) mapView);
						}
					}

					@Override
					public void beforeViewChange(final java.awt.Component oldView,
							final java.awt.Component newView) {
					}
				});
				final java.awt.Component current = controller.getMapViewManager().getMapViewComponent();
				if (current instanceof MapView) {
					attachAllMainViews((MapView) current);
				}
			}
		}
		catch (Throwable t) {
			LogUtils.warn("RichPreview: map listener registration failed", t);
		}
		LogUtils.info("RichPreview: zoom/resize installed");
	}

	private static void scheduleAttach(final NodeModel node) {
		if (node == null) {
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					final Controller controller = Controller.getCurrentController();
					if (controller == null) {
						return;
					}
					final java.awt.Component comp = controller.getMapViewManager().getComponent(node);
					if (comp instanceof MainView) {
						attachToMainView((MainView) comp);
					}
				}
				catch (Throwable t) {
					LogUtils.warn("RichPreview: attach failed", t);
				}
			}
		});
	}

	private static void attachAllMainViews(final MapView mapView) {
		attachMapWheel(mapView);
		RichPreviewScale.purgeLegacyZoomOnTree(mapView.getRoot().getModel());
		attachToNodeView(mapView.getRoot());
	}

	private static void attachMapWheel(final MapView mapView) {
		if (mapView == null || mapView.getClientProperty(MAP_HOOK_KEY) != null) {
			return;
		}
		mapView.putClientProperty(MAP_HOOK_KEY, Boolean.TRUE);
		mapView.addMouseWheelListener(MAP_WHEEL_LISTENER);
	}

	private static void attachToNodeView(final NodeView nodeView) {
		if (nodeView == null) {
			return;
		}
		final java.awt.Component content = nodeView.getContent();
		if (content instanceof MainView) {
			attachToMainView((MainView) content);
		}
		for (int i = 0; i < nodeView.getComponentCount(); i++) {
			final java.awt.Component child = nodeView.getComponent(i);
			if (child instanceof NodeView) {
				attachToNodeView((NodeView) child);
			}
		}
	}

	private static void attachToMainView(final MainView mainView) {
		if (mainView == null || mainView.getClientProperty(HOOK_KEY) != null) {
			return;
		}
		mainView.putClientProperty(HOOK_KEY, Boolean.TRUE);
		mainView.addMouseListener(PREVIEW_LISTENER);
		mainView.addMouseMotionListener(PREVIEW_LISTENER);
		mainView.addMouseWheelListener(WHEEL_LISTENER);
	}
}
