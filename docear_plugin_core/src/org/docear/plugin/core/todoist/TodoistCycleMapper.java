package org.docear.plugin.core.todoist;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Maps DocearReminder cycle attributes ({@code REMINDERTYPE}/{@code RDAYS}/…) to Todoist
 * {@code due_string} values and back. ReminderExtension hook PERIOD/UNIT is often stuck at
 * {@code 1/DAY} (UI bug); the authoritative cycle lives on the node attributes.
 */
final class TodoistCycleMapper {
	private TodoistCycleMapper() {
	}

	static final class Cycle {
		final String remindType;
		final int interval;
		final String weekDays;
		final boolean recurring;

		Cycle(String remindType, int interval, String weekDays) {
			this.remindType = remindType == null || remindType.length() == 0 ? "onetime" : remindType.toLowerCase(Locale.ENGLISH);
			this.interval = interval <= 0 ? 1 : interval;
			this.weekDays = weekDays == null ? "" : weekDays;
			this.recurring = this.remindType.length() > 0 && !"onetime".equals(this.remindType);
		}

		static Cycle oneTime() {
			return new Cycle("onetime", 1, "");
		}

		String periodUnit() {
			if ("hour".equals(remindType)) {
				return "HOUR";
			}
			if ("day".equals(remindType)) {
				return "DAY";
			}
			if ("week".equals(remindType)) {
				return "WEEK";
			}
			if ("month".equals(remindType)) {
				return "MONTH";
			}
			if ("year".equals(remindType)) {
				return "YEAR";
			}
			return "DAY";
		}
	}

	static Cycle fromNodeAttributes(String remindType, int rHour, int rDays, int rWeek, int rMonth, int rYear,
			String weekDays) {
		if (remindType == null || remindType.length() == 0 || "onetime".equalsIgnoreCase(remindType)) {
			return Cycle.oneTime();
		}
		final String type = remindType.toLowerCase(Locale.ENGLISH);
		int interval = 1;
		if ("hour".equals(type)) {
			interval = rHour;
		}
		else if ("day".equals(type)) {
			interval = rDays;
		}
		else if ("week".equals(type)) {
			interval = rWeek;
		}
		else if ("month".equals(type)) {
			interval = rMonth;
		}
		else if ("year".equals(type)) {
			interval = rYear;
		}
		return new Cycle(type, interval, weekDays);
	}

	static Cycle fromPublicNodeReaders(String remindType, int interval, String weekDays) {
		return new Cycle(remindType, interval, weekDays);
	}

	/**
	 * Builds a Todoist natural-language due string that preserves local wall-clock time from
	 * {@code remindAtMillis} (JVM default timezone — same as Freeplane's reminder editor).
	 * Todoist interprets {@code due_string} times in the account timezone; on the user's machine
	 * these normally match. Returns null when Todoist cannot express the cycle (caller should
	 * fall back to {@code due_datetime}).
	 */
	static String toDueString(Cycle cycle, long remindAtMillis) {
		if (cycle == null || !cycle.recurring) {
			return null;
		}
		if ("eb".equals(cycle.remindType)) {
			return null;
		}
		final String timePart = formatLocalTimeSuffix(remindAtMillis);
		if ("hour".equals(cycle.remindType)) {
			if (cycle.interval == 1) {
				return "every hour";
			}
			return "every " + cycle.interval + " hours";
		}
		if ("day".equals(cycle.remindType)) {
			if (cycle.interval == 1) {
				return "every day" + timePart;
			}
			return "every " + cycle.interval + " days" + timePart;
		}
		if ("week".equals(cycle.remindType)) {
			final String days = weekDaysToEnglish(cycle.weekDays);
			if (days.length() > 0) {
				if (cycle.interval == 1) {
					return "every " + days + timePart;
				}
				return "every " + cycle.interval + " weeks on " + days + timePart;
			}
			if (cycle.interval == 1) {
				return "every week" + timePart;
			}
			return "every " + cycle.interval + " weeks" + timePart;
		}
		if ("month".equals(cycle.remindType)) {
			if (cycle.interval == 1) {
				return "every month" + timePart;
			}
			return "every " + cycle.interval + " months" + timePart;
		}
		if ("year".equals(cycle.remindType)) {
			if (cycle.interval == 1) {
				return "every year" + timePart;
			}
			return "every " + cycle.interval + " years" + timePart;
		}
		return null;
	}

