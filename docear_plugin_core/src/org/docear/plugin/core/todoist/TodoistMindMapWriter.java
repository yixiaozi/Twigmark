package org.docear.plugin.core.todoist;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.core.util.Compat;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.note.NoteController;
import org.freeplane.features.note.mindmapmode.MNoteController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.features.url.mindmapmode.MFileManager;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderCycleAttributes;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderHook;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderTaskAttributes;

/**
 * Writes Todoist tasks into the import mind map.
 * <p>
 * Updates are incremental: nodes keep a hidden {@code TODOIST_TASK_ID} XML attribute and are
 * updated in place. Missing tasks are created; nodes for closed/removed tasks are deleted.
 * <p>
 * If the import map is already open, updates happen in memory on the EDT. If it is closed,
 * {@link TodoistSilentImportWriter} patches the {@code .mm} file on disk — the map is never
 * auto-opened in the UI during sync.
 */
final class TodoistMindMapWriter {
	private static final String TODOIST_BRANCH = "Todoist";
	private static final String NO_SECTION_KEY = "__no_section__";

	TodoistImportResult write(File targetFile, List tasks, Map projectNames, Map sectionNames) {
		return write(targetFile, tasks, projectNames, sectionNames, false, null);
	}

	/**
	 * @param preserveLinkedInboxCopies when true (unlinked-only import), do not delete import-map
	 *            nodes whose task id is still 1:1-linked to a source mind map.
	 */
	TodoistImportResult write(File targetFile, List tasks, Map projectNames, Map sectionNames,
			boolean preserveLinkedInboxCopies) {
		return write(targetFile, tasks, projectNames, sectionNames, preserveLinkedInboxCopies, null);
	}

	TodoistImportResult write(File targetFile, List tasks, Map projectNames, Map sectionNames,
			boolean preserveLinkedInboxCopies, TodoistTaskAnchorIndex anchors) {
		final TodoistImportResult result = new TodoistImportResult();
		result.targetFile = targetFile.getAbsolutePath();
		// Prefer silent disk write when the import map is not already open — never auto-open UI.
		if (findOpenMap(targetFile) == null) {
			return TodoistSilentImportWriter.write(targetFile, tasks, projectNames, sectionNames,
					preserveLinkedInboxCopies, anchors);
		}
		final boolean previousSuppress = TodoistAutoSyncService.setSuppressOutgoing(true);
		try {
			final MapModel map = findOpenMap(targetFile);
			if (map == null) {
				// Race: map closed between check and now — fall back to silent write.
				return TodoistSilentImportWriter.write(targetFile, tasks, projectNames, sectionNames,
						preserveLinkedInboxCopies, anchors);
			}
			final NodeModel mapRoot = map.getRootNode();
			final Map existingByTaskId = indexTaskNodes(mapRoot);
			final TodoistImportLayout layout = TodoistImportLayout.build(tasks, projectNames, sectionNames);
			final List projectKeys = layout.projectKeys();
			for (int p = 0; p < projectKeys.size(); p++) {
				final String projectKey = (String) projectKeys.get(p);
				final String projectName = layout.projectLabel(projectKey);
				final NodeModel projectNode = ensureSection(map, mapRoot, projectName);
				final List sectionKeys = layout.sectionKeys(projectKey);
				for (int s = 0; s < sectionKeys.size(); s++) {
					final String sectionKey = (String) sectionKeys.get(s);
					final String sectionName = layout.sectionLabel(sectionKey);
					final NodeModel sectionNode = ensureSection(map, projectNode, sectionName);
					final List roots = layout.roots(sectionKey);
					final String linePrefix = projectName + " / " + sectionName;
					for (int t = 0; t < roots.size(); t++) {
						placeTask(map, sectionNode, (TodoistImportTask) roots.get(t), layout, existingByTaskId,
								anchors, targetFile, result, linePrefix);
					}
				}
			}
			removeStaleTaskNodes(existingByTaskId, result, preserveLinkedInboxCopies);
			pruneEmptyImportedFolders(mapRoot);
			final NodeModel legacyBranch = findChildByPlainText(mapRoot, TODOIST_BRANCH);
			if (legacyBranch != null && TodoistReminderFactory.getTaskId(legacyBranch) == null) {
				pruneEmptyFolders(legacyBranch);
				if (legacyBranch.getChildCount() == 0) {
					final MMapController mapController = (MMapController) Controller.getCurrentModeController()
							.getMapController();
					mapController.deleteNode(legacyBranch);
				}
			}
			saveMap(map, targetFile);
		}
		catch (Exception e) {
			result.failed++;
			result.errorMessage = e.getMessage();
			result.addFailed(e.getMessage());
			LogUtils.warn("Todoist import write failed", e);
		}
		finally {
			TodoistAutoSyncService.setSuppressOutgoing(previousSuppress);
		}
		return result;
	}

