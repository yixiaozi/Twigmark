package org.docear.plugin.core.canvas;

import java.util.ArrayList;
import java.util.List;

/** JSON Canvas 1.0 document: nodes + edges. */
public final class JsonCanvasDocument {

	private final List<JsonCanvasNode> nodes = new ArrayList<JsonCanvasNode>();
	private final List<JsonCanvasEdge> edges = new ArrayList<JsonCanvasEdge>();

	public List<JsonCanvasNode> getNodes() {
		return nodes;
	}

	public List<JsonCanvasEdge> getEdges() {
		return edges;
	}

	public JsonCanvasNode findNode(final String id) {
		if (id == null) {
			return null;
		}
		for (int i = 0; i < nodes.size(); i++) {
			final JsonCanvasNode n = nodes.get(i);
			if (id.equals(n.getId())) {
				return n;
			}
		}
		return null;
	}

	public void removeNode(final String id) {
		if (id == null) {
			return;
		}
		for (int i = nodes.size() - 1; i >= 0; i--) {
			if (id.equals(nodes.get(i).getId())) {
				nodes.remove(i);
			}
		}
		for (int i = edges.size() - 1; i >= 0; i--) {
			final JsonCanvasEdge e = edges.get(i);
			if (id.equals(e.getFromNode()) || id.equals(e.getToNode())) {
				edges.remove(i);
			}
		}
	}

	public void removeEdge(final String id) {
		if (id == null) {
			return;
		}
		for (int i = edges.size() - 1; i >= 0; i--) {
			if (id.equals(edges.get(i).getId())) {
				edges.remove(i);
			}
		}
	}

	public static String newId() {
		return Long.toString(System.currentTimeMillis(), 36) + Integer.toHexString((int) (Math.random() * 0xfffff));
	}
}
