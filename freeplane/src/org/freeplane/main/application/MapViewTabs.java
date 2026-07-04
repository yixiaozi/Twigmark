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
import java.util.Vector;

import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IDocumentTabView;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.features.ui.ViewController;
import org.freeplane.features.url.mindmapmode.FileOpener;
import java.awt.Component;

import javax.swing.JPanel;
import javax.swing.JSplitPane;

import org.freeplane.view.swing.map.MapView;
import org.freeplane.view.swing.ui.DefaultMapMouseListener;

class MapViewTabs implements IMapViewChangeListener {
// // 	final private Controller controller;
	private static MapViewTabs instance;
	private Component mContentComponent;
	private JTabbedPane mTabbedPane = null;
	final private Vector<Component> mTabbedPaneMapViews;
	private boolean mTabbedPaneSelectionUpdate = true;
	private TabbedPaneUI tabbedPaneUI;
	private int nextTabInsertIndex = -1;
	private int dragTabIndex = -1;
	
	private static final int MAX_TAB_SHORTCUT = 9;

	/**
	 * Optional hook (Docear relationship graph): fired when the user clicks the already-selected bottom map tab.
	 */
	interface SameTabClickListener {
		boolean onSameTabClicked(int tabIndex, Component mapView);
	}

	private static SameTabClickListener sameTabClickListener;

	static void setSameTabClickListener(final SameTabClickListener listener) {
		sameTabClickListener = listener;
	}

