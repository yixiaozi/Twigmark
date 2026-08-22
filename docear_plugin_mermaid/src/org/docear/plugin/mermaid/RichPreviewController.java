package org.docear.plugin.mermaid;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;

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

/** Double-click on preview icon area opens zoom dialog (MainView only, icon hit-test). */
final class RichPreviewController {

	private static final String HOOK_KEY = "org.docear.richpreview.hook";
	private static volatile boolean installed;
	private static volatile long lastOpenMs;

	private static final AMouseListener PREVIEW_LISTENER = new AMouseListener() {
		@Override
		public void mouseClicked(final MouseEvent e) {
			if (e.getButton() != MouseEvent.BUTTON1 || e.getClickCount() != 2) {
				return;
			}
			if (!(e.getComponent() instanceof MainView)) {
				return;
			}
			final MainView mainView = (MainView) e.getComponent();
			final Object iconObj = mainView.getClientProperty(ZoomableLabel.TEXT_RENDERING_ICON);
			if (!(iconObj instanceof ZoomableRichIcon)) {
				return;
			}
			final ZoomableRichIcon icon = (ZoomableRichIcon) iconObj;
			if (icon.getFullImage() == null) {
				return;
			}
			final Rectangle iconR = ((ZoomableLabelUI) mainView.getUI()).getIconR(mainView);
			if (iconR == null || iconR.width <= 0 || iconR.height <= 0 || !iconR.contains(e.getPoint())) {
				return;
			}
			final long now = System.currentTimeMillis();
			if (now - lastOpenMs < 400L) {
				return;
			}
			lastOpenMs = now;
			e.consume();
			RichPreviewDialog.show(icon);
		}
	};

	private RichPreviewController() {
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
		LogUtils.info("RichPreview: icon double-click zoom installed");
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
		attachToNodeView(mapView.getRoot());
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
	}
}
