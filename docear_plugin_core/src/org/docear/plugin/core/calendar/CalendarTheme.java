package org.docear.plugin.core.calendar;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Shared look for the scheduling-hub calendar (month / week / day).
 */
final class CalendarTheme {
	static final Color CANVAS = new Color(0xF4, 0xF7, 0xF8);
	static final Color SURFACE = new Color(0xFF, 0xFF, 0xFF);
	static final Color SURFACE_SOFT = new Color(0xF0, 0xF5, 0xF4);
	static final Color TEXT = new Color(0x0F, 0x17, 0x2A);
	static final Color TEXT_MUTED = new Color(0x64, 0x74, 0x8B);
	static final Color TEXT_FAINT = new Color(0x94, 0xA3, 0xB8);
	static final Color ACCENT = new Color(0x0D, 0x94, 0x88);
	static final Color ACCENT_DEEP = new Color(0x0F, 0x76, 0x6E);
	static final Color ACCENT_WASH = new Color(0xD1, 0xFA, 0xE5);
	static final Color CHIP_BG = new Color(0xCC, 0xFB, 0xF1);
	static final Color HAIRLINE = new Color(0xE2, 0xE8, 0xF0);
	static final Color GRID = new Color(0xE8, 0xEE, 0xF2);
	static final Color GRID_STRONG = new Color(0xCB, 0xD5, 0xE1);
	static final Color TODAY_RING = new Color(0x0D, 0x94, 0x88);
	static final Color NOW = new Color(0xE1, 0x1D, 0x48);
	static final Color WEEKEND_WASH = new Color(0xF8, 0xFA, 0xFC);
	static final Color SELECTION = new Color(0x99, 0xF6, 0xE4);
	static final Color EVENT_A = new Color(0x0E, 0xA5, 0xE9);
	static final Color EVENT_B = new Color(0x8B, 0x5C, 0xF6);
	static final Color EVENT_C = new Color(0xF5, 0x9E, 0x0B);
	static final Color EVENT_D = new Color(0x10, 0xB9, 0x81);
	static final Color HEADER_TOP = new Color(0x0F, 0x76, 0x6E);
	static final Color HEADER_BOTTOM = new Color(0x14, 0xB8, 0xA6);

	private CalendarTheme() {
	}

	static Font font(final float size) {
		return font(size, Font.PLAIN);
	}

	static Font font(final float size, final int style) {
		final String[] prefer = new String[] { "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
		        "Noto Sans CJK SC", "SansSerif" };
		for (int i = 0; i < prefer.length; i++) {
			final Font font = new Font(prefer[i], style, Math.round(size));
			if (font.canDisplay('历')) {
				return font.deriveFont(style, size);
			}
		}
		return new Font("SansSerif", style, Math.round(size));
	}

	static void paintHeaderBand(final Graphics2D g2, final Rectangle bounds) {
		final GradientPaint paint = new GradientPaint(bounds.x, bounds.y, HEADER_TOP, bounds.x, bounds.y + bounds.height,
		        HEADER_BOTTOM);
		g2.setPaint(paint);
		g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
	}

	static Color eventColor(final int index) {
		final Color[] palette = new Color[] { EVENT_A, EVENT_D, EVENT_B, EVENT_C, ACCENT_DEEP };
		return palette[Math.abs(index) % palette.length];
	}

}
