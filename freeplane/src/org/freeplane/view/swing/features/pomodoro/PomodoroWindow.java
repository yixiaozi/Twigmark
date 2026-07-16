package org.freeplane.view.swing.features.pomodoro;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;

/**
 * Always-on-top free-timing window. Close ends the running session.
 * Click a row / title to jump to the mind-map node.
 */
final class PomodoroWindow extends JFrame {
	private static final long serialVersionUID = 1L;

	private final PomodoroSessionManager manager;
	private final JLabel clockLabel = new JLabel("00:00", SwingConstants.CENTER);
	private final JLabel titleLabel = new JLabel(" ", SwingConstants.CENTER);
	private final JLabel totalLabel = new JLabel(" ", SwingConstants.CENTER);
	private final DefaultListModel listModel = new DefaultListModel();
	private final JList sessionList = new JList(listModel);
	private NodeModel focusedNode;

	PomodoroWindow(final PomodoroSessionManager manager) {
		super("番茄钟");
		this.manager = manager;
		setAlwaysOnTop(true);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setMinimumSize(new Dimension(320, 280));
		setSize(360, 420);
		buildUi();
		addWindowListener(new WindowAdapter() {
			public void windowClosing(final WindowEvent e) {
				manager.endRunningOnWindowClose();
				setVisible(false);
			}
		});
	}

