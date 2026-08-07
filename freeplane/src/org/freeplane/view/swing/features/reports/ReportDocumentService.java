package org.freeplane.view.swing.features.reports;

import java.awt.Component;

import javax.swing.SwingUtilities;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.usagestats.UsageStatsReportService;
import org.freeplane.main.application.DocumentTabSupport;

/**
 * Opens report UIs as a bottom document tab (like Draw.io), so users can close / group
 * them like mind maps and always have a path back via closing the tab or 「返回导图」.
 */
public final class ReportDocumentService {

	private static ReportDocumentView documentView;

	private ReportDocumentService() {
	}

	public static synchronized ReportDocumentView getDocumentView() {
		if (documentView == null) {
			documentView = new ReportDocumentView();
		}
		return documentView;
	}

	public static void showInTab(final String title, final Component content) {
		if (content == null) {
			return;
		}
		final Runnable show = new Runnable() {
			public void run() {
				try {
					// Soft viewport services must not fight the document tab.
					final ReportViewportService charts = ReportViewportService.get();
					if (charts != null) {
						charts.releaseSoftViewport();
					}
					final UsageStatsReportService activity = UsageStatsReportService.get();
					if (activity != null) {
						activity.releaseSoftViewport();
					}
					final ReportDocumentView view = getDocumentView();
					view.setContent(title, content);
					DocumentTabSupport.openDocumentTab(view);
					DocumentTabSupport.selectDocumentTab(view);
				}
				catch (Exception e) {
					LogUtils.warn("ReportDocumentService.showInTab failed: " + e.getMessage(), e);
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			show.run();
		}
		else {
			SwingUtilities.invokeLater(show);
		}
	}

	public static void closeTab() {
		final ReportDocumentView view = documentView;
		if (view == null) {
			return;
		}
		final Runnable close = new Runnable() {
			public void run() {
				view.requestClose(true);
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			close.run();
		}
		else {
			SwingUtilities.invokeLater(close);
		}
	}

	public static boolean isOpen() {
		return documentView != null && DocumentTabSupport.getActiveDocumentView() == documentView;
	}
}
