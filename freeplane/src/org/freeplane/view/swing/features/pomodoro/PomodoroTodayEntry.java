package org.freeplane.view.swing.features.pomodoro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;

/**
 * One focus segment for the day timeline view (completed or live).
 * Display order: time range · focus duration · title · pause (at end).
 */
final class PomodoroTodayEntry {
	final NodeModel node;
	final long startMs;
	final long endMs;
	final long focusMs;
	final boolean live;
	/** Index in {@link PomodoroLog#decode(String)}; {@code -1} for live segment. */
	final int recordIndex;
	final List pauseIntervals;
	final String timeText;
	final String durationText;
	final String titleText;
	final String pauseText;
	final String label;

	PomodoroTodayEntry(final NodeModel node, final long startMs, final long endMs, final long focusMs,
			final boolean live, final int recordIndex) {
		this(node, startMs, endMs, focusMs, live, recordIndex, Collections.EMPTY_LIST);
	}

	PomodoroTodayEntry(final NodeModel node, final long startMs, final long endMs, final long focusMs,
			final boolean live, final int recordIndex, final List pauseIntervals) {
		this.node = node;
		this.startMs = startMs;
		this.endMs = endMs;
		this.focusMs = focusMs;
		this.live = live;
		this.recordIndex = recordIndex;
		this.pauseIntervals = PomodoroPauseInterval.copyOf(pauseIntervals);
		this.timeText = formatTimeRange(startMs, endMs, live);
		this.durationText = PomodoroFormatter.formatDuration(focusMs);
		this.titleText = plain(node);
		this.pauseText = formatPauseTail(this.pauseIntervals, startMs, endMs, focusMs);
		this.label = buildPlainLabel(timeText, durationText, titleText, pauseText);
	}

	private static String buildPlainLabel(final String time, final String duration, final String title,
			final String pause) {
		final StringBuilder sb = new StringBuilder();
		sb.append(time).append("  ").append(duration).append("  ").append(title);
		if (pause != null && pause.length() > 0) {
			sb.append("  ").append(pause);
		}
		return sb.toString();
	}

	private static String formatTimeRange(final long startMs, final long endMs, final boolean live) {
		final String start = formatHm(startMs);
		final String end = live ? "进行中" : formatHm(endMs);
		return start + " -> " + end;
	}

	private static String formatPauseTail(final List pauseIntervals, final long startMs, final long endMs,
			final long focusMs) {
		final long pauseMs;
		final String ranges;
		if (pauseIntervals != null && !pauseIntervals.isEmpty()) {
			pauseMs = PomodoroPauseInterval.sumMs(pauseIntervals);
			ranges = PomodoroPauseInterval.formatRanges(pauseIntervals, 2);
		}
		else {
			pauseMs = Math.max(0L, endMs - startMs - focusMs);
			ranges = "";
		}
		if (pauseMs < 1000L) {
			return "";
		}
		final StringBuilder sb = new StringBuilder("暂停");
		if (ranges.length() > 0) {
			sb.append(' ').append(ranges);
		}
		sb.append('（').append(PomodoroFormatter.formatDuration(pauseMs)).append('）');
		return sb.toString();
	}

	private static String formatHm(final long millis) {
		final java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA);
		fmt.setTimeZone(java.util.TimeZone.getDefault());
		return fmt.format(new java.util.Date(millis));
	}

	private static String plain(final NodeModel node) {
		// Use stored node text (not TextController transformed content): the pomodoro
		// display chip (⏱ / Σ / ▶) is display-only and often renders as □ in list fonts.
		String text = node.getText() == null ? "" : HtmlUtils.htmlToPlain(node.getText());
		text = text.replaceAll("\\s+", " ").trim();
		text = stripPomodoroChip(text);
		return text;
	}

	/** Removes display-only pomodoro suffixes that may have leaked into stored/plain text. */
	static String stripPomodoroChip(final String raw) {
		if (raw == null || raw.length() == 0) {
			return "";
		}
		String t = raw;
		t = t.replaceAll("\\s*[⏱\\u23F1\\u23F0]\\S*(\\s*[·\\u00B7]\\s*Σ\\S*)?(\\s*[▶❚\\|]+)?\\s*$", "").trim();
		t = t.replaceAll("\\s*[·\\u00B7]\\s*Σ\\S*(\\s*[▶❚\\|]+)?\\s*$", "").trim();
		t = t.replaceAll("^[▶❚\\|\\s]+", "").trim();
		return t;
	}

	public String toString() {
		return label;
	}

	static List collect(final List nodes, final long now) {
		return collect(nodes, now, PomodoroLog.startOfToday());
	}

	static List collect(final List nodes, final long now, final long dayStartMs) {
		final List out = new ArrayList();
		final long dayStart = PomodoroLog.startOfDay(dayStartMs);
		final long dayEnd = dayStart + 24L * 60L * 60L * 1000L;
		final long todayStart = PomodoroLog.startOfToday();
		if (nodes == null) {
			return out;
		}
		for (int i = 0; i < nodes.size(); i++) {
			final NodeModel node = (NodeModel) nodes.get(i);
			final PomodoroExtension ext = PomodoroAttributes.read(node);
			if (ext == null || !ext.isEnabled()) {
				continue;
			}
			final List records = PomodoroLog.decode(ext.getLog());
			for (int r = 0; r < records.size(); r++) {
				final PomodoroSessionRecord rec = (PomodoroSessionRecord) records.get(r);
				if (rec.endMs > dayStart && rec.startMs < dayEnd) {
					final long clipStart = Math.max(rec.startMs, dayStart);
					final long clipEnd = Math.min(rec.endMs, dayEnd);
					out.add(new PomodoroTodayEntry(node, clipStart, clipEnd, rec.focusMs, false, r,
							rec.pauseIntervals));
				}
			}
			if (dayStart == todayStart) {
				final long liveMs = ext.liveSegmentMs(now);
				if (liveMs > 0) {
					final long anchor = ext.getSessionAt() > 0 ? ext.getSessionAt() : ext.getStartedAt();
					if (anchor >= dayStart && anchor < dayEnd) {
						out.add(new PomodoroTodayEntry(node, anchor, now, liveMs, true, -1,
								livePauseIntervals(ext, now)));
					}
				}
			}
		}
		Collections.sort(out, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long sa = ((PomodoroTodayEntry) a).startMs;
				final long sb = ((PomodoroTodayEntry) b).startMs;
				return sa < sb ? -1 : (sa > sb ? 1 : 0);
			}
		});
		return out;
	}

	private static List livePauseIntervals(final PomodoroExtension ext, final long now) {
		final List pauses = new ArrayList(PomodoroPauseInterval.decodeList(ext.getSessionPauses()));
		if (PomodoroExtension.STATE_PAUSED.equals(ext.getState()) && ext.getPausedAt() > 0 && now > ext.getPausedAt()) {
			pauses.add(new PomodoroPauseInterval(ext.getPausedAt(), now));
		}
		return pauses;
	}
}
