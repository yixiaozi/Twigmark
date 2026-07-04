package org.freeplane.plugin.workspace.components;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;

import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;
import org.freeplane.plugin.workspace.model.WorkspaceModel;
import org.freeplane.plugin.workspace.model.WorkspaceModelEvent;
import org.freeplane.plugin.workspace.model.WorkspaceModelListener;
import org.freeplane.plugin.workspace.model.WorkspaceTreeModel;

public class ExpandedStateHandler implements TreeExpansionListener, WorkspaceModelListener {
	private Set<String> expandedNodeKeys = new LinkedHashSet<String>();
	private final TreeView treeView;

	public ExpandedStateHandler(final TreeView treeView) {
		this.treeView = treeView;
	}

	public void treeExpanded(TreeExpansionEvent event) {
		final AWorkspaceTreeNode node = (AWorkspaceTreeNode) event.getPath().getLastPathComponent();
		expandedNodeKeys.add(node.getKey());
	}

	public void treeCollapsed(TreeExpansionEvent event) {
		final AWorkspaceTreeNode node = (AWorkspaceTreeNode) event.getPath().getLastPathComponent();
		expandedNodeKeys.remove(node.getKey());
	}

	public void treeNodesChanged(TreeModelEvent e) {
	}

	public void treeNodesInserted(TreeModelEvent e) {
	}

	public void treeNodesRemoved(TreeModelEvent e) {
	}

	public void treeStructureChanged(TreeModelEvent e) {
		// Do not re-expand here: folder lazy-load fires structure changes on model paths,
		// while JTree uses hoisted display paths — expandPath(modelPath) blanks the tree.
	}

	public void projectAdded(WorkspaceModelEvent event) {
		String key = ((AWorkspaceTreeNode) event.getTreePath().getLastPathComponent()).getKey() + "/"
		        + event.getProject().getProjectID();
		expandedNodeKeys.add(key);
	}

	public void projectRemoved(WorkspaceModelEvent event) {
	}

	public void registerModel(WorkspaceModel model) {
		if (model == null) {
			return;
		}
		model.removeTreeModelListener(this);
		model.addWorldModelListener(this);
	}

	public void setExpandedStates(WorkspaceTreeModel targetModel, boolean cleanInvalidEntries) {
		Iterator<String> iter = expandedNodeKeys.iterator();
		try {
			while (iter.hasNext()) {
				AWorkspaceTreeNode node = targetModel.getNode(iter.next());
				if (node != null) {
					final TreePath displayPath = treeView.getDisplayTreePath(node);
					if (displayPath != null) {
						treeView.expandPath(displayPath);
					}
				}
				else if (cleanInvalidEntries) {
					iter.remove();
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn(
			        "Exception in org.freeplane.plugin.workspace.components.ExpandedStateHandler.setExpandedStates(targetModel, cleanInvalidEntries): aborted");
		}
	}

}
