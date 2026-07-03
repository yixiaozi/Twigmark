package org.docear.plugin.mcp.service;

import java.io.File;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.docear.plugin.core.features.DocearNodePrivacyExtensionController;
import org.docear.plugin.core.features.DocearNodePrivacyExtensionController.DocearPrivacyLevel;
import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.util.EdtRunner;
import org.docear.plugin.mcp.util.EdtRunner.Task;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.MindMapWorkspaceContextScanner;
import org.freeplane.core.util.MindMapWorkspaceContextScanner.IconItem;
import org.freeplane.core.util.WorkspaceSideTabSnapshot;
import org.freeplane.core.util.WorkspaceSideTabSnapshotRegistry;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.factory.MindIconFactory;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.note.NoteModel;
import org.freeplane.features.note.mindmapmode.MNoteController;
import org.freeplane.features.text.DetailTextModel;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.plugin.workspace.actions.WorkspaceNewMapAction;
import org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagService;
import org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagUtils;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderCycleAttributes;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderHook;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderTaskAttributes;

public final class McpNodeService {

	private static final SimpleDateFormat MODIFIED_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private McpNodeService() {
	}

	public static String getNodeDetails(final String filePath, final String nodeId) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				return buildNodeDetailsJson(session.getFile(), node).toJson();
			}
		});
	}

	public static String listPinned(final int limit) {
		final WorkspaceSideTabSnapshot snapshot = WorkspaceSideTabSnapshotRegistry.getSnapshot();
		List pinned = snapshot.getPinnedEntries();
		if (pinned == null) {
			pinned = new ArrayList();
		}
		if (limit > 0 && pinned.size() > limit) {
			pinned = new ArrayList(pinned.subList(0, limit));
		}
		return JsonValue.ofList(McpContextService.pinnedToJson(pinned)).toJson();
	}

	public static String listPublished(final int limit) {
		final List items = MindMapWorkspaceContextScanner.scanPublishedItems();
		final List<JsonValue> json = new ArrayList<JsonValue>();
		for (int i = 0; i < items.size(); i++) {
			if (limit > 0 && json.size() >= limit) {
				break;
			}
			final IconItem item = (IconItem) items.get(i);
			final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
			row.put("mapFile", JsonValue.ofString(McpContextService.pathOf(item.mapFile)));
			row.put("nodeId", JsonValue.ofString(item.nodeId));
			row.put("nodeText", JsonValue.ofString(item.nodeText));
			json.add(JsonValue.ofMap(row));
		}
		return JsonValue.ofList(json).toJson();
	}

	public static String moveNode(final String filePath, final String nodeId, final String newParentNodeId,
			final int index) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final MMapController mapController = getMapController();
				final NodeModel node = session.requireNode(nodeId);
				final NodeModel newParent = session.requireNode(newParentNodeId);
				if (node.getParentNode() == null) {
					throw new IllegalArgumentException("Cannot move root node.");
				}
				final int targetIndex = index >= 0 ? index : newParent.getChildCount();
				mapController.moveNode(node, newParent, targetIndex);
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("newParentNodeId", JsonValue.ofString(newParentNodeId));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setNodeFolded(final String filePath, final String nodeId, final boolean folded)
			throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				getMapController().setFolded(node, folded);
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("folded", JsonValue.ofBoolean(folded));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setNodeLink(final String filePath, final String nodeId, final String link) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final MLinkController linkController = (MLinkController) LinkController.getController();
				if (link == null || link.trim().length() == 0) {
					linkController.setLink(node, (URI) null, LinkController.LINK_ABSOLUTE);
				}
				else {
					linkController.setLink(node, link.trim(), LinkController.LINK_ABSOLUTE);
				}
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("link", JsonValue.ofString(link != null ? link.trim() : ""));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setNodeNote(final String filePath, final String nodeId, final String noteHtml) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				((MNoteController) org.freeplane.features.note.NoteController.getController()).setNoteText(node,
						noteHtml != null && noteHtml.trim().length() > 0 ? noteHtml : null);
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setNodeTags(final String filePath, final String nodeId, final String tagsCsv,
			final boolean pinned) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final Set tags = parseTags(tagsCsv);
				if (pinned) {
					tags.add(NodeDetailsTagUtils.PIN_TAG);
				}
				NodeDetailsTagService.setUserTags(node, tags);
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("tags", JsonValue.ofString(joinTags(NodeDetailsTagService.getUserTags(node))));
				result.put("pinned", JsonValue.ofBoolean(NodeDetailsTagService.isPinned(node)));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String togglePin(final String filePath, final String nodeId) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				NodeDetailsTagService.togglePin(node);
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("pinned", JsonValue.ofBoolean(NodeDetailsTagService.isPinned(node)));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setNodeIcon(final String filePath, final String nodeId, final String iconName,
			final boolean enabled) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final MIconController iconController = (MIconController) IconController.getController();
				if (enabled) {
					iconController.addIcon(node, MindIconFactory.create(iconName));
				}
				else {
					removeIconByName(node, iconName);
				}
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("icon", JsonValue.ofString(iconName));
				result.put("enabled", JsonValue.ofBoolean(enabled));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setRecurringReminder(final String filePath, final String nodeId, final long remindAtMillis,
			final String remindType, final int interval, final String weekDays, final int taskLevel, final int jinji)
			throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final ReminderHook reminderHook = (ReminderHook) Controller.getCurrentModeController()
						.getExtension(ReminderHook.class);
				if (reminderHook == null) {
					throw new IllegalStateException("ReminderHook is not available.");
				}
				final ReminderExtension existing = ReminderExtension.getExtension(node);
				if (existing != null) {
					reminderHook.undoableToggleHook(node);
				}
				final ReminderExtension reminderExtension = new ReminderExtension(node);
				reminderExtension.setRemindUserAt(remindAtMillis);
				reminderExtension.setPeriodUnitAsString("DAY");
				reminderExtension.setPeriod(1);
				reminderHook.undoableActivateHook(node, reminderExtension);
				final String type = remindType != null ? remindType.trim().toLowerCase() : "day";
				ReminderCycleAttributes.writeRecurringCycle(node, type, interval > 0 ? interval : 1, weekDays);
				ReminderTaskAttributes.writeTaskMetadata(node, taskLevel, jinji);
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("remindAtMillis", JsonValue.ofNumber(Long.valueOf(remindAtMillis)));
				result.put("remindType", JsonValue.ofString(type));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String createMindmap(final String filePath, final String rootText, final boolean openInUi)
			throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				if (filePath == null || filePath.trim().length() == 0) {
					throw new IllegalArgumentException("filePath is required.");
				}
				final File file = new File(filePath.trim());
				if (file.exists()) {
					throw new IllegalArgumentException("File already exists: " + file.getAbsolutePath());
				}
				final String name = rootText != null && rootText.trim().length() > 0 ? rootText.trim() : file.getName();
				final org.freeplane.features.map.MapModel map = WorkspaceNewMapAction.createNewMap(file.toURI(), name,
						true);
				if (map == null) {
					throw new IllegalStateException("Failed to create mind map.");
				}
				if (openInUi) {
					WorkspaceNewMapAction.openMap(file.toURI());
				}
				final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
				result.put("created", JsonValue.ofBoolean(true));
				result.put("mapFile", JsonValue.ofString(file.getAbsolutePath()));
				result.put("rootText", JsonValue.ofString(name));
				result.put("openedInUi", JsonValue.ofBoolean(openInUi));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	static JsonValue buildNodeDetailsJson(final File mapFile, final NodeModel node) {
		final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
		data.put("mapFile", JsonValue.ofString(mapFile != null ? mapFile.getAbsolutePath() : ""));
		data.put("nodeId", JsonValue.ofString(node.getID()));
		data.put("text", JsonValue.ofString(TextController.getController().getPlainTextContent(node)));
		data.put("folded", JsonValue.ofBoolean(node.isFolded()));
		data.put("depth", JsonValue.ofNumber(Integer.valueOf(getDepth(node))));
		data.put("parentNodeId", JsonValue.ofString(getParentId(node)));
		data.put("parentPath", JsonValue.ofString(buildParentPath(node)));
		final long modifiedAt = node.getHistoryInformation().getLastModifiedAt().getTime();
		data.put("modifiedAtMillis", JsonValue.ofNumber(modifiedAt));
		data.put("modifiedAt", JsonValue.ofString(MODIFIED_DATE_FORMAT.format(new Date(modifiedAt))));
		final long createdAt = node.getHistoryInformation().getCreatedAt().getTime();
		data.put("createdAtMillis", JsonValue.ofNumber(createdAt));

		final String noteHtml = NoteModel.getNoteText(node);
		data.put("note", JsonValue.ofString(noteHtml != null ? noteHtml : ""));
		data.put("notePlain", JsonValue.ofString(noteHtml != null ? HtmlUtils.removeHtmlTagsFromString(noteHtml) : ""));

		final String link = NodeLinks.getLinkAsString(node);
		data.put("link", JsonValue.ofString(link != null ? link : ""));

		final String detailsHtml = DetailTextModel.getDetailTextText(node);
		data.put("detailsHtml", JsonValue.ofString(detailsHtml != null ? detailsHtml : ""));
		data.put("detailsPlain",
				JsonValue.ofString(detailsHtml != null ? HtmlUtils.removeHtmlTagsFromString(detailsHtml) : ""));
		data.put("tags", JsonValue.ofString(joinTags(NodeDetailsTagService.getUserTags(node))));
		data.put("pinned", JsonValue.ofBoolean(NodeDetailsTagService.isPinned(node)));

		data.put("icons", JsonValue.ofList(toIconJson(IconController.getController().getIcons(node))));

		final ReminderExtension reminder = ReminderExtension.getExtension(node);
		if (reminder != null) {
			data.put("remindAtMillis", JsonValue.ofNumber(Long.valueOf(reminder.getRemindUserAt())));
			data.put("remindAt", JsonValue.ofString(MODIFIED_DATE_FORMAT.format(new Date(reminder.getRemindUserAt()))));
		}
		data.put("remindType", JsonValue.ofString(ReminderCycleAttributes.readRemindTypeFromNode(node)));
		data.put("remindInterval", JsonValue.ofNumber(Integer.valueOf(ReminderCycleAttributes.readIntervalFromNode(node))));
		data.put("remindWeekDays", JsonValue.ofString(ReminderCycleAttributes.readWeekDaysFromNode(node)));
		data.put("taskTime", JsonValue.ofNumber(Integer.valueOf(ReminderTaskAttributes.readTaskTimeFromNode(node))));
		data.put("taskLevel", JsonValue.ofNumber(Integer.valueOf(ReminderTaskAttributes.readTaskLevelFromNode(node))));
		data.put("jinji", JsonValue.ofNumber(Integer.valueOf(ReminderTaskAttributes.readJinjiFromNode(node))));

		data.put("privacy", JsonValue.ofString(readPrivacyLevel(node)));

		return JsonValue.ofMap(data);
	}

	private static String readPrivacyLevel(final NodeModel node) {
		final DocearNodePrivacyExtensionController.DocearNodePrivacyExtension privacy = DocearNodePrivacyExtensionController
				.getExtension(node);
		return privacy != null ? privacy.getPrivacyLevel().name() : DocearPrivacyLevel.PUBLIC.name();
	}

	private static List<JsonValue> toIconJson(final Collection icons) {
		final List<JsonValue> list = new ArrayList<JsonValue>();
		if (icons == null) {
			return list;
		}
		for (final Iterator it = icons.iterator(); it.hasNext();) {
			final MindIcon icon = (MindIcon) it.next();
			if (icon != null && icon.getName() != null) {
				list.add(JsonValue.ofString(icon.getName()));
			}
		}
		return list;
	}

	private static int getDepth(final NodeModel node) {
		int depth = 0;
		NodeModel current = node.getParentNode();
		while (current != null) {
			depth++;
			current = current.getParentNode();
		}
		return depth;
	}

	private static String getParentId(final NodeModel node) {
		final NodeModel parent = node.getParentNode();
		return parent != null ? parent.getID() : "";
	}

	private static String buildParentPath(final NodeModel node) {
		final List parts = new ArrayList();
		NodeModel current = node.getParentNode();
		while (current != null) {
			parts.add(0, TextController.getController().getPlainTextContent(current));
			current = current.getParentNode();
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				sb.append(" / ");
			}
			sb.append(parts.get(i));
		}
		return sb.toString();
	}

	private static Set parseTags(final String tagsCsv) {
		final Set tags = new LinkedHashSet();
		if (tagsCsv == null || tagsCsv.trim().length() == 0) {
			return tags;
		}
		final String[] parts = tagsCsv.split("[,，;；]");
		for (int i = 0; i < parts.length; i++) {
			final String part = parts[i].trim();
			if (part.length() > 0 && !NodeDetailsTagUtils.PIN_TAG.equals(part)) {
				tags.add(part);
			}
		}
		return tags;
	}

	private static String joinTags(final Set tags) {
		if (tags == null || tags.isEmpty()) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		for (final Iterator it = tags.iterator(); it.hasNext();) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(String.valueOf(it.next()));
		}
		return sb.toString();
	}

	private static void removeIconByName(final NodeModel node, final String iconName) {
		final Collection icons = IconController.getController().getIcons(node);
		if (icons == null) {
			return;
		}
		final MIconController iconController = (MIconController) IconController.getController();
		int position = 0;
		for (final Iterator it = icons.iterator(); it.hasNext();) {
			final MindIcon icon = (MindIcon) it.next();
			if (icon != null && iconName.equals(icon.getName())) {
				iconController.removeIcon(node, position);
				return;
			}
			position++;
		}
	}

	private static MMapController getMapController() {
		return (MMapController) Controller.getCurrentModeController().getMapController();
	}

	private static void ensureWritable() {
		if (DocearMcpConfig.isReadOnly()) {
			throw new SecurityException("Docear MCP is running in read-only mode.");
		}
	}
}
