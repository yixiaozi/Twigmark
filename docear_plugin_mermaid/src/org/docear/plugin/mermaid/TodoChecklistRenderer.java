package org.docear.plugin.mermaid;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Icon;

/**
 * Checklist / todo fence preview using GFM {@code - [ ]}/{@code - [x]} lines.
 */
final class TodoChecklistRenderer {

	static final class Item {
		final boolean done;
		final String text;
		final int sourceLineIndex;

		Item(final boolean done, final String text, final int sourceLineIndex) {
			this.done = done;
			this.text = text;
			this.sourceLineIndex = sourceLineIndex;
		}
	}

	private static final Pattern ITEM = Pattern.compile("^(\\s*[-*]\\s+)\\[([ xX])\\]\\s*(.*)$");
	private static final Pattern PLAIN = Pattern.compile("^(\\s*[-*]\\s+)(.*)$");

	private TodoChecklistRenderer() {
	}

	static List parseItems(final String source) {
		final List items = new ArrayList();
		if (source == null) {
			return items;
		}
		final String[] lines = source.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			final String line = lines[i];
			final Matcher m = ITEM.matcher(line);
			if (m.matches()) {
				items.add(new Item("x".equalsIgnoreCase(m.group(2)), m.group(3).trim(), i));
				continue;
			}
			final Matcher p = PLAIN.matcher(line);
			if (p.matches() && p.group(2).trim().length() > 0) {
				items.add(new Item(false, p.group(2).trim(), i));
			}
		}
		return items;
	}

	static String toggleItem(final String source, final int sourceLineIndex) {
		final String[] lines = source.split("\n", -1);
		if (sourceLineIndex < 0 || sourceLineIndex >= lines.length) {
			return source;
		}
		final String line = lines[sourceLineIndex];
		final Matcher m = ITEM.matcher(line);
		if (m.matches()) {
			final boolean done = "x".equalsIgnoreCase(m.group(2));
			lines[sourceLineIndex] = m.group(1) + (done ? "[ ] " : "[x] ") + m.group(3);
		}
		else {
			final Matcher p = PLAIN.matcher(line);
			if (p.matches()) {
				lines[sourceLineIndex] = p.group(1) + "[x] " + p.group(2);
			}
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				sb.append('\n');
			}
			sb.append(lines[i]);
		}
		return sb.toString();
	}

	static boolean allDone(final List items) {
		if (items.isEmpty()) {
			return false;
		}
		for (int i = 0; i < items.size(); i++) {
			if (!((Item) items.get(i)).done) {
				return false;
			}
		}
		return true;
	}

	static boolean anyOpen(final List items) {
		for (int i = 0; i < items.size(); i++) {
			if (!((Item) items.get(i)).done) {
				return true;
			}
		}
		return false;
	}

	static InteractiveTodoIcon render(final String source, final float zoom) {
		final List items = parseItems(source);
		if (items.isEmpty()) {
			return InteractiveTodoIcon.errorPlaceholder(source);
		}
		return InteractiveTodoIcon.fromItems(source, items, zoom);
	}
}

/** Interactive checklist preview with per-row hit regions. */
final class InteractiveTodoIcon implements ZoomableRichIcon {

	private final RichPreviewIcon delegate;
	private final List itemBounds;
	private final List items;
	private final String source;

	static InteractiveTodoIcon fromItems(final String source, final List items, final float zoom) {
		final List bounds = new ArrayList();
		final BufferedImage img = draw(items, bounds, zoom);
		return new InteractiveTodoIcon(RichPreviewIcon.fromNativeImage("todo", img), items, bounds, source);
	}

	static InteractiveTodoIcon errorPlaceholder(final String source) {
		return new InteractiveTodoIcon(RichPreviewIcon.placeholder("todo", "todo: - [ ] item"), new ArrayList(),
		        new ArrayList(), source != null ? source : "");
	}

	private InteractiveTodoIcon(final RichPreviewIcon delegate, final List items, final List itemBounds,
			final String source) {
		this.delegate = delegate;
		this.items = items;
		this.itemBounds = itemBounds;
		this.source = source;
	}

	int hitItem(final int x, final int y) {
		for (int i = 0; i < itemBounds.size(); i++) {
			final Rectangle r = (Rectangle) itemBounds.get(i);
			if (r.contains(x, y)) {
				return i;
			}
		}
		return -1;
	}

	TodoChecklistRenderer.Item getItem(final int index) {
		return (TodoChecklistRenderer.Item) items.get(index);
	}

	String getSource() {
		return source;
	}

	InteractiveTodoIcon withDisplayScaleRatio(final float ratio) {
		if (delegate.getFullImage() == null || Math.abs(ratio - 1f) < 0.01f) {
			return this;
		}
		final RichPreviewIcon scaled = delegate.withDisplayScaleRatio(ratio);
		return new InteractiveTodoIcon(scaled, items, scaleBounds(itemBounds, ratio), source);
	}

	InteractiveTodoIcon withDisplayScale(final float scale) {
		return withDisplayScaleRatio(scale);
	}

	private static List scaleBounds(final List bounds, final float scale) {
		if (Math.abs(scale - 1f) < 0.01f) {
			return bounds;
		}
		final List out = new ArrayList();
		for (int i = 0; i < bounds.size(); i++) {
			final Rectangle r = (Rectangle) bounds.get(i);
			out.add(new Rectangle(Math.round(r.x * scale), Math.round(r.y * scale), Math.round(r.width * scale),
			        Math.round(r.height * scale)));
		}
		return out;
	}

	@Override
	public String getKindLabel() {
		return "todo";
	}

	@Override
	public BufferedImage getFullImage() {
		return delegate.getFullImage();
	}

	@Override
	public Icon withMaxWidth(final int newMaxWidth) {
		final Icon wrapped = delegate.withMaxWidth(newMaxWidth);
		if (wrapped instanceof RichPreviewIcon) {
			return new InteractiveTodoIcon((RichPreviewIcon) wrapped, items, itemBounds, source);
		}
		return wrapped;
	}

	@Override
	public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
		delegate.paintIcon(c, g, x, y);
	}

	@Override
	public int getIconWidth() {
		return delegate.getIconWidth();
	}

	@Override
	public int getIconHeight() {
		return delegate.getIconHeight();
	}

	private static BufferedImage draw(final List items, final List boundsOut, final float zoom) {
		final int maxW = RichPreviewScale.displayMaxWidth("todo", zoom);
		final int maxH = RichPreviewScale.displayMaxHeight("todo", zoom);
		final Font badgeFont = new Font(Font.SANS_SERIF, Font.BOLD, RichPreviewScale.fontSize(10, zoom));
		final Font textFont = new Font(Font.SANS_SERIF, Font.PLAIN, RichPreviewScale.fontSize(12, zoom));
		final FontMetrics textFm = metrics(textFont);
		final int rowH = textFm.getHeight() + RichPreviewScale.fontSize(8, zoom);
		final int padX = RichPreviewScale.fontSize(10, zoom);
		final int padTop = RichPreviewScale.fontSize(20, zoom);
		int maxText = 120;
		for (int i = 0; i < items.size(); i++) {
			maxText = Math.max(maxText, textFm.stringWidth(((TodoChecklistRenderer.Item) items.get(i)).text));
		}
		final int w = Math.min(maxW, padX * 2 + RichPreviewScale.fontSize(22, zoom) + maxText);
		final int h = Math.min(maxH, padTop + items.size() * rowH + 8);
		final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = img.createGraphics();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(new Color(0xFF, 0xFB, 0xF5));
			g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
			g2.setColor(new Color(0xF5, 0xD0, 0xA0));
			g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
			g2.setFont(badgeFont);
			g2.setColor(new Color(0x9A, 0x34, 0x12));
			g2.drawString("TODO", padX, RichPreviewScale.fontSize(14, zoom));

			int y = padTop;
			for (int i = 0; i < items.size() && y + rowH <= h; i++) {
				final TodoChecklistRenderer.Item item = (TodoChecklistRenderer.Item) items.get(i);
				final int box = RichPreviewScale.fontSize(14, zoom);
				final int boxX = padX;
				final int boxY = y + (rowH - box) / 2 - 2;
				boundsOut.add(new Rectangle(0, y - 2, w, rowH));
				g2.setColor(Color.WHITE);
				g2.fillRoundRect(boxX, boxY, box, box, 3, 3);
				g2.setColor(item.done ? new Color(0x16, 0xA3, 0x4A) : new Color(0xCB, 0xD5, 0xE1));
				g2.drawRoundRect(boxX, boxY, box, box, 3, 3);
				if (item.done) {
					g2.setColor(new Color(0x16, 0xA3, 0x4A));
					g2.drawLine(boxX + 3, boxY + 7, boxX + 6, boxY + 10);
					g2.drawLine(boxX + 6, boxY + 10, boxX + 11, boxY + 3);
				}
				g2.setFont(textFont);
				g2.setColor(item.done ? new Color(0x64, 0x74, 0x8B) : new Color(0x1E, 0x29, 0x3B));
				String text = item.text;
				while (text.length() > 1 && textFm.stringWidth(text) > w - padX - 28) {
					text = text.substring(0, text.length() - 1);
				}
				if (!text.equals(item.text)) {
					text = text + "…";
				}
				g2.drawString(text, padX + RichPreviewScale.fontSize(22, zoom), y + textFm.getAscent() + 2);
				y += rowH;
			}
		}
		finally {
			g2.dispose();
		}
		return img;
	}

	private static FontMetrics metrics(final Font font) {
		final BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = img.createGraphics();
		try {
			return g2.getFontMetrics(font);
		}
		finally {
			g2.dispose();
		}
	}
}
