package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

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

public class ReminderTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final class ReminderItem {
		private final NodeModel node;
		private final long remindAt;
		private final String text;

		private ReminderItem(NodeModel node, long remindAt, String text) {
			this.node = node;
			this.remindAt = remindAt;
			this.text = text;
		}
	}

	private final DefaultListModel items = new DefaultListModel();
	private final JList reminderList = new JList(items);
	private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
	private final ModeController modeController;

	public ReminderTabPanel(final ModeController modeController) {
		super(new BorderLayout());
		this.modeController = modeController;
		reminderList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		installArrowKeyConsumption();
		reminderList.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof ReminderItem) {
					ReminderItem item = (ReminderItem) value;
					String nodeText = item.text == null ? "" : HtmlUtils.removeHtmlTagsFromString(item.text).replaceAll("\\s+", " ");
					setText(dateFormat.format(new Date(item.remindAt)) + "  -  " + nodeText);
				}
				return this;
			}
		});
		reminderList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() >= 2) {
					gotoSelectedReminder();
				}
			}
		});
		reminderList.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					gotoSelectedReminder();
				}
			}
		});

		add(new JScrollPane(reminderList), BorderLayout.CENTER);
		addListeners();
		reloadReminders();
	}

	private void addListeners() {
		final MapController mapController = modeController.getMapController();
		mapController.addNodeChangeListener(new INodeChangeListener() {
			public void nodeChanged(NodeChangeEvent event) {
				reloadReminders();
			}
		});
		mapController.addMapChangeListener(new IMapChangeListener() {
			public void mapChanged(MapChangeEvent event) {
				reloadReminders();
			}

			public void onNodeInserted(NodeModel parent, NodeModel child, int newIndex) {
				reloadReminders();
			}

			public void onNodeDeleted(NodeModel parent, NodeModel child, int index) {
				reloadReminders();
			}

			public void onNodeMoved(NodeModel oldParent, int oldIndex, NodeModel newParent, NodeModel child, int newIndex) {
				reloadReminders();
			}

			public void onPreNodeDelete(NodeModel oldParent, NodeModel selectedNode, int index) {
			}

			public void onPreNodeMoved(NodeModel oldParent, int oldIndex, NodeModel newParent, NodeModel child, int newIndex) {
			}
		});
		final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
		mapViewManager.addMapSelectionListener(new IMapSelectionListener() {
			public void beforeMapChange(MapModel oldMap, MapModel newMap) {
			}

			public void afterMapChange(MapModel oldMap, MapModel newMap) {
				reloadReminders();
			}
		});
	}

	private void gotoSelectedReminder() {
		Object selected = reminderList.getSelectedValue();
		if (!(selected instanceof ReminderItem)) {
			return;
		}
		NodeModel node = ((ReminderItem) selected).node;
		if (node == null) {
			return;
		}
		Controller.getCurrentController().getSelection().selectAsTheOnlyOneSelected(node);
		Controller.getCurrentModeController().getMapController().centerNode(node);
		reminderList.requestFocusInWindow();
	}

	private void installArrowKeyConsumption() {
		reminderList.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), "reminder.up");
		reminderList.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "reminder.down");
		reminderList.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "reminder.left");
		reminderList.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "reminder.right");
		reminderList.getActionMap().put("reminder.up", new AbstractAction() {
			private static final long serialVersionUID = 1L;
			public void actionPerformed(java.awt.event.ActionEvent e) {
				int index = reminderList.getSelectedIndex();
				if (index > 0) {
					reminderList.setSelectedIndex(index - 1);
					reminderList.ensureIndexIsVisible(index - 1);
				}
			}
		});
		reminderList.getActionMap().put("reminder.down", new AbstractAction() {
			private static final long serialVersionUID = 1L;
			public void actionPerformed(java.awt.event.ActionEvent e) {
				int index = reminderList.getSelectedIndex();
				int max = items.getSize() - 1;
				if (index < max) {
					reminderList.setSelectedIndex(index + 1);
					reminderList.ensureIndexIsVisible(index + 1);
				}
			}
		});
		AbstractAction keepFocusAction = new AbstractAction() {
			private static final long serialVersionUID = 1L;
			public void actionPerformed(java.awt.event.ActionEvent e) {
				// consume left/right to avoid map navigation
			}
		};
		reminderList.getActionMap().put("reminder.left", keepFocusAction);
		reminderList.getActionMap().put("reminder.right", keepFocusAction);
	}

	private void reloadReminders() {
		items.clear();
		MapModel map = Controller.getCurrentController().getMap();
		if (map == null || map.getRootNode() == null) {
			SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_CURRENT_REMINDERS, 0);
			return;
		}
		List<ReminderItem> reminders = new ArrayList<ReminderItem>();
		collectReminderItems(map.getRootNode(), reminders);
		Collections.sort(reminders, new Comparator<ReminderItem>() {
			public int compare(ReminderItem o1, ReminderItem o2) {
				return o1.remindAt < o2.remindAt ? -1 : (o1.remindAt == o2.remindAt ? 0 : 1);
			}
		});
		for (ReminderItem item : reminders) {
			items.addElement(item);
		}
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_CURRENT_REMINDERS, items.size());
	}

	private void collectReminderItems(NodeModel node, List<ReminderItem> out) {
		ReminderExtension extension = ReminderExtension.getExtension(node);
		if (extension != null && extension.getRemindUserAt() > 0) {
			final String plainText = TextController.getController().getPlainTextContent(node);
			if (!"bin".equalsIgnoreCase(plainText == null ? "" : plainText.trim())) {
				out.add(new ReminderItem(node, extension.getRemindUserAt(), plainText));
			}
		}
		for (NodeModel child : node.getChildren()) {
			collectReminderItems(child, out);
		}
	}
}
