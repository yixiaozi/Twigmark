package org.freeplane.view.swing.features.pomodoro;

import java.util.List;

import org.freeplane.features.map.NodeModel;

/**
 * Self + subtree focus totals. Only nodes with {@code POMODORO=true} contribute.
 */
public final class PomodoroTotals {
	private PomodoroTotals() {
	}

	public static long selfMs(final NodeModel node, final long now) {
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		if (ext == null || !ext.isEnabled()) {
			return 0L;
		}
		return ext.liveTotalMs(now);
	}

	public static long subtreeMs(final NodeModel node, final long now) {
		if (node == null) {
			return 0L;
		}
		long sum = selfMs(node, now);
		final List children = node.getChildren();
		if (children != null) {
			for (int i = 0; i < children.size(); i++) {
				sum += subtreeMs((NodeModel) children.get(i), now);
			}
		}
		return sum;
	}

	/** Display suffix when enabled: {@code ⏱ 12m · Σ1h5m} (Σ omitted when equal to self). */
	public static String formatInline(final NodeModel node, final long now) {
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		if (ext == null || !ext.isEnabled()) {
			return "";
		}
		final long self = ext.liveTotalMs(now);
		final long subtree = subtreeMs(node, now);
		final StringBuilder sb = new StringBuilder();
		sb.append("⏱ ").append(PomodoroFormatter.formatDuration(self));
		if (subtree > self) {
			sb.append(" · Σ").append(PomodoroFormatter.formatDuration(subtree));
		}
		final String state = ext.getState();
		if (PomodoroExtension.STATE_RUNNING.equals(state)) {
			sb.append(" ▶");
		}
		else if (PomodoroExtension.STATE_PAUSED.equals(state)) {
			sb.append(" ❚❚");
		}
		return sb.toString();
	}
}
