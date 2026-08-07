package org.docear.plugin.mcp.ui;

import java.awt.event.ActionEvent;

import org.freeplane.core.ui.AFreeplaneAction;

public class McpStatusAuditAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;
	public static final String KEY = "McpStatusAuditAction";

	public McpStatusAuditAction() {
		super(KEY);
	}

	public void actionPerformed(final ActionEvent e) {
		McpStatusAuditDialog.showInMapTab();
	}
}
