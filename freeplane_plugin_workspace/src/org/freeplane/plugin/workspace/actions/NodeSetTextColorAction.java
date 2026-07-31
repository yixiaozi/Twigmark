package org.freeplane.plugin.workspace.actions;

import java.awt.Color;
import java.awt.event.ActionEvent;

import javax.swing.JColorChooser;

import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.workspace.components.menu.CheckEnableOnPopup;
import org.freeplane.plugin.workspace.features.colors.WorkspaceItemColorStore;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;

@CheckEnableOnPopup
public class NodeSetTextColorAction extends AWorkspaceAction {

	public static final String KEY = "workspace.action.node.set.text.color";
	private static final long serialVersionUID = 1L;

	public NodeSetTextColorAction() {
		super(KEY);
	}

	public void setEnabledFor(final AWorkspaceTreeNode node, final javax.swing.tree.TreePath[] selectedPaths) {
		setEnabled(node != null);
	}

	public void actionPerformed(final ActionEvent e) {
		final AWorkspaceTreeNode node = getNodeFromActionEvent(e);
		if (node == null) {
			return;
		}
		final String key = WorkspaceItemColorStore.keyFor(node);
		final WorkspaceItemColorStore store = WorkspaceItemColorStore.getInstance();
		final Color current = store.getTextColor(key);
		final Color chosen = JColorChooser.showDialog(UITools.getFrame(),
				TextUtils.getText(KEY + ".label"), current != null ? current : Color.DARK_GRAY);
		if (chosen != null) {
			store.setTextColor(key, chosen);
		}
	}
}
