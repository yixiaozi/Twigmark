package org.docear.plugin.core.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RelationshipGraphIndex {

	private final List<RelationshipGraphNode> nodes;
	private final List<RelationshipGraphEdge> edges;
	private final int totalNodeCount;
	private final int graphMode;

	public RelationshipGraphIndex(final List<RelationshipGraphNode> nodes, final List<RelationshipGraphEdge> edges) {
		this(nodes, edges, nodes.size(), RelationshipGraphScanner.MODE_MAP_FILES);
	}

	RelationshipGraphIndex(final List<RelationshipGraphNode> nodes, final List<RelationshipGraphEdge> edges,
	        final int totalNodeCount) {
		this(nodes, edges, totalNodeCount, RelationshipGraphScanner.MODE_MAP_FILES);
	}

	RelationshipGraphIndex(final List<RelationshipGraphNode> nodes, final List<RelationshipGraphEdge> edges,
	        final int totalNodeCount, final int graphMode) {
		this.nodes = Collections.unmodifiableList(new ArrayList<RelationshipGraphNode>(nodes));
		this.edges = Collections.unmodifiableList(new ArrayList<RelationshipGraphEdge>(edges));
		this.totalNodeCount = totalNodeCount;
		this.graphMode = graphMode;
	}

	public int getGraphMode() {
		return graphMode;
	}

	public List<RelationshipGraphNode> getNodes() {
		return nodes;
	}

	public List<RelationshipGraphEdge> getEdges() {
		return edges;
	}

	public int getNodeCount() {
		return nodes.size();
	}

	public int getEdgeCount() {
		return edges.size();
	}

	public int getTotalNodeCount() {
		return totalNodeCount;
	}

	public static RelationshipGraphIndex withoutIsolatedNodes(final RelationshipGraphIndex index) {
		final Set<String> connected = new HashSet<String>();
		final List<RelationshipGraphEdge> edges = index.getEdges();
		for (int i = 0; i < edges.size(); i++) {
			final RelationshipGraphEdge edge = edges.get(i);
			connected.add(edge.getSource().getPathKey());
			connected.add(edge.getTarget().getPathKey());
		}
		final List<RelationshipGraphNode> nodes = new ArrayList<RelationshipGraphNode>();
		final List<RelationshipGraphNode> allNodes = index.getNodes();
		for (int i = 0; i < allNodes.size(); i++) {
			final RelationshipGraphNode node = allNodes.get(i);
			if (connected.contains(node.getPathKey())) {
				nodes.add(node);
			}
		}
		final List<RelationshipGraphEdge> keptEdges = new ArrayList<RelationshipGraphEdge>(edges);
		return copyWith(nodes, keptEdges, index.getTotalNodeCount(), index.getGraphMode());
	}
	public static RelationshipGraphIndex filterBySearch(final RelationshipGraphIndex index, final String rawQuery,
	        final boolean includeNeighbors) {
		final String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ENGLISH);
		if (query.length() == 0) {
			return index;
		}
		final Set<String> matchedKeys = new HashSet<String>();
		final List<RelationshipGraphNode> allNodes = index.getNodes();
		for (int i = 0; i < allNodes.size(); i++) {
			final RelationshipGraphNode node = allNodes.get(i);
			if (nodeMatchesQuery(node, query)) {
				matchedKeys.add(node.getPathKey());
			}
		}
		if (matchedKeys.isEmpty()) {
			return copyWith(new ArrayList<RelationshipGraphNode>(), new ArrayList<RelationshipGraphEdge>(),
			        index.getTotalNodeCount(), index.getGraphMode());
		}
		final Set<String> visibleKeys = new HashSet<String>(matchedKeys);
		if (includeNeighbors) {
			final List<RelationshipGraphEdge> edges = index.getEdges();
			for (int i = 0; i < edges.size(); i++) {
				final RelationshipGraphEdge edge = edges.get(i);
				final String sourceKey = edge.getSource().getPathKey();
				final String targetKey = edge.getTarget().getPathKey();
				if (matchedKeys.contains(sourceKey)) {
					visibleKeys.add(targetKey);
				}
				if (matchedKeys.contains(targetKey)) {
					visibleKeys.add(sourceKey);
				}
			}
		}
		final List<RelationshipGraphNode> nodes = new ArrayList<RelationshipGraphNode>();
		for (int i = 0; i < allNodes.size(); i++) {
			final RelationshipGraphNode node = allNodes.get(i);
			if (visibleKeys.contains(node.getPathKey())) {
				nodes.add(node);
			}
		}
		final List<RelationshipGraphEdge> keptEdges = new ArrayList<RelationshipGraphEdge>();
		final List<RelationshipGraphEdge> edges = index.getEdges();
		for (int i = 0; i < edges.size(); i++) {
			final RelationshipGraphEdge edge = edges.get(i);
			if (visibleKeys.contains(edge.getSource().getPathKey()) && visibleKeys.contains(edge.getTarget().getPathKey())) {
				keptEdges.add(edge);
			}
		}
		return copyWith(nodes, keptEdges, index.getTotalNodeCount(), index.getGraphMode());
	}

	public static RelationshipGraphIndex filterEgoNetwork(final RelationshipGraphIndex index, final String centerKey,
	        final int hops) {
		if (centerKey == null || centerKey.length() == 0 || hops < 0) {
			return index;
		}
		final Set<String> visibleKeys = new HashSet<String>();
		visibleKeys.add(centerKey);
		Set<String> frontier = new HashSet<String>();
		frontier.add(centerKey);
		for (int hop = 0; hop < hops; hop++) {
			final Set<String> next = new HashSet<String>();
			final List<RelationshipGraphEdge> edges = index.getEdges();
			for (int i = 0; i < edges.size(); i++) {
				final RelationshipGraphEdge edge = edges.get(i);
				final String sourceKey = edge.getSource().getPathKey();
				final String targetKey = edge.getTarget().getPathKey();
				if (frontier.contains(sourceKey)) {
					next.add(targetKey);
				}
				if (frontier.contains(targetKey)) {
					next.add(sourceKey);
				}
			}
			visibleKeys.addAll(next);
			frontier = next;
			if (frontier.isEmpty()) {
				break;
			}
		}
		final List<RelationshipGraphNode> nodes = new ArrayList<RelationshipGraphNode>();
		final List<RelationshipGraphNode> allNodes = index.getNodes();
		for (int i = 0; i < allNodes.size(); i++) {
			final RelationshipGraphNode node = allNodes.get(i);
			if (visibleKeys.contains(node.getPathKey())) {
				nodes.add(node);
			}
		}
		final List<RelationshipGraphEdge> keptEdges = new ArrayList<RelationshipGraphEdge>();
		final List<RelationshipGraphEdge> edges = index.getEdges();
		for (int i = 0; i < edges.size(); i++) {
			final RelationshipGraphEdge edge = edges.get(i);
			if (visibleKeys.contains(edge.getSource().getPathKey()) && visibleKeys.contains(edge.getTarget().getPathKey())) {
				keptEdges.add(edge);
			}
		}
		return copyWith(nodes, keptEdges, index.getTotalNodeCount(), index.getGraphMode());
	}

	/**
	 * Keep tag hubs in {@code allowedTagNames} plus any nodes directly connected to those hubs.
	 * Pass {@code null} to skip filtering. Pass an empty set to show an empty graph.
	 */
	public static RelationshipGraphIndex filterByAllowedTags(final RelationshipGraphIndex index,
	        final Set allowedTagNames) {
		if (index == null || allowedTagNames == null) {
			return index;
		}
		final Set<String> visibleKeys = new HashSet<String>();
		final List<RelationshipGraphNode> allNodes = index.getNodes();
		for (int i = 0; i < allNodes.size(); i++) {
			final RelationshipGraphNode node = allNodes.get(i);
			if (node.isTagNode() && allowedTagNames.contains(node.getTagName())) {
				visibleKeys.add(node.getPathKey());
			}
		}
		final List<RelationshipGraphEdge> edges = index.getEdges();
		for (int i = 0; i < edges.size(); i++) {
			final RelationshipGraphEdge edge = edges.get(i);
			final String sourceKey = edge.getSource().getPathKey();
			final String targetKey = edge.getTarget().getPathKey();
			if (visibleKeys.contains(sourceKey)) {
				visibleKeys.add(targetKey);
			}
			if (visibleKeys.contains(targetKey)) {
				visibleKeys.add(sourceKey);
			}
		}
		final List<RelationshipGraphNode> nodes = new ArrayList<RelationshipGraphNode>();
		for (int i = 0; i < allNodes.size(); i++) {
			final RelationshipGraphNode node = allNodes.get(i);
			if (visibleKeys.contains(node.getPathKey())) {
				nodes.add(node);
			}
		}
		final List<RelationshipGraphEdge> keptEdges = new ArrayList<RelationshipGraphEdge>();
		for (int i = 0; i < edges.size(); i++) {
			final RelationshipGraphEdge edge = edges.get(i);
			if (visibleKeys.contains(edge.getSource().getPathKey()) && visibleKeys.contains(edge.getTarget().getPathKey())) {
				keptEdges.add(edge);
			}
		}
		return copyWith(nodes, keptEdges, index.getTotalNodeCount(), index.getGraphMode());
	}

	/** Applies sidebar filters; safe to call off the EDT for large graphs. */
	public static RelationshipGraphIndex buildDisplayIndex(final RelationshipGraphIndex base, final boolean showIsolated,
	        final String rawSearchQuery, final String focusCenterKey) {
		return buildDisplayIndex(base, showIsolated, rawSearchQuery, focusCenterKey, 1, null);
	}

	public static RelationshipGraphIndex buildDisplayIndex(final RelationshipGraphIndex base, final boolean showIsolated,
	        final String rawSearchQuery, final String focusCenterKey, final int focusHops,
	        final Set allowedTagNames) {
		if (base == null) {
			return null;
		}
		RelationshipGraphIndex index = base;
		if (allowedTagNames != null && (base.getGraphMode() == RelationshipGraphScanner.MODE_TAGS
		        || base.getGraphMode() == RelationshipGraphScanner.MODE_FAVORITES)) {
			index = filterByAllowedTags(index, allowedTagNames);
		}
		if (!showIsolated) {
			index = withoutIsolatedNodes(index);
		}
		if (rawSearchQuery != null && rawSearchQuery.trim().length() > 0) {
			index = filterBySearch(index, rawSearchQuery, true);
		}
		if (focusCenterKey != null && focusCenterKey.length() > 0) {
			index = filterEgoNetwork(index, focusCenterKey, Math.max(1, focusHops));
		}
		return index;
	}

	private static RelationshipGraphIndex copyWith(final List<RelationshipGraphNode> nodes,
	        final List<RelationshipGraphEdge> edges, final int totalNodeCount, final int graphMode) {
		return new RelationshipGraphIndex(nodes, edges, totalNodeCount, graphMode);
	}

	private static boolean nodeMatchesQuery(final RelationshipGraphNode node, final String query) {
		return node.getLabel().toLowerCase(Locale.ENGLISH).indexOf(query) >= 0
		        || node.getMapLabel().toLowerCase(Locale.ENGLISH).indexOf(query) >= 0
		        || node.getPathKey().toLowerCase(Locale.ENGLISH).indexOf(query) >= 0;
	}
}
