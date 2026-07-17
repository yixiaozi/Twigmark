package org.freeplane.view.swing.features.reports;

/**
 * One KPI tile shown above charts (e.g. "逾期 3", "今日计划 4.5h").
 */
public final class ReportKpi {
	public final String label;
	public final String value;
	public final String hint;

	public ReportKpi(final String label, final String value, final String hint) {
		this.label = label == null ? "" : label;
		this.value = value == null ? "" : value;
		this.hint = hint == null ? "" : hint;
	}
}
