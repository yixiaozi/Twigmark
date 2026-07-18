package org.docear.plugin.core.calendar;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import org.freeplane.core.ui.theme.DocearUiTheme;

/**
 * Calendar tokens — thin alias of {@link DocearUiTheme} plus calendar-only accents.
 */
final class CalendarTheme {
	static final Color CANVAS = DocearUiTheme.CANVAS;
	static final Color SURFACE = DocearUiTheme.SURFACE;
	static final Color SURFACE_SOFT = DocearUiTheme.SURFACE_SOFT;
	static final Color TEXT = DocearUiTheme.TEXT;
	static final Color TEXT_MUTED = DocearUiTheme.TEXT_MUTED;
	static final Color TEXT_FAINT = DocearUiTheme.TEXT_FAINT;
	static final Color ACCENT = DocearUiTheme.ACCENT;
	static final Color ACCENT_DEEP = DocearUiTheme.ACCENT_DEEP;
	static final Color ACCENT_WASH = DocearUiTheme.ACCENT_WASH;
	static final Color CHIP_BG = DocearUiTheme.ACCENT_WASH;
	static final Color HAIRLINE = DocearUiTheme.HAIRLINE;
	static final Color GRID = DocearUiTheme.GRID;
	static final Color GRID_STRONG = new Color(0xCB, 0xD5, 0xE1);
	static final Color TODAY_RING = DocearUiTheme.ACCENT;
	static final Color NOW = new Color(0xE1, 0x1D, 0x48);
	static final Color WEEKEND_WASH = new Color(0xF8, 0xFA, 0xFC);
	static final Color SELECTION = DocearUiTheme.SELECTION;
	static final Color EVENT_A = new Color(0x0E, 0xA5, 0xE9);
	static final Color EVENT_B = new Color(0x8B, 0x5C, 0xF6);
	static final Color EVENT_C = new Color(0xF5, 0x9E, 0x0B);
	static final Color EVENT_D = new Color(0x10, 0xB9, 0x81);
	static final Color HEADER_TOP = DocearUiTheme.HEADER_TOP;
	static final Color HEADER_BOTTOM = DocearUiTheme.HEADER_BOTTOM;

	private CalendarTheme() {
	}

	static Font font(final float size) {
		return DocearUiTheme.font(size);
	}

	static Font font(final float size, final int style) {
		return DocearUiTheme.font(size, style);
	}

	static void paintHeaderBand(final Graphics2D g2, final Rectangle bounds) {
		DocearUiTheme.paintHeaderBand(g2, bounds);
	}

	static Color eventColor(final int index) {
		final Color[] palette = new Color[] { EVENT_A, EVENT_D, EVENT_B, EVENT_C, ACCENT_DEEP };
		return palette[Math.abs(index) % palette.length];
	}
}
