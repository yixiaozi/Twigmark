package org.docear.plugin.core.calendar;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.text.TextController;
import org.freeplane.view.swing.features.pomodoro.PomodoroSessionManager;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderCalendarBridge;

/**
 * Loads reminder / pomodoro blocks into {@link CalendarAppointment}s.
 */
final class CalendarTaskService {
	static final String KIND_REMINDER = "reminder";
	static final String KIND_POMODORO = "pomodoro";
	static final Color POMODORO_COLOR = new Color(0xE1, 0x1D, 0x48);
	static final Color POMODORO_LIVE = new Color(0xF4, 0x3F, 0x5E);

	private CalendarTaskService() {
	}

	static final class LoadResult {
		final List appointments;
		final Map dayCounts;
		final long elapsedMs;

		LoadResult(final List appointments, final Map dayCounts, final long elapsedMs) {
			this.appointments = appointments;
			this.dayCounts = dayCounts;
			this.elapsedMs = elapsedMs;
		}
	}

	static LoadResult loadBundle(final long viewStart, final long viewEnd, final long expandStart,
	        final long expandEnd, final boolean includePomodoro) {
		final ReminderCalendarBridge.LoadBundle bundle = ReminderCalendarBridge.loadBundle(viewStart, viewEnd,
		        expandStart, expandEnd);
		final List appointments = new ArrayList();
		for (int i = 0; i < bundle.occurrences.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) bundle.occurrences
			        .get(i);
			appointments.add(toAppointment(ref, i));
		}
		if (includePomodoro) {
			appointments.addAll(loadPomodoroAppointments(viewStart, viewEnd));
		}
		return new LoadResult(appointments, bundle.dayCounts, bundle.elapsedMs);
	}

	static List loadPomodoroAppointments(final long rangeStart, final long rangeEnd) {
		final List out = new ArrayList();
		try {
			final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
			if (mgr == null) {
				return out;
			}
			final List sessions = mgr.collectSessionsInRange(rangeStart, rangeEnd);
			for (int i = 0; i < sessions.size(); i++) {
				final PomodoroSessionManager.CalendarSession s = (PomodoroSessionManager.CalendarSession) sessions
				        .get(i);
				final String title = (s.live ? "▶ " : "● ") + plain(s.node);
				final Color color = s.live ? POMODORO_LIVE : POMODORO_COLOR;
				out.add(new CalendarAppointment(new Date(s.startMs), new Date(s.endMs), title, color, s));
			}
		}
		catch (Exception e) {
			LogUtils.warn("CalendarTaskService.loadPomodoroAppointments failed", e);
		}
		return out;
	}

	static CalendarAppointment toAppointment(final ReminderCalendarBridge.OccurrenceRef ref, final int index) {
		final int minutes = ref.taskTimeMinutes > 0 ? ref.taskTimeMinutes : 30;
		final Date start = new Date(ref.occurrenceAt);
		final Date end = new Date(ref.occurrenceAt + minutes * 60L * 1000L);
		final Color color = colorFor(ref, index);
		final String title = ref.nodeText == null ? "" : ref.nodeText;
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

	static boolean isPomodoro(final CalendarAppointment appt) {
		return appt != null && appt.userData instanceof PomodoroSessionManager.CalendarSession;
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
		if (isPomodoro(appt)) {
			final PomodoroSessionManager.CalendarSession s = (PomodoroSessionManager.CalendarSession) appt.userData;
			final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
			if (mgr != null) {
				CalendarViewportService.hide();
				mgr.navigateTo(s.node);
			}
			return;
		}
		final ReminderCalendarBridge.OccurrenceRef ref = asRef(appt);
		if (ref == null) {
			return;
		}
		CalendarViewportService.hide();
		ReminderCalendarBridge.openNode(ref.file, ref.nodeId);
	}

	static boolean resizeDuration(final CalendarAppointment appt, final long newEndMillis) {
		final ReminderCalendarBridge.OccurrenceRef ref = asRef(appt);
		if (ref == null || appt.start == null) {
			return false;
		}
		final int minutes = (int) Math.max(5L, (newEndMillis - appt.startMillis()) / 60000L);
		return ReminderCalendarBridge.updateTaskDuration(ref.file, ref.nodeId, minutes);
	}

	/**
	 * Open create dialog (title / cycle / duration / level / urgency) and write the reminder.
	 *
	 * @return {@link Boolean#TRUE} created, {@link Boolean#FALSE} failed, {@code null} cancelled
	 */
	static Boolean promptCreateTask(final java.awt.Component owner, final long startMs, final long endMs) {
		return ReminderCalendarBridge.promptAndCreateReminderTask(owner, startMs, endMs);
	}

	/** @return {@link Boolean#TRUE} updated, {@link Boolean#FALSE} failed, {@code null} cancelled */
	static Boolean promptEditTask(final java.awt.Component owner, final CalendarAppointment appt) {
		final ReminderCalendarBridge.OccurrenceRef ref = asRef(appt);
		if (ref == null) {
			return Boolean.FALSE;
		}
		return ReminderCalendarBridge.promptAndUpdateReminderTask(owner, ref);
	}

	static boolean createTask(final String title, final long startMs, final long endMs) {
		NodeModel parent = null;
		try {
			parent = Controller.getCurrentController().getSelection().getSelected();
		}
		catch (Exception e) {
		}
		if (parent == null) {
			try {
				parent = Controller.getCurrentController().getMap().getRootNode();
			}
			catch (Exception e) {
				return false;
			}
		}
		final int minutes = (int) Math.max(5L, (endMs - startMs) / 60000L);
		return ReminderCalendarBridge.createReminderTask(parent, title, startMs, minutes);
	}

	static List dayTaskLines(final List appointments, final Date day) {
		final List out = new ArrayList();
		if (appointments == null || day == null) {
			return out;
		}
		final long start = DayViewPanel.startOfDay(day).getTime();
		final long end = start + 24L * 60L * 60L * 1000L;
		for (int i = 0; i < appointments.size(); i++) {
			final CalendarAppointment appt = (CalendarAppointment) appointments.get(i);
			if (appt.startMillis() >= start && appt.startMillis() < end) {
				out.add(appt);
			}
		}
		return out;
	}

	private static String plain(final NodeModel node) {
		try {
			final String text = TextController.getController().getPlainTextContent(node);
			if (text != null) {
				return HtmlUtils.htmlToPlain(text).replaceAll("\\s+", " ").trim();
			}
		}
		catch (Exception e) {
		}
		return node == null || node.getText() == null ? "" : HtmlUtils.htmlToPlain(node.getText());
	}
}
