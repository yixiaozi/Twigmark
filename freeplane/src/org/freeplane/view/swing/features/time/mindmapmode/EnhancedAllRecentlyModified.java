package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
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
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.core.util.WorkspaceSideTabSnapshot;
import org.freeplane.core.util.WorkspaceSideTabSnapshotRegistry;
import org.freeplane.features.map.IMapSelectionListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class EnhancedAllRecentlyModified extends JPanel {
	private static final long serialVersionUID = 1L;
	/** Look back far enough to fill the list; display is capped at {@link #MAX_ITEMS}. */
	private static final int MAX_DAYS = 365;
	/** Show at most the newest N modified nodes. */
	private static final int MAX_ITEMS = 1000;

	private static final class ModifiedNode {
		private final File file;
		private final String nodeId;
		private final String nodeText;
		private final long modifiedAt;
		private final boolean isGrouped;

		private ModifiedNode(File file, String nodeId, String nodeText, long modifiedAt, boolean isGrouped) {
			this.file = file;
			this.nodeId = nodeId;
			this.nodeText = nodeText;
			this.modifiedAt = modifiedAt;
			this.isGrouped = isGrouped;
		}
	}

	private static final class CachedFileResult {
		private final long modified;
		private final long length;
		private final List nodes;

		private CachedFileResult(long modified, long length, List nodes) {
			this.modified = modified;
			this.length = length;
			this.nodes = nodes;
		}
	}

	private static final class GroupLabel {
		private final String text;
		private GroupLabel(String text) {
			this.text = text;
		}
	}

	private static final class ScanChunk {
		private final String fileKey;
		private final List nodes;
		private final int scanned;
		private final int total;

		private ScanChunk(String fileKey, List nodes, int scanned, int total) {
			this.fileKey = fileKey;
			this.nodes = nodes;
			this.scanned = scanned;
			this.total = total;
		}
	}

	private final JButton refreshButton = new JButton("刷新");
	private final JLabel statusLabel = new JLabel("最近修改: 0");
	private final JTree tree = new JTree(new DefaultMutableTreeNode("最近修改"));
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
	private final DecimalFormat twoDigits = new DecimalFormat("00");
	private final Map cacheByFile = new HashMap();
	private final Map nodesByKey = new HashMap();
	private final Map nodeKeysByFile = new HashMap();
	private SwingWorker activeWorker;
	private boolean rescanRequested;
	private final Timer autoRefreshTimer;

	public EnhancedAllRecentlyModified() {
		super(new BorderLayout(4, 4));
		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.add(statusLabel, BorderLayout.CENTER);
		top.add(refreshButton, BorderLayout.EAST);
		add(top, BorderLayout.NORTH);

		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		installArrowKeyNavigation();
		tree.setCellRenderer(new DefaultTreeCellRenderer() {
			private static final long serialVersionUID = 1L;
			public Component getTreeCellRendererComponent(JTree pTree, Object value, boolean sel, boolean expanded,
					boolean leaf, int row, boolean hasFocus) {
				super.getTreeCellRendererComponent(pTree, value, sel, expanded, leaf, row, hasFocus);
				setOpenIcon(null);
				setClosedIcon(null);
				setLeafIcon(null);
				Object user = ((DefaultMutableTreeNode) value).getUserObject();
				if (user instanceof GroupLabel) {
					setText(((GroupLabel) user).text);
				}
				else if (user instanceof ModifiedNode) {
					ModifiedNode record = (ModifiedNode) user;
					String text = record.nodeText == null ? "" : HtmlUtils.removeHtmlTagsFromString(record.nodeText).replaceAll("\\s+", " ").trim();
					String displayText = dateFormat.format(new Date(record.modifiedAt)) + " " + text.trim();
					if (!record.isGrouped) {
						displayText += " (" + record.file.getName() + ")";
					}
					setText(displayText);
				}
				return this;
			}
		});
		tree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
					showContextMenu(e);
					return;
				}
				if (e.getClickCount() >= 1) {
					openSelectedNode();
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				if (e.isPopupTrigger()) {
					showContextMenu(e);
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger()) {
					showContextMenu(e);
				}
			}
		});
		add(new JScrollPane(tree), BorderLayout.CENTER);

		refreshButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				refreshInBackground();
			}
		});
		autoRefreshTimer = new Timer(12000, new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				refreshInBackground();
			}
		});
		autoRefreshTimer.setDelay(300000);
		autoRefreshTimer.setRepeats(true);
		autoRefreshTimer.start();

		addListeners();
		refreshInBackground();
	}

	private void addListeners() {
		Controller.getCurrentController().getMapViewManager().addMapSelectionListener(new IMapSelectionListener() {
			public void beforeMapChange(MapModel oldMap, MapModel newMap) {
			}

			public void afterMapChange(MapModel oldMap, MapModel newMap) {
			}
		});
	}

	private synchronized void refreshInBackground() {
		if (activeWorker != null && !activeWorker.isDone()) {
			rescanRequested = true;
			return;
		}
		activeWorker = new SwingWorker() {
			protected Object doInBackground() throws Exception {
				List files = collectAllMindmapFiles();
				cleanupCache(files);
				for (int i = 0; i < files.size(); i++) {
					if (isCancelled()) {
						break;
					}
					File file = (File) files.get(i);
					List nodes = getNodesForFile(file);
					publish(new ScanChunk(file.getAbsolutePath(), nodes, i + 1, files.size()));
				}
				return null;
			}

			protected void process(List chunks) {
				for (int i = 0; i < chunks.size(); i++) {
					ScanChunk chunk = (ScanChunk) chunks.get(i);
					mergeChunk(chunk);
				}
				rebuildTreeFromCache();
				publishRecentlyModifiedMetric(nodesByKey.size());
			}

			protected void done() {
				final int shown = Math.min(MAX_ITEMS, nodesByKey.size());
				statusLabel.setText("最近修改: " + shown + (nodesByKey.size() > MAX_ITEMS ? " / " + nodesByKey.size() : ""));
				publishRecentlyModifiedMetric(shown);
				if (rescanRequested) {
					rescanRequested = false;
					refreshInBackground();
				}
			}
		};
		activeWorker.execute();
	}

	private void mergeChunk(ScanChunk chunk) {
		List oldKeys = (List) nodeKeysByFile.get(chunk.fileKey);
		if (oldKeys != null) {
			for (int i = 0; i < oldKeys.size(); i++) {
				nodesByKey.remove(oldKeys.get(i));
			}
		}
		List newKeys = new ArrayList();
		for (int i = 0; i < chunk.nodes.size(); i++) {
			ModifiedNode record = (ModifiedNode) chunk.nodes.get(i);
			String key = nodeKey(record);
			if (!nodesByKey.containsKey(key)) {
				nodesByKey.put(key, record);
				newKeys.add(key);
			}
		}
		nodeKeysByFile.put(chunk.fileKey, newKeys);
	}

	private List collectAllMindmapFiles() {
		Set roots = new HashSet();
		final File[] scanRoots = MindMapDataRootResolver.getScanRoots();
		for (int i = 0; i < scanRoots.length; i++) {
			if (scanRoots[i] != null && scanRoots[i].exists() && scanRoots[i].isDirectory()) {
				roots.add(scanRoots[i]);
			}
		}
		List normalizedRoots = normalizeRoots(roots);
		List files = new ArrayList();
		for (Object root : normalizedRoots) {
			collectMindmapFiles((File) root, files);
		}
		return files;
	}

	private List normalizeRoots(Set roots) {
		List normalizedRoots = new ArrayList();
		for (Object root : roots) {
			File file = (File) root;
			if (file.exists()) {
				try {
					normalizedRoots.add(file.getCanonicalFile());
				}
				catch (Exception e) {
					normalizedRoots.add(file.getAbsoluteFile());
				}
			}
		}
		return normalizedRoots;
	}

	private void collectMindmapFiles(File directory, List files) {
		if (!directory.exists() || !directory.isDirectory()) {
			return;
		}
		File[] children = directory.listFiles();
		if (children == null) {
			return;
		}
		for (File child : children) {
			if (child.getName().startsWith(".")) {
				continue;
			}
			if (child.isDirectory()) {
				collectMindmapFiles(child, files);
			}
			else if (child.getName().toLowerCase().endsWith(".mm")) {
				files.add(child);
			}
		}
	}

	private void cleanupCache(List currentFiles) {
		Set currentPaths = new HashSet();
		for (int i = 0; i < currentFiles.size(); i++) {
			File file = (File) currentFiles.get(i);
			currentPaths.add(file.getAbsolutePath());
		}
		List toRemove = new ArrayList();
		for (Object key : cacheByFile.keySet()) {
			if (!currentPaths.contains(key)) {
				toRemove.add(key);
			}
		}
		for (int i = 0; i < toRemove.size(); i++) {
			cacheByFile.remove(toRemove.get(i));
		}
		toRemove.clear();
		for (Object key : nodeKeysByFile.keySet()) {
			if (!currentPaths.contains(key)) {
				toRemove.add(key);
			}
		}
		for (int i = 0; i < toRemove.size(); i++) {
			String key = (String) toRemove.get(i);
			List keys = (List) nodeKeysByFile.remove(key);
			if (keys != null) {
				for (int j = 0; j < keys.size(); j++) {
					nodesByKey.remove(keys.get(j));
				}
			}
		}
	}

	private List getNodesForFile(final File file) {
		long modified = file.lastModified();
		long length = file.length();
		CachedFileResult cached = (CachedFileResult) cacheByFile.get(file.getAbsolutePath());
		if (cached != null && cached.modified == modified && cached.length == length) {
			return cached.nodes;
		}
		final List nodes = new ArrayList();
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
						String modifiedStr = attributes.getValue("MODIFIED");
						if (id != null && modifiedStr != null && text != null) {
							try {
								long modifiedAt = Long.parseLong(modifiedStr);
								Calendar cal = Calendar.getInstance();
								long cutoff = cal.getTimeInMillis() - (MAX_DAYS * 24L * 60L * 60L * 1000L);
								if (modifiedAt > cutoff && !"bin".equalsIgnoreCase(text) && !isNumericOnly(text)) {
								nodes.add(new ModifiedNode(file, id, text, modifiedAt, false));
							}
							}
							catch (Exception e) {
							}
						}
						nodeStack.add(new String[] { id, text });
					}
				}

				public void endElement(String uri, String localName, String qName) {
					if ("node".equals(qName) && !nodeStack.isEmpty()) {
						nodeStack.remove(nodeStack.size() - 1);
					}
				}
			});
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
		cacheByFile.put(file.getAbsolutePath(), new CachedFileResult(modified, length, nodes));
		return nodes;
	}

	private void rebuildTreeFromCache() {
		String selectedKey = getSelectedNodeKey();
		List records = new ArrayList(nodesByKey.values());
		Collections.sort(records, new Comparator() {
			public int compare(Object o1, Object o2) {
				ModifiedNode a = (ModifiedNode) o1;
				ModifiedNode b = (ModifiedNode) o2;
				return Long.compare(b.modifiedAt, a.modifiedAt);
			}
		});
		if (records.size() > MAX_ITEMS) {
			records = new ArrayList(records.subList(0, MAX_ITEMS));
		}

		DefaultMutableTreeNode root = new DefaultMutableTreeNode("最近修改");
		Map timeNodes = new HashMap();
		Map nodeNodesByKey = new HashMap();

		for (int i = 0; i < records.size(); i++) {
			ModifiedNode record = (ModifiedNode) records.get(i);

			String groupLabel = getTimeGroupLabel(record.modifiedAt);
			String fileName = record.file.getName();

			DefaultMutableTreeNode timeNode = (DefaultMutableTreeNode) timeNodes.get(groupLabel);
			if (timeNode == null) {
				timeNode = new DefaultMutableTreeNode(new GroupLabel(groupLabel), true);
				timeNodes.put(groupLabel, timeNode);
				root.add(timeNode);
			}

			boolean hasNextSameFile = (i + 1 < records.size()) && 
				((ModifiedNode) records.get(i + 1)).file.getName().equals(fileName);

			boolean hasPrevSameFile = (i > 0) && 
				((ModifiedNode) records.get(i - 1)).file.getName().equals(fileName);

			if (hasNextSameFile) {
				String fileGroupKey = groupLabel + "|" + fileName + "|" + i;
				DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(new GroupLabel(fileName), true);
				timeNode.add(fileNode);

				ModifiedNode groupedRecord = new ModifiedNode(record.file, record.nodeId, record.nodeText, record.modifiedAt, true);
				DefaultMutableTreeNode nodeNode = new DefaultMutableTreeNode(groupedRecord, false);
				fileNode.add(nodeNode);
				nodeNodesByKey.put(nodeKey(record), nodeNode);

				i++;
				while (i < records.size() && ((ModifiedNode) records.get(i)).file.getName().equals(fileName)) {
					ModifiedNode nextRecord = (ModifiedNode) records.get(i);
					ModifiedNode groupedNextRecord = new ModifiedNode(nextRecord.file, nextRecord.nodeId, nextRecord.nodeText, nextRecord.modifiedAt, true);
					nodeNode = new DefaultMutableTreeNode(groupedNextRecord, false);
					fileNode.add(nodeNode);
					nodeNodesByKey.put(nodeKey(nextRecord), nodeNode);
					i++;
				}
				i--;
			} else if (!hasPrevSameFile) {
				DefaultMutableTreeNode nodeNode = new DefaultMutableTreeNode(record, false);
				timeNode.add(nodeNode);
				nodeNodesByKey.put(nodeKey(record), nodeNode);
			}
		}

		tree.setModel(new DefaultTreeModel(root));

		for (int i = 0; i < tree.getRowCount(); i++) {
			tree.expandRow(i);
		}

		if (selectedKey != null) {
			DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) nodeNodesByKey.get(selectedKey);
			if (selectedNode != null) {
				TreePath selectedPath = new TreePath(selectedNode.getPath());
				tree.setSelectionPath(selectedPath);
				tree.scrollPathToVisible(selectedPath);
			}
		}
	}

	private String getTimeGroupLabel(long timestamp) {
		Calendar now = Calendar.getInstance();
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(timestamp);

		long nowTime = now.getTimeInMillis();
		long todayStart = getStartOfDay(now);
		long yesterdayStart = todayStart - 24 * 60 * 60 * 1000L;
		long twoDaysAgoStart = yesterdayStart - 24 * 60 * 60 * 1000L;

		if (timestamp >= todayStart) {
			return "今天";
		}
		else if (timestamp >= yesterdayStart) {
			return "昨天";
		}
		else if (timestamp >= twoDaysAgoStart) {
			return "前天";
		}
		else {
			cal.setTimeInMillis(timestamp);
			SimpleDateFormat sdf = new SimpleDateFormat("MM月dd日 EEEE", Locale.CHINA);
			return sdf.format(cal.getTime());
		}
	}

	private long getStartOfDay(Calendar cal) {
		Calendar start = Calendar.getInstance();
		start.setTime(cal.getTime());
		start.set(Calendar.HOUR_OF_DAY, 0);
		start.set(Calendar.MINUTE, 0);
		start.set(Calendar.SECOND, 0);
		start.set(Calendar.MILLISECOND, 0);
		return start.getTimeInMillis();
	}

	private boolean isNumericOnly(String text) {
		if (text == null) {
			return false;
		}
		text = text.trim();
		return text.length() <= 4 && text.matches("^\\d+$");
	}

	private String getSelectedNodeKey() {
		TreePath path = tree.getSelectionPath();
		if (path == null) {
			return null;
		}
		Object nodeObj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (!(nodeObj instanceof ModifiedNode)) {
			return null;
		}
		return nodeKey((ModifiedNode) nodeObj);
	}

	private String nodeKey(ModifiedNode record) {
		if (record == null || record.nodeId == null) {
			return "";
		}
		return canonicalPath(record.file) + "|" + record.nodeId;
	}

	private String canonicalPath(File file) {
		if (file == null) {
			return "";
		}
		try {
			return file.getCanonicalPath();
		}
		catch (Exception e) {
			return file.getAbsolutePath();
		}
	}

	private void showContextMenu(MouseEvent e) {
		TreePath path = tree.getPathForLocation(e.getX(), e.getY());
		if (path == null) {
			return;
		}
		tree.setSelectionPath(path);
		final DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
		final Object user = selectedNode.getUserObject();
		JPopupMenu menu = new JPopupMenu();

		if (user instanceof ModifiedNode) {
			final ModifiedNode record = (ModifiedNode) user;
			JMenuItem openFolderItem = new JMenuItem("打开文件夹");
			openFolderItem.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent event) {
					openContainingFolder(record.file);
				}
			});
			menu.add(openFolderItem);
		}

		JMenuItem copyItem = new JMenuItem("复制");
		copyItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent event) {
				copyNodeContent(selectedNode, user);
			}
		});
		menu.add(copyItem);
		menu.show(tree, e.getX(), e.getY());
	}

	private void openContainingFolder(File file) {
		if (file == null || !file.exists()) {
			return;
		}
		File parentDir = file.getParentFile();
		if (parentDir == null || !parentDir.isDirectory()) {
			return;
		}
		try {
			if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
				Runtime.getRuntime().exec("explorer.exe \"" + parentDir.getAbsolutePath() + "\"");
			} else if (System.getProperty("os.name").toLowerCase().startsWith("mac")) {
				Runtime.getRuntime().exec(new String[] { "open", parentDir.getAbsolutePath() });
			} else {
				Runtime.getRuntime().exec(new String[] { "xdg-open", parentDir.getAbsolutePath() });
			}
		} catch (Exception e) {
			LogUtils.warn(e);
		}
	}

	private void copyNodeContent(DefaultMutableTreeNode selectedNode, Object user) {
		String text = "";
		if (user instanceof ModifiedNode) {
			text = ((ModifiedNode) user).nodeText;
		}
		text = normalizeTaskText(text);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}

	private String normalizeTaskText(String text) {
		if (text == null) {
			return "";
		}
		return HtmlUtils.removeHtmlTagsFromString(text).replaceAll("\\s+", " ").trim();
	}

	private void openSelectedNode() {
		TreePath path = tree.getSelectionPath();
		if (path == null) {
			return;
		}
		Object nodeObj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (!(nodeObj instanceof ModifiedNode)) {
			return;
		}
		ModifiedNode record = (ModifiedNode) nodeObj;
		try {
			IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			URL url = record.file.toURI().toURL();
			if (!mapViewManager.tryToChangeToMapView(url)) {
				Controller.getCurrentModeController().getMapController().newMap(url);
			}
			selectNodeWithRetry(record, 0);
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
	}

	private void selectNodeWithRetry(final ModifiedNode record, final int attempt) {
		final int maxAttempts = 12;
		if (record == null || attempt > maxAttempts) {
			return;
		}
		try {
			IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			Map maps = mapViewManager.getMaps(MModeController.MODENAME);
			for (Object mapObj : maps.values()) {
				MapModel map = (MapModel) mapObj;
				File mapFile = map.getFile();
				if (isSameFile(mapFile, record.file)) {
					NodeModel node = map.getNodeForID(record.nodeId);
					if (node != null) {
						Controller.getCurrentController().getSelection().selectAsTheOnlyOneSelected(node);
						Controller.getCurrentModeController().getMapController().centerNode(node);
						tree.requestFocusInWindow();
						return;
					}
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
		Timer retry = new Timer(250, new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				selectNodeWithRetry(record, attempt + 1);
			}
		});
		retry.setRepeats(false);
		retry.start();
	}

	private boolean isSameFile(File file1, File file2) {
		if (file1 == null || file2 == null) {
			return file1 == file2;
		}
		try {
			return file1.getCanonicalPath().equals(file2.getCanonicalPath());
		}
		catch (Exception e) {
			return file1.getAbsolutePath().equals(file2.getAbsolutePath());
		}
	}

	private void installArrowKeyNavigation() {
		tree.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), "all.recent.up");
		tree.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "all.recent.down");
		tree.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "all.recent.left");
		tree.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "all.recent.right");
		tree.getActionMap().put("all.recent.up", new AbstractAction() {
			private static final long serialVersionUID = 1L;
			public void actionPerformed(java.awt.event.ActionEvent e) {
				int row = tree.getLeadSelectionRow();
				if (row <= 0) {
					return;
				}
				tree.setSelectionRow(row - 1);
				tree.scrollRowToVisible(row - 1);
			}
		});
		tree.getActionMap().put("all.recent.down", new AbstractAction() {
			private static final long serialVersionUID = 1L;
			public void actionPerformed(java.awt.event.ActionEvent e) {
				int row = tree.getLeadSelectionRow();
				if (row < 0) {
					row = 0;
				}
				if (row >= tree.getRowCount() - 1) {
					return;
				}
				tree.setSelectionRow(row + 1);
				tree.scrollRowToVisible(row + 1);
			}
		});
		tree.getActionMap().put("all.recent.left", new AbstractAction() {
			private static final long serialVersionUID = 1L;
			public void actionPerformed(java.awt.event.ActionEvent e) {
				TreePath path = tree.getSelectionPath();
				if (path == null) {
					return;
				}
				if (tree.isExpanded(path)) {
					tree.collapsePath(path);
				}
				else if (path.getParentPath() != null) {
					tree.setSelectionPath(path.getParentPath());
				}
			}
		});
		tree.getActionMap().put("all.recent.right", new AbstractAction() {
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

	private void publishRecentlyModifiedMetric(final int count) {
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_RECENT_MODIFIED, count);
		final List entries = new ArrayList();
		for (final Object key : nodesByKey.keySet()) {
			final ModifiedNode record = (ModifiedNode) nodesByKey.get(key);
			if (record == null || record.isGrouped) {
				continue;
			}
			String text = record.nodeText == null ? "" : HtmlUtils.removeHtmlTagsFromString(record.nodeText)
					.replaceAll("\\s+", " ").trim();
			entries.add(new WorkspaceSideTabSnapshot.ModifiedEntry(record.file, record.nodeId, text, record.modifiedAt));
		}
		WorkspaceSideTabSnapshotRegistry.updateRecentlyModifiedEntries(entries);
	}
}
