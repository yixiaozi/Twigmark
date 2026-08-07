package org.docear.plugin.mcp.service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.docear.plugin.core.graph.RelationshipGraphEdge;
import org.docear.plugin.core.graph.RelationshipGraphIndex;
import org.docear.plugin.core.graph.RelationshipGraphNode;
import org.docear.plugin.core.graph.RelationshipGraphScanner;
import org.docear.plugin.mcp.json.JsonValue;

/**
 * Exposes Docear relationship-graph data to MCP (silent scan, cached).
 */
public final class McpRelationshipGraphService {

	private static final long CACHE_TTL_MS = 10L * 60L * 1000L;
	private static final RelationshipGraphIndex[] CACHED_BASE = new RelationshipGraphIndex[4];
	private static final long[] CACHE_TIME = new long[4];

	private McpRelationshipGraphService() {
	}

	public static String getRelationshipGraph(final String modeName, final String query, final String filePath,
	        final String nodeId, final int hops, final boolean showIsolated, final int maxNodes, final int maxEdges,
	        final boolean refresh) throws Exception {
		final int mode = parseMode(modeName);
		final RelationshipGraphIndex base = loadBaseIndex(mode, refresh);
		String centerKey = null;
		if (filePath != null && filePath.trim().length() > 0) {
			centerKey = resolveCenterKey(base, filePath.trim(), nodeId, mode);
		}
		else if (nodeId != null && nodeId.trim().startsWith("tag:")) {
			centerKey = nodeId.trim();
		}
		RelationshipGraphIndex index = RelationshipGraphIndex.buildDisplayIndex(base, showIsolated, query, null,
		        Math.max(1, hops), null);
		if (centerKey != null && centerKey.length() > 0 && index != null) {
			index = RelationshipGraphIndex.filterEgoNetwork(index, centerKey, Math.max(1, hops));
		}
		return serializeGraph(mode, base, index, maxNodes, maxEdges, refresh, query, centerKey);
	}

	public static String getNodeRelationships(final String filePath, final String nodeId, final int hops,
	        final String modeName, final int maxNodes, final int maxEdges, final boolean refresh) throws Exception {
		final String mode = nodeId != null && nodeId.trim().length() > 0 ? "map_nodes" : modeName;
		return getRelationshipGraph(mode, "", filePath, nodeId, Math.max(1, hops), false, maxNodes, maxEdges, refresh);
	}

	public static String getGraphSummary(final boolean refresh) throws Exception {
		final Map<String, JsonValue> root = new LinkedHashMap<String, JsonValue>();
		// Fast modes only by default — map_nodes/tags are full-library SAX and must be
		// requested explicitly via get_relationship_graph (unless already warm in cache).
		root.put("mapFiles", JsonValue.ofMap(buildModeSummary(RelationshipGraphScanner.MODE_MAP_FILES, refresh)));
		root.put("favorites", JsonValue.ofMap(buildModeSummary(RelationshipGraphScanner.MODE_FAVORITES, refresh)));
		root.put("mapNodes", JsonValue.ofMap(summaryOrSkipped(RelationshipGraphScanner.MODE_MAP_NODES, refresh)));
		root.put("tags", JsonValue.ofMap(summaryOrSkipped(RelationshipGraphScanner.MODE_TAGS, refresh)));
		return JsonValue.ofMap(root).toJson();
	}

	private static Map<String, JsonValue> summaryOrSkipped(final int mode, final boolean refresh) throws Exception {
		if (!refresh && isCacheFresh(mode)) {
			return buildModeSummary(mode, false);
		}
		final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
		item.put("mode", JsonValue.ofString(modeName(mode)));
		item.put("skipped", JsonValue.ofBoolean(true));
		item.put("reason", JsonValue.ofString(
				"Heavy mode omitted from summary; call get_relationship_graph with mode=" + modeName(mode)));
		item.put("cached", JsonValue.ofBoolean(false));
		return item;
	}

	private static Map<String, JsonValue> buildModeSummary(final int mode, final boolean refresh) throws Exception {
		final RelationshipGraphIndex base = loadBaseIndex(mode, refresh);
		final RelationshipGraphIndex display = RelationshipGraphIndex.buildDisplayIndex(base, false, "", null, 1, null);
		final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
		item.put("mode", JsonValue.ofString(modeName(mode)));
		item.put("totalScanned", JsonValue.ofNumber(base.getTotalNodeCount()));
		item.put("connectedNodes", JsonValue.ofNumber(display != null ? display.getNodeCount() : 0));
		item.put("edges", JsonValue.ofNumber(display != null ? display.getEdgeCount() : 0));
		item.put("cached", JsonValue.ofBoolean(!refresh && isCacheFresh(mode)));
		return item;
	}

