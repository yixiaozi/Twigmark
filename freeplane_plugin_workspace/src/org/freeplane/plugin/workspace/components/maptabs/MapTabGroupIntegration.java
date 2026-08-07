package org.freeplane.plugin.workspace.components.maptabs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IDocumentTabView;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.main.application.MapViewTabOrder;
import org.freeplane.plugin.workspace.components.tagfilter.TagGroupCascadeBar;
import org.freeplane.plugin.workspace.features.nodepins.TagGroupStore;
import org.freeplane.view.swing.map.MapView;

/**
 * Nested group filter for bottom mind-map tabs (work / life / subgroups),
 * reusing {@link TagGroupStore} + {@link TagGroupCascadeBar}.
 * <p>
 * Assignment keys are normalized map file paths persisted in
 * {@code {dataRoot}/_data/map-tab-groups.properties}. 「全部」remains available
 * for manual browsing. Activating a map outside the active group switches the
 * cascade to that map’s <b>first-level</b> group (or 「未分组」), not 「全部」, so
 * the tab strip stays aligned with the map’s assignment.
 * <p>
 * Active group and per-parent subcategory memory are restored on restart via
 * {@link TagGroupCascadeBar} properties ({@code workspace.maptabs.group.active} etc.).
 */
public final class MapTabGroupIntegration {

	public static final String STORE_FILE = "map-tab-groups.properties";
	private static final String PROP_ACTIVE_GROUP = "workspace.maptabs.group.active";
	private static final String PROP_DIRECT_ONLY = "workspace.maptabs.group.direct";

	private static TagGroupCascadeBar cascade;
	private static boolean installed;
	private static boolean applyingFilter;

	private MapTabGroupIntegration() {
	}

