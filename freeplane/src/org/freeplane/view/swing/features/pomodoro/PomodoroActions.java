package org.freeplane.view.swing.features.pomodoro;

import java.awt.event.ActionEvent;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

final class StartPomodoroAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;

	StartPomodoroAction() {
		super("StartPomodoroAction");
	}

	public void actionPerformed(final ActionEvent e) {
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		final NodeModel node = Controller.getCurrentController().getSelection().getSelected();
		if (manager != null && node != null) {
			manager.start(node);
		}
	}
}

final class PausePomodoroAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;

	PausePomodoroAction() {
		super("PausePomodoroAction");
	}

	public void actionPerformed(final ActionEvent e) {
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		final NodeModel node = Controller.getCurrentController().getSelection().getSelected();
		if (manager != null && node != null) {
			manager.pause(node);
		}
	}
}

final class StopPomodoroAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;

	StopPomodoroAction() {
		super("StopPomodoroAction");
	}

	public void actionPerformed(final ActionEvent e) {
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		final NodeModel node = Controller.getCurrentController().getSelection().getSelected();
		if (manager != null && node != null) {
			manager.stop(node);
		}
	}
}

final class TogglePomodoroAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;

	TogglePomodoroAction() {
		super("TogglePomodoroAction");
	}

	public void actionPerformed(final ActionEvent e) {
		final NodeModel node = Controller.getCurrentController().getSelection().getSelected();
		if (node != null) {
			PomodoroAttributes.toggleEnabled(node);
		}
	}
}

final class ShowPomodoroWindowAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;

	ShowPomodoroWindowAction() {
		super("ShowPomodoroWindowAction");
	}

	public void actionPerformed(final ActionEvent e) {
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		if (manager != null) {
			manager.showWindow();
		}
	}
}

final class ShowPomodoroHistoryAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;

	ShowPomodoroHistoryAction() {
		super("ShowPomodoroHistoryAction");
	}

	public void actionPerformed(final ActionEvent e) {
		final NodeModel node = Controller.getCurrentController().getSelection().getSelected();
		if (node != null) {
			PomodoroHistoryDialog.showForNode(node);
		}
	}
}

final class ExportPomodoroStatsAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;

	ExportPomodoroStatsAction() {
		super("ExportPomodoroStatsAction");
	}

	public void actionPerformed(final ActionEvent e) {
		PomodoroExport.exportInteractive(true);
	}
}

final class SyncPomodoroNoteAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;

	SyncPomodoroNoteAction() {
		super("SyncPomodoroNoteAction");
	}

	public void actionPerformed(final ActionEvent e) {
		final NodeModel node = Controller.getCurrentController().getSelection().getSelected();
		if (node == null) {
			return;
		}
		final PomodoroExtension ext = PomodoroAttributes.read(node);
		if (ext != null) {
			PomodoroNoteSync.sync(node, ext);
		}
	}
}

final class CyclePomodoroSkinAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;

	CyclePomodoroSkinAction() {
		super("CyclePomodoroSkinAction");
	}

	public void actionPerformed(final ActionEvent e) {
		final String next = PomodoroTheme.nextSkin(PomodoroTheme.current().name);
		PomodoroTheme.setSkin(next);
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		if (manager != null) {
			manager.showWindow();
			manager.refreshUi();
		}
	}
}
