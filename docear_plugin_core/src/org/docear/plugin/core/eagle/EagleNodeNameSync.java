package org.docear.plugin.core.eagle;

import java.net.URI;
import java.util.List;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.view.swing.features.filepreview.ExternalImageSelection;
import org.freeplane.view.swing.features.filepreview.ExternalResource;

/**
 * Eagle item id is the primary key for the image link. When a node stores
 * {@code eagle://item/{id}.ext}, keep the node title aligned with the Eagle item name.
 */
public final class EagleNodeNameSync implements ExternalImageSelection.AfterApply {

	private EagleNodeNameSync() {
	}

	public static void install(final ModeController modeController) {
		final EagleNodeNameSync sync = new EagleNodeNameSync();
		ExternalImageSelection.setAfterApply(sync);
		modeController.getMapController().addMapLifeCycleListener(new IMapLifeCycleListener() {
			public void onCreate(final MapModel map) {
				try {
					syncMap(map);
				}
				catch (Exception e) {
					LogUtils.warn("Eagle: node name sync on map open failed: " + e.getMessage());
				}
			}

			public void onRemove(MapModel map) {
			}

			public void onSavedAs(MapModel map) {
			}

			public void onSaved(MapModel map) {
			}
		});
	}

	public void afterApply(final NodeModel node, final URI storedUri) {
		syncNode(node, storedUri);
	}

	public static int syncMap(final MapModel map) {
		if (map == null || map.getRootNode() == null) {
			return 0;
		}
		if (EagleConfig.existingLibraryRoots().isEmpty()) {
			return 0;
		}
		EagleItemIndex.getInstance().ensureLoaded(false);
		return syncNodeRecursive(map.getRootNode());
	}

	private static int syncNodeRecursive(final NodeModel node) {
		int updated = 0;
		if (syncNode(node, null)) {
			updated++;
		}
		final List<NodeModel> children = node.getChildren();
		if (children == null) {
			return updated;
		}
		for (int i = 0; i < children.size(); i++) {
			updated += syncNodeRecursive(children.get(i));
		}
		return updated;
	}

	/**
	 * @param storedUri optional; when null, read from the node's ExternalResource
	 * @return true if node text was changed
	 */
	public static boolean syncNode(final NodeModel node, final URI storedUri) {
		if (node == null) {
			return false;
		}
		URI uri = storedUri;
		if (uri == null) {
			final ExternalResource resource = (ExternalResource) node.getExtension(ExternalResource.class);
			if (resource == null) {
				return false;
			}
			uri = resource.getUri();
		}
		if (!EagleUri.isEagleUri(uri)) {
			return false;
		}
		final String itemId = EagleUri.parseItemId(uri);
		if (itemId == null) {
			return false;
		}
		final EagleItem item = EagleItemIndex.getInstance().getById(itemId);
		if (item == null) {
			return false;
		}
		final String desired = preferredDisplayName(item);
		if (desired == null || desired.length() == 0) {
			return false;
		}
		final String currentPlain = HtmlUtils.htmlToPlain(String.valueOf(node.getUserObject())).trim();
		if (namesMatch(currentPlain, item, desired)) {
			return false;
		}
		try {
			MTextController.getController().setNodeText(node, desired);
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("Eagle: failed to sync node title for " + itemId + ": " + e.getMessage());
			return false;
		}
	}

	static String preferredDisplayName(final EagleItem item) {
		if (item.getName() != null && item.getName().trim().length() > 0) {
			return item.getName().trim();
		}
		if (item.getFile() != null) {
			final String fileName = item.getFile().getName();
			final int dot = fileName.lastIndexOf('.');
			return dot > 0 ? fileName.substring(0, dot) : fileName;
		}
		return item.getId();
	}

	private static boolean namesMatch(final String currentPlain, final EagleItem item, final String desired) {
		if (currentPlain == null) {
			return false;
		}
		if (currentPlain.equals(desired)) {
			return true;
		}
		final String ext = item.getExt() == null ? "" : item.getExt().trim();
		if (ext.length() > 0) {
			final String withExt = desired + "." + ext;
			if (currentPlain.equals(withExt) || currentPlain.equalsIgnoreCase(withExt)) {
				return true;
			}
		}
		if (item.getFile() != null && currentPlain.equals(item.getFile().getName())) {
			return true;
		}
		return currentPlain.equalsIgnoreCase(desired);
	}
}
