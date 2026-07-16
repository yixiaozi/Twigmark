package org.freeplane.view.swing.features.pomodoro;

/**
 * Formats focus durations for node labels and UI.
 */
public final class PomodoroFormatter {
	private PomodoroFormatter() {
	}

	public static String formatDuration(final long millis) {
		if (millis <= 0) {
			return "0m";
		}
		long totalSeconds = millis / 1000L;
		final long hours = totalSeconds / 3600L;
		totalSeconds %= 3600L;
		final long minutes = totalSeconds / 60L;
		final long seconds = totalSeconds % 60L;
		if (hours > 0) {
			if (minutes > 0) {
				return hours + "h" + minutes + "m";
			}
			return hours + "h";
		}
		if (minutes > 0) {
			if (seconds > 0 && minutes < 10) {
				return minutes + "m" + pad2((int) seconds) + "s";
			}
			return minutes + "m";
		}
		return seconds + "s";
	}

	public static String formatClock(final long millis) {
		long totalSeconds = Math.max(0L, millis) / 1000L;
		final long hours = totalSeconds / 3600L;
		totalSeconds %= 3600L;
		final long minutes = totalSeconds / 60L;
		final long seconds = totalSeconds % 60L;
		if (hours > 0) {
			return hours + ":" + pad2((int) minutes) + ":" + pad2((int) seconds);
		}
		return pad2((int) minutes) + ":" + pad2((int) seconds);
	}

	private static String pad2(final int value) {
		return value < 10 ? "0" + value : Integer.toString(value);
	}
}
