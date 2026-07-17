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
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.JPanel;
import javax.swing.Scrollable;

/**
 * Timed multi-day grid. Snap precision is 1 minute; vertical scale is
 * pixels-per-minute (fit to viewport or mouse-wheel zoom). Hour range can be
 * full day (0–24) or work hours (4–22).
 */
final class DayViewPanel extends JPanel implements Scrollable {
	private static final long serialVersionUID = 1L;

	private static final int HOUR_LABEL_WIDTH = 48;
	private static final int DAY_HEADER_HEIGHT = 36;
	private static final int SNAP_MINUTES = 1;
	private static final int MIN_CREATE_MINUTES = 5;
	private static final double MIN_PPM = 0.35;
	private static final double MAX_PPM = 8.0;

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

		void onAppointmentResized(CalendarAppointment appt, long newEndMillis);

		void onAppointmentPopup(CalendarAppointment appt, int x, int y);
	}

	interface EmptyPopupListener {
		/** Right-click on empty grid (or current time selection) to create a task. */
		void onEmptyPopup(Date start, Date end, int x, int y);
	}

	private final SimpleDateFormat dayHeaderFormat = new SimpleDateFormat("M/d EEE", Locale.CHINA);
	private final SimpleDateFormat hourFormat = new SimpleDateFormat("H:mm", Locale.CHINA);

	private Date startDate = startOfDay(new Date());
	private int daysToShow = 7;
	/** Visible hour window, inclusive start / exclusive end (e.g. 0–24 or 4–22). */
	private int rangeStartHour = 0;
	private int rangeEndHour = 24;
	/** Vertical zoom: pixels per minute. */
	private double pixelsPerMinute = 1.2;
	private boolean userZoomed;
	private List appointments = Collections.EMPTY_LIST;
	private static final int RESIZE_EDGE_PX = 7;

	private Date selectionStart;
	private Date selectionEnd;
	private SelectionListener selectionListener;
	private DayHeaderListener dayHeaderListener;
	private AppointmentListener appointmentListener;
	private EmptyPopupListener emptyPopupListener;
	private Date selectedDayHighlight;
	private CalendarAppointment selectedAppointment;
	private CalendarAppointment draggingAppointment;
	private long draggingDurationMs;
	private Date dragAnchorTime;
	private int dragDayIndex = -1;
	private boolean resizingAppointment;
	private boolean dragMoved;
	private int pressX;
	private int pressY;

	DayViewPanel() {
		setBackground(CalendarTheme.CANVAS);
		setOpaque(true);
		setFocusable(true);
		addMouseWheelListener(new java.awt.event.MouseWheelListener() {
			public void mouseWheelMoved(final java.awt.event.MouseWheelEvent e) {
				// Wheel zooms; Shift+wheel keeps normal scroll for panning when zoomed in.
				if (e.isShiftDown()) {
					return;
				}
				final double factor = e.getWheelRotation() < 0 ? 1.12 : (1.0 / 1.12);
				zoomAt(e.getY(), factor);
				e.consume();
			}
		});
		final MouseAdapter mouse = new MouseAdapter() {
			private Date emptyDragStart;

			public void mousePressed(final MouseEvent e) {
				requestFocusInWindow();
				pressX = e.getX();
				pressY = e.getY();
				dragMoved = false;
				draggingAppointment = null;
				resizingAppointment = false;
				dragDayIndex = -1;
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
					dragDayIndex = dayIndexFor(hit.start);
					resizingAppointment = isResizeEdge(hit, e.getX(), e.getY());
					dragAnchorTime = snapTime(getTimeAtLockedDay(e.getY(), dragDayIndex));
					selectionStart = hit.start;
					selectionEnd = hit.end != null ? hit.end
					        : new Date(hit.startMillis() + draggingDurationMs);
					if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
						if (appointmentListener != null) {
							appointmentListener.onAppointmentPopup(hit, e.getX(), e.getY());
						}
						draggingAppointment = null;
						resizingAppointment = false;
					}
					repaint();
					return;
				}
				selectedAppointment = null;
				final Date at = snapTime(getTimeAt(e.getX(), e.getY()));
				if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
					fireEmptyPopup(at, e.getX(), e.getY());
					return;
				}
				emptyDragStart = at;
				selectionStart = emptyDragStart;
				selectionEnd = emptyDragStart == null ? null
				        : new Date(emptyDragStart.getTime() + MIN_CREATE_MINUTES * 60L * 1000L);
				repaint();
			}

			public void mouseDragged(final MouseEvent e) {
				if (Math.abs(e.getX() - pressX) + Math.abs(e.getY() - pressY) > 3) {
					dragMoved = true;
				}
				if (draggingAppointment != null) {
					if (resizingAppointment) {
						final Date at = snapTime(getTimeAtLockedDay(e.getY(), dragDayIndex));
						if (at == null || selectionStart == null) {
							return;
						}
						final long minEnd = selectionStart.getTime() + SNAP_MINUTES * 60L * 1000L;
						selectionEnd = new Date(Math.max(minEnd, at.getTime()));
						draggingDurationMs = selectionEnd.getTime() - selectionStart.getTime();
						setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
						repaint();
						return;
					}
					final Date at = snapTime(getTimeAtLockedDay(e.getY(), dragDayIndex));
					if (at == null || dragAnchorTime == null) {
						return;
					}
					final long delta = at.getTime() - dragAnchorTime.getTime();
					final long newStart = draggingAppointment.startMillis() + delta;
					selectionStart = new Date(newStart);
					selectionEnd = new Date(newStart + draggingDurationMs);
					repaint();
					return;
				}
				if (emptyDragStart == null) {
					return;
				}
				final Date at = snapTime(getTimeAt(e.getX(), e.getY()));
				if (at == null) {
					return;
				}
				if (at.before(emptyDragStart)) {
					selectionStart = at;
					selectionEnd = new Date(emptyDragStart.getTime() + MIN_CREATE_MINUTES * 60L * 1000L);
				}
				else {
					selectionStart = emptyDragStart;
					selectionEnd = new Date(at.getTime() + MIN_CREATE_MINUTES * 60L * 1000L);
				}
				if (selectionEnd.getTime() - selectionStart.getTime() < MIN_CREATE_MINUTES * 60L * 1000L) {
					selectionEnd = new Date(selectionStart.getTime() + MIN_CREATE_MINUTES * 60L * 1000L);
				}
				repaint();
			}

			public void mouseMoved(final MouseEvent e) {
				final CalendarAppointment hit = getAppointmentAt(e.getX(), e.getY());
				if (hit != null && isResizeEdge(hit, e.getX(), e.getY())) {
					setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
				}
				else {
					setCursor(Cursor.getDefaultCursor());
				}
			}

			public void mouseReleased(final MouseEvent e) {
				setCursor(Cursor.getDefaultCursor());
				if (e.isPopupTrigger() && selectedAppointment != null && appointmentListener != null) {
					appointmentListener.onAppointmentPopup(selectedAppointment, e.getX(), e.getY());
					draggingAppointment = null;
					resizingAppointment = false;
					return;
				}
				if (draggingAppointment != null) {
					final CalendarAppointment source = draggingAppointment;
					final boolean wasResize = resizingAppointment;
					final Date newStart = selectionStart;
					final Date newEnd = selectionEnd;
					if (dragMoved && newStart != null && newEnd != null && appointmentListener != null) {
						// Optimistic local update first so the block does not snap back
						// while the async reload runs.
						replaceAppointmentLocally(source, newStart, newEnd);
						draggingAppointment = null;
						resizingAppointment = false;
						selectionStart = null;
						selectionEnd = null;
						repaint();
						if (wasResize) {
							appointmentListener.onAppointmentResized(source, newEnd.getTime());
						}
						else {
							appointmentListener.onAppointmentMoved(source, newStart.getTime());
						}
						return;
					}
					draggingAppointment = null;
					resizingAppointment = false;
					selectionStart = null;
					selectionEnd = null;
					if (!dragMoved && appointmentListener != null) {
						if (e.getClickCount() >= 2) {
							appointmentListener.onAppointmentActivated(source);
						}
						else {
							appointmentListener.onAppointmentClicked(source);
						}
					}
					repaint();
					return;
				}
				if (e.isPopupTrigger()) {
					final Date at = snapTime(getTimeAt(e.getX(), e.getY()));
					fireEmptyPopup(at, e.getX(), e.getY());
					return;
				}
				if (selectionListener != null && selectionStart != null && selectionEnd != null) {
					selectionListener.onTimeSelected(selectionStart, selectionEnd);
				}
			}

			public void mouseClicked(final MouseEvent e) {
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

	void setEmptyPopupListener(final EmptyPopupListener listener) {
		this.emptyPopupListener = listener;
	}

	void setSelectedDayHighlight(final Date day) {
		this.selectedDayHighlight = day == null ? null : startOfDay(day);
		repaint();
	}

	private void fireEmptyPopup(final Date clicked, final int x, final int y) {
		if (emptyPopupListener == null) {
			return;
		}
		Date start = selectionStart;
		Date end = selectionEnd;
		if (start == null || end == null) {
			start = clicked;
			end = clicked == null ? null : new Date(clicked.getTime() + MIN_CREATE_MINUTES * 60L * 1000L);
		}
		if (start == null || end == null) {
			return;
		}
		selectionStart = start;
		selectionEnd = end;
		repaint();
		emptyPopupListener.onEmptyPopup(start, end, x, y);
	}

	CalendarAppointment getAppointmentAt(final int x, final int y) {
		final int bodyWidth = Math.max(1, getWidth() - HOUR_LABEL_WIDTH);
		final int dayWidth = Math.max(1, bodyWidth / daysToShow);
		final int[] layout = layoutOverlapColumns();
		for (int i = appointments.size() - 1; i >= 0; i--) {
			final CalendarAppointment appt = (CalendarAppointment) appointments.get(i);
			final int day = dayIndexFor(appt.start);
			if (day < 0) {
				continue;
			}
			final int col = layout[i * 2];
			final int colCount = Math.max(1, layout[i * 2 + 1]);
			final int colW = Math.max(8, (dayWidth - 6) / colCount);
			final int ax = HOUR_LABEL_WIDTH + day * dayWidth + 3 + col * colW;
			final int y1 = yForTime(appt.start);
			Date end = appt.end;
			if (end == null || !end.after(appt.start)) {
				end = new Date(appt.start.getTime() + MIN_CREATE_MINUTES * 60L * 1000L);
			}
			int y2 = yForTime(end);
			if (y2 <= y1) {
				y2 = y1 + Math.max(minBlockHeight() / 2, 8);
			}
			final int w = colW - 2;
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

	void setHourRange(final int startHour, final int endHour) {
		rangeStartHour = Math.max(0, Math.min(23, startHour));
		rangeEndHour = Math.max(rangeStartHour + 1, Math.min(24, endHour));
		userZoomed = false;
		revalidate();
		repaint();
	}

	int getRangeStartHour() {
		return rangeStartHour;
	}

	int getRangeEndHour() {
		return rangeEndHour;
	}

	String getRangeLabel() {
		return rangeStartHour + ":00–" + rangeEndHour + ":00 · 1分钟";
	}

	/** Fit the visible hour range into {@code viewportHeight} pixels (fills the screen). */
	void fitToViewportHeight(final int viewportHeight) {
		final int usable = Math.max(80, viewportHeight - DAY_HEADER_HEIGHT - 8);
		final int span = Math.max(60, (rangeEndHour - rangeStartHour) * 60);
		pixelsPerMinute = usable / (double) span;
		if (pixelsPerMinute < MIN_PPM) {
			pixelsPerMinute = MIN_PPM;
		}
		if (pixelsPerMinute > MAX_PPM) {
			pixelsPerMinute = MAX_PPM;
		}
		userZoomed = false;
		revalidate();
		repaint();
	}

	boolean isUserZoomed() {
		return userZoomed;
	}

	void zoomAt(final int anchorY, final double factor) {
		final double old = pixelsPerMinute;
		double next = old * factor;
		if (next < MIN_PPM) {
			next = MIN_PPM;
		}
		if (next > MAX_PPM) {
			next = MAX_PPM;
		}
		if (Math.abs(next - old) < 0.0001) {
			return;
		}
		// Keep the time under the cursor stable while zooming.
		final Date under = getTimeAtLockedDay(anchorY, 0);
		pixelsPerMinute = next;
		userZoomed = true;
		revalidate();
		if (getParent() instanceof javax.swing.JViewport) {
			final javax.swing.JViewport vp = (javax.swing.JViewport) getParent();
			vp.validate();
			if (under != null) {
				final int newY = yForTime(under);
				final java.awt.Point view = vp.getViewPosition();
				view.y = Math.max(0, newY - (anchorY - DAY_HEADER_HEIGHT));
				vp.setViewPosition(view);
			}
		}
		repaint();
	}

	/** @deprecated kept for callers; always 1-minute snap. */
	void setTimeScaleMinutes(final int minutes) {
		// no-op: snap is fixed at 1 minute
		revalidate();
		repaint();
	}

	int getTimeScaleMinutes() {
		return SNAP_MINUTES;
	}

	int getSlotHeight() {
		return Math.max(8, (int) Math.round(pixelsPerMinute * 15));
	}

	private int minBlockHeight() {
		return Math.max(6, (int) Math.round(pixelsPerMinute * 2));
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
		revalidate();
		repaint();
	}

	/** Scroll helper: pixel Y of a time, or -1. */
	int getYForTime(final Date time) {
		if (time == null) {
			return -1;
		}
		return yForTime(time);
	}

	/** Pixel → Date; day column locked so vertical drag does not jump across days. */
	Date getTimeAtLockedDay(final int y, final int dayIndex) {
		if (y < DAY_HEADER_HEIGHT || dayIndex < 0 || dayIndex >= daysToShow) {
			return null;
		}
		final double minutesFromStart = Math.max(0, (y - DAY_HEADER_HEIGHT) / pixelsPerMinute);
		final int totalMinutes = rangeStartHour * 60 + (int) Math.round(minutesFromStart);
		final int maxMinute = rangeEndHour * 60 - SNAP_MINUTES;
		final Calendar cal = Calendar.getInstance();
		cal.setTime(startDate);
		cal.add(Calendar.DAY_OF_MONTH, dayIndex);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.add(Calendar.MINUTE, Math.min(maxMinute, Math.max(rangeStartHour * 60, totalMinutes)));
		return cal.getTime();
	}

	private boolean isResizeEdge(final CalendarAppointment appt, final int x, final int y) {
		if (appt == null || appt.start == null) {
			return false;
		}
		final int bodyWidth = Math.max(1, getWidth() - HOUR_LABEL_WIDTH);
		final int dayWidth = Math.max(1, bodyWidth / daysToShow);
		final int day = dayIndexFor(appt.start);
		if (day < 0) {
			return false;
		}
		final int[] layout = layoutOverlapColumns();
		int col = 0;
		int colCount = 1;
		for (int i = 0; i < appointments.size(); i++) {
			if (appointments.get(i) == appt) {
				col = layout[i * 2];
				colCount = Math.max(1, layout[i * 2 + 1]);
				break;
			}
		}
		final int colW = Math.max(8, (dayWidth - 6) / colCount);
		final int ax = HOUR_LABEL_WIDTH + day * dayWidth + 3 + col * colW;
		final int w = colW - 2;
		Date end = appt.end;
		if (end == null || !end.after(appt.start)) {
			end = new Date(appt.start.getTime() + MIN_CREATE_MINUTES * 60L * 1000L);
		}
		int y2 = yForTime(end);
		final int y1 = yForTime(appt.start);
		if (y2 <= y1) {
			y2 = y1 + Math.max(minBlockHeight() / 2, 8);
		}
		return x >= ax && x <= ax + w && y >= y2 - RESIZE_EDGE_PX && y <= y2 + 2;
	}

	/**
	 * Replace an appointment in the local list so the UI stays at the dragged
	 * position until the async reload finishes (avoids snap-back jump).
	 */
	void replaceAppointmentLocally(final CalendarAppointment oldAppt, final Date newStart, final Date newEnd) {
		if (oldAppt == null || newStart == null || appointments == null || appointments.isEmpty()) {
			return;
		}
		final ArrayList copy = new ArrayList(appointments);
		for (int i = 0; i < copy.size(); i++) {
			if (copy.get(i) == oldAppt) {
				copy.set(i, new CalendarAppointment(newStart, newEnd, oldAppt.title, oldAppt.color, oldAppt.userData));
				appointments = copy;
				selectedAppointment = (CalendarAppointment) copy.get(i);
				return;
			}
		}
	}

	/** Pixel → Date with 1-minute snap. */
	Date getTimeAt(final int x, final int y) {
		if (y < DAY_HEADER_HEIGHT || x < HOUR_LABEL_WIDTH) {
			return null;
		}
		final int bodyWidth = Math.max(1, getWidth() - HOUR_LABEL_WIDTH);
		final int dayWidth = Math.max(1, bodyWidth / daysToShow);
		final int dayIndex = Math.min(daysToShow - 1, (x - HOUR_LABEL_WIDTH) / dayWidth);
		return getTimeAtLockedDay(y, dayIndex);
	}

	/** Snap to 1-minute grid. */
	Date snapTime(final Date time) {
		if (time == null) {
			return null;
		}
		final Calendar cal = Calendar.getInstance();
		cal.setTime(time);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		final int minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
		final int snapped = (minutes / SNAP_MINUTES) * SNAP_MINUTES;
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.add(Calendar.MINUTE, snapped);
		return cal.getTime();
	}

	Date getSelectionStart() {
		return selectionStart;
	}

	Date getSelectionEnd() {
		return selectionEnd;
	}

	void clearSelection() {
		selectionStart = null;
		selectionEnd = null;
		repaint();
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
		final int minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
		        + cal.get(Calendar.SECOND) / 60;
		return DAY_HEADER_HEIGHT + (int) Math.round((minutes - rangeStartHour * 60) * pixelsPerMinute);
	}

	private int dayIndexFor(final Date time) {
		if (time == null || startDate == null) {
			return -1;
		}
		final Calendar cursor = Calendar.getInstance();
		cursor.setTime(startOfDay(startDate));
		final Calendar target = Calendar.getInstance();
		target.setTime(startOfDay(time));
		for (int idx = 0; idx < daysToShow; idx++) {
			if (sameDay(cursor, target)) {
				return idx;
			}
			cursor.add(Calendar.DAY_OF_MONTH, 1);
		}
		return -1;
	}

	public Dimension getPreferredSize() {
		final int span = Math.max(60, (rangeEndHour - rangeStartHour) * 60);
		final int bodyH = (int) Math.round(span * pixelsPerMinute);
		return new Dimension(860, DAY_HEADER_HEIGHT + bodyH + 8);
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
			final boolean isSelectedDay = selectedDayHighlight != null
			        && sameDay(day, startOfDayCalendar(selectedDayHighlight));
			if (isSelectedDay) {
				g2.setColor(CalendarTheme.CHIP_BG);
			}
			else {
				g2.setColor(isToday ? CalendarTheme.ACCENT_WASH
				        : (weekend ? CalendarTheme.WEEKEND_WASH : CalendarTheme.SURFACE));
			}
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
		for (int hour = rangeStartHour; hour <= rangeEndHour; hour++) {
			final Calendar hourCal = Calendar.getInstance();
			hourCal.set(Calendar.HOUR_OF_DAY, hour);
			hourCal.set(Calendar.MINUTE, 0);
			hourCal.set(Calendar.SECOND, 0);
			hourCal.set(Calendar.MILLISECOND, 0);
			// Use today's date shell — yForTime only reads hour/minute.
			final int y = DAY_HEADER_HEIGHT
			        + (int) Math.round((hour - rangeStartHour) * 60 * pixelsPerMinute);
			g2.setColor(CalendarTheme.GRID_STRONG);
			g2.drawLine(HOUR_LABEL_WIDTH, y, width, y);
			if (pixelsPerMinute >= 1.0 && hour < rangeEndHour) {
				for (int m = 15; m < 60; m += 15) {
					final int mid = y + (int) Math.round(m * pixelsPerMinute);
					g2.setColor(m == 30 ? CalendarTheme.GRID_STRONG : CalendarTheme.GRID);
					g2.drawLine(HOUR_LABEL_WIDTH, mid, width, mid);
				}
			}
			else if (pixelsPerMinute >= 0.6 && hour < rangeEndHour) {
				final int mid = y + (int) Math.round(30 * pixelsPerMinute);
				g2.setColor(CalendarTheme.GRID);
				g2.drawLine(HOUR_LABEL_WIDTH, mid, width, mid);
			}
			if (hour < rangeEndHour) {
				g2.setColor(CalendarTheme.TEXT_MUTED);
				final String h = hour + ":00";
				final FontMetrics hourFm = g2.getFontMetrics();
				g2.drawString(h, HOUR_LABEL_WIDTH - hourFm.stringWidth(h) - 6, y + 12);
			}
		}

		if (draggingAppointment == null) {
			paintTimeRange(g2, selectionStart, selectionEnd, CalendarTheme.SELECTION, dayWidth);
		}

		final int[] layout = layoutOverlapColumns();
		for (int i = 0; i < appointments.size(); i++) {
			try {
				final CalendarAppointment appt = (CalendarAppointment) appointments.get(i);
				final int col = layout[i * 2];
				final int colCount = Math.max(1, layout[i * 2 + 1]);
				if (draggingAppointment == appt && selectionStart != null) {
					final CalendarAppointment ghost = new CalendarAppointment(selectionStart,
					        new Date(selectionStart.getTime() + draggingDurationMs), appt.title, appt.color,
					        appt.userData);
					paintAppointment(g2, ghost, dayWidth, i, true, 0, 1);
				}
				else {
					paintAppointment(g2, appt, dayWidth, i, appt == selectedAppointment, col, colCount);
				}
			}
			catch (Exception ignore) {
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

	/**
	 * Greedy overlap columns per day: returns pairs [col, colCount] for each appointment index.
	 */
	private int[] layoutOverlapColumns() {
		final int n = appointments.size();
		final int[] result = new int[Math.max(2, n * 2)];
		if (n == 0) {
			return result;
		}
		final int[] colOf = new int[n];
		final long[] starts = new long[n];
		final long[] ends = new long[n];
		final int[] days = new int[n];
		for (int i = 0; i < n; i++) {
			final CalendarAppointment a = (CalendarAppointment) appointments.get(i);
			days[i] = dayIndexFor(a.start);
			starts[i] = a.startMillis();
			long end = a.endMillis();
			if (end <= starts[i]) {
				end = starts[i] + MIN_CREATE_MINUTES * 60L * 1000L;
			}
			ends[i] = end;
			colOf[i] = 0;
		}
		for (int i = 0; i < n; i++) {
			if (days[i] < 0) {
				continue;
			}
			boolean[] used = new boolean[n];
			for (int j = 0; j < i; j++) {
				if (days[j] != days[i]) {
					continue;
				}
				if (starts[i] < ends[j] && starts[j] < ends[i]) {
					used[colOf[j]] = true;
				}
			}
			int c = 0;
			while (c < n && used[c]) {
				c++;
			}
			colOf[i] = c;
		}
		final int[] maxCol = new int[Math.max(1, daysToShow)];
		for (int i = 0; i < n; i++) {
			if (days[i] >= 0 && days[i] < maxCol.length) {
				if (colOf[i] + 1 > maxCol[days[i]]) {
					maxCol[days[i]] = colOf[i] + 1;
				}
			}
		}
		for (int i = 0; i < n; i++) {
			result[i * 2] = colOf[i];
			result[i * 2 + 1] = days[i] >= 0 && days[i] < maxCol.length ? Math.max(1, maxCol[days[i]]) : 1;
		}
		return result;
	}

	private void paintAppointment(final Graphics2D g2, final CalendarAppointment appt, final int dayWidth,
	        final int index, final boolean selected, final int col, final int colCount) {
		if (appt == null || appt.start == null) {
			return;
		}
		final int day = dayIndexFor(appt.start);
		if (day < 0) {
			return;
		}
		final int cols = Math.max(1, colCount);
		final int colW = Math.max(8, (dayWidth - 6) / cols);
		final int x = HOUR_LABEL_WIDTH + day * dayWidth + 3 + col * colW;
		final int y1 = yForTime(appt.start);
		Date end = appt.end;
		if (end == null || !end.after(appt.start)) {
			end = new Date(appt.start.getTime() + MIN_CREATE_MINUTES * 60L * 1000L);
		}
		int y2 = yForTime(end);
		if (y2 <= y1) {
			y2 = y1 + Math.max(minBlockHeight() / 2, 6);
		}
		final int w = colW - 2;
		final int h = Math.max(minBlockHeight(), y2 - y1);
		final Color fill = appt.color != null ? appt.color : CalendarTheme.eventColor(index);
		g2.setColor(fill);
		g2.fillRoundRect(x, y1, w, h, 6, 6);
		g2.setColor(new Color(255, 255, 255, 50));
		g2.fillRoundRect(x, y1, w, Math.min(8, h / 3), 6, 6);
		if (selected) {
			g2.setColor(CalendarTheme.TEXT);
			g2.drawRoundRect(x, y1, w, h, 6, 6);
		}
		g2.setColor(Color.WHITE);
		g2.setFont(CalendarTheme.font(11f, Font.BOLD));
		final FontMetrics fm = g2.getFontMetrics();
		final String line = appt.title == null ? "" : appt.title;
		g2.drawString(clip(line, fm, w - 8), x + 4, y1 + Math.min(h - 3, fm.getAscent() + 2));
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

	private static Calendar startOfDayCalendar(final Date date) {
		final Calendar cal = Calendar.getInstance();
		cal.setTime(startOfDay(date));
		return cal;
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
		return Math.max(12, (int) Math.round(pixelsPerMinute * 15));
	}

	public int getScrollableBlockIncrement(final java.awt.Rectangle visibleRect, final int orientation,
	        final int direction) {
		return Math.max(visibleRect.height / 2, (int) Math.round(pixelsPerMinute * 60));
	}

	public boolean getScrollableTracksViewportWidth() {
		return true;
	}

	public boolean getScrollableTracksViewportHeight() {
		return false;
	}
}
