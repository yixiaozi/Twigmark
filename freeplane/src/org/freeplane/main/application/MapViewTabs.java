/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is modified by Dimitry Polivaev in 2008.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.main.application;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Insets;
import java.awt.dnd.DropTarget;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IDocumentTabView;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.features.ui.ViewController;
import org.freeplane.features.url.mindmapmode.FileOpener;
import org.freeplane.view.swing.map.MapView;
import org.freeplane.view.swing.ui.DefaultMapMouseListener;

class MapViewTabs implements IMapViewChangeListener {
	private static MapViewTabs instance;
	private Component mContentComponent;
	private JTabbedPane mTabbedPane = null;
	/** Master list of all open map/document tabs (unfiltered order). */
	final private Vector<Component> mTabbedPaneMapViews;
	/** Currently visible strip (index-aligned with {@link #mTabbedPane}). */
	final private Vector<Component> visibleTabKeys;
	private boolean mTabbedPaneSelectionUpdate = true;
	private TabbedPaneUI tabbedPaneUI;
	private int nextTabInsertIndex = -1;
	private int dragTabIndex = -1;
	private final JPanel tabShell;
	private Component tabGroupChrome;
	private TabVisibilityFilter visibilityFilter;
	private TabsChangedListener tabsChangedListener;
	private TabPopupMenuProvider tabPopupMenuProvider;
	private TabOutsideFilterHandler outsideFilterHandler;
	private boolean rebuildingVisibleTabs;
	/** True while applying a cascade/filter rebuild so view-change listeners don't re-enter. */
	private boolean suppressingViewSync;

	private static final int MAX_TAB_SHORTCUT = 9;

	interface SameTabClickListener {
		boolean onSameTabClicked(int tabIndex, Component mapView);
	}

	/** Fired when a bottom map tab becomes the active document (user or rebuild). */
	interface MapTabActivationListener {
		void onMapTabActivated(Component mapView);
	}

	interface TabVisibilityFilter {
		boolean isVisible(Component tabKey);
	}

	interface TabsChangedListener {
		void tabsChanged();
	}

	interface TabPopupMenuProvider {
		JPopupMenu createPopup(Component tabKey, int visibleTabIndex);
	}

	/**
	 * Adjusts the active group when a map outside the current filter must become visible
	 * (e.g. switch cascade to 「全部」). Return true if the filter was changed to include the tab.
	 */
	interface TabOutsideFilterHandler {
		boolean revealTab(Component tabKey);

		/** Expand filter to show every open map (e.g. active group is empty). */
		boolean revealAll();
	}

	private static SameTabClickListener sameTabClickListener;
	private static MapTabActivationListener mapTabActivationListener;

	static void setSameTabClickListener(final SameTabClickListener listener) {
		sameTabClickListener = listener;
	}

	static void setMapTabActivationListener(final MapTabActivationListener listener) {
		mapTabActivationListener = listener;
	}

	static MapViewTabs getInstance() {
		return instance;
	}

	List getMindMapViewsInTabOrder() {
		final List result = new LinkedList();
		for (int i = 0; i < mTabbedPaneMapViews.size(); i++) {
			final Component component = mTabbedPaneMapViews.get(i);
			if (component instanceof MapView) {
				result.add(component);
			}
		}
		return result;
	}

	List getAllTabKeysInOrder() {
		return new LinkedList(mTabbedPaneMapViews);
	}

	void setNextTabInsertIndex(final int index) {
		nextTabInsertIndex = index;
	}

	int getTabIndexForMapView(final Component mapView) {
		if (mapView == null) {
			return -1;
		}
		for (int i = 0; i < mTabbedPaneMapViews.size(); ++i) {
			if (mTabbedPaneMapViews.get(i) == mapView) {
				return i;
			}
		}
		return -1;
	}

	void setTabGroupChrome(final Component chrome) {
		if (tabGroupChrome != null) {
			tabShell.remove(tabGroupChrome);
		}
		tabGroupChrome = chrome;
		if (chrome != null) {
			tabShell.add(chrome, BorderLayout.SOUTH);
		}
		tabShell.revalidate();
		tabShell.repaint();
	}

	void setTabVisibilityFilter(final TabVisibilityFilter filter) {
		visibilityFilter = filter;
		rebuildVisibleTabs(null);
	}

