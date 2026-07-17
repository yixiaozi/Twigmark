package org.freeplane.view.swing.features.reports;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured report for the center viewport: charts + detail lines.
 */
public final class ReportViewModel {
	public final String title;
	public final String subtitle;
	public final List charts = new ArrayList(); // ReportChartSeries
	public final List details = new ArrayList(); // String

	public ReportViewModel(final String title, final String subtitle) {
		this.title = title == null ? "报表" : title;
		this.subtitle = subtitle == null ? "" : subtitle;
	}

	public void addChart(final ReportChartSeries series) {
		if (series != null && !series.isEmpty()) {
			charts.add(series);
		}
	}

	public void addDetail(final String line) {
		if (line != null && line.trim().length() > 0) {
			details.add(line);
		}
	}

	public boolean hasCharts() {
		return !charts.isEmpty();
	}
}
