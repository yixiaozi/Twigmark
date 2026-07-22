package org.freeplane.view.swing.features.pomodoro;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * One completed focus segment (start → end). Pause spans are stored explicitly
 * when available; otherwise pause duration falls back to wall − focus.
 */
public final class PomodoroSessionRecord {
	public final long startMs;
	public final long endMs;
	public final long focusMs;
	/** Completed pause intervals inside this session; never null. */
	public final List pauseIntervals;

	public PomodoroSessionRecord(final long startMs, final long endMs, final long focusMs) {
		this(startMs, endMs, focusMs, Collections.EMPTY_LIST);
	}

	public PomodoroSessionRecord(final long startMs, final long endMs, final long focusMs,
			final List pauseIntervals) {
		this.startMs = startMs;
		this.endMs = endMs;
		this.focusMs = Math.max(0L, focusMs);
		this.pauseIntervals = PomodoroPauseInterval.copyOf(pauseIntervals);
	}

	public long pauseMs() {
		if (!pauseIntervals.isEmpty()) {
			return PomodoroPauseInterval.sumMs(pauseIntervals);
		}
		final long span = Math.max(0L, endMs - startMs);
		return Math.max(0L, span - focusMs);
	}

	/** Compact wire format: start-end:focus or start-end:focus@p1s-p1e,p2s-p2e */
	String encode() {
		final String base = startMs + "-" + endMs + ":" + focusMs;
		if (pauseIntervals.isEmpty()) {
			return base;
		}
		return base + "@" + PomodoroPauseInterval.encodeList(pauseIntervals);
	}

	static PomodoroSessionRecord decode(final String token) {
		if (token == null || token.length() == 0) {
			return null;
		}
		try {
			final int at = token.indexOf('@');
			final String head = at >= 0 ? token.substring(0, at) : token;
			final String pauseRaw = at >= 0 ? token.substring(at + 1) : "";
			final int dash = head.indexOf('-');
			final int colon = head.indexOf(':');
			if (dash <= 0 || colon <= dash) {
				return null;
			}
			final long start = Long.parseLong(head.substring(0, dash));
			final long end = Long.parseLong(head.substring(dash + 1, colon));
			final long focus = Long.parseLong(head.substring(colon + 1));
			if (start <= 0 || end < start || focus < 0) {
				return null;
			}
			return new PomodoroSessionRecord(start, end, focus, PomodoroPauseInterval.decodeList(pauseRaw));
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	public String toDisplayLine() {
		final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
		fmt.setTimeZone(TimeZone.getDefault());
		final StringBuilder sb = new StringBuilder();
		sb.append(fmt.format(new Date(startMs))).append(" → ").append(fmt.format(new Date(endMs)));
		sb.append("  ").append(PomodoroFormatter.formatDuration(focusMs));
		final long pause = pauseMs();
		if (pause > 0) {
			// Pause details always last so the focus duration column stays readable.
			sb.append("  ·  暂停");
			final String ranges = PomodoroPauseInterval.formatRanges(pauseIntervals);
			if (ranges.length() > 0) {
				sb.append(' ').append(ranges);
			}
			sb.append('（').append(PomodoroFormatter.formatDuration(pause)).append('）');
		}
		return sb.toString();
	}
}
