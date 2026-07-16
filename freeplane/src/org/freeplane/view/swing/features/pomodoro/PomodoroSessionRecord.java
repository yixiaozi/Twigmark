package org.freeplane.view.swing.features.pomodoro;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * One completed focus segment (start → end, after pauses collapsed into focusMs).
 */
public final class PomodoroSessionRecord {
	public final long startMs;
	public final long endMs;
	public final long focusMs;

	public PomodoroSessionRecord(final long startMs, final long endMs, final long focusMs) {
		this.startMs = startMs;
		this.endMs = endMs;
		this.focusMs = Math.max(0L, focusMs);
	}

	public long pauseMs() {
		final long span = Math.max(0L, endMs - startMs);
		return Math.max(0L, span - focusMs);
	}

	/** Compact wire format: start-end:focus */
	String encode() {
		return startMs + "-" + endMs + ":" + focusMs;
	}

	static PomodoroSessionRecord decode(final String token) {
		if (token == null || token.length() == 0) {
			return null;
		}
		try {
			final int dash = token.indexOf('-');
			final int colon = token.indexOf(':');
			if (dash <= 0 || colon <= dash) {
				return null;
			}
			final long start = Long.parseLong(token.substring(0, dash));
			final long end = Long.parseLong(token.substring(dash + 1, colon));
			final long focus = Long.parseLong(token.substring(colon + 1));
			if (start <= 0 || end < start || focus < 0) {
				return null;
			}
			return new PomodoroSessionRecord(start, end, focus);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	public String toDisplayLine() {
		final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
		fmt.setTimeZone(TimeZone.getDefault());
		return fmt.format(new Date(startMs)) + " → " + fmt.format(new Date(endMs)) + "  "
				+ PomodoroFormatter.formatDuration(focusMs)
				+ (pauseMs() > 0 ? "（暂停 " + PomodoroFormatter.formatDuration(pauseMs()) + "）" : "");
	}
}
