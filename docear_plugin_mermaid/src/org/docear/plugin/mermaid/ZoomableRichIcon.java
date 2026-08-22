package org.docear.plugin.mermaid;

import java.awt.image.BufferedImage;

import org.freeplane.core.ui.components.WidthConstrainedIcon;

/**
 * Node preview icon that can open a full-size zoom dialog (double-click).
 */
public interface ZoomableRichIcon extends WidthConstrainedIcon {

	String getKindLabel();

	BufferedImage getFullImage();
}
