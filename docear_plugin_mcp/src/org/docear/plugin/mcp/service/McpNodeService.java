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
import org.freeplane.core.util.ColorUtils;
import org.freeplane.features.cloud.CloudModel;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.factory.MindIconFactory;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.link.ConnectorModel;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.link.LinkModel;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.nodestyle.NodeStyleModel;
import org.freeplane.view.swing.features.filepreview.ExternalResource;
import org.freeplane.features.encrypt.EncryptionConfig;
import org.freeplane.features.encrypt.EncryptionController;
import org.freeplane.features.map.EncryptionModel;
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
		final WorkspaceSideTabSnapshot snapshot = WorkspaceSideTabSnapshotRegistry.getSnapshot();
		List items = snapshot.getPublishedEntries();
		if (items == null || items.isEmpty()) {
			items = MindMapWorkspaceContextScanner.scanPublishedItems();
		}
		final List<JsonValue> json = new ArrayList<JsonValue>();
		for (int i = 0; i < items.size(); i++) {
			if (limit > 0 && json.size() >= limit) {
				break;
			}
			final Object raw = items.get(i);
			final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
			if (raw instanceof IconItem) {
				final IconItem item = (IconItem) raw;
				row.put("mapFile", JsonValue.ofString(McpContextService.pathOf(item.mapFile)));
				row.put("nodeId", JsonValue.ofString(item.nodeId));
				row.put("nodeText", JsonValue.ofString(item.nodeText));
			}
			else if (raw instanceof WorkspaceSideTabSnapshot.ItemEntry) {
				final WorkspaceSideTabSnapshot.ItemEntry item = (WorkspaceSideTabSnapshot.ItemEntry) raw;
				row.put("mapFile", JsonValue.ofString(McpContextService.pathOf(item.mapFile)));
				row.put("nodeId", JsonValue.ofString(item.nodeId));
				row.put("nodeText", JsonValue.ofString(item.nodeText));
			}
			else {
				continue;
			}
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

	public static String encryptNode(final String filePath, final String nodeId, final String password)
			throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(resolveNodeId(session, nodeId));
				final EncryptionModel before = EncryptionModel.getModel(node);
				final boolean alreadyEncrypted = before != null;
				final boolean alreadyLocked = before != null && !before.isAccessible();
				if (alreadyLocked) {
					return encryptionResult(session, node, "encrypt", true, true, false);
				}
				final PasswordChoice choice = resolvePassword(password);
				encryptionController().encryptAndLock(node, choice.password);
				session.save();
				return encryptionResult(session, node, "encrypt", alreadyEncrypted, false, choice.usedSavedPassword);
			}
		});
	}

	public static String decryptNode(final String filePath, final String nodeId, final String password)
			throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(resolveNodeId(session, nodeId));
				final EncryptionModel before = EncryptionModel.getModel(node);
				if (before == null) {
					throw new IllegalArgumentException("Node is not encrypted.");
				}
				if (before.isAccessible()) {
					return encryptionResult(session, node, "decrypt", true, true, false);
				}
				final PasswordChoice choice = resolvePassword(password);
				if (!encryptionController().unlockWithPassword(node, choice.password)) {
					throw new IllegalArgumentException("Wrong password.");
				}
				session.save();
				return encryptionResult(session, node, "decrypt", true, false, choice.usedSavedPassword);
			}
		});
	}

	public static String removeNodeEncryption(final String filePath, final String nodeId, final String password)
			throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(resolveNodeId(session, nodeId));
				final EncryptionModel before = EncryptionModel.getModel(node);
				if (before == null) {
					final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
					putEncryptionFields(result, node);
					result.put("nodeId", JsonValue.ofString(node.getID()));
					result.put("action", JsonValue.ofString("remove_encryption"));
					result.put("removed", JsonValue.ofBoolean(false));
					return JsonValue.ofMap(result).toJson();
				}
				final PasswordChoice choice = resolvePassword(password);
				if (!encryptionController().removeEncryptionWithPassword(node, choice.password)) {
					throw new IllegalArgumentException("Wrong password.");
				}
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				putEncryptionFields(result, node);
				result.put("nodeId", JsonValue.ofString(node.getID()));
				result.put("action", JsonValue.ofString("remove_encryption"));
				result.put("removed", JsonValue.ofBoolean(true));
				result.put("usedSavedPassword", JsonValue.ofBoolean(choice.usedSavedPassword));
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
				// Older WorkspaceNewMapAction builds omit MapStyle; nested add_nodes needs it.
				McpMapWriteSession.ensureMapStyle(map);
				final String rootNodeId = map.getRootNode() != null ? map.getRootNode().createID() : "";
				if (openInUi) {
					WorkspaceNewMapAction.openMap(file.toURI());
				}
				final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
				result.put("created", JsonValue.ofBoolean(true));
				result.put("mapFile", JsonValue.ofString(file.getAbsolutePath()));
				result.put("rootText", JsonValue.ofString(name));
				result.put("rootNodeId", JsonValue.ofString(rootNodeId != null ? rootNodeId : ""));
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
		putStyleFields(data, node);
		putEncryptionFields(data, node);

		try {
			final org.freeplane.view.swing.features.pomodoro.PomodoroExtension pomodoro =
					org.freeplane.view.swing.features.pomodoro.PomodoroAttributes.read(node);
			if (pomodoro != null && (pomodoro.isEnabled() || pomodoro.getTotalMs() > 0 || pomodoro.sessionCount() > 0)) {
				data.put("pomodoro", McpPomodoroService.sessionJson(node, System.currentTimeMillis()));
			}
			else {
				data.put("pomodoro", JsonValue.ofNull());
			}
		}
		catch (Exception e) {
			data.put("pomodoro", JsonValue.ofNull());
		}

		return JsonValue.ofMap(data);
	}

	private static String readPrivacyLevel(final NodeModel node) {
		final DocearNodePrivacyExtensionController.DocearNodePrivacyExtension privacy = DocearNodePrivacyExtensionController
				.getExtension(node);
		return privacy != null ? privacy.getPrivacyLevel().name() : DocearPrivacyLevel.PUBLIC.name();
	}

	private static void putStyleFields(final Map<String, JsonValue> data, final NodeModel node) {
		final CloudModel cloud = CloudModel.getModel(node);
		data.put("cloud", JsonValue.ofBoolean(cloud != null));
		if (cloud != null) {
			data.put("cloudColor", JsonValue.ofString(ColorUtils.colorToString(cloud.getColor())));
			data.put("cloudShape", JsonValue.ofString(cloud.getShape() != null ? cloud.getShape().name() : ""));
		}
		else {
			data.put("cloudColor", JsonValue.ofString(""));
			data.put("cloudShape", JsonValue.ofString(""));
		}
		data.put("nodeColor", JsonValue.ofString(nullToEmpty(ColorUtils.colorToString(NodeStyleModel.getColor(node)))));
		data.put("backgroundColor",
				JsonValue.ofString(nullToEmpty(ColorUtils.colorToString(NodeStyleModel.getBackgroundColor(node)))));
		data.put("fontFamily", JsonValue.ofString(nullToEmpty(NodeStyleModel.getFontFamilyName(node))));
		final Integer fontSize = NodeStyleModel.getFontSize(node);
		data.put("fontSize", fontSize != null ? JsonValue.ofNumber(fontSize) : JsonValue.ofNull());
		data.put("bold", JsonValue.ofBoolean(Boolean.TRUE.equals(NodeStyleModel.isBold(node))));
		data.put("italic", JsonValue.ofBoolean(Boolean.TRUE.equals(NodeStyleModel.isItalic(node))));
		data.put("nodeShape", JsonValue.ofString(nullToEmpty(NodeStyleModel.getShape(node))));
		final ExternalResource image = (ExternalResource) node.getExtension(ExternalResource.class);
		data.put("imageUri", JsonValue.ofString(image != null && image.getUri() != null ? image.getUri().toString() : ""));
		final List<JsonValue> arrows = new ArrayList<JsonValue>();
		final Collection links = NodeLinks.getLinks(node);
		if (links != null) {
			for (final Iterator it = links.iterator(); it.hasNext();) {
				final LinkModel link = (LinkModel) it.next();
				if (!(link instanceof ConnectorModel)) {
					continue;
				}
				final ConnectorModel connector = (ConnectorModel) link;
				final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
				row.put("targetNodeId", JsonValue.ofString(connector.getTargetID()));
				row.put("label", JsonValue.ofString(nullToEmpty(connector.getMiddleLabel())));
				arrows.add(JsonValue.ofMap(row));
			}
		}
		data.put("arrowLinks", JsonValue.ofList(arrows));
	}

	private static String nullToEmpty(final String value) {
		return value != null ? value : "";
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

	private static EncryptionController encryptionController() {
		final EncryptionController controller = (EncryptionController) Controller.getCurrentModeController()
				.getExtension(EncryptionController.class);
		if (controller == null) {
			throw new IllegalStateException("EncryptionController is not available.");
		}
		return controller;
	}

	private static String resolveNodeId(final McpMapWriteSession session, final String nodeId) {
		if (nodeId != null && nodeId.trim().length() > 0) {
			return nodeId.trim();
		}
		try {
			final NodeModel selected = Controller.getCurrentController().getSelection().getSelected();
			if (selected != null && selected.getMap() == session.getMap()) {
				return selected.getID();
			}
		}
		catch (Exception ignored) {
		}
		throw new IllegalArgumentException("nodeId is required (or select a node in the target map).");
	}

	private static PasswordChoice resolvePassword(final String password) {
		if (password != null && password.length() > 0) {
			return new PasswordChoice(password, false);
		}
		if (EncryptionConfig.hasPassword()) {
			return new PasswordChoice(EncryptionConfig.getPassword(), true);
		}
		throw new IllegalArgumentException(
				"Password required. Pass password= or set a default in Encryption settings.");
	}

	private static String encryptionResult(final McpMapWriteSession session, final NodeModel node, final String action,
			final boolean alreadyEncrypted, final boolean unchanged, final boolean usedSavedPassword) {
		final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
		putEncryptionFields(result, node);
		result.put("nodeId", JsonValue.ofString(node.getID()));
		result.put("nodeText", JsonValue.ofString(plainText(node)));
		result.put("action", JsonValue.ofString(action));
		result.put("alreadyEncrypted", JsonValue.ofBoolean(alreadyEncrypted));
		if ("encrypt".equals(action)) {
			result.put("alreadyLocked", JsonValue.ofBoolean(unchanged));
		}
		else if ("decrypt".equals(action)) {
			result.put("alreadyUnlocked", JsonValue.ofBoolean(unchanged));
		}
		result.put("usedSavedPassword", JsonValue.ofBoolean(usedSavedPassword));
		return JsonValue.ofMap(result).toJson();
	}

	static void putEncryptionFields(final Map<String, JsonValue> data, final NodeModel node) {
		final EncryptionModel enc = EncryptionModel.getModel(node);
		final boolean encrypted = enc != null;
		data.put("encrypted", JsonValue.ofBoolean(encrypted));
		data.put("encryptionUnlocked", JsonValue.ofBoolean(encrypted && enc.isAccessible()));
		data.put("visibleChildCount", JsonValue.ofNumber(Integer.valueOf(node.getChildCount())));
	}

	private static String plainText(final NodeModel node) {
		try {
			return TextController.getController().getPlainTextContent(node);
		}
		catch (Exception e) {
			return node != null ? String.valueOf(node.toString()) : "";
		}
	}

	private static final class PasswordChoice {
		final String password;
		final boolean usedSavedPassword;

		PasswordChoice(final String password, final boolean usedSavedPassword) {
			this.password = password;
			this.usedSavedPassword = usedSavedPassword;
		}
	}

	private static void ensureWritable() {
		if (DocearMcpConfig.isReadOnly()) {
			throw new SecurityException("Docear MCP is running in read-only mode.");
		}
	}
}
