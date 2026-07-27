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
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.workspace.components.favorites.TagChipFactory;
import org.freeplane.plugin.workspace.components.favorites.WrapFlowLayout;
import org.freeplane.plugin.workspace.components.overlay.FillWidthScrollPanel;
import org.freeplane.plugin.workspace.components.overlay.FloatingOverlayInteraction;
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
	private static final int PILL_ARC = 20;
	private static final int DEFAULT_WIDTH = 300;
	private static final int MIN_WIDTH = 220;
	private static final int MAX_WIDTH = 520;
	private static final int MIN_EXPANDED_HEIGHT = 200;
	private static final int MAX_EXPANDED_HEIGHT = 10000;
	private static final int RESIZE_HANDLE = 14;
	private static final int COLLAPSED_PAD_X = 12;
	private static final int COLLAPSED_PAD_Y = 5;
	private static final int COLLAPSED_GAP = 6;

	/** Soft slate card — not stark white. */
	private static final Color CARD_BG = new Color(0xF7, 0xF8, 0xFA);
	private static final Color CARD_BORDER = new Color(0xCB, 0xD5, 0xE1);
	private static final Color CARD_SHADOW = new Color(15, 23, 42, 28);
	private static final Color PILL_BG = new Color(0xFF, 0xFF, 0xFF);
	private static final Color PILL_BORDER = new Color(0x94, 0xA3, 0xB8);
	private static final Color PILL_ACTIVE_BG = new Color(0xEF, 0xF6, 0xFF);
	private static final Color PILL_ACTIVE_BORDER = new Color(0x3B, 0x82, 0xF6);
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
	private final JToggleButton viewToggle;
	private final JToggleButton includeToggle;
	private final JToggleButton excludeToggle;
	private final JToggleButton allToggle;
	private final JTextField searchField;
	private final JPanel chipHost;
	private final JScrollPane chipScroll;
	private final JPanel nodeListHost;
	private final JScrollPane nodeScroll;
	private final JToggleButton untaggedToggle;
	private final JLabel statusLabel;
	private final JButton clearButton;
	private final JButton collapseButton;
	private final JButton homeButton;
	private final JPanel resizeCorner;

	private boolean expanded;
	private String searchQuery = "";
	private List availableTags = new ArrayList();
	private java.util.Map tagCounts = new java.util.LinkedHashMap();
	private boolean rebuilding;
	private LayoutListener layoutListener;

	private int userWidth = DEFAULT_WIDTH;
	private int userHeight = 300;
	private boolean userSized;
	private boolean positionDirty;
	private boolean homeRequested;
	private Dimension lastHostedSize;

	private Point dragStartOnScreen;
	private Point dragStartLocation;
	private boolean dragging;
	private boolean resizing;
	private Dimension resizeStartSize;

	public MapTagFilterPanel() {
		setOpaque(false);
		setLayout(new BorderLayout());

		collapsedTitle = new JLabel("标签");
		collapsedTitle.setFont(DocearUiTheme.font(12f, Font.BOLD));
		collapsedTitle.setForeground(MODE_ON_BG);
		collapsedTitle.setBorder(null);

		collapsedBadge = new JLabel();
		collapsedBadge.setFont(DocearUiTheme.font(10.5f, Font.BOLD));
		collapsedBadge.setForeground(Color.WHITE);
		collapsedBadge.setOpaque(true);
		collapsedBadge.setBackground(PILL_ACTIVE_BORDER);
		collapsedBadge.setBorder(new EmptyBorder(1, 5, 1, 5));
		collapsedBadge.setVisible(false);

		collapsedBar = buildCollapsedBar();
		expandedCard = buildExpandedShell();
		headerBar = buildHeaderBar();

		viewToggle = createModeButton("查看");
		includeToggle = createModeButton("仅看");
		excludeToggle = createModeButton("排除");
		allToggle = createModeButton("同时包含");
		final ButtonGroup modeGroup = new ButtonGroup();
		modeGroup.add(viewToggle);
		modeGroup.add(includeToggle);
		modeGroup.add(excludeToggle);
		modeGroup.add(allToggle);
		wireModeToggle(viewToggle, TagFilterMode.VIEW);
		wireModeToggle(includeToggle, TagFilterMode.INCLUDE);
		wireModeToggle(excludeToggle, TagFilterMode.EXCLUDE);
		wireModeToggle(allToggle, TagFilterMode.ALL);

		searchField = buildSearchField();
		chipHost = new FillWidthScrollPanel(new WrapFlowLayout());
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

		nodeListHost = new FillWidthScrollPanel();
		nodeListHost.setOpaque(true);
		nodeListHost.setBackground(CHIP_AREA_BG);
		nodeListHost.setLayout(new BoxLayout(nodeListHost, BoxLayout.Y_AXIS));
		nodeListHost.setBorder(new EmptyBorder(4, 4, 4, 4));
		nodeScroll = new JScrollPane(nodeListHost);
		nodeScroll.setBorder(BorderFactory.createLineBorder(CARD_BORDER));
		nodeScroll.getViewport().setBackground(CHIP_AREA_BG);
		nodeScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		DocearUiTheme.styleScrollPane(nodeScroll);
		nodeScroll.setVisible(false);

		untaggedToggle = createModeButton("无标签：隐藏");
		statusLabel = DocearUiTheme.mutedLabel("");
		clearButton = DocearUiTheme.ghostButton("清除");
		collapseButton = DocearUiTheme.ghostButton("收起");
		homeButton = DocearUiTheme.ghostButton("归位");
		homeButton.setToolTipText("回到导图右上角默认位置");
		resizeCorner = buildResizeCorner();

		assembleExpandedCard();
		wireActions();
		installDragAndResize();
		installEdgeResize();

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

	private JPanel buildCollapsedBar() {
		final RoundedPanel bar = new RoundedPanel(PILL_ARC, PILL_BG, PILL_BORDER, true);
		// Tight horizontal pack — never stretch content across a wide host.
		bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
		bar.setBorder(new EmptyBorder(COLLAPSED_PAD_Y, COLLAPSED_PAD_X, COLLAPSED_PAD_Y + 2, COLLAPSED_PAD_X + 2));
		bar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		bar.add(collapsedTitle);
		bar.add(Box.createHorizontalStrut(COLLAPSED_GAP));
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
		final JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		headerActions.setOpaque(false);
		headerActions.add(homeButton);
		headerActions.add(collapseButton);
		headerBar.add(headerActions, BorderLayout.EAST);

		final JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBorder(new EmptyBorder(8, 10, 6, 10));

		final JPanel modeRow = new JPanel(new GridLayout(1, 4, 4, 0));
		modeRow.setOpaque(false);
		modeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		modeRow.add(viewToggle);
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
		nodeScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

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

		final JPanel centerStack = new JPanel();
		centerStack.setOpaque(false);
		centerStack.setLayout(new BoxLayout(centerStack, BoxLayout.Y_AXIS));
		centerStack.setAlignmentX(Component.LEFT_ALIGNMENT);
		centerStack.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
		centerStack.add(chipScroll);
		centerStack.add(Box.createVerticalStrut(6));
		centerStack.add(nodeScroll);

		body.add(modeRow);
		body.add(Box.createVerticalStrut(6));
		body.add(searchWrap);
		body.add(tagsLabel);
		body.add(centerStack);
		body.add(Box.createVerticalGlue());
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
				// Update chrome first so the click feels instant; filter runs after paint.
				MapTagFilterService.setModeStateOnly(map, mode);
				refreshSelectionState();
				rebuildChips();
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MapTagFilterService.applyFromExtension(map);
					}
				});
			}
		});
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
		clearButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final MapModel map = currentMap();
				if (map == null) {
					return;
				}
				MapTagFilterService.clearActiveModeTagsStateOnly(map);
				refreshSelectionState();
				rebuildChips();
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MapTagFilterService.applyFromExtension(map);
					}
				});
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
				MapTagFilterService.setShowUntaggedStateOnly(map, untaggedToggle.isSelected());
				refreshUntaggedLabel(untaggedToggle.isSelected());
				refreshStatus(TagFilterMapExtension.get(map));
				refreshCollapsed();
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						MapTagFilterService.applyFromExtension(map);
					}
				});
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

	private void installEdgeResize() {
		FloatingOverlayInteraction.install(this, new FloatingOverlayInteraction.Host() {
			public boolean isResizeEnabled() {
				return expanded;
			}

			public boolean isDragEnabled(final MouseEvent e) {
				return false;
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
				updateChipScrollSize();
				fireLayoutChanged();
			}

			public void onInteractionFinished() {
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
			// Drop any expanded preferred-size sticky value before measuring the pill.
			setPreferredSize(null);
			setMinimumSize(null);
			setMaximumSize(null);
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
		tagCounts = MapTagFilterService.collectMapTagCounts(map);
		availableTags = new ArrayList(tagCounts.keySet());
		if (expanded) {
			refreshSelectionState();
			rebuildChips();
			rebuildViewNodes();
			updateChipScrollSize();
		}
		else {
			// Collapsed pill only needs counts / active badge — skip chip + node-list rebuild.
			refreshCollapsed();
		}
		revalidate();
		repaint();
		fireLayoutChangedIfSizeChanged();
	}

	/**
	 * Fast path for mode / selection changes: no map-wide tag scan, no chip recreate.
	 */
	private void refreshSelectionState() {
		final MapModel map = currentMap();
		rebuilding = true;
		try {
			final TagFilterMapExtension extension = map != null ? TagFilterMapExtension.getOrCreate(map) : null;
			final TagFilterMode mode = extension != null ? extension.getMode() : TagFilterMode.INCLUDE;
			viewToggle.setSelected(mode == TagFilterMode.VIEW);
			includeToggle.setSelected(mode == TagFilterMode.INCLUDE);
			excludeToggle.setSelected(mode == TagFilterMode.EXCLUDE);
			allToggle.setSelected(mode == TagFilterMode.ALL);
			updateModeLook(viewToggle);
			updateModeLook(includeToggle);
			updateModeLook(excludeToggle);
			updateModeLook(allToggle);

			final boolean showUntagged = extension != null ? extension.isShowUntagged()
					: TagFilterMode.INCLUDE.defaultShowUntagged();
			untaggedToggle.setSelected(showUntagged);
			refreshUntaggedLabel(showUntagged);
			untaggedToggle.setVisible(mode != TagFilterMode.VIEW);

			syncChipSelection(extension);
			rebuildViewNodes();
			refreshCollapsed();
			refreshStatus(extension);
			updateChipScrollSize();
		}
		finally {
			rebuilding = false;
		}
		revalidate();
		repaint();
		if (!expanded) {
			fireLayoutChanged();
		}
	}

	private void syncChipSelection(final TagFilterMapExtension extension) {
		final Set selected = extension != null ? extension.getActiveTags() : java.util.Collections.EMPTY_SET;
		final Component[] comps = chipHost.getComponents();
		for (int i = 0; i < comps.length; i++) {
			if (!(comps[i] instanceof JToggleButton)) {
				continue;
			}
			final JToggleButton chip = (JToggleButton) comps[i];
			final Object tagObj = chip.getClientProperty("mapTag");
			final String tag = tagObj != null ? String.valueOf(tagObj) : chip.getText();
			final boolean isSelected = selected.contains(tag);
			if (chip.isSelected() != isSelected) {
				chip.setSelected(isSelected);
			}
			enhanceChipLook(chip, tag, isSelected);
		}
	}

	private void refreshUntaggedLabel(final boolean show) {
		untaggedToggle.setText(show ? "无标签：显示" : "无标签：隐藏");
		updateModeLook(untaggedToggle);
	}

	private void refreshStatus(final TagFilterMapExtension extension) {
		if (extension == null) {
			statusLabel.setText("未筛选");
			return;
		}
		if (extension.getMode() == TagFilterMode.VIEW) {
			statusLabel.setText(extension.getActiveTagCount() == 0 ? "选一个标签查看" : "查看节点列表");
			return;
		}
		if (!extension.hasActiveFilter()) {
			statusLabel.setText("未筛选");
			return;
		}
		statusLabel.setText("已选 " + extension.getActiveTagCount());
	}

	private void refreshCollapsed() {
		final MapModel map = currentMap();
		final TagFilterMapExtension extension = map != null ? TagFilterMapExtension.get(map) : null;
		final boolean filtering = extension != null && extension.hasActiveFilter();
		final boolean viewing = extension != null && extension.getMode() == TagFilterMode.VIEW
		        && extension.getActiveTagCount() > 0;
		final int totalTags = availableTags != null ? availableTags.size() : 0;
		final RoundedPanel pill = (RoundedPanel) collapsedBar;
		if (filtering || viewing) {
			collapsedBadge.setText(String.valueOf(extension.getActiveTagCount()));
			collapsedBadge.setVisible(true);
			collapsedTitle.setText(modeShortLabel(extension.getMode()));
			collapsedTitle.setForeground(new Color(0x1E, 0x3A, 0x8A));
			collapsedBar.setToolTipText(MapTagFilterService.summarizeActive(extension));
			pill.setColors(PILL_ACTIVE_BG, PILL_ACTIVE_BORDER);
			pill.setAccent(true);
		}
		else {
			collapsedBadge.setText(String.valueOf(totalTags));
			collapsedBadge.setVisible(totalTags > 0);
			collapsedTitle.setText("标签");
			collapsedTitle.setForeground(MODE_ON_BG);
			collapsedBar.setToolTipText(totalTags > 0 ? ("本图共 " + totalTags + " 个标签；点开展开")
			        : "按节点【标签】筛选；点开展开");
			pill.setColors(PILL_BG, PILL_BORDER);
			pill.setAccent(false);
		}
		if (collapsedBar.getComponentCount() >= 3) {
			collapsedBar.getComponent(1).setVisible(collapsedBadge.isVisible());
		}
		collapsedBar.invalidate();
		collapsedBar.revalidate();
		collapsedBar.repaint();
		if (!expanded) {
			applyCurrentSize();
		}
	}

	private static String modeShortLabel(final TagFilterMode mode) {
		if (mode == TagFilterMode.VIEW) {
			return "查看";
		}
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
			final int count = tagCountOf(tag);
			final String label = count > 0 ? (tag + " " + count) : tag;
			final JToggleButton chip = TagChipFactory.createFilterChip(label, tag, isSelected);
			chip.putClientProperty("mapTag", tag);
			enhanceChipLook(chip, tag, isSelected);
			chip.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					final MapModel current = currentMap();
					if (current == null) {
						return;
					}
					MapTagFilterService.toggleTagStateOnly(current, tag);
					refreshSelectionState();
					rebuildChips();
					rebuildViewNodes();
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							MapTagFilterService.applyFromExtension(current);
						}
					});
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

	private int tagCountOf(final String tag) {
		if (tagCounts == null || tag == null) {
			return 0;
		}
		final Object v = tagCounts.get(tag);
		return v instanceof Integer ? ((Integer) v).intValue() : 0;
	}

	private void rebuildViewNodes() {
		nodeListHost.removeAll();
		final MapModel map = currentMap();
		final TagFilterMapExtension extension = map != null ? TagFilterMapExtension.getOrCreate(map) : null;
		final boolean viewMode = extension != null && extension.getMode() == TagFilterMode.VIEW;
		nodeScroll.setVisible(viewMode);
		if (!viewMode) {
			nodeListHost.revalidate();
			nodeListHost.repaint();
			return;
		}
		final Set selected = extension.getActiveTags();
		if (selected.isEmpty()) {
			final JLabel tip = DocearUiTheme.mutedLabel("选择一个标签，在此列出对应节点");
			tip.setBorder(new EmptyBorder(12, 8, 12, 8));
			nodeListHost.add(tip);
		}
		else {
			final String tag = String.valueOf(selected.iterator().next());
			final List nodes = MapTagFilterService.collectNodesWithTag(map, tag);
			if (nodes.isEmpty()) {
				nodeListHost.add(DocearUiTheme.mutedLabel("没有带该标签的节点"));
			}
			else {
				for (int i = 0; i < nodes.size(); i++) {
					final NodeModel node = (NodeModel) nodes.get(i);
					nodeListHost.add(buildViewNodeRow(node, i));
				}
			}
		}
		nodeListHost.revalidate();
		nodeListHost.repaint();
		updateChipScrollSize();
	}

	private JPanel buildViewNodeRow(final NodeModel node, final int index) {
		final JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(true);
		row.setBackground(index % 2 == 0 ? DocearUiTheme.SURFACE : DocearUiTheme.SURFACE_SOFT);
		row.setBorder(new EmptyBorder(4, 8, 4, 8));
		row.setMaximumSize(new Dimension(Short.MAX_VALUE, 80));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		String text = node.getText() == null ? "" : org.freeplane.core.util.HtmlUtils.htmlToPlain(node.getText());
		text = text.replaceAll("\\s+", " ").trim();
		final JLabel label = new JLabel("<html><body style='width:220px'>" + escapeHtml(text) + "</body></html>");
		label.setFont(DocearUiTheme.font(12f));
		row.add(label, BorderLayout.CENTER);
		row.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					navigateToNode(node);
				}
			}
		});
		return row;
	}

	private void navigateToNode(final NodeModel node) {
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

	private static String escapeHtml(final String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
		final int panelW = Math.max(MIN_WIDTH, (expanded ? (userSized ? userWidth : DEFAULT_WIDTH) : DEFAULT_WIDTH) - 28);
		final int panelH = expanded ? Math.max(MIN_EXPANDED_HEIGHT, (userSized ? userHeight : 300)) : 80;
		final MapModel map = currentMap();
		final TagFilterMapExtension extension = map != null ? TagFilterMapExtension.get(map) : null;
		final boolean viewMode = extension != null && extension.getMode() == TagFilterMode.VIEW;
		if (viewMode) {
			final int chipH = Math.min(140, Math.max(72, panelH / 4));
			final int nodeH = Math.max(100, panelH - 210 - chipH);
			chipScroll.setPreferredSize(new Dimension(panelW, chipH));
			chipScroll.setMaximumSize(new Dimension(Short.MAX_VALUE, chipH));
			chipScroll.setVisible(true);
			nodeScroll.setVisible(true);
			nodeScroll.setPreferredSize(new Dimension(panelW, nodeH));
			nodeScroll.setMaximumSize(new Dimension(Short.MAX_VALUE, Integer.MAX_VALUE));
		}
		else {
			// Prefer content height; do not stretch chips into a huge empty white area.
			final int contentH = Math.max(72, chipHost.getPreferredSize().height + 8);
			final int chipH = Math.min(contentH, Math.max(72, panelH - 200));
			chipScroll.setPreferredSize(new Dimension(panelW, chipH));
			chipScroll.setMaximumSize(new Dimension(Short.MAX_VALUE, chipH));
			chipScroll.setVisible(true);
			nodeScroll.setVisible(false);
			nodeScroll.setPreferredSize(new Dimension(panelW, 0));
			nodeScroll.setMaximumSize(new Dimension(Short.MAX_VALUE, 0));
		}
	}

	private void applyCurrentSize() {
		final Dimension pref = getPreferredSize();
		if (!expanded) {
			// Collapsed pill must never keep an expanded width sticky.
			setPreferredSize(pref);
			setMinimumSize(pref);
			setMaximumSize(pref);
			setSize(pref);
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
		lastHostedSize = new Dimension(getPreferredSize());
		if (layoutListener != null) {
			layoutListener.onPanelLayoutChanged();
		}
		final java.awt.Container parent = getParent();
		if (parent != null) {
			parent.repaint();
		}
	}

	private void fireLayoutChangedIfSizeChanged() {
		final Dimension next = getPreferredSize();
		if (lastHostedSize != null && lastHostedSize.width == next.width && lastHostedSize.height == next.height
		        && !positionDirty && !homeRequested) {
			repaint();
			return;
		}
		fireLayoutChanged();
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
		return computeCollapsedSize();
	}

	private Dimension computeCollapsedSize() {
		final java.awt.FontMetrics fm = collapsedTitle.getFontMetrics(collapsedTitle.getFont());
		final String title = collapsedTitle.getText() != null ? collapsedTitle.getText() : "标签";
		int contentW = fm.stringWidth(title);
		int contentH = Math.max(fm.getHeight(), 14);
		if (collapsedBadge.isVisible() && collapsedBadge.getText() != null && collapsedBadge.getText().length() > 0) {
			final java.awt.FontMetrics bfm = collapsedBadge.getFontMetrics(collapsedBadge.getFont());
			final Insets bi = collapsedBadge.getInsets();
			contentW += COLLAPSED_GAP + bfm.stringWidth(collapsedBadge.getText()) + bi.left + bi.right;
			contentH = Math.max(contentH, bfm.getHeight() + bi.top + bi.bottom);
		}
		final Insets pad = collapsedBar.getInsets();
		// +3 for soft shadow drawn outside the fill.
		final int w = contentW + pad.left + pad.right + 3;
		final int h = contentH + pad.top + pad.bottom + 3;
		return new Dimension(Math.max(52, w), Math.max(28, h));
	}

	public Dimension getMinimumSize() {
		if (expanded) {
			return new Dimension(MIN_WIDTH, MIN_EXPANDED_HEIGHT);
		}
		return computeCollapsedSize();
	}

	public Dimension getMaximumSize() {
		if (expanded) {
			return new Dimension(MAX_WIDTH, MAX_EXPANDED_HEIGHT);
		}
		return computeCollapsedSize();
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
			this.accent = false;
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
				g2.fillRoundRect(2, 3, Math.max(1, w - 3), Math.max(1, h - 3), arc, arc);
			}
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, Math.max(1, w - 3), Math.max(1, h - 3), arc, arc);
			g2.setColor(accent ? border : border);
			g2.setStroke(new java.awt.BasicStroke(accent ? 1.6f : 1f));
			g2.drawRoundRect(0, 0, Math.max(1, w - 3), Math.max(1, h - 3), arc, arc);
			g2.dispose();
			super.paintComponent(g);
		}
	}
}