	private static String serializeGraph(final int mode, final RelationshipGraphIndex base,
	        final RelationshipGraphIndex index, final int maxNodes, final int maxEdges, final boolean refreshed,
	        final String query, final String centerKey) throws Exception {
		final Map<String, JsonValue> root = new LinkedHashMap<String, JsonValue>();
		root.put("mode", JsonValue.ofString(modeName(mode)));
		root.put("cached", JsonValue.ofBoolean(!refreshed && isCacheFresh(mode)));
		root.put("totalScanned", JsonValue.ofNumber(base.getTotalNodeCount()));
		if (index == null) {
			root.put("nodeCount", JsonValue.ofNumber(0));
			root.put("edgeCount", JsonValue.ofNumber(0));
			root.put("nodes", JsonValue.ofList(new ArrayList<JsonValue>()));
			root.put("edges", JsonValue.ofList(new ArrayList<JsonValue>()));
			return JsonValue.ofMap(root).toJson();
		}
		root.put("nodeCount", JsonValue.ofNumber(index.getNodeCount()));
		root.put("edgeCount", JsonValue.ofNumber(index.getEdgeCount()));
		if (query != null && query.trim().length() > 0) {
			root.put("query", JsonValue.ofString(query.trim()));
		}
		if (centerKey != null) {
			root.put("centerKey", JsonValue.ofString(centerKey));
		}

		final List<RelationshipGraphNode> allNodes = index.getNodes();
		final int nodeLimit = maxNodes > 0 ? maxNodes : 100;
		final List<JsonValue> nodeJson = new ArrayList<JsonValue>();
		final Set<String> includedKeys = new HashSet<String>();
		for (int i = 0; i < allNodes.size() && nodeJson.size() < nodeLimit; i++) {
			final RelationshipGraphNode node = allNodes.get(i);
			includedKeys.add(node.getPathKey());
			nodeJson.add(nodeToJson(node));
		}
		root.put("nodes", JsonValue.ofList(nodeJson));
		root.put("nodesTruncated", JsonValue.ofBoolean(allNodes.size() > nodeJson.size()));

		final List<RelationshipGraphEdge> allEdges = index.getEdges();
		final int edgeLimit = maxEdges > 0 ? maxEdges : 200;
		final List<JsonValue> edgeJson = new ArrayList<JsonValue>();
		for (int i = 0; i < allEdges.size() && edgeJson.size() < edgeLimit; i++) {
			final RelationshipGraphEdge edge = allEdges.get(i);
			if (!includedKeys.contains(edge.getSource().getPathKey())
			        || !includedKeys.contains(edge.getTarget().getPathKey())) {
				continue;
			}
			edgeJson.add(edgeToJson(edge));
		}
		root.put("edges", JsonValue.ofList(edgeJson));
		root.put("edgesTruncated", JsonValue.ofBoolean(allEdges.size() > edgeJson.size() || allNodes.size() > nodeJson.size()));
		return JsonValue.ofMap(root).toJson();
	}

	private static JsonValue nodeToJson(final RelationshipGraphNode node) throws Exception {
		final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
		item.put("key", JsonValue.ofString(node.getPathKey()));
		item.put("label", JsonValue.ofString(node.getLabel()));
		if (node.isTagNode()) {
			item.put("kind", JsonValue.ofString("tag"));
			item.put("tag", JsonValue.ofString(node.getTagName()));
		}
		else if (node.isMapNode()) {
			item.put("kind", JsonValue.ofString("node"));
		}
		else {
			item.put("kind", JsonValue.ofString("map"));
		}
		item.put("mapFile", JsonValue.ofString(pathOf(node.getFile())));
		if (node.getNodeId() != null && node.getNodeId().length() > 0) {
			item.put("nodeId", JsonValue.ofString(node.getNodeId()));
		}
		if (node.getMapLabel() != null && node.getMapLabel().length() > 0) {
			item.put("mapLabel", JsonValue.ofString(node.getMapLabel()));
		}
		final java.net.URL openUrl = node.getOpenUrl();
		if (openUrl != null) {
			item.put("openUrl", JsonValue.ofString(openUrl.toString()));
		}
		return JsonValue.ofMap(item);
	}

