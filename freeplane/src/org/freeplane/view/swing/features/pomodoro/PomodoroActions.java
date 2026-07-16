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
