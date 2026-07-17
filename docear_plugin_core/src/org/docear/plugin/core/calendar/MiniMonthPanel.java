package org.docear.plugin.core.calendar;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.swing.JPanel;

/** Compact month navigator; dots under dates mark task density. */
final class MiniMonthPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final String[] HEADERS = { "一", "二", "三", "四", "五", "六", "日" };
	private static final int CELL_H = 24;
	private static final int GRID_TOP = 30;
	private static final int PANEL_H = GRID_TOP + 6 * CELL_H + 6;

	interface Listener {
		void onDayChosen(Date day);
	}

	private final SimpleDateFormat titleFormat = new SimpleDateFormat("yyyy年M月", Locale.CHINA);
	private Date monthStart = MonthViewPanel.firstOfMonth(new Date());
	private Date selectedDay = DayViewPanel.startOfDay(new Date());
	private Map dayCounts = Collections.EMPTY_MAP;
	private Listener listener;

	MiniMonthPanel() {
		setOpaque(true);
		setBackground(CalendarTheme.SURFACE);
		setPreferredSize(new Dimension(214, PANEL_H));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, PANEL_H));
		setAlignmentX(LEFT_ALIGNMENT);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				final Date day = dayAt(e.getX(), e.getY());
				if (day != null) {
					selectedDay = day;
					monthStart = MonthViewPanel.firstOfMonth(day);
					repaint();
					if (listener != null) {
						listener.onDayChosen(day);
					}
				}
			}
		});
	}

	void setListener(final Listener listener) {
		this.listener = listener;
	}

	Date getSelectedDay() {
		return selectedDay;
	}

	Date getMonthStart() {
		return monthStart;
	}

	void setSelectedDay(final Date day) {
		selectedDay = DayViewPanel.startOfDay(day);
		monthStart = MonthViewPanel.firstOfMonth(selectedDay);
		repaint();
	}

	void setDayCounts(final Map counts) {
		if (counts == null || counts.isEmpty()) {
			dayCounts = Collections.EMPTY_MAP;
		}
		else {
			dayCounts = new HashMap(counts);
		}
		repaint();
	}

	void shiftMonth(final int delta) {
		final Calendar cal = Calendar.getInstance();
		cal.setTime(monthStart);
		cal.add(Calendar.MONTH, delta);
		monthStart = MonthViewPanel.firstOfMonth(cal.getTime());
		repaint();
	}

	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final int w = getWidth();
		final int h = Math.min(getHeight(), PANEL_H);
		g2.setColor(CalendarTheme.SURFACE);
		g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
		g2.setColor(CalendarTheme.HAIRLINE);
		g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

		g2.setFont(CalendarTheme.font(12f, Font.BOLD));
		g2.setColor(CalendarTheme.TEXT);
		g2.drawString(titleFormat.format(monthStart), 10, 18);

		final int cellW = Math.max(24, (w - 12) / 7);
		g2.setFont(CalendarTheme.font(10f));
		for (int i = 0; i < 7; i++) {
			g2.setColor(CalendarTheme.TEXT_FAINT);
			final FontMetrics hfm = g2.getFontMetrics();
			final String label = HEADERS[i];
			g2.drawString(label, 6 + i * cellW + (cellW - hfm.stringWidth(label)) / 2, GRID_TOP - 8);
		}

		final Calendar cursor = Calendar.getInstance();
		cursor.setTime(monthStart);
		cursor.setFirstDayOfWeek(Calendar.MONDAY);
		int lead = cursor.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY;
		if (lead < 0) {
			lead += 7;
		}
		cursor.add(Calendar.DAY_OF_MONTH, -lead);
		final Calendar today = Calendar.getInstance();
		today.setTime(DayViewPanel.startOfDay(new Date()));
		final Calendar sel = Calendar.getInstance();
		sel.setTime(selectedDay);
		final Calendar month = Calendar.getInstance();
		month.setTime(monthStart);

		g2.setFont(CalendarTheme.font(11f));
		final FontMetrics fm = g2.getFontMetrics();
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 7; col++) {
				final int x = 6 + col * cellW;
				final int y = GRID_TOP + row * CELL_H;
				final boolean inMonth = cursor.get(Calendar.MONTH) == month.get(Calendar.MONTH);
				final boolean isToday = sameDay(cursor, today);
				final boolean isSel = sameDay(cursor, sel);
				final String num = String.valueOf(cursor.get(Calendar.DAY_OF_MONTH));
				final int count = countForDay(cursor.getTime());
				final int cx = x + cellW / 2;
				final int cy = y + 10;
				if (isSel) {
					g2.setColor(CalendarTheme.ACCENT);
					g2.fillOval(cx - 9, cy - 9, 18, 18);
					g2.setColor(Color.WHITE);
				}
				else if (isToday) {
					g2.setColor(CalendarTheme.ACCENT);
					g2.drawOval(cx - 9, cy - 9, 18, 18);
					g2.setColor(CalendarTheme.ACCENT_DEEP);
				}
				else {
					g2.setColor(inMonth ? CalendarTheme.TEXT : CalendarTheme.TEXT_FAINT);
				}
				g2.drawString(num, cx - fm.stringWidth(num) / 2, cy + fm.getAscent() / 2 - 1);
				if (count > 0 && inMonth) {
					paintDots(g2, cx, y + CELL_H - 5, count, isSel);
				}
				cursor.add(Calendar.DAY_OF_MONTH, 1);
			}
		}
		g2.dispose();
	}

	private void paintDots(final Graphics2D g2, final int cx, final int y, final int count, final boolean onSel) {
		final int n = Math.min(3, count);
		final int gap = 5;
		final int start = cx - (n - 1) * gap / 2;
		g2.setColor(onSel ? new Color(255, 255, 255, 220) : CalendarTheme.ACCENT);
		for (int i = 0; i < n; i++) {
			final int d = count >= 3 && i == 1 ? 4 : 3;
			g2.fillOval(start + i * gap - d / 2, y - d / 2, d, d);
		}
	}

	private int countForDay(final Date day) {
		if (dayCounts.isEmpty()) {
			return 0;
		}
		final Integer n = (Integer) dayCounts.get(Long.valueOf(DayViewPanel.startOfDay(day).getTime()));
		return n == null ? 0 : n.intValue();
	}

	private Date dayAt(final int px, final int py) {
		final int cellW = Math.max(24, (getWidth() - 12) / 7);
		if (px < 6 || py < GRID_TOP) {
			return null;
		}
		final int col = (px - 6) / cellW;
		final int row = (py - GRID_TOP) / CELL_H;
		if (col < 0 || col > 6 || row < 0 || row > 5) {
			return null;
		}
		final Calendar cursor = Calendar.getInstance();
		cursor.setTime(monthStart);
		cursor.setFirstDayOfWeek(Calendar.MONDAY);
		int lead = cursor.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY;
		if (lead < 0) {
			lead += 7;
		}
		cursor.add(Calendar.DAY_OF_MONTH, -lead + row * 7 + col);
		return DayViewPanel.startOfDay(cursor.getTime());
	}

	private static boolean sameDay(final Calendar a, final Calendar b) {
		return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
	}
}
