package org.freeplane.view.swing.features.pomodoro;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.DefaultListModel;
import org.freeplane.core.ui.components.DateTimeFieldsPanel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.HtmlUtils;
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
import org.freeplane.features.text.TextController;
import org.freeplane.features.ui.IMapViewManager;

/**
 * Right sidebar: node tree or today timeline, stats, and session controls.
 */
public final class PomodoroTabPanel extends JPanel implements PomodoroSessionManager.Listener {
	private static final long serialVersionUID = 1L;

	private final ModeController modeController;
	private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("番茄钟");
	private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
	private final JTree tree = new JTree(treeModel);
	private final DefaultListModel todayModel = new DefaultListModel();
	private final JList todayList = new JList(todayModel);
	private final JScrollPane treeScroll = new JScrollPane(tree);
	private final JScrollPane todayScroll = new JScrollPane(todayList);
	private final JPanel centerHost = new JPanel(new BorderLayout());
	private final JLabel statsLabel = new JLabel(" ");
	private final JToggleButton currentMapButton = new JToggleButton("当前导图");
	private final JToggleButton allMapsButton = new JToggleButton("全部", true);
	private final JToggleButton nodesViewButton = new JToggleButton("节点");
	private final JToggleButton todayViewButton = new JToggleButton("时间", true);
	private final DateTimeFieldsPanel dayPicker;
	private final JButton todayResetButton = new JButton("回到今天");
	private boolean showAllMaps = true;
	private boolean todayView = true;
	private long selectedDayStart = PomodoroLog.startOfToday();
	private boolean reloadQueued;

