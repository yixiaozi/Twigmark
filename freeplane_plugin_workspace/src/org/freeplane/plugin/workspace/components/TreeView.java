package org.freeplane.plugin.workspace.components;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.freeplane.core.ui.components.OneTouchCollapseResizer.ComponentCollapseListener;
import org.freeplane.core.ui.components.ResizeEvent;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.features.favorites.FavoritesAndTagsStore;
import org.freeplane.plugin.workspace.dnd.DnDController;
import org.freeplane.plugin.workspace.dnd.WorkspaceTransferHandler;
import org.freeplane.plugin.workspace.event.IWorkspaceNodeActionListener;
import org.freeplane.plugin.workspace.event.WorkspaceActionEvent;
import org.freeplane.plugin.workspace.features.WorkspaceNodeSelectionHandler;
import org.freeplane.plugin.workspace.handler.DefaultNodeTypeIconManager;
import org.freeplane.plugin.workspace.handler.INodeTypeIconManager;
import org.freeplane.plugin.workspace.listener.DefaultTreeExpansionListener;
import org.freeplane.plugin.workspace.listener.DefaultWorkspaceSelectionListener;
import org.freeplane.plugin.workspace.io.IFileSystemRepresentation;
import org.freeplane.plugin.workspace.mindmapmode.DefaultFileDropHandler;
import org.freeplane.plugin.workspace.mindmapmode.FileFolderDropHandler;
import org.freeplane.plugin.workspace.mindmapmode.InputController;
import org.freeplane.plugin.workspace.mindmapmode.VirtualFolderDropHandler;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;
import org.freeplane.plugin.workspace.model.MyFilesTreeDisplayHelper;
import org.freeplane.plugin.workspace.model.WorkspaceModel;
import org.freeplane.plugin.workspace.model.project.AWorkspaceProject;
import org.freeplane.plugin.workspace.model.project.IProjectSelectionListener;
import org.freeplane.plugin.workspace.model.project.ProjectSelectionEvent;
import org.freeplane.plugin.workspace.nodes.AFolderNode;
import org.freeplane.plugin.workspace.nodes.DefaultFileNode;
import org.freeplane.plugin.workspace.nodes.FolderFileNode;
import org.freeplane.plugin.workspace.nodes.FolderLinkNode;
import org.freeplane.plugin.workspace.nodes.FolderTypeMyFilesNode;
import org.freeplane.plugin.workspace.nodes.FolderVirtualNode;
import org.freeplane.plugin.workspace.nodes.LinkTypeFileNode;
import org.freeplane.plugin.workspace.nodes.ProjectRootNode;
import org.freeplane.plugin.workspace.nodes.WorkspaceRootNode;

public class TreeView extends JPanel implements IWorkspaceView, ComponentCollapseListener {
	private static final long serialVersionUID = 1L;
	private static final int view_margin = 3;
	
	protected JTree mTree;
	protected JTextField m_display;
	private WorkspaceTransferHandler transferHandler;
	private INodeTypeIconManager nodeTypeIconManager;
	private List<IProjectSelectionListener> projectSelectionListeners = new ArrayList<IProjectSelectionListener>();
	private AWorkspaceProject lastSelectedProject;
	private InputController inputController;
	private ExpandedStateHandler expandedStateHandler;
	private boolean paintingEnabled;
	private WorkspaceNodeSelectionHandler nodeSelectionHandler;
	private WorkspaceModel sourceModel;
	private String searchQuery = "";
	private final Map<AWorkspaceTreeNode, Boolean> visibleNodeCache = new HashMap<AWorkspaceTreeNode, Boolean>();
	private final Map<AWorkspaceTreeNode, List<AWorkspaceTreeNode>> visibleChildrenCache = new HashMap<AWorkspaceTreeNode, List<AWorkspaceTreeNode>>();
	private boolean allFoldersPreloaded = false;
	/** Expanded paths captured when entering search; restored when search is cleared so auto-expanded matches do not stick. */
	private List<TreePath> expansionSnapshotBeforeSearch;

