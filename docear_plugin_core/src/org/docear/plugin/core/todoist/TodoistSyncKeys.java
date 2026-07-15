package org.docear.plugin.core.todoist;

import java.io.File;

/**
 * Stable keys for Todoist ↔ mind-map linkage.
 * <p>
 * Exact sync keys use a canonical absolute path (so short/long Windows paths do not
 * drift). Identity keys use {@code mapFileName|nodeId} so relocated maps and
 * absolute-path variants still resolve to the same Todoist task.
 */
final class TodoistSyncKeys {
	private TodoistSyncKeys() {
	}

	static String syncKey(File file, String nodeId) {
		return absolutePath(file) + "|" + (nodeId == null ? "" : nodeId);
	}

	static String identityKey(File file, String nodeId) {
		if (file == null || nodeId == null || nodeId.length() == 0) {
			return null;
		}
		return file.getName() + "|" + nodeId;
	}

	static String identityKeyFromSyncKey(String syncKey) {
		if (syncKey == null) {
			return null;
		}
		final int sep = syncKey.lastIndexOf('|');
		if (sep <= 0 || sep >= syncKey.length() - 1) {
			return null;
		}
		final String path = syncKey.substring(0, sep);
		final String nodeId = syncKey.substring(sep + 1);
		final int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		final String name = slash >= 0 ? path.substring(slash + 1) : path;
		if (name.length() == 0 || nodeId.length() == 0) {
			return null;
		}
		return name + "|" + nodeId;
	}

	static String identityKeyFromDescription(String description) {
		if (description == null || description.indexOf("Docear reminder") < 0) {
			return null;
		}
		final String map = extractLinePrefix(description, "Map: ");
		final String nodeId = extractLinePrefix(description, "Node ID: ");
		if (map == null || nodeId == null || map.length() == 0 || nodeId.length() == 0) {
			return null;
		}
		return map.trim() + "|" + nodeId.trim();
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
