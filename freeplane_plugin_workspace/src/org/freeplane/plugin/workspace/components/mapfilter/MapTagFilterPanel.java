package org.freeplane.plugin.workspace.components.mapfilter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.workspace.components.favorites.TagChipFactory;
import org.freeplane.plugin.workspace.components.favorites.WrapFlowLayout;
import org.freeplane.plugin.workspace.features.mapfilter.MapTagFilterService;
import org.freeplane.plugin.workspace.features.mapfilter.TagFilterMapExtension;
import org.freeplane.plugin.workspace.features.mapfilter.TagFilterMode;
import org.freeplane.plugin.workspace.features.nodepins.TagColorStore;

/**
 * Floating tag-filter card: collapsed pill / expanded panel, draggable & resizable.
 */
public class MapTagFilterPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	public interface LayoutListener {
		void onPanelLayoutChanged();
	}

	private static final int ARC = 14;
	private static final int DEFAULT_WIDTH = 300;
	private static final int MIN_WIDTH = 220;
	private static final int MAX_WIDTH = 520;
	private static final int MIN_EXPANDED_HEIGHT = 220;
	private static final int MAX_EXPANDED_HEIGHT = 560;
	private static final int RESIZE_HANDLE = 14;

	/** Soft slate card — not stark white. */
	private static final Color CARD_BG = new Color(0xF7, 0xF8, 0xFA);
	private static final Color CARD_BORDER = new Color(0xCB, 0xD5, 0xE1);
	private static final Color CARD_SHADOW = new Color(15, 23, 42, 28);
	private static final Color HEADER_BG = new Color(0xEE, 0xF2, 0xF6);
	private static final Color CHIP_AREA_BG = new Color(0xFF, 0xFF, 0xFF);
	private static final Color MODE_ON_BG = new Color(0x1E, 0x29, 0x3B);
	private static final Color MODE_ON_FG = Color.WHITE;
	private static final Color MODE_OFF_BG = new Color(0xE2, 0xE8, 0xF0);
	private static final Color MODE_OFF_FG = new Color(0x47, 0x55, 0x69);

	private final JPanel collapsedBar;
	private final JLabel collapsedTitle;
	private final JLabel collapsedBadge;
	private final JPanel expandedCard;
	private final JPanel headerBar;
	private final JToggleButton includeToggle;
	private final JToggleButton excludeToggle;
	private final JToggleButton allToggle;
	private final JTextField searchField;
	private final JPanel chipHost;
	private final JScrollPane chipScroll;
	private final JToggleButton untaggedToggle;
	private final JLabel statusLabel;
	private final JButton clearButton;
	private final JButton collapseButton;
	private final JPanel resizeCorner;

	private boolean expanded;
	private String searchQuery = "";
	private List availableTags = new ArrayList();
	private boolean rebuilding;
	private LayoutListener layoutListener;

	private int userWidth = DEFAULT_WIDTH;
	private int userHeight = 300;
	private boolean userSized;
	private boolean positionDirty;

	private Point dragStartOnScreen;
	private Point dragStartLocation;
	private boolean dragging;
	private boolean resizing;
	private Dimension resizeStartSize;

	public MapTagFilterPanel() {
		setOpaque(false);
		setLayout(new BorderLayout());

		collapsedTitle = new JLabel("标签");
		collapsedTitle.setFont(DocearUiTheme.font(12.5f, Font.BOLD));
		collapsedTitle.setForeground(DocearUiTheme.TEXT);

		collapsedBadge = new JLabel();
		collapsedBadge.setFont(DocearUiTheme.font(11f, Font.BOLD));
		collapsedBadge.setForeground(Color.WHITE);
		collapsedBadge.setOpaque(true);
		collapsedBadge.setBackground(MODE_ON_BG);
		collapsedBadge.setBorder(new EmptyBorder(1, 6, 1, 6));
		collapsedBadge.setVisible(false);

		collapsedBar = buildCollapsedBar();
		expandedCard = buildExpandedShell();
		headerBar = buildHeaderBar();

		includeToggle = createModeButton("仅看");
		excludeToggle = createModeButton("排除");
		allToggle = createModeButton("同时包含");
		final ButtonGroup modeGroup = new ButtonGroup();
		modeGroup.add(includeToggle);
		modeGroup.add(excludeToggle);
		modeGroup.add(allToggle);
		wireModeToggle(includeToggle, TagFilterMode.INCLUDE);
		wireModeToggle(excludeToggle, TagFilterMode.EXCLUDE);
		wireModeToggle(allToggle, TagFilterMode.ALL);

		searchField = buildSearchField();
		chipHost = new JPanel(new WrapFlowLayout());
		chipHost.setOpaque(true);
		chipHost.setBackground(CHIP_AREA_BG);
		chipHost.setBorder(new EmptyBorder(6, 6, 6, 6));

		chipScroll = new JScrollPane(chipHost);
		chipScroll.setBorder(BorderFactory.createLineBorder(CARD_BORDER));
		chipScroll.setOpaque(true);
		chipScroll.getViewport().setOpaque(true);
		chipScroll.getViewport().setBackground(CHIP_AREA_BG);
		chipScroll.setBackground(CHIP_AREA_BG);
		chipScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		chipScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		DocearUiTheme.styleScrollPane(chipScroll);

		untaggedToggle = createModeButton("无标签：隐藏");
		statusLabel = DocearUiTheme.mutedLabel("");
		clearButton = DocearUiTheme.ghostButton("清除");
		collapseButton = DocearUiTheme.ghostButton("收起");
		resizeCorner = buildResizeCorner();

		assembleExpandedCard();
		wireActions();
		installDragAndResize();

		expanded = false;
		add(collapsedBar, BorderLayout.CENTER);
		refreshFromCurrentMap();
	}

	public void setLayoutListener(final LayoutListener listener) {
		this.layoutListener = listener;
	}

	public boolean isUserSized() {
		return userSized;
	}

	/** True if the user just dragged the panel; overlay should keep that position. */
	public boolean takePositionDirty() {
		final boolean dirty = positionDirty;
		positionDirty = false;
		return dirty;
	}

	private JPanel buildCollapsedBar() {
		final RoundedPanel bar = new RoundedPanel(ARC, CARD_BG, CARD_BORDER, true);
		bar.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 6));
		bar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		bar.add(collapsedTitle);
		bar.add(collapsedBadge);
		bar.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() == 1 && !dragging) {
					setExpanded(true);
				}
			}
		});
		return bar;
	}

	private JPanel buildExpandedShell() {
		return new RoundedPanel(ARC, CARD_BG, CARD_BORDER, true);
	}

	private JPanel buildHeaderBar() {
		final JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(true);
		header.setBackground(HEADER_BG);
		header.setBorder(new EmptyBorder(8, 10, 8, 8));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
		final JLabel title = new JLabel("标签筛选");
		title.setFont(DocearUiTheme.font(13f, Font.BOLD));
		title.setForeground(DocearUiTheme.TEXT);
		header.add(title, BorderLayout.WEST);
		return header;
	}

	private JPanel buildResizeCorner() {
		final JPanel corner = new JPanel() {
			private static final long serialVersionUID = 1L;

			protected void paintComponent(final Graphics g) {
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new Color(0x94, 0xA3, 0xB8));
				final int w = getWidth();
				final int h = getHeight();
				for (int i = 0; i < 3; i++) {
					final int o = 3 + i * 3;
					g2.drawLine(w - 2, h - o, w - o, h - 2);
				}
				g2.dispose();
			}
		};
		corner.setOpaque(false);
		corner.setPreferredSize(new Dimension(RESIZE_HANDLE, RESIZE_HANDLE));
		corner.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
		corner.setToolTipText("拖动调整大小");
		return corner;
	}

	private void assembleExpandedCard() {
		expandedCard.setLayout(new BorderLayout());
		headerBar.add(collapseButton, BorderLayout.EAST);

		final JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBorder(new EmptyBorder(8, 10, 6, 10));

		final JPanel modeRow = new JPanel(new GridLayout(1, 3, 6, 0));
		modeRow.setOpaque(false);
		modeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		modeRow.add(includeToggle);
		modeRow.add(excludeToggle);
		modeRow.add(allToggle);

		final JLabel searchLabel = new JLabel("搜索");
		searchLabel.setFont(DocearUiTheme.font(11f));
		searchLabel.setForeground(DocearUiTheme.TEXT_MUTED);
		searchLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JPanel searchWrap = new JPanel(new BorderLayout(0, 4));
		searchWrap.setOpaque(false);
		searchWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchWrap.add(searchLabel, BorderLayout.NORTH);
		searchWrap.add(searchField, BorderLayout.CENTER);

		final JLabel tagsLabel = new JLabel("本图标签");
		tagsLabel.setFont(DocearUiTheme.font(11f));
		tagsLabel.setForeground(DocearUiTheme.TEXT_MUTED);
		tagsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		tagsLabel.setBorder(new EmptyBorder(8, 0, 4, 0));

		chipScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JPanel footer = new JPanel(new BorderLayout(8, 0));
		footer.setOpaque(false);
		footer.setAlignmentX(Component.LEFT_ALIGNMENT);
		footer.setBorder(new EmptyBorder(8, 0, 0, 0));
		untaggedToggle.setFont(DocearUiTheme.font(11f));
		footer.add(untaggedToggle, BorderLayout.WEST);

		final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		right.setOpaque(false);
		right.add(statusLabel);
		right.add(clearButton);
		footer.add(right, BorderLayout.EAST);

		modeRow.setMaximumSize(new Dimension(Short.MAX_VALUE, 36));
		searchWrap.setMaximumSize(new Dimension(Short.MAX_VALUE, 56));
		footer.setMaximumSize(new Dimension(Short.MAX_VALUE, 40));

		body.add(modeRow);
		body.add(Box.createVerticalStrut(6));
		body.add(searchWrap);
		body.add(tagsLabel);
		body.add(chipScroll);
		body.add(footer);

		final JPanel south = new JPanel(new BorderLayout());
		south.setOpaque(false);
		south.add(resizeCorner, BorderLayout.EAST);

		expandedCard.add(headerBar, BorderLayout.NORTH);
		expandedCard.add(body, BorderLayout.CENTER);
		expandedCard.add(south, BorderLayout.SOUTH);
	}

	private JToggleButton createModeButton(final String text) {
		final JToggleButton b = new JToggleButton(text);
		b.setFocusPainted(false);
		b.setOpaque(true);
		b.setFont(DocearUiTheme.font(12f, Font.BOLD));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setMargin(new Insets(6, 4, 6, 4));
		return b;
	}

	private JTextField buildSearchField() {
		final JTextField field = new JTextField();
		field.setFont(DocearUiTheme.font(12.5f));
		field.setForeground(DocearUiTheme.TEXT);
		field.setBackground(CHIP_AREA_BG);
		field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(CARD_BORDER),
				new EmptyBorder(6, 8, 6, 8)));
		field.putClientProperty("JTextField.placeholderText", "输入关键字过滤标签");
		return field;
	}

	private void wireModeToggle(final JToggleButton button, final TagFilterMode mode) {
		button.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (rebuilding) {
					return;
				}
				final MapModel map = currentMap();
				if (map == null) {
					return;
				}
				MapTagFilterService.setMode(map, mode);
				refreshFromCurrentMap();
			}
		});
	}

	private void wireActions() {
		collapseButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setExpanded(false);
			}
		});
		clearButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final MapModel map = currentMap();
				if (map == null) {
					return;
				}
				MapTagFilterService.clearActiveModeTags(map);
				refreshFromCurrentMap();
			}
		});
		untaggedToggle.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (rebuilding) {
					return;
				}
				final MapModel map = currentMap();
				if (map == null) {
					return;
				}
				MapTagFilterService.setShowUntagged(map, untaggedToggle.isSelected());
				refreshUntaggedLabel(untaggedToggle.isSelected());
			}
		});
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(final DocumentEvent e) {
				onSearchChanged();
			}

			public void removeUpdate(final DocumentEvent e) {
				onSearchChanged();
			}

			public void changedUpdate(final DocumentEvent e) {
				onSearchChanged();
			}
		});
	}

	private void onSearchChanged() {
		searchQuery = searchField.getText() != null ? searchField.getText().trim() : "";
		rebuildChips();
	}

	private void installDragAndResize() {
		final MouseAdapter drag = new MouseAdapter() {
			public void mousePressed(final MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					dragging = true;
					dragStartOnScreen = e.getLocationOnScreen();
					dragStartLocation = getLocation();
				}
			}

			public void mouseReleased(final MouseEvent e) {
				dragging = false;
			}
		};
		final MouseMotionAdapter dragMove = new MouseMotionAdapter() {
			public void mouseDragged(final MouseEvent e) {
				if (!dragging || dragStartOnScreen == null || dragStartLocation == null) {
					return;
				}
				final Point now = e.getLocationOnScreen();
				final int nx = dragStartLocation.x + (now.x - dragStartOnScreen.x);
				final int ny = dragStartLocation.y + (now.y - dragStartOnScreen.y);
				setLocation(Math.max(0, nx), Math.max(0, ny));
				positionDirty = true;
				fireLayoutChanged();
			}
		};
		headerBar.addMouseListener(drag);
		headerBar.addMouseMotionListener(dragMove);
		collapsedBar.addMouseListener(drag);
		collapsedBar.addMouseMotionListener(dragMove);

		resizeCorner.addMouseListener(new MouseAdapter() {
			public void mousePressed(final MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e) && expanded) {
					resizing = true;
					dragStartOnScreen = e.getLocationOnScreen();
					resizeStartSize = getSize();
				}
			}

			public void mouseReleased(final MouseEvent e) {
				resizing = false;
			}
		});
		resizeCorner.addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseDragged(final MouseEvent e) {
				if (!resizing || dragStartOnScreen == null || resizeStartSize == null) {
					return;
				}
				final Point now = e.getLocationOnScreen();
				final int nw = clamp(resizeStartSize.width + (now.x - dragStartOnScreen.x), MIN_WIDTH, MAX_WIDTH);
				final int nh = clamp(resizeStartSize.height + (now.y - dragStartOnScreen.y), MIN_EXPANDED_HEIGHT,
						MAX_EXPANDED_HEIGHT);
				userWidth = nw;
				userHeight = nh;
				userSized = true;
				setSize(nw, nh);
				revalidate();
				updateChipScrollSize();
				fireLayoutChanged();
			}
		});
	}

	public void setExpanded(final boolean expanded) {
		if (this.expanded == expanded) {
			fireLayoutChanged();
			return;
		}
		this.expanded = expanded;
		removeAll();
		if (expanded) {
			add(expandedCard, BorderLayout.CENTER);
			if (!userSized) {
				userWidth = DEFAULT_WIDTH;
				userHeight = 300;
			}
			refreshFromCurrentMap();
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					searchField.requestFocusInWindow();
					applyCurrentSize();
					fireLayoutChanged();
				}
			});
		}
		else {
			add(collapsedBar, BorderLayout.CENTER);
			refreshCollapsed();
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					applyCurrentSize();
					fireLayoutChanged();
				}
			});
		}
		revalidate();
		repaint();
		applyCurrentSize();
		fireLayoutChanged();
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void refreshFromCurrentMap() {
		final MapModel map = currentMap();
		availableTags = MapTagFilterService.collectMapTags(map);
		rebuilding = true;
		try {
			final TagFilterMapExtension extension = map != null ? TagFilterMapExtension.getOrCreate(map) : null;
			final TagFilterMode mode = extension != null ? extension.getMode() : TagFilterMode.INCLUDE;
			includeToggle.setSelected(mode == TagFilterMode.INCLUDE);
			excludeToggle.setSelected(mode == TagFilterMode.EXCLUDE);
			allToggle.setSelected(mode == TagFilterMode.ALL);
			updateModeLook(includeToggle);
			updateModeLook(excludeToggle);
			updateModeLook(allToggle);

			final boolean showUntagged = extension != null ? extension.isShowUntagged()
					: TagFilterMode.INCLUDE.defaultShowUntagged();
			untaggedToggle.setSelected(showUntagged);
			refreshUntaggedLabel(showUntagged);

			rebuildChips();
			refreshCollapsed();
			refreshStatus(extension);
			updateChipScrollSize();
		}
		finally {
			rebuilding = false;
		}
		revalidate();
		repaint();
		fireLayoutChanged();
	}

	private void refreshUntaggedLabel(final boolean show) {
		untaggedToggle.setText(show ? "无标签：显示" : "无标签：隐藏");
		updateModeLook(untaggedToggle);
	}

	private void refreshStatus(final TagFilterMapExtension extension) {
		if (extension == null || !extension.hasActiveFilter()) {
			statusLabel.setText("未筛选");
			return;
		}
		statusLabel.setText("已选 " + extension.getActiveTagCount());
	}

	private void refreshCollapsed() {
		final MapModel map = currentMap();
		final TagFilterMapExtension extension = map != null ? TagFilterMapExtension.get(map) : null;
		final boolean active = extension != null && extension.hasActiveFilter();
		if (active) {
			collapsedBadge.setText(String.valueOf(extension.getActiveTagCount()));
			collapsedBadge.setVisible(true);
			collapsedTitle.setText(modeShortLabel(extension.getMode()));
			collapsedBar.setToolTipText(MapTagFilterService.summarizeActive(extension));
			((RoundedPanel) collapsedBar).setAccent(true);
		}
		else {
			collapsedBadge.setVisible(false);
			collapsedTitle.setText("标签");
			collapsedBar.setToolTipText("按节点【标签】筛选；点开展开");
			((RoundedPanel) collapsedBar).setAccent(false);
		}
		collapsedBar.revalidate();
	}

	private static String modeShortLabel(final TagFilterMode mode) {
		if (mode == TagFilterMode.EXCLUDE) {
			return "排除";
		}
		if (mode == TagFilterMode.ALL) {
			return "同时";
		}
		return "仅看";
	}

	private void rebuildChips() {
		chipHost.removeAll();
		final MapModel map = currentMap();
		final TagFilterMapExtension extension = map != null ? TagFilterMapExtension.getOrCreate(map) : null;
		final Set selected = extension != null ? extension.getActiveTags() : java.util.Collections.EMPTY_SET;
		final String query = searchQuery == null ? "" : searchQuery.toLowerCase();

		int shown = 0;
		for (int i = 0; i < availableTags.size(); i++) {
			final String tag = String.valueOf(availableTags.get(i));
			if (query.length() > 0 && tag.toLowerCase().indexOf(query) < 0) {
				continue;
			}
			final boolean isSelected = selected.contains(tag);
			final JToggleButton chip = TagChipFactory.createFilterChip(tag, tag, isSelected);
			enhanceChipLook(chip, tag, isSelected);
			chip.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					final MapModel current = currentMap();
					if (current == null) {
						return;
					}
					MapTagFilterService.toggleTag(current, tag);
					refreshFromCurrentMap();
				}
			});
			chipHost.add(chip);
			shown++;
		}
		if (shown == 0) {
			final JLabel empty = DocearUiTheme.mutedLabel(availableTags.isEmpty() ? "当前导图还没有标签" : "无匹配标签");
			empty.setBorder(new EmptyBorder(10, 4, 10, 4));
			chipHost.add(empty);
		}
		chipHost.revalidate();
		chipHost.repaint();
	}

	private void enhanceChipLook(final JToggleButton chip, final String tag, final boolean selected) {
		final Color base = TagColorStore.getInstance().getColor(tag);
		if (selected) {
			chip.setFont(chip.getFont().deriveFont(Font.BOLD));
			TagChipFactory.applyChipStyle(chip, TagColorStore.darkerVariant(base, 0.82f), true);
			chip.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(MODE_ON_BG, 2),
					new EmptyBorder(2, 8, 2, 8)));
		}
		else {
			chip.setFont(chip.getFont().deriveFont(Font.PLAIN));
			final Color soft = new Color(Math.min(255, base.getRed() + 30), Math.min(255, base.getGreen() + 30),
					Math.min(255, base.getBlue() + 30));
			TagChipFactory.applyChipStyle(chip, soft, false);
			chip.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(new Color(0xCB, 0xD5, 0xE1)),
					new EmptyBorder(2, 8, 2, 8)));
		}
	}

	private void updateModeLook(final JToggleButton button) {
		if (button.isSelected()) {
			button.setBackground(MODE_ON_BG);
			button.setForeground(MODE_ON_FG);
			button.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		}
		else {
			button.setBackground(MODE_OFF_BG);
			button.setForeground(MODE_OFF_FG);
			button.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		}
	}

	private void updateChipScrollSize() {
		final int w = Math.max(MIN_WIDTH, (expanded ? (userSized ? userWidth : DEFAULT_WIDTH) : DEFAULT_WIDTH) - 28);
		final int h = expanded ? Math.max(80, (userSized ? userHeight : 300) - 180) : 80;
		final Dimension d = new Dimension(w, Math.min(220, h));
		chipScroll.setPreferredSize(d);
		chipScroll.setMaximumSize(new Dimension(Short.MAX_VALUE, d.height));
	}

	private void applyCurrentSize() {
		final Dimension pref = getPreferredSize();
		setSize(pref);
		setPreferredSize(pref);
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

	private MapModel currentMap() {
		try {
			return Controller.getCurrentController().getMap();
		}
		catch (final Exception e) {
			return null;
		}
	}

	private static int clamp(final int v, final int min, final int max) {
		return Math.max(min, Math.min(max, v));
	}

	public Dimension getPreferredSize() {
		if (expanded) {
			final int w = userSized ? userWidth : DEFAULT_WIDTH;
			final int h = userSized ? userHeight : 300;
			return new Dimension(clamp(w, MIN_WIDTH, MAX_WIDTH), clamp(h, MIN_EXPANDED_HEIGHT, MAX_EXPANDED_HEIGHT));
		}
		collapsedBar.doLayout();
		final Dimension bar = collapsedBar.getPreferredSize();
		int w = Math.max(72, bar.width + 4);
		int h = Math.max(30, bar.height);
		if (collapsedBadge.isVisible()) {
			w = Math.max(w, bar.width + 2);
		}
		return new Dimension(w, h);
	}

	public Dimension getMinimumSize() {
		if (expanded) {
			return new Dimension(MIN_WIDTH, MIN_EXPANDED_HEIGHT);
		}
		return getPreferredSize();
	}

	public Dimension getMaximumSize() {
		if (expanded) {
			return new Dimension(MAX_WIDTH, MAX_EXPANDED_HEIGHT);
		}
		return getPreferredSize();
	}

	private static final class RoundedPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		private final int arc;
		private final Color fill;
		private final Color border;
		private final boolean drawShadow;
		private boolean accent;

		RoundedPanel(final int arc, final Color fill, final Color border, final boolean drawShadow) {
			this.arc = arc;
			this.fill = fill;
			this.border = border;
			this.drawShadow = drawShadow;
			this.accent = false;
			setOpaque(false);
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
				g2.fillRoundRect(2, 3, w - 3, h - 3, arc, arc);
			}
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, w - 3, h - 3, arc, arc);
			g2.setColor(accent ? MODE_ON_BG : border);
			g2.drawRoundRect(0, 0, w - 3, h - 3, arc, arc);
			g2.dispose();
			super.paintComponent(g);
		}
	}
}
