package org.docear.plugin.core.settings;

import java.awt.event.ActionEvent;

import org.freeplane.core.ui.AFreeplaneAction;

public class ProductSettingsAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;
	public static final String KEY = "ProductSettingsAction";

	public ProductSettingsAction() {
		super(KEY);
	}

	public void actionPerformed(final ActionEvent e) {
		ProductSettingsDialog.showDialog(false);
	}
}