	private static Map indexTaskNodes(NodeModel todoistRoot) {
		final Map byId = new HashMap();
		collectTaskNodes(todoistRoot, byId);
		return byId;
	}

	private static void collectTaskNodes(NodeModel node, Map byId) {
		if (node == null) {
			return;
		}
		final String taskId = TodoistReminderFactory.getTaskId(node);
		if (taskId != null && taskId.length() > 0) {
			byId.put(taskId, node);
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			collectTaskNodes((NodeModel) node.getChildAt(i), byId);
		}
	}

	private static void removeStaleTaskNodes(Map leftoverByTaskId, TodoistImportResult result,
			boolean preserveLinkedInboxCopies) {
		if (leftoverByTaskId.isEmpty()) {
			return;
		}
		final TodoistMappingStore store = preserveLinkedInboxCopies ? TodoistMappingStore.get() : null;
		final MMapController mapController = (MMapController) Controller.getCurrentModeController().getMapController();
		for (Iterator it = leftoverByTaskId.entrySet().iterator(); it.hasNext();) {
			Map.Entry entry = (Map.Entry) it.next();
			final String taskId = (String) entry.getKey();
			if (store != null && store.isLinkedToSourceMap(taskId)) {
				continue;
			}
			NodeModel stale = (NodeModel) entry.getValue();
			if (TodoistReminderFactory.isUserPlaced(stale)) {
				continue;
			}
			try {
				mapController.deleteNode(stale);
				result.addUpdated("[removed closed task] " + nodePlainText(stale));
			}
			catch (Exception e) {
				result.addFailed("Could not remove stale node: " + e.getMessage());
			}
		}
	}

	private static void pruneEmptyFolders(NodeModel todoistRoot) {
		final MMapController mapController = (MMapController) Controller.getCurrentModeController().getMapController();
		for (int p = todoistRoot.getChildCount() - 1; p >= 0; p--) {
			NodeModel project = (NodeModel) todoistRoot.getChildAt(p);
			for (int s = project.getChildCount() - 1; s >= 0; s--) {
				NodeModel section = (NodeModel) project.getChildAt(s);
				if (section.getChildCount() == 0 && TodoistReminderFactory.getTaskId(section) == null) {
					mapController.deleteNode(section);
				}
			}
			if (project.getChildCount() == 0 && TodoistReminderFactory.getTaskId(project) == null) {
				mapController.deleteNode(project);
			}
		}
	}

	private static Map groupTasks(List tasks) {
		final Map grouped = new HashMap();
		for (int i = 0; i < tasks.size(); i++) {
			TodoistImportTask task = (TodoistImportTask) tasks.get(i);
			String projectId = task.projectId == null ? "" : task.projectId;
			String sectionId = task.sectionId == null || task.sectionId.length() == 0 ? NO_SECTION_KEY : task.sectionId;
			Map sectionMap = (Map) grouped.get(projectId);
			if (sectionMap == null) {
				sectionMap = new HashMap();
				grouped.put(projectId, sectionMap);
			}
			List sectionTasks = (List) sectionMap.get(sectionId);
			if (sectionTasks == null) {
				sectionTasks = new ArrayList();
				sectionMap.put(sectionId, sectionTasks);
			}
			sectionTasks.add(task);
		}
		return grouped;
	}

	/**
	 * Returns the import map only if it is already open. Sync must never auto-open maps in the UI.
	 */
	private static MapModel findOpenMap(File targetFile) {
		return TodoistNodeLocator.findOpenMap(targetFile);
	}

	private static NodeModel ensureTodoistBranch(MapModel map) {
		NodeModel root = map.getRootNode();
		NodeModel branch = findChildByPlainText(root, TODOIST_BRANCH);
		if (branch == null) {
			branch = createNode(map, root, TODOIST_BRANCH);
		}
		return branch;
	}

	private static NodeModel ensureChild(MapModel map, NodeModel parent, String plainText) {
		NodeModel existing = findChildByPlainText(parent, plainText);
		if (existing != null) {
			return existing;
		}
		return createNode(map, parent, plainText);
	}

