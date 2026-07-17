package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.features.map.IMapSelectionListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderWorkspaceScanHelper.TimelineOccurrence;

/**
 * Day view timeline of all reminders (one-time and recurring occurrences).
 */
public class ReminderTimelineTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final String[] TABLE_COLUMNS = { "\u65f6\u95f4", "\u7c7b\u578b", "\u65f6\u957f", "\u7b49\u7ea7",
			"\u7d27\u6025", "\u4efb\u52a1", "\u5bfc\u56fe" };

	private final JButton refreshButton = new JButton("\u5237\u65b0");
	private final JButton prevDayButton = new JButton("\u25C0");
	private final JButton nextDayButton = new JButton("\u25B6");
	private final JButton todayButton = new JButton("\u4eca\u5929");
	private final JLabel dayLabel = new JLabel(" ");
	private final JLabel statusLabel = new JLabel("\u5f53\u65e5\u4efb\u52a1: 0");
	private final DefaultTableModel tableModel = new DefaultTableModel(TABLE_COLUMNS, 0) {
		private static final long serialVersionUID = 1L;

		public boolean isCellEditable(final int row, final int column) {
			return false;
		}
	};
	private final List tableRows = new ArrayList();
	private final JTable table = new JTable(tableModel);
	private final SimpleDateFormat dayTitleFormat = new SimpleDateFormat("yyyy\u5e74M\u6708d\u65e5 EEEE", Locale.CHINA);
	private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
	private final List allEntries = new ArrayList();
	private long selectedDayStart = ReminderCycleScheduler.startOfDay(System.currentTimeMillis());
	private SwingWorker activeWorker;
	private boolean rescanRequested;
	private final Timer autoRefreshTimer;

	public ReminderTimelineTabPanel() {
		super(new BorderLayout(4, 4));
		final JPanel top = new JPanel(new BorderLayout(4, 0));
		final JPanel navBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		navBar.add(prevDayButton);
		navBar.add(nextDayButton);
		navBar.add(todayButton);
		navBar.add(dayLabel);
		top.add(navBar, BorderLayout.CENTER);
		final JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		topButtons.add(statusLabel);
		topButtons.add(refreshButton);
		top.add(topButtons, BorderLayout.EAST);
		add(top, BorderLayout.NORTH);

		prevDayButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				selectedDayStart = ReminderCycleScheduler.addDays(selectedDayStart, -1);
				rebuildTableForSelectedDay();
			}
		});
		nextDayButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				selectedDayStart = ReminderCycleScheduler.addDays(selectedDayStart, 1);
				rebuildTableForSelectedDay();
			}
		});
		todayButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				selectedDayStart = ReminderCycleScheduler.startOfDay(System.currentTimeMillis());
				rebuildTableForSelectedDay();
			}
		});

		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getColumnModel().getColumn(0).setPreferredWidth(56);
		table.getColumnModel().getColumn(1).setPreferredWidth(48);
		table.getColumnModel().getColumn(2).setPreferredWidth(44);
		table.getColumnModel().getColumn(3).setPreferredWidth(40);
		table.getColumnModel().getColumn(4).setPreferredWidth(40);
		table.getColumnModel().getColumn(5).setPreferredWidth(220);
		table.getColumnModel().getColumn(6).setPreferredWidth(100);
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() >= 1) {
					openSelectedEntry();
				}
			}
		});
		add(new JScrollPane(table), BorderLayout.CENTER);

		refreshButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				ReminderWorkspaceEntryCache.invalidateAll();
				refreshInBackground();
			}
		});
		autoRefreshTimer = new Timer(300000, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				refreshInBackground();
			}
		});
		autoRefreshTimer.setRepeats(true);
		autoRefreshTimer.start();

		Controller.getCurrentController().getMapViewManager().addMapSelectionListener(new IMapSelectionListener() {
			public void beforeMapChange(final MapModel oldMap, final MapModel newMap) {
			}

			public void afterMapChange(final MapModel oldMap, final MapModel newMap) {
				refreshInBackground();
			}
		});
		refreshInBackground();
	}

	private synchronized void refreshInBackground() {
		if (activeWorker != null && !activeWorker.isDone()) {
			rescanRequested = true;
			return;
		}
		rescanRequested = false;
		activeWorker = new SwingWorker() {
			protected Object doInBackground() throws Exception {
				allEntries.clear();
				allEntries.addAll(ReminderWorkspaceEntryCache.getAllEntries());
				return null;
			}

			protected void done() {
				try {
					rebuildTableForSelectedDay();
				}
				catch (Exception e) {
					LogUtils.warn(e);
				}
				if (rescanRequested) {
					rescanRequested = false;
					refreshInBackground();
				}
			}
		};
		activeWorker.execute();
	}

	private void rebuildTableForSelectedDay() {
		dayLabel.setText(dayTitleFormat.format(new Date(selectedDayStart)));
		final long dayEnd = ReminderCycleScheduler.addDays(selectedDayStart, 1);
		rebuildTable(ReminderWorkspaceScanHelper.buildTimelineOccurrences(allEntries, selectedDayStart, dayEnd));
	}

	private void rebuildTable(final List occurrences) {
		while (tableModel.getRowCount() > 0) {
			tableModel.removeRow(0);
		}
		tableRows.clear();
		if (occurrences == null || occurrences.isEmpty()) {
			SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_TIMELINE_TODAY, 0);
			statusLabel.setText("\u5f53\u65e5\u4efb\u52a1: 0");
			return;
		}
		for (int i = 0; i < occurrences.size(); i++) {
			final TimelineOccurrence item = (TimelineOccurrence) occurrences.get(i);
			final ReminderCalendarEntry entry = item.entry;
			final String typeLabel = entry.recurring ? "\u5468\u671f" : "\u4e00\u6b21";
			tableModel.addRow(new Object[] { timeFormat.format(new Date(item.occurrenceAt)), typeLabel,
					ReminderTaskFormatter.formatDurationMinutes(entry.taskTime),
					ReminderTaskFormatter.formatLevel(entry.taskLevel),
					ReminderTaskFormatter.formatUrgency(entry.jinji), normalizeTaskText(entry.nodeText),
					entry.file.getName() });
			tableRows.add(item);
		}
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_TIMELINE_TODAY, occurrences.size());
		statusLabel.setText("\u5f53\u65e5\u4efb\u52a1: " + occurrences.size());
	}

	private void openSelectedEntry() {
		final int row = table.getSelectedRow();
		if (row < 0 || row >= tableRows.size()) {
			return;
		}
		final TimelineOccurrence item = (TimelineOccurrence) tableRows.get(row);
		ReminderTabNavigation.openEntry(item.entry);
	}

	private static String normalizeTaskText(final String text) {
		if (text == null) {
			return "";
		}
		return HtmlUtils.removeHtmlTagsFromString(text).replaceAll("\\s+", " ").trim();
	}
}
