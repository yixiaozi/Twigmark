package org.freeplane.view.swing.map;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;

import javax.swing.JComponent;

import org.freeplane.features.nodestyle.NodeStyleController;
import org.freeplane.view.swing.features.filepreview.ExternalResource;

class ContentPaneLayout implements LayoutManager {
	public void addLayoutComponent(final String name, final Component comp) {
	}

	public void layoutContainer(final Container parent) {
		final int componentCount = parent.getComponentCount();
		final int width = parent.getWidth();
		NodeView view = (NodeView) parent.getParent();
		final MapView map = view.getMap();
		final NodeStyleController ncs = NodeStyleController.getController(map.getModeController());
		final int maxWidth = limitWidthToAttachedImage(parent, map, ncs.getMaxWidth(view.getModel()));
		int y = 0;
		for (int i = 0; i < componentCount; i++) {
			final Component component = parent.getComponent(i);
			if (component.isVisible()) {
				component.validate();
				final Dimension preferredCompSize;
				int labelMaxWidth = maxWidth;
				if (width > 0) {
					final float zoom = map.getZoom();
					final int unzoomedWidth = (zoom > 0f && zoom != 1f) ? Math.round(width / zoom) : width;
					labelMaxWidth = Math.min(maxWidth, unzoomedWidth);
				}
				if( width == 0) 
					preferredCompSize = new Dimension();
				else if (component instanceof ZoomableLabel){
					preferredCompSize=  ((ZoomableLabel)component).getPreferredSize(labelMaxWidth);
				}
				else{
					preferredCompSize=  component.getPreferredSize();
				}
				
				if (component instanceof MainView) {
					component.setBounds(0, y, width, preferredCompSize.height);
				}
				else {
					if(width > preferredCompSize.width){
						final int x = (int) (component.getAlignmentX() * (width - preferredCompSize.width));
						component.setBounds(x, y, preferredCompSize.width, preferredCompSize.height);
					}
					else{
						component.setBounds(0, y, width, preferredCompSize.height);
					}
				}
				y += preferredCompSize.height;
			}
			else{
				component.setBounds(0, y, 0, 0);
			}
		}
	}

	public Dimension minimumLayoutSize(final Container parent) {
		return preferredLayoutSize(parent);
	}

	public Dimension preferredLayoutSize(final Container parent) {
		NodeView view = (NodeView) parent.getParent();
		final MapView map = view.getMap();
		final NodeStyleController ncs = NodeStyleController.getController(map.getModeController());
		final int width = limitWidthToAttachedImage(parent, map, ncs.getMaxWidth(view.getModel()));
		final Dimension prefSize = new Dimension(0, 0);
		final int componentCount = parent.getComponentCount();
		for (int i = 0; i < componentCount; i++) {
			final Component component = parent.getComponent(i);
			if (component.isVisible()) {
				component.validate();
				final Dimension preferredCompSize;
				if(component instanceof ZoomableLabel)
					preferredCompSize = ((ZoomableLabel)component).getPreferredSize(width);
				else
					preferredCompSize = component.getPreferredSize();
				
				prefSize.height += preferredCompSize.height;
				prefSize.width = Math.max(prefSize.width, preferredCompSize.width);
			}
		}
		return prefSize;
	}

	public void removeLayoutComponent(final Component comp) {
	}

	/** When a node has an attached image narrower than the style max width, wrap text to the image. */
	private static int limitWidthToAttachedImage(final Container parent, final MapView map, final int styleMaxWidth) {
		int imageWidth = 0;
		final int componentCount = parent.getComponentCount();
		for (int i = 0; i < componentCount; i++) {
			final Component component = parent.getComponent(i);
			if (!component.isVisible() || !(component instanceof JComponent)) {
				continue;
			}
			if (((JComponent) component).getClientProperty(ExternalResource.class) == null) {
				continue;
			}
			imageWidth = Math.max(imageWidth, component.getPreferredSize().width);
		}
		if (imageWidth <= 0) {
			return styleMaxWidth;
		}
		final float zoom = map.getZoom();
		if (zoom > 0f && zoom != 1f) {
			imageWidth = Math.round(imageWidth / zoom);
		}
		return Math.min(styleMaxWidth, imageWidth);
	}
}