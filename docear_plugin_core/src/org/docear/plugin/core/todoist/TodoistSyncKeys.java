package org.docear.plugin.core.todoist;

import java.io.File;

/**
 * Link keys for Todoist ↔ mind-map.
 * <p>
 * The association is the pair of ids: the mind-map node stores {@code TODOIST_TASK_ID},
 * and the Todoist task stores the node id. File paths are not part of the link. A node
 * moved to another map keeps the same node id, so the task still matches.
 * <p>
 * Sync-store keys may still cache {@code absolutePath|nodeId} so a closed file can be
 * patched. Matching never requires that path.
 */
final class TodoistSyncKeys {
	private TodoistSyncKeys() {
	}

	static String syncKey(File file, String nodeId) {
		return absolutePath(file) + "|" + (nodeId == null ? "" : nodeId);
	}

	/** Identity is the mind-map node id alone. */
	static String identityKey(File file, String nodeId) {
		if (nodeId == null || nodeId.length() == 0) {
			return null;
		}
		return nodeId;
	}

	static String identityKeyFromSyncKey(String syncKey) {
		return nodeIdFromSyncKey(syncKey);
	}

	static String nodeIdFromSyncKey(String syncKey) {
		if (syncKey == null) {
			return null;
		}
		final int sep = syncKey.lastIndexOf('|');
		if (sep < 0 || sep >= syncKey.length() - 1) {
			return null;
		}
		final String nodeId = syncKey.substring(sep + 1).trim();
		return nodeId.length() == 0 ? null : nodeId;
	}

	/** File name cached in a {@code path|nodeId} store key. Not used for matching. */
	static String mapNameFromSyncKey(String syncKey) {
		if (syncKey == null) {
			return null;
		}
		final int sep = syncKey.lastIndexOf('|');
		if (sep <= 0) {
			return null;
		}
		final String path = syncKey.substring(0, sep);
		final int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		final String name = slash >= 0 ? path.substring(slash + 1) : path;
		return name.length() == 0 ? null : name;
	}

	static String identityKeyFromDescription(String description) {
		if (description == null || description.indexOf("Docear reminder") < 0) {
			return null;
		}
		return nodeIdFromDescription(description);
	}

	static String nodeIdFromDescription(String description) {
		if (description == null) {
			return null;
		}
		final String nodeId = extractLinePrefix(description, "Node ID: ");
		if (nodeId == null || nodeId.length() == 0) {
			return null;
		}
		return nodeId.trim();
	}

	static String absolutePath(File file) {
		if (file == null) {
			return "";
		}
		try {
			return file.getCanonicalFile().getAbsolutePath();
		}
		catch (Exception e) {
			return file.getAbsolutePath();
		}
	}

	static String extractLinePrefix(String text, String prefix) {
		int idx = text.indexOf(prefix);
		if (idx < 0) {
			return null;
		}
		int start = idx + prefix.length();
		int end = text.indexOf('\n', start);
		if (end < 0) {
			return text.substring(start).trim();
		}
		return text.substring(start, end).trim();
	}
}
