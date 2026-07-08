package org.freeplane.plugin.workspace.components.currentmapfolder;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URL;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.IMapSelectionListener;
import org.freeplane.features.map.MapChangeEvent;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mapio.MapIO;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.features.url.UrlManager;
import org.freeplane.plugin.workspace.components.menu.WorkspacePopupMenu;
import org.freeplane.plugin.workspace.handler.WorkspaceDocumentOpenRegistry;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;
import org.freeplane.plugin.workspace.nodes.DefaultFileNode;
import org.freeplane.plugin.workspace.nodes.FolderFileNode;

/**
 * Right-side tab: tree of files in the folder that contains the current mind map.
 */
public final class CurrentMapFolderTabPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private final JLabel emptyLabel = new JLabel("\u5f53\u524d\u5bfc\u56fe\u672a\u4fdd\u5b58\u6216\u65e0\u6240\u5728\u76ee\u5f55", JLabel.CENTER);
	private final MapFolderTree tree = new MapFolderTree();
	private DefaultTreeModel treeModel;
	private File currentMapFile;
	private File currentFolder;

	public CurrentMapFolderTabPanel(final ModeController modeController) {
		super(new BorderLayout());
		setName("\u6587\u4ef6");
		tree.setRootVisible(true);
		tree.setShowsRootHandles(true);
		tree.setRowHeight(18);
		tree.setCellRenderer(new FolderTreeCellRenderer());
		tree.addTreeExpansionListener(new TreeExpansionListener() {
			public void treeExpanded(final TreeExpansionEvent event) {
				expandNode(event.getPath());
			}

			public void treeCollapsed(final TreeExpansionEvent event) {
			}
		});
		installMouseHandler();
		add(emptyLabel, BorderLayout.CENTER);
		addListeners(modeController);
		syncToCurrentMap();
	}

	private void installMouseHandler() {
		final MouseAdapter handler = new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				handleMouse(e);
			}

			public void mouseReleased(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					handleMouse(e);
				}
			}
		};
		tree.addMouseListener(handler);
	}

	private void handleMouse(final MouseEvent e) {
		final TreePath path = tree.getPathForLocation(e.getX(), e.getY());
		if (path == null) {
			return;
		}
		tree.setSelectionPath(path);
		final SimpleFileTreeNode node = (SimpleFileTreeNode) path.getLastPathComponent();
		if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
			showContextMenu(node, e.getX(), e.getY());
			return;
		}
		if (e.getClickCount() >= 2 && e.getButton() == MouseEvent.BUTTON1) {
			openFile(node.getFile());
		}
	}

	private void showContextMenu(final SimpleFileTreeNode node, final int x, final int y) {
		final AWorkspaceTreeNode shadow = createShadowNode(node, node.getFile());
		tree.setContextShadow(shadow);
		final WorkspacePopupMenu popup = shadow.getContextMenu();
		if (popup == null) {
			tree.setContextShadow(null);
			return;
		}
		popup.addPopupMenuListener(new PopupMenuListener() {
			public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
			}

			public void popupMenuWillBecomeInvisible(final PopupMenuEvent e) {
				tree.setContextShadow(null);
			}

			public void popupMenuCanceled(final PopupMenuEvent e) {
				tree.setContextShadow(null);
			}
		});
		shadow.showPopup(tree, x, y);
	}

	private AWorkspaceTreeNode createShadowNode(final SimpleFileTreeNode treeNode, final File file) {
		if (file.isDirectory()) {
			return new ShadowFolderNode(file, treeNode, this);
		}
		return new ShadowFileNode(file, treeNode, this);
	}

	void refreshTreeNode(final SimpleFileTreeNode node) {
		if (treeModel == null || node == null) {
			return;
		}
		if (node.getFile().isDirectory()) {
			final boolean expanded = tree.isExpanded(new TreePath(node.getPath()));
			node.refresh(treeModel);
			if (expanded) {
				tree.expandPath(new TreePath(node.getPath()));
			}
		}
		updateMetric(currentFolder);
		tree.repaint();
	}

	void refreshParentOf(final SimpleFileTreeNode node) {
		if (node == null || treeModel == null) {
			return;
		}
		final SimpleFileTreeNode parent = (SimpleFileTreeNode) node.getParent();
		if (parent != null) {
			refreshTreeNode(parent);
		}
		else {
			rebuildTree(currentFolder);
		}
	}

	private void openFile(final File file) {
		if (file == null || !file.exists()) {
			return;
		}
		final String lower = file.getName().toLowerCase();
		if (lower.endsWith(".mm") || lower.endsWith(".dcr")) {
			try {
				final URL mapUrl = Compat.fileToUrl(file);
				final MapIO mapIO = (MapIO) Controller.getCurrentModeController().getExtension(MapIO.class);
				mapIO.newMap(mapUrl);
			}
			catch (final Exception e) {
				LogUtils.severe(e);
			}
			return;
		}
		if (WorkspaceDocumentOpenRegistry.tryOpen(file)) {
			return;
		}
		try {
			Controller.getCurrentController().getViewController().openDocument(Compat.fileToUrl(file));
		}
		catch (final Exception e) {
			LogUtils.warn("could not open document (" + file + ")", e);
		}
	}

	private void addListeners(final ModeController modeController) {
		final MapController mapController = modeController.getMapController();
		mapController.addMapChangeListener(new IMapChangeListener() {
			public void mapChanged(final MapChangeEvent event) {
				if (UrlManager.MAP_URL.equals(event.getProperty())) {
					syncToCurrentMap();
				}
			}

			public void onNodeInserted(final NodeModel parent, final NodeModel child, final int newIndex) {
			}

			public void onNodeDeleted(final NodeModel parent, final NodeModel child, final int index) {
			}

			public void onNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
			        final NodeModel child, final int newIndex) {
			}

			public void onPreNodeDelete(final NodeModel oldParent, final NodeModel selectedNode, final int index) {
			}

			public void onPreNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
			        final NodeModel child, final int newIndex) {
			}
		});
		final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
		mapViewManager.addMapSelectionListener(new IMapSelectionListener() {
			public void beforeMapChange(final MapModel oldMap, final MapModel newMap) {
			}

			public void afterMapChange(final MapModel oldMap, final MapModel newMap) {
				syncToCurrentMap();
			}
		});
	}

	private void syncToCurrentMap() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					syncToCurrentMap();
				}
			});
			return;
		}
		final MapModel map = Controller.getCurrentController().getMap();
		File mapFile = map != null ? map.getFile() : null;
		File folder = null;
		if (mapFile != null) {
			final File parent = mapFile.getParentFile();
			if (parent != null && parent.isDirectory()) {
				folder = parent;
			}
		}
		currentMapFile = mapFile;
		if (folder == null) {
			currentFolder = null;
			updateMetric(null);
			showEmptyState();
			return;
		}
		updateMetric(folder);
		if (folder.equals(currentFolder) && treeModel != null) {
			highlightCurrentMap();
			return;
		}
		rebuildTree(folder);
	}

	private void rebuildTree(final File folder) {
		currentFolder = folder;
		try {
			final SimpleFileTreeNode root = new SimpleFileTreeNode(folder);
			treeModel = new DefaultTreeModel(root);
			tree.setModel(treeModel);
			root.loadChildren(treeModel);
			showTree();
			highlightCurrentMap();
		}
		catch (final Exception e) {
			LogUtils.warn("Could not load current map folder tree: " + e.getMessage(), e);
			showEmptyState();
		}
	}

	private void expandNode(final TreePath path) {
		if (treeModel == null || path == null) {
			return;
		}
		final Object last = path.getLastPathComponent();
		if (last instanceof SimpleFileTreeNode) {
			((SimpleFileTreeNode) last).loadChildren(treeModel);
		}
	}

	private void updateMetric(final File folder) {
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_CURRENT_FOLDER, countFilesRecursively(folder));
	}

	private static int countFilesRecursively(final File folder) {
		if (folder == null || !folder.isDirectory()) {
			return 0;
		}
		int count = 0;
		final File[] children = folder.listFiles();
		if (children == null) {
			return 0;
		}
		for (int i = 0; i < children.length; i++) {
			final File child = children[i];
			if (child.isFile()) {
				count++;
			}
			else if (child.isDirectory()) {
				count += countFilesRecursively(child);
			}
		}
		return count;
	}

	private void showEmptyState() {
		treeModel = null;
		tree.setModel(new DefaultTreeModel(null));
		removeAll();
		add(emptyLabel, BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private void showTree() {
		if (tree.getParent() == null) {
			removeAll();
			add(new JScrollPane(tree), BorderLayout.CENTER);
		}
		revalidate();
		repaint();
	}

	private static final class FolderTreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = 1L;

		public java.awt.Component getTreeCellRendererComponent(final javax.swing.JTree tree, final Object value,
		        final boolean selected, final boolean expanded, final boolean leaf, final int row,
		        final boolean hasFocus) {
			super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
			if (value instanceof SimpleFileTreeNode) {
				final File file = ((SimpleFileTreeNode) value).getFile();
				setText(file.getName());
				setToolTipText(file.getAbsolutePath());
				if (file.isDirectory()) {
					setIcon(FileSystemView.getFileSystemView().getSystemIcon(file));
				}
				else {
					setLeafIcon(FileSystemView.getFileSystemView().getSystemIcon(file));
				}
			}
			return this;
		}
	}

	private void highlightCurrentMap() {
		if (treeModel == null) {
			return;
		}
		final SimpleFileTreeNode root = (SimpleFileTreeNode) treeModel.getRoot();
		if (root == null) {
			return;
		}
		if (currentMapFile == null) {
			tree.expandPath(new TreePath(root.getPath()));
			return;
		}
		final TreePath mapPath = findAndLoadPath(root, currentMapFile);
		if (mapPath != null) {
			TreePath parent = mapPath.getParentPath();
			while (parent != null) {
				tree.expandPath(parent);
				parent = parent.getParentPath();
			}
			tree.setSelectionPath(mapPath);
			tree.scrollPathToVisible(mapPath);
		}
		else {
			tree.expandPath(new TreePath(root.getPath()));
		}
	}

	private TreePath findAndLoadPath(final SimpleFileTreeNode node, final File target) {
		if (node.getFile().equals(target)) {
			return new TreePath(node.getPath());
		}
		if (!target.getAbsolutePath().startsWith(node.getFile().getAbsolutePath())) {
			return null;
		}
		if (node.getFile().isDirectory()) {
			node.loadChildren(treeModel);
			for (int i = 0; i < node.getChildCount(); i++) {
				final SimpleFileTreeNode child = (SimpleFileTreeNode) node.getChildAt(i);
				final TreePath found = findAndLoadPath(child, target);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/**
	 * JTree that exposes workspace shadow nodes to context-menu actions.
	 */
	static final class MapFolderTree extends javax.swing.JTree {
		private static final long serialVersionUID = 1L;
		private AWorkspaceTreeNode contextShadow;

		void setContextShadow(final AWorkspaceTreeNode shadow) {
			this.contextShadow = shadow;
		}

		public TreePath getSelectionPath() {
			if (contextShadow != null) {
				return contextShadow.getTreePath();
			}
			return super.getSelectionPath();
		}

		public TreePath[] getSelectionPaths() {
			if (contextShadow != null) {
				return new TreePath[] { contextShadow.getTreePath() };
			}
			return super.getSelectionPaths();
		}
	}

	private static final class ShadowFolderNode extends FolderFileNode {
		private static final long serialVersionUID = 1L;
		private final SimpleFileTreeNode treeNode;
		private final CurrentMapFolderTabPanel panel;

		ShadowFolderNode(final File file, final SimpleFileTreeNode treeNode, final CurrentMapFolderTabPanel panel) {
			super(file.getName(), file);
			includeAllEntries(true);
			this.treeNode = treeNode;
			this.panel = panel;
		}

		public void refresh() {
			panel.refreshTreeNode(treeNode);
		}

		public boolean changeName(final String newName, final boolean renameLink) {
			if (rename(newName)) {
				panel.refreshParentOf(treeNode);
				return true;
			}
			return false;
		}
	}

	private static final class ShadowFileNode extends DefaultFileNode {
		private static final long serialVersionUID = 1L;
		private final SimpleFileTreeNode treeNode;
		private final CurrentMapFolderTabPanel panel;

		ShadowFileNode(final File file, final SimpleFileTreeNode treeNode, final CurrentMapFolderTabPanel panel) {
			super(file.getName(), file);
			this.treeNode = treeNode;
			this.panel = panel;
		}

		public void refresh() {
			panel.refreshParentOf(treeNode);
		}

		public boolean rename(final String name) {
			if (super.rename(name)) {
				panel.refreshParentOf(treeNode);
				return true;
			}
			return false;
		}

		public void delete() {
			super.delete();
			panel.refreshParentOf(treeNode);
		}

		public boolean changeName(final String newName, final boolean renameLink) {
			if (rename(newName)) {
				panel.refreshParentOf(treeNode);
				return true;
			}
			return false;
		}
	}
}
