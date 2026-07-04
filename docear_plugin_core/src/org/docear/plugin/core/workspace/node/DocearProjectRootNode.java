package org.docear.plugin.core.workspace.node;

import javax.swing.tree.TreeNode;

import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;
import org.freeplane.plugin.workspace.model.IMyFilesTreeHoist;
import org.freeplane.plugin.workspace.nodes.FolderTypeMyFilesNode;
import org.freeplane.plugin.workspace.nodes.ProjectRootNode;

/**
 * Project root for customized Docear builds. Display hoisting (show only "My files"
 * contents in the workspace tree) is handled via {@link IMyFilesTreeHoist} in
 * {@link org.freeplane.plugin.workspace.components.TreeView}; the model tree keeps
 * the full node structure so load/save and tree events stay consistent.
 */
public class DocearProjectRootNode extends ProjectRootNode implements IMyFilesTreeHoist {

	private static final long serialVersionUID = 1L;

	private FolderTypeMyFilesNode findMyFilesNode() {
		for (int i = 0; i < getModelChildCount(); i++) {
			final AWorkspaceTreeNode child = getModelChildAt(i);
			if (child instanceof FolderTypeMyFilesNode) {
				return (FolderTypeMyFilesNode) child;
			}
		}
		return null;
	}

	public DocearProjectRootNode() {
		super();
	}

	@Override
	public int getDisplayChildCount() {
		final FolderTypeMyFilesNode myFiles = findMyFilesNode();
		if (myFiles != null) {
			return myFiles.getModelChildCount();
		}
		return getModelChildCount();
	}

	@Override
	public AWorkspaceTreeNode getDisplayChildAt(int childIndex) {
		final FolderTypeMyFilesNode myFiles = findMyFilesNode();
		if (myFiles != null) {
			return myFiles.getModelChildAt(childIndex);
		}
		return getModelChildAt(childIndex);
	}

	@Override
	public int getDisplayChildIndex(TreeNode node) {
		final FolderTypeMyFilesNode myFiles = findMyFilesNode();
		if (myFiles != null && node instanceof AWorkspaceTreeNode) {
			for (int i = 0; i < myFiles.getModelChildCount(); i++) {
				if (myFiles.getModelChildAt(i) == node) {
					return i;
				}
			}
			return -1;
		}
		for (int i = 0; i < getModelChildCount(); i++) {
			if (getModelChildAt(i) == node) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public AWorkspaceTreeNode clone() {
		DocearProjectRootNode node = new DocearProjectRootNode();
		return clone(node);
	}
}
