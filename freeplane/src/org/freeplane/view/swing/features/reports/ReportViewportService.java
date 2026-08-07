package org.freeplane.view.swing.features.reports;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.usagestats.UsageStatsReportService;
import org.freeplane.main.application.DocumentTabSupport;

/**
 * Chart reports as bottom tabs. Same report id reuses its tab (focus + reload)
 * instead of stacking duplicates. Tab titles stay short like the sidebar.
 */
public final class ReportViewportService implements IExtension {

	/** One open (or loading) report tab. */
	public static final class ReportLoadSession {
		public final int id;
		public final String shortTitle;
		public final ReportViewportPanel panel;
		public final ReportDocumentView view;
		public final long startedAtMs;
		private volatile String lastMessage;
		private volatile boolean finished;
		private volatile boolean failed;

		ReportLoadSession(final int id, final String shortTitle, final ReportViewportPanel panel,
		        final ReportDocumentView view) {
			this.id = id;
			this.shortTitle = shortTitle == null || shortTitle.length() == 0
			        ? TextUtils.getText("ReportViewport.title.default") : shortTitle;
			this.panel = panel;
			this.view = view;
			this.startedAtMs = System.currentTimeMillis();
			this.lastMessage = TextUtils.getText("ReportViewport.loading");
		}

		public boolean isAlive() {
			return view != null && !view.isClosed() && !finished;
		}

		public boolean isFinished() {
			return finished || (view != null && view.isClosed());
		}

		public String getLastMessage() {
			return lastMessage;
		}

		void setLastMessage(final String message) {
			if (message != null && message.length() > 0) {
				lastMessage = message;
			}
		}

		void markFinished() {
			finished = true;
		}

		void markFailed() {
			failed = true;
			finished = true;
		}

		public boolean isFailed() {
			return failed;
		}
	}

	private final Map sessionsById = Collections.synchronizedMap(new HashMap());
	private int nextSessionId;

	public static ReportViewportService get() {
		final Controller controller = Controller.getCurrentController();
		if (controller == null || controller.getModeController() == null) {
			return null;
		}
		ReportViewportService service = (ReportViewportService) controller.getModeController()
		        .getExtension(ReportViewportService.class);
		if (service == null && controller.getModeController() instanceof MModeController) {
			service = install((MModeController) controller.getModeController());
		}
		return service;
	}

	public static ReportViewportService install(final MModeController modeController) {
		ReportViewportService service = (ReportViewportService) modeController.getExtension(ReportViewportService.class);
		if (service != null) {
			return service;
		}
		service = new ReportViewportService();
		modeController.addExtension(ReportViewportService.class, service);
		return service;
	}

	/**
	 * Focus existing tab for this report id, or open one. Always shows loading then fills data.
	 */
	public ReportLoadSession beginReport(final ReportDefinition def, final String subtitle) {
		final UsageStatsReportService activity = UsageStatsReportService.get();
		if (activity != null) {
			activity.releaseSoftViewport();
		}
		final int id;
		synchronized (this) {
			id = ++nextSessionId;
		}
		final String shortTitle = def == null || def.title == null || def.title.length() == 0
		        ? TextUtils.getText("ReportViewport.title.default") : def.title;
		final String decision = def == null || def.decision == null ? "" : def.decision;
		final String dataSource = def == null || def.dataSource == null ? "" : def.dataSource;
		final String sub = subtitle == null ? "" : subtitle;
		final String assignKey = ReportDocumentService.keyForReportId(def == null ? null : def.id);

		final ReportDocumentView existing = ReportDocumentService.findByAssignmentKey(assignKey);
		final ReportViewportPanel panel;
		final ReportDocumentView view;
		if (existing != null && !existing.isClosed() && existing.getContent() instanceof ReportViewportPanel) {
			view = existing;
			panel = (ReportViewportPanel) existing.getContent();
			panel.showLoading(shortTitle, sub, decision, dataSource);
			view.setTabTitle(shortTitle);
			DocumentTabSupport.openDocumentTab(view);
			DocumentTabSupport.selectDocumentTab(view);
		}
		else {
			panel = new ReportViewportPanel();
			final ReportDocumentView[] viewRef = new ReportDocumentView[1];
			panel.setOnClose(new Runnable() {
				public void run() {
					final ReportDocumentView v = viewRef[0];
					if (v != null) {
						ReportDocumentService.close(v);
					}
				}
			});
			panel.setOnWrite(new Runnable() {
				public void run() {
					writeFromPanel(panel);
				}
			});
			panel.showLoading(shortTitle, sub, decision, dataSource);
			view = ReportDocumentService.openOrFocus(shortTitle, panel, assignKey);
			viewRef[0] = view;
		}

		// Invalidate any in-flight load for a previous click on the same tab.
		invalidateSessionsForView(view);
		final ReportLoadSession session = new ReportLoadSession(id, shortTitle, panel, view);
		sessionsById.put(Integer.valueOf(id), session);
		return session;
	}

	private void invalidateSessionsForView(final ReportDocumentView view) {
		if (view == null) {
			return;
		}
		synchronized (sessionsById) {
			final Object[] values = sessionsById.values().toArray();
			for (int i = 0; i < values.length; i++) {
				final ReportLoadSession s = (ReportLoadSession) values[i];
				if (s != null && s.view == view) {
					s.markFinished();
					sessionsById.remove(Integer.valueOf(s.id));
				}
			}
		}
	}

	public ReportLoadSession getSession(final int id) {
		return (ReportLoadSession) sessionsById.get(Integer.valueOf(id));
	}