	public PomodoroTabPanel(final ModeController modeController) {
		super(new BorderLayout(8, 8));
		this.modeController = modeController;
		DocearUiTheme.styleCanvas(this);
		setBorder(DocearUiTheme.pageBorder());
		DocearUiTheme.styleScrollPane(treeScroll);
		DocearUiTheme.styleScrollPane(todayScroll);

		final JPanel top = new JPanel(new BorderLayout(2, 2));
		top.setOpaque(false);
		final JPanel modeBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		modeBar.setOpaque(false);
		currentMapButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				showAllMaps = false;
				currentMapButton.setSelected(true);
				allMapsButton.setSelected(false);
				reload();
			}
		});
		allMapsButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				showAllMaps = true;
				allMapsButton.setSelected(true);
				currentMapButton.setSelected(false);
				reload();
			}
		});
		nodesViewButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				todayView = false;
				nodesViewButton.setSelected(true);
				todayViewButton.setSelected(false);
				updateDayControlsVisible();
				showCenter();
				reload();
			}
		});
		todayViewButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				todayView = true;
				todayViewButton.setSelected(true);
				nodesViewButton.setSelected(false);
				updateDayControlsVisible();
				showCenter();
				reload();
			}
		});
		dayPicker = new DateTimeFieldsPanel(false, new Date(selectedDayStart));
		dayPicker.setToolTipText("选择查看的日期");
		dayPicker.setChangeListener(new PropertyChangeListener() {
			public void propertyChange(final PropertyChangeEvent evt) {
				selectedDayStart = PomodoroLog.startOfDay(dayPicker.getTimeMillis());
				if (todayView) {
					reload();
				}
			}
		});
		todayResetButton.setToolTipText("跳到今天");
		todayResetButton.setFocusable(false);
		todayResetButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				selectedDayStart = PomodoroLog.startOfToday();
				dayPicker.setDate(new Date(selectedDayStart));
				reload();
			}
		});
		modeBar.add(currentMapButton);
		modeBar.add(allMapsButton);
		modeBar.add(nodesViewButton);
		modeBar.add(todayViewButton);
		modeBar.add(dayPicker);
		modeBar.add(todayResetButton);
		updateDayControlsVisible();
		top.add(modeBar, BorderLayout.NORTH);

		statsLabel.setFont(DocearUiTheme.font(11f));
		statsLabel.setForeground(DocearUiTheme.TEXT_MUTED);
		statsLabel.setBorder(new EmptyBorder(2, 6, 4, 6));
		top.add(statsLabel, BorderLayout.CENTER);

		final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
		actions.add(btn("开始", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				startSelectedOrFocused();
			}
		}));
		actions.add(btn("暂停", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
				final NodeModel node = selectedActionNode();
				if (manager != null && node != null) {
					manager.pause(node);
				}
			}
		}));
		actions.add(btn("结束", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
				final NodeModel node = selectedActionNode();
				if (manager != null && node != null) {
					manager.stop(node);
				}
			}
		}));
		actions.add(btn("开关", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = Controller.getCurrentController().getSelection().getSelected();
				if (node != null) {
					PomodoroAttributes.toggleEnabled(node);
					reload();
				}
			}
		}));
		actions.add(btn("小窗", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
				if (manager != null) {
					manager.showWindow();
				}
			}
		}));
		actions.add(btn("历史", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = selectedActionNode();
				if (node != null) {
					PomodoroHistoryDialog.showForNode(node);
				}
			}
		}));
		actions.add(btn("修改", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				editSelectedSession();
			}
		}));
		actions.add(btn("删除", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				deleteSelectedSession();
			}
		}));
		actions.add(btn("导出", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				PomodoroExport.exportInteractive(showAllMaps);
			}
		}));
		top.add(actions, BorderLayout.SOUTH);
		add(top, BorderLayout.NORTH);

		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.setRowHeight(22);
		tree.setCellRenderer(new DefaultTreeCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean sel,
					final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
				super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
				if (value instanceof DefaultMutableTreeNode) {
					final Object user = ((DefaultMutableTreeNode) value).getUserObject();
					if (user instanceof TreeEntry) {
						final TreeEntry entry = (TreeEntry) user;
						setText(entry.label);
						if (entry.running) {
							setForeground(sel ? getTextSelectionColor() : DocearUiTheme.WARNING);
						}
					}
				}
				return this;
			}
		});
		tree.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
				final NodeModel node = selectedMindMapNode();
				if (manager == null || node == null) {
					return;
				}
				manager.navigateTo(node);
				if (e.getClickCount() >= 2) {
					PomodoroHistoryDialog.showForNode(node);
				}
			}
		});

		todayList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		todayList.setFont(DocearUiTheme.font(12f));
		todayList.setFixedCellHeight(24);
		todayList.setCellRenderer(new PomodoroTodayRowRenderer());
		todayList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				final Object v = todayList.getSelectedValue();
				if (!(v instanceof PomodoroTodayEntry)) {
					return;
				}
				final PomodoroTodayEntry entry = (PomodoroTodayEntry) v;
				final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
				if (manager != null) {
					manager.navigateTo(entry.node);
				}
				if (e.getClickCount() >= 2) {
					PomodoroHistoryDialog.showForNode(entry.node);
				}
			}
		});

		centerHost.add(treeScroll, BorderLayout.CENTER);
		add(centerHost, BorderLayout.CENTER);
		addListeners();
		showCenter();
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		if (manager != null) {
			manager.addListener(this);
		}
		reload();
	}

	private void showCenter() {
		centerHost.removeAll();
		centerHost.add(todayView ? todayScroll : treeScroll, BorderLayout.CENTER);
		centerHost.revalidate();
		centerHost.repaint();
	}

	private void updateDayControlsVisible() {
		dayPicker.setVisible(todayView);
		todayResetButton.setVisible(todayView);
	}

	private static JButton btn(final String text, final ActionListener listener) {
		final JButton b = DocearUiTheme.softButton(text);
		b.addActionListener(listener);
		return b;
	}

	private void startSelectedOrFocused() {
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		if (manager == null) {
			return;
		}
		NodeModel node = selectedActionNode();
		if (node == null) {
			node = manager.getActiveSessionNode();
		}
		if (node == null) {
			node = Controller.getCurrentController().getSelection().getSelected();
		}
		if (node != null) {
			manager.start(node);
		}
	}

	private NodeModel selectedActionNode() {
		if (todayView) {
			final Object v = todayList.getSelectedValue();
			return v instanceof PomodoroTodayEntry ? ((PomodoroTodayEntry) v).node : null;
		}
		return selectedMindMapNode();
	}

	private NodeModel selectedMindMapNode() {
		final TreePath path = tree.getSelectionPath();
		if (path == null) {
			return null;
		}
		final Object last = path.getLastPathComponent();
		if (!(last instanceof DefaultMutableTreeNode)) {
			return null;
		}
		final Object user = ((DefaultMutableTreeNode) last).getUserObject();
		return user instanceof TreeEntry ? ((TreeEntry) user).node : null;
	}

	private PomodoroTodayEntry selectedTodayEntry() {
		final Object v = todayList.getSelectedValue();
		return v instanceof PomodoroTodayEntry ? (PomodoroTodayEntry) v : null;
	}

	private void editSelectedSession() {
		final PomodoroTodayEntry entry = selectedTodayEntry();
		if (entry == null) {
			javax.swing.JOptionPane.showMessageDialog(this, "请先在「今日」列表中选择一条记录", "修改番茄钟",
			        javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (entry.live || entry.recordIndex < 0) {
			javax.swing.JOptionPane.showMessageDialog(this, "进行中的会话请先「结束」，再修改历史记录", "修改番茄钟",
			        javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final PomodoroExtension ext = PomodoroAttributes.read(entry.node);
		if (ext == null) {
			return;
		}
		final PomodoroSessionRecord record = PomodoroLog.getRecord(ext.getLog(), entry.recordIndex);
		if (record == null) {
			reload();
			return;
		}
		if (PomodoroSessionEditDialog.showForRecord(PomodoroSessionEditDialog.ownerFrame(), entry.node,
		        entry.recordIndex, record)) {
			reload();
		}
	}

	private void deleteSelectedSession() {
		final PomodoroTodayEntry entry = selectedTodayEntry();
		if (entry == null) {
			javax.swing.JOptionPane.showMessageDialog(this, "请先在「今日」列表中选择一条记录", "删除番茄钟",
			        javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (entry.live || entry.recordIndex < 0) {
			javax.swing.JOptionPane.showMessageDialog(this, "进行中的会话请用「结束」停止，不能直接删除", "删除番茄钟",
			        javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final int confirm = javax.swing.JOptionPane.showConfirmDialog(this,
		        "确定删除这条今日记录吗？\n" + entry.label, "删除番茄钟", javax.swing.JOptionPane.YES_NO_OPTION,
		        javax.swing.JOptionPane.WARNING_MESSAGE);
		if (confirm != javax.swing.JOptionPane.YES_OPTION) {
			return;
		}
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		if (manager != null) {
			manager.deleteLogRecord(entry.node, entry.recordIndex);
		}
		reload();
	}

	private void addListeners() {
		final MapController mapController = modeController.getMapController();
		mapController.addNodeChangeListener(new INodeChangeListener() {
			public void nodeChanged(final NodeChangeEvent event) {
				queueReload();
			}
		});
		mapController.addMapChangeListener(new IMapChangeListener() {
			public void mapChanged(final MapChangeEvent event) {
				queueReload();
			}

			public void onNodeInserted(final NodeModel parent, final NodeModel child, final int newIndex) {
				queueReload();
			}

			public void onNodeDeleted(final NodeModel parent, final NodeModel child, final int index) {
				queueReload();
			}

			public void onNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
					final NodeModel child, final int newIndex) {
				queueReload();
			}

			public void onPreNodeDelete(final NodeModel oldParent, final NodeModel selectedNode, final int index) {
			}

			public void onPreNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
					final NodeModel child, final int newIndex) {
			}
		});
		Controller.getCurrentController().getMapViewManager().addMapSelectionListener(new IMapSelectionListener() {
			public void beforeMapChange(final MapModel oldMap, final MapModel newMap) {
			}

			public void afterMapChange(final MapModel oldMap, final MapModel newMap) {
				reload();
			}
		});
	}

	private void queueReload() {
		if (reloadQueued) {
			return;
		}
		reloadQueued = true;
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				reloadQueued = false;
				reload();
			}
		});
	}

	public void pomodoroSessionsChanged() {
		queueReload();
	}

	private void reload() {
		try {
			reloadImpl();
		}
		catch (Exception e) {
			org.freeplane.core.util.LogUtils.warn("Pomodoro sidebar reload failed", e);
		}
	}

	private void reloadImpl() {
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		final long now = System.currentTimeMillis();
		final List nodes = manager == null ? java.util.Collections.EMPTY_LIST
				: (showAllMaps ? manager.listOpenPomodoroNodes() : manager.listCurrentMapPomodoroNodes());

		if (todayView) {
			todayModel.clear();
			final List today = PomodoroTodayEntry.collect(nodes, now, selectedDayStart);
			for (int i = 0; i < today.size(); i++) {
				todayModel.addElement(today.get(i));
			}
			SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_POMODORO, today.size());
		}
		else {
			final Enumeration expanded = tree.getExpandedDescendants(new TreePath(rootNode.getPath()));
			rootNode.removeAllChildren();
			int count = 0;
			if (showAllMaps) {
				final IMapViewManager views = Controller.getCurrentController().getMapViewManager();
				final java.util.Map maps = views.getMaps(modeController.getModeName());
				if (maps != null) {
					final Iterator it = maps.values().iterator();
					while (it.hasNext()) {
						final Object value = it.next();
						if (value instanceof MapModel) {
							count += appendMap((MapModel) value, now, true);
						}
					}
				}
			}
			else {
				final MapModel map = Controller.getCurrentController().getMap();
				if (map != null) {
					count += appendMap(map, now, false);
				}
			}
			treeModel.reload();
			expandAll();
			restoreExpanded(expanded);
			SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_POMODORO, count);
		}

		if (manager == null) {
			statsLabel.setText("番茄钟未就绪");
			return;
		}
		final long[] stats = manager.computeStats(showAllMaps);
		final String scope = showAllMaps ? "全部已打开" : "当前导图";
		if (todayView) {
			final boolean isToday = selectedDayStart == PomodoroLog.startOfToday();
			final String dayLabel = isToday ? "今日" : new java.text.SimpleDateFormat("MM-dd", java.util.Locale.CHINA)
					.format(new Date(selectedDayStart));
			statsLabel.setText(scope + " · " + dayLabel + " " + todayModel.size() + " 段"
					+ (isToday ? " · 进行中 " + stats[4] : ""));
		}
		else {
			statsLabel.setText(scope + " · 今日 " + PomodoroFormatter.formatDuration(stats[0]) + " · 本周 "
					+ PomodoroFormatter.formatDuration(stats[1]) + " · 累计 "
					+ PomodoroFormatter.formatDuration(stats[2]) + " · " + stats[3] + " 节点 · 进行中 " + stats[4]
					+ " · 已暂停 " + stats[5]);
		}
	}

	private void expandAll() {
		for (int i = 0; i < tree.getRowCount(); i++) {
			tree.expandRow(i);
		}
	}

	private void restoreExpanded(final Enumeration expanded) {
	}

	private int appendMap(final MapModel map, final long now, final boolean withMapHeader) {
		if (map == null || map.getRootNode() == null) {
			return 0;
		}
		final DefaultMutableTreeNode mapRoot;
		if (withMapHeader) {
			final String name = map.getFile() != null ? map.getFile().getName() : "未命名";
			final long[] mapStats = statsForMap(map, now);
			mapRoot = new DefaultMutableTreeNode(new TreeEntry(null,
					"▸ " + name + "  [" + PomodoroFormatter.formatDuration(mapStats[0]) + " 今日 · " + mapStats[1]
							+ " 节点]",
					false, false));
			rootNode.add(mapRoot);
		}
		else {
			mapRoot = rootNode;
		}
		return appendNodeTree(map.getRootNode(), mapRoot, now);
	}

	private static long[] statsForMap(final MapModel map, final long now) {
		long today = 0L;
		int enabled = 0;
		final long todayStart = PomodoroLog.startOfToday();
		final List bucket = new java.util.ArrayList();
		collectEnabled(map.getRootNode(), bucket);
		for (int i = 0; i < bucket.size(); i++) {
			final NodeModel node = (NodeModel) bucket.get(i);
			final PomodoroExtension ext = PomodoroAttributes.read(node);
			if (ext == null || !ext.isEnabled()) {
				continue;
			}
			enabled++;
			today += PomodoroLog.sumFocusSince(PomodoroLog.decode(ext.getLog()), todayStart);
			if (ext.liveSegmentMs(now) > 0) {
				final long anchor = ext.getSessionAt() > 0 ? ext.getSessionAt() : ext.getStartedAt();
				if (anchor >= todayStart) {
					today += ext.liveSegmentMs(now);
				}
			}
		}
		return new long[] { today, enabled };
	}

	private static void collectEnabled(final NodeModel node, final List out) {
		final PomodoroExtension ext = PomodoroAttributes.read(node);
		if (ext != null && ext.isEnabled()) {
			out.add(node);
		}
		final List children = node.getChildren();
		if (children != null) {
			for (int i = 0; i < children.size(); i++) {
				collectEnabled((NodeModel) children.get(i), out);
			}
		}
	}

	private int appendNodeTree(final NodeModel node, final DefaultMutableTreeNode parent, final long now) {
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		final boolean enabled = ext != null && ext.isEnabled();
		final DefaultMutableTreeNode holder = new DefaultMutableTreeNode();
		int childCount = 0;
		final List children = node.getChildren();
		if (children != null) {
			for (int i = 0; i < children.size(); i++) {
				childCount += appendNodeTree((NodeModel) children.get(i), holder, now);
			}
		}
		if (!enabled && childCount == 0) {
			return 0;
		}
		if (enabled) {
			final boolean running = PomodoroExtension.STATE_RUNNING.equals(ext.getState());
			holder.setUserObject(new TreeEntry(node, formatLabel(node, now, ext), true, running));
		}
		else {
			holder.setUserObject(new TreeEntry(null, "▹ " + plain(node), false, false));
		}
		parent.add(holder);
		return (enabled ? 1 : 0) + childCount;
	}

	private static String formatLabel(final NodeModel node, final long now, final PomodoroExtension ext) {
		String mark = "·";
		if (PomodoroExtension.STATE_RUNNING.equals(ext.getState())) {
			mark = "▶";
		}
		else if (PomodoroExtension.STATE_PAUSED.equals(ext.getState())) {
			mark = "❚❚";
		}
		final long self = ext.liveTotalMs(now);
		final long subtree = PomodoroTotals.subtreeMs(node, now);
		String time = PomodoroFormatter.formatDuration(self);
		if (subtree > self) {
			time += " · Σ" + PomodoroFormatter.formatDuration(subtree);
		}
		final int sessions = ext.sessionCount();
		if (sessions > 0) {
			time += " · " + sessions + "次";
		}
		return mark + " " + plain(node) + "  [" + time + "]";
	}

	private static String plain(final NodeModel node) {
		try {
			final String text = TextController.getController().getPlainTextContent(node);
			if (text != null) {
				return HtmlUtils.htmlToPlain(text).replaceAll("\\s+", " ").trim();
			}
		}
		catch (Exception e) {
		}
		return node.getText() == null ? "" : HtmlUtils.htmlToPlain(node.getText());
	}

	/**
	 * Fixed columns so task titles share one left edge; pause stays on the right.
	 */
	private static final class PomodoroTodayRowRenderer extends JPanel implements ListCellRenderer {
		private static final long serialVersionUID = 1L;
		private static final int TIME_W = 108;
		private static final int DUR_W = 52;
		private final JLabel timeLabel = new JLabel();
		private final JLabel durationLabel = new JLabel();
		private final JLabel titleLabel = new JLabel();
		private final JLabel pauseLabel = new JLabel();

		PomodoroTodayRowRenderer() {
			super(new GridBagLayout());
			setOpaque(true);
			setBorder(new EmptyBorder(1, 4, 1, 4));
			final Font font = DocearUiTheme.font(12f);
			timeLabel.setFont(font);
			durationLabel.setFont(font);
			titleLabel.setFont(font);
			pauseLabel.setFont(font);
			final GridBagConstraints c = new GridBagConstraints();
			c.gridy = 0;
			c.insets = new Insets(0, 0, 0, 8);
			c.anchor = GridBagConstraints.WEST;
			c.fill = GridBagConstraints.NONE;
			c.gridx = 0;
			c.weightx = 0;
			timeLabel.setPreferredSize(new Dimension(TIME_W, 18));
			timeLabel.setMinimumSize(new Dimension(TIME_W, 18));
			add(timeLabel, c);
			c.gridx = 1;
			durationLabel.setPreferredSize(new Dimension(DUR_W, 18));
			durationLabel.setMinimumSize(new Dimension(DUR_W, 18));
			add(durationLabel, c);
			c.gridx = 2;
			c.weightx = 1;
			c.fill = GridBagConstraints.HORIZONTAL;
			add(titleLabel, c);
			c.gridx = 3;
			c.weightx = 0;
			c.fill = GridBagConstraints.NONE;
			c.insets = new Insets(0, 8, 0, 0);
			add(pauseLabel, c);
		}

		public Component getListCellRendererComponent(final JList list, final Object value, final int index,
				final boolean isSelected, final boolean cellHasFocus) {
			if (value instanceof PomodoroTodayEntry) {
				final PomodoroTodayEntry entry = (PomodoroTodayEntry) value;
				timeLabel.setText(entry.timeText);
				durationLabel.setText(entry.durationText);
				titleLabel.setText(entry.titleText);
				pauseLabel.setText(entry.pauseText);
				final Color fg = entry.live && !isSelected ? DocearUiTheme.WARNING
						: (isSelected ? list.getSelectionForeground() : list.getForeground());
				timeLabel.setForeground(fg);
				durationLabel.setForeground(isSelected ? fg : DocearUiTheme.TEXT_MUTED);
				titleLabel.setForeground(fg);
				pauseLabel.setForeground(isSelected ? fg : DocearUiTheme.TEXT_MUTED);
			}
			else {
				timeLabel.setText("");
				durationLabel.setText("");
				titleLabel.setText(value == null ? "" : value.toString());
				pauseLabel.setText("");
			}
			setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
			return this;
		}
	}

	private static final class TreeEntry {
		final NodeModel node;
		final String label;
		final boolean selectable;
		final boolean running;

		TreeEntry(final NodeModel node, final String label, final boolean selectable, final boolean running) {
			this.node = node;
			this.label = label;
			this.selectable = selectable;
			this.running = running;
		}

		public String toString() {
			return label;
		}
	}
}
