package org.docear.plugin.core.todoist;

import java.awt.EventQueue;
import java.io.File;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;

import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;

/**
 * Resolves Todoist-linked mind-map nodes by {@code path|nodeId} or task id across open maps
 * (opening the map on the EDT when necessary).
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

	static NodeModel findOrOpenNodeBySyncKey(final String syncKey) throws Exception {
		NodeModel node = findOpenNodeBySyncKey(syncKey);
		if (node != null) {
			return node;
		}
		if (syncKey == null) {
			return null;
		}
		final int sep = syncKey.lastIndexOf('|');
		if (sep <= 0) {
			return null;
		}
		final File file = new File(syncKey.substring(0, sep));
		final String nodeId = syncKey.substring(sep + 1);
		if (!file.isFile()) {
			return null;
		}
		final MapModel map = openMapOnEdt(file);
		if (map == null) {
			return null;
		}
		return map.getNodeForID(nodeId);
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

	private static MapModel openMapOnEdt(final File targetFile) throws Exception {
		final MapModel[] box = new MapModel[1];
		final Exception[] error = new Exception[1];
		final Runnable job = new Runnable() {
			public void run() {
				try {
					MapModel existing = findOpenMap(targetFile);
					if (existing != null) {
						box[0] = existing;
						return;
					}
					final ModeController modeController = Controller.getCurrentModeController();
					final MMapController mapController = (MMapController) modeController.getMapController();
					final URL url = Compat.fileToUrl(targetFile);
					mapController.newMap(url);
					box[0] = findOpenMap(targetFile);
				}
				catch (Exception e) {
					error[0] = e;
				}
			}
		};
		if (EventQueue.isDispatchThread()) {
			job.run();
		}
		else {
			EventQueue.invokeAndWait(job);
		}
		if (error[0] != null) {
			throw error[0];
		}
		return box[0];
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
