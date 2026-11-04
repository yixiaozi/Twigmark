package org.docear.plugin.mcp.service;

import java.awt.Color;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonParser;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.util.EdtRunner;
import org.docear.plugin.mcp.util.EdtRunner.Task;
import org.freeplane.core.util.ColorUtils;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapNodeSearchIndex;
import org.freeplane.core.util.WorkspaceSideTabScanCache;
import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.attribute.mindmapmode.MAttributeController;
import org.freeplane.features.edge.EdgeModel;
import org.freeplane.features.edge.EdgeStyle;
import org.freeplane.features.edge.mindmapmode.MEdgeController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.note.NoteModel;
import org.freeplane.features.note.mindmapmode.MNoteController;
import org.freeplane.features.styles.MapStyle;
import org.freeplane.features.styles.MapStyleModel;
import org.freeplane.features.styles.MapViewLayout;
import org.freeplane.features.text.DetailTextModel;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagService;

/**
 * File lifecycle, batch replace/update, attributes, edges, map layout, save/reload.
 */
public final class McpMapControlService {

	private static final int BULK_MAX = 200;
	private static final int REPLACE_MAX = 500;
	private static final int SEARCH_EXTRA_MAX_FILES = 40;

	private McpMapControlService() {
	}

	// ---------- 1. File lifecycle ----------

	public static String listOpenMaps() {
		final List json = new ArrayList();
		final IMapViewManager mgr = Controller.getCurrentController().getMapViewManager();
		final Map maps = mgr.getMaps(MModeController.MODENAME);
		final MapModel active = Controller.getCurrentController().getMap();
		if (maps != null) {
			for (final Iterator it = maps.entrySet().iterator(); it.hasNext();) {
				final Map.Entry entry = (Map.Entry) it.next();
				final MapModel map = (MapModel) entry.getValue();
				if (map == null) {
					continue;
				}
				final Map row = new LinkedHashMap();
				final File file = map.getFile();
				row.put("mapFile", JsonValue.ofString(file != null ? file.getAbsolutePath() : ""));
				row.put("viewKey", JsonValue.ofString(String.valueOf(entry.getKey())));
				row.put("title", JsonValue.ofString(map.getTitle() != null ? map.getTitle() : ""));
				row.put("saved", JsonValue.ofBoolean(map.isSaved()));
				row.put("active", JsonValue.ofBoolean(map == active));
				final NodeModel root = map.getRootNode();
				row.put("rootNodeId", JsonValue.ofString(root != null ? root.getID() : ""));
				json.add(JsonValue.ofMap(row));
			}
		}
		return JsonValue.ofList(json).toJson();
	}

