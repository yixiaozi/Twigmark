package org.docear.plugin.core.todoist;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;

final class TodoistSyncProgressDialog extends JDialog implements TodoistSyncProgressCallback {
	private static final long serialVersionUID = 1L;

	private final JLabel summaryLabel = new JLabel(" ");
	private final JLabel statusLabel = new JLabel(" ");
	private final JLabel progressLabel = new JLabel(" ");
	private final JTextArea logArea = new JTextArea();
	private final JTextArea createdArea = new JTextArea();
	private final JTextArea skippedArea = new JTextArea();
	private final JTextArea updatedArea = new JTextArea();
	private final JTextArea failedArea = new JTextArea();
	private final JTextArea closedArea = new JTextArea();
	private final JTabbedPane tabs = new JTabbedPane();
	private final JButton closeButton = new JButton(TextUtils.getText("todoist.sync.close"));

	private int countCreated;
	private int countSkipped;
	private int countUpdated;
	private int countFailed;
	private int countClosed;
	private int totalScanned;
	private volatile boolean finished;
	private TodoistSyncResult finalResult;
	private final boolean importMode;

	private TodoistSyncProgressDialog(Frame owner, boolean importMode) {
		super(owner, importMode ? TextUtils.getText("todoist.import.title") : TextUtils.getText("todoist.sync.title"),
				false);
		this.importMode = importMode;
		setLayout(new BorderLayout(8, 8));
		getContentPane().setBackground(DocearUiTheme.CANVAS);
		((JPanel) getContentPane()).setBorder(DocearUiTheme.pageBorder());

		summaryLabel.setFont(DocearUiTheme.font(13f, java.awt.Font.BOLD));
		summaryLabel.setForeground(DocearUiTheme.TEXT);
		statusLabel.setFont(DocearUiTheme.font(12f));
		statusLabel.setForeground(DocearUiTheme.TEXT_MUTED);
		progressLabel.setFont(DocearUiTheme.font(12f));
		progressLabel.setForeground(DocearUiTheme.ACCENT_DEEP);

		JPanel north = new JPanel(new BorderLayout(4, 4));
		DocearUiTheme.styleCanvas(north);
		north.add(summaryLabel, BorderLayout.NORTH);
		north.add(statusLabel, BorderLayout.CENTER);
		north.add(progressLabel, BorderLayout.SOUTH);
		add(north, BorderLayout.NORTH);

		configureArea(logArea);
		configureArea(createdArea);
		configureArea(skippedArea);
		configureArea(updatedArea);
		configureArea(failedArea);
		configureArea(closedArea);

		tabs.setFont(DocearUiTheme.font(12f, java.awt.Font.BOLD));
		tabs.addTab(TextUtils.getText("todoist.sync.tab.live"), styledScroll(logArea));
		tabs.addTab(tabTitle(TextUtils.getText("todoist.sync.tab.created"), 0), styledScroll(createdArea));
		tabs.addTab(tabTitle(TextUtils.getText("todoist.sync.tab.skipped"), 0), styledScroll(skippedArea));
		tabs.addTab(tabTitle(TextUtils.getText("todoist.sync.tab.updated"), 0), styledScroll(updatedArea));
		tabs.addTab(tabTitle(TextUtils.getText("todoist.sync.tab.failed"), 0), styledScroll(failedArea));
		tabs.addTab(tabTitle(TextUtils.getText("todoist.sync.tab.closed"), 0), styledScroll(closedArea));
		add(tabs, BorderLayout.CENTER);

		closeButton.setEnabled(false);
		closeButton.setFocusPainted(false);
		closeButton.setFont(DocearUiTheme.font(12f, java.awt.Font.BOLD));
		closeButton.setBackground(DocearUiTheme.ACCENT);
		closeButton.setForeground(java.awt.Color.WHITE);
		closeButton.setOpaque(true);
		closeButton.setBorderPainted(false);
		closeButton.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});
		JPanel south = new JPanel(new BorderLayout());
		DocearUiTheme.styleCanvas(south);
		south.setBorder(new EmptyBorder(4, 0, 0, 0));
		south.add(closeButton, BorderLayout.EAST);
		add(south, BorderLayout.SOUTH);

		setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				if (finished) {
					dispose();
				}
			}
		});

		setMinimumSize(new Dimension(640, 420));
		setSize(720, 480);
		setLocationRelativeTo(owner);
		appendLog(importMode ? TextUtils.getText("todoist.import.live.start")
				: TextUtils.getText("todoist.sync.live.start"));
	}

	static TodoistSyncProgressDialog open(Frame owner) {
		TodoistSyncProgressDialog dialog = new TodoistSyncProgressDialog(owner, false);
		dialog.setVisible(true);
		return dialog;
	}

	static TodoistSyncProgressDialog openImport(Frame owner) {
		TodoistSyncProgressDialog dialog = new TodoistSyncProgressDialog(owner, true);
		dialog.setVisible(true);
		return dialog;
	}

	static Frame resolveOwnerFrame() {
		try {
			if (Controller.getCurrentController() != null && Controller.getCurrentController().getViewController() != null) {
				return Controller.getCurrentController().getViewController().getFrame();
			}
		}
		catch (Exception e) {
		}
		return null;
	}

	public void onStatus(final String message) {
		runOnEdt(new Runnable() {
			public void run() {
				statusLabel.setText(message);
			}
		});
	}

	public void onProgress(final int current, final int total) {
		runOnEdt(new Runnable() {
			public void run() {
				totalScanned = total;
				progressLabel.setText(TextUtils.format("todoist.sync.progress", new Object[] {
						Integer.valueOf(current), Integer.valueOf(total) }));
				updateSummary();
			}
		});
	}

	public void onCreated(final String line) {
		runOnEdt(new Runnable() {
			public void run() {
				countCreated++;
				appendLog(TextUtils.getText("todoist.sync.live.created") + " " + line);
				appendLine(createdArea, line);
				updateTabTitles();
				updateSummary();
			}
		});
	}

	public void onSkipped(final String line) {
		runOnEdt(new Runnable() {
			public void run() {
				countSkipped++;
				appendLog(TextUtils.getText("todoist.sync.live.skipped") + " " + line);
				appendLine(skippedArea, line);
				updateTabTitles();
				updateSummary();
			}
		});
	}

	public void onUpdated(final String line) {
		runOnEdt(new Runnable() {
			public void run() {
				countUpdated++;
				appendLog(TextUtils.getText("todoist.sync.live.updated") + " " + line);
				appendLine(updatedArea, line);
				updateTabTitles();
				updateSummary();
			}
		});
	}

	public void onFailed(final String line) {
		runOnEdt(new Runnable() {
			public void run() {
				countFailed++;
				appendLog(TextUtils.getText("todoist.sync.live.failed") + " " + line);
				appendLine(failedArea, line);
				updateTabTitles();
				updateSummary();
			}
		});
	}

	public void onClosed(final String line) {
		runOnEdt(new Runnable() {
			public void run() {
				countClosed++;
				appendLog(TextUtils.getText("todoist.sync.live.closed") + " " + line);
				appendLine(closedArea, line);
				updateTabTitles();
				updateSummary();
			}
		});
	}

	public void onFinished(final TodoistSyncResult result) {
		runOnEdt(new Runnable() {
			public void run() {
				finalResult = result;
				finished = true;
				if (result != null && result.projectName != null) {
					totalScanned = result.totalScanned;
					countCreated = result.created;
					countSkipped = result.skipped;
					countUpdated = result.updated;
					countFailed = result.failed;
					countClosed = result.closed;
				}
				appendLog(importMode ? TextUtils.getText("todoist.import.live.done")
						: TextUtils.getText("todoist.sync.live.done"));
				statusLabel.setText(importMode ? TextUtils.getText("todoist.import.live.done")
						: TextUtils.getText("todoist.sync.live.done"));
				progressLabel.setText("");
				updateSummary();
				updateTabTitles();
				closeButton.setEnabled(true);
				setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
				tabs.setSelectedIndex(0);
				toFront();
			}
		});
	}

	private void updateSummary() {
		String project = finalResult != null && finalResult.projectName != null ? finalResult.projectName
				: (importMode ? TodoistConfig.getImportTargetFile().getAbsolutePath() : TodoistConfig.getProjectName());
		String summary;
		if (importMode) {
			summary = TextUtils.format("todoist.import.summary", new Object[] {
					Integer.valueOf(totalScanned), project, Integer.valueOf(countCreated),
					Integer.valueOf(countFailed) });
		}
		else {
			summary = TextUtils.format("todoist.sync.summary", new Object[] {
					Integer.valueOf(totalScanned), project, Integer.valueOf(countCreated), Integer.valueOf(countUpdated),
					Integer.valueOf(countSkipped), Integer.valueOf(countClosed), Integer.valueOf(countFailed) });
		}
		summaryLabel.setText("<html>" + escapeHtml(summary).replace("\n", "<br/>") + "</html>");
	}

	private void updateTabTitles() {
		tabs.setTitleAt(1, tabTitle(TextUtils.getText("todoist.sync.tab.created"), countCreated));
		tabs.setTitleAt(2, tabTitle(TextUtils.getText("todoist.sync.tab.skipped"), countSkipped));
		tabs.setTitleAt(3, tabTitle(TextUtils.getText("todoist.sync.tab.updated"), countUpdated));
		tabs.setTitleAt(4, tabTitle(TextUtils.getText("todoist.sync.tab.failed"), countFailed));
		tabs.setTitleAt(5, tabTitle(TextUtils.getText("todoist.sync.tab.closed"), countClosed));
	}

	private static String tabTitle(String label, int count) {
		return label + " (" + count + ")";
	}

	private void appendLog(String line) {
		logArea.append(line);
		logArea.append("\n");
		logArea.setCaretPosition(logArea.getDocument().getLength());
	}

	private static void appendLine(JTextArea area, String line) {
		if (area.getText().length() == 0) {
			area.setText(line);
		}
		else {
			area.append("\n");
			area.append(line);
		}
		area.setCaretPosition(area.getDocument().getLength());
	}

	private static void configureArea(JTextArea area) {
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setFont(DocearUiTheme.font(12f));
		area.setForeground(DocearUiTheme.TEXT);
		area.setBackground(DocearUiTheme.SURFACE);
		area.setBorder(new EmptyBorder(8, 10, 8, 10));
	}

	private static JScrollPane styledScroll(JTextArea area) {
		final JScrollPane scroll = new JScrollPane(area);
		DocearUiTheme.styleScrollPane(scroll);
		return scroll;
	}

	private static void runOnEdt(Runnable runnable) {
		if (EventQueue.isDispatchThread()) {
			runnable.run();
		}
		else {
			EventQueue.invokeLater(runnable);
		}
	}

	private static String escapeHtml(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
