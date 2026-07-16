package org.freeplane.view.swing.features.pomodoro;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

/**
 * Compact always-on-top pomodoro dock. Quiet chrome; optional left history
 * that resizes independently of the fixed timer column.
 */
final class PomodoroWindow extends JFrame {
	private static final long serialVersionUID = 1L;

	private static final int RIGHT_W = 252;
	private static final int DEFAULT_H = 160;
	private static final int COLLAPSED_W = 168;
	private static final int COLLAPSED_H = 34;
	private static final int MARGIN = 16;
	/** Left history default — wider so titles are readable. */
	private static final int HISTORY_DEFAULT = 360;
	private static final int HISTORY_MIN = 200;
	private static final int HISTORY_MAX = 640;
	private static final String PROP_OPACITY = "pomodoro_window_opacity";
	private static final float OPACITY_MIN = 0.40f;
	private static final float OPACITY_MAX = 1.00f;
	private static final float OPACITY_STEP = 0.05f;

	private final PomodoroSessionManager manager;
	private final JPanel expandedPanel = new JPanel(new BorderLayout(0, 0));
	private final JPanel collapsedPanel = new JPanel(new BorderLayout(4, 0));
	private final JPanel leftPanel = new JPanel(new BorderLayout(0, 2));
	private final JPanel leftResizeStrip = new JPanel();
	private final JPanel rightPanel = new JPanel(new BorderLayout(2, 4));
	private final JLabel clockLabel = new JLabel("00:00", SwingConstants.CENTER);
	private final JLabel titleLabel = new JLabel(" ", SwingConstants.CENTER);
	private final JLabel collapsedClock = new JLabel("00:00", SwingConstants.CENTER);
	private final JLabel brand = new JLabel("番茄钟");
	private final JLabel leftTitle = new JLabel("今日记录");
	private final DefaultListModel historyModel = new DefaultListModel();
	private final JList historyList = new JList(historyModel);
	private final JScrollPane leftScroll;
	private final JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
	private final JButton startBtn;
	private final JButton pauseBtn;
	private final JButton stopBtn;
	private final JLabel historyToggle;
	private final JLabel skinToggle;
	private final JLabel minimizeLbl;
	private NodeModel focusedNode;
	private boolean barCollapsed;
	private boolean historyExpanded;
	private int historyWidth = HISTORY_DEFAULT;
	private boolean closingEndsSession = true;
	private float windowOpacity = OPACITY_MAX;
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
				pulsePhase += 0.05;
				final Color c = pulseClockColor(pulsePhase);
				clockLabel.setForeground(c);
				collapsedClock.setForeground(c);
			}
		});
		pulseTimer.setRepeats(true);

		startBtn = iconChip("▶", "开始 / 继续", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.start(node);
				}
			}
		});
		pauseBtn = iconChip("❚❚", "暂停", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.pause(node);
				}
			}
		});
		stopBtn = iconChip("■", "结束并关闭", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = resolveTarget();
				if (node != null) {
					manager.stop(node);
				}
			}
		});
		historyToggle = linkLabel("记录");
		historyToggle.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				setHistoryExpanded(!historyExpanded);
			}
		});
		skinToggle = linkLabel("皮肤");
		skinToggle.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				PomodoroTheme.setSkin(PomodoroTheme.nextSkin(theme.name));
				applyTheme();
				refresh();
			}
		});
		minimizeLbl = linkLabel("▾");
		minimizeLbl.setToolTipText("最小化");
		minimizeLbl.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				setBarCollapsed(true);
			}
		});

		buildUi();
		windowOpacity = loadOpacity();
		applyTheme();
		applyOpacity();
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
	}

	private void buildUi() {
		getContentPane().setLayout(new BorderLayout());

		collapsedPanel.setBorder(new EmptyBorder(4, 10, 4, 8));
		collapsedClock.setFont(new Font("SansSerif", Font.BOLD, 13));
		collapsedClock.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		collapsedClock.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				setBarCollapsed(false);
			}
		});
		collapsedPanel.add(collapsedClock, BorderLayout.CENTER);
		final JLabel expandLbl = linkLabel("▴");
		expandLbl.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				setBarCollapsed(false);
			}
		});
		collapsedPanel.add(expandLbl, BorderLayout.EAST);

		// Left history — resize strip on outer (left) edge
		leftPanel.setOpaque(true);
		leftTitle.setFont(new Font("SansSerif", Font.PLAIN, 10));
		leftTitle.setBorder(new EmptyBorder(4, 8, 2, 8));
		leftPanel.add(leftTitle, BorderLayout.NORTH);
		historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		historyList.setFixedCellHeight(20);
		historyList.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getListCellRendererComponent(final JList list, final Object value, final int index,
					final boolean isSelected, final boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setOpaque(true);
				setBackground(isSelected ? theme.border : theme.card);
				setForeground(theme.text);
				setFont(new Font("SansSerif", Font.PLAIN, 11));
				setBorder(new EmptyBorder(1, 8, 1, 8));
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
		leftScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		leftScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		leftScroll.getVerticalScrollBar().setUnitIncrement(16);
		leftPanel.add(leftScroll, BorderLayout.CENTER);
		leftPanel.setVisible(false);

		leftResizeStrip.setPreferredSize(new Dimension(5, 1));
		leftResizeStrip.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
		leftResizeStrip.setToolTipText("向左拖动加宽记录区");
		final MouseAdapter leftResize = new MouseAdapter() {
			private Point press;
			private int startHistory;
			private int startX;

			public void mousePressed(final MouseEvent e) {
				press = e.getLocationOnScreen();
				startHistory = historyWidth;
				startX = getLocationOnScreen().x;
			}

			public void mouseDragged(final MouseEvent e) {
				if (press == null || !historyExpanded || barCollapsed) {
					return;
				}
				// Dragging left edge left → wider history; window grows leftward, right stays put.
				final int delta = press.x - e.getLocationOnScreen().x;
				historyWidth = clamp(startHistory + delta, HISTORY_MIN, HISTORY_MAX);
				final int newW = historyWidth + RIGHT_W;
				final int rightEdge = startX + startHistory + RIGHT_W;
				setBounds(rightEdge - newW, getY(), newW, getHeight());
				leftPanel.setPreferredSize(new Dimension(historyWidth, 1));
				leftPanel.revalidate();
			}
		};
		leftResizeStrip.addMouseListener(leftResize);
		leftResizeStrip.addMouseMotionListener(leftResize);

		final JPanel leftWrap = new JPanel(new BorderLayout());
		leftWrap.setOpaque(false);
		leftWrap.add(leftResizeStrip, BorderLayout.WEST);
		leftWrap.add(leftPanel, BorderLayout.CENTER);
		leftWrap.setVisible(false);
		this.leftWrap = leftWrap;

		// Right timer column — fixed width
		rightPanel.setOpaque(true);
		rightPanel.setPreferredSize(new Dimension(RIGHT_W, DEFAULT_H));
		rightPanel.setBorder(new EmptyBorder(6, 10, 8, 10));

		final JPanel topBar = new JPanel(new BorderLayout());
		topBar.setOpaque(false);
		brand.setFont(new Font("SansSerif", Font.PLAIN, 10));
		topBar.add(brand, BorderLayout.WEST);
		final JPanel topLinks = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		topLinks.setOpaque(false);
		topLinks.add(historyToggle);
		topLinks.add(skinToggle);
		topLinks.add(minimizeLbl);
		topBar.add(topLinks, BorderLayout.EAST);
		rightPanel.add(topBar, BorderLayout.NORTH);

		clockLabel.setFont(new Font("SansSerif", Font.BOLD, 30));
		titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
		titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
		rightPanel.add(mid, BorderLayout.CENTER);

		buttonRow.setOpaque(false);
		buttonRow.add(startBtn);
		buttonRow.add(pauseBtn);
		buttonRow.add(stopBtn);
		rightPanel.add(buttonRow, BorderLayout.SOUTH);

		expandedPanel.add(leftWrap, BorderLayout.WEST);
		expandedPanel.add(rightPanel, BorderLayout.CENTER);

		enableDrag(collapsedPanel);
		enableDrag(expandedPanel);
		enableDrag(rightPanel);
		enableDrag(clockLabel);
		enableDrag(collapsedClock);
		enableDrag(brand);
		enableDrag(titleLabel);
		enableOpacityWheel(collapsedPanel);
		enableOpacityWheel(expandedPanel);
		enableOpacityWheel(rightPanel);
		enableOpacityWheel(leftPanel);
		enableOpacityWheel(clockLabel);
		enableOpacityWheel(titleLabel);
	}

	private JPanel leftWrap;

	void hideQuietly() {
		closingEndsSession = false;
		pulseTimer.stop();
		setVisible(false);
	}

	private void setHistoryExpanded(final boolean expanded) {
		historyExpanded = expanded;
		leftPanel.setVisible(expanded);
		leftWrap.setVisible(expanded);
		historyToggle.setText(expanded ? "收起" : "记录");
		if (!barCollapsed) {
			applyExpandedSize(true);
		}
		revalidate();
		repaint();
	}

	private void applyExpandedSize(final boolean keepRightEdge) {
		final int w = historyExpanded ? historyWidth + RIGHT_W : RIGHT_W;
		final int h = Math.max(DEFAULT_H, getHeight() > 40 ? getHeight() : DEFAULT_H);
		leftPanel.setPreferredSize(new Dimension(historyWidth, 1));
		rightPanel.setPreferredSize(new Dimension(RIGHT_W, 1));
		if (keepRightEdge && isShowing()) {
			final int rightEdge = getLocationOnScreen().x + getWidth();
			setBounds(rightEdge - w, getY(), w, h);
		}
		else {
			setSize(w, h);
			dockBottomRight();
		}
	}

	void applyTheme() {
		theme = PomodoroTheme.current();
		getRootPane().setBorder(BorderFactory.createLineBorder(theme.border, 1));
		getContentPane().setBackground(theme.bg);
		collapsedPanel.setBackground(theme.bg);
		expandedPanel.setBackground(theme.bg);
		rightPanel.setBackground(theme.bg);
		leftPanel.setBackground(theme.card);
		leftResizeStrip.setBackground(theme.bg);
		historyList.setBackground(theme.card);
		historyList.setForeground(theme.text);
		leftScroll.getViewport().setBackground(theme.card);
		styleScrollBar(leftScroll.getVerticalScrollBar());
		clockLabel.setForeground(theme.accent);
		collapsedClock.setForeground(theme.accent);
		brand.setForeground(theme.muted);
		leftTitle.setForeground(theme.muted);
		titleLabel.setForeground(theme.muted);
		styleLink(historyToggle);
		styleLink(skinToggle);
		styleLink(minimizeLbl);
		styleChip(startBtn);
		styleChip(pauseBtn);
		styleChip(stopBtn);
		repaint();
	}

	private void styleScrollBar(final JScrollBar bar) {
		bar.setOpaque(true);
		bar.setBackground(theme.card);
		bar.setPreferredSize(new Dimension(6, 0));
		bar.setUI(new BasicScrollBarUI() {
			protected void configureScrollBarColors() {
				thumbColor = theme.border;
				trackColor = theme.card;
			}

			protected JButton createDecreaseButton(final int orientation) {
				return zeroButton();
			}

			protected JButton createIncreaseButton(final int orientation) {
				return zeroButton();
			}

			private JButton zeroButton() {
				final JButton b = new JButton();
				b.setPreferredSize(new Dimension(0, 0));
				b.setMinimumSize(new Dimension(0, 0));
				b.setMaximumSize(new Dimension(0, 0));
				return b;
			}

			protected void paintThumb(final Graphics g, final JComponent c, final Rectangle thumbBounds) {
				if (thumbBounds.isEmpty() || !bar.isEnabled()) {
					return;
				}
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(theme.border);
				g2.fillRoundRect(thumbBounds.x + 1, thumbBounds.y + 2, Math.max(4, thumbBounds.width - 2),
						thumbBounds.height - 4, 4, 4);
				g2.dispose();
			}

			protected void paintTrack(final Graphics g, final JComponent c, final Rectangle trackBounds) {
				g.setColor(theme.card);
				g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
			}
		});
	}

	private JLabel linkLabel(final String text) {
		final JLabel l = new JLabel(text);
		l.setFont(new Font("SansSerif", Font.PLAIN, 10));
		l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return l;
	}

	private void styleLink(final JLabel l) {
		l.setForeground(theme.muted);
		l.setOpaque(false);
	}

	private JButton iconChip(final String icon, final String tip, final ActionListener listener) {
		final JButton b = new JButton(icon);
		b.setUI(new BasicButtonUI());
		b.setToolTipText(tip);
		b.setFont(new Font("SansSerif", Font.PLAIN, 12));
		b.setFocusPainted(false);
		b.setMargin(new java.awt.Insets(2, 10, 2, 10));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addActionListener(listener);
		return b;
	}

	private void styleChip(final JButton b) {
		b.setUI(new BasicButtonUI());
		b.setOpaque(true);
		b.setContentAreaFilled(true);
		b.setBorderPainted(false);
		b.setBackground(theme.card);
		b.setForeground(theme.text);
		b.setBorder(new EmptyBorder(4, 12, 4, 12));
	}

	private void updateControlButtons(final boolean running, final boolean paused) {
		startBtn.setVisible(!running);
		pauseBtn.setVisible(running);
		stopBtn.setVisible(running || paused);
		buttonRow.revalidate();
		buttonRow.repaint();
	}

	private Color pulseClockColor(final double phase) {
		final double t = 0.5 + 0.5 * Math.sin(phase);
		return blend(theme.accent, blend(theme.accent, Color.WHITE, 0.35f), (float) t);
	}

	private static Color blend(final Color a, final Color b, final float t) {
		final float u = Math.max(0f, Math.min(1f, t));
		return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * u),
				(int) (a.getGreen() + (b.getGreen() - a.getGreen()) * u),
				(int) (a.getBlue() + (b.getBlue() - a.getBlue()) * u));
	}

	private static int clamp(final int v, final int min, final int max) {
		return Math.max(min, Math.min(max, v));
	}

	private void enableOpacityWheel(final Component c) {
		c.addMouseWheelListener(new MouseWheelListener() {
			public void mouseWheelMoved(final MouseWheelEvent e) {
				// Wheel up → more opaque; wheel down → more transparent (floor OPACITY_MIN).
				final float delta = e.getWheelRotation() < 0 ? OPACITY_STEP : -OPACITY_STEP;
				final float next = clampFloat(windowOpacity + delta, OPACITY_MIN, OPACITY_MAX);
				if (Math.abs(next - windowOpacity) < 0.001f) {
					return;
				}
				windowOpacity = next;
				saveOpacity(windowOpacity);
				applyOpacity();
				e.consume();
			}
		});
	}

	private void applyOpacity() {
		try {
			setOpacity(windowOpacity);
		}
		catch (Exception e) {
			try {
				// Older JREs / platforms that reject per-pixel opacity.
				com.sun.awt.AWTUtilities.setWindowOpacity(this, windowOpacity);
			}
			catch (Throwable t) {
			}
		}
	}

	private static float loadOpacity() {
		try {
			final String raw = ResourceController.getResourceController().getProperty(PROP_OPACITY, "1.0");
			return clampFloat(Float.parseFloat(raw), OPACITY_MIN, OPACITY_MAX);
		}
		catch (Exception e) {
			return OPACITY_MAX;
		}
	}

	private static void saveOpacity(final float opacity) {
		try {
			ResourceController.getResourceController().setProperty(PROP_OPACITY,
					String.valueOf(Math.round(opacity * 100f) / 100f));
		}
		catch (Exception e) {
		}
	}

	private static float clampFloat(final float v, final float min, final float max) {
		return Math.max(min, Math.min(max, v));
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
		getContentPane().removeAll();
		if (collapsed) {
			getContentPane().add(collapsedPanel, BorderLayout.CENTER);
			setSize(COLLAPSED_W, COLLAPSED_H);
			dockBottomRight();
		}
		else {
			getContentPane().add(expandedPanel, BorderLayout.CENTER);
			applyExpandedSize(false);
		}
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
		final int w = getWidth() > 0 ? getWidth() : RIGHT_W;
		final int h = getHeight() > 0 ? getHeight() : DEFAULT_H;
		setLocation(bounds.x + bounds.width - w - MARGIN, bounds.y + bounds.height - h - MARGIN);
	}

	public void setVisible(final boolean visible) {
		if (visible) {
			applyOpacity();
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
		final NodeModel running = manager.getRunningNode();
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
			if (PomodoroExtension.STATE_PAUSED.equals(state) && paused == null) {
				paused = node;
			}
			if (PomodoroExtension.STATE_PAUSED.equals(state) || ext.getLog().length() > 0 || ext.getTotalMs() > 0) {
				historyModel.addElement(new HistoryRow(node, now, historyWidth));
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
		final int maxChars = Math.max(18, historyExpanded ? 36 : 28);
		final String name = plainText(node, maxChars);
		if (name.length() == 0) {
			titleLabel.setText("选中节点后开始");
			titleLabel.setForeground(theme.muted);
		}
		else {
			titleLabel.setText(name);
			titleLabel.setForeground(running || paused ? theme.text : theme.muted);
		}
		collapsedClock.setText(PomodoroFormatter.formatClock(segment)
				+ (running ? " ▶" : (paused ? " ❚❚" : "")));
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

	/** Raw node title without pomodoro display chip (⏱ / ▶ / ❚❚). */
	private static String plainText(final NodeModel node, final int maxLen) {
		if (node == null) {
			return "";
		}
		String t = node.getText() == null ? "" : HtmlUtils.htmlToPlain(node.getText());
		t = t.replaceAll("\\s+", " ").trim();
		// Strip accidental chip leftovers if present in stored text.
		t = t.replaceAll("\\s*⏱[^\\s]*(\\s*·\\s*Σ[^\\s]*)?(\\s*[▶❚]+)?\\s*$", "").trim();
		t = t.replaceAll("^[▶❚\\s]+", "").trim();
		return t.length() > maxLen ? t.substring(0, maxLen) + "…" : t;
	}

	private static final class HistoryRow {
		final NodeModel node;
		final String display;

		HistoryRow(final NodeModel node, final long now, final int panelWidth) {
			this.node = node;
			final PomodoroExtension ext = PomodoroExtension.getExtension(node);
			final String state = ext == null ? PomodoroExtension.STATE_IDLE : ext.getState();
			String mark = "·";
			if (PomodoroExtension.STATE_PAUSED.equals(state)) {
				mark = "❚❚";
			}
			final long total = ext == null ? 0L : ext.liveTotalMs(now);
			final int chars = Math.max(24, panelWidth / 7);
			display = mark + " " + plainText(node, chars) + "  " + PomodoroFormatter.formatDuration(total);
		}

		public String toString() {
			return display;
		}
	}
}
