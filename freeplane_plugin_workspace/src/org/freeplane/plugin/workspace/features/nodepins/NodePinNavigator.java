package org.freeplane.plugin.workspace.features.nodepins;

import java.io.File;
import java.net.URL;

import javax.swing.Timer;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;

public final class NodePinNavigator {

	private NodePinNavigator() {
	}

	public static void openNode(final String globalKey) {
		if (globalKey == null) {
			return;
		}
		final File mapFile = NodePinKeyUtils.resolveMapFile(globalKey);
		final String nodeId = NodePinKeyUtils.parseNodeId(globalKey);
		if (mapFile == null || nodeId == null || !mapFile.exists()) {
			return;
		}
		try {
			final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			final URL url = mapFile.toURI().toURL();
			if (!mapViewManager.tryToChangeToMapView(url)) {
				final MMapController mapController = (MMapController) Controller.getCurrentModeController().getMapController();
				mapController.newMap(url);
			}
			selectNodeWithRetry(globalKey, 0);
		}
		catch (final Exception e) {
			LogUtils.warn(e);
		}
	}

	private static void selectNodeWithRetry(final String globalKey, final int attempt) {
		final int maxAttempts = 12;
		if (globalKey == null || attempt > maxAttempts) {
			return;
		}
		final File mapFile = NodePinKeyUtils.resolveMapFile(globalKey);
		final String nodeId = NodePinKeyUtils.parseNodeId(globalKey);
		if (mapFile == null || nodeId == null) {
			return;
		}
		try {
			final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			final java.util.Map maps = mapViewManager.getMaps(MModeController.MODENAME);
			for (final Object mapObj : maps.values()) {
				final MapModel map = (MapModel) mapObj;
				final File openFile = map.getFile();
				if (openFile != null && openFile.equals(mapFile)) {
					final NodeModel node = map.getNodeForID(nodeId);
					if (node != null) {
						Controller.getCurrentModeController().getMapController().select(node);
						return;
					}
				}
			}
		}
		catch (final Exception e) {
			LogUtils.warn(e);
		}
		final Timer retry = new Timer(250, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				selectNodeWithRetry(globalKey, attempt + 1);
			}
		});
		retry.setRepeats(false);
		retry.start();
	}
}
