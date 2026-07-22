package org.freeplane.plugin.workspace.features.mapfilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.freeplane.features.filter.Filter;
import org.freeplane.features.filter.FilterController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagUtils;

/**
 * Applies / clears the map tag filter through Freeplane {@link FilterController}.
 */
public final class MapTagFilterService {

	private MapTagFilterService() {
	}

	public static List collectMapTags(final MapModel map) {
		final LinkedHashSet tags = new LinkedHashSet();
		if (map == null || map.getRootNode() == null) {
			return new ArrayList();
		}
		collectTagsRecursive(map.getRootNode(), tags);
		final ArrayList sorted = new ArrayList(tags);
		Collections.sort(sorted, new Comparator() {
			public int compare(final Object a, final Object b) {
				return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
			}
		});
		return sorted;
	}

	private static void collectTagsRecursive(final NodeModel node, final Set tags) {
		if (node == null) {
			return;
		}
		final Set nodeTags = NodeDetailsTagUtils.parseUserTags(node.getText());
		if (nodeTags != null && !nodeTags.isEmpty()) {
			tags.addAll(nodeTags);
		}
		final List children = node.getChildren();
		if (children == null) {
			return;
		}
		for (int i = 0; i < children.size(); i++) {
			collectTagsRecursive((NodeModel) children.get(i), tags);
		}
	}

	public static void applyFromExtension(final MapModel map) {
		if (map == null) {
			return;
		}
		TagFilterMapExtensionIO.ensureLoadedFromUnknownElements(map);
		final TagFilterMapExtension extension = TagFilterMapExtension.getOrCreate(map);
		if (!extension.hasActiveFilter()) {
			clearFilter(map);
			return;
		}
		final MapTagFilterCondition condition = new MapTagFilterCondition(extension.getMode(),
				extension.getActiveTags(), extension.isShowUntagged());
		final FilterController filterController = FilterController.getCurrentFilterController();
		if (filterController == null) {
			return;
		}
		// Prefer Freeplane Filter pipeline (ancestors kept via toolbar default / explicit Filter).
		filterController.getShowAncestors().setSelected(true);
		filterController.getShowDescendants().setSelected(false);
		final Filter filter = new Filter(condition, true, false, false);
		filterController.applyFilter(filter, map, true);
	}

	public static void clearFilter(final MapModel map) {
		final FilterController filterController = FilterController.getCurrentFilterController();
		if (filterController == null) {
			return;
		}
		if (map != null) {
			final Filter transparent = new Filter(null, true, false, false);
			filterController.applyFilter(transparent, map, true);
		}
		else {
			filterController.applyNoFiltering();
		}
	}

	public static void markDirty(final MapModel map) {
		if (map == null || map.getFile() == null) {
			return;
		}
		try {
			Controller.getCurrentModeController().getMapController().setSaved(map, false);
		}
		catch (final Exception ignore) {
		}
	}

	public static void setMode(final MapModel map, final TagFilterMode mode) {
		if (map == null || mode == null) {
			return;
		}
		final TagFilterMapExtension extension = TagFilterMapExtension.getOrCreate(map);
		if (extension.getMode() == mode) {
			return;
		}
		extension.setMode(mode);
		// Switching mode restores that mode's tags and resets untagged to the mode default.
		extension.clearShowUntaggedOverride();
		markDirty(map);
		applyFromExtension(map);
	}

	public static void toggleTag(final MapModel map, final String tag) {
		if (map == null || tag == null || tag.trim().length() == 0) {
			return;
		}
		final TagFilterMapExtension extension = TagFilterMapExtension.getOrCreate(map);
		extension.toggleTag(extension.getMode(), tag.trim());
		markDirty(map);
		applyFromExtension(map);
	}

	public static void setShowUntagged(final MapModel map, final boolean show) {
		if (map == null) {
			return;
		}
		final TagFilterMapExtension extension = TagFilterMapExtension.getOrCreate(map);
		if (extension.isShowUntagged() == show) {
			return;
		}
		extension.setShowUntagged(show);
		markDirty(map);
		applyFromExtension(map);
	}

	public static void clearActiveModeTags(final MapModel map) {
		if (map == null) {
			return;
		}
		final TagFilterMapExtension extension = TagFilterMapExtension.getOrCreate(map);
		extension.clearTagsForMode(extension.getMode());
		markDirty(map);
		applyFromExtension(map);
	}

	public static void clearAll(final MapModel map) {
		if (map == null) {
			return;
		}
		final TagFilterMapExtension extension = TagFilterMapExtension.getOrCreate(map);
		extension.clearAllModes();
		extension.setMode(TagFilterMode.INCLUDE);
		extension.clearShowUntaggedOverride();
		markDirty(map);
		clearFilter(map);
	}

	public static boolean isOurFilterActive(final MapModel map) {
		if (map == null || map.getFilter() == null) {
			return false;
		}
		return map.getFilter().getCondition() instanceof MapTagFilterCondition;
	}

	public static String summarizeActive(final TagFilterMapExtension extension) {
		if (extension == null || !extension.hasActiveFilter()) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		final Iterator it = extension.getActiveTags().iterator();
		int count = 0;
		while (it.hasNext() && count < 3) {
			if (count > 0) {
				sb.append(' ');
			}
			sb.append('【').append(it.next()).append('】');
			count++;
		}
		final int remaining = extension.getActiveTagCount() - count;
		if (remaining > 0) {
			sb.append(" +").append(remaining);
		}
		return sb.toString();
	}
}