	static MapViewTabs getInstance() {
		return instance;
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

	public MapViewTabs( final ViewController fm, final JComponent contentComponent) {
		instance = this;
//		this.controller = controller;
		mContentComponent = contentComponent;
		mTabbedPane = new JTabbedPane();
		removeTabbedPaneAccelerators();

		mTabbedPane.setFocusable(false);
		mTabbedPane.setTabPlacement(JTabbedPane.BOTTOM);
		mTabbedPaneMapViews = new Vector<Component>();
		mTabbedPane.addChangeListener(new ChangeListener() {
			public synchronized void stateChanged(final ChangeEvent pE) {
				if("true".equals(mTabbedPane.getClientProperty("ChangedEventConsumed"))) {
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
				if (!mTabbedPane.isEnabled() || !SwingUtilities.isLeftMouseButton(e)) {
					return;
				}
				dragTabIndex = mTabbedPane.indexAtLocation(e.getX(), e.getY());
			}

			public void mouseReleased(final MouseEvent e) {
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

		//DOCEAR - MapViewTabs: keep track on not MapView tab additions
		mTabbedPane.addContainerListener(new ContainerListener() {
			public void componentRemoved(ContainerEvent event) {
				if (shouldTrackAsDocumentTab(event.getChild())) {
					mTabbedPaneMapViews.remove(event.getChild());
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							setTabsVisible();
						}
					});
				}
			}
			
			public void componentAdded(final ContainerEvent event) {
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
						setTabsVisible();
					}
				});
			}
		});
		final Controller controller = Controller.getCurrentController();
		controller.getMapViewManager().addMapViewChangeListener(this);
		fm.getContentPane().add(mTabbedPane, BorderLayout.CENTER);
		
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
				mTabbedPane.setSelectedIndex(i);
				return;
			}
		}
		final String title = formatTabTitle(documentView.getTabTitle());
		final int insertIndex = resolveInsertIndex();
		mTabbedPaneMapViews.insertElementAt(tabKey, insertIndex);
		mTabbedPane.insertTab(title, null, new JPanel(), null, insertIndex);
		mTabbedPane.setSelectedIndex(insertIndex);
		setTabsVisible();
	}

	public void selectDocumentTab(final Component tabKey) {
		if (tabKey == null) {
			return;
		}
		for (int i = 0; i < mTabbedPaneMapViews.size(); ++i) {
			if (mTabbedPaneMapViews.get(i) == tabKey) {
				mTabbedPane.setSelectedIndex(i);
				return;
			}
		}
	}

	public void closeDocumentTab(final Component tabKey) {
		for (int i = 0; i < mTabbedPaneMapViews.size(); ++i) {
			if (mTabbedPaneMapViews.get(i) == tabKey) {
				mTabbedPaneSelectionUpdate = false;
				mTabbedPane.removeTabAt(i);
				mTabbedPaneMapViews.remove(i);
				mTabbedPaneSelectionUpdate = true;
				tabSelectionChanged();
				setTabsVisible();
				return;
			}
		}
	}

	public void afterViewChange(final Component pOldMap, final Component pNewMap) {
		final int selectedIndex = mTabbedPane.getSelectedIndex();
		if (pNewMap == null) {
			return;
		}
		for (int i = 0; i < mTabbedPaneMapViews.size(); ++i) {
			if (mTabbedPaneMapViews.get(i) == pNewMap) {
				if (selectedIndex != i) {
					mTabbedPane.setSelectedIndex(i);
				}
				return;
			}
		}
		final String title = formatTabTitle(pNewMap.getName());
		final int insertIndex = resolveInsertIndex();
		mTabbedPaneMapViews.insertElementAt(pNewMap, insertIndex);
		mTabbedPane.insertTab(title, null, new JPanel(), null, insertIndex);
		mTabbedPane.setSelectedIndex(insertIndex);
		setTabsVisible();
	}

	private int resolveInsertIndex() {
		if (nextTabInsertIndex >= 0) {
			final int index = Math.min(nextTabInsertIndex, mTabbedPane.getTabCount());
			nextTabInsertIndex = -1;
			return index;
		}
		return mTabbedPane.getTabCount();
	}

	private void reorderTab(int fromIndex, int toIndex) {
		if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) {
			return;
		}
		if (fromIndex >= mTabbedPaneMapViews.size() || toIndex >= mTabbedPaneMapViews.size()) {
			return;
		}
		final int selectedBefore = mTabbedPane.getSelectedIndex();
		final Component movedMapView = mTabbedPaneMapViews.remove(fromIndex);
		final String title = mTabbedPane.getTitleAt(fromIndex);

		mTabbedPaneSelectionUpdate = false;
		mTabbedPane.removeTabAt(fromIndex);
		if (fromIndex < toIndex) {
			toIndex--;
		}
		mTabbedPaneMapViews.insertElementAt(movedMapView, toIndex);
		mTabbedPane.insertTab(title, null, new JPanel(), null, toIndex);

		int newSelectedIndex = selectedBefore;
		if (selectedBefore == fromIndex) {
			newSelectedIndex = toIndex;
		}
		else if (fromIndex < selectedBefore && toIndex >= selectedBefore) {
			newSelectedIndex = selectedBefore - 1;
		}
		else if (fromIndex > selectedBefore && toIndex <= selectedBefore) {
			newSelectedIndex = selectedBefore + 1;
		}
		if (newSelectedIndex >= 0 && newSelectedIndex < mTabbedPane.getTabCount()) {
			mTabbedPane.putClientProperty("ChangedEventConsumed", "true");
			mTabbedPane.setSelectedIndex(newSelectedIndex);
		}
		mTabbedPaneSelectionUpdate = true;
		tabSelectionChanged();
	}

	public void afterViewClose(final Component pOldMapView) {
		for (int i = 0; i < mTabbedPaneMapViews.size(); ++i) {
			if (mTabbedPaneMapViews.get(i) == pOldMapView) {
				mTabbedPaneSelectionUpdate = false;
				mTabbedPane.removeTabAt(i);
				mTabbedPaneMapViews.remove(i);
				mTabbedPaneSelectionUpdate = true;
				tabSelectionChanged();
				setTabsVisible();
				return;
			}
		}
	}

	public void afterViewCreated(final Component mapView) {
		mapView.addPropertyChangeListener("name", new PropertyChangeListener() {
			public void propertyChange(final PropertyChangeEvent evt) {
				final Component pMapView = (Component) evt.getSource();
				for (int i = 0; i < mTabbedPaneMapViews.size(); ++i) {
					if (mTabbedPaneMapViews.get(i) == pMapView) {
						mTabbedPane.setTitleAt(i, formatTabTitle(pMapView.getName()));
					}
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
		if (selectedIndex < 0) {
			return;
		}
		final Component mapView = mTabbedPaneMapViews.get(selectedIndex);
		Controller controller = Controller.getCurrentController();

		if (mapView instanceof IDocumentTabView) {
			DocumentTabSupport.activateDocumentView((IDocumentTabView) mapView);
		}
		else if (mapView instanceof MapView) {
			DocumentTabSupport.deactivateDocumentView();
			if (mapView != controller.getMapViewManager().getMapViewComponent()) {
				controller.getMapViewManager().changeToMapView(mapView.getName());
			}
			else {
				notifySameTabClicked(selectedIndex);
			}
		}
		if (mContentComponent != null) {
			mContentComponent.setVisible(true);
			mTabbedPane.setComponentAt(selectedIndex, mContentComponent);
		}
	}

	private boolean notifySameTabClicked(final int tabIndex) {
		if (sameTabClickListener == null || tabIndex < 0 || tabIndex >= mTabbedPaneMapViews.size()) {
			return false;
		}
		return sameTabClickListener.onSameTabClicked(tabIndex, mTabbedPaneMapViews.get(tabIndex));
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

	/** Tab placeholders and the split pane must not become tab identity entries. */
	private static boolean shouldTrackAsDocumentTab(final Component child) {
		if (child == null || child instanceof MapView || child instanceof JSplitPane || child instanceof JPanel) {
			return false;
		}
		return child instanceof IDocumentTabView;
	}
}
