package org.freeplane.plugin.workspace.features.nodepins;

import java.util.LinkedHashSet;
import java.util.Set;

import org.freeplane.features.map.NodeModel;
import org.freeplane.core.undo.IActor;
import org.freeplane.features.mode.Controller;

public final class NodeDetailsTagService {

	private NodeDetailsTagService() {
	}

	public static Set getUserTags(final NodeModel node) {
		if (node == null) {
			return new LinkedHashSet();
		}
		return NodeDetailsTagUtils.parseUserTags(node.getText());
	}

	public static boolean isPinned(final NodeModel node) {
		if (node == null) {
			return false;
		}
		return NodeDetailsTagUtils.isPinnedInDetails(node.getText());
	}

	public static void setUserTags(final NodeModel node, final Set userTags) {
		writeTags(node, userTags, isPinned(node));
	}

	public static void togglePin(final NodeModel node) {
		writeTags(node, getUserTags(node), !isPinned(node));
	}

	public static void removeAllManagedTags(final NodeModel node) {
		writeTags(node, new LinkedHashSet(), false);
	}

	public static void removePinOnly(final NodeModel node) {
		writeTags(node, getUserTags(node), false);
	}

	private static void writeTags(final NodeModel node, final Set userTags, final boolean pinned) {
		if (node == null || node.getMap() == null) {
			return;
		}
		final String currentText = node.getText();
		final String textWithoutTags = NodeDetailsTagUtils.stripBracketTags(currentText);
		final LinkedHashSet allTags = new LinkedHashSet();
		if (userTags != null) {
			allTags.addAll(userTags);
		}
		if (pinned) {
			allTags.add(NodeDetailsTagUtils.PIN_TAG);
		}
		final String newText = NodeDetailsTagUtils.appendBracketTags(textWithoutTags, allTags);
		if (currentText.equals(newText)) {
			return;
		}
		if (Controller.getCurrentModeController() == null) {
			return;
		}
		final IActor actor = new IActor() {
			public void act() {
				node.setText(newText);
			}

			public String getDescription() {
				return "updateNodeTags";
			}

			public void undo() {
				node.setText(currentText);
			}
		};
		Controller.getCurrentModeController().execute(actor, node.getMap());
		NodePinsIndex.getInstance().updateFromNode(node);
	}
}