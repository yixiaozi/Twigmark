package org.freeplane.plugin.workspace.features.nodepins;

import java.io.File;

import org.freeplane.features.map.NodeModel;

public final class NodePinKeyUtils {

	private static final String KEY_SEPARATOR = "#";

	private NodePinKeyUtils() {
	}

	public static String fromNode(final NodeModel node) {
		if (node == null || node.getMap() == null) {
			return null;
		}
		final File mapFile = node.getMap().getFile();
		if (mapFile == null) {
			return null;
		}
		return mapFile.getAbsolutePath() + KEY_SEPARATOR + node.createID();
	}

	public static String parseMapUri(final String globalKey) {
		if (globalKey == null) {
			return null;
		}
		final int separator = globalKey.lastIndexOf(KEY_SEPARATOR);
		if (separator <= 0) {
			return null;
		}
		return globalKey.substring(0, separator);
	}

	public static String parseNodeId(final String globalKey) {
		if (globalKey == null) {
			return null;
		}
		final int separator = globalKey.lastIndexOf(KEY_SEPARATOR);
		if (separator < 0 || separator >= globalKey.length() - 1) {
			return null;
		}
		return globalKey.substring(separator + 1);
	}

	public static File resolveMapFile(final String globalKey) {
		final String mapUri = parseMapUri(globalKey);
		if (mapUri == null) {
			return null;
		}
		return new File(mapUri);
	}

	public static String toRelativeStorageKey(final String globalKey, final Object project) {
		return globalKey;
	}

	public static String toGlobalKey(final Object project, final String relativeStorageKey) {
		return relativeStorageKey;
	}
}