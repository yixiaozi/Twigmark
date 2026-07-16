package org.freeplane.view.swing.features.pomodoro;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Encode/decode session history stored in {@code POMODORO_LOG}.
 * Keeps the newest {@link #MAX_SESSIONS} records.
 */
public final class PomodoroLog {
	public static final int MAX_SESSIONS = 200;

	private PomodoroLog() {
	}

	public static List decode(final String raw) {
		final List out = new ArrayList();
		if (raw == null || raw.trim().length() == 0) {
			return out;
		}
		final String[] parts = raw.split(";");
		for (int i = 0; i < parts.length; i++) {
			final PomodoroSessionRecord rec = PomodoroSessionRecord.decode(parts[i].trim());
			if (rec != null) {
				out.add(rec);
			}
		}
		return out;
	}

	public static String encode(final List records) {
		if (records == null || records.isEmpty()) {
			return "";
		}
		final int from = Math.max(0, records.size() - MAX_SESSIONS);
		final StringBuilder sb = new StringBuilder();
		for (int i = from; i < records.size(); i++) {
			if (sb.length() > 0) {
				sb.append(';');
			}
			sb.append(((PomodoroSessionRecord) records.get(i)).encode());
		}
		return sb.toString();
	}

	public static String append(final String raw, final PomodoroSessionRecord record) {
		final List list = decode(raw);
		list.add(record);
		return encode(list);
	}

	public static long sumFocus(final List records) {
		long sum = 0L;
		if (records == null) {
			return 0L;
		}
		for (int i = 0; i < records.size(); i++) {
			sum += ((PomodoroSessionRecord) records.get(i)).focusMs;
		}
		return sum;
	}

	public static long sumFocusSince(final List records, final long sinceMs) {
		long sum = 0L;
		if (records == null) {
			return 0L;
		}
		for (int i = 0; i < records.size(); i++) {
			final PomodoroSessionRecord rec = (PomodoroSessionRecord) records.get(i);
			if (rec.endMs >= sinceMs) {
				sum += rec.focusMs;
			}
		}
		return sum;
	}

	public static long startOfToday() {
		final Calendar cal = Calendar.getInstance(TimeZone.getDefault(), Locale.CHINA);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTimeInMillis();
	}

	public static long startOfWeek() {
		final Calendar cal = Calendar.getInstance(TimeZone.getDefault(), Locale.CHINA);
		cal.setFirstDayOfWeek(Calendar.MONDAY);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
		return cal.getTimeInMillis();
	}

	public static String formatHistoryPreview(final String raw, final int maxLines) {
		final List records = decode(raw);
		if (records.isEmpty()) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		final int from = Math.max(0, records.size() - maxLines);
		for (int i = records.size() - 1; i >= from; i--) {
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(((PomodoroSessionRecord) records.get(i)).toDisplayLine());
		}
		return sb.toString();
	}
}
