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

	TodoistReminderRecord(File file, String nodeId, String nodeText, long remindAt, int period, String periodUnit,
			boolean recurring) {
		this(file, nodeId, nodeText, remindAt, period, periodUnit, recurring, 0, 0);
	}

	TodoistReminderRecord(File file, String nodeId, String nodeText, long remindAt, int period, String periodUnit,
			boolean recurring, int durationMinutes, int jinji) {
		this.file = file;
		this.nodeId = nodeId;
		this.nodeText = nodeText;
		this.remindAt = remindAt;
		this.period = period;
		this.periodUnit = periodUnit == null ? "DAY" : periodUnit;
		this.recurring = recurring;
		this.durationMinutes = durationMinutes < 0 ? 0 : durationMinutes;
		this.jinji = jinji;
	}

	String syncKey() {
		return TodoistSyncKeys.syncKey(file, nodeId);
	}

	String identityKey() {
		return TodoistSyncKeys.identityKey(file, nodeId);
	}
}