	/** Parse Todoist due.string / is_recurring back into a Docear cycle. */
	static Cycle fromTodoistDue(String dueString, boolean isRecurring) {
		if (!isRecurring && (dueString == null || dueString.toLowerCase(Locale.ENGLISH).indexOf("every") < 0)) {
			return Cycle.oneTime();
		}
		final String s = dueString == null ? "" : dueString.toLowerCase(Locale.ENGLISH);
		if (s.indexOf("hour") >= 0) {
			return new Cycle("hour", extractEveryN(s, 1), "");
		}
		// Weekday names contain the substring "day" (monday…); resolve them before day/month/year.
		final String weekDays = englishWeekDaysToCodes(s);
		if (weekDays.length() > 0) {
			return new Cycle("week", extractEveryN(s, 1), weekDays);
		}
		if (s.indexOf("week") >= 0) {
			return new Cycle("week", extractEveryN(s, 1), "1");
		}
		if (s.indexOf("month") >= 0) {
			return new Cycle("month", extractEveryN(s, 1), "");
		}
		if (s.indexOf("year") >= 0) {
			return new Cycle("year", extractEveryN(s, 1), "");
		}
		if (isDayCyclePhrase(s)) {
			return new Cycle("day", extractEveryN(s, 1), "");
		}
		if (isRecurring) {
			return new Cycle("day", 1, "");
		}
		return Cycle.oneTime();
	}

	/**
	 * True when Todoist only says "recurring" without a usable due.string — not safe to overwrite
	 * local REMINDERTYPE / RWEEKS from a weak day/1 fallback.
	 */
	static boolean isWeakRecurringFallback(String dueString, boolean isRecurring) {
		if (!isRecurring) {
			return false;
		}
		if (dueString == null || dueString.trim().length() == 0) {
			return true;
		}
		final String s = dueString.toLowerCase(Locale.ENGLISH);
		return s.indexOf("every") < 0 && s.indexOf("daily") < 0;
	}

	/** True for "every day" / "every N days" / "daily" — not weekday names. */
	private static boolean isDayCyclePhrase(String dueLower) {
		if (dueLower.indexOf("daily") >= 0) {
			return true;
		}
		return java.util.regex.Pattern.compile("\\bevery\\s+(\\d+\\s+)?days?\\b").matcher(dueLower).find();
	}

	private static String formatLocalTimeSuffix(long remindAtMillis) {
		if (remindAtMillis <= 0) {
			return "";
		}
		final Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(remindAtMillis);
		final int hour = cal.get(Calendar.HOUR_OF_DAY);
		final int minute = cal.get(Calendar.MINUTE);
		if (hour == 0 && minute == 0) {
			return "";
		}
		return " at " + pad2(hour) + ":" + pad2(minute);
	}

	private static String pad2(int value) {
		return value < 10 ? "0" + value : Integer.toString(value);
	}

	private static int extractEveryN(String dueString, int defaultValue) {
		final java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("every\\s+(\\d+)").matcher(dueString);
		if (matcher.find()) {
			try {
				final int n = Integer.parseInt(matcher.group(1));
				return n > 0 ? n : defaultValue;
			}
			catch (NumberFormatException e) {
				return defaultValue;
			}
		}
		return defaultValue;
	}

	private static String weekDaysToEnglish(String weekDays) {
		if (weekDays == null || weekDays.length() == 0) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < weekDays.length(); i++) {
			final String name = weekDayName(weekDays.charAt(i));
			if (name.length() == 0) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(name);
		}
		return sb.toString();
	}

	private static String weekDayName(char code) {
		switch (code) {
			case '1':
				return "monday";
			case '2':
				return "tuesday";
			case '3':
				return "wednesday";
			case '4':
				return "thursday";
			case '5':
				return "friday";
			case '6':
				return "saturday";
			case '7':
				return "sunday";
			default:
				return "";
		}
	}

	private static String englishWeekDaysToCodes(String dueString) {
		final StringBuilder sb = new StringBuilder();
		appendIfContains(sb, dueString, "monday", '1');
		appendIfContains(sb, dueString, "tuesday", '2');
		appendIfContains(sb, dueString, "wednesday", '3');
		appendIfContains(sb, dueString, "thursday", '4');
		appendIfContains(sb, dueString, "friday", '5');
		appendIfContains(sb, dueString, "saturday", '6');
		appendIfContains(sb, dueString, "sunday", '7');
		return sb.toString();
	}

	private static void appendIfContains(StringBuilder sb, String haystack, String needle, char code) {
		if (haystack.indexOf(needle) >= 0 && sb.toString().indexOf(code) < 0) {
			sb.append(code);
		}
	}
}
