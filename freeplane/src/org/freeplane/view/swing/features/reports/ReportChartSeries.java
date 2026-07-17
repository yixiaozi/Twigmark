package org.freeplane.view.swing.features.reports;

import java.util.ArrayList;
import java.util.List;

/**
 * One named numeric series for a report chart (pie / bar / line).
 */
public final class ReportChartSeries {
	public static final int TYPE_PIE = 1;
	public static final int TYPE_BAR = 2;
	public static final int TYPE_LINE = 3;

	public final String title;
	public final int type;
	public final List labels = new ArrayList();
	public final List values = new ArrayList(); // Double

	public ReportChartSeries(final String title, final int type) {
		this.title = title == null ? "" : title;
		this.type = type;
	}

	public void add(final String label, final double value) {
		if (label == null) {
			return;
		}
		if (value < 0) {
			return;
		}
		labels.add(label);
		values.add(Double.valueOf(value));
	}

	public int size() {
		return labels.size();
	}

	public boolean isEmpty() {
		return labels.isEmpty();
	}

	public double valueAt(final int index) {
		return ((Double) values.get(index)).doubleValue();
	}

	public String labelAt(final int index) {
		return (String) labels.get(index);
	}

	public double maxValue() {
		double max = 0;
		for (int i = 0; i < values.size(); i++) {
			final double v = valueAt(i);
			if (v > max) {
				max = v;
			}
		}
		return max;
	}

	public double sum() {
		double s = 0;
		for (int i = 0; i < values.size(); i++) {
			s += valueAt(i);
		}
		return s;
	}
}
