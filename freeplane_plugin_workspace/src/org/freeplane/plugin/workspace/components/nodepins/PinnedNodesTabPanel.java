package org.freeplane.plugin.workspace.components.nodepins;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
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
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
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
import javax.swing.UIManager;

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
	private static final int DEFAULT_TAG_PANEL_HEIGHT = 120;
	private static final int MIN_TAG_PANEL_HEIGHT = 72;
	private static final String PROP_FILTER_DIVIDER = "workspace.nodepins.filter.divider";
	private static final String PROP_ACTIVE_GROUP = "workspace.nodepins.filter.active.group";
	private static final String TAG_DND_PREFIX = "docear-tag:";

	private final ModeController modeController;
	private final NodePinsIndex index = NodePinsIndex.getInstance();
	private final TagGroupStore groupStore = TagGroupStore.getInstance();
	private final DefaultListModel listModel = new DefaultListModel();
	private final JList entryList = new JList(listModel);
	private final TagFilterPanel tagFilterPanel = new TagFilterPanel();
	private final JPanel groupTabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
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
		final JPanel shell = new JPanel(new BorderLayout(0, 2));
		shell.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(0xBDBDBD)),
				BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		shell.setMinimumSize(new Dimension(80, MIN_TAG_PANEL_HEIGHT));
		final JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		final JLabel titleLabel = new JLabel(TextUtils.getText("workspace.nodepins.filter.label"));
		titleLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		header.add(titleLabel, BorderLayout.WEST);
		groupTabBar.setOpaque(false);
		rebuildGroupTabs();
		final JScrollPane tabScroll = new JScrollPane(groupTabBar, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		tabScroll.setBorder(BorderFactory.createEmptyBorder());
		tabScroll.setOpaque(false);
		tabScroll.getViewport().setOpaque(false);
		header.add(tabScroll, BorderLayout.CENTER);
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

	private void rebuildGroupTabs() {
		groupTabBar.removeAll();
		final List groupIds = groupStore.getGroupIds();
		if (!groupIds.contains(activeGroupId)) {
			activeGroupId = TagGroupStore.UNGROUPED_ID;
		}
		for (final Iterator it = groupIds.iterator(); it.hasNext();) {
			final String groupId = (String) it.next();
			groupTabBar.add(createGroupTabButton(groupId));
		}
		final JButton addButton = new JButton("+");
		addButton.setToolTipText(TextUtils.getText("workspace.nodepins.group.add"));
		addButton.setMargin(new Insets(1, 8, 1, 8));
		addButton.setFocusable(false);
		addButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				promptAddGroup();
			}
		});
		groupTabBar.add(addButton);
		groupTabBar.revalidate();
		groupTabBar.repaint();
	}

	private JToggleButton createGroupTabButton(final String groupId) {
		final boolean selected = groupId.equals(activeGroupId);
		final JToggleButton tab = new JToggleButton(resolveGroupLabel(groupId));
		tab.setSelected(selected);
		tab.setFocusable(false);
		tab.setMargin(new Insets(1, 8, 1, 8));
		styleGroupTab(tab, selected);
		tab.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				selectGroup(groupId);
			}
		});
		tab.addMouseListener(new MouseAdapter() {
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

	private void styleGroupTab(final JToggleButton tab, final boolean selected) {
		final Color bg = selected ? new Color(0xE3F2FD) : UIManager.getColor("Button.background");
		final Color fg = UIManager.getColor("Button.foreground");
		tab.setOpaque(true);
		tab.setBackground(bg != null ? bg : Color.WHITE);
		tab.setForeground(fg != null ? fg : Color.BLACK);
		tab.setFont(tab.getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN));
		tab.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(selected ? new Color(0x90CAF9) : new Color(0xBDBDBD)),
				BorderFactory.createEmptyBorder(2, 6, 2, 6)));
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
		rebuildGroupTabs();
		rebuildTagButtons();
	}

	private void promptAddGroup() {
		final String name = JOptionPane.showInputDialog(this,
				TextUtils.getText("workspace.nodepins.group.add.prompt"),
				TextUtils.getText("workspace.nodepins.group.add"), JOptionPane.PLAIN_MESSAGE);
		if (name == null) {
			return;
		}
		final String id = groupStore.addGroup(name);
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
		if (groupStore.canRename(groupId)) {
			final JMenuItem renameItem = new JMenuItem(TextUtils.getText("workspace.nodepins.group.rename"));
			renameItem.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent event) {
					promptRenameGroup(groupId);
				}
			});
			popup.add(renameItem);
		}
		if (groupStore.canRemove(groupId)) {
			final JMenuItem deleteItem = new JMenuItem(TextUtils.getText("workspace.nodepins.group.delete"));
			deleteItem.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent event) {
					final int answer = JOptionPane.showConfirmDialog(PinnedNodesTabPanel.this,
							TextUtils.getText("workspace.nodepins.group.delete.confirm"),
							TextUtils.getText("workspace.nodepins.group.delete"), JOptionPane.YES_NO_OPTION);
					if (answer == JOptionPane.YES_OPTION && groupStore.removeGroup(groupId)) {
						if (groupId.equals(activeGroupId)) {
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
		if (popup.getComponentCount() == 0) {
			return;
		}
		popup.show(source, e.getX(), e.getY());
	}

	private void rebuildTagButtons() {
		tagFilterPanel.removeAll();
		tagFilterPanel.add(createFilterButton(null, formatCountLabel(
				TextUtils.getText("workspace.nodepins.filter.all"), index.countAll())));
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
		final List tags = getTagsSortedByCount();
		final List filtered = new ArrayList();
		for (final Iterator it = tags.iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			if (activeGroupId.equals(groupStore.getTagGroupId(tag))) {
				filtered.add(tag);
			}
		}
		return filtered;
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
			final JMenuItem moveItem = new JMenuItem(TextUtils.format("workspace.nodepins.group.move.to",
					resolveGroupLabel(groupId)));
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
		final List entries = index.getDisplayEntries(false, activeFilter);
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
