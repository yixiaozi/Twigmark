package org.freeplane.view.swing.features.git;

import org.freeplane.core.ui.theme.DocearUiTheme;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.freeplane.core.util.LogUtils;

class GitHistoryPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final String LOG_DATE_FORMAT = "--date=format:%Y-%m-%d %H:%M:%S";
	private static final int DETAIL_HEIGHT = 56;
	/** Set true to show commit detail panel below history table. */
	private static final boolean SHOW_COMMIT_DETAIL = false;

	private final JTable historyTable = new JTable(new HistoryTableModel());
	private final JTextArea detailArea = new JTextArea();
	private final List<HistoryEntry> entries = new ArrayList<HistoryEntry>();
	private File repoDir;

	GitHistoryPanel() {
		super(new BorderLayout(4, 4));
		historyTable.setRowHeight(22);
		historyTable.setShowGrid(true);
		historyTable.getTableHeader().setReorderingAllowed(false);
		historyTable.getColumnModel().getColumn(0).setPreferredWidth(130);
		historyTable.getColumnModel().getColumn(0).setMaxWidth(150);
		historyTable.getColumnModel().getColumn(1).setPreferredWidth(220);
		historyTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				if (SHOW_COMMIT_DETAIL) {
					showSelectedCommit();
				}
			}
		});

		detailArea.setEditable(false);
		detailArea.setLineWrap(true);
		detailArea.setWrapStyleWord(true);
		detailArea.setRows(2);
		detailArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		final JScrollPane detailScroll = new JScrollPane(detailArea);
		detailScroll.setPreferredSize(new Dimension(100, DETAIL_HEIGHT));
		detailScroll.setMinimumSize(new Dimension(50, DETAIL_HEIGHT));
		detailScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, DETAIL_HEIGHT + 12));
		detailScroll.setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(), "提交详情"));

		if (SHOW_COMMIT_DETAIL) {
			final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			split.setResizeWeight(1.0);
			split.setOneTouchExpandable(true);
			split.setTopComponent(new JScrollPane(historyTable));
			split.setBottomComponent(detailScroll);
			split.setDividerSize(5);
			add(split, BorderLayout.CENTER);
		}
		else {
			add(new JScrollPane(historyTable), BorderLayout.CENTER);
		}
	}

	void refresh(final File repository) {
		repoDir = repository;
		detailArea.setText("");
		new Thread(new Runnable() {
			@Override
			public void run() {
				final List<HistoryEntry> loaded = loadHistory(repository);
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						entries.clear();
						entries.addAll(loaded);
						((HistoryTableModel) historyTable.getModel()).fireTableDataChanged();
					}
				});
			}
		}).start();
	}

	private List<HistoryEntry> loadHistory(final File repository) {
		final List<HistoryEntry> result = new ArrayList<HistoryEntry>();
		if (repository == null) {
			return result;
		}
		final GitCommand.Result gitResult = GitCommand.run(repository, "log", LOG_DATE_FORMAT, "--max-count=80",
		    "--pretty=format:%H|%ad|%s");
		for (int i = 0; i < gitResult.output.size(); i++) {
			final String line = gitResult.output.get(i);
			final String[] parts = line.split("\\|", 3);
			if (parts.length >= 3) {
				result.add(new HistoryEntry(parts[0], GitCommand.formatGitDate(parts[1]), parts[2]));
			}
		}
		if (gitResult.exitCode != 0 && gitResult.errors.size() > 0) {
			LogUtils.warn("Git log failed: " + gitResult.errorText());
		}
		return result;
	}

	private void showSelectedCommit() {
		final int row = historyTable.getSelectedRow();
		if (row < 0 || row >= entries.size() || repoDir == null) {
			return;
		}
		final HistoryEntry entry = entries.get(row);
		detailArea.setText("正在加载...");
		final String hash = entry.hash;
		new Thread(new Runnable() {
			@Override
			public void run() {
				final GitCommand.Result gitResult = GitCommand.run(repoDir, "show", LOG_DATE_FORMAT, "--no-patch",
				    "--pretty=format:日期: %ad%n%n%s%n%n%b", hash);
				final StringBuilder text = new StringBuilder();
				for (int i = 0; i < gitResult.output.size(); i++) {
					if (i > 0) {
						text.append('\n');
					}
					text.append(gitResult.output.get(i));
				}
				if (text.length() == 0 && gitResult.errors.size() > 0) {
					text.append(gitResult.errorText());
				}
				final String finalText = text.toString();
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						detailArea.setText(finalText);
					}
				});
			}
		}).start();
	}

	private class HistoryTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private final String[] columns = { "日期", "摘要" };

		public int getRowCount() {
			return entries.size();
		}

		public int getColumnCount() {
			return columns.length;
		}

		public String getColumnName(final int column) {
			return columns[column];
		}

		public Object getValueAt(final int rowIndex, final int columnIndex) {
			final HistoryEntry entry = entries.get(rowIndex);
			if (columnIndex == 0) {
				return entry.date;
			}
			return entry.summary;
		}
	}

	private static final class HistoryEntry {
		private final String hash;
		private final String date;
		private final String summary;

		HistoryEntry(final String hash, final String date, final String summary) {
			this.hash = hash;
			this.date = date;
			this.summary = summary;
		}
	}
}
