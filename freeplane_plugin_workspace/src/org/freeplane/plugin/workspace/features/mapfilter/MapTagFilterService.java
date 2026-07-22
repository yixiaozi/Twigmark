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
		return new ArrayList(collectMapTagCounts(map).keySet());
	}

	/** tag -> node count (nodes that contain the tag). */
	public static java.util.Map collectMapTagCounts(final MapModel map) {
		final java.util.LinkedHashMap counts = new java.util.LinkedHashMap();
		if (map == null || map.getRootNode() == null) {
			return counts;
		}
		collectTagCountsRecursive(map.getRootNode(), counts);
		final ArrayList keys = new ArrayList(counts.keySet());
		Collections.sort(keys, new Comparator() {
			public int compare(final Object a, final Object b) {
				return String.valueOf(a).compareToIgnoreCase(String.valueOf(b));
			}
		});
		final java.util.LinkedHashMap sorted = new java.util.LinkedHashMap();
		for (int i = 0; i < keys.size(); i++) {
			final Object key = keys.get(i);
			sorted.put(key, counts.get(key));
		}
		return sorted;
	}

	public static List collectNodesWithTag(final MapModel map, final String tag) {
		final ArrayList nodes = new ArrayList();
		if (map == null || map.getRootNode() == null || tag == null || tag.trim().length() == 0) {
			return nodes;
		}
		collectNodesWithTagRecursive(map.getRootNode(), tag.trim(), nodes);
		return nodes;
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

	private static void collectTagCountsRecursive(final NodeModel node, final java.util.Map counts) {
		if (node == null) {
			return;
		}
		final Set nodeTags = NodeDetailsTagUtils.parseUserTags(node.getText());
		if (nodeTags != null) {
			final Iterator it = nodeTags.iterator();
			while (it.hasNext()) {
				final String tag = String.valueOf(it.next());
				final Integer prev = (Integer) counts.get(tag);
				counts.put(tag, Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
			}
		}
		final List children = node.getChildren();
		if (children == null) {
			return;
		}
		for (int i = 0; i < children.size(); i++) {
			collectTagCountsRecursive((NodeModel) children.get(i), counts);
		}
	}

	private static void collectNodesWithTagRecursive(final NodeModel node, final String tag, final List out) {
		if (node == null) {
			return;
		}
		final Set nodeTags = NodeDetailsTagUtils.parseUserTags(node.getText());
		if (nodeTags != null && nodeTags.contains(tag)) {
			out.add(node);
		}
		final List children = node.getChildren();
		if (children == null) {
			return;
		}
		for (int i = 0; i < children.size(); i++) {
			collectNodesWithTagRecursive((NodeModel) children.get(i), tag, out);
		}
	}

	public static void applyFromExtension(final MapModel map) {
		if (map == null) {
			return;
		}
		TagFilterMapExtensionIO.ensureLoadedFromUnknownElements(map);
		final TagFilterMapExtension extension = TagFilterMapExtension.getOrCreate(map);
		if (!extension.getMode().filtersMap() || !extension.hasActiveFilter()) {
			// Already transparent / not our filter — avoid a full map re-filter.
			if (isOurFilterActive(map)) {
				clearFilter(map);
			}
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
		setModeStateOnly(map, mode);
		applyFromExtension(map);
	}

	/** Persist mode change without applying filter (UI can paint first). */
	public static void setModeStateOnly(final MapModel map, final TagFilterMode mode) {
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
	}

	public static void toggleTag(final MapModel map, final String tag) {
		toggleTagStateOnly(map, tag);
		applyFromExtension(map);
	}

	public static void toggleTagStateOnly(final MapModel map, final String tag) {
		if (map == null || tag == null || tag.trim().length() == 0) {
			return;
		}
		final TagFilterMapExtension extension = TagFilterMapExtension.getOrCreate(map);
		extension.toggleTag(extension.getMode(), tag.trim());
		markDirty(map);
	}

	public static void setShowUntagged(final MapModel map, final boolean show) {
		setShowUntaggedStateOnly(map, show);
		applyFromExtension(map);
	}

	public static void setShowUntaggedStateOnly(final MapModel map, final boolean show) {
		if (map == null) {
			return;
		}
		final TagFilterMapExtension extension = TagFilterMapExtension.getOrCreate(map);
		if (extension.isShowUntagged() == show) {
			return;
		}
		extension.setShowUntagged(show);
		markDirty(map);
	}

	public static void clearActiveModeTags(final MapModel map) {
		clearActiveModeTagsStateOnly(map);
		applyFromExtension(map);
	}

	public static void clearActiveModeTagsStateOnly(final MapModel map) {
		if (map == null) {
			return;
		}
		final TagFilterMapExtension extension = TagFilterMapExtension.getOrCreate(map);
		extension.clearTagsForMode(extension.getMode());
		markDirty(map);
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
		if (extension == null || extension.getActiveTagCount() == 0) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		if (extension.getMode() == TagFilterMode.VIEW) {
			sb.append("查看 ");
		}
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
