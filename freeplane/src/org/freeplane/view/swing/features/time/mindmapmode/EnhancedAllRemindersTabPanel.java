package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URL;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.text.DateFormat;
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
import javax.swing.JSplitPane;
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
import org.freeplane.core.util.WorkspaceSideTabSnapshot;
import org.freeplane.core.util.WorkspaceSideTabSnapshotRegistry;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.features.map.IMapSelectionListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class EnhancedAllRemindersTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final class ReminderRecord {
		private final File file;
		private final String nodeId;
		private final String nodeText;
		private final long remindAt;
		private final boolean isRecurring;
		private final int taskTime;
		private final int taskLevel;
		private final int jinji;

		private ReminderRecord(File file, String nodeId, String nodeText, long remindAt, boolean isRecurring,
				int taskTime, int taskLevel, int jinji) {
			this.file = file;
			this.nodeId = nodeId;
			this.nodeText = nodeText;
			this.remindAt = remindAt;
			this.isRecurring = isRecurring;
			this.taskTime = taskTime;
			this.taskLevel = taskLevel;
			this.jinji = jinji;
		}
	}

	private static final class CachedFileResult {
		private final long modified;
		private final long length;
		private final List reminders;

		private CachedFileResult(long modified, long length, List reminders) {
			this.modified = modified;
			this.length = length;
			this.reminders = reminders;
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
		private final List reminders;
		private final int scanned;
		private final int total;

		private ScanChunk(String fileKey, List reminders, int scanned, int total) {
			this.fileKey = fileKey;
			this.reminders = reminders;
			this.scanned = scanned;
			this.total = total;
		}
	}

	private final JButton refreshButton = new JButton("\u5237\u65b0");
	private final JLabel statusLabel = new JLabel("\u63d0\u9192\u603b\u6570: 0");
	private final ReminderCalendarPanel calendarPanel = new ReminderCalendarPanel();
	private final JTree tree = new JTree(new DefaultMutableTreeNode("\u5168\u90e8\u63d0\u9192"));
	private final DateFormat dayFormat = new SimpleDateFormat("ddE", Locale.CHINA);
	private final DateFormat monthFormat = new SimpleDateFormat("MM", Locale.CHINA);
	private final DateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.CHINA);
	private final DateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
	private final DecimalFormat twoDigits = new DecimalFormat("00");
	private final Map cacheByFile = new HashMap();
	private final Map remindersByKey = new HashMap();
	private final Map reminderKeysByFile = new HashMap();
	private SwingWorker activeWorker;
	private boolean rescanRequested;
	private Set lastActiveFileKeys = Collections.emptySet();
	private final Timer autoRefreshTimer;

	public EnhancedAllRemindersTabPanel() {
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
				else if (user instanceof ReminderRecord) {
					ReminderRecord record = (ReminderRecord) user;
					String text = record.nodeText == null ? "" : HtmlUtils.removeHtmlTagsFromString(record.nodeText).replaceAll("\\s+", " ").trim();
					final String duration = ReminderTaskFormatter.formatDurationPadding(record.taskTime, 4);
					setText(timeFormat.format(new Date(record.remindAt)) + duration + " " + text.trim() + " ("
							+ record.file.getName() + ")");
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
					openSelectedReminder();
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
		final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tree), calendarPanel);
		split.setResizeWeight(0.55);
		split.setDividerLocation(260);
		add(split, BorderLayout.CENTER);

		calendarPanel.setCheckInEnabled(false);
		calendarPanel.setNavigationHandler(new ReminderCalendarPanel.NavigationHandler() {
			public void openEntry(final ReminderCalendarEntry entry) {
				ReminderTabNavigation.openEntry(entry);
			}
		});

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
				refreshInBackground();
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
				Set activeFileKeys = new HashSet();
				for (int i = 0; i < files.size(); i++) {
					activeFileKeys.add(((File) files.get(i)).getAbsolutePath());
				}
				lastActiveFileKeys = activeFileKeys;
				purgeStaleReminders(activeFileKeys);
				javax.swing.SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						rebuildTreeFromCache();
						statusLabel.setText("\u63d0\u9192\u603b\u6570: " + remindersByKey.size());
					}
				});
				for (int i = 0; i < files.size(); i++) {
					if (isCancelled()) {
						break;
					}
					File file = (File) files.get(i);
					if (!isValidMindmapFile(file)) {
						continue;
					}
					List reminders = getRemindersForFile(file);
					publish(new ScanChunk(file.getAbsolutePath(), reminders, i + 1, files.size()));
				}
				return null;
			}

			protected void process(List chunks) {
				for (int i = 0; i < chunks.size(); i++) {
					ScanChunk chunk = (ScanChunk) chunks.get(i);
					mergeChunk(chunk);
				}
				rebuildTreeFromCache();
			}

			protected void done() {
				purgeStaleReminders(lastActiveFileKeys);
				rebuildTreeFromCache();
				statusLabel.setText("\u63d0\u9192\u603b\u6570: " + remindersByKey.size());
				if (rescanRequested) {
					rescanRequested = false;
					refreshInBackground();
				}
			}
		};
		activeWorker.execute();
	}

	private void mergeChunk(ScanChunk chunk) {
		if (chunk.fileKey != null && !lastActiveFileKeys.contains(chunk.fileKey)) {
			return;
		}
		List oldKeys = (List) reminderKeysByFile.get(chunk.fileKey);
		if (oldKeys != null) {
			for (int i = 0; i < oldKeys.size(); i++) {
				remindersByKey.remove(oldKeys.get(i));
			}
		}
		List newKeys = new ArrayList();
		for (int i = 0; i < chunk.reminders.size(); i++) {
			ReminderRecord record = (ReminderRecord) chunk.reminders.get(i);
			if (!isValidMindmapFile(record.file)) {
				continue;
			}
			String key = reminderKey(record);
			if (!remindersByKey.containsKey(key)) {
				remindersByKey.put(key, record);
				newKeys.add(key);
			}
		}
		reminderKeysByFile.put(chunk.fileKey, newKeys);
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
		Set seenFiles = new HashSet();
		for (Object rootObj : normalizedRoots) {
			collectMindmapFilesRecursive((File) rootObj, files);
		}
		for (int i = files.size() - 1; i >= 0; i--) {
			File f = (File) files.get(i);
			if (!seenFiles.add(f.getAbsolutePath())) {
				files.remove(i);
			}
		}
		return files;
	}

	private List normalizeRoots(Set rawRoots) {
		List roots = new ArrayList(rawRoots);
		Collections.sort(roots, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((File) o1).getAbsolutePath().length() - ((File) o2).getAbsolutePath().length();
			}
		});
		List normalized = new ArrayList();
		for (int i = 0; i < roots.size(); i++) {
			File candidate = (File) roots.get(i);
			boolean covered = false;
			for (int j = 0; j < normalized.size(); j++) {
				File existing = (File) normalized.get(j);
				String existingPath = existing.getAbsolutePath();
				String candidatePath = candidate.getAbsolutePath();
				if (candidatePath.equals(existingPath) || candidatePath.startsWith(existingPath + File.separator)) {
					covered = true;
					break;
				}
			}
			if (!covered) {
				normalized.add(candidate);
			}
		}
		return normalized;
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
		for (Object key : reminderKeysByFile.keySet()) {
			if (!currentPaths.contains(key)) {
				toRemove.add(key);
			}
		}
		for (int i = 0; i < toRemove.size(); i++) {
			String fileKey = (String) toRemove.get(i);
			List oldKeys = (List) reminderKeysByFile.get(fileKey);
			if (oldKeys != null) {
				for (int j = 0; j < oldKeys.size(); j++) {
					remindersByKey.remove(oldKeys.get(j));
				}
			}
			reminderKeysByFile.remove(fileKey);
		}
	}

	private void collectMindmapFilesRecursive(File dir, List out) {
		if (dir == null || !dir.exists() || !dir.isDirectory()) {
			return;
		}
		File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (int i = 0; i < children.length; i++) {
			File file = children[i];
			if (file.isDirectory()) {
				if (!file.isHidden() && !file.getName().startsWith(".")) {
					collectMindmapFilesRecursive(file, out);
				}
			}
			else if (isValidMindmapFile(file)) {
				out.add(file);
			}
		}
	}

	private boolean isValidMindmapFile(File file) {
		if (file == null || !file.isFile() || !file.exists()) {
			return false;
		}
		String name = file.getName();
		if (name.startsWith("~") || name.contains("冲突副本")) {
			return false;
		}
		return name.toLowerCase().endsWith(".mm");
	}

	private void purgeStaleReminders(Set activeFileKeys) {
		if (activeFileKeys == null) {
			activeFileKeys = Collections.emptySet();
		}
		List staleFileKeys = new ArrayList();
		for (Object fileKeyObj : reminderKeysByFile.keySet()) {
			String fileKey = (String) fileKeyObj;
			// MindMapFileIdentity.storageKey is not a path — do not use new File(fileKey).
			if (!activeFileKeys.contains(fileKey) || !hasValidBackingReminderFile(fileKey)) {
				staleFileKeys.add(fileKey);
			}
		}
		for (int i = 0; i < staleFileKeys.size(); i++) {
			String fileKey = (String) staleFileKeys.get(i);
			List oldKeys = (List) reminderKeysByFile.remove(fileKey);
			if (oldKeys != null) {
				for (int j = 0; j < oldKeys.size(); j++) {
					remindersByKey.remove(oldKeys.get(j));
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

	private boolean hasValidBackingReminderFile(final String fileKey) {
		final List keys = (List) reminderKeysByFile.get(fileKey);
		if (keys == null || keys.isEmpty()) {
			return false;
		}
		for (int i = 0; i < keys.size(); i++) {
			final Object item = remindersByKey.get(keys.get(i));
			if (item instanceof ReminderRecord) {
				final ReminderRecord record = (ReminderRecord) item;
				if (isValidMindmapFile(record.file)) {
					return true;
				}
			}
		}
		return false;
	}

	private List getRemindersForFile(final File file) {
		if (!isValidMindmapFile(file)) {
			return Collections.emptyList();
		}
		long modified = file.lastModified();
		long length = file.length();
		CachedFileResult cached = (CachedFileResult) cacheByFile.get(file.getAbsolutePath());
		if (cached != null && cached.modified == modified && cached.length == length) {
			return cached.reminders;
		}
		final List reminders = new ArrayList();
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
						String remindType = attributes.getValue("REMINDERTYPE");
						final ReminderTaskAttributes.TaskConfig taskConfig = ReminderTaskAttributes
								.readFromSaxAttributes(attributes);
						nodeStack.add(new Object[] { id, text == null ? "" : text, remindType, taskConfig });
					}
					else if ("Parameters".equals(qName) && !nodeStack.isEmpty()) {
						String remindAt = attributes.getValue("REMINDUSERAT");
						if (remindAt != null) {
							try {
								long remindTs = Long.parseLong(remindAt);
								if (remindTs > 0) {
									Object[] nodeInfo = (Object[]) nodeStack.get(nodeStack.size() - 1);
									String nodeText = nodeInfo[1] == null ? "" : ((String) nodeInfo[1]).trim();
									String remindType = nodeInfo.length > 2 ? (String) nodeInfo[2] : null;
									final ReminderTaskAttributes.TaskConfig taskConfig = nodeInfo.length > 3 ? (ReminderTaskAttributes.TaskConfig) nodeInfo[3]
											: ReminderTaskAttributes.TaskConfig.empty();
									boolean isRecurring = (remindType != null && !"onetime".equalsIgnoreCase(remindType));
									if (!"bin".equalsIgnoreCase(nodeText) && !isRecurring) {
										reminders.add(new ReminderRecord(file, (String) nodeInfo[0], nodeText, remindTs,
												false, taskConfig.taskTime, taskConfig.taskLevel, taskConfig.jinji));
									}
								}
							}
							catch (Exception e) {
							}
						}
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
		cacheByFile.put(file.getAbsolutePath(), new CachedFileResult(modified, length, reminders));
		return reminders;
	}

	private void rebuildTreeFromCache() {
		String selectedKey = getSelectedReminderKey();
		List records = new ArrayList(remindersByKey.values());
		Collections.sort(records, new Comparator() {
			public int compare(Object o1, Object o2) {
				ReminderRecord a = (ReminderRecord) o1;
				ReminderRecord b = (ReminderRecord) o2;
				return a.remindAt < b.remindAt ? -1 : (a.remindAt == b.remindAt ? 0 : 1);
			}
		});
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("\u5168\u90e8\u63d0\u9192");
		Map yearNodes = new HashMap();
		Map monthNodes = new HashMap();
		Map weekNodes = new HashMap();
		Map dayNodes = new HashMap();
		Map reminderNodesByKey = new HashMap();
		for (int i = 0; i < records.size(); i++) {
			ReminderRecord record = (ReminderRecord) records.get(i);
			if (!isValidMindmapFile(record.file)) {
				continue;
			}
			Date date = new Date(record.remindAt);
			Calendar cal = Calendar.getInstance();
			cal.setFirstDayOfWeek(Calendar.MONDAY);
			cal.setMinimalDaysInFirstWeek(4);
			cal.setTime(date);
			String yearKey = yearFormat.format(date);
			String monthKey = monthFormat.format(date);
			String monthNodeKey = yearKey + "|" + monthKey;
			String weekKey = monthNodeKey + "|W" + cal.get(Calendar.WEEK_OF_MONTH);
			String dayKey = dayFormat.format(date);
			String dayNodeKey = weekKey + "|" + dayKey;

			DefaultMutableTreeNode yearNode = (DefaultMutableTreeNode) yearNodes.get(yearKey);
			if (yearNode == null) {
				yearNode = new DefaultMutableTreeNode(new GroupLabel(yearKey + "年"));
				yearNodes.put(yearKey, yearNode);
				root.add(yearNode);
			}

			DefaultMutableTreeNode monthNode = (DefaultMutableTreeNode) monthNodes.get(monthNodeKey);
			if (monthNode == null) {
				monthNode = new DefaultMutableTreeNode(new GroupLabel(monthKey + "月"));
				monthNodes.put(monthNodeKey, monthNode);
				yearNode.add(monthNode);
			}

			DefaultMutableTreeNode weekNode = (DefaultMutableTreeNode) weekNodes.get(weekKey);
			if (weekNode == null) {
				String weekLabel = "\u7b2c" + cal.get(Calendar.WEEK_OF_MONTH) + "\u5468";
				weekNode = new DefaultMutableTreeNode(new GroupLabel(weekLabel));
				weekNodes.put(weekKey, weekNode);
				monthNode.add(weekNode);
			}

			DefaultMutableTreeNode dayNode = (DefaultMutableTreeNode) dayNodes.get(dayNodeKey);
			if (dayNode == null) {
				dayNode = new DefaultMutableTreeNode(new GroupLabel(dayKey));
				dayNodes.put(dayNodeKey, dayNode);
				weekNode.add(dayNode);
			}
			DefaultMutableTreeNode reminderNode = new DefaultMutableTreeNode(record, false);
			dayNode.add(reminderNode);
			reminderNodesByKey.put(reminderKey(record), reminderNode);
		}
		tree.setModel(new DefaultTreeModel(root));
		for (int i = 0; i < tree.getRowCount(); i++) {
			tree.expandRow(i);
		}
		if (selectedKey != null) {
			DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) reminderNodesByKey.get(selectedKey);
			if (selectedNode != null) {
				TreePath selectedPath = new TreePath(selectedNode.getPath());
				tree.setSelectionPath(selectedPath);
				tree.scrollPathToVisible(selectedPath);
			}
		}
		publishOneTimeReminderSnapshot(records);
		updateCalendarPanel(records);
	}

	private void updateCalendarPanel(final List records) {
		final List calendarEntries = new ArrayList();
		for (int i = 0; i < records.size(); i++) {
			final ReminderRecord record = (ReminderRecord) records.get(i);
			calendarEntries.add(new ReminderCalendarEntry(record.file, record.nodeId, record.nodeText, record.remindAt,
					false, null, record.taskTime, record.taskLevel, record.jinji));
		}
		calendarPanel.setEntries(calendarEntries);
	}

	private void publishOneTimeReminderSnapshot(List records) {
		List entries = new ArrayList();
		for (int i = 0; i < records.size(); i++) {
			ReminderRecord record = (ReminderRecord) records.get(i);
			if (!isValidMindmapFile(record.file)) {
				continue;
			}
			String text = record.nodeText == null ? "" : HtmlUtils.removeHtmlTagsFromString(record.nodeText)
					.replaceAll("\\s+", " ").trim();
			entries.add(new WorkspaceSideTabSnapshot.ReminderEntry(record.file, record.nodeId, text, record.remindAt,
					false, null));
		}
		WorkspaceSideTabSnapshotRegistry.updateOneTimeReminders(entries);
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_ALL_REMINDERS, entries.size());
	}

	private String getSelectedReminderKey() {
		TreePath path = tree.getSelectionPath();
		if (path == null) {
			return null;
		}
		Object nodeObj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (!(nodeObj instanceof ReminderRecord)) {
			return null;
		}
		return reminderKey((ReminderRecord) nodeObj);
	}

	private String reminderKey(ReminderRecord record) {
		if (record == null || record.nodeId == null) {
			return "";
		}
		return canonicalPath(record.file) + "|" + record.nodeId;
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
		
		if (user instanceof ReminderRecord) {
			final ReminderRecord record = (ReminderRecord) user;
			JMenuItem openFolderItem = new JMenuItem("\u6253\u5f00\u6587\u4ef6\u5939");
			openFolderItem.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent event) {
					openContainingFolder(record.file);
				}
			});
			menu.add(openFolderItem);
		}
		
		JMenuItem copyItem = new JMenuItem("\u590d\u5236");
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
		String textToCopy = "";
		if (user instanceof ReminderRecord) {
			ReminderRecord record = (ReminderRecord) user;
			textToCopy = normalizeTaskText(record.nodeText);
		}
		else {
			List lines = new ArrayList();
			collectReminderLines(selectedNode, lines, "");
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < lines.size(); i++) {
				if (i > 0) {
					sb.append('\n');
				}
				sb.append(((String) lines.get(i)).trim());
			}
			textToCopy = sb.toString();
		}
		if (textToCopy != null && textToCopy.length() > 0) {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(textToCopy.trim()), null);
		}
	}

	private void collectReminderLines(DefaultMutableTreeNode node, List out, String prefix) {
		Object user = node.getUserObject();
		if (user instanceof ReminderRecord) {
			ReminderRecord record = (ReminderRecord) user;
			String leaf = timeFormat.format(new Date(record.remindAt)) + " " + normalizeTaskText(record.nodeText) + " (" + record.file.getName() + ")";
			String line = prefix.length() == 0 ? leaf : (prefix + " > " + leaf);
			out.add(line.trim());
			return;
		}
		String nextPrefix = prefix;
		if (user instanceof GroupLabel) {
			String groupText = ((GroupLabel) user).text == null ? "" : ((GroupLabel) user).text.trim();
			if (groupText.length() > 0) {
				nextPrefix = prefix.length() == 0 ? groupText : (prefix + " > " + groupText);
			}
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			collectReminderLines((DefaultMutableTreeNode) node.getChildAt(i), out, nextPrefix);
		}
	}

	private String normalizeTaskText(String text) {
		if (text == null) {
			return "";
		}
		return HtmlUtils.removeHtmlTagsFromString(text).replaceAll("\\s+", " ").trim();
	}

	private void openSelectedReminder() {
		TreePath path = tree.getSelectionPath();
		if (path == null) {
			return;
		}
		Object nodeObj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (!(nodeObj instanceof ReminderRecord)) {
			return;
		}
		ReminderRecord record = (ReminderRecord) nodeObj;
		try {
			IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			URL url = record.file.toURI().toURL();
			if (!mapViewManager.tryToChangeToMapView(url)) {
				Controller.getCurrentModeController().getMapController().newMap(url);
			}
			selectReminderNodeWithRetry(record, 0);
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
	}

	private void selectReminderNodeWithRetry(final ReminderRecord record, final int attempt) {
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
				selectReminderNodeWithRetry(record, attempt + 1);
			}
		});
		retry.setRepeats(false);
		retry.start();
	}

	private boolean isSameFile(File a, File b) {
		if (a == null || b == null) {
			return false;
		}
		return canonicalPath(a).equals(canonicalPath(b));
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

	private void installArrowKeyNavigation() {
		tree.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), "all.reminders.up");
		tree.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "all.reminders.down");
		tree.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "all.reminders.left");
		tree.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "all.reminders.right");
		tree.getActionMap().put("all.reminders.up", new AbstractAction() {
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
		tree.getActionMap().put("all.reminders.down", new AbstractAction() {
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
		tree.getActionMap().put("all.reminders.left", new AbstractAction() {
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
		tree.getActionMap().put("all.reminders.right", new AbstractAction() {
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
}
