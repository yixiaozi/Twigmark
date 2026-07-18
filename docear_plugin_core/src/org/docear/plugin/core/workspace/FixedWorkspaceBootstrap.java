package org.docear.plugin.core.workspace;

import java.io.File;
import java.io.IOException;

import org.docear.plugin.core.logging.DocearLogger;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.features.map.MapModel;
import org.freeplane.plugin.workspace.URIUtils;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.model.WorkspaceModel;
import org.freeplane.plugin.workspace.model.project.AWorkspaceProject;

/**
 * Ensures the workspace project for {@link MindMapDataRootResolver#getWorkingDirectory()}
 * is loaded and maps can attach to it without a project wizard.
 */
public final class FixedWorkspaceBootstrap {

	private FixedWorkspaceBootstrap() {
	}

	public static AWorkspaceProject attachMapToFixedProject(final MapModel map) {
		if (map == null) {
			return null;
		}
		final AWorkspaceProject project = getOrLoadFixedProject();
		if (project != null) {
			WorkspaceController.getMapModelExtension(map).setProject(project);
			try {
				LogUtils.info("attached map \"" + map.getTitle() + "\" to library project at "
				        + project.getProjectHome());
			}
			catch (final Exception e) {
				// ignore
			}
		}
		return project;
	}

	public static AWorkspaceProject getOrLoadFixedProject() {
		final File dataRoot = MindMapDataRootResolver.getWorkingDirectory();
		if (!dataRoot.isDirectory()) {
			LogUtils.severe("Working directory is missing or not a directory: " + dataRoot.getAbsolutePath());
			return null;
		}
		try {
			final AWorkspaceProject existing = findProjectByHome(dataRoot);
			if (existing != null) {
				FixedLibraryWorkspaceTree.syncProjectFromDisk(existing);
				existing.setLoaded();
				return existing;
			}
			final String projectId = MindMapDataRootResolver.resolveProjectIdForDataRoot(dataRoot);
			final AWorkspaceProject project = AWorkspaceProject.create(projectId, dataRoot.toURI());
			WorkspaceController.getCurrentModel().addProject(project);
			FixedLibraryWorkspaceTree.syncProjectFromDisk(project);
			project.setLoaded();
			return project;
		}
		catch (final Exception e) {
			if (e instanceof IOException) {
				DocearLogger.error((IOException) e);
			}
			else {
				LogUtils.severe(e);
			}
			return null;
		}
	}

	private static AWorkspaceProject findProjectByHome(final File dataRoot) {
		final WorkspaceModel model = WorkspaceController.getCurrentModel();
		if (model == null) {
			return null;
		}
		for (final AWorkspaceProject project : model.getProjects()) {
			final File home = URIUtils.getFile(project.getProjectHome());
			if (home != null && home.equals(dataRoot)) {
				return project;
			}
		}
		return null;
	}
}
