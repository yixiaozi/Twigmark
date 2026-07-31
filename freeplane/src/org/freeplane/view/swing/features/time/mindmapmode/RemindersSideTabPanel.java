package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.IMapSelectionListener;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.MapChangeEvent;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;

/**
 * Left sidebar tab: all workspace reminders grouped by date, with search and
 * a live “next up” hint. Refresh is silent (no load dialogs / status spam).
 */
public class RemindersSideTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final int PAST_DAYS = 30;
	private static final int FUTURE_DAYS = 90;
	private static final int DEBOUNCE_MS = 450;
	private static final int CLOCK_MS = 15000;
	private static final int AUTO_SCAN_MS = 5 * 60 * 1000;
	private static final String CALENDAR_ACTION_KEY = "CalendarViewportAction";

	private static final class GroupLabel {
		final String text;
		final boolean overdue;

		GroupLabel(final String text, final boolean overdue) {
			this.text = text;
			this.overdue = overdue;
		}
	}

	private static final class Row {
		final ReminderCalendarBridge.OccurrenceRef ref;

		Row(final ReminderCalendarBridge.OccurrenceRef ref) {
			this.ref = ref;
		}
	}

	private final JTextField searchField = new JTextField();
	private final JLabel nextLabel = new JLabel(" ");
	private final JLabel countLabel = DocearUiTheme.mutedLabel(" ");
	private final JTree tree = new JTree(new DefaultMutableTreeNode("提醒"));
	private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm", Locale.CHINA);
	private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.CHINA);
	private final SimpleDateFormat dayFmt = new SimpleDateFormat("M月d日 E", Locale.CHINA);
	private final SimpleDateFormat dayKeyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

	private final List allRows = new ArrayList();
	private ReminderCalendarBridge.OccurrenceRef nextUp;
	private String selectedKey;
	private SwingWorker activeWorker;
	private boolean rescanRequested;
	private boolean forceInvalidate;
	private long lastScanAt;
	private final Timer debounceTimer;
	private final Timer clockTimer;
	private final Timer autoScanTimer;

	public RemindersSideTabPanel() {
		super(new BorderLayout(0, 0));
		DocearUiTheme.styleCanvas(this);
		setBorder(DocearUiTheme.pageBorder());

		debounceTimer = new Timer(DEBOUNCE_MS, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				debounceTimer.stop();
				refreshSilent(false);
			}
		});
		debounceTimer.setRepeats(false);

		clockTimer = new Timer(CLOCK_MS, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				updateNextBanner();
				tree.repaint();
			}
		});
		clockTimer.setRepeats(true);

		autoScanTimer = new Timer(AUTO_SCAN_MS, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				refreshSilent(false);
			}
		});
		autoScanTimer.setRepeats(true);

		buildUi();
		installListeners();
		clockTimer.start();
		autoScanTimer.start();
		refreshSilent(false);
	}

	private void buildUi() {
		add(buildHeader(), BorderLayout.NORTH);

		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.setRowHeight(0);
		tree.setBackground(DocearUiTheme.SURFACE);
		tree.setBorder(new EmptyBorder(2, 2, 2, 2));
		installArrowKeyNavigation();
		tree.setCellRenderer(new DefaultTreeCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getTreeCellRendererComponent(final JTree pTree, final Object value, final boolean sel,
					final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
				super.getTreeCellRendererComponent(pTree, value, sel, expanded, leaf, row, hasFocus);
				setOpenIcon(null);
				setClosedIcon(null);
				setLeafIcon(null);
				setBorder(new EmptyBorder(3, 2, 3, 2));
				final Object user = ((DefaultMutableTreeNode) value).getUserObject();
				if (user instanceof GroupLabel) {
					final GroupLabel group = (GroupLabel) user;
					setFont(DocearUiTheme.font(11.5f, Font.BOLD));
					setText(group.text);
					if (!sel) {
						setForeground(group.overdue ? DocearUiTheme.DANGER : DocearUiTheme.TEXT_MUTED);
					}
				}
				else if (user instanceof Row) {
					final Row rowObj = (Row) user;
					final ReminderCalendarBridge.OccurrenceRef ref = rowObj.ref;
					final String text = plain(ref.nodeText);
					final String mapName = ref.file == null ? "" : ref.file.getName();
					final String duration = ReminderTaskFormatter.formatDurationPadding(ref.taskTimeMinutes, 4);
					final String cycle = ref.recurring ? " ↻" : "";
					setFont(DocearUiTheme.font(12.5f));
					setText(timeFmt.format(new Date(ref.occurrenceAt)) + duration + " " + text + cycle
							+ (mapName.length() > 0 ? "  · " + mapName : ""));
					if (!sel) {
						if (isNextUp(ref)) {
							setForeground(DocearUiTheme.ACCENT_DEEP);
							setFont(DocearUiTheme.font(12.5f, Font.BOLD));
						}
						else if (ref.occurrenceAt < System.currentTimeMillis()) {
							setForeground(DocearUiTheme.WARNING);
						}
						else {
							setForeground(DocearUiTheme.TEXT);
						}
					}
				}
				return this;
			}
		});
		tree.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
					showContextMenu(e);
					return;
				}
				if (e.getClickCount() >= 1) {
					openSelected();
				}
			}

			public void mousePressed(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					showContextMenu(e);
				}
			}

			public void mouseReleased(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					showContextMenu(e);
				}
			}
		});

		final JScrollPane scroll = new JScrollPane(tree);
		DocearUiTheme.styleScrollPane(scroll);
		scroll.setBorder(DocearUiTheme.hairlineBorder());
		add(scroll, BorderLayout.CENTER);
	}

	private JPanel buildHeader() {
		final JPanel header = new JPanel(new BorderLayout(0, 6));
		header.setOpaque(false);

		DocearUiTheme.styleSearchField(searchField);
		searchField.setToolTipText("搜索提醒标题或导图名");
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(final DocumentEvent e) {
				rebuildTreeFromCache();
			}

			public void removeUpdate(final DocumentEvent e) {
				rebuildTreeFromCache();
			}

			public void changedUpdate(final DocumentEvent e) {
				rebuildTreeFromCache();
			}
		});

		final JPanel nextBand = new JPanel(new BorderLayout(0, 2));
		nextBand.setOpaque(true);
		nextBand.setBackground(DocearUiTheme.ACCENT_WASH);
		nextBand.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DocearUiTheme.ACCENT),
				new EmptyBorder(8, 10, 8, 10)));
		nextLabel.setFont(DocearUiTheme.font(12.5f));
		nextLabel.setForeground(DocearUiTheme.ACCENT_DEEP);
		nextBand.add(nextLabel, BorderLayout.CENTER);

		final JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		meta.setOpaque(false);
		meta.add(countLabel);

		final JPanel stack = new JPanel();
		stack.setOpaque(false);
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
		nextBand.setAlignmentX(Component.LEFT_ALIGNMENT);
		meta.setAlignmentX(Component.LEFT_ALIGNMENT);
		stack.add(searchField);
		stack.add(Box.createVerticalStrut(6));
		stack.add(nextBand);
		stack.add(Box.createVerticalStrut(4));
		stack.add(meta);

		header.add(stack, BorderLayout.CENTER);
		return header;
	}

	private void installListeners() {
		try {
			final ModeController modeController = Controller.getCurrentModeController();
			final MapController mapController = modeController.getMapController();
			mapController.addNodeChangeListener(new INodeChangeListener() {
				public void nodeChanged(final NodeChangeEvent event) {
					if (event == null || event.getNode() == null) {
						return;
					}
					final Object prop = event.getProperty();
					if (prop == ReminderExtension.class || prop == ReminderCycleExtension.class
							|| prop == ReminderTaskExtension.class) {
						scheduleSilentRefresh(true);
						return;
					}
					if (NodeModel.NODE_TEXT.equals(prop) && ReminderExtension.getExtension(event.getNode()) != null) {
						scheduleSilentRefresh(true);
					}
				}
			});
			mapController.addMapChangeListener(new IMapChangeListener() {
				public void mapChanged(final MapChangeEvent event) {
					scheduleSilentRefresh(true);
				}

				public void onNodeInserted(final NodeModel parent, final NodeModel child, final int newIndex) {
					if (child != null && ReminderExtension.getExtension(child) != null) {
						scheduleSilentRefresh(true);
					}
				}

				public void onNodeDeleted(final NodeModel parent, final NodeModel child, final int index) {
					scheduleSilentRefresh(true);
				}

				public void onNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
						final NodeModel child, final int newIndex) {
					if (child != null && ReminderExtension.getExtension(child) != null) {
						scheduleSilentRefresh(true);
					}
				}

				public void onPreNodeDelete(final NodeModel oldParent, final NodeModel selectedNode, final int index) {
				}

				public void onPreNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
						final NodeModel child, final int newIndex) {
				}
			});
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}

		final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
		mapViewManager.addMapSelectionListener(new IMapSelectionListener() {
			public void beforeMapChange(final MapModel oldMap, final MapModel newMap) {
			}

			public void afterMapChange(final MapModel oldMap, final MapModel newMap) {
				scheduleSilentRefresh(false);
			}
		});

		addHierarchyListener(new HierarchyListener() {
			public void hierarchyChanged(final HierarchyEvent e) {
				if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
					return;
				}
				if (isShowing() && System.currentTimeMillis() - lastScanAt > 30000L) {
					scheduleSilentRefresh(false);
				}
			}
		});
	}

	private void scheduleSilentRefresh(final boolean invalidate) {
		if (invalidate) {
			forceInvalidate = true;
		}
		debounceTimer.restart();
	}

	private synchronized void refreshSilent(final boolean force) {
		if (force) {
			forceInvalidate = true;
		}
		if (activeWorker != null && !activeWorker.isDone()) {
			rescanRequested = true;
			return;
		}
		final boolean invalidate = forceInvalidate;
		forceInvalidate = false;
		activeWorker = new SwingWorker() {
			protected Object doInBackground() throws Exception {
				if (invalidate) {
					ReminderCalendarBridge.invalidateReminderCache();
				}
				final long[] range = scanRange();
				return ReminderCalendarBridge.loadOccurrences(range[0], range[1]);
			}

			protected void done() {
				try {
					final Object result = get();
					allRows.clear();
					if (result instanceof List) {
						final List list = (List) result;
						for (int i = 0; i < list.size(); i++) {
							final Object item = list.get(i);
							if (item instanceof ReminderCalendarBridge.OccurrenceRef) {
								allRows.add(item);
							}
						}
					}
					Collections.sort(allRows, new Comparator() {
						public int compare(final Object o1, final Object o2) {
							final ReminderCalendarBridge.OccurrenceRef a = (ReminderCalendarBridge.OccurrenceRef) o1;
							final ReminderCalendarBridge.OccurrenceRef b = (ReminderCalendarBridge.OccurrenceRef) o2;
							return a.occurrenceAt < b.occurrenceAt ? -1
									: (a.occurrenceAt == b.occurrenceAt ? 0 : 1);
						}
					});
					lastScanAt = System.currentTimeMillis();
					rebuildTreeFromCache();
					SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_REMINDERS, allRows.size());
				}
				catch (Exception e) {
					LogUtils.warn(e);
				}
				finally {
					if (rescanRequested) {
						rescanRequested = false;
						refreshSilent(false);
					}
				}
			}
		};
		activeWorker.execute();
	}

	private long[] scanRange() {
		final Calendar start = Calendar.getInstance();
		start.set(Calendar.HOUR_OF_DAY, 0);
		start.set(Calendar.MINUTE, 0);
		start.set(Calendar.SECOND, 0);
		start.set(Calendar.MILLISECOND, 0);
		start.add(Calendar.DAY_OF_MONTH, -PAST_DAYS);
		final Calendar end = Calendar.getInstance();
		end.set(Calendar.HOUR_OF_DAY, 0);
		end.set(Calendar.MINUTE, 0);
		end.set(Calendar.SECOND, 0);
		end.set(Calendar.MILLISECOND, 0);
		end.add(Calendar.DAY_OF_MONTH, FUTURE_DAYS);
		return new long[] { start.getTimeInMillis(), end.getTimeInMillis() };
	}

	private void rebuildTreeFromCache() {
		selectedKey = getSelectedRowKey();
		final String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
		final List filtered = new ArrayList();
		for (int i = 0; i < allRows.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) allRows.get(i);
			if (matches(ref, query)) {
				filtered.add(ref);
			}
		}

		final long now = System.currentTimeMillis();
		final long todayStart = startOfDay(now);
		final long tomorrowStart = todayStart + 24L * 60L * 60L * 1000L;
		final long dayAfterStart = tomorrowStart + 24L * 60L * 60L * 1000L;

		final DefaultMutableTreeNode root = new DefaultMutableTreeNode("提醒");
		final Map groupNodes = new LinkedHashMap();
		DefaultMutableTreeNode restoreNode = null;

		for (int i = 0; i < filtered.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) filtered.get(i);
			final String groupKey;
			final String groupText;
			final boolean overdue;
			if (ref.occurrenceAt < todayStart) {
				groupKey = "overdue";
				groupText = "已过期";
				overdue = true;
			}
			else if (ref.occurrenceAt < tomorrowStart) {
				groupKey = "today";
				groupText = "今天  " + dayFmt.format(new Date(todayStart));
				overdue = false;
			}
			else if (ref.occurrenceAt < dayAfterStart) {
				groupKey = "tomorrow";
				groupText = "明天  " + dayFmt.format(new Date(tomorrowStart));
				overdue = false;
			}
			else {
				groupKey = dayKeyFmt.format(new Date(ref.occurrenceAt));
				groupText = dayFmt.format(new Date(ref.occurrenceAt));
				overdue = false;
			}
			DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) groupNodes.get(groupKey);
			if (groupNode == null) {
				groupNode = new DefaultMutableTreeNode(new GroupLabel(groupText, overdue));
				groupNodes.put(groupKey, groupNode);
				root.add(groupNode);
			}
			final DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(new Row(ref), false);
			groupNode.add(leaf);
			if (selectedKey != null && selectedKey.equals(rowKey(ref))) {
				restoreNode = leaf;
			}
		}

		final DefaultMutableTreeNode overdueNode = (DefaultMutableTreeNode) groupNodes.get("overdue");
		if (overdueNode != null) {
			final GroupLabel label = (GroupLabel) overdueNode.getUserObject();
			overdueNode.setUserObject(new GroupLabel(label.text + "  (" + overdueNode.getChildCount() + ")", true));
		}

		tree.setModel(new DefaultTreeModel(root));
		for (int i = 0; i < tree.getRowCount(); i++) {
			tree.expandRow(i);
		}
		if (restoreNode != null) {
			final TreePath path = new TreePath(restoreNode.getPath());
			tree.setSelectionPath(path);
			tree.scrollPathToVisible(path);
		}

		final String qHint = query.length() > 0 ? " · 筛选 " + filtered.size() : "";
		countLabel.setText("共 " + allRows.size() + " 条" + qHint);
		updateNextBannerFrom(filtered);
	}

	private void updateNextBanner() {
		final String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
		final List filtered = new ArrayList();
		for (int i = 0; i < allRows.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) allRows.get(i);
			if (matches(ref, query)) {
				filtered.add(ref);
			}
		}
		updateNextBannerFrom(filtered);
	}

	private void updateNextBannerFrom(final List filtered) {
		final long now = System.currentTimeMillis();
		nextUp = null;
		ReminderCalendarBridge.OccurrenceRef overdueNearest = null;
		for (int i = 0; i < filtered.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) filtered.get(i);
			if (ref.occurrenceAt >= now) {
				nextUp = ref;
				break;
			}
			overdueNearest = ref;
		}
		final String nowText = "现在 " + clockFmt.format(new Date(now));
		if (nextUp != null) {
			final long mins = Math.max(0L, (nextUp.occurrenceAt - now) / 60000L);
			final String eta = mins < 60 ? mins + " 分钟后"
					: (mins < 24 * 60 ? (mins / 60) + " 小时后" : dayFmt.format(new Date(nextUp.occurrenceAt)));
			nextLabel.setText("<html>" + nowText + " · 下一件 <b>" + timeFmt.format(new Date(nextUp.occurrenceAt))
					+ "</b> " + escape(plain(nextUp.nodeText)) + " <span style='color:#64748B'>(" + eta
					+ ")</span></html>");
		}
		else if (overdueNearest != null) {
			nextLabel.setText("<html>" + nowText + " · 最近逾期 <b>" + timeFmt.format(new Date(overdueNearest.occurrenceAt))
					+ "</b> " + escape(plain(overdueNearest.nodeText)) + "</html>");
			nextUp = overdueNearest;
		}
		else {
			nextLabel.setText(nowText + " · 暂无安排");
		}
	}

	private boolean isNextUp(final ReminderCalendarBridge.OccurrenceRef ref) {
		return nextUp != null && ref != null && rowKey(nextUp).equals(rowKey(ref));
	}

	private boolean matches(final ReminderCalendarBridge.OccurrenceRef ref, final String query) {
		if (query == null || query.length() == 0) {
			return true;
		}
		final String text = plain(ref.nodeText).toLowerCase(Locale.ROOT);
		final String map = ref.file == null ? "" : ref.file.getName().toLowerCase(Locale.ROOT);
		return text.contains(query) || map.contains(query);
	}

	private void showContextMenu(final MouseEvent e) {
		final TreePath path = tree.getPathForLocation(e.getX(), e.getY());
		if (path == null) {
			final JPopupMenu emptyMenu = new JPopupMenu();
			emptyMenu.add(menuItem("刷新", new Runnable() {
				public void run() {
					refreshSilent(true);
				}
			}));
			emptyMenu.add(menuItem("打开安排中心", new Runnable() {
				public void run() {
					openScheduleCenter();
				}
			}));
			emptyMenu.show(tree, e.getX(), e.getY());
			return;
		}
		tree.setSelectionPath(path);
		final DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
		final Object user = node.getUserObject();
		final JPopupMenu menu = new JPopupMenu();

		if (user instanceof Row) {
			final ReminderCalendarBridge.OccurrenceRef ref = ((Row) user).ref;
			menu.add(menuItem("打开节点", new Runnable() {
				public void run() {
					ReminderCalendarBridge.openNode(ref.file, ref.nodeId);
				}
			}));
			menu.add(menuItem("编辑安排", new Runnable() {
				public void run() {
					final Boolean ok = ReminderCalendarBridge.promptAndUpdateReminderTask(RemindersSideTabPanel.this,
							ref);
					if (Boolean.TRUE.equals(ok)) {
						scheduleSilentRefresh(true);
					}
				}
			}));
			menu.add(menuItem("打卡 / 完成", new Runnable() {
				public void run() {
					if (ReminderCalendarBridge.checkIn(ref.file, ref.nodeId, ref.occurrenceAt)) {
						scheduleSilentRefresh(true);
					}
				}
			}));
			menu.addSeparator();
			menu.add(menuItem("打开文件夹", new Runnable() {
				public void run() {
					openContainingFolder(ref.file);
				}
			}));
			menu.add(menuItem("复制", new Runnable() {
				public void run() {
					copyText(timeFmt.format(new Date(ref.occurrenceAt)) + " " + plain(ref.nodeText)
							+ (ref.file == null ? "" : " (" + ref.file.getName() + ")"));
				}
			}));
		}
		else {
			menu.add(menuItem("复制分组", new Runnable() {
				public void run() {
					copyGroup(node);
				}
			}));
		}
		menu.addSeparator();
		menu.add(menuItem("刷新", new Runnable() {
			public void run() {
				refreshSilent(true);
			}
		}));
		menu.add(menuItem("打开安排中心", new Runnable() {
			public void run() {
				openScheduleCenter();
			}
		}));
		menu.show(tree, e.getX(), e.getY());
	}

	private JMenuItem menuItem(final String text, final Runnable action) {
		final JMenuItem item = new JMenuItem(text);
		item.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				action.run();
			}
		});
		return item;
	}

	private void openSelected() {
		final TreePath path = tree.getSelectionPath();
		if (path == null) {
			return;
		}
		final Object user = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (!(user instanceof Row)) {
			return;
		}
		final ReminderCalendarBridge.OccurrenceRef ref = ((Row) user).ref;
		ReminderCalendarBridge.openNode(ref.file, ref.nodeId);
	}

	private void openScheduleCenter() {
		try {
			final AFreeplaneAction action = Controller.getCurrentModeController().getAction(CALENDAR_ACTION_KEY);
			if (action != null) {
				action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, CALENDAR_ACTION_KEY));
			}
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
	}

	private void openContainingFolder(final File file) {
		if (file == null || !file.exists()) {
			return;
		}
		final File parent = file.getParentFile();
		if (parent == null || !parent.isDirectory()) {
			return;
		}
		try {
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(parent);
			}
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
	}

	private void copyGroup(final DefaultMutableTreeNode node) {
		final List lines = new ArrayList();
		collectLines(node, lines);
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				sb.append('\n');
			}
			sb.append(lines.get(i));
		}
		copyText(sb.toString());
	}

	private void collectLines(final DefaultMutableTreeNode node, final List out) {
		final Object user = node.getUserObject();
		if (user instanceof Row) {
			final ReminderCalendarBridge.OccurrenceRef ref = ((Row) user).ref;
			out.add(timeFmt.format(new Date(ref.occurrenceAt)) + " " + plain(ref.nodeText)
					+ (ref.file == null ? "" : " (" + ref.file.getName() + ")"));
			return;
		}
		if (user instanceof GroupLabel) {
			out.add(((GroupLabel) user).text);
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			collectLines((DefaultMutableTreeNode) node.getChildAt(i), out);
		}
	}

	private void copyText(final String text) {
		if (text == null || text.length() == 0) {
			return;
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}

	private String getSelectedRowKey() {
		final TreePath path = tree.getSelectionPath();
		if (path == null) {
			return null;
		}
		final Object user = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (!(user instanceof Row)) {
			return null;
		}
		return rowKey(((Row) user).ref);
	}

	private String rowKey(final ReminderCalendarBridge.OccurrenceRef ref) {
		if (ref == null) {
			return "";
		}
		final String path = ref.file == null ? "" : ref.file.getAbsolutePath();
		return path + "|" + ref.nodeId + "|" + ref.occurrenceAt;
	}

	private long startOfDay(final long millis) {
		final Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(millis);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTimeInMillis();
	}

	private static String plain(final String text) {
		if (text == null) {
			return "";
		}
		return HtmlUtils.htmlToPlain(text).replaceAll("\\s+", " ").trim();
	}

	private static String escape(final String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void installArrowKeyNavigation() {
		tree.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), "reminders.up");
		tree.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "reminders.down");
		tree.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "reminders.open");
		tree.getActionMap().put("reminders.up", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent e) {
				final int row = tree.getLeadSelectionRow();
				if (row <= 0) {
					return;
				}
				tree.setSelectionRow(row - 1);
				tree.scrollRowToVisible(row - 1);
			}
		});
		tree.getActionMap().put("reminders.down", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent e) {
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
		tree.getActionMap().put("reminders.open", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent e) {
				openSelected();
			}
		});
	}
}
