package org.docear.plugin.core.todoist;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.map.NodeModel;

/**
 * In-memory holder for Todoist linkage metadata. Persisted as hidden {@code <node>} XML
 * attributes ({@code TODOIST_TASK_ID}, {@code TODOIST_CONTENT_HASH}), not as visible Freeplane attributes.
 */
final class TodoistNodeMeta implements IExtension {
	private String taskId;
	private String contentHash;
	/** User cut or moved this node; later imports must not pull it back. */
	private boolean userPlaced;
	/** Node {@code MODIFIED} remembered at the last sync. */
	private long syncedLocalMillis;
	/** Todoist {@code updated_at} remembered at the last sync. */
	private long syncedRemoteMillis;
	/** This node is a Todoist task. The yellow background is only a visual reminder. */
	private boolean linked;
	private String projectId;
	private String sectionId;

	static TodoistNodeMeta get(final NodeModel node) {
		if (node == null) {
			return null;
		}
		return (TodoistNodeMeta) node.getExtension(TodoistNodeMeta.class);
	}

	static TodoistNodeMeta getOrCreate(final NodeModel node) {
		TodoistNodeMeta meta = get(node);
		if (meta == null) {
			meta = new TodoistNodeMeta();
			node.addExtension(meta);
		}
		return meta;
	}

	String getTaskId() {
		return taskId;
	}

	void setTaskId(final String taskId) {
		this.taskId = taskId == null || taskId.trim().length() == 0 ? null : taskId.trim();
	}

	String getContentHash() {
		return contentHash;
	}

	void setContentHash(final String contentHash) {
		this.contentHash = contentHash == null || contentHash.trim().length() == 0 ? null : contentHash.trim();
	}

	boolean isUserPlaced() {
		return userPlaced;
	}

	void setUserPlaced(final boolean userPlaced) {
		this.userPlaced = userPlaced;
	}

	long getSyncedLocalMillis() {
		return syncedLocalMillis;
	}

	void setSyncedLocalMillis(final long syncedLocalMillis) {
		this.syncedLocalMillis = syncedLocalMillis < 0 ? 0L : syncedLocalMillis;
	}

	long getSyncedRemoteMillis() {
		return syncedRemoteMillis;
	}

	void setSyncedRemoteMillis(final long syncedRemoteMillis) {
		this.syncedRemoteMillis = syncedRemoteMillis < 0 ? 0L : syncedRemoteMillis;
	}

	boolean isLinked() {
		return linked;
	}

	void setLinked(final boolean linked) {
		this.linked = linked;
	}

	String getProjectId() {
		return projectId;
	}

	void setProjectId(final String projectId) {
		this.projectId = projectId == null || projectId.trim().length() == 0 ? null : projectId.trim();
	}

	String getSectionId() {
		return sectionId;
	}

	void setSectionId(final String sectionId) {
		this.sectionId = sectionId == null || sectionId.trim().length() == 0 ? null : sectionId.trim();
	}

	boolean isEmpty() {
		return (taskId == null || taskId.length() == 0) && (contentHash == null || contentHash.length() == 0)
				&& !userPlaced && syncedLocalMillis <= 0 && syncedRemoteMillis <= 0 && !linked
				&& (projectId == null || projectId.length() == 0) && (sectionId == null || sectionId.length() == 0);
	}
}
