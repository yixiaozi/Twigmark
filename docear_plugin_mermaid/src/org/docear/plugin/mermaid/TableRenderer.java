package org.docear.plugin.mermaid;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Renders Markdown pipe tables to a preview image. */
final class TableRenderer {

	private TableRenderer() {
	}

	static RichPreviewIcon render(final String source) {
		try {
			final List<String[]> rows = parseTable(source);
			if (rows.isEmpty()) {
				return RichPreviewIcon.error("table", "empty table");
			}
			return RichPreviewIcon.fromImage("table", draw(rows));
		}
		catch (Throwable t) {
			return RichPreviewIcon.error("table", t.getMessage());
		}
	}

	private static List<String[]> parseTable(final String source) {
		final List<String[]> rows = new ArrayList<String[]>();
		final String[] lines = source.split("\n");
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			if (line.length() == 0 || line.indexOf('|') < 0) {
				continue;
			}
			if (line.startsWith("|")) {
				line = line.substring(1);
			}
			if (line.endsWith("|")) {
				line = line.substring(0, line.length() - 1);
			}
			if (line.matches("^[-:\\s|]+$")) {
				continue;
			}
			final String[] cells = line.split("\\|", -1);
			final List<String> trimmed = new ArrayList<String>();
			for (int c = 0; c < cells.length; c++) {
				trimmed.add(cells[c].trim());
			}
			rows.add(trimmed.toArray(new String[trimmed.size()]));
		}
		return rows;
	}

	private static BufferedImage draw(final List<String[]> rows) {
		final Font headerFont = new Font(Font.SANS_SERIF, Font.BOLD, 13);
		final Font cellFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
		final int cols = maxCols(rows);
		final int[] colWidths = new int[cols];
		final FontMetrics headerFm = metrics(headerFont);
		final FontMetrics cellFm = metrics(cellFont);
		for (int r = 0; r < rows.size(); r++) {
			final String[] row = rows.get(r);
			for (int c = 0; c < cols; c++) {
				final String text = c < row.length ? row[c] : "";
				final FontMetrics fm = r == 0 ? headerFm : cellFm;
				colWidths[c] = Math.max(colWidths[c], fm.stringWidth(text) + 16);
			}
		}
		int tableW = 1;
		for (int c = 0; c < cols; c++) {
			tableW += colWidths[c];
		}
		final int rowH = Math.max(headerFm.getHeight(), cellFm.getHeight()) + 10;
		final int tableH = rowH * rows.size() + 1;
		final int w = Math.min(RichPreviewIcon.MAX_WIDTH, tableW);
		final int h = Math.min(RichPreviewIcon.MAX_HEIGHT, tableH);
		final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = img.createGraphics();
		try {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(Color.WHITE);
			g2.fillRect(0, 0, w, h);
			g2.setColor(new Color(0xDD, 0xDD, 0xDD));
			int y = 0;
			for (int r = 0; r < rows.size() && y < h; r++) {
				int x = 0;
				if (r == 0) {
					g2.setColor(new Color(0xF0, 0xF4, 0xF8));
					g2.fillRect(0, y, w, rowH);
				}
				g2.setColor(new Color(0x33, 0x33, 0x33));
				g2.setFont(r == 0 ? headerFont : cellFont);
				final String[] row = rows.get(r);
				for (int c = 0; c < cols && x < w; c++) {
					final String text = c < row.length ? row[c] : "";
					g2.drawString(text, x + 8, y + rowH - 8);
					x += colWidths[c];
					g2.setColor(new Color(0xDD, 0xDD, 0xDD));
					g2.drawLine(x, y, x, y + rowH);
				}
				g2.drawLine(0, y + rowH, w, y + rowH);
				y += rowH;
			}
		}
		finally {
			g2.dispose();
		}
		return img;
	}

	private static int maxCols(final List<String[]> rows) {
		int max = 0;
		for (int i = 0; i < rows.size(); i++) {
			max = Math.max(max, rows.get(i).length);
		}
		return Math.max(1, max);
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
