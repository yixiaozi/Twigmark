package org.docear.plugin.core.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JViewport;

import org.docear.plugin.core.ui.IViewportOverlay.VIEW_CHANGE;

/**
 * Viewport wrapper kept for lifecycle hooks. Do <b>not</b> add interactive overlays
 * as children here — {@link JViewport#setView}/{@code ViewportLayout} treat child 0
 * as the map view; an overlay child blanks the canvas.
 * <p>
 * Interactive chrome such as the tag filter must host on the frame layered pane
 * (see {@link MapTagFilterOverlay}).
 */
public class OverlayViewport extends JViewport {

	private static final long serialVersionUID = 1L;
	private final OverlayLayoutManager layoutManager;
	private final List<IViewportOverlay> overlayComponents = new ArrayList<IViewportOverlay>();

	public OverlayViewport(JViewport viewport) {
		this.layoutManager = new OverlayLayoutManager(viewport.getLayout());
		super.setLayout(this.layoutManager);
		super.addContainerListener(new ContainerListener() {
			public void componentRemoved(ContainerEvent e) {
				dispatchChangeEvent(VIEW_CHANGE.REMOVE, e);
			}

			public void componentAdded(ContainerEvent e) {
				dispatchChangeEvent(VIEW_CHANGE.ADD, e);
			}
		});
	}

	protected void dispatchChangeEvent(VIEW_CHANGE changeType, ContainerEvent e) {
		synchronized (overlayComponents) {
			for (IViewportOverlay overlay : overlayComponents) {
				overlay.viewChanged(changeType, e);
			}
		}
	}

	public void enableOverlays(boolean b) {
		synchronized (overlayComponents) {
			for (IViewportOverlay overlay : overlayComponents) {
				Component comp = overlay.getComponent();
				if (comp != null) {
					comp.setVisible(b);
				}
			}
		}
	}

	/**
	 * Registers overlay metadata only — does <em>not</em> add the component as a
	 * viewport child (that breaks map rendering).
	 */
	public void addOverlay(IViewportOverlay overlay) {
		synchronized (overlayComponents) {
			this.overlayComponents.add(overlay);
			overlay.setParent(this);
			Component comp = overlay.getComponent();
			if (comp == null) {
				throw new RuntimeException("IViewportOverlay.getComponent() must not return NULL");
			}
			this.layoutManager.addLayoutComponent(overlay.getPositionConstraints(), comp);
			fireStateChanged();
		}
	}

	public void removeOverlay(IViewportOverlay overlay) {
		synchronized (overlayComponents) {
			this.overlayComponents.remove(overlay);
			overlay.setParent(null);
			Component comp = overlay.getComponent();
			if (comp == null) {
				throw new RuntimeException("IViewportOverlay.getComponent() must not return NULL");
			}
			this.layoutManager.removeLayoutComponent(comp);
			if (comp.getParent() == this) {
				remove(comp);
			}
			revalidate();
			repaint();
			fireStateChanged();
		}
	}

	public IViewportOverlay[] getOverlays() {
		synchronized (overlayComponents) {
			return overlayComponents.toArray(new IViewportOverlay[0]);
		}
	}

	public void paint(Graphics g) {
		super.paint(g);
	}

	public JComponent getIntersectingOverlay(Point point) {
		synchronized (overlayComponents) {
			for (IViewportOverlay overlay : overlayComponents) {
				Component comp = overlay.getComponent();
				if (comp != null && comp.getParent() == this && comp.getBounds().contains(point) && comp.isVisible()) {
					return (JComponent) comp;
				}
			}
		}
		return null;
	}

	public void setLayout(LayoutManager layout) {
	}
}
