package org.freeplane.view.swing.features.pomodoro;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;

/**
 * Polished always-on-top free-timing window with ring clock, multi-session list,
 * history preview and jump-to-node.
 */
final class PomodoroWindow extends JFrame {
	private static final long serialVersionUID = 1L;

	private static final Color BG = new Color(0x1C1A19);
	private static final Color CARD = new Color(0x2A2624);
	private static final Color ACCENT = new Color(0xE07A3D);
	private static final Color TEXT = new Color(0xF4EDE6);
	private static final Color MUTED = new Color(0xA89F96);

	private final PomodoroSessionManager manager;
	private final RingClockPanel ring = new RingClockPanel();
	private final JLabel titleLabel = new JLabel(" ", SwingConstants.CENTER);
	private final JLabel metaLabel = new JLabel(" ", SwingConstants.CENTER);
	private final JLabel statsLabel = new JLabel(" ", SwingConstants.CENTER);
	private final DefaultListModel listModel = new DefaultListModel();
	private final JList sessionList = new JList(listModel);
	private final JTextArea historyArea = new JTextArea(4, 20);
	private NodeModel focusedNode;

	PomodoroWindow(final PomodoroSessionManager manager) {
		super("番茄钟");
		this.manager = manager;
		setAlwaysOnTop(true);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setMinimumSize(new Dimension(380, 560));
		setSize(400, 620);
		buildUi();
		addWindowListener(new WindowAdapter() {
			public void windowClosing(final WindowEvent e) {
				manager.endRunningOnWindowClose();
				setVisible(false);
			}
		});
	}