	public TreeView() {
		this.setLayout(new BorderLayout());

		mTree = new JTree() {
			private static final long serialVersionUID = 1L;

			public void paint(Graphics g) {
				if(isPaintingEnabled()) {
					try {
						super.paint(g);
					}
					catch (Exception e) {
					}
				}
			}
		};
		mTree.setBorder(BorderFactory.createEmptyBorder(2, view_margin, view_margin, view_margin));
		mTree.putClientProperty("JTree.lineStyle", "Angled");
		mTree.setCellRenderer(new WorkspaceNodeRenderer());
		mTree.setCellEditor(new WorkspaceCellEditor(mTree, (DefaultTreeCellRenderer) mTree.getCellRenderer()));
		FavoritesAndTagsStore.getInstance().addChangeListener(new Runnable() {
			public void run() {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						if (mTree != null) {
							mTree.repaint();
						}
					}
				});
			}
		});
		mTree.addTreeExpansionListener(new DefaultTreeExpansionListener());
		mTree.addTreeExpansionListener(getExpandedStateHandler());
		mTree.addTreeSelectionListener(getNodeSelectionHandler().getTreeSelectionListener());
        mTree.addTreeSelectionListener(new DefaultWorkspaceSelectionListener());
        mTree.addTreeSelectionListener(getProjectSelectionHandler());
        //WORKSPACE - impl(later): enable multi selection 
		mTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		mTree.addMouseListener(getInputController());
		mTree.addMouseMotionListener(getInputController());
		mTree.addKeyListener(getInputController());
		mTree.setRowHeight(18);
		mTree.setLargeModel(false);
		mTree.setShowsRootHandles(true);
		mTree.setRootVisible(false);
		mTree.setEditable(true);
		
		this.transferHandler = WorkspaceTransferHandler.configureDragAndDrop(mTree);
		
		initTransferHandler();
		
		initSearchField();
		workspaceBox = Box.createVerticalBox();
		workspaceBox.add(new JScrollPane(mTree));		
		this.add(workspaceBox, BorderLayout.CENTER);
	}

	/** Search field kept for filter API; UI hidden per product request. */
	private void initSearchField() {
		m_display = new JTextField();
		m_display.getDocument().addDocumentListener(new DocumentListener() {
			public void removeUpdate(DocumentEvent e) {
				handleClearedSearchInput();
			}
			public void insertUpdate(DocumentEvent e) {
				handleClearedSearchInput();
			}
			public void changedUpdate(DocumentEvent e) {
				handleClearedSearchInput();
			}
		});
	}

	private void handleClearedSearchInput() {
		if (m_display == null) {
			return;
		}
		String normalized = normalize(m_display.getText());
		if (normalized.length() == 0 && hasSearchFilter()) {
			applySearchFilter(false);
		}
	}

	private void applySearchFilter(boolean forceFullPreload) {
		final String raw = m_display != null ? m_display.getText() : "";
		final boolean hadSearchBefore = hasSearchFilter();
		final String normalized = normalize(raw);
		if (!hadSearchBefore && normalized.length() > 0) {
			expansionSnapshotBeforeSearch = captureExpandedPaths();
		}
		searchQuery = normalized;
		updateSearchHighlight();
		if (sourceModel != null) {
			mTree.setModel(new TreeModelProxy(sourceModel));
		}
		if (hasSearchFilter()) {
			if (forceFullPreload) {
				refreshTopLevelProjects();
				allFoldersPreloaded = false;
				preloadAllFolderNodes(false);
			}
			else if (!allFoldersPreloaded) {
				preloadAllFolderNodes(false);
			}
		}
		clearFilterCaches();
		collapseAllRows();
		mTree.treeDidChange();
		if (searchQuery.length() > 0) {
			expandVisiblePaths();
		}
		else {
			if (expansionSnapshotBeforeSearch != null) {
				restoreExpansionSnapshot();
				expansionSnapshotBeforeSearch = null;
			}
			else {
				restoreExpandedState();
			}
		}
		mTree.revalidate();
		mTree.repaint();
	}

	private void refreshTopLevelProjects() {
		if (sourceModel == null) {
			return;
		}
		Object root = sourceModel.getRoot();
		if (!(root instanceof AWorkspaceTreeNode)) {
			return;
		}
		final List<AWorkspaceTreeNode> topNodes = getRawChildren((AWorkspaceTreeNode) root);
		for (AWorkspaceTreeNode node : topNodes) {
			try {
				node.refresh();
			}
			catch (Exception e) {
				LogUtils.warn(e);
			}
		}
	}

	private void updateSearchHighlight() {
		if (mTree.getCellRenderer() instanceof WorkspaceNodeRenderer) {
			((WorkspaceNodeRenderer) mTree.getCellRenderer()).setHighlightQuery(searchQuery);
		}
	}

	private void preloadAllFolderNodes(boolean refreshExistingNodes) {
		if (sourceModel == null) {
			return;
		}
		Object root = sourceModel.getRoot();
		if (!(root instanceof AWorkspaceTreeNode)) {
			return;
		}
		try {
			preloadAllFolderNodesRecursive((AWorkspaceTreeNode) root, refreshExistingNodes);
			allFoldersPreloaded = true;
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
	}

	private void preloadAllFolderNodesRecursive(AWorkspaceTreeNode node, boolean refreshExistingNodes) {
		if (node == null) {
			return;
		}
		if (node instanceof FolderFileNode || node instanceof FolderLinkNode) {
			try {
				if (refreshExistingNodes || node.getChildCount() == 0) {
					node.refresh();
				}
			}
			catch (Exception e) {
				LogUtils.warn(e);
			}
		}
		List<AWorkspaceTreeNode> children = getRawChildren(node);
		for (AWorkspaceTreeNode child : children) {
			preloadAllFolderNodesRecursive(child, refreshExistingNodes);
		}
	}

	private void collapseAllRows() {
		for (int row = mTree.getRowCount() - 1; row >= 0; row--) {
			mTree.collapseRow(row);
		}
	}

	private List<TreePath> captureExpandedPaths() {
		List<TreePath> list = new ArrayList<TreePath>();
		TreeModel tm = mTree.getModel();
		if (tm == null) {
			return list;
		}
		Object root = tm.getRoot();
		if (root == null) {
			return list;
		}
		Enumeration<TreePath> desc = mTree.getExpandedDescendants(new TreePath(root));
		if (desc != null) {
			while (desc.hasMoreElements()) {
				list.add(desc.nextElement());
			}
		}
		return list;
	}

	private void restoreExpansionSnapshot() {
		List<TreePath> paths = expansionSnapshotBeforeSearch;
		if (paths == null || paths.isEmpty()) {
			restoreExpandedState();
			return;
		}
		try {
			for (TreePath tp : paths) {
				if (tp != null) {
					mTree.expandPath(tp);
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn(e);
			restoreExpandedState();
		}
	}

	private void restoreExpandedState() {
		try {
			TreeModel treeModel = mTree.getModel();
			Object root = treeModel != null ? treeModel.getRoot() : null;
			if (root instanceof AWorkspaceTreeNode) {
				getExpandedStateHandler().setExpandedStates(((AWorkspaceTreeNode) root).getModel(), true);
			}
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
	}

	private void expandVisiblePaths() {
		TreeModel treeModel = mTree.getModel();
		if (treeModel == null) {
			return;
		}
		Object root = treeModel.getRoot();
		if (!(root instanceof AWorkspaceTreeNode)) {
			return;
		}
		expandVisiblePathsRecursive((AWorkspaceTreeNode) root);
	}

	private void expandVisiblePathsRecursive(AWorkspaceTreeNode node) {
		List<AWorkspaceTreeNode> children = getVisibleChildren(node);
		if (children.isEmpty()) {
			return;
		}
		mTree.expandPath(getDisplayTreePath(node));
		for (AWorkspaceTreeNode child : children) {
			expandVisiblePathsRecursive(child);
		}
	}

	private void clearFilterCaches() {
		visibleNodeCache.clear();
		visibleChildrenCache.clear();
	}

	private boolean hasSearchFilter() {
		return searchQuery != null && searchQuery.length() > 0;
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
	}

	private boolean containsQuery(String text) {
		if (text == null) {
			return false;
		}
		return normalize(text).indexOf(searchQuery) >= 0;
	}

	private boolean matchesNode(AWorkspaceTreeNode node) {
		if (containsQuery(node.getName())) {
			return true;
		}
		if (node instanceof IFileSystemRepresentation) {
			try {
				File file = ((IFileSystemRepresentation) node).getFile();
				if (file != null) {
					if (containsQuery(file.getName())) {
						return true;
					}
					// Keep search responsive: rely on in-memory node/file names.
				}
			}
			catch (Exception e) {
				LogUtils.warn(e);
			}
		}
		return false;
	}

	private boolean isNodeVisible(AWorkspaceTreeNode node) {
		if (!hasSearchFilter()) {
			return true;
		}
		Boolean cached = visibleNodeCache.get(node);
		if (cached != null) {
			return cached.booleanValue();
		}
		boolean visible = matchesNode(node);
		if (!visible) {
			List<AWorkspaceTreeNode> children = getRawChildren(node);
			for (AWorkspaceTreeNode child : children) {
				if (isNodeVisible(child)) {
					visible = true;
					break;
				}
			}
		}
		visibleNodeCache.put(node, Boolean.valueOf(visible));
		return visible;
	}

	private List<AWorkspaceTreeNode> getVisibleChildren(AWorkspaceTreeNode parent) {
		if (!hasSearchFilter()) {
			return getRawChildren(parent);
		}
		if (parent instanceof AFolderNode && matchesNode(parent)) {
			// If a folder itself matches, show its direct children (one level).
			ensureFolderChildrenLoaded(parent);
			return getRawChildren(parent);
		}
		List<AWorkspaceTreeNode> cached = visibleChildrenCache.get(parent);
		if (cached != null) {
			return cached;
		}
		List<AWorkspaceTreeNode> visibleChildren = new ArrayList<AWorkspaceTreeNode>();
		List<AWorkspaceTreeNode> children = getRawChildren(parent);
		for (AWorkspaceTreeNode child : children) {
			if (isNodeVisible(child)) {
				visibleChildren.add(child);
			}
		}
		visibleChildrenCache.put(parent, visibleChildren);
		return visibleChildren;
	}

	private void ensureFolderChildrenLoaded(AWorkspaceTreeNode node) {
		if (node == null) {
			return;
		}
		try {
			if (node.getChildCount() > 0) {
				return;
			}
			if (node instanceof FolderFileNode || node instanceof FolderLinkNode) {
				node.refresh();
			}
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
	}

	private boolean isFixedLibraryHoistActive() {
		return getFixedLibraryProject() != null;
	}

	private org.freeplane.plugin.workspace.model.project.AWorkspaceProject getFixedLibraryProject() {
		if (sourceModel == null) {
			return null;
		}
		final File fixedRoot = MindMapDataRootResolver.getFixedDataRoot();
		if (fixedRoot == null) {
			return null;
		}
		try {
			final List projects = WorkspaceController.getCurrentModel().getProjects();
			if (projects == null) {
				return null;
			}
			for (int i = 0; i < projects.size(); i++) {
				final org.freeplane.plugin.workspace.model.project.AWorkspaceProject project =
				    (org.freeplane.plugin.workspace.model.project.AWorkspaceProject) projects.get(i);
				final File projectHome = resolveProjectHome(project);
				if (projectHome != null && projectHome.equals(fixedRoot)) {
					return project;
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
		return null;
	}

	private File resolveProjectHome(final org.freeplane.plugin.workspace.model.project.AWorkspaceProject project) {
		if (project == null) {
			return null;
		}
		File home = org.freeplane.plugin.workspace.URIUtils.getAbsoluteFile(project.getProjectHome());
		if (home == null) {
			home = org.freeplane.plugin.workspace.URIUtils.getFile(project.getProjectHome());
		}
		return home;
	}

	private boolean isHiddenHoistedNode(final AWorkspaceTreeNode node) {
		if (!isFixedLibraryHoistActive() || node == null) {
			return false;
		}
		return node instanceof FolderTypeMyFilesNode || MyFilesTreeDisplayHelper.isProjectRoot(node);
	}

	private TreePath getWorkspaceRootTreePath() {
		if (sourceModel == null || sourceModel.getRoot() == null) {
			return null;
		}
		return new TreePath(sourceModel.getRoot());
	}

	private List<AWorkspaceTreeNode> getFixedLibraryDisplayChildren() {
		final org.freeplane.plugin.workspace.model.project.AWorkspaceProject project = getFixedLibraryProject();
		if (project == null || project.getModel().getRoot() == null) {
			return null;
		}
		final List<AWorkspaceTreeNode> children = MyFilesTreeDisplayHelper.getDisplayChildren(project.getModel().getRoot());
		return children != null ? children : new ArrayList<AWorkspaceTreeNode>();
	}

	private List<AWorkspaceTreeNode> getHoistedWorkspaceRootChildren() {
		if (isFixedLibraryHoistActive()) {
			return getFixedLibraryDisplayChildren();
		}
		if (sourceModel == null) {
			return null;
		}
		try {
			final List projects = WorkspaceController.getCurrentModel().getProjects();
			if (projects == null || projects.isEmpty()) {
				return null;
			}
			if (projects.size() == 1) {
				final org.freeplane.plugin.workspace.model.project.AWorkspaceProject project =
				    (org.freeplane.plugin.workspace.model.project.AWorkspaceProject) projects.get(0);
				return MyFilesTreeDisplayHelper.getDisplayChildren(project.getModel().getRoot());
			}
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
		return null;
	}

	private List<AWorkspaceTreeNode> getRawChildren(AWorkspaceTreeNode parent) {
		List<AWorkspaceTreeNode> children = new ArrayList<AWorkspaceTreeNode>();
		if (parent == null) {
			return children;
		}
		try {
			if (sourceModel != null && parent == sourceModel.getRoot()) {
				if (isFixedLibraryHoistActive()) {
					final List<AWorkspaceTreeNode> hoisted = getFixedLibraryDisplayChildren();
					if (hoisted != null) {
						return hoisted;
					}
				}
				final List<AWorkspaceTreeNode> hoisted = getHoistedWorkspaceRootChildren();
				if (hoisted != null && !hoisted.isEmpty()) {
					return hoisted;
				}
			}
			if (MyFilesTreeDisplayHelper.isProjectRoot(parent)) {
				final List<AWorkspaceTreeNode> hoisted = MyFilesTreeDisplayHelper.getDisplayChildren(parent);
				if (hoisted != null) {
					return hoisted;
				}
			}
			boolean useSourceModel = sourceModel != null && parent == sourceModel.getRoot();
			int count = useSourceModel ? sourceModel.getChildCount(parent) : parent.getChildCount();
			for (int i = 0; i < count; i++) {
				Object child = useSourceModel ? sourceModel.getChild(parent, i) : parent.getChildAt(i);
				if (child instanceof AWorkspaceTreeNode) {
					final AWorkspaceTreeNode treeChild = (AWorkspaceTreeNode) child;
					if (!MyFilesTreeDisplayHelper.isHiddenWorkspaceFolder(treeChild)) {
						children.add(treeChild);
					}
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
		return children;
	}
	
	public void addBottomBanner(Component comp) {
		workspaceBox.remove(comp);
		workspaceBox.add(comp);
		workspaceBox.validate();
	}
	
	public void removeBottomBanner(Component comp) {
		workspaceBox.remove(comp);
		workspaceBox.validate();
	}
	
	private ExpandedStateHandler getExpandedStateHandler() {
		if(expandedStateHandler == null) {
			expandedStateHandler = new ExpandedStateHandler(this);
		}
		return expandedStateHandler;
	}

	private void initTransferHandler() {
		getTransferHandler().registerNodeDropHandler(DefaultFileNode.class, new DefaultFileDropHandler());
	
		getTransferHandler().registerNodeDropHandler(FolderFileNode.class, new FileFolderDropHandler());
		getTransferHandler().registerNodeDropHandler(FolderLinkNode.class, new FileFolderDropHandler());
		getTransferHandler().registerNodeDropHandler(FolderTypeMyFilesNode.class, new FileFolderDropHandler());
		
		getTransferHandler().registerNodeDropHandler(FolderVirtualNode.class, new VirtualFolderDropHandler());
		getTransferHandler().registerNodeDropHandler(ProjectRootNode.class, new VirtualFolderDropHandler());
		
		//default fallback for folder
		getTransferHandler().registerNodeDropHandler(AFolderNode.class, new VirtualFolderDropHandler());
		
		DnDController.excludeFromDND(WorkspaceRootNode.class);
		DnDController.excludeFromDND(LinkTypeFileNode.class);
		DnDController.excludeFromDND(DefaultFileNode.class);
	}

	public InputController getInputController() {
		if(inputController == null) {
			inputController = new InputController();
		}
		return inputController;
	}

	private TreeSelectionListener getProjectSelectionHandler() {
		return new TreeSelectionListener() {			
			public void valueChanged(TreeSelectionEvent e) {
				try {
					AWorkspaceProject selected = WorkspaceController.getCurrentModel().getProject(((AWorkspaceTreeNode) e.getNewLeadSelectionPath().getLastPathComponent()).getModel());				
					if(selected != lastSelectedProject) {
						fireProjectSelectionChanged(selected);
					}
				}
				catch (Exception ex) {
					// just for convenience, ignore everything 
				}
			}
		};
	}

	public void addTreeMouseListener(MouseListener l) {
		this.mTree.addMouseListener(l);
	}

	public void addTreeComponentListener(ComponentListener l) {
		this.mTree.addComponentListener(l);
	}
	
	public void setPreferredSize(Dimension size) {
		super.setPreferredSize(new Dimension(Math.max(size.width, getMinimumSize().width), Math.max(size.height, getMinimumSize().height)));	
	}

	/**
	 * Tree path compatible with {@link TreeModelProxy} when "My files" children are hoisted
	 * above the project root (skips hidden project / my-files nodes in the ancestry).
	 */
	public TreePath getDisplayTreePath(AWorkspaceTreeNode node) {
		if (node == null) {
			return null;
		}
		if (isHiddenHoistedNode(node)) {
			return getWorkspaceRootTreePath();
		}
		final List<Object> path = new ArrayList<Object>();
		AWorkspaceTreeNode current = node;
		while (current != null) {
			if (!isHiddenHoistedNode(current)) {
				path.add(0, current);
			}
			current = getDisplayParent(current);
		}
		if (path.isEmpty()) {
			return getWorkspaceRootTreePath();
		}
		return new TreePath(path.toArray());
	}

	private AWorkspaceTreeNode getDisplayParent(AWorkspaceTreeNode node) {
		final AWorkspaceTreeNode parent = node.getParent();
		if (parent == null) {
			return null;
		}
		if (parent instanceof FolderTypeMyFilesNode) {
			final AWorkspaceTreeNode projectRoot = parent.getParent();
			if (projectRoot != null && MyFilesTreeDisplayHelper.isProjectRoot(projectRoot)
			        && (isFixedLibraryHoistActive() || getHoistedWorkspaceRootChildren() != null)) {
				return sourceModel != null ? sourceModel.getRoot() : projectRoot;
			}
			return projectRoot;
		}
		if (MyFilesTreeDisplayHelper.isProjectRoot(parent)
		        && (isFixedLibraryHoistActive() || getHoistedWorkspaceRootChildren() != null)) {
			return sourceModel != null ? sourceModel.getRoot() : parent;
		}
		return parent;
	}

	public void expandPath(final TreePath treePath) {
		if(EventQueue.isDispatchThread()) {
			try {
				mTree.expandPath(treePath);
			}
			catch(Exception e) {
				LogUtils.warn("TreeView.expandPath(): ", e);
			}
		}
		else {
			try {
				SwingUtilities.invokeAndWait(new Runnable() {
					public void run() {
						try {
							mTree.expandPath(treePath);
						}
						catch(Exception e) {
							LogUtils.warn("TreeView.expandPath(): ", e);
						}
					}
				});
			} catch (Exception e) {
				LogUtils.warn(e);
			}
		}
	}

	public void collapsePath(TreePath treePath) {
		mTree.collapsePath(treePath);		
	}
	
	public void refreshView() {
		paintingEnabled = true;
		clearFilterCaches();
		mTree.treeDidChange();
		if (hasSearchFilter()) {
			expandVisiblePaths();
		}
		repaint();
	}
	
	public void setModel(WorkspaceModel model) {
		sourceModel = model;
		mTree.setModel(new TreeModelProxy(model));
		getExpandedStateHandler().registerModel(model);
		allFoldersPreloaded = false;
		clearFilterCaches();
		if (hasSearchFilter()) {
			expandVisiblePaths();
		}
	}
	
	public WorkspaceTransferHandler getTransferHandler() {
		return this.transferHandler;
	}
	
	public void addSelectionPath(TreePath path) {
		mTree.addSelectionPath(path);		
	}
		
	public boolean isPaintingEnabled() {
		return paintingEnabled;
	}
	
	public void setPaintingEnabled(boolean enabled) {
		this.paintingEnabled = enabled;
	}

	public class TreeModelProxy implements TreeModel {
		private final WorkspaceModel model;
		private final List<TreeModelListener> proxyListeners;
		/**
		 * Receives low-level events from {@link #model} and re-dispatches them to
		 * {@link JTree} listeners. When a search filter is active, the proxy exposes a
		 * <em>different</em> child count/order than the source model, so raw
		 * {@code treeNodesInserted/Removed/Changed} from the model carry indices for the
		 * <em>unfiltered</em> list and will corrupt the tree UI (blank lines, extra rows).
		 * In that case we translate to {@code treeStructureChanged} for the affected path.
		 */
		private final TreeModelListener modelRelay;

		public TreeModelProxy(WorkspaceModel model) {
			this.model = model;
			this.proxyListeners = new ArrayList<TreeModelListener>();
			this.modelRelay = new TreeModelListener() {
				@Override
				public void treeNodesChanged(TreeModelEvent e) {
					relayEvent(e, 0);
				}
				@Override
				public void treeNodesInserted(TreeModelEvent e) {
					relayEvent(e, 1);
				}
				@Override
				public void treeNodesRemoved(TreeModelEvent e) {
					relayEvent(e, 2);
				}
				@Override
				public void treeStructureChanged(TreeModelEvent e) {
					relayEvent(e, 3);
				}
			};
		}

		private void relayEvent(TreeModelEvent e, int eventKind) {
			List<TreeModelListener> copy;
			synchronized (proxyListeners) {
				copy = new ArrayList<TreeModelListener>(proxyListeners);
			}
			if (copy.isEmpty()) {
				return;
			}
			if (TreeView.this.hasSearchFilter()) {
				clearFilterCaches();
				TreePath path = eventPathToRefresh(e);
				if (path == null) {
					return;
				}
				TreeModelEvent refresh = new TreeModelEvent(this, path);
				for (TreeModelListener l : copy) {
					l.treeStructureChanged(refresh);
				}
				runAfterModelChangeWhileFiltering();
			}
			else if (TreeView.this.isFixedLibraryHoistActive()) {
				TreeView.this.clearFilterCaches();
				TreePath refreshPath = displayRefreshPathForModelEvent(e);
				if (refreshPath == null) {
					final Object root = getRoot();
					if (root != null) {
						refreshPath = new TreePath(root);
					}
				}
				if (refreshPath == null) {
					return;
				}
				final TreeModelEvent refresh = new TreeModelEvent(this, refreshPath);
				for (TreeModelListener l : copy) {
					l.treeStructureChanged(refresh);
				}
			}
			else {
				if (eventKind == 3) {
					TreeView.this.clearFilterCaches();
				}
				TreeModelEvent out = toDisplayModelEvent(withProxySource(e));
				for (TreeModelListener l : copy) {
					switch (eventKind) {
					case 0: l.treeNodesChanged(out); break;
					case 1: l.treeNodesInserted(out); break;
					case 2: l.treeNodesRemoved(out); break;
					case 3: l.treeStructureChanged(out); break;
					}
				}
			}
		}

		private TreePath displayRefreshPathForModelEvent(final TreeModelEvent e) {
			TreePath path = e.getTreePath();
			if (path == null && e.getPath() != null) {
				path = new TreePath(e.getPath());
			}
			if (path == null) {
				return TreeView.this.getWorkspaceRootTreePath();
			}
			final Object last = path.getLastPathComponent();
			if (last instanceof AWorkspaceTreeNode) {
				final AWorkspaceTreeNode node = (AWorkspaceTreeNode) last;
				if (TreeView.this.isHiddenHoistedNode(node)
				        || (TreeView.this.sourceModel != null && node == TreeView.this.sourceModel.getRoot())) {
					return TreeView.this.getWorkspaceRootTreePath();
				}
				final TreePath displayPath = TreeView.this.getDisplayTreePath(node);
				return displayPath != null ? displayPath : TreeView.this.getWorkspaceRootTreePath();
			}
			return path;
		}

		private TreeModelEvent toDisplayModelEvent(TreeModelEvent e) {
			TreePath path = e.getTreePath();
			if (path == null && e.getPath() != null) {
				path = new TreePath(e.getPath());
			}
			if (path != null && path.getLastPathComponent() instanceof AWorkspaceTreeNode) {
				final TreePath displayPath = TreeView.this.getDisplayTreePath(
				    (AWorkspaceTreeNode) path.getLastPathComponent());
				if (displayPath != null) {
					path = displayPath;
				}
			}
			if (e.getChildIndices() == null && e.getChildren() == null) {
				return new TreeModelEvent(this, path);
			}
			return new TreeModelEvent(this, path, e.getChildIndices(), e.getChildren());
		}

		private void runAfterModelChangeWhileFiltering() {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					if (!TreeView.this.hasSearchFilter()) {
						return;
					}
					clearFilterCaches();
					TreeView.this.expandVisiblePaths();
					TreeView.this.mTree.revalidate();
					TreeView.this.mTree.repaint();
				}
			});
		}

		private TreePath eventPathToRefresh(TreeModelEvent e) {
			TreePath p = e.getTreePath();
			if (p == null) {
				if (e.getPath() != null) {
					p = new TreePath(e.getPath());
				}
			}
			if (p == null) {
				Object r = getRoot();
				if (r != null) {
					p = new TreePath(r);
				}
			}
			return p;
		}

		/**
		 * JTree and default handlers often assume {@code TreeModelEvent#getSource()} is
		 * the same instance as the tree's {@code TreeModel} (this proxy), not the backing
		 * {@link WorkspaceModel}.
		 */
		private TreeModelEvent withProxySource(TreeModelEvent e) {
			TreePath path = e.getTreePath();
			if (path == null) {
				if (e.getPath() != null) {
					path = new TreePath(e.getPath());
				}
			}
			if (e.getChildIndices() == null && e.getChildren() == null) {
				return new TreeModelEvent(this, path);
			}
			return new TreeModelEvent(this, path, e.getChildIndices(), e.getChildren());
		}

		public Object getRoot() {
			if(model == null) return null;
			return model.getRoot();
		}

		public Object getChild(Object parent, int index) {
			if (parent == null) {
				return null;
			}
			if (!(parent instanceof AWorkspaceTreeNode)) {
				return model.getChild(parent, index);
			}
			List<AWorkspaceTreeNode> children = getVisibleChildren((AWorkspaceTreeNode) parent);
			if (index < 0 || index >= children.size()) {
				throw new ArrayIndexOutOfBoundsException(index);
			}
			return children.get(index);
		}

		public int getChildCount(Object parent) {
			if (parent == null) {
				return 0;
			}
			if (!(parent instanceof AWorkspaceTreeNode)) {
				return model.getChildCount(parent);
			}
			return getVisibleChildren((AWorkspaceTreeNode) parent).size();
		}

		public boolean isLeaf(Object node) {
			if (!(node instanceof AWorkspaceTreeNode)) {
				return model.isLeaf(node);
			}
			return ((AWorkspaceTreeNode) node).isLeaf();
		}

		public void valueForPathChanged(TreePath path, Object newValue) {
			AWorkspaceTreeNode node = (AWorkspaceTreeNode) path.getLastPathComponent();
			if (node instanceof IWorkspaceNodeActionListener) {
				((IWorkspaceNodeActionListener) node).handleAction(new WorkspaceActionEvent(node, WorkspaceActionEvent.WSNODE_CHANGED, newValue));
				//nodeChanged(node);
			}
			else {
				node.setName(newValue.toString());
			}
		}

		public int getIndexOfChild(Object parent, Object child) {
			if (!(parent instanceof AWorkspaceTreeNode) || !(child instanceof AWorkspaceTreeNode)) {
				return model.getIndexOfChild(parent, child);
			}
			List<AWorkspaceTreeNode> children = getVisibleChildren((AWorkspaceTreeNode) parent);
			return children.indexOf(child);
		}

		public void addTreeModelListener(TreeModelListener l) {
			synchronized (proxyListeners) {
				if (proxyListeners.isEmpty()) {
					this.model.addTreeModelListener(modelRelay);
				}
				if (!proxyListeners.contains(l)) {
					proxyListeners.add(l);
				}
			}
		}

		public void removeTreeModelListener(TreeModelListener l) {
			synchronized (proxyListeners) {
				proxyListeners.remove(l);
				if (proxyListeners.isEmpty()) {
					this.model.removeTreeModelListener(modelRelay);
				}
			}
		}

	}

	public boolean containsComponent(Component comp) {
		if(this.equals(comp)) {
			return true;
		}
		else if(mTree.equals(comp)) {
			return true;
		}
		return false;
	}

	public TreePath getSelectionPath() {
		return mTree.getSelectionPath();
	}

	public TreePath getPathForLocation(int x, int y) {
		return mTree.getClosestPathForLocation(x, y);
	}

	public INodeTypeIconManager getNodeTypeIconManager() {
		if(nodeTypeIconManager == null) {
			nodeTypeIconManager = new DefaultNodeTypeIconManager();
		}
		return nodeTypeIconManager;
	}

	public void componentCollapsed(ResizeEvent event) {
		if(this.equals(event.getComponent())) {
			super.setPreferredSize(new Dimension(0, getPreferredSize().height));
		}
	}

	public void componentExpanded(ResizeEvent event) {
		if(this.equals(event.getComponent())) {
			// nothing
		}
	}

	public AWorkspaceTreeNode getNodeForLocation(int x, int y) {
		TreePath path = mTree.getPathForLocation(x, y);
		if(path == null) {
			return null;
		}
		return (AWorkspaceTreeNode) path.getLastPathComponent();		
	}

	public void addProjectSelectionListener(IProjectSelectionListener listener) {
		if(listener == null) {
			return;
		}
		synchronized (projectSelectionListeners ) {
			projectSelectionListeners.add(listener);
		}		
	}
	
	private void fireProjectSelectionChanged(AWorkspaceProject selected) {
//		if(selected == null) {
//			return;
//		}
		ProjectSelectionEvent event = new ProjectSelectionEvent(this, selected, this.lastSelectedProject);
		this.lastSelectedProject = selected;
		synchronized (projectSelectionListeners ) {
			for (IProjectSelectionListener listener : projectSelectionListeners) {
				listener.selectionChanged(event);
			}
		}
		
	}

	public void expandAll(AWorkspaceTreeNode nodeFromActionEvent) {
		for (int i = 1; i < mTree.getRowCount(); i++) {
			mTree.expandRow(i);
		}		
	}

	public final static String BOTTOM_TOOLBAR_STACK = BorderLayout.SOUTH;
	public final static String TOP_TOOLBAR_STACK = BorderLayout.NORTH;
	private Box workspaceBox;
	public void addToolBar(Component comp, String toolBarStack) {
		this.add(comp, toolBarStack);
	}

	public WorkspaceNodeSelectionHandler getNodeSelectionHandler() {
		if(nodeSelectionHandler == null) {
			nodeSelectionHandler = new WorkspaceNodeSelectionHandler();
		}
		return nodeSelectionHandler;
	}

	public Component getComponent() {
		return this;
	}
}
