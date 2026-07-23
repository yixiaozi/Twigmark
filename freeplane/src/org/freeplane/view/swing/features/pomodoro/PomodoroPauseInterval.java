package org.freeplane.view.swing.features.pomodoro;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * One pause span inside a pomodoro session (wall-clock from → to).
 */
public final class PomodoroPauseInterval {
	public final long startMs;
	public final long endMs;

	public PomodoroPauseInterval(final long startMs, final long endMs) {
		this.startMs = startMs;
		this.endMs = endMs;
	}

	public long durationMs() {
		return Math.max(0L, endMs - startMs);
	}

	String encode() {
		return startMs + "-" + endMs;
	}

	static PomodoroPauseInterval decode(final String token) {
		if (token == null || token.length() == 0) {
			return null;
		}
		try {
			final int dash = token.indexOf('-');
			if (dash <= 0 || dash >= token.length() - 1) {
				return null;
			}
			final long start = Long.parseLong(token.substring(0, dash));
			final long end = Long.parseLong(token.substring(dash + 1));
			if (start <= 0 || end <= start) {
				return null;
			}
			return new PomodoroPauseInterval(start, end);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	public static List decodeList(final String raw) {
		final List out = new ArrayList();
		if (raw == null || raw.trim().length() == 0) {
			return out;
		}
		final String[] parts = raw.split(",");
		for (int i = 0; i < parts.length; i++) {
			final PomodoroPauseInterval interval = decode(parts[i].trim());
			if (interval != null) {
				out.add(interval);
			}
		}
		return out;
	}

	public static String encodeList(final List intervals) {
		if (intervals == null || intervals.isEmpty()) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < intervals.size(); i++) {
			final Object item = intervals.get(i);
			if (!(item instanceof PomodoroPauseInterval)) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(((PomodoroPauseInterval) item).encode());
		}
		return sb.toString();
	}

	public static String append(final String raw, final long startMs, final long endMs) {
		if (startMs <= 0 || endMs <= startMs) {
			return raw == null ? "" : raw;
		}
		final List list = decodeList(raw);
		list.add(new PomodoroPauseInterval(startMs, endMs));
		return encodeList(list);
	}

	/**
	 * Split a session wall span into contiguous <b>focus</b> segments by removing
	 * pause holes. Display-only: e.g. A 08:00–11:00 with pause 09:00–10:00 →
	 * [08:00–09:00], [10:00–11:00] so the day timeline can interleave B in between.
	 */
	public static List focusSegments(final long sessionStart, final long sessionEnd, final List pauses) {
		final List out = new ArrayList();
		if (sessionStart <= 0 || sessionEnd <= sessionStart) {
			return out;
		}
		final List sorted = copyOf(pauses);
		Collections.sort(sorted, new java.util.Comparator() {
			public int compare(final Object a, final Object b) {
				final long sa = ((PomodoroPauseInterval) a).startMs;
				final long sb = ((PomodoroPauseInterval) b).startMs;
				return sa < sb ? -1 : (sa > sb ? 1 : 0);
			}
		});
		long cursor = sessionStart;
		for (int i = 0; i < sorted.size(); i++) {
			final PomodoroPauseInterval pause = (PomodoroPauseInterval) sorted.get(i);
			if (pause.endMs <= cursor || pause.startMs >= sessionEnd) {
				continue;
			}
			final long pauseStart = Math.max(pause.startMs, sessionStart);
			final long pauseEnd = Math.min(pause.endMs, sessionEnd);
			if (pauseStart > cursor && pauseStart - cursor >= 1000L) {
				out.add(new PomodoroPauseInterval(cursor, pauseStart));
			}
			if (pauseEnd > cursor) {
				cursor = pauseEnd;
			}
		}
		if (sessionEnd - cursor >= 1000L) {
			out.add(new PomodoroPauseInterval(cursor, sessionEnd));
		}
		return out;
	}

	public static long sumMs(final List intervals) {
		long sum = 0L;
		if (intervals == null) {
			return 0L;
		}
		for (int i = 0; i < intervals.size(); i++) {
			final long d = ((PomodoroPauseInterval) intervals.get(i)).durationMs();
			if (d >= 1000L) {
				sum += d;
			}
		}
		return sum;
	}

	/** Copy list defensively; never returns null. */
	public static List copyOf(final List intervals) {
		if (intervals == null || intervals.isEmpty()) {
			return Collections.EMPTY_LIST;
		}
		final List out = new ArrayList(intervals.size());
		for (int i = 0; i < intervals.size(); i++) {
			final Object item = intervals.get(i);
			if (item instanceof PomodoroPauseInterval) {
				out.add(item);
			}
		}
		return out;
	}

	public String toHmRange() {
		return formatHm(startMs) + "→" + formatHm(endMs);
	}

	public static String formatRanges(final List intervals) {
		return formatRanges(intervals, 3);
	}

	/** Compact pause ranges for UI; skips zero-length spans; caps listed items. */
	public static String formatRanges(final List intervals, final int maxShown) {
		if (intervals == null || intervals.isEmpty()) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		int shown = 0;
		int skipped = 0;
		for (int i = 0; i < intervals.size(); i++) {
			final PomodoroPauseInterval interval = (PomodoroPauseInterval) intervals.get(i);
			if (interval.durationMs() < 1000L) {
				continue;
			}
			if (shown >= maxShown) {
				skipped++;
				continue;
			}
			if (shown > 0) {
				sb.append('、');
			}
			sb.append(interval.toHmRange());
			shown++;
		}
		if (skipped > 0) {
			sb.append(" 等").append(shown + skipped).append("段");
		}
		return sb.toString();
	}

	private static String formatHm(final long millis) {
		final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.CHINA);
		fmt.setTimeZone(TimeZone.getDefault());
		return fmt.format(new Date(millis));
	}
}
