package org.freeplane.view.swing.features.reports;

/**
 * Progress while a report is being built on a background thread.
 * <p>
 * Most of the work is opaque I/O / workspace scans, so callers should prefer
 * {@code percent &lt; 0} (indeterminate) with a stage message during collection,
 * and only use 0–100 near the end (assemble / render).
 */
public interface ReportProgress {
	void update(int percent, String message);
}
