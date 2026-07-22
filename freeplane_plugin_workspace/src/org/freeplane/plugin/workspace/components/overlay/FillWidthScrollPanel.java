package org.freeplane.plugin.workspace.components.overlay;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * Scroll view content that fills viewport width but never stretches taller than
 * its preferred height — avoids BoxLayout children with {@code Integer.MAX_VALUE}
 * max-height absorbing empty gaps inside a tall JScrollPane.
 */
public class FillWidthScrollPanel extends JPanel implements Scrollable {

	private static final long serialVersionUID = 1L;

	public FillWidthScrollPanel() {
		super();
	}

	public FillWidthScrollPanel(final LayoutManager layout) {
		super(layout);
	}

	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}

	public int getScrollableUnitIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
		return 16;
	}

	public int getScrollableBlockIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
		return orientation == SwingConstants.VERTICAL ? Math.max(16, visibleRect.height - 16)
		        : Math.max(16, visibleRect.width - 16);
	}

	public boolean getScrollableTracksViewportWidth() {
		return true;
	}

	public boolean getScrollableTracksViewportHeight() {
		return false;
	}
}
