package org.docear.plugin.core.todoist;

import java.io.File;

final class TodoistReminderRecord {
	final File file;
	final String nodeId;
	/** Not final: an open map replaces the text read from disk before the direction is chosen. */
	String nodeText;
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
	/**
	 * Todoist id already stored on the mind-map node. Set for tasks imported from Todoist.
	 * Sync must not create a second task or edit the original when this is set and the task
	 * lives outside the Docear project.
	 */
	String linkedTaskId;
	/** Node {@code MODIFIED} when this record was read. */
	long localModified;
	long syncedLocal;
	long syncedRemote;
	/** User cut or moved this node; the linked Todoist task must stay in its own project. */
	boolean userPlaced;

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
