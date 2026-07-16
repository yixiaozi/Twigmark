package org.freeplane.view.swing.features.pomodoro;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
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
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.text.TextController;

/**
 * Compact always-on-top pomodoro dock: bottom-right, foldable bar, optional history pane.
 */
final class PomodoroWindow extends JFrame {
	private static final long serialVersionUID = 1L;

	private static final int EXPANDED_W = 252;
	private static final int EXPANDED_W_HISTORY = 448;
	private static final int EXPANDED_H = 176;
	private static final int COLLAPSED_W = 168;
	private static final int COLLAPSED_H = 36;
	private static final int MARGIN = 16;
	private static final int HISTORY_W = 196;

	private final PomodoroSessionManager manager;
	private final JPanel expandedPanel = new JPanel(new BorderLayout(6, 4));
	private final JPanel collapsedPanel = new JPanel(new BorderLayout(4, 0));
	private final JPanel leftPanel = new JPanel(new BorderLayout(0, 2));
	private final JPanel mainRow = new JPanel(new BorderLayout(0, 0));
	private final JLabel clockLabel = new JLabel("00:00", SwingConstants.CENTER);
	private final JLabel titleLabel = new JLabel(" ", SwingConstants.CENTER);
	private final JLabel collapsedClock = new JLabel("00:00 ▶", SwingConstants.CENTER);
	private final JLabel brand = new JLabel("番茄钟");
	private final JLabel leftTitle = new JLabel("今日记录");
	private final DefaultListModel historyModel = new DefaultListModel();
	private final JList historyList = new JList(historyModel);
	private final JScrollPane leftScroll;
	private final JButton startBtn;
	private final JButton pauseBtn;
	private final JButton stopBtn;
	private final JButton historyToggleBtn;
	private final JButton minimizeBtn;
	private final JButton closeBtn;
	private NodeModel focusedNode;
	private boolean collapsed;
	private boolean historyExpanded;
	private boolean closingEndsSession = true;
	private PomodoroTheme theme = PomodoroTheme.current();
	private final Timer pulseTimer;
	private double pulsePhase;
	private boolean wasRunning;

