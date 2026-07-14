package org.freeplane.plugin.workspace.components.favorites;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * FlowLayout variant that wraps components onto multiple rows and sizes itself
 * against the enclosing viewport width when placed in a JScrollPane.
 */
public class WrapFlowLayout extends FlowLayout {

	private static final long serialVersionUID = 1L;

	public WrapFlowLayout() {
		super(FlowLayout.LEFT, 6, 6);
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
			final int maxWidth = Math.max(1, resolveWrapWidth(target) - insets.left - insets.right);
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
			final int maxWidth = Math.max(1, resolveWrapWidth(target) - insets.left - insets.right);
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
				rowHeight = Math.max(rowHeight, size.height);
				x += size.width + hgap;
			}
		}
	}

	private static int resolveWrapWidth(final Container target) {
		int width = target.getWidth();
		final JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
		if (scroll != null && scroll.getViewport() != null) {
			final int viewportWidth = scroll.getViewport().getWidth();
			if (viewportWidth > 0) {
				width = viewportWidth;
			}
		}
		if (width <= 0) {
			width = 220;
		}
		return width;
	}
}
