package org.freeplane.plugin.workspace.components.nodepins;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;

import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.MapChangeEvent;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.plugin.workspace.actions.MindMapOpenLocationAction;
import org.freeplane.plugin.workspace.components.favorites.TagChipFactory;
import org.freeplane.plugin.workspace.components.favorites.WrapFlowLayout;
import org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagService;
import org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagUtils;
import org.freeplane.plugin.workspace.features.nodepins.NodeMindMapActionUtils;
import org.freeplane.plugin.workspace.features.nodepins.NodePinEntry;
import org.freeplane.plugin.workspace.features.nodepins.NodePinNavigator;
import org.freeplane.plugin.workspace.features.nodepins.NodePinsIndex;
import org.freeplane.plugin.workspace.features.nodepins.NodePinsMetricsPublisher;

public class PinnedNodesTabPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final String FAVORITE_NAME_COLOR = "#0066CC";
	private static final String FILTER_PINS = "__pins__";

	private final ModeController modeController;
	private final NodePinsIndex index = NodePinsIndex.getInstance();
	private final DefaultListModel listModel = new DefaultListModel();
	private final JList entryList = new JList(listModel);
	private final JPanel tagFilterPanel = new JPanel(new WrapFlowLayout());
	private String activeFilter = FILTER_PINS;

	private final Runnable refreshListener = new Runnable() {
		public void run() {
			refreshView();
		}
	};

	private final INodeChangeListener nodeChangeListener = new INodeChangeListener() {
		public void nodeChanged(final NodeChangeEvent event) {
			if (event.getNode() != null) {
				PinnedNodesTabPanel.this.index.updateFromNode(event.getNode());
			}
		}
	};

	private final IMapChangeListener mapChangeListener = new IMapChangeListener() {
		public void mapChanged(final MapChangeEvent event) {
			index.scheduleRescan();
		}

		public void onPreNodeDelete(final NodeModel oldParent, final NodeModel selectedNode, final int index) {
		}

		public void onNodeInserted(final NodeModel parent, final NodeModel child, final int newIndex) {
		}

		public void onNodeDeleted(final NodeModel parent, final NodeModel child, final int index) {
			PinnedNodesTabPanel.this.index.scheduleRescan();
		}

		public void onPreNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
				final NodeModel child, final int newIndex) {
		}

		public void onNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
				final NodeModel child, final int newIndex) {
		}
	};

	public PinnedNodesTabPanel(final ModeController modeController) {
		super(new BorderLayout(0, 4));
		this.modeController = modeController;
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		buildTagFilterPanel();
		buildEntryList();
		add(tagFilterPanel, BorderLayout.NORTH);
		add(new JScrollPane(entryList), BorderLayout.CENTER);
		index.addChangeListener(refreshListener);
		modeController.getMapController().addNodeChangeListener(nodeChangeListener);
		modeController.getMapController().addMapChangeListener(mapChangeListener);
		index.rescan();
	}

	private void buildTagFilterPanel() {
		tagFilterPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder(TextUtils.getText("workspace.nodepins.filter.label")),
				BorderFactory.createEmptyBorder(0, 2, 2, 2)));
		rebuildTagButtons();
	}

	private void rebuildTagButtons() {
		tagFilterPanel.removeAll();
		tagFilterPanel.add(createFilterButton(FILTER_PINS, formatCountLabel(
				TextUtils.getText("workspace.nodepins.filter.pins"), index.countPinned())));
		for (final Iterator it = getTagsSortedByCount().iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			tagFilterPanel.add(createFilterButton(tag, formatCountLabel(tag, index.countWithTag(tag))));
		}
		tagFilterPanel.revalidate();
		tagFilterPanel.repaint();
		revalidate();
		repaint();
	}

	private List getTagsSortedByCount() {
		final List tags = new ArrayList(index.getQuickSelectTags());
		Collections.sort(tags, new Comparator() {
			public int compare(final Object o1, final Object o2) {
				final String tag1 = (String) o1;
				final String tag2 = (String) o2;
				final int count1 = index.countWithTag(tag1);
				final int count2 = index.countWithTag(tag2);
				if (count1 != count2) {
					return count2 - count1;
				}
				return tag1.compareTo(tag2);
			}
		});
		return tags;
	}

	private String formatCountLabel(final String baseLabel, final int count) {
		return baseLabel + " " + count;
	}

	private JToggleButton createFilterButton(final String filterId, final String label) {
		final boolean selected = filterId.equals(activeFilter);
		final JToggleButton button = TagChipFactory.createFilterChip(filterId, label, selected);
		button.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				activeFilter = filterId;
				rebuildTagButtons();
				refreshList();
			}
		});
		return button;
	}

	private void buildEntryList() {
		entryList.setCellRenderer(new NodePinEntryRenderer());
		entryList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() == 2) {
					openSelectedEntry();
				}
				else if (e.isPopupTrigger() || (e.getButton() == MouseEvent.BUTTON3 && e.getClickCount() == 1)) {
					showPopup(e);
				}
			}

			public void mousePressed(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					showPopup(e);
				}
			}

			public void mouseReleased(final MouseEvent e) {
				if (e.isPopupTrigger()) {
					showPopup(e);
				}
			}
		});
	}

	private void showPopup(final MouseEvent e) {
		final int index = entryList.locationToIndex(e.getPoint());
		if (index < 0) {
			return;
		}
		entryList.setSelectedIndex(index);
		final NodePinEntry entry = (NodePinEntry) listModel.getElementAt(index);
		final JPopupMenu popup = new JPopupMenu();
		final JMenuItem openItem = new JMenuItem(TextUtils.getText("workspace.nodepins.action.open"));
		openItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				openEntry(entry);
			}
		});
		popup.add(openItem);
		final File mapFile = entry.getMapFile();
		if (mapFile != null) {
			final JMenuItem openLocationItem = new JMenuItem(TextUtils.getText("workspace.action.mindmap.open.location.label"));
			openLocationItem.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent event) {
					MindMapOpenLocationAction.openContainingFolder(mapFile);
				}
			});
			popup.add(openLocationItem);
		}
		final JMenuItem editTagsItem = new JMenuItem(TextUtils.getText("workspace.action.nodepins.edit.tags"));
		editTagsItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				EditNodePinTagsDialog.showForKey(entry.getKey());
			}
		});
		popup.add(editTagsItem);
		if (entry.isPinned()) {
			final JMenuItem unpinItem = new JMenuItem(TextUtils.getText("workspace.nodepins.action.unpin"));
			unpinItem.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent event) {
					NodeMindMapActionUtils.withNodeByKey(entry.getKey(), new NodeMindMapActionUtils.NodeRunnable() {
						public void run(final NodeModel node) {
							NodeDetailsTagService.removePinOnly(node);
						}
					});
				}
			});
			popup.add(unpinItem);
		}
		popup.addSeparator();
		final JMenuItem removeItem = new JMenuItem(TextUtils.getText("workspace.nodepins.action.remove"));
		removeItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				NodeMindMapActionUtils.withNodeByKey(entry.getKey(), new NodeMindMapActionUtils.NodeRunnable() {
					public void run(final NodeModel node) {
						NodeDetailsTagService.removeAllManagedTags(node);
					}
				});
			}
		});
		popup.add(removeItem);
		popup.show(entryList, e.getX(), e.getY());
	}

	private void openSelectedEntry() {
		final NodePinEntry entry = (NodePinEntry) entryList.getSelectedValue();
		if (entry != null) {
			openEntry(entry);
		}
	}

	private void openEntry(final NodePinEntry entry) {
		if (entry != null) {
			NodePinNavigator.openNode(entry.getKey());
		}
	}

	public void refreshView() {
		rebuildTagButtons();
		refreshList();
	}

	private void refreshList() {
		listModel.clear();
		final boolean pinsMode = FILTER_PINS.equals(activeFilter);
		final String tagFilter = pinsMode ? null : activeFilter;
		final List entries = index.getDisplayEntries(pinsMode, tagFilter);
		for (int i = 0; i < entries.size(); i++) {
			listModel.addElement(resolveDisplayEntry((NodePinEntry) entries.get(i)));
		}
		publishPinnedSnapshot(index.getDisplayEntries(true, null));
	}

	private void publishPinnedSnapshot(final List entries) {
		NodePinsMetricsPublisher.publishFromEntries(entries);
	}

	private NodePinEntry resolveDisplayEntry(final NodePinEntry entry) {
		final NodeModel node = NodeMindMapActionUtils.resolveNodeByKey(entry.getKey());
		if (node == null) {
			return entry;
		}
		final Set userTags = NodeDetailsTagService.getUserTags(node);
		final boolean pinned = NodeDetailsTagService.isPinned(node);
		final String label = NodeMindMapActionUtils.getNodePlainText(node);
		final NodePinEntry liveEntry = new NodePinEntry(entry.getKey(), userTags, pinned,
				label.length() > 0 ? label : entry.getListNodeLabel());
		liveEntry.setExists(true);
		return liveEntry;
	}

	private class NodePinEntryRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		public Component getListCellRendererComponent(final JList list, final Object value, final int index,
				final boolean isSelected, final boolean cellHasFocus) {
			final JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value instanceof NodePinEntry) {
				final NodePinEntry entry = (NodePinEntry) value;
				label.setText(formatEntryLabelHtml(entry, isSelected));
			}
			return label;
		}

		private String formatEntryLabelHtml(final NodePinEntry entry, final boolean isSelected) {
			final String nodeName = escapeHtml(entry.getListNodeLabel());
			final String mapName = escapeHtml(stripMindMapExtension(entry.getMapDisplayName()));
			final StringBuilder html = new StringBuilder("<html>");
			if (!entry.exists() && !isSelected) {
				html.append("<b><font color='#999999'>").append(nodeName).append("</font></b>");
			}
			else if (isSelected) {
				html.append("<b>").append(nodeName).append("</b>");
			}
			else {
				html.append("<b><font color='").append(FAVORITE_NAME_COLOR).append("'>").append(nodeName).append("</font></b>");
			}
			if (mapName.length() > 0) {
				if (isSelected) {
					html.append("  <font size='2'>").append(mapName).append("</font>");
				}
				else if (!entry.exists()) {
					html.append("  <font color='#999999' size='2'>").append(mapName).append("</font>");
				}
				else {
					html.append("  <font color='#888888' size='2'>").append(mapName).append("</font>");
				}
			}
			if (!entry.getTags().isEmpty()) {
				final String tagsText = formatTagsText(entry);
				if (isSelected) {
					html.append("  [").append(escapeHtml(tagsText)).append(']');
				}
				else if (!entry.exists()) {
					html.append("  <font color='#999999'>[").append(escapeHtml(tagsText)).append("]</font>");
				}
				else {
					html.append("  <font color='#666666'>[").append(escapeHtml(tagsText)).append("]</font>");
				}
			}
			html.append("</html>");
			return html.toString();
		}

		private String formatTagsText(final NodePinEntry entry) {
			final StringBuilder builder = new StringBuilder();
			boolean first = true;
			for (final Iterator it = entry.getTags().iterator(); it.hasNext();) {
				final String tag = (String) it.next();
				if (!first) {
					builder.append(", ");
				}
				builder.append(tag);
				first = false;
			}
			return builder.toString();
		}

		private String stripMindMapExtension(final String fileName) {
			if (fileName == null) {
				return "";
			}
			final String lower = fileName.toLowerCase();
			if (lower.endsWith(".mm")) {
				return fileName.substring(0, fileName.length() - 3);
			}
			if (lower.endsWith(".dcr")) {
				return fileName.substring(0, fileName.length() - 4);
			}
			return fileName;
		}

		private String escapeHtml(final String text) {
			if (text == null) {
				return "";
			}
			return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		}
	}
}
