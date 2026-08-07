package org.docear.plugin.mcp.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;

import javax.swing.JDialog;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;

/**
 * MCP audit UI. Prefer embedding in the mind-map document tab; dialog is fallback.
 */
public final class McpStatusAuditDialog extends JDialog {
	private static final long serialVersionUID = 1L;

	private McpStatusAuditDialog(final Frame owner, final McpStatusAuditPanel panel) {
		super(owner, TextUtils.getText("McpStatusAuditAction.text"), true);
		setLayout(new BorderLayout());
		add(panel, BorderLayout.CENTER);
		final Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		setSize(Math.min(1400, screen.width - 40), Math.min(900, screen.height - 60));
		setMinimumSize(new Dimension(980, 640));
		setLocationRelativeTo(owner);
		panel.setOnClose(new Runnable() {
			public void run() {
				dispose();
			}
		});
	}

	/** Open in the mind-map area as a bottom tab (preferred). */
	public static void showInMapTab() {
		final McpStatusAuditPanel panel = McpStatusAuditPanel.create();
		final String title = TextUtils.getText("McpStatusAuditAction.text", "MCP 审计");
		if (showViaReportDocumentService(title, panel)) {
			return;
		}
		final Frame frame = Controller.getCurrentController().getViewController().getFrame();
		final McpStatusAuditDialog dialog = new McpStatusAuditDialog(frame, panel);
		dialog.setVisible(true);
	}

	private static boolean showViaReportDocumentService(final String title, final McpStatusAuditPanel panel) {
		try {
			final Class cls = Class.forName("org.freeplane.view.swing.features.reports.ReportDocumentService");
			panel.setOnClose(new Runnable() {
				public void run() {
					try {
						cls.getMethod("closeContent", Component.class).invoke(null, panel);
					}
					catch (Exception e) {
						try {
							cls.getMethod("closeTab").invoke(null);
						}
						catch (Exception e2) {
							LogUtils.warn("MCP audit close failed: " + e2.getMessage());
						}
					}
				}
			});
			// Prefer openNew so each open gets its own tab (keeps previous reports).
			try {
				final String key = "report://mcp-audit/" + System.nanoTime();
				cls.getMethod("openNew", String.class, Component.class, String.class).invoke(null, title, panel, key);
			}
			catch (NoSuchMethodException missing) {
				cls.getMethod("showInTab", String.class, Component.class).invoke(null, title, panel);
			}
			return true;
		}
		catch (Throwable t) {
			LogUtils.warn("ReportDocumentService unavailable, falling back to dialog: " + t.getMessage());
			return false;
		}
	}

	/** Opens tab when possible; otherwise modal dialog. */
	public static void show(final Frame owner) {
		showInMapTab();
	}
}