	PomodoroWindow(final PomodoroSessionManager manager) {
		super("番茄钟");
		this.manager = manager;
		setAlwaysOnTop(true);
		setUndecorated(true);
		setType(Window.Type.UTILITY);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setResizable(false);
		leftScroll = new JScrollPane(historyList);
		pulseTimer = new Timer(50, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (!wasRunning) {
					return;
				}
				pulsePhase += 0.06;
				final Color c = pulseClockColor(pulsePhase);
				clockLabel.setForeground(c);
				collapsedClock.setForeground(c);
			}
		});
		pulseTimer.setRepeats(true);
		startBtn = chipButton("▶", "开始", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.start(node);
				}
			}
		});
		pauseBtn = chipButton("❚❚", "暂停", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.pause(node);
				}
			}
		});
		stopBtn = chipButton("■", "结束", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.stop(node);
				}
			}
		});
		historyToggleBtn = chromeButton("记录", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setHistoryExpanded(!historyExpanded);
			}
		});
		minimizeBtn = chromeButton("—", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setCollapsed(true);
			}
		});
		closeBtn = chromeButton("✕", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				closingEndsSession = true;
				manager.endRunningOnWindowClose();
				hideQuietly();
			}
		});
		buildUi();
		applyTheme();
		setHistoryExpanded(false);
		setCollapsed(false);
		dockBottomRight();
		addWindowListener(new WindowAdapter() {
			public void windowClosing(final WindowEvent e) {
				if (closingEndsSession) {
					manager.endRunningOnWindowClose();
				}
				hideQuietly();
			}
		});
	}

	private void buildUi() {
		collapsedPanel.setBorder(new EmptyBorder(4, 8, 4, 6));
		collapsedClock.setFont(new Font("SansSerif", Font.BOLD, 13));
		collapsedClock.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		collapsedClock.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				setCollapsed(false);
			}
		});
		collapsedPanel.add(collapsedClock, BorderLayout.CENTER);
		collapsedPanel.add(chromeButton("▴", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setCollapsed(false);
			}
		}), BorderLayout.EAST);

		expandedPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

		leftPanel.setOpaque(false);
		leftTitle.setFont(new Font("SansSerif", Font.PLAIN, 10));
		leftPanel.add(leftTitle, BorderLayout.NORTH);
		historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		historyList.setFont(new Font("SansSerif", Font.PLAIN, 11));
		historyList.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		historyList.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getListCellRendererComponent(final JList list, final Object value, final int index,
					final boolean isSelected, final boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setOpaque(true);
				setBackground(isSelected ? theme.border : theme.card);
				setForeground(theme.text);
				setFont(new Font("SansSerif", Font.PLAIN, 11));
				setBorder(new EmptyBorder(3, 4, 3, 4));
				if (value instanceof HistoryRow) {
					setText("<html><body style='width:" + (HISTORY_W - 24) + "px'>"
							+ HtmlUtils.toHTMLEscapedText(((HistoryRow) value).display) + "</body></html>");
				}
				return this;
			}
		});
		historyList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				final Object v = historyList.getSelectedValue();
				if (!(v instanceof HistoryRow)) {
					return;
				}
				final HistoryRow row = (HistoryRow) v;
				focusedNode = row.node;
				refreshHeader(row.node);
				if (e.getClickCount() >= 2) {
					manager.navigateTo(row.node);
					PomodoroHistoryDialog.showForNode(row.node);
				}
			}
		});
		leftScroll.setPreferredSize(new Dimension(HISTORY_W, 120));
		leftPanel.add(leftScroll, BorderLayout.CENTER);
		leftPanel.setVisible(false);

		mainRow.add(leftPanel, BorderLayout.WEST);

		final JPanel right = new JPanel(new BorderLayout(2, 2));
		right.setOpaque(false);
		right.setBorder(new EmptyBorder(0, 4, 0, 0));

		final JPanel topBar = new JPanel(new BorderLayout());
		topBar.setOpaque(false);
		brand.setFont(new Font("SansSerif", Font.PLAIN, 10));
		topBar.add(brand, BorderLayout.WEST);
		final JPanel topBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		topBtns.setOpaque(false);
		topBtns.add(historyToggleBtn);
		topBtns.add(chromeButton("皮肤", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				PomodoroTheme.setSkin(PomodoroTheme.nextSkin(theme.name));
				applyTheme();
				refresh();
			}
		}));
		topBtns.add(minimizeBtn);
		topBtns.add(closeBtn);
		topBar.add(topBtns, BorderLayout.EAST);
		right.add(topBar, BorderLayout.NORTH);

		clockLabel.setFont(new Font("SansSerif", Font.BOLD, 30));
		titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
		titleLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		titleLabel.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (focusedNode != null) {
					manager.navigateTo(focusedNode);
				}
			}
		});
		final JPanel mid = new JPanel(new GridLayout(2, 1, 0, 2));
		mid.setOpaque(false);
		mid.add(clockLabel);
		mid.add(titleLabel);
		right.add(mid, BorderLayout.CENTER);

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
		buttons.setOpaque(false);
		buttons.add(startBtn);
		buttons.add(pauseBtn);
		buttons.add(stopBtn);
		right.add(buttons, BorderLayout.SOUTH);
		mainRow.add(right, BorderLayout.CENTER);
		expandedPanel.add(mainRow, BorderLayout.CENTER);

		enableDrag(collapsedPanel);
		enableDrag(expandedPanel);
		enableDrag(clockLabel);
		enableDrag(collapsedClock);
	}

	void hideQuietly() {
		closingEndsSession = false;
		pulseTimer.stop();
		setVisible(false);
	}

	private void setHistoryExpanded(final boolean expanded) {
		historyExpanded = expanded;
		leftPanel.setVisible(expanded);
		historyToggleBtn.setText(expanded ? "收起" : "记录");
		if (!collapsed) {
			setSize(expanded ? EXPANDED_W_HISTORY : EXPANDED_W, EXPANDED_H);
			dockBottomRight();
		}
		revalidate();
		repaint();
	}

	void applyTheme() {
		theme = PomodoroTheme.current();
		getRootPane().setBorder(BorderFactory.createLineBorder(theme.border, 1));
		getContentPane().setBackground(theme.bg);
		collapsedPanel.setBackground(theme.bg);
		expandedPanel.setBackground(theme.bg);
		historyList.setBackground(theme.card);
		historyList.setForeground(theme.text);
		leftScroll.setBorder(BorderFactory.createLineBorder(theme.border));
		leftScroll.getViewport().setBackground(theme.card);
		clockLabel.setForeground(theme.text);
		collapsedClock.setForeground(theme.accent);
		titleLabel.setForeground(theme.muted);
		brand.setForeground(theme.muted);
		leftTitle.setForeground(theme.muted);
		styleChromeButton(historyToggleBtn, false);
		styleChromeButton(minimizeBtn, false);
		styleChromeButton(closeBtn, true);
		styleChipButton(startBtn);
		styleChipButton(pauseBtn);
		styleChipButton(stopBtn);
		repaint();
	}

	private JButton chromeButton(final String text, final ActionListener listener) {
		final JButton b = new JButton(text);
		b.setMargin(new java.awt.Insets(2, 8, 2, 8));
		b.setFont(new Font("SansSerif", Font.BOLD, 11));
		b.setFocusPainted(false);
		b.setPreferredSize(new Dimension(text.length() > 2 ? 44 : 30, 24));
		b.addActionListener(listener);
		return b;
	}

	private void styleChromeButton(final JButton b, final boolean danger) {
		b.setBackground(danger ? new Color(theme.accent.getRed(), theme.accent.getGreen(), theme.accent.getBlue(), 48)
				: theme.border);
		b.setForeground(danger ? theme.accent : theme.text);
		b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(
				danger ? theme.accent : theme.muted, 1), new EmptyBorder(1, 4, 1, 4)));
		b.setContentAreaFilled(true);
		b.setOpaque(true);
	}

	private void styleChipButton(final JButton b) {
		b.setForeground(theme.text);
		b.setBackground(theme.border);
		b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(theme.muted),
				new EmptyBorder(2, 4, 2, 4)));
	}

	private JButton chipButton(final String icon, final String tip, final ActionListener listener) {
		final JButton b = new JButton(icon);
		b.setToolTipText(tip);
		b.setMargin(new java.awt.Insets(4, 14, 4, 14));
		b.setFont(new Font("SansSerif", Font.PLAIN, 13));
		b.setFocusPainted(false);
		b.addActionListener(listener);
		styleChipButton(b);
		return b;
	}

	private void updateControlButtons(final boolean running, final boolean paused) {
		startBtn.setVisible(!running);
		pauseBtn.setVisible(running);
		stopBtn.setVisible(running || paused);
	}

	private Color pulseClockColor(final double phase) {
		final double t = 0.5 + 0.5 * Math.sin(phase);
		final Color glow = brighten(theme.accent, 0.55f);
		final Color deep = darken(theme.accent, 0.15f);
		return blend(deep, glow, (float) t);
	}

	private static Color blend(final Color a, final Color b, final float t) {
		final float u = Math.max(0f, Math.min(1f, t));
		return new Color(
				(int) (a.getRed() + (b.getRed() - a.getRed()) * u),
				(int) (a.getGreen() + (b.getGreen() - a.getGreen()) * u),
				(int) (a.getBlue() + (b.getBlue() - a.getBlue()) * u));
	}

	private static Color brighten(final Color c, final float amount) {
		return blend(c, Color.WHITE, amount);
	}

	private static Color darken(final Color c, final float amount) {
		return blend(c, Color.BLACK, amount);
	}

	private void enableDrag(final Component c) {
		final MouseAdapter drag = new MouseAdapter() {
			private Point press;

			public void mousePressed(final MouseEvent e) {
				press = e.getPoint();
			}

			public void mouseDragged(final MouseEvent e) {
				if (press == null) {
					return;
				}
				final Point loc = getLocation();
				setLocation(loc.x + e.getX() - press.x, loc.y + e.getY() - press.y);
			}
		};
		c.addMouseListener(drag);
		c.addMouseMotionListener(drag);
	}

	void setCollapsed(final boolean collapsed) {
		this.collapsed = collapsed;
		getContentPane().removeAll();
		if (collapsed) {
			getContentPane().add(collapsedPanel, BorderLayout.CENTER);
			setSize(COLLAPSED_W, COLLAPSED_H);
		}
		else {
			getContentPane().add(expandedPanel, BorderLayout.CENTER);
			setSize(historyExpanded ? EXPANDED_W_HISTORY : EXPANDED_W, EXPANDED_H);
		}
		dockBottomRight();
		revalidate();
		repaint();
	}

	void dockBottomRight() {
		Rectangle bounds = null;
		try {
			final Frame frame = Controller.getCurrentController().getViewController().getFrame();
			if (frame != null && frame.isShowing()) {
				bounds = frame.getBounds();
			}
		}
		catch (Exception e) {
		}
		if (bounds == null) {
			bounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		}
		final int w = getWidth() > 0 ? getWidth() : (collapsed ? COLLAPSED_W : EXPANDED_W);
		final int h = getHeight() > 0 ? getHeight() : (collapsed ? COLLAPSED_H : EXPANDED_H);
		setLocation(bounds.x + bounds.width - w - MARGIN, bounds.y + bounds.height - h - MARGIN);
	}

	public void setVisible(final boolean visible) {
		if (visible) {
			dockBottomRight();
		}
		else {
			pulseTimer.stop();
		}
		super.setVisible(visible);
	}

	private NodeModel resolveTarget() {
		if (historyExpanded) {
			final Object v = historyList.getSelectedValue();
			if (v instanceof HistoryRow && ((HistoryRow) v).node != null) {
				return ((HistoryRow) v).node;
			}
		}
		if (focusedNode != null) {
			return focusedNode;
		}
		return manager.getRunningNode();
	}

	void refresh() {
		final long now = System.currentTimeMillis();
		historyModel.clear();
		final List nodes = manager.collectOpenPomodoroNodes();
		NodeModel running = null;
		for (int i = 0; i < nodes.size(); i++) {
			final NodeModel node = (NodeModel) nodes.get(i);
			final PomodoroExtension ext = PomodoroExtension.getExtension(node);
			if (ext == null || !ext.isEnabled()) {
				continue;
			}
			final String state = ext.getState();
			if (PomodoroExtension.STATE_RUNNING.equals(state)) {
				running = node;
				continue;
			}
			if (PomodoroExtension.STATE_PAUSED.equals(state) || ext.getLog().length() > 0 || ext.getTotalMs() > 0) {
				historyModel.addElement(new HistoryRow(node, now));
			}
		}
		if (focusedNode == null || (running != null && focusedNode != running && !stillInHistory(focusedNode))) {
			focusedNode = running != null ? running
					: (historyModel.isEmpty() ? null : ((HistoryRow) historyModel.get(0)).node);
		}
		if (running != null) {
			focusedNode = running;
		}
		if (focusedNode != null) {
			refreshHeader(focusedNode);
		}
		else {
			clockLabel.setText("00:00");
			titleLabel.setText("选中节点后开始");
			titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
			titleLabel.setForeground(theme.muted);
			titleLabel.setBorder(null);
			collapsedClock.setText("00:00");
			wasRunning = false;
			pulseTimer.stop();
			updateControlButtons(false, false);
			startBtn.setVisible(true);
			pauseBtn.setVisible(false);
			stopBtn.setVisible(false);
		}
		if (!collapsed) {
			dockBottomRight();
		}
	}

	private boolean stillInHistory(final NodeModel node) {
		for (int i = 0; i < historyModel.size(); i++) {
			if (((HistoryRow) historyModel.get(i)).node == node) {
				return true;
			}
		}
		return false;
	}

	private void refreshHeader(final NodeModel node) {
		focusedNode = node;
		final long now = System.currentTimeMillis();
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		final long segment = ext == null ? 0L : ext.liveSegmentMs(now);
		final boolean running = ext != null && PomodoroExtension.STATE_RUNNING.equals(ext.getState());
		final boolean paused = ext != null && PomodoroExtension.STATE_PAUSED.equals(ext.getState());
		clockLabel.setText(PomodoroFormatter.formatClock(segment));
		final String name = plainText(node, running ? 40 : 28);
		if (running) {
			titleLabel.setText("▶  " + name);
			titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
			titleLabel.setForeground(theme.text);
			titleLabel.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(theme.accent, 1, true),
					new EmptyBorder(3, 8, 3, 8)));
		}
		else {
			final String mark = paused ? "❚❚  " : "";
			titleLabel.setText(mark + name);
			titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
			titleLabel.setForeground(paused ? theme.text : theme.muted);
			titleLabel.setBorder(paused ? BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(theme.border, 1, true),
					new EmptyBorder(2, 6, 2, 6)) : null);
		}
		collapsedClock.setText(PomodoroFormatter.formatClock(segment) + (running ? " ▶" : (paused ? " ❚❚" : "")));
		updateControlButtons(running, paused);
		wasRunning = running;
		if (running) {
			if (!pulseTimer.isRunning()) {
				pulsePhase = 0;
				pulseTimer.start();
			}
		}
		else {
			pulseTimer.stop();
			clockLabel.setForeground(theme.text);
			collapsedClock.setForeground(theme.accent);
		}
	}

	private static String plainText(final NodeModel node, final int maxLen) {
		try {
			final Object text = TextController.getController().getPlainTextContent(node);
			if (text != null) {
				final String plain = HtmlUtils.htmlToPlain(text.toString()).replaceAll("\\s+", " ").trim();
				return plain.length() > maxLen ? plain.substring(0, maxLen) + "…" : plain;
			}
		}
		catch (Exception e) {
		}
		final String t = node.getText() == null ? "" : HtmlUtils.htmlToPlain(node.getText());
		return t.length() > maxLen ? t.substring(0, maxLen) + "…" : t;
	}

	private static final class HistoryRow {
		final NodeModel node;
		final String display;

		HistoryRow(final NodeModel node, final long now) {
			this.node = node;
			final PomodoroExtension ext = PomodoroExtension.getExtension(node);
			final String state = ext == null ? PomodoroExtension.STATE_IDLE : ext.getState();
			String mark = "·";
			if (PomodoroExtension.STATE_PAUSED.equals(state)) {
				mark = "❚❚";
			}
			final long total = ext == null ? 0L : ext.liveTotalMs(now);
			final String name = plainText(node, 56);
			display = mark + " " + name + "  ·  " + PomodoroFormatter.formatDuration(total);
		}

		public String toString() {
			return display;
		}
	}
}
