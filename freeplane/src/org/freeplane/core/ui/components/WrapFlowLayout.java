package org.freeplane.core.ui.components;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * FlowLayout that wraps to the next line when the container is narrow.
 */
public class WrapFlowLayout extends FlowLayout {

	private static final long serialVersionUID = 1L;

	public WrapFlowLayout() {
		this(LEFT, 4, 2);
	}

	public WrapFlowLayout(final int align, final int hgap, final int vgap) {
		super(align, hgap, vgap);
	}

	public Dimension preferredLayoutSize(final Container target) {
		return layoutSize(target, true);
	}

	public Dimension minimumLayoutSize(final Container target) {
		return layoutSize(target, false);
	}

	private Dimension layoutSize(final Container target, final boolean preferred) {
		synchronized (target.getTreeLock()) {
			final Insets insets = target.getInsets();
			int maxWidth = target.getWidth();
			if (maxWidth <= 0 && target.getParent() != null) {
				maxWidth = target.getParent().getWidth();
			}
			if (maxWidth <= 0) {
				maxWidth = Integer.MAX_VALUE;
			}
			maxWidth = Math.max(1, maxWidth - insets.left - insets.right);
			int x = 0;
			int y = 0;
			int rowHeight = 0;
			final int hgap = getHgap();
			final int vgap = getVgap();
			for (int i = 0; i < target.getComponentCount(); i++) {
				final Component comp = target.getComponent(i);
				if (!comp.isVisible()) {
					continue;
				}
				final Dimension size = preferred ? comp.getPreferredSize() : comp.getMinimumSize();
				if (x > 0 && x + size.width > maxWidth) {
					x = 0;
					y += rowHeight + vgap;
					rowHeight = 0;
				}
				rowHeight = Math.max(rowHeight, size.height);
				x += size.width + hgap;
			}
			return new Dimension(maxWidth + insets.left + insets.right,
					y + rowHeight + insets.top + insets.bottom + vgap);
		}
	}

	public void layoutContainer(final Container target) {
		synchronized (target.getTreeLock()) {
			final Insets insets = target.getInsets();
			final int maxWidth = Math.max(1, target.getWidth() - insets.left - insets.right);
			int x = insets.left;
			int y = insets.top;
			int rowHeight = 0;
			final int hgap = getHgap();
			final int vgap = getVgap();
			for (int i = 0; i < target.getComponentCount(); i++) {
				final Component comp = target.getComponent(i);
				if (!comp.isVisible()) {
					continue;
				}
				final Dimension size = comp.getPreferredSize();
				if (x > insets.left && x + size.width > insets.left + maxWidth) {
					x = insets.left;
					y += rowHeight + vgap;
					rowHeight = 0;
				}
				comp.setBounds(x, y, size.width, size.height);
				x += size.width + hgap;
				rowHeight = Math.max(rowHeight, size.height);
			}
		}
	}
}
