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
import org.freeplane.features.ui.IDocumentTabView;
import org.freeplane.main.application.MapViewTabOrder;
import org.freeplane.plugin.workspace.components.tagfilter.TagGroupCascadeBar;
import org.freeplane.plugin.workspace.features.nodepins.TagGroupStore;
import org.freeplane.view.swing.map.MapView;

/**
 * Nested group filter for bottom mind-map tabs (work / life / subgroups),
 * reusing {@link TagGroupStore} + {@link TagGroupCascadeBar}.
 * <p>
 * Assignment keys are normalized map file paths persisted in
 * {@code {dataRoot}/_data/map-tab-groups.properties}. Default filter is 「全部」.
 * Activating a map outside the active group expands the filter to 「全部」 so the
 * tab strip never carries “forced” out-of-group tabs (which broke map/view switches).
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
				return expandFilterToAll();
			}

			public boolean revealAll() {
				return expandFilterToAll();
			}
		});
		MapViewTabOrder.setTabsChangedListener(new MapViewTabOrder.TabsChangedListener() {
			public void tabsChanged() {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						if (cascade != null && !applyingFilter) {
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

	private static boolean expandFilterToAll() {
		if (cascade == null) {
			return false;
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
		final String key = resolveAssignmentKey(tabKey);
		if (key == null) {
			return null;
		}
		final TagGroupStore store = TagGroupStore.getInstance(STORE_FILE);
		final JPopupMenu popup = new JPopupMenu();
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
		return popup;
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
			return null;
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
