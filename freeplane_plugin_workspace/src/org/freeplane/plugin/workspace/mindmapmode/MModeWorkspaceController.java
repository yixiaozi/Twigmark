package org.freeplane.plugin.workspace.mindmapmode;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import org.freeplane.core.ui.components.TabbedPaneWidthUtils;
import org.freeplane.core.ui.components.SideTabTitleUpdater;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeSelectionEvent;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.IMenuContributor;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.ui.components.JResizer.Direction;
import org.freeplane.core.ui.components.OneTouchCollapseResizer;
import org.freeplane.core.ui.components.OneTouchCollapseResizer.CollapseDirection;
import org.freeplane.core.ui.components.OneTouchCollapseResizer.ComponentCollapseListener;
import org.freeplane.core.ui.components.ResizeEvent;
import org.freeplane.core.ui.components.ResizerListener;
import org.freeplane.core.ui.ribbon.RibbonMapChangeAdapter;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.TextUtils;
import org.freeplane.core.util.WorkspaceSearchFileMenuBridge;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.INodeViewLifeCycleListener;
import org.freeplane.features.ui.ViewController;
import org.freeplane.features.url.UrlManager;
import org.freeplane.plugin.workspace.URIUtils;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;
import org.freeplane.plugin.workspace.actions.EditFavoriteTagsAction;
import org.freeplane.plugin.workspace.actions.FileNodeDeleteAction;
import org.freeplane.plugin.workspace.actions.MindMapNodeOpenLocationAction;
import org.freeplane.plugin.workspace.actions.MindMapPopupOpenLocationAction;
import org.freeplane.plugin.workspace.actions.MindMapOpenLocationAction;
import org.freeplane.plugin.workspace.actions.ToggleFavoriteAction;
import org.freeplane.plugin.workspace.actions.FileNodeNewFileAction;
import org.freeplane.plugin.workspace.actions.FileNodeNewMindmapAction;
import org.freeplane.plugin.workspace.actions.NodeCopyAction;
import org.freeplane.plugin.workspace.actions.NodeCutAction;
import org.freeplane.plugin.workspace.actions.NodeNewFolderAction;
import org.freeplane.plugin.workspace.actions.NodeNewLinkAction;
import org.freeplane.plugin.workspace.actions.NodeOpenLocationAction;
import org.freeplane.plugin.workspace.actions.NodePasteAction;
import org.freeplane.plugin.workspace.actions.NodeRefreshAction;
import org.freeplane.plugin.workspace.actions.NodeRemoveAction;
import org.freeplane.plugin.workspace.actions.NodeRenameAction;
import org.freeplane.plugin.workspace.actions.PhysicalFolderSortOrderAction;
import org.freeplane.plugin.workspace.actions.ProjectOpenLocationAction;
import org.freeplane.plugin.workspace.actions.ProjectRenameAction;
import org.freeplane.plugin.workspace.actions.WorkspaceCollapseAction;
import org.freeplane.plugin.workspace.actions.WorkspaceExpandAction;
import org.freeplane.plugin.workspace.actions.WorkspaceImportProjectAction;
import org.freeplane.plugin.workspace.actions.WorkspaceNewMapAction;
import org.freeplane.plugin.workspace.actions.WorkspaceNewProjectAction;
import org.freeplane.plugin.workspace.actions.WorkspaceProjectOpenLocationAction;
import org.freeplane.plugin.workspace.actions.WorkspaceRemoveProjectAction;
import org.freeplane.plugin.workspace.components.DraggableTabbedPane;
import org.freeplane.plugin.workspace.components.RelationshipGraphTabBridge;
import org.freeplane.plugin.workspace.components.IWorkspaceView;
import org.freeplane.plugin.workspace.components.TreeView;
import org.freeplane.plugin.workspace.components.favorites.FavoritesTabPanel;
import org.freeplane.plugin.workspace.components.currentmapfolder.CurrentMapFolderTabInstaller;
import org.freeplane.plugin.workspace.components.maptabs.MapTabGroupIntegration;
import org.freeplane.plugin.workspace.components.nodepins.PinnedNodesTabInstaller;
import org.freeplane.plugin.workspace.features.favorites.FavoritesAndTagsStore;
import org.freeplane.plugin.workspace.features.favorites.SearchFileContextMenuHelper;
import org.freeplane.features.text.TextController;
import org.freeplane.plugin.workspace.features.nodepins.NodePinsIndex;
import org.freeplane.plugin.workspace.features.nodepins.NodePinsMetricsPublisher;
import org.freeplane.plugin.workspace.features.nodepins.TagChipContentTransformer;
import org.freeplane.plugin.workspace.features.nodepins.TagColorStore;
import org.freeplane.plugin.workspace.creator.DefaultFileNodeCreator;
import org.freeplane.plugin.workspace.dnd.WorkspaceTransferable;
import org.freeplane.plugin.workspace.features.AWorkspaceModeExtension;
import org.freeplane.plugin.workspace.features.IWorkspaceNodeSelectionListener;
import org.freeplane.plugin.workspace.features.IWorkspaceSettingsHandler;
import org.freeplane.plugin.workspace.handler.DefaultFileNodeIconHandler;
import org.freeplane.plugin.workspace.handler.DirectoryMergeConflictDialog;
import org.freeplane.plugin.workspace.handler.FileExistsConflictDialog;
import org.freeplane.plugin.workspace.handler.LinkTypeFileIconHandler;
import org.freeplane.plugin.workspace.io.AFileNodeCreator;
import org.freeplane.plugin.workspace.io.FileReadManager;
import org.freeplane.plugin.workspace.io.FileSystemManager;
import org.freeplane.plugin.workspace.model.WorkspaceModel;
import org.freeplane.plugin.workspace.model.WorkspaceModelEvent;
import org.freeplane.plugin.workspace.model.WorkspaceModelListener;
import org.freeplane.plugin.workspace.model.project.AWorkspaceProject;
import org.freeplane.plugin.workspace.model.project.IProjectSelectionListener;
import org.freeplane.plugin.workspace.model.project.ProjectSelectionEvent;
import org.freeplane.plugin.workspace.nodes.DefaultFileNode;
import org.freeplane.plugin.workspace.nodes.FolderTypeMyFilesNode;
import org.freeplane.plugin.workspace.nodes.LinkTypeFileNode;
import org.freeplane.plugin.workspace.nodes.ProjectRootNode;
import org.freeplane.view.swing.features.git.GitTabPanel;
import org.freeplane.view.swing.features.time.mindmapmode.EnhancedAllFlagsTabPanel;
import org.freeplane.view.swing.features.time.mindmapmode.ActivityAnalysisPanel;
import org.freeplane.view.swing.features.time.mindmapmode.AllFileSearchPanel;
import org.freeplane.view.swing.features.time.mindmapmode.GlobalSearchTabPanel;
import org.freeplane.view.swing.features.time.mindmapmode.MindMapFileSearchPanel;
import org.freeplane.view.swing.map.NodeView;
import org.freeplane.view.swing.ui.mindmapmode.MNodeDropListener;

