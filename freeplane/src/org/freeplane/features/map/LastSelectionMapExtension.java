package org.freeplane.features.map;

import org.freeplane.core.extension.IExtension;

/**
 * Persists the last selected node id on the map (saved into the .mm file as {@code last_selected_id}
 * on the {@code <map>} element). Restored when the map is opened.
 */
public class LastSelectionMapExtension implements IExtension {
	private String lastSelectedNodeId;

	public String getLastSelectedNodeId() {
		return lastSelectedNodeId;
	}

	public void setLastSelectedNodeId(final String lastSelectedNodeId) {
		this.lastSelectedNodeId = lastSelectedNodeId;
	}

	public static LastSelectionMapExtension getOrCreate(final MapModel map) {
		LastSelectionMapExtension extension = map.getExtension(LastSelectionMapExtension.class);
		if (extension == null) {
			extension = new LastSelectionMapExtension();
			map.addExtension(LastSelectionMapExtension.class, extension);
		}
		return extension;
	}

	public static LastSelectionMapExtension get(final MapModel map) {
		if (map == null) {
			return null;
		}
		return map.getExtension(LastSelectionMapExtension.class);
	}
}
