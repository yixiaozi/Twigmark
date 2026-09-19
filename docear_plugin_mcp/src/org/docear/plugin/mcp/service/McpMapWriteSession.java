package org.docear.plugin.mcp.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.core.util.MapUtils;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mapio.mindmapmode.MMapIO;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.styles.MapStyle;
import org.freeplane.features.styles.MapStyleModel;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.features.url.mindmapmode.MFileManager;
import org.freeplane.plugin.workspace.actions.WorkspaceNewMapAction;

/**
 * Resolves a mind map for MCP write operations without requiring it to be the active UI tab.
 * Prefers an already-open map instance; otherwise loads headlessly via {@link MapUtils}.
 * Headless loads are cached briefly to avoid re-parsing the same .mm on bursty add_node calls.
 */
final class McpMapWriteSession {

	private static final int HEADLESS_CACHE_MAX = 8;
	private static final Object CACHE_LOCK = new Object();
	private static final LinkedHashMap HEADLESS_CACHE = new LinkedHashMap(16, 0.75f, true);

	private final MapModel map;
	private final File file;
	private final boolean headlessLoad;

	private McpMapWriteSession(final MapModel map, final File file, final boolean headlessLoad) {
		this.map = map;
		this.file = file;
		this.headlessLoad = headlessLoad;
	}

	static McpMapWriteSession open(final String filePath) throws Exception {
		if (filePath == null || filePath.trim().length() == 0) {
			final MapModel current = Controller.getCurrentController().getMap();
			if (current == null || current.getFile() == null) {
				throw new IllegalArgumentException(
						"No mind map is open. Provide filePath to write to a specific .mm file.");
			}
			return new McpMapWriteSession(current, current.getFile(), false);
		}
		final File file = McpMindMapService.resolveMindMapFileForWrite(filePath);
		MapModel openMap = findOpenMap(file);
		if (openMap == null) {
			try {
				WorkspaceNewMapAction.openMap(file.toURI());
				openMap = findOpenMap(file);
			}
			catch (Exception e) {
				LogUtils.warn("MCP openMap for write failed: " + e.getMessage());
			}
		}
		if (openMap != null) {
			ensureMapStyle(openMap);
			return new McpMapWriteSession(openMap, file, false);
		}
		final String cacheKey = file.getCanonicalPath();
		synchronized (CACHE_LOCK) {
			final MapModel cached = (MapModel) HEADLESS_CACHE.get(cacheKey);
			if (cached != null && isSameFile(cached.getFile(), file)) {
				ensureMapStyle(cached);
				return new McpMapWriteSession(cached, file, true);
			}
		}
		final MapModel loaded = MapUtils.getMapFromUri(file.toURI());
		if (loaded == null) {
			throw new IllegalArgumentException("Failed to load mind map: " + file.getAbsolutePath());
		}
		if (loaded.getFile() == null) {
			loaded.setURL(Compat.fileToUrl(file));
		}
		ensureMapStyle(loaded);
		synchronized (CACHE_LOCK) {
			HEADLESS_CACHE.put(cacheKey, loaded);
			while (HEADLESS_CACHE.size() > HEADLESS_CACHE_MAX) {
				final Iterator it = HEADLESS_CACHE.keySet().iterator();
				if (!it.hasNext()) {
					break;
				}
				it.next();
				it.remove();
			}
		}
		return new McpMapWriteSession(loaded, file, true);
	}

	/**
	 * Minimal / headless-created maps often have AutomaticEdgeColor but no MapStyleModel.
	 * Nested addNewNode then NPEs in LogicalStyleController via AutomaticEdgeColorHook.
	 */
	static void ensureMapStyle(final MapModel map) {
		if (map == null || map.getRootNode() == null) {
			return;
		}
		final MapStyleModel existing = MapStyleModel.getExtension(map);
		if (existing != null && existing.getStyleMap() != null) {
			return;
		}
		try {
			final MapStyle mapStyle = MapStyle.getController();
			if (mapStyle != null) {
				mapStyle.onCreate(map);
			}
		}
		catch (Exception e) {
			LogUtils.warn("MCP ensureMapStyle failed: " + e.getMessage());
		}
	}

