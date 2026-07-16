package org.freeplane.view.swing.features.pomodoro;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.text.TextController;

/**
 * Modal dialog listing completed pomodoro sessions for one node.
 */
final class PomodoroHistoryDialog extends JDialog {
	private static final long serialVersionUID = 1L;

	static void showForNode(final NodeModel node) {
		if (node == null) {
			return;
		}
		Frame owner = null;
		try {
			owner = Controller.getCurrentController().getViewController().getFrame();
		}
		catch (Exception e) {
		}
		final PomodoroHistoryDialog dialog = new PomodoroHistoryDialog(owner, node);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
	}

	private PomodoroHistoryDialog(final Frame owner, final NodeModel node) {
		super(owner, "番茄钟历史", true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		final JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(new EmptyBorder(10, 12, 10, 12));

		final PomodoroExtension ext = PomodoroAttributes.read(node);
		final long now = System.currentTimeMillis();
		final String title = plain(node);
		final JLabel header = new JLabel("<html><b>" + escape(title) + "</b><br>"
				+ summarize(ext, now) + "</html>");
		header.setFont(new Font("SansSerif", Font.PLAIN, 12));
		root.add(header, BorderLayout.NORTH);

		final DefaultListModel model = new DefaultListModel();
		final List records = ext == null ? java.util.Collections.EMPTY_LIST : PomodoroLog.decode(ext.getLog());
		if (records.isEmpty()) {
			model.addElement("（暂无完成会话）");
		}
		else {
			for (int i = records.size() - 1; i >= 0; i--) {
				model.addElement(((PomodoroSessionRecord) records.get(i)).toDisplayLine());
			}
		}
		final JList list = new JList(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setFont(new Font("Monospaced", Font.PLAIN, 12));
		final JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(420, 280));
		root.add(scroll, BorderLayout.CENTER);

		final JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		south.add(btn("写入笔记", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (ext != null) {
					PomodoroNoteSync.sync(node, ext);
				}
			}
		}));
		south.add(btn("定位节点", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
				if (manager != null) {
					manager.navigateTo(node);
				}
			}
		}));
		south.add(btn("关闭", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				dispose();
			}
		}));
		root.add(south, BorderLayout.SOUTH);
		setContentPane(root);
		pack();
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				"close");
		getRootPane().getActionMap().put("close", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent e) {
				dispose();
			}
		});
	}

	private static JButton btn(final String text, final ActionListener listener) {
		final JButton b = new JButton(text);
		b.addActionListener(listener);
		return b;
	}

	private static String summarize(final PomodoroExtension ext, final long now) {
		if (ext == null || (!ext.isEnabled() && ext.getTotalMs() <= 0 && ext.sessionCount() == 0)) {
			return "未开启番茄钟";
		}
		final String state = PomodoroAttributes.stateLabel(ext.getState());
		final long today = PomodoroLog.sumFocusSince(PomodoroLog.decode(ext.getLog()), PomodoroLog.startOfToday())
				+ (ext.liveSegmentMs(now) > 0
						&& (ext.getSessionAt() > 0 ? ext.getSessionAt() : ext.getStartedAt()) >= PomodoroLog.startOfToday()
								? ext.liveSegmentMs(now) : 0L);
		return "状态 " + state + " · 今日 " + PomodoroFormatter.formatDuration(today) + " · 累计 "
				+ PomodoroFormatter.formatDuration(ext.liveTotalMs(now)) + " · " + ext.sessionCount() + " 次会话";
	}

	private static String plain(final NodeModel node) {
		try {
			final String text = TextController.getController().getPlainTextContent(node);
			if (text != null) {
				return HtmlUtils.htmlToPlain(text).replaceAll("\\s+", " ").trim();
			}
		}
		catch (Exception e) {
		}
		return node.getText() == null ? "" : HtmlUtils.htmlToPlain(node.getText());
	}

	private static String escape(final String text) {
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
