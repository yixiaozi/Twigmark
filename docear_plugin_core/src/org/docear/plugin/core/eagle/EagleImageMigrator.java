package org.docear.plugin.core.eagle;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.url.UrlManager;
import org.freeplane.view.swing.features.filepreview.ExternalResource;
import org.freeplane.view.swing.features.filepreview.ViewerController;

/**
 * Migrates only broken ExternalObject paths that uniquely match an Eagle item by filename.
 * Working path references are left unchanged (path-first; Eagle optional).
 */
public final class EagleImageMigrator {

	public static final class Result {
		public int scanned;
		public int alreadyEagle;
		public int keptPath;
		public int migrated;
		public int imported;
		public int unmatched;
		public final List<String> unmatchedDetails = new ArrayList<String>();
		public final List<String> migratedDetails = new ArrayList<String>();

		public String summaryText() {
			return "scanned=" + scanned + ", alreadyEagle=" + alreadyEagle + ", keptPath=" + keptPath
					+ ", migrated=" + migrated + ", imported=" + imported + ", unmatched=" + unmatched;
		}
	}

	private EagleImageMigrator() {
	}

	public static Result migrateMap(final MapModel map) {
		final Result result = new Result();
		if (map == null || map.getRootNode() == null) {
			return result;
		}
		EagleItemIndex.getInstance().ensureLoaded(false);
		migrateNodeRecursive(map, map.getRootNode(), result);
		// Identity is Eagle id; keep node titles aligned with Eagle item names after URI rewrites.
		EagleNodeNameSync.syncMap(map);
		return result;
	}

	private static void migrateNodeRecursive(final MapModel map, final NodeModel node, final Result result) {
		migrateNode(map, node, result);
		final List<NodeModel> children = node.getChildren();
		if (children == null) {
			return;
		}
		for (int i = 0; i < children.size(); i++) {
			migrateNodeRecursive(map, children.get(i), result);
		}
	}

	private static void migrateNode(final MapModel map, final NodeModel node, final Result result) {
		final ExternalResource resource = (ExternalResource) node.getExtension(ExternalResource.class);
		if (resource == null || resource.getUri() == null) {
			return;
		}
		result.scanned++;
		final URI uri = resource.getUri();
		if (EagleUri.isEagleUri(uri)) {
			final String id = EagleUri.parseItemId(uri);
			final File file = EagleItemIndex.getInstance().resolveFile(id);
			if (file != null && file.isFile()) {
				result.alreadyEagle++;
				return;
			}
			// Broken eagle:// — try recover by filename embedded in URI, else unmatched
			final EagleItem byName = EagleItemIndex.getInstance().findUniqueByFileNameHint(uriPath(uri));
			if (byName != null && replaceExternalResource(node, resource, EagleUri.create(byName.getId(), byName.getExt()))) {
				result.migrated++;
				result.migratedDetails.add(node.getID() + " repaired eagle:// → " + byName.getId());
				return;
			}
			result.unmatched++;
			result.unmatchedDetails.add(node.getID() + " " + uri);
			return;
		}

		File absolute = null;
		try {
			absolute = UrlManager.getController().getAbsoluteFile(map, uri);
		}
		catch (Exception e) {
			LogUtils.warn("Eagle migrate resolve failed: " + uri + " — " + e.getMessage());
		}
		final boolean exists = absolute != null && absolute.isFile();

		// Path still works → keep original URI (no Eagle required)
		if (exists) {
			result.keptPath++;
			return;
		}

		// Broken path → unique filename match in Eagle (one-shot fix for moved files)
		final EagleItem match = EagleItemIndex.getInstance().findUniqueByFileNameHint(uriPath(uri));
		if (match == null) {
			result.unmatched++;
			result.unmatchedDetails.add(node.getID() + " " + uri + " (missing file, no unique Eagle match by filename)");
			return;
		}
		final URI eagleUri = EagleUri.create(match.getId(), match.getExt());
		if (!replaceExternalResource(node, resource, eagleUri)) {
			result.unmatched++;
			result.unmatchedDetails.add(node.getID() + " failed to write " + eagleUri);
			return;
		}
		result.migrated++;
		result.migratedDetails.add(node.getID() + " " + uri + " → " + eagleUri + " (by filename)");
	}

	private static boolean replaceExternalResource(final NodeModel node, final ExternalResource oldResource,
			final URI eagleUri) {
		try {
			final float zoom = oldResource.getZoom();
			final ModeController modeController = Controller.getCurrentModeController();
			final ViewerController viewer = modeController == null ? null
					: (ViewerController) modeController.getExtension(ViewerController.class);
			final ExternalResource next = new ExternalResource(eagleUri);
			if (zoom > 0) {
				next.setZoom(zoom);
			}
			if (viewer != null) {
				viewer.undoableDeactivateHook(node);
				viewer.undoableActivateHook(node, next);
			}
			else {
				node.removeExtension(ExternalResource.class);
				node.addExtension(next);
			}
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("Eagle replace ExternalResource failed: " + e.getMessage());
			return false;
		}
	}

	private static String uriPath(final URI uri) {
		if (uri == null) {
			return null;
		}
		if (uri.getPath() != null && uri.getPath().length() > 0) {
			return uri.getPath();
		}
		return uri.getSchemeSpecificPart();
	}
}
