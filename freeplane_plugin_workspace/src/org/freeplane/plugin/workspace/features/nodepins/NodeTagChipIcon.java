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

import org.freeplane.core.ui.components.WidthConstrainedIcon;

/**
 * Paints node content as ordered text / tag-chip segments so {@code 【tag】} stays
 * where it appears in the node string. Supports wrapping when {@code maxWidth} is set
 * so long tagged nodes no longer stay on a single clipped line.
 */
public final class NodeTagChipIcon implements WidthConstrainedIcon {

	private static final int CHIP_ARC = 12;
	private static final int CHIP_PAD_X = 7;
	private static final int CHIP_PAD_Y = 2;
	private static final int GAP_AFTER_TEXT = 6;
	private static final int GAP_AFTER_CHIP = 4;
	private static final int LINE_GAP = 2;

	private final List segments = new ArrayList();
	private final Font titleFont;
	private final Font chipFont;
	private final Color titleColor;
	private final int maxWidth;
	private final List lines = new ArrayList();
	private final int width;
	private final int height;

	public NodeTagChipIcon(final List displaySegments, final Font baseFont, final Color titleColor) {
		this(displaySegments, baseFont, titleColor, Integer.MAX_VALUE);
	}

	public NodeTagChipIcon(final List displaySegments, final Font baseFont, final Color titleColor,
			final int maxWidth) {
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
		this.maxWidth = maxWidth > 0 ? maxWidth : Integer.MAX_VALUE;
		final FontMetrics titleFm = metrics(this.titleFont);
		final FontMetrics chipFm = metrics(this.chipFont);
		layoutLines(titleFm, chipFm);
		int w = 1;
		int h = 0;
		for (int i = 0; i < lines.size(); i++) {
			final LineLayout line = (LineLayout) lines.get(i);
			w = Math.max(w, line.width);
			if (i > 0) {
				h += LINE_GAP;
			}
			h += line.height;
		}
		this.width = Math.max(1, w);
		this.height = Math.max(1, h);
	}

	/** Return a re-wrapped icon for a tighter layout width (e.g. after zoom). */
	public NodeTagChipIcon withMaxWidth(final int newMaxWidth) {
		if (newMaxWidth <= 0 || (newMaxWidth >= maxWidth && getIconWidth() <= newMaxWidth)) {
			return this;
		}
		return new NodeTagChipIcon(segments, titleFont, titleColor, newMaxWidth);
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
		int cursorY = y;
		for (int li = 0; li < lines.size(); li++) {
			final LineLayout line = (LineLayout) lines.get(li);
			if (li > 0) {
				cursorY += LINE_GAP;
			}
			int cursorX = x;
			final int midY = cursorY + line.height / 2;
			final int chipH = chipFm.getHeight() + CHIP_PAD_Y * 2;
			final int chipTop = midY - chipH / 2;
			for (int i = 0; i < line.parts.size(); i++) {
				final TagDisplaySegment segment = (TagDisplaySegment) line.parts.get(i);
				if (i > 0) {
					final TagDisplaySegment previous = (TagDisplaySegment) line.parts.get(i - 1);
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
			cursorY += line.height;
		}
		g2.dispose();
	}

	private void layoutLines(final FontMetrics titleFm, final FontMetrics chipFm) {
		lines.clear();
		LineLayout current = new LineLayout();
		final int titleH = titleFm.getHeight();
		final int chipH = chipFm.getHeight() + CHIP_PAD_Y * 2;
		for (int i = 0; i < segments.size(); i++) {
			final TagDisplaySegment segment = (TagDisplaySegment) segments.get(i);
			if (segment.isTag()) {
				final int chipW = chipFm.stringWidth(segment.getValue()) + CHIP_PAD_X * 2;
				if (!current.parts.isEmpty() && current.width + GAP_AFTER_CHIP + chipW > maxWidth) {
					lines.add(current);
					current = new LineLayout();
				}
				if (!current.parts.isEmpty()) {
					current.width += GAP_AFTER_CHIP;
				}
				current.parts.add(segment);
				current.width += chipW;
				current.height = Math.max(current.height, chipH);
			}
			else {
				current = appendWrappedText(current, segment.getValue(), titleFm, titleH);
			}
		}
		if (!current.parts.isEmpty() || lines.isEmpty()) {
			lines.add(current);
		}
	}

	private LineLayout appendWrappedText(LineLayout current, final String text, final FontMetrics titleFm,
			final int titleH) {
		if (text == null || text.length() == 0) {
			return current;
		}
		int offset = 0;
		while (offset < text.length()) {
			final int gap = current.parts.isEmpty() ? 0 : GAP_AFTER_TEXT;
			final int avail = maxWidth == Integer.MAX_VALUE ? Integer.MAX_VALUE
					: Math.max(8, maxWidth - current.width - gap);
			int fit = fitChars(text, offset, avail, titleFm);
			if (fit <= 0) {
				if (current.parts.isEmpty()) {
					fit = Math.max(1, fitChars(text, offset, maxWidth, titleFm));
					final String part = text.substring(offset, offset + fit);
					current.parts.add(TagDisplaySegment.text(part));
					current.width += titleFm.stringWidth(part);
					current.height = Math.max(current.height, titleH);
					offset += fit;
					if (offset < text.length()) {
						lines.add(current);
						current = new LineLayout();
					}
				}
				else {
					lines.add(current);
					current = new LineLayout();
				}
				continue;
			}
			if (!current.parts.isEmpty()) {
				current.width += GAP_AFTER_TEXT;
			}
			final String part = text.substring(offset, offset + fit);
			current.parts.add(TagDisplaySegment.text(part));
			current.width += titleFm.stringWidth(part);
			current.height = Math.max(current.height, titleH);
			offset += fit;
			if (offset < text.length()) {
				lines.add(current);
				current = new LineLayout();
			}
		}
		return current;
	}

	private static int fitChars(final String text, final int offset, final int availPx, final FontMetrics fm) {
		if (availPx <= 0 || offset >= text.length()) {
			return 0;
		}
		int lo = 1;
		int hi = text.length() - offset;
		int best = 0;
		while (lo <= hi) {
			final int mid = (lo + hi) >>> 1;
			final int w = fm.stringWidth(text.substring(offset, offset + mid));
			if (w <= availPx) {
				best = mid;
				lo = mid + 1;
			}
			else {
				hi = mid - 1;
			}
		}
		return best;
	}

	private static FontMetrics metrics(final Font font) {
		final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1,
				java.awt.image.BufferedImage.TYPE_INT_ARGB);
		return img.getGraphics().getFontMetrics(font);
	}

	private static final class LineLayout {
		private final List parts = new ArrayList();
		private int width;
		private int height;
	}
}
