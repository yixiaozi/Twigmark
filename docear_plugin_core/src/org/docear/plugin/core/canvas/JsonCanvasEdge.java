package org.docear.plugin.core.canvas;

/** One edge in a JSON Canvas 1.0 document. */
public final class JsonCanvasEdge {

	private String id;
	private String fromNode;
	private String toNode;
	private String fromSide;
	private String toSide;
	private String fromEnd;
	private String toEnd;
	private String color;
	private String label;

	public JsonCanvasEdge() {
		this.toEnd = "arrow";
	}

	public static JsonCanvasEdge connect(final String id, final String fromNode, final String toNode,
			final String fromSide, final String toSide) {
		final JsonCanvasEdge e = new JsonCanvasEdge();
		e.id = id;
		e.fromNode = fromNode;
		e.toNode = toNode;
		e.fromSide = fromSide;
		e.toSide = toSide;
		e.toEnd = "arrow";
		return e;
	}

	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public String getFromNode() {
		return fromNode;
	}

	public void setFromNode(final String fromNode) {
		this.fromNode = fromNode;
	}

	public String getToNode() {
		return toNode;
	}

	public void setToNode(final String toNode) {
		this.toNode = toNode;
	}

	public String getFromSide() {
		return fromSide;
	}

	public void setFromSide(final String fromSide) {
		this.fromSide = fromSide;
	}

	public String getToSide() {
		return toSide;
	}

	public void setToSide(final String toSide) {
		this.toSide = toSide;
	}

	public String getFromEnd() {
		return fromEnd;
	}

	public void setFromEnd(final String fromEnd) {
		this.fromEnd = fromEnd;
	}

	public String getToEnd() {
		return toEnd;
	}

	public void setToEnd(final String toEnd) {
		this.toEnd = toEnd;
	}

	public String getColor() {
		return color;
	}

	public void setColor(final String color) {
		this.color = color;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(final String label) {
		this.label = label;
	}
}
