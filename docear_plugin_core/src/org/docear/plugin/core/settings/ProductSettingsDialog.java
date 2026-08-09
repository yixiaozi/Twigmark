package org.docear.plugin.core.settings;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.security.SecureRandom;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;

import javax.swing.JTextArea;

import org.docear.plugin.core.eagle.EagleConfig;
import org.docear.plugin.core.eagle.EagleItemIndex;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.MindMapFileIdentity;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.view.swing.features.finance.FinanceLedgerService;
import org.freeplane.view.swing.features.git.GitConfig;

/**
 * Product settings hub: working directory + MCP / Git / Finance / QuickCapture / Eagle.
 */
public final class ProductSettingsDialog extends JDialog {
	private static final long serialVersionUID = 1L;

	private static final String PROP_INBOX_DIRECTORY = "quickcapture.inbox_directory";
	private static final String PROP_INBOX_FILENAME = "quickcapture.inbox_filename";

	private final JTextField workingDirField = new JTextField(36);
	private final JTextArea eagleLibrariesArea = new JTextArea(5, 36);
	private final JTextField eaglePrimaryField = new JTextField(36);
	private final JCheckBox eagleAutoImport = new JCheckBox();
	private final JLabel eagleIndexLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel configDirLabel = DocearUiTheme.mutedLabel(" ");
	private final JCheckBox mcpEnabled = new JCheckBox();
	private final JTextField mcpHostField = new JTextField(16);
	private final JSpinner mcpPortSpinner = new JSpinner(new SpinnerNumberModel(7720, 1, 65535, 1));
	private final JCheckBox mcpReadOnly = new JCheckBox();
	private final JCheckBox mcpAuthEnabled = new JCheckBox();
	private final JPasswordField mcpApiKeyField = new JPasswordField(28);
	private final JCheckBox mcpWebEnabled = new JCheckBox();
	private final JTextField mcpLlmBaseUrlField = new JTextField(28);
	private final JPasswordField mcpLlmApiKeyField = new JPasswordField(28);
	private final JTextField mcpLlmModelField = new JTextField(20);
	private final JCheckBox mcpCursorSync = new JCheckBox();
	private final JCheckBox mcpAuditEnabled = new JCheckBox();
	private final JLabel mcpStatusBadge = DocearUiTheme.titleLabel(" ");
	private final JLabel mcpStatusLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel mcpAuditLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel mcpWebUrlLabel = DocearUiTheme.mutedLabel(" ");
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
		tabs.addTab(TextUtils.getText("ProductSettingsAction.tab.eagle"), buildEagleTab());
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
		mcpAuthEnabled.setText(TextUtils.getText("ProductSettingsAction.mcp.auth_enabled"));
		mcpWebEnabled.setText(TextUtils.getText("ProductSettingsAction.mcp.web_enabled"));
		mcpCursorSync.setText(TextUtils.getText("ProductSettingsAction.mcp.cursor_sync"));
		mcpAuditEnabled.setText(TextUtils.getText("ProductSettingsAction.mcp.audit_enabled"));
		int row = 0;
		addCheck(panel, c, row++, mcpEnabled);
		addLabel(panel, c, row, TextUtils.getText("ProductSettingsAction.mcp.host"));
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(mcpHostField, c);
		row++;
		addLabel(panel, c, row, TextUtils.getText("ProductSettingsAction.mcp.port"));
		c.gridx = 1;
		panel.add(mcpPortSpinner, c);
		row++;
		addCheck(panel, c, row++, mcpReadOnly);
		addCheck(panel, c, row++, mcpAuthEnabled);
		addLabel(panel, c, row, TextUtils.getText("ProductSettingsAction.mcp.api_key"));
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(mcpApiKeyField, c);
		row++;
		c.gridx = 1;
		c.gridy = row;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		final JPanel keyActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		keyActions.setOpaque(false);
		final JButton generateKey = DocearUiTheme.softButton(TextUtils.getText("ProductSettingsAction.mcp.generate_key"));
		final JButton bindPublic = DocearUiTheme.softButton(TextUtils.getText("ProductSettingsAction.mcp.bind_public"));
		generateKey.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mcpApiKeyField.setText(generateMcpApiKey());
				mcpAuthEnabled.setSelected(true);
			}
		});
		bindPublic.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				mcpHostField.setText("0.0.0.0");
				mcpAuthEnabled.setSelected(true);
				if (passwordValue(mcpApiKeyField).length() == 0) {
					mcpApiKeyField.setText(generateMcpApiKey());
				}
			}
		});
		keyActions.add(generateKey);
		keyActions.add(bindPublic);
		panel.add(keyActions, c);
		row++;
		c.gridx = 0;
		c.gridy = row;
		c.gridwidth = 2;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(DocearUiTheme.mutedLabel(TextUtils.getText("ProductSettingsAction.mcp.auth_hint")), c);
		row++;
		addCheck(panel, c, row++, mcpWebEnabled);
		addLabel(panel, c, row, TextUtils.getText("ProductSettingsAction.mcp.llm_base_url"));
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(mcpLlmBaseUrlField, c);
		row++;
		addLabel(panel, c, row, TextUtils.getText("ProductSettingsAction.mcp.llm_api_key"));
		c.gridx = 1;
		panel.add(mcpLlmApiKeyField, c);
		row++;
		addLabel(panel, c, row, TextUtils.getText("ProductSettingsAction.mcp.llm_model"));
		c.gridx = 1;
		panel.add(mcpLlmModelField, c);
		row++;
		c.gridx = 0;
		c.gridy = row;
		c.gridwidth = 2;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(mcpWebUrlLabel, c);
		row++;
		addCheck(panel, c, row++, mcpCursorSync);
		addCheck(panel, c, row++, mcpAuditEnabled);

		c.gridx = 0;
		c.gridy = row;
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
				if (!validateMcpSettings()) {
					return;
				}
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

		final JScrollPane scroll = new JScrollPane(panel);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setPreferredSize(new Dimension(560, 420));
		final JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.add(scroll, BorderLayout.CENTER);
		return wrap;
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

	private JPanel buildEagleTab() {
		final JPanel panel = formPanel();
		final GridBagConstraints c = baseConstraints();
		eagleLibrariesArea.setLineWrap(true);
		eagleLibrariesArea.setWrapStyleWord(true);
		eagleLibrariesArea.setFont(DocearUiTheme.font(12f));
		final JScrollPane libScroll = new JScrollPane(eagleLibrariesArea);
		libScroll.setPreferredSize(new Dimension(420, 100));

		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 3;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(DocearUiTheme.mutedLabel(TextUtils.getText("eagle.settings.libraries")), c);
		c.gridy = 1;
		c.weightx = 1;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		panel.add(libScroll, c);
		c.gridy = 2;
		c.weighty = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(DocearUiTheme.mutedLabel(TextUtils.getText("eagle.settings.primary")), c);
		c.gridy = 3;
		c.gridwidth = 2;
		panel.add(eaglePrimaryField, c);
		c.gridx = 2;
		c.gridwidth = 1;
		c.fill = GridBagConstraints.NONE;
		panel.add(browseButton(eaglePrimaryField, true), c);
		c.gridx = 0;
		c.gridy = 4;
		c.gridwidth = 3;
		eagleAutoImport.setText(TextUtils.getText("eagle.settings.auto_import"));
		eagleAutoImport.setOpaque(false);
		eagleAutoImport.setFont(DocearUiTheme.font(12f));
		panel.add(eagleAutoImport, c);
		c.gridy = 5;
		panel.add(DocearUiTheme.mutedLabel(TextUtils.getText("eagle.settings.hint")), c);
		c.gridy = 6;
		panel.add(eagleIndexLabel, c);
		c.gridy = 7;
		c.fill = GridBagConstraints.NONE;
		final JButton rebuild = DocearUiTheme.softButton(TextUtils.getText("eagle.settings.rebuild_now"));
		rebuild.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				applyEagleFieldsToConfig();
				EagleItemIndex.getInstance().rebuild(true, null);
				refreshEagleIndexLabel();
				JOptionPane.showMessageDialog(ProductSettingsDialog.this,
						TextUtils.format("eagle.settings.rebuilt",
								Integer.valueOf(EagleItemIndex.getInstance().size()),
								Integer.valueOf(EagleConfig.existingLibraryRoots().size())),
						TextUtils.getText("ProductSettingsAction.tab.eagle"), JOptionPane.INFORMATION_MESSAGE);
			}
		});
		panel.add(rebuild, c);
		return panel;
	}

	private void applyEagleFieldsToConfig() {
		EagleConfig.setLibraryPathsText(eagleLibrariesArea.getText());
		final String primary = eaglePrimaryField.getText() == null ? "" : eaglePrimaryField.getText().trim();
		EagleConfig.setPrimaryLibrary(primary.length() == 0 ? null : new File(primary));
		EagleConfig.setAutoImportEnabled(eagleAutoImport.isSelected());
	}

	private void refreshEagleIndexLabel() {
		eagleIndexLabel.setText(TextUtils.format("eagle.settings.index_status",
				Integer.valueOf(EagleItemIndex.getInstance().size()),
				Integer.valueOf(EagleConfig.existingLibraryRoots().size())));
	}

	private void loadValues() {
		workingDirField.setText(MindMapDataRootResolver.getWorkingDirectory().getAbsolutePath());
		refreshConfigHint();
		eagleLibrariesArea.setText(EagleConfig.getLibraryPathsText());
		final File primary = EagleConfig.getPrimaryLibrary();
		eaglePrimaryField.setText(primary == null ? "" : primary.getAbsolutePath());
		eagleAutoImport.setSelected(EagleConfig.isAutoImportEnabled());
		refreshEagleIndexLabel();
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
		mcpAuthEnabled.setSelected(isTrue(rc.getProperty("mcp.auth.enabled", "false")));
		mcpApiKeyField.setText(rc.getProperty("mcp.auth.apiKey", ""));
		mcpWebEnabled.setSelected(isTrue(rc.getProperty("mcp.web.enabled", "true")));
		mcpLlmBaseUrlField.setText(rc.getProperty("mcp.web.llm.baseUrl", "https://openrouter.ai/api/v1"));
		mcpLlmApiKeyField.setText(rc.getProperty("mcp.web.llm.apiKey", ""));
		mcpLlmModelField.setText(rc.getProperty("mcp.web.llm.model", "openai/gpt-4o-mini"));
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
		if (!MindMapDataRootResolver.isUsableWorkingDirectoryPath(working)) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("ProductSettingsAction.working_dir_invalid_os"),
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

		if (!validateMcpSettings()) {
			return false;
		}
		applyMcpRuntimeProperties();
		final McpRuntimeFacade.Backend backend = McpRuntimeFacade.get();
		if (backend != null) {
			backend.restartServer();
		}

		GitConfig.setRepositoryPath(gitRepoField.getText());

		final ResourceController rc = ResourceController.getResourceController();
		rc.setProperty(FinanceLedgerService.PROP_MAP_PATH,
		        financeMapField.getText() == null ? "" : financeMapField.getText().trim());
		rc.setProperty(PROP_INBOX_DIRECTORY, inboxDirField.getText() == null ? "" : inboxDirField.getText().trim());
		rc.setProperty(PROP_INBOX_FILENAME,
		        inboxFileField.getText() == null || inboxFileField.getText().trim().length() == 0
		                ? "\u6536\u4ef6\u7bb1.mm" : inboxFileField.getText().trim());

		applyEagleFieldsToConfig();
		try {
			if (!EagleConfig.existingLibraryRoots().isEmpty()) {
				EagleItemIndex.getInstance().rebuild(false, null);
			}
		}
		catch (Exception e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), getTitle(), JOptionPane.WARNING_MESSAGE);
		}

		MindMapDataRootResolver.markSetupCompleted();

		if (workingChanged && !firstRun) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("ProductSettingsAction.restart_hint"), getTitle(),
			        JOptionPane.INFORMATION_MESSAGE);
		}
		return true;
	}

	private static boolean samePath(final File a, final File b) {
		return MindMapFileIdentity.isSameFile(a, b);
	}

	private JButton browseButton(final JTextField field, final boolean directoriesOnly) {
		final JButton button = DocearUiTheme.softButton(TextUtils.getText("browse"));
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				File start = null;
				final String current = field.getText();
				if (current != null && current.trim().length() > 0) {
					final File candidate = new File(current.trim());
					if (directoriesOnly && !MindMapDataRootResolver.isUsableWorkingDirectoryPath(current.trim())) {
						start = null;
					}
					else {
						start = candidate;
					}
				}
				if (start == null) {
					try {
						start = MindMapDataRootResolver.getWorkingDirectory();
					}
					catch (Exception ignored) {
						start = new File(System.getProperty("user.home", "."));
					}
				}
				final String title = directoriesOnly ? TextUtils.getText("ProductSettingsAction.working_dir")
				        : TextUtils.getText("browse");
				final File selected = org.freeplane.core.util.DirectoryPicker.choose(ProductSettingsDialog.this,
				        title, start, directoriesOnly);
				if (selected != null) {
					field.setText(selected.getAbsolutePath());
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
		rc.setProperty("mcp.auth.enabled", mcpAuthEnabled.isSelected() ? "true" : "false");
		rc.setProperty("mcp.auth.apiKey", passwordValue(mcpApiKeyField));
		rc.setProperty("mcp.web.enabled", mcpWebEnabled.isSelected() ? "true" : "false");
		final String baseUrl = mcpLlmBaseUrlField.getText() == null || mcpLlmBaseUrlField.getText().trim().length() == 0
		        ? "https://openrouter.ai/api/v1" : mcpLlmBaseUrlField.getText().trim();
		rc.setProperty("mcp.web.llm.baseUrl", baseUrl);
		rc.setProperty("mcp.web.llm.apiKey", passwordValue(mcpLlmApiKeyField));
		final String model = mcpLlmModelField.getText() == null || mcpLlmModelField.getText().trim().length() == 0
		        ? "openai/gpt-4o-mini" : mcpLlmModelField.getText().trim();
		rc.setProperty("mcp.web.llm.model", model);
		rc.setProperty("mcp.cursorPlugin.sync.enabled", mcpCursorSync.isSelected() ? "true" : "false");
		rc.setProperty("mcp.audit.enabled", mcpAuditEnabled.isSelected() ? "true" : "false");
		final McpRuntimeFacade.Backend llmBackend = McpRuntimeFacade.get();
		if (llmBackend != null) {
			llmBackend.syncLlmFromProductSettings(baseUrl, passwordValue(mcpLlmApiKeyField), model);
		}
	}

	private boolean validateMcpSettings() {
		final String host = mcpHostField.getText() == null || mcpHostField.getText().trim().length() == 0
		        ? "127.0.0.1" : mcpHostField.getText().trim();
		final boolean publicBind = isPublicMcpHost(host);
		final String apiKey = passwordValue(mcpApiKeyField);
		if ((publicBind || mcpAuthEnabled.isSelected()) && apiKey.length() == 0) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("ProductSettingsAction.mcp.api_key_required"),
			        getTitle(), JOptionPane.WARNING_MESSAGE);
			return false;
		}
		if (publicBind && !mcpAuthEnabled.isSelected()) {
			mcpAuthEnabled.setSelected(true);
		}
		return true;
	}

	private void refreshMcpStatus() {
		final ResourceController rc = ResourceController.getResourceController();
		final String host = mcpHostField.getText() == null || mcpHostField.getText().trim().length() == 0
		        ? rc.getProperty("mcp.host", "127.0.0.1") : mcpHostField.getText().trim();
		final String port = String.valueOf(((Number) mcpPortSpinner.getValue()).intValue());
		mcpStatusLabel.setText(TextUtils.format("ProductSettingsAction.mcp.status", host, port));
		mcpWebUrlLabel.setText(TextUtils.format("ProductSettingsAction.mcp.web_url",
		        isPublicMcpHost(host) ? "127.0.0.1" : host, port)
		        + "  |  "
		        + TextUtils.getText("ProductSettingsAction.mcp.webchat_db_hint"));

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
			mcpStatusLabel.setText(mcpStatusLabel.getText() + "  |  " + err);
		}
		mcpAuditLabel.setText(TextUtils.format("ProductSettingsAction.mcp.audit_status",
		        backend.isAuditEnabled() ? TextUtils.getText("ProductSettingsAction.mcp.audit_on")
		                : TextUtils.getText("ProductSettingsAction.mcp.audit_off"),
		        Integer.valueOf(backend.getAuditEventCount()), Integer.valueOf(backend.getAuditPendingCount()),
		        backend.getAuditDbPath()));
	}

	private static String passwordValue(final JPasswordField field) {
		if (field == null) {
			return "";
		}
		final char[] chars = field.getPassword();
		if (chars == null || chars.length == 0) {
			return "";
		}
		return new String(chars).trim();
	}

	private static boolean isPublicMcpHost(final String host) {
		if (host == null || host.length() == 0) {
			return false;
		}
		final String h = host.trim().toLowerCase();
		return !("127.0.0.1".equals(h) || "localhost".equals(h) || "::1".equals(h));
	}

	private static String generateMcpApiKey() {
		final SecureRandom random = new SecureRandom();
		final byte[] bytes = new byte[24];
		random.nextBytes(bytes);
		final StringBuilder sb = new StringBuilder("tm_");
		for (int i = 0; i < bytes.length; i++) {
			final int v = bytes[i] & 0xff;
			if (v < 16) {
				sb.append('0');
			}
			sb.append(Integer.toHexString(v));
		}
		return sb.toString();
	}

	private static boolean isTrue(final String value) {
		return value != null && ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value));
	}
}