	private static NodeModel findChildByPlainText(NodeModel parent, String plainText) {
		for (int i = 0; i < parent.getChildCount(); i++) {
			NodeModel child = (NodeModel) parent.getChildAt(i);
			if (plainText.equals(nodePlainText(child))) {
				return child;
			}
		}
		return null;
	}

	private static NodeModel createNode(MapModel map, NodeModel parent, String text) {
		final MMapController mapController = (MMapController) Controller.getCurrentModeController().getMapController();
		final MapModel target = parent != null && parent.getMap() != null ? parent.getMap() : map;
		final NodeModel node = new NodeModel(text, target);
		MTextController.getController().setNodeText(node, text);
		mapController.insertNode(node, parent, parent.getChildCount());
		return node;
	}

	private static void placeTask(MapModel map, NodeModel canonicalParent, TodoistImportTask task,
			TodoistImportLayout layout, Map existingByTaskId, TodoistTaskAnchorIndex anchors, File importFile,
			TodoistImportResult result, String sectionName) {
		result.totalFetched++;
		final String line = "[" + sectionName + "] " + parsedLine(task);
		NodeModel existing = (NodeModel) existingByTaskId.remove(task.id);
		final NodeModel openElsewhere = TodoistTaskAnchorIndex.findOpenNode(task.id, importFile);
		final File anchoredFile = anchors == null ? null : anchors.fileFor(task.id);
		if (existing == null && openElsewhere != null) {
			existing = openElsewhere;
		}
		if (existing != null) {
			final boolean userPlaced = TodoistReminderFactory.isUserPlaced(existing) || existing.getMap() != map;
			if (userPlaced) {
				TodoistReminderFactory.setUserPlaced(existing, true);
			}
			if (updateTaskNode(map, existing, canonicalParent, task, !userPlaced)) {
				result.addUpdated(line);
			}
			else {
				result.addSkipped(line);
			}
		}
		else if (anchoredFile != null) {
			result.addSkipped(line + " (kept in " + anchoredFile.getName() + ")");
		}
		else {
			existing = createTaskNode(map, canonicalParent, task);
			result.addCreated(line);
		}
		final List children = layout.childrenOf(task.id);
		for (int i = 0; i < children.size(); i++) {
			final TodoistImportTask child = (TodoistImportTask) children.get(i);
			final NodeModel parentForChild = existing != null ? existing : canonicalParent;
			placeTask(map, parentForChild, child, layout, existingByTaskId, anchors, importFile, result, sectionName);
		}
	}

	private static void pruneEmptyImportedFolders(NodeModel root) {
		final MMapController mapController = (MMapController) Controller.getCurrentModeController().getMapController();
		for (int p = root.getChildCount() - 1; p >= 0; p--) {
			final NodeModel project = (NodeModel) root.getChildAt(p);
			if (TodoistReminderFactory.getTaskId(project) != null) {
				continue;
			}
			for (int s = project.getChildCount() - 1; s >= 0; s--) {
				final NodeModel section = (NodeModel) project.getChildAt(s);
				if (TodoistReminderFactory.getTaskId(section) == null && section.getChildCount() == 0) {
					mapController.deleteNode(section);
				}
			}
			if (project.getChildCount() == 0) {
				mapController.deleteNode(project);
			}
		}
	}

	private static NodeModel ensureSection(MapModel map, NodeModel root, String label) {
		for (int i = 0; i < root.getChildCount(); i++) {
			NodeModel child = (NodeModel) root.getChildAt(i);
			if (TodoistReminderFactory.getTaskId(child) == null && label.equals(nodePlainText(child))) {
				return child;
			}
		}
		return createNode(map, root, label);
	}

	private static NodeModel createTaskNode(MapModel map, NodeModel parent, TodoistImportTask task) {
		TodoistContentParser parsed = TodoistContentParser.parse(task.content);
		final NodeModel node = createNode(map, parent, parsed.nodeText);
		applyTaskFields(map, node, task, parsed);
		return node;
	}