	private void buildUi() {
		final JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(new EmptyBorder(12, 14, 12, 14));
		root.setBackground(new Color(0xF7F4EF));

		final JPanel header = new JPanel(new BorderLayout(4, 4));
		header.setOpaque(false);
		clockLabel.setFont(new Font("SansSerif", Font.BOLD, 42));
		clockLabel.setForeground(new Color(0x2C2A28));
		titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
		titleLabel.setForeground(new Color(0x5A5550));
		titleLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		titleLabel.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (focusedNode != null) {
					manager.navigateTo(focusedNode);
				}
			}
		});
		totalLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
		totalLabel.setForeground(new Color(0x8A837A));
		header.add(clockLabel, BorderLayout.CENTER);
		final JPanel under = new JPanel(new GridLayout(2, 1, 0, 2));
		under.setOpaque(false);
		under.add(titleLabel);
		under.add(totalLabel);
		header.add(under, BorderLayout.SOUTH);
		root.add(header, BorderLayout.NORTH);

		sessionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		sessionList.setBackground(new Color(0xFFFCF8));
		sessionList.setBorder(BorderFactory.createLineBorder(new Color(0xE5DED4)));
		sessionList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				final SessionRow row = selectedRow();
				if (row == null) {
					return;
				}
				focusedNode = row.node;
				if (e.getClickCount() >= 2) {
					manager.navigateTo(row.node);
				}
				else {
					refreshHeader(row.node);
				}
			}
		});
		root.add(new JScrollPane(sessionList), BorderLayout.CENTER);

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		buttons.setOpaque(false);
		buttons.add(button("开始 / 继续", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.start(node);
				}
			}
		}));
		buttons.add(button("暂停", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.pause(node);
				}
			}
		}));
		buttons.add(button("结束", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.stop(node);
					refresh();
				}
			}
		}));
		buttons.add(button("定位节点", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.navigateTo(node);
				}
			}
		}));
		root.add(buttons, BorderLayout.SOUTH);
		setContentPane(root);
	}

	private static JButton button(final String text, final ActionListener listener) {
		final JButton b = new JButton(text);
		b.addActionListener(listener);
		return b;
	}

	private NodeModel resolveTarget() {
		final SessionRow row = selectedRow();
		if (row != null) {
			return row.node;
		}
		if (focusedNode != null) {
			return focusedNode;
		}
		return manager.getRunningNode();
	}

	private SessionRow selectedRow() {
		final Object value = sessionList.getSelectedValue();
		return value instanceof SessionRow ? (SessionRow) value : null;
	}

	void refresh() {
		final long now = System.currentTimeMillis();
		listModel.clear();
		final List nodes = manager.collectOpenPomodoroNodes();
		NodeModel running = null;
		for (int i = 0; i < nodes.size(); i++) {
			final NodeModel node = (NodeModel) nodes.get(i);
			final PomodoroExtension ext = PomodoroExtension.getExtension(node);
			if (ext == null || !ext.isEnabled()) {
				continue;
			}
			final String state = ext.getState();
			if (PomodoroExtension.STATE_RUNNING.equals(state) || PomodoroExtension.STATE_PAUSED.equals(state)
					|| ext.liveTotalMs(now) > 0) {
				listModel.addElement(new SessionRow(node, now));
			}
			if (PomodoroExtension.STATE_RUNNING.equals(state)) {
				running = node;
			}
		}
		if (focusedNode == null || !stillListed(focusedNode)) {
			focusedNode = running != null ? running : (listModel.isEmpty() ? null
					: ((SessionRow) listModel.get(0)).node);
		}
		if (focusedNode != null) {
			refreshHeader(focusedNode);
			selectRow(focusedNode);
		}
		else {
			clockLabel.setText("00:00");
			titleLabel.setText("选择节点后开始");
			totalLabel.setText(" ");
		}
	}

	private boolean stillListed(final NodeModel node) {
		for (int i = 0; i < listModel.size(); i++) {
			if (((SessionRow) listModel.get(i)).node == node) {
				return true;
			}
		}
		return false;
	}

	private void selectRow(final NodeModel node) {
		for (int i = 0; i < listModel.size(); i++) {
			if (((SessionRow) listModel.get(i)).node == node) {
				sessionList.setSelectedIndex(i);
				return;
			}
		}
	}

	private void refreshHeader(final NodeModel node) {
		focusedNode = node;
		final long now = System.currentTimeMillis();
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		final long segment = ext == null ? 0L : ext.liveSegmentMs(now);
		final long total = ext == null ? 0L : ext.liveTotalMs(now);
		clockLabel.setText(PomodoroFormatter.formatClock(segment));
		titleLabel.setText(plainText(node));
		final String state = ext == null ? "" : ext.getState();
		String stateLabel = "";
		if (PomodoroExtension.STATE_RUNNING.equals(state)) {
			stateLabel = "进行中";
		}
		else if (PomodoroExtension.STATE_PAUSED.equals(state)) {
			stateLabel = "已暂停";
		}
		else {
			stateLabel = "空闲";
		}
		totalLabel.setText(stateLabel + " · 累计 " + PomodoroFormatter.formatDuration(total) + " · 点击标题跳转节点");
	}

	private static String plainText(final NodeModel node) {
		try {
			final Object text = TextController.getController().getPlainTextContent(node);
			if (text != null) {
				return HtmlUtils.htmlToPlain(text.toString()).replaceAll("\\s+", " ").trim();
			}
		}
		catch (Exception e) {
		}
		return node.getText() == null ? "" : HtmlUtils.htmlToPlain(node.getText());
	}

	private static final class SessionRow {
		final NodeModel node;
		final String label;

		SessionRow(final NodeModel node, final long now) {
			this.node = node;
			final PomodoroExtension ext = PomodoroExtension.getExtension(node);
			final String state = ext == null ? PomodoroExtension.STATE_IDLE : ext.getState();
			String mark = "·";
			if (PomodoroExtension.STATE_RUNNING.equals(state)) {
				mark = "▶";
			}
			else if (PomodoroExtension.STATE_PAUSED.equals(state)) {
				mark = "❚❚";
			}
			final String name = plainText(node);
			final long total = ext == null ? 0L : ext.liveTotalMs(now);
			final String mapName = node.getMap() != null && node.getMap().getFile() != null
					? node.getMap().getFile().getName()
					: "";
			label = mark + " " + name + "  [" + PomodoroFormatter.formatDuration(total) + "]"
					+ (mapName.length() > 0 ? "  — " + mapName : "");
		}

		public String toString() {
			return label;
		}
	}
}
