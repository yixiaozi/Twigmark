package org.freeplane.view.swing.features.pomodoro;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;

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
 * Right sidebar: hierarchical pomodoro times for current map or all open maps.
 */
public final class PomodoroTabPanel extends JPanel implements PomodoroSessionManager.Listener {
	private static final long serialVersionUID = 1L;

	private final ModeController modeController;
	private final DefaultListModel items = new DefaultListModel();
	private final JList list = new JList(items);
	private final JToggleButton currentMapButton = new JToggleButton("当前导图", true);
	private final JToggleButton allMapsButton = new JToggleButton("全部");
	private boolean showAllMaps;

	public PomodoroTabPanel(final ModeController modeController) {
		super(new BorderLayout());
		this.modeController = modeController;
		final JPanel top = new JPanel(new BorderLayout());
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

		final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		actions.add(actionButton("开始", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				startSelectedOrFocused();
			}
		}));
		actions.add(actionButton("暂停", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = selectedNode();
				if (node != null) {
					PomodoroSessionManager.getInstance().pause(node);
				}
			}
		}));
		actions.add(actionButton("结束", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = selectedNode();
				if (node != null) {
					PomodoroSessionManager.getInstance().stop(node);
				}
			}
		}));
		actions.add(actionButton("开关", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final NodeModel node = Controller.getCurrentController().getSelection().getSelected();
				if (node != null) {
					PomodoroAttributes.toggleEnabled(node);
					reload();
				}
			}
		}));
		actions.add(actionButton("小窗", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				PomodoroSessionManager.getInstance().showWindow();
			}
		}));
		top.add(actions, BorderLayout.SOUTH);
		add(top, BorderLayout.NORTH);

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getListCellRendererComponent(final JList list, final Object value, final int index,
					final boolean isSelected, final boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof Row) {
					setText(((Row) value).label);
				}
				return this;
			}
		});
		list.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				final NodeModel node = selectedNode();
				if (node == null) {
					return;
				}
				PomodoroSessionManager.getInstance().navigateTo(node);
				if (e.getClickCount() >= 2) {
					PomodoroSessionManager.getInstance().start(node);
				}
			}
		});
		add(new JScrollPane(list), BorderLayout.CENTER);
		addListeners();
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		if (manager != null) {
			manager.addListener(this);
		}
		reload();
	}

	private static JButton actionButton(final String text, final ActionListener listener) {
		final JButton b = new JButton(text);
		b.addActionListener(listener);
		return b;
	}

	private void startSelectedOrFocused() {
		NodeModel node = selectedNode();
		if (node == null) {
			node = Controller.getCurrentController().getSelection().getSelected();
		}
		if (node != null) {
			PomodoroSessionManager.getInstance().start(node);
		}
	}

	private NodeModel selectedNode() {
		final Object value = list.getSelectedValue();
		return value instanceof Row ? ((Row) value).node : null;
	}

	private void addListeners() {
		final MapController mapController = modeController.getMapController();
		mapController.addNodeChangeListener(new INodeChangeListener() {
			public void nodeChanged(final NodeChangeEvent event) {
				reload();
			}
		});
		mapController.addMapChangeListener(new IMapChangeListener() {
			public void mapChanged(final MapChangeEvent event) {
				reload();
			}

			public void onNodeInserted(final NodeModel parent, final NodeModel child, final int newIndex) {
				reload();
			}

			public void onNodeDeleted(final NodeModel parent, final NodeModel child, final int index) {
				reload();
			}

			public void onNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
					final NodeModel child, final int newIndex) {
				reload();
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

	public void pomodoroSessionsChanged() {
		reload();
	}

	private void reload() {
		items.clear();
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
						count += appendMapTree((MapModel) value, now, true);
					}
				}
			}
		}
		else {
			final MapModel map = Controller.getCurrentController().getMap();
			if (map != null) {
				count += appendMapTree(map, now, false);
			}
		}
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_POMODORO, count);
	}

	private int appendMapTree(final MapModel map, final long now, final boolean withMapHeader) {
		if (map == null || map.getRootNode() == null) {
			return 0;
		}
		final List rows = new ArrayList();
		collectRows(map.getRootNode(), 0, now, rows);
		if (rows.isEmpty()) {
			return 0;
		}
		if (withMapHeader) {
			final String name = map.getFile() != null ? map.getFile().getName() : "未命名";
			items.addElement(new Row(null, "▸ " + name, true));
		}
		for (int i = 0; i < rows.size(); i++) {
			items.addElement(rows.get(i));
		}
		return rows.size();
	}

	private void collectRows(final NodeModel node, final int depth, final long now, final List out) {
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		final boolean enabled = ext != null && ext.isEnabled();
		final List children = node.getChildren();
		final List childRows = new ArrayList();
		if (children != null) {
			for (int i = 0; i < children.size(); i++) {
				collectRows((NodeModel) children.get(i), depth + 1, now, childRows);
			}
		}
		if (enabled || !childRows.isEmpty()) {
			if (enabled) {
				out.add(new Row(node, formatRow(node, depth, now, ext), false));
			}
			out.addAll(childRows);
		}
	}

	private static String formatRow(final NodeModel node, final int depth, final long now,
			final PomodoroExtension ext) {
		final StringBuilder indent = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			indent.append("  ");
		}
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
		final String name = plain(node);
		return indent.toString() + mark + " " + name + "  [" + time + "]";
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

	private static final class Row {
		final NodeModel node;
		final String label;
		final boolean header;

		Row(final NodeModel node, final String label, final boolean header) {
			this.node = node;
			this.label = label;
			this.header = header;
		}
	}
}
