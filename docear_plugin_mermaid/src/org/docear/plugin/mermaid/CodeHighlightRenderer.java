package org.docear.plugin.mermaid;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/** Simple monospace code preview with basic keyword highlighting. */
final class CodeHighlightRenderer {

	private CodeHighlightRenderer() {
	}

	static RichPreviewIcon render(final String language, final String source, final float zoom) {
		try {
			return RichPreviewIcon.fromNativeImage("code", draw(language, source, zoom));
		}
		catch (Throwable t) {
			return RichPreviewIcon.error("code", t.getMessage());
		}
	}

	private static BufferedImage draw(final String language, final String source, final float zoom) {
		final int maxW = RichPreviewScale.displayMaxWidth("code", zoom);
		final int maxH = RichPreviewScale.displayMaxHeight("code", zoom);
		final Font font = new Font(Font.MONOSPACED, Font.PLAIN, RichPreviewScale.fontSize(12, zoom));
		final FontMetrics fm = metrics(font);
		final String[] lines = source.split("\n", -1);
		final int lineCount = Math.min(lines.length, 40);
		int contentW = 80;
		for (int i = 0; i < lineCount; i++) {
			contentW = Math.max(contentW, fm.stringWidth(lines[i]) + 40);
		}
		final int w = Math.min(maxW, contentW);
		final int h = Math.min(maxH, lineCount * (fm.getHeight() + 2) + RichPreviewScale.fontSize(24, zoom));
		final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = img.createGraphics();
		try {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(new Color(0x1E, 0x1E, 0x1E));
			g2.fillRect(0, 0, w, h);
			g2.setFont(font);
			g2.setColor(new Color(0x88, 0x88, 0x88));
			g2.drawString(language != null ? language : "code", 8, RichPreviewScale.fontSize(16, zoom));
			final Set<String> keywords = keywordsFor(language);
			int y = RichPreviewScale.fontSize(28, zoom);
			for (int i = 0; i < lineCount && y < h - fm.getHeight(); i++) {
				drawLine(g2, fm, lines[i], keywords, 8, y, maxW);
				y += fm.getHeight() + 2;
			}
			if (lines.length > lineCount) {
				g2.setColor(new Color(0x88, 0x88, 0x88));
				g2.drawString("…", 8, Math.min(h - 6, y));
			}
		}
		finally {
			g2.dispose();
		}
		return img;
	}

	private static void drawLine(final Graphics2D g2, final FontMetrics fm, final String line,
			final Set<String> keywords, final int x, final int y, final int maxW) {
		final String[] tokens = line.split("(\\b)", -1);
		int cx = x;
		for (int i = 0; i < tokens.length; i++) {
			final String token = tokens[i];
			if (token.length() == 0) {
				continue;
			}
			if (keywords.contains(token)) {
				g2.setColor(new Color(0x56, 0x9C, 0xD6));
			}
			else if (token.startsWith("\"") || token.startsWith("'")) {
				g2.setColor(new Color(0xCE, 0x91, 0x78));
			}
			else if (token.startsWith("//") || token.startsWith("#")) {
				g2.setColor(new Color(0x6A, 0x99, 0x55));
			}
			else {
				g2.setColor(new Color(0xD4, 0xD4, 0xD4));
			}
			g2.drawString(token, cx, y);
			cx += fm.stringWidth(token);
			if (cx > maxW - 8) {
				break;
			}
		}
	}

	private static Set<String> keywordsFor(final String language) {
		final Set<String> set = new HashSet<String>();
		final String lang = language != null ? language.toLowerCase() : "";
		if (lang.indexOf("java") >= 0) {
			addAll(set, "public", "private", "protected", "class", "interface", "void", "int", "return",
					"if", "else", "for", "while", "new", "import", "package", "static", "final");
		}
		else if (lang.indexOf("py") >= 0 || "python".equals(lang)) {
			addAll(set, "def", "class", "import", "from", "return", "if", "else", "elif", "for", "while",
					"in", "True", "False", "None");
		}
		else if (lang.indexOf("js") >= 0 || "javascript".equals(lang) || "typescript".equals(lang)) {
			addAll(set, "function", "const", "let", "var", "return", "if", "else", "for", "while", "class",
					"import", "export", "async", "await");
		}
		else if ("sql".equals(lang)) {
			addAll(set, "select", "from", "where", "insert", "update", "delete", "join", "create", "table");
		}
		return set;
	}

	private static void addAll(final Set<String> set, final String... words) {
		for (int i = 0; i < words.length; i++) {
			set.add(words[i]);
		}
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
