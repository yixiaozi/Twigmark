package org.docear.plugin.core.calendar;

import java.awt.Color;
import java.util.Date;

/**
 * One timed block on the DayView calendar (DocearReminder Appointment subset).
 */
public final class CalendarAppointment {
	public final Date start;
	public final Date end;
	public final String title;
	public final Color color;
	public final Object userData;

	public CalendarAppointment(final Date start, final Date end, final String title, final Color color,
	        final Object userData) {
		this.start = start;
		this.end = end;
		this.title = title == null ? "" : title;
		this.color = color == null ? new Color(0x5B, 0x9B, 0xD5) : color;
		this.userData = userData;
	}

	public long startMillis() {
		return start == null ? 0L : start.getTime();
	}

	public long endMillis() {
		return end == null ? startMillis() : end.getTime();
	}
}
