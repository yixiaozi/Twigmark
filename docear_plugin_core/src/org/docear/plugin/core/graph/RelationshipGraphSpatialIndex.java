package org.docear.plugin.core.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uniform grid for O(1) average neighbor lookup during hit-testing and layout.
 */
final class RelationshipGraphSpatialIndex {

	private static final double DEFAULT_CELL = 120.0;

	private final double cellSize;
	private Map<String, List<RelationshipGraphNode>> grid = new HashMap<String, List<RelationshipGraphNode>>();

	RelationshipGraphSpatialIndex(final double cellSize) {
		this.cellSize = cellSize > 0 ? cellSize : DEFAULT_CELL;
	}

	void rebuild(final RelationshipGraphIndex index) {
		grid = new HashMap<String, List<RelationshipGraphNode>>();
		if (index == null) {
			return;
		}
		final List<RelationshipGraphNode> nodes = index.getNodes();
		for (int i = 0; i < nodes.size(); i++) {
			final RelationshipGraphNode node = nodes.get(i);
			final String key = cellKey(node.getX(), node.getY());
			List<RelationshipGraphNode> bucket = grid.get(key);
			if (bucket == null) {
				bucket = new ArrayList<RelationshipGraphNode>();
				grid.put(key, bucket);
			}
			bucket.add(node);
		}
	}

	RelationshipGraphNode findNearest(final double graphX, final double graphY, final double maxDistance) {
		final int cx = cellCoord(graphX);
		final int cy = cellCoord(graphY);
		RelationshipGraphNode best = null;
		double bestDistSq = maxDistance * maxDistance;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				final List<RelationshipGraphNode> bucket = grid.get(cellKey(cx + dx, cy + dy));
				if (bucket == null) {
					continue;
				}
				for (int i = 0; i < bucket.size(); i++) {
					final RelationshipGraphNode node = bucket.get(i);
					final double ndx = graphX - node.getX();
					final double ndy = graphY - node.getY();
					final double distSq = ndx * ndx + ndy * ndy;
					if (distSq <= bestDistSq) {
						bestDistSq = distSq;
						best = node;
					}
				}
			}
		}
		return best;
	}

	void forEachNeighbor(final double graphX, final double graphY, final NodeVisitor visitor) {
		final int cx = cellCoord(graphX);
		final int cy = cellCoord(graphY);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				final List<RelationshipGraphNode> bucket = grid.get(cellKey(cx + dx, cy + dy));
				if (bucket == null) {
					continue;
				}
				for (int i = 0; i < bucket.size(); i++) {
					visitor.visit(bucket.get(i));
				}
			}
		}
	}

	interface NodeVisitor {
		void visit(RelationshipGraphNode node);
	}

	private int cellCoord(final double value) {
		return (int) Math.floor(value / cellSize);
	}

	private String cellKey(final double x, final double y) {
		return cellKey(cellCoord(x), cellCoord(y));
	}

	private String cellKey(final int cx, final int cy) {
		return cx + ":" + cy;
	}
}
