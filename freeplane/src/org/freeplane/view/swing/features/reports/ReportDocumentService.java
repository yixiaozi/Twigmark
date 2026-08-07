package org.freeplane.view.swing.features.reports;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.SwingUtilities;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ui.IDocumentTabView;
import org.freeplane.features.usagestats.UsageStatsReportService;
import org.freeplane.main.application.DocumentTabSupport;

/**
 * Opens each report as its own bottom document tab (like mind maps), so users can
 * keep several reports open, switch, group, and close them individually.
 */
public final class ReportDocumentService {

	private static final List openViews = new ArrayList();

	private ReportDocumentService() {
	}

	/**
	 * Open a new tab for {@code content}. Does not reuse or replace existing report tabs.
	 */
	public static ReportDocumentView openNew(final String title, final Component content,
	        final String assignmentKey) {
		if (content == null) {
			return null;
		}
		if (SwingUtilities.isEventDispatchThread()) {
			return openNewNow(title, content, assignmentKey);
		}
		final ReportDocumentView[] holder = new ReportDocumentView[1];
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					holder[0] = openNewNow(title, content, assignmentKey);
				}
			});
		}
		catch (Exception e) {
			LogUtils.warn("ReportDocumentService.openNew failed: " + e.getMessage(), e);
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					openNewNow(title, content, assignmentKey);
				}
			});
		}
		return holder[0];
	}

	private static ReportDocumentView openNewNow(final String title, final Component content,
	        final String assignmentKey) {
		try {
			final UsageStatsReportService activity = UsageStatsReportService.get();
			if (activity != null) {
				activity.releaseSoftViewport();
			}
			final ReportDocumentView view = new ReportDocumentView(assignmentKey);
			view.setContent(title, content);
			synchronized (openViews) {
				openViews.add(view);
			}
			DocumentTabSupport.openDocumentTab(view);
			DocumentTabSupport.selectDocumentTab(view);
			return view;
		}
		catch (Exception e) {
			LogUtils.warn("ReportDocumentService.openNew failed: " + e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Backward-compatible: always opens a <b>new</b> tab (no longer a singleton).
	 */
	public static void showInTab(final String title, final Component content) {
		openNew(title, content, "report://adhoc/" + System.nanoTime());
	}

	public static void close(final ReportDocumentView view) {
		if (view == null) {
			return;
		}
		final Runnable close = new Runnable() {
			public void run() {
				if (!view.isClosed()) {
					view.requestClose(true);
				}
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			close.run();
		}
		else {
			SwingUtilities.invokeLater(close);
		}
	}

	/** Close the report tab that currently hosts {@code content}. */
	public static void closeContent(final Component content) {
		if (content == null) {
			return;
		}
		final ReportDocumentView view = findByContent(content);
		if (view != null) {
			close(view);
		}
	}

	/** Close the active tab if it is a report document. */
	public static void closeTab() {
		final IDocumentTabView active = DocumentTabSupport.getActiveDocumentView();
		if (active instanceof ReportDocumentView) {
			close((ReportDocumentView) active);
		}
	}

	static void forget(final ReportDocumentView view) {
		if (view == null) {
			return;
		}
		synchronized (openViews) {
			openViews.remove(view);
		}
	}

	public static ReportDocumentView findByContent(final Component content) {
		if (content == null) {
			return null;
		}
		synchronized (openViews) {
			for (final Iterator it = openViews.iterator(); it.hasNext();) {
				final ReportDocumentView view = (ReportDocumentView) it.next();
				if (view.getContent() == content) {
					return view;
				}
			}
		}
		return null;
	}

	public static boolean isOpen() {
		return DocumentTabSupport.getActiveDocumentView() instanceof ReportDocumentView;
	}

	public static int openCount() {
		synchronized (openViews) {
			return openViews.size();
		}
	}
}
