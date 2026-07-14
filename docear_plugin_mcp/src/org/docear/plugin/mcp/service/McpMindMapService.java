package org.docear.plugin.mcp.service;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import org.docear.plugin.core.features.DocearNodePrivacyExtensionController;
import org.docear.plugin.core.features.DocearNodePrivacyExtensionController.DocearPrivacyLevel;
import org.docear.plugin.core.todoist.TodoistSyncService;
import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonParser;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.util.EdtRunner;
import org.docear.plugin.mcp.util.EdtRunner.Task;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.factory.MindIconFactory;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mapio.MapIO;
import org.freeplane.features.mapio.mindmapmode.MMapIO;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.note.NoteModel;
import org.freeplane.features.text.DetailTextModel;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagService;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderCycleAttributes;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderHook;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderTaskAttributes;

public final class McpMindMapService {

	private static final String TODO_ICON = "hourglass";
	private static final SimpleDateFormat MODIFIED_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;
	/** Caps for {@link #addNodes} to keep one MCP call bounded. */
	private static final int ADD_NODES_MAX_COUNT = 300;
	private static final int ADD_NODES_MAX_DEPTH = 20;

	private static final class SearchMatch {
		private final File mapFile;
		private final String nodeId;
		private final String nodeText;
		private final long modifiedAt;
		private final String parentNodeId;
		private final String parentPath;
		private final int depth;

		private SearchMatch(final File mapFile, final String nodeId, final String nodeText, final long modifiedAt,
				final String parentNodeId, final String parentPath, final int depth) {
			this.mapFile = mapFile;
			this.nodeId = nodeId;
			this.nodeText = nodeText;
			this.modifiedAt = modifiedAt;
			this.parentNodeId = parentNodeId;
			this.parentPath = parentPath;
			this.depth = depth;
		}
	}

	private McpMindMapService() {
	}

