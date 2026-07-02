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

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import org.docear.plugin.core.todoist.TodoistSyncService;
import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.util.EdtRunner;
import org.docear.plugin.mcp.util.EdtRunner.Task;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.MindMapWorkspaceContextScanner;
import org.freeplane.core.util.MindMapWorkspaceContextScanner.ModifiedItem;
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
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.features.url.UrlManager;
import org.freeplane.features.url.mindmapmode.MFileManager;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderCycleAttributes;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderHook;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderTaskAttributes;

public final class McpMindMapService {

	private static final String TODO_ICON = "hourglass";
	private static final SimpleDateFormat MODIFIED_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

	private static final class SearchMatch {
		private final File mapFile;
		private final String nodeId;
		private final String nodeText;
		private final long modifiedAt;

		private SearchMatch(final File mapFile, final String nodeId, final String nodeText, final long modifiedAt) {
			this.mapFile = mapFile;
			this.nodeId = nodeId;
			this.nodeText = nodeText;
			this.modifiedAt = modifiedAt;
		}
	}

	private McpMindMapService() {
	}

	public static String getActiveMapJson() throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() {
				final MapModel map = Controller.getCurrentController().getMap();
				if (map == null) {
					return "{\"message\":\"No mind map is open.\"}";
				}
				return serializeMap(map).toJson();
			}
		});
	}

	public static String getMindmapJson(final String filePath, final int maxDepth) throws Exception {
		final File file = resolveMindMapFileQuiet(filePath);
		if (!file.exists()) {
			throw new IllegalArgumentException("Mind map not found: " + filePath);
		}
		final Map<String, JsonValue> root = new LinkedHashMap<String, JsonValue>();
		root.put("file", JsonValue.ofString(file.getAbsolutePath()));
		root.put("root", parseMindMapFileToJson(file, maxDepth));
		return JsonValue.ofMap(root).toJson();
	}

	public static String searchNodes(final String query, final int limit, final int modifiedWithinDays) {
		final String needle = query == null ? "" : query.trim().toLowerCase();
		if (needle.length() == 0) {
			return JsonValue.ofList(Collections.EMPTY_LIST).toJson();
		}
		final List matches = modifiedWithinDays > 0
				? searchRecentModifiedNodes(needle, limit, modifiedWithinDays)
				: searchAllNodesSorted(needle, limit, 0L);
		return JsonValue.ofList(toSearchMatchJson(matches)).toJson();
	}

	public static String listRecentlyModified(final String query, final int limit, final int modifiedWithinDays) {
		final String needle = query == null ? "" : query.trim().toLowerCase();
		final List matches = searchRecentModifiedNodes(needle, limit, modifiedWithinDays > 0 ? modifiedWithinDays : 0);
		return JsonValue.ofList(toSearchMatchJson(matches)).toJson();
	}

	private static List searchRecentModifiedNodes(final String needle, final int limit, final int modifiedWithinDays) {
		final long cutoff = modifiedWithinDays > 0
				? System.currentTimeMillis() - modifiedWithinDays * MILLIS_PER_DAY
				: 0L;
		final int scanLimit = limit > 0 ? Math.max(limit * 20, 500) : 5000;
		final List scanned = MindMapWorkspaceContextScanner.scanRecentlyModified(scanLimit);
		final List matches = new ArrayList();
		for (int i = 0; i < scanned.size(); i++) {
			final ModifiedItem item = (ModifiedItem) scanned.get(i);
			if (cutoff > 0L && item.modifiedAt < cutoff) {
				break;
			}
			if (needle.length() > 0 && item.nodeText.toLowerCase().indexOf(needle) < 0) {
				continue;
			}
			matches.add(new SearchMatch(item.mapFile, item.nodeId, item.nodeText, item.modifiedAt));
			if (limit > 0 && matches.size() >= limit) {
				break;
			}
		}
		return matches;
	}

	private static List searchAllNodesSorted(final String needle, final int limit, final long modifiedAfterMillis) {
		final List files = new ArrayList();
		MindMapDataRootResolver.collectMindmapFiles(files);
		final List matches = new ArrayList();
		for (int i = 0; i < files.size(); i++) {
			collectMatchesInFile((File) files.get(i), needle, matches);
		}
		sortMatchesByModifiedDesc(matches);
		if (modifiedAfterMillis > 0L) {
			for (int i = matches.size() - 1; i >= 0; i--) {
				if (((SearchMatch) matches.get(i)).modifiedAt < modifiedAfterMillis) {
					matches.remove(i);
				}
			}
		}
		if (limit > 0 && matches.size() > limit) {
			return new ArrayList(matches.subList(0, limit));
		}
		return matches;
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
			json.add(JsonValue.ofMap(item));
		}
		return json;
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

	public static String addNode(final String parentNodeId, final String text) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() {
				final MMapController mapController = getMapController();
				final NodeModel parent = mapController.getNodeFromID(parentNodeId);
				if (parent == null) {
					throw new IllegalArgumentException("Parent node not found: " + parentNodeId);
				}
				final NodeModel node = mapController.addNewNode(parent, parent.getChildCount(), parent.isNewChildLeft());
				((MTextController) TextController.getController()).setNodeText(node, text);
				final String nodeId = node.createID();
				final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("nodeText", JsonValue.ofString(text));
				saveCurrentMapIfPossible();
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String changeNodeText(final String nodeId, final String text) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() {
				final MMapController mapController = getMapController();
				final NodeModel node = mapController.getNodeFromID(nodeId);
				if (node == null) {
					throw new IllegalArgumentException("Node not found: " + nodeId);
				}
				((MTextController) TextController.getController()).setNodeText(node, text);
				final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("nodeText", JsonValue.ofString(text));
				saveCurrentMapIfPossible();
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String removeNode(final String nodeId) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() {
				final MMapController mapController = getMapController();
				final NodeModel node = mapController.getNodeFromID(nodeId);
				if (node == null) {
					throw new IllegalArgumentException("Node not found: " + nodeId);
				}
				mapController.deleteNode(node);
				final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
				result.put("removed", JsonValue.ofBoolean(true));
				result.put("nodeId", JsonValue.ofString(nodeId));
				saveCurrentMapIfPossible();
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String createTodo(final String parentNodeId, final String text) throws Exception {
		final String created = addNode(parentNodeId, text);
		final JsonValue createdValue = JsonParserHelper.parse(created);
		final String nodeId = createdValue.asMap().get("nodeId").asString();
		return setTodoIcon(nodeId, true);
	}

	public static String completeTodo(final String nodeId) throws Exception {
		return setTodoIcon(nodeId, false);
	}

	public static String setReminder(final String nodeId, final long remindAtMillis) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() {
				final MMapController mapController = getMapController();
				final NodeModel node = mapController.getNodeFromID(nodeId);
				if (node == null) {
					throw new IllegalArgumentException("Node not found: " + nodeId);
				}
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
				final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("remindAtMillis", JsonValue.ofNumber(Long.valueOf(remindAtMillis)));
				saveCurrentMapIfPossible();
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setPriority(final String nodeId, final int level) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() {
				if (level < 1 || level > 7) {
					throw new IllegalArgumentException("Priority level must be between 1 and 7.");
				}
				final MMapController mapController = getMapController();
				final NodeModel node = mapController.getNodeFromID(nodeId);
				if (node == null) {
					throw new IllegalArgumentException("Node not found: " + nodeId);
				}
				final MIconController iconController = (MIconController) IconController.getController();
				removePriorityIcons(iconController, node);
				final MindIcon icon = MindIconFactory.create("full-" + level);
				iconController.addIcon(node, icon);
				final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("priority", JsonValue.ofNumber(Integer.valueOf(level)));
				saveCurrentMapIfPossible();
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

	private static String setTodoIcon(final String nodeId, final boolean enabled) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() {
				final MMapController mapController = getMapController();
				final NodeModel node = mapController.getNodeFromID(nodeId);
				if (node == null) {
					throw new IllegalArgumentException("Node not found: " + nodeId);
				}
				final MIconController iconController = (MIconController) IconController.getController();
				final MindIcon todoIcon = MindIconFactory.create(TODO_ICON);
				if (enabled) {
					iconController.addIcon(node, todoIcon);
				}
				else {
					removeIconByName(node, TODO_ICON);
				}
				final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
				result.put("nodeId", JsonValue.ofString(nodeId));
				result.put("todo", JsonValue.ofBoolean(enabled));
				saveCurrentMapIfPossible();
				return JsonValue.ofMap(result).toJson();
			}
		});
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

	private static JsonValue serializeMap(final MapModel map) {
		final Map<String, JsonValue> root = new LinkedHashMap<String, JsonValue>();
		root.put("file", JsonValue.ofString(map.getFile() != null ? map.getFile().getAbsolutePath() : ""));
		root.put("root", serializeNode(map.getRootNode()));
		return JsonValue.ofMap(root);
	}

	private static JsonValue serializeNode(final NodeModel node) {
		final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
		data.put("id", JsonValue.ofString(node.getID()));
		data.put("text", JsonValue.ofString(TextController.getController().getPlainTextContent(node)));
		data.put("folded", JsonValue.ofBoolean(node.isFolded()));
		final List<JsonValue> children = new ArrayList<JsonValue>();
		final MapController mapController = Controller.getCurrentModeController().getMapController();
		for (final NodeModel child : mapController.childrenUnfolded(node)) {
			children.add(serializeNode(child));
		}
		data.put("children", JsonValue.ofList(children));
		return JsonValue.ofMap(data);
	}

	private static JsonValue parseMindMapFileToJson(final File file, final int maxDepth) throws Exception {
		final SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
		final List stack = new ArrayList();
		final List roots = new ArrayList();
		saxParser.parse(file, new DefaultHandler() {
			public void startElement(final String uri, final String localName, final String qName,
					final Attributes attributes) {
				if (!"node".equals(qName)) {
					return;
				}
				final Map node = new LinkedHashMap();
				final String id = attributes.getValue("ID");
				node.put("id", id != null ? id : "");
				final String rawText = attributes.getValue("TEXT");
				final String plain = rawText != null ? HtmlUtils.removeHtmlTagsFromString(rawText) : null;
				node.put("text", plain != null ? plain.trim() : "");
				final String folded = attributes.getValue("FOLDED");
				node.put("folded", "true".equalsIgnoreCase(folded) || "folded".equalsIgnoreCase(folded));
				node.put("depth", Integer.valueOf(stack.size()));
				node.put("children", new ArrayList());
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
				stack.add(node);
			}

			public void endElement(final String uri, final String localName, final String qName) {
				if ("node".equals(qName) && !stack.isEmpty()) {
					stack.remove(stack.size() - 1);
				}
			}
		});
		if (roots.isEmpty()) {
			throw new IllegalArgumentException("No nodes found in mind map: " + file.getAbsolutePath());
		}
		return serializeParsedNode((Map) roots.get(0));
	}

	private static JsonValue serializeParsedNode(final Map node) {
		final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
		data.put("id", JsonValue.ofString(String.valueOf(node.get("id"))));
		data.put("text", JsonValue.ofString(String.valueOf(node.get("text"))));
		data.put("folded", JsonValue.ofBoolean(Boolean.TRUE.equals(node.get("folded"))));
		final List children = (List) node.get("children");
		final List<JsonValue> childJson = new ArrayList<JsonValue>();
		for (int i = 0; i < children.size(); i++) {
			childJson.add(serializeParsedNode((Map) children.get(i)));
		}
		data.put("children", JsonValue.ofList(childJson));
		return JsonValue.ofMap(data);
	}

	private static File resolveMindMapFileQuiet(final String filePath) {
		File file = new File(filePath);
		if (file.exists()) {
			try {
				return file.getCanonicalFile();
			}
			catch (Exception e) {
				return file.getAbsoluteFile();
			}
		}
		final String targetName = file.getName();
		if (targetName.length() > 0) {
			final List candidates = new ArrayList();
			MindMapDataRootResolver.collectMindmapFiles(candidates);
			for (int i = 0; i < candidates.size(); i++) {
				final File candidate = (File) candidates.get(i);
				if (candidate.getName().equalsIgnoreCase(targetName)) {
					return candidate;
				}
			}
		}
		throw new IllegalArgumentException("Mind map not found: " + filePath);
	}

	private static void collectMatchesInFile(final File file, final String needle, final List matches) {
		if (file == null || !file.isFile() || !file.exists()) {
			return;
		}
		try {
			final SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse(file, new DefaultHandler() {
				public void startElement(final String uri, final String localName, final String qName,
						final Attributes attributes) {
					if (!"node".equals(qName)) {
						return;
					}
					final String id = attributes.getValue("ID");
					final String text = attributes.getValue("TEXT");
					if (id == null || text == null) {
						return;
					}
					final String plain = HtmlUtils.removeHtmlTagsFromString(text);
					if (plain != null && plain.toLowerCase().indexOf(needle) >= 0) {
						matches.add(new SearchMatch(file, id, plain.trim(), parseModifiedAt(attributes, file)));
					}
				}
			});
		}
		catch (Exception e) {
			LogUtils.warn("MCP search failed for " + file.getAbsolutePath() + ": " + e.getMessage());
		}
	}

	private static boolean isSameFile(final File file1, final File file2) {
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

	private static File resolveMindMapFile(final String filePath) {
		File file = new File(filePath);
		if (file.exists()) {
			try {
				return file.getCanonicalFile();
			}
			catch (Exception e) {
				return file.getAbsoluteFile();
			}
		}
		final String targetName = file.getName();
		if (targetName.length() > 0) {
			final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			final Map<String, MapModel> maps = mapViewManager.getMaps(MModeController.MODENAME);
			for (final MapModel map : maps.values()) {
				if (map.getFile() != null && map.getFile().getName().equalsIgnoreCase(targetName)) {
					return map.getFile();
				}
			}
			final List candidates = new ArrayList();
			MindMapDataRootResolver.collectMindmapFiles(candidates);
			for (int i = 0; i < candidates.size(); i++) {
				final File candidate = (File) candidates.get(i);
				if (candidate.getName().equalsIgnoreCase(targetName)) {
					return candidate;
				}
			}
		}
		throw new IllegalArgumentException("Mind map not found: " + filePath);
	}

	private static void saveCurrentMapIfPossible() {
		final MapModel map = Controller.getCurrentController().getMap();
		if (map == null || map.getFile() == null) {
			return;
		}
		((MFileManager) UrlManager.getController()).save(map, map.getFile());
	}
}
