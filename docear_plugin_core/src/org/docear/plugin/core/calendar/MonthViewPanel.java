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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.JPanel;

/**
 * Month grid inspired by DocearReminder {@code System.Windows.Forms.Calendar.MonthView}.
 */
final class MonthViewPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final String[] WEEK_HEADERS = { "一", "二", "三", "四", "五", "六", "日" };

	interface DayHandler {
		void onDaySelected(Date dayStart);

		void onDayActivated(Date dayStart);
	}

	private final SimpleDateFormat dayNumFormat = new SimpleDateFormat("d", Locale.CHINA);
	private Date monthStart = firstOfMonth(new Date());
	private Date selectedDay = DayViewPanel.startOfDay(new Date());
	private List appointments = Collections.EMPTY_LIST;
	private DayHandler dayHandler;
	private int hoverIndex = -1;

	MonthViewPanel() {
		setOpaque(true);
		setBackground(CalendarTheme.CANVAS);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		final MouseAdapter mouse = new MouseAdapter() {
			public void mouseMoved(final MouseEvent e) {
				final int idx = indexAt(e.getX(), e.getY());
				if (idx != hoverIndex) {
					hoverIndex = idx;
					repaint();
				}
			}

			public void mouseExited(final MouseEvent e) {
				hoverIndex = -1;
				repaint();
			}

			public void mouseClicked(final MouseEvent e) {
				final Date day = dayAt(e.getX(), e.getY());
				if (day == null) {
					return;
				}
				selectedDay = day;
				repaint();
				if (dayHandler != null) {
					if (e.getClickCount() >= 2) {
						dayHandler.onDayActivated(day);
					}
					else {
						dayHandler.onDaySelected(day);
					}
				}
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	void setDayHandler(final DayHandler handler) {
		this.dayHandler = handler;
	}

	void setMonthStart(final Date date) {
		this.monthStart = firstOfMonth(date == null ? new Date() : date);
		revalidate();
		repaint();
	}

	Date getMonthStart() {
		return monthStart;
	}

	void setSelectedDay(final Date day) {
		this.selectedDay = DayViewPanel.startOfDay(day == null ? new Date() : day);
		repaint();
	}

	Date getSelectedDay() {
		return selectedDay;
	}

	void setAppointments(final List list) {
		appointments = list == null ? Collections.EMPTY_LIST : new ArrayList(list);
		repaint();
	}

	public Dimension getPreferredSize() {
		return new Dimension(720, 520);
	}

	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final int w = getWidth();
		final int h = getHeight();
		g2.setColor(CalendarTheme.CANVAS);
		g2.fillRect(0, 0, w, h);

		final int pad = 12;
		final int headerH = 28;
		final int gridTop = pad + headerH + 6;
		final int gridW = w - pad * 2;
		final int gridH = h - gridTop - pad;
		final int cellW = Math.max(40, gridW / 7);
		final int rows = 6;
		final int cellH = Math.max(48, gridH / rows);

		final Font headerFont = CalendarTheme.font(12f, Font.BOLD);
		g2.setFont(headerFont);
		final FontMetrics hfm = g2.getFontMetrics();
		for (int i = 0; i < 7; i++) {
			final int x = pad + i * cellW;
			g2.setColor(CalendarTheme.TEXT_MUTED);
			final String label = WEEK_HEADERS[i];
			g2.drawString(label, x + (cellW - hfm.stringWidth(label)) / 2, pad + 18);
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

		final Font dayFont = CalendarTheme.font(13f, Font.BOLD);
		final Font eventFont = CalendarTheme.font(10f);
		int index = 0;
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < 7; col++) {
				final int x = pad + col * cellW;
				final int y = gridTop + row * cellH;
				final boolean inMonth = cursor.get(Calendar.MONTH) == month.get(Calendar.MONTH);
				final boolean isToday = sameDay(cursor, today);
				final boolean isSelected = sameDay(cursor, sel);
				final boolean isHover = index == hoverIndex;
				final boolean weekend = col >= 5;

				g2.setColor(CalendarTheme.SURFACE);
				g2.fillRoundRect(x + 2, y + 2, cellW - 4, cellH - 4, 10, 10);
				if (weekend && inMonth) {
					g2.setColor(CalendarTheme.WEEKEND_WASH);
					g2.fillRoundRect(x + 2, y + 2, cellW - 4, cellH - 4, 10, 10);
				}
				if (isHover) {
					g2.setColor(CalendarTheme.ACCENT_WASH);
					g2.fillRoundRect(x + 2, y + 2, cellW - 4, cellH - 4, 10, 10);
				}
				if (isSelected) {
					g2.setColor(CalendarTheme.CHIP_BG);
					g2.fillRoundRect(x + 2, y + 2, cellW - 4, cellH - 4, 10, 10);
					g2.setColor(CalendarTheme.ACCENT);
					g2.drawRoundRect(x + 2, y + 2, cellW - 4, cellH - 4, 10, 10);
				}
				else {
					g2.setColor(CalendarTheme.HAIRLINE);
					g2.drawRoundRect(x + 2, y + 2, cellW - 4, cellH - 4, 10, 10);
				}

				g2.setFont(dayFont);
				final String num = dayNumFormat.format(cursor.getTime());
				final FontMetrics dfm = g2.getFontMetrics();
				final int nx = x + 10;
				final int ny = y + 8 + dfm.getAscent();
				if (isToday) {
					g2.setColor(CalendarTheme.TODAY_RING);
					g2.fillOval(nx - 4, y + 4, dfm.stringWidth(num) + 12, 22);
					g2.setColor(Color.WHITE);
				}
				else {
					g2.setColor(inMonth ? CalendarTheme.TEXT : CalendarTheme.TEXT_FAINT);
				}
				g2.drawString(num, nx, ny);

				final List dayEvents = eventsOn(cursor.getTime());
				g2.setFont(eventFont);
				final FontMetrics efm = g2.getFontMetrics();
				int ey = y + 32;
				final int maxLines = Math.max(1, (cellH - 38) / 16);
				for (int e = 0; e < dayEvents.size() && e < maxLines; e++) {
					final CalendarAppointment appt = (CalendarAppointment) dayEvents.get(e);
					final Color c = appt.color != null ? appt.color : CalendarTheme.eventColor(e);
					g2.setColor(c);
					g2.fillRoundRect(x + 8, ey, cellW - 16, 14, 6, 6);
					g2.setColor(Color.WHITE);
					g2.drawString(clip(appt.title, efm, cellW - 24), x + 12, ey + 11);
					ey += 16;
				}
				if (dayEvents.size() > maxLines) {
					g2.setColor(CalendarTheme.TEXT_MUTED);
					g2.drawString("+" + (dayEvents.size() - maxLines), x + 10, y + cellH - 8);
				}

				cursor.add(Calendar.DAY_OF_MONTH, 1);
				index++;
			}
		}
		g2.dispose();
	}

	private List eventsOn(final Date day) {
		final long start = DayViewPanel.startOfDay(day).getTime();
		final long end = start + 24L * 60L * 60L * 1000L;
		final ArrayList out = new ArrayList();
		for (int i = 0; i < appointments.size(); i++) {
			final CalendarAppointment appt = (CalendarAppointment) appointments.get(i);
			final long s = appt.startMillis();
			if (s >= start && s < end) {
				out.add(appt);
			}
		}
		return out;
	}

	private Date dayAt(final int px, final int py) {
		final int idx = indexAt(px, py);
		if (idx < 0) {
			return null;
		}
		final Calendar cursor = Calendar.getInstance();
		cursor.setTime(monthStart);
		cursor.setFirstDayOfWeek(Calendar.MONDAY);
		int lead = cursor.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY;
		if (lead < 0) {
			lead += 7;
		}
		cursor.add(Calendar.DAY_OF_MONTH, -lead + idx);
		return DayViewPanel.startOfDay(cursor.getTime());
	}

	private int indexAt(final int px, final int py) {
		final int pad = 12;
		final int headerH = 28;
		final int gridTop = pad + headerH + 6;
		final int gridW = getWidth() - pad * 2;
		final int gridH = getHeight() - gridTop - pad;
		final int cellW = Math.max(40, gridW / 7);
		final int cellH = Math.max(48, gridH / 6);
		if (px < pad || py < gridTop) {
			return -1;
		}
		final int col = (px - pad) / cellW;
		final int row = (py - gridTop) / cellH;
		if (col < 0 || col > 6 || row < 0 || row > 5) {
			return -1;
		}
		return row * 7 + col;
	}

	private static boolean sameDay(final Calendar a, final Calendar b) {
		return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
	}

	static Date firstOfMonth(final Date date) {
		final Calendar cal = Calendar.getInstance();
		cal.setTime(DayViewPanel.startOfDay(date));
		cal.set(Calendar.DAY_OF_MONTH, 1);
		return cal.getTime();
	}

	private static String clip(final String text, final FontMetrics fm, final int maxWidth) {
		if (text == null) {
			return "";
		}
		if (fm.stringWidth(text) <= maxWidth) {
			return text;
		}
		for (int i = text.length() - 1; i > 0; i--) {
			final String sub = text.substring(0, i) + "…";
			if (fm.stringWidth(sub) <= maxWidth) {
				return sub;
			}
		}
		return "…";
	}
}
