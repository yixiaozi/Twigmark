package org.docear.plugin.core.mapswitcher;

import java.awt.event.ActionEvent;

import org.freeplane.core.ui.AFreeplaneAction;

public class MapSwitcherAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;
	public static final String KEY = "MapSwitcherAction";

	public MapSwitcherAction() {
		super(KEY);
	}

	public void actionPerformed(final ActionEvent e) {
		MapSwitcherService.showDialog();
	}
}
