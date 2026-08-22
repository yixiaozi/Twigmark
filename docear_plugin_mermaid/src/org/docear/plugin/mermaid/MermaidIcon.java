package org.docear.plugin.mermaid;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.freeplane.core.ui.components.WidthConstrainedIcon;

/**
 * Renders a Mermaid diagram (or placeholder / error) as a node text icon.
 * Scales down when wider than {@code maxWidth} or taller than {@link #MAX_HEIGHT}.
 */
public final class MermaidIcon implements WidthConstrainedIcon {

	public static final int MAX_WIDTH = 640;
	public static final int MAX_HEIGHT = 480;

	private final ImageIcon image;
	private final String placeholder;
	private final boolean error;
	private final int maxWidth;
	private final int width;
	private final int height;

	public static MermaidIcon fromImage(final BufferedImage img) {
		final BufferedImage fitted = fit(img, MAX_WIDTH, MAX_HEIGHT);
		return new MermaidIcon(new ImageIcon(fitted), null, false, MAX_WIDTH);
	}

	public static MermaidIcon placeholder(final String label) {
		return new MermaidIcon(null, label != null ? label : "Mermaid…", false, MAX_WIDTH);
	}

	public static MermaidIcon error(final String message) {
		final String msg = message != null && message.length() > 0 ? message : "Mermaid render failed";
		return new MermaidIcon(null, truncate(msg, 120), true, MAX_WIDTH);
	}

	private MermaidIcon(final ImageIcon image, final String placeholder, final boolean error, final int maxWidth) {
		this.placeholder = placeholder;
		this.error = error;
		this.maxWidth = maxWidth > 0 ? maxWidth : MAX_WIDTH;
		if (image != null) {
			final Image scaled = scaleToMax(image.getImage(), this.maxWidth, MAX_HEIGHT);
			this.image = scaled == image.getImage() ? image : new ImageIcon(scaled);
			this.width = Math.max(1, this.image.getIconWidth());
			this.height = Math.max(1, this.image.getIconHeight());
		}
		else {
			this.image = null;
			final Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
			final FontMetrics fm = metrics(font);
			final String text = this.placeholder != null ? this.placeholder : "";
			this.width = Math.min(this.maxWidth, Math.max(80, fm.stringWidth(text) + 16));
			this.height = Math.max(24, fm.getHeight() + 10);
		}
	}

	@Override
	public Icon withMaxWidth(final int newMaxWidth) {
		if (newMaxWidth <= 0 || newMaxWidth >= width) {
			return this;
		}
		if (image != null) {
			final Image scaled = scaleToMax(image.getImage(), newMaxWidth, MAX_HEIGHT);
			return new MermaidIcon(new ImageIcon(scaled), null, false, newMaxWidth);
		}
		return new MermaidIcon(null, placeholder, error, newMaxWidth);
	}

	@Override
	public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
		if (image != null) {
			image.paintIcon(c, g, x, y);
			return;
		}
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(error ? new Color(0xFF, 0xEB, 0xEE) : new Color(0xF5, 0xF5, 0xF5));
			g2.fillRoundRect(x, y, width, height, 6, 6);
			g2.setColor(error ? new Color(0xB0, 0x00, 0x20) : new Color(0x55, 0x55, 0x55));
			g2.drawRoundRect(x, y, width - 1, height - 1, 6, 6);
			final Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
			g2.setFont(font);
			final FontMetrics fm = g2.getFontMetrics();
			g2.drawString(placeholder != null ? placeholder : "", x + 8, y + (height + fm.getAscent()) / 2 - 2);
		}
		finally {
			g2.dispose();
		}
	}

	@Override
	public int getIconWidth() {
		return width;
	}

	@Override
	public int getIconHeight() {
		return height;
	}

	private static BufferedImage fit(final BufferedImage src, final int maxW, final int maxH) {
		if (src == null) {
			return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		}
		if (src.getWidth() <= maxW && src.getHeight() <= maxH) {
			return src;
		}
		final Image scaled = scaleToMax(src, maxW, maxH);
		if (scaled instanceof BufferedImage) {
			return (BufferedImage) scaled;
		}
		final BufferedImage out = new BufferedImage(scaled.getWidth(null), scaled.getHeight(null),
				BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = out.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(scaled, 0, 0, null);
		g2.dispose();
		return out;
	}

	private static Image scaleToMax(final Image src, final int maxW, final int maxH) {
		final int w = src.getWidth(null);
		final int h = src.getHeight(null);
		if (w <= 0 || h <= 0) {
			return src;
		}
		final double scale = Math.min(1.0, Math.min(maxW / (double) w, maxH / (double) h));
		if (scale >= 0.999) {
			return src;
		}
		final int nw = Math.max(1, (int) Math.round(w * scale));
		final int nh = Math.max(1, (int) Math.round(h * scale));
		final BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = out.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(src, 0, 0, nw, nh, null);
		g2.dispose();
		return out;
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

	private static String truncate(final String s, final int max) {
		if (s == null) {
			return "";
		}
		final String oneLine = s.replace('\n', ' ').replace('\r', ' ').trim();
		if (oneLine.length() <= max) {
			return oneLine;
		}
		return oneLine.substring(0, max - 1) + "…";
	}
}
