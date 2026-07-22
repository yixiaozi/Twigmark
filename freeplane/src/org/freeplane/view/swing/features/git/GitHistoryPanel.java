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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import org.freeplane.core.util.LogUtils;

class GitHistoryPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final String LOG_DATE_FORMAT = "--date=format:%Y-%m-%d %H:%M:%S";
	private static final int DETAIL_MIN_HEIGHT = 80;
	private static final int DETAIL_PREF_HEIGHT = 140;

	private final JTable historyTable = new JTable(new HistoryTableModel());
	private final JTextArea detailArea = new JTextArea();
	private final List<HistoryEntry> entries = new ArrayList<HistoryEntry>();
	private final JSplitPane split;
	private File repoDir;

	GitHistoryPanel() {
		super(new BorderLayout(4, 4));
		historyTable.setRowHeight(22);
		historyTable.setShowGrid(true);
		historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		historyTable.getTableHeader().setReorderingAllowed(false);
		historyTable.getColumnModel().getColumn(0).setPreferredWidth(130);
		historyTable.getColumnModel().getColumn(0).setMaxWidth(150);
		historyTable.getColumnModel().getColumn(1).setPreferredWidth(220);
		historyTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(final ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					showSelectedCommit();
				}
			}
		});
		historyTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() >= 2) {
					showSelectedCommit();
				}
			}
		});

		detailArea.setEditable(false);
		detailArea.setLineWrap(true);
		detailArea.setWrapStyleWord(true);
		detailArea.setFont(DocearUiTheme.font(12f));
		detailArea.setBackground(DocearUiTheme.SURFACE);
		detailArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		detailArea.setText("选择一条提交以查看描述");
		detailArea.setForeground(DocearUiTheme.TEXT_MUTED);

		final JScrollPane detailScroll = new JScrollPane(detailArea);
		DocearUiTheme.styleScrollPane(detailScroll);
		detailScroll.setPreferredSize(new Dimension(100, DETAIL_PREF_HEIGHT));
		detailScroll.setMinimumSize(new Dimension(50, DETAIL_MIN_HEIGHT));

		final JPanel detailPanel = new JPanel(new BorderLayout(0, 2));
		detailPanel.setOpaque(false);
		final JLabel detailTitle = new JLabel("提交描述");
		detailTitle.setFont(DocearUiTheme.font(11f));
		detailTitle.setForeground(DocearUiTheme.TEXT_MUTED);
		detailTitle.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 4));
		detailPanel.add(detailTitle, BorderLayout.NORTH);
		detailPanel.add(detailScroll, BorderLayout.CENTER);

		final JScrollPane tableScroll = new JScrollPane(historyTable);
		DocearUiTheme.styleScrollPane(tableScroll);

		split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		split.setResizeWeight(0.72);
		split.setContinuousLayout(true);
		split.setOneTouchExpandable(true);
		split.setDividerSize(6);
		split.setTopComponent(tableScroll);
		split.setBottomComponent(detailPanel);
		add(split, BorderLayout.CENTER);
	}

	void refresh(final File repository) {
		repoDir = repository;
		detailArea.setForeground(DocearUiTheme.TEXT_MUTED);
		detailArea.setText("选择一条提交以查看描述");
		new Thread(new Runnable() {
			public void run() {
				final List<HistoryEntry> loaded = loadHistory(repository);
				SwingUtilities.invokeLater(new Runnable() {
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
		detailArea.setForeground(DocearUiTheme.TEXT_MUTED);
		detailArea.setText("正在加载...");
		final String hash = entry.hash;
		new Thread(new Runnable() {
			public void run() {
				final GitCommand.Result gitResult = GitCommand.run(repoDir, "show", LOG_DATE_FORMAT, "--no-patch",
				    "--pretty=format:%s%n%n%b", hash);
				final StringBuilder text = new StringBuilder();
				for (int i = 0; i < gitResult.output.size(); i++) {
					if (i > 0) {
						text.append('\n');
					}
					text.append(gitResult.output.get(i));
				}
				String body = text.toString().trim();
				if (body.length() == 0 && gitResult.errors.size() > 0) {
					body = gitResult.errorText();
				}
				if (body.length() == 0) {
					body = entry.summary + "\n\n（无详细描述）";
				}
				final String finalText = body;
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						detailArea.setForeground(DocearUiTheme.TEXT);
						detailArea.setText(finalText);
						detailArea.setCaretPosition(0);
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
