package org.freeplane.features.map;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.io.IAttributeHandler;
import org.freeplane.core.io.IExtensionAttributeWriter;
import org.freeplane.core.io.ITreeWriter;
import org.freeplane.core.io.ReadManager;
import org.freeplane.features.mode.ModeController;

/**
 * Reads/writes {@link LastSelectionMapExtension} as a {@code last_selected_id} attribute on the map element.
 */
public class LastSelectionMapExtensionIO implements IExtensionAttributeWriter {
	static final String MAP_TAG = "map";
	static final String LAST_SELECTED_ID_ATTR = "last_selected_id";

	private LastSelectionMapExtensionIO(final MapController mapController) {
		registerAttributeHandlers(mapController.getReadManager());
		mapController.getWriteManager().addExtensionAttributeWriter(LastSelectionMapExtension.class, this);
	}

	private void registerAttributeHandlers(final ReadManager reader) {
		reader.addAttributeHandler(MAP_TAG, LAST_SELECTED_ID_ATTR, new IAttributeHandler() {
			public void setAttribute(final Object userObject, final String value) {
				final MapModel map = (MapModel) userObject;
				if (value == null || value.trim().isEmpty()) {
					return;
				}
				LastSelectionMapExtension.getOrCreate(map).setLastSelectedNodeId(value.trim());
			}
		});
	}

	public void writeAttributes(final ITreeWriter writer, final Object userObject, final IExtension extension) {
		final LastSelectionMapExtension lastSelection = (LastSelectionMapExtension) extension;
		final String nodeId = lastSelection.getLastSelectedNodeId();
		if (nodeId != null && !nodeId.isEmpty()) {
			writer.addAttribute(LAST_SELECTED_ID_ATTR, nodeId);
		}
	}

	public static void install(final ModeController modeController) {
		new LastSelectionMapExtensionIO(modeController.getMapController());
		modeController.getMapController().addNodeSelectionListener(new LastSelectionNodeListener());
	}

	private static final class LastSelectionNodeListener implements INodeSelectionListener {
		public void onDeselect(final NodeModel node) {
		}

		public void onSelect(final NodeModel node) {
			if (node == null || node.getMap() == null) {
				return;
			}
			final String nodeId = node.createID();
			if (nodeId == null || nodeId.isEmpty()) {
				return;
			}
			LastSelectionMapExtension.getOrCreate(node.getMap()).setLastSelectedNodeId(nodeId);
			SessionViewStateStore.getInstance().rememberSelection(node);
		}
	}
}
