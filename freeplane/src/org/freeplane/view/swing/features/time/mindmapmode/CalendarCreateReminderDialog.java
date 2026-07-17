package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Calendar create/edit form: title + cycle + duration/level/urgency.
 * Reuses the same settings panels as {@link SimpleReminderDialogPanel}.
 */
final class CalendarCreateReminderDialog {
	private CalendarCreateReminderDialog() {
	}

	static final class Result {
		final String title;
		final long startMs;
		final ReminderCycleAttributes.CycleConfig cycleConfig;
		final ReminderTaskAttributes.TaskConfig taskConfig;

		Result(final String title, final long startMs, final ReminderCycleAttributes.CycleConfig cycleConfig,
		        final ReminderTaskAttributes.TaskConfig taskConfig) {
			this.title = title;
			this.startMs = startMs;
			this.cycleConfig = cycleConfig;
			this.taskConfig = taskConfig;
		}
	}

	static Result showCreate(final Component owner, final long startMs, final long endMs) {
		final long safeStart = startMs > 0L ? startMs : System.currentTimeMillis();
		final int defaultMinutes = (int) Math.max(5L,
		        (endMs > safeStart ? endMs - safeStart : 30L * 60L * 1000L) / 60000L);
		return show(owner, "新建安排", "新安排", safeStart, ReminderCycleAttributes.CycleConfig.oneTime(),
		        new ReminderTaskAttributes.TaskConfig(defaultMinutes, 0, 0), defaultMinutes);
	}

	static Result showEdit(final Component owner, final String title, final long startMs,
	        final ReminderCycleAttributes.CycleConfig cycleConfig, final ReminderTaskAttributes.TaskConfig taskConfig) {
		final int fallbackMinutes = taskConfig != null && taskConfig.taskTime > 0 ? taskConfig.taskTime : 30;
		return show(owner, "编辑安排", title == null || title.length() == 0 ? "新安排" : title, startMs,
		        cycleConfig == null ? ReminderCycleAttributes.CycleConfig.oneTime() : cycleConfig,
		        taskConfig == null ? new ReminderTaskAttributes.TaskConfig(fallbackMinutes, 0, 0) : taskConfig,
		        fallbackMinutes);
	}

	private static Result show(final Component owner, final String dialogTitle, final String initialTitle,
	        final long startMs, final ReminderCycleAttributes.CycleConfig cycleConfig,
	        final ReminderTaskAttributes.TaskConfig taskConfig, final int defaultMinutes) {
		final long safeStart = startMs > 0L ? startMs : System.currentTimeMillis();
		final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

		final JPanel root = new JPanel(new BorderLayout(0, 6));
		root.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));

		final JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JPanel titleRow = new JPanel(new BorderLayout(8, 0));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		titleRow.add(new JLabel("标题"), BorderLayout.WEST);
		final JTextField titleField = new JTextField(initialTitle);
		titleRow.add(titleField, BorderLayout.CENTER);
		body.add(titleRow);
		body.add(Box.createVerticalStrut(4));

		final JLabel timeLabel = new JLabel("开始时间：" + fmt.format(new Date(safeStart)) + "（时长见下方）");
		timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(timeLabel);
		body.add(Box.createVerticalStrut(6));

		final RecurringReminderSettingsPanel cyclePanel = new RecurringReminderSettingsPanel();
		cyclePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		cyclePanel.loadFromConfig(cycleConfig);
		body.add(cyclePanel);
		body.add(Box.createVerticalStrut(6));

		final ReminderTaskSettingsPanel taskPanel = new ReminderTaskSettingsPanel();
		taskPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		taskPanel.loadFromConfig(taskConfig);
		body.add(taskPanel);

		root.add(body, BorderLayout.CENTER);

		cyclePanel.setOnLayoutChange(new Runnable() {
			public void run() {
				final Window window = SwingUtilities.getWindowAncestor(root);
				if (window != null) {
					window.pack();
				}
			}
		});

		final int option = JOptionPane.showConfirmDialog(owner, root, dialogTitle, JOptionPane.OK_CANCEL_OPTION,
		        JOptionPane.PLAIN_MESSAGE);
		if (option != JOptionPane.OK_OPTION) {
			return null;
		}
		String title = titleField.getText();
		if (title != null) {
			title = title.trim();
		}
		if (title == null || title.length() == 0) {
			title = "新安排";
		}
		ReminderTaskAttributes.TaskConfig resultTask = taskPanel.getConfig();
		if (resultTask.taskTime <= 0) {
			resultTask = new ReminderTaskAttributes.TaskConfig(defaultMinutes, resultTask.taskLevel, resultTask.jinji);
		}
		return new Result(title, safeStart, cyclePanel.getConfig(), resultTask);
	}
}
