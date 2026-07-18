package org.freeplane.view.swing.features.git;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.DefaultCellEditor;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;

public class GitTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private final JLabel statusLabel = new JLabel("就绪");
	private final JButton refreshButton = DocearUiTheme.softButton("刷新");
	private final JButton pullButton = DocearUiTheme.softButton("拉取");
	private final JButton pushButton = DocearUiTheme.softButton("推送");
	private final JButton commitButton = DocearUiTheme.primaryButton("提交");
	private final JCheckBox selectAllCheckBox = new JCheckBox("全选", true);
	private final JTextField summaryField = new JTextField();
	private final JTextArea descriptionArea = new JTextArea(2, 20);
	private final JTable changesTable = new JTable(new ChangesTableModel());
	private final GitHistoryPanel historyPanel = new GitHistoryPanel();
	private final List<GitFileChange> changes = new ArrayList<GitFileChange>();
	private File repoDir;
	private GitSyncStatus lastSyncStatus;
	private Timer syncTimer;
	private Timer changesRefreshTimer;
	private volatile boolean syncCheckRunning;
	private volatile boolean changesRefreshRunning;
	private volatile boolean remoteActionRunning;
	private volatile boolean autoSyncRunning;
	private int secondsUntilAutoRefresh;

	private static final int CHANGES_REFRESH_INTERVAL_MS = 60000;

	private static final String LABEL_PULL = "拉取";
	private static final String LABEL_PUSH = "推送";
	private static final String LABEL_REFRESH = "刷新";

	public GitTabPanel() {
		super(new BorderLayout(8, 8));
		DocearUiTheme.styleCanvas(this);
		setBorder(DocearUiTheme.pageBorder());
		buildUi();
		wireEvents();
		startSyncTimer();
		startChangesRefreshTimer();
		refreshLocalChanges(false);
	}

	private void buildUi() {
		final JPanel toolbar = new JPanel(new BorderLayout(4, 0));
		toolbar.setOpaque(true);
		toolbar.setBackground(DocearUiTheme.SURFACE);
		toolbar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(DocearUiTheme.HAIRLINE),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		statusLabel.setFont(DocearUiTheme.font(12f));
		statusLabel.setForeground(DocearUiTheme.TEXT_MUTED);
		toolbar.add(statusLabel, BorderLayout.CENTER);

		final JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		actionPanel.setOpaque(false);
		actionPanel.add(pullButton);
		actionPanel.add(pushButton);
		actionPanel.add(refreshButton);
		toolbar.add(actionPanel, BorderLayout.EAST);
		add(toolbar, BorderLayout.NORTH);

		final JTabbedPane tabs = new JTabbedPane();
		DocearUiTheme.styleTabbedPane(tabs);
		tabs.addTab("更改", buildChangesTab());
		tabs.addTab("历史", historyPanel);
		tabs.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(final ChangeEvent e) {
				if (tabs.getSelectedIndex() == 1) {
					historyPanel.refresh(repoDir);
				}
			}
		});
		add(tabs, BorderLayout.CENTER);
	}

	private void startSyncTimer() {
		final int intervalMs = GitConfig.getSyncIntervalSeconds() * 1000;
		syncTimer = new Timer(intervalMs, new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				checkRemoteSync(false);
			}
		});
		syncTimer.setInitialDelay(intervalMs);
		syncTimer.start();
	}

	private void startChangesRefreshTimer() {
		secondsUntilAutoRefresh = CHANGES_REFRESH_INTERVAL_MS / 1000;
		updateRefreshButtonCountdown();
		changesRefreshTimer = new Timer(1000, new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				secondsUntilAutoRefresh--;
				if (secondsUntilAutoRefresh <= 0) {
					secondsUntilAutoRefresh = CHANGES_REFRESH_INTERVAL_MS / 1000;
					refreshLocalChanges(true);
				}
				updateRefreshButtonCountdown();
			}
		});
		changesRefreshTimer.start();
	}

	private void resetRefreshCountdown() {
		secondsUntilAutoRefresh = CHANGES_REFRESH_INTERVAL_MS / 1000;
		updateRefreshButtonCountdown();
	}

	private void updateRefreshButtonCountdown() {
		refreshButton.setText(LABEL_REFRESH + " (" + secondsUntilAutoRefresh + "s)");
		refreshButton.setToolTipText("刷新本地修改列表（" + secondsUntilAutoRefresh + " 秒后自动刷新）");
	}

	private void checkRemoteSync(final boolean triggeredByRefresh) {
		if (syncCheckRunning || remoteActionRunning || autoSyncRunning) {
			return;
		}
		final File repository = repoDir != null ? repoDir : GitConfig.locateRepository();
		if (repository == null) {
			return;
		}
		syncCheckRunning = true;
		if (triggeredByRefresh) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					statusLabel.setText("正在检查远端同步...");
				}
			});
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				final GitSyncStatus status = GitSyncChecker.check(repository);
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						syncCheckRunning = false;
						lastSyncStatus = status;
						updateSyncButtons(status);
						updateStatusWithSync(status);
						maybeAutoSync(status);
					}
				});
			}
		}).start();
	}

	private void updateSyncButtons(final GitSyncStatus status) {
		if (status == null) {
			pullButton.setText(LABEL_PULL);
			pushButton.setText(LABEL_PUSH);
			pullButton.setToolTipText(null);
			pushButton.setToolTipText(null);
			pullButton.setForeground(null);
			pushButton.setForeground(null);
			return;
		}
		pullButton.setText(status.needsPull() ? LABEL_PULL + " \u2193" + status.behind : LABEL_PULL);
		pushButton.setText(status.needsPush() ? LABEL_PUSH + " \u2191" + status.ahead : LABEL_PUSH);
		pullButton.setToolTipText(status.needsPull() ? "远端有 " + status.behind + " 个新提交待拉取" : LABEL_PULL);
		pushButton.setToolTipText(status.needsPush() ? "本地有 " + status.ahead + " 个提交待推送" : LABEL_PUSH);
		pullButton.setForeground(status.needsPull() ? DocearUiTheme.ACCENT_DEEP : DocearUiTheme.TEXT_MUTED);
		pushButton.setForeground(status.needsPush() ? DocearUiTheme.SUCCESS : DocearUiTheme.TEXT_MUTED);
	}

	private void updateStatusWithSync(final GitSyncStatus status) {
		if (status == null || repoDir == null) {
			return;
		}
		final String summary = status.syncSummary();
		final StringBuilder text = new StringBuilder("仓库: ");
		text.append(repoDir.getAbsolutePath());
		if (changes.size() > 0) {
			text.append(" - ").append(changes.size()).append(" 个修改");
		}
		if (summary.length() > 0) {
			if (changes.size() > 0) {
				text.append(", ");
			}
			else {
				text.append(" - ");
			}
			text.append(summary);
		}
		if (summary.length() > 0 || changes.size() > 0) {
			statusLabel.setText(text.toString());
		}
	}

	private void maybeAutoSync(final GitSyncStatus status) {
		if (!GitConfig.isAutoSyncEnabled() || status == null || repoDir == null) {
			return;
		}
		if (!status.fetchOk || !status.hasUpstream || status.inSync() || status.diverged()) {
			return;
		}
		if (GitSyncChecker.hasUncommittedChanges(repoDir)) {
			return;
		}
		if (status.needsPull()) {
			runRemoteAction("拉取", GitCommand.buildPullArgs(repoDir, true), true);
		}
		else if (status.needsPush()) {
			runRemoteAction("推送", new String[] { "push" }, true);
		}
	}

	private JPanel buildChangesTab() {
		final JPanel panel = new JPanel(new BorderLayout(4, 4));

		changesTable.setRowHeight(22);
		changesTable.setShowGrid(true);
		changesTable.getTableHeader().setReorderingAllowed(false);
		changesTable.getColumnModel().getColumn(0).setMaxWidth(36);
		changesTable.getColumnModel().getColumn(0).setMinWidth(36);
		changesTable.getColumnModel().getColumn(1).setMaxWidth(48);
		changesTable.getColumnModel().getColumn(1).setMinWidth(40);
		changesTable.getColumnModel().getColumn(2).setPreferredWidth(160);
		changesTable.getColumnModel().getColumn(3).setMaxWidth(72);
		changesTable.getColumnModel().getColumn(3).setMinWidth(56);
		changesTable.setDefaultRenderer(Object.class, new ChangesCellRenderer());
		setupCheckboxColumn();
		setupChangesContextMenu();

		final JPanel tableHeader = new JPanel(new BorderLayout());
		tableHeader.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
		tableHeader.add(new JLabel("修改的文件"), BorderLayout.WEST);
		tableHeader.add(selectAllCheckBox, BorderLayout.EAST);

		final JPanel tablePanel = new JPanel(new BorderLayout(0, 2));
		tablePanel.add(tableHeader, BorderLayout.NORTH);
		tablePanel.add(new JScrollPane(changesTable), BorderLayout.CENTER);

		final JPanel commitPanel = new JPanel(new BorderLayout(4, 4));
		commitPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		final JPanel summaryPanel = new JPanel(new BorderLayout(4, 0));
		summaryPanel.add(new JLabel("Summary"), BorderLayout.WEST);
		summaryField.setPreferredSize(new Dimension(200, 24));
		summaryPanel.add(summaryField, BorderLayout.CENTER);
		commitPanel.add(summaryPanel, BorderLayout.NORTH);

		final JPanel descriptionPanel = new JPanel(new BorderLayout(4, 0));
		descriptionPanel.add(new JLabel("Description"), BorderLayout.NORTH);
		descriptionArea.setLineWrap(true);
		descriptionArea.setWrapStyleWord(true);
		descriptionArea.setBorder(BorderFactory.createCompoundBorder(
				DocearUiTheme.hairlineBorder(),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		descriptionArea.setFont(DocearUiTheme.font(12f));
		descriptionArea.setBackground(DocearUiTheme.SURFACE);
		final JScrollPane descScroll = new JScrollPane(descriptionArea);
		DocearUiTheme.styleScrollPane(descScroll);
		descScroll.setPreferredSize(new Dimension(100, 52));
		descScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
		descriptionPanel.add(descScroll, BorderLayout.CENTER);
		commitPanel.add(descriptionPanel, BorderLayout.CENTER);

		final JPanel commitButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		commitButtons.add(commitButton);
		commitPanel.add(commitButtons, BorderLayout.SOUTH);

		panel.add(tablePanel, BorderLayout.CENTER);
		panel.add(commitPanel, BorderLayout.SOUTH);
		return panel;
	}

	private void wireEvents() {
		refreshButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				refreshLocalChanges(false);
			}
		});
		pullButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				runRemoteAction("拉取", GitCommand.buildPullArgs(repoDir, false), false);
			}
		});
		pushButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				runRemoteAction("推送", new String[] { "push" }, false);
			}
		});
		commitButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				commitChanges();
			}
		});
		selectAllCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				final boolean selected = selectAllCheckBox.isSelected();
				for (int i = 0; i < changes.size(); i++) {
					changes.get(i).setSelected(selected);
				}
				((ChangesTableModel) changesTable.getModel()).fireTableDataChanged();
				updateCommitButtonState();
			}
		});
	}

	private void setupCheckboxColumn() {
		final JCheckBox checkBox = new JCheckBox();
		checkBox.setHorizontalAlignment(SwingConstants.CENTER);
		changesTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(checkBox));
		changesTable.getColumnModel().getColumn(0).setCellRenderer(changesTable.getDefaultRenderer(Boolean.class));
		changesTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent e) {
				final int row = changesTable.rowAtPoint(e.getPoint());
				final int col = changesTable.columnAtPoint(e.getPoint());
				if (row >= 0 && col == 0 && !e.isPopupTrigger()) {
					final GitFileChange change = changes.get(row);
					change.setSelected(!change.isSelected());
					((ChangesTableModel) changesTable.getModel()).fireTableCellUpdated(row, 0);
					updateSelectAllState();
					updateCommitButtonState();
				}
			}
		});
	}

	private void setupChangesContextMenu() {
		final JPopupMenu popupMenu = new JPopupMenu();
		final JMenuItem undoItem = new JMenuItem("撤销修改");
		final JMenuItem openFileItem = new JMenuItem("打开文件");
		final JMenuItem openFolderItem = new JMenuItem("打开所在文件夹");
		popupMenu.add(undoItem);
		popupMenu.add(openFileItem);
		popupMenu.add(openFolderItem);

		undoItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				undoSelectedChange();
			}
		});
		openFileItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				openSelectedFile(false);
			}
		});
		openFolderItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				openSelectedFile(true);
			}
		});

		changesTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent e) {
				showChangesPopup(e, popupMenu, undoItem, openFileItem, openFolderItem);
			}

			@Override
			public void mouseReleased(final MouseEvent e) {
				showChangesPopup(e, popupMenu, undoItem, openFileItem, openFolderItem);
			}
		});
	}

	private void showChangesPopup(final MouseEvent e, final JPopupMenu popupMenu, final JMenuItem undoItem,
	    final JMenuItem openFileItem, final JMenuItem openFolderItem) {
		if (!e.isPopupTrigger()) {
			return;
		}
		final int row = changesTable.rowAtPoint(e.getPoint());
		if (row < 0 || row >= changes.size()) {
			return;
		}
		changesTable.setRowSelectionInterval(row, row);
		final GitFileChange change = changes.get(row);
		final File file = resolveChangeFile(change);
		undoItem.setEnabled(change.getStatus() != GitFileChange.Status.DELETED || file != null);
		openFileItem.setEnabled(file != null && file.isFile());
		openFolderItem.setEnabled(resolveChangeFolder(change) != null);
		popupMenu.show(e.getComponent(), e.getX(), e.getY());
	}

	private File resolveChangeFile(final GitFileChange change) {
		if (repoDir == null || change == null) {
			return null;
		}
		return new File(repoDir, change.getRelativePath().replace('/', File.separatorChar));
	}

	private File resolveChangeFolder(final GitFileChange change) {
		final File file = resolveChangeFile(change);
		if (file == null) {
			return null;
		}
		if (file.isDirectory()) {
			return file;
		}
		if (file.isFile()) {
			return file.getParentFile();
		}
		final File parent = file.getParentFile();
		return parent != null && parent.isDirectory() ? parent : null;
	}

	private GitFileChange getSelectedChange() {
		final int row = changesTable.getSelectedRow();
		if (row < 0 || row >= changes.size()) {
			return null;
		}
		return changes.get(row);
	}

	private void undoSelectedChange() {
		final GitFileChange change = getSelectedChange();
		if (change == null || repoDir == null) {
			return;
		}
		final String path = change.getRelativePath();
		final int confirm = JOptionPane.showConfirmDialog(this,
		    "确定撤销对「" + change.getDisplayName() + "」的修改吗？", "撤销修改", JOptionPane.YES_NO_OPTION);
		if (confirm != JOptionPane.YES_OPTION) {
			return;
		}
		statusLabel.setText("正在撤销...");
		new Thread(new Runnable() {
			@Override
			public void run() {
				GitCommand.Result result;
				if (change.getStatus() == GitFileChange.Status.ADDED) {
					result = GitCommand.run(repoDir, "clean", "-fd", "--", path);
				} else if (change.getStatus() == GitFileChange.Status.DELETED) {
					result = GitCommand.run(repoDir, "restore", "--source=HEAD", "--", path);
				} else {
					result = GitCommand.run(repoDir, "restore", "--", path);
				}
				final GitCommand.Result finalResult = result;
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						if (finalResult.exitCode == 0) {
							statusLabel.setText("已撤销: " + change.getDisplayName());
							refreshLocalChanges(false);
						} else {
							statusLabel.setText("撤销失败: " + finalResult.errorText());
						}
					}
				});
			}
		}).start();
	}

	private void openSelectedFile(final boolean openFolderOnly) {
		final GitFileChange change = getSelectedChange();
		if (change == null) {
			return;
		}
		final File file = resolveChangeFile(change);
		if (openFolderOnly) {
			openContainingFolder(file);
		} else {
			openFileWithSystemApp(file);
		}
	}

	private void openFileWithSystemApp(final File file) {
		if (file == null || !file.isFile()) {
			statusLabel.setText("文件不存在");
			return;
		}
		try {
			if (java.awt.Desktop.isDesktopSupported()) {
				java.awt.Desktop.getDesktop().open(file);
			}
		}
		catch (Exception e) {
			LogUtils.warn("无法打开文件: " + e.getMessage(), e);
			statusLabel.setText("无法打开文件");
		}
	}

	private void openContainingFolder(final File file) {
		final File folder = file != null && file.isDirectory() ? file : file != null ? file.getParentFile() : null;
		if (folder == null || !folder.isDirectory()) {
			statusLabel.setText("文件夹不存在");
			return;
		}
		try {
			if (java.awt.Desktop.isDesktopSupported()) {
				java.awt.Desktop.getDesktop().open(folder);
			}
		}
		catch (Exception e) {
			try {
				Runtime.getRuntime().exec("explorer.exe \"" + folder.getAbsolutePath() + "\"");
			}
			catch (Exception ex) {
				LogUtils.warn("无法打开文件夹: " + ex.getMessage(), ex);
				statusLabel.setText("无法打开文件夹");
			}
		}
	}

	private void updateSelectAllState() {
		boolean allSelected = !changes.isEmpty();
		for (int i = 0; i < changes.size(); i++) {
			if (!changes.get(i).isSelected()) {
				allSelected = false;
				break;
			}
		}
		selectAllCheckBox.setSelected(allSelected);
	}

	private void updateCommitButtonState() {
		int selectedCount = 0;
		for (int i = 0; i < changes.size(); i++) {
			if (changes.get(i).isSelected()) {
				selectedCount++;
			}
		}
		commitButton.setEnabled(repoDir != null && selectedCount > 0);
	}

	private void refreshLocalChanges(final boolean silent) {
		if (changesRefreshRunning) {
			return;
		}
		changesRefreshRunning = true;
		if (!silent) {
			resetRefreshCountdown();
			statusLabel.setText("正在扫描...");
			commitButton.setEnabled(false);
		}

		final Map<String, Boolean> previousSelection = captureSelectionState();
		new Thread(new Runnable() {
			@Override
			public void run() {
				final File repository = GitConfig.locateRepository();
				final List<GitFileChange> loaded = loadGitChanges(repository);
				restoreSelectionState(loaded, previousSelection);
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						changesRefreshRunning = false;
						final boolean unchanged = repository != null && repository.equals(repoDir)
						    && sameChanges(changes, loaded);
						repoDir = repository;
						if (!unchanged) {
							changes.clear();
							changes.addAll(loaded);
							((ChangesTableModel) changesTable.getModel()).fireTableDataChanged();
							updateSelectAllState();
						}
						SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_GIT, changes.size());
						if (!silent) {
							if (repository != null) {
								statusLabel.setText("仓库: " + repository.getAbsolutePath() + " - 发现 " + loaded.size() + " 个修改");
							} else {
								statusLabel.setText("未找到 Git 仓库，请在用户配置目录的 git.local.properties 中设置 git.repo.path=<仓库路径>");
							}
							if (unchanged) {
								selectAllCheckBox.setSelected(!loaded.isEmpty());
							}
						} else if (!unchanged && lastSyncStatus != null) {
							updateStatusWithSync(lastSyncStatus);
						}
						updateCommitButtonState();
					}
				});
			}
		}).start();
	}

	private Map<String, Boolean> captureSelectionState() {
		final Map<String, Boolean> selection = new HashMap<String, Boolean>();
		for (int i = 0; i < changes.size(); i++) {
			final GitFileChange change = changes.get(i);
			selection.put(change.getRelativePath(), Boolean.valueOf(change.isSelected()));
		}
		return selection;
	}

	private static void restoreSelectionState(final List<GitFileChange> loaded, final Map<String, Boolean> previousSelection) {
		for (int i = 0; i < loaded.size(); i++) {
			final GitFileChange change = loaded.get(i);
			final Boolean selected = previousSelection.get(change.getRelativePath());
			if (selected != null) {
				change.setSelected(selected.booleanValue());
			}
		}
	}

	private static boolean sameChanges(final List<GitFileChange> current, final List<GitFileChange> loaded) {
		if (current.size() != loaded.size()) {
			return false;
		}
		for (int i = 0; i < current.size(); i++) {
			final GitFileChange a = current.get(i);
			final GitFileChange b = loaded.get(i);
			if (!a.getRelativePath().equals(b.getRelativePath()) || a.getStatus() != b.getStatus()) {
				return false;
			}
		}
		return true;
	}

	private List<GitFileChange> loadGitChanges(final File repository) {
		final List<GitFileChange> result = new ArrayList<GitFileChange>();
		if (repository == null) {
			return result;
		}
		final List<GitStatusParser.Entry> entries = GitStatusParser.parsePorcelain(repository);
		for (int i = 0; i < entries.size(); i++) {
			final GitStatusParser.Entry entry = entries.get(i);
			final String status = entry.status;
			final String relativePath = entry.path;
			if (relativePath.length() == 0) {
				continue;
			}
			GitFileChange.Status changeStatus;
			if (status.charAt(0) == 'A' || status.charAt(1) == 'A' || (status.charAt(0) == '?' && status.charAt(1) == '?')) {
				changeStatus = GitFileChange.Status.ADDED;
			} else if (status.charAt(0) == 'D' || status.charAt(1) == 'D') {
				changeStatus = GitFileChange.Status.DELETED;
			} else {
				changeStatus = GitFileChange.Status.MODIFIED;
			}
			final long size = changeStatus == GitFileChange.Status.DELETED
			    ? -1L : GitCommand.resolveWorkingTreeSize(repository, relativePath);
			result.add(new GitFileChange(relativePath, changeStatus, size));
		}
		return result;
	}

	private void commitChanges() {
		final String summary = summaryField.getText().trim();
		if (summary.isEmpty()) {
			statusLabel.setText("请输入提交摘要");
			return;
		}
		if (repoDir == null) {
			statusLabel.setText("未找到 Git 仓库");
			return;
		}
		final List<String> selectedPaths = new ArrayList<String>();
		for (int i = 0; i < changes.size(); i++) {
			final GitFileChange change = changes.get(i);
			if (change.isSelected()) {
				selectedPaths.add(change.getRelativePath());
			}
		}
		if (selectedPaths.isEmpty()) {
			statusLabel.setText("请至少选择一个文件");
			return;
		}

		final String description = descriptionArea.getText().trim();
		final String commitMessage = summary + (description.isEmpty() ? "" : "\n\n" + description);

		statusLabel.setText("正在提交...");
		commitButton.setEnabled(false);

		new Thread(new Runnable() {
			@Override
			public void run() {
				boolean success = false;
				String failureMessage = "提交失败";
				try {
					GitCommand.Result resetResult = GitCommand.run(repoDir, "reset", "HEAD");
					if (resetResult.exitCode != 0) {
						failureMessage = "取消暂存失败: " + resetResult.errorText();
					} else {
						final String[] addArgs = new String[selectedPaths.size() + 2];
						addArgs[0] = "add";
						addArgs[1] = "--";
						for (int i = 0; i < selectedPaths.size(); i++) {
							addArgs[i + 2] = selectedPaths.get(i);
						}
						final GitCommand.Result addResult = GitCommand.run(repoDir, addArgs);
						if (addResult.exitCode != 0) {
							failureMessage = "git add 失败: " + addResult.errorText();
						} else {
							final GitCommand.Result commitResult = GitCommand.run(repoDir, "commit", "-m", commitMessage);
							success = commitResult.exitCode == 0;
							if (!success) {
								failureMessage = commitResult.errors.isEmpty() ? "没有可提交的更改" : commitResult.errorText();
							}
						}
					}
				}
				catch (Exception e) {
					LogUtils.warn("Git commit failed: " + e.getMessage(), e);
					failureMessage = "Git 命令执行失败: " + e.getMessage();
				}

				final boolean finalSuccess = success;
				final String finalFailureMessage = failureMessage;
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						if (finalSuccess) {
							statusLabel.setText("提交成功");
							summaryField.setText("");
							descriptionArea.setText("");
							GitPostCommitScriptRunner.scheduleAfterSuccessfulCommit();
							refreshLocalChanges(false);
						} else {
							statusLabel.setText(finalFailureMessage);
							updateCommitButtonState();
						}
					}
				});
			}
		}).start();
	}

	private void setStatusMessage(final String message) {
		statusLabel.setText(message);
		statusLabel.setToolTipText(message != null && message.length() > 0 ? message : null);
	}

	private void runRemoteAction(final String actionLabel, final String[] gitArgs, final boolean automatic) {
		if (repoDir == null) {
			setStatusMessage("未找到 Git 仓库");
			return;
		}
		if (remoteActionRunning) {
			return;
		}
		remoteActionRunning = true;
		if (automatic) {
			autoSyncRunning = true;
		}
		setStatusMessage((automatic ? "自动" : "正在") + actionLabel + "...");
		pullButton.setEnabled(false);
		pushButton.setEnabled(false);
		refreshButton.setEnabled(false);

		new Thread(new Runnable() {
			@Override
			public void run() {
				final GitCommand.Result result = GitCommand.runRemote(repoDir, gitArgs);
				final boolean success = result.exitCode == 0;
				final String message;
				if (success) {
					final String detail = result.messageText();
					message = (automatic ? "自动" : "") + actionLabel + "成功"
					    + (detail.length() > 0 ? ": " + detail : "");
				} else {
					message = result.failureMessage(actionLabel);
				}
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						remoteActionRunning = false;
						autoSyncRunning = false;
						setStatusMessage(message);
						pullButton.setEnabled(true);
						pushButton.setEnabled(true);
						refreshButton.setEnabled(true);
						if (success) {
							refreshLocalChanges(false);
							historyPanel.refresh(repoDir);
						} else {
							checkRemoteSync(false);
						}
					}
				});
			}
		}).start();
	}

	private class ChangesTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private final String[] columns = { "", "状态", "文件名", "大小" };

		public int getRowCount() {
			return changes.size();
		}

		public int getColumnCount() {
			return columns.length;
		}

		public String getColumnName(final int column) {
			return columns[column];
		}

		public Class<?> getColumnClass(final int columnIndex) {
			if (columnIndex == 0) {
				return Boolean.class;
			}
			return String.class;
		}

		public boolean isCellEditable(final int rowIndex, final int columnIndex) {
			return columnIndex == 0;
		}

		public Object getValueAt(final int rowIndex, final int columnIndex) {
			final GitFileChange change = changes.get(rowIndex);
			switch (columnIndex) {
			case 0:
				return Boolean.valueOf(change.isSelected());
			case 1:
				return GitCommand.statusLabel(change.getStatus());
			case 2:
				return change.getDisplayName();
			default:
				return change.getFormattedSize();
			}
		}

		public void setValueAt(final Object value, final int rowIndex, final int columnIndex) {
			if (columnIndex == 0 && value instanceof Boolean) {
				changes.get(rowIndex).setSelected(((Boolean) value).booleanValue());
				updateSelectAllState();
				updateCommitButtonState();
			}
		}
	}

	private class ChangesCellRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		@Override
		public Component getTableCellRendererComponent(final JTable table, final Object value, final boolean isSelected,
		    final boolean hasFocus, final int row, final int column) {
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			if (column == 1 && row < changes.size()) {
				switch (changes.get(row).getStatus()) {
				case ADDED:
					setForeground(DocearUiTheme.SUCCESS);
					break;
				case DELETED:
					setForeground(DocearUiTheme.DANGER);
					break;
				default:
					setForeground(DocearUiTheme.WARNING);
					break;
				}
			} else if (!isSelected) {
				setForeground(table.getForeground());
			}
			if (column == 2) {
				setToolTipText(changes.get(row).getRelativePath());
			}
			return this;
		}
	}
}
