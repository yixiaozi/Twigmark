package org.freeplane.plugin.workspace.components.nodepins;

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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.MapChangeEvent;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.url.UrlManager;
import org.freeplane.plugin.workspace.actions.MindMapOpenLocationAction;
import org.freeplane.plugin.workspace.components.favorites.TagChipFactory;
import org.freeplane.plugin.workspace.components.favorites.WrapFlowLayout;
import org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagService;
import org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagUtils;
import org.freeplane.plugin.workspace.features.nodepins.NodeMindMapActionUtils;
import org.freeplane.plugin.workspace.features.nodepins.NodePinEntry;
import org.freeplane.plugin.workspace.features.nodepins.NodePinNavigator;
import org.freeplane.plugin.workspace.features.nodepins.NodePinKeyUtils;
import org.freeplane.plugin.workspace.features.nodepins.NodePinsIndex;
import org.freeplane.plugin.workspace.features.nodepins.NodePinsMetricsPublisher;
import org.freeplane.plugin.workspace.features.nodepins.TagColorStore;
import org.freeplane.plugin.workspace.features.nodepins.TagGroupStore;

public class PinnedNodesTabPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final String FAVORITE_NAME_COLOR = "#0066CC";
	private static final int REFRESH_DEBOUNCE_MS = 200;
	private static final int DEFAULT_TAG_PANEL_HEIGHT = 160;
	private static final int MIN_TAG_PANEL_HEIGHT = 96;
	private static final int GROUP_ROW_HEIGHT = 30;
	private static final int GROUP_ROWS_MAX_HEIGHT = 140;
	private static final String PROP_FILTER_DIVIDER = "workspace.nodepins.filter.divider";
	private static final String PROP_ACTIVE_GROUP = "workspace.nodepins.filter.active.group";
	private static final String TAG_DND_PREFIX = "docear-tag:";

	private static final Color FILTER_SHELL_BG = new Color(0xF7F9FB);
	private static final Color FILTER_SHELL_BORDER = new Color(0xCFD8DC);
	private static final Color GROUP_SELECTED_BG = new Color(0xE3F2F1);
	private static final Color GROUP_SELECTED_BORDER = new Color(0x4DB6AC);
	private static final Color GROUP_PATH_BG = new Color(0xF1F8F7);
	private static final Color GROUP_PATH_BORDER = new Color(0x80CBC4);
	private static final Color GROUP_HOVER_BG = new Color(0xEEF3F6);
	private static final Color GROUP_IDLE_BORDER = new Color(0xD0D7DE);
	private static final Color GROUP_ACCENT = new Color(0x00897B);
	private static final Color GROUP_MUTED_FG = new Color(0x546E7A);
	private static final Color GROUP_FG = new Color(0x263238);
	private static final Color ADD_BUTTON_BG = new Color(0xECEFF1);

	private final ModeController modeController;
	private final NodePinsIndex index = NodePinsIndex.getInstance();
	private final TagGroupStore groupStore = TagGroupStore.getInstance();
	private final DefaultListModel listModel = new DefaultListModel();
	private final JList entryList = new JList(listModel);
	private final TagFilterPanel tagFilterPanel = new TagFilterPanel();
	private final JPanel groupTabBar = new JPanel();
	private JScrollPane groupTabScroll;
	private JPanel filterShell;
	private JSplitPane splitPane;
	private JScrollPane tagScrollPane;
	private String activeFilter = null;
	private String activeGroupId = TagGroupStore.UNGROUPED_ID;
	private final Timer refreshDebounceTimer;

	{
		refreshDebounceTimer = new Timer(REFRESH_DEBOUNCE_MS, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				refreshViewNow();
			}
		});
		refreshDebounceTimer.setRepeats(false);
	}

	private final Runnable refreshListener = new Runnable() {
		public void run() {
			scheduleRefreshView();
		}
	};

	private final INodeChangeListener nodeChangeListener = new INodeChangeListener() {
		public void nodeChanged(final NodeChangeEvent event) {
			if (event.getNode() == null) {
				return;
			}
			if (!NodeModel.NODE_TEXT.equals(event.getProperty())) {
				return;
			}
			PinnedNodesTabPanel.this.index.updateFromNode(event.getNode());
		}
	};

	private final IMapChangeListener mapChangeListener = new IMapChangeListener() {
		public void mapChanged(final MapChangeEvent event) {
			// Style/filter/background changes must not trigger a full-project scan.
			if (event != null && UrlManager.MAP_URL.equals(event.getProperty())) {
				index.scheduleRescan();
			}
		}

		public void onPreNodeDelete(final NodeModel oldParent, final NodeModel selectedNode, final int index) {
		}

		public void onNodeInserted(final NodeModel parent, final NodeModel child, final int newIndex) {
		}

		public void onNodeDeleted(final NodeModel parent, final NodeModel child, final int index) {
			final String key = NodePinKeyUtils.fromNode(child);
			if (key != null) {
				PinnedNodesTabPanel.this.index.removeByKey(key);
			}
		}

		public void onPreNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
				final NodeModel child, final int newIndex) {
		}

		public void onNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
				final NodeModel child, final int newIndex) {
		}
	};

	public PinnedNodesTabPanel(final ModeController modeController) {
		super(new BorderLayout(0, 4));
		this.modeController = modeController;
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		activeGroupId = ResourceController.getResourceController().getProperty(PROP_ACTIVE_GROUP,
				TagGroupStore.UNGROUPED_ID);
		if (!groupStore.getGroupIds().contains(activeGroupId)) {
			activeGroupId = TagGroupStore.UNGROUPED_ID;
		}
		filterShell = buildFilterShell();
		buildEntryList();
		final JScrollPane listScrollPane = new JScrollPane(entryList);
		listScrollPane.setMinimumSize(new Dimension(80, 80));
		splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filterShell, listScrollPane);
		splitPane.setResizeWeight(0);
		splitPane.setContinuousLayout(true);
		splitPane.setOneTouchExpandable(false);
		splitPane.setBorder(BorderFactory.createEmptyBorder());
		final int savedHeight = ResourceController.getResourceController().getIntProperty(PROP_FILTER_DIVIDER,
				DEFAULT_TAG_PANEL_HEIGHT);
		splitPane.setDividerLocation(Math.max(MIN_TAG_PANEL_HEIGHT, savedHeight));
		splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new java.beans.PropertyChangeListener() {
			public void propertyChange(final java.beans.PropertyChangeEvent evt) {
				if (evt.getNewValue() instanceof Integer) {
					final int location = ((Integer) evt.getNewValue()).intValue();
					if (location >= MIN_TAG_PANEL_HEIGHT) {
						ResourceController.getResourceController().setProperty(PROP_FILTER_DIVIDER,
								String.valueOf(location));
					}
					tagFilterPanel.revalidate();
					tagFilterPanel.repaint();
				}
			}
		});
		add(splitPane, BorderLayout.CENTER);
		index.addChangeListener(refreshListener);
		modeController.getMapController().addNodeChangeListener(nodeChangeListener);
		modeController.getMapController().addMapChangeListener(mapChangeListener);
		index.rescan();
	}

	private JPanel buildFilterShell() {
		final JPanel shell = new JPanel(new BorderLayout(0, 4));
		shell.setBackground(FILTER_SHELL_BG);
		shell.setOpaque(true);
		shell.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(FILTER_SHELL_BORDER),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		shell.setMinimumSize(new Dimension(80, MIN_TAG_PANEL_HEIGHT));
		final JPanel header = new JPanel(new BorderLayout(0, 4));
		header.setOpaque(false);
		final JLabel titleLabel = new JLabel(TextUtils.getText("workspace.nodepins.filter.label"));
		titleLabel.setForeground(GROUP_MUTED_FG);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize2D()));
		titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 0));
		header.add(titleLabel, BorderLayout.NORTH);
		groupTabBar.setOpaque(false);
		groupTabBar.setLayout(new BoxLayout(groupTabBar, BoxLayout.Y_AXIS));
		rebuildGroupTabs();
		groupTabScroll = new JScrollPane(groupTabBar, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		groupTabScroll.setBorder(BorderFactory.createEmptyBorder());
		groupTabScroll.setOpaque(false);
		groupTabScroll.getViewport().setOpaque(false);
		header.add(groupTabScroll, BorderLayout.CENTER);
		shell.add(header, BorderLayout.NORTH);
		tagScrollPane = new JScrollPane(tagFilterPanel);
		tagScrollPane.setBorder(BorderFactory.createEmptyBorder());
		tagScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		tagScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		tagScrollPane.getViewport().setOpaque(false);
		tagScrollPane.setOpaque(false);
		tagScrollPane.getViewport().addComponentListener(new ComponentAdapter() {
			public void componentResized(final ComponentEvent e) {
				tagFilterPanel.revalidate();
				tagFilterPanel.repaint();
			}
		});
		installChipPanelDropTarget(tagFilterPanel);
		shell.add(tagScrollPane, BorderLayout.CENTER);
		rebuildTagButtons();
		return shell;
	}

	/**
	 * Horizontal cascade: root groups on row 0; each deeper level of the active path
	 * adds another FlowLayout row of siblings. Depth of path (+ child row) = number of rows.
	 */
	private void rebuildGroupTabs() {
		groupTabBar.removeAll();
		final List groupIds = groupStore.getGroupIds();
		if (!groupIds.contains(activeGroupId)) {
			activeGroupId = TagGroupStore.UNGROUPED_ID;
		}
		final List path = getActiveGroupPath();
		addGroupCascadeRow(groupStore.getRootGroupIds(), null, path.isEmpty() ? null : (String) path.get(0));
		for (int i = 0; i < path.size(); i++) {
			final String parentId = (String) path.get(i);
			final String selectedOnRow = (i + 1 < path.size()) ? (String) path.get(i + 1) : null;
			if (selectedOnRow != null) {
				addGroupCascadeRow(groupStore.getChildIds(parentId), parentId, selectedOnRow);
			}
			else if (!groupStore.isUngrouped(parentId) && parentId.equals(activeGroupId)) {
				// Selected leaf on this path: one more row for its children / "add subgroup".
				addGroupCascadeRow(groupStore.getChildIds(parentId), parentId, null);
			}
		}
		final int rows = groupTabBar.getComponentCount();
		final int height = Math.min(GROUP_ROWS_MAX_HEIGHT, Math.max(GROUP_ROW_HEIGHT, rows * GROUP_ROW_HEIGHT + 4));
		if (groupTabScroll != null) {
			groupTabScroll.setPreferredSize(new Dimension(10, height));
			groupTabScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, GROUP_ROWS_MAX_HEIGHT));
		}
		groupTabBar.revalidate();
		groupTabBar.repaint();
		if (filterShell != null) {
			filterShell.revalidate();
		}
	}

	/** Path from root custom/ungrouped ancestor down to {@link #activeGroupId} (inclusive). */
	private List getActiveGroupPath() {
		final List reverse = new ArrayList();
		String current = activeGroupId;
		final Set seen = new HashSet();
		while (current != null && seen.add(current)) {
			reverse.add(current);
			current = groupStore.getParentId(current);
		}
		Collections.reverse(reverse);
		return reverse;
	}

	private void addGroupCascadeRow(final List groupIdsOnRow, final String parentIdForAdd,
			final String selectedOnRow) {
		final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		// Child rows: leading "全部" selects the parent and shows the whole subtree.
		if (parentIdForAdd != null) {
			row.add(createGroupAllButton(parentIdForAdd));
		}
		for (final Iterator it = groupIdsOnRow.iterator(); it.hasNext();) {
			final String groupId = (String) it.next();
			final boolean exact = groupId.equals(activeGroupId);
			final boolean onPath = !exact && selectedOnRow != null && groupId.equals(selectedOnRow);
			row.add(createGroupTabButton(groupId, exact, onPath));
		}
		row.add(createAddGroupButton(parentIdForAdd));
		groupTabBar.add(row);
	}

	/**
	 * "全部" on a child cascade row — select the parent group and list tags in that
	 * group plus all nested subgroups.
	 */
	private JToggleButton createGroupAllButton(final String parentGroupId) {
		final boolean selected = parentGroupId.equals(activeGroupId);
		final int subtreeCount = countEntriesInGroupSubtree(parentGroupId);
		final String label = formatCountLabel(TextUtils.getText("workspace.nodepins.group.all"), subtreeCount);
		final JToggleButton tab = new JToggleButton(label) {
			private static final long serialVersionUID = 1L;

			protected void paintComponent(final Graphics g) {
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				final int arc = 12;
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
				g2.setColor(selected ? GROUP_SELECTED_BORDER : GROUP_IDLE_BORDER);
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
				if (selected) {
					g2.setColor(GROUP_ACCENT);
					g2.fillRoundRect(2, 3, 3, getHeight() - 6, 3, 3);
				}
				g2.dispose();
				super.paintComponent(g);
			}
		};
		tab.setSelected(selected);
		tab.setFocusable(false);
		tab.setContentAreaFilled(false);
		tab.setBorderPainted(false);
		tab.setOpaque(false);
		tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		tab.setMargin(new Insets(1, 8, 1, 8));
		tab.setToolTipText(TextUtils.format("workspace.nodepins.group.all.tip", resolveGroupLabel(parentGroupId)));
		styleGroupTab(tab, selected, false);
		tab.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				selectGroup(parentGroupId);
			}
		});
		installGroupTabDropTarget(tab, parentGroupId);
		return tab;
	}

	private JButton createAddGroupButton(final String parentId) {
		final boolean child = parentId != null;
		final JButton addButton = new JButton("+");
		addButton.setToolTipText(child
				? TextUtils.getText("workspace.nodepins.group.add.child")
				: TextUtils.getText("workspace.nodepins.group.add"));
		addButton.setFocusable(false);
		addButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addButton.setMargin(new Insets(1, 8, 1, 8));
		addButton.setOpaque(true);
		addButton.setBackground(ADD_BUTTON_BG);
		addButton.setForeground(GROUP_MUTED_FG);
		addButton.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(GROUP_IDLE_BORDER),
				BorderFactory.createEmptyBorder(2, 6, 2, 6)));
		addButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				promptAddGroup(parentId);
			}
		});
		return addButton;
	}

	private JToggleButton createGroupTabButton(final String groupId, final boolean exactSelected,
			final boolean onPath) {
		final JToggleButton tab = new JToggleButton(resolveGroupLabel(groupId)) {
			private static final long serialVersionUID = 1L;

			protected void paintComponent(final Graphics g) {
				final Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				final int arc = 12;
				g2.setColor(getBackground());
				g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
				final Color border = exactSelected ? GROUP_SELECTED_BORDER
						: (onPath ? GROUP_PATH_BORDER : GROUP_IDLE_BORDER);
				g2.setColor(border);
				g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
				if (exactSelected) {
					g2.setColor(GROUP_ACCENT);
					g2.fillRoundRect(2, 3, 3, getHeight() - 6, 3, 3);
				}
				g2.dispose();
				super.paintComponent(g);
			}
		};
		tab.setSelected(exactSelected);
		tab.setFocusable(false);
		tab.setContentAreaFilled(false);
		tab.setBorderPainted(false);
		tab.setOpaque(false);
		tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		tab.setMargin(new Insets(1, 8, 1, 8));
		styleGroupTab(tab, exactSelected, onPath);
		tab.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				selectGroup(groupId);
			}
		});
		tab.addMouseListener(new MouseAdapter() {
			public void mouseEntered(final MouseEvent e) {
				if (!exactSelected) {
					tab.setBackground(GROUP_HOVER_BG);
					tab.repaint();
				}
			}

			public void mouseExited(final MouseEvent e) {
				styleGroupTab(tab, exactSelected, onPath);
				tab.repaint();
			}

			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() == 2 && groupStore.canRename(groupId)) {
					promptRenameGroup(groupId);
				}
			}

			public void mousePressed(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					showGroupTabPopup(e, groupId, tab);
				}
			}

			public void mouseReleased(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					showGroupTabPopup(e, groupId, tab);
				}
			}
		});
		installGroupTabDropTarget(tab, groupId);
		return tab;
	}

	private void styleGroupTab(final JToggleButton tab, final boolean exactSelected, final boolean onPath) {
		if (exactSelected) {
			tab.setBackground(GROUP_SELECTED_BG);
			tab.setForeground(GROUP_ACCENT);
			tab.setFont(tab.getFont().deriveFont(Font.BOLD));
		}
		else if (onPath) {
			tab.setBackground(GROUP_PATH_BG);
			tab.setForeground(GROUP_ACCENT);
			tab.setFont(tab.getFont().deriveFont(Font.PLAIN));
		}
		else {
			tab.setBackground(Color.WHITE);
			tab.setForeground(GROUP_FG);
			tab.setFont(tab.getFont().deriveFont(Font.PLAIN));
		}
		tab.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
	}

	private String resolveGroupLabel(final String groupId) {
		if (groupStore.isUngrouped(groupId)) {
			return TextUtils.getText("workspace.nodepins.group.ungrouped");
		}
		final String name = groupStore.getGroupName(groupId);
		return name != null ? name : groupId;
	}

	private void selectGroup(final String groupId) {
		activeGroupId = groupId != null ? groupId : TagGroupStore.UNGROUPED_ID;
		ResourceController.getResourceController().setProperty(PROP_ACTIVE_GROUP, activeGroupId);
		activeFilter = null;
		rebuildGroupTabs();
		rebuildTagButtons();
		refreshList();
	}

	private void promptAddGroup(final String parentId) {
		final String title = parentId == null
				? TextUtils.getText("workspace.nodepins.group.add")
				: TextUtils.getText("workspace.nodepins.group.add.child");
		final String prompt = parentId == null
				? TextUtils.getText("workspace.nodepins.group.add.prompt")
				: TextUtils.format("workspace.nodepins.group.add.child.prompt", resolveGroupLabel(parentId));
		final String name = JOptionPane.showInputDialog(this, prompt, title, JOptionPane.PLAIN_MESSAGE);
		if (name == null) {
			return;
		}
		final String id = groupStore.addGroup(name, parentId);
		if (id != null) {
			selectGroup(id);
		}
	}

	private void promptRenameGroup(final String groupId) {
		if (!groupStore.canRename(groupId)) {
			return;
		}
		final String current = resolveGroupLabel(groupId);
		final String name = (String) JOptionPane.showInputDialog(this,
				TextUtils.getText("workspace.nodepins.group.rename.prompt"),
				TextUtils.getText("workspace.nodepins.group.rename"), JOptionPane.PLAIN_MESSAGE, null, null, current);
		if (name == null) {
			return;
		}
		if (groupStore.renameGroup(groupId, name)) {
			rebuildGroupTabs();
		}
	}

	private void showGroupTabPopup(final MouseEvent e, final String groupId, final JComponent source) {
		final JPopupMenu popup = new JPopupMenu();
		if (!groupStore.isUngrouped(groupId)) {
			final JMenuItem addChildItem = new JMenuItem(TextUtils.getText("workspace.nodepins.group.add.child"));
			addChildItem.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent event) {
					promptAddGroup(groupId);
				}
			});
			popup.add(addChildItem);
			final JMenuItem renameItem = new JMenuItem(TextUtils.getText("workspace.nodepins.group.rename"));
			renameItem.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent event) {
					promptRenameGroup(groupId);
				}
			});
			popup.add(renameItem);
			final JMenu moveMenu = new JMenu(TextUtils.getText("workspace.nodepins.group.move"));
			populateMoveGroupMenu(moveMenu, groupId);
			if (moveMenu.getItemCount() > 0) {
				popup.add(moveMenu);
			}
			final JMenuItem deleteItem = new JMenuItem(TextUtils.getText("workspace.nodepins.group.delete"));
			deleteItem.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent event) {
					final int answer = JOptionPane.showConfirmDialog(PinnedNodesTabPanel.this,
							TextUtils.getText("workspace.nodepins.group.delete.confirm"),
							TextUtils.getText("workspace.nodepins.group.delete"), JOptionPane.YES_NO_OPTION);
					if (answer != JOptionPane.YES_OPTION) {
						return;
					}
					final boolean clearActive = groupId.equals(activeGroupId)
							|| groupStore.isDescendantOf(activeGroupId, groupId);
					if (groupStore.removeGroup(groupId)) {
						if (clearActive) {
							selectGroup(TagGroupStore.UNGROUPED_ID);
						}
						else {
							rebuildGroupTabs();
							rebuildTagButtons();
						}
					}
				}
			});
			popup.add(deleteItem);
		}
		else {
			final JMenuItem addRootItem = new JMenuItem(TextUtils.getText("workspace.nodepins.group.add"));
			addRootItem.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent event) {
					promptAddGroup(null);
				}
			});
			popup.add(addRootItem);
		}
		popup.show(source, e.getX(), e.getY());
	}

	private void populateMoveGroupMenu(final JMenu menu, final String groupId) {
		final String currentParent = groupStore.getParentId(groupId);
		final JMenuItem toRoot = new JMenuItem(TextUtils.getText("workspace.nodepins.group.move.root"));
		toRoot.setEnabled(currentParent != null);
		toRoot.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				if (groupStore.moveGroup(groupId, null)) {
					rebuildGroupTabs();
				}
			}
		});
		menu.add(toRoot);
		final List targets = groupStore.getGroupIds();
		for (final Iterator it = targets.iterator(); it.hasNext();) {
			final String targetId = (String) it.next();
			if (TagGroupStore.UNGROUPED_ID.equals(targetId) || groupId.equals(targetId)) {
				continue;
			}
			if (groupStore.isDescendantOf(targetId, groupId)) {
				continue;
			}
			final String indent = buildDepthPrefix(groupStore.getDepth(targetId));
			final JMenuItem item = new JMenuItem(indent + resolveGroupLabel(targetId));
			item.setEnabled(!targetId.equals(currentParent));
			item.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent event) {
					if (groupStore.moveGroup(groupId, targetId)) {
						rebuildGroupTabs();
					}
				}
			});
			menu.add(item);
		}
	}

	private String buildDepthPrefix(final int depth) {
		if (depth <= 0) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			sb.append("  ");
		}
		sb.append("· ");
		return sb.toString();
	}

	private void rebuildTagButtons() {
		tagFilterPanel.removeAll();
		tagFilterPanel.add(createFilterButton(null, formatCountLabel(
				TextUtils.getText("workspace.nodepins.filter.all"), countEntriesInActiveGroup())));
		for (final Iterator it = getTagsForActiveGroup().iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			tagFilterPanel.add(createFilterButton(tag, formatCountLabel(tag, index.countWithTag(tag))));
		}
		tagFilterPanel.revalidate();
		tagFilterPanel.repaint();
		if (filterShell != null) {
			filterShell.revalidate();
			filterShell.repaint();
		}
		revalidate();
		repaint();
	}

	private List getTagsForActiveGroup() {
		return getTagsForGroupSubtree(activeGroupId);
	}

	/** Tags assigned to {@code groupId} or any nested subgroup under it. */
	private List getTagsForGroupSubtree(final String groupId) {
		final List tags = getTagsSortedByCount();
		final List filtered = new ArrayList();
		for (final Iterator it = tags.iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			final String tagGroupId = groupStore.getTagGroupId(tag);
			if (groupStore.isDescendantOf(tagGroupId, groupId)) {
				filtered.add(tag);
			}
		}
		return filtered;
	}

	/** Union of entries that carry any tag in the active group subtree. */
	private int countEntriesInActiveGroup() {
		return countEntriesInGroupSubtree(activeGroupId);
	}

	private int countEntriesInGroupSubtree(final String groupId) {
		final List groupTags = getTagsForGroupSubtree(groupId);
		if (groupTags.isEmpty()) {
			return 0;
		}
		final Set tagSet = new HashSet(groupTags);
		int count = 0;
		final List entries = index.getDisplayEntries(false, null);
		for (int i = 0; i < entries.size(); i++) {
			if (entryMatchesAnyTag((NodePinEntry) entries.get(i), tagSet)) {
				count++;
			}
		}
		return count;
	}

	private boolean entryMatchesAnyTag(final NodePinEntry entry, final Set tagSet) {
		if (entry == null || tagSet == null || tagSet.isEmpty()) {
			return false;
		}
		for (final Iterator it = entry.getTags().iterator(); it.hasNext();) {
			if (tagSet.contains(it.next())) {
				return true;
			}
		}
		return false;
	}

	private List getTagsSortedByCount() {
		final List tags = new ArrayList(index.getQuickSelectTags());
		Collections.sort(tags, new Comparator() {
			public int compare(final Object o1, final Object o2) {
				final String tag1 = (String) o1;
				final String tag2 = (String) o2;
				final int count1 = index.countWithTag(tag1);
				final int count2 = index.countWithTag(tag2);
				if (count1 != count2) {
					return count2 - count1;
				}
				return tag1.compareTo(tag2);
			}
		});
		return tags;
	}

	private String formatCountLabel(final String baseLabel, final int count) {
		return baseLabel + " " + count;
	}

	private JToggleButton createFilterButton(final String filterId, final String label) {
		final boolean selected = filterId == null ? activeFilter == null : filterId.equals(activeFilter);
		final JToggleButton button = TagChipFactory.createFilterChip(filterId, label, selected);
		button.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				activeFilter = filterId;
				rebuildTagButtons();
				refreshList();
			}
		});
		if (filterId != null) {
			enableTagDrag(button, filterId);
			button.addMouseListener(new MouseAdapter() {
				public void mousePressed(final MouseEvent e) {
					if (e.isPopupTrigger()) {
						showTagColorPopup(e, filterId, button);
					}
				}

				public void mouseReleased(final MouseEvent e) {
					if (e.isPopupTrigger()) {
						showTagColorPopup(e, filterId, button);
					}
				}
			});
		}
		return button;
	}

	private void enableTagDrag(final JComponent chip, final String tag) {
		final DragSource dragSource = DragSource.getDefaultDragSource();
		dragSource.createDefaultDragGestureRecognizer(chip, DnDConstants.ACTION_MOVE, new DragGestureListener() {
			public void dragGestureRecognized(final DragGestureEvent dge) {
				final Transferable transferable = new StringSelection(TAG_DND_PREFIX + tag);
				dge.startDrag(DragSource.DefaultMoveDrop, transferable);
			}
		});
	}

	private void installGroupTabDropTarget(final JComponent tab, final String groupId) {
		new DropTarget(tab, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
			public void drop(final DropTargetDropEvent dtde) {
				final String tag = extractDraggedTag(dtde);
				if (tag == null) {
					dtde.rejectDrop();
					return;
				}
				dtde.acceptDrop(DnDConstants.ACTION_MOVE);
				groupStore.setTagGroup(tag, groupId);
				dtde.dropComplete(true);
				rebuildTagButtons();
			}
		}, true);
	}

	private void installChipPanelDropTarget(final JComponent panel) {
		new DropTarget(panel, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
			public void drop(final DropTargetDropEvent dtde) {
				final String tag = extractDraggedTag(dtde);
				if (tag == null) {
					dtde.rejectDrop();
					return;
				}
				dtde.acceptDrop(DnDConstants.ACTION_MOVE);
				groupStore.setTagGroup(tag, activeGroupId);
				dtde.dropComplete(true);
				rebuildTagButtons();
			}
		}, true);
	}

	private String extractDraggedTag(final DropTargetDropEvent dtde) {
		try {
			final Transferable transferable = dtde.getTransferable();
			if (!transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
				return null;
			}
			final String data = (String) transferable.getTransferData(DataFlavor.stringFlavor);
			if (data == null || !data.startsWith(TAG_DND_PREFIX)) {
				return null;
			}
			final String tag = data.substring(TAG_DND_PREFIX.length());
			return tag.length() > 0 ? tag : null;
		}
		catch (final Exception e) {
			return null;
		}
	}

	private void showTagColorPopup(final MouseEvent e, final String tag, final JToggleButton button) {
		final JPopupMenu popup = new JPopupMenu();
		final JMenuItem setColorItem = new JMenuItem(TextUtils.getText("workspace.nodepins.action.set.color"));
		setColorItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				final Color current = TagColorStore.getInstance().getColor(tag);
				final Color chosen = JColorChooser.showDialog(PinnedNodesTabPanel.this,
						TextUtils.getText("workspace.nodepins.action.set.color"), current);
				if (chosen != null) {
					TagColorStore.getInstance().setColor(tag, chosen);
					rebuildTagButtons();
					refreshList();
				}
			}
		});
		popup.add(setColorItem);
		final JMenuItem resetColorItem = new JMenuItem(TextUtils.getText("workspace.nodepins.action.reset.color"));
		resetColorItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				TagColorStore.getInstance().clearColor(tag);
				rebuildTagButtons();
				refreshList();
			}
		});
		popup.add(resetColorItem);
		popup.addSeparator();
		final List groupIds = groupStore.getGroupIds();
		for (final Iterator it = groupIds.iterator(); it.hasNext();) {
			final String groupId = (String) it.next();
			final String indent = buildDepthPrefix(groupStore.getDepth(groupId));
			final JMenuItem moveItem = new JMenuItem(TextUtils.format("workspace.nodepins.group.move.to",
					indent + resolveGroupLabel(groupId)));
			moveItem.setEnabled(!groupId.equals(groupStore.getTagGroupId(tag)));
			moveItem.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent event) {
					groupStore.setTagGroup(tag, groupId);
					rebuildTagButtons();
				}
			});
			popup.add(moveItem);
		}
		popup.show(button, e.getX(), e.getY());
	}

	private void buildEntryList() {
		entryList.setCellRenderer(new NodePinEntryRenderer());
		entryList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() == 2) {
					openSelectedEntry();
				}
				else if (e.isPopupTrigger() || (e.getButton() == MouseEvent.BUTTON3 && e.getClickCount() == 1)) {
					showPopup(e);
				}
			}

			public void mousePressed(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					showPopup(e);
				}
			}

			public void mouseReleased(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					showPopup(e);
				}
			}
		});
	}

	private void showPopup(final MouseEvent e) {
		final int index = entryList.locationToIndex(e.getPoint());
		if (index < 0) {
			return;
		}
		entryList.setSelectedIndex(index);
		final NodePinEntry entry = (NodePinEntry) listModel.getElementAt(index);
		final JPopupMenu popup = new JPopupMenu();
		final JMenuItem openItem = new JMenuItem(TextUtils.getText("workspace.nodepins.action.open"));
		openItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				openEntry(entry);
			}
		});
		popup.add(openItem);
		final File mapFile = entry.getMapFile();
		if (mapFile != null) {
			final JMenuItem openLocationItem = new JMenuItem(TextUtils.getText("workspace.action.mindmap.open.location.label"));
			openLocationItem.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent event) {
					MindMapOpenLocationAction.openContainingFolder(mapFile);
				}
			});
			popup.add(openLocationItem);
		}
		popup.addSeparator();
		final JMenuItem removeItem = new JMenuItem(TextUtils.getText("workspace.nodepins.action.remove"));
		removeItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				NodeMindMapActionUtils.withNodeByKey(entry.getKey(), new NodeMindMapActionUtils.NodeRunnable() {
					public void run(final NodeModel node) {
						NodeDetailsTagService.removeAllManagedTags(node);
					}
				});
			}
		});
		popup.add(removeItem);
		popup.show(entryList, e.getX(), e.getY());
	}

	private void openSelectedEntry() {
		final NodePinEntry entry = (NodePinEntry) entryList.getSelectedValue();
		if (entry != null) {
			openEntry(entry);
		}
	}

	private void openEntry(final NodePinEntry entry) {
		if (entry != null) {
			NodePinNavigator.openNode(entry.getKey());
		}
	}

	public void refreshView() {
		scheduleRefreshView();
	}

	private void scheduleRefreshView() {
		refreshDebounceTimer.restart();
	}

	private void refreshViewNow() {
		rebuildGroupTabs();
		rebuildTagButtons();
		refreshList();
	}

	private void refreshList() {
		listModel.clear();
		final List entries;
		if (activeFilter != null) {
			entries = index.getDisplayEntries(false, activeFilter);
		}
		else {
			final List groupTags = getTagsForActiveGroup();
			final Set tagSet = new HashSet(groupTags);
			final List all = index.getDisplayEntries(false, null);
			entries = new ArrayList();
			for (int i = 0; i < all.size(); i++) {
				final NodePinEntry entry = (NodePinEntry) all.get(i);
				if (entryMatchesAnyTag(entry, tagSet)) {
					entries.add(entry);
				}
			}
		}
		for (int i = 0; i < entries.size(); i++) {
			listModel.addElement(resolveDisplayEntry((NodePinEntry) entries.get(i)));
		}
		// Always publish the full index so the side-tab badge stays "全部" count,
		// not the currently filtered subset.
		NodePinsMetricsPublisher.publishFromIndex();
	}

	private NodePinEntry resolveDisplayEntry(final NodePinEntry entry) {
		final NodeModel node = NodeMindMapActionUtils.resolveNodeByKey(entry.getKey());
		if (node == null) {
			return entry;
		}
		final Set userTags = NodeDetailsTagService.getUserTags(node);
		final String label = NodeDetailsTagUtils.extractNodeTitle(node.getText());
		String displayLabel = label.length() > 0 ? label : entry.getListNodeLabel();
		if (displayLabel.length() == 0 || displayLabel.startsWith("ID_")) {
			displayLabel = formatTagsText(userTags);
		}
		final NodePinEntry liveEntry = new NodePinEntry(entry.getKey(), userTags, false, displayLabel);
		liveEntry.setExists(true);
		return liveEntry;
	}

	private String formatTagsText(final Set tags) {
		final StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (final Iterator it = tags.iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			if (!first) {
				builder.append(", ");
			}
			builder.append(tag);
			first = false;
		}
		return builder.toString();
	}

	private class NodePinEntryRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		public Component getListCellRendererComponent(final JList list, final Object value, final int index,
				final boolean isSelected, final boolean cellHasFocus) {
			final JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value instanceof NodePinEntry) {
				final NodePinEntry entry = (NodePinEntry) value;
				label.setText(formatEntryLabelHtml(entry, isSelected));
			}
			return label;
		}

		private String formatEntryLabelHtml(final NodePinEntry entry, final boolean isSelected) {
			final String nodeName = escapeHtml(entry.getListNodeLabel());
			final String mapName = escapeHtml(stripMindMapExtension(entry.getMapDisplayName()));
			final StringBuilder html = new StringBuilder("<html>");
			if (!entry.exists() && !isSelected) {
				html.append("<b><font color='#999999'>").append(nodeName).append("</font></b>");
			}
			else if (isSelected) {
				html.append("<b>").append(nodeName).append("</b>");
			}
			else {
				html.append("<b><font color='").append(FAVORITE_NAME_COLOR).append("'>").append(nodeName).append("</font></b>");
			}
			if (mapName.length() > 0) {
				if (isSelected) {
					html.append("  <font size='2'>").append(mapName).append("</font>");
				}
				else if (!entry.exists()) {
					html.append("  <font color='#999999' size='2'>").append(mapName).append("</font>");
				}
				else {
					html.append("  <font color='#888888' size='2'>").append(mapName).append("</font>");
				}
			}
			if (!entry.getTags().isEmpty()) {
				html.append(' ');
				boolean first = true;
				for (final Iterator it = entry.getTags().iterator(); it.hasNext();) {
					final String tag = (String) it.next();
					if (!first) {
						html.append(' ');
					}
					first = false;
					final Color tagColor = TagColorStore.darkerVariant(TagColorStore.getInstance().getColor(tag), 0.55f);
					html.append("<font color='").append(TagColorStore.toHex(tagColor)).append("'>[")
							.append(escapeHtml(tag)).append("]</font>");
				}
			}
			html.append("</html>");
			return html.toString();
		}

		private String stripMindMapExtension(final String fileName) {
			if (fileName == null) {
				return "";
			}
			final String lower = fileName.toLowerCase();
			if (lower.endsWith(".mm")) {
				return fileName.substring(0, fileName.length() - 3);
			}
			if (lower.endsWith(".dcr")) {
				return fileName.substring(0, fileName.length() - 4);
			}
			return fileName;
		}

		private String escapeHtml(final String text) {
			if (text == null) {
				return "";
			}
			return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		}
	}

	/**
	 * Tag chip container that tracks the viewport width so chips reflow when
	 * the split pane or window is resized.
	 */
	private static final class TagFilterPanel extends JPanel implements Scrollable {
		private static final long serialVersionUID = 1L;

		TagFilterPanel() {
			super(new WrapFlowLayout());
			setOpaque(false);
		}

		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		public int getScrollableUnitIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
			return 16;
		}

		public int getScrollableBlockIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
			return orientation == SwingConstants.VERTICAL ? Math.max(16, visibleRect.height - 16)
					: Math.max(16, visibleRect.width - 16);
		}

		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		public boolean getScrollableTracksViewportHeight() {
			if (getParent() instanceof JComponent) {
				return getPreferredSize().height <= getParent().getHeight();
			}
			return false;
		}
	}
}
