package org.freeplane.plugin.workspace.features.mapfilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.map.MapModel;

/**
 * Persists map-level tag filter state on the {@code <map>} element. Each mode
 * keeps an independent tag selection so switching modes restores prior picks.
 */
public class TagFilterMapExtension implements IExtension {

	private TagFilterMode mode = TagFilterMode.INCLUDE;
	private final LinkedHashSet includeTags = new LinkedHashSet();
	private final LinkedHashSet excludeTags = new LinkedHashSet();
	private final LinkedHashSet allTags = new LinkedHashSet();
	/** Null = follow mode default (include/all hide, exclude show). */
	private Boolean showUntaggedOverride = null;
	private boolean collapsed = true;

	public TagFilterMode getMode() {
		return mode;
	}

	public void setMode(final TagFilterMode mode) {
		this.mode = mode != null ? mode : TagFilterMode.INCLUDE;
	}

	public boolean isShowUntagged() {
		if (showUntaggedOverride != null) {
			return showUntaggedOverride.booleanValue();
		}
		return mode.defaultShowUntagged();
	}

	public void setShowUntagged(final boolean showUntagged) {
		this.showUntaggedOverride = Boolean.valueOf(showUntagged);
	}

	public void clearShowUntaggedOverride() {
		this.showUntaggedOverride = null;
	}

	public boolean hasShowUntaggedOverride() {
		return showUntaggedOverride != null;
	}

	public boolean isCollapsed() {
		return collapsed;
	}

	public void setCollapsed(final boolean collapsed) {
		this.collapsed = collapsed;
	}

	public Set getTagsForMode(final TagFilterMode forMode) {
		return Collections.unmodifiableSet(mutableTags(forMode));
	}

	public Set getActiveTags() {
		return getTagsForMode(mode);
	}

	public void setTagsForMode(final TagFilterMode forMode, final Set tags) {
		final LinkedHashSet target = mutableTags(forMode);
		target.clear();
		if (tags == null) {
			return;
		}
		final Iterator it = tags.iterator();
		while (it.hasNext()) {
			final Object item = it.next();
			if (item == null) {
				continue;
			}
			final String tag = String.valueOf(item).trim();
			if (tag.length() > 0) {
				target.add(tag);
			}
		}
	}

	public void toggleTag(final TagFilterMode forMode, final String tag) {
		if (tag == null || tag.trim().length() == 0) {
			return;
		}
		final String normalized = tag.trim();
		final LinkedHashSet target = mutableTags(forMode);
		if (target.contains(normalized)) {
			target.remove(normalized);
		}
		else {
			target.add(normalized);
		}
	}

	public void clearTagsForMode(final TagFilterMode forMode) {
		mutableTags(forMode).clear();
	}

	public void clearAllModes() {
		includeTags.clear();
		excludeTags.clear();
		allTags.clear();
	}

	public boolean hasActiveFilter() {
		return !getActiveTags().isEmpty();
	}

	public int getActiveTagCount() {
		return getActiveTags().size();
	}

	public List snapshotActiveTags() {
		return new ArrayList(getActiveTags());
	}

	private LinkedHashSet mutableTags(final TagFilterMode forMode) {
		final TagFilterMode resolved = forMode != null ? forMode : mode;
		if (resolved == TagFilterMode.EXCLUDE) {
			return excludeTags;
		}
		if (resolved == TagFilterMode.ALL) {
			return allTags;
		}
		return includeTags;
	}

	public static TagFilterMapExtension getOrCreate(final MapModel map) {
		TagFilterMapExtension extension = map.getExtension(TagFilterMapExtension.class);
		if (extension == null) {
			extension = new TagFilterMapExtension();
			map.addExtension(TagFilterMapExtension.class, extension);
		}
		return extension;
	}

	public static TagFilterMapExtension get(final MapModel map) {
		if (map == null) {
			return null;
		}
		return map.getExtension(TagFilterMapExtension.class);
	}
}
