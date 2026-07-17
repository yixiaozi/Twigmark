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
import java.util.Date;
import java.util.Locale;

import javax.swing.JPanel;

/** Compact month navigator (MonthView side panel). */
final class MiniMonthPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final String[] HEADERS = { "一", "二", "三", "四", "五", "六", "日" };

	interface Listener {
		void onDayChosen(Date day);
	}

	private final SimpleDateFormat titleFormat = new SimpleDateFormat("yyyy年M月", Locale.CHINA);
	private Date monthStart = MonthViewPanel.firstOfMonth(new Date());
	private Date selectedDay = DayViewPanel.startOfDay(new Date());
	private Listener listener;

	MiniMonthPanel() {
		setOpaque(true);
		setBackground(CalendarTheme.SURFACE);
		setPreferredSize(new Dimension(220, 220));
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

	void setSelectedDay(final Date day) {
		selectedDay = DayViewPanel.startOfDay(day);
		monthStart = MonthViewPanel.firstOfMonth(selectedDay);
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
		final int h = getHeight();
		g2.setColor(CalendarTheme.SURFACE);
		g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
		g2.setColor(CalendarTheme.HAIRLINE);
		g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);

		g2.setFont(CalendarTheme.font(13f, Font.BOLD));
		g2.setColor(CalendarTheme.TEXT);
		final String title = titleFormat.format(monthStart);
		g2.drawString(title, 12, 22);

		final int top = 36;
		final int cellW = (w - 16) / 7;
		final int cellH = Math.max(18, (h - top - 10) / 7);
		g2.setFont(CalendarTheme.font(10f));
		for (int i = 0; i < 7; i++) {
			g2.setColor(CalendarTheme.TEXT_FAINT);
			g2.drawString(HEADERS[i], 8 + i * cellW + 4, top);
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
				final int x = 8 + col * cellW;
				final int y = top + 8 + row * cellH;
				final boolean inMonth = cursor.get(Calendar.MONTH) == month.get(Calendar.MONTH);
				final boolean isToday = sameDay(cursor, today);
				final boolean isSel = sameDay(cursor, sel);
				final String num = String.valueOf(cursor.get(Calendar.DAY_OF_MONTH));
				if (isSel) {
					g2.setColor(CalendarTheme.ACCENT);
					g2.fillOval(x, y - 2, cellW - 4, cellH - 2);
					g2.setColor(Color.WHITE);
				}
				else if (isToday) {
					g2.setColor(CalendarTheme.CHIP_BG);
					g2.fillOval(x, y - 2, cellW - 4, cellH - 2);
					g2.setColor(CalendarTheme.ACCENT_DEEP);
				}
				else {
					g2.setColor(inMonth ? CalendarTheme.TEXT : CalendarTheme.TEXT_FAINT);
				}
				g2.drawString(num, x + (cellW - 4 - fm.stringWidth(num)) / 2, y + fm.getAscent());
				cursor.add(Calendar.DAY_OF_MONTH, 1);
			}
		}
		g2.dispose();
	}

	private Date dayAt(final int px, final int py) {
		final int top = 44;
		final int cellW = (getWidth() - 16) / 7;
		final int cellH = Math.max(18, (getHeight() - top - 10) / 6);
		if (px < 8 || py < top) {
			return null;
		}
		final int col = (px - 8) / cellW;
		final int row = (py - top) / cellH;
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
