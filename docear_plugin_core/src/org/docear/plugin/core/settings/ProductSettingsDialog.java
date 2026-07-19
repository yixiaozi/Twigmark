package org.docear.plugin.core.settings;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.view.swing.features.finance.FinanceLedgerService;
import org.freeplane.view.swing.features.git.GitConfig;

/**
 * Product settings hub: working directory + MCP / Git / Finance / QuickCapture.
 */
public final class ProductSettingsDialog extends JDialog {
	private static final long serialVersionUID = 1L;

	private static final String PROP_INBOX_DIRECTORY = "quickcapture.inbox_directory";
	private static final String PROP_INBOX_FILENAME = "quickcapture.inbox_filename";

	private final JTextField workingDirField = new JTextField(36);
	private final JLabel configDirLabel = DocearUiTheme.mutedLabel(" ");
	private final JCheckBox mcpEnabled = new JCheckBox();
	private final JTextField mcpHostField = new JTextField(16);
	private final JSpinner mcpPortSpinner = new JSpinner(new SpinnerNumberModel(7720, 1, 65535, 1));
	private final JCheckBox mcpReadOnly = new JCheckBox();
	private final JCheckBox mcpCursorSync = new JCheckBox();
	private final JCheckBox mcpAuditEnabled = new JCheckBox();
	private final JLabel mcpStatusBadge = DocearUiTheme.titleLabel(" ");
	private final JLabel mcpStatusLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel mcpAuditLabel = DocearUiTheme.mutedLabel(" ");
	private final JTextField gitRepoField = new JTextField(36);
	private final JTextField financeMapField = new JTextField(36);
	private final JTextField inboxDirField = new JTextField(36);
	private final JTextField inboxFileField = new JTextField(16);
	private boolean saved;

