package org.docear.plugin.core.calendar;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.freeplane.core.util.LogUtils;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderCalendarBridge;

/**
 * Loads reminder occurrences into {@link CalendarAppointment} blocks.
 */
final class CalendarTaskService {
	private CalendarTaskService() {
	}

	static List loadAppointments(final long rangeStart, final long rangeEnd) {
		final List appointments = new ArrayList();
		try {
			final List refs = ReminderCalendarBridge.loadOccurrences(rangeStart, rangeEnd);
			for (int i = 0; i < refs.size(); i++) {
				final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) refs.get(i);
				appointments.add(toAppointment(ref, i));
			}
		}
		catch (Exception e) {
			LogUtils.warn("CalendarTaskService.loadAppointments failed", e);
		}
		return appointments;
	}

	static CalendarAppointment toAppointment(final ReminderCalendarBridge.OccurrenceRef ref, final int index) {
		final int minutes = ref.taskTimeMinutes > 0 ? ref.taskTimeMinutes : 30;
		final Date start = new Date(ref.occurrenceAt);
		final Date end = new Date(ref.occurrenceAt + minutes * 60L * 1000L);
		final Color color = colorFor(ref, index);
		final String title = (ref.recurring ? "↻ " : "") + (ref.nodeText == null ? "" : ref.nodeText);
		return new CalendarAppointment(start, end, title, color, ref);
	}

	static Color colorFor(final ReminderCalendarBridge.OccurrenceRef ref, final int index) {
		if (ref.jinji >= 2) {
			return CalendarTheme.EVENT_C;
		}
		if (ref.recurring) {
			return CalendarTheme.EVENT_D;
		}
		return CalendarTheme.eventColor(index);
	}

	static ReminderCalendarBridge.OccurrenceRef asRef(final CalendarAppointment appt) {
		if (appt == null || !(appt.userData instanceof ReminderCalendarBridge.OccurrenceRef)) {
			return null;
		}
		return (ReminderCalendarBridge.OccurrenceRef) appt.userData;
	}

	static boolean reschedule(final CalendarAppointment appt, final long newStartMillis) {
		final ReminderCalendarBridge.OccurrenceRef ref = asRef(appt);
		if (ref == null) {
			return false;
		}
		final long delta = newStartMillis - ref.occurrenceAt;
		final long newStored = ref.storedRemindAt + delta;
		return ReminderCalendarBridge.updateRemindAt(ref.file, ref.nodeId, newStored);
	}

	static boolean checkInOrComplete(final CalendarAppointment appt) {
		final ReminderCalendarBridge.OccurrenceRef ref = asRef(appt);
		if (ref == null) {
			return false;
		}
		return ReminderCalendarBridge.checkIn(ref.file, ref.nodeId, ref.occurrenceAt);
	}

	static void open(final CalendarAppointment appt) {
		final ReminderCalendarBridge.OccurrenceRef ref = asRef(appt);
		if (ref == null) {
			return;
		}
		CalendarViewportService.hide();
		ReminderCalendarBridge.openNode(ref.file, ref.nodeId);
	}
}
