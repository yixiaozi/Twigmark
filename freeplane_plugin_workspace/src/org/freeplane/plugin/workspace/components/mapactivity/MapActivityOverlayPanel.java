package org.freeplane.plugin.workspace.components.mapactivity;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.workspace.components.overlay.FloatingOverlayInteraction;
import org.freeplane.plugin.workspace.features.mapactivity.MapActivityCollector;
import org.freeplane.plugin.workspace.features.mapactivity.MapActivityItem;
import org.freeplane.plugin.workspace.features.mapactivity.MapActivitySnapshot;
import org.freeplane.plugin.workspace.features.mapactivity.PomodoroRange;

/**
 * Top-left floating card: collapsed pulse pill / expanded current-map activity.
 */
public class MapActivityOverlayPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	public interface LayoutListener {
		void onPanelLayoutChanged();
	}

	public enum CategoryTab {
		ALL, FLAG, REMINDER, TODO, POMODORO
	}

	private static final String PROP_WIDTH = "docear.map_activity.width";
	private static final String PROP_HEIGHT = "docear.map_activity.height";
	private static final String PROP_POMO_RANGE = "docear.map_activity.pomodoro.range";
	private static final String PROP_TAB = "docear.map_activity.tab";

	private static final int ARC = 14;
	private static final int PILL_ARC = 20;
	private static final int DEFAULT_WIDTH = 340;
	private static final int MIN_WIDTH = 260;
	private static final int MAX_WIDTH = 560;
	private static final int MIN_EXPANDED_HEIGHT = 200;
	private static final int MAX_EXPANDED_HEIGHT = 10000;
	private static final int COLLAPSED_PAD_X = 12;
	private static final int COLLAPSED_PAD_Y = 5;
	private static final int COLLAPSED_GAP = 5;
	private static final int META_W = 148;
	private static final int ROW_H = 28;

	private static final Color ACCENT = new Color(0x0D, 0x94, 0x88);
	private static final Color ACCENT_SOFT = new Color(0xCC, 0xFB, 0xF1);
	private static final Color CARD_BG = new Color(0xF7, 0xF8, 0xFA);
	private static final Color CARD_BORDER = new Color(0xCB, 0xD5, 0xE1);
	private static final Color CARD_SHADOW = new Color(15, 23, 42, 28);
	private static final Color PILL_BG = Color.WHITE;
	private static final Color PILL_BORDER = new Color(0x94, 0xA3, 0xB8);
	private static final Color PILL_ACTIVE_BG = ACCENT_SOFT;
	private static final Color PILL_ACTIVE_BORDER = ACCENT;
	private static final Color HEADER_BG = new Color(0xEC, 0xF4, 0xF3);
	private static final Color SECTION_BG = Color.WHITE;
	private static final Color DANGER = new Color(0xDC, 0x26, 0x26);
	private static final Color DANGER_SOFT = new Color(0xFE, 0xF2, 0xF2);
	private static final Color WARN = new Color(0xD9, 0x77, 0x06);
	private static final Color FLAG = new Color(0xEF, 0x44, 0x44);
	private static final Color TODO = new Color(0x1D, 0x4E, 0xD8);

	private final JPanel collapsedBar;
	private final JLabel collapsedTitle;
	private final JPanel collapsedBadges;
	private final JPanel expandedCard;
	private final JPanel headerBar;
	private final JLabel headerSubtitle;
	private final JPanel tabBar;
	private final JPanel pomoRangeBar;
	private final JPanel sectionsHost;
	private final JScrollPane sectionsScroll;
	private final JLabel emptyLabel;
	private final JButton collapseButton;
	private final JButton homeButton;

	private final JToggleButton tabAll;
	private final JToggleButton tabFlag;
	private final JToggleButton tabReminder;
	private final JToggleButton tabTodo;
	private final JToggleButton tabPomo;
	private final JToggleButton rangeToday;
	private final JToggleButton rangeWeek;
	private final JToggleButton rangeAll;

	private boolean expanded;
	private CategoryTab activeTab = CategoryTab.ALL;
	private PomodoroRange pomodoroRange = PomodoroRange.TODAY;
	private MapActivitySnapshot snapshot = MapActivitySnapshot.empty();
	private LayoutListener layoutListener;

	private int userWidth = DEFAULT_WIDTH;
	private int userHeight = 380;
	private boolean userSized;
	private boolean positionDirty;
	private boolean homeRequested;

	private boolean sectionFlagOpen = true;
	private boolean sectionReminderOpen = true;
	private boolean sectionTodoOpen = true;
	private boolean sectionPomodoroOpen = true;

	public MapActivityOverlayPanel() {
		setOpaque(false);
		setLayout(new BorderLayout());
		loadPersistedChrome();

		collapsedTitle = new JLabel("本图");
		collapsedTitle.setFont(DocearUiTheme.font(12f, Font.BOLD));
		collapsedTitle.setForeground(ACCENT);

		collapsedBadges = new JPanel();
		collapsedBadges.setOpaque(false);
		collapsedBadges.setLayout(new BoxLayout(collapsedBadges, BoxLayout.X_AXIS));

		collapsedBar = buildCollapsedBar();
		expandedCard = new RoundedPanel(ARC, CARD_BG, CARD_BORDER, true);
		headerSubtitle = DocearUiTheme.mutedLabel("");
		headerBar = buildHeaderBar();

		tabAll = createTab("全部");
		tabFlag = createTab("红旗");
		tabReminder = createTab("提醒");
		tabTodo = createTab("待办");
		tabPomo = createTab("专注");
		final ButtonGroup tabs = new ButtonGroup();
		tabs.add(tabAll);
		tabs.add(tabFlag);
		tabs.add(tabReminder);
		tabs.add(tabTodo);
		tabs.add(tabPomo);
		tabBar = buildTabBar();

		rangeToday = createTab("今日");
		rangeWeek = createTab("本周");
		rangeAll = createTab("全部");
		final ButtonGroup ranges = new ButtonGroup();
		ranges.add(rangeToday);
		ranges.add(rangeWeek);
		ranges.add(rangeAll);
		pomoRangeBar = buildPomoRangeBar();

		sectionsHost = new org.freeplane.plugin.workspace.components.overlay.FillWidthScrollPanel();
		sectionsHost.setOpaque(true);
		sectionsHost.setBackground(SECTION_BG);
		sectionsHost.setLayout(new BoxLayout(sectionsHost, BoxLayout.Y_AXIS));
		sectionsHost.setBorder(new EmptyBorder(4, 0, 4, 0));

		emptyLabel = DocearUiTheme.mutedLabel("这张导图暂无此类动态");
		emptyLabel.setBorder(new EmptyBorder(24, 16, 24, 16));
		emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		sectionsScroll = new JScrollPane(sectionsHost);
		sectionsScroll.setBorder(BorderFactory.createLineBorder(CARD_BORDER));
		sectionsScroll.getViewport().setBackground(SECTION_BG);
		sectionsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sectionsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		DocearUiTheme.styleScrollPane(sectionsScroll);

		collapseButton = DocearUiTheme.ghostButton("收起");
		homeButton = DocearUiTheme.ghostButton("归位");
		homeButton.setToolTipText("回到导图左上角默认位置");

		assembleExpandedCard();
		wireActions();
		installInteractions();
		syncTabSelection();
		syncRangeSelection();

		expanded = false;
		add(collapsedBar, BorderLayout.CENTER);
		refreshFromCurrentMap();
	}

	public void setLayoutListener(final LayoutListener listener) {
		this.layoutListener = listener;
	}

	public boolean takePositionDirty() {
		final boolean dirty = positionDirty;
		positionDirty = false;
		return dirty;
	}

	public boolean takeHomeRequest() {
		final boolean req = homeRequested;
		homeRequested = false;
		return req;
	}

	public void requestHome() {
		homeRequested = true;
		positionDirty = false;
		fireLayoutChanged();
	}

	public void refreshFromCurrentMap() {
		snapshot = MapActivityCollector.collectCurrentMap(pomodoroRange);
		refreshCollapsed();
		if (expanded) {
			rebuildBody();
			updateScrollSize();
		}
		revalidate();
		repaint();
		if (!expanded) {
			applyCurrentSize();
		}
		fireLayoutChanged();
	}

	public void setExpanded(final boolean value) {
		if (expanded == value) {
			applyCurrentSize();
			fireLayoutChanged();
			return;
		}
		expanded = value;
		removeAll();
		if (expanded) {
			if (!userSized) {
				userWidth = DEFAULT_WIDTH;
				userHeight = 380;
			}
			rebuildBody();
			updateScrollSize();
			add(expandedCard, BorderLayout.CENTER);
		}
		else {
			add(collapsedBar, BorderLayout.CENTER);
			setPreferredSize(null);
			setMinimumSize(null);
			setMaximumSize(null);
		}
		refreshCollapsed();
		applyCurrentSize();
		revalidate();
		repaint();
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				applyCurrentSize();
				fireLayoutChanged();
			}
		});
		fireLayoutChanged();
	}

	private void loadPersistedChrome() {
		try {
			final ResourceController rc = ResourceController.getResourceController();
			userWidth = clamp(rc.getIntProperty(PROP_WIDTH, DEFAULT_WIDTH), MIN_WIDTH, MAX_WIDTH);
			userHeight = clamp(rc.getIntProperty(PROP_HEIGHT, 380), MIN_EXPANDED_HEIGHT, MAX_EXPANDED_HEIGHT);
			userSized = rc.getIntProperty(PROP_WIDTH, -1) > 0;
			pomodoroRange = PomodoroRange.fromKey(rc.getProperty(PROP_POMO_RANGE, "today"));
			activeTab = tabFromKey(rc.getProperty(PROP_TAB, "all"));
		}
		catch (final Exception e) {
			// defaults
		}
	}

	private void persistChrome() {
		try {
			final ResourceController rc = ResourceController.getResourceController();
			if (userSized) {
				rc.setProperty(PROP_WIDTH, String.valueOf(userWidth));
				rc.setProperty(PROP_HEIGHT, String.valueOf(userHeight));
			}
			rc.setProperty(PROP_POMO_RANGE, pomodoroRange.toKey());
			rc.setProperty(PROP_TAB, tabToKey(activeTab));
		}
		catch (final Exception e) {
			// ignore
		}
	}

	private JPanel buildCollapsedBar() {
		final RoundedPanel bar = new RoundedPanel(PILL_ARC, PILL_BG, PILL_BORDER, true);
		bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
		bar.setBorder(new EmptyBorder(COLLAPSED_PAD_Y, COLLAPSED_PAD_X, COLLAPSED_PAD_Y + 2, COLLAPSED_PAD_X + 2));
		bar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		bar.add(collapsedTitle);
		bar.add(Box.createHorizontalStrut(COLLAPSED_GAP));
		bar.add(collapsedBadges);
		bar.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
					setExpanded(true);
				}
			}
		});
		return bar;
	}

	private JPanel buildHeaderBar() {
		final JPanel header = new JPanel(new BorderLayout(8, 0));
		header.setOpaque(true);
		header.setBackground(HEADER_BG);
		header.setBorder(new EmptyBorder(8, 10, 8, 8));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
		final JPanel left = new JPanel();
		left.setOpaque(false);
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		final JLabel title = new JLabel("本图动态");
		title.setFont(DocearUiTheme.font(13f, Font.BOLD));
		title.setForeground(DocearUiTheme.TEXT);
		headerSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		left.add(title);
		left.add(Box.createVerticalStrut(2));
		left.add(headerSubtitle);
		header.add(left, BorderLayout.CENTER);
		return header;
	}

	private JToggleButton createTab(final String text) {
		final JToggleButton b = new JToggleButton(text);
		b.setFocusable(false);
		b.setFont(DocearUiTheme.font(11f));
		b.setOpaque(true);
		b.setBorder(new EmptyBorder(4, 8, 4, 8));
		return b;
	}

	private JPanel buildTabBar() {
		final JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		bar.setOpaque(false);
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		bar.add(tabAll);
		bar.add(tabFlag);
		bar.add(tabReminder);
		bar.add(tabTodo);
		bar.add(tabPomo);
		return bar;
	}

	private JPanel buildPomoRangeBar() {
		final JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		bar.setOpaque(false);
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		final JLabel tip = new JLabel("时间");
		tip.setFont(DocearUiTheme.font(11f));
		tip.setForeground(DocearUiTheme.TEXT_MUTED);
		bar.add(tip);
		bar.add(rangeToday);
		bar.add(rangeWeek);
		bar.add(rangeAll);
		return bar;
	}

	private void assembleExpandedCard() {
		expandedCard.setLayout(new BorderLayout());
		final JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		headerActions.setOpaque(false);
		headerActions.add(homeButton);
		headerActions.add(collapseButton);
		headerBar.add(headerActions, BorderLayout.EAST);

		final JPanel north = new JPanel();
		north.setOpaque(false);
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
		north.setBorder(new EmptyBorder(0, 10, 0, 10));
		headerBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		tabBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		pomoRangeBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		north.add(headerBar);
		north.add(Box.createVerticalStrut(6));
		north.add(tabBar);
		north.add(Box.createVerticalStrut(4));
		north.add(pomoRangeBar);

		final JPanel body = new JPanel(new BorderLayout(0, 6));
		body.setOpaque(false);
		body.setBorder(new EmptyBorder(4, 10, 8, 10));
		body.add(sectionsScroll, BorderLayout.CENTER);

		expandedCard.add(north, BorderLayout.NORTH);
		expandedCard.add(body, BorderLayout.CENTER);
	}

	private void wireActions() {
		collapseButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setExpanded(false);
			}
		});
		homeButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				requestHome();
			}
		});
		wireTab(tabAll, CategoryTab.ALL);
		wireTab(tabFlag, CategoryTab.FLAG);
		wireTab(tabReminder, CategoryTab.REMINDER);
		wireTab(tabTodo, CategoryTab.TODO);
		wireTab(tabPomo, CategoryTab.POMODORO);
		wireRange(rangeToday, PomodoroRange.TODAY);
		wireRange(rangeWeek, PomodoroRange.WEEK);
		wireRange(rangeAll, PomodoroRange.ALL);
	}

	private void wireTab(final JToggleButton button, final CategoryTab tab) {
		button.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (!button.isSelected()) {
					return;
				}
				activeTab = tab;
				persistChrome();
				rebuildBody();
				updateScrollSize();
				revalidate();
				repaint();
			}
		});
	}

	private void wireRange(final JToggleButton button, final PomodoroRange range) {
		button.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (!button.isSelected()) {
					return;
				}
				pomodoroRange = range;
				persistChrome();
				refreshFromCurrentMap();
			}
		});
	}

	private void installInteractions() {
		FloatingOverlayInteraction.install(this, new FloatingOverlayInteraction.Host() {
			public boolean isResizeEnabled() {
				return expanded;
			}

			public boolean isDragEnabled(final MouseEvent e) {
				final Component src = e.getComponent();
				return src == MapActivityOverlayPanel.this || isDescendant(headerBar, e.getComponent())
				        || isDescendant(collapsedBar, e.getComponent());
			}

			public Dimension getMinSize() {
				return new Dimension(MIN_WIDTH, MIN_EXPANDED_HEIGHT);
			}

			public Dimension getMaxSize() {
				return new Dimension(MAX_WIDTH, MAX_EXPANDED_HEIGHT);
			}

			public void onUserMoved() {
				positionDirty = true;
				fireLayoutChanged();
			}

			public void onUserResized(final int width, final int height) {
				userWidth = width;
				userHeight = height;
				userSized = true;
				updateScrollSize();
				fireLayoutChanged();
			}

			public void onInteractionFinished() {
				persistChrome();
				fireLayoutChanged();
			}
		});
		// Header/collapsed still need explicit drag when click starts on children.
		installDragOn(headerBar);
		installDragOn(collapsedBar);
	}

	private void installDragOn(final JComponent target) {
		final int[] start = new int[4];
		final boolean[] dragging = new boolean[1];
		target.addMouseListener(new MouseAdapter() {
			public void mousePressed(final MouseEvent e) {
				if (!SwingUtilities.isLeftMouseButton(e)) {
					return;
				}
				if (expanded && FloatingOverlayInteraction.hitTest(MapActivityOverlayPanel.this, SwingUtilities
				        .convertPoint(target, e.getPoint(), MapActivityOverlayPanel.this)) != FloatingOverlayInteraction.Edge.NONE) {
					return;
				}
				dragging[0] = true;
				final Point screen = e.getLocationOnScreen();
				start[0] = screen.x;
				start[1] = screen.y;
				start[2] = getX();
				start[3] = getY();
			}

			public void mouseReleased(final MouseEvent e) {
				dragging[0] = false;
			}
		});
		target.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
			public void mouseDragged(final MouseEvent e) {
				if (!dragging[0]) {
					return;
				}
				final Point screen = e.getLocationOnScreen();
				setLocation(Math.max(0, start[2] + (screen.x - start[0])), Math.max(0, start[3] + (screen.y - start[1])));
				positionDirty = true;
				fireLayoutChanged();
			}
		});
	}

	private static boolean isDescendant(final Component root, final Component child) {
		Component c = child;
		while (c != null) {
			if (c == root) {
				return true;
			}
			c = c.getParent();
		}
		return false;
	}

	private void syncTabSelection() {
		tabAll.setSelected(activeTab == CategoryTab.ALL);
		tabFlag.setSelected(activeTab == CategoryTab.FLAG);
		tabReminder.setSelected(activeTab == CategoryTab.REMINDER);
		tabTodo.setSelected(activeTab == CategoryTab.TODO);
		tabPomo.setSelected(activeTab == CategoryTab.POMODORO);
		updateTabLook(tabAll);
		updateTabLook(tabFlag);
		updateTabLook(tabReminder);
		updateTabLook(tabTodo);
		updateTabLook(tabPomo);
	}

	private void syncRangeSelection() {
		rangeToday.setSelected(pomodoroRange == PomodoroRange.TODAY);
		rangeWeek.setSelected(pomodoroRange == PomodoroRange.WEEK);
		rangeAll.setSelected(pomodoroRange == PomodoroRange.ALL);
		updateTabLook(rangeToday);
		updateTabLook(rangeWeek);
		updateTabLook(rangeAll);
	}

	private void updateTabLook(final JToggleButton button) {
		if (button.isSelected()) {
			button.setBackground(ACCENT);
			button.setForeground(Color.WHITE);
		}
		else {
			button.setBackground(new Color(0xE2, 0xE8, 0xF0));
			button.setForeground(new Color(0x33, 0x41, 0x55));
		}
	}

	private void rebuildBody() {
		syncTabSelection();
		syncRangeSelection();
		pomoRangeBar.setVisible(activeTab == CategoryTab.ALL || activeTab == CategoryTab.POMODORO);
		sectionsHost.removeAll();
		final List content = itemsForActiveTab();
		if (content.isEmpty() && activeTab != CategoryTab.ALL) {
			sectionsHost.add(emptyLabel);
		}
		else if (activeTab == CategoryTab.ALL) {
			if (snapshot.isEmpty()) {
				sectionsHost.add(emptyLabel);
			}
			else {
				addSection("红旗", FLAG, snapshot.getFlags(), sectionFlagOpen, new Runnable() {
					public void run() {
						sectionFlagOpen = !sectionFlagOpen;
						rebuildBody();
					}
				});
				addSection("提醒", ACCENT, mergedReminders(), sectionReminderOpen, new Runnable() {
					public void run() {
						sectionReminderOpen = !sectionReminderOpen;
						rebuildBody();
					}
				});
				addSection("待办", TODO, snapshot.getTodos(), sectionTodoOpen, new Runnable() {
					public void run() {
						sectionTodoOpen = !sectionTodoOpen;
						rebuildBody();
					}
				});
				addSection("专注", WARN, snapshot.getPomodoro(), sectionPomodoroOpen, new Runnable() {
					public void run() {
						sectionPomodoroOpen = !sectionPomodoroOpen;
						rebuildBody();
					}
				});
			}
		}
		else {
			for (int i = 0; i < content.size(); i++) {
				sectionsHost.add(buildRow((MapActivityItem) content.get(i), i));
			}
		}
		sectionsHost.revalidate();
		sectionsHost.repaint();
		headerSubtitle.setText(buildSubtitle());
	}

	private List itemsForActiveTab() {
		if (activeTab == CategoryTab.FLAG) {
			return snapshot.getFlags();
		}
		if (activeTab == CategoryTab.REMINDER) {
			return mergedReminders();
		}
		if (activeTab == CategoryTab.TODO) {
			return snapshot.getTodos();
		}
		if (activeTab == CategoryTab.POMODORO) {
			return snapshot.getPomodoro();
		}
		return snapshot.allItems();
	}

	private List mergedReminders() {
		final List merged = new ArrayList();
		merged.addAll(snapshot.getOverdue());
		merged.addAll(snapshot.getReminders());
		return merged;
	}

	private void addSection(final String title, final Color accent, final List items, final boolean open,
	        final Runnable toggle) {
		if (items == null || items.isEmpty()) {
			return;
		}
		final JPanel section = new JPanel(new BorderLayout());
		section.setOpaque(false);
		section.setAlignmentX(Component.LEFT_ALIGNMENT);
		final JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(true);
		head.setBackground(softTint(accent));
		head.setBorder(new EmptyBorder(5, 8, 5, 8));
		head.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		final JLabel mark = new JLabel(open ? "v" : ">");
		mark.setForeground(accent);
		mark.setFont(DocearUiTheme.font(11f, Font.BOLD));
		final JLabel name = new JLabel("  " + title + "  " + items.size());
		name.setFont(DocearUiTheme.font(11.5f, Font.BOLD));
		name.setForeground(accent);
		head.add(mark, BorderLayout.WEST);
		head.add(name, BorderLayout.CENTER);
		head.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				toggle.run();
				sectionsHost.revalidate();
				sectionsHost.repaint();
				updateScrollSize();
				fireLayoutChanged();
			}
		});
		section.add(head, BorderLayout.NORTH);
		if (open) {
			final JPanel list = new JPanel();
			list.setOpaque(false);
			list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
			for (int i = 0; i < items.size(); i++) {
				list.add(buildRow((MapActivityItem) items.get(i), i));
			}
			section.add(list, BorderLayout.CENTER);
		}
		// Cap to content height so BoxLayout cannot leave empty gaps between sections.
		section.doLayout();
		final int contentH = Math.max(28, section.getPreferredSize().height);
		section.setMaximumSize(new Dimension(Short.MAX_VALUE, contentH));
		sectionsHost.add(section);
		sectionsHost.add(Box.createVerticalStrut(4));
	}

	private JPanel buildRow(final MapActivityItem item, final int index) {
		final JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(true);
		Color bg = index % 2 == 0 ? DocearUiTheme.SURFACE : DocearUiTheme.SURFACE_SOFT;
		if (item.live) {
			bg = new Color(0xFF, 0xF7, 0xED);
		}
		else if (item.overdue) {
			bg = DANGER_SOFT;
		}
		row.setBackground(bg);
		row.setBorder(new EmptyBorder(4, 8, 4, 8));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Short.MAX_VALUE, 120));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		final boolean showMeta = item.meta != null && item.meta.trim().length() > 0
		        && item.kind != MapActivityItem.Kind.TODO;
		if (showMeta) {
			final JLabel meta = new JLabel("<html><body style='width:" + (META_W - 8) + "px'>" + escapeHtml(item.meta)
			        + "</body></html>");
			meta.setFont(DocearUiTheme.font(11f));
			meta.setForeground(item.overdue ? DANGER : (item.live ? WARN : DocearUiTheme.TEXT_MUTED));
			meta.setPreferredSize(new Dimension(META_W, ROW_H));
			row.add(meta, BorderLayout.WEST);
		}

		final int wrapWidth = Math.max(120, (userSized ? userWidth : DEFAULT_WIDTH) - (showMeta ? META_W + 40 : 36));
		final JLabel title = new JLabel("<html><body style='width:" + wrapWidth + "px'>" + escapeHtml(item.title)
		        + "</body></html>");
		title.setFont(DocearUiTheme.font(12f));
		title.setForeground(DocearUiTheme.TEXT);
		title.setToolTipText(item.title);
		row.add(title, BorderLayout.CENTER);

		row.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					navigate(item.node);
				}
			}

			public void mouseEntered(final MouseEvent e) {
				row.setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT), new EmptyBorder(4, 5, 4, 8)));
			}

			public void mouseExited(final MouseEvent e) {
				row.setBorder(new EmptyBorder(4, 8, 4, 8));
			}
		});
		return row;
	}

	private static String escapeHtml(final String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void navigate(final NodeModel node) {
		if (node == null) {
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					final Controller controller = Controller.getCurrentController();
					if (controller == null || controller.getSelection() == null) {
						return;
					}
					Controller.getCurrentModeController().getMapController().displayNode(node);
					controller.getSelection().selectAsTheOnlyOneSelected(node);
					controller.getSelection().centerNode(node);
					// Unfold/layout may finish one tick later; re-center so the node is not left at the edge.
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							try {
								if (Controller.getCurrentController() != null
								        && Controller.getCurrentController().getSelection() != null) {
									Controller.getCurrentController().getSelection().centerNode(node);
								}
							}
							catch (final Exception ignored) {
								// ignore
							}
						}
					});
				}
				catch (final Exception e) {
					// ignore
				}
			}
		});
	}

	private void refreshCollapsed() {
		collapsedBadges.removeAll();
		final boolean attention = snapshot.attentionCount() > 0 || snapshot.hasRunningPomodoro();
		if (snapshot.hasRunningPomodoro()) {
			collapsedTitle.setText("专注中");
			collapsedBar.setToolTipText(snapshot.getRunningTitle());
		}
		else if (!snapshot.isEmpty()) {
			collapsedTitle.setText("本图");
			collapsedBar.setToolTipText("本图动态 · 共 " + snapshot.totalCount() + " 项 · 点击展开");
		}
		else {
			collapsedTitle.setText("本图");
			collapsedBar.setToolTipText("本图暂无动态 · 点击展开");
		}
		collapsedTitle.setForeground(attention ? new Color(0x11, 0x5E, 0x59) : ACCENT);

		addBadgeIf(snapshot.flagCount() > 0, snapshot.flagCount() + "旗", FLAG, Color.WHITE);
		addBadgeIf(snapshot.overdueCount() > 0, snapshot.overdueCount() + "逾", DANGER, Color.WHITE);
		addBadgeIf(snapshot.reminderCount() > 0, snapshot.reminderCount() + "提", ACCENT, Color.WHITE);
		addBadgeIf(snapshot.todoCount() > 0, snapshot.todoCount() + "办", TODO, Color.WHITE);
		addBadgeIf(snapshot.hasRunningPomodoro() || snapshot.pomodoroCount() > 0,
		        snapshot.hasRunningPomodoro() ? "专注" : (snapshot.pomodoroCount() + "专"), WARN, Color.WHITE);

		final RoundedPanel pill = (RoundedPanel) collapsedBar;
		if (attention || !snapshot.isEmpty()) {
			pill.setColors(PILL_ACTIVE_BG, PILL_ACTIVE_BORDER);
			pill.setAccent(true);
		}
		else {
			pill.setColors(PILL_BG, PILL_BORDER);
			pill.setAccent(false);
		}
		collapsedBadges.setVisible(collapsedBadges.getComponentCount() > 0);
		if (collapsedBar.getComponentCount() >= 2) {
			collapsedBar.getComponent(1).setVisible(collapsedBadges.isVisible());
		}
		headerSubtitle.setText(buildSubtitle());
		collapsedBar.revalidate();
		collapsedBar.repaint();
		if (!expanded) {
			applyCurrentSize();
		}
	}

	private String buildSubtitle() {
		if (snapshot.isEmpty()) {
			return "暂无动态";
		}
		final StringBuilder sb = new StringBuilder();
		appendPart(sb, snapshot.flagCount(), "红旗");
		appendPart(sb, snapshot.overdueCount() + snapshot.reminderCount(), "提醒");
		appendPart(sb, snapshot.todoCount(), "待办");
		appendPart(sb, snapshot.pomodoroCount(), "专注");
		if (activeTab == CategoryTab.POMODORO || activeTab == CategoryTab.ALL) {
			if (sb.length() > 0) {
				sb.append(" · ");
			}
			sb.append(pomodoroRange.label());
		}
		return sb.toString();
	}

	private static void appendPart(final StringBuilder sb, final int count, final String label) {
		if (count <= 0) {
			return;
		}
		if (sb.length() > 0) {
			sb.append(" · ");
		}
		sb.append(count).append(label);
	}

	private void addBadgeIf(final boolean show, final String text, final Color bg, final Color fg) {
		if (!show) {
			return;
		}
		if (collapsedBadges.getComponentCount() > 0) {
			collapsedBadges.add(Box.createHorizontalStrut(4));
		}
		final JLabel badge = new JLabel(text);
		badge.setFont(DocearUiTheme.font(10f, Font.BOLD));
		badge.setForeground(fg);
		badge.setOpaque(true);
		badge.setBackground(bg);
		badge.setBorder(new EmptyBorder(1, 5, 1, 5));
		collapsedBadges.add(badge);
	}

	private void updateScrollSize() {
		final int w = Math.max(MIN_WIDTH, (userSized ? userWidth : DEFAULT_WIDTH) - 28);
		final int h = Math.max(120, (userSized ? userHeight : 380) - 150);
		sectionsScroll.setPreferredSize(new Dimension(w, h));
		sectionsScroll.setMaximumSize(new Dimension(Short.MAX_VALUE, Integer.MAX_VALUE));
	}

	private void applyCurrentSize() {
		final Dimension pref = getPreferredSize();
		if (!expanded) {
			setPreferredSize(pref);
			setMinimumSize(pref);
			setMaximumSize(pref);
			setSize(pref);
			collapsedBar.setPreferredSize(pref);
			collapsedBar.setSize(pref);
		}
		else {
			setMaximumSize(new Dimension(MAX_WIDTH, MAX_EXPANDED_HEIGHT));
			setMinimumSize(new Dimension(MIN_WIDTH, MIN_EXPANDED_HEIGHT));
			setPreferredSize(pref);
			setSize(pref);
		}
	}

	private void fireLayoutChanged() {
		if (layoutListener != null) {
			layoutListener.onPanelLayoutChanged();
		}
		final java.awt.Container parent = getParent();
		if (parent != null) {
			parent.repaint();
		}
	}

	public Dimension getPreferredSize() {
		if (expanded) {
			return new Dimension(clamp(userSized ? userWidth : DEFAULT_WIDTH, MIN_WIDTH, MAX_WIDTH),
			        clamp(userSized ? userHeight : 380, MIN_EXPANDED_HEIGHT, MAX_EXPANDED_HEIGHT));
		}
		return computeCollapsedSize();
	}

	private Dimension computeCollapsedSize() {
		final java.awt.FontMetrics fm = collapsedTitle.getFontMetrics(collapsedTitle.getFont());
		final String title = collapsedTitle.getText() != null ? collapsedTitle.getText() : "本图";
		int contentW = fm.stringWidth(title);
		int contentH = Math.max(fm.getHeight(), 14);
		if (collapsedBadges.isVisible()) {
			final Dimension bd = collapsedBadges.getPreferredSize();
			contentW += COLLAPSED_GAP + Math.max(0, bd.width);
			contentH = Math.max(contentH, Math.max(14, bd.height));
		}
		final Insets pad = collapsedBar.getInsets();
		return new Dimension(Math.max(52, contentW + pad.left + pad.right + 3),
		        Math.max(28, contentH + pad.top + pad.bottom + 3));
	}

	public Dimension getMinimumSize() {
		return expanded ? new Dimension(MIN_WIDTH, MIN_EXPANDED_HEIGHT) : computeCollapsedSize();
	}

	public Dimension getMaximumSize() {
		return expanded ? new Dimension(MAX_WIDTH, MAX_EXPANDED_HEIGHT) : computeCollapsedSize();
	}

	private static Color softTint(final Color accent) {
		return new Color((accent.getRed() * 12 + 255 * 88) / 100, (accent.getGreen() * 12 + 255 * 88) / 100,
		        (accent.getBlue() * 12 + 255 * 88) / 100);
	}

	private static String truncate(final String text, final int max) {
		if (text == null) {
			return "";
		}
		if (text.length() <= max) {
			return text;
		}
		return text.substring(0, Math.max(1, max - 1)) + "...";
	}

	private static int clamp(final int v, final int min, final int max) {
		return Math.max(min, Math.min(max, v));
	}

	private static CategoryTab tabFromKey(final String key) {
		if ("flag".equalsIgnoreCase(key)) {
			return CategoryTab.FLAG;
		}
		if ("reminder".equalsIgnoreCase(key)) {
			return CategoryTab.REMINDER;
		}
		if ("todo".equalsIgnoreCase(key)) {
			return CategoryTab.TODO;
		}
		if ("pomodoro".equalsIgnoreCase(key) || "focus".equalsIgnoreCase(key)) {
			return CategoryTab.POMODORO;
		}
		return CategoryTab.ALL;
	}

	private static String tabToKey(final CategoryTab tab) {
		if (tab == CategoryTab.FLAG) {
			return "flag";
		}
		if (tab == CategoryTab.REMINDER) {
			return "reminder";
		}
		if (tab == CategoryTab.TODO) {
			return "todo";
		}
		if (tab == CategoryTab.POMODORO) {
			return "pomodoro";
		}
		return "all";
	}

	private static final class RoundedPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		private final int arc;
		private Color fill;
		private Color border;
		private final boolean drawShadow;
		private boolean accent;

		RoundedPanel(final int arc, final Color fill, final Color border, final boolean drawShadow) {
			this.arc = arc;
			this.fill = fill;
			this.border = border;
			this.drawShadow = drawShadow;
			setOpaque(false);
		}

		void setColors(final Color fill, final Color border) {
			this.fill = fill;
			this.border = border;
			repaint();
		}

		void setAccent(final boolean accent) {
			this.accent = accent;
			repaint();
		}

		protected void paintComponent(final Graphics g) {
			final Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			final int w = getWidth();
			final int h = getHeight();
			if (drawShadow) {
				g2.setColor(CARD_SHADOW);
				g2.fillRoundRect(2, 3, Math.max(1, w - 4), Math.max(1, h - 4), arc, arc);
			}
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, Math.max(1, w - 3), Math.max(1, h - 3), arc, arc);
			g2.setColor(border);
			g2.drawRoundRect(0, 0, Math.max(1, w - 4), Math.max(1, h - 4), arc, arc);
			if (accent) {
				g2.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), 90));
				g2.drawRoundRect(1, 1, Math.max(1, w - 6), Math.max(1, h - 6), arc - 2, arc - 2);
			}
			g2.dispose();
			super.paintComponent(g);
		}
	}
}
