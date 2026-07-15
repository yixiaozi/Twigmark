package org.docear.plugin.core.todoist;

import java.io.File;

final class TodoistReminderRecord {
	final File file;
	final String nodeId;
	final String nodeText;
	final long remindAt;
	final int period;
	final String periodUnit;
	final boolean recurring;
	/** Duration in minutes ({@code TASKTIME}). */
	final int durationMinutes;
	/** Urgency ({@code JINJI}); not task level. */
	final int jinji;
	/** DocearReminder cycle type: onetime/hour/day/week/month/year/eb. */
	final String remindType;
	/** Weekday codes 1=Mon … 7=Sun for weekly cycles ({@code RWEEKS}). */
	final String weekDays;

	TodoistReminderRecord(File file, String nodeId, String nodeText, long remindAt, int period, String periodUnit,
			boolean recurring) {
		this(file, nodeId, nodeText, remindAt, period, periodUnit, recurring, 0, 0, recurring ? "day" : "onetime", "");
	}

	TodoistReminderRecord(File file, String nodeId, String nodeText, long remindAt, int period, String periodUnit,
			boolean recurring, int durationMinutes, int jinji) {
		this(file, nodeId, nodeText, remindAt, period, periodUnit, recurring, durationMinutes, jinji,
				recurring ? unitToRemindType(periodUnit) : "onetime", "");
	}

	TodoistReminderRecord(File file, String nodeId, String nodeText, long remindAt, int period, String periodUnit,
			boolean recurring, int durationMinutes, int jinji, String remindType, String weekDays) {
		this.file = file;
		this.nodeId = nodeId;
		this.nodeText = nodeText;
		this.remindAt = remindAt;
		this.period = period;
		this.periodUnit = periodUnit == null ? "DAY" : periodUnit;
		this.recurring = recurring;
		this.durationMinutes = durationMinutes < 0 ? 0 : durationMinutes;
		this.jinji = jinji;
		this.remindType = remindType == null || remindType.length() == 0 ? (recurring ? "day" : "onetime") : remindType;
		this.weekDays = weekDays == null ? "" : weekDays;
	}

	TodoistCycleMapper.Cycle cycle() {
		return new TodoistCycleMapper.Cycle(remindType, period, weekDays);
	}

	String syncKey() {
		return TodoistSyncKeys.syncKey(file, nodeId);
	}

	String identityKey() {
		return TodoistSyncKeys.identityKey(file, nodeId);
	}

	private static String unitToRemindType(String periodUnit) {
		if (periodUnit == null) {
			return "day";
		}
		final String u = periodUnit.toUpperCase();
		if ("HOUR".equals(u)) {
			return "hour";
		}
		if ("WEEK".equals(u)) {
			return "week";
		}
		if ("MONTH".equals(u)) {
			return "month";
		}
		if ("YEAR".equals(u)) {
			return "year";
		}
		return "day";
	}
}
