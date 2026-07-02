package org.docear.plugin.mcp.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.json.JsonParser;
import org.docear.plugin.mcp.json.JsonValue;
import org.freeplane.plugin.workspace.URIUtils;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.model.WorkspaceModel;
import org.freeplane.plugin.workspace.model.project.AWorkspaceProject;

public final class McpWorkspaceService {

	private McpWorkspaceService() {
	}

	public static String listProjects() {
		final List<JsonValue> projects = new ArrayList<JsonValue>();
		try {
			final WorkspaceModel model = WorkspaceController.getCurrentModel();
			if (model != null) {
				final Collection<AWorkspaceProject> allProjects = model.getProjects();
				if (allProjects != null) {
					for (final AWorkspaceProject project : allProjects) {
						final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
						item.put("id", JsonValue.ofString(project.getProjectID()));
						item.put("name", JsonValue.ofString(project.getProjectName()));
						final File home = URIUtils.getAbsoluteFile(project.getProjectHome());
						item.put("home", JsonValue.ofString(home != null ? home.getAbsolutePath() : ""));
						projects.add(JsonValue.ofMap(item));
					}
				}
			}
		}
		catch (Exception e) {
			final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
			item.put("error", JsonValue.ofString(e.getMessage()));
			projects.add(JsonValue.ofMap(item));
		}
		return JsonValue.ofList(projects).toJson();
	}

	public static String getOverview() {
		final Map<String, JsonValue> overview = new LinkedHashMap<String, JsonValue>();
		overview.put("projects", JsonParser.parse(listProjects()));
		final org.freeplane.core.util.WorkspaceSideTabSnapshot snapshot =
				org.freeplane.core.util.WorkspaceSideTabSnapshotRegistry.getSnapshot();
		overview.put("todoCount", JsonValue.ofNumber(Integer.valueOf(snapshot.getTodos().size())));
		overview.put("oneTimeReminderCount",
				JsonValue.ofNumber(Integer.valueOf(snapshot.getOneTimeReminders().size())));
		overview.put("recurringReminderCount",
				JsonValue.ofNumber(Integer.valueOf(snapshot.getRecurringReminders().size())));
		overview.put("pinnedCount", JsonValue.ofNumber(Integer.valueOf(snapshot.getPinnedEntries().size())));
		overview.put("workspacePlan", JsonValue.ofString(McpContextService.getWorkspacePlan()));
		return JsonValue.ofMap(overview).toJson();
	}
}
