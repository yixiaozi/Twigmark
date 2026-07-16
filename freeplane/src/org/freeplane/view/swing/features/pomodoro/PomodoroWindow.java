package org.freeplane.view.swing.features.pomodoro;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
 * Floating pomodoro dock: opaque dark chrome, contextual controls, optional history.
 */
final class PomodoroWindow extends JFrame {
	private static final long serialVersionUID = 1L;

	private static final int MIN_W = 220;
	private static final int MIN_H = 140;
	private static final int DEFAULT_W = 268;
	private static final int DEFAULT_W_HISTORY = 520;
	private static final int DEFAULT_H = 168;
	private static final int COLLAPSED_W = 172;
	private static final int COLLAPSED_H = 34;
	private static final int MARGIN = 16;
	private static final int HISTORY_W = 248;

	private final PomodoroSessionManager manager;
	private final JPanel rootPanel = new JPanel(new BorderLayout());
	private final JPanel expandedPanel = new JPanel(new BorderLayout(8, 0));
	private final JPanel collapsedPanel = new JPanel(new BorderLayout(4, 0));
	private final JPanel leftPanel = new JPanel(new BorderLayout(0, 4));
	private final JLabel clockLabel = new JLabel("00:00", SwingConstants.CENTER);
	private final JLabel titleLabel = new JLabel(" ", SwingConstants.LEFT);
	private final JPanel titleBar = new JPanel(new BorderLayout(6, 0));
	private final JLabel collapsedClock = new JLabel("00:00", SwingConstants.CENTER);
	private final JLabel brand = new JLabel("番茄钟");
	private final JLabel leftTitle = new JLabel("今日记录");
	private final DefaultListModel historyModel = new DefaultListModel();
	private final JList historyList = new JList(historyModel);
	private final JScrollPane leftScroll;
	private final JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
	private final JButton startBtn;
	private final JButton pauseBtn;
	private final JButton stopBtn;
	private final JButton historyToggleBtn;
	private final JButton skinBtn;
	private final JButton minimizeBtn;
	private final JButton closeBtn;
	private final JLabel resizeGrip = new JLabel("◢");
	private NodeModel focusedNode;
	private boolean barCollapsed;
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
		setResizable(true);
		setMinimumSize(new Dimension(MIN_W, MIN_H));
		leftScroll = new JScrollPane(historyList);
		pulseTimer = new Timer(40, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (!wasRunning) {
					return;
				}
				pulsePhase += 0.045;
				clockLabel.setForeground(pulseClockColor(pulsePhase));
				collapsedClock.setForeground(pulseClockColor(pulsePhase));
			}
		});
		pulseTimer.setRepeats(true);

		startBtn = actionButton("▶  继续", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.start(node);
				}
			}
		});
		pauseBtn = actionButton("❚❚  暂停", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.pause(node);
				}
			}
		});
		stopBtn = actionButton("■  结束", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.stop(node);
				}
			}
		});
		historyToggleBtn = topButton("记录");
		historyToggleBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setHistoryExpanded(!historyExpanded);
			}
		});
		skinBtn = topButton("皮肤");
		skinBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				PomodoroTheme.setSkin(PomodoroTheme.nextSkin(theme.name));
				applyTheme();
				refresh();
			}
		});
		minimizeBtn = topButton("—");
		minimizeBtn.setToolTipText("最小化");
		minimizeBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setBarCollapsed(true);
			}
		});
		closeBtn = topButton("×");
		closeBtn.setToolTipText("结束并关闭");
		closeBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				closingEndsSession = true;
				manager.endRunningOnWindowClose();
				hideQuietly();
			}
		});

		buildUi();
		applyTheme();
		setHistoryExpanded(false);
		setBarCollapsed(false);
		dockBottomRight();
		addWindowListener(new WindowAdapter() {
			public void windowClosing(final WindowEvent e) {
				if (closingEndsSession) {
					manager.endRunningOnWindowClose();
				}
				hideQuietly();
			}
		});
		addComponentListener(new ComponentAdapter() {
			public void componentResized(final ComponentEvent e) {
				if (!barCollapsed && historyExpanded) {
					leftPanel.setPreferredSize(new Dimension(Math.max(HISTORY_W, getWidth() / 2), 1));
					leftPanel.revalidate();
				}
			}
		});
	}

	private void buildUi() {
		rootPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(rootPanel, BorderLayout.CENTER);

		// collapsed strip
		collapsedPanel.setBorder(new EmptyBorder(4, 10, 4, 6));
		collapsedClock.setFont(new Font("SansSerif", Font.BOLD, 13));
		collapsedClock.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		collapsedClock.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				setBarCollapsed(false);
			}
		});
		collapsedPanel.add(collapsedClock, BorderLayout.CENTER);
		final JButton expandBtn = topButton("▴");
		expandBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setBarCollapsed(false);
			}
		});
		collapsedPanel.add(expandBtn, BorderLayout.EAST);

		// left history
		leftPanel.setOpaque(true);
		leftTitle.setFont(new Font("SansSerif", Font.PLAIN, 11));
		leftTitle.setBorder(new EmptyBorder(0, 2, 2, 0));
		leftPanel.add(leftTitle, BorderLayout.NORTH);
		historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		historyList.setFixedCellHeight(22);
		historyList.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getListCellRendererComponent(final JList list, final Object value, final int index,
					final boolean isSelected, final boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setOpaque(true);
				setBackground(isSelected ? theme.border : theme.card);
				setForeground(theme.text);
				setFont(new Font("SansSerif", Font.PLAIN, 12));
				setBorder(new EmptyBorder(2, 6, 2, 6));
				if (value instanceof HistoryRow) {
					setText(((HistoryRow) value).display);
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
		leftScroll.setBorder(null);
		leftScroll.getViewport().setOpaque(true);
		leftPanel.setPreferredSize(new Dimension(HISTORY_W, 120));
		leftPanel.add(leftScroll, BorderLayout.CENTER);
		leftPanel.setVisible(false);

		// right timer column
		final JPanel right = new JPanel(new BorderLayout(0, 6));
		right.setOpaque(false);
		right.setBorder(new EmptyBorder(8, 10, 8, 10));

		final JPanel topBar = new JPanel(new BorderLayout());
		topBar.setOpaque(false);
		brand.setFont(new Font("SansSerif", Font.BOLD, 11));
		topBar.add(brand, BorderLayout.WEST);
		final JPanel topBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		topBtns.setOpaque(false);
		topBtns.add(historyToggleBtn);
		topBtns.add(skinBtn);
		topBtns.add(minimizeBtn);
		topBtns.add(closeBtn);
		topBar.add(topBtns, BorderLayout.EAST);
		right.add(topBar, BorderLayout.NORTH);

		clockLabel.setFont(new Font("SansSerif", Font.BOLD, 34));
		clockLabel.setHorizontalAlignment(SwingConstants.CENTER);

		titleBar.setOpaque(true);
		titleBar.setBorder(new EmptyBorder(6, 8, 6, 8));
		titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
		titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		titleLabel.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (focusedNode != null) {
					manager.navigateTo(focusedNode);
				}
			}
		});
		titleBar.add(titleLabel, BorderLayout.CENTER);

		final JPanel mid = new JPanel(new GridBagLayout());
		mid.setOpaque(false);
		final GridBagConstraints gc = new GridBagConstraints();
		gc.gridx = 0;
		gc.gridy = 0;
		gc.weightx = 1;
		gc.fill = GridBagConstraints.HORIZONTAL;
		mid.add(clockLabel, gc);
		gc.gridy = 1;
		gc.insets = new Insets(4, 0, 0, 0);
		mid.add(titleBar, gc);
		right.add(mid, BorderLayout.CENTER);

		buttonRow.setOpaque(false);
		buttonRow.add(startBtn);
		buttonRow.add(pauseBtn);
		buttonRow.add(stopBtn);
		right.add(buttonRow, BorderLayout.SOUTH);

		expandedPanel.setOpaque(true);
		expandedPanel.add(leftPanel, BorderLayout.WEST);
		expandedPanel.add(right, BorderLayout.CENTER);

		resizeGrip.setHorizontalAlignment(SwingConstants.RIGHT);
		resizeGrip.setFont(new Font("SansSerif", Font.PLAIN, 10));
		resizeGrip.setBorder(new EmptyBorder(0, 0, 2, 4));
		resizeGrip.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
		resizeGrip.setToolTipText("拖动调整大小");
		final MouseAdapter resize = new MouseAdapter() {
			private Point press;
			private Dimension startSize;

			public void mousePressed(final MouseEvent e) {
				press = e.getLocationOnScreen();
				startSize = getSize();
			}

			public void mouseDragged(final MouseEvent e) {
				if (press == null || startSize == null || barCollapsed) {
					return;
				}
				final int nw = Math.max(MIN_W, startSize.width + e.getLocationOnScreen().x - press.x);
				final int nh = Math.max(MIN_H, startSize.height + e.getLocationOnScreen().y - press.y);
				setSize(nw, nh);
			}
		};
		resizeGrip.addMouseListener(resize);
		resizeGrip.addMouseMotionListener(resize);
		expandedPanel.add(resizeGrip, BorderLayout.SOUTH);

		enableDrag(collapsedPanel);
		enableDrag(expandedPanel);
		enableDrag(clockLabel);
		enableDrag(collapsedClock);
		enableDrag(brand);
		enableDrag(titleBar);
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
		if (!barCollapsed) {
			final int h = Math.max(MIN_H, getHeight() > 0 ? getHeight() : DEFAULT_H);
			if (expanded) {
				setSize(Math.max(DEFAULT_W_HISTORY, getWidth()), h);
				leftPanel.setPreferredSize(new Dimension(HISTORY_W, 1));
			}
			else {
				setSize(Math.max(DEFAULT_W, Math.min(getWidth(), DEFAULT_W + 40)), h);
			}
			dockBottomRight();
		}
		revalidate();
		repaint();
	}

	void applyTheme() {
		theme = PomodoroTheme.current();
		rootPanel.setBorder(BorderFactory.createLineBorder(theme.border, 1));
		rootPanel.setBackground(theme.bg);
		getContentPane().setBackground(theme.bg);
		collapsedPanel.setBackground(theme.bg);
		expandedPanel.setBackground(theme.bg);
		leftPanel.setBackground(theme.card);
		leftPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, theme.border));
		historyList.setBackground(theme.card);
		historyList.setForeground(theme.text);
		leftScroll.getViewport().setBackground(theme.card);
		clockLabel.setForeground(theme.accent);
		collapsedClock.setForeground(theme.accent);
		brand.setForeground(theme.muted);
		leftTitle.setForeground(theme.muted);
		titleBar.setBackground(theme.card);
		titleLabel.setForeground(theme.text);
		styleTopButton(historyToggleBtn, false);
		styleTopButton(skinBtn, false);
		styleTopButton(minimizeBtn, false);
		styleTopButton(closeBtn, true);
		styleActionButton(startBtn, false);
		styleActionButton(pauseBtn, false);
		styleActionButton(stopBtn, true);
		if (resizeGrip != null) {
			resizeGrip.setForeground(theme.muted);
			resizeGrip.setBackground(theme.bg);
			resizeGrip.setOpaque(true);
		}
		repaint();
	}

	private JButton topButton(final String text) {
		final JButton b = new JButton(text);
		b.setFont(new Font("SansSerif", Font.PLAIN, 11));
		b.setFocusPainted(false);
		b.setMargin(new Insets(2, 8, 2, 8));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return b;
	}

	private void styleTopButton(final JButton b, final boolean danger) {
		b.setOpaque(true);
		b.setContentAreaFilled(true);
		b.setBorderPainted(false);
		b.setBackground(danger ? theme.accent : theme.card);
		b.setForeground(danger ? theme.text : theme.muted);
		b.setBorder(new EmptyBorder(3, 8, 3, 8));
	}

	private JButton actionButton(final String text, final ActionListener listener) {
		final JButton b = new JButton(text);
		b.setFont(new Font("SansSerif", Font.PLAIN, 12));
		b.setFocusPainted(false);
		b.setMargin(new Insets(5, 14, 5, 14));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addActionListener(listener);
		return b;
	}

	private void styleActionButton(final JButton b, final boolean primary) {
		b.setOpaque(true);
		b.setContentAreaFilled(true);
		b.setBorderPainted(false);
		if (primary) {
			b.setBackground(theme.accent);
			b.setForeground(theme.text);
		}
		else {
			b.setBackground(theme.card);
			b.setForeground(theme.text);
		}
		b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(theme.border),
				new EmptyBorder(4, 10, 4, 10)));
	}

	private void updateControlButtons(final boolean running, final boolean paused) {
		// Running: Pause + Stop. Paused/Idle: Start + Stop (stop only if paused or has segment).
		startBtn.setVisible(!running);
		pauseBtn.setVisible(running);
		stopBtn.setVisible(running || paused);
		startBtn.setText(paused ? "▶  继续" : "▶  开始");
		buttonRow.revalidate();
		buttonRow.repaint();
	}

	private Color pulseClockColor(final double phase) {
		final double t = 0.5 + 0.5 * Math.sin(phase);
		return blend(theme.accent, brighten(theme.accent, 0.45f), (float) t);
	}

	private static Color blend(final Color a, final Color b, final float t) {
		final float u = Math.max(0f, Math.min(1f, t));
		return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * u),
				(int) (a.getGreen() + (b.getGreen() - a.getGreen()) * u),
				(int) (a.getBlue() + (b.getBlue() - a.getBlue()) * u));
	}

	private static Color brighten(final Color c, final float amount) {
		return blend(c, Color.WHITE, amount);
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

	void setBarCollapsed(final boolean collapsed) {
		this.barCollapsed = collapsed;
		rootPanel.removeAll();
		if (collapsed) {
			setResizable(false);
			rootPanel.add(collapsedPanel, BorderLayout.CENTER);
			setSize(COLLAPSED_W, COLLAPSED_H);
		}
		else {
			setResizable(true);
			rootPanel.add(expandedPanel, BorderLayout.CENTER);
			setSize(historyExpanded ? Math.max(DEFAULT_W_HISTORY, getWidth()) : DEFAULT_W, DEFAULT_H);
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
		final int w = getWidth() > 0 ? getWidth() : DEFAULT_W;
		final int h = getHeight() > 0 ? getHeight() : DEFAULT_H;
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
		NodeModel running = manager.getRunningNode();
		if (running != null) {
			return running;
		}
		return Controller.getCurrentController().getSelection().getSelected();
	}

	void refresh() {
		final long now = System.currentTimeMillis();
		historyModel.clear();
		final List nodes = manager.collectOpenPomodoroNodes();
		NodeModel running = null;
		NodeModel paused = null;
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
			if (PomodoroExtension.STATE_PAUSED.equals(state)) {
				if (paused == null) {
					paused = node;
				}
			}
			if (PomodoroExtension.STATE_PAUSED.equals(state) || ext.getLog().length() > 0 || ext.getTotalMs() > 0) {
				historyModel.addElement(new HistoryRow(node, now));
			}
		}
		if (running != null) {
			focusedNode = running;
		}
		else if (focusedNode == null || !stillValid(focusedNode)) {
			focusedNode = paused != null ? paused
					: (historyModel.isEmpty() ? Controller.getCurrentController().getSelection().getSelected()
							: ((HistoryRow) historyModel.get(0)).node);
		}
		if (focusedNode != null) {
			refreshHeader(focusedNode);
		}
		else {
			clockLabel.setText("00:00");
			clockLabel.setForeground(theme.accent);
			titleLabel.setText("选中节点后开始");
			titleLabel.setForeground(theme.muted);
			collapsedClock.setText("00:00");
			wasRunning = false;
			pulseTimer.stop();
			updateControlButtons(false, false);
		}
	}

	private boolean stillValid(final NodeModel node) {
		if (node == null) {
			return false;
		}
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		return ext != null && ext.isEnabled();
	}

	private void refreshHeader(final NodeModel node) {
		focusedNode = node;
		final long now = System.currentTimeMillis();
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		final long segment = ext == null ? 0L : ext.liveSegmentMs(now);
		final boolean running = ext != null && PomodoroExtension.STATE_RUNNING.equals(ext.getState());
		final boolean paused = ext != null && PomodoroExtension.STATE_PAUSED.equals(ext.getState());
		clockLabel.setText(PomodoroFormatter.formatClock(segment));
		final String name = plainText(node, historyExpanded ? 48 : 36);
		if (running) {
			titleLabel.setText("▶  " + name);
			titleLabel.setForeground(theme.text);
			titleBar.setBackground(theme.border);
			titleBar.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 3, 0, 0, theme.accent), new EmptyBorder(6, 8, 6, 8)));
		}
		else if (paused) {
			titleLabel.setText("❚❚  " + name);
			titleLabel.setForeground(theme.text);
			titleBar.setBackground(theme.card);
			titleBar.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 3, 0, 0, theme.muted), new EmptyBorder(6, 8, 6, 8)));
		}
		else {
			titleLabel.setText(name.length() == 0 ? "选中节点后开始" : name);
			titleLabel.setForeground(theme.muted);
			titleBar.setBackground(theme.card);
			titleBar.setBorder(new EmptyBorder(6, 8, 6, 8));
		}
		collapsedClock.setText(PomodoroFormatter.formatClock(segment) + (running ? "  ▶" : (paused ? "  ❚❚" : "")));
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
			clockLabel.setForeground(theme.accent);
			collapsedClock.setForeground(theme.accent);
		}
	}

	private static String plainText(final NodeModel node, final int maxLen) {
		if (node == null) {
			return "";
		}
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
			display = mark + "  " + plainText(node, 64) + "   " + PomodoroFormatter.formatDuration(total);
		}

		public String toString() {
			return display;
		}
	}
}