	/** @return true if anything changed */
	private static boolean updateTaskNode(MapModel map, NodeModel node, NodeModel desiredParent, TodoistImportTask task,
			boolean allowMove) {
		boolean changed = false;
		if (allowMove && node.getMap() == map && node.getParentNode() != desiredParent) {
			final MMapController mapController = (MMapController) Controller.getCurrentModeController().getMapController();
			mapController.moveNode(node, desiredParent, desiredParent.getChildCount());
			changed = true;
		}
		TodoistContentParser parsed = TodoistContentParser.parse(task.content);
		final String desiredText = parsed.nodeText;
		final String currentText = nodePlainText(node);
		if (!desiredText.equals(currentText)) {
			if (TodoistSyncService.localTitleWins(TodoistReminderFactory.localModifiedMillis(node), task.updatedAtMillis,
					TodoistReminderFactory.syncedLocalMillis(node), TodoistReminderFactory.syncedRemoteMillis(node))) {
				try {
					final long remote = TodoistSyncService.pushLinkedTitle(task.id, currentText);
					if (remote > 0) {
						TodoistReminderFactory.stampWatermarks(node, remote);
					}
					changed = true;
				}
				catch (Exception e) {
					LogUtils.warn("Todoist: could not push newer mind-map title for " + task.id, e);
				}
			}
			else if (TodoistSyncClock.choose(TodoistReminderFactory.localModifiedMillis(node), task.updatedAtMillis,
					TodoistReminderFactory.syncedLocalMillis(node),
					TodoistReminderFactory.syncedRemoteMillis(node)) == TodoistSyncClock.PULL) {
				MTextController.getController().setNodeText(node, desiredText);
				changed = true;
			}
		}
		if (applyTaskFields(map, node, task, parsed)) {
			changed = true;
		}
		return changed;
	}

	private static boolean applyTaskFields(MapModel map, NodeModel node, TodoistImportTask task,
			TodoistContentParser parsed) {
		boolean changed = false;
		final String previousTaskId = TodoistReminderFactory.getTaskId(node);
		if (!task.id.equals(previousTaskId)) {
			TodoistReminderFactory.setTaskId(node, task.id);
			changed = true;
		}
		if (TodoistReminderFactory.markLinkedNode(node, task.projectId, task.sectionId)) {
			changed = true;
		}
		applyLink(node, parsed.linkUri);
		final String desiredNote = task.description == null ? "" : task.description.trim();
		final MNoteController noteController = (MNoteController) NoteController.getController();
		final String existingNote = noteController.getNoteText(node);
		final String existingPlain = existingNote == null ? "" : HtmlUtils.htmlToPlain(existingNote).trim();
		if (!desiredNote.equals(existingPlain)) {
			if (desiredNote.length() > 0) {
				noteController.setNoteText(node, desiredNote);
			}
			else if (existingNote != null && existingNote.length() > 0) {
				noteController.setNoteText(node, null);
			}
			changed = true;
		}
		final TodoistCycleMapper.Cycle cycle = TodoistCycleMapper.fromTodoistDue(task.dueString, task.recurring);
		final ReminderExtension existing = ReminderExtension.getExtension(node);
		final long existingAt = existing == null ? 0L : existing.getRemindUserAt();
		final String localType = ReminderCycleAttributes.readRemindTypeFromNode(node);
		final int localInterval = ReminderCycleAttributes.readIntervalFromNode(node);
		final String localWeekDays = ReminderCycleAttributes.readWeekDaysFromNode(node);
		final boolean cycleDiffers = cycle.recurring != (localType != null && localType.length() > 0
				&& !"onetime".equalsIgnoreCase(localType))
				|| (cycle.recurring && (cycle.interval != localInterval
						|| !cycle.remindType.equalsIgnoreCase(localType == null ? "" : localType)
						|| !cycle.weekDays.equals(localWeekDays == null ? "" : localWeekDays)));
		if (task.dueAtMillis > 0) {
			if (existingAt != task.dueAtMillis || existing == null || cycleDiffers) {
				applyReminder(node, task.dueAtMillis, cycle);
				changed = true;
			}
		}
		else if (existing != null) {
			final ModeController modeController = Controller.getCurrentModeController();
			final ReminderHook reminderHook = (ReminderHook) modeController.getExtension(ReminderHook.class);
			if (reminderHook != null) {
				reminderHook.undoableDeactivateHook(node);
				ReminderCycleAttributes.writeOneTimeReminder(node);
				changed = true;
			}
		}
		final int localDuration = ReminderTaskAttributes.readTaskTimeFromNode(node);
		final int localLevel = ReminderTaskAttributes.readTaskLevelFromNode(node);
		final int localJinji = ReminderTaskAttributes.readJinjiFromNode(node);
		final int desiredJinji = TodoistPriority.toJinji(task.priority, localJinji);
		if (task.durationMinutes != localDuration || desiredJinji != localJinji) {
			ReminderTaskAttributes.writeFull(node, task.durationMinutes, localLevel, desiredJinji);
			changed = true;
		}
		final String hash = Integer.toString((task.content + "|" + task.dueAtMillis + "|" + cycle.recurring + "|"
				+ cycle.interval + "|" + cycle.periodUnit() + "|" + cycle.remindType + "|" + cycle.weekDays + "|"
				+ task.durationMinutes + "|" + TodoistPriority.toTodoistApi(desiredJinji)).hashCode());
		if (!hash.equals(TodoistReminderFactory.getStoredContentHash(node))) {
			TodoistReminderFactory.setStoredContentHash(node, hash);
		}
		return changed;
	}

