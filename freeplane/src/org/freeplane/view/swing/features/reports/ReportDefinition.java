package org.freeplane.view.swing.features.reports;

/**
 * Catalog entry for one report type.
 */
public final class ReportDefinition {
	public final String id;
	public final String title;
	public final String description;
	public final String iconName;
	public final boolean usesTimeRange;
	/** Product: which decision this report supports. */
	public String decision = "";
	/** Product: human-readable data source. */
	public String dataSource = "";

	public ReportDefinition(final String id, final String title, final String description, final String iconName,
	        final boolean usesTimeRange) {
		this.id = id;
		this.title = title;
		this.description = description;
		this.iconName = iconName;
		this.usesTimeRange = usesTimeRange;
	}

	public String toString() {
		return title;
	}
}
