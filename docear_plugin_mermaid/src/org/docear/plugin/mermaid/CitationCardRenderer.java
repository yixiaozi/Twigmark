package org.docear.plugin.mermaid;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Renders a literature citation as a compact preview card. */
final class CitationCardRenderer {

	private CitationCardRenderer() {
	}

	static RichPreviewIcon render(final String source, final float zoom) {
		try {
			final CitationResolve.Meta meta = CitationResolve.resolve(source);
			return RichPreviewIcon.fromNativeImage("cite", draw(meta, zoom));
		}
		catch (Throwable t) {
			return RichPreviewIcon.error("cite", t.getMessage());
		}
	}

	private static BufferedImage draw(final CitationResolve.Meta meta, final float zoom) {
		final int maxW = RichPreviewScale.displayMaxWidth("cite", zoom);
		final Font badgeFont = new Font(Font.SANS_SERIF, Font.BOLD, RichPreviewScale.fontSize(10, zoom));
		final Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, RichPreviewScale.fontSize(13, zoom));
		final Font metaFont = new Font(Font.SANS_SERIF, Font.PLAIN, RichPreviewScale.fontSize(11, zoom));
		final Font idFont = new Font(Font.MONOSPACED, Font.PLAIN, RichPreviewScale.fontSize(10, zoom));
		final FontMetrics titleFm = metrics(titleFont);
		final FontMetrics metaFm = metrics(metaFont);
		final FontMetrics idFm = metrics(idFont);

		final String title = wrap(meta.title.length() > 0 ? meta.title : meta.id, titleFm, maxW - 24, 2);
		final String authors = ellipsize(meta.authors, metaFm, maxW - 24);
		final String line3 = joinNonEmpty(" · ", meta.year, meta.venue);
		final String idLine = ellipsize(meta.id, idFm, maxW - 24);

		final int titleLines = countLines(title);
		final int h = 18 + titleLines * (titleFm.getHeight() + 2) + 8
		        + (authors.length() > 0 ? metaFm.getHeight() + 2 : 0)
		        + (line3.length() > 0 ? metaFm.getHeight() + 2 : 0) + idFm.getHeight() + 14;
		final int w = Math.min(maxW, Math.max(200,
		        Math.max(titleFm.stringWidth(firstLine(title)), idFm.stringWidth(idLine)) + 28));

		final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = img.createGraphics();
		try {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(new Color(0xF8, 0xFA, 0xFC));
			g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
			g2.setColor(new Color(0x3B, 0x82, 0xF6));
			g2.fillRect(0, 0, 4, h);
			g2.setColor(new Color(0xCB, 0xD5, 0xE1));
			g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

			g2.setFont(badgeFont);
			g2.setColor(new Color(0x64, 0x74, 0x8B));
			final String badge = "CITE · " + meta.source.toUpperCase();
			g2.drawString(badge, 12, 14);

			int y = 30;
			g2.setFont(titleFont);
			g2.setColor(new Color(0x0F, 0x17, 0x2A));
			for (final String line : title.split("\n")) {
				g2.drawString(line, 12, y);
				y += titleFm.getHeight() + 2;
			}
			if (authors.length() > 0) {
				g2.setFont(metaFont);
				g2.setColor(new Color(0x33, 0x41, 0x55));
				g2.drawString(authors, 12, y);
				y += metaFm.getHeight() + 2;
			}
			if (line3.length() > 0) {
				g2.setFont(metaFont);
				g2.setColor(new Color(0x64, 0x74, 0x8B));
				g2.drawString(ellipsize(line3, metaFm, w - 24), 12, y);
				y += metaFm.getHeight() + 2;
			}
			g2.setFont(idFont);
			g2.setColor(new Color(0x94, 0xA3, 0xB8));
			g2.drawString(idLine, 12, Math.min(h - 8, y + 4));
		}
		finally {
			g2.dispose();
		}
		return img;
	}

	private static String wrap(final String text, final FontMetrics fm, final int maxW, final int maxLines) {
		if (text == null || text.length() == 0) {
			return "";
		}
		final StringBuilder out = new StringBuilder();
		String rest = text;
		int lines = 0;
		while (rest.length() > 0 && lines < maxLines) {
			if (fm.stringWidth(rest) <= maxW) {
				if (out.length() > 0) {
					out.append('\n');
				}
				out.append(rest);
				break;
			}
			int cut = rest.length();
			while (cut > 1 && fm.stringWidth(rest.substring(0, cut)) > maxW) {
				cut--;
			}
			final int space = rest.lastIndexOf(' ', cut);
			if (space > 8) {
				cut = space;
			}
			if (out.length() > 0) {
				out.append('\n');
			}
			String piece = rest.substring(0, cut).trim();
			rest = rest.substring(cut).trim();
			lines++;
			if (lines == maxLines && rest.length() > 0) {
				piece = ellipsize(piece + "…", fm, maxW);
				out.append(piece);
				break;
			}
			out.append(piece);
		}
		return out.toString();
	}

	private static int countLines(final String s) {
		if (s == null || s.length() == 0) {
			return 1;
		}
		int n = 1;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '\n') {
				n++;
			}
		}
		return n;
	}

	private static String firstLine(final String s) {
		final int i = s.indexOf('\n');
		return i < 0 ? s : s.substring(0, i);
	}

	private static String ellipsize(final String s, final FontMetrics fm, final int maxW) {
		if (s == null) {
			return "";
		}
		if (fm.stringWidth(s) <= maxW) {
			return s;
		}
		String t = s;
		while (t.length() > 1 && fm.stringWidth(t + "…") > maxW) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "…";
	}

	private static String joinNonEmpty(final String sep, final String a, final String b) {
		final boolean ha = a != null && a.length() > 0;
		final boolean hb = b != null && b.length() > 0;
		if (ha && hb) {
			return a + sep + b;
		}
		if (ha) {
			return a;
		}
		if (hb) {
			return b;
		}
		return "";
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