	private static void applyReminder(NodeModel node, long dueAtMillis, TodoistCycleMapper.Cycle cycle) {
		final ModeController modeController = Controller.getCurrentModeController();
		final ReminderHook reminderHook = (ReminderHook) modeController.getExtension(ReminderHook.class);
		if (reminderHook == null) {
			return;
		}
		final ReminderExtension reminder = new ReminderExtension(node);
		reminder.setRemindUserAt(dueAtMillis);
		reminder.setPeriod(cycle.interval);
		reminder.setPeriodUnitAsString(cycle.periodUnit());
		reminderHook.undoableActivateHook(node, reminder);
		if (cycle.recurring) {
			ReminderCycleAttributes.writeRecurringCycle(node, cycle.remindType, cycle.interval, cycle.weekDays);
		}
		else {
			ReminderCycleAttributes.writeOneTimeReminder(node);
		}
	}

	private static void applyLink(NodeModel node, String linkUri) {
		if (linkUri == null || linkUri.trim().length() == 0) {
			return;
		}
		try {
			final ModeController modeController = Controller.getCurrentModeController();
			final URI uri = new URI(linkUri.trim());
			((MLinkController) LinkController.getController(modeController)).setLink(node, uri,
					LinkController.LINK_ABSOLUTE);
		}
		catch (Exception e) {
			LogUtils.warn("Todoist import: could not set link " + linkUri, e);
		}
	}

	private static String parsedLine(TodoistImportTask task) {
		TodoistContentParser parsed = TodoistContentParser.parse(task.content);
		if (parsed.linkUri != null) {
			return parsed.nodeText + " -> " + parsed.linkUri;
		}
		return parsed.nodeText;
	}

	private static void saveMap(MapModel map, File targetFile) throws Exception {
		final ModeController modeController = Controller.getCurrentModeController();
		final MFileManager fileManager = MFileManager.getController(modeController);
		File parent = targetFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		((MapModel) map).setURL(Compat.fileToUrl(targetFile));
		map.setSaved(false);
		if (!fileManager.save(map, targetFile)) {
			throw new Exception("Could not save " + targetFile.getAbsolutePath());
		}
	}

	private static String nodePlainText(NodeModel node) {
		String text = node.getText();
		if (text == null) {
			return "";
		}
		return HtmlUtils.htmlToPlain(text).trim();
	}

	private static String resolveName(Map names, String id, String fallback) {
		if (id == null || id.length() == 0) {
			return fallback;
		}
		String name = (String) names.get(id);
		return name != null && name.length() > 0 ? name : fallback + " (" + id + ")";
	}

	private static final class ProjectNameComparator implements Comparator {
		private final Map projectNames;

		private ProjectNameComparator(Map projectNames) {
			this.projectNames = projectNames;
		}

		public int compare(Object a, Object b) {
			String nameA = resolveName(projectNames, (String) a, "");
			String nameB = resolveName(projectNames, (String) b, "");
			return nameA.compareToIgnoreCase(nameB);
		}
	}

	private static final class SectionNameComparator implements Comparator {
		private final Map sectionNames;

		private SectionNameComparator(Map sectionNames) {
			this.sectionNames = sectionNames;
		}

		public int compare(Object a, Object b) {
			if (NO_SECTION_KEY.equals(a)) {
				return 1;
			}
			if (NO_SECTION_KEY.equals(b)) {
				return -1;
			}
			String nameA = resolveName(sectionNames, (String) a, "");
			String nameB = resolveName(sectionNames, (String) b, "");
			return nameA.compareToIgnoreCase(nameB);
		}
	}
}
