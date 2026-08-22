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
 * Scaled preview icon for rich node content (diagrams, tables, code, …).
 */
public final class RichPreviewIcon implements ZoomableRichIcon {

	public static final int MAX_WIDTH = 480;
	public static final int MAX_HEIGHT = 320;

	private final ImageIcon image;
	private final BufferedImage fullImage;
	private final String kindLabel;
	private final String placeholder;
	private final boolean error;
	private final int maxWidth;
	private final int maxHeight;
	private final int width;
	private final int height;

	public static RichPreviewIcon fromImage(final String kind, final BufferedImage img) {
		return new RichPreviewIcon(kind, img, null, false, MAX_WIDTH, MAX_HEIGHT);
	}

	public static RichPreviewIcon fromImage(final String kind, final BufferedImage img, final int maxWidth,
			final int maxHeight) {
		return new RichPreviewIcon(kind, img, null, false, maxWidth, maxHeight);
	}

	/** Image is already at the intended display resolution (no bitmap upscale). */
	public static RichPreviewIcon fromNativeImage(final String kind, final BufferedImage img) {
		if (img == null) {
			return placeholder(kind, kind + "…");
		}
		return new RichPreviewIcon(kind, img, null, false, img.getWidth(), img.getHeight(), false);
	}

	public static RichPreviewIcon placeholder(final String kind, final String label) {
		return new RichPreviewIcon(kind, null, label != null ? label : kind + "…", false, MAX_WIDTH, MAX_HEIGHT);
	}

	public static RichPreviewIcon error(final String kind, final String message) {
		final String msg = message != null && message.length() > 0 ? message : kind + " render failed";
		return new RichPreviewIcon(kind, null, truncate(msg, 120), true, MAX_WIDTH, MAX_HEIGHT);
	}

	private RichPreviewIcon(final String kind, final BufferedImage img, final String placeholder,
			final boolean error, final int maxWidth, final int maxHeight) {
		this(kind, img, placeholder, error, maxWidth, maxHeight, false);
	}

	/**
	 * Fast bitmap resize for in-progress zoom gestures (blurry but immediate).
	 * {@code ratio} is relative to the current icon size (1 = unchanged).
	 */
	public RichPreviewIcon withDisplayScaleRatio(final float ratio) {
		if (fullImage == null || Math.abs(ratio - 1f) < 0.01f) {
			return this;
		}
		final float r = ratio <= 0f ? 1f : ratio;
		final int targetW = Math.max(40, Math.round(width * r));
		final int targetH = Math.max(30, Math.round(height * r));
		if (targetW == width && targetH == height) {
			return this;
		}
		return new RichPreviewIcon(kindLabel, fullImage, null, false, targetW, targetH, true);
	}

	/** @deprecated Use {@link #withDisplayScaleRatio(float)} for interim zoom. */
	public RichPreviewIcon withDisplayScale(final float scale) {
		return withDisplayScaleRatio(scale);
	}

	private RichPreviewIcon(final String kind, final BufferedImage img, final String placeholder,
			final boolean error, final int maxWidth, final int maxHeight, final boolean allowUpscale) {
		this.kindLabel = kind != null ? kind : "preview";
		this.placeholder = placeholder;
		this.error = error;
		this.maxWidth = maxWidth > 0 ? maxWidth : MAX_WIDTH;
		this.maxHeight = maxHeight > 0 ? maxHeight : MAX_HEIGHT;
		this.fullImage = img;
		if (img != null) {
			final Image scaled = scaleToFit(img, this.maxWidth, this.maxHeight, allowUpscale);
			this.image = new ImageIcon(scaled);
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
	public String getKindLabel() {
		return kindLabel;
	}

	@Override
	public BufferedImage getFullImage() {
		return fullImage;
	}

	@Override
	public Icon withMaxWidth(final int newMaxWidth) {
		if (newMaxWidth <= 0 || newMaxWidth >= width) {
			return this;
		}
		if (fullImage != null) {
			final Image scaled = scaleToFit(fullImage, newMaxWidth, maxHeight, true);
			final BufferedImage buf = toBufferedImage(scaled);
			return new RichPreviewIcon(kindLabel, buf, null, false, newMaxWidth, maxHeight);
		}
		return new RichPreviewIcon(kindLabel, null, placeholder, error, newMaxWidth, maxHeight);
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
		return toBufferedImage(scaleToFit(src, maxW, maxH, false));
	}

	private static BufferedImage toBufferedImage(final Image src) {
		if (src instanceof BufferedImage) {
			return (BufferedImage) src;
		}
		final int w = Math.max(1, src.getWidth(null));
		final int h = Math.max(1, src.getHeight(null));
		final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = out.createGraphics();
		g2.drawImage(src, 0, 0, null);
		g2.dispose();
		return out;
	}

	private static Image scaleToFit(final Image src, final int maxW, final int maxH, final boolean allowUpscale) {
		final int w = src.getWidth(null);
		final int h = src.getHeight(null);
		if (w <= 0 || h <= 0) {
			return src;
		}
		double scale = Math.min(maxW / (double) w, maxH / (double) h);
		if (!allowUpscale) {
			scale = Math.min(1.0, scale);
		}
		if (Math.abs(scale - 1.0) < 0.001) {
			return src;
		}
		final int nw = Math.max(1, (int) Math.round(w * scale));
		final int nh = Math.max(1, (int) Math.round(h * scale));
		final BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = out.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
		        allowUpscale ? RenderingHints.VALUE_INTERPOLATION_BICUBIC
		                : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.drawImage(src, 0, 0, nw, nh, null);
		g2.dispose();
		return out;
	}

	private static Image scaleToMax(final Image src, final int maxW, final int maxH) {
		return scaleToFit(src, maxW, maxH, false);
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
