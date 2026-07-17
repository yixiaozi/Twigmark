package org.docear.plugin.core.calendar;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.JPanel;
import javax.swing.Scrollable;

/**
 * Timed multi-day grid inspired by DocearReminder {@code DayView}:
 * half-hour rows, minute-precise time mapping, appointment blocks.
 */
final class DayViewPanel extends JPanel implements Scrollable {
	private static final long serialVersionUID = 1L;

	private static final Color BG = new Color(0xFA, 0xFB, 0xFC);
	private static final Color GRID = new Color(0xE5, 0xE7, 0xEB);
	private static final Color GRID_HOUR = new Color(0xD1, 0xD5, 0xDB);
	private static final Color HEADER_BG = new Color(0xF3, 0xF4, 0xF6);
	private static final Color HEADER_TEXT = new Color(0x37, 0x41, 0x51);
	private static final Color HOUR_TEXT = new Color(0x6B, 0x72, 0x80);
	private static final Color WORK_TINT = new Color(0xFF, 0xFF, 0xFF);
	private static final Color OFF_TINT = new Color(0xF9, 0xFA, 0xFB);
	private static final Color TODAY_HEADER = new Color(0x0F, 0x76, 0x6E);
	private static final Color NOW_LINE = new Color(0xDC, 0x26, 0x26);
	private static final Color SELECTION = new Color(0xCC, 0xF2, 0xE9);

	private static final int HOUR_LABEL_WIDTH = 52;
	private static final int DAY_HEADER_HEIGHT = 28;
	static final int HALF_HOUR_HEIGHT = 18;
	private static final int START_HOUR = 0;
	private static final int END_HOUR = 24;

	interface SelectionListener {
		void onTimeSelected(Date start, Date end);
	}

	private final SimpleDateFormat dayHeaderFormat = new SimpleDateFormat("M/d EEE", Locale.CHINA);
	private final SimpleDateFormat hourFormat = new SimpleDateFormat("H:mm", Locale.CHINA);

	private Date startDate = startOfDay(new Date());
	private int daysToShow = 7;
	private List appointments = Collections.EMPTY_LIST;
	private Date selectionStart;
	private Date selectionEnd;
	private SelectionListener selectionListener;

