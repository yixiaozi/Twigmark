package org.docear.plugin.core.quickcommand;

import java.awt.event.ActionEvent;

import org.freeplane.core.ui.AFreeplaneAction;

public class QuickCommandAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;
	public static final String KEY = "QuickCommandAction";

	public QuickCommandAction() {
		super(KEY);
	}

	public void actionPerformed(final ActionEvent e) {
		QuickCommandService.showDialog();
	}
}
