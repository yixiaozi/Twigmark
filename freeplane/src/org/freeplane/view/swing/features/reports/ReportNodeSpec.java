package org.freeplane.view.swing.features.reports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One node in a generated report tree (written into the mind map).
 */
public final class ReportNodeSpec {
	public final String text;
	public final String iconName;
	private final List children = new ArrayList();

	public ReportNodeSpec(final String text) {
		this(text, null);
	}

	public ReportNodeSpec(final String text, final String iconName) {
		this.text = text == null ? "" : text;
		this.iconName = iconName;
	}

	public ReportNodeSpec add(final String childText, final String childIcon) {
		final ReportNodeSpec child = new ReportNodeSpec(childText, childIcon);
		children.add(child);
		return child;
	}

	public ReportNodeSpec add(final ReportNodeSpec child) {
		if (child != null) {
			children.add(child);
		}
		return child;
	}

	public List getChildren() {
		return Collections.unmodifiableList(children);
	}

	public boolean hasChildren() {
		return !children.isEmpty();
	}
}
