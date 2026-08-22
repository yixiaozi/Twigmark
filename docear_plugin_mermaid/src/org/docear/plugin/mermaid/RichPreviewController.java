package org.docear.plugin.mermaid;

import java.awt.AWTEvent;
import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.features.mode.Controller;
import org.freeplane.view.swing.map.ZoomableLabel;

/** Global double-click handler for {@link ZoomableRichIcon} node previews. */
final class RichPreviewController {

	private static volatile boolean installed;

	private RichPreviewController() {
	}

	static void install() {
		if (installed) {
			return;
		}
		installed = true;
		java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(new java.awt.event.AWTEventListener() {
			@Override
			public void eventDispatched(final AWTEvent event) {
				if (!(event instanceof MouseEvent)) {
					return;
				}
				final MouseEvent me = (MouseEvent) event;
				if (me.getID() != MouseEvent.MOUSE_CLICKED || me.getClickCount() < 2) {
					return;
				}
				if (!(me.getComponent() instanceof ZoomableLabel)) {
					return;
				}
				final ZoomableLabel label = (ZoomableLabel) me.getComponent();
				final Object iconObj = label.getClientProperty(ZoomableLabel.TEXT_RENDERING_ICON);
				if (!(iconObj instanceof ZoomableRichIcon)) {
					return;
				}
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						RichPreviewDialog.show((ZoomableRichIcon) iconObj);
					}
				});
			}
		}, AWTEvent.MOUSE_EVENT_MASK);
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller != null) {
				controller.getMapViewManager().addMapViewChangeListener(new IMapViewChangeListener() {
					@Override
					public void afterViewChange(final java.awt.Component oldView,
							final java.awt.Component newView) {
					}

					@Override
					public void afterViewClose(final java.awt.Component oldView) {
					}

					@Override
					public void afterViewCreated(final java.awt.Component mapView) {
						// listener registered
					}

					@Override
					public void beforeViewChange(final java.awt.Component oldView,
							final java.awt.Component newView) {
					}
				});
			}
		}
		catch (Throwable t) {
			LogUtils.warn("RichPreview: map listener registration failed", t);
		}
		LogUtils.info("RichPreview: double-click zoom installed");
	}
}