public class MModeWorkspaceController extends AWorkspaceModeExtension {

	private static final String TAB_WORKSPACE = "workspace";
	private static final String TAB_FAVORITES = "favorites";
	private static final String TAB_SEARCH = "search";
	private static final String TAB_FILE_SEARCH = "file_search";
	private static final String TAB_ALL_FILE_SEARCH = "all_file_search";
	private static final String TAB_ACTIVITY = "activity";
	private static final String TAB_GIT = "git";
	private static final String TAB_GRAPH = "relationship_graph";
	private static final String TAB_NEXT_ACTIONS = "next_actions";
	private static final int SIDE_TAB_PRELOAD_DELAY_MS = 5000;
	private static final int SIDE_TAB_PRELOAD_STAGGER_MS = 800;
	private static final String[] BACKGROUND_PRELOAD_TAB_IDS = {
			TAB_SEARCH, TAB_FILE_SEARCH, TAB_ALL_FILE_SEARCH, TAB_ACTIVITY, TAB_GIT, TAB_NEXT_ACTIONS
	};
	private static final String[] DEFAULT_SIDE_TAB_ORDER = {
			TAB_WORKSPACE, TAB_FAVORITES, TAB_FILE_SEARCH, TAB_ALL_FILE_SEARCH, TAB_SEARCH, TAB_ACTIVITY, TAB_GRAPH,
			TAB_GIT, TAB_NEXT_ACTIONS
	};

	abstract class ResizerEventAdapter implements ResizerListener, ComponentCollapseListener {
	}

	private FileReadManager fileTypeManager;
	private TreeView view;
	private DraggableTabbedPane sideTabs;
	private SideTabTitleUpdater sideTabTitleUpdater;
	private final List<String> sideTabOrder = new ArrayList<String>();
	private final Map<String, Boolean> sideTabLoaded = new HashMap<String, Boolean>();
	private final Map<String, JComponent> sideTabComponents = new HashMap<String, JComponent>();
	private IWorkspaceSettingsHandler settings;
	private volatile WorkspaceModel wsModel;
	private AWorkspaceProject selectedProject = null;
	private IProjectSelectionListener projectSelectionListener;
	private Runnable viewUpdater;

	public MModeWorkspaceController(ModeController modeController) {
		super(modeController);
		setupController(modeController);
	}
	
	public void start(ModeController modeController) {
		setupActions(modeController);
		setupModel(modeController);
		setupView(modeController);
		setupPinnedNodesTab(modeController);
		setupCurrentMapFolderTab(modeController);
		setupMapTabGroups();
		scheduleLoadAfterFrameVisible();
	}

	private void setupMapTabGroups() {
		MapTabGroupIntegration.install();
	}

	private void scheduleLoadAfterFrameVisible() {
		final Runnable waitForFrame = new Runnable() {
			private int attempts;

			public void run() {
				final java.awt.Frame frame = org.freeplane.core.ui.components.UITools.getFrame();
				if (frame != null && frame.isVisible()) {
					load();
					return;
				}
				if (++attempts < 1200) {
					Controller.getCurrentController().getViewController().invokeLater(this);
				}
				else {
					LogUtils.warn("Workspace load: main frame not visible, loading anyway");
					load();
				}
			}
		};
		Controller.getCurrentController().getViewController().invokeLater(waitForFrame);
	}

	private void setupController(ModeController modeController) {
		modeController.removeExtension(UrlManager.class);
		UrlManager.install(new MModeWorkspaceUrlManager());
		
		modeController.removeExtension(LinkController.class);
		LinkController.install(MModeWorkspaceLinkController.getController());
		
		//add link type entry to the chooser
		MModeWorkspaceLinkController.getController().prepareOptionPanelBuilder(((MModeController)modeController).getOptionPanelBuilder());
		
		modeController.addINodeViewLifeCycleListener(new INodeViewLifeCycleListener() {

			public void onViewCreated(Container nodeView) {
				NodeView node = (NodeView) nodeView;
				final DropTarget dropTarget = new DropTarget(node.getMainView(), new MNodeDropListener() {
					public void drop(final DropTargetDropEvent dtde) {
						DropTargetDropEvent evt = dtde;
						if(dtde.getTransferable().isDataFlavorSupported(WorkspaceTransferable.WORKSPACE_NODE_FLAVOR)) {
							evt = new DropTargetDropEvent(dtde.getDropTargetContext(), dtde.getLocation(), dtde.getDropAction(), dtde.getSourceActions(), false);
						}
						super.drop(evt);
					}
				});
				dropTarget.setActive(true);
			}

			public void onViewRemoved(Container nodeView) {
			}

		});
		
		modeController.addMenuContributor(new IMenuContributor() {
			public void updateMenus(ModeController modeController, MenuBuilder builder) {
				final String MENU_PROJECT_KEY = "/menu_bar/project";
				//insert project menu into main menu
				JMenu projectMenu = new JMenu(TextUtils.getText("menu.project.entry.label"));
				projectMenu.setMnemonic('o');				
				builder.addMenuItem("/menu_bar/format", projectMenu, MENU_PROJECT_KEY, MenuBuilder.AFTER);
				
				builder.addSeparator(MENU_PROJECT_KEY, MenuBuilder.AS_CHILD);
				final String MENU_PROJECT_ADD_KEY = builder.getMenuKey(MENU_PROJECT_KEY, "new");				
				final JMenu addMenu = new JMenu(TextUtils.getText("workspace.action.new.label"));
				builder.addMenuItem(MENU_PROJECT_KEY, addMenu, MENU_PROJECT_ADD_KEY, MenuBuilder.AS_CHILD);
				builder.addAction(MENU_PROJECT_ADD_KEY, new NodeNewFolderAction(), MenuBuilder.AS_CHILD);
				builder.addAction(MENU_PROJECT_ADD_KEY, new NodeNewLinkAction(), MenuBuilder.AS_CHILD);
				final WorkspaceRemoveProjectAction rmProjectAction = new WorkspaceRemoveProjectAction();
				builder.addAction(MENU_PROJECT_KEY, rmProjectAction, MenuBuilder.AS_CHILD);
				
				builder.addSeparator(MENU_PROJECT_KEY, MenuBuilder.AS_CHILD);
				setDefaultAccelerator(builder.getShortcutKey(builder.getMenuKey(MENU_PROJECT_KEY,WorkspaceProjectOpenLocationAction.KEY)), "control alt L");
				final WorkspaceProjectOpenLocationAction openLocAction = new WorkspaceProjectOpenLocationAction();
				builder.addAction(MENU_PROJECT_KEY, openLocAction, MenuBuilder.AS_CHILD);
				
				builder.addAction("/map_popup", WorkspaceController.getAction(MindMapPopupOpenLocationAction.KEY), MenuBuilder.AS_CHILD);
				builder.addAction("/node_popup", WorkspaceController.getAction(MindMapNodeOpenLocationAction.KEY), MenuBuilder.AS_CHILD);

				projectMenu.getPopupMenu().addPopupMenuListener(new PopupMenuListener() {
					public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
						rmProjectAction.setEnabled();
						openLocAction.setEnabled();
						if(WorkspaceController.getSelectedProject() == null) {
							addMenu.setEnabled(false);
						}
						else {
							addMenu.setEnabled(true);
						}						
					}
					
					public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
					
					public void popupMenuCanceled(PopupMenuEvent e) {}
				});
			}
			
			private void setDefaultAccelerator(final String shortcutKey, String accelerator) {
				if (accelerator != null) {				
					if (null == ResourceController.getResourceController().getProperty(shortcutKey, null)) {
						if (Compat.isMacOsX()) {
							accelerator = accelerator.replaceFirst("CONTROL", "META").replaceFirst("control", "meta");
						}
						
						ResourceController.getResourceController().setDefaultProperty(shortcutKey, accelerator);
					}
				}
			}
		});
		//RIBBONS - workspace
		final RibbonMapChangeAdapter adapter = modeController.getUserInputListenerFactory().getRibbonBuilder().getMapChangeAdapter();
		getView().addProjectSelectionListener(getWSSelectionListener(adapter));		
		getView().getNodeSelectionHandler().add(new IWorkspaceNodeSelectionListener() {			
			public void selectionChanged(TreeSelectionEvent event) {
				adapter.selectionChanged(event);
			}
		});
		modeController.getUserInputListenerFactory().getRibbonBuilder().registerContributorFactory("project_band_main", new WorkspaceProjectBandContributorFactory(this));
		File file = new File(Compat.getApplicationUserDirectory(), "workspace_ribbon.xml");
		if (file.exists()) {
			LogUtils.info("using alternative ribbon configuration file: "+file.getAbsolutePath());
			try {				
				modeController.getUserInputListenerFactory().getRibbonBuilder().updateRibbon(file.toURI().toURL());
			}
			catch (MalformedURLException e) {				
				LogUtils.severe("MModeControllerFactory.createStandardControllers(): "+e.getMessage());
			}
		}
		else {
			modeController.getUserInputListenerFactory().getRibbonBuilder().updateRibbon(MModeWorkspaceController.class.getResource("/xml/ribbons.xml"));
		}
	}