	public static String closeMindmap(final String filePath, final boolean withoutSave) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final IMapViewManager mgr = Controller.getCurrentController().getMapViewManager();
				if (filePath != null && filePath.trim().length() > 0) {
					final File file = McpMindMapService.resolveMindMapFileForWrite(filePath.trim());
					if (!switchToOpenMap(file)) {
						final Map result = new LinkedHashMap();
						result.put("closed", JsonValue.ofBoolean(false));
						result.put("reason", JsonValue.ofString("Map is not open in UI"));
						result.put("mapFile", JsonValue.ofString(file.getAbsolutePath()));
						return JsonValue.ofMap(result).toJson();
					}
				}
				final MapModel before = Controller.getCurrentController().getMap();
				final String path = before != null && before.getFile() != null ? before.getFile().getAbsolutePath() : "";
				final boolean closed = mgr.close(withoutSave);
				if (before != null && before.getFile() != null) {
					McpMapWriteSession.invalidateHeadlessCache(before.getFile());
				}
				final Map result = new LinkedHashMap();
				result.put("closed", JsonValue.ofBoolean(closed));
				result.put("mapFile", JsonValue.ofString(path));
				result.put("withoutSave", JsonValue.ofBoolean(withoutSave));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String deleteMindmap(final String filePath, final boolean confirm) throws Exception {
		ensureWritable();
		if (!confirm) {
			throw new IllegalArgumentException("delete_mindmap requires confirm=true");
		}
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final File file = McpMindMapService.resolveMindMapFileForWrite(filePath);
				if (!file.isFile()) {
					throw new IllegalArgumentException("Mind map not found: " + file.getAbsolutePath());
				}
				boolean wasOpen = switchToOpenMap(file);
				if (wasOpen) {
					Controller.getCurrentController().getMapViewManager().close(true);
				}
				McpMapWriteSession.invalidateHeadlessCache(file);
				MindMapNodeSearchIndex.invalidate(file);
				final boolean deleted = file.delete();
				if (!deleted && file.exists()) {
					throw new IllegalStateException("Failed to delete: " + file.getAbsolutePath());
				}
				try {
					WorkspaceSideTabScanCache.invalidate();
				}
				catch (Exception e) {
				}
				final Map result = new LinkedHashMap();
				result.put("deleted", JsonValue.ofBoolean(true));
				result.put("mapFile", JsonValue.ofString(file.getAbsolutePath()));
				result.put("wasOpen", JsonValue.ofBoolean(wasOpen));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String moveMindmap(final String filePath, final String newFilePath) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final File src = McpMindMapService.resolveMindMapFileForWrite(filePath);
				if (!src.isFile()) {
					throw new IllegalArgumentException("Mind map not found: " + src.getAbsolutePath());
				}
				File dest = new File(newFilePath);
				if (!dest.isAbsolute()) {
					dest = new File(McpMindMapService.resolveMindMapFileForWrite(".").getParentFile(), newFilePath);
				}
				if (!dest.getName().toLowerCase().endsWith(".mm")) {
					dest = new File(dest.getAbsolutePath() + ".mm");
				}
				if (dest.exists()) {
					throw new IllegalArgumentException("Destination already exists: " + dest.getAbsolutePath());
				}
				final File parent = dest.getParentFile();
				if (parent != null && !parent.exists() && !parent.mkdirs()) {
					throw new IllegalStateException("Cannot create directory: " + parent.getAbsolutePath());
				}
				final boolean wasOpen = switchToOpenMap(src);
				if (wasOpen) {
					Controller.getCurrentController().getMapViewManager().close(true);
				}
				McpMapWriteSession.invalidateHeadlessCache(src);
				MindMapNodeSearchIndex.invalidate(src);
				if (!src.renameTo(dest)) {
					// fallback copy+delete
					final java.nio.channels.FileChannel in = new java.io.FileInputStream(src).getChannel();
					final java.nio.channels.FileChannel out = new java.io.FileOutputStream(dest).getChannel();
					try {
						out.transferFrom(in, 0, in.size());
					}
					finally {
						in.close();
						out.close();
					}
					if (!src.delete()) {
						LogUtils.warn("MCP moveMindmap: copied but could not delete source " + src);
					}
				}
				try {
					WorkspaceSideTabScanCache.invalidate();
				}
				catch (Exception e) {
				}
				final Map result = new LinkedHashMap();
				result.put("moved", JsonValue.ofBoolean(true));
				result.put("from", JsonValue.ofString(src.getAbsolutePath()));
				result.put("to", JsonValue.ofString(dest.getAbsolutePath()));
				result.put("mapFile", JsonValue.ofString(dest.getAbsolutePath()));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String renameMindmap(final String filePath, final String newName) throws Exception {
		if (newName == null || newName.trim().length() == 0) {
			throw new IllegalArgumentException("newName is required");
		}
		final File src = McpMindMapService.resolveMindMapFileForWrite(filePath);
		String name = newName.trim();
		if (!name.toLowerCase().endsWith(".mm")) {
			name = name + ".mm";
		}
		final File dest = new File(src.getParentFile(), name);
		return moveMindmap(src.getAbsolutePath(), dest.getAbsolutePath());
	}

	// ---------- 2. Batch / replace ----------

	public static String replaceNodeText(final String filePath, final String find, final String replace,
			final boolean regex, final String searchIn, final int limit, final boolean dryRun) throws Exception {
		ensureWritable();
		if (find == null || find.length() == 0) {
			throw new IllegalArgumentException("find is required");
		}
		final String repl = replace == null ? "" : replace;
		final int want = limit > 0 ? Math.min(limit, REPLACE_MAX) : REPLACE_MAX;
		final String scope = normalizeSearchIn(searchIn);
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final Pattern pattern = regex ? Pattern.compile(find) : null;
				final List changes = new ArrayList();
				walkReplace(session.getMap().getRootNode(), find, repl, pattern, regex, scope, want, dryRun, changes,
						session);
				if (!dryRun && !changes.isEmpty()) {
					session.save();
				}
				final Map result = McpMindMapService.writeResult(session);
				if (dryRun) {
					result.put("saved", JsonValue.ofBoolean(false));
					result.put("dryRun", JsonValue.ofBoolean(true));
				}
				result.put("changedCount", JsonValue.ofNumber(Integer.valueOf(changes.size())));
				result.put("changes", JsonValue.ofList(changes));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String bulkUpdateNodes(final String filePath, final JsonValue updatesValue, final boolean dryRun)
			throws Exception {
		ensureWritable();
		if (updatesValue == null || !updatesValue.isList()) {
			throw new IllegalArgumentException("updates must be a JSON array");
		}
		final List updates = updatesValue.asList();
		if (updates.size() > BULK_MAX) {
			throw new IllegalArgumentException("updates exceeds max " + BULK_MAX);
		}
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final List results = new ArrayList();
				int changed = 0;
				for (int i = 0; i < updates.size(); i++) {
					final JsonValue item = (JsonValue) updates.get(i);
					final Map args = item.asMap();
					final String nodeId = str(args, "nodeId");
					if (nodeId.length() == 0) {
						throw new IllegalArgumentException("updates[" + i + "].nodeId required");
					}
					final NodeModel node = session.requireNode(nodeId);
					final Map row = new LinkedHashMap();
					row.put("nodeId", JsonValue.ofString(nodeId));
					final List fields = new ArrayList();
					if (args.containsKey("text") && !((JsonValue) args.get("text")).isNull()) {
						final String text = ((JsonValue) args.get("text")).asString();
						if (!dryRun) {
							((MTextController) TextController.getController()).setNodeText(node, text);
						}
						fields.add(JsonValue.ofString("text"));
					}
					if (args.containsKey("noteHtml")) {
						final String note = ((JsonValue) args.get("noteHtml")).isNull() ? null
								: ((JsonValue) args.get("noteHtml")).asString();
						if (!dryRun) {
							((MNoteController) org.freeplane.features.note.NoteController.getController())
									.setNoteText(node, note != null && note.trim().length() > 0 ? note : null);
						}
						fields.add(JsonValue.ofString("noteHtml"));
					}
					if (args.containsKey("detailsHtml")) {
						final String details = ((JsonValue) args.get("detailsHtml")).isNull() ? ""
								: ((JsonValue) args.get("detailsHtml")).asString();
						if (!dryRun) {
							org.freeplane.features.text.mindmapmode.MTextController.getController().setDetails(
									node, details != null && details.length() > 0 ? details : null);
						}
						fields.add(JsonValue.ofString("detailsHtml"));
					}
					if (args.containsKey("folded") && !((JsonValue) args.get("folded")).isNull()) {
						final boolean folded = ((JsonValue) args.get("folded")).asBoolean();
						if (!dryRun) {
							((MMapController) Controller.getCurrentModeController().getMapController()).setFolded(node,
									folded);
						}
						fields.add(JsonValue.ofString("folded"));
					}
					row.put("fields", JsonValue.ofList(fields));
					if (!fields.isEmpty()) {
						changed++;
					}
					results.add(JsonValue.ofMap(row));
				}
				if (!dryRun && changed > 0) {
					session.save();
				}
				final Map result = McpMindMapService.writeResult(session);
				if (dryRun) {
					result.put("saved", JsonValue.ofBoolean(false));
					result.put("dryRun", JsonValue.ofBoolean(true));
				}
				result.put("changedCount", JsonValue.ofNumber(Integer.valueOf(changed)));
				result.put("updates", JsonValue.ofList(results));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String beginWriteBatch() {
		ensureWritable();
		if (McpMapWriteSession.inBatch()) {
			throw new IllegalStateException("Write batch already active");
		}
		McpMapWriteSession.beginBatch();
		final Map result = new LinkedHashMap();
		result.put("batch", JsonValue.ofBoolean(true));
		result.put("started", JsonValue.ofBoolean(true));
		return JsonValue.ofMap(result).toJson();
	}

	public static String commitWriteBatch() throws Exception {
		ensureWritable();
		return McpMapWriteSession.commitBatch();
	}

	public static String discardWriteBatch() {
		return McpMapWriteSession.discardBatch();
	}

	// ---------- 3. Attributes / edge / map layout ----------

	public static String getNodeAttributes(final String filePath, final String nodeId) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final List attrs = readAttributes(node);
				final Map result = new LinkedHashMap();
				result.put("mapFile", JsonValue.ofString(session.getFile().getAbsolutePath()));
				result.put("nodeId", JsonValue.ofString(node.getID()));
				result.put("attributes", JsonValue.ofList(attrs));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setNodeAttribute(final String filePath, final String nodeId, final String name,
			final String value) throws Exception {
		ensureWritable();
		if (name == null || name.trim().length() == 0) {
			throw new IllegalArgumentException("name is required");
		}
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final MAttributeController attr = (MAttributeController) MAttributeController.getController();
				attr.editAttribute(node, name.trim(), value);
				session.save();
				final Map result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(node.getID()));
				result.put("name", JsonValue.ofString(name.trim()));
				result.put("value", JsonValue.ofString(value != null ? value : ""));
				result.put("attributes", JsonValue.ofList(readAttributes(node)));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String removeNodeAttribute(final String filePath, final String nodeId, final String name)
			throws Exception {
		ensureWritable();
		if (name == null || name.trim().length() == 0) {
			throw new IllegalArgumentException("name is required");
		}
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final MAttributeController attr = (MAttributeController) MAttributeController.getController();
				attr.editAttribute(node, name.trim(), null);
				session.save();
				final Map result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(node.getID()));
				result.put("removed", JsonValue.ofString(name.trim()));
				result.put("attributes", JsonValue.ofList(readAttributes(node)));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setNodeEdge(final String filePath, final String nodeId, final String color,
			final Integer width, final String style) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final MEdgeController edge = (MEdgeController) MEdgeController.getController();
				if (color != null && color.trim().length() > 0) {
					edge.setColor(node, ColorUtils.stringToColor(color.trim()));
				}
				if (width != null) {
					edge.setWidth(node, width.intValue());
				}
				if (style != null && style.trim().length() > 0) {
					final EdgeStyle es = EdgeStyle.getStyle(style.trim());
					if (es == null) {
						throw new IllegalArgumentException(
								"Unknown edge style. Use: bezier, linear, sharp_bezier, sharp_linear, horizontal, hide_edge, summary");
					}
					edge.setStyle(node, es);
				}
				session.save();
				final EdgeModel model = EdgeModel.getModel(node);
				final Map result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(node.getID()));
				if (model != null) {
					result.put("edgeColor", JsonValue.ofString(
							model.getColor() != null ? ColorUtils.colorToString(model.getColor()) : ""));
					result.put("edgeWidth", JsonValue.ofNumber(Integer.valueOf(model.getWidth())));
					result.put("edgeStyle",
							JsonValue.ofString(model.getStyle() != null ? model.getStyle().toString() : ""));
				}
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setMapLayout(final String filePath, final String layout) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final MapViewLayout viewLayout = parseLayout(layout);
				MapStyle.getController().setMapViewLayout(session.getMap(), viewLayout);
				session.save();
				final Map result = McpMindMapService.writeResult(session);
				result.put("layout", JsonValue.ofString(viewLayout.name()));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setMapBackground(final String filePath, final String color) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final MapStyleModel model = MapStyleModel.getExtension(session.getMap());
				if (model == null) {
					throw new IllegalStateException("Map has no MapStyleModel");
				}
				final Color c = color == null || color.trim().length() == 0 ? null
						: ColorUtils.stringToColor(color.trim());
				MapStyle.getController().setBackgroundColor(model, c);
				session.save();
				final Map result = McpMindMapService.writeResult(session);
				result.put("backgroundColor",
						JsonValue.ofString(c != null ? ColorUtils.colorToString(c) : ""));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setMapProperty(final String filePath, final String key, final String value) throws Exception {
		ensureWritable();
		if (key == null || key.trim().length() == 0) {
			throw new IllegalArgumentException("key is required");
		}
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final MapStyleModel model = MapStyleModel.getExtension(session.getMap());
				if (model == null) {
					throw new IllegalStateException("Map has no MapStyleModel");
				}
				model.setProperty(key.trim(), value);
				session.getMap().setSaved(false);
				session.save();
				final Map result = McpMindMapService.writeResult(session);
				result.put("key", JsonValue.ofString(key.trim()));
				result.put("value", JsonValue.ofString(value != null ? value : ""));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String getMapProperties(final String filePath) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final MapStyleModel model = MapStyleModel.getExtension(session.getMap());
				final Map result = new LinkedHashMap();
				result.put("mapFile", JsonValue.ofString(session.getFile().getAbsolutePath()));
				if (model != null) {
					result.put("layout", JsonValue.ofString(model.getMapViewLayout().name()));
					result.put("backgroundColor", JsonValue.ofString(
							model.getBackgroundColor() != null ? ColorUtils.colorToString(model.getBackgroundColor())
									: ""));
				}
				else {
					result.put("layout", JsonValue.ofString("MAP"));
					result.put("backgroundColor", JsonValue.ofString(""));
				}
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	// ---------- 4. Save / reload / select ----------

	public static String saveMap(final String filePath) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				session.saveNow();
				final Map result = McpMindMapService.writeResult(session);
				result.put("action", JsonValue.ofString("save"));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String saveMapAs(final String filePath, final String newFilePath) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				File dest = new File(newFilePath);
				if (!dest.isAbsolute()) {
					dest = new File(session.getFile().getParentFile(), newFilePath);
				}
				if (!dest.getName().toLowerCase().endsWith(".mm")) {
					dest = new File(dest.getAbsolutePath() + ".mm");
				}
				final File parent = dest.getParentFile();
				if (parent != null && !parent.exists()) {
					parent.mkdirs();
				}
				final org.freeplane.features.mapio.mindmapmode.MMapIO mapIO =
						(org.freeplane.features.mapio.mindmapmode.MMapIO) MModeController.getMModeController()
								.getExtension(org.freeplane.features.mapio.MapIO.class);
				mapIO.writeToFile(session.getMap(), dest);
				MindMapNodeSearchIndex.invalidate(dest);
				final Map result = new LinkedHashMap();
				result.put("savedAs", JsonValue.ofBoolean(true));
				result.put("from", JsonValue.ofString(session.getFile().getAbsolutePath()));
				result.put("mapFile", JsonValue.ofString(dest.getAbsolutePath()));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String reloadMap(final String filePath) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final File file = McpMindMapService.resolveMindMapFileForWrite(
						filePath != null && filePath.trim().length() > 0 ? filePath : "");
				final File target = filePath == null || filePath.trim().length() == 0
						? (Controller.getCurrentController().getMap() != null
								? Controller.getCurrentController().getMap().getFile()
								: null)
						: file;
				if (target == null) {
					throw new IllegalArgumentException("No map to reload; provide filePath");
				}
				McpMapWriteSession.invalidateHeadlessCache(target);
				MindMapNodeSearchIndex.invalidate(target);
				final boolean wasOpen = switchToOpenMap(target);
				if (wasOpen) {
					Controller.getCurrentController().getMapViewManager().close(true);
					final URL url = target.toURI().toURL();
					Controller.getCurrentModeController().getMapController().newMap(url);
				}
				// warm headless cache with fresh load
				McpMapWriteSession.open(target.getAbsolutePath());
				final Map result = new LinkedHashMap();
				result.put("reloaded", JsonValue.ofBoolean(true));
				result.put("mapFile", JsonValue.ofString(target.getAbsolutePath()));
				result.put("reopenedUi", JsonValue.ofBoolean(wasOpen));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String selectNode(final String filePath, final String nodeId) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final File file = session.getFile();
				final URL url = file.toURI().toURL();
				final IMapViewManager mgr = Controller.getCurrentController().getMapViewManager();
				if (!mgr.tryToChangeToMapView(url)) {
					Controller.getCurrentModeController().getMapController().newMap(url);
				}
				Controller.getCurrentModeController().getMapController().select(node);
				final Map result = new LinkedHashMap();
				result.put("selected", JsonValue.ofBoolean(true));
				result.put("mapFile", JsonValue.ofString(file.getAbsolutePath()));
				result.put("nodeId", JsonValue.ofString(node.getID()));
				result.put("nodeText", JsonValue
						.ofString(TextController.getController().getPlainTextContent(node)));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	// ---------- helpers ----------

	static List searchExtraFields(final List files, final String query, final String searchIn, final int limit,
			final long modifiedAfterMillis) {
		final String scope = normalizeSearchIn(searchIn);
		if ("text".equals(scope) || query == null || query.trim().length() == 0) {
			return java.util.Collections.EMPTY_LIST;
		}
		final String needle = query.trim().toLowerCase();
		final int want = limit > 0 ? limit : 50;
		final List hits = new ArrayList();
		final int maxFiles = Math.min(files != null ? files.size() : 0, SEARCH_EXTRA_MAX_FILES);
		for (int i = 0; i < maxFiles && hits.size() < want; i++) {
			final File file = (File) files.get(i);
			if (file == null || !file.isFile()) {
				continue;
			}
			if (modifiedAfterMillis > 0L) {
				try {
					if (file.lastModified() < modifiedAfterMillis) {
						continue;
					}
				}
				catch (Exception e) {
					continue;
				}
			}
			try {
				final McpMapWriteSession session = McpMapWriteSession.open(file.getAbsolutePath());
				collectFieldHits(session.getMap().getRootNode(), file, needle, scope, want, hits);
			}
			catch (Exception e) {
				LogUtils.warn("MCP searchExtraFields failed for " + file + ": " + e.getMessage());
			}
		}
		return hits;
	}

	/** Recursive collector for note/details/tags/text field search. */
	static void collectFieldHits(final NodeModel node, final File file, final String needle, final String scope,
			final int want, final List hits) {
		if (node == null || hits.size() >= want) {
			return;
		}
		boolean match = false;
		if ("note".equals(scope) || "all".equals(scope)) {
			final String note = NoteModel.getNoteText(node);
			if (note != null && HtmlUtils.htmlToPlain(note).toLowerCase().indexOf(needle) >= 0) {
				match = true;
			}
		}
		if (!match && ("details".equals(scope) || "all".equals(scope))) {
			final String details = DetailTextModel.getDetailTextText(node);
			if (details != null && HtmlUtils.htmlToPlain(details).toLowerCase().indexOf(needle) >= 0) {
				match = true;
			}
		}
		if (!match && ("tags".equals(scope) || "all".equals(scope))) {
			final Set tags = NodeDetailsTagService.getUserTags(node);
			if (tags != null) {
				for (final Iterator it = tags.iterator(); it.hasNext();) {
					final Object tag = it.next();
					if (tag != null && String.valueOf(tag).toLowerCase().indexOf(needle) >= 0) {
						match = true;
						break;
					}
				}
			}
		}
		if (!match && ("text".equals(scope) || "all".equals(scope))) {
			final String text = TextController.getController().getPlainTextContent(node);
			if (text != null && text.toLowerCase().indexOf(needle) >= 0) {
				match = true;
			}
		}
		if (match) {
			final String text = TextController.getController().getPlainTextContent(node);
			long modified = 0L;
			try {
				if (node.getHistoryInformation() != null && node.getHistoryInformation().getLastModifiedAt() != null) {
					modified = node.getHistoryInformation().getLastModifiedAt().getTime();
				}
			}
			catch (Exception e) {
			}
			hits.add(new MindMapNodeSearchIndex.Hit(file, node.getID(), text != null ? text : "", modified,
					node.getParentNode() != null ? node.getParentNode().getID() : "", "", node.depth()));
		}
		for (int i = 0; i < node.getChildCount() && hits.size() < want; i++) {
			collectFieldHits((NodeModel) node.getChildAt(i), file, needle, scope, want, hits);
		}
	}

	private static void walkReplace(final NodeModel node, final String find, final String repl, final Pattern pattern,
			final boolean regex, final String scope, final int want, final boolean dryRun, final List changes,
			final McpMapWriteSession session) {
		if (node == null || changes.size() >= want) {
			return;
		}
		if ("text".equals(scope) || "all".equals(scope)) {
			final String plain = TextController.getController().getPlainTextContent(node);
			final String next = applyReplace(plain, find, repl, pattern, regex);
			if (next != null && !next.equals(plain)) {
				if (!dryRun) {
					((MTextController) TextController.getController()).setNodeText(node, next);
				}
				changes.add(changeJson(node, "text", plain, next));
			}
		}
		if (changes.size() < want && ("note".equals(scope) || "all".equals(scope))) {
			final String note = NoteModel.getNoteText(node);
			final String plain = note != null ? note : "";
			final String next = applyReplace(plain, find, repl, pattern, regex);
			if (next != null && !next.equals(plain)) {
				if (!dryRun) {
					((MNoteController) org.freeplane.features.note.NoteController.getController()).setNoteText(node,
							next.length() > 0 ? next : null);
				}
				changes.add(changeJson(node, "note", plain, next));
			}
		}
		if (changes.size() < want && ("details".equals(scope) || "all".equals(scope))) {
			final String details = DetailTextModel.getDetailTextText(node);
			final String plain = details != null ? details : "";
			final String next = applyReplace(plain, find, repl, pattern, regex);
			if (next != null && !next.equals(plain)) {
				if (!dryRun) {
					MTextController.getController().setDetails(node, next.length() > 0 ? next : null);
				}
				changes.add(changeJson(node, "details", plain, next));
			}
		}
		for (int i = 0; i < node.getChildCount() && changes.size() < want; i++) {
			walkReplace((NodeModel) node.getChildAt(i), find, repl, pattern, regex, scope, want, dryRun, changes,
					session);
		}
	}

	private static JsonValue changeJson(final NodeModel node, final String field, final String before,
			final String after) {
		final Map row = new LinkedHashMap();
		row.put("nodeId", JsonValue.ofString(node.getID()));
		row.put("field", JsonValue.ofString(field));
		row.put("before", JsonValue.ofString(truncate(before, 200)));
		row.put("after", JsonValue.ofString(truncate(after, 200)));
		return JsonValue.ofMap(row);
	}

	private static String applyReplace(final String source, final String find, final String repl,
			final Pattern pattern, final boolean regex) {
		if (source == null) {
			return null;
		}
		if (regex) {
			final Matcher m = pattern.matcher(source);
			if (!m.find()) {
				return source;
			}
			return m.replaceAll(Matcher.quoteReplacement(repl));
		}
		if (source.indexOf(find) < 0) {
			return source;
		}
		return source.replace(find, repl);
	}

	private static List readAttributes(final NodeModel node) {
		final List attrs = new ArrayList();
		final NodeAttributeTableModel model = NodeAttributeTableModel.getModel(node);
		if (model == null) {
			return attrs;
		}
		for (int i = 0; i < model.getRowCount(); i++) {
			final Attribute attribute = model.getAttribute(i);
			if (attribute == null) {
				continue;
			}
			final Map row = new LinkedHashMap();
			row.put("name", JsonValue.ofString(attribute.getName() != null ? attribute.getName() : ""));
			row.put("value", JsonValue.ofString(attribute.getValue() != null ? String.valueOf(attribute.getValue()) : ""));
			attrs.add(JsonValue.ofMap(row));
		}
		return attrs;
	}

	private static boolean switchToOpenMap(final File file) throws Exception {
		final IMapViewManager mgr = Controller.getCurrentController().getMapViewManager();
		final Map maps = mgr.getMaps(MModeController.MODENAME);
		if (maps == null) {
			return false;
		}
		for (final Iterator it = maps.values().iterator(); it.hasNext();) {
			final MapModel map = (MapModel) it.next();
			if (McpMindMapService.isSameMapFile(map, file)) {
				final URL url = file.toURI().toURL();
				return mgr.tryToChangeToMapView(url);
			}
		}
		return false;
	}

	private static MapViewLayout parseLayout(final String layout) {
		if (layout == null || layout.trim().length() == 0) {
			return MapViewLayout.MAP;
		}
		try {
			return MapViewLayout.valueOf(layout.trim().toUpperCase());
		}
		catch (Exception e) {
			throw new IllegalArgumentException("layout must be MAP or OUTLINE");
		}
	}

	static String normalizeSearchIn(final String searchIn) {
		if (searchIn == null || searchIn.trim().length() == 0) {
			return "text";
		}
		final String s = searchIn.trim().toLowerCase();
		if ("text".equals(s) || "note".equals(s) || "details".equals(s) || "tags".equals(s) || "all".equals(s)) {
			return s;
		}
		throw new IllegalArgumentException("searchIn must be text|note|details|tags|all");
	}

	private static String str(final Map args, final String key) {
		if (!args.containsKey(key) || args.get(key) == null || ((JsonValue) args.get(key)).isNull()) {
			return "";
		}
		final String v = ((JsonValue) args.get(key)).asString();
		return v != null ? v : "";
	}

	private static String truncate(final String s, final int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max) + "…";
	}

	private static void ensureWritable() {
		if (DocearMcpConfig.isReadOnly()) {
			throw new IllegalStateException("MCP is in read-only mode");
		}
	}
}