	void setTabsChangedListener(final TabsChangedListener listener) {
		tabsChangedListener = listener;
	}

	void setTabPopupMenuProvider(final TabPopupMenuProvider provider) {
		tabPopupMenuProvider = provider;
	}

	void setTabOutsideFilterHandler(final TabOutsideFilterHandler handler) {
		outsideFilterHandler = handler;
	}

	void refreshVisibleTabs() {
		rebuildVisibleTabs(null);
	}

	public MapViewTabs(final ViewController fm, final JComponent contentComponent) {
		instance = this;
		mContentComponent = contentComponent;
		mTabbedPane = new JTabbedPane();
		removeTabbedPaneAccelerators();

		mTabbedPane.setFocusable(false);
		mTabbedPane.setTabPlacement(JTabbedPane.BOTTOM);
		DocearUiTheme.styleTabbedPane(mTabbedPane);
		mTabbedPaneMapViews = new Vector<Component>();
		visibleTabKeys = new Vector<Component>();
		mTabbedPane.addChangeListener(new ChangeListener() {
			public synchronized void stateChanged(final ChangeEvent pE) {
				if ("true".equals(mTabbedPane.getClientProperty("ChangedEventConsumed"))) {
					mTabbedPane.putClientProperty("ChangedEventConsumed", null);
				}
				else {
					tabSelectionChanged();
				}
			}
		});
		final FileOpener fileOpener = new FileOpener();
		new DropTarget(mTabbedPane, fileOpener);
		mTabbedPane.addMouseListener(new DefaultMapMouseListener());
		mTabbedPane.addMouseListener(new MouseAdapter() {
			public void mousePressed(final MouseEvent e) {
				if (!mTabbedPane.isEnabled()) {
					return;
				}
				if (maybeShowTabPopup(e)) {
					return;
				}
				if (!SwingUtilities.isLeftMouseButton(e)) {
					return;
				}
				dragTabIndex = mTabbedPane.indexAtLocation(e.getX(), e.getY());
			}

			public void mouseReleased(final MouseEvent e) {
				if (maybeShowTabPopup(e)) {
					dragTabIndex = -1;
					return;
				}
				if (dragTabIndex < 0 || !SwingUtilities.isLeftMouseButton(e)) {
					dragTabIndex = -1;
					return;
				}
				final int dropIndex = mTabbedPane.indexAtLocation(e.getX(), e.getY());
				if (dropIndex >= 0 && dropIndex != dragTabIndex) {
					reorderTab(dragTabIndex, dropIndex);
				}
				else if (dropIndex >= 0 && dropIndex == dragTabIndex && dropIndex == mTabbedPane.getSelectedIndex()) {
					notifySameTabClicked(dropIndex);
				}
				dragTabIndex = -1;
			}
		});

		mTabbedPane.addContainerListener(new ContainerListener() {
			public void componentRemoved(ContainerEvent event) {
				if (rebuildingVisibleTabs) {
					return;
				}
				if (shouldTrackAsDocumentTab(event.getChild())) {
					mTabbedPaneMapViews.remove(event.getChild());
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							rebuildVisibleTabs(null);
							notifyTabsChanged();
						}
					});
				}
			}

