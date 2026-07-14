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

	boolean isEmpty() {
		return (taskId == null || taskId.length() == 0) && (contentHash == null || contentHash.length() == 0);
	}
}
