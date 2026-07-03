package org.docear.plugin.core.graph;

import java.util.List;
import java.util.Random;

/**
 * Force-directed layout with grid-accelerated repulsion for large graphs.
 */
final class RelationshipGraphLayout {

	static final int MODE_INTERACTIVE = 0;
	static final int MODE_BATCH = 1;
	static final int MODE_STATIC = 2;

	private static final double REPULSION = 8000.0;
	private static final double REPULSION_LARGE = 4000.0;
	private static final double ATTRACTION = 0.02;
	private static final double DAMPING = 0.85;
	private static final double MIN_DISTANCE = 30.0;
	private static final int INTERACTIVE_MAX_ITERATIONS = 250;
	private static final int BATCH_MAX_ITERATIONS = 120;
	private static final double GRID_CELL = 100.0;

	private RelationshipGraphLayout() {
	}

	static int chooseMode(final int nodeCount) {
		if (nodeCount <= 250) {
			return MODE_INTERACTIVE;
		}
		if (nodeCount <= 900) {
			return MODE_BATCH;
		}
		return MODE_STATIC;
	}

	static int maxIterations(final int mode) {
		if (mode == MODE_INTERACTIVE) {
			return INTERACTIVE_MAX_ITERATIONS;
		}
		if (mode == MODE_BATCH) {
			return BATCH_MAX_ITERATIONS;
		}
		return 0;
	}

	static void initializePositions(final RelationshipGraphIndex index, final double width, final double height) {
		final List<RelationshipGraphNode> nodes = index.getNodes();
		final int n = nodes.size();
		if (n == 0) {
			return;
		}
		final int mode = chooseMode(n);
		if (mode == MODE_STATIC) {
			layoutCircle(nodes, width, height);
			return;
		}
		final Random random = new Random(42L);
		final double cx = width / 2.0;
		final double cy = height / 2.0;
		final double spread = Math.min(width, height) * 0.4;
		for (int i = 0; i < n; i++) {
			final RelationshipGraphNode node = nodes.get(i);
			final double angle = random.nextDouble() * Math.PI * 2.0;
			final double radius = spread * (0.2 + random.nextDouble() * 0.8);
			node.setX(cx + Math.cos(angle) * radius);
			node.setY(cy + Math.sin(angle) * radius);
			node.setVx(0);
			node.setVy(0);
		}
	}

	static void iterate(final RelationshipGraphIndex index, final double width, final double height, final int mode) {
		final List<RelationshipGraphNode> nodes = index.getNodes();
		final int n = nodes.size();
		if (n == 0 || mode == MODE_STATIC) {
			return;
		}
		if (n > 250) {
			iterateWithGrid(index, width, height, mode == MODE_BATCH);
			return;
		}
		iterateAllPairs(index, width, height);
	}

	private static void iterateAllPairs(final RelationshipGraphIndex index, final double width, final double height) {
		final List<RelationshipGraphNode> nodes = index.getNodes();
		final List<RelationshipGraphEdge> edges = index.getEdges();
		final int n = nodes.size();

		for (int i = 0; i < n; i++) {
			nodes.get(i).setVx(0);
			nodes.get(i).setVy(0);
		}

		for (int i = 0; i < n; i++) {
			final RelationshipGraphNode a = nodes.get(i);
			for (int j = i + 1; j < n; j++) {
				applyRepulsion(a, nodes.get(j), REPULSION);
			}
		}
		applyEdgeForces(edges);
		applyMotion(nodes, width, height);
	}

	private static void iterateWithGrid(final RelationshipGraphIndex index, final double width, final double height,
	        final boolean batchMode) {
		final List<RelationshipGraphNode> nodes = index.getNodes();
		final List<RelationshipGraphEdge> edges = index.getEdges();
		final double repulsion = batchMode ? REPULSION_LARGE : REPULSION;
		for (int i = 0; i < nodes.size(); i++) {
			nodes.get(i).setVx(0);
			nodes.get(i).setVy(0);
		}
		final RelationshipGraphSpatialIndex spatial = new RelationshipGraphSpatialIndex(GRID_CELL);
		spatial.rebuild(index);
		for (int i = 0; i < nodes.size(); i++) {
			final RelationshipGraphNode a = nodes.get(i);
			spatial.forEachNeighbor(a.getX(), a.getY(), new RelationshipGraphSpatialIndex.NodeVisitor() {
				public void visit(final RelationshipGraphNode b) {
					if (a == b) {
						return;
					}
					applyRepulsion(a, b, repulsion);
				}
			});
		}
		applyEdgeForces(edges);
		applyMotion(nodes, width, height);
	}

	private static void applyRepulsion(final RelationshipGraphNode a, final RelationshipGraphNode b, final double repulsion) {
		double dx = a.getX() - b.getX();
		double dy = a.getY() - b.getY();
		double dist = Math.sqrt(dx * dx + dy * dy);
		if (dist < 1.0) {
			dist = 1.0;
			dx = 1.0;
			dy = 0.0;
		}
		final double force = repulsion / (dist * dist);
		final double fx = force * dx / dist;
		final double fy = force * dy / dist;
		a.setVx(a.getVx() + fx);
		a.setVy(a.getVy() + fy);
		b.setVx(b.getVx() - fx);
		b.setVy(b.getVy() - fy);
	}

	private static void applyEdgeForces(final List<RelationshipGraphEdge> edges) {
		for (int i = 0; i < edges.size(); i++) {
			final RelationshipGraphEdge edge = edges.get(i);
			final RelationshipGraphNode a = edge.getSource();
			final RelationshipGraphNode b = edge.getTarget();
			double dx = b.getX() - a.getX();
			double dy = b.getY() - a.getY();
			double dist = Math.sqrt(dx * dx + dy * dy);
			if (dist < MIN_DISTANCE) {
				dist = MIN_DISTANCE;
			}
			final double force = ATTRACTION * dist;
			final double fx = force * dx / dist;
			final double fy = force * dy / dist;
			a.setVx(a.getVx() + fx);
			a.setVy(a.getVy() + fy);
			b.setVx(b.getVx() - fx);
			b.setVy(b.getVy() - fy);
		}
	}

	private static void applyMotion(final List<RelationshipGraphNode> nodes, final double width, final double height) {
		final double cx = width / 2.0;
		final double cy = height / 2.0;
		for (int i = 0; i < nodes.size(); i++) {
			final RelationshipGraphNode node = nodes.get(i);
			double vx = node.getVx() * DAMPING;
			double vy = node.getVy() * DAMPING;
			node.setVx(vx);
			node.setVy(vy);
			node.setX(node.getX() + vx);
			node.setY(node.getY() + vy);
			node.setX(node.getX() + (cx - node.getX()) * 0.001);
			node.setY(node.getY() + (cy - node.getY()) * 0.001);
		}
	}

	private static void layoutCircle(final List<RelationshipGraphNode> nodes, final double width, final double height) {
		final int n = nodes.size();
		final double cx = width / 2.0;
		final double cy = height / 2.0;
		final double radius = Math.max(120.0, Math.sqrt(n) * 18.0);
		for (int i = 0; i < n; i++) {
			final double angle = (Math.PI * 2.0 * i) / n;
			final RelationshipGraphNode node = nodes.get(i);
			node.setX(cx + Math.cos(angle) * radius);
			node.setY(cy + Math.sin(angle) * radius);
			node.setVx(0);
			node.setVy(0);
		}
	}
}
