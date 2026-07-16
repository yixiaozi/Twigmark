package org.freeplane.view.swing.features.pomodoro;

import java.util.List;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.attribute.mindmapmode.MAttributeController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

/**
 * Mirrors pomodoro state into Freeplane's visible Attribute table (Chinese names).
 */
final class PomodoroVisibleAttributes {
	private PomodoroVisibleAttributes() {
	}

	static void sync(final NodeModel node, final PomodoroExtension ext) {
		syncInternal(node, ext, false);
	}

	static void syncSilent(final NodeModel node, final PomodoroExtension ext) {
		syncInternal(node, ext, true);
	}

	static void clear(final NodeModel node) {
		try {
			final MAttributeController attrs = MAttributeController.getController();
			if (attrs == null) {
				return;
			}
			attrs.editAttribute(node, PomodoroAttributes.ATTR_ENABLED, null);
			attrs.editAttribute(node, PomodoroAttributes.ATTR_TOTAL, null);
			attrs.editAttribute(node, PomodoroAttributes.ATTR_STATE, null);
			attrs.editAttribute(node, PomodoroAttributes.ATTR_TODAY, null);
			attrs.editAttribute(node, PomodoroAttributes.ATTR_COUNT, null);
			attrs.editAttribute(node, PomodoroAttributes.ATTR_SUBTREE, null);
		}
		catch (Exception e) {
			LogUtils.warn("Pomodoro: clear visible attributes failed", e);
		}
	}

	private static void syncInternal(final NodeModel node, final PomodoroExtension ext, final boolean silent) {
		if (node == null) {
			return;
		}
		try {
			if (ext == null || !ext.isEnabled()) {
				clear(node);
				return;
			}
			final long now = System.currentTimeMillis();
			final long self = ext.liveTotalMs(now);
			final long subtree = PomodoroTotals.subtreeMs(node, now);
			final List records = PomodoroLog.decode(ext.getLog());
			final long today = PomodoroLog.sumFocusSince(records, PomodoroLog.startOfToday()) + ext.liveSegmentMs(now);
			final String enabled = "开";
			final String total = PomodoroFormatter.formatDuration(self);
			final String state = PomodoroAttributes.stateLabel(ext.getState());
			final String todayStr = PomodoroFormatter.formatDuration(today);
			final String countStr = Integer.toString(ext.sessionCount());
			final String subtreeStr = subtree > self ? PomodoroFormatter.formatDuration(subtree) : total;
			if (silent) {
				setDirect(node, PomodoroAttributes.ATTR_ENABLED, enabled);
				setDirect(node, PomodoroAttributes.ATTR_TOTAL, total);
				setDirect(node, PomodoroAttributes.ATTR_STATE, state);
				setDirect(node, PomodoroAttributes.ATTR_TODAY, todayStr);
				setDirect(node, PomodoroAttributes.ATTR_COUNT, countStr);
				setDirect(node, PomodoroAttributes.ATTR_SUBTREE, subtreeStr);
			}
			else {
				final MAttributeController attrs = MAttributeController.getController();
				if (attrs == null) {
					return;
				}
				attrs.editAttribute(node, PomodoroAttributes.ATTR_ENABLED, enabled);
				attrs.editAttribute(node, PomodoroAttributes.ATTR_TOTAL, total);
				attrs.editAttribute(node, PomodoroAttributes.ATTR_STATE, state);
				attrs.editAttribute(node, PomodoroAttributes.ATTR_TODAY, todayStr);
				attrs.editAttribute(node, PomodoroAttributes.ATTR_COUNT, countStr);
				attrs.editAttribute(node, PomodoroAttributes.ATTR_SUBTREE, subtreeStr);
			}
		}
		catch (Exception e) {
			LogUtils.warn("Pomodoro: sync visible attributes failed", e);
		}
	}

	/** Bypass undo during shutdown / recover. */
	private static void setDirect(final NodeModel node, final String name, final String value) {
		try {
			final MAttributeController attrs = MAttributeController.getController();
			if (attrs == null) {
				return;
			}
			attrs.createAttributeTableModel(node);
			final NodeAttributeTableModel model = NodeAttributeTableModel.getModel(node);
			if (model == null) {
				return;
			}
			for (int i = 0; i < model.getRowCount(); i++) {
				final Attribute attribute = model.getAttribute(i);
				if (name.equals(attribute.getName())) {
					attribute.setValue(value);
					return;
				}
			}
			model.addRowNoUndo(new Attribute(name, value));
		}
		catch (Exception e) {
			try {
				// Fallback: still try undoable path if available.
				if (Controller.getCurrentModeController() != null) {
					MAttributeController.getController().editAttribute(node, name, value);
				}
			}
			catch (Exception ignored) {
			}
		}
	}
}
