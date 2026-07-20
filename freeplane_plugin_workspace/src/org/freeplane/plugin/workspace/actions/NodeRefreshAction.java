package org.freeplane.plugin.workspace.actions;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;

import javax.swing.JTree;

import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.components.IWorkspaceView;
import org.freeplane.plugin.workspace.components.menu.WorkspacePopupMenu;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;

public class NodeRefreshAction extends AWorkspaceAction {

	public static final String KEY = "workspace.action.node.refresh";
	private static final long serialVersionUID = 1L;

	public NodeRefreshAction() {
		super(KEY);
	}

	public void actionPerformed(final ActionEvent e) {
		final AWorkspaceTreeNode blankRoot = resolveBlankAreaRoot(e);
		if (blankRoot != null) {
			blankRoot.refresh();
		}
		else {
			final AWorkspaceTreeNode[] targetNodes = getSelectedNodes(e);
			if (targetNodes == null || targetNodes.length == 0) {
				final AWorkspaceTreeNode root = WorkspaceController.getCurrentModel().getRoot();
				if (root != null) {
					root.refresh();
				}
			}
			else {
				for (AWorkspaceTreeNode targetNode : targetNodes) {
					if (targetNode == null) {
						targetNode = WorkspaceController.getCurrentModel().getRoot();
					}
					if (targetNode != null) {
						targetNode.refresh();
					}
				}
			}
		}
		final IWorkspaceView view = WorkspaceController.getCurrentModeExtension().getView();
		if (view != null) {
			view.refreshView();
		}
	}

	/**
	 * Blank-area context menus belong to the workspace root, but the tree may still
	 * keep a previous selection. Prefer the root so refresh rescans the whole library.
	 */
	private AWorkspaceTreeNode resolveBlankAreaRoot(final ActionEvent e) {
		if (e.getSource() instanceof JTree) {
			return null;
		}
		final WorkspacePopupMenu pop = getRootPopupMenu((Component) e.getSource());
		if (pop == null || !(pop.getInvoker() instanceof JTree)) {
			return null;
		}
		final Point loc = pop.getInvokerLocation();
		if (loc == null) {
			return null;
		}
		final JTree tree = (JTree) pop.getInvoker();
		if (tree.getPathForLocation(loc.x, loc.y) != null) {
			return null;
		}
		return WorkspaceController.getCurrentModel().getRoot();
	}
}
