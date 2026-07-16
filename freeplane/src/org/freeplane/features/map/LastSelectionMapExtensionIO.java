package org.freeplane.features.map;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.io.IAttributeHandler;
import org.freeplane.core.io.IExtensionAttributeWriter;
import org.freeplane.core.io.ITreeWriter;
import org.freeplane.core.io.ReadManager;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;

/**
 * Reads/writes {@link LastSelectionMapExtension} as {@code last_selected_id} on the
 * {@code <map>} element so the next open can restore the selection.
 * <p>
 * Also mirrors selection into {@link SessionViewStateStore} for unsaved sessions.
 * When the selected node id changes, the map is marked unsaved so the attribute is
 * written into the {@code .mm} on the next save.
 */
public class LastSelectionMapExtensionIO implements IExtensionAttributeWriter {
	static final String MAP_TAG = "map";
	public static final String LAST_SELECTED_ID_ATTR = "last_selected_id";

	/** When true, selection hooks must not overwrite last_selected_id / session during open restore. */
	private static final ThreadLocal<Boolean> SUPPRESS_REMEMBER = new ThreadLocal<Boolean>();

	private LastSelectionMapExtensionIO(final MapController mapController) {
		registerAttributeHandlers(mapController.getReadManager());
		mapController.getWriteManager().addExtensionAttributeWriter(LastSelectionMapExtension.class, this);
	}

	public static void setSuppressRememberSelection(final boolean suppress) {
		if (suppress) {
			SUPPRESS_REMEMBER.set(Boolean.TRUE);
		}
		else {
			SUPPRESS_REMEMBER.remove();
		}
	}

	private static boolean isRememberSelectionSuppressed() {
		return Boolean.TRUE.equals(SUPPRESS_REMEMBER.get());
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
		final MapController mapController = modeController.getMapController();
		mapController.addNodeSelectionListener(new LastSelectionNodeListener());
		mapController.addMapLifeCycleListener(new IMapLifeCycleListener() {
			public void onCreate(final MapModel map) {
			}

			public void onRemove(final MapModel map) {
				syncSelectionFromController(map);
			}

			public void onSavedAs(final MapModel map) {
				syncSelectionFromController(map);
			}

			public void onSaved(final MapModel map) {
			}
		});
	}

	/** Keep map attribute in sync with the real selection just before save/close. */
	static void syncSelectionFromController(final MapModel map) {
		if (map == null) {
			return;
		}
		try {
			final IMapSelection selection = Controller.getCurrentController().getSelection();
			if (selection == null) {
				return;
			}
			final NodeModel selected = selection.getSelected();
			if (selected == null || selected.getMap() != map) {
				return;
			}
			rememberSelection(selected, false);
		}
		catch (Exception e) {
		}
	}

	/**
	 * @param markMapDirty when true and the id changed, mark the map unsaved so
	 *        {@code last_selected_id} is persisted into the {@code .mm} on save.
	 */
	public static void rememberSelection(final NodeModel node, final boolean markMapDirty) {
		if (isRememberSelectionSuppressed()) {
			return;
		}
		if (node == null || node.getMap() == null) {
			return;
		}
		final MapModel map = node.getMap();
		final String nodeId = node.createID();
		if (nodeId == null || nodeId.isEmpty()) {
			return;
		}
		final LastSelectionMapExtension extension = LastSelectionMapExtension.getOrCreate(map);
		final String previous = extension.getLastSelectedNodeId();
		final boolean changed = previous == null || !nodeId.equals(previous);
		if (changed) {
			extension.setLastSelectedNodeId(nodeId);
		}
		SessionViewStateStore.getInstance().rememberSelection(node);
		if (markMapDirty && changed && map.getFile() != null) {
			try {
				Controller.getCurrentModeController().getMapController().setSaved(map, false);
			}
			catch (Exception e) {
			}
		}
	}

	private static final class LastSelectionNodeListener implements INodeSelectionListener {
		public void onDeselect(final NodeModel node) {
		}

		public void onSelect(final NodeModel node) {
			rememberSelection(node, true);
		}
	}
}
