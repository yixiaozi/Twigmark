package org.freeplane.plugin.workspace.features.mapactivity;

import org.freeplane.features.map.NodeModel;

/**
 * One row in the current-map activity overlay.
 */
public final class MapActivityItem {

	public enum Kind {
		POMODORO, OVERDUE, REMINDER, TODO, FLAG
	}

	public final Kind kind;
	public final NodeModel node;
	public final String nodeId;
	public final String title;
	/** Left meta column: time, duration, flag name, etc. */
	public final String meta;
	public final boolean live;
	public final boolean overdue;
	public final long sortKey;

	public MapActivityItem(final Kind kind, final NodeModel node, final String title, final String meta,
	        final boolean live, final boolean overdue, final long sortKey) {
		this.kind = kind;
		this.node = node;
		this.nodeId = node != null ? node.getID() : "";
		this.title = title != null ? title : "";
		this.meta = meta != null ? meta : "";
		this.live = live;
		this.overdue = overdue;
		this.sortKey = sortKey;
	}
}
