package org.docear.plugin.mermaid;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.factory.MindIconFactory;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.mindmapmode.MTextController;

/** Syncs ```todo fence checkbox state with hourglass / button_ok icons. */
final class TodoNodeSync {

	private static final String TODO_ICON = "hourglass";
	private static final String DONE_ICON = "button_ok";

	private TodoNodeSync() {
	}

	static void applyAfterToggle(final NodeModel node, final String newFenceBody) {
		if (node == null) {
			return;
		}
		try {
			final String updated = replaceFenceBody(node.getUserObject().toString(), newFenceBody);
			final MTextController textController = (MTextController) TextController.getController();
			textController.setNodeText(node, updated);

			final List items = TodoChecklistRenderer.parseItems(newFenceBody);
			final boolean allDone = TodoChecklistRenderer.allDone(items);
			final boolean anyOpen = TodoChecklistRenderer.anyOpen(items);
			final MIconController icons = (MIconController) IconController.getController();
			removeIconByName(node, TODO_ICON);
			removeIconByName(node, DONE_ICON);
			if (allDone && !items.isEmpty()) {
				icons.addIcon(node, MindIconFactory.create(DONE_ICON));
			}
			else if (anyOpen) {
				icons.addIcon(node, MindIconFactory.create(TODO_ICON));
			}
			Controller.getCurrentModeController().getMapController().nodeChanged(node);
		}
		catch (Throwable t) {
			LogUtils.warn("Todo sync failed: " + t.getMessage());
		}
	}

	static String replaceFenceBody(final String nodeText, final String newBody) {
		final String text = nodeText != null ? nodeText : "";
		final int start = text.indexOf("```");
		if (start < 0) {
			return "```todo\n" + newBody + "\n```";
		}
		final int lineEnd = text.indexOf('\n', start);
		if (lineEnd < 0) {
			return text;
		}
		final int close = text.lastIndexOf("```");
		if (close <= lineEnd) {
			return text.substring(0, lineEnd + 1) + newBody + "\n```";
		}
		return text.substring(0, lineEnd + 1) + newBody + "\n" + text.substring(close);
	}

	private static void removeIconByName(final NodeModel node, final String iconName) {
		final Collection icons = IconController.getController().getIcons(node);
		if (icons == null) {
			return;
		}
		final MIconController iconController = (MIconController) IconController.getController();
		int position = 0;
		for (final Iterator it = icons.iterator(); it.hasNext();) {
			final MindIcon icon = (MindIcon) it.next();
			if (icon != null && iconName.equals(icon.getName())) {
				iconController.removeIcon(node, position);
				return;
			}
			position++;
		}
	}
}