	public static String getActiveMapJson(final boolean includeFolded) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() {
				final MapModel map = Controller.getCurrentController().getMap();
				if (map == null) {
					return "{\"message\":\"No mind map is open.\"}";
				}
				return serializeMap(map, includeFolded).toJson();
			}
		});
	}

	public static String getActiveMapJson() throws Exception {
		return getActiveMapJson(true);
	}

	public static String getMindmapJson(final String filePath, final int maxDepth, final boolean includeFolded)
			throws Exception {
		final File file = resolveMindMapFileByPath(filePath);
		if (!file.exists()) {
			throw new IllegalArgumentException("Mind map not found: " + filePath);
		}
		final Map<String, JsonValue> root = new LinkedHashMap<String, JsonValue>();
		root.put("file", JsonValue.ofString(file.getAbsolutePath()));
		root.put("root", parseMindMapFileToJson(file, maxDepth, includeFolded));
		return JsonValue.ofMap(root).toJson();
	}

	public static String getMindmapJson(final String filePath, final int maxDepth) throws Exception {
		return getMindmapJson(filePath, maxDepth, true);
	}

	public static String searchNodes(final String query, final int limit, final int modifiedWithinDays,
			final String filePath, final String projectId) {
		final String needle = query == null ? "" : query.trim().toLowerCase();
		if (needle.length() == 0 && (filePath == null || filePath.trim().length() == 0)) {
			return JsonValue.ofList(Collections.EMPTY_LIST).toJson();
		}
		if (filePath != null && filePath.trim().length() > 0) {
			final File file = resolveMindMapFileByPath(filePath.trim());
			final List matches = new ArrayList();
			collectMatchesInFile(file, needle, matches, 0L);
			sortMatchesByModifiedDesc(matches);
			return JsonValue.ofList(toSearchMatchJson(limitMatches(matches, limit))).toJson();
		}
		final long cutoff = modifiedWithinDays > 0
				? System.currentTimeMillis() - modifiedWithinDays * MILLIS_PER_DAY
				: 0L;
		final List matches = searchAllNodesSorted(needle, limit, cutoff, projectId);
		return JsonValue.ofList(toSearchMatchJson(matches)).toJson();
	}

	public static String searchNodes(final String query, final int limit, final int modifiedWithinDays) {
		return searchNodes(query, limit, modifiedWithinDays, null, null);
	}

	public static String listRecentlyModified(final String query, final int limit, final int modifiedWithinDays) {
		final String needle = query == null ? "" : query.trim().toLowerCase();
		final long cutoff = modifiedWithinDays > 0
				? System.currentTimeMillis() - modifiedWithinDays * MILLIS_PER_DAY
				: 0L;
		final List matches = searchAllNodesSorted(needle, limit, cutoff, null);
		return JsonValue.ofList(toSearchMatchJson(matches)).toJson();
	}

	private static List searchAllNodesSorted(final String needle, final int limit, final long modifiedAfterMillis,
			final String projectId) {
		final File projectRoot = resolveProjectRoot(projectId);
		final List files = collectSearchScopeFiles(projectRoot);
		sortFilesByModifiedDesc(files);
		final List matches = new ArrayList();
		for (int i = 0; i < files.size(); i++) {
			final File file = (File) files.get(i);
			if (modifiedAfterMillis > 0L && file.lastModified() < modifiedAfterMillis) {
				continue;
			}
			collectMatchesInFile(file, needle, matches, modifiedAfterMillis);
		}
		sortMatchesByModifiedDesc(matches);
		return limitMatches(matches, limit);
	}

	private static List collectSearchScopeFiles(final File projectRoot) {
		final List files = new ArrayList();
		MindMapDataRootResolver.collectMindmapFiles(files);
		if (projectRoot == null) {
			return files;
		}
		final List scoped = new ArrayList();
		for (int i = 0; i < files.size(); i++) {
			final File file = (File) files.get(i);
			if (isUnderProject(file, projectRoot)) {
				scoped.add(file);
			}
		}
		return scoped;
	}

	private static void sortFilesByModifiedDesc(final List files) {
		Collections.sort(files, new Comparator() {
			public int compare(final Object o1, final Object o2) {
				final long a = ((File) o1).lastModified();
				final long b = ((File) o2).lastModified();
				return a < b ? 1 : (a > b ? -1 : 0);
			}
		});
	}

	private static List<JsonValue> toSearchMatchJson(final List matches) {
		final List<JsonValue> json = new ArrayList<JsonValue>();
		for (int i = 0; i < matches.size(); i++) {
			final SearchMatch match = (SearchMatch) matches.get(i);
			final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
			item.put("mapFile", JsonValue.ofString(match.mapFile.getAbsolutePath()));
			item.put("nodeId", JsonValue.ofString(match.nodeId));
			item.put("nodeText", JsonValue.ofString(match.nodeText));
			item.put("modifiedAtMillis", JsonValue.ofNumber(match.modifiedAt));
			item.put("modifiedAt", JsonValue.ofString(MODIFIED_DATE_FORMAT.format(new Date(match.modifiedAt))));
			item.put("parentNodeId", JsonValue.ofString(match.parentNodeId != null ? match.parentNodeId : ""));
			item.put("parentPath", JsonValue.ofString(match.parentPath != null ? match.parentPath : ""));
			item.put("depth", JsonValue.ofNumber(Integer.valueOf(match.depth)));
			json.add(JsonValue.ofMap(item));
		}
		return json;
	}

	private static List limitMatches(final List matches, final int limit) {
		if (limit > 0 && matches.size() > limit) {
			return new ArrayList(matches.subList(0, limit));
		}
		return matches;
	}

	private static File resolveProjectRoot(final String projectId) {
		if (projectId == null || projectId.trim().length() == 0) {
			return null;
		}
		try {
			final String projectsJson = McpWorkspaceService.listProjects();
			final JsonValue parsed = JsonParserHelper.parse(projectsJson);
			final List<JsonValue> items = parsed.asList();
			for (int i = 0; i < items.size(); i++) {
				final Map<String, JsonValue> item = items.get(i).asMap();
				if (projectId.equals(item.get("id").asString())) {
					final String home = item.get("home").asString();
					if (home != null && home.length() > 0) {
						return new File(home);
					}
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn("MCP project filter failed: " + e.getMessage());
		}
		return null;
	}

	private static boolean isUnderProject(final File file, final File projectRoot) {
		if (file == null || projectRoot == null) {
			return false;
		}
		try {
			return file.getCanonicalPath().startsWith(projectRoot.getCanonicalPath());
		}
		catch (Exception e) {
			return file.getAbsolutePath().startsWith(projectRoot.getAbsolutePath());
		}
	}

	private static void sortMatchesByModifiedDesc(final List matches) {
		Collections.sort(matches, new Comparator() {
			public int compare(final Object o1, final Object o2) {
				final long a = ((SearchMatch) o1).modifiedAt;
				final long b = ((SearchMatch) o2).modifiedAt;
				return a < b ? 1 : (a > b ? -1 : 0);
			}
		});
	}

	private static long parseModifiedAt(final Attributes attributes, final File file) {
		final String modifiedStr = attributes.getValue("MODIFIED");
		if (modifiedStr != null) {
			try {
				return Long.parseLong(modifiedStr);
			}
			catch (Exception e) {
				// fall through
			}
		}
		return file != null ? file.lastModified() : 0L;
	}

	public static String openMindmap(final String filePath) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final File file = resolveMindMapFile(filePath);
				if (!file.exists()) {
					throw new IllegalArgumentException("Mind map not found: " + filePath);
				}
				final URL url = file.toURI().toURL();
				final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
				if (!mapViewManager.tryToChangeToMapView(url)) {
					Controller.getCurrentModeController().getMapController().newMap(url);
				}
				final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
				result.put("opened", JsonValue.ofString(file.getAbsolutePath()));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String navigateToNode(final String filePath, final String nodeId) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final File file = resolveMindMapFile(filePath);
				if (!file.exists()) {
					throw new IllegalArgumentException("Mind map not found: " + filePath);
				}
				final URL url = file.toURI().toURL();
				final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
				if (!mapViewManager.tryToChangeToMapView(url)) {
					Controller.getCurrentModeController().getMapController().newMap(url);
				}
				final Map<String, MapModel> maps = mapViewManager.getMaps(MModeController.MODENAME);
				for (final MapModel map : maps.values()) {
					if (isSameFile(map.getFile(), file)) {
						final NodeModel node = map.getNodeForID(nodeId);
						if (node != null) {
							Controller.getCurrentController().getSelection().selectAsTheOnlyOneSelected(node);
							Controller.getCurrentModeController().getMapController().centerNode(node);
							final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
							result.put("navigated", JsonValue.ofBoolean(true));
							result.put("nodeText",
									JsonValue.ofString(TextController.getController().getPlainTextContent(node)));
							return JsonValue.ofMap(result).toJson();
						}
					}
				}
				throw new IllegalArgumentException("Node not found: " + nodeId);
			}
		});
	}

	public static String addNode(final String filePath, final String parentNodeId, final String text) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final MMapController mapController = getMapController();
				final NodeModel parent = session.requireNode(parentNodeId);
				final NodeModel node = mapController.addNewNode(parent, parent.getChildCount(), parent.isNewChildLeft());
				((MTextController) TextController.getController()).setNodeText(node, text);
				final String nodeId = node.createID();
				session.save();
				final Map<String, JsonValue> result = writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("nodeText", JsonValue.ofString(text));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	/**
	 * Batch-create a node tree under {@code parentNodeId} in one EDT pass and one save.
	 * <p>
	 * {@code nodesValue} is a JSON array. Each item may be:
	 * <ul>
	 * <li>a string → one child with that text</li>
	 * <li>an object {@code { "text": "...", "todo"?: bool, "children"?: [...] }}</li>
	 * </ul>
	 * Nested {@code children} builds multiple levels. Prefer this over repeated {@link #addNode}.
	 */
	public static String addNodes(final String filePath, final String parentNodeId, final JsonValue nodesValue)
			throws Exception {
		ensureWritable();
		final List specs = normalizeNodeSpecs(nodesValue);
		if (specs.isEmpty()) {
			throw new IllegalArgumentException("nodes must be a non-empty JSON array");
		}
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final MMapController mapController = getMapController();
				final MTextController textController = (MTextController) TextController.getController();
				final MIconController iconController = (MIconController) IconController.getController();
				final NodeModel parent = session.requireNode(parentNodeId);
				final int[] created = new int[] { 0 };
				final List<JsonValue> createdTree = new ArrayList<JsonValue>();
				for (int i = 0; i < specs.size(); i++) {
					createdTree.add(createNodeSpecRecursive(mapController, textController, iconController, parent,
							(JsonValue) specs.get(i), 1, created));
				}
				session.save();
				final Map<String, JsonValue> result = writeResult(session);
				result.put("parentNodeId", JsonValue.ofString(parentNodeId));
				result.put("createdCount", JsonValue.ofNumber(Integer.valueOf(created[0])));
				result.put("nodes", JsonValue.ofList(createdTree));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	@SuppressWarnings("rawtypes")
	private static List normalizeNodeSpecs(final JsonValue nodesValue) {
		if (nodesValue == null || nodesValue.isNull()) {
			return Collections.EMPTY_LIST;
		}
		JsonValue root = nodesValue;
		final Object raw = nodesValue.raw();
		if (raw instanceof String) {
			final String text = ((String) raw).trim();
			if (text.length() == 0) {
				return Collections.EMPTY_LIST;
			}
			root = JsonParser.parse(text);
		}
		final List list = root.asList();
		if (list == null || list.isEmpty()) {
			// Allow a single object / string as a one-item batch.
			if (raw instanceof Map || (root.raw() instanceof Map)) {
				final List one = new ArrayList();
				one.add(root);
				return one;
			}
			final String asText = root.asString();
			if (asText != null && asText.length() > 0 && !asText.startsWith("[") && !asText.startsWith("{")) {
				final List one = new ArrayList();
				one.add(JsonValue.ofString(asText));
				return one;
			}
			return Collections.EMPTY_LIST;
		}
		return list;
	}

	private static JsonValue createNodeSpecRecursive(final MMapController mapController,
			final MTextController textController, final MIconController iconController, final NodeModel parent,
			final JsonValue spec, final int depth, final int[] created) {
		if (depth > ADD_NODES_MAX_DEPTH) {
			throw new IllegalArgumentException("nodes tree exceeds max depth " + ADD_NODES_MAX_DEPTH);
		}
		if (created[0] >= ADD_NODES_MAX_COUNT) {
			throw new IllegalArgumentException("nodes exceeds max count " + ADD_NODES_MAX_COUNT);
		}
		final String text;
		boolean todo = false;
		List children = Collections.EMPTY_LIST;
		final Object raw = spec != null ? spec.raw() : null;
		if (raw instanceof String || (spec != null && !(raw instanceof Map) && !(raw instanceof List))) {
			text = spec.asString();
		}
		else if (raw instanceof Map) {
			final Map map = spec.asMap();
			if (!map.containsKey("text") || map.get("text") == null || ((JsonValue) map.get("text")).isNull()) {
				throw new IllegalArgumentException("each node object requires text");
			}
			text = ((JsonValue) map.get("text")).asString();
			todo = map.containsKey("todo") && ((JsonValue) map.get("todo")).asBoolean();
			if (map.containsKey("children") && map.get("children") != null) {
				children = ((JsonValue) map.get("children")).asList();
			}
		}
		else {
			throw new IllegalArgumentException("invalid node spec; use string or {text, children?, todo?}");
		}
		if (text == null || text.trim().length() == 0) {
			throw new IllegalArgumentException("node text must not be empty");
		}
		final NodeModel node = mapController.addNewNode(parent, parent.getChildCount(), parent.isNewChildLeft());
		textController.setNodeText(node, text);
		final String nodeId = node.createID();
		if (todo) {
			iconController.addIcon(node, MindIconFactory.create(TODO_ICON));
		}
		created[0]++;
		final List<JsonValue> childResults = new ArrayList<JsonValue>();
		for (int i = 0; i < children.size(); i++) {
			childResults.add(createNodeSpecRecursive(mapController, textController, iconController, node,
					(JsonValue) children.get(i), depth + 1, created));
		}
		final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
		row.put("nodeId", JsonValue.ofString(nodeId));
		row.put("nodeText", JsonValue.ofString(text));
		if (todo) {
			row.put("todo", JsonValue.ofBoolean(true));
		}
		if (!childResults.isEmpty()) {
			row.put("children", JsonValue.ofList(childResults));
		}
		return JsonValue.ofMap(row);
	}

	public static String changeNodeText(final String filePath, final String nodeId, final String text) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				((MTextController) TextController.getController()).setNodeText(node, text);
				session.save();
				final Map<String, JsonValue> result = writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("nodeText", JsonValue.ofString(text));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String removeNode(final String filePath, final String nodeId) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final MMapController mapController = getMapController();
				final NodeModel node = session.requireNode(nodeId);
				mapController.deleteNode(node);
				session.save();
				final Map<String, JsonValue> result = writeResult(session);
				result.put("removed", JsonValue.ofBoolean(true));
				result.put("nodeId", JsonValue.ofString(nodeId));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String createTodo(final String filePath, final String parentNodeId, final String text) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final MMapController mapController = getMapController();
				final NodeModel parent = session.requireNode(parentNodeId);
				final NodeModel node = mapController.addNewNode(parent, parent.getChildCount(), parent.isNewChildLeft());
				((MTextController) TextController.getController()).setNodeText(node, text);
				final String nodeId = node.createID();
				final MIconController iconController = (MIconController) IconController.getController();
				iconController.addIcon(node, MindIconFactory.create(TODO_ICON));
				session.save();
				final Map<String, JsonValue> result = writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("nodeText", JsonValue.ofString(text));
				result.put("todo", JsonValue.ofBoolean(true));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String completeTodo(final String filePath, final String nodeId) throws Exception {
		return setTodoIcon(filePath, nodeId, false);
	}

	public static String setReminder(final String filePath, final String nodeId, final long remindAtMillis)
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
				ReminderCycleAttributes.writeOneTimeReminder(node);
				ReminderTaskAttributes.writeEmptyTask(node);
				session.save();
				final Map<String, JsonValue> result = writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("remindAtMillis", JsonValue.ofNumber(Long.valueOf(remindAtMillis)));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setPriority(final String filePath, final String nodeId, final int level) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				if (level < 1 || level > 7) {
					throw new IllegalArgumentException("Priority level must be between 1 and 7.");
				}
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final MIconController iconController = (MIconController) IconController.getController();
				removePriorityIcons(iconController, node);
				final MindIcon icon = MindIconFactory.create("full-" + level);
				iconController.addIcon(node, icon);
				session.save();
				final Map<String, JsonValue> result = writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("priority", JsonValue.ofNumber(Integer.valueOf(level)));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String quickCapture(final String text) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final Class<?> controllerClass = Class
						.forName("org.docear.plugin.core.quickcapture.QuickCaptureController");
				final Method method = controllerClass.getDeclaredMethod("capture", String.class);
				method.setAccessible(true);
				final Boolean ok = (Boolean) method.invoke(null, text);
				final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
				result.put("captured", JsonValue.ofBoolean(ok != null && ok.booleanValue()));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String syncTodoist() {
		try {
			final Object syncResult = TodoistSyncService.syncAllReminders();
			final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
			result.put("totalScanned", JsonValue.ofNumber(Integer.valueOf(readIntField(syncResult, "totalScanned"))));
			result.put("created", JsonValue.ofNumber(Integer.valueOf(readIntField(syncResult, "created"))));
			result.put("updated", JsonValue.ofNumber(Integer.valueOf(readIntField(syncResult, "updated"))));
			result.put("failed", JsonValue.ofNumber(Integer.valueOf(readIntField(syncResult, "failed"))));
			result.put("errorMessage", JsonValue.ofString(readStringField(syncResult, "errorMessage")));
			return JsonValue.ofMap(result).toJson();
		}
		catch (Exception e) {
			throw new RuntimeException("Todoist sync failed: " + e.getMessage(), e);
		}
	}

	private static int readIntField(final Object target, final String fieldName) throws Exception {
		final java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.getInt(target);
	}

	private static String readStringField(final Object target, final String fieldName) throws Exception {
		final java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		final Object value = field.get(target);
		return value != null ? String.valueOf(value) : "";
	}

	public static String exportWorkspaceSnapshot() throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final Class<?> exporterClass = Class
						.forName("org.docear.plugin.ai.snapshot.AiWorkspaceSnapshotExporter");
				final Class<?> configClass = Class.forName("org.docear.plugin.ai.DocearAiConfig");
				final Object config = configClass.newInstance();
				final Object exporter = exporterClass.getConstructor(configClass).newInstance(config);
				final Method exportMethod = exporterClass.getMethod("export");
				exportMethod.invoke(exporter);
				final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
				result.put("exported", JsonValue.ofBoolean(true));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	private static String setTodoIcon(final String filePath, final String nodeId, final boolean enabled)
			throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final MIconController iconController = (MIconController) IconController.getController();
				final MindIcon todoIcon = MindIconFactory.create(TODO_ICON);
				if (enabled) {
					iconController.addIcon(node, todoIcon);
				}
				else {
					removeIconByName(node, TODO_ICON);
				}
				session.save();
				final Map<String, JsonValue> result = writeResult(session);
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("todo", JsonValue.ofBoolean(enabled));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	static Map<String, JsonValue> writeResult(final McpMapWriteSession session) {
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("mapFile", JsonValue.ofString(session.getFile().getAbsolutePath()));
		result.put("saved", JsonValue.ofBoolean(true));
		result.put("headlessLoad", JsonValue.ofBoolean(session.isHeadlessLoad()));
		return result;
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

	private static void removePriorityIcons(final MIconController iconController, final NodeModel node) {
		for (int level = 1; level <= 7; level++) {
			removeIconByName(node, "full-" + level);
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

	private static JsonValue serializeMap(final MapModel map, final boolean includeFolded) {
		final Map<String, JsonValue> root = new LinkedHashMap<String, JsonValue>();
		root.put("file", JsonValue.ofString(map.getFile() != null ? map.getFile().getAbsolutePath() : ""));
		root.put("root", serializeNode(map.getRootNode(), includeFolded));
		return JsonValue.ofMap(root);
	}

	private static JsonValue serializeNode(final NodeModel node, final boolean includeFolded) {
		final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
		data.put("id", JsonValue.ofString(node.getID()));
		data.put("text", JsonValue.ofString(TextController.getController().getPlainTextContent(node)));
		data.put("folded", JsonValue.ofBoolean(node.isFolded()));
		appendRichNodeFields(data, node);
		final List<JsonValue> children = new ArrayList<JsonValue>();
		final List childNodes = includeFolded ? node.getChildren()
				: Controller.getCurrentModeController().getMapController().childrenUnfolded(node);
		for (int i = 0; i < childNodes.size(); i++) {
			children.add(serializeNode((NodeModel) childNodes.get(i), includeFolded));
		}
		data.put("children", JsonValue.ofList(children));
		return JsonValue.ofMap(data);
	}

	private static void appendRichNodeFields(final Map<String, JsonValue> data, final NodeModel node) {
		final long modifiedAt = node.getHistoryInformation().getLastModifiedAt().getTime();
		data.put("modifiedAtMillis", JsonValue.ofNumber(modifiedAt));
		data.put("modifiedAt", JsonValue.ofString(MODIFIED_DATE_FORMAT.format(new Date(modifiedAt))));

		final String noteHtml = NoteModel.getNoteText(node);
		data.put("note", JsonValue.ofString(noteHtml != null ? noteHtml : ""));
		data.put("notePlain",
				JsonValue.ofString(noteHtml != null ? HtmlUtils.removeHtmlTagsFromString(noteHtml) : ""));

		final String link = NodeLinks.getLinkAsString(node);
		data.put("link", JsonValue.ofString(link != null ? link : ""));

		final String detailsHtml = DetailTextModel.getDetailTextText(node);
		data.put("detailsHtml", JsonValue.ofString(detailsHtml != null ? detailsHtml : ""));

		data.put("tags", JsonValue.ofString(joinTags(NodeDetailsTagService.getUserTags(node))));
		data.put("pinned", JsonValue.ofBoolean(NodeDetailsTagService.isPinned(node)));
		data.put("icons", JsonValue.ofList(toIconNames(IconController.getController().getIcons(node))));

		data.put("taskTime", JsonValue.ofNumber(Integer.valueOf(ReminderTaskAttributes.readTaskTimeFromNode(node))));
		data.put("taskLevel", JsonValue.ofNumber(Integer.valueOf(ReminderTaskAttributes.readTaskLevelFromNode(node))));
		data.put("jinji", JsonValue.ofNumber(Integer.valueOf(ReminderTaskAttributes.readJinjiFromNode(node))));

		data.put("remindType", JsonValue.ofString(ReminderCycleAttributes.readRemindTypeFromNode(node)));

		data.put("privacy", JsonValue.ofString(readPrivacyLevel(node)));
	}

	private static String readPrivacyLevel(final NodeModel node) {
		final DocearNodePrivacyExtensionController.DocearNodePrivacyExtension privacy = DocearNodePrivacyExtensionController
				.getExtension(node);
		return privacy != null ? privacy.getPrivacyLevel().name() : DocearPrivacyLevel.PUBLIC.name();
	}

	private static List<JsonValue> toIconNames(final Collection icons) {
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

	private static JsonValue parseMindMapFileToJson(final File file, final int maxDepth, final boolean includeFolded)
			throws Exception {
		final SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
		final List stack = new ArrayList();
		final List roots = new ArrayList();
		saxParser.parse(file, new DefaultHandler() {
			private final StringBuilder richContentBuilder = new StringBuilder();
			private String richContentType;
			private Map currentNode;

			public void startElement(final String uri, final String localName, final String qName,
					final Attributes attributes) {
				if ("node".equals(qName)) {
					final Map node = new LinkedHashMap();
					final String id = attributes.getValue("ID");
					node.put("id", id != null ? id : "");
					final String rawText = attributes.getValue("TEXT");
					final String plain = rawText != null ? HtmlUtils.removeHtmlTagsFromString(rawText) : null;
					node.put("text", plain != null ? plain.trim() : "");
					final String folded = attributes.getValue("FOLDED");
					node.put("folded", Boolean.valueOf("true".equalsIgnoreCase(folded)
							|| "folded".equalsIgnoreCase(folded)));
					node.put("depth", Integer.valueOf(stack.size()));
					node.put("children", new ArrayList());
					node.put("icons", new ArrayList());
					final long modifiedAt = parseModifiedAt(attributes, file);
					node.put("modifiedAtMillis", Long.valueOf(modifiedAt));
					node.put("modifiedAt", MODIFIED_DATE_FORMAT.format(new Date(modifiedAt)));
					final String link = attributes.getValue("LINK");
					node.put("link", link != null ? link : "");
					node.put("taskTime", parseIntAttr(attributes.getValue("TASKTIME"), 0));
					node.put("taskLevel", parseIntAttr(attributes.getValue("TASKLEVEL"), 0));
					node.put("jinji", parseIntAttr(attributes.getValue("JINJI"), 0));
					final String remindType = attributes.getValue("REMINDERTYPE");
					node.put("remindType", remindType != null ? remindType : "");
					node.put("note", "");
					node.put("detailsHtml", "");
					node.put("tags", "");
					node.put("pinned", Boolean.FALSE);

					boolean inTree = stack.isEmpty();
					if (!stack.isEmpty()) {
						final Map parent = (Map) stack.get(stack.size() - 1);
						inTree = Boolean.TRUE.equals(parent.get("inTree"));
						if (inTree && !includeFolded && Boolean.TRUE.equals(parent.get("folded"))) {
							inTree = false;
						}
					}
					node.put("inTree", Boolean.valueOf(inTree));

					if (inTree) {
						if (stack.isEmpty()) {
							roots.add(node);
						}
						else {
							final Map parent = (Map) stack.get(stack.size() - 1);
							final int depth = ((Integer) parent.get("depth")).intValue();
							if (maxDepth <= 0 || depth < maxDepth) {
								((List) parent.get("children")).add(node);
							}
						}
					}
					stack.add(node);
					currentNode = node;
					richContentType = null;
					richContentBuilder.setLength(0);
					return;
				}
				if ("icon".equals(qName) && currentNode != null) {
					final String iconName = attributes.getValue("BUILTIN");
					if (iconName != null) {
						((List) currentNode.get("icons")).add(iconName);
					}
					return;
				}
				if ("richcontent".equals(qName) && currentNode != null) {
					richContentType = attributes.getValue("TYPE");
					richContentBuilder.setLength(0);
				}
			}

			public void characters(final char[] ch, final int start, final int length) {
				if (richContentType != null) {
					richContentBuilder.append(ch, start, length);
				}
			}

			public void endElement(final String uri, final String localName, final String qName) {
				if ("richcontent".equals(qName) && currentNode != null && richContentType != null) {
					final String html = richContentBuilder.toString();
					if ("NOTE".equals(richContentType)) {
						currentNode.put("note", html);
					}
					else if ("DETAILS".equals(richContentType)) {
						currentNode.put("detailsHtml", html);
						final Set allTags = org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagUtils
								.parseAllTags(html);
						currentNode.put("pinned", Boolean.valueOf(allTags
								.contains(org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagUtils.PIN_TAG)));
						final List tagNames = new ArrayList();
						for (final Iterator it = allTags.iterator(); it.hasNext();) {
							final String tag = (String) it.next();
							if (!org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagUtils.PIN_TAG
									.equals(tag)
									&& org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagUtils
											.isValidTagName(tag)) {
								tagNames.add(tag);
							}
						}
						currentNode.put("tags", joinStringList(tagNames));
					}
					richContentType = null;
					richContentBuilder.setLength(0);
					return;
				}
				if ("node".equals(qName) && !stack.isEmpty()) {
					stack.remove(stack.size() - 1);
					currentNode = stack.isEmpty() ? null : (Map) stack.get(stack.size() - 1);
				}
			}
		});
		if (roots.isEmpty()) {
			throw new IllegalArgumentException("No nodes found in mind map: " + file.getAbsolutePath());
		}
		return serializeParsedNode((Map) roots.get(0));
	}

	private static int parseIntAttr(final String value, final int defaultValue) {
		if (value == null || value.trim().length() == 0) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		}
		catch (Exception e) {
			return defaultValue;
		}
	}

	private static String joinStringList(final List parts) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(String.valueOf(parts.get(i)));
		}
		return sb.toString();
	}

	private static JsonValue serializeParsedNode(final Map node) {
		final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
		data.put("id", JsonValue.ofString(String.valueOf(node.get("id"))));
		data.put("text", JsonValue.ofString(String.valueOf(node.get("text"))));
		data.put("folded", JsonValue.ofBoolean(Boolean.TRUE.equals(node.get("folded"))));
		data.put("modifiedAtMillis", JsonValue.ofNumber(((Long) node.get("modifiedAtMillis")).longValue()));
		data.put("modifiedAt", JsonValue.ofString(String.valueOf(node.get("modifiedAt"))));
		data.put("link", JsonValue.ofString(String.valueOf(node.get("link"))));
		data.put("note", JsonValue.ofString(String.valueOf(node.get("note"))));
		data.put("detailsHtml", JsonValue.ofString(String.valueOf(node.get("detailsHtml"))));
		data.put("tags", JsonValue.ofString(String.valueOf(node.get("tags"))));
		data.put("pinned", JsonValue.ofBoolean(Boolean.TRUE.equals(node.get("pinned"))));
		data.put("taskTime", JsonValue.ofNumber(((Integer) node.get("taskTime")).intValue()));
		data.put("taskLevel", JsonValue.ofNumber(((Integer) node.get("taskLevel")).intValue()));
		data.put("jinji", JsonValue.ofNumber(((Integer) node.get("jinji")).intValue()));
		data.put("remindType", JsonValue.ofString(String.valueOf(node.get("remindType"))));
		final List icons = (List) node.get("icons");
		final List<JsonValue> iconJson = new ArrayList<JsonValue>();
		for (int i = 0; i < icons.size(); i++) {
			iconJson.add(JsonValue.ofString(String.valueOf(icons.get(i))));
		}
		data.put("icons", JsonValue.ofList(iconJson));
		final List children = (List) node.get("children");
		final List<JsonValue> childJson = new ArrayList<JsonValue>();
		for (int i = 0; i < children.size(); i++) {
			childJson.add(serializeParsedNode((Map) children.get(i)));
		}
		data.put("children", JsonValue.ofList(childJson));
		return JsonValue.ofMap(data);
	}

	private static File resolveMindMapFileByPath(final String filePath) {
		if (filePath == null || filePath.trim().length() == 0) {
			throw new IllegalArgumentException("filePath is required.");
		}
		final String trimmed = filePath.trim();
		final File direct = new File(trimmed);
		if (direct.isFile() && direct.exists()) {
			return canonicalFile(direct);
		}

		final List allFiles = new ArrayList();
		MindMapDataRootResolver.collectMindmapFiles(allFiles);
		final String normalizedHint = normalizePathForMatch(trimmed);

		for (int i = 0; i < allFiles.size(); i++) {
			final File candidate = (File) allFiles.get(i);
			if (pathsEqual(normalizePathForMatch(candidate.getAbsolutePath()), normalizedHint)) {
				return canonicalFile(candidate);
			}
		}

		final List suffixMatches = new ArrayList();
		if (normalizedHint.indexOf('/') >= 0) {
			for (int i = 0; i < allFiles.size(); i++) {
				final File candidate = (File) allFiles.get(i);
				if (normalizePathForMatch(candidate.getAbsolutePath()).endsWith(normalizedHint)) {
					suffixMatches.add(candidate);
				}
			}
			if (suffixMatches.size() == 1) {
				return canonicalFile((File) suffixMatches.get(0));
			}
			if (suffixMatches.size() > 1) {
				return disambiguateCandidates(suffixMatches, trimmed);
			}
		}

		final String targetName = direct.getName();
		final List nameMatches = new ArrayList();
		for (int i = 0; i < allFiles.size(); i++) {
			final File candidate = (File) allFiles.get(i);
			if (candidate.getName().equalsIgnoreCase(targetName)) {
				nameMatches.add(candidate);
			}
		}
		if (nameMatches.isEmpty()) {
			throw new IllegalArgumentException("Mind map not found: " + filePath);
		}
		return disambiguateCandidates(nameMatches, trimmed);
	}

	private static File disambiguateCandidates(final List candidates, final String hint) {
		if (candidates.size() == 1) {
			return canonicalFile((File) candidates.get(0));
		}

		List remaining = filterOpenMapCandidates(candidates);
		if (remaining.size() == 1) {
			return canonicalFile((File) remaining.get(0));
		}

		remaining = filterSameDirectoryAsActiveMap(remaining);
		if (remaining.size() == 1) {
			return canonicalFile((File) remaining.get(0));
		}

		remaining = filterUnderSelectedProject(remaining);
		if (remaining.size() == 1) {
			return canonicalFile((File) remaining.get(0));
		}

		final File newest = pickNewestFile(remaining);
		if (newest != null && countFilesWithLastModified(remaining, newest.lastModified()) == 1) {
			return canonicalFile(newest);
		}

		final StringBuilder message = new StringBuilder();
		message.append("Ambiguous mind map path '").append(hint).append("'. Candidates:");
		for (int i = 0; i < remaining.size(); i++) {
			message.append("\n  - ").append(((File) remaining.get(i)).getAbsolutePath());
		}
		message.append("\nUse a full or partial path (e.g. project/subdir/file.mm) to disambiguate.");
		throw new IllegalArgumentException(message.toString());
	}

	private static List filterOpenMapCandidates(final List candidates) {
		final List openMatches = new ArrayList();
		try {
			final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			final Map<String, MapModel> maps = mapViewManager.getMaps(MModeController.MODENAME);
			for (final MapModel map : maps.values()) {
				final File mapFile = map.getFile();
				if (mapFile == null) {
					continue;
				}
				for (int i = 0; i < candidates.size(); i++) {
					final File candidate = (File) candidates.get(i);
					if (isSameFile(candidate, mapFile)) {
						openMatches.add(candidate);
					}
				}
			}
		}
		catch (Exception e) {
			// headless / no UI
		}
		return openMatches.isEmpty() ? candidates : openMatches;
	}

	private static List filterSameDirectoryAsActiveMap(final List candidates) {
		try {
			final MapModel map = Controller.getCurrentController().getMap();
			if (map == null || map.getFile() == null) {
				return candidates;
			}
			final File activeDir = map.getFile().getParentFile();
			if (activeDir == null) {
				return candidates;
			}
			final List sameDir = new ArrayList();
			for (int i = 0; i < candidates.size(); i++) {
				final File candidate = (File) candidates.get(i);
				final File parent = candidate.getParentFile();
				if (parent != null && isSameFile(parent, activeDir)) {
					sameDir.add(candidate);
				}
			}
			return sameDir.isEmpty() ? candidates : sameDir;
		}
		catch (Exception e) {
			return candidates;
		}
	}

	private static List filterUnderSelectedProject(final List candidates) {
		final File projectRoot = MindMapDataRootResolver.getPrimaryScanRoot();
		if (projectRoot == null) {
			return candidates;
		}
		final List underProject = new ArrayList();
		for (int i = 0; i < candidates.size(); i++) {
			final File candidate = (File) candidates.get(i);
			if (isUnderProject(candidate, projectRoot)) {
				underProject.add(candidate);
			}
		}
		return underProject.isEmpty() ? candidates : underProject;
	}

	private static File pickNewestFile(final List files) {
		File newest = null;
		for (int i = 0; i < files.size(); i++) {
			final File file = (File) files.get(i);
			if (newest == null || file.lastModified() > newest.lastModified()) {
				newest = file;
			}
		}
		return newest;
	}

	private static int countFilesWithLastModified(final List files, final long lastModified) {
		int count = 0;
		for (int i = 0; i < files.size(); i++) {
			if (((File) files.get(i)).lastModified() == lastModified) {
				count++;
			}
		}
		return count;
	}

	private static File canonicalFile(final File file) {
		try {
			return file.getCanonicalFile();
		}
		catch (Exception e) {
			return file.getAbsoluteFile();
		}
	}

	private static String normalizePathForMatch(final String path) {
		if (path == null) {
			return "";
		}
		return path.replace('\\', '/').toLowerCase();
	}

	private static boolean pathsEqual(final String left, final String right) {
		return left != null && left.equals(right);
	}

	private static File resolveMindMapFileQuiet(final String filePath) {
		return resolveMindMapFileByPath(filePath);
	}

	private static void collectMatchesInFile(final File file, final String needle, final List matches,
			final long modifiedAfterMillis) {
		if (file == null || !file.isFile() || !file.exists()) {
			return;
		}
		try {
			final SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse(file, new DefaultHandler() {
				private final List nodeStack = new ArrayList();

				public void startElement(final String uri, final String localName, final String qName,
						final Attributes attributes) {
					if (!"node".equals(qName)) {
						return;
					}
					final String id = attributes.getValue("ID");
					final String text = attributes.getValue("TEXT");
					if (id == null || text == null) {
						nodeStack.add(null);
						return;
					}
					final String plain = HtmlUtils.removeHtmlTagsFromString(text);
					final String nodeText = plain != null ? plain.trim() : "";
					final long modifiedAt = parseModifiedAt(attributes, file);
					String parentNodeId = "";
					final StringBuilder parentPath = new StringBuilder();
					for (int i = 0; i < nodeStack.size(); i++) {
						final String[] ancestor = (String[]) nodeStack.get(i);
						if (ancestor == null) {
							continue;
						}
						if (parentPath.length() > 0) {
							parentPath.append(" / ");
						}
						parentPath.append(ancestor[1]);
						parentNodeId = ancestor[0];
					}
					final int depth = nodeStack.size();
					nodeStack.add(new String[] { id, nodeText });
					if (modifiedAfterMillis > 0L && modifiedAt < modifiedAfterMillis) {
						return;
					}
					if (needle.length() > 0 && nodeText.toLowerCase().indexOf(needle) < 0) {
						return;
					}
					matches.add(new SearchMatch(file, id, nodeText, modifiedAt, parentNodeId, parentPath.toString(),
							depth));
				}

				public void endElement(final String uri, final String localName, final String qName) {
					if ("node".equals(qName) && !nodeStack.isEmpty()) {
						nodeStack.remove(nodeStack.size() - 1);
					}
				}
			});
		}
		catch (Exception e) {
			LogUtils.warn("MCP search failed for " + file.getAbsolutePath() + ": " + e.getMessage());
		}
	}

	static boolean isSameFile(final File file1, final File file2) {
		if (file1 == null || file2 == null) {
			return file1 == file2;
		}
		try {
			return file1.getCanonicalPath().equals(file2.getCanonicalPath());
		}
		catch (Exception e) {
			return file1.getAbsolutePath().equals(file2.getAbsolutePath());
		}
	}

	static boolean isSameMapFile(final MapModel map, final File file) {
		if (map == null || file == null) {
			return false;
		}
		if (isSameFile(map.getFile(), file)) {
			return true;
		}
		final URL url = map.getURL();
		if (url != null) {
			try {
				final File urlFile = Compat.urlToFile(url);
				if (urlFile != null && isSameFile(urlFile, file)) {
					return true;
				}
			}
			catch (Exception e) {
				// ignore invalid map URL
			}
		}
		return false;
	}

	static File resolveMindMapFileForWrite(final String filePath) {
		return resolveMindMapFileQuiet(filePath);
	}

	private static File resolveMindMapFile(final String filePath) {
		return resolveMindMapFileByPath(filePath);
	}

}
