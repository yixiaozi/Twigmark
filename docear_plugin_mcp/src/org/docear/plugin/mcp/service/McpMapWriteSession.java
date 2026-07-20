package org.docear.plugin.mcp.service;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
		final MapModel openMap = findOpenMap(file);
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

	void save() {
		try {
			final MFileManager fileManager = (MFileManager) MFileManager.getController();
			if (fileManager.save(map, file)) {
				return;
			}
			final MMapIO mapIO = (MMapIO) MModeController.getMModeController().getExtension(
					org.freeplane.features.mapio.MapIO.class);
			mapIO.writeToFile(map, file);
			map.setURL(Compat.fileToUrl(file));
			map.setSaved(true);
		}
		catch (Exception e) {
			LogUtils.warn("MCP save failed for " + file.getAbsolutePath() + ": " + e.getMessage());
			throw new RuntimeException("Failed to save mind map: " + file.getAbsolutePath(), e);
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
