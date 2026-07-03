package org.docear.plugin.mcp.service;

import java.io.File;
import java.util.Map;

import org.docear.plugin.core.util.MapUtils;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mapio.mindmapmode.MMapIO;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.features.url.mindmapmode.MFileManager;

/**
 * Resolves a mind map for MCP write operations without requiring it to be the active UI tab.
 * Prefers an already-open map instance; otherwise loads headlessly via {@link MapUtils}.
 */
final class McpMapWriteSession {

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
			return new McpMapWriteSession(openMap, file, false);
		}
		final MapModel loaded = MapUtils.getMapFromUri(file.toURI());
		if (loaded == null) {
			throw new IllegalArgumentException("Failed to load mind map: " + file.getAbsolutePath());
		}
		if (loaded.getFile() == null) {
			loaded.setURL(Compat.fileToUrl(file));
		}
		return new McpMapWriteSession(loaded, file, true);
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
			throw new IllegalArgumentException("Node not found: " + nodeId + " in " + file.getAbsolutePath());
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
		final Map<String, MapModel> maps = mapViewManager.getMaps(MModeController.MODENAME);
		for (final MapModel map : maps.values()) {
			if (McpMindMapService.isSameMapFile(map, file)) {
				return map;
			}
		}
		return null;
	}
}
