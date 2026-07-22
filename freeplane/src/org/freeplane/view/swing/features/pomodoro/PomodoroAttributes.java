package org.freeplane.view.swing.features.pomodoro;

import org.freeplane.core.undo.IActor;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

/**
 * XML attribute names and undoable read/write for pomodoro node state.
 */
public final class PomodoroAttributes {
	public static final String POMODORO = "POMODORO";
	public static final String POMODORO_MS = "POMODORO_MS";
	public static final String POMODORO_ACTIVE_MS = "POMODORO_ACTIVE_MS";
	public static final String POMODORO_STATE = "POMODORO_STATE";
	public static final String POMODORO_STARTED_AT = "POMODORO_STARTED_AT";
	public static final String POMODORO_SESSION_AT = "POMODORO_SESSION_AT";
	public static final String POMODORO_PAUSED_AT = "POMODORO_PAUSED_AT";
	public static final String POMODORO_SESSION_PAUSES = "POMODORO_SESSION_PAUSES";
	public static final String POMODORO_LOG = "POMODORO_LOG";

	/** Visible AttributeModel names (Chinese, shown under the node). */
	public static final String ATTR_ENABLED = "番茄钟";
	public static final String ATTR_TOTAL = "番茄累计";
	public static final String ATTR_STATE = "番茄状态";
	public static final String ATTR_TODAY = "番茄今日";
	public static final String ATTR_COUNT = "番茄次数";
	public static final String ATTR_SUBTREE = "番茄含子树";

	private PomodoroAttributes() {
	}

	public static boolean isEnabled(final NodeModel node) {
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		return ext != null && ext.isEnabled();
	}

	public static PomodoroExtension read(final NodeModel node) {
		return PomodoroExtension.getExtension(node);
	}

	public static void write(final NodeModel node, final PomodoroExtension desired) {
		if (node == null || desired == null) {
			return;
		}
		final PomodoroExtension before = copyOrNull(PomodoroExtension.getExtension(node));
		final PomodoroExtension after = desired.copy();
		final MapController mapController = Controller.getCurrentModeController().getMapController();
		Controller.getCurrentModeController().execute(new IActor() {
			public void act() {
				applyToNode(node, after);
				mapController.nodeChanged(node, PomodoroExtension.class, before, after);
			}

			public String getDescription() {
				return "pomodoro";
			}

			public void undo() {
				if (before == null || before.isEmpty()) {
					final PomodoroExtension existing = PomodoroExtension.getExtension(node);
					if (existing != null) {
						node.removeExtension(existing);
					}
				}
				else {
					PomodoroExtension.getOrCreateExtension(node).apply(before);
				}
				mapController.nodeChanged(node, PomodoroExtension.class, after, before);
			}
		}, node.getMap());
		// Never mirror into the visible Attribute table — data stays in hidden XML attrs only.
		PomodoroVisibleAttributes.clear(node);
	}

	public static void writeSilent(final NodeModel node, final PomodoroExtension desired) {
		if (node == null || desired == null) {
			return;
		}
		final PomodoroExtension before = copyOrNull(PomodoroExtension.getExtension(node));
		final PomodoroExtension after = desired.copy();
		applyToNode(node, after);
		PomodoroVisibleAttributes.clear(node);
		try {
			Controller.getCurrentModeController().getMapController()
					.nodeChanged(node, PomodoroExtension.class, before, after);
		}
		catch (Exception e) {
		}
	}

	static void applyToNode(final NodeModel node, final PomodoroExtension value) {
		if (value == null || value.isEmpty()) {
			final PomodoroExtension existing = PomodoroExtension.getExtension(node);
			if (existing != null) {
				node.removeExtension(existing);
			}
			return;
		}
		PomodoroExtension.getOrCreateExtension(node).apply(value);
	}

	public static void setEnabled(final NodeModel node, final boolean enabled) {
		final PomodoroExtension next = PomodoroExtension.getExtension(node);
		final PomodoroExtension desired = next == null ? new PomodoroExtension() : next.copy();
		desired.setEnabled(enabled);
		if (!enabled) {
			if (PomodoroExtension.STATE_RUNNING.equals(desired.getState())) {
				desired.setActiveMs(desired.getActiveMs()
						+ Math.max(0L, System.currentTimeMillis() - desired.getStartedAt()));
			}
			desired.setState(PomodoroExtension.STATE_IDLE);
			desired.setStartedAt(0);
			desired.setActiveMs(0);
			desired.setSessionAt(0);
			desired.setPausedAt(0);
			desired.setSessionPauses("");
		}
		write(node, desired);
	}

	public static void toggleEnabled(final NodeModel node) {
		setEnabled(node, !isEnabled(node));
	}

	private static PomodoroExtension copyOrNull(final PomodoroExtension source) {
		return source == null ? null : source.copy();
	}

	static long parseLong(final String value, final long defaultValue) {
		if (value == null || value.trim().length() == 0) {
			return defaultValue;
		}
		try {
			return Long.parseLong(value.trim());
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	static boolean parseBoolean(final String value) {
		if (value == null) {
			return false;
		}
		final String v = value.trim();
		return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
	}

	public static String stateLabel(final String state) {
		if (PomodoroExtension.STATE_RUNNING.equals(state)) {
			return "进行中";
		}
		if (PomodoroExtension.STATE_PAUSED.equals(state)) {
			return "已暂停";
		}
		return "空闲";
	}
}
