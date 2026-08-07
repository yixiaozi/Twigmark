package org.freeplane.view.swing.features.reports;

/**
 * Coarse progress while a report is being built on a background thread.
 * {@code percent} is 0–100; use a negative value for indeterminate stage updates.
 */
public interface ReportProgress {
	void update(int percent, String message);
}
