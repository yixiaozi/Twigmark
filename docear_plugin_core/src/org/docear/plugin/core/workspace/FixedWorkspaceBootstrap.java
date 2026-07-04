package org.docear.plugin.core.workspace;

import java.io.File;
import java.io.IOException;

import org.docear.plugin.core.logging.DocearLogger;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.features.map.MapModel;
import org.freeplane.plugin.workspace.URIUtils;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.io.IProjectSettingsIOHandler.LOAD_RETURN_TYPE;
import org.freeplane.plugin.workspace.model.WorkspaceModel;
import org.freeplane.plugin.workspace.model.project.AWorkspaceProject;

/**
 * Ensures the single fixed workspace project at {@link MindMapDataRootResolver#FIXED_DATA_ROOT_PATH}
 * is loaded and maps are attached to it without any project wizard.
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
				LogUtils.info("attached map \"" + map.getTitle() + "\" to fixed project at "
				        + MindMapDataRootResolver.FIXED_DATA_ROOT_PATH);
			}
			catch (final Exception e) {
				// ignore
			}
		}
		return project;
	}

	public static AWorkspaceProject getOrLoadFixedProject() {
		File dataRoot = MindMapDataRootResolver.getFixedDataRoot();
		if (dataRoot == null) {
			dataRoot = new File(MindMapDataRootResolver.FIXED_DATA_ROOT_PATH);
			if (!dataRoot.exists() && !dataRoot.mkdirs()) {
				LogUtils.severe("Could not create fixed data root: " + MindMapDataRootResolver.FIXED_DATA_ROOT_PATH);
				return null;
			}
		}
		try {
			final AWorkspaceProject existing = findProjectByHome(dataRoot);
			if (existing != null) {
				return existing;
			}
			final String projectId = MindMapDataRootResolver.resolveProjectIdForDataRoot(dataRoot);
			final AWorkspaceProject project = AWorkspaceProject.create(projectId, dataRoot.toURI());
			WorkspaceController.getCurrentModel().addProject(project);
			final LOAD_RETURN_TYPE loadResult = WorkspaceController.getCurrentModeExtension().getProjectLoader()
			        .loadProject(project);
			if (loadResult == LOAD_RETURN_TYPE.NEW_PROJECT) {
				project.getModel().getRoot().setName(dataRoot.getName());
				project.getModel().nodeChanged(project.getModel().getRoot(), null, dataRoot.getName());
			}
			return project;
		}
		catch (final IOException e) {
			DocearLogger.error(e);
			return null;
		}
		catch (final Exception e) {
			LogUtils.severe(e);
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
