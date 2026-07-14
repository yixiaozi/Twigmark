package org.docear.plugin.core.todoist;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;

public class TodoistSettingsAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;
	public static final String KEY = "TodoistSettingsAction";

	public TodoistSettingsAction() {
		super(KEY);
	}

	public void actionPerformed(ActionEvent e) {
		showSettingsDialog();
	}

	static void showSettingsDialog() {
		final JTextField projectField = new JTextField(TodoistConfig.getProjectName(), 32);
		final JTextField projectIdField = new JTextField(TodoistConfig.getProjectId(), 32);
		final JTextField importTargetField = new JTextField(TodoistConfig.getImportTargetFile().getAbsolutePath(), 32);
		final JPasswordField tokenField = new JPasswordField(TodoistConfig.getApiToken(), 32);
		final JCheckBox autoSyncCheck = new JCheckBox(TextUtils.getText("todoist.settings.auto_sync"),
				TodoistConfig.isAutoSyncEnabled());
		final JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(
				TodoistConfig.getAutoSyncIntervalMinutes(), 1, 120, 1));
		final JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(4, 4, 4, 4);
		panel.add(new JLabel(TextUtils.getText("todoist.settings.token")), c);
		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		panel.add(tokenField, c);
		c.gridx = 0;
		c.gridy = 1;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		panel.add(new JLabel(TextUtils.getText("todoist.settings.project")), c);
		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		panel.add(projectField, c);
		c.gridx = 0;
		c.gridy = 2;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		panel.add(new JLabel(TextUtils.getText("todoist.settings.project_id")), c);
		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		panel.add(projectIdField, c);
		c.gridx = 0;
		c.gridy = 3;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		panel.add(new JLabel(TextUtils.getText("todoist.settings.import_target")), c);
		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		panel.add(importTargetField, c);
		c.gridx = 0;
		c.gridy = 4;
		c.gridwidth = 2;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(autoSyncCheck, c);
		c.gridx = 0;
		c.gridy = 5;
		c.gridwidth = 1;
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		panel.add(new JLabel(TextUtils.getText("todoist.settings.auto_sync_interval")), c);
		c.gridx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		panel.add(intervalSpinner, c);
		c.gridx = 0;
		c.gridy = 6;
		c.gridwidth = 2;
		panel.add(new JLabel(TextUtils.getText("todoist.settings.hint")), c);

		int option = JOptionPane.showConfirmDialog(Controller.getCurrentController().getViewController().getFrame(),
				panel, TextUtils.getText("todoist.settings.title"), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (option != JOptionPane.OK_OPTION) {
			return;
		}
		TodoistConfig.setApiToken(new String(tokenField.getPassword()));
		TodoistConfig.setProjectName(projectField.getText());
		String projectId = projectIdField.getText().trim();
		if (projectId.length() > 0) {
			TodoistConfig.setProjectId(projectId, projectField.getText().trim());
		}
		TodoistConfig.setImportTargetFile(importTargetField.getText());
		TodoistConfig.setAutoSyncEnabled(autoSyncCheck.isSelected());
		TodoistConfig.setAutoSyncIntervalMinutes(((Integer) intervalSpinner.getValue()).intValue());
		TodoistAutoSyncService.getInstance().restartPeriodicTimer();
		if (TodoistConfig.isAutoSyncEnabled()) {
			TodoistAutoSyncService.getInstance().requestImmediateFullSync();
		}
	}
}
