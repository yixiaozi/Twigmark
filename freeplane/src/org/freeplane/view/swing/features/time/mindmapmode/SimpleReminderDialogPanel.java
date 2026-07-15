package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

/**
 * Reminder editor with datetime picker and cycle settings.
 */
class SimpleReminderDialogPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final ReminderHook reminderHook;
	private final Runnable onClose;
	private final Runnable onLayoutChange;
	private final ReminderDateTimeEditorPanel dateTimeEditor;
	private final JButton okButton;
	private final JButton removeButton;
	private final RecurringReminderSettingsPanel cycleSettingsPanel;
	private final ReminderTaskSettingsPanel taskSettingsPanel;

	SimpleReminderDialogPanel(final ReminderHook reminderHook, final Runnable onClose, final Runnable onLayoutChange) {
		super(new BorderLayout(0, 8));
		this.reminderHook = reminderHook;
		this.onClose = onClose;
		this.onLayoutChange = onLayoutChange;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));

		final JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		dateTimeEditor = new ReminderDateTimeEditorPanel();
		dateTimeEditor.setAlignmentX(LEFT_ALIGNMENT);
		body.add(dateTimeEditor);
		body.add(Box.createVerticalStrut(8));

		cycleSettingsPanel = new RecurringReminderSettingsPanel();
		cycleSettingsPanel.setAlignmentX(LEFT_ALIGNMENT);
		cycleSettingsPanel.setOnLayoutChange(new Runnable() {
			public void run() {
				if (SimpleReminderDialogPanel.this.onLayoutChange != null) {
					SimpleReminderDialogPanel.this.onLayoutChange.run();
				}
			}
		});
		body.add(cycleSettingsPanel);
		body.add(Box.createVerticalStrut(8));

		final ReminderTaskSettingsPanel taskSettingsPanel = new ReminderTaskSettingsPanel();
		taskSettingsPanel.setAlignmentX(LEFT_ALIGNMENT);
		body.add(taskSettingsPanel);
		this.taskSettingsPanel = taskSettingsPanel;

		add(body, BorderLayout.CENTER);

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		okButton = new JButton(getText("plugins/TimeManagement.xml_confirmButton"));
		okButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				applyReminder();
			}
		});
		removeButton = new JButton(getText("plugins/TimeManagement.xml_removeReminderButton"));
		removeButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				removeReminder();
				if (onClose != null) {
					onClose.run();
				}
			}
		});
		final JButton cancelButton = new JButton(getText("plugins/TimeManagement.xml_cancelButton"));
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (onClose != null) {
					onClose.run();
				}
			}
		});
		buttons.add(okButton);
		buttons.add(removeButton);
		buttons.add(cancelButton);
		add(buttons, BorderLayout.SOUTH);

		setFocusable(true);
		loadFromSelectedNode();
	}

	JButton getOkButton() {
		return okButton;
	}

	void focusInput() {
		dateTimeEditor.focusInput();
	}

	boolean handleArrowKey(final KeyEvent e) {
		return dateTimeEditor.handleArrowKey(e);
	}

	void loadFromSelectedNode() {
		final NodeModel node = getSelectedNode();
		if (node == null) {
			dateTimeEditor.setDate(null);
			cycleSettingsPanel.loadFromConfig(ReminderCycleAttributes.CycleConfig.oneTime());
			taskSettingsPanel.loadFromConfig(ReminderTaskAttributes.TaskConfig.empty());
			okButton.setEnabled(false);
			removeButton.setEnabled(false);
			return;
		}
		okButton.setEnabled(true);
		final ReminderExtension reminder = ReminderExtension.getExtension(node);
		final ReminderCycleAttributes.CycleConfig cycleConfig = ReminderCycleAttributes.readFromNode(node);
		cycleSettingsPanel.loadFromConfig(cycleConfig);
		taskSettingsPanel.loadFromConfig(ReminderTaskAttributes.readFromNode(node));
		if (reminder != null) {
			dateTimeEditor.setDate(new Date(reminder.getRemindUserAt()));
			removeButton.setEnabled(true);
		}
		else {
			dateTimeEditor.setDate(null);
			removeButton.setEnabled(false);
		}
	}

	private void applyReminder() {
		final Date date = dateTimeEditor.getDate();
		if (date == null) {
			JOptionPane.showMessageDialog(this, getText("plugins/TimeManagement.xml_invalidDateTime"),
					getText("plugins/TimeManagement.xml_WindowTitle"), JOptionPane.WARNING_MESSAGE);
			focusInput();
			return;
		}
		final ReminderCycleAttributes.CycleConfig cycleConfig = cycleSettingsPanel.getConfig();
		final ReminderTaskAttributes.TaskConfig taskConfig = taskSettingsPanel.getConfig();
		final Collection nodes = Controller.getCurrentModeController().getMapController().getSelectedNodes();
		if (nodes.isEmpty()) {
			return;
		}
		for (final Iterator it = nodes.iterator(); it.hasNext();) {
			final NodeModel node = (NodeModel) it.next();
			final ReminderExtension existing = ReminderExtension.getExtension(node);
			if (existing != null) {
				reminderHook.undoableToggleHook(node);
			}
			final ReminderExtension reminderExtension = new ReminderExtension(node);
			reminderExtension.setRemindUserAt(date.getTime());
			if (cycleConfig.isRecurring()) {
				reminderExtension.setPeriod(cycleConfig.interval <= 0 ? 1 : cycleConfig.interval);
				reminderExtension.setPeriodUnit(periodUnitForRemindType(cycleConfig.remindType));
			}
			else {
				reminderExtension.setPeriodUnit(PeriodUnit.DAY);
				reminderExtension.setPeriod(1);
			}
			reminderExtension.setScript(null);
			reminderHook.undoableActivateHook(node, reminderExtension);
			ReminderCycleAttributes.writeToNode(node, cycleConfig);
			ReminderTaskAttributes.writeToNode(node, taskConfig);
		}
		if (onClose != null) {
			onClose.run();
		}
	}

	private static PeriodUnit periodUnitForRemindType(final String remindType) {
		if (ReminderCycleAttributes.TYPE_HOUR.equals(remindType)) {
			return PeriodUnit.HOUR;
		}
		if (ReminderCycleAttributes.TYPE_WEEK.equals(remindType)) {
			return PeriodUnit.WEEK;
		}
		if (ReminderCycleAttributes.TYPE_MONTH.equals(remindType)) {
			return PeriodUnit.MONTH;
		}
		if (ReminderCycleAttributes.TYPE_YEAR.equals(remindType)) {
			return PeriodUnit.YEAR;
		}
		return PeriodUnit.DAY;
	}

	private void removeReminder() {
		final Collection nodes = Controller.getCurrentModeController().getMapController().getSelectedNodes();
		for (final Iterator it = nodes.iterator(); it.hasNext();) {
			final NodeModel node = (NodeModel) it.next();
			if (ReminderExtension.getExtension(node) != null) {
				reminderHook.undoableToggleHook(node);
			}
			ReminderCycleAttributes.clearFromNode(node);
			ReminderTaskAttributes.clearFromNode(node);
		}
	}

	private NodeModel getSelectedNode() {
		return Controller.getCurrentModeController().getMapController().getSelectedNode();
	}

	private String getText(final String key) {
		return TextUtils.getText(key);
	}
}
