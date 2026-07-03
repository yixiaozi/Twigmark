package org.docear.plugin.core.graph;

public final class RelationshipGraphEdge {

	private final RelationshipGraphNode source;
	private final RelationshipGraphNode target;

	public RelationshipGraphEdge(final RelationshipGraphNode source, final RelationshipGraphNode target) {
		this.source = source;
		this.target = target;
	}

	public RelationshipGraphNode getSource() {
		return source;
	}

	public RelationshipGraphNode getTarget() {
		return target;
	}
}
