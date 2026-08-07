package org.freeplane.view.swing.features.reports;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.usagestats.UsageStatsReportService;

/**
 * Opens each chart report in its own bottom tab. Previous reports stay open until
 * the user closes them. Loading progress is scoped per tab/session.
 */
public final class ReportViewportService implements IExtension {

	/** One open (or loading) report tab. */
	public static final class ReportLoadSession {
		public final int id;
		public final ReportViewportPanel panel;
		public final ReportDocumentView view;
		public final long startedAtMs;
		private volatile String lastMessage = "正在加载…";
		private volatile boolean finished;
		private volatile boolean failed;

		ReportLoadSession(final int id, final ReportViewportPanel panel, final ReportDocumentView view) {
			this.id = id;
			this.panel = panel;
			this.view = view;
			this.startedAtMs = System.currentTimeMillis();
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
	 * Open a <b>new</b> report tab immediately with a loading shell.
	 * Previous report tabs are left alone (they keep loading / showing independently).
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
		final String title = def == null || def.title == null || def.title.length() == 0 ? "报表"
		        : "报表 · " + def.title;
		final String decision = def == null || def.decision == null ? "" : def.decision;
		final String dataSource = def == null || def.dataSource == null ? "" : def.dataSource;
		final String sub = subtitle == null ? "" : subtitle;
		final String assignKey = "report://" + (def == null || def.id == null ? "unknown" : def.id) + "/" + id;
		final ReportViewportPanel panel = new ReportViewportPanel();
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
		panel.showLoading(title, sub, decision, dataSource);
		final ReportDocumentView view = ReportDocumentService.openNew(title + " · 加载中", panel, assignKey);
		viewRef[0] = view;
		final ReportLoadSession session = new ReportLoadSession(id, panel, view);
		sessionsById.put(Integer.valueOf(id), session);
		return session;
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
				final String withTime = elapsedSec > 0 ? base + " · 已用时 " + elapsedSec + "s" : base;
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
				session.panel.showModel(model, tree);
				final String title = model == null || model.title == null || model.title.length() == 0 ? "报表"
				        : model.title;
				session.view.setTabTitle(title);
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
		final String msg = message == null ? "生成失败" : message;
		final Runnable show = new Runnable() {
			public void run() {
				if (session.view == null || session.view.isClosed()) {
					session.markFailed();
					sessionsById.remove(Integer.valueOf(session.id));
					return;
				}
				session.panel.showError("报表生成失败", msg);
				session.view.setTabTitle("报表 · 失败");
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

	/** Legacy helper: open a finished model in a brand-new tab. */
	public void showReport(final ReportViewModel model, final ReportNodeSpec tree) {
		final UsageStatsReportService activity = UsageStatsReportService.get();
		if (activity != null) {
			activity.releaseSoftViewport();
		}
		final ReportViewportPanel panel = new ReportViewportPanel();
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
		final String title = model == null || model.title == null || model.title.length() == 0 ? "报表"
		        : model.title;
		viewRef[0] = ReportDocumentService.openNew(title, panel, "report://direct/" + System.nanoTime());
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
				JOptionPane.showMessageDialog(panel, "请先在导图中选中一个节点", "写入报表",
				        JOptionPane.WARNING_MESSAGE);
				return;
			}
			JOptionPane.showMessageDialog(panel, "已写入选中节点", "写入报表", JOptionPane.INFORMATION_MESSAGE);
		}
		catch (Exception e) {
			JOptionPane.showMessageDialog(panel, e.getMessage(), "写入报表", JOptionPane.ERROR_MESSAGE);
		}
	}
}
