package org.freeplane.features.usagestats;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;

/**
 * Full-size usage report shown in the main mind map viewport.
 */
public class UsageStatsReportPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

	private final JLabel headerSummary = new JLabel(" ");
	private final DefaultTableModel summaryTableModel = new DefaultTableModel(
	        new String[] { "\u5bfc\u56fe", "\u6253\u5f00\u6b21\u6570", "\u603b\u65f6\u957f", "\u6709\u6548\u65f6\u957f", "\u6700\u8fd1\u4f7f\u7528" }, 0) {
		private static final long serialVersionUID = 1L;

		@Override
		public boolean isCellEditable(final int row, final int column) {
			return false;
		}
	};
	private final JTable summaryTable = new JTable(summaryTableModel);
	private final List<String> summaryRowMapPaths = new ArrayList<String>();
	private final JLabel detailTitle = new JLabel("\u5728\u4e0b\u65b9\u8868\u683c\u4e2d\u9009\u62e9\u5bfc\u56fe\u67e5\u770b\u6bcf\u6b21\u6253\u5f00\u8bb0\u5f55");
	private final DefaultTableModel detailTableModel = new DefaultTableModel(
	        new String[] { "\u5f00\u59cb\u65f6\u95f4", "\u7ed3\u675f\u65f6\u95f4", "\u603b\u65f6\u957f", "\u6709\u6548\u65f6\u957f" }, 0) {
		private static final long serialVersionUID = 1L;

		@Override
		public boolean isCellEditable(final int row, final int column) {
			return false;
		}
	};
	private final JTable detailTable = new JTable(detailTableModel);
	private final JButton refreshButton = new JButton("\u5237\u65b0");
	private final JButton closeButton = new JButton("\u8fd4\u56de\u5bfc\u56fe");
	private Runnable onClose;
	private String displayedMapPath = "";
	private int refreshGeneration = 0;
	private int detailLoadGeneration = 0;

	public UsageStatsReportPanel() {
		super(new BorderLayout(4, 4));
		setOpaque(true);
		buildUi();
		refreshButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				refresh();
			}
		});
		closeButton.setToolTipText("\u5173\u95ed\u6d3b\u52a8\u62a5\u8868\uff0c\u56de\u5230\u601d\u7ef4\u5bfc\u56fe");
		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (onClose != null) {
					onClose.run();
				}
			}
		});
		summaryTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(final ListSelectionEvent e) {
				if (e.getValueIsAdjusting()) {
					return;
				}
				onSummaryRowSelected();
			}
		});
		headerSummary.setText("\u70b9\u51fb\u5237\u65b0\u52a0\u8f7d\u6d3b\u52a8\u8bb0\u5f55");
	}

	private void buildUi() {
		headerSummary.setFont(headerSummary.getFont().deriveFont(Font.BOLD));
		final JPanel topPanel = new JPanel(new BorderLayout(4, 4));
		topPanel.add(headerSummary, BorderLayout.CENTER);
		final JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		buttonRow.add(refreshButton);
		buttonRow.add(closeButton);
		topPanel.add(buttonRow, BorderLayout.EAST);
		add(topPanel, BorderLayout.NORTH);

		summaryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		final JScrollPane summaryScroll = new JScrollPane(summaryTable);
		detailTitle.setFont(detailTitle.getFont().deriveFont(Font.PLAIN));
		final JPanel detailPanel = new JPanel(new BorderLayout(4, 4));
		detailPanel.add(detailTitle, BorderLayout.NORTH);
		detailPanel.add(new JScrollPane(detailTable), BorderLayout.CENTER);

		final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, summaryScroll, detailPanel);
		split.setResizeWeight(0.45);
		split.setOneTouchExpandable(true);
		add(split, BorderLayout.CENTER);
	}

	public void setOnClose(final Runnable onClose) {
		this.onClose = onClose;
	}

	public void refresh() {
		final int selectedRow = summaryTable.getSelectedRow();
		String selectedPath = null;
		if (selectedRow >= 0 && selectedRow < summaryRowMapPaths.size()) {
			selectedPath = summaryRowMapPaths.get(selectedRow);
		}
		String currentPath = "";
		final MapModel map = Controller.getCurrentController().getMap();
		if (map != null && map.getFile() != null) {
			currentPath = map.getFile().getAbsolutePath();
		}
		final String pathToRestore = selectedPath;
		final String openMapPath = currentPath;
		headerSummary.setText("\u52a0\u8f7d\u4e2d...");
		final int generation = ++refreshGeneration;
		new Thread(new Runnable() {
			public void run() {
				final List<MapUsageSummary> summaries = new ArrayList<MapUsageSummary>(
				        UsageStatsManager.getInstance().summarizeByMap().values());
				Collections.sort(summaries, new Comparator<MapUsageSummary>() {
					public int compare(final MapUsageSummary a, final MapUsageSummary b) {
						final long diff = b.getEffectiveDurationMs() - a.getEffectiveDurationMs();
						if (diff > 0L) {
							return 1;
						}
						if (diff < 0L) {
							return -1;
						}
						return 0;
					}
				});
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						if (generation != refreshGeneration) {
							return;
						}
						applySummaryRefresh(summaries, pathToRestore, openMapPath);
					}
				});
			}
		}).start();
	}

	private void applySummaryRefresh(final List<MapUsageSummary> summaries, final String selectedPath,
	        final String currentPath) {
		summaryRowMapPaths.clear();
		while (summaryTableModel.getRowCount() > 0) {
			summaryTableModel.removeRow(0);
		}

		int rowToSelect = -1;
		for (final MapUsageSummary summary : summaries) {
			if (summary.getSessionCount() <= 0) {
				continue;
			}
			final String lastUsed = summary.getLastEndTime() > 0L
			        ? DATE_TIME_FORMAT.format(new Date(summary.getLastEndTime()))
			        : "-";
			summaryRowMapPaths.add(summary.getMapPath());
			summaryTableModel.addRow(new Object[] {
			        summary.getDisplayName(),
			        Integer.valueOf(summary.getSessionCount()),
			        UsageStatsManager.formatDuration(summary.getTotalDurationMs()),
			        UsageStatsManager.formatDuration(summary.getEffectiveDurationMs()),
			        lastUsed
			});
			final int rowIndex = summaryTableModel.getRowCount() - 1;
			if (selectedPath != null && summary.matchesPath(selectedPath)) {
				rowToSelect = rowIndex;
			}
			else if (rowToSelect < 0 && currentPath != null && !currentPath.isEmpty()
			        && summary.matchesPath(currentPath)) {
				rowToSelect = rowIndex;
			}
		}

		if (rowToSelect >= 0) {
			summaryTable.setRowSelectionInterval(rowToSelect, rowToSelect);
			onSummaryRowSelected();
		}
		else if (summaryTableModel.getRowCount() > 0) {
			summaryTable.setRowSelectionInterval(0, 0);
			onSummaryRowSelected();
		}
		else {
			displayedMapPath = "";
			headerSummary.setText("\u6682\u65e0\u6d3b\u52a8\u8bb0\u5f55");
			clearDetailTable();
		}
	}

	private void onSummaryRowSelected() {
		final int row = summaryTable.getSelectedRow();
		if (row < 0 || row >= summaryRowMapPaths.size()) {
			return;
		}
		final String mapPath = summaryRowMapPaths.get(row);
		displayedMapPath = mapPath;
		detailTitle.setText("\u52a0\u8f7d\u4e2d...");
		final int generation = ++detailLoadGeneration;
		new Thread(new Runnable() {
			public void run() {
				final MapUsageSummary summary = UsageStatsManager.getInstance().summarizeForMap(mapPath);
				final List<UsageRecord> records = UsageStatsManager.getInstance().loadRecordsForMap(mapPath);
				final List<Object[]> detailRows = new ArrayList<Object[]>();
				for (final UsageRecord record : records) {
					final long effectiveMs = effectiveDuration(record);
					final long totalMs = totalDuration(record);
					final String start = record.getStartTime() > 0L
					        ? DATE_TIME_FORMAT.format(new Date(record.getStartTime()))
					        : "-";
					final String end = record.getEndTime() > 0L
					        ? DATE_TIME_FORMAT.format(new Date(record.getEndTime()))
					        : "-";
					detailRows.add(new Object[] {
					        start,
					        end,
					        UsageStatsManager.formatDuration(totalMs),
					        UsageStatsManager.formatDuration(effectiveMs)
					});
				}
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						if (generation != detailLoadGeneration || !mapPath.equals(displayedMapPath)) {
							return;
						}
						headerSummary.setText("<html><b>" + summary.getDisplayName() + "</b> \u2014 "
						        + "\u6253\u5f00 " + summary.getSessionCount() + " \u6b21\uFF0C"
						        + "\u6709\u6548 "
						        + UsageStatsManager.formatDuration(summary.getEffectiveDurationMs()) + "</html>");
						detailTitle.setText(summary.getDisplayName()
						        + " \u2014 \u6253\u5f00\u8bb0\u5f55\uff08\u4ece\u65b0\u5230\u65e7\uff09");
						while (detailTableModel.getRowCount() > 0) {
							detailTableModel.removeRow(0);
						}
						for (final Object[] rowData : detailRows) {
							detailTableModel.addRow(rowData);
						}
					}
				});
			}
		}).start();
	}

	private void clearDetailTable() {
		while (detailTableModel.getRowCount() > 0) {
			detailTableModel.removeRow(0);
		}
		detailTitle.setText("\u5728\u4e0a\u65b9\u8868\u683c\u4e2d\u9009\u62e9\u5bfc\u56fe\u67e5\u770b\u6bcf\u6b21\u6253\u5f00\u8bb0\u5f55");
	}

	private long effectiveDuration(final UsageRecord record) {
		long effectiveMs = record.getEffectiveDurationMs();
		if (effectiveMs <= 0L && record.getEndTime() > record.getStartTime()) {
			effectiveMs = record.getEndTime() - record.getStartTime() - record.getIdleDurationMs();
		}
		if (effectiveMs <= 0L) {
			effectiveMs = record.getTotalDurationMs();
		}
		return effectiveMs;
	}

	private long totalDuration(final UsageRecord record) {
		long totalMs = record.getTotalDurationMs();
		if (totalMs <= 0L && record.getEndTime() > record.getStartTime()) {
			totalMs = record.getEndTime() - record.getStartTime();
		}
		return totalMs;
	}
}
