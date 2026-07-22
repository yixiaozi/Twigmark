package org.freeplane.plugin.workspace.features.mapactivity;

/**
 * Time window for pomodoro rows in the map-activity overlay.
 */
public enum PomodoroRange {
	TODAY, WEEK, ALL;

	public long sinceMs(final long now) {
		if (this == TODAY) {
			return org.freeplane.view.swing.features.pomodoro.PomodoroLog.startOfToday();
		}
		if (this == WEEK) {
			return org.freeplane.view.swing.features.pomodoro.PomodoroLog.startOfWeek();
		}
		return 0L;
	}

	public String label() {
		if (this == TODAY) {
			return "今日";
		}
		if (this == WEEK) {
			return "本周";
		}
		return "全部";
	}

	public static PomodoroRange fromKey(final String key) {
		if ("week".equalsIgnoreCase(key)) {
			return WEEK;
		}
		if ("all".equalsIgnoreCase(key)) {
			return ALL;
		}
		return TODAY;
	}

	public String toKey() {
		if (this == WEEK) {
			return "week";
		}
		if (this == ALL) {
			return "all";
		}
		return "today";
	}
}
