package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URL;
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
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
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

public class EnhancedAllRecurringRemindersTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;

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

	private static final String[] TABLE_COLUMNS = { "\u5206\u7ec4", "\u65f6\u95f4", "\u65f6\u957f", "\u7b49\u7ea7",
			"\u7d27\u6025", "\u5468\u671f", "\u4efb\u52a1", "\u5bfc\u56fe" };

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
	private final JButton postponeAllButton = new JButton("\u5ef6\u671f\u6240\u6709");
	private final JLabel statusLabel = new JLabel("\u5468\u671f\u63d0\u9192\u603b\u6570: 0");
	private final ReminderCalendarPanel calendarPanel = new ReminderCalendarPanel();
	private final DefaultTableModel tableModel = new DefaultTableModel(TABLE_COLUMNS, 0) {
		private static final long serialVersionUID = 1L;
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final List tableRowRecords = new ArrayList();
	private final JTable table = new JTable(tableModel);
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
	private final Timer autoRefreshTimer;

	public EnhancedAllRecurringRemindersTabPanel() {
		super(new BorderLayout(4, 4));
		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.add(statusLabel, BorderLayout.CENTER);
		JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		topButtons.add(postponeAllButton);
		topButtons.add(refreshButton);
		top.add(topButtons, BorderLayout.EAST);
		add(top, BorderLayout.NORTH);

		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setAutoCreateRowSorter(false);
		table.getColumnModel().getColumn(0).setPreferredWidth(72);
		table.getColumnModel().getColumn(1).setPreferredWidth(56);
		table.getColumnModel().getColumn(2).setPreferredWidth(44);
		table.getColumnModel().getColumn(3).setPreferredWidth(40);
		table.getColumnModel().getColumn(4).setPreferredWidth(40);
		table.getColumnModel().getColumn(5).setPreferredWidth(120);
		table.getColumnModel().getColumn(6).setPreferredWidth(200);
		table.getColumnModel().getColumn(7).setPreferredWidth(100);
		table.addMouseListener(new MouseAdapter() {
			private static final int COL_TIME = 1;

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
					showContextMenu(e);
					return;
				}
				if (e.getButton() != MouseEvent.BUTTON1 || e.getClickCount() < 2) {
					return;
				}
				final int row = table.rowAtPoint(e.getPoint());
				if (row < 0 || row >= tableRowRecords.size()) {
					return;
				}
				table.setRowSelectionInterval(row, row);
				final int column = table.columnAtPoint(e.getPoint());
				final RecurringReminderEntry record = (RecurringReminderEntry) tableRowRecords.get(row);
				if (column == COL_TIME) {
					if (RecurringReminderCheckInService.openCheckInForEntry(record, record.remindAt)) {
						refreshInBackground();
					}
				}
				else {
					openReminderEntry(record);
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
		add(buildMainSplit(), BorderLayout.CENTER);

		refreshButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				refreshInBackground();
			}
		});
		postponeAllButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				postponeAllExpiredReminders();
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
		calendarPanel.setNavigationHandler(new ReminderCalendarPanel.NavigationHandler() {
			public void openEntry(final ReminderCalendarEntry entry) {
				ReminderTabNavigation.openEntry(entry);
			}
		});
		calendarPanel.setCheckInHandler(new ReminderCalendarPanel.CheckInHandler() {
			public void checkInEntry(final ReminderCalendarEntry entry, final long occurrenceAt) {
				final RecurringReminderEntry recurring = entry == null ? null : entry.toRecurringEntry();
				if (recurring != null && RecurringReminderCheckInService.openCheckInForEntry(recurring, occurrenceAt)) {
					refreshInBackground();
				}
			}
		});
		refreshInBackground();
	}

	private JSplitPane buildMainSplit() {
		final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), calendarPanel);
		split.setResizeWeight(0.55);
		split.setDividerLocation(260);
		return split;
	}

	private void postponeAllExpiredReminders() {
		final List expired = collectExpiredEntries();
		if (expired.isEmpty()) {
			JOptionPane.showMessageDialog(this, "\u5f53\u524d\u6ca1\u6709\u8fc7\u671f\u7684\u5468\u671f\u4efb\u52a1\u3002", "\u5468\u671f\u63d0\u9192",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final int choice = JOptionPane.showConfirmDialog(this, "\u5c06\u628a " + expired.size()
				+ " \u4e2a\u8fc7\u671f\u4efb\u52a1\u63a8\u8fdb\u5230\u5404\u81ea\u7684\u4e0b\u4e00\u4e2a\u5468\u671f\u65e5\u671f\u3002\n"
				+ "\u4fee\u6539\u540e\u8bf7\u4fdd\u5b58\u76f8\u5173\u5bfc\u56fe\uff08Ctrl+S\uff09\u3002", "\u5ef6\u671f\u6240\u6709",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (choice != JOptionPane.OK_OPTION) {
			return;
		}
		postponeAllButton.setEnabled(false);
		new SwingWorker() {
			protected Object doInBackground() throws Exception {
				return Integer.valueOf(RecurringReminderPostponeService.postponeAllExpired(expired));
			}

			protected void done() {
				postponeAllButton.setEnabled(true);
				try {
					final int updated = ((Integer) get()).intValue();
					invalidateCacheForEntries(expired);
					refreshInBackground();
					JOptionPane.showMessageDialog(EnhancedAllRecurringRemindersTabPanel.this, "\u5df2\u5ef6\u671f "
							+ updated + " \u4e2a\u4efb\u52a1\u3002", "\u5468\u671f\u63d0\u9192", JOptionPane.INFORMATION_MESSAGE);
				}
				catch (Exception e) {
					LogUtils.warn(e);
				}
			}
		}.execute();
	}

	private List collectExpiredEntries() {
		final List expired = new ArrayList();
		final long todayStart = startOfToday();
		for (final Object keyObj : remindersByKey.keySet()) {
			final RecurringReminderEntry entry = (RecurringReminderEntry) remindersByKey.get(keyObj);
			if (entry != null && entry.remindAt < todayStart) {
				expired.add(entry);
			}
		}
		return expired;
	}

	private long startOfToday() {
		final Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTimeInMillis();
	}

	private void invalidateCacheForEntries(final List entries) {
		for (int i = 0; i < entries.size(); i++) {
			final RecurringReminderEntry entry = (RecurringReminderEntry) entries.get(i);
			if (entry != null && entry.file != null) {
				cacheByFile.remove(entry.file.getAbsolutePath());
			}
		}
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
		rescanRequested = false;
		activeWorker = new SwingWorker() {
			protected Object doInBackground() throws Exception {
				List files = collectAllMindmapFiles();
				cleanupCache(files);
				for (int i = 0; i < files.size(); i++) {
					if (isCancelled()) {
						break;
					}
					File file = (File) files.get(i);
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
				rebuildTableFromCache();
			}

			protected void done() {
				statusLabel.setText("\u5468\u671f\u63d0\u9192\u603b\u6570: " + remindersByKey.size());
				if (rescanRequested) {
					rescanRequested = false;
					refreshInBackground();
				}
			}
		};
		activeWorker.execute();
	}

	private void mergeChunk(ScanChunk chunk) {
		List oldKeys = (List) reminderKeysByFile.get(chunk.fileKey);
		if (oldKeys != null) {
			for (int i = 0; i < oldKeys.size(); i++) {
				remindersByKey.remove(oldKeys.get(i));
			}
		}
		List newKeys = new ArrayList();
		for (int i = 0; i < chunk.reminders.size(); i++) {
			RecurringReminderEntry record = (RecurringReminderEntry) chunk.reminders.get(i);
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
		for (Object key : reminderKeysByFile.keySet()) {
			if (!currentPaths.contains(key)) {
				toRemove.add(key);
			}
		}
		for (int i = 0; i < toRemove.size(); i++) {
			String key = (String) toRemove.get(i);
			List keys = (List) reminderKeysByFile.remove(key);
			if (keys != null) {
				for (int j = 0; j < keys.size(); j++) {
					remindersByKey.remove(keys.get(j));
				}
			}
		}
	}

	private List getRemindersForFile(final File file) {
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
						final ReminderCycleAttributes.CycleConfig cycleConfig = ReminderCycleAttributes
								.readFromSaxAttributes(attributes);
						final ReminderTaskAttributes.TaskConfig taskConfig = ReminderTaskAttributes
								.readFromSaxAttributes(attributes);
						nodeStack.add(new Object[] { id, text == null ? "" : text, cycleConfig, taskConfig });
					}
					else if ("Parameters".equals(qName) && !nodeStack.isEmpty()) {
						String remindAt = attributes.getValue("REMINDUSERAT");
						if (remindAt != null) {
							try {
								long remindTs = Long.parseLong(remindAt);
								if (remindTs > 0) {
									Object[] nodeInfo = (Object[]) nodeStack.get(nodeStack.size() - 1);
									String nodeText = nodeInfo[1] == null ? "" : ((String) nodeInfo[1]).trim();
									final ReminderCycleAttributes.CycleConfig cycleConfig = (ReminderCycleAttributes.CycleConfig) nodeInfo[2];
									final ReminderTaskAttributes.TaskConfig taskConfig = (ReminderTaskAttributes.TaskConfig) nodeInfo[3];
									if (!"bin".equalsIgnoreCase(nodeText) && cycleConfig.isRecurring()) {
										reminders.add(new RecurringReminderEntry(file, (String) nodeInfo[0], nodeText,
												remindTs, cycleConfig, taskConfig.taskTime, taskConfig.taskLevel,
												taskConfig.jinji));
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

	private void rebuildTableFromCache() {
		String selectedKey = getSelectedReminderKey();
		List records = new ArrayList(remindersByKey.values());
		mergeOpenMapReminders(records);
		Collections.sort(records, new Comparator() {
			public int compare(Object o1, Object o2) {
				RecurringReminderEntry a = (RecurringReminderEntry) o1;
				RecurringReminderEntry b = (RecurringReminderEntry) o2;
				return Long.compare(a.remindAt, b.remindAt);
			}
		});

		while (tableModel.getRowCount() > 0) {
			tableModel.removeRow(0);
		}
		tableRowRecords.clear();
		Map rowIndexByKey = new HashMap();

		for (int i = 0; i < records.size(); i++) {
			RecurringReminderEntry record = (RecurringReminderEntry) records.get(i);
			final GroupLabel group = buildGroupLabel(record.remindAt);
			final String taskText = normalizeTaskText(record.nodeText);
			final String cycleLabel = ReminderCycleTypeFormatter.format(record.cycleConfig);
			tableModel.addRow(new Object[] { group.text, timeFormat.format(new Date(record.remindAt)),
					ReminderTaskFormatter.formatDurationMinutes(record.taskTime),
					ReminderTaskFormatter.formatLevel(record.taskLevel),
					ReminderTaskFormatter.formatUrgency(record.jinji), cycleLabel, taskText, record.file.getName() });
			tableRowRecords.add(record);
			rowIndexByKey.put(reminderKey(record), Integer.valueOf(tableRowRecords.size() - 1));
		}

		if (selectedKey != null) {
			Integer rowIndex = (Integer) rowIndexByKey.get(selectedKey);
			if (rowIndex != null) {
				final int row = rowIndex.intValue();
				table.setRowSelectionInterval(row, row);
				table.scrollRectToVisible(table.getCellRect(row, 0, true));
			}
		}
		publishRecurringReminderSnapshot(records);
		calendarPanel.setEntries(records);
	}

	private GroupLabel buildGroupLabel(final long remindAt) {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		long today = cal.getTimeInMillis();
		if (remindAt < today) {
			return new GroupLabel("\u8fc7\u671f");
		}
		if (remindAt < today + 24 * 60 * 60 * 1000) {
			return new GroupLabel("\u4eca\u5929");
		}
		if (remindAt < today + 2 * 24 * 60 * 60 * 1000) {
			return new GroupLabel("\u660e\u5929");
		}
		cal.setTimeInMillis(remindAt);
		return new GroupLabel(monthFormat.format(cal.getTime()) + "\u6708" + dayFormat.format(cal.getTime()));
	}

	private void mergeOpenMapReminders(final List records) {
		final Set existingKeys = new HashSet();
		for (int i = 0; i < records.size(); i++) {
			existingKeys.add(reminderKey((RecurringReminderEntry) records.get(i)));
		}
		try {
			final Map maps = Controller.getCurrentController().getMapViewManager().getMaps(MModeController.MODENAME);
			for (final Object mapObj : maps.values()) {
				final MapModel map = (MapModel) mapObj;
				final File mapFile = map.getFile();
				if (mapFile == null) {
					continue;
				}
				collectRecurringRemindersFromNode(map.getRootNode(), mapFile, records, existingKeys);
			}
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
	}

	private void collectRecurringRemindersFromNode(final NodeModel node, final File file, final List records,
			final Set existingKeys) {
		if (node == null) {
			return;
		}
		final ReminderExtension reminder = ReminderExtension.getExtension(node);
		if (reminder != null) {
			final ReminderCycleAttributes.CycleConfig cycleConfig = ReminderCycleAttributes.readFromNode(node);
			if (cycleConfig.isRecurring()) {
				final String nodeText = node.getText() == null ? "" : node.getText().trim();
				if (nodeText.length() > 0 && !"bin".equalsIgnoreCase(nodeText)) {
					final ReminderTaskAttributes.TaskConfig taskConfig = ReminderTaskAttributes.readFromNode(node);
					final RecurringReminderEntry record = new RecurringReminderEntry(file, node.getID(), nodeText,
							reminder.getRemindUserAt(), cycleConfig, taskConfig.taskTime, taskConfig.taskLevel,
							taskConfig.jinji);
					final String key = reminderKey(record);
					if (!existingKeys.contains(key)) {
						records.add(record);
						existingKeys.add(key);
						remindersByKey.put(key, record);
					}
					else {
						remindersByKey.put(key, record);
					}
				}
			}
		}
		for (final NodeModel child : node.getChildren()) {
			collectRecurringRemindersFromNode(child, file, records, existingKeys);
		}
	}

	private void publishRecurringReminderSnapshot(List records) {
		List entries = new ArrayList();
		for (int i = 0; i < records.size(); i++) {
			RecurringReminderEntry record = (RecurringReminderEntry) records.get(i);
			String text = record.nodeText == null ? "" : HtmlUtils.removeHtmlTagsFromString(record.nodeText)
					.replaceAll("\\s+", " ").trim();
			entries.add(new WorkspaceSideTabSnapshot.ReminderEntry(record.file, record.nodeId, text, record.remindAt,
					true, record.cycleConfig.remindType));
		}
		WorkspaceSideTabSnapshotRegistry.updateRecurringReminders(entries);
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_RECURRING_REMINDERS, entries.size());
	}

	private String getSelectedReminderKey() {
		final int row = table.getSelectedRow();
		if (row < 0 || row >= tableRowRecords.size()) {
			return null;
		}
		return reminderKey((RecurringReminderEntry) tableRowRecords.get(row));
	}

	private RecurringReminderEntry getSelectedRecurringReminderEntry() {
		final int row = table.getSelectedRow();
		if (row < 0 || row >= tableRowRecords.size()) {
			return null;
		}
		return (RecurringReminderEntry) tableRowRecords.get(row);
	}

	private String reminderKey(RecurringReminderEntry record) {
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
		final int row = table.rowAtPoint(e.getPoint());
		if (row < 0 || row >= tableRowRecords.size()) {
			return;
		}
		table.setRowSelectionInterval(row, row);
		final RecurringReminderEntry record = (RecurringReminderEntry) tableRowRecords.get(row);
		JPopupMenu menu = new JPopupMenu();

		if (record != null) {
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
				copyNodeContent(record);
			}
		});
		menu.add(copyItem);
		menu.show(table, e.getX(), e.getY());
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

	private void copyNodeContent(final RecurringReminderEntry record) {
		String text = record == null ? "" : record.nodeText;
		text = normalizeTaskText(text);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}

	private String normalizeTaskText(String text) {
		if (text == null) {
			return "";
		}
		return HtmlUtils.removeHtmlTagsFromString(text).replaceAll("\\s+", " ").trim();
	}

	private void openSelectedReminder() {
		openReminderEntry(getSelectedRecurringReminderEntry());
	}

	private void openReminderEntry(final RecurringReminderEntry record) {
		if (record == null) {
			return;
		}
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

	private void selectReminderNodeWithRetry(final RecurringReminderEntry record, final int attempt) {
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
						table.requestFocusInWindow();
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
}