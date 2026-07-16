package org.freeplane.view.swing.features.pomodoro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;

/**
 * One focus segment for the "today" timeline view (completed or live).
 */
final class PomodoroTodayEntry {
	final NodeModel node;
	final long startMs;
	final long endMs;
	final long focusMs;
	final boolean live;
	final String label;

	PomodoroTodayEntry(final NodeModel node, final long startMs, final long endMs, final long focusMs,
			final boolean live) {
		this.node = node;
		this.startMs = startMs;
		this.endMs = endMs;
		this.focusMs = focusMs;
		this.live = live;
		this.label = buildLabel(node, startMs, endMs, focusMs, live);
	}

	private static String buildLabel(final NodeModel node, final long startMs, final long endMs, final long focusMs,
			final boolean live) {
		final String start = formatHm(startMs);
		final String end = live ? "进行中" : formatHm(endMs);
		final String mark = live ? "▶ " : "";
		return mark + start + " → " + end + "  " + PomodoroFormatter.formatDuration(focusMs) + "  ·  " + plain(node);
	}

	private static String formatHm(final long millis) {
		final java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA);
		fmt.setTimeZone(java.util.TimeZone.getDefault());
		return fmt.format(new java.util.Date(millis));
	}

	private static String plain(final NodeModel node) {
		try {
			final String text = TextController.getController().getPlainTextContent(node);
			if (text != null) {
				return HtmlUtils.htmlToPlain(text).replaceAll("\\s+", " ").trim();
			}
		}
		catch (Exception e) {
		}
		return node.getText() == null ? "" : HtmlUtils.htmlToPlain(node.getText());
	}

	public String toString() {
		return label;
	}

	/** Collect today's completed + live segments from enabled nodes, sorted by start ascending. */
	static List collect(final List nodes, final long now) {
		final List out = new ArrayList();
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
				if (rec.endMs >= todayStart || rec.startMs >= todayStart) {
					out.add(new PomodoroTodayEntry(node, Math.max(rec.startMs, todayStart), rec.endMs, rec.focusMs,
							false));
				}
			}
			final long liveMs = ext.liveSegmentMs(now);
			if (liveMs > 0) {
				final long anchor = ext.getSessionAt() > 0 ? ext.getSessionAt() : ext.getStartedAt();
				if (anchor >= todayStart) {
					out.add(new PomodoroTodayEntry(node, anchor, now, liveMs, true));
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
}
