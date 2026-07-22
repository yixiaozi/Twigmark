package org.freeplane.plugin.workspace.features.mapfilter;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.io.IAttributeHandler;
import org.freeplane.core.io.IExtensionAttributeWriter;
import org.freeplane.core.io.ITreeWriter;
import org.freeplane.core.io.ReadManager;
import org.freeplane.core.io.UnknownElements;
import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.n3.nanoxml.XMLElement;

/**
 * Reads/writes tag-filter attributes on the {@code <map>} element.
 */
public class TagFilterMapExtensionIO implements IExtensionAttributeWriter {

	static final String MAP_TAG = "map";
	public static final String MODE_ATTR = "docear_tag_filter_mode";
	public static final String INCLUDE_ATTR = "docear_tag_filter_include";
	public static final String EXCLUDE_ATTR = "docear_tag_filter_exclude";
	public static final String ALL_ATTR = "docear_tag_filter_all";
	public static final String UNTAGGED_ATTR = "docear_tag_filter_untagged";

	private TagFilterMapExtensionIO(final MapController mapController) {
		registerAttributeHandlers(mapController.getReadManager());
		mapController.getWriteManager().addExtensionAttributeWriter(TagFilterMapExtension.class, this);
	}

	public static void install(final ModeController modeController) {
		final MapController mapController = modeController.getMapController();
		new TagFilterMapExtensionIO(mapController);
		mapController.addMapLifeCycleListener(new IMapLifeCycleListener() {
			public void onCreate(final MapModel map) {
				ensureLoadedFromUnknownElements(map);
			}

			public void onRemove(final MapModel map) {
			}

			public void onSavedAs(final MapModel map) {
			}

			public void onSaved(final MapModel map) {
			}
		});
	}

	private void registerAttributeHandlers(final ReadManager reader) {
		reader.addAttributeHandler(MAP_TAG, MODE_ATTR, new IAttributeHandler() {
			public void setAttribute(final Object userObject, final String value) {
				final TagFilterMapExtension extension = extensionOf(userObject);
				if (extension != null) {
					extension.setMode(TagFilterMode.fromXml(value));
				}
			}
		});
		reader.addAttributeHandler(MAP_TAG, INCLUDE_ATTR, new IAttributeHandler() {
			public void setAttribute(final Object userObject, final String value) {
				final TagFilterMapExtension extension = extensionOf(userObject);
				if (extension != null) {
					extension.setTagsForMode(TagFilterMode.INCLUDE, parseTags(value));
				}
			}
		});
		reader.addAttributeHandler(MAP_TAG, EXCLUDE_ATTR, new IAttributeHandler() {
			public void setAttribute(final Object userObject, final String value) {
				final TagFilterMapExtension extension = extensionOf(userObject);
				if (extension != null) {
					extension.setTagsForMode(TagFilterMode.EXCLUDE, parseTags(value));
				}
			}
		});
		reader.addAttributeHandler(MAP_TAG, ALL_ATTR, new IAttributeHandler() {
			public void setAttribute(final Object userObject, final String value) {
				final TagFilterMapExtension extension = extensionOf(userObject);
				if (extension != null) {
					extension.setTagsForMode(TagFilterMode.ALL, parseTags(value));
				}
			}
		});
		reader.addAttributeHandler(MAP_TAG, UNTAGGED_ATTR, new IAttributeHandler() {
			public void setAttribute(final Object userObject, final String value) {
				final TagFilterMapExtension extension = extensionOf(userObject);
				if (extension != null) {
					extension.setShowUntagged(!"hide".equalsIgnoreCase(value));
				}
			}
		});
	}

	public void writeAttributes(final ITreeWriter writer, final Object userObject, final IExtension extension) {
		final TagFilterMapExtension state = (TagFilterMapExtension) extension;
		if (!hasAnyState(state)) {
			return;
		}
		writer.addAttribute(MODE_ATTR, state.getMode().getXmlValue());
		writeTags(writer, INCLUDE_ATTR, state.getTagsForMode(TagFilterMode.INCLUDE));
		writeTags(writer, EXCLUDE_ATTR, state.getTagsForMode(TagFilterMode.EXCLUDE));
		writeTags(writer, ALL_ATTR, state.getTagsForMode(TagFilterMode.ALL));
		writer.addAttribute(UNTAGGED_ATTR, state.isShowUntagged() ? "show" : "hide");
	}

	private static boolean hasAnyState(final TagFilterMapExtension state) {
		return !state.getTagsForMode(TagFilterMode.INCLUDE).isEmpty()
				|| !state.getTagsForMode(TagFilterMode.EXCLUDE).isEmpty()
				|| !state.getTagsForMode(TagFilterMode.ALL).isEmpty()
				|| state.getMode() != TagFilterMode.INCLUDE
				|| state.hasShowUntaggedOverride();
	}

	private static void writeTags(final ITreeWriter writer, final String attr, final Set tags) {
		if (tags == null || tags.isEmpty()) {
			return;
		}
		writer.addAttribute(attr, joinTags(tags));
	}

	public static void ensureLoadedFromUnknownElements(final MapModel map) {
		if (map == null) {
			return;
		}
		final UnknownElements unknown = map.getExtension(UnknownElements.class);
		if (unknown == null || unknown.getUnknownElements() == null) {
			return;
		}
		final XMLElement xml = unknown.getUnknownElements();
		final TagFilterMapExtension extension = TagFilterMapExtension.getOrCreate(map);
		final String mode = xml.getAttribute(MODE_ATTR, null);
		if (mode != null && mode.trim().length() > 0) {
			extension.setMode(TagFilterMode.fromXml(mode));
			xml.removeAttribute(MODE_ATTR);
		}
		promoteTags(xml, extension, INCLUDE_ATTR, TagFilterMode.INCLUDE);
		promoteTags(xml, extension, EXCLUDE_ATTR, TagFilterMode.EXCLUDE);
		promoteTags(xml, extension, ALL_ATTR, TagFilterMode.ALL);
		final String untagged = xml.getAttribute(UNTAGGED_ATTR, null);
		if (untagged != null) {
			extension.setShowUntagged(!"hide".equalsIgnoreCase(untagged));
			xml.removeAttribute(UNTAGGED_ATTR);
		}
	}

	private static void promoteTags(final XMLElement xml, final TagFilterMapExtension extension,
			final String attr, final TagFilterMode mode) {
		final String value = xml.getAttribute(attr, null);
		if (value == null) {
			return;
		}
		extension.setTagsForMode(mode, parseTags(value));
		xml.removeAttribute(attr);
	}

	private static TagFilterMapExtension extensionOf(final Object userObject) {
		if (!(userObject instanceof MapModel)) {
			return null;
		}
		return TagFilterMapExtension.getOrCreate((MapModel) userObject);
	}

	static Set parseTags(final String csv) {
		final LinkedHashSet tags = new LinkedHashSet();
		if (csv == null || csv.trim().length() == 0) {
			return tags;
		}
		final String[] parts = csv.split("[,，;；]");
		for (int i = 0; i < parts.length; i++) {
			final String tag = parts[i].trim();
			if (tag.length() > 0) {
				tags.add(tag);
			}
		}
		return tags;
	}

	static String joinTags(final Set tags) {
		if (tags == null || tags.isEmpty()) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		final Iterator it = tags.iterator();
		while (it.hasNext()) {
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(String.valueOf(it.next()));
		}
		return sb.toString();
	}
}
