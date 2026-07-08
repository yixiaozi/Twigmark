package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.IconNotFound;
import org.freeplane.features.icon.IconStore;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.factory.IconStoreFactory;
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

public class TodoTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final String TODO_ICON_NAME = "hourglass";

	private static final class TodoRecord {
		private final NodeModel node;
		private final String nodeId;
		private final String nodeText;
		private final String todoParentId;
		private final List extraIconNames;

		private TodoRecord(NodeModel node, String nodeId, String nodeText, String todoParentId, List extraIconNames) {
			this.node = node;
			this.nodeId = nodeId;
			this.nodeText = nodeText;
			this.todoParentId = todoParentId;
			this.extraIconNames = extraIconNames;
		}
	}

	private static final class GroupLabel {
		private final String text;

		private GroupLabel(String text) {
			this.text = text;
		}
	}

	private final JTree tree = new JTree(new DefaultMutableTreeNode("\u5F85\u529E"));
	private final ModeController modeController;

	public TodoTabPanel(final ModeController modeController) {
		super(new BorderLayout(4, 4));
		this.modeController = modeController;

		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		installArrowKeyNavigation();

		tree.setCellRenderer(new DefaultTreeCellRenderer() {
			private static final long serialVersionUID = 1L;
			private final Color TODO_COLOR = new Color(0, 102, 204);
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
				} else if (user instanceof TodoRecord) {
					TodoRecord record = (TodoRecord) user;
					setText(normalizeNodeText(record.nodeText));
					setIcon(buildCombinedIcon(record.extraIconNames));
					if (!sel) {
						setForeground(TODO_COLOR);
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
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() >= 1) {
					openSelectedTodo();
				}
			}
		});

		add(new JScrollPane(tree), BorderLayout.CENTER);
		addListeners();
		reloadTodos();
	}

	private void addListeners() {
		final MapController mapController = modeController.getMapController();
		mapController.addNodeChangeListener(new INodeChangeListener() {
			public void nodeChanged(NodeChangeEvent event) {
				reloadTodos();
			}
		});
		mapController.addMapChangeListener(new IMapChangeListener() {
			public void mapChanged(MapChangeEvent event) {
				reloadTodos();
			}

			public void onNodeInserted(NodeModel parent, NodeModel child, int newIndex) {
				reloadTodos();
			}

			public void onNodeDeleted(NodeModel parent, NodeModel child, int index) {
				reloadTodos();
			}

			public void onNodeMoved(NodeModel oldParent, int oldIndex, NodeModel newParent, NodeModel child,
					int newIndex) {
				reloadTodos();
			}

			public void onPreNodeDelete(NodeModel oldParent, NodeModel selectedNode, int index) {
			}

			public void onPreNodeMoved(NodeModel oldParent, int oldIndex, NodeModel newParent, NodeModel child,
					int newIndex) {
			}
		});
		final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
		mapViewManager.addMapSelectionListener(new IMapSelectionListener() {
			public void beforeMapChange(MapModel oldMap, MapModel newMap) {
			}

			public void afterMapChange(MapModel oldMap, MapModel newMap) {
				reloadTodos();
			}
		});
	}

	private void openSelectedTodo() {
		TreePath path = tree.getSelectionPath();
		if (path == null) {
			return;
		}
		Object nodeObj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
		if (!(nodeObj instanceof TodoRecord)) {
			return;
		}
		TodoRecord record = (TodoRecord) nodeObj;
		Controller.getCurrentController().getSelection().selectAsTheOnlyOneSelected(record.node);
		Controller.getCurrentModeController().getMapController().centerNode(record.node);
		tree.requestFocusInWindow();
	}

	private void installArrowKeyNavigation() {
		tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0),
				"todo.up");
		tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0),
				"todo.down");
		tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0),
				"todo.left");
		tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0),
				"todo.right");

		tree.getActionMap().put("todo.up", new AbstractAction() {
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

		tree.getActionMap().put("todo.down", new AbstractAction() {
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

		tree.getActionMap().put("todo.left", new AbstractAction() {
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

		tree.getActionMap().put("todo.right", new AbstractAction() {
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

	private void reloadTodos() {
		MapModel map = Controller.getCurrentController().getMap();
		if (map == null || map.getRootNode() == null) {
			SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_CURRENT_TODOS, 0);
			return;
		}

		DefaultMutableTreeNode root = new DefaultMutableTreeNode("\u5F85\u529E");
		List records = new ArrayList();
		collectTodoRecords(map.getRootNode(), records);
		attachTodosByParentId(root, records);

		tree.setModel(new DefaultTreeModel(root));

		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_CURRENT_TODOS, records.size());

		for (int i = 0; i < tree.getRowCount(); i++) {
			tree.expandRow(i);
		}
	}

	private void collectTodoRecords(NodeModel node, List records) {
		if (hasHourglassIcon(node)) {
			String nodeText = node.getText();
			String label = nodeText == null ? "" : normalizeNodeText(nodeText);
			if (!"bin".equalsIgnoreCase(label)) {
				records.add(new TodoRecord(node, node.getID(), label, findNearestTodoParentId(node),
						getExtraIconNames(node)));
			}
		}
		for (NodeModel child : node.getChildren()) {
			collectTodoRecords(child, records);
		}
	}

	private void attachTodosByParentId(DefaultMutableTreeNode mapNode, List records) {
		Map itemNodesByKey = new HashMap();

		for (int i = 0; i < records.size(); i++) {
			TodoRecord record = (TodoRecord) records.get(i);
			itemNodesByKey.put(record.nodeId, new DefaultMutableTreeNode(record, true));
		}

		for (int i = 0; i < records.size(); i++) {
			TodoRecord record = (TodoRecord) records.get(i);
			DefaultMutableTreeNode itemNode = (DefaultMutableTreeNode) itemNodesByKey.get(record.nodeId);
			if (itemNode == null) {
				continue;
			}

			DefaultMutableTreeNode attachParent = mapNode;
			if (record.todoParentId != null && record.todoParentId.length() > 0) {
				DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) itemNodesByKey.get(record.todoParentId);
				if (parentNode != null) {
					attachParent = parentNode;
				}
			}
			attachParent.add(itemNode);
		}
	}

	private String findNearestTodoParentId(NodeModel node) {
		NodeModel parent = node.getParentNode();
		while (parent != null) {
			if (hasHourglassIcon(parent)) {
				String label = normalizeNodeText(parent.getText());
				if (!"bin".equalsIgnoreCase(label)) {
					return parent.getID();
				}
			}
			parent = parent.getParentNode();
		}
		return null;
	}

	private List getExtraIconNames(NodeModel node) {
		final List result = new ArrayList();
		Collection icons = IconController.getController().getIcons(node);
		for (Object iconObj : icons) {
			MindIcon icon = (MindIcon) iconObj;
			if (!TODO_ICON_NAME.equalsIgnoreCase(icon.getName())) {
				result.add(icon.getName());
			}
		}
		return result;
	}

	private boolean hasHourglassIcon(NodeModel node) {
		Collection icons = IconController.getController().getIcons(node);
		for (Object iconObj : icons) {
			MindIcon icon = (MindIcon) iconObj;
			if (TODO_ICON_NAME.equalsIgnoreCase(icon.getName())) {
				return true;
			}
		}
		return false;
	}

	private String normalizeNodeText(String text) {
		if (text == null) {
			return "";
		}
		return HtmlUtils.removeHtmlTagsFromString(text).replaceAll("\\s+", " ").trim();
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
