package org.freeplane.view.swing.features.pomodoro;

import org.freeplane.core.undo.IActor;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

/**
 * XML attribute names and undoable read/write for pomodoro node state.
 * Visible on {@code <node>} so Freeplane's attribute table can show them.
 */
public final class PomodoroAttributes {
	public static final String POMODORO = "POMODORO";
	public static final String POMODORO_MS = "POMODORO_MS";
	public static final String POMODORO_ACTIVE_MS = "POMODORO_ACTIVE_MS";
	public static final String POMODORO_STATE = "POMODORO_STATE";
	public static final String POMODORO_STARTED_AT = "POMODORO_STARTED_AT";

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
	}

	/** Persist without undo stack (pause-all-on-shutdown / recover). Still marks map dirty via nodeChanged. */
	public static void writeSilent(final NodeModel node, final PomodoroExtension desired) {
		if (node == null || desired == null) {
			return;
		}
		final PomodoroExtension before = copyOrNull(PomodoroExtension.getExtension(node));
		final PomodoroExtension after = desired.copy();
		applyToNode(node, after);
		try {
			Controller.getCurrentModeController().getMapController()
					.nodeChanged(node, PomodoroExtension.class, before, after);
		}
		catch (Exception e) {
			// Headless / shutdown paths may lack a mode controller.
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
			// Keep totals; clear live segment when turning off.
			if (PomodoroExtension.STATE_RUNNING.equals(desired.getState())) {
				desired.setActiveMs(desired.getActiveMs()
						+ Math.max(0L, System.currentTimeMillis() - desired.getStartedAt()));
			}
			desired.setState(PomodoroExtension.STATE_IDLE);
			desired.setStartedAt(0);
			desired.setActiveMs(0);
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
}