	private static JsonValue edgeToJson(final RelationshipGraphEdge edge) {
		final Map<String, JsonValue> item = new LinkedHashMap<String, JsonValue>();
		item.put("source", JsonValue.ofString(edge.getSource().getPathKey()));
		item.put("target", JsonValue.ofString(edge.getTarget().getPathKey()));
		return JsonValue.ofMap(item);
	}

	private static synchronized RelationshipGraphIndex loadBaseIndex(final int mode, final boolean refresh)
	        throws Exception {
		if (!refresh && CACHED_BASE[mode] != null && isCacheFresh(mode)) {
			return CACHED_BASE[mode];
		}
		final RelationshipGraphIndex index = RelationshipGraphScanner.scan(mode);
		CACHED_BASE[mode] = index;
		CACHE_TIME[mode] = System.currentTimeMillis();
		return index;
	}

	private static boolean isCacheFresh(final int mode) {
		return CACHED_BASE[mode] != null && System.currentTimeMillis() - CACHE_TIME[mode] < CACHE_TTL_MS;
	}

	private static String resolveCenterKey(final RelationshipGraphIndex base, final String filePath, final String nodeId,
	        final int mode) throws Exception {
		if (mode == RelationshipGraphScanner.MODE_TAGS || mode == RelationshipGraphScanner.MODE_FAVORITES) {
			if (nodeId != null && nodeId.trim().length() > 0) {
				final String tag = nodeId.trim();
				if (tag.startsWith("tag:")) {
					return tag;
				}
				return "tag:" + tag;
			}
		}
		final File file = McpMindMapService.resolveMindMapFileForWrite(filePath);
		if (file == null || !file.exists()) {
			throw new IllegalArgumentException("Mind map not found: " + filePath);
		}
		final String wantNodeId = nodeId == null ? "" : nodeId.trim();
		final List<RelationshipGraphNode> nodes = base.getNodes();
		for (int i = 0; i < nodes.size(); i++) {
			final RelationshipGraphNode node = nodes.get(i);
			if (!sameFile(node.getFile(), file)) {
				continue;
			}
			if (mode == RelationshipGraphScanner.MODE_MAP_FILES && wantNodeId.length() == 0) {
				if (node.getNodeId() == null || node.getNodeId().length() == 0) {
					return node.getPathKey();
				}
			}
			if (wantNodeId.length() > 0 && wantNodeId.equals(node.getNodeId())) {
				return node.getPathKey();
			}
			if ((mode == RelationshipGraphScanner.MODE_FAVORITES || mode == RelationshipGraphScanner.MODE_MAP_FILES)
			        && wantNodeId.length() == 0 && !node.isTagNode() && !node.isMapNode()) {
				return node.getPathKey();
			}
		}
		if (wantNodeId.length() == 0) {
			return pathOf(file);
		}
		return pathOf(file) + "#" + wantNodeId;
	}

	private static boolean sameFile(final File a, final File b) {
		if (a == null || b == null) {
			return false;
		}
		return pathOf(a).equalsIgnoreCase(pathOf(b));
	}

	private static String pathOf(final File file) {
		if (file == null) {
			return "";
		}
		return file.getAbsolutePath().replace('\\', '/');
	}

	private static int parseMode(final String modeName) {
		if (modeName == null) {
			return RelationshipGraphScanner.MODE_MAP_FILES;
		}
		final String lower = modeName.trim().toLowerCase(Locale.ENGLISH);
		if ("map_nodes".equals(lower) || "nodes".equals(lower) || "node".equals(lower)) {
			return RelationshipGraphScanner.MODE_MAP_NODES;
		}
		if ("tags".equals(lower) || "tag".equals(lower)) {
			return RelationshipGraphScanner.MODE_TAGS;
		}
		if ("favorites".equals(lower) || "favorite".equals(lower) || "fav".equals(lower)) {
			return RelationshipGraphScanner.MODE_FAVORITES;
		}
		return RelationshipGraphScanner.MODE_MAP_FILES;
	}

	private static String modeName(final int mode) {
		if (mode == RelationshipGraphScanner.MODE_MAP_NODES) {
			return "map_nodes";
		}
		if (mode == RelationshipGraphScanner.MODE_TAGS) {
			return "tags";
		}
		if (mode == RelationshipGraphScanner.MODE_FAVORITES) {
			return "favorites";
		}
		return "map_files";
	}
}
