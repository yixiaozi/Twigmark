package org.freeplane.view.swing.features.reports;

import org.freeplane.core.ui.theme.DocearUiTheme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

/**
 * Lightweight Swing chart (pie / bar / line) without external chart libraries.
 */
public final class ReportChartPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final Color[] PALETTE = new Color[] {
	        DocearUiTheme.ACCENT, new Color(0x0E, 0xA5, 0xE9), new Color(0x10, 0xB9, 0x81),
	        new Color(0xF5, 0x9E, 0x0B), new Color(0x8B, 0x5C, 0xF6), DocearUiTheme.ACCENT_DEEP,
	        new Color(0xE0, 0x70, 0x70), new Color(0x14, 0xB8, 0xA6), new Color(0x90, 0x70, 0xE0),
	        new Color(0x64, 0x74, 0x8B)
	};

	private ReportChartSeries series;

	public ReportChartPanel(final ReportChartSeries series) {
		this.series = series;
		setOpaque(true);
		setBackground(DocearUiTheme.SURFACE);
		setPreferredSize(new Dimension(520, 280));
		setMinimumSize(new Dimension(280, 200));
	}

	public void setSeries(final ReportChartSeries series) {
		this.series = series;
		repaint();
	}

	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			final int w = getWidth();
			final int h = getHeight();
			g2.setColor(getBackground());
			g2.fillRect(0, 0, w, h);
			if (series == null || series.isEmpty()) {
				g2.setColor(DocearUiTheme.TEXT_FAINT);
				g2.drawString("暂无图表数据", 16, 28);
				return;
			}
			g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
			g2.setColor(DocearUiTheme.TEXT);
			g2.drawString(series.title, 12, 20);
			if (series.type == ReportChartSeries.TYPE_PIE) {
				paintPie(g2, w, h);
			}
			else if (series.type == ReportChartSeries.TYPE_LINE) {
				paintLine(g2, w, h);
			}
			else {
				paintBar(g2, w, h);
			}
		}
		finally {
			g2.dispose();
		}
	}

	private void paintPie(final Graphics2D g2, final int w, final int h) {
		final int legendW = Math.min(220, Math.max(140, w / 3));
		final int size = Math.min(w - legendW - 24, h - 40);
		final int cx = 16 + size / 2;
		final int cy = 28 + (h - 28) / 2;
		final int r = Math.max(40, size / 2 - 8);
		final double total = Math.max(0.0001, series.sum());
		double start = 90;
		for (int i = 0; i < series.size(); i++) {
			final double v = series.valueAt(i);
			final double extent = -360.0 * (v / total);
			g2.setColor(PALETTE[i % PALETTE.length]);
			g2.fill(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, start, extent, Arc2D.PIE));
			start += extent;
		}
		g2.setColor(DocearUiTheme.HAIRLINE);
		g2.drawOval(cx - r, cy - r, r * 2, r * 2);

		final int lx = w - legendW + 8;
		int ly = 40;
		final FontMetrics fm = g2.getFontMetrics();
		g2.setFont(getFont().deriveFont(11f));
		for (int i = 0; i < series.size() && ly < h - 8; i++) {
			g2.setColor(PALETTE[i % PALETTE.length]);
			g2.fillRect(lx, ly - 8, 10, 10);
			g2.setColor(DocearUiTheme.TEXT);
			final int pct = (int) Math.round(series.valueAt(i) * 100.0 / total);
			final String text = trim(series.labelAt(i), 18) + "  " + formatValue(series.valueAt(i)) + " (" + pct + "%)";
			g2.drawString(text, lx + 16, ly);
			ly += fm.getHeight() + 4;
		}
	}

	private void paintBar(final Graphics2D g2, final int w, final int h) {
		final int left = 48;
		final int right = 12;
		final int top = 36;
		final int bottom = 48;
		final int plotW = Math.max(10, w - left - right);
		final int plotH = Math.max(10, h - top - bottom);
		final double max = Math.max(1.0, series.maxValue() * 1.1);
		g2.setColor(DocearUiTheme.GRID);
		g2.drawLine(left, top, left, top + plotH);
		g2.drawLine(left, top + plotH, left + plotW, top + plotH);

		final int n = series.size();
		final double slot = plotW / (double) Math.max(1, n);
		final int barW = Math.max(4, (int) (slot * 0.62));
		g2.setFont(getFont().deriveFont(10f));
		for (int i = 0; i < n; i++) {
			final double v = series.valueAt(i);
			final int barH = (int) Math.round(plotH * (v / max));
			final int x = left + (int) (i * slot + (slot - barW) / 2);
			final int y = top + plotH - barH;
			g2.setColor(PALETTE[i % PALETTE.length]);
			g2.fillRoundRect(x, y, barW, Math.max(1, barH), 4, 4);
			g2.setColor(DocearUiTheme.TEXT_MUTED);
			final String label = trim(series.labelAt(i), slot < 36 ? 4 : 8);
			final int tw = g2.getFontMetrics().stringWidth(label);
			g2.drawString(label, x + (barW - tw) / 2, top + plotH + 14);
		}
		g2.setColor(DocearUiTheme.TEXT_FAINT);
		g2.drawString(formatValue(max), 4, top + 10);
		g2.drawString("0", 8, top + plotH);
	}

	private void paintLine(final Graphics2D g2, final int w, final int h) {
		final int left = 48;
		final int right = 12;
		final int top = 36;
		final int bottom = 48;
		final int plotW = Math.max(10, w - left - right);
		final int plotH = Math.max(10, h - top - bottom);
		final double max = Math.max(1.0, series.maxValue() * 1.1);
		final int n = series.size();
		g2.setColor(DocearUiTheme.GRID);
		g2.drawLine(left, top, left, top + plotH);
		g2.drawLine(left, top + plotH, left + plotW, top + plotH);

		final List xs = new ArrayList();
		final List ys = new ArrayList();
		for (int i = 0; i < n; i++) {
			final double t = n <= 1 ? 0.5 : i / (double) (n - 1);
			final int x = left + (int) Math.round(plotW * t);
			final int y = top + plotH - (int) Math.round(plotH * (series.valueAt(i) / max));
			xs.add(Integer.valueOf(x));
			ys.add(Integer.valueOf(y));
		}
		g2.setStroke(new BasicStroke(2.2f));
		g2.setColor(PALETTE[0]);
		for (int i = 1; i < n; i++) {
			g2.drawLine(((Integer) xs.get(i - 1)).intValue(), ((Integer) ys.get(i - 1)).intValue(),
			        ((Integer) xs.get(i)).intValue(), ((Integer) ys.get(i)).intValue());
		}
		for (int i = 0; i < n; i++) {
			final int x = ((Integer) xs.get(i)).intValue();
			final int y = ((Integer) ys.get(i)).intValue();
			g2.fillOval(x - 3, y - 3, 6, 6);
		}
		g2.setStroke(new BasicStroke(1f));
		g2.setFont(getFont().deriveFont(10f));
		g2.setColor(DocearUiTheme.TEXT_MUTED);
		final int step = Math.max(1, n / 8);
		for (int i = 0; i < n; i += step) {
			final String label = trim(series.labelAt(i), 8);
			final int x = ((Integer) xs.get(i)).intValue();
			final int tw = g2.getFontMetrics().stringWidth(label);
			g2.drawString(label, x - tw / 2, top + plotH + 14);
		}
		g2.setColor(DocearUiTheme.TEXT_FAINT);
		g2.drawString(formatValue(max), 4, top + 10);
	}

	private static String trim(final String s, final int max) {
		if (s == null) {
			return "";
		}
		if (s.length() <= max) {
			return s;
		}
		return s.substring(0, Math.max(1, max - 1)) + "…";
	}

	private static String formatValue(final double v) {
		if (v >= 100 || Math.abs(v - Math.rint(v)) < 0.05) {
			return String.valueOf((long) Math.round(v));
		}
		return String.format("%.1f", Double.valueOf(v));
	}
}
