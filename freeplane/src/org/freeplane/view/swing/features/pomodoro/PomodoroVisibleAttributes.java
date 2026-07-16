package org.freeplane.view.swing.features.pomodoro;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.attribute.mindmapmode.MAttributeController;
import org.freeplane.features.map.NodeModel;

/**
 * Clears leftover Chinese Attribute-table rows previously written by pomodoro.
 * Pomodoro data stays in hidden XML node attributes only (not shown on the map).
 */
final class PomodoroVisibleAttributes {
	private PomodoroVisibleAttributes() {
	}

	static void clear(final NodeModel node) {
		if (node == null) {
			return;
		}
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
}
