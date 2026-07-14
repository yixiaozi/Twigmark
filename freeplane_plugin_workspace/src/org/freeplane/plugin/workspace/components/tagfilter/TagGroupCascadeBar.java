package org.freeplane.plugin.workspace.components.tagfilter;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.workspace.features.nodepins.TagGroupStore;

/**
 * Horizontal cascade rows for nested tag groups: one FlowLayout row per depth.
 * Child rows lead with 「全部」(subtree) and 「本级」(direct tags only).
 */
public class TagGroupCascadeBar extends JPanel {

	private static final long serialVersionUID = 1L;

	public static final String TAG_DND_PREFIX = "docear-tag:";

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

	public interface Listener {
		void selectionChanged();

		/** All known tag names in this sidebar scope (pins or favorites). */
		Set getAvailableTags();
	}

	private final TagGroupStore groupStore;
	private final String propActiveGroup;
	private final String propDirectOnly;
	private final JPanel rowsPanel = new JPanel();
	private Listener listener;
	private String activeGroupId = TagGroupStore.UNGROUPED_ID;
	/** When true, only tags assigned directly to {@link #activeGroupId} (not subgroups). */
	private boolean directOnly;

	public TagGroupCascadeBar(final TagGroupStore groupStore, final String propActiveGroup,
			final String propDirectOnly) {
		super(new java.awt.BorderLayout());
		this.groupStore = groupStore != null ? groupStore : TagGroupStore.getInstance();
		this.propActiveGroup = propActiveGroup;
		this.propDirectOnly = propDirectOnly;
		setOpaque(false);
		rowsPanel.setOpaque(false);
		rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
		rowsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		add(rowsPanel, java.awt.BorderLayout.CENTER);
		activeGroupId = ResourceController.getResourceController().getProperty(propActiveGroup,
				TagGroupStore.UNGROUPED_ID);
		directOnly = "true".equalsIgnoreCase(
				ResourceController.getResourceController().getProperty(propDirectOnly, "false"));
		if (!this.groupStore.getGroupIds().contains(activeGroupId)) {
			activeGroupId = TagGroupStore.UNGROUPED_ID;
			directOnly = false;
		}
	}

	/** @deprecated use {@link #TagGroupCascadeBar(TagGroupStore, String, String)} */
	public TagGroupCascadeBar(final String propActiveGroup, final String propDirectOnly) {
		this(TagGroupStore.getInstance(), propActiveGroup, propDirectOnly);
	}

	public void setListener(final Listener listener) {
		this.listener = listener;
	}

	public String getActiveGroupId() {
		return activeGroupId;
	}

	public boolean isDirectOnly() {
		return directOnly;
	}

	public void rebuild() {
		rowsPanel.removeAll();
		if (!groupStore.getGroupIds().contains(activeGroupId)) {
			activeGroupId = TagGroupStore.UNGROUPED_ID;
			directOnly = false;
		}
		final List path = getActiveGroupPath();
		addCascadeRow(groupStore.getRootGroupIds(), null, path.isEmpty() ? null : (String) path.get(0));
		for (int i = 0; i < path.size(); i++) {
			final String parentId = (String) path.get(i);
			final String selectedOnRow = (i + 1 < path.size()) ? (String) path.get(i + 1) : null;
			if (selectedOnRow != null) {
				addCascadeRow(groupStore.getChildIds(parentId), parentId, selectedOnRow);
			}
			else if (!groupStore.isUngrouped(parentId) && parentId.equals(activeGroupId)) {
				final List children = groupStore.getChildIds(parentId);
				if (directOnly && children.isEmpty()) {
					directOnly = false;
					ResourceController.getResourceController().setProperty(propDirectOnly, "false");
				}
				addCascadeRow(children, parentId, null);
			}
		}
		rowsPanel.revalidate();
		rowsPanel.repaint();
		revalidate();
		repaint();
	}

	public boolean tagMatchesActiveScope(final String tag) {
		final String tagGroupId = groupStore.getTagGroupId(tag);
		if (directOnly) {
			return activeGroupId.equals(tagGroupId);
		}
		return groupStore.isDescendantOf(tagGroupId, activeGroupId);
	}

	public List filterTagsInActiveScope(final List sortedTags) {
		final List filtered = new ArrayList();
		for (final Iterator it = sortedTags.iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			if (tagMatchesActiveScope(tag)) {
				filtered.add(tag);
			}
		}
		return filtered;
	}

