package org.freeplane.core.ui.components;

import javax.swing.Icon;

/**
 * Icon that can re-layout itself to a maximum pixel width (e.g. tagged node text).
 */
public interface WidthConstrainedIcon extends Icon {
	/**
	 * @return this icon if already within {@code maxWidth}, otherwise a re-wrapped copy
	 */
	Icon withMaxWidth(int maxWidth);
}