	private void buildUi() {
		final JPanel root = new JPanel(new BorderLayout(10, 10));
		root.setBorder(new EmptyBorder(16, 16, 14, 16));
		root.setBackground(BG);

		final JPanel header = new JPanel(new BorderLayout(6, 8));
		header.setOpaque(false);
		header.add(ring, BorderLayout.CENTER);

		titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
		titleLabel.setForeground(TEXT);
		titleLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		titleLabel.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (focusedNode != null) {
					manager.navigateTo(focusedNode);
				}
			}
		});
		metaLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
		metaLabel.setForeground(MUTED);
		statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
		statsLabel.setForeground(ACCENT);
		final JPanel under = new JPanel(new GridLayout(3, 1, 0, 2));
		under.setOpaque(false);
		under.add(titleLabel);
		under.add(metaLabel);
		under.add(statsLabel);
		header.add(under, BorderLayout.SOUTH);
		root.add(header, BorderLayout.NORTH);

		final JPanel mid = new JPanel(new BorderLayout(6, 6));
		mid.setOpaque(false);
		sessionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		sessionList.setBackground(CARD);
		sessionList.setForeground(TEXT);
		sessionList.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		sessionList.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getListCellRendererComponent(final JList list, final Object value, final int index,
					final boolean isSelected, final boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setOpaque(true);
				setBackground(isSelected ? new Color(0x3A342F) : CARD);
				setForeground(TEXT);
				setBorder(new EmptyBorder(6, 8, 6, 8));
				if (value instanceof SessionRow) {
					setText(((SessionRow) value).label);
				}
				return this;
			}
		});
		sessionList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				final SessionRow row = selectedRow();
				if (row == null) {
					return;
				}
				focusedNode = row.node;
				refreshHeader(row.node);
				if (e.getClickCount() >= 2) {
					manager.navigateTo(row.node);
					manager.start(row.node);
				}
			}
		});
		final JScrollPane listScroll = new JScrollPane(sessionList);
		listScroll.setBorder(BorderFactory.createLineBorder(new Color(0x3E3833)));
		listScroll.getViewport().setBackground(CARD);
		mid.add(listScroll, BorderLayout.CENTER);

		historyArea.setEditable(false);
		historyArea.setLineWrap(true);
		historyArea.setWrapStyleWord(true);
		historyArea.setBackground(CARD);
		historyArea.setForeground(MUTED);
		historyArea.setFont(new Font("SansSerif", Font.PLAIN, 11));
		historyArea.setBorder(new EmptyBorder(8, 8, 8, 8));
		final JScrollPane histScroll = new JScrollPane(historyArea);
		histScroll.setPreferredSize(new Dimension(100, 110));
		histScroll.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(new Color(0x3E3833)), "最近会话", 0, 0,
				new Font("SansSerif", Font.PLAIN, 11), MUTED));
		mid.add(histScroll, BorderLayout.SOUTH);
		root.add(mid, BorderLayout.CENTER);

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
		buttons.setOpaque(false);
		buttons.add(accentButton("开始 / 继续", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.start(node);
				}
			}
		}));
		buttons.add(darkButton("暂停", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.pause(node);
				}
			}
		}));
		buttons.add(darkButton("结束", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.stop(node);
					refresh();
				}
			}
		}));
		buttons.add(darkButton("定位", new ActionListener() {
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

	private JButton accentButton(final String text, final ActionListener listener) {
		final JButton b = new JButton(text);
		b.setBackground(ACCENT);
		b.setForeground(Color.WHITE);
		b.setFocusPainted(false);
		b.setBorder(new EmptyBorder(8, 14, 8, 14));
		b.addActionListener(listener);
		return b;
	}

	private JButton darkButton(final String text, final ActionListener listener) {
		final JButton b = new JButton(text);
		b.setBackground(new Color(0x3A342F));
		b.setForeground(TEXT);
		b.setFocusPainted(false);
		b.setBorder(new EmptyBorder(8, 12, 8, 12));
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
					|| ext.liveTotalMs(now) > 0 || ext.getLog().length() > 0) {
				listModel.addElement(new SessionRow(node, now));
			}
			if (PomodoroExtension.STATE_RUNNING.equals(state)) {
				running = node;
			}
		}
		if (focusedNode == null || !stillListed(focusedNode)) {
			focusedNode = running != null ? running
					: (listModel.isEmpty() ? null : ((SessionRow) listModel.get(0)).node);
		}
		final long[] stats = manager.computeStats(true);
		statsLabel.setText("今日 " + PomodoroFormatter.formatDuration(stats[0]) + " · 本周 "
				+ PomodoroFormatter.formatDuration(stats[1]) + " · 进行中 " + stats[4] + " · 暂停 " + stats[5]);
		if (focusedNode != null) {
			refreshHeader(focusedNode);
			selectRow(focusedNode);
		}
		else {
			ring.setMillis(0);
			ring.setRunning(false);
			titleLabel.setText("选择节点后开始");
			metaLabel.setText("开始会自动打开开关；同时只跑一个");
			historyArea.setText("");
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
		final boolean running = ext != null && PomodoroExtension.STATE_RUNNING.equals(ext.getState());
		ring.setMillis(segment);
		ring.setRunning(running);
		titleLabel.setText(plainText(node));
		final String state = ext == null ? "" : PomodoroAttributes.stateLabel(ext.getState());
		final long subtree = PomodoroTotals.subtreeMs(node, now);
		metaLabel.setText(state + " · 本段 " + PomodoroFormatter.formatClock(segment) + " · 累计 "
				+ PomodoroFormatter.formatDuration(total)
				+ (subtree > total ? " · Σ" + PomodoroFormatter.formatDuration(subtree) : "")
				+ "  · 点击标题跳转");
		historyArea.setText(ext == null ? "" : PomodoroLog.formatHistoryPreview(ext.getLog(), 8));
		historyArea.setCaretPosition(0);
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
			label = mark + "  " + name + "   " + PomodoroFormatter.formatDuration(total)
					+ (mapName.length() > 0 ? "   · " + mapName : "");
		}

		public String toString() {
			return label;
		}
	}

	/** Circular elapsed clock. */
	private static final class RingClockPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		private long millis;
		private boolean running;

		RingClockPanel() {
			setOpaque(false);
			setPreferredSize(new Dimension(220, 200));
		}

		void setMillis(final long millis) {
			this.millis = millis;
			repaint();
		}

		void setRunning(final boolean running) {
			this.running = running;
			repaint();
		}

		protected void paintComponent(final Graphics g) {
			super.paintComponent(g);
			final Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			final int size = Math.min(getWidth(), getHeight()) - 24;
			final int x = (getWidth() - size) / 2;
			final int y = (getHeight() - size) / 2;
			g2.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.setColor(new Color(0x3A342F));
			g2.drawOval(x, y, size, size);
			// One full ring per hour of focus in the current segment.
			final float progress = (millis % 3600000L) / 3600000f;
			g2.setColor(running ? ACCENT : new Color(0x8A6A4E));
			g2.drawArc(x, y, size, size, 90, Math.round(-360 * progress));
			g2.setColor(TEXT);
			g2.setFont(new Font("SansSerif", Font.BOLD, 40));
			final String clock = PomodoroFormatter.formatClock(millis);
			final int tw = g2.getFontMetrics().stringWidth(clock);
			g2.drawString(clock, getWidth() / 2 - tw / 2, getHeight() / 2 + 14);
			g2.dispose();
		}
	}
}
