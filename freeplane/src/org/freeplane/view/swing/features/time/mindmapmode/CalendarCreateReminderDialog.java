package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Calendar "新建任务" form: title + cycle + duration/level/urgency.
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

	static Result show(final Component owner, final long startMs, final long endMs) {
		final long safeStart = startMs > 0L ? startMs : System.currentTimeMillis();
		final int defaultMinutes = (int) Math.max(5L, (endMs > safeStart ? endMs - safeStart : 30L * 60L * 1000L) / 60000L);

		final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
		final JPanel root = new JPanel(new BorderLayout(0, 8));
		root.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));

		final JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		final JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleRow.add(new JLabel("标题"));
		final JTextField titleField = new JTextField("新安排", 28);
		titleRow.add(titleField);
		body.add(titleRow);
		body.add(Box.createVerticalStrut(6));

		final JLabel timeLabel = new JLabel("开始时间：" + fmt.format(new Date(safeStart))
		        + "    （时长见下方「任务设置」）");
		timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(timeLabel);
		body.add(Box.createVerticalStrut(8));

		final RecurringReminderSettingsPanel cyclePanel = new RecurringReminderSettingsPanel();
		cyclePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		cyclePanel.loadFromConfig(ReminderCycleAttributes.CycleConfig.oneTime());
		body.add(cyclePanel);
		body.add(Box.createVerticalStrut(8));

		final ReminderTaskSettingsPanel taskPanel = new ReminderTaskSettingsPanel();
		taskPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		taskPanel.loadFromConfig(new ReminderTaskAttributes.TaskConfig(defaultMinutes, 0, 0));
		body.add(taskPanel);

		final JScrollPane scroll = new JScrollPane(body);
		scroll.setBorder(null);
		scroll.setPreferredSize(new Dimension(520, 280));
		root.add(scroll, BorderLayout.CENTER);

		cyclePanel.setOnLayoutChange(new Runnable() {
			public void run() {
				final Window window = SwingUtilities.getWindowAncestor(root);
				if (window != null) {
					window.pack();
				}
			}
		});

		final int option = JOptionPane.showConfirmDialog(owner, root, "新建安排", JOptionPane.OK_CANCEL_OPTION,
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
		ReminderTaskAttributes.TaskConfig taskConfig = taskPanel.getConfig();
		if (taskConfig.taskTime <= 0) {
			taskConfig = new ReminderTaskAttributes.TaskConfig(defaultMinutes, taskConfig.taskLevel, taskConfig.jinji);
		}
		return new Result(title, safeStart, cyclePanel.getConfig(), taskConfig);
	}
}