	public void installChipPanelDropTarget(final JComponent panel) {
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
				fireSelectionChanged();
			}
		}, true);
	}

	public void appendMoveToGroupMenuItems(final JPopupMenu popup, final String tag, final Runnable after) {
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
					if (after != null) {
						after.run();
					}
				}
			});
			popup.add(moveItem);
		}
	}

	private void addCascadeRow(final List groupIdsOnRow, final String parentIdForAdd, final String selectedOnRow) {
		final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (parentIdForAdd != null) {
			// Nested level: slight tint so it differs from the root row.
			row.setOpaque(true);
			row.setBackground(new Color(0xEEF2F4));
			row.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
			row.add(createScopeButton(parentIdForAdd, false));
			// 「未分组」only when subgroups exist; otherwise 全部 already covers direct tags.
			if (!groupStore.isUngrouped(parentIdForAdd) && !groupIdsOnRow.isEmpty()) {
				row.add(createScopeButton(parentIdForAdd, true));
			}
		}
		else {
			row.setOpaque(false);
			row.setBorder(BorderFactory.createEmptyBorder(1, 0, 2, 0));
		}
		for (final Iterator it = groupIdsOnRow.iterator(); it.hasNext();) {
			final String groupId = (String) it.next();
			final boolean exact = groupId.equals(activeGroupId) && !directOnly;
			final boolean onPath = !exact && (groupId.equals(activeGroupId)
					|| (selectedOnRow != null && groupId.equals(selectedOnRow)));
			row.add(createGroupTabButton(groupId, exact, onPath));
		}
		row.add(createAddGroupButton(parentIdForAdd));
		rowsPanel.add(row);
	}

	private JToggleButton createScopeButton(final String parentGroupId, final boolean forDirectOnly) {
		final boolean selected = parentGroupId.equals(activeGroupId) && directOnly == forDirectOnly;
		final Set tagSet = collectScopeTags(parentGroupId, forDirectOnly);
		final int count = tagSet.size();
		final String base = TextUtils.getText(forDirectOnly ? "workspace.nodepins.group.direct"
				: "workspace.nodepins.group.all");
		final String tipKey = forDirectOnly ? "workspace.nodepins.group.direct.tip"
				: "workspace.nodepins.group.all.tip";
		final JToggleButton tab = createStyledToggle(base + " " + count, selected, false);
		tab.setToolTipText(TextUtils.format(tipKey, resolveGroupLabel(parentGroupId)));
		tab.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				selectGroup(parentGroupId, forDirectOnly);
			}
		});
		installGroupDropTarget(tab, parentGroupId);
		return tab;
	}

	private JToggleButton createGroupTabButton(final String groupId, final boolean exactSelected,
			final boolean onPath) {
		final int tagCount = collectScopeTags(groupId, false).size();
		final JToggleButton tab = createStyledToggle(resolveGroupLabel(groupId) + " " + tagCount, exactSelected,
				onPath);
		tab.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				selectGroup(groupId, false);
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
					showGroupPopup(e, groupId, tab);
				}
			}

			public void mouseReleased(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					showGroupPopup(e, groupId, tab);
				}
			}
		});
		installGroupDropTarget(tab, groupId);
		return tab;
	}

	private JToggleButton createStyledToggle(final String label, final boolean exactSelected, final boolean onPath) {
		final JToggleButton tab = new JToggleButton(label) {
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

	public void selectGroup(final String groupId, final boolean direct) {
		activeGroupId = groupId != null ? groupId : TagGroupStore.UNGROUPED_ID;
		directOnly = direct && !groupStore.isUngrouped(activeGroupId);
		ResourceController.getResourceController().setProperty(propActiveGroup, activeGroupId);
		ResourceController.getResourceController().setProperty(propDirectOnly, directOnly ? "true" : "false");
		rebuild();
		fireSelectionChanged();
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
			selectGroup(id, false);
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
			rebuild();
			fireSelectionChanged();
		}
	}

	private void showGroupPopup(final MouseEvent e, final String groupId, final JComponent source) {
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
					final int answer = JOptionPane.showConfirmDialog(TagGroupCascadeBar.this,
							TextUtils.getText("workspace.nodepins.group.delete.confirm"),
							TextUtils.getText("workspace.nodepins.group.delete"), JOptionPane.YES_NO_OPTION);
					if (answer != JOptionPane.YES_OPTION) {
						return;
					}
					final boolean clearActive = groupId.equals(activeGroupId)
							|| groupStore.isDescendantOf(activeGroupId, groupId);
					if (groupStore.removeGroup(groupId)) {
						if (clearActive) {
							selectGroup(TagGroupStore.UNGROUPED_ID, false);
						}
						else {
							rebuild();
							fireSelectionChanged();
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
					rebuild();
					fireSelectionChanged();
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
						rebuild();
						fireSelectionChanged();
					}
				}
			});
			menu.add(item);
		}
	}

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

	private Set collectScopeTags(final String groupId, final boolean onlyDirect) {
		final Set result = new HashSet();
		if (listener == null) {
			return result;
		}
		for (final Iterator it = listener.getAvailableTags().iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			final String tagGroupId = groupStore.getTagGroupId(tag);
			if (onlyDirect) {
				if (groupId.equals(tagGroupId)) {
					result.add(tag);
				}
			}
			else if (groupStore.isDescendantOf(tagGroupId, groupId)) {
				result.add(tag);
			}
		}
		return result;
	}

	private void installGroupDropTarget(final JComponent tab, final String groupId) {
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
				fireSelectionChanged();
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

	private String resolveGroupLabel(final String groupId) {
		if (groupStore.isUngrouped(groupId)) {
			return TextUtils.getText("workspace.nodepins.group.ungrouped");
		}
		final String name = groupStore.getGroupName(groupId);
		return name != null ? name : groupId;
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

	private void fireSelectionChanged() {
		if (listener != null) {
			listener.selectionChanged();
		}
	}
}
