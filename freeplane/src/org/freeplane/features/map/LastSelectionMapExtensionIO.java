package org.freeplane.features.map;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.io.IAttributeHandler;
import org.freeplane.core.io.IExtensionAttributeWriter;
import org.freeplane.core.io.ITreeWriter;
import org.freeplane.core.io.ReadManager;
import org.freeplane.core.io.UnknownElements;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.n3.nanoxml.XMLElement;

/**
 * Reads/writes {@link LastSelectionMapExtension} as {@code last_selected_id} on the
 * {@code <map>} element so the next open can restore the selection.
 * <p>
 * When the user changes the selected node, the map is marked unsaved so the attribute is
 * written into the {@code .mm} on the next save. Open-time restore suppresses that
 * bookkeeping (and folding dirty) so simply opening a map does not prompt to save.
 */
public class LastSelectionMapExtensionIO implements IExtensionAttributeWriter {
	static final String MAP_TAG = "map";
	public static final String LAST_SELECTED_ID_ATTR = "last_selected_id";

	/** When true, selection hooks must not overwrite last_selected_id during open restore. */
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
				if (!(userObject instanceof MapModel)) {
					return;
				}
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
				ensureLoadedFromUnknownElements(map);
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

	/** If the attribute was kept as unknown XML, promote it into the extension. */
	public static void ensureLoadedFromUnknownElements(final MapModel map) {
		if (map == null) {
			return;
		}
		final LastSelectionMapExtension existing = LastSelectionMapExtension.get(map);
		if (existing != null && existing.getLastSelectedNodeId() != null
				&& !existing.getLastSelectedNodeId().isEmpty()) {
			return;
		}
		final UnknownElements unknown = map.getExtension(UnknownElements.class);
		if (unknown == null || unknown.getUnknownElements() == null) {
			return;
		}
		final XMLElement xml = unknown.getUnknownElements();
		final String fromUnknown = xml.getAttribute(LAST_SELECTED_ID_ATTR, null);
		if (fromUnknown == null || fromUnknown.trim().isEmpty()) {
			return;
		}
		LastSelectionMapExtension.getOrCreate(map).setLastSelectedNodeId(fromUnknown.trim());
		xml.removeAttribute(LAST_SELECTED_ID_ATTR);
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
		if (markMapDirty && changed && map.getFile() != null) {
			try {
				Controller.getCurrentModeController().getMapController().setSaved(map, false);
			}
			catch (Exception e) {
			}
		}
	}

	public static String readLastSelectedId(final MapModel map) {
		ensureLoadedFromUnknownElements(map);
		final LastSelectionMapExtension lastSelection = LastSelectionMapExtension.get(map);
		if (lastSelection != null) {
			final String id = lastSelection.getLastSelectedNodeId();
			if (id != null && !id.isEmpty()) {
				return id;
			}
		}
		final String fromFile = readLastSelectedIdFromMapFile(map);
		if (fromFile != null) {
			LastSelectionMapExtension.getOrCreate(map).setLastSelectedNodeId(fromFile);
			return fromFile;
		}
		return null;
	}

	/**
	 * Last-resort parse of {@code last_selected_id} from the map file header when the
	 * XML attribute handler did not populate the extension (e.g. dual map handlers).
	 */
	private static String readLastSelectedIdFromMapFile(final MapModel map) {
		if (map == null || map.getFile() == null || !map.getFile().isFile()) {
			return null;
		}
		java.io.InputStreamReader reader = null;
		try {
			reader = new java.io.InputStreamReader(new java.io.FileInputStream(map.getFile()), "UTF-8");
			final char[] buf = new char[4096];
			final int n = reader.read(buf);
			if (n <= 0) {
				return null;
			}
			final String head = new String(buf, 0, n);
			final String marker = LAST_SELECTED_ID_ATTR + "=\"";
			final int start = head.indexOf(marker);
			if (start < 0) {
				return null;
			}
			final int valueStart = start + marker.length();
			final int valueEnd = head.indexOf('"', valueStart);
			if (valueEnd <= valueStart) {
				return null;
			}
			final String id = head.substring(valueStart, valueEnd).trim();
			return id.isEmpty() ? null : id;
		}
		catch (Exception e) {
			return null;
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (Exception ignore) {
				}
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
