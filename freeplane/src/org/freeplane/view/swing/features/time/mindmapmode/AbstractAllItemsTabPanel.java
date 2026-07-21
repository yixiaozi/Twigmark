package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.MindMapFileIdentity;
import org.freeplane.features.icon.IconNotFound;
import org.freeplane.features.icon.IconStore;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.factory.IconStoreFactory;
import org.freeplane.core.util.WorkspaceSideTabScanCache;
import org.freeplane.core.util.WorkspaceSideTabSnapshot;
import org.freeplane.core.util.WorkspaceSideTabSnapshotRegistry;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.MapChangeEvent;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public abstract class AbstractAllItemsTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	protected static final class ItemRecord {
		private final File file;
		private final String nodeId;
		private final String nodeText;
		private final String mapName;
		private final String todoParentId;
		private final List extraIconNames;

		private ItemRecord(File file, String nodeId, String nodeText, String mapName, String todoParentId,
				List extraIconNames) {
			this.file = file;
			this.nodeId = nodeId;
			this.nodeText = nodeText;
			this.mapName = mapName;
			this.todoParentId = todoParentId;
			this.extraIconNames = extraIconNames == null ? Collections.EMPTY_LIST : extraIconNames;
		}
	}

	protected static final class NodeScanInfo {
		private final String id;
		private final String parentId;
		private final String text;
		private final List iconNames = new ArrayList();

		private NodeScanInfo(String id, String parentId, String text) {
			this.id = id;
			this.parentId = parentId;
			this.text = text == null ? "" : text;
		}

	}

	protected static final class CachedFileResult {
		private final long modified;
		private final long length;
		private final List items;

		private CachedFileResult(long modified, long length, List items) {
			this.modified = modified;
			this.length = length;
			this.items = items;
		}
	}

	protected static final class GroupLabel {
		private final String text;

		private GroupLabel(String text) {
			this.text = text;
		}
	}

	protected static final class ScanChunk {
		private final String fileKey;
		private final List items;
		private final int scanned;
		private final int total;

		private ScanChunk(String fileKey, List items, int scanned, int total) {
			this.fileKey = fileKey;
			this.items = items;
			this.scanned = scanned;
			this.total = total;
		}
	}

	/** Raw substrings to look for in .mm XML before running SAX (must be cheap). */
	protected String[] getIconTextProbes() {
		final String name = getIconName();
		if (name == null || name.length() == 0) {
			return new String[0];
		}
		return new String[] {
				"BUILTIN=\"" + name + "\"",
				"BUILTIN='" + name + "'"
		};
	}

	protected abstract String getIconName();
	protected abstract String getRootLabel();
	protected abstract String getStatusLabelPrefix();

	/** Override to accept multiple icon names (e.g. all {@code flag*}). */
	protected boolean isTargetIcon(final String iconName) {
		final String want = getIconName();
		return want != null && want.equalsIgnoreCase(iconName);
	}

	private boolean isTargetIconName(final String iconName) {
		return isTargetIcon(iconName);
	}

	protected final JButton refreshButton = new JButton("\u5237\u65b0");
	protected final JLabel statusLabel = new JLabel();
	/** Empty root — never use {@code new JTree()} default (colors/sports/food demo). */
	protected final JTree tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode("")));
	protected final DecimalFormat twoDigits = new DecimalFormat("00");
	protected final Map cacheByFile = new HashMap();
	protected final Map itemsByKey = new HashMap();
	protected final Map itemKeysByFile = new HashMap();
	protected SwingWorker activeWorker;
	protected boolean rescanRequested;
	/** Queued force-disk flag when a rescan is requested while another is still running. */
	private boolean pendingForceDiskRescan;
	protected Set lastActiveFileKeys = new HashSet();
	protected final Timer autoRefreshTimer;
	private final Timer liveRefreshTimer;
	private boolean mapListenersInstalled;

	public AbstractAllItemsTabPanel() {
		super(new BorderLayout(4, 4));
		((DefaultMutableTreeNode) tree.getModel().getRoot()).setUserObject(getRootLabel());
		statusLabel.setText(getStatusLabelPrefix() + ": 0");
		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.add(statusLabel, BorderLayout.CENTER);
		top.add(refreshButton, BorderLayout.EAST);
		add(top, BorderLayout.NORTH);

		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		installArrowKeyNavigation();

		tree.setCellRenderer(new DefaultTreeCellRenderer() {
			private static final long serialVersionUID = 1L;
			private final Color ITEM_COLOR = org.freeplane.core.ui.theme.DocearUiTheme.ACCENT_DEEP;
			private final IconStore iconStore = IconStoreFactory.create();

			public Component getTreeCellRendererComponent(JTree pTree, Object value, boolean sel, boolean expanded,
					boolean leaf, int row, boolean hasFocus) {
				super.getTreeCellRendererComponent(pTree, value, sel, expanded, leaf, row, hasFocus);
				setOpenIcon(null);
				setClosedIcon(null);
				setLeafIcon(null);
				Object user = ((DefaultMutableTreeNode) value).getUserObject();
				if (user instanceof GroupLabel) {
					setText(((GroupLabel) user).text);
					setIcon(null);
					if (!sel) {
						setForeground(null);
					}
				} else if (user instanceof ItemRecord) {
					ItemRecord record = (ItemRecord) user;
					setText(normalizeNodeText(record.nodeText));
					setIcon(buildCombinedIcon(record.extraIconNames));
					if (!sel) {
						setForeground(ITEM_COLOR);
					}
				}
				return this;
			}

			private Icon buildCombinedIcon(List iconNames) {
				if (iconNames == null || iconNames.isEmpty()) {
					return null;
				}
				final List icons = new ArrayList();
				for (int i = 0; i < iconNames.size(); i++) {
					MindIcon mindIcon = iconStore.getMindIcon((String) iconNames.get(i));
					if (mindIcon != null && !(mindIcon instanceof IconNotFound) && mindIcon.getIcon() != null) {
						icons.add(mindIcon.getIcon());
					}
				}
				if (icons.isEmpty()) {
					return null;
				}
				if (icons.size() == 1) {
					return (Icon) icons.get(0);
				}
				return new CombinedIcon((Icon[]) icons.toArray(new Icon[icons.size()]));
			}
		});

		tree.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
					showPopupMenu(e);
				}
			}
			
			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
					showPopupMenu(e);
				}
			}
			
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() >= 1 && e.getButton() == MouseEvent.BUTTON1) {
					openSelectedItem();
				}
			}
		});

		JScrollPane scrollPane = new JScrollPane(tree);
		add(scrollPane, BorderLayout.CENTER);

		refreshButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				triggerRescan(true);
			}
		});

		autoRefreshTimer = new Timer(5 * 60 * 1000, new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				triggerRescan();
			}
		});
		autoRefreshTimer.start();

		liveRefreshTimer = new Timer(500, new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				refreshFromOpenMapsQuick();
			}
		});
		liveRefreshTimer.setRepeats(false);

		installOpenMapListeners();
		addHierarchyListener(new HierarchyListener() {
			public void hierarchyChanged(final HierarchyEvent e) {
				if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
					refreshFromOpenMapsQuick();
				}
			}
		});
		triggerRescan(true);
	}

	private void installOpenMapListeners() {
		if (mapListenersInstalled) {
			return;
		}
		try {
			final ModeController modeController = Controller.getCurrentModeController();
			if (modeController == null) {
				return;
			}
			final org.freeplane.features.map.MapController mapController = modeController.getMapController();
			mapController.addNodeChangeListener(new INodeChangeListener() {
				public void nodeChanged(final NodeChangeEvent event) {
					if (event == null) {
						return;
					}
					final Object prop = event.getProperty();
					if (NodeModel.NODE_ICON.equals(prop) || NodeModel.NODE_TEXT.equals(prop)
							|| "hierarchical_icons".equals(prop)) {
						scheduleLiveRefresh();
					}
				}
			});
			mapController.addMapChangeListener(new IMapChangeListener() {
				public void mapChanged(final MapChangeEvent event) {
					scheduleLiveRefresh();
				}

				public void onNodeInserted(final NodeModel parent, final NodeModel child, final int newIndex) {
					scheduleLiveRefresh();
				}

				public void onNodeDeleted(final NodeModel parent, final NodeModel child, final int index) {
					scheduleLiveRefresh();
				}

				public void onNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
						final NodeModel child, final int newIndex) {
					scheduleLiveRefresh();
				}

				public void onPreNodeDelete(final NodeModel oldParent, final NodeModel selectedNode, final int index) {
				}

				public void onPreNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
						final NodeModel child, final int newIndex) {
				}
			});
			mapController.addMapLifeCycleListener(new IMapLifeCycleListener() {
				public void onCreate(final MapModel map) {
					scheduleLiveRefresh();
				}

				public void onRemove(final MapModel map) {
					invalidateCacheForMap(map);
					scheduleLiveRefresh();
				}

				public void onSavedAs(final MapModel map) {
					invalidateCacheForMap(map);
					scheduleLiveRefresh();
				}

				public void onSaved(final MapModel map) {
					invalidateCacheForMap(map);
					scheduleLiveRefresh();
				}
			});
			mapListenersInstalled = true;
		}
		catch (Exception e) {
			LogUtils.warn("All-items tab: could not listen for open-map icon changes.", e);
		}
	}

	private void scheduleLiveRefresh() {
		// Open-map-only refresh; do not kick a full-disk scan on every keystroke.
		liveRefreshTimer.restart();
	}

	/** Drop disk-parse cache for one map so the next scan re-reads it; keep the visible list. */
	private void invalidateCacheForMap(final MapModel map) {
		if (map == null || map.getFile() == null) {
			return;
		}
		cacheByFile.remove(fileKey(map.getFile()));
	}

	protected void triggerRescan() {
		triggerRescan(false);
	}

	/**
	 * Fast path: refresh from currently open maps on the EDT so the list updates
	 * immediately. Optional full-disk scan runs in the background afterward.
	 *
	 * @param forceDiskRescan when true (Refresh button / first load), also re-parse
	 *        closed maps from disk in the background.
	 */
	protected void triggerRescan(final boolean forceDiskRescan) {
		if (forceDiskRescan) {
			pendingForceDiskRescan = true;
		}
		if (SwingUtilities.isEventDispatchThread()) {
			refreshFromOpenMapsQuick();
			maybeStartDiskScan();
		}
		else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					refreshFromOpenMapsQuick();
					maybeStartDiskScan();
				}
			});
		}
	}

	/**
	 * Instant UI update from open MapModels only (no disk walk).
	 * This is what makes 红旗/全部待办 usable while the workspace has 10k+ .mm files.
	 */
	private void refreshFromOpenMapsQuick() {
		try {
			final Map openByKey = collectOpenMapModels();
			for (final Object entryObj : openByKey.entrySet()) {
				final Map.Entry entry = (Map.Entry) entryObj;
				final String key = (String) entry.getKey();
				final MapModel map = (MapModel) entry.getValue();
				final List items = getItemsFromOpenMap(map);
				mergeChunk(new ScanChunk(key, items, 0, 0));
			}
			rebuildTreeFromCache();
			statusLabel.setText(getStatusLabelPrefix() + ": " + itemsByKey.size());
			publishItemCountMetric(itemsByKey.size());
		}
		catch (Exception e) {
			LogUtils.warn("All-items tab: open-map refresh failed", e);
		}
	}

	private void maybeStartDiskScan() {
		final boolean forceDisk = pendingForceDiskRescan;
		if (!forceDisk && diskScanCompletedOnce) {
			return;
		}
		pendingForceDiskRescan = false;
		startDiskScanWorker(forceDisk);
	}

	private boolean diskScanCompletedOnce;

	private void startDiskScanWorker(final boolean forceDisk) {
		if (activeWorker != null) {
			rescanRequested = true;
			if (forceDisk) {
				pendingForceDiskRescan = true;
			}
			return;
		}
		rescanRequested = false;
		if (forceDisk) {
			cacheByFile.clear();
		}
		activeWorker = new SwingWorker() {
			private volatile boolean completedPass;
			private long lastUiPublishMs;

			protected Object doInBackground() throws Exception {
				final List files = collectAllMindmapFiles();
				final Set activeFileKeys = new HashSet();
				for (int i = 0; i < files.size(); i++) {
					activeFileKeys.add(fileKey((File) files.get(i)));
				}
				final Map openByKey = collectOpenMapModels();
				for (final Object keyObj : openByKey.keySet()) {
					activeFileKeys.add((String) keyObj);
				}
				lastActiveFileKeys = activeFileKeys;
				final Set scannedOpenKeys = new HashSet();
				for (final Object entryObj : openByKey.entrySet()) {
					final Map.Entry entry = (Map.Entry) entryObj;
					final String key = (String) entry.getKey();
					final MapModel map = (MapModel) entry.getValue();
					final List items = getItemsFromOpenMap(map);
					scannedOpenKeys.add(key);
					publish(new ScanChunk(key, items, 0, 0));
				}
				for (int i = 0; i < files.size(); i++) {
					final File file = (File) files.get(i);
					if (!isValidMindmapFile(file)) {
						continue;
					}
					final String key = fileKey(file);
					if (scannedOpenKeys.contains(key)) {
						continue;
					}
					final CachedFileResult prev = (CachedFileResult) cacheByFile.get(key);
					final boolean hadItems = prev != null && prev.items != null && !prev.items.isEmpty();
					final List items = getItemsForFile(file);
					// Skip empty files with no prior hits — most .mm have neither flag nor todo.
					if (items.isEmpty() && !hadItems) {
						continue;
					}
					publish(new ScanChunk(key, items, 0, 0));
				}
				completedPass = true;
				return null;
			}

			protected void process(final List chunks) {
				for (int i = 0; i < chunks.size(); i++) {
					mergeChunk((ScanChunk) chunks.get(i));
				}
				final long now = System.currentTimeMillis();
				if (now - lastUiPublishMs >= 500L) {
					lastUiPublishMs = now;
					rebuildTreeFromCache();
					statusLabel.setText(getStatusLabelPrefix() + ": " + itemsByKey.size());
					publishItemCountMetric(itemsByKey.size());
				}
			}

			protected void done() {
				activeWorker = null;
				try {
					if (completedPass) {
						purgeStaleItems(lastActiveFileKeys);
						diskScanCompletedOnce = true;
					}
					rebuildTreeFromCache();
					statusLabel.setText(getStatusLabelPrefix() + ": " + itemsByKey.size());
					publishItemCountMetric(itemsByKey.size());
				}
				catch (Exception e) {
					LogUtils.warn("All-items tab: rebuild after disk scan failed", e);
					statusLabel.setText(getStatusLabelPrefix() + ": " + itemsByKey.size());
				}
				if (rescanRequested || pendingForceDiskRescan) {
					final boolean force = pendingForceDiskRescan;
					pendingForceDiskRescan = false;
					rescanRequested = false;
					if (force || !diskScanCompletedOnce) {
						startDiskScanWorker(force);
					}
				}
			}
		};
		activeWorker.execute();
	}

	private void showPopupMenu(MouseEvent e) {
		final TreePath path = tree.getPathForLocation(e.getX(), e.getY());
		if (path == null) {
			return;
		}
		Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		
		JPopupMenu menu = new JPopupMenu();

		if (userObject instanceof ItemRecord) {
			final ItemRecord record = (ItemRecord) userObject;

			JMenuItem copyItem = new JMenuItem("\u590D\u5236");
			copyItem.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e1) {
					String text = record.nodeText == null ? ""
							: HtmlUtils.removeHtmlTagsFromString(record.nodeText).replaceAll("\\s+", " ").trim();
					Toolkit.getDefaultToolkit().getSystemClipboard()
							.setContents(new StringSelection(text), null);
				}
			});
			menu.add(copyItem);
		} else if (userObject instanceof GroupLabel) {
			final GroupLabel label = (GroupLabel) userObject;
			final TreePath treePath = path;
			
			JMenuItem openFolderItem = new JMenuItem("\u6253\u5F00\u6587\u4EF6\u5939");
			openFolderItem.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e1) {
					try {
						String folderPath = findFolderPathForLabel(label.text, treePath);
						if (folderPath != null) {
							Runtime.getRuntime().exec("explorer.exe \"" + folderPath + "\"");
						}
					} catch (Exception ex) {
						LogUtils.warn(ex);
					}
				}
			});
			menu.add(openFolderItem);
		}

		if (menu.getComponentCount() > 0) {
			menu.show(tree, e.getX(), e.getY());
		}
	}
	
	private String findFolderPathForLabel(String label, TreePath path) {
		if (label.toLowerCase().endsWith(".mm")) {
			for (Object key : itemsByKey.keySet()) {
				ItemRecord record = (ItemRecord) itemsByKey.get(key);
				if (record.mapName.equals(label)) {
					File parent = record.file.getParentFile();
					if (parent != null && parent.exists()) {
						return parent.getAbsolutePath();
					}
				}
			}
			return null;
		}
		
		Object[] pathComponents = path.getPath();
		final File scanRoot = MindMapDataRootResolver.getPrimaryScanRoot();
		if (scanRoot == null) {
			return null;
		}
		StringBuilder folderPath = new StringBuilder(scanRoot.getAbsolutePath());
		
		for (int i = 1; i < pathComponents.length; i++) {
			Object component = pathComponents[i];
			if (component instanceof DefaultMutableTreeNode) {
				Object userObj = ((DefaultMutableTreeNode) component).getUserObject();
				if (userObj instanceof GroupLabel) {
					String part = ((GroupLabel) userObj).text;
					if (part.toLowerCase().endsWith(".mm")) {
						continue;
					}
					folderPath.append(File.separator).append(part);
					if (part.equals(label)) {
						File folder = new File(folderPath.toString());
						if (folder.exists() && folder.isDirectory()) {
							return folder.getAbsolutePath();
						}
					}
				}
			}
		}
		
		return null;
	}

	private void openSelectedItem() {
		TreePath path = tree.getSelectionPath();
		if (path == null) {
			return;
		}
		Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (!(userObject instanceof ItemRecord)) {
			return;
		}

		ItemRecord record = (ItemRecord) userObject;
		try {
			IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			URL url = record.file.toURI().toURL();
			if (!mapViewManager.tryToChangeToMapView(url)) {
				Controller.getCurrentModeController().getMapController().newMap(url);
			}
			selectItemNodeWithRetry(record, 0);
		} catch (Exception e) {
			LogUtils.warn(e);
		}
	}

	private void selectItemNodeWithRetry(final ItemRecord record, final int attempt) {
		final int maxAttempts = 12;
		if (record == null || attempt > maxAttempts) {
			return;
		}
		try {
			IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			java.util.Map maps = mapViewManager.getMaps(MModeController.MODENAME);
			for (Object mapObj : maps.values()) {
				MapModel map = (MapModel) mapObj;
				File mapFile = map.getFile();
				if (mapFile != null && mapFile.equals(record.file)) {
					NodeModel node = map.getNodeForID(record.nodeId);
					if (node != null) {
						Controller.getCurrentController().getSelection().selectAsTheOnlyOneSelected(node);
						Controller.getCurrentModeController().getMapController().centerNode(node);
						tree.requestFocusInWindow();
						return;
					}
				}
			}
		} catch (Exception e) {
			LogUtils.warn(e);
		}
		Timer retry = new Timer(250, new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				selectItemNodeWithRetry(record, attempt + 1);
			}
		});
		retry.setRepeats(false);
		retry.start();
	}

	private void installArrowKeyNavigation() {
		tree.getInputMap().put(KeyStroke.getKeyStroke("RIGHT"), "item.right");
		tree.getInputMap().put(KeyStroke.getKeyStroke("LEFT"), "item.left");

		tree.getActionMap().put("item.left", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(java.awt.event.ActionEvent e) {
				TreePath path = tree.getSelectionPath();
				if (path == null) {
					return;
				}
				if (tree.isExpanded(path)) {
					tree.collapsePath(path);
				} else if (path.getParentPath() != null) {
					tree.setSelectionPath(path.getParentPath());
				}
			}
		});

		tree.getActionMap().put("item.right", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(java.awt.event.ActionEvent e) {
				TreePath path = tree.getSelectionPath();
				if (path == null) {
					return;
				}
				if (!tree.isExpanded(path)) {
					tree.expandPath(path);
				}
			}
		});
	}

	private void mergeChunk(ScanChunk chunk) {
		if (chunk.fileKey != null && lastActiveFileKeys != null && !lastActiveFileKeys.isEmpty()
				&& !lastActiveFileKeys.contains(chunk.fileKey)) {
			return;
		}
		List oldKeys = (List) itemKeysByFile.get(chunk.fileKey);
		if (oldKeys != null) {
			for (int i = 0; i < oldKeys.size(); i++) {
				itemsByKey.remove(oldKeys.get(i));
			}
		}

		List newKeys = new ArrayList();
		for (int i = 0; i < chunk.items.size(); i++) {
			ItemRecord record = (ItemRecord) chunk.items.get(i);
			if (!isValidMindmapFile(record.file)) {
				continue;
			}
			String key = itemKey(record);
			itemsByKey.put(key, record);
			newKeys.add(key);
		}
		itemKeysByFile.put(chunk.fileKey, newKeys);
	}

	private boolean isValidMindmapFile(File file) {
		if (file == null || !file.exists() || !file.isFile()) {
			return false;
		}
		String name = file.getName();
		if (name.startsWith("~") || name.contains("\u51b2\u7a81\u526f\u672c")) {
			return false;
		}
		return name.toLowerCase().endsWith(".mm");
	}

	private void purgeStaleItems(Set activeFileKeys) {
		if (activeFileKeys == null) {
			activeFileKeys = Collections.emptySet();
		}
		List staleFileKeys = new ArrayList();
		for (Object fileKeyObj : itemKeysByFile.keySet()) {
			String fileKey = (String) fileKeyObj;
			// storageKey is an identity token (id:/f:/…), not a filesystem path — never
			// reconstruct File from it or every scan result is wiped after merge.
			if (!activeFileKeys.contains(fileKey) || !hasValidBackingMindmap(fileKey)) {
				staleFileKeys.add(fileKey);
			}
		}
		for (int i = 0; i < staleFileKeys.size(); i++) {
			String fileKey = (String) staleFileKeys.get(i);
			List oldKeys = (List) itemKeysByFile.remove(fileKey);
			if (oldKeys != null) {
				for (int j = 0; j < oldKeys.size(); j++) {
					itemsByKey.remove(oldKeys.get(j));
				}
			}
			cacheByFile.remove(fileKey);
		}
		for (Object cacheKeyObj : new ArrayList(cacheByFile.keySet())) {
			String cacheKey = (String) cacheKeyObj;
			if (!activeFileKeys.contains(cacheKey)) {
				cacheByFile.remove(cacheKey);
			}
		}
	}

	/** True when at least one cached item for this identity key still points at a real .mm file. */
	private boolean hasValidBackingMindmap(final String fileKey) {
		final List keys = (List) itemKeysByFile.get(fileKey);
		if (keys == null || keys.isEmpty()) {
			return false;
		}
		for (int i = 0; i < keys.size(); i++) {
			final ItemRecord record = (ItemRecord) itemsByKey.get(keys.get(i));
			if (record != null && isValidMindmapFile(record.file)) {
				return true;
			}
		}
		return false;
	}

	private List collectAllMindmapFiles() {
		try {
			WorkspaceSideTabScanCache.schedulePreload();
			final List cached = WorkspaceSideTabScanCache.getMindMapFilesSnapshot();
			if (cached != null && !cached.isEmpty()) {
				return new ArrayList(cached);
			}
		}
		catch (Exception e) {
		}
		final List files = new ArrayList();
		MindMapDataRootResolver.collectMindmapFiles(files);
		return files;
	}

	private List getItemsForFile(final File file) {
		if (!isValidMindmapFile(file)) {
			return Collections.emptyList();
		}
		long modified = file.lastModified();
		long length = file.length();
		CachedFileResult cached = (CachedFileResult) cacheByFile.get(fileKey(file));
		if (cached != null && cached.modified == modified && cached.length == length) {
			return cached.items;
		}

		// Cheap text probe: skip SAX when the file cannot contain our icons.
		if (!fileMayContainTargetIcon(file)) {
			final List empty = Collections.emptyList();
			cacheByFile.put(fileKey(file), new CachedFileResult(modified, length, empty));
			return empty;
		}

		final List items = new ArrayList();
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(false);
			SAXParser saxParser = factory.newSAXParser();
			saxParser.parse(file, new DefaultHandler() {
				private final List nodeStack = new ArrayList();

				public void startElement(String uri, String localName, String qName, Attributes attributes) {
					if ("node".equals(qName)) {
						String id = attributes.getValue("ID");
						String text = attributes.getValue("TEXT");
						String parentId = null;
						if (!nodeStack.isEmpty()) {
							parentId = ((NodeScanInfo) nodeStack.get(nodeStack.size() - 1)).id;
						}
						nodeStack.add(new NodeScanInfo(id, parentId, text));
					} else if ("icon".equals(qName) && !nodeStack.isEmpty()) {
						String iconName = attributes.getValue("BUILTIN");
						if (iconName != null) {
							((NodeScanInfo) nodeStack.get(nodeStack.size() - 1)).iconNames.add(iconName);
						}
					}
				}

				public void endElement(String uri, String localName, String qName) {
					if ("node".equals(qName) && !nodeStack.isEmpty()) {
						NodeScanInfo info = (NodeScanInfo) nodeStack.remove(nodeStack.size() - 1);
						if (nodeHasTargetIcon(info.iconNames)) {
							String nodeText = info.text == null ? "" : info.text.trim();
							if (!"bin".equalsIgnoreCase(nodeText)) {
								items.add(new ItemRecord(file, info.id, nodeText, file.getName(),
										findNearestTodoParentOnStack(nodeStack), extraIconNames(info.iconNames)));
							}
						}
					}
				}
			});
		} catch (Exception e) {
			LogUtils.warn(e);
		}

		cacheByFile.put(fileKey(file), new CachedFileResult(modified, length, items));
		return items;
	}

	/**
	 * Stream-search .mm for icon marker text. Most files have no flags/todos — skip SAX for them.
	 */
	private boolean fileMayContainTargetIcon(final File file) {
		final String[] probes = getIconTextProbes();
		if (probes == null || probes.length == 0) {
			return true;
		}
		final byte[][] needles = new byte[probes.length][];
		int maxNeedle = 0;
		try {
			for (int i = 0; i < probes.length; i++) {
				needles[i] = probes[i].getBytes("UTF-8");
				if (needles[i].length > maxNeedle) {
					maxNeedle = needles[i].length;
				}
			}
		}
		catch (Exception e) {
			return true;
		}
		if (maxNeedle == 0) {
			return true;
		}
		java.io.InputStream in = null;
		try {
			in = new java.io.BufferedInputStream(new java.io.FileInputStream(file), 64 * 1024);
			final byte[] buf = new byte[64 * 1024];
			final byte[] window = new byte[Math.max(maxNeedle * 2, 256)];
			int windowLen = 0;
			int read;
			while ((read = in.read(buf)) >= 0) {
				if (read == 0) {
					continue;
				}
				// Search in current chunk
				if (indexOfAny(buf, read, needles) >= 0) {
					return true;
				}
				// Also check overlap across chunk boundary
				final int keep = Math.min(windowLen, maxNeedle - 1);
				if (keep > 0 || read > 0) {
					final int overlapLen = Math.min(maxNeedle - 1, read);
					final byte[] overlap = new byte[keep + overlapLen];
					if (keep > 0) {
						System.arraycopy(window, windowLen - keep, overlap, 0, keep);
					}
					System.arraycopy(buf, 0, overlap, keep, overlapLen);
					if (indexOfAny(overlap, overlap.length, needles) >= 0) {
						return true;
					}
				}
				windowLen = Math.min(read, window.length);
				System.arraycopy(buf, read - windowLen, window, 0, windowLen);
			}
			return false;
		}
		catch (Exception e) {
			return true;
		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (Exception e) {
				}
			}
		}
	}

	private static int indexOfAny(final byte[] hay, final int hayLen, final byte[][] needles) {
		for (int n = 0; n < needles.length; n++) {
			final byte[] needle = needles[n];
			if (needle.length == 0 || needle.length > hayLen) {
				continue;
			}
			outer: for (int i = 0; i <= hayLen - needle.length; i++) {
				for (int j = 0; j < needle.length; j++) {
					if (hay[i + j] != needle[j]) {
						continue outer;
					}
				}
				return i;
			}
		}
		return -1;
	}

	private Map collectOpenMapModels() {
		final Map openByKey = new HashMap();
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null || controller.getMapViewManager() == null) {
				return openByKey;
			}
			final Map maps = controller.getMapViewManager().getMaps();
			if (maps == null) {
				return openByKey;
			}
			for (Object mapObj : maps.values()) {
				final MapModel map = (MapModel) mapObj;
				if (map == null || map.getFile() == null || !isValidMindmapFile(map.getFile())) {
					continue;
				}
				openByKey.put(fileKey(map.getFile()), map);
			}
		}
		catch (Exception e) {
			LogUtils.warn("All-items tab: open map scan failed.", e);
		}
		return openByKey;
	}

	private List getItemsFromOpenMap(final MapModel map) {
		final List items = new ArrayList();
		if (map == null || map.getRootNode() == null || map.getFile() == null) {
			return items;
		}
		collectOpenMapItems(map.getRootNode(), map.getFile(), null, items);
		return items;
	}

	private void collectOpenMapItems(final NodeModel node, final File file, final String nearestTargetParentId,
	        final List items) {
		if (node == null) {
			return;
		}
		final List iconNames = collectNodeIconNames(node);
		String parentForChildren = nearestTargetParentId;
		if (nodeHasTargetIcon(iconNames)) {
			final String nodeText = normalizeNodeText(node.getText());
			if (!"bin".equalsIgnoreCase(nodeText)) {
				final String nodeId = node.getID() != null ? node.getID() : node.createID();
				items.add(new ItemRecord(file, nodeId, nodeText, file.getName(), nearestTargetParentId,
				        extraIconNames(iconNames)));
				parentForChildren = nodeId;
			}
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			collectOpenMapItems((NodeModel) node.getChildAt(i), file, parentForChildren, items);
		}
	}

	private List collectNodeIconNames(final NodeModel node) {
		final List names = new ArrayList();
		final List icons = node.getIcons();
		if (icons == null) {
			return names;
		}
		for (int i = 0; i < icons.size(); i++) {
			final Object icon = icons.get(i);
			if (icon instanceof MindIcon) {
				names.add(((MindIcon) icon).getName());
			}
		}
		return names;
	}

	private boolean nodeHasTargetIcon(final List iconNames) {
		if (iconNames == null) {
			return false;
		}
		for (int i = 0; i < iconNames.size(); i++) {
			if (isTargetIcon((String) iconNames.get(i))) {
				return true;
			}
		}
		return false;
	}

	private List extraIconNames(final List iconNames) {
		final List result = new ArrayList();
		if (iconNames == null) {
			return result;
		}
		for (int i = 0; i < iconNames.size(); i++) {
			final String name = (String) iconNames.get(i);
			if (!isTargetIcon(name)) {
				result.add(name);
			}
		}
		return result;
	}

	private void rebuildTreeFromCache() {
		String selectedKey = getSelectedItemKey();
		List records = new ArrayList(itemsByKey.values());

		Collections.sort(records, new Comparator() {
			public int compare(Object o1, Object o2) {
				ItemRecord a = (ItemRecord) o1;
				ItemRecord b = (ItemRecord) o2;
				String pathA = a.file.getParent() != null ? a.file.getParent() : "";
				String pathB = b.file.getParent() != null ? b.file.getParent() : "";
				int pathCompare = pathA.compareTo(pathB);
				if (pathCompare != 0) {
					return pathCompare;
				}
				int fileCompare = a.mapName.compareTo(b.mapName);
				if (fileCompare != 0) {
					return fileCompare;
				}
				return a.nodeText.compareTo(b.nodeText);
			}
		});

		DefaultMutableTreeNode root = new DefaultMutableTreeNode(getRootLabel());
		Map pathNodes = new HashMap();
		Map fileNodesByPath = new HashMap();
		Map recordsByFile = new HashMap();
		Map itemNodesByKey = new HashMap();

		for (int i = 0; i < records.size(); i++) {
			ItemRecord record = (ItemRecord) records.get(i);
			if (!isValidMindmapFile(record.file)) {
				continue;
			}

			String filePath = fileKey(record.file);
			List fileRecords = (List) recordsByFile.get(filePath);
			if (fileRecords == null) {
				fileRecords = new ArrayList();
				recordsByFile.put(filePath, fileRecords);
			}
			fileRecords.add(record);

			if (fileNodesByPath.containsKey(filePath)) {
				continue;
			}

			String parentPath = record.file.getParent();
			DefaultMutableTreeNode parentNode = root;

			if (parentPath != null) {
				final String relativePath = MindMapDataRootResolver.getRelativePathWithinScanRoots(record.file
						.getParentFile());
				if (relativePath != null && relativePath.length() > 0) {
					final String[] pathParts = relativePath.split("/");
					String currentPath = "";
					for (int p = 0; p < pathParts.length; p++) {
						final String part = pathParts[p];
						if (part == null || part.trim().length() == 0) {
							continue;
						}
						currentPath += "/" + part;
						DefaultMutableTreeNode pathNode = (DefaultMutableTreeNode) pathNodes.get(currentPath);
						if (pathNode == null) {
							pathNode = new DefaultMutableTreeNode(new GroupLabel(part), true);
							pathNodes.put(currentPath, pathNode);
							parentNode.add(pathNode);
						}
						parentNode = pathNode;
					}
				}
			}

			DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(new GroupLabel(record.mapName), true);
			pathNodes.put(filePath, fileNode);
			parentNode.add(fileNode);
			fileNodesByPath.put(filePath, fileNode);
		}

		for (Object filePathObj : recordsByFile.keySet()) {
			String filePath = (String) filePathObj;
			DefaultMutableTreeNode fileNode = (DefaultMutableTreeNode) fileNodesByPath.get(filePath);
			if (fileNode != null) {
				attachTodosByParentId(fileNode, (List) recordsByFile.get(filePath), itemNodesByKey);
			}
		}

		tree.setModel(new DefaultTreeModel(root));
		if (records.isEmpty()) {
			statusLabel.setText(getStatusLabelPrefix() + ": 0");
		}

		for (int i = 0; i < tree.getRowCount(); i++) {
			tree.expandRow(i);
		}

		if (selectedKey != null) {
			DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) itemNodesByKey.get(selectedKey);
			if (selectedNode != null) {
				TreePath selectedPath = new TreePath(selectedNode.getPath());
				tree.setSelectionPath(selectedPath);
				tree.scrollPathToVisible(selectedPath);
			}
		}
		onSideTabCacheRefreshed(records);
	}

	private void attachTodosByParentId(DefaultMutableTreeNode fileNode, List fileRecords, Map itemNodesByKey) {
		for (int i = 0; i < fileRecords.size(); i++) {
			ItemRecord record = (ItemRecord) fileRecords.get(i);
			String key = itemKey(record);
			itemNodesByKey.put(key, new DefaultMutableTreeNode(record, true));
		}

		for (int i = 0; i < fileRecords.size(); i++) {
			ItemRecord record = (ItemRecord) fileRecords.get(i);
			String key = itemKey(record);
			DefaultMutableTreeNode itemNode = (DefaultMutableTreeNode) itemNodesByKey.get(key);
			if (itemNode == null) {
				continue;
			}

			DefaultMutableTreeNode attachParent = fileNode;
			if (record.todoParentId != null && record.todoParentId.length() > 0) {
				String parentKey = fileKey(record.file) + "|" + record.todoParentId;
				DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) itemNodesByKey.get(parentKey);
				if (parentNode != null) {
					attachParent = parentNode;
				}
			}
			attachParent.add(itemNode);
		}
	}

	private String fileKey(File file) {
		return MindMapFileIdentity.storageKey(file);
	}

	private String findNearestTodoParentOnStack(List nodeStack) {
		for (int i = nodeStack.size() - 1; i >= 0; i--) {
			NodeScanInfo ancestor = (NodeScanInfo) nodeStack.get(i);
			if (!nodeHasTargetIcon(ancestor.iconNames)) {
				continue;
			}
			String label = ancestor.text == null ? "" : ancestor.text.trim();
			if (!"bin".equalsIgnoreCase(label)) {
				return ancestor.id;
			}
		}
		return null;
	}

	private String normalizeNodeText(String text) {
		if (text == null) {
			return "";
		}
		return HtmlUtils.removeHtmlTagsFromString(text).replaceAll("\\s+", " ").trim();
	}

	protected void onSideTabCacheRefreshed(List records) {
		if ("\u5168\u90e8\u5f85\u529e".equals(getRootLabel())) {
			List entries = new ArrayList();
			for (int i = 0; i < records.size(); i++) {
				ItemRecord record = (ItemRecord) records.get(i);
				if (!isValidMindmapFile(record.file)) {
					continue;
				}
				String text = record.nodeText == null ? "" : HtmlUtils.removeHtmlTagsFromString(record.nodeText)
						.replaceAll("\\s+", " ").trim();
				entries.add(new WorkspaceSideTabSnapshot.TodoEntry(record.file, record.nodeId, text));
			}
			WorkspaceSideTabSnapshotRegistry.updateTodos(entries);
			SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_ALL_TODOS, entries.size());
			return;
		}
		if ("\u5168\u90e8\u53d1\u5e03".equals(getRootLabel())) {
			List entries = new ArrayList();
			for (int i = 0; i < records.size(); i++) {
				ItemRecord record = (ItemRecord) records.get(i);
				if (!isValidMindmapFile(record.file)) {
					continue;
				}
				String text = record.nodeText == null ? "" : HtmlUtils.removeHtmlTagsFromString(record.nodeText)
						.replaceAll("\\s+", " ").trim();
				entries.add(new WorkspaceSideTabSnapshot.ItemEntry(record.file, record.nodeId, text));
			}
			WorkspaceSideTabSnapshotRegistry.updatePublishedEntries(entries);
			SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_PUBLISHED, entries.size());
			return;
		}
		if ("\u7ea2\u65d7".equals(getRootLabel())) {
			int count = 0;
			for (int i = 0; i < records.size(); i++) {
				ItemRecord record = (ItemRecord) records.get(i);
				if (isValidMindmapFile(record.file)) {
					count++;
				}
			}
			SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_NEXT_ACTIONS, count);
		}
	}

	private void publishItemCountMetric(final int count) {
		if ("\u5168\u90e8\u5f85\u529e".equals(getRootLabel())) {
			SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_ALL_TODOS, count);
		}
		else if ("\u5168\u90e8\u53d1\u5e03".equals(getRootLabel())) {
			SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_PUBLISHED, count);
		}
		else if ("\u7ea2\u65d7".equals(getRootLabel())) {
			SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_NEXT_ACTIONS, count);
		}
	}

	private String getSelectedItemKey() {
		TreePath path = tree.getSelectionPath();
		if (path == null) {
			return null;
		}
		Object nodeObj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (!(nodeObj instanceof ItemRecord)) {
			return null;
		}
		return itemKey((ItemRecord) nodeObj);
	}

	private String itemKey(ItemRecord record) {
		return fileKey(record.file) + "|" + record.nodeId;
	}

	private static final class CombinedIcon implements Icon {
		private final Icon[] icons;

		private CombinedIcon(Icon[] icons) {
			this.icons = icons;
		}

		public int getIconWidth() {
			int width = 0;
			for (int i = 0; i < icons.length; i++) {
				width += icons[i].getIconWidth();
				if (i > 0) {
					width += 1;
				}
			}
			return width;
		}

		public int getIconHeight() {
			int height = 0;
			for (int i = 0; i < icons.length; i++) {
				height = Math.max(height, icons[i].getIconHeight());
			}
			return height;
		}

		public void paintIcon(Component c, Graphics g, int x, int y) {
			int offsetX = x;
			for (int i = 0; i < icons.length; i++) {
				icons[i].paintIcon(c, g, offsetX, y);
				offsetX += icons[i].getIconWidth() + 1;
			}
		}
	}
}