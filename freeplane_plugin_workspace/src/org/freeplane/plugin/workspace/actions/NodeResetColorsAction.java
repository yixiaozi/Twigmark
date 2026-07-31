package org.freeplane.plugin.workspace.actions;

import java.awt.event.ActionEvent;

import javax.swing.tree.TreePath;

import org.freeplane.plugin.workspace.components.menu.CheckEnableOnPopup;
import org.freeplane.plugin.workspace.features.colors.WorkspaceItemColorStore;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;

@CheckEnableOnPopup
public class NodeResetColorsAction extends AWorkspaceAction {

	public static final String KEY = "workspace.action.node.reset.color";
	private static final long serialVersionUID = 1L;

	public NodeResetColorsAction() {
		super(KEY);
	}

	public void setEnabledFor(final AWorkspaceTreeNode node, final TreePath[] selectedPaths) {
		if (node == null) {
			setEnabled(false);
			return;
		}
		final String key = WorkspaceItemColorStore.keyFor(node);
		setEnabled(WorkspaceItemColorStore.getInstance().hasAnyColor(key));
	}

	public void actionPerformed(final ActionEvent e) {
		final AWorkspaceTreeNode node = getNodeFromActionEvent(e);
		if (node == null) {
			return;
		}
		WorkspaceItemColorStore.getInstance().clearAllColors(WorkspaceItemColorStore.keyFor(node));
	}
}