	static void rememberHeadlessMap(final File file, final MapModel map) {
		if (file == null || map == null) {
			return;
		}
		try {
			final String cacheKey = file.getCanonicalPath();
			synchronized (CACHE_LOCK) {
				HEADLESS_CACHE.put(cacheKey, map);
				while (HEADLESS_CACHE.size() > HEADLESS_CACHE_MAX) {
					final Iterator it = HEADLESS_CACHE.keySet().iterator();
					if (!it.hasNext()) {
						break;
					}
					it.next();
					it.remove();
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn("MCP rememberHeadlessMap failed: " + e.getMessage());
		}
	}

	MapModel getMap() {
		return map;
	}

	File getFile() {
		return file;
	}

	boolean isHeadlessLoad() {
		return headlessLoad;
	}

	NodeModel requireNode(final String nodeId) {
		final NodeModel node = map.getNodeForID(nodeId);
		if (node == null) {
			final NodeModel root = map.getRootNode();
			final String rootId = root != null ? root.getID() : "";
			throw new IllegalArgumentException("Node not found: " + nodeId + " in " + file.getAbsolutePath()
					+ (rootId != null && rootId.length() > 0 ? " (rootNodeId=" + rootId + ")" : ""));
		}
		return node;
	}

	private static final ThreadLocal BATCH = new ThreadLocal();

	static final class BatchState {
		final LinkedHashMap dirty = new LinkedHashMap();
		boolean active = true;
	}

	/** Start a write batch: session.save() defers until {@link #commitBatch()}. */
	static void beginBatch() {
		BATCH.set(new BatchState());
	}

	static boolean inBatch() {
		final BatchState state = (BatchState) BATCH.get();
		return state != null && state.active;
	}

	static String commitBatch() throws Exception {
		final BatchState state = (BatchState) BATCH.get();
		if (state == null || !state.active) {
			throw new IllegalStateException("No active write batch. Call begin_write_batch first.");
		}
		final List saved = new ArrayList();
		try {
			for (final Iterator it = state.dirty.entrySet().iterator(); it.hasNext();) {
				final Map.Entry entry = (Map.Entry) it.next();
				final McpMapWriteSession session = (McpMapWriteSession) entry.getValue();
				session.saveNow();
				saved.add(session.getFile().getAbsolutePath());
			}
		}
		finally {
			BATCH.remove();
		}
		final Map result = new LinkedHashMap();
		result.put("committed", Boolean.TRUE);
		result.put("savedMaps", saved);
		result.put("count", Integer.valueOf(saved.size()));
		return org.docear.plugin.mcp.json.JsonValue.ofMap(toJsonMap(result)).toJson();
	}

	static String discardBatch() {
		BATCH.remove();
		final Map result = new LinkedHashMap();
		result.put("discarded", Boolean.TRUE);
		return org.docear.plugin.mcp.json.JsonValue.ofMap(toJsonMap(result)).toJson();
	}

	private static Map toJsonMap(final Map raw) {
		final Map out = new LinkedHashMap();
		for (final Iterator it = raw.entrySet().iterator(); it.hasNext();) {
			final Map.Entry e = (Map.Entry) it.next();
			final Object v = e.getValue();
			if (v instanceof Boolean) {
				out.put(e.getKey(), org.docear.plugin.mcp.json.JsonValue.ofBoolean(((Boolean) v).booleanValue()));
			}
			else if (v instanceof Integer) {
				out.put(e.getKey(), org.docear.plugin.mcp.json.JsonValue.ofNumber((Integer) v));
			}
			else if (v instanceof List) {
				final List list = new ArrayList();
				final List src = (List) v;
				for (int i = 0; i < src.size(); i++) {
					list.add(org.docear.plugin.mcp.json.JsonValue.ofString(String.valueOf(src.get(i))));
				}
				out.put(e.getKey(), org.docear.plugin.mcp.json.JsonValue.ofList(list));
			}
			else {
				out.put(e.getKey(), org.docear.plugin.mcp.json.JsonValue.ofString(String.valueOf(v)));
			}
		}
		return out;
	}

	static void invalidateHeadlessCache(final File file) {
		if (file == null) {
			return;
		}
		try {
			final String cacheKey = file.getCanonicalPath();
			synchronized (CACHE_LOCK) {
				HEADLESS_CACHE.remove(cacheKey);
			}
		}
		catch (Exception e) {
		}
	}

	void save() {
		final BatchState state = (BatchState) BATCH.get();
		if (state != null && state.active) {
			try {
				state.dirty.put(file.getCanonicalPath(), this);
			}
			catch (Exception e) {
				state.dirty.put(file.getAbsolutePath(), this);
			}
			return;
		}
		saveNow();
	}

	void saveNow() {
		try {
			final MFileManager fileManager = (MFileManager) MFileManager.getController();
			if (fileManager.save(map, file)) {
				invalidateSearchCaches();
				return;
			}
			final MMapIO mapIO = (MMapIO) MModeController.getMModeController().getExtension(
					org.freeplane.features.mapio.MapIO.class);
			mapIO.writeToFile(map, file);
			map.setURL(Compat.fileToUrl(file));
			map.setSaved(true);
			invalidateSearchCaches();
		}
		catch (Exception e) {
			LogUtils.warn("MCP save failed for " + file.getAbsolutePath() + ": " + e.getMessage());
			throw new RuntimeException("Failed to save mind map: " + file.getAbsolutePath(), e);
		}
	}

	private void invalidateSearchCaches() {
		try {
			org.freeplane.core.util.MindMapNodeSearchIndex.invalidate(file);
			org.freeplane.core.util.MindMapWorkspaceContextScanner.invalidateFileCache(file);
		}
		catch (Exception e) {
		}
	}

	private static MapModel findOpenMap(final File file) {
		final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
		final Map maps = mapViewManager.getMaps(MModeController.MODENAME);
		for (final Object value : maps.values()) {
			final MapModel map = (MapModel) value;
			if (McpMindMapService.isSameMapFile(map, file)) {
				return map;
			}
		}
		return null;
	}

	private static boolean isSameFile(final File a, final File b) {
		if (a == null || b == null) {
			return false;
		}
		try {
			return a.getCanonicalFile().equals(b.getCanonicalFile());
		}
		catch (Exception e) {
			return a.getAbsolutePath().equalsIgnoreCase(b.getAbsolutePath());
		}
	}
}
