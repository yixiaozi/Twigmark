package org.freeplane.core.ui.theme;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Thin, pill-thumb scrollbar (iOS-like). No arrow buttons.
 */
public final class DocearScrollBarUI extends BasicScrollBarUI {
	private static final int THICKNESS = 9;
	private static final Color THUMB = new Color(0xB8, 0xC0, 0xCC);
	private static final Color THUMB_HOVER = new Color(0x8E, 0x99, 0xA8);

	public static ComponentUI createUI(final JComponent c) {
		return new DocearScrollBarUI();
	}

	protected void configureScrollBarColors() {
		thumbColor = THUMB;
		thumbDarkShadowColor = THUMB;
		thumbHighlightColor = THUMB;
		thumbLightShadowColor = THUMB;
		trackColor = new Color(0, 0, 0, 0);
		trackHighlightColor = trackColor;
	}

	public Dimension getPreferredSize(final JComponent c) {
		if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
			return new Dimension(THICKNESS, 48);
		}
		return new Dimension(48, THICKNESS);
	}

	protected JButton createDecreaseButton(final int orientation) {
		return zeroButton();
	}

	protected JButton createIncreaseButton(final int orientation) {
		return zeroButton();
	}

	private static JButton zeroButton() {
		final JButton button = new JButton();
		button.setPreferredSize(new Dimension(0, 0));
		button.setMinimumSize(new Dimension(0, 0));
		button.setMaximumSize(new Dimension(0, 0));
		button.setOpaque(false);
		button.setBorder(null);
		button.setFocusable(false);
		return button;
	}

	protected void paintTrack(final Graphics g, final JComponent c, final Rectangle trackBounds) {
		// Transparent track — content shows through.
	}

	protected void paintThumb(final Graphics g, final JComponent c, final Rectangle thumbBounds) {
		if (thumbBounds == null || thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
			return;
		}
		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final boolean hover = isThumbRollover();
		g2.setColor(hover ? THUMB_HOVER : THUMB);
		final int gap = 2;
		if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
			final int x = thumbBounds.x + gap;
			final int w = Math.max(4, thumbBounds.width - gap * 2);
			final int y = thumbBounds.y + 1;
			final int h = Math.max(16, thumbBounds.height - 2);
			g2.fillRoundRect(x, y, w, h, 999, 999);
		}
		else {
			final int y = thumbBounds.y + gap;
			final int h = Math.max(4, thumbBounds.height - gap * 2);
			final int x = thumbBounds.x + 1;
			final int w = Math.max(16, thumbBounds.width - 2);
			g2.fillRoundRect(x, y, w, h, 999, 999);
		}
		g2.dispose();
	}
}
