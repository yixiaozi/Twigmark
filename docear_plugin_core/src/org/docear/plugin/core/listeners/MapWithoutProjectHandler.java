package org.docear.plugin.core.listeners;

import org.docear.plugin.core.workspace.FixedWorkspaceBootstrap;
import org.freeplane.features.map.MapModel;
import org.freeplane.plugin.workspace.model.project.AWorkspaceProject;

public class MapWithoutProjectHandler {

	public static AWorkspaceProject showProjectSelectionWizard(final MapModel map) {
		return showProjectSelectionWizard(map, true);
	}

	public static AWorkspaceProject showProjectSelectionWizard(final MapModel map, final boolean showCloseButton) {
		return FixedWorkspaceBootstrap.attachMapToFixedProject(map);
	}
}
