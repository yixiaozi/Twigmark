package org.freeplane.plugin.workspace.features.nodepins;

import java.io.File;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;

public final class NodeMindMapActionUtils {

	public interface NodeRunnable {
		void run(NodeModel node);
	}

	private NodeMindMapActionUtils() {
	}

	public static NodeModel getSelectedNode() {
		return Controller.getCurrentController().getSelection().getSelected();
	}

	public static boolean isSavedMapNode(final NodeModel node) {
		return node != null && node.getMap() != null && node.getMap().getFile() != null;
	}

	public static String getNodePlainText(final NodeModel node) {
		if (node == null) {
			return "";
		}
		final String text = node.getText();
		if (text == null) {
			return "";
		}
		return HtmlUtils.unescapeHTMLUnicodeEntity(HtmlUtils.removeHtmlTagsFromString(text)).replaceAll("\\s+", " ")
				.trim();
	}

	public static String buildPinKey(final NodeModel node) {
		return NodePinKeyUtils.fromNode(node);
	}

	public static File getMapFile(final NodeModel node) {
		if (node == null || node.getMap() == null) {
			return null;
		}
		return node.getMap().getFile();
	}

	public static NodeModel resolveNodeByKey(final String globalKey) {
		if (globalKey == null) {
			return null;
		}
		final File mapFile = NodePinKeyUtils.resolveMapFile(globalKey);
		final String nodeId = NodePinKeyUtils.parseNodeId(globalKey);
		if (mapFile == null || nodeId == null) {
			return null;
		}
		try {
			final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			final java.util.Map maps = mapViewManager.getMaps(MModeController.MODENAME);
			for (final Object mapObj : maps.values()) {
				final MapModel map = (MapModel) mapObj;
				final File openFile = map.getFile();
				if (openFile != null && openFile.equals(mapFile)) {
					return map.getNodeForID(nodeId);
				}
			}
		} catch (final Exception e) {
			// ignore
		}
		return null;
	}

	public static void withNodeByKey(final String globalKey, final NodeRunnable action) {
		withNodeByKey(globalKey, action, 0);
	}

	private static void withNodeByKey(final String globalKey, final NodeRunnable action, final int attempt) {
		if (globalKey == null || action == null) {
			return;
		}
		NodePinNavigator.openNode(globalKey);
		final NodeModel node = resolveNodeByKey(globalKey);
		if (node != null) {
			action.run(node);
			return;
		}
		if (attempt >= 12) {
			return;
		}
		final javax.swing.Timer retry = new javax.swing.Timer(250, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				withNodeByKey(globalKey, action, attempt + 1);
			}
		});
		retry.setRepeats(false);
		retry.start();
	}
}