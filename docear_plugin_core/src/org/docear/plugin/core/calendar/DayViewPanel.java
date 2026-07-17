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
 * Timed multi-day grid (DocearReminder DayView + CalendarTimeScale).
 * Supports 5 / 15 / 30 / 60 minute slots with 1-minute drag precision.
 */
final class DayViewPanel extends JPanel implements Scrollable {
	private static final long serialVersionUID = 1L;

	private static final int HOUR_LABEL_WIDTH = 54;
	private static final int DAY_HEADER_HEIGHT = 36;
	private static final int START_HOUR = 0;
	private static final int END_HOUR = 24;

	interface SelectionListener {
		void onTimeSelected(Date start, Date end);
	}

	interface DayHeaderListener {
		void onDayHeaderClicked(Date dayStart);
	}

	interface AppointmentListener {
		void onAppointmentClicked(CalendarAppointment appt);

		void onAppointmentActivated(CalendarAppointment appt);

		void onAppointmentMoved(CalendarAppointment appt, long newStartMillis);

		void onAppointmentPopup(CalendarAppointment appt, int x, int y);
	}

	private final SimpleDateFormat dayHeaderFormat = new SimpleDateFormat("M/d EEE", Locale.CHINA);
	private final SimpleDateFormat hourFormat = new SimpleDateFormat("H:mm", Locale.CHINA);

	private Date startDate = startOfDay(new Date());
	private int daysToShow = 7;
	/** Slot size in minutes: 5, 15, 30, 60 (System.Windows.Forms.Calendar.CalendarTimeScale). */
	private int timeScaleMinutes = 30;
	private int slotHeight = 22;
	private List appointments = Collections.EMPTY_LIST;
	private Date selectionStart;
	private Date selectionEnd;
	private SelectionListener selectionListener;
	private DayHeaderListener dayHeaderListener;
	private AppointmentListener appointmentListener;
	private CalendarAppointment selectedAppointment;
	private CalendarAppointment draggingAppointment;
	private long draggingDurationMs;
	private Date dragAnchorTime;
	private boolean dragMoved;
	private int pressX;
	private int pressY;

