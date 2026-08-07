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
 * Opens reports as bottom document tabs. Same stable {@code assignmentKey} reuses /
 * focuses an existing tab instead of stacking duplicates.
 */
public final class ReportDocumentService {

	public static final String KEY_ACTIVITY = "report://activity";
	public static final String KEY_MCP_AUDIT = "report://mcp-audit";

	private static final List openViews = new ArrayList();

	private ReportDocumentService() {
	}

	public static String keyForReportId(final String reportId) {
		return "report://" + (reportId == null || reportId.length() == 0 ? "unknown" : reportId);
	}

	/**
	 * Focus an already-open tab with this assignment key, or return null.
	 */
	public static ReportDocumentView focusByKey(final String assignmentKey) {
		final ReportDocumentView existing = findByAssignmentKey(assignmentKey);
		if (existing == null || existing.isClosed()) {
			return null;
		}
		final Runnable select = new Runnable() {
			public void run() {
				DocumentTabSupport.openDocumentTab(existing);
				DocumentTabSupport.selectDocumentTab(existing);
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			select.run();
		}
		else {
			SwingUtilities.invokeLater(select);
		}
		return existing;
	}

	/**
	 * Open a tab, or reuse the existing one with the same {@code assignmentKey}.
	 * When reusing, {@code content} replaces the previous content if different.
	 */
	public static ReportDocumentView openOrFocus(final String title, final Component content,
	        final String assignmentKey) {
		if (content == null) {
			return null;
		}
		if (SwingUtilities.isEventDispatchThread()) {
			return openOrFocusNow(title, content, assignmentKey);
		}
		final ReportDocumentView[] holder = new ReportDocumentView[1];
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					holder[0] = openOrFocusNow(title, content, assignmentKey);
				}
			});
		}
		catch (Exception e) {
			LogUtils.warn("ReportDocumentService.openOrFocus failed: " + e.getMessage(), e);
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					openOrFocusNow(title, content, assignmentKey);
				}
			});
		}
		return holder[0];
	}

	private static ReportDocumentView openOrFocusNow(final String title, final Component content,
	        final String assignmentKey) {
		try {
			final UsageStatsReportService activity = UsageStatsReportService.get();
			if (activity != null) {
				activity.releaseSoftViewport();
			}
			final ReportDocumentView existing = findByAssignmentKey(assignmentKey);
			if (existing != null && !existing.isClosed()) {
				existing.setContent(title, content);
				DocumentTabSupport.openDocumentTab(existing);
				DocumentTabSupport.selectDocumentTab(existing);
				return existing;
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
			LogUtils.warn("ReportDocumentService.openOrFocus failed: " + e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Always create a new tab (rare). Prefer {@link #openOrFocus}.
	 */
	public static ReportDocumentView openNew(final String title, final Component content,
	        final String assignmentKey) {
		return openOrFocus(title, content, assignmentKey == null || assignmentKey.length() == 0
		        ? ("report://adhoc/" + System.nanoTime())
		        : assignmentKey);
	}

	public static void showInTab(final String title, final Component content) {
		openOrFocus(title, content, "report://adhoc/" + System.nanoTime());
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

	public static void closeContent(final Component content) {
		if (content == null) {
			return;
		}
		final ReportDocumentView view = findByContent(content);
		if (view != null) {
			close(view);
		}
	}

	public static void closeTab() {
		final IDocumentTabView active = DocumentTabSupport.getActiveDocumentView();
		if (active instanceof ReportDocumentView) {
			close((ReportDocumentView) active);
		}
	}

	/** Close whichever document tab is active (report / draw.io / …). */
	public static boolean closeActiveDocumentTab() {
		final IDocumentTabView active = DocumentTabSupport.getActiveDocumentView();
		if (active == null) {
			return false;
		}
		return active.requestClose(false);
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

	public static ReportDocumentView findByAssignmentKey(final String assignmentKey) {
		if (assignmentKey == null || assignmentKey.length() == 0) {
			return null;
		}
		synchronized (openViews) {
			for (final Iterator it = openViews.iterator(); it.hasNext();) {
				final ReportDocumentView view = (ReportDocumentView) it.next();
				if (view.isClosed()) {
					continue;
				}
				if (assignmentKey.equals(view.getTabAssignmentKey())) {
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
