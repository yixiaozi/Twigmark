package org.freeplane.view.swing.features.reports;

import java.util.List;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.factory.IconStoreFactory;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.mindmapmode.MTextController;

/**
 * Writes a {@link ReportNodeSpec} tree under the currently selected mind-map node.
 */
public final class ReportMindMapWriter {
	private ReportMindMapWriter() {
	}

	public static NodeModel writeUnderSelection(final ReportNodeSpec root) {
		if (root == null) {
			return null;
		}
		final NodeModel parent = resolveParent();
		if (parent == null) {
			throw new IllegalStateException("请先打开导图并选中一个节点作为报表写入位置");
		}
		return writeUnder(parent, root);
	}

	public static NodeModel writeUnder(final NodeModel parent, final ReportNodeSpec root) {
		if (parent == null || root == null) {
			return null;
		}
		try {
			final ModeController modeController = Controller.getCurrentModeController();
			final MMapController mapController = (MMapController) modeController.getMapController();
			final MTextController textController = (MTextController) TextController.getController();
			final MIconController iconController = (MIconController) org.freeplane.features.icon.IconController
			        .getController();
			final NodeModel created = writeRecursive(mapController, textController, iconController, parent, root);
			mapController.setSaved(parent.getMap(), false);
			try {
				Controller.getCurrentController().getSelection().selectAsTheOnlyOneSelected(created);
			}
			catch (Exception e) {
			}
			return created;
		}
		catch (RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
			LogUtils.warn("ReportMindMapWriter failed", e);
			throw new IllegalStateException("写入导图失败：" + e.getMessage(), e);
		}
	}

	private static NodeModel writeRecursive(final MMapController mapController, final MTextController textController,
	        final MIconController iconController, final NodeModel parent, final ReportNodeSpec spec) {
		final NodeModel child = mapController.addNewNode(parent, parent.getChildCount(), parent.isNewChildLeft());
		if (child == null) {
			throw new IllegalStateException("无法创建节点");
		}
		textController.setNodeText(child, spec.text);
		addIcon(iconController, child, spec.iconName);
		final List kids = spec.getChildren();
		for (int i = 0; i < kids.size(); i++) {
			writeRecursive(mapController, textController, iconController, child, (ReportNodeSpec) kids.get(i));
		}
		return child;
	}

	private static void addIcon(final MIconController iconController, final NodeModel node, final String iconName) {
		if (iconName == null || iconName.length() == 0 || node == null) {
			return;
		}
		try {
			final MindIcon icon = IconStoreFactory.create().getMindIcon(iconName);
			if (icon == null || icon instanceof org.freeplane.features.icon.IconNotFound) {
				return;
			}
			if (iconController != null) {
				iconController.addIcon(node, icon);
			}
			else {
				node.addIcon(icon);
			}
		}
		catch (Exception e) {
			LogUtils.warn("ReportMindMapWriter.addIcon failed: " + iconName, e);
		}
	}

	private static NodeModel resolveParent() {
		try {
			final NodeModel selected = Controller.getCurrentController().getSelection().getSelected();
			if (selected != null) {
				return selected;
			}
		}
		catch (Exception e) {
		}
		try {
			return Controller.getCurrentController().getMap().getRootNode();
		}
		catch (Exception e) {
			return null;
		}
	}
}
