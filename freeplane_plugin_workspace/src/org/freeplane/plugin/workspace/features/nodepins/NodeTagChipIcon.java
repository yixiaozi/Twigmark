package org.freeplane.plugin.workspace.features.nodepins;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;

/**
 * Paints node title plus soft pill chips for {@code 【tag】} markers. Used only for
 * canvas display; edit still uses the raw node text.
 */
public final class NodeTagChipIcon implements Icon {

	private static final int CHIP_ARC = 12;
	private static final int CHIP_PAD_X = 7;
	private static final int CHIP_PAD_Y = 2;
	private static final int GAP_TITLE_CHIP = 6;
	private static final int GAP_CHIPS = 4;

	private final String title;
	private final List tags = new ArrayList();
	private final Font titleFont;
	private final Font chipFont;
	private final Color titleColor;
	private final int width;
	private final int height;

	public NodeTagChipIcon(final String title, final Set tagNames, final Font baseFont, final Color titleColor) {
		this.title = title != null ? title : "";
		if (tagNames != null) {
			for (final Iterator it = tagNames.iterator(); it.hasNext();) {
				final String tag = (String) it.next();
				if (tag != null && tag.length() > 0) {
					tags.add(tag);
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
		if (this.title.length() > 0) {
			w += titleFm.stringWidth(this.title);
		}
		for (int i = 0; i < tags.size(); i++) {
			if (w > 0) {
				w += i == 0 ? GAP_TITLE_CHIP : GAP_CHIPS;
			}
			final String tag = (String) tags.get(i);
			w += chipFm.stringWidth(tag) + CHIP_PAD_X * 2;
		}
		final int titleH = this.title.length() > 0 ? titleFm.getHeight() : 0;
		final int chipH = tags.isEmpty() ? 0 : chipFm.getHeight() + CHIP_PAD_Y * 2;
		this.width = Math.max(1, w);
		this.height = Math.max(titleH, chipH);
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
		if (title.length() > 0) {
			g2.setFont(titleFont);
			g2.setColor(titleColor);
			final int titleY = midY + (titleFm.getAscent() - titleFm.getDescent()) / 2;
			g2.drawString(title, cursorX, titleY);
			cursorX += titleFm.stringWidth(title) + GAP_TITLE_CHIP;
		}
		final int chipH = chipFm.getHeight() + CHIP_PAD_Y * 2;
		final int chipTop = midY - chipH / 2;
		for (int i = 0; i < tags.size(); i++) {
			final String tag = (String) tags.get(i);
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
			cursorX += chipW + GAP_CHIPS;
		}
		g2.dispose();
	}

	private static FontMetrics metrics(final Font font) {
		final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1,
				java.awt.image.BufferedImage.TYPE_INT_ARGB);
		return img.getGraphics().getFontMetrics(font);
	}
}
