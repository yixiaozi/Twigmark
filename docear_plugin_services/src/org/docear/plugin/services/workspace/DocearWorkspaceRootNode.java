package org.docear.plugin.services.workspace;

import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.workspace.actions.NodeRefreshAction;
import org.freeplane.plugin.workspace.components.menu.WorkspacePopupMenu;
import org.freeplane.plugin.workspace.components.menu.WorkspacePopupMenuBuilder;
import org.freeplane.plugin.workspace.nodes.WorkspaceRootNode;

/**
 * Workspace root context menu without cloud-user / Docear-service actions.
 */
public class DocearWorkspaceRootNode extends WorkspaceRootNode {

	private static final long serialVersionUID = 4058474904352649840L;
	private static WorkspacePopupMenu popupMenu;

	public String getName() {
		return TextUtils.getText("docear.node.root.default");
	}

	public void initializePopup() {
		if (popupMenu == null) {
			popupMenu = new WorkspacePopupMenu();
			WorkspacePopupMenuBuilder.addActions(popupMenu, new String[] {
					NodeRefreshAction.KEY
			});
		}
	}

	public WorkspacePopupMenu getContextMenu() {
		if (popupMenu == null) {
			initializePopup();
		}
		return popupMenu;
	}
}
