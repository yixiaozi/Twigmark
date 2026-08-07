package org.freeplane.view.swing.features.git;

import java.awt.Cursor;
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

import javax.swing.Box;
import javax.swing.BoxLayout;
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
import org.freeplane.core.util.TextUtils;

public class GitTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private final JLabel statusLabel = new JLabel(TextUtils.getText("git.status.ready"));
	private final JLabel statusErrorLabel = new JLabel();
	private final JPanel statusPanel = new JPanel();
	private String lastErrorDetail = "";
	private final JButton refreshButton = DocearUiTheme.softButton(TextUtils.getText("git.refresh"));
	private final JButton pullButton = DocearUiTheme.softButton(TextUtils.getText("git.pull"));
	private final JButton pushButton = DocearUiTheme.softButton(TextUtils.getText("git.push"));
	private final JButton commitButton = DocearUiTheme.primaryButton(TextUtils.getText("git.commit"));
	private final JCheckBox selectAllCheckBox = new JCheckBox(TextUtils.getText("git.select_all"), true);
	private final JTextField summaryField = new JTextField();
	private final JTextArea descriptionArea = new JTextArea(6, 20);
	private static final String PLACEHOLDER_SUMMARY = "Summary";
	private static final String PLACEHOLDER_DESCRIPTION = "Description";
	private boolean summaryShowingHint = true;
	private boolean descriptionShowingHint = true;
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

	private String labelPull() { return TextUtils.getText("git.pull"); }
	private String labelPush() { return TextUtils.getText("git.push"); }
	private String labelRefresh() { return TextUtils.getText("git.refresh"); }

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
		statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 4));
		statusLabel.setFont(DocearUiTheme.font(12f));
		statusLabel.setForeground(DocearUiTheme.TEXT_MUTED);
		statusErrorLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 4));
		statusErrorLabel.setFont(DocearUiTheme.font(11f));
		statusErrorLabel.setForeground(DocearUiTheme.DANGER);
		statusErrorLabel.setVisible(false);
		statusErrorLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
		statusPanel.setOpaque(false);
		statusPanel.add(statusLabel);
		statusPanel.add(statusErrorLabel);
		toolbar.add(statusPanel, BorderLayout.CENTER);

		final JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		actionPanel.setOpaque(false);
		actionPanel.add(pullButton);
		actionPanel.add(pushButton);
		actionPanel.add(refreshButton);
		toolbar.add(actionPanel, BorderLayout.EAST);
		add(toolbar, BorderLayout.NORTH);

		final JTabbedPane tabs = new JTabbedPane();
		DocearUiTheme.styleTabbedPane(tabs);
		tabs.addTab(TextUtils.getText("git.tab.changes"), buildChangesTab());
		tabs.addTab(TextUtils.getText("git.tab.history"), historyPanel);
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
		refreshButton.setText(labelRefresh() + " (" + secondsUntilAutoRefresh + "s)");
		refreshButton.setToolTipText(TextUtils.format("git.tooltip.refresh", Integer.valueOf(secondsUntilAutoRefresh)));
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
					statusLabel.setText(TextUtils.getText("git.status.checking_remote"));
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
			pullButton.setText(labelPull());
			pushButton.setText(labelPush());
			pullButton.setToolTipText(null);
			pushButton.setToolTipText(null);
			pullButton.setForeground(null);
			pushButton.setForeground(null);
			return;
		}
		pullButton.setText(status.needsPull() ? labelPull() + " \u2193" + status.behind : labelPull());
		pushButton.setText(status.needsPush() ? labelPush() + " \u2191" + status.ahead : labelPush());
		pullButton.setToolTipText(status.needsPull() ? TextUtils.format("git.tooltip.needs_pull", Integer.valueOf(status.behind)) : labelPull());
		pushButton.setToolTipText(status.needsPush() ? TextUtils.format("git.tooltip.needs_push", Integer.valueOf(status.ahead)) : labelPush());
		pullButton.setForeground(status.needsPull() ? DocearUiTheme.ACCENT_DEEP : DocearUiTheme.TEXT_MUTED);
		pushButton.setForeground(status.needsPush() ? DocearUiTheme.SUCCESS : DocearUiTheme.TEXT_MUTED);
	}

	private void updateStatusWithSync(final GitSyncStatus status) {
		if (status == null || repoDir == null) {
			return;
		}
		final StringBuilder summary = new StringBuilder();
		summary.append(TextUtils.format("git.summary.repo", shortRepoPath(repoDir)));
		if (changes.size() > 0) {
			summary.append(TextUtils.format("git.summary.changes", Integer.valueOf(changes.size())));
		}
		if (!status.fetchOk) {
			// Background network failures stay silent; keep local summary only.
			setStatusSummary(summary.toString(), repoDir.getAbsolutePath());
			clearStatusError();
			return;
		}
		if (status.error == null || status.error.length() == 0) {
			appendSyncSummary(summary, status);
			setStatusSummary(summary.toString(), repoDir.getAbsolutePath());
			clearStatusError();
			return;
		}
		setStatusSummary(summary.toString(), repoDir.getAbsolutePath());
		setStatusError(status.error);
	}

	private static void appendSyncSummary(final StringBuilder summary, final GitSyncStatus status) {
		if (status.inSync()) {
			summary.append(TextUtils.getText("git.summary.in_sync"));
			return;
		}
		if (status.diverged()) {
			summary.append(TextUtils.getText("git.summary.diverged"));
			return;
		}
		if (status.needsPull()) {
			summary.append(TextUtils.format("git.summary.behind", Integer.valueOf(status.behind)));
			return;
		}
		if (status.needsPush()) {
			summary.append(TextUtils.format("git.summary.ahead", Integer.valueOf(status.ahead)));
			return;
		}
		if (!status.hasUpstream) {
			summary.append(TextUtils.getText("git.summary.no_upstream"));
		}
	}

	private static String shortRepoPath(final File repo) {
		if (repo == null) {
			return "";
		}
		final File parent = repo.getParentFile();
		if (parent != null) {
			return parent.getName() + File.separator + repo.getName();
		}
		return repo.getName();
	}

	private static String escapeHtml(final String text) {
		if (text == null || text.length() == 0) {
			return "";
		}
		final StringBuilder sb = new StringBuilder(text.length() + 16);
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			switch (c) {
			case '&':
				sb.append("&amp;");
				break;
			case '<':
				sb.append("&lt;");
				break;
			case '>':
				sb.append("&gt;");
				break;
			case '"':
				sb.append("&quot;");
				break;
			case '\n':
				sb.append("<br/>");
				break;
			default:
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private void setStatusSummary(final String summary, final String toolTip) {
		statusLabel.setText(summary == null ? "" : summary);
		statusLabel.setToolTipText(toolTip != null && toolTip.length() > 0 ? toolTip : summary);
	}

	private void clearStatusError() {
		lastErrorDetail = "";
		statusErrorLabel.setText("");
		statusErrorLabel.setToolTipText(null);
		statusErrorLabel.setVisible(false);
	}

	private void setStatusError(final String error) {
		lastErrorDetail = error == null ? "" : error.trim();
		if (lastErrorDetail.length() == 0) {
			clearStatusError();
			return;
		}
		final String shortText = shortenErrorForStrip(lastErrorDetail);
		statusErrorLabel.setText("<html>" + escapeHtml(shortText) + "</html>");
		statusErrorLabel.setToolTipText(TextUtils.getText("git.error.click_details"));
		statusErrorLabel.setVisible(true);
	}

	/** Keep the status strip short; full text stays in the click dialog. */
	private static String shortenErrorForStrip(final String error) {
		if (error == null || error.length() == 0) {
			return "";
		}
		String first = error;
		final int nl = error.indexOf('\n');
		if (nl > 0) {
			first = error.substring(0, nl).trim();
		}
		if (first.length() > 120) {
			first = first.substring(0, 117) + "...";
		}
		if (error.length() > first.length() + 2) {
			return first + TextUtils.getText("git.error.click_suffix");
		}
		return first;
	}

	private void showErrorDetailDialog() {
		if (lastErrorDetail.length() == 0) {
			return;
		}
		final JTextArea area = new JTextArea(lastErrorDetail);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setFont(DocearUiTheme.font(12f));
		area.setBackground(DocearUiTheme.SURFACE);
		area.setCaretPosition(0);
		final JScrollPane scroll = new JScrollPane(area);
		scroll.setPreferredSize(new Dimension(560, 200));
		DocearUiTheme.styleScrollPane(scroll);
		JOptionPane.showMessageDialog(this, scroll, TextUtils.getText("git.error.dialog_title"), JOptionPane.ERROR_MESSAGE);
	}

	private void maybeAutoSync(final GitSyncStatus status) {
		if (status == null || repoDir == null) {
			return;
		}
		if (GitConfig.isAutoPullEnabled() && status.fetchOk && status.hasUpstream && status.needsPull()) {
			runAutoPull(status);
			return;
		}
		if (!GitConfig.isAutoSyncEnabled()) {
			return;
		}
		if (!status.fetchOk || !status.hasUpstream || status.inSync() || status.diverged()) {
			return;
		}
		if (GitSyncChecker.hasUncommittedChanges(repoDir)) {
			return;
		}
		if (status.needsPush()) {
			runRemoteAction(TextUtils.getText("git.push"), new String[] { "push" }, true);
		}
	}

	private void runAutoPull(final GitSyncStatus status) {
		if (remoteActionRunning || autoSyncRunning || repoDir == null) {
			return;
		}
		remoteActionRunning = true;
		autoSyncRunning = true;
		pullButton.setEnabled(false);
		pushButton.setEnabled(false);
		refreshButton.setEnabled(false);

		final File repository = repoDir;
		new Thread(new Runnable() {
			@Override
			public void run() {
				final GitAutoPuller.Outcome outcome = GitAutoPuller.pull(repository, status);
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						remoteActionRunning = false;
						autoSyncRunning = false;
						pullButton.setEnabled(true);
						pushButton.setEnabled(true);
						refreshButton.setEnabled(true);
						if (outcome.networkFailure || outcome.nothingToDo) {
							clearStatusError();
							return;
						}
						if (outcome.success) {
							setStatusMessage(outcome.message.length() > 0 ? outcome.message : TextUtils.getText("git.auto_pull_ok"));
							refreshLocalChanges(false);
							historyPanel.refresh(repoDir);
							checkRemoteSync(false);
							return;
						}
						LogUtils.warn("Git auto-pull failed: " + outcome.message);
						clearStatusError();
					}
				});
			}
		}, "GitAutoPull").start();
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
		tableHeader.add(new JLabel(TextUtils.getText("git.files_header")), BorderLayout.WEST);
		tableHeader.add(selectAllCheckBox, BorderLayout.EAST);

		final JPanel tablePanel = new JPanel(new BorderLayout(0, 2));
		tablePanel.add(tableHeader, BorderLayout.NORTH);
		final JScrollPane changesScroll = new JScrollPane(changesTable);
		DocearUiTheme.styleScrollPane(changesScroll);
		tablePanel.add(changesScroll, BorderLayout.CENTER);

		// GitHub Desktop–style commit box: placeholders inside fields, no avatar / outer labels.
		final JPanel commitPanel = new JPanel(new BorderLayout(4, 6));
		commitPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, DocearUiTheme.HAIRLINE),
				BorderFactory.createEmptyBorder(8, 6, 6, 6)));
		commitPanel.setOpaque(true);
		commitPanel.setBackground(DocearUiTheme.CANVAS);

		summaryField.setPreferredSize(new Dimension(200, 30));
		summaryField.setFont(DocearUiTheme.font(13f));
		summaryField.setBorder(BorderFactory.createCompoundBorder(
				DocearUiTheme.hairlineBorder(),
				BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		summaryField.putClientProperty("JTextField.placeholderText", PLACEHOLDER_SUMMARY);
		installSummaryHint();

		descriptionArea.setLineWrap(true);
		descriptionArea.setWrapStyleWord(true);
		descriptionArea.setRows(6);
		descriptionArea.setFont(DocearUiTheme.font(12.5f));
		descriptionArea.setBackground(DocearUiTheme.SURFACE);
		descriptionArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		descriptionArea.putClientProperty("JTextArea.placeholderText", PLACEHOLDER_DESCRIPTION);
		installDescriptionHint();

		final JScrollPane descScroll = new JScrollPane(descriptionArea);
		DocearUiTheme.styleScrollPane(descScroll);
		descScroll.setBorder(DocearUiTheme.hairlineBorder());
		descScroll.setPreferredSize(new Dimension(100, 128));
		descScroll.setMinimumSize(new Dimension(80, 96));

		final JPanel fields = new JPanel(new BorderLayout(0, 6));
		fields.setOpaque(false);
		fields.add(summaryField, BorderLayout.NORTH);
		fields.add(descScroll, BorderLayout.CENTER);
		commitPanel.add(fields, BorderLayout.CENTER);

		final JPanel commitButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		commitButtons.setOpaque(false);
		commitButtons.add(commitButton);
		commitPanel.add(commitButtons, BorderLayout.SOUTH);

		panel.add(tablePanel, BorderLayout.CENTER);
		panel.add(commitPanel, BorderLayout.SOUTH);
		return panel;
	}

	private void installSummaryHint() {
		showSummaryHint();
		summaryField.addFocusListener(new java.awt.event.FocusAdapter() {
			public void focusGained(final java.awt.event.FocusEvent e) {
				if (summaryShowingHint) {
					summaryField.setText("");
					summaryField.setForeground(DocearUiTheme.TEXT);
					summaryShowingHint = false;
				}
			}

			public void focusLost(final java.awt.event.FocusEvent e) {
				if (summaryField.getText().trim().length() == 0) {
					showSummaryHint();
				}
			}
		});
	}

	private void showSummaryHint() {
		summaryShowingHint = true;
		summaryField.setForeground(DocearUiTheme.TEXT_FAINT);
		summaryField.setText(PLACEHOLDER_SUMMARY);
	}

	private void installDescriptionHint() {
		showDescriptionHint();
		descriptionArea.addFocusListener(new java.awt.event.FocusAdapter() {
			public void focusGained(final java.awt.event.FocusEvent e) {
				if (descriptionShowingHint) {
					descriptionArea.setText("");
					descriptionArea.setForeground(DocearUiTheme.TEXT);
					descriptionShowingHint = false;
				}
			}

			public void focusLost(final java.awt.event.FocusEvent e) {
				if (descriptionArea.getText().trim().length() == 0) {
					showDescriptionHint();
				}
			}
		});
	}

	private void showDescriptionHint() {
		descriptionShowingHint = true;
		descriptionArea.setForeground(DocearUiTheme.TEXT_FAINT);
		descriptionArea.setText(PLACEHOLDER_DESCRIPTION);
	}

	private String readSummaryText() {
		if (summaryShowingHint) {
			return "";
		}
		return summaryField.getText().trim();
	}

	private String readDescriptionText() {
		if (descriptionShowingHint) {
			return "";
		}
		return descriptionArea.getText().trim();
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
				runRemoteAction(TextUtils.getText("git.pull"), GitCommand.buildPullArgs(repoDir, false), false);
			}
		});
		pushButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				runRemoteAction(TextUtils.getText("git.push"), new String[] { "push" }, false);
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
		final MouseAdapter showErrorOnClick = new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				if (lastErrorDetail.length() > 0) {
					showErrorDetailDialog();
				}
			}
		};
		statusErrorLabel.addMouseListener(showErrorOnClick);
		statusLabel.addMouseListener(showErrorOnClick);
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
		final JMenuItem undoItem = new JMenuItem(TextUtils.getText("git.confirm.undo_title"));
		final JMenuItem openFileItem = new JMenuItem(TextUtils.getText("git.menu.open_file"));
		final JMenuItem openFolderItem = new JMenuItem(TextUtils.getText("git.menu.open_folder"));
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
		    TextUtils.format("git.confirm.undo", change.getDisplayName()), TextUtils.getText("git.confirm.undo_title"), JOptionPane.YES_NO_OPTION);
		if (confirm != JOptionPane.YES_OPTION) {
			return;
		}
		statusLabel.setText(TextUtils.getText("git.status.undoing"));
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
							setStatusMessage(TextUtils.format("git.status.undone", change.getDisplayName()));
							refreshLocalChanges(false);
						} else {
							setStatusSummary(TextUtils.getText("git.status.undo_failed"), null);
							setStatusError(finalResult.errorText());
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
			statusLabel.setText(TextUtils.getText("git.status.file_missing"));
			return;
		}
		try {
			if (java.awt.Desktop.isDesktopSupported()) {
				java.awt.Desktop.getDesktop().open(file);
			}
		}
		catch (Exception e) {
			LogUtils.warn("无法打开文件: " + e.getMessage(), e);
			statusLabel.setText(TextUtils.getText("git.status.open_file_failed"));
		}
	}

	private void openContainingFolder(final File file) {
		final File folder = file != null && file.isDirectory() ? file : file != null ? file.getParentFile() : null;
		if (folder == null || !folder.isDirectory()) {
			statusLabel.setText(TextUtils.getText("git.status.folder_missing"));
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
				statusLabel.setText(TextUtils.getText("git.status.open_folder_failed"));
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
			statusLabel.setText(TextUtils.getText("git.status.scanning"));
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
								setStatusSummary(TextUtils.format("git.summary.found", shortRepoPath(repository), Integer.valueOf(loaded.size())),
								    repository.getAbsolutePath());
								checkRemoteSync(true);
							} else {
								setStatusMessage(TextUtils.getText("git.status.no_repo_hint"));
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
		final String summary = readSummaryText();
		if (summary.isEmpty()) {
			statusLabel.setText(TextUtils.getText("git.status.need_message"));
			return;
		}
		if (repoDir == null) {
			statusLabel.setText(TextUtils.getText("git.status.no_repo"));
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
			statusLabel.setText(TextUtils.getText("git.status.need_file"));
			return;
		}

		final String description = readDescriptionText();
		final String commitMessage = summary + (description.isEmpty() ? "" : "\n\n" + description);

		statusLabel.setText(TextUtils.getText("git.status.committing"));
		commitButton.setEnabled(false);

		new Thread(new Runnable() {
			@Override
			public void run() {
				boolean success = false;
				String failureMessage = TextUtils.getText("git.status.commit_failed");
				try {
					GitCommand.Result resetResult = GitCommand.run(repoDir, "reset", "HEAD");
					if (resetResult.exitCode != 0) {
						failureMessage = TextUtils.format("git.status.reset_failed", resetResult.errorText());
					} else {
						final String[] addArgs = new String[selectedPaths.size() + 2];
						addArgs[0] = "add";
						addArgs[1] = "--";
						for (int i = 0; i < selectedPaths.size(); i++) {
							addArgs[i + 2] = selectedPaths.get(i);
						}
						final GitCommand.Result addResult = GitCommand.run(repoDir, addArgs);
						if (addResult.exitCode != 0) {
							failureMessage = TextUtils.format("git.status.add_failed", addResult.errorText());
						} else {
							final GitCommand.Result commitResult = GitCommand.run(repoDir, "commit", "-m", commitMessage);
							success = commitResult.exitCode == 0;
							if (!success) {
								failureMessage = commitResult.errors.isEmpty() ? TextUtils.getText("git.status.nothing_to_commit") : commitResult.errorText();
							}
						}
					}
				}
				catch (Exception e) {
					LogUtils.warn("Git commit failed: " + e.getMessage(), e);
					failureMessage = TextUtils.format("git.status.cmd_failed", e.getMessage());
				}

				final boolean finalSuccess = success;
				final String finalFailureMessage = failureMessage;
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						if (finalSuccess) {
							setStatusMessage(TextUtils.getText("git.status.commit_ok"));
							showSummaryHint();
							showDescriptionHint();
							GitPostCommitScriptRunner.scheduleAfterSuccessfulCommit();
							refreshLocalChanges(false);
						} else {
							setStatusSummary(TextUtils.getText("git.status.commit_failed"), null);
							setStatusError(finalFailureMessage);
							updateCommitButtonState();
						}
					}
				});
			}
		}).start();
	}

	private void setStatusMessage(final String message) {
		setStatusSummary(message, message);
		clearStatusError();
	}

	private void runRemoteAction(final String actionLabel, final String[] gitArgs, final boolean automatic) {
		if (repoDir == null) {
			setStatusMessage(TextUtils.getText("git.status.no_repo"));
			return;
		}
		if (remoteActionRunning) {
			return;
		}
		remoteActionRunning = true;
		if (automatic) {
			autoSyncRunning = true;
		}
		setStatusMessage(TextUtils.format(
		        automatic ? "git.status.action_running_auto" : "git.status.action_running", actionLabel));
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
					message = TextUtils.format(
					        automatic ? "git.status.action_ok_auto" : "git.status.action_ok", actionLabel)
					    + (detail.length() > 0 ? ": " + detail : "");
				} else {
					message = result.failureMessage(actionLabel);
				}
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						remoteActionRunning = false;
						autoSyncRunning = false;
						pullButton.setEnabled(true);
						pushButton.setEnabled(true);
						refreshButton.setEnabled(true);
						if (success) {
							setStatusMessage(message);
							refreshLocalChanges(false);
							historyPanel.refresh(repoDir);
							return;
						}
						if (automatic && GitAutoPuller.looksLikeNetworkFailure(result.messageText())) {
							clearStatusError();
							checkRemoteSync(false);
							return;
						}
						setStatusSummary(TextUtils.format(
						        automatic ? "git.status.action_failed_auto" : "git.status.action_failed", actionLabel),
						    repoDir != null ? repoDir.getAbsolutePath() : null);
						final String detail = result.messageText();
						setStatusError(detail.length() > 0 ? detail : message);
						checkRemoteSync(false);
					}
				});
			}
		}).start();
	}

	private class ChangesTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private final String[] columns = { "", TextUtils.getText("git.col.status"), TextUtils.getText("git.col.name"), TextUtils.getText("git.col.size") };

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