	DayViewPanel() {
		setBackground(CalendarTheme.CANVAS);
		setOpaque(true);
		setFocusable(true);
		final MouseAdapter mouse = new MouseAdapter() {
			private Date emptyDragStart;

			public void mousePressed(final MouseEvent e) {
				requestFocusInWindow();
				pressX = e.getX();
				pressY = e.getY();
				dragMoved = false;
				draggingAppointment = null;
				emptyDragStart = null;
				if (e.getY() < DAY_HEADER_HEIGHT && e.getX() >= HOUR_LABEL_WIDTH) {
					final Date day = dayAtX(e.getX());
					if (day != null && dayHeaderListener != null) {
						dayHeaderListener.onDayHeaderClicked(day);
					}
					return;
				}
				final CalendarAppointment hit = getAppointmentAt(e.getX(), e.getY());
				if (hit != null) {
					selectedAppointment = hit;
					draggingAppointment = hit;
					draggingDurationMs = Math.max(60L * 1000L, hit.endMillis() - hit.startMillis());
					dragAnchorTime = getTimeAt(e.getX(), e.getY());
					selectionStart = null;
					selectionEnd = null;
					if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
						if (appointmentListener != null) {
							appointmentListener.onAppointmentPopup(hit, e.getX(), e.getY());
						}
					}
					repaint();
					return;
				}
				selectedAppointment = null;
				emptyDragStart = getTimeAt(e.getX(), e.getY());
				selectionStart = emptyDragStart;
				selectionEnd = emptyDragStart == null ? null
				        : new Date(emptyDragStart.getTime() + timeScaleMinutes * 60L * 1000L);
				repaint();
			}

			public void mouseDragged(final MouseEvent e) {
				if (Math.abs(e.getX() - pressX) + Math.abs(e.getY() - pressY) > 3) {
					dragMoved = true;
				}
				if (draggingAppointment != null) {
					final Date at = getTimeAt(e.getX(), e.getY());
					if (at == null || dragAnchorTime == null) {
						return;
					}
					final long delta = at.getTime() - dragAnchorTime.getTime();
					final long newStart = draggingAppointment.startMillis() + delta;
					final Date ns = new Date(newStart);
					selectionStart = ns;
					selectionEnd = new Date(newStart + draggingDurationMs);
					repaint();
					return;
				}
				if (emptyDragStart == null) {
					return;
				}
				final Date at = getTimeAt(e.getX(), e.getY());
				if (at == null) {
					return;
				}
				if (at.before(emptyDragStart)) {
					selectionStart = at;
					selectionEnd = emptyDragStart;
				}
				else {
					selectionStart = emptyDragStart;
					selectionEnd = at;
				}
				if (selectionEnd.getTime() - selectionStart.getTime() < 60L * 1000L) {
					selectionEnd = new Date(selectionStart.getTime() + 60L * 1000L);
				}
				repaint();
			}

			public void mouseReleased(final MouseEvent e) {
				if (e.isPopupTrigger() && selectedAppointment != null && appointmentListener != null) {
					appointmentListener.onAppointmentPopup(selectedAppointment, e.getX(), e.getY());
					draggingAppointment = null;
					return;
				}
				if (draggingAppointment != null) {
					if (dragMoved && selectionStart != null && appointmentListener != null) {
						appointmentListener.onAppointmentMoved(draggingAppointment, selectionStart.getTime());
					}
					else if (!dragMoved && appointmentListener != null) {
						if (e.getClickCount() >= 2) {
							appointmentListener.onAppointmentActivated(draggingAppointment);
						}
						else {
							appointmentListener.onAppointmentClicked(draggingAppointment);
						}
					}
					draggingAppointment = null;
					selectionStart = null;
					selectionEnd = null;
					repaint();
					return;
				}
				if (!dragMoved && selectionListener != null && selectionStart != null && selectionEnd != null) {
					selectionListener.onTimeSelected(selectionStart, selectionEnd);
				}
			}

			public void mouseClicked(final MouseEvent e) {
				// double-click handled in released for appointments
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	void setSelectionListener(final SelectionListener listener) {
		this.selectionListener = listener;
	}

	void setDayHeaderListener(final DayHeaderListener listener) {
		this.dayHeaderListener = listener;
	}

	void setAppointmentListener(final AppointmentListener listener) {
		this.appointmentListener = listener;
	}

	CalendarAppointment getAppointmentAt(final int x, final int y) {
		final int bodyWidth = Math.max(1, getWidth() - HOUR_LABEL_WIDTH);
		final int dayWidth = Math.max(1, bodyWidth / daysToShow);
		for (int i = appointments.size() - 1; i >= 0; i--) {
			final CalendarAppointment appt = (CalendarAppointment) appointments.get(i);
			final int day = dayIndexFor(appt.start);
			if (day < 0) {
				continue;
			}
			final int ax = HOUR_LABEL_WIDTH + day * dayWidth + 3;
			final int y1 = yForTime(appt.start);
			Date end = appt.end;
			if (end == null || !end.after(appt.start)) {
				end = new Date(appt.start.getTime() + timeScaleMinutes * 60L * 1000L);
			}
			int y2 = yForTime(end);
			if (y2 <= y1) {
				y2 = y1 + Math.max(slotHeight / 2, 10);
			}
			final int w = dayWidth - 6;
			if (x >= ax && x <= ax + w && y >= y1 && y <= y2) {
				return appt;
			}
		}
		return null;
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

	void setTimeScaleMinutes(final int minutes) {
		if (minutes == 5 || minutes == 15 || minutes == 30 || minutes == 60) {
			this.timeScaleMinutes = minutes;
		}
		else {
			this.timeScaleMinutes = 30;
		}
		this.slotHeight = minutes <= 5 ? 16 : (minutes <= 15 ? 18 : 22);
		revalidate();
		repaint();
	}

	int getTimeScaleMinutes() {
		return timeScaleMinutes;
	}

	int getSlotHeight() {
		return slotHeight;
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

	/** Pixel → Date with 1-minute precision. */
	Date getTimeAt(final int x, final int y) {
		if (y < DAY_HEADER_HEIGHT || x < HOUR_LABEL_WIDTH) {
			return null;
		}
		final int bodyWidth = Math.max(1, getWidth() - HOUR_LABEL_WIDTH);
		final int dayWidth = Math.max(1, bodyWidth / daysToShow);
		final int dayIndex = Math.min(daysToShow - 1, (x - HOUR_LABEL_WIDTH) / dayWidth);
		final int slotsFromTop = Math.max(0, (y - DAY_HEADER_HEIGHT) / slotHeight);
		final int totalMinutes = START_HOUR * 60 + slotsFromTop * timeScaleMinutes;
		// Sub-slot minute precision within the visible slot.
		final int within = (y - DAY_HEADER_HEIGHT) % slotHeight;
		final int extra = timeScaleMinutes <= 1 ? 0
		        : Math.min(timeScaleMinutes - 1, within * timeScaleMinutes / Math.max(1, slotHeight));
		final Calendar cal = Calendar.getInstance();
		cal.setTime(startDate);
		cal.add(Calendar.DAY_OF_MONTH, dayIndex);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.add(Calendar.MINUTE, Math.min(END_HOUR * 60 - 1, totalMinutes + extra));
		return cal.getTime();
	}

	private Date dayAtX(final int x) {
		if (x < HOUR_LABEL_WIDTH) {
			return null;
		}
		final int bodyWidth = Math.max(1, getWidth() - HOUR_LABEL_WIDTH);
		final int dayWidth = Math.max(1, bodyWidth / daysToShow);
		final int dayIndex = Math.min(daysToShow - 1, (x - HOUR_LABEL_WIDTH) / dayWidth);
		final Calendar cal = Calendar.getInstance();
		cal.setTime(startDate);
		cal.add(Calendar.DAY_OF_MONTH, dayIndex);
		return startOfDay(cal.getTime());
	}

	private int yForTime(final Date time) {
		if (time == null) {
			return DAY_HEADER_HEIGHT;
		}
		final Calendar cal = Calendar.getInstance();
		cal.setTime(time);
		final int minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
		return DAY_HEADER_HEIGHT + (minutes - START_HOUR * 60) * slotHeight / timeScaleMinutes;
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
		final int slots = (END_HOUR - START_HOUR) * 60 / timeScaleMinutes;
		return new Dimension(860, DAY_HEADER_HEIGHT + slots * slotHeight + 8);
	}

	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final int width = getWidth();
		final int height = getHeight();
		g2.setColor(CalendarTheme.CANVAS);
		g2.fillRect(0, 0, width, height);

		final int bodyWidth = Math.max(1, width - HOUR_LABEL_WIDTH);
		final int dayWidth = Math.max(1, bodyWidth / daysToShow);
		final Font headerFont = CalendarTheme.font(12f, Font.BOLD);
		final Font hourFont = CalendarTheme.font(11f);
		final FontMetrics headerFm = g2.getFontMetrics(headerFont);

		final Calendar todayStart = Calendar.getInstance();
		todayStart.setTime(startOfDay(new Date()));
		for (int d = 0; d < daysToShow; d++) {
			final int x = HOUR_LABEL_WIDTH + d * dayWidth;
			final Calendar day = Calendar.getInstance();
			day.setTime(startDate);
			day.add(Calendar.DAY_OF_MONTH, d);
			final boolean isToday = sameDay(day, todayStart);
			final boolean weekend = day.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
			        || day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
			g2.setColor(isToday ? CalendarTheme.ACCENT_WASH : (weekend ? CalendarTheme.WEEKEND_WASH : CalendarTheme.SURFACE));
			g2.fillRect(x, DAY_HEADER_HEIGHT, dayWidth, height - DAY_HEADER_HEIGHT);

			if (isToday) {
				final java.awt.GradientPaint gp = new java.awt.GradientPaint(x, 0, CalendarTheme.HEADER_TOP, x + dayWidth,
				        0, CalendarTheme.HEADER_BOTTOM);
				g2.setPaint(gp);
			}
			else {
				g2.setColor(CalendarTheme.SURFACE_SOFT);
			}
			g2.fillRect(x, 0, dayWidth, DAY_HEADER_HEIGHT);
			g2.setColor(CalendarTheme.GRID);
			g2.drawLine(x, 0, x, height);
			g2.setFont(headerFont);
			g2.setColor(isToday ? Color.WHITE : CalendarTheme.TEXT);
			final String label = dayHeaderFormat.format(day.getTime());
			g2.drawString(label, x + Math.max(6, (dayWidth - headerFm.stringWidth(label)) / 2), 23);
		}

		g2.setFont(hourFont);
		final int slotsPerHour = 60 / timeScaleMinutes;
		for (int hour = START_HOUR; hour <= END_HOUR; hour++) {
			final int y = DAY_HEADER_HEIGHT + (hour - START_HOUR) * slotsPerHour * slotHeight;
			g2.setColor(CalendarTheme.GRID_STRONG);
			g2.drawLine(HOUR_LABEL_WIDTH, y, width, y);
			for (int s = 1; s < slotsPerHour && hour < END_HOUR; s++) {
				final int mid = y + s * slotHeight;
				g2.setColor(CalendarTheme.GRID);
				g2.drawLine(HOUR_LABEL_WIDTH, mid, width, mid);
			}
			if (hour < END_HOUR) {
				g2.setColor(CalendarTheme.TEXT_MUTED);
				final String h = hour + ":00";
				final FontMetrics hourFm = g2.getFontMetrics();
				g2.drawString(h, HOUR_LABEL_WIDTH - hourFm.stringWidth(h) - 8, y + 12);
			}
		}

		if (draggingAppointment == null) {
			paintTimeRange(g2, selectionStart, selectionEnd, CalendarTheme.SELECTION, dayWidth);
		}

		for (int i = 0; i < appointments.size(); i++) {
			final CalendarAppointment appt = (CalendarAppointment) appointments.get(i);
			if (draggingAppointment == appt && selectionStart != null) {
				final CalendarAppointment ghost = new CalendarAppointment(selectionStart,
				        new Date(selectionStart.getTime() + draggingDurationMs), appt.title, appt.color, appt.userData);
				paintAppointment(g2, ghost, dayWidth, i, true);
			}
			else {
				paintAppointment(g2, appt, dayWidth, i, appt == selectedAppointment);
			}
		}

		final Date now = new Date();
		final int nowDay = dayIndexFor(now);
		if (nowDay >= 0) {
			final int y = yForTime(now);
			final int x = HOUR_LABEL_WIDTH + nowDay * dayWidth;
			g2.setColor(CalendarTheme.NOW);
			g2.fillRect(x, y - 1, dayWidth, 2);
			g2.fillOval(x - 4, y - 4, 8, 8);
		}

		g2.setColor(CalendarTheme.SURFACE_SOFT);
		g2.fillRect(0, 0, HOUR_LABEL_WIDTH, DAY_HEADER_HEIGHT);
		g2.setColor(CalendarTheme.GRID_STRONG);
		g2.drawLine(HOUR_LABEL_WIDTH - 1, 0, HOUR_LABEL_WIDTH - 1, height);
		g2.drawLine(0, DAY_HEADER_HEIGHT, width, DAY_HEADER_HEIGHT);
		g2.dispose();
	}

	private void paintAppointment(final Graphics2D g2, final CalendarAppointment appt, final int dayWidth,
	        final int index, final boolean selected) {
		if (appt == null || appt.start == null) {
			return;
		}
		final int day = dayIndexFor(appt.start);
		if (day < 0) {
			return;
		}
		final int x = HOUR_LABEL_WIDTH + day * dayWidth + 3;
		final int y1 = yForTime(appt.start);
		Date end = appt.end;
		if (end == null || !end.after(appt.start)) {
			end = new Date(appt.start.getTime() + timeScaleMinutes * 60L * 1000L);
		}
		int y2 = yForTime(end);
		if (y2 <= y1) {
			y2 = y1 + Math.max(slotHeight / 2, 10);
		}
		final int w = dayWidth - 6;
		final int h = Math.max(12, y2 - y1);
		final Color fill = appt.color != null ? appt.color : CalendarTheme.eventColor(index);
		g2.setColor(fill);
		g2.fillRoundRect(x, y1, w, h, 8, 8);
		g2.setColor(new Color(255, 255, 255, 60));
		g2.fillRoundRect(x, y1, w, Math.min(10, h / 3), 8, 8);
		if (selected) {
			g2.setColor(CalendarTheme.TEXT);
			g2.drawRoundRect(x, y1, w, h, 8, 8);
			g2.drawRoundRect(x + 1, y1 + 1, w - 2, h - 2, 7, 7);
		}
		g2.setColor(Color.WHITE);
		g2.setFont(CalendarTheme.font(11f, Font.BOLD));
		final FontMetrics fm = g2.getFontMetrics();
		final String line = hourFormat.format(appt.start) + "  " + appt.title;
		g2.drawString(clip(line, fm, w - 10), x + 6, y1 + Math.min(h - 4, fm.getAscent() + 3));
	}

	private void paintTimeRange(final Graphics2D g2, final Date start, final Date end, final Color color,
	        final int dayWidth) {
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
		g2.fillRect(x, y1, dayWidth - 2, y2 - y1);
	}

	private static String clip(final String text, final FontMetrics fm, final int maxWidth) {
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

	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}

	public int getScrollableUnitIncrement(final java.awt.Rectangle visibleRect, final int orientation,
	        final int direction) {
		return slotHeight;
	}

	public int getScrollableBlockIncrement(final java.awt.Rectangle visibleRect, final int orientation,
	        final int direction) {
		return visibleRect.height - slotHeight;
	}

	public boolean getScrollableTracksViewportWidth() {
		return true;
	}

	public boolean getScrollableTracksViewportHeight() {
		return false;
	}
}
