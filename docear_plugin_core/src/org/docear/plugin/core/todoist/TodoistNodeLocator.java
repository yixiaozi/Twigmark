package org.docear.plugin.core.todoist;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

/**
 * Resolves Todoist-linked mind-map nodes by {@code path|nodeId} or task id across <em>already open</em>
 * maps. Sync paths must never open maps in the UI — closed maps are updated with silent XML writers.
 */
final class TodoistNodeLocator {
	private TodoistNodeLocator() {
	}

	static NodeModel findOpenNodeBySyncKey(final String syncKey) {
		if (syncKey == null) {
			return null;
		}
		final int sep = syncKey.lastIndexOf('|');
		if (sep <= 0) {
			return null;
		}
		final File file = new File(syncKey.substring(0, sep));
		final String nodeId = syncKey.substring(sep + 1);
		final MapModel map = findOpenMap(file);
		if (map == null) {
			return null;
		}
		return map.getNodeForID(nodeId);
	}

	/**
	 * @deprecated Prefer {@link #findOpenNodeBySyncKey} + silent disk patch. Kept for rare interactive
	 *             navigation callers; Todoist sync must not use this.
	 */
	static NodeModel findOrOpenNodeBySyncKey(final String syncKey) throws Exception {
		return findOpenNodeBySyncKey(syncKey);
	}

	static MapModel findOpenMap(final File targetFile) {
		if (targetFile == null) {
			return null;
		}
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null || controller.getMapViewManager() == null) {
				return null;
			}
			final Map maps = controller.getMapViewManager().getMaps();
			if (maps == null) {
				return null;
			}
			for (Iterator it = maps.values().iterator(); it.hasNext();) {
				MapModel map = (MapModel) it.next();
				File file = map.getFile();
				if (file != null && pathsEqual(file, targetFile)) {
					return map;
				}
			}
		}
		catch (Exception e) {
			return null;
		}
		return null;
	}

	static void stampOpenMapsFromStore(final TodoistMappingStore store) {
		if (store == null) {
			return;
		}
		final Map maps = Controller.getCurrentController().getMapViewManager().getMaps();
		for (Iterator it = maps.values().iterator(); it.hasNext();) {
			stampMapFromStore((MapModel) it.next(), store);
		}
	}

	/** Apply known Todoist 1:1 links onto one open map (and migrate legacy visible attrs). */
	static void stampMapFromStore(final MapModel map, final TodoistMappingStore store) {
		if (map == null) {
			return;
		}
		final File file = map.getFile();
		if (file == null) {
			return;
		}
		if (store != null) {
			final String abs = file.getAbsolutePath();
			for (Iterator keys = store.keySet().iterator(); keys.hasNext();) {
				String syncKey = (String) keys.next();
				if (!syncKey.startsWith(abs + "|")) {
					continue;
				}
				final int sep = syncKey.lastIndexOf('|');
				final String nodeId = syncKey.substring(sep + 1);
				final NodeModel node = map.getNodeForID(nodeId);
				if (node == null) {
					continue;
				}
				final String taskId = store.getTaskIdOnly(syncKey);
				final String hash = store.getStoredContentHash(syncKey);
				if (taskId != null && taskId.length() > 0) {
					TodoistReminderFactory.setTaskId(node, taskId);
				}
				if (hash != null && hash.length() > 0) {
					TodoistReminderFactory.setStoredContentHash(node, hash);
				}
			}
		}
		// Inbox / import map historically showed todoist_* as visible attributes — strip on open.
		if (TodoistConfig.isImportTargetFile(file)) {
			migrateLegacyOnMap(map.getRootNode());
		}
	}

	private static void migrateLegacyOnMap(final NodeModel node) {
		if (node == null) {
			return;
		}
		TodoistReminderFactory.getTaskId(node);
		for (Iterator it = node.getChildren().iterator(); it.hasNext();) {
			migrateLegacyOnMap((NodeModel) it.next());
		}
	}

	private static boolean pathsEqual(File a, File b) {
		try {
			return a.getCanonicalFile().equals(b.getCanonicalFile());
		}
		catch (Exception e) {
			return a.getAbsolutePath().equalsIgnoreCase(b.getAbsolutePath());
		}
	}
}
