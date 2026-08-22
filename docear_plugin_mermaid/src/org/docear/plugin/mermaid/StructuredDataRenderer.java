package org.docear.plugin.mermaid;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Renders JSON/YAML small-data previews as an indented tree view. */
final class StructuredDataRenderer {

	private static final int INDENT = 14;
	private static final int MAX_LINES = 28;
	private static final int PAD_X = 10;
	private static final int PAD_TOP = 22;

	private StructuredDataRenderer() {
	}

	static RichPreviewIcon render(final String format, final String source, final float zoom) {
		try {
			final StructuredValue root = StructuredDataParser.parse(source, format);
			return RichPreviewIcon.fromNativeImage(format != null ? format : "data", draw(format, root, zoom));
		}
		catch (Throwable t) {
			return RichPreviewIcon.error(format != null ? format : "data", t.getMessage());
		}
	}

	private static BufferedImage draw(final String format, final StructuredValue root, final float zoom) {
		final int maxW = RichPreviewScale.displayMaxWidth(format != null ? format : "data", zoom);
		final int maxH = RichPreviewScale.displayMaxHeight(format != null ? format : "data", zoom);
		final Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, RichPreviewScale.fontSize(11, zoom));
		final Font keyFont = new Font(Font.MONOSPACED, Font.BOLD, RichPreviewScale.fontSize(12, zoom));
		final Font valueFont = new Font(Font.MONOSPACED, Font.PLAIN, RichPreviewScale.fontSize(12, zoom));
		final FontMetrics labelFm = metrics(labelFont);
		final FontMetrics keyFm = metrics(keyFont);
		final FontMetrics valueFm = metrics(valueFont);
		final List<Line> lines = new ArrayList<Line>();
		flatten(root, 0, null, lines);
		if (lines.size() > MAX_LINES) {
			lines.subList(MAX_LINES, lines.size()).clear();
			lines.add(new Line(0, LineKind.META, "…", null));
		}
		int maxContentW = 120;
		for (int i = 0; i < lines.size(); i++) {
			maxContentW = Math.max(maxContentW, lineWidth(lines.get(i), keyFm, valueFm) + PAD_X * 2);
		}
		final int lineH = Math.max(keyFm.getHeight(), valueFm.getHeight()) + 3;
		final int w = Math.min(maxW, maxContentW);
		final int h = Math.min(maxH, PAD_TOP + lines.size() * lineH + 8);
		final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = img.createGraphics();
		try {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(new Color(0xFA, 0xFB, 0xFC));
			g2.fillRect(0, 0, w, h);
			g2.setFont(labelFont);
			g2.setColor(new Color(0x66, 0x6E, 0x76));
			final String badge = format != null ? format.toUpperCase() : "DATA";
			g2.drawString(badge, PAD_X, 14);
			int y = PAD_TOP;
			for (int i = 0; i < lines.size() && y < h - 4; i++) {
				drawLine(g2, lines.get(i), keyFont, valueFont, keyFm, valueFm, y, w);
				y += lineH;
			}
		}
		finally {
			g2.dispose();
		}
		return img;
	}

	private static void flatten(final StructuredValue value, final int depth, final String key,
			final List<Line> lines) {
		if (value.kind == StructuredValue.Kind.SCALAR) {
			lines.add(new Line(depth, LineKind.ENTRY, key, value.scalar));
			return;
		}
		if (value.kind == StructuredValue.Kind.OBJECT) {
			if (key != null) {
				lines.add(new Line(depth, LineKind.GROUP, key, "{…}"));
			}
			for (final Map.Entry<String, StructuredValue> e : value.fields.entrySet()) {
				flatten(e.getValue(), key == null ? depth : depth + 1, e.getKey(), lines);
				if (lines.size() >= MAX_LINES) {
					return;
				}
			}
			return;
		}
		if (key != null) {
			lines.add(new Line(depth, LineKind.GROUP, key, "[…]"));
		}
		for (int i = 0; i < value.items.size(); i++) {
			final StructuredValue item = value.items.get(i);
			if (item.kind == StructuredValue.Kind.SCALAR) {
				lines.add(new Line(key == null ? depth : depth + 1, LineKind.ITEM, null, item.scalar));
			}
			else {
				flatten(item, key == null ? depth : depth + 1, "[" + i + "]", lines);
			}
			if (lines.size() >= MAX_LINES) {
				return;
			}
		}
	}

	private static void drawLine(final Graphics2D g2, final Line line, final Font keyFont, final Font valueFont,
			final FontMetrics keyFm, final FontMetrics valueFm, final int y, final int maxW) {
		int x = PAD_X + line.depth * INDENT;
		if (line.kind == LineKind.GROUP) {
			g2.setFont(keyFont);
			g2.setColor(new Color(0x09, 0x6D, 0xD9));
			g2.drawString(line.key + " ", x, y);
			x += keyFm.stringWidth(line.key + " ");
			g2.setFont(valueFont);
			g2.setColor(new Color(0x99, 0x99, 0x99));
			g2.drawString(line.value, x, y);
			return;
		}
		if (line.kind == LineKind.ITEM) {
			g2.setFont(valueFont);
			g2.setColor(new Color(0x55, 0x55, 0x55));
			g2.drawString("• " + line.value, x, y);
			return;
		}
		if (line.kind == LineKind.META) {
			g2.setFont(valueFont);
			g2.setColor(new Color(0x99, 0x99, 0x99));
			g2.drawString(line.value, x, y);
			return;
		}
		if (line.key != null) {
			g2.setFont(keyFont);
			g2.setColor(new Color(0x09, 0x6D, 0xD9));
			g2.drawString(line.key + ": ", x, y);
			x += keyFm.stringWidth(line.key + ": ");
		}
		g2.setFont(valueFont);
		g2.setColor(colorForValue(line.value));
		final String text = clip(line.value, maxW - x - PAD_X, valueFm);
		g2.drawString(text, x, y);
	}

	private static Color colorForValue(final String value) {
		if (value == null) {
			return new Color(0x99, 0x99, 0x99);
		}
		if ("true".equals(value) || "false".equals(value) || "null".equals(value)) {
			return new Color(0x82, 0x5A, 0xDF);
		}
		if (value.length() > 0 && (value.charAt(0) == '-' || Character.isDigit(value.charAt(0)))) {
			try {
				Double.parseDouble(value);
				return new Color(0x0A, 0x7A, 0x3B);
			}
			catch (NumberFormatException e) {
				// keep string color
			}
		}
		return new Color(0x55, 0x55, 0x55);
	}

	private static int lineWidth(final Line line, final FontMetrics keyFm, final FontMetrics valueFm) {
		int w = PAD_X + line.depth * INDENT;
		if (line.kind == LineKind.GROUP) {
			return w + keyFm.stringWidth(line.key + " ") + valueFm.stringWidth(line.value);
		}
		if (line.kind == LineKind.ITEM || line.kind == LineKind.META) {
			return w + valueFm.stringWidth(line.value != null ? line.value : "");
		}
		if (line.key != null) {
			w += keyFm.stringWidth(line.key + ": ");
		}
		return w + valueFm.stringWidth(line.value != null ? line.value : "");
	}

	private static String clip(final String text, final int maxPx, final FontMetrics fm) {
		if (text == null) {
			return "";
		}
		if (fm.stringWidth(text) <= maxPx) {
			return text;
		}
		String s = text;
		while (s.length() > 1 && fm.stringWidth(s + "…") > maxPx) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "…";
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

	private enum LineKind {
		ENTRY, GROUP, ITEM, META
	}

	private static final class Line {
		final int depth;
		final LineKind kind;
		final String key;
		final String value;

		Line(final int depth, final LineKind kind, final String key, final String value) {
			this.depth = depth;
			this.kind = kind;
			this.key = key;
			this.value = value;
		}
	}
}