	public void updateProgress(final ReportLoadSession session, final String message, final int percent) {
		if (session == null || session.isFinished()) {
			return;
		}
		session.setLastMessage(message);
		final Runnable update = new Runnable() {
			public void run() {
				if (session.isFinished()) {
					return;
				}
				final long elapsedSec = Math.max(0L, (System.currentTimeMillis() - session.startedAtMs) / 1000L);
				final String base = message == null || message.length() == 0 ? session.getLastMessage() : message;
				final String withTime = elapsedSec > 0
				        ? TextUtils.format("ReportViewport.elapsed", base, Long.valueOf(elapsedSec)) : base;
				session.panel.setLoadProgress(withTime, percent);
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			update.run();
		}
		else {
			SwingUtilities.invokeLater(update);
		}
	}

	public void showReport(final ReportLoadSession session, final ReportViewModel model,
	        final ReportNodeSpec tree) {
		if (session == null) {
			return;
		}
		final Runnable show = new Runnable() {
			public void run() {
				if (session.view == null || session.view.isClosed()) {
					session.markFinished();
					sessionsById.remove(Integer.valueOf(session.id));
					return;
				}
				if (session.isFinished()) {
					// Superseded by a newer click on the same report tab.
					return;
				}
				session.panel.showModel(model, tree);
				session.view.setTabTitle(session.shortTitle);
				session.markFinished();
				sessionsById.remove(Integer.valueOf(session.id));
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			show.run();
		}
		else {
			SwingUtilities.invokeLater(show);
		}
	}

	public void showError(final ReportLoadSession session, final String message) {
		if (session == null) {
			return;
		}
		final String msg = message == null ? TextUtils.getText("ReportViewport.error.generateFailed") : message;
		final Runnable show = new Runnable() {
			public void run() {
				if (session.view == null || session.view.isClosed()) {
					session.markFailed();
					sessionsById.remove(Integer.valueOf(session.id));
					return;
				}
				if (session.isFinished() && !session.isFailed()) {
					return;
				}
				session.panel.showError(TextUtils.getText("ReportViewport.error.title"), msg);
				session.view.setTabTitle(session.shortTitle);
				session.markFailed();
				sessionsById.remove(Integer.valueOf(session.id));
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			show.run();
		}
		else {
			SwingUtilities.invokeLater(show);
		}
	}

	/** Legacy helper: open a finished model in a tab keyed by title. */
	public void showReport(final ReportViewModel model, final ReportNodeSpec tree) {
		final UsageStatsReportService activity = UsageStatsReportService.get();
		if (activity != null) {
			activity.releaseSoftViewport();
		}
		final String shortTitle = model == null || model.title == null || model.title.length() == 0
		        ? TextUtils.getText("ReportViewport.title.default") : stripReportPrefix(model.title);
		final String assignKey = ReportDocumentService.keyForReportId(shortTitle);
		final ReportDocumentView existing = ReportDocumentService.findByAssignmentKey(assignKey);
		final ReportViewportPanel panel;
		if (existing != null && !existing.isClosed() && existing.getContent() instanceof ReportViewportPanel) {
			panel = (ReportViewportPanel) existing.getContent();
			panel.showModel(model, tree);
			existing.setTabTitle(shortTitle);
			DocumentTabSupport.selectDocumentTab(existing);
			return;
		}
		panel = new ReportViewportPanel();
		final ReportDocumentView[] viewRef = new ReportDocumentView[1];
		panel.setOnClose(new Runnable() {
			public void run() {
				if (viewRef[0] != null) {
					ReportDocumentService.close(viewRef[0]);
				}
			}
		});
		panel.setOnWrite(new Runnable() {
			public void run() {
				writeFromPanel(panel);
			}
		});
		panel.showModel(model, tree);
		viewRef[0] = ReportDocumentService.openOrFocus(shortTitle, panel, assignKey);
	}

	private static String stripReportPrefix(final String title) {
		final String prefix = TextUtils.getText("ReportViewport.title.prefix");
		if (title.startsWith(prefix)) {
			return title.substring(prefix.length());
		}
		// Legacy Chinese prefix from older report titles.
		if (title.startsWith("报表 · ")) {
			return title.substring("报表 · ".length());
		}
		return title;
	}

	public boolean isReportInViewport() {
		return ReportDocumentService.isOpen();
	}

	public void hideFromMapViewport() {
		ReportDocumentService.closeTab();
	}

	public void releaseSoftViewport() {
		// Intentionally empty: reports live in independent ReportDocumentView tabs.
	}

	private void writeFromPanel(final ReportViewportPanel panel) {
		if (panel == null) {
			return;
		}
		final ReportNodeSpec tree = panel.getCurrentTree();
		if (tree == null) {
			return;
		}
		try {
			final NodeModel written = ReportMindMapWriter.writeUnderSelection(tree);
			if (written == null) {
				JOptionPane.showMessageDialog(panel, TextUtils.getText("ReportViewport.write.needSelection"),
				        TextUtils.getText("ReportViewport.write.dialogTitle"), JOptionPane.WARNING_MESSAGE);
				return;
			}
			JOptionPane.showMessageDialog(panel, TextUtils.getText("ReportViewport.write.done"),
			        TextUtils.getText("ReportViewport.write.dialogTitle"), JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception e) {
			JOptionPane.showMessageDialog(panel, e.getMessage(), TextUtils.getText("ReportViewport.write.dialogTitle"),
			        JOptionPane.ERROR_MESSAGE);
		}
	}
}