	DayViewPanel() {
		setBackground(BG);
		setOpaque(true);
		setFocusable(true);
		final MouseAdapter mouse = new MouseAdapter() {
			private Date dragStart;

			public void mousePressed(final MouseEvent e) {
				requestFocusInWindow();
				dragStart = getTimeAt(e.getX(), e.getY());
				selectionStart = dragStart;
				selectionEnd = dragStart == null ? null : new Date(dragStart.getTime() + 30L * 60L * 1000L);
				repaint();
			}

			public void mouseDragged(final MouseEvent e) {
				if (dragStart == null) {
					return;
				}
				final Date at = getTimeAt(e.getX(), e.getY());
				if (at == null) {
					return;
				}
				if (at.before(dragStart)) {
					selectionStart = at;
					selectionEnd = dragStart;
				}
				else {
					selectionStart = dragStart;
					selectionEnd = at;
				}
				// Snap end to at least 1 minute after start.
				if (selectionEnd.getTime() - selectionStart.getTime() < 60L * 1000L) {
					selectionEnd = new Date(selectionStart.getTime() + 60L * 1000L);
				}
				repaint();
			}

			public void mouseReleased(final MouseEvent e) {
				if (selectionListener != null && selectionStart != null && selectionEnd != null) {
					selectionListener.onTimeSelected(selectionStart, selectionEnd);
				}
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
		addMouseWheelListener(new MouseWheelListener() {
			public void mouseWheelMoved(final MouseWheelEvent e) {
				// Let parent JScrollPane handle wheel.
				getParent().dispatchEvent(e);
			}
		});
	}

	void setSelectionListener(final SelectionListener listener) {
		this.selectionListener = listener;
	}

	void setStartDate(final Date date) {
		this.startDate = startOfDay(date == null ? new Date() : date);
		revalidate();
		repaint();
	}

	Date getStartDate() {
		return startDate;
	}

	void setDaysToShow(final int days) {
		this.daysToShow = Math.max(1, Math.min(14, days));
		revalidate();
		repaint();
	}

	int getDaysToShow() {
		return daysToShow;
	}

	void setAppointments(final List list) {
		if (list == null || list.isEmpty()) {
			appointments = Collections.EMPTY_LIST;
		}
		else {
			final ArrayList copy = new ArrayList(list);
			Collections.sort(copy, new Comparator() {
				public int compare(final Object a, final Object b) {
					final long sa = ((CalendarAppointment) a).startMillis();
					final long sb = ((CalendarAppointment) b).startMillis();
					return sa < sb ? -1 : (sa > sb ? 1 : 0);
				}
			});
			appointments = copy;
		}
		repaint();
	}

	/**
	 * Maps pixel position to a Date with <b>1-minute</b> precision (DocearReminder DayView style).
	 */
	Date getTimeAt(final int x, final int y) {
		if (y < DAY_HEADER_HEIGHT || x < HOUR_LABEL_WIDTH) {
			return null;
		}
		final int bodyWidth = Math.max(1, getWidth() - HOUR_LABEL_WIDTH);
		final int dayWidth = Math.max(1, bodyWidth / daysToShow);
		final int dayIndex = Math.min(daysToShow - 1, (x - HOUR_LABEL_WIDTH) / dayWidth);
		final int minutesFromTop = Math.max(0, (y - DAY_HEADER_HEIGHT) * 30 / HALF_HOUR_HEIGHT);
		final int totalMinutes = START_HOUR * 60 + minutesFromTop;
		final Calendar cal = Calendar.getInstance();
		cal.setTime(startDate);
		cal.add(Calendar.DAY_OF_MONTH, dayIndex);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.add(Calendar.MINUTE, Math.min(END_HOUR * 60 - 1, totalMinutes));
		return cal.getTime();
	}

	private int yForTime(final Date time) {
		if (time == null) {
			return DAY_HEADER_HEIGHT;
		}
		final Calendar cal = Calendar.getInstance();
		cal.setTime(time);
		final int minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
		return DAY_HEADER_HEIGHT + (minutes - START_HOUR * 60) * HALF_HOUR_HEIGHT / 30;
	}

	private int dayIndexFor(final Date time) {
		if (time == null) {
			return -1;
		}
		final long dayMs = 24L * 60L * 60L * 1000L;
		final long start = startOfDay(startDate).getTime();
		final long t = startOfDay(time).getTime();
		final int idx = (int) ((t - start) / dayMs);
		if (idx < 0 || idx >= daysToShow) {
			return -1;
		}
		return idx;
	}

	public Dimension getPreferredSize() {
		final int hours = END_HOUR - START_HOUR;
		final int height = DAY_HEADER_HEIGHT + hours * 2 * HALF_HOUR_HEIGHT + 8;
		return new Dimension(800, height);
	}

	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final int width = getWidth();
		final int height = getHeight();
		g2.setColor(BG);
		g2.fillRect(0, 0, width, height);

		final int bodyWidth = Math.max(1, width - HOUR_LABEL_WIDTH);
		final int dayWidth = Math.max(1, bodyWidth / daysToShow);
		final Font headerFont = preferFont(12f);
		final Font hourFont = preferFont(11f);
		final FontMetrics headerFm = g2.getFontMetrics(headerFont);
		final FontMetrics hourFm = g2.getFontMetrics(hourFont);

		// Day columns background + headers.
		final Calendar todayStart = Calendar.getInstance();
		todayStart.setTime(startOfDay(new Date()));
		for (int d = 0; d < daysToShow; d++) {
			final int x = HOUR_LABEL_WIDTH + d * dayWidth;
			final Calendar day = Calendar.getInstance();
			day.setTime(startDate);
			day.add(Calendar.DAY_OF_MONTH, d);
			final boolean isToday = sameDay(day, todayStart);
			g2.setColor(isToday ? new Color(0xEC, 0xFE, 0xFF) : (isWorkDay(day) ? WORK_TINT : OFF_TINT));
			g2.fillRect(x, DAY_HEADER_HEIGHT, dayWidth, height - DAY_HEADER_HEIGHT);

			g2.setColor(HEADER_BG);
			g2.fillRect(x, 0, dayWidth, DAY_HEADER_HEIGHT);
			g2.setColor(GRID);
			g2.drawLine(x, 0, x, height);
			g2.setFont(headerFont);
			g2.setColor(isToday ? TODAY_HEADER : HEADER_TEXT);
			final String label = dayHeaderFormat.format(day.getTime());
			final int lw = headerFm.stringWidth(label);
			g2.drawString(label, x + Math.max(4, (dayWidth - lw) / 2), 18);
		}

		// Hour labels + horizontal grid.
		g2.setFont(hourFont);
		for (int hour = START_HOUR; hour <= END_HOUR; hour++) {
			final int y = DAY_HEADER_HEIGHT + (hour - START_HOUR) * 2 * HALF_HOUR_HEIGHT;
			g2.setColor(GRID_HOUR);
			g2.drawLine(HOUR_LABEL_WIDTH, y, width, y);
			if (hour < END_HOUR) {
				final int mid = y + HALF_HOUR_HEIGHT;
				g2.setColor(GRID);
				g2.drawLine(HOUR_LABEL_WIDTH, mid, width, mid);
			}
			if (hour < END_HOUR) {
				g2.setColor(HOUR_TEXT);
				final String h = hour + ":00";
				g2.drawString(h, HOUR_LABEL_WIDTH - hourFm.stringWidth(h) - 6, y + 12);
			}
		}

		// Selection range.
		paintTimeRange(g2, selectionStart, selectionEnd, SELECTION, dayWidth, true);

		// Appointments.
		for (int i = 0; i < appointments.size(); i++) {
			final CalendarAppointment appt = (CalendarAppointment) appointments.get(i);
			paintAppointment(g2, appt, dayWidth);
		}

		// Now line.
		final Date now = new Date();
		final int nowDay = dayIndexFor(now);
		if (nowDay >= 0) {
			final int y = yForTime(now);
			final int x = HOUR_LABEL_WIDTH + nowDay * dayWidth;
			g2.setColor(NOW_LINE);
			g2.fillRect(x, y - 1, dayWidth, 2);
			g2.fillOval(x - 3, y - 3, 6, 6);
		}

		// Left gutter.
		g2.setColor(HEADER_BG);
		g2.fillRect(0, 0, HOUR_LABEL_WIDTH, DAY_HEADER_HEIGHT);
		g2.setColor(GRID);
		g2.drawLine(HOUR_LABEL_WIDTH - 1, 0, HOUR_LABEL_WIDTH - 1, height);
		g2.drawLine(0, DAY_HEADER_HEIGHT, width, DAY_HEADER_HEIGHT);

		g2.dispose();
	}

	private void paintAppointment(final Graphics2D g2, final CalendarAppointment appt, final int dayWidth) {
		if (appt == null || appt.start == null) {
			return;
		}
		final int day = dayIndexFor(appt.start);
		if (day < 0) {
			return;
		}
		final int x = HOUR_LABEL_WIDTH + day * dayWidth + 2;
		final int y1 = yForTime(appt.start);
		Date end = appt.end;
		if (end == null || !end.after(appt.start)) {
			end = new Date(appt.start.getTime() + 30L * 60L * 1000L);
		}
		int y2 = yForTime(end);
		if (y2 <= y1) {
			y2 = y1 + Math.max(HALF_HOUR_HEIGHT / 2, 8);
		}
		final int w = dayWidth - 4;
		final int h = Math.max(10, y2 - y1);
		g2.setColor(appt.color);
		g2.fillRoundRect(x, y1, w, h, 6, 6);
		g2.setColor(new Color(0, 0, 0, 40));
		g2.drawRoundRect(x, y1, w, h, 6, 6);
		g2.setColor(Color.WHITE);
		g2.setFont(preferFont(11f));
		final FontMetrics fm = g2.getFontMetrics();
		final String time = hourFormat.format(appt.start);
		final String title = appt.title;
		final String line1 = time + " " + title;
		g2.drawString(clip(line1, fm, w - 8), x + 4, y1 + Math.min(h - 4, fm.getAscent() + 2));
	}

	private void paintTimeRange(final Graphics2D g2, final Date start, final Date end, final Color color,
	        final int dayWidth, final boolean fill) {
		if (start == null || end == null) {
			return;
		}
		final int day = dayIndexFor(start);
		if (day < 0) {
			return;
		}
		final int x = HOUR_LABEL_WIDTH + day * dayWidth + 1;
		final int y1 = yForTime(start);
		final int y2 = Math.max(y1 + 4, yForTime(end));
		g2.setColor(color);
		if (fill) {
			g2.fillRect(x, y1, dayWidth - 2, y2 - y1);
		}
		else {
			g2.drawRect(x, y1, dayWidth - 2, y2 - y1);
		}
	}

	private static String clip(final String text, final FontMetrics fm, final int maxWidth) {
		if (fm.stringWidth(text) <= maxWidth) {
			return text;
		}
		final String ellipsis = "…";
		for (int i = text.length() - 1; i > 0; i--) {
			final String sub = text.substring(0, i) + ellipsis;
			if (fm.stringWidth(sub) <= maxWidth) {
				return sub;
			}
		}
		return ellipsis;
	}

	private static boolean isWorkDay(final Calendar day) {
		final int dow = day.get(Calendar.DAY_OF_WEEK);
		return dow != Calendar.SATURDAY && dow != Calendar.SUNDAY;
	}

	private static boolean sameDay(final Calendar a, final Calendar b) {
		return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
	}

	static Date startOfDay(final Date date) {
		final Calendar cal = Calendar.getInstance();
		cal.setTime(date == null ? new Date() : date);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTime();
	}

	static Date startOfWeekMonday(final Date date) {
		final Calendar cal = Calendar.getInstance();
		cal.setTime(startOfDay(date));
		cal.setFirstDayOfWeek(Calendar.MONDAY);
		cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
		return cal.getTime();
	}

	private static Font preferFont(final float size) {
		final String[] prefer = new String[] { "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
		        "Noto Sans CJK SC", "SansSerif" };
		for (int i = 0; i < prefer.length; i++) {
			final Font font = new Font(prefer[i], Font.PLAIN, Math.round(size));
			if (font.canDisplay('周')) {
				return font.deriveFont(size);
			}
		}
		return new Font("SansSerif", Font.PLAIN, Math.round(size));
	}

	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}

	public int getScrollableUnitIncrement(final java.awt.Rectangle visibleRect, final int orientation,
	        final int direction) {
		return HALF_HOUR_HEIGHT;
	}

	public int getScrollableBlockIncrement(final java.awt.Rectangle visibleRect, final int orientation,
	        final int direction) {
		return visibleRect.height - HALF_HOUR_HEIGHT;
	}

	public boolean getScrollableTracksViewportWidth() {
		return true;
	}

	public boolean getScrollableTracksViewportHeight() {
		return false;
	}
}