			public void componentAdded(final ContainerEvent event) {
				if (rebuildingVisibleTabs) {
					return;
				}
				if (!shouldTrackAsDocumentTab(event.getChild())) {
					return;
				}
				for (int i = 0; i < mTabbedPaneMapViews.size(); ++i) {
					if (mTabbedPaneMapViews.get(i) == event.getChild()) {
						return;
					}
				}
				mTabbedPaneMapViews.add(event.getChild());
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						ensureVisibleAndSelect(event.getChild());
						notifyTabsChanged();
					}
				});
			}
		});
		final Controller controller = Controller.getCurrentController();
		controller.getMapViewManager().addMapViewChangeListener(this);
		tabShell = new JPanel(new BorderLayout());
		tabShell.add(mTabbedPane, BorderLayout.CENTER);
		fm.getContentPane().add(tabShell, BorderLayout.CENTER);

		installTabShortcuts();
	}

	void removeTabbedPaneAccelerators() {
	}

	private void installTabShortcuts() {
		InputMap inputMap = mTabbedPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		if (inputMap == null) {
			inputMap = new InputMap();
			mTabbedPane.setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, inputMap);
		}

		javax.swing.ActionMap actionMap = mTabbedPane.getActionMap();

		for (int i = 1; i <= MAX_TAB_SHORTCUT; i++) {
			final int tabIndex = i;
			KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_0 + i, InputEvent.ALT_MASK);
			String actionKey = "mapviewtabs.switch.to.tab." + i;
			inputMap.put(keyStroke, actionKey);
			actionMap.put(actionKey, new javax.swing.AbstractAction() {
				private static final long serialVersionUID = 1L;

				public void actionPerformed(java.awt.event.ActionEvent e) {
					switchToTab(tabIndex - 1);
				}
			});
		}
	}

	private void switchToTab(int index) {
		if (index >= 0 && index < mTabbedPane.getTabCount()) {
			if (index < visibleTabKeys.size()) {
				final Component key = visibleTabKeys.get(index);
				if (key instanceof MapView) {
					notifyMapTabActivated(key);
				}
			}
			mTabbedPane.setSelectedIndex(index);
		}
	}

	public void openDocumentTab(final IDocumentTabView documentView) {
		if (documentView == null) {
			return;
		}
		final Component tabKey = documentView.getTabKey();
		for (int i = 0; i < mTabbedPaneMapViews.size(); ++i) {
			if (mTabbedPaneMapViews.get(i) == tabKey) {
				ensureVisibleAndSelect(tabKey);
				return;
			}
		}
		final int insertIndex = resolveInsertIndexInMaster();
		mTabbedPaneMapViews.insertElementAt(tabKey, insertIndex);
		ensureVisibleAndSelect(tabKey);
		notifyTabsChanged();
	}

	public void selectDocumentTab(final Component tabKey) {
		if (tabKey == null) {
			return;
		}
		ensureVisibleAndSelect(tabKey);
	}

	public void closeDocumentTab(final Component tabKey) {
		for (int i = 0; i < mTabbedPaneMapViews.size(); ++i) {
			if (mTabbedPaneMapViews.get(i) == tabKey) {
				mTabbedPaneMapViews.remove(i);
				rebuildVisibleTabs(null);
				notifyTabsChanged();
				return;
			}
		}
	}

	public void afterViewChange(final Component pOldMap, final Component pNewMap) {
		if (pNewMap == null) {
			return;
		}
		boolean known = false;
		for (int i = 0; i < mTabbedPaneMapViews.size(); ++i) {
			if (mTabbedPaneMapViews.get(i) == pNewMap) {
				known = true;
				break;
			}
		}
		if (!known) {
			final int insertIndex = resolveInsertIndexInMaster();
			mTabbedPaneMapViews.insertElementAt(pNewMap, insertIndex);
			notifyTabsChanged();
		}
		if (rebuildingVisibleTabs || suppressingViewSync) {
			return;
		}
		ensureVisibleAndSelect(pNewMap);
	}

	private int resolveInsertIndexInMaster() {
		if (nextTabInsertIndex >= 0) {
			final int index = Math.min(nextTabInsertIndex, mTabbedPaneMapViews.size());
			nextTabInsertIndex = -1;
			return index;
		}
		return mTabbedPaneMapViews.size();
	}

	private void reorderTab(int fromVisibleIndex, int toVisibleIndex) {
		if (fromVisibleIndex < 0 || toVisibleIndex < 0 || fromVisibleIndex == toVisibleIndex) {
			return;
		}
		if (fromVisibleIndex >= visibleTabKeys.size() || toVisibleIndex >= visibleTabKeys.size()) {
			return;
		}
		final Component moved = visibleTabKeys.get(fromVisibleIndex);
		final Component target = visibleTabKeys.get(toVisibleIndex);
		final int fromMaster = getTabIndexForMapView(moved);
		final int toMaster = getTabIndexForMapView(target);
		if (fromMaster < 0 || toMaster < 0 || fromMaster == toMaster) {
			return;
		}
		mTabbedPaneMapViews.remove(fromMaster);
		int insertAt = toMaster;
		if (fromMaster < toMaster) {
			insertAt = toMaster - 1;
		}
		mTabbedPaneMapViews.insertElementAt(moved, insertAt);
		rebuildVisibleTabs(moved);
		notifyTabsChanged();
	}

	public void afterViewClose(final Component pOldMapView) {
		for (int i = 0; i < mTabbedPaneMapViews.size(); ++i) {
			if (mTabbedPaneMapViews.get(i) == pOldMapView) {
				mTabbedPaneMapViews.remove(i);
				rebuildVisibleTabs(null);
				notifyTabsChanged();
				return;
			}
		}
	}

	public void afterViewCreated(final Component mapView) {
		mapView.addPropertyChangeListener("name", new PropertyChangeListener() {
			public void propertyChange(final PropertyChangeEvent evt) {
				final Component pMapView = (Component) evt.getSource();
				final int visible = visibleTabKeys.indexOf(pMapView);
				if (visible >= 0) {
					mTabbedPane.setTitleAt(visible, formatTabTitle(pMapView.getName()));
				}
			}
		});
	}

	private String formatTabTitle(String title) {
		if (title == null) {
			return "";
		}
		final String lower = title.toLowerCase();
		if (lower.endsWith(".mm")) {
			return title.substring(0, title.length() - 3);
		}
		if (lower.endsWith(".drawio")) {
			return title.substring(0, title.length() - 7);
		}
		return title;
	}

	public void beforeViewChange(final Component pOldMapView, final Component pNewMapView) {
	}

	public void removeContentComponent() {
		mContentComponent = null;
		if (mTabbedPane.getSelectedIndex() >= 0) {
			mTabbedPane.setComponentAt(mTabbedPane.getSelectedIndex(), new JPanel());
		}
	}

	public void setContentComponent(final Component mContentComponent) {
		this.mContentComponent = mContentComponent;
		if (mTabbedPane.getSelectedIndex() >= 0) {
			mTabbedPane.setComponentAt(mTabbedPane.getSelectedIndex(), mContentComponent);
		}
	}

	private void tabSelectionChanged() {
		if (!mTabbedPaneSelectionUpdate) {
			return;
		}
		final int selectedIndex = mTabbedPane.getSelectedIndex();
		for (int j = 0; j < mTabbedPane.getTabCount(); j++) {
			if (j != selectedIndex) {
				mTabbedPane.setComponentAt(j, new JPanel());
			}
		}
		if (selectedIndex < 0 || selectedIndex >= visibleTabKeys.size()) {
			return;
		}
		final Component mapView = visibleTabKeys.get(selectedIndex);
		Controller controller = Controller.getCurrentController();

		if (mapView instanceof IDocumentTabView) {
			DocumentTabSupport.activateDocumentView((IDocumentTabView) mapView);
		}
		else if (mapView instanceof MapView) {
			DocumentTabSupport.deactivateDocumentView();
			if (mapView != controller.getMapViewManager().getMapViewComponent()) {
				suppressingViewSync = true;
				try {
					controller.getMapViewManager().changeToMapView(mapView.getName());
				}
				finally {
					suppressingViewSync = false;
				}
			}
			// Do NOT exit overlay views here: rebuilds/filter sync also call this path.
			// User mouse clicks use SameTabClickListener; Alt+N uses switchToTab.
		}
		if (mContentComponent != null) {
			mContentComponent.setVisible(true);
			mTabbedPane.setComponentAt(selectedIndex, mContentComponent);
			mContentComponent.revalidate();
			mContentComponent.repaint();
			mTabbedPane.revalidate();
			mTabbedPane.repaint();
		}
	}

	private boolean notifySameTabClicked(final int tabIndex) {
		if (sameTabClickListener == null || tabIndex < 0 || tabIndex >= visibleTabKeys.size()) {
			return false;
		}
		return sameTabClickListener.onSameTabClicked(tabIndex, visibleTabKeys.get(tabIndex));
	}

	private void notifyMapTabActivated(final Component mapView) {
		if (mapTabActivationListener != null && mapView != null) {
			mapTabActivationListener.onMapTabActivated(mapView);
		}
	}

	private void setTabsVisible() {
		final boolean visible = mTabbedPane.getTabCount() > 1;
		if (visible == areTabsVisible()) {
			return;
		}
		if (tabbedPaneUI == null) {
			tabbedPaneUI = mTabbedPane.getUI();
		}
		if (visible) {
			mTabbedPane.setUI(tabbedPaneUI);
		}
		else {
			mTabbedPane.setUI(new BasicTabbedPaneUI() {
				@Override
				protected int calculateTabAreaHeight(final int tabPlacement, final int horizRunCount,
						final int maxTabHeight) {
					return 0;
				}

				@Override
				protected Insets getContentBorderInsets(final int tabPlacement) {
					return new Insets(0, 0, 0, 0);
				}

				@Override
				protected MouseListener createMouseListener() {
					return null;
				}
			});
		}
		mTabbedPane.revalidate();
	}

	private boolean areTabsVisible() {
		return tabbedPaneUI == null || tabbedPaneUI == mTabbedPane.getUI();
	}

	private static boolean shouldTrackAsDocumentTab(final Component child) {
		if (child == null || child instanceof MapView || child instanceof JSplitPane || child instanceof JPanel) {
			return false;
		}
		return child instanceof IDocumentTabView;
	}

	private boolean isAllowedByFilter(final Component tabKey) {
		return visibilityFilter == null || visibilityFilter.isVisible(tabKey);
	}

	/**
	 * Ensures {@code tabKey} is on the visible strip and selected. Never force-shows a tab
	 * outside the active group (that desynced the strip and broke later map/view switches);
	 * ask {@link #outsideFilterHandler} to expand the filter instead.
	 */
	private void ensureVisibleAndSelect(final Component tabKey) {
		if (rebuildingVisibleTabs || suppressingViewSync) {
			return;
		}
		if (tabKey != null && !isAllowedByFilter(tabKey) && outsideFilterHandler != null) {
			if (outsideFilterHandler.revealTab(tabKey)) {
				// Handler updated the cascade and refreshed the strip; just select.
				final int idx = visibleTabKeys.indexOf(tabKey);
				if (idx >= 0) {
					selectVisibleIndex(idx);
					return;
				}
			}
		}
		if (tabKey != null && isAllowedByFilter(tabKey) && stripMatchesFilter()) {
			final int idx = visibleTabKeys.indexOf(tabKey);
			if (idx >= 0) {
				selectVisibleIndex(idx);
				return;
			}
		}
		rebuildVisibleTabs(tabKey);
	}

	/**
	 * Selects a visible tab and guarantees its real content is mounted.
	 * Non-selected tabs only hold empty placeholder panels; when the target index is
	 * already selected, {@link JTabbedPane#setSelectedIndex(int)} does not fire a
	 * ChangeEvent, so {@link #tabSelectionChanged()} would never run — leaving a gray
	 * page until the user switches tabs. A stuck {@code ChangedEventConsumed} flag can
	 * also swallow the next selection event; heal by mounting content explicitly.
	 */
	private void selectVisibleIndex(final int idx) {
		if (idx < 0 || idx >= mTabbedPane.getTabCount()) {
			return;
		}
		if (mTabbedPane.getSelectedIndex() != idx) {
			mTabbedPane.setSelectedIndex(idx);
		}
		ensureSelectedTabContentMounted();
	}

	/** True when the selected tab still shows the shared map/document content component. */
	private boolean isSelectedTabContentMounted() {
		final int selectedIndex = mTabbedPane.getSelectedIndex();
		return selectedIndex >= 0 && mContentComponent != null
				&& mTabbedPane.getComponentAt(selectedIndex) == mContentComponent;
	}

	/**
	 * Re-attaches {@link #mContentComponent} when the selected tab is still a gray
	 * placeholder (or after a selection ChangeEvent was incorrectly consumed).
	 */
	private void ensureSelectedTabContentMounted() {
		if (isSelectedTabContentMounted()) {
			mContentComponent.setVisible(true);
			mContentComponent.revalidate();
			mContentComponent.repaint();
			return;
		}
		tabSelectionChanged();
	}

	private boolean stripMatchesFilter() {
		int j = 0;
		for (int i = 0; i < mTabbedPaneMapViews.size(); i++) {
			final Component key = mTabbedPaneMapViews.get(i);
			if (!isAllowedByFilter(key)) {
				continue;
			}
			if (j >= visibleTabKeys.size() || visibleTabKeys.get(j) != key) {
				return false;
			}
			j++;
		}
		return j == visibleTabKeys.size() && j == mTabbedPane.getTabCount();
	}

	/**
	 * Rebuilds the visible strip from the master list under the current filter.
	 * {@code preferSelect} is selected when present and allowed by the filter; otherwise
	 * the first visible tab is selected (and becomes the active map view).
	 */
	private void rebuildVisibleTabs(final Component preferSelect) {
		if (rebuildingVisibleTabs) {
			return;
		}
		rebuildingVisibleTabs = true;
		Component prefer = preferSelect;
		if (prefer != null && !isAllowedByFilter(prefer)) {
			prefer = null;
		}
		if (prefer == null && mTabbedPane.getSelectedIndex() >= 0
				&& mTabbedPane.getSelectedIndex() < visibleTabKeys.size()) {
			final Component selected = visibleTabKeys.get(mTabbedPane.getSelectedIndex());
			if (isAllowedByFilter(selected)) {
				prefer = selected;
			}
		}
		if (prefer == null) {
			final Component current = Controller.getCurrentController().getMapViewManager().getMapViewComponent();
			if (current != null && isAllowedByFilter(current)) {
				prefer = current;
			}
		}
		try {
			mTabbedPaneSelectionUpdate = false;
			while (mTabbedPane.getTabCount() > 0) {
				mTabbedPane.removeTabAt(mTabbedPane.getTabCount() - 1);
			}
			visibleTabKeys.clear();
			int selectVisible = -1;
			for (int i = 0; i < mTabbedPaneMapViews.size(); i++) {
				final Component tabKey = mTabbedPaneMapViews.get(i);
				if (!isAllowedByFilter(tabKey)) {
					continue;
				}
				final String title = formatTabTitle(resolveTabTitle(tabKey));
				final int visibleIndex = mTabbedPane.getTabCount();
				mTabbedPane.insertTab(title, null, new JPanel(), null, visibleIndex);
				visibleTabKeys.add(tabKey);
				if (prefer != null && tabKey == prefer) {
					selectVisible = visibleIndex;
				}
			}
			if (visibleTabKeys.isEmpty() && !mTabbedPaneMapViews.isEmpty() && visibilityFilter != null
					&& outsideFilterHandler != null) {
				rebuildingVisibleTabs = false;
				mTabbedPaneSelectionUpdate = true;
				if (outsideFilterHandler.revealAll()) {
					return;
				}
				rebuildingVisibleTabs = true;
				mTabbedPaneSelectionUpdate = false;
			}
			if (selectVisible < 0 && mTabbedPane.getTabCount() > 0) {
				selectVisible = 0;
			}
			if (selectVisible >= 0) {
				// Suppress the ChangeListener only for this setSelectedIndex call.
				// If the index is unchanged, Swing may not fire ChangeEvent — clear the
				// flag immediately so a later real tab switch is not swallowed (that left
				// the new tab on an empty gray placeholder panel).
				mTabbedPane.putClientProperty("ChangedEventConsumed", "true");
				mTabbedPane.setSelectedIndex(selectVisible);
				mTabbedPane.putClientProperty("ChangedEventConsumed", null);
			}
			mTabbedPaneSelectionUpdate = true;
			tabSelectionChanged();
			setTabsVisible();
		}
		finally {
			rebuildingVisibleTabs = false;
		}
	}

	private static String resolveTabTitle(final Component tabKey) {
		if (tabKey instanceof IDocumentTabView) {
			return ((IDocumentTabView) tabKey).getTabTitle();
		}
		return tabKey != null ? tabKey.getName() : "";
	}

	private boolean maybeShowTabPopup(final MouseEvent e) {
		if (tabPopupMenuProvider == null || !e.isPopupTrigger()) {
			return false;
		}
		final int index = mTabbedPane.indexAtLocation(e.getX(), e.getY());
		if (index < 0 || index >= visibleTabKeys.size()) {
			return false;
		}
		final Component tabKey = visibleTabKeys.get(index);
		final JPopupMenu popup = tabPopupMenuProvider.createPopup(tabKey, index);
		if (popup == null) {
			return false;
		}
		popup.show(mTabbedPane, e.getX(), e.getY());
		return true;
	}

	private void notifyTabsChanged() {
		if (tabsChangedListener != null) {
			tabsChangedListener.tabsChanged();
		}
	}
}