//	private void setupSettings(ModeController modeController) {
//		loadSettings(getSettingsPath());
//	}
	
	private void setupModel(ModeController modeController) {
		NodePinsMetricsPublisher.install();
		TextController.getController(modeController).addTextTransformer(new TagChipContentTransformer());
		TagColorStore.getInstance().addChangeListener(new Runnable() {
			public void run() {
				try {
					final Object mapView = Controller.getCurrentController().getMapViewManager().getMapViewComponent();
					if (mapView instanceof org.freeplane.view.swing.map.MapView) {
						((org.freeplane.view.swing.map.MapView) mapView).getRoot().updateAll();
					}
				}
				catch (final Exception ignore) {
				}
			}
		});
		FavoritesAndTagsStore.getInstance().addChangeListener(new Runnable() {
			public void run() {
				try {
					SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_FAVORITES,
					    FavoritesAndTagsStore.getInstance().getFavorites().size());
				}
				catch (final Exception e) {
					// ignore
				}
			}
		});
		FavoritesAndTagsStore.getInstance().reloadAllProjects();
		NodePinsIndex.getInstance().rescan();
	}

	private void setupPinnedNodesTab(final ModeController modeController) {
		PinnedNodesTabInstaller.install(modeController);
	}

	private void setupCurrentMapFolderTab(final ModeController modeController) {
		CurrentMapFolderTabInstaller.install(modeController);
	}

	private void setupView(ModeController modeController) {
		FileSystemManager.setDirectoryConflictHandler(new DirectoryMergeConflictDialog());
		FileSystemManager.setFileConflictHandler(new FileExistsConflictDialog());
		
		
		final OneTouchCollapseResizer otcr = new OneTouchCollapseResizer(Direction.LEFT, CollapseDirection.COLLAPSE_LEFT);
		ResizerEventAdapter adapter = new ResizerEventAdapter() {
			
			public void componentResized(ResizeEvent event) {
				if(event.getComponent().equals(sideTabs)) {
					getWorkspaceSettings().setProperty(WorkspaceSettings.WORKSPACE_VIEW_WIDTH, String.valueOf(((JComponent) event.getComponent()).getPreferredSize().width));
				}
			}

			public void componentCollapsed(ResizeEvent event) {
				if(event.getComponent().equals(sideTabs)) {
					getWorkspaceSettings().setProperty(WorkspaceSettings.WORKSPACE_VIEW_COLLAPSED, "true");
				}
			}

			public void componentExpanded(ResizeEvent event) {
				if(event.getComponent().equals(sideTabs)) {
					getWorkspaceSettings().setProperty(WorkspaceSettings.WORKSPACE_VIEW_COLLAPSED, "false");
				}
			}			
		};
		
		otcr.addResizerListener(adapter);
		otcr.addCollapseListener(adapter);
		
		loadSideTabOrder();
		sideTabs = new DraggableTabbedPane();
		// SCROLL keeps the content area visible: WRAP + tall metric tab headers
		// can consume the entire left-dock height and leave an empty gray panel.
		sideTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		for (final String tabId : sideTabOrder) {
			final JComponent component = createSideTabPlaceholder(tabId);
			sideTabComponents.put(tabId, component);
			sideTabs.add(getSideTabTitle(tabId), component);
			if (TAB_WORKSPACE.equals(tabId)) {
				sideTabLoaded.put(tabId, Boolean.TRUE);
			}
		}
		sideTabs.addChangeListener(new ChangeListener() {
			private String previousTabId = TAB_WORKSPACE;

			public void stateChanged(final ChangeEvent e) {
				try {
					ensureSideTabLoaded(sideTabs.getSelectedIndex());
				}
				catch (final Throwable t) {
					LogUtils.warn(t);
					showRelationshipGraphLoadError(t);
				}
				final String selectedTabId = getSelectedSideTabId();
				notifyRelationshipGraphTabChange(previousTabId, selectedTabId);
				previousTabId = selectedTabId;
			}
		});
		RelationshipGraphTabBridge.addReadyListener(new Runnable() {
			public void run() {
				installRelationshipGraphSideTabIfNeeded(false);
			}
		});
		sideTabs.setTabReorderListener(new DraggableTabbedPane.TabReorderListener() {
			public void tabReordered(final int fromIndex, final int toIndex) {
				final String selectedTabId = getSelectedSideTabId();
				final String tabId = sideTabOrder.remove(fromIndex);
				sideTabOrder.add(toIndex, tabId);
				rebuildSideTabs(selectedTabId);
				persistSideTabOrder();
			}
		});
		
		Box resizableTools = Box.createHorizontalBox();
		resizableTools.add(sideTabs);
		this.viewUpdater = new Runnable() {
			public void run() {
				try {
					if (Boolean.parseBoolean(getWorkspaceSettings().getProperty(
					    WorkspaceSettings.WORKSPACE_VIEW_COLLAPSED, "false"))) {
						return;
					}
					int width = Integer.parseInt(getWorkspaceSettings().getProperty(WorkspaceSettings.WORKSPACE_VIEW_WIDTH, "0"));
					if (width <= 10) {
						width = TabbedPaneWidthUtils.computeMinimumWidth(sideTabs);
					}
					sideTabs.setPreferredSize(new Dimension(width, 100));
				}
				catch (Exception e) {
					// ignore
				}
			}
		};
		resizableTools.add(otcr);
		
		modeController.getUserInputListenerFactory().addToolBar("workspace", ViewController.LEFT, resizableTools);
		
		getWorkspaceView().setModel(getModel());
		// Expand workspace root only so top-level entries are visible; leave folders collapsed.
		getView().expandPath(getModel().getRoot().getTreePath());
		
		getView().getNodeTypeIconManager().addNodeTypeIconHandler(LinkTypeFileNode.class, new LinkTypeFileIconHandler());
		getView().getNodeTypeIconManager().addNodeTypeIconHandler(DefaultFileNode.class, new DefaultFileNodeIconHandler());
		getView().refreshView();
		sideTabTitleUpdater = SideTabTitleUpdater.install(sideTabs, new Runnable() {
			public void run() {
				applySideTabsWidth();
			}
		});
		SideTabTitleUpdater.setLeftMetricsRefreshHook(new Runnable() {
			public void run() {
				refreshLeftTabMetrics();
				applySideTabsWidth();
			}
		});
		bindSideTabTitles();
		try {
			otcr.setExpanded(!Boolean.parseBoolean(getWorkspaceSettings().getProperty(
			    WorkspaceSettings.WORKSPACE_VIEW_COLLAPSED, "false")));
		}
		catch (Exception e) {
			otcr.setExpanded(true);
		}
		this.viewUpdater.run();
		scheduleScanCachePreload();
		scheduleSideTabBackgroundPreload();
	}

	private void scheduleScanCachePreload() {
		try {
			final Class cacheClass = Class.forName("org.freeplane.core.util.WorkspaceSideTabScanCache");
			cacheClass.getMethod("schedulePreload", new Class[0]).invoke(null, new Object[0]);
		}
		catch (final Exception e) {
			LogUtils.warn(e);
		}
	}

	/**
	 * After startup, silently create scan-heavy side tabs so their data is ready before the user opens them.
	 */
	private void scheduleSideTabBackgroundPreload() {
		final Timer timer = new Timer(SIDE_TAB_PRELOAD_DELAY_MS, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				((Timer) e.getSource()).stop();
				scheduleRelationshipGraphMetricsPreload();
				preloadSideTabsSequentially(0);
			}
		});
		timer.setRepeats(false);
		timer.start();
	}

	private void scheduleRelationshipGraphMetricsPreload() {
		if (!RelationshipGraphTabBridge.isAvailable()) {
			return;
		}
		try {
			RelationshipGraphTabBridge.getProvider().preloadMetrics();
		}
		catch (final Exception e) {
			LogUtils.warn(e);
		}
	}

	private void preloadSideTabsSequentially(final int preloadIndex) {
		if (preloadIndex >= BACKGROUND_PRELOAD_TAB_IDS.length) {
			return;
		}
		final String tabId = BACKGROUND_PRELOAD_TAB_IDS[preloadIndex];
		if (sideTabOrder.contains(tabId)) {
			final int tabIndex = sideTabOrder.indexOf(tabId);
			if (tabIndex >= 0) {
				ensureSideTabLoaded(tabIndex);
			}
		}
		final Timer nextTimer = new Timer(SIDE_TAB_PRELOAD_STAGGER_MS, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				((Timer) e.getSource()).stop();
				preloadSideTabsSequentially(preloadIndex + 1);
			}
		});
		nextTimer.setRepeats(false);
		nextTimer.start();
	}
		
	private void setupActions(ModeController modeController) {
		ResourceController res = ResourceController.getResourceController();
		res.setDefaultProperty(WorkspaceRemoveProjectAction.KEY+".icon", "/images/docear/project/Project-RemoveProject.png");
		res.setDefaultProperty(ProjectOpenLocationAction.KEY+".icon", "/images/docear/project/Project-OpenLocation.png");
		res.setDefaultProperty(ProjectRenameAction.KEY+".icon", "/images/docear/project/Project-Rename.png");
		
		WorkspaceController.addAction(new WorkspaceExpandAction());
		WorkspaceController.addAction(new WorkspaceCollapseAction());
		WorkspaceController.addAction(new WorkspaceNewProjectAction());
		WorkspaceController.addAction(new WorkspaceImportProjectAction());
		WorkspaceController.addAction(new NodeNewFolderAction());
		WorkspaceController.addAction(new NodeNewLinkAction());
		WorkspaceController.addAction(new NodeOpenLocationAction());		
		
		//WORKSPACE - fixed: #332
		WorkspaceController.addAction(new NodeCutAction());
		WorkspaceController.addAction(new NodeCopyAction());
		WorkspaceController.addAction(new NodePasteAction());
		WorkspaceController.addAction(new NodeRenameAction());
		WorkspaceController.addAction(new NodeRemoveAction());
		WorkspaceController.addAction(new NodeRefreshAction());
		WorkspaceController.addAction(new WorkspaceRemoveProjectAction());
		WorkspaceController.addAction(new ProjectOpenLocationAction());
		WorkspaceController.addAction(new ProjectRenameAction());
		
		WorkspaceController.replaceAction(new WorkspaceNewMapAction());
		WorkspaceController.addAction(new FileNodeNewMindmapAction());
		WorkspaceController.addAction(new FileNodeNewFileAction());
		WorkspaceController.addAction(new FileNodeDeleteAction());
		
		WorkspaceController.addAction(new PhysicalFolderSortOrderAction());
		WorkspaceController.addAction(new ToggleFavoriteAction());
		WorkspaceController.addAction(new EditFavoriteTagsAction());
		WorkspaceSearchFileMenuBridge.setProvider(new WorkspaceSearchFileMenuBridge.Provider() {
			public boolean appendFavoriteItems(final JPopupMenu menu, final File file) {
				return SearchFileContextMenuHelper.appendFavoriteItems(menu, file);
			}
		});
		WorkspaceController.addAction(new MindMapOpenLocationAction());
		WorkspaceController.addAction(new MindMapPopupOpenLocationAction());
		WorkspaceController.addAction(new MindMapNodeOpenLocationAction());
	}
	
	private IProjectSelectionListener getWSSelectionListener(final RibbonMapChangeAdapter mapChangeAdapter) {
		if(projectSelectionListener == null) {
			projectSelectionListener = new IProjectSelectionListener() {				
				
				public void selectionChanged(ProjectSelectionEvent evt) {
					if(mapChangeAdapter != null) {
						mapChangeAdapter.selectionChanged(evt.getSelectedProject());
					}
					selectedProject = evt.getSelectedProject();
				}
			};
		}
		return projectSelectionListener;
	}

	private void saveSettings() {
		
		// clear old settings
		String[] projectsIds = getWorkspaceSettings().getProperty(WorkspaceSettings.WORKSPACE_MODEL_PROJECTS, "").split(WorkspaceSettings.WORKSPACE_MODEL_PROJECTS_SEPARATOR);
		for (String projectID : projectsIds) {
			getWorkspaceSettings().removeProperty(projectID);
		}
		// build new project stack
		List<String> projectIDs = new ArrayList<String>();
		synchronized (getModel().getProjects()) {
			for(AWorkspaceProject project : getModel().getProjects()) {
				saveProject(project);
				if(projectIDs.contains(project.getProjectID())) {
					continue;
				}
				projectIDs.add(project.getProjectID());
				getWorkspaceSettings().setProperty(project.getProjectID(), project.getProjectHome().toString());
			}
		}
		StringBuilder sb = new StringBuilder();
		for (String prjId : projectIDs) {
			if(sb.length()>0) {
				sb.append(WorkspaceSettings.WORKSPACE_MODEL_PROJECTS_SEPARATOR);
			}
			sb.append(prjId);
		}
		getWorkspaceSettings().setProperty(WorkspaceSettings.WORKSPACE_MODEL_PROJECTS, sb.toString());
		saveSideTabOrder();
		try {
			getWorkspaceSettings().store();
		}
		catch (final Exception ex) {
			LogUtils.severe("could not store workspace settings.", ex);
		}
	}
	
	private void saveProject(AWorkspaceProject project) {
		try {
			getProjectLoader().storeProject(project);
		} catch (IOException e) {
			LogUtils.severe(e);
		}
		
	}

	private void loadSideTabOrder() {
		sideTabOrder.clear();
		final Set<String> seen = new HashSet<String>();
		final String savedOrder = getWorkspaceSettings().getProperty(WorkspaceSettings.WORKSPACE_SIDE_TAB_ORDER, "");
		if (savedOrder.length() > 0) {
			for (final String part : savedOrder.split(",")) {
				final String tabId = part.trim();
				if (isValidSideTabId(tabId) && !seen.contains(tabId)) {
					sideTabOrder.add(tabId);
					seen.add(tabId);
				}
			}
		}
		for (final String tabId : DEFAULT_SIDE_TAB_ORDER) {
			if (!seen.contains(tabId)) {
				sideTabOrder.add(tabId);
			}
		}
		normalizeSearchTabOrder();
		normalizeSideTabOrder();
		normalizeNextActionsTabOrder();
	}

	private void normalizeSearchTabOrder() {
		if (!sideTabOrder.contains(TAB_SEARCH)) {
			return;
		}
		sideTabOrder.remove(TAB_SEARCH);
		int insertIndex = sideTabOrder.indexOf(TAB_ALL_FILE_SEARCH);
		if (insertIndex < 0) {
			insertIndex = sideTabOrder.indexOf(TAB_FILE_SEARCH);
		}
		if (insertIndex < 0) {
			insertIndex = sideTabOrder.indexOf(TAB_FAVORITES);
		}
		if (insertIndex < 0) {
			sideTabOrder.add(TAB_SEARCH);
		}
		else {
			sideTabOrder.add(insertIndex + 1, TAB_SEARCH);
		}
	}

	private void normalizeSideTabOrder() {
		if (!sideTabOrder.contains(TAB_GRAPH) || !sideTabOrder.contains(TAB_GIT)) {
			return;
		}
		final int graphIndex = sideTabOrder.indexOf(TAB_GRAPH);
		final int gitIndex = sideTabOrder.indexOf(TAB_GIT);
		if (graphIndex >= 0 && gitIndex >= 0 && graphIndex == gitIndex - 1) {
			return;
		}
		sideTabOrder.remove(TAB_GRAPH);
		final int newGitIndex = sideTabOrder.indexOf(TAB_GIT);
		if (newGitIndex >= 0) {
			sideTabOrder.add(newGitIndex, TAB_GRAPH);
		}
		else {
			sideTabOrder.add(TAB_GRAPH);
		}
		persistSideTabOrder();
	}

	/** Keep「红旗」immediately to the right of Git. */
	private void normalizeNextActionsTabOrder() {
		if (!sideTabOrder.contains(TAB_NEXT_ACTIONS)) {
			return;
		}
		final int gitIndex = sideTabOrder.indexOf(TAB_GIT);
		final int currentIndex = sideTabOrder.indexOf(TAB_NEXT_ACTIONS);
		if (gitIndex >= 0 && currentIndex == gitIndex + 1) {
			return;
		}
		if (gitIndex < 0 && currentIndex == sideTabOrder.size() - 1) {
			return;
		}
		sideTabOrder.remove(TAB_NEXT_ACTIONS);
		if (gitIndex >= 0) {
			final int insertAt = sideTabOrder.indexOf(TAB_GIT) + 1;
			sideTabOrder.add(insertAt, TAB_NEXT_ACTIONS);
		}
		else {
			sideTabOrder.add(TAB_NEXT_ACTIONS);
		}
		persistSideTabOrder();
	}

	private void saveSideTabOrder() {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < sideTabOrder.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(sideTabOrder.get(i));
		}
		getWorkspaceSettings().setProperty(WorkspaceSettings.WORKSPACE_SIDE_TAB_ORDER, sb.toString());
	}

	private void persistSideTabOrder() {
		saveSideTabOrder();
		try {
			getWorkspaceSettings().store();
		}
		catch (final Exception e) {
			LogUtils.severe("could not store side tab order.", e);
		}
	}

	private boolean isValidSideTabId(final String tabId) {
		for (final String validId : DEFAULT_SIDE_TAB_ORDER) {
			if (validId.equals(tabId)) {
				return true;
			}
		}
		return false;
	}

	private String getSideTabTitle(final String tabId) {
		return getSideTabBaseTitle(tabId);
	}

	private String getSideTabBaseTitle(final String tabId) {
		if (TAB_WORKSPACE.equals(tabId)) {
			return "\u5de5\u4f5c\u533a";
		}
		if (TAB_FAVORITES.equals(tabId)) {
			return "\u6536\u85cf";
		}
		if (TAB_SEARCH.equals(tabId)) {
			return "\u8282\u70b9";
		}
		if (TAB_FILE_SEARCH.equals(tabId)) {
			return "\u5bfc\u56fe";
		}
		if (TAB_ALL_FILE_SEARCH.equals(tabId)) {
			return "\u6587\u4ef6";
		}
		if (TAB_ACTIVITY.equals(tabId)) {
			return "\u6d3b\u52a8\u5206\u6790";
		}
		if (TAB_GIT.equals(tabId)) {
			return "Git";
		}
		if (TAB_NEXT_ACTIONS.equals(tabId)) {
			return "\u7ea2\u65d7";
		}
		if (TAB_GRAPH.equals(tabId)) {
			return "\u5173\u7cfb\u56fe";
		}
		return tabId;
	}

	private String getSideTabMetricKey(final String tabId) {
		if (TAB_WORKSPACE.equals(tabId)) {
			return SideTabMetricKeys.LEFT_WORKSPACE;
		}
		if (TAB_FAVORITES.equals(tabId)) {
			return SideTabMetricKeys.LEFT_FAVORITES;
		}
		if (TAB_SEARCH.equals(tabId)) {
			return SideTabMetricKeys.LEFT_NODES;
		}
		if (TAB_FILE_SEARCH.equals(tabId)) {
			return SideTabMetricKeys.LEFT_MINDMAP;
		}
		if (TAB_ALL_FILE_SEARCH.equals(tabId)) {
			return SideTabMetricKeys.LEFT_FILES;
		}
		if (TAB_ACTIVITY.equals(tabId)) {
			return SideTabMetricKeys.LEFT_ACTIVITY;
		}
		if (TAB_GIT.equals(tabId)) {
			return SideTabMetricKeys.LEFT_GIT;
		}
		if (TAB_NEXT_ACTIONS.equals(tabId)) {
			return SideTabMetricKeys.LEFT_NEXT_ACTIONS;
		}
		if (TAB_GRAPH.equals(tabId)) {
			return SideTabMetricKeys.LEFT_GRAPH;
		}
		return "";
	}

	private void bindSideTabTitles() {
		if (sideTabTitleUpdater == null) {
			return;
		}
		refreshLeftTabMetrics();
		sideTabTitleUpdater.bindLeftTabs(new SideTabTitleUpdater.LeftTabSource() {
			public String getTabId(final int index) {
				return sideTabOrder.get(index);
			}

			public String getBaseTitle(final String tabId) {
				return getSideTabBaseTitle(tabId);
			}

			public String getMetricKey(final String tabId) {
				return getSideTabMetricKey(tabId);
			}

			public int getTabCount() {
				return sideTabOrder.size();
			}
		});
	}

	private void refreshLeftTabMetrics() {
		try {
			FavoritesAndTagsStore.getInstance().reloadIfChanged();
			SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_FAVORITES,
			    FavoritesAndTagsStore.getInstance().getFavorites().size());
		}
		catch (final Exception e) {
			// ignore
		}
		try {
			final Controller controller = Controller.getCurrentController();
			final int openMaps = controller != null && controller.getMapViewManager() != null
			    ? controller.getMapViewManager().getMapViewVector().size()
			    : 0;
			SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_WORKSPACE, openMaps);
		}
		catch (final Exception e) {
			// ignore
		}
	}

	private JComponent createSideTabPlaceholder(final String tabId) {
		if (TAB_WORKSPACE.equals(tabId)) {
			return getWorkspaceView();
		}
		if (TAB_GRAPH.equals(tabId)) {
			final JPanel placeholder = new JPanel(new BorderLayout(0, 8));
			placeholder.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
			placeholder.putClientProperty("docear.relationshipGraph.placeholder", Boolean.TRUE);
			final JLabel status = new JLabel(
			        "<html><body style='width:220px'>\u5173\u7cfb\u56fe\u52a0\u8f7d\u4e2d\u2026<br>"
			                + "\u82e5\u957f\u65f6\u95f4\u505c\u7559\u5728\u6b64\uff0c\u8bf4\u660e\u771f\u9762\u677f\u5c1a\u672a\u6362\u4e0a\u3002"
			                + "</body></html>");
			placeholder.add(status, BorderLayout.NORTH);
			final JButton retry = new JButton("\u7acb\u5373\u52a0\u8f7d\u5173\u7cfb\u56fe");
			retry.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					installRelationshipGraphSideTabIfNeeded(true);
				}
			});
			placeholder.add(retry, BorderLayout.SOUTH);
			placeholder.addHierarchyListener(new HierarchyListener() {
				public void hierarchyChanged(final HierarchyEvent e) {
					if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
						return;
					}
					if (!placeholder.isShowing()) {
						return;
					}
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							installRelationshipGraphSideTabIfNeeded(false);
						}
					});
				}
			});
			return placeholder;
		}
		return new JPanel();
	}

	private String getSelectedSideTabId() {
		final int selectedIndex = sideTabs.getSelectedIndex();
		if (selectedIndex >= 0 && selectedIndex < sideTabOrder.size()) {
			return sideTabOrder.get(selectedIndex);
		}
		return TAB_WORKSPACE;
	}

	private void applySideTabsWidth() {
		if (sideTabs == null || viewUpdater == null) {
			return;
		}
		viewUpdater.run();
		sideTabs.revalidate();
	}

	private void rebuildSideTabs(final String selectedTabId) {
		sideTabs.removeAll();
		for (final String tabId : sideTabOrder) {
			JComponent component = sideTabComponents.get(tabId);
			if (component == null) {
				component = createSideTabPlaceholder(tabId);
				sideTabComponents.put(tabId, component);
			}
			sideTabs.add(getSideTabTitle(tabId), component);
		}
		final int selectedIndex = sideTabOrder.indexOf(selectedTabId);
		if (selectedIndex >= 0) {
			sideTabs.setSelectedIndex(selectedIndex);
		}
		else if (sideTabs.getTabCount() > 0) {
			sideTabs.setSelectedIndex(0);
		}
		applySideTabsWidth();
		bindSideTabTitles();
		ensureSideTabLoaded(sideTabs.getSelectedIndex());
	}

	private void ensureSideTabLoaded(final int tabIndex) {
		if (tabIndex < 0 || tabIndex >= sideTabOrder.size()) {
			return;
		}
		final String tabId = sideTabOrder.get(tabIndex);
		if (TAB_GRAPH.equals(tabId)) {
			installRelationshipGraphSideTabIfNeeded(false);
			return;
		}
		if (Boolean.TRUE.equals(sideTabLoaded.get(tabId))) {
			return;
		}
		JComponent panel = null;
		if (TAB_FAVORITES.equals(tabId)) {
			panel = new FavoritesTabPanel();
		}
		else if (TAB_SEARCH.equals(tabId)) {
			panel = new GlobalSearchTabPanel();
		}
		else if (TAB_FILE_SEARCH.equals(tabId)) {
			panel = new MindMapFileSearchPanel();
		}
		else if (TAB_ALL_FILE_SEARCH.equals(tabId)) {
			panel = new AllFileSearchPanel();
		}
		else if (TAB_ACTIVITY.equals(tabId)) {
			final ActivityAnalysisPanel activityPanel = new ActivityAnalysisPanel();
			activityPanel.refreshAnalysis();
			panel = activityPanel;
		}
		else if (TAB_GIT.equals(tabId)) {
			panel = new GitTabPanel();
		}
		else if (TAB_NEXT_ACTIONS.equals(tabId)) {
			panel = new EnhancedAllFlagsTabPanel();
		}
		if (panel != null) {
			sideTabComponents.put(tabId, panel);
			if (tabIndex < sideTabs.getTabCount()) {
				sideTabs.setComponentAt(tabIndex, panel);
			}
			sideTabLoaded.put(tabId, Boolean.TRUE);
		}
	}

	/**
	 * Replace the「加载中」placeholder with the real graph panel.
	 * Badge count can appear from silent preload before this ever succeeds.
	 */
	private void installRelationshipGraphSideTabIfNeeded(final boolean forceNotify) {
		if (sideTabs == null || sideTabOrder == null) {
			return;
		}
		final int tabIndex = sideTabOrder.indexOf(TAB_GRAPH);
		if (tabIndex < 0 || tabIndex >= sideTabs.getTabCount()) {
			return;
		}
		final Component displayed = sideTabs.getComponentAt(tabIndex);
		if (isRelationshipGraphSideTabPanel(displayed)) {
			sideTabComponents.put(TAB_GRAPH, (JComponent) displayed);
			sideTabLoaded.put(TAB_GRAPH, Boolean.TRUE);
			if (forceNotify && TAB_GRAPH.equals(getSelectedSideTabId())) {
				notifyRelationshipGraphTabChange("", TAB_GRAPH);
			}
			return;
		}
		JComponent panel;
		try {
			panel = createRelationshipGraphSideTabPanel();
		}
		catch (final Throwable t) {
			LogUtils.warn(t);
			showRelationshipGraphLoadError(t);
			return;
		}
		if (panel == null) {
			showRelationshipGraphLoadError(new IllegalStateException("createSideTabPanel returned null"));
			return;
		}
		sideTabComponents.put(TAB_GRAPH, panel);
		sideTabs.setComponentAt(tabIndex, panel);
		sideTabLoaded.put(TAB_GRAPH, Boolean.TRUE);
		panel.revalidate();
		panel.repaint();
		sideTabs.revalidate();
		sideTabs.repaint();
		if (TAB_GRAPH.equals(getSelectedSideTabId()) || forceNotify) {
			if (RelationshipGraphTabBridge.isAvailable()) {
				try {
					RelationshipGraphTabBridge.getProvider().onTabSelected();
				}
				catch (final Throwable t) {
					LogUtils.warn(t);
				}
			}
		}
	}

	private void showRelationshipGraphLoadError(final Throwable error) {
		final int tabIndex = sideTabOrder.indexOf(TAB_GRAPH);
		if (tabIndex < 0 || sideTabs == null || tabIndex >= sideTabs.getTabCount()) {
			return;
		}
		final String detail = error == null ? "unknown" : error.getClass().getSimpleName() + ": "
		        + String.valueOf(error.getMessage());
		final JPanel errorPanel = new JPanel(new BorderLayout(0, 8));
		errorPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		errorPanel.add(new JLabel("<html><body style='width:220px'>\u5173\u7cfb\u56fe\u9762\u677f\u52a0\u8f7d\u5931\u8d25<br><br>"
		        + escapeHtml(detail)
		        + "<br><br>\u63d2\u4ef6\u53ef\u7528: "
		        + RelationshipGraphTabBridge.isAvailable()
		        + "</body></html>"), BorderLayout.NORTH);
		final JButton retry = new JButton("\u91cd\u8bd5");
		retry.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				installRelationshipGraphSideTabIfNeeded(true);
			}
		});
		errorPanel.add(retry, BorderLayout.SOUTH);
		sideTabComponents.put(TAB_GRAPH, errorPanel);
		sideTabs.setComponentAt(tabIndex, errorPanel);
		sideTabLoaded.put(TAB_GRAPH, Boolean.FALSE);
	}

	private static String escapeHtml(final String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static boolean isRelationshipGraphSideTabPanel(final Component component) {
		return component instanceof JComponent
		        && Boolean.TRUE.equals(((JComponent) component).getClientProperty(
		                RelationshipGraphTabBridge.SIDE_TAB_CLIENT_PROPERTY));
	}

	private TreeView getWorkspaceView() {
		if (this.view == null) {
			this.view = new TreeView();
			this.view.setMinimumSize(new Dimension(100, 100));
			this.view.setPreferredSize(new Dimension(150, 100));
			this.view.addProjectSelectionListener(getProjectSelectionListener());
			getModel();
		}
		return this.view;
	}
	
	public IWorkspaceSettingsHandler getWorkspaceSettings() {
		if(settings == null) {
			settings = new WorkspaceSettings();
			try {
				settings.load();
			} catch (IOException e) {
				LogUtils.info("Workspace settings not loaded: "+e.getMessage());
			}
		}
		return settings;
	}
	
	public void setWorkspaceSettings(IWorkspaceSettingsHandler settings) {
		this.settings = settings;
	}

	public WorkspaceModel getModel() {
		if(wsModel == null) {
			wsModel = WorkspaceModel.createDefaultModel();
			setModel(wsModel);
		}
		return wsModel;
	}
	
	public void setModel(WorkspaceModel model) {
		wsModel = model;
		if(wsModel != null) {
			wsModel.addWorldModelListener(new WorkspaceModelListener() {
				
				public void treeStructureChanged(TreeModelEvent arg0) {}
				
				public void treeNodesRemoved(TreeModelEvent arg0) {}
				
				public void treeNodesInserted(TreeModelEvent arg0) {}
				
				public void treeNodesChanged(TreeModelEvent arg0) {}
				
				public void projectRemoved(WorkspaceModelEvent event) {
					if(event.getProject().equals(getSelectedProject())) {
						selectedProject = null;
						final RibbonMapChangeAdapter adapter = Controller.getCurrentModeController().getUserInputListenerFactory().getRibbonBuilder().getMapChangeAdapter();
						adapter.selectionChanged(selectedProject);
					}
					refreshLeftTabMetrics();
				}
				
				public void projectAdded(WorkspaceModelEvent event) {
					refreshLeftTabMetrics();
				}
			});
		}
	}

	@Override
	public IWorkspaceView getView() {
		return getWorkspaceView();
	}
	
	public FileReadManager getFileTypeManager() {
		if (this.fileTypeManager == null) {
			this.fileTypeManager = new FileReadManager();
			Properties props = new Properties();
			try {
				props.load(this.getClass().getResourceAsStream("/conf/filenodetypes.properties"));

				Class<?>[] args = {};
				for (Object key : props.keySet()) {
					try {
						Class<?> clazz = DefaultFileNodeCreator.class;
						
						clazz = this.getClass().getClassLoader().loadClass(key.toString());

						AFileNodeCreator handler = (AFileNodeCreator) clazz.getConstructor(args).newInstance();
						handler.setFileTypeList(props.getProperty(key.toString(), ""), "\\|");
						this.fileTypeManager.addFileHandler(handler);
					}
					catch (ClassNotFoundException e) {
						LogUtils.warn("Class not found [" + key + "]", e);
					}
					catch (ClassCastException e) {
						LogUtils.warn("Class [" + key + "] is not of type: PhysicalNode", e);
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		return this.fileTypeManager;
	}

	public URI getDefaultProjectHome() {
		final File fixed = MindMapDataRootResolver.getFixedDataRoot();
		if (fixed != null) {
			return fixed.toURI();
		}
		File home = URIUtils.getAbsoluteFile(WorkspaceController.getApplicationHome());
		home = new File(home, "projects");
		return home.toURI();
	}

	public void shutdown() {
		save();
	}

	private IProjectSelectionListener getProjectSelectionListener() {
		if(this.projectSelectionListener == null) {
			this.projectSelectionListener = new IProjectSelectionListener() {
				public void selectionChanged(ProjectSelectionEvent event) {
					selectedProject = event.getSelectedProject();
				}
			};
		}
		return this.projectSelectionListener;
	}
	
	@Override
	public AWorkspaceProject getSelectedProject() {
		return selectedProject;		
	}

	@Override
	public void save() {
		saveSettings();		
	}

	@Override
	public void load() {
		if(this.viewUpdater != null) {
			this.viewUpdater.run();
		}
		getView().setPaintingEnabled(false);
		final AWorkspaceProject[] projects = getModel().getProjects().toArray(new AWorkspaceProject[0]);
		for (final AWorkspaceProject project : projects) {
			getModel().removeProject(project);
		}
		loadFixedDataRootProject();
		getView().setPaintingEnabled(true);
		getView().refreshView();
		if (getModel().getRoot() != null) {
			getView().expandPath(getModel().getRoot().getTreePath());
		}
		FavoritesAndTagsStore.getInstance().reloadAllProjects();
		NodePinsIndex.getInstance().rescan();
		refreshLeftTabMetrics();
	}

	private void loadFixedDataRootProject() {
		final File dataRoot = MindMapDataRootResolver.getFixedDataRoot();
		if (dataRoot == null) {
			LogUtils.severe("Fixed data root is missing or not a directory: "
			        + MindMapDataRootResolver.FIXED_DATA_ROOT_PATH);
			return;
		}
		try {
			final Runnable loadTask = new Runnable() {
				public void run() {
					AWorkspaceProject project = null;
					try {
						final String projectId = MindMapDataRootResolver.resolveProjectIdForDataRoot(dataRoot);
						project = AWorkspaceProject.create(projectId, dataRoot.toURI());
						getModel().addProject(project);
						loadProjectResilient(project);
					}
					catch (Exception e) {
						LogUtils.severe(e);
						if (project != null) {
							getModel().removeProject(project);
						}
					}
				}
			};
			if (SwingUtilities.isEventDispatchThread()) {
				loadTask.run();
			}
			else {
				SwingUtilities.invokeAndWait(loadTask);
			}
		}
		catch (Exception e) {
			LogUtils.severe(e);
		}
	}

	private void loadProjectResilient(final AWorkspaceProject project) throws IOException {
		try {
			getProjectLoader().loadProject(project);
		}
		catch (IOException e) {
			LogUtils.warn("Project settings damaged, rebuilding workspace tree from folder: " + e.getMessage());
			backupAndRemoveProjectSettings(project);
			getProjectLoader().loadProject(project);
		}
	}

	private void refreshFixedLibraryWorkspaceView() {
		final File fixedRoot = MindMapDataRootResolver.getFixedDataRoot();
		if (fixedRoot == null) {
			return;
		}
		for (final AWorkspaceProject project : getModel().getProjects()) {
			final File home = URIUtils.getAbsoluteFile(project.getProjectHome());
			if (home == null || !home.equals(fixedRoot)) {
				continue;
			}
			try {
				getProjectLoader().loadProject(project);
			}
			catch (final IOException e) {
				LogUtils.warn("Could not refresh fixed library workspace: " + e.getMessage());
			}
		}
		getView().refreshView();
	}

	private void backupAndRemoveProjectSettings(final AWorkspaceProject project) {
		final File settingsFile = new File(URIUtils.getAbsoluteFile(project.getProjectDataPath()), "settings.xml");
		if (!settingsFile.exists()) {
			return;
		}
		final File backup = new File(settingsFile.getParentFile(),
		    "settings.xml.corrupt." + System.currentTimeMillis());
		if (!settingsFile.renameTo(backup)) {
			settingsFile.delete();
		}
	}

	@Override
	public void clear() {
		getView().setPaintingEnabled(false);
		AWorkspaceProject[] projects = getModel().getProjects().toArray(new AWorkspaceProject[0]);
		for (AWorkspaceProject project : projects) {
			getModel().removeProject(project);
		}
		getView().setPaintingEnabled(true);
	}

	/**
	 * Switches the left workspace side tab (e.g. back to workspace after opening a mind map from the graph).
	 */
	public void selectSideTab(final String tabId) {
		if (tabId == null || sideTabs == null) {
			return;
		}
		final int index = sideTabOrder.indexOf(tabId);
		if (index >= 0 && index < sideTabs.getTabCount()) {
			sideTabs.setSelectedIndex(index);
		}
	}

	private JComponent createRelationshipGraphSideTabPanel() {
		if (!RelationshipGraphTabBridge.isAvailable()) {
			final JPanel placeholder = new JPanel(new BorderLayout(0, 8));
			placeholder.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
			placeholder.add(new JLabel(
			        "<html><body style='width:220px'>\u5173\u7cfb\u56fe\u63d2\u4ef6\u5c1a\u672a\u6ce8\u518c<br>"
			                + "\u89d2\u6807\u53ef\u80fd\u6765\u81ea\u9759\u9ed8\u9884\u626b\uff0c\u4f46\u4fa7\u680f Provider \u4e3a\u7a7a\u3002"
			                + "</body></html>"),
			        BorderLayout.NORTH);
			final JButton retry = new JButton("\u91cd\u8bd5");
			retry.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					installRelationshipGraphSideTabIfNeeded(true);
				}
			});
			placeholder.add(retry, BorderLayout.SOUTH);
			return placeholder;
		}
		try {
			final JComponent panel = RelationshipGraphTabBridge.getProvider().createSideTabPanel();
			if (panel != null) {
				return panel;
			}
		}
		catch (final Throwable e) {
			LogUtils.warn(e);
			throw new RuntimeException(e);
		}
		throw new IllegalStateException("createSideTabPanel returned null");
	}

	private void notifyRelationshipGraphTabChange(final String previousTabId, final String selectedTabId) {
		if (!RelationshipGraphTabBridge.isAvailable()) {
			return;
		}
		final RelationshipGraphTabBridge.Provider provider = RelationshipGraphTabBridge.getProvider();
		if (TAB_GRAPH.equals(previousTabId) && !TAB_GRAPH.equals(selectedTabId)) {
			provider.onTabDeselected();
		}
		if (TAB_GRAPH.equals(selectedTabId)) {
			provider.onTabSelected();
		}
	}

}