	private ProductSettingsDialog(final Frame owner, final boolean firstRun) {
		super(owner, firstRun ? TextUtils.getText("ProductSettingsAction.first_run_title")
		        : TextUtils.getText("ProductSettingsAction.text"), true);
		setLayout(new BorderLayout(0, 0));
		getContentPane().setBackground(DocearUiTheme.CANVAS);
		final JPanel root = new JPanel(new BorderLayout(0, 0));
		DocearUiTheme.styleCanvas(root);
		root.setBorder(new EmptyBorder(12, 14, 10, 14));

		final JLabel intro = DocearUiTheme.mutedLabel(firstRun
		        ? TextUtils.getText("ProductSettingsAction.first_run_hint")
		        : TextUtils.getText("ProductSettingsAction.hint"));
		intro.setBorder(new EmptyBorder(0, 0, 10, 0));
		root.add(intro, BorderLayout.NORTH);

		final JTabbedPane tabs = new JTabbedPane();
		tabs.setFont(DocearUiTheme.font(12f, java.awt.Font.BOLD));
		tabs.addTab(TextUtils.getText("ProductSettingsAction.tab.working_dir"), buildWorkingDirTab());
		tabs.addTab(TextUtils.getText("ProductSettingsAction.tab.mcp"), buildMcpTab());
		tabs.addTab(TextUtils.getText("ProductSettingsAction.tab.git"), buildGitTab());
		tabs.addTab(TextUtils.getText("ProductSettingsAction.tab.finance"), buildFinanceTab());
		tabs.addTab(TextUtils.getText("ProductSettingsAction.tab.quickcapture"), buildQuickCaptureTab());
		root.add(tabs, BorderLayout.CENTER);

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setOpaque(false);
		buttons.setBorder(new EmptyBorder(10, 0, 0, 0));
		final JButton cancel = DocearUiTheme.softButton(TextUtils.getText("cancel"));
		final JButton save = DocearUiTheme.primaryButton(TextUtils.getText("ok"));
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saved = false;
				dispose();
			}
		});
		save.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (applyChanges(firstRun)) {
					saved = true;
					dispose();
				}
			}
		});
		buttons.add(cancel);
		buttons.add(save);
		root.add(buttons, BorderLayout.SOUTH);

		add(root);
		loadValues();
		pack();
		setMinimumSize(getPreferredSize());
		setLocationRelativeTo(owner);
	}

	public static boolean showDialog(final boolean firstRun) {
		final Frame frame = Controller.getCurrentController().getViewController().getFrame();
		final ProductSettingsDialog dialog = new ProductSettingsDialog(frame, firstRun);
		dialog.setVisible(true);
		return dialog.saved;
	}

	private JPanel buildWorkingDirTab() {
		final JPanel panel = formPanel();
		final GridBagConstraints c = baseConstraints();
		addLabel(panel, c, 0, TextUtils.getText("ProductSettingsAction.working_dir"));
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(workingDirField, c);
		c.gridx = 2;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		panel.add(browseButton(workingDirField, true), c);

		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 3;
		c.fill = GridBagConstraints.HORIZONTAL;
		configDirLabel.setText(" ");
		panel.add(configDirLabel, c);

		c.gridy = 2;
		final JLabel note = DocearUiTheme.mutedLabel(TextUtils.getText("ProductSettingsAction.working_dir_note"));
		panel.add(note, c);
		workingDirField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) {
				refreshConfigHint();
			}

			public void removeUpdate(javax.swing.event.DocumentEvent e) {
				refreshConfigHint();
			}

			public void changedUpdate(javax.swing.event.DocumentEvent e) {
				refreshConfigHint();
			}
		});
		return panel;
	}

	private JPanel buildMcpTab() {
		final JPanel panel = formPanel();
		final GridBagConstraints c = baseConstraints();
		mcpEnabled.setText(TextUtils.getText("ProductSettingsAction.mcp.enabled"));
		mcpReadOnly.setText(TextUtils.getText("ProductSettingsAction.mcp.readonly"));
		mcpCursorSync.setText(TextUtils.getText("ProductSettingsAction.mcp.cursor_sync"));
		mcpAuditEnabled.setText(TextUtils.getText("ProductSettingsAction.mcp.audit_enabled"));
		addCheck(panel, c, 0, mcpEnabled);
		addLabel(panel, c, 1, TextUtils.getText("ProductSettingsAction.mcp.host"));
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(mcpHostField, c);
		addLabel(panel, c, 2, TextUtils.getText("ProductSettingsAction.mcp.port"));
		c.gridx = 1;
		panel.add(mcpPortSpinner, c);
		addCheck(panel, c, 3, mcpReadOnly);
		addCheck(panel, c, 4, mcpCursorSync);
		addCheck(panel, c, 5, mcpAuditEnabled);

		c.gridx = 0;
		c.gridy = 6;
		c.gridwidth = 2;
		c.fill = GridBagConstraints.HORIZONTAL;
		final JPanel statusCard = new JPanel(new BorderLayout(6, 4));
		DocearUiTheme.styleSurface(statusCard);
		statusCard.setBorder(BorderFactory.createCompoundBorder(DocearUiTheme.hairlineBorder(),
		        new EmptyBorder(8, 10, 8, 10)));
		mcpStatusBadge.setFont(DocearUiTheme.font(14f, java.awt.Font.BOLD));
		final JPanel statusText = new JPanel(new GridBagLayout());
		statusText.setOpaque(false);
		final GridBagConstraints sc = new GridBagConstraints();
		sc.gridx = 0;
		sc.gridy = 0;
		sc.anchor = GridBagConstraints.WEST;
		sc.insets = new Insets(0, 0, 2, 0);
		statusText.add(mcpStatusBadge, sc);
		sc.gridy = 1;
		statusText.add(mcpStatusLabel, sc);
		sc.gridy = 2;
		statusText.add(mcpAuditLabel, sc);
		statusCard.add(statusText, BorderLayout.CENTER);

		final JPanel statusActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		statusActions.setOpaque(false);
		final JButton refreshStatus = DocearUiTheme.softButton(TextUtils.getText("ProductSettingsAction.mcp.refresh"));
		final JButton restart = DocearUiTheme.softButton(TextUtils.getText("ProductSettingsAction.mcp.restart"));
		final JButton openAudit = DocearUiTheme.primaryButton(TextUtils.getText("ProductSettingsAction.mcp.open_audit"));
		refreshStatus.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshMcpStatus();
			}
		});
		restart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				applyMcpRuntimeProperties();
				final McpRuntimeFacade.Backend backend = McpRuntimeFacade.get();
				if (backend != null) {
					backend.restartServer();
				}
				refreshMcpStatus();
			}
		});
		openAudit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final McpRuntimeFacade.Backend backend = McpRuntimeFacade.get();
				if (backend != null) {
					backend.openStatusAuditDialog(Controller.getCurrentController().getViewController().getFrame());
				}
				else {
					JOptionPane.showMessageDialog(ProductSettingsDialog.this,
					        TextUtils.getText("ProductSettingsAction.mcp.unavailable"), getTitle(),
					        JOptionPane.INFORMATION_MESSAGE);
				}
			}
		});
		statusActions.add(refreshStatus);
		statusActions.add(restart);
		statusActions.add(openAudit);
		statusCard.add(statusActions, BorderLayout.SOUTH);
		panel.add(statusCard, c);
		return panel;
	}

	private JPanel buildGitTab() {
		final JPanel panel = formPanel();
		final GridBagConstraints c = baseConstraints();
		addLabel(panel, c, 0, TextUtils.getText("ProductSettingsAction.git.repo"));
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(gitRepoField, c);
		c.gridx = 2;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		panel.add(browseButton(gitRepoField, true), c);
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 3;
		panel.add(DocearUiTheme.mutedLabel(TextUtils.getText("ProductSettingsAction.git.hint")), c);
		return panel;
	}

	private JPanel buildFinanceTab() {
		final JPanel panel = formPanel();
		final GridBagConstraints c = baseConstraints();
		addLabel(panel, c, 0, TextUtils.getText("ProductSettingsAction.finance.map"));
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(financeMapField, c);
		c.gridx = 2;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		panel.add(browseButton(financeMapField, false), c);
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 3;
		panel.add(DocearUiTheme.mutedLabel(TextUtils.getText("ProductSettingsAction.finance.hint")), c);
		return panel;
	}

	private JPanel buildQuickCaptureTab() {
		final JPanel panel = formPanel();
		final GridBagConstraints c = baseConstraints();
		addLabel(panel, c, 0, TextUtils.getText("ProductSettingsAction.quickcapture.dir"));
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(inboxDirField, c);
		c.gridx = 2;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		panel.add(browseButton(inboxDirField, true), c);
		addLabel(panel, c, 1, TextUtils.getText("ProductSettingsAction.quickcapture.file"));
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(inboxFileField, c);
		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 3;
		panel.add(DocearUiTheme.mutedLabel(TextUtils.getText("ProductSettingsAction.quickcapture.hint")), c);
		return panel;
	}

	private void loadValues() {
		workingDirField.setText(MindMapDataRootResolver.getWorkingDirectory().getAbsolutePath());
		refreshConfigHint();
		final ResourceController rc = ResourceController.getResourceController();
		mcpEnabled.setSelected(isTrue(rc.getProperty("mcp.enabled", "true")));
		mcpHostField.setText(rc.getProperty("mcp.host", "127.0.0.1"));
		try {
			mcpPortSpinner.setValue(Integer.valueOf(Integer.parseInt(rc.getProperty("mcp.port", "7720"))));
		}
		catch (NumberFormatException e) {
			mcpPortSpinner.setValue(Integer.valueOf(7720));
		}
		mcpReadOnly.setSelected(isTrue(rc.getProperty("mcp.readonly", "false")));
		mcpCursorSync.setSelected(isTrue(rc.getProperty("mcp.cursorPlugin.sync.enabled", "true")));
		mcpAuditEnabled.setSelected(isTrue(rc.getProperty("mcp.audit.enabled", "true")));
		refreshMcpStatus();
		gitRepoField.setText(GitConfig.getConfiguredRepositoryPathRaw());
		financeMapField.setText(rc.getProperty(FinanceLedgerService.PROP_MAP_PATH, FinanceLedgerService.DEFAULT_FILENAME));
		inboxDirField.setText(rc.getProperty(PROP_INBOX_DIRECTORY, ""));
		inboxFileField.setText(rc.getProperty(PROP_INBOX_FILENAME, "\u6536\u4ef6\u7bb1.mm"));
	}

	private void refreshConfigHint() {
		final String path = workingDirField.getText() == null ? "" : workingDirField.getText().trim();
		if (path.length() == 0) {
			configDirLabel.setText(" ");
			return;
		}
		final File data = new File(new File(path), MindMapDataRootResolver.CONFIG_DIR_NAME);
		configDirLabel.setText(TextUtils.format("ProductSettingsAction.config_dir", data.getAbsolutePath()));
	}

	private boolean applyChanges(final boolean firstRun) {
		final String working = workingDirField.getText() == null ? "" : workingDirField.getText().trim();
		if (working.length() == 0) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("ProductSettingsAction.working_dir_required"),
			        getTitle(), JOptionPane.WARNING_MESSAGE);
			return false;
		}
		final File workingDir = new File(working);
		final File previous = MindMapDataRootResolver.getWorkingDirectory();
		final boolean workingChanged = !samePath(previous, workingDir);
		try {
			MindMapDataRootResolver.setWorkingDirectory(workingDir);
			if (MindMapDataRootResolver.isEffectivelyEmptyWorkingDirectory(workingDir)) {
				org.docear.plugin.core.workspace.WorkingDirectoryDefaults.seedInto(workingDir);
			}
		}
		catch (Exception e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
			return false;
		}

		final ResourceController rc = ResourceController.getResourceController();
		rc.setProperty("mcp.enabled", mcpEnabled.isSelected() ? "true" : "false");
		final String host = mcpHostField.getText() == null || mcpHostField.getText().trim().length() == 0
		        ? "127.0.0.1" : mcpHostField.getText().trim();
		rc.setProperty("mcp.host", host);
		rc.setProperty("mcp.port", String.valueOf(((Number) mcpPortSpinner.getValue()).intValue()));
		rc.setProperty("mcp.readonly", mcpReadOnly.isSelected() ? "true" : "false");
		rc.setProperty("mcp.cursorPlugin.sync.enabled", mcpCursorSync.isSelected() ? "true" : "false");
		rc.setProperty("mcp.audit.enabled", mcpAuditEnabled.isSelected() ? "true" : "false");
		final McpRuntimeFacade.Backend backend = McpRuntimeFacade.get();
		if (backend != null) {
			backend.restartServer();
		}

		GitConfig.setRepositoryPath(gitRepoField.getText());

		rc.setProperty(FinanceLedgerService.PROP_MAP_PATH,
		        financeMapField.getText() == null ? "" : financeMapField.getText().trim());
		rc.setProperty(PROP_INBOX_DIRECTORY, inboxDirField.getText() == null ? "" : inboxDirField.getText().trim());
		rc.setProperty(PROP_INBOX_FILENAME,
		        inboxFileField.getText() == null || inboxFileField.getText().trim().length() == 0
		                ? "\u6536\u4ef6\u7bb1.mm" : inboxFileField.getText().trim());

		MindMapDataRootResolver.markSetupCompleted();

		if (workingChanged && !firstRun) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("ProductSettingsAction.restart_hint"), getTitle(),
			        JOptionPane.INFORMATION_MESSAGE);
		}
		return true;
	}

	private static boolean samePath(final File a, final File b) {
		if (a == null || b == null) {
			return false;
		}
		try {
			return a.getCanonicalFile().equals(b.getCanonicalFile());
		}
		catch (Exception e) {
			return a.getAbsoluteFile().equals(b.getAbsoluteFile());
		}
	}

	private JButton browseButton(final JTextField field, final boolean directoriesOnly) {
		final JButton button = DocearUiTheme.softButton(TextUtils.getText("browse"));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				final JFileChooser chooser = new JFileChooser();
				chooser.setFileSelectionMode(directoriesOnly ? JFileChooser.DIRECTORIES_ONLY
				        : JFileChooser.FILES_ONLY);
				final String current = field.getText();
				if (current != null && current.trim().length() > 0) {
					final File start = new File(current.trim());
					chooser.setCurrentDirectory(start.isDirectory() ? start : start.getParentFile());
					if (start.exists()) {
						chooser.setSelectedFile(start);
					}
				}
				else {
					chooser.setCurrentDirectory(MindMapDataRootResolver.getWorkingDirectory());
				}
				if (chooser.showOpenDialog(ProductSettingsDialog.this) == JFileChooser.APPROVE_OPTION) {
					field.setText(chooser.getSelectedFile().getAbsolutePath());
				}
			}
		});
		return button;
	}

	private static JPanel formPanel() {
		final JPanel panel = new JPanel(new GridBagLayout());
		DocearUiTheme.styleSurface(panel);
		panel.setBorder(BorderFactory.createCompoundBorder(DocearUiTheme.hairlineBorder(),
		        new EmptyBorder(12, 12, 12, 12)));
		return panel;
	}

	private static GridBagConstraints baseConstraints() {
		final GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(4, 4, 4, 4);
		return c;
	}

	private static void addLabel(final JPanel panel, final GridBagConstraints c, final int row, final String text) {
		c.gridx = 0;
		c.gridy = row;
		c.gridwidth = 1;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		final JLabel label = new JLabel(text);
		label.setFont(DocearUiTheme.font(12f));
		label.setForeground(DocearUiTheme.TEXT);
		panel.add(label, c);
	}

	private static void addCheck(final JPanel panel, final GridBagConstraints c, final int row, final JCheckBox box) {
		c.gridx = 0;
		c.gridy = row;
		c.gridwidth = 2;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		box.setOpaque(false);
		box.setFont(DocearUiTheme.font(12f));
		box.setForeground(DocearUiTheme.TEXT);
		panel.add(box, c);
	}

	private void applyMcpRuntimeProperties() {
		final ResourceController rc = ResourceController.getResourceController();
		rc.setProperty("mcp.enabled", mcpEnabled.isSelected() ? "true" : "false");
		final String host = mcpHostField.getText() == null || mcpHostField.getText().trim().length() == 0
		        ? "127.0.0.1" : mcpHostField.getText().trim();
		rc.setProperty("mcp.host", host);
		rc.setProperty("mcp.port", String.valueOf(((Number) mcpPortSpinner.getValue()).intValue()));
		rc.setProperty("mcp.readonly", mcpReadOnly.isSelected() ? "true" : "false");
		rc.setProperty("mcp.cursorPlugin.sync.enabled", mcpCursorSync.isSelected() ? "true" : "false");
		rc.setProperty("mcp.audit.enabled", mcpAuditEnabled.isSelected() ? "true" : "false");
	}

	private void refreshMcpStatus() {
		final ResourceController rc = ResourceController.getResourceController();
		final String host = mcpHostField.getText() == null || mcpHostField.getText().trim().length() == 0
		        ? rc.getProperty("mcp.host", "127.0.0.1") : mcpHostField.getText().trim();
		final String port = String.valueOf(((Number) mcpPortSpinner.getValue()).intValue());
		mcpStatusLabel.setText(TextUtils.format("ProductSettingsAction.mcp.status", host, port));

		final McpRuntimeFacade.Backend backend = McpRuntimeFacade.get();
		if (backend == null) {
			mcpStatusBadge.setText(TextUtils.getText("ProductSettingsAction.mcp.state.unavailable"));
			mcpStatusBadge.setForeground(DocearUiTheme.TEXT_MUTED);
			mcpAuditLabel.setText(TextUtils.getText("ProductSettingsAction.mcp.unavailable"));
			return;
		}
		final boolean running = backend.isServerRunning();
		final boolean healthy = backend.probeHealth();
		if (!mcpEnabled.isSelected()) {
			mcpStatusBadge.setText(TextUtils.getText("ProductSettingsAction.mcp.state.disabled"));
			mcpStatusBadge.setForeground(DocearUiTheme.TEXT_MUTED);
		}
		else if (running && healthy) {
			mcpStatusBadge.setText(TextUtils.getText("ProductSettingsAction.mcp.state.running"));
			mcpStatusBadge.setForeground(DocearUiTheme.SUCCESS);
		}
		else if (running) {
			mcpStatusBadge.setText(TextUtils.getText("ProductSettingsAction.mcp.state.listening"));
			mcpStatusBadge.setForeground(DocearUiTheme.WARNING);
		}
		else {
			mcpStatusBadge.setText(TextUtils.getText("ProductSettingsAction.mcp.state.stopped"));
			mcpStatusBadge.setForeground(DocearUiTheme.DANGER);
		}
		final String err = backend.getLastError();
		if (err != null && err.length() > 0) {
			mcpStatusLabel.setText(mcpStatusLabel.getText() + "  ·  " + err);
		}
		mcpAuditLabel.setText(TextUtils.format("ProductSettingsAction.mcp.audit_status",
		        backend.isAuditEnabled() ? TextUtils.getText("ProductSettingsAction.mcp.audit_on")
		                : TextUtils.getText("ProductSettingsAction.mcp.audit_off"),
		        Integer.valueOf(backend.getAuditEventCount()), Integer.valueOf(backend.getAuditPendingCount()),
		        backend.getAuditDbPath()));
	}

	private static boolean isTrue(final String value) {
		return value != null && ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value));
	}
}
