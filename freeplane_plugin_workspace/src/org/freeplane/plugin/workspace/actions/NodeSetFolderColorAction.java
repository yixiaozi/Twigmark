package org.freeplane.plugin.workspace.actions;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.JColorChooser;
import javax.swing.tree.TreePath;

import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.workspace.components.menu.CheckEnableOnPopup;
import org.freeplane.plugin.workspace.features.colors.WorkspaceItemColorStore;
import org.freeplane.plugin.workspace.io.IFileSystemRepresentation;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;
import org.freeplane.plugin.workspace.nodes.FolderFileNode;
import org.freeplane.plugin.workspace.nodes.FolderLinkNode;
import org.freeplane.plugin.workspace.nodes.FolderTypeMyFilesNode;
import org.freeplane.plugin.workspace.nodes.FolderVirtualNode;

@CheckEnableOnPopup
public class NodeSetFolderColorAction extends AWorkspaceAction {

	public static final String KEY = "workspace.action.node.set.folder.color";
	private static final long serialVersionUID = 1L;

	public NodeSetFolderColorAction() {
		super(KEY);
	}

	public void setEnabledFor(final AWorkspaceTreeNode node, final TreePath[] selectedPaths) {
		setEnabled(isFolderLike(node));
	}

	public static boolean isFolderLike(final AWorkspaceTreeNode node) {
		if (node == null) {
			return false;
		}
		if (node instanceof FolderFileNode || node instanceof FolderLinkNode
				|| node instanceof FolderVirtualNode || node instanceof FolderTypeMyFilesNode) {
			return true;
		}
		if (node instanceof IFileSystemRepresentation) {
			final File file = ((IFileSystemRepresentation) node).getFile();
			return file != null && file.isDirectory();
		}
		return false;
	}

	public void actionPerformed(final ActionEvent e) {
		final AWorkspaceTreeNode node = getNodeFromActionEvent(e);
		if (!isFolderLike(node)) {
			return;
		}
		final String key = WorkspaceItemColorStore.keyFor(node);
		final WorkspaceItemColorStore store = WorkspaceItemColorStore.getInstance();
		final Color current = store.getFolderColor(key);
		final Color chosen = JColorChooser.showDialog(UITools.getFrame(),
				TextUtils.getText(KEY + ".label"), current != null ? current : new Color(0xFFB74D));
		if (chosen != null) {
			store.setFolderColor(key, chosen);
		}
	}
}
