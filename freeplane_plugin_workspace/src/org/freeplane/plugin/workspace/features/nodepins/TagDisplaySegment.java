package org.freeplane.plugin.workspace.features.nodepins;

/**
 * One display piece of a mind-map node title: plain text or a {@code 【tag】} chip.
 */
public final class TagDisplaySegment {

	public static final int TYPE_TEXT = 0;
	public static final int TYPE_TAG = 1;

	private final int type;
	private final String value;

	private TagDisplaySegment(final int type, final String value) {
		this.type = type;
		this.value = value != null ? value : "";
	}

	public static TagDisplaySegment text(final String value) {
		return new TagDisplaySegment(TYPE_TEXT, value);
	}

	public static TagDisplaySegment tag(final String value) {
		return new TagDisplaySegment(TYPE_TAG, value);
	}

	public boolean isTag() {
		return type == TYPE_TAG;
	}

	public boolean isText() {
		return type == TYPE_TEXT;
	}

	public String getValue() {
		return value;
	}
}
