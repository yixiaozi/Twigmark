package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.TextUtils;

/**
 * Task duration, level and urgency settings compatible with DocearReminder.
 */
class ReminderTaskSettingsPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private final JSpinner durationSpinner;
	private final JSpinner levelSpinner;
	private final JSpinner urgencySpinner;

	ReminderTaskSettingsPanel() {
		super(new FlowLayout(FlowLayout.LEFT, 8, 0));
		setAlignmentX(LEFT_ALIGNMENT);
		setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(),
				TextUtils.getText("plugins/TimeManagement.xml_taskSettingsTitle")));
		setOpaque(false);

		durationSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100000, 5));
		levelSpinner = new JSpinner(new SpinnerNumberModel(0, -100000, 10000, 1));
		urgencySpinner = new JSpinner(new SpinnerNumberModel(0, -100000, 10000, 1));

		add(new JLabel(TextUtils.getText("plugins/TimeManagement.xml_taskDurationLabel")));
		add(durationSpinner);
		add(new JLabel(TextUtils.getText("plugins/TimeManagement.xml_taskLevelLabel")));
		add(levelSpinner);
		add(new JLabel(TextUtils.getText("plugins/TimeManagement.xml_taskUrgencyLabel")));
		add(urgencySpinner);
	}

	void loadFromConfig(final ReminderTaskAttributes.TaskConfig config) {
		final ReminderTaskAttributes.TaskConfig value = config == null ? ReminderTaskAttributes.TaskConfig.empty()
				: config;
		durationSpinner.setValue(Integer.valueOf(value.taskTime));
		levelSpinner.setValue(Integer.valueOf(value.taskLevel));
		urgencySpinner.setValue(Integer.valueOf(value.jinji));
	}

	ReminderTaskAttributes.TaskConfig getConfig() {
		return new ReminderTaskAttributes.TaskConfig(((Number) durationSpinner.getValue()).intValue(),
				((Number) levelSpinner.getValue()).intValue(), ((Number) urgencySpinner.getValue()).intValue());
	}
}
