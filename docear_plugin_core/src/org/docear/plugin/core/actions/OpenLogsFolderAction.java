package org.docear.plugin.core.actions;

import java.awt.event.ActionEvent;

import org.freeplane.core.ui.AFreeplaneAction;

public class OpenLogsFolderAction extends AFreeplaneAction {

	private static final long serialVersionUID = 1L;
	private final static String KEY = "OpenLogsFolderAction";

	public OpenLogsFolderAction() {
		super(KEY);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		org.freeplane.features.help.SystemLogViewer.show();
	}

}
