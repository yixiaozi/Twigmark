package org.freeplane.plugin.workspace.features.mapfilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.freeplane.features.filter.condition.ASelectableCondition;
import org.freeplane.features.map.NodeModel;
import org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagUtils;
import org.freeplane.n3.nanoxml.XMLElement;

/**
 * Freeplane filter condition that matches nodes by {@code 【tag】} markers only.
 */
public class MapTagFilterCondition extends ASelectableCondition {

	static final String NAME = "docear_map_tag_filter";

	private final TagFilterMode mode;
	private final LinkedHashSet tags;
	private final boolean showUntagged;

	public MapTagFilterCondition(final TagFilterMode mode, final Collection selectedTags,
			final boolean showUntagged) {
		this.mode = mode != null ? mode : TagFilterMode.INCLUDE;
		this.tags = new LinkedHashSet();
		if (selectedTags != null) {
			final Iterator it = selectedTags.iterator();
			while (it.hasNext()) {
				final Object item = it.next();
				if (item == null) {
					continue;
				}
				final String tag = String.valueOf(item).trim();
				if (tag.length() > 0) {
					this.tags.add(tag);
				}
			}
		}
		this.showUntagged = showUntagged;
	}

	public TagFilterMode getMode() {
		return mode;
	}

	public Set getTags() {
		return Collections.unmodifiableSet(tags);
	}

	public boolean isShowUntagged() {
		return showUntagged;
	}

	public boolean checkNode(final NodeModel node) {
		if (node == null || node.isRoot()) {
			return true;
		}
		final Set nodeTags = NodeDetailsTagUtils.parseUserTags(node.getText());
		if (nodeTags.isEmpty()) {
			return showUntagged;
		}
		if (tags.isEmpty()) {
			return true;
		}
		if (mode == TagFilterMode.EXCLUDE) {
			final Iterator it = tags.iterator();
			while (it.hasNext()) {
				if (nodeTags.contains(it.next())) {
					return false;
				}
			}
			return true;
		}
		if (mode == TagFilterMode.ALL) {
			final Iterator it = tags.iterator();
			while (it.hasNext()) {
				if (!nodeTags.contains(it.next())) {
					return false;
				}
			}
			return true;
		}
		// INCLUDE (OR)
		final Iterator it = tags.iterator();
		while (it.hasNext()) {
			if (nodeTags.contains(it.next())) {
				return true;
			}
		}
		return false;
	}

	protected String createDescription() {
		final StringBuilder sb = new StringBuilder();
		if (mode == TagFilterMode.EXCLUDE) {
			sb.append("排除标签");
		}
		else if (mode == TagFilterMode.ALL) {
			sb.append("同时包含");
		}
		else {
			sb.append("仅看标签");
		}
		if (!tags.isEmpty()) {
			sb.append(": ");
			final Iterator it = tags.iterator();
			boolean first = true;
			while (it.hasNext()) {
				if (!first) {
					sb.append(", ");
				}
				sb.append('【').append(it.next()).append('】');
				first = false;
			}
		}
		return sb.toString();
	}

	protected String getName() {
		return NAME;
	}

	protected void fillXML(final XMLElement element) {
		element.setAttribute("mode", mode.getXmlValue());
		element.setAttribute("untagged", showUntagged ? "show" : "hide");
		element.setAttribute("tags", TagFilterMapExtensionIO.joinTags(tags));
	}

	public List snapshotTags() {
		return new ArrayList(tags);
	}
}
