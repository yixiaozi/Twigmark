package org.freeplane.plugin.workspace.features.mapfilter;

/**
 * Per-map tag filter modes. Each mode keeps its own selected tag set.
 */
public enum TagFilterMode {
	/** Show nodes that have at least one of the selected tags (OR). */
	INCLUDE("include"),
	/** Hide nodes that have any of the selected tags. */
	EXCLUDE("exclude"),
	/** Show nodes that have every selected tag (AND). */
	ALL("all");

	private final String xmlValue;

	private TagFilterMode(final String xmlValue) {
		this.xmlValue = xmlValue;
	}

	public String getXmlValue() {
		return xmlValue;
	}

	public static TagFilterMode fromXml(final String value) {
		if (value == null || value.trim().length() == 0) {
			return INCLUDE;
		}
		final String normalized = value.trim().toLowerCase();
		for (final TagFilterMode mode : values()) {
			if (mode.xmlValue.equals(normalized)) {
				return mode;
			}
		}
		return INCLUDE;
	}

	/** Default for untagged nodes when the user has not overridden it for this mode switch. */
	public boolean defaultShowUntagged() {
		return this == EXCLUDE;
	}
}
