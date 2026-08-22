package org.docear.plugin.core.canvas;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.core.graph.RelationshipGraphEdge;
import org.docear.plugin.core.graph.RelationshipGraphIndex;
import org.docear.plugin.core.graph.RelationshipGraphNode;
import org.freeplane.core.util.LogUtils;

/** Convert relationship-graph views to/from JSON Canvas (layout + export). */
public final class GraphCanvasBridge {

	private GraphCanvasBridge() {
	}

	public static File layoutFile(final int mode) {
		final File dir = new File(System.getProperty("user.home"), ".docear/graph-layouts");
		dir.mkdirs();
		return new File(dir, "mode-" + mode + ".canvas");
	}

	public static JsonCanvasDocument fromGraph(final RelationshipGraphIndex index) {
		final JsonCanvasDocument doc = new JsonCanvasDocument();
		if (index == null) {
			return doc;
		}
		final Map<String, String> idByKey = new HashMap<String, String>();
		final List<RelationshipGraphNode> nodes = index.getNodes();
		for (int i = 0; i < nodes.size(); i++) {
			final RelationshipGraphNode n = nodes.get(i);
			final String id = JsonCanvasDocument.newId() + "-" + i;
			idByKey.put(n.getPathKey(), id);
			final int x = (int) Math.round(n.getX());
			final int y = (int) Math.round(n.getY());
			if (n.isTagNode()) {
				final JsonCanvasNode card = JsonCanvasNode.text(id, x, y, n.getLabel());
				card.setColor("6");
				card.setLabel(n.getLabel());
				doc.getNodes().add(card);
			}
			else if (n.getFile() != null) {
				final String sub = n.isMapNode() ? "#" + n.getNodeId() : null;
				doc.getNodes().add(JsonCanvasNode.file(id, x, y, n.getFile().getAbsolutePath(), sub, n.getLabel()));
			}
			else {
				doc.getNodes().add(JsonCanvasNode.text(id, x, y, n.getLabel()));
			}
		}
		final List<RelationshipGraphEdge> edges = index.getEdges();
		for (int i = 0; i < edges.size(); i++) {
			final RelationshipGraphEdge e = edges.get(i);
			final String from = idByKey.get(e.getSource().getPathKey());
			final String to = idByKey.get(e.getTarget().getPathKey());
			if (from != null && to != null) {
				doc.getEdges().add(JsonCanvasEdge.connect(JsonCanvasDocument.newId() + "-e" + i, from, to, "right",
						"left"));
			}
		}
		return doc;
	}

	public static boolean applyToGraph(final JsonCanvasDocument doc, final RelationshipGraphIndex index) {
		if (doc == null || index == null) {
			return false;
		}
		final Map<String, JsonCanvasNode> byKey = new HashMap<String, JsonCanvasNode>();
		final List<JsonCanvasNode> cards = doc.getNodes();
		for (int i = 0; i < cards.size(); i++) {
			final JsonCanvasNode card = cards.get(i);
			final String key = pathKeyOf(card);
			if (key != null) {
				byKey.put(key, card);
			}
		}
		boolean applied = false;
		final List<RelationshipGraphNode> nodes = index.getNodes();
		for (int i = 0; i < nodes.size(); i++) {
			final RelationshipGraphNode n = nodes.get(i);
			JsonCanvasNode card = byKey.get(n.getPathKey());
			if (card == null && n.getFile() != null) {
				card = byKey.get(canonical(n.getFile()));
			}
			if (card != null) {
				n.setX(card.getX());
				n.setY(card.getY());
				n.setVx(0);
				n.setVy(0);
				applied = true;
			}
		}
		return applied;
	}

	public static boolean applySavedLayout(final int mode, final RelationshipGraphIndex index) {
		final File file = layoutFile(mode);
		if (!file.isFile() || index == null) {
			return false;
		}
		try {
			return applyToGraph(JsonCanvasIo.read(file), index);
		}
		catch (Exception e) {
			LogUtils.warn("Canvas: could not apply graph layout", e);
			return false;
		}
	}

	public static void saveLayout(final int mode, final RelationshipGraphIndex index) {
		try {
			JsonCanvasIo.write(layoutFile(mode), fromGraph(index));
		}
		catch (Exception e) {
			LogUtils.warn("Canvas: could not save graph layout", e);
		}
	}

	public static void clearLayout(final int mode) {
		final File file = layoutFile(mode);
		if (file.isFile()) {
			file.delete();
		}
	}

	private static String pathKeyOf(final JsonCanvasNode card) {
		if (card == null || card.getFile() == null) {
			return null;
		}
		File f = new File(card.getFile());
		final String canon = canonical(f);
		final String sub = card.getSubpath();
		if (sub != null && sub.startsWith("#") && sub.length() > 1) {
			return canon + sub;
		}
		return canon;
	}

	private static String canonical(final File file) {
		try {
			return file.getCanonicalFile().getAbsolutePath();
		}
		catch (Exception e) {
			return file.getAbsolutePath();
		}
	}
}