	public static synchronized void install() {
		if (installed) {
			return;
		}
		final TagGroupStore store = TagGroupStore.getInstance(STORE_FILE);
		cascade = new TagGroupCascadeBar(store, PROP_ACTIVE_GROUP, PROP_DIRECT_ONLY, true);
		cascade.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
		cascade.setListener(new TagGroupCascadeBar.Listener() {
			public void selectionChanged() {
				applyFilter();
			}

			public Set getAvailableTags() {
				return collectOpenMapKeys();
			}
		});

		final JPanel chrome = new JPanel(new BorderLayout());
		chrome.setOpaque(false);
		chrome.add(cascade, BorderLayout.CENTER);

		MapViewTabOrder.setTabGroupChrome(chrome);
		MapViewTabOrder.setTabVisibilityFilter(new MapViewTabOrder.TabVisibilityFilter() {
			public boolean isVisible(final Component tabKey) {
				return isTabVisibleInActiveGroup(tabKey);
			}
		});
		MapViewTabOrder.setTabOutsideFilterHandler(new MapViewTabOrder.TabOutsideFilterHandler() {
			public boolean revealTab(final Component tabKey) {
				return expandFilterToMapGroup(tabKey);
			}

			public boolean revealAll() {
				return expandFilterForCurrentMapOrAll();
			}
		});
		MapViewTabOrder.setTabsChangedListener(new MapViewTabOrder.TabsChangedListener() {
			public void tabsChanged() {
				if (org.freeplane.view.swing.map.MapViewController.isClosingAllMaps()) {
					return;
				}
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						if (cascade != null && !applyingFilter
								&& !org.freeplane.view.swing.map.MapViewController.isClosingAllMaps()) {
							cascade.rebuild();
						}
					}
				});
			}
		});
		MapViewTabOrder.setTabPopupMenuProvider(new MapViewTabOrder.TabPopupMenuProvider() {
			public JPopupMenu createPopup(final Component tabKey, final int visibleTabIndex) {
				return createMoveToGroupPopup(tabKey);
			}
		});

		cascade.rebuild();
		applyFilter();
		installed = true;
	}

	/**
	 * Empty visible strip fallback: prefer the current map’s first-level group;
	 * only use 「全部」 when there is no current map to anchor on.
	 */
	private static boolean expandFilterForCurrentMapOrAll() {
		try {
			final Component current = Controller.getCurrentController().getMapViewManager()
					.getMapViewComponent();
			if (current != null) {
				return expandFilterToMapGroup(current);
			}
		}
		catch (Exception e) {
		}
		return expandFilterToAll();
	}

	/**
	 * Switch cascade to the map’s top-level group (subtree), or 「未分组」.
	 * Does not restore remembered subcategories — those could still hide the map.
	 */
	private static boolean expandFilterToMapGroup(final Component tabKey) {
		if (cascade == null) {
			return false;
		}
		// During quit, never rewrite the remembered group.
		if (org.freeplane.view.swing.map.MapViewController.isClosingAllMaps()) {
			return isTabVisibleInActiveGroup(tabKey);
		}
		final String mapKey = resolveAssignmentKey(tabKey);
		final String scopeKey = mapKey != null ? mapKey : "";
		if (!cascade.isAllScope() && cascade.tagMatchesActiveScope(scopeKey)) {
			return true;
		}
		final TagGroupStore store = TagGroupStore.getInstance(STORE_FILE);
		final String firstLevel = store.getRootAncestorId(store.getTagGroupId(scopeKey));
		final boolean wasApplying = applyingFilter;
		applyingFilter = false;
		try {
			cascade.selectGroupExact(firstLevel, false);
		}
		finally {
			applyingFilter = wasApplying;
		}
		return cascade.tagMatchesActiveScope(scopeKey);
	}

	private static boolean expandFilterToAll() {
		if (cascade == null) {
			return false;
		}
		// During quit, never expand — that would overwrite the remembered group
		// (shutdown hook persists properties after closeAllMaps).
		if (org.freeplane.view.swing.map.MapViewController.isClosingAllMaps()) {
			return cascade.isAllScope();
		}
		if (cascade.isAllScope()) {
			return true;
		}
		// Allow nested applyFilter when expanding from an empty-group rebuild.
		final boolean wasApplying = applyingFilter;
		applyingFilter = false;
		try {
			cascade.selectGroup(TagGroupCascadeBar.ALL_SCOPE_ID, false);
		}
		finally {
			applyingFilter = wasApplying;
		}
		return cascade.isAllScope();
	}

	private static void applyFilter() {
		if (applyingFilter) {
			return;
		}
		applyingFilter = true;
		try {
			MapViewTabOrder.refreshVisibleTabs();
			if (cascade != null) {
				cascade.rebuild();
			}
		}
		finally {
			applyingFilter = false;
		}
	}

	private static boolean isTabVisibleInActiveGroup(final Component tabKey) {
		if (cascade == null || cascade.isAllScope()) {
			return true;
		}
		final String key = resolveAssignmentKey(tabKey);
		// Untitled / non-map docs behave as ungrouped (empty key → TagGroupStore.UNGROUPED_ID).
		return cascade.tagMatchesActiveScope(key != null ? key : "");
	}

	private static JPopupMenu createMoveToGroupPopup(final Component tabKey) {
		final JPopupMenu popup = new JPopupMenu();
		final String key = resolveAssignmentKey(tabKey);
		if (key != null) {
			final TagGroupStore store = TagGroupStore.getInstance(STORE_FILE);
			final JMenuItem header = new JMenuItem(TextUtils.getText("workspace.maptabs.group.assign"));
			header.setEnabled(false);
			popup.add(header);
			popup.addSeparator();
			final List groupIds = store.getGroupIds();
			for (final Iterator it = groupIds.iterator(); it.hasNext();) {
				final String groupId = (String) it.next();
				final String indent = buildDepthPrefix(store.getDepth(groupId));
				final String label = resolveGroupLabel(store, groupId);
				final JMenuItem item = new JMenuItem(TextUtils.format("workspace.nodepins.group.move.to", indent + label));
				item.setEnabled(!groupId.equals(store.getTagGroupId(key)));
				item.addActionListener(new ActionListener() {
					public void actionPerformed(final ActionEvent e) {
						store.setTagGroup(key, groupId);
						applyFilter();
					}
				});
				popup.add(item);
			}
			popup.addSeparator();
		}
		final JMenuItem closeItem = new JMenuItem(TextUtils.getText("workspace.maptabs.close"));
		closeItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				closeTabKey(tabKey, false);
			}
		});
		popup.add(closeItem);
		final JMenuItem forceCloseItem = new JMenuItem(TextUtils.getText("workspace.maptabs.forceClose"));
		forceCloseItem.setToolTipText(TextUtils.getText("workspace.maptabs.forceClose.tip"));
		forceCloseItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				closeTabKey(tabKey, true);
			}
		});
		popup.add(forceCloseItem);
		return popup;
	}

	/**
	 * Close a bottom tab. With {@code force=true}, skips save dialogs and removes
	 * ghost tabs whose MapView is no longer registered.
	 */
	private static void closeTabKey(final Component tabKey, final boolean force) {
		if (tabKey == null) {
			return;
		}
		try {
			if (tabKey instanceof IDocumentTabView) {
				((IDocumentTabView) tabKey).requestClose(force);
				return;
			}
			if (tabKey instanceof MapView) {
				final Controller controller = Controller.getCurrentController();
				final IMapViewManager views = controller.getMapViewManager();
				final List registered = views.getMapViewVector();
				boolean stillOpen = false;
				if (registered != null) {
					for (int i = 0; i < registered.size(); i++) {
						if (registered.get(i) == tabKey) {
							stillOpen = true;
							break;
						}
					}
				}
				if (stillOpen) {
					views.changeToMapView(tabKey);
					views.close(force);
					return;
				}
				// Ghost tab: MapView gone from controller but label remains.
				MapViewTabOrder.forceRemoveTabKey(tabKey);
				return;
			}
			// Unknown / orphan placeholder
			MapViewTabOrder.forceRemoveTabKey(tabKey);
		}
		catch (Exception e) {
			MapViewTabOrder.forceRemoveTabKey(tabKey);
		}
	}

	private static String resolveGroupLabel(final TagGroupStore store, final String groupId) {
		if (store.isUngrouped(groupId)) {
			return TextUtils.getText("workspace.nodepins.group.ungrouped");
		}
		final String name = store.getGroupName(groupId);
		return name != null ? name : groupId;
	}

	private static String buildDepthPrefix(final int depth) {
		if (depth <= 0) {
			return "";
		}
		final StringBuffer sb = new StringBuffer();
		for (int i = 0; i < depth; i++) {
			sb.append("  ");
		}
		return sb.toString();
	}

	private static Set collectOpenMapKeys() {
		final Set keys = new HashSet();
		final List tabs = MapViewTabOrder.getAllTabKeysInOrder();
		for (final Iterator it = tabs.iterator(); it.hasNext();) {
			final String key = resolveAssignmentKey((Component) it.next());
			if (key != null) {
				keys.add(key);
			}
		}
		return keys;
	}

	static String resolveAssignmentKey(final Component tabKey) {
		if (tabKey instanceof MapView) {
			final MapModel model = ((MapView) tabKey).getModel();
			if (model == null) {
				return null;
			}
			return normalizePath(model.getFile());
		}
		if (tabKey instanceof IDocumentTabView) {
			// Reports / Draw.io: stable keys so tabs can be moved between groups like maps.
			return ((IDocumentTabView) tabKey).getTabAssignmentKey();
		}
		return null;
	}

	static String normalizePath(final File mapFile) {
		if (mapFile == null) {
			return null;
		}
		String path;
		try {
			path = mapFile.getCanonicalFile().getAbsolutePath();
		}
		catch (final Exception e) {
			path = mapFile.getAbsolutePath();
		}
		if (path == null || path.length() == 0) {
			return null;
		}
		path = path.replace('\\', '/');
		if (path.length() >= 2 && path.charAt(1) == ':') {
			path = Character.toLowerCase(path.charAt(0)) + path.substring(1);
		}
		return path;
	}
}
