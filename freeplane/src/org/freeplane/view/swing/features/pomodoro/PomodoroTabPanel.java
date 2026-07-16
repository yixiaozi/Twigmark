package org.freeplane.view.swing.features.pomodoro;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

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
 * Right sidebar with real tree hierarchy, stats, and session controls.
 */
public final class PomodoroTabPanel extends JPanel implements PomodoroSessionManager.Listener {
	private static final long serialVersionUID = 1L;

	private final ModeController modeController;
	private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("番茄钟");
	private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
	private final JTree tree = new JTree(treeModel);
	private final JLabel statsLabel = new JLabel(" ");
	private final JToggleButton currentMapButton = new JToggleButton("当前导图", true);
	private final JToggleButton allMapsButton = new JToggleButton("全部");
	private boolean showAllMaps;
	private boolean reloadQueued;

	public PomodoroTabPanel(final ModeController modeController) {
		super(new BorderLayout(4, 4));
		this.modeController = modeController;
		setBorder(new EmptyBorder(4, 4, 4, 4));

		final JPanel top = new JPanel(new BorderLayout(2, 2));
		final JPanel modeBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
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
		modeBar.add(currentMapButton);
		modeBar.add(allMapsButton);
		top.add(modeBar, BorderLayout.NORTH);

		statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
		statsLabel.setForeground(new Color(0x6B5E54));
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
				final NodeModel node = selectedMindMapNode();
				if (node != null) {
					PomodoroSessionManager.getInstance().pause(node);
				}
			}
		}));
		actions.add(btn("结束", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = selectedMindMapNode();
				if (node != null) {
					PomodoroSessionManager.getInstance().stop(node);
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
				PomodoroSessionManager.getInstance().showWindow();
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
							setForeground(sel ? getTextSelectionColor() : new Color(0xC45C26));
						}
					}
				}
				return this;
			}
		});
		tree.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				final NodeModel node = selectedMindMapNode();
				if (node == null) {
					return;
				}
				PomodoroSessionManager.getInstance().navigateTo(node);
				if (e.getClickCount() >= 2) {
					PomodoroSessionManager.getInstance().start(node);
				}
			}
		});
		add(new JScrollPane(tree), BorderLayout.CENTER);
		addListeners();
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		if (manager != null) {
			manager.addListener(this);
		}
		reload();
	}

	private static JButton btn(final String text, final ActionListener listener) {
		final JButton b = new JButton(text);
		b.addActionListener(listener);
		return b;
	}

	private void startSelectedOrFocused() {
		NodeModel node = selectedMindMapNode();
		if (node == null) {
			node = Controller.getCurrentController().getSelection().getSelected();
		}
		if (node != null) {
			PomodoroSessionManager.getInstance().start(node);
		}
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
				if (newMap != null && newMap.getRootNode() != null) {
					final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
					if (manager != null) {
						manager.recoverStaleRunning(newMap.getRootNode());
					}
				}
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
		final Enumeration expanded = tree.getExpandedDescendants(new TreePath(rootNode.getPath()));
		rootNode.removeAllChildren();
		final long now = System.currentTimeMillis();
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
		final long[] stats = PomodoroSessionManager.getInstance().computeStats(showAllMaps);
		statsLabel.setText("今日 " + PomodoroFormatter.formatDuration(stats[0]) + " · 本周 "
				+ PomodoroFormatter.formatDuration(stats[1]) + " · 累计 "
				+ PomodoroFormatter.formatDuration(stats[2]) + " · " + stats[3] + " 个节点");
	}

	private void expandAll() {
		for (int i = 0; i < tree.getRowCount(); i++) {
			tree.expandRow(i);
		}
	}

	private void restoreExpanded(final Enumeration expanded) {
		// Best-effort: already expand-all for usability with moderate trees.
	}

	private int appendMap(final MapModel map, final long now, final boolean withMapHeader) {
		if (map == null || map.getRootNode() == null) {
			return 0;
		}
		final DefaultMutableTreeNode mapRoot;
		if (withMapHeader) {
			final String name = map.getFile() != null ? map.getFile().getName() : "未命名";
			mapRoot = new DefaultMutableTreeNode(new TreeEntry(null, "▸ " + name, false, false));
			rootNode.add(mapRoot);
		}
		else {
			mapRoot = rootNode;
		}
		return appendNodeTree(map.getRootNode(), mapRoot, now);
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
