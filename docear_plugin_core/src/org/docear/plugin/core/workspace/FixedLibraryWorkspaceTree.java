package org.docear.plugin.core.workspace;

import java.io.File;
import java.io.FileFilter;

import org.docear.plugin.core.workspace.model.DocearWorkspaceProject;
import org.docear.plugin.core.workspace.node.DocearProjectRootNode;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.plugin.workspace.URIUtils;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;
import org.freeplane.plugin.workspace.model.WorkspaceModel;
import org.freeplane.plugin.workspace.model.project.AWorkspaceProject;
import org.freeplane.plugin.workspace.nodes.FolderTypeMyFilesNode;
import org.freeplane.plugin.workspace.nodes.ProjectRootNode;

/**
 * Builds the workspace tree from {@link MindMapDataRootResolver#getWorkingDirectory()}.
 * Ignores saved settings.xml structure so the tree always mirrors disk (with standard filters).
 */
public final class FixedLibraryWorkspaceTree {

	private static final String MY_FILES_LABEL = "\u6211\u7684\u6587\u4ef6";

	private FixedLibraryWorkspaceTree() {
	}

	public static boolean isFixedLibrary(final AWorkspaceProject project) {
		if (project == null) {
			return false;
		}
		final File workingDirectory = MindMapDataRootResolver.getWorkingDirectory();
		final File projectHome = URIUtils.getAbsoluteFile(project.getProjectHome());
		if (projectHome == null) {
			return false;
		}
		try {
			return projectHome.getCanonicalFile().equals(workingDirectory.getCanonicalFile());
		}
		catch (final Exception e) {
			return projectHome.equals(workingDirectory);
		}
	}

	/** Same rules as {@link FolderTypeMyFilesNode#refresh()} and {@link MyFilesTreeDisplayHelper}. */
	public static FileFilter createDiskFileFilter() {
		return new FileFilter() {
			public boolean accept(final File pathname) {
				if (pathname == null) {
					return false;
				}
				if (MindMapDataRootResolver.isConfigDirectoryName(pathname.getName())) {
					return false;
				}
				if (pathname.isDirectory() && pathname.getName().startsWith("_")) {
					return false;
				}
				return true;
			}
		};
	}

	/**
	 * Rebuilds project root + My files from disk. Safe to call repeatedly (e.g. after opening maps).
	 */
	public static void syncProjectFromDisk(final AWorkspaceProject project) {
		if (!isFixedLibrary(project)) {
			return;
		}
		final File dataRoot = URIUtils.getAbsoluteFile(project.getProjectHome());
		if (dataRoot == null || !dataRoot.isDirectory()) {
			LogUtils.severe("Library path is missing or not a directory: "
			        + (dataRoot == null ? "(null)" : dataRoot.getAbsolutePath()));
			return;
		}

		final DocearWorkspaceProject docearProject = (DocearWorkspaceProject) project;
		ProjectRootNode root = (ProjectRootNode) project.getModel().getRoot();
		if (!(root instanceof DocearProjectRootNode)) {
			final DocearProjectRootNode newRoot = new DocearProjectRootNode();
			newRoot.setProjectID(project.getProjectID());
			newRoot.setModel(project.getModel());
			newRoot.setName("\u5de5\u4f5c\u533a");
			project.getModel().setRoot(newRoot);
			root = newRoot;
		}
		else {
			root.setName("\u5de5\u4f5c\u533a");
		}

		clearRootChildren(docearProject, root);

		final FolderTypeMyFilesNode myFiles = new FolderTypeMyFilesNode(project);
		myFiles.setName(MY_FILES_LABEL);
		project.getModel().addNodeTo(myFiles, root);

		myFiles.getModel().removeAllElements(myFiles);
		WorkspaceController.getFileSystemMgr().scanFileSystem(myFiles, dataRoot, false, createDiskFileFilter());
		myFiles.getModel().reload(myFiles);

		final int count = myFiles.getModelChildCount();
		LogUtils.info("Library workspace synced from " + dataRoot.getAbsolutePath() + " (" + count
		        + " top-level entries)");

		final WorkspaceModel workspaceModel = WorkspaceController.getCurrentModel();
		if (workspaceModel != null && workspaceModel.getRoot() != null && workspaceModel.getRoot().getModel() != null) {
			workspaceModel.getRoot().getModel().reload(workspaceModel.getRoot());
		}
	}

	private static void clearRootChildren(final DocearWorkspaceProject project, final ProjectRootNode root) {
		while (root.getModelChildCount() > 0) {
			final AWorkspaceTreeNode child = root.getModelChildAt(0);
			try {
				project.getModel().removeNodeFromParent(child);
			}
			catch (final Exception e) {
				LogUtils.warn("Could not remove workspace child " + child + ": " + e.getMessage());
				break;
			}
		}
	}
}
