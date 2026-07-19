package org.freeplane.core.ui.components;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Insets;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

import org.freeplane.core.ui.theme.DocearUiTheme;

/**
 * Keeps wrapped {@link JTabbedPane} rows in original top-to-bottom order and
 * paints iOS-like pill / segmented tabs (style only).
 */
public final class TabbedPaneStableOrder {
	private static final String APPLYING = "docear.stableTabOrder.applying";
	private static final String MARKER = "docear.stableTabOrder.installed";

	private TabbedPaneStableOrder() {
	}

	public static void install(final JTabbedPane tabs) {
		if (tabs == null) {
			return;
		}
		try {
			UIManager.getDefaults().put("TabbedPane.tabRunOverlay", Integer.valueOf(0));
			DocearUiTheme.styleTabbedPane(tabs);
			apply(tabs);
		}
		catch (Throwable t) {
			// Style must never prevent startup.
		}
	}

	private static void apply(final JTabbedPane tabs) {
		if (Boolean.TRUE.equals(tabs.getClientProperty(APPLYING))) {
			return;
		}
		final TabbedPaneUI current = tabs.getUI();
		if (isStableUi(current)) {
			tabs.putClientProperty(MARKER, Boolean.TRUE);
			return;
		}
		tabs.putClientProperty(APPLYING, Boolean.TRUE);
		try {
			tabs.setUI(new StableOrderTabbedPaneUI());
			tabs.putClientProperty(MARKER, Boolean.TRUE);
		}
		catch (Throwable t) {
			// leave default UI if customization fails
		}
		finally {
			tabs.putClientProperty(APPLYING, Boolean.FALSE);
		}
	}

	private static boolean isStableUi(final TabbedPaneUI ui) {
		return ui instanceof StableOrderTabbedPaneUI;
	}

	/**
	 * Wrap-stable layout + rounded pill tabs on a soft well.
	 */
	private static final class StableOrderTabbedPaneUI extends BasicTabbedPaneUI {
		private static final int PILL_INSET = 2;
		private static final int PILL_ARC = 10;

		protected boolean shouldRotateTabRuns(final int tabPlacement) {
			return false;
		}

		protected LayoutManager createLayoutManager() {
			if (tabPane.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT) {
				return super.createLayoutManager();
			}
			return new TabbedPaneLayout() {
				protected void rotateTabRuns(final int tabPlacement, final int selectedRun) {
					// no-op — keep first row on top
				}
			};
		}

		protected Insets getTabAreaInsets(final int tabPlacement) {
			return new Insets(4, 4, 3, 4);
		}

		protected Insets getContentBorderInsets(final int tabPlacement) {
			return new Insets(0, 0, 0, 0);
		}

		protected Insets getTabInsets(final int tabPlacement, final int tabIndex) {
			return new Insets(4, 7, 4, 7);
		}

		protected Insets getSelectedTabPadInsets(final int tabPlacement) {
			return new Insets(0, 0, 0, 0);
		}

		protected int calculateTabHeight(final int tabPlacement, final int tabIndex, final int fontHeight) {
			return Math.max(super.calculateTabHeight(tabPlacement, tabIndex, fontHeight), 28);
		}

		public void paint(final Graphics g, final JComponent c) {
			final Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(DocearUiTheme.TAB_WELL);
				g2.fillRect(0, 0, c.getWidth(), c.getHeight());
			}
			finally {
				g2.dispose();
			}
			super.paint(g, c);
		}

		protected void paintTabBackground(final Graphics g, final int tabPlacement, final int tabIndex, final int x,
				final int y, final int w, final int h, final boolean isSelected) {
			final Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				final int ix = x + PILL_INSET;
				final int iy = y + PILL_INSET;
				final int iw = Math.max(4, w - PILL_INSET * 2);
				final int ih = Math.max(4, h - PILL_INSET * 2);
				if (isSelected) {
					g2.setColor(DocearUiTheme.TAB_SELECTED);
					g2.fillRoundRect(ix, iy, iw, ih, PILL_ARC, PILL_ARC);
					g2.setColor(new Color(0x00, 0x00, 0x00, 18));
					g2.drawRoundRect(ix, iy, iw - 1, ih - 1, PILL_ARC, PILL_ARC);
				}
				else {
					g2.setColor(new Color(0xFF, 0xFF, 0xFF, 70));
					g2.fillRoundRect(ix, iy, iw, ih, PILL_ARC, PILL_ARC);
				}
			}
			finally {
				g2.dispose();
			}
		}

		protected void paintTabBorder(final Graphics g, final int tabPlacement, final int tabIndex, final int x,
				final int y, final int w, final int h, final boolean isSelected) {
			// Pills are self-contained; no classic square border.
		}

		protected void paintContentBorder(final Graphics g, final int tabPlacement, final int selectedIndex) {
			// No hard content rim — keeps the chrome airy.
		}

		protected void paintFocusIndicator(final Graphics g, final int tabPlacement, final Rectangle[] rects,
				final int tabIndex, final Rectangle iconRect, final Rectangle textRect, final boolean isSelected) {
			// No focus ring on tabs.
		}

		protected void paintText(final Graphics g, final int tabPlacement, final Font font, final FontMetrics metrics,
				final int tabIndex, final String title, final Rectangle textRect, final boolean isSelected) {
			if (title == null) {
				return;
			}
			final Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				final boolean enabled = tabPane.isEnabled() && tabPane.isEnabledAt(tabIndex);
				g2.setFont(isSelected ? DocearUiTheme.font(11.5f, Font.BOLD) : DocearUiTheme.font(11.5f, Font.PLAIN));
				if (!enabled) {
					g2.setColor(DocearUiTheme.TEXT_FAINT);
				}
				else {
					g2.setColor(isSelected ? DocearUiTheme.TEXT : DocearUiTheme.TEXT_MUTED);
				}
				g2.drawString(title, textRect.x, textRect.y + metrics.getAscent());
			}
			finally {
				g2.dispose();
			}
		}
	}
}
