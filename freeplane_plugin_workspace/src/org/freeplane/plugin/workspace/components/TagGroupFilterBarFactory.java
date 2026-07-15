package org.freeplane.plugin.workspace.components;

import java.util.Collections;
import java.util.Set;

import javax.swing.JComponent;

import org.freeplane.plugin.workspace.components.tagfilter.TagGroupCascadeBar;
import org.freeplane.plugin.workspace.features.nodepins.TagGroupStore;

/**
 * Factory so other plugins (e.g. docear_plugin_core) can use tag-group cascade UI
 * without importing the non-exported {@code ...tagfilter} package across OSGi
 * classloaders (avoids {@code NoClassDefFoundError: TagGroupCascadeBar}).
 */
public final class TagGroupFilterBarFactory {

	private TagGroupFilterBarFactory() {
	}

	public static JComponent createTagsBar(final String propActiveGroup, final String propDirectOnly,
	        final Runnable onSelectionChanged) {
		return create(TagGroupStore.getInstance(), propActiveGroup, propDirectOnly, onSelectionChanged);
	}

	public static JComponent createFavoritesBar(final String propActiveGroup, final String propDirectOnly,
	        final Runnable onSelectionChanged) {
		return create(TagGroupStore.getFavoritesInstance(), propActiveGroup, propDirectOnly, onSelectionChanged);
	}

	private static JComponent create(final TagGroupStore store, final String propActiveGroup,
	        final String propDirectOnly, final Runnable onSelectionChanged) {
		final TagGroupCascadeBar bar = new TagGroupCascadeBar(store, propActiveGroup, propDirectOnly, true);
		bar.bind(onSelectionChanged, Collections.EMPTY_SET);
		return bar;
	}

	public static void setAvailableTags(final JComponent bar, final Set availableTags) {
		final TagGroupCascadeBar cascade = asCascade(bar);
		if (cascade != null) {
			cascade.setAvailableTagsSnapshot(availableTags);
		}
	}

	public static void rebuild(final JComponent bar) {
		final TagGroupCascadeBar cascade = asCascade(bar);
		if (cascade != null) {
			cascade.rebuild();
		}
	}

	public static boolean isAllScope(final JComponent bar) {
		final TagGroupCascadeBar cascade = asCascade(bar);
		return cascade != null && cascade.isAllScope();
	}

	public static boolean tagMatchesActiveScope(final JComponent bar, final String tag) {
		final TagGroupCascadeBar cascade = asCascade(bar);
		return cascade != null && cascade.tagMatchesActiveScope(tag);
	}

	private static TagGroupCascadeBar asCascade(final JComponent bar) {
		return bar instanceof TagGroupCascadeBar ? (TagGroupCascadeBar) bar : null;
	}
}
