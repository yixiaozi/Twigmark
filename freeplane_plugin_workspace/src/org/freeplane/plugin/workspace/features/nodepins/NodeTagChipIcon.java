package org.freeplane.plugin.workspace.features.nodepins;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;

/**
 * Paints node content as ordered text / tag-chip segments so {@code 【tag】} stays
 * where it appears in the node string (not forced to the end).
 */
public final class NodeTagChipIcon implements Icon {

	private static final int CHIP_ARC = 12;
	private static final int CHIP_PAD_X = 7;
	private static final int CHIP_PAD_Y = 2;
	private static final int GAP_AFTER_TEXT = 6;
	private static final int GAP_AFTER_CHIP = 4;

	private final List segments = new ArrayList();
	private final Font titleFont;
	private final Font chipFont;
	private final Color titleColor;
	private final int width;
	private final int height;

	public NodeTagChipIcon(final List displaySegments, final Font baseFont, final Color titleColor) {
		if (displaySegments != null) {
			for (int i = 0; i < displaySegments.size(); i++) {
				final Object item = displaySegments.get(i);
				if (item instanceof TagDisplaySegment) {
					final TagDisplaySegment segment = (TagDisplaySegment) item;
					if (segment.getValue().length() > 0) {
						segments.add(segment);
					}
				}
			}
		}
		final Font safeBase = baseFont != null ? baseFont : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
		this.titleFont = safeBase;
		this.chipFont = safeBase.deriveFont(Math.max(9f, safeBase.getSize2D() * 0.88f));
		this.titleColor = titleColor != null ? titleColor : Color.BLACK;
		final FontMetrics titleFm = metrics(this.titleFont);
		final FontMetrics chipFm = metrics(this.chipFont);
		int w = 0;
		int titleH = 0;
		int chipH = 0;
		for (int i = 0; i < segments.size(); i++) {
			final TagDisplaySegment segment = (TagDisplaySegment) segments.get(i);
			if (i > 0) {
				final TagDisplaySegment previous = (TagDisplaySegment) segments.get(i - 1);
				w += previous.isTag() ? GAP_AFTER_CHIP : GAP_AFTER_TEXT;
			}
			if (segment.isTag()) {
				w += chipFm.stringWidth(segment.getValue()) + CHIP_PAD_X * 2;
				chipH = Math.max(chipH, chipFm.getHeight() + CHIP_PAD_Y * 2);
			}
			else {
				w += titleFm.stringWidth(segment.getValue());
				titleH = Math.max(titleH, titleFm.getHeight());
			}
		}
		this.width = Math.max(1, w);
		this.height = Math.max(1, Math.max(titleH, chipH));
	}

	public int getIconWidth() {
		return width;
	}

	public int getIconHeight() {
		return height;
	}

	public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		final FontMetrics titleFm = g2.getFontMetrics(titleFont);
		final FontMetrics chipFm = g2.getFontMetrics(chipFont);
		int cursorX = x;
		final int midY = y + height / 2;
		final int chipH = chipFm.getHeight() + CHIP_PAD_Y * 2;
		final int chipTop = midY - chipH / 2;
		for (int i = 0; i < segments.size(); i++) {
			final TagDisplaySegment segment = (TagDisplaySegment) segments.get(i);
			if (i > 0) {
				final TagDisplaySegment previous = (TagDisplaySegment) segments.get(i - 1);
				cursorX += previous.isTag() ? GAP_AFTER_CHIP : GAP_AFTER_TEXT;
			}
			if (segment.isTag()) {
				final String tag = segment.getValue();
				final int textW = chipFm.stringWidth(tag);
				final int chipW = textW + CHIP_PAD_X * 2;
				final Color bg = TagColorStore.getInstance().getColor(tag);
				final Color fg = TagColorStore.contrastingTextColor(bg);
				g2.setColor(bg);
				g2.fillRoundRect(cursorX, chipTop, chipW, chipH, CHIP_ARC, CHIP_ARC);
				g2.setFont(chipFont);
				g2.setColor(fg);
				final int textY = chipTop + CHIP_PAD_Y + chipFm.getAscent();
				g2.drawString(tag, cursorX + CHIP_PAD_X, textY);
				cursorX += chipW;
			}
			else {
				g2.setFont(titleFont);
				g2.setColor(titleColor);
				final int titleY = midY + (titleFm.getAscent() - titleFm.getDescent()) / 2;
				g2.drawString(segment.getValue(), cursorX, titleY);
				cursorX += titleFm.stringWidth(segment.getValue());
			}
		}
		g2.dispose();
	}

	private static FontMetrics metrics(final Font font) {
		final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1,
				java.awt.image.BufferedImage.TYPE_INT_ARGB);
		return img.getGraphics().getFontMetrics(font);
	}
}
