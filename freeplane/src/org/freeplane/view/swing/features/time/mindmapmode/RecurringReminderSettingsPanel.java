package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.TextUtils;

/**
 * Cycle reminder settings compatible with DocearReminder.
 */
class RecurringReminderSettingsPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final String[] CYCLE_TYPE_KEYS = { ReminderCycleAttributes.TYPE_ONETIME,
			ReminderCycleAttributes.TYPE_HOUR, ReminderCycleAttributes.TYPE_DAY, ReminderCycleAttributes.TYPE_WEEK,
			ReminderCycleAttributes.TYPE_MONTH, ReminderCycleAttributes.TYPE_YEAR, ReminderCycleAttributes.TYPE_EB };

	private final JComboBox cycleTypeBox;
	private final JSpinner intervalSpinner;
	private final JLabel intervalLabel;
	private final JPanel intervalPanel;
	private final JPanel weekDaysPanel;
	private final JCheckBox[] weekDayBoxes = new JCheckBox[7];
	private Runnable onLayoutChange;
	private Runnable onChangeListener;

	RecurringReminderSettingsPanel() {
		super();
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(),
				TextUtils.getText("plugins/TimeManagement.xml_cycleSettingsTitle")));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);

		final JPanel typeIntervalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		typeIntervalRow.setAlignmentX(LEFT_ALIGNMENT);
		typeIntervalRow.add(new JLabel(TextUtils.getText("plugins/TimeManagement.xml_cycleRepeatLabel")));
		cycleTypeBox = new JComboBox(buildCycleTypeLabels());
		typeIntervalRow.add(cycleTypeBox);
		intervalPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		intervalLabel = new JLabel(TextUtils.getText("plugins/TimeManagement.xml_cycleIntervalLabel"));
		intervalSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
		intervalPanel.add(intervalLabel);
		intervalPanel.add(intervalSpinner);
		typeIntervalRow.add(intervalPanel);
		add(typeIntervalRow);

		weekDaysPanel = new JPanel(new GridLayout(2, 4, 6, 4));
		weekDaysPanel.setAlignmentX(LEFT_ALIGNMENT);
		final String[] weekDayKeys = { "plugins/TimeManagement.xml_weekday_monday_label",
				"plugins/TimeManagement.xml_weekday_tuesday_label", "plugins/TimeManagement.xml_weekday_wednesday_label",
				"plugins/TimeManagement.xml_weekday_thursday_label", "plugins/TimeManagement.xml_weekday_friday_label",
				"plugins/TimeManagement.xml_weekday_saturday_label", "plugins/TimeManagement.xml_weekday_sunday_label" };
		for (int i = 0; i < weekDayBoxes.length; i++) {
			weekDayBoxes[i] = new JCheckBox(TextUtils.getText(weekDayKeys[i]));
			weekDayBoxes[i].addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					notifyChanged();
				}
			});
			weekDaysPanel.add(weekDayBoxes[i]);
		}
		weekDaysPanel.add(new JLabel());
		add(weekDaysPanel);
		add(Box.createVerticalStrut(4));

		final ActionListener refreshVisibility = new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				updateVisibility();
				notifyChanged();
			}
		};
		cycleTypeBox.addActionListener(refreshVisibility);
		intervalSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(final ChangeEvent e) {
				notifyChanged();
			}
		});
		updateVisibility();
	}

	void setOnLayoutChange(final Runnable onLayoutChange) {
		this.onLayoutChange = onLayoutChange;
	}

	void setOnChangeListener(final Runnable onChangeListener) {
		this.onChangeListener = onChangeListener;
	}

	void loadFromConfig(final ReminderCycleAttributes.CycleConfig config) {
		final ReminderCycleAttributes.CycleConfig effective = config == null ? ReminderCycleAttributes.CycleConfig
				.oneTime() : config;
		cycleTypeBox.setSelectedIndex(findTypeIndex(effective.remindType));
		intervalSpinner.setValue(effective.interval <= 0 ? 1 : effective.interval);
		for (int i = 0; i < weekDayBoxes.length; i++) {
			weekDayBoxes[i].setSelected(containsWeekDay(effective.weekDays, (char) ('1' + i)));
		}
		if (ReminderCycleAttributes.TYPE_WEEK.equals(effective.remindType) && effective.weekDays.length() == 0) {
			weekDayBoxes[0].setSelected(true);
		}
		updateVisibility();
	}

	ReminderCycleAttributes.CycleConfig getConfig() {
		final String type = CYCLE_TYPE_KEYS[cycleTypeBox.getSelectedIndex()];
		final int interval = ((Number) intervalSpinner.getValue()).intValue();
		if (ReminderCycleAttributes.TYPE_ONETIME.equals(type)) {
			return ReminderCycleAttributes.CycleConfig.oneTime();
		}
		if (ReminderCycleAttributes.TYPE_EB.equals(type)) {
			return new ReminderCycleAttributes.CycleConfig(type, 1, "", 0);
		}
		if (ReminderCycleAttributes.TYPE_WEEK.equals(type)) {
			String weekDays = buildWeekDays();
			if (weekDays.length() == 0) {
				weekDays = "1";
			}
			return new ReminderCycleAttributes.CycleConfig(type, interval, weekDays, 0);
		}
		return new ReminderCycleAttributes.CycleConfig(type, interval, "", 0);
	}

	private String[] buildCycleTypeLabels() {
		final String[] labels = new String[CYCLE_TYPE_KEYS.length];
		labels[0] = TextUtils.getText("plugins/TimeManagement.xml_cycleType_onetime");
		labels[1] = TextUtils.getText("plugins/TimeManagement.xml_cycleType_hour");
		labels[2] = TextUtils.getText("plugins/TimeManagement.xml_cycleType_day");
		labels[3] = TextUtils.getText("plugins/TimeManagement.xml_cycleType_week");
		labels[4] = TextUtils.getText("plugins/TimeManagement.xml_cycleType_month");
		labels[5] = TextUtils.getText("plugins/TimeManagement.xml_cycleType_year");
		labels[6] = TextUtils.getText("plugins/TimeManagement.xml_cycleType_eb");
		return labels;
	}

	private int findTypeIndex(final String remindType) {
		if (remindType == null || remindType.length() == 0) {
			return 0;
		}
		for (int i = 0; i < CYCLE_TYPE_KEYS.length; i++) {
			if (CYCLE_TYPE_KEYS[i].equalsIgnoreCase(remindType)) {
				return i;
			}
		}
		return 0;
	}

	private void updateVisibility() {
		final String type = CYCLE_TYPE_KEYS[cycleTypeBox.getSelectedIndex()];
		final boolean showInterval = !ReminderCycleAttributes.TYPE_ONETIME.equals(type)
				&& !ReminderCycleAttributes.TYPE_EB.equals(type);
		intervalPanel.setVisible(showInterval);
		weekDaysPanel.setVisible(ReminderCycleAttributes.TYPE_WEEK.equals(type));
		if (onLayoutChange != null) {
			onLayoutChange.run();
		}
	}

	private void notifyChanged() {
		if (onChangeListener != null) {
			onChangeListener.run();
		}
	}

	private String buildWeekDays() {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < weekDayBoxes.length; i++) {
			if (weekDayBoxes[i].isSelected()) {
				sb.append((char) ('1' + i));
			}
		}
		return sb.toString();
	}

	private static boolean containsWeekDay(final String weekDays, final char day) {
		return weekDays != null && weekDays.indexOf(day) >= 0;
	}
}
