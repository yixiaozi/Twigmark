package org.freeplane.plugin.workspace.components.tagfilter;

import org.freeplane.core.ui.theme.DocearUiTheme;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Child rows lead with 「全部」(subtree) and 「未分组」(direct tags only).
 * Optional root 「全部」 scope ({@link #ALL_SCOPE_ID}) shows every tag regardless of group.
 * <p>
 * Remembers the last subcategory under each parent (persisted with
 * {@code propActiveGroup + ".memory"}). Switching back to parent A restores A2 if that
 * was last used under A; the active group itself is restored on restart via
 * {@code propActiveGroup}.
 */
public class TagGroupCascadeBar extends JPanel {

	private static final long serialVersionUID = 1L;

	public static final String TAG_DND_PREFIX = "docear-tag:";
	/** Synthetic root scope: every tag (used by relationship graph). */
	public static final String ALL_SCOPE_ID = "all";

	private static final Color GROUP_SELECTED_BG = DocearUiTheme.ACCENT_WASH;
	private static final Color GROUP_SELECTED_BORDER = DocearUiTheme.ACCENT;
	private static final Color GROUP_PATH_BG = new Color(0xF1F8F7);
	private static final Color GROUP_PATH_BORDER = new Color(0x80CBC4);
	private static final Color GROUP_HOVER_BG = new Color(0xEEF3F6);
	private static final Color GROUP_IDLE_BORDER = new Color(0xD0D7DE);
	private static final Color GROUP_ACCENT = DocearUiTheme.ACCENT;
	private static final Color GROUP_MUTED_FG = DocearUiTheme.TEXT_MUTED;
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
	private final String propPathMemory;
	private final boolean includeAllScope;
	private final JPanel rowsPanel = new JPanel();
	private Listener listener;
	/** Optional handler that avoids nested-interface classloader issues for foreign plugins. */
	private Runnable selectionChangedAction;
	private Set availableTagsSnapshot = Collections.EMPTY_SET;
	private String activeGroupId = TagGroupStore.UNGROUPED_ID;
	/** When true, only tags assigned directly to {@link #activeGroupId} (not subgroups). */
	private boolean directOnly;
	/**
	 * Per-parent last selection: parentGroupId → "childId" or "childId!" (directOnly).
	 * Switching back to parent A restores the last child (e.g. A2) under A.
	 */
	private final Map pathMemory = new LinkedHashMap();

	public TagGroupCascadeBar(final TagGroupStore groupStore, final String propActiveGroup,
			final String propDirectOnly) {
		this(groupStore, propActiveGroup, propDirectOnly, false);
	}

	public TagGroupCascadeBar(final TagGroupStore groupStore, final String propActiveGroup,
			final String propDirectOnly, final boolean includeAllScope) {
		super(new java.awt.BorderLayout());
		this.groupStore = groupStore != null ? groupStore : TagGroupStore.getInstance();
		this.propActiveGroup = propActiveGroup;
		this.propDirectOnly = propDirectOnly;
		this.propPathMemory = propActiveGroup + ".memory";
		this.includeAllScope = includeAllScope;
		setOpaque(false);
		rowsPanel.setOpaque(false);
		rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
		rowsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		add(rowsPanel, java.awt.BorderLayout.CENTER);
		loadPathMemory();
		final String defaultGroup = includeAllScope ? ALL_SCOPE_ID : TagGroupStore.UNGROUPED_ID;
		activeGroupId = ResourceController.getResourceController().getProperty(propActiveGroup, defaultGroup);
		directOnly = "true".equalsIgnoreCase(
				ResourceController.getResourceController().getProperty(propDirectOnly, "false"));
		if (!isValidActiveGroup(activeGroupId)) {
			activeGroupId = defaultGroup;
			directOnly = false;
		}
		// Seed per-parent memory from the restored selection so A→A2 works after upgrade / first run.
		rememberSelectionAlongPath(activeGroupId, directOnly);
	}

	/** @deprecated use {@link #TagGroupCascadeBar(TagGroupStore, String, String)} */
	public TagGroupCascadeBar(final String propActiveGroup, final String propDirectOnly) {
		this(TagGroupStore.getInstance(), propActiveGroup, propDirectOnly);
	}

	public boolean isAllScope() {
		return ALL_SCOPE_ID.equals(activeGroupId);
	}

	public void setListener(final Listener listener) {
		this.listener = listener;
	}

	/**
	 * Bind callbacks without implementing {@link Listener}. Prefer this from other plugins
	 * (e.g. docear_plugin_core): nested {@code Listener} can fail with
	 * {@code NoClassDefFoundError} across Freeplane plugin classloaders.
	 */
	public void bind(final Runnable onSelectionChanged, final Set availableTags) {
		this.selectionChangedAction = onSelectionChanged;
		setAvailableTagsSnapshot(availableTags);
	}

	public void setAvailableTagsSnapshot(final Set availableTags) {
		this.availableTagsSnapshot = availableTags != null ? availableTags : Collections.EMPTY_SET;
	}

	public String getActiveGroupId() {
		return activeGroupId;
	}

	public boolean isDirectOnly() {
		return directOnly;
	}

	public void rebuild() {
		rowsPanel.removeAll();
		if (!isValidActiveGroup(activeGroupId)) {
			activeGroupId = includeAllScope ? ALL_SCOPE_ID : TagGroupStore.UNGROUPED_ID;
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
			else if (!groupStore.isUngrouped(parentId) && !ALL_SCOPE_ID.equals(parentId)
					&& parentId.equals(activeGroupId)) {
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
		if (isAllScope()) {
			return true;
		}
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
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(1, 0, 2, 0));
		if (parentIdForAdd != null) {
			row.add(createScopeButton(parentIdForAdd, false));
			// 「未分组」only when subgroups exist and there is at least one direct tag.
			if (!groupStore.isUngrouped(parentIdForAdd) && !groupIdsOnRow.isEmpty()) {
				final boolean directSelected = parentIdForAdd.equals(activeGroupId) && directOnly;
				if (directSelected || !collectScopeTags(parentIdForAdd, true).isEmpty()) {
					row.add(createScopeButton(parentIdForAdd, true));
				}
			}
		}
		else if (includeAllScope) {
			final boolean exact = isAllScope() && !directOnly;
			row.add(createAllScopeButton(exact));
		}
		for (final Iterator it = groupIdsOnRow.iterator(); it.hasNext();) {
			final String groupId = (String) it.next();
			// Hide root 「未分组」 when it has no maps/tags (unless it is the active filter).
			if (parentIdForAdd == null && groupStore.isUngrouped(groupId)) {
				final boolean ungroupedSelected = groupId.equals(activeGroupId) && !directOnly;
				if (!ungroupedSelected && collectScopeTags(groupId, false).isEmpty()) {
					continue;
				}
			}
			final boolean exact = groupId.equals(activeGroupId) && !directOnly;
			final boolean onPath = !exact && (groupId.equals(activeGroupId)
					|| (selectedOnRow != null && groupId.equals(selectedOnRow)));
			row.add(createGroupTabButton(groupId, exact, onPath));
		}
		row.add(createAddGroupButton(parentIdForAdd));
		rowsPanel.add(row);
	}

	private JToggleButton createAllScopeButton(final boolean exactSelected) {
		final int tagCount = resolveAvailableTags().size();
		final String label = TextUtils.getText("workspace.nodepins.group.all") + " " + tagCount;
		final JToggleButton tab = createStyledToggle(label, exactSelected, false);
		tab.setToolTipText(TextUtils.getText("workspace.nodepins.group.all"));
		tab.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				selectGroup(ALL_SCOPE_ID, false);
			}
		});
		return tab;
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
		// Empty groups stay visible but are not selectable (unless already current / on path).
		if (tagCount <= 0 && !exactSelected && !onPath) {
			tab.setEnabled(false);
			tab.setToolTipText(TextUtils.getText("workspace.nodepins.group.empty_tip"));
		}
		tab.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (tagCount <= 0 && !exactSelected) {
					tab.setSelected(false);
					return;
				}
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
		String targetId = groupId;
		boolean targetDirect = direct;
		if (!direct && shouldRestoreRememberedChild(groupId)) {
			final String[] remembered = resolveRememberedSelection(groupId);
			if (remembered != null) {
				targetId = remembered[0];
				targetDirect = "true".equals(remembered[1]);
			}
		}
		applyActiveSelection(targetId, targetDirect);
		rememberSelectionAlongPath(activeGroupId, directOnly);
		rebuild();
		fireSelectionChanged();
	}

	/**
	 * Select a group without restoring the remembered subcategory path.
	 * Used when revealing a map: jump to its first-level group (whole subtree),
	 * not a sibling subcategory that would still hide the map.
	 */
	public void selectGroupExact(final String groupId, final boolean direct) {
		applyActiveSelection(groupId, direct);
		rememberSelectionAlongPath(activeGroupId, directOnly);
		rebuild();
		fireSelectionChanged();
	}

	private void applyActiveSelection(final String groupId, final boolean direct) {
		if (ALL_SCOPE_ID.equals(groupId)) {
			activeGroupId = ALL_SCOPE_ID;
			directOnly = false;
		}
		else {
			activeGroupId = groupId != null ? groupId : TagGroupStore.UNGROUPED_ID;
			directOnly = direct && !groupStore.isUngrouped(activeGroupId);
		}
		// Skip persist during quit — shutdown hook would otherwise save「全部」over the real filter.
		if (org.freeplane.view.swing.map.MapViewController.isClosingAllMaps()) {
			return;
		}
		ResourceController.getResourceController().setProperty(propActiveGroup, activeGroupId);
		ResourceController.getResourceController().setProperty(propDirectOnly, directOnly ? "true" : "false");
	}

	/**
	 * Restore last child only when entering a branch from outside its path.
	 * Clicking an ancestor already on the path keeps navigate-up (e.g. A2 → A 全部).
	 * From 「全部」, always open the clicked parent itself — do not jump into the
	 * last remembered child (often empty after a new subcategory was just added).
	 */
	private boolean shouldRestoreRememberedChild(final String groupId) {
		if (groupId == null || ALL_SCOPE_ID.equals(groupId) || groupStore.isUngrouped(groupId)) {
			return false;
		}
		if (isAllScope()) {
			return false;
		}
		if (isOnActivePath(groupId)) {
			return false;
		}
		return !groupStore.getChildIds(groupId).isEmpty();
	}

	private boolean isOnActivePath(final String groupId) {
		if (groupId == null || isAllScope()) {
			return false;
		}
		if (groupId.equals(activeGroupId)) {
			return true;
		}
		return groupStore.isDescendantOf(activeGroupId, groupId);
	}

	/**
	 * Walk parent→remembered-child until a leaf intent (self / direct / no further memory).
	 * @return {groupId, "true"|"false"} or null to keep the clicked group
	 */
	private String[] resolveRememberedSelection(final String startGroupId) {
		String current = startGroupId;
		boolean currentDirect = false;
		final Set seen = new HashSet();
		while (current != null && seen.add(current)) {
			final String encoded = (String) pathMemory.get(current);
			if (encoded == null || encoded.length() == 0) {
				break;
			}
			final boolean rememberedDirect = encoded.endsWith("!");
			final String rememberedId = rememberedDirect ? encoded.substring(0, encoded.length() - 1) : encoded;
			if (rememberedId.length() == 0 || !isValidActiveGroup(rememberedId)) {
				pathMemory.remove(current);
				break;
			}
			if (rememberedId.equals(current)) {
				currentDirect = rememberedDirect;
				break;
			}
			if (!groupStore.isDescendantOf(rememberedId, current)) {
				pathMemory.remove(current);
				break;
			}
			current = rememberedId;
			currentDirect = rememberedDirect;
			if (currentDirect || groupStore.getChildIds(current).isEmpty()) {
				break;
			}
		}
		if (current == null || (current.equals(startGroupId) && !currentDirect)) {
			return null;
		}
		return new String[] { current, currentDirect ? "true" : "false" };
	}

	private void rememberSelectionAlongPath(final String selectedId, final boolean selectedDirect) {
		if (selectedId == null || ALL_SCOPE_ID.equals(selectedId)) {
			return;
		}
		final String encoded = selectedDirect ? selectedId + "!" : selectedId;
		pathMemory.put(selectedId, encoded);
		String parent = groupStore.getParentId(selectedId);
		final Set seen = new HashSet();
		seen.add(selectedId);
		while (parent != null && seen.add(parent)) {
			pathMemory.put(parent, encoded);
			parent = groupStore.getParentId(parent);
		}
		persistPathMemory();
	}

	private void loadPathMemory() {
		pathMemory.clear();
		final String raw = ResourceController.getResourceController().getProperty(propPathMemory, "");
		if (raw == null || raw.length() == 0) {
			return;
		}
		final String[] entries = raw.split(";");
		for (int i = 0; i < entries.length; i++) {
			final String entry = entries[i];
			if (entry == null || entry.length() == 0) {
				continue;
			}
			final int sep = entry.indexOf('>');
			if (sep <= 0 || sep >= entry.length() - 1) {
				continue;
			}
			final String parentId = entry.substring(0, sep);
			final String value = entry.substring(sep + 1);
			if (parentId.length() > 0 && value.length() > 0) {
				pathMemory.put(parentId, value);
			}
		}
	}

	private void persistPathMemory() {
		if (org.freeplane.view.swing.map.MapViewController.isClosingAllMaps()) {
			return;
		}
		final StringBuffer sb = new StringBuffer();
		for (final Iterator it = pathMemory.entrySet().iterator(); it.hasNext();) {
			final Map.Entry entry = (Map.Entry) it.next();
			final String parentId = (String) entry.getKey();
			final String value = (String) entry.getValue();
			if (parentId == null || value == null || parentId.length() == 0 || value.length() == 0) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(';');
			}
			sb.append(parentId).append('>').append(value);
		}
		ResourceController.getResourceController().setProperty(propPathMemory, sb.toString());
	}

	private boolean isValidActiveGroup(final String groupId) {
		if (groupId == null) {
			return false;
		}
		if (ALL_SCOPE_ID.equals(groupId)) {
			return includeAllScope;
		}
		return groupStore.getGroupIds().contains(groupId);
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
		if (isAllScope()) {
			return reverse;
		}
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
		final Set available = resolveAvailableTags();
		for (final Iterator it = available.iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			if (ALL_SCOPE_ID.equals(groupId)) {
				result.add(tag);
				continue;
			}
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

	private Set resolveAvailableTags() {
		if (listener != null) {
			final Set fromListener = listener.getAvailableTags();
			if (fromListener != null) {
				return fromListener;
			}
		}
		return availableTagsSnapshot;
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
		if (ALL_SCOPE_ID.equals(groupId)) {
			return TextUtils.getText("workspace.nodepins.group.all");
		}
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
		if (selectionChangedAction != null) {
			selectionChangedAction.run();
		}
	}
}
