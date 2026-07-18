package org.freeplane.plugin.workspace.components.favorites;

import org.freeplane.core.ui.theme.DocearUiTheme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mapio.MapIO;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.workspace.actions.MindMapOpenLocationAction;
import org.freeplane.plugin.workspace.components.tagfilter.TagGroupCascadeBar;
import org.freeplane.plugin.workspace.dnd.WorkspaceTransferable;
import org.freeplane.plugin.workspace.features.favorites.FavoriteEntry;
import org.freeplane.plugin.workspace.features.favorites.FavoriteUriUtils;
import org.freeplane.plugin.workspace.features.favorites.FavoritesAndTagsStore;
import org.freeplane.plugin.workspace.features.favorites.WorkspaceMindMapUtils;
import org.freeplane.plugin.workspace.features.nodepins.TagColorStore;
import org.freeplane.plugin.workspace.features.nodepins.TagGroupStore;

public class FavoritesTabPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final DataFlavor REORDER_FLAVOR = DataFlavor.stringFlavor;
	private static final String FAVORITE_NAME_COLOR = "#0F766E";
	private static final int DEFAULT_TAG_PANEL_HEIGHT = 168;
	private static final int MIN_TAG_PANEL_HEIGHT = 96;
	private static final String PROP_FILTER_DIVIDER = "workspace.favorites.filter.divider";
	private static final String PROP_ACTIVE_GROUP = "workspace.favorites.filter.active.group";
	private static final String PROP_DIRECT_ONLY = "workspace.favorites.filter.direct.only";

	private static final Color FILTER_SHELL_BG = DocearUiTheme.CANVAS;
	private static final Color FILTER_SHELL_BORDER = DocearUiTheme.HAIRLINE;

	private final FavoritesAndTagsStore store = FavoritesAndTagsStore.getInstance();
	private final DefaultListModel listModel = new DefaultListModel();
	private final JList favoritesList = new JList(listModel);
	private final TagFilterPanel tagFilterPanel = new TagFilterPanel();
	private final TagGroupCascadeBar groupCascade = new TagGroupCascadeBar(TagGroupStore.getFavoritesInstance(),
			PROP_ACTIVE_GROUP, PROP_DIRECT_ONLY);
	private JPanel filterShell;
	private JSplitPane splitPane;
	private final Runnable refreshListener = new Runnable() {
		public void run() {
			refreshView();
		}
	};

	private String activeTagFilter = null;
	private int draggedIndex = -1;
	private int dropIndex = -1;

	public FavoritesTabPanel() {
		super(new BorderLayout(0, 4));
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		groupCascade.setListener(new TagGroupCascadeBar.Listener() {
			public void selectionChanged() {
				activeTagFilter = null;
				rebuildTagButtons();
				refreshList();
			}

			public Set getAvailableTags() {
				return store.getQuickSelectTags();
			}
		});
		filterShell = buildFilterShell();
		buildFavoritesList();
		final JScrollPane listScrollPane = new JScrollPane(favoritesList);
		listScrollPane.setMinimumSize(new Dimension(80, 80));
		splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filterShell, listScrollPane);
		splitPane.setResizeWeight(0);
		splitPane.setContinuousLayout(true);
		splitPane.setOneTouchExpandable(false);
		splitPane.setBorder(BorderFactory.createEmptyBorder());
		final int savedHeight = ResourceController.getResourceController().getIntProperty(PROP_FILTER_DIVIDER,
				DEFAULT_TAG_PANEL_HEIGHT);
		splitPane.setDividerLocation(Math.max(MIN_TAG_PANEL_HEIGHT, savedHeight));
		splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new java.beans.PropertyChangeListener() {
			public void propertyChange(final java.beans.PropertyChangeEvent evt) {
				if (evt.getNewValue() instanceof Integer) {
					final int location = ((Integer) evt.getNewValue()).intValue();
					if (location >= MIN_TAG_PANEL_HEIGHT) {
						ResourceController.getResourceController().setProperty(PROP_FILTER_DIVIDER,
								String.valueOf(location));
					}
					tagFilterPanel.revalidate();
					tagFilterPanel.repaint();
				}
			}
		});
		add(splitPane, BorderLayout.CENTER);
		store.addChangeListener(refreshListener);
		refreshView();
	}

	private JPanel buildFilterShell() {
		final JPanel shell = new JPanel(new BorderLayout(0, 6));
		shell.setBackground(FILTER_SHELL_BG);
		shell.setOpaque(true);
		shell.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(FILTER_SHELL_BORDER),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)));
		shell.setMinimumSize(new Dimension(80, MIN_TAG_PANEL_HEIGHT));
		groupCascade.rebuild();
		shell.add(groupCascade, BorderLayout.NORTH);
		final JPanel tagArea = new JPanel(new BorderLayout());
		tagArea.setOpaque(false);
		tagArea.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xE0E5E8)),
				BorderFactory.createEmptyBorder(8, 2, 2, 2)));
		final JScrollPane tagScrollPane = new JScrollPane(tagFilterPanel);
		tagScrollPane.setBorder(BorderFactory.createEmptyBorder());
		tagScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		tagScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		tagScrollPane.getViewport().setOpaque(false);
		tagScrollPane.setOpaque(false);
		tagScrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
		tagScrollPane.getViewport().addComponentListener(new ComponentAdapter() {
			public void componentResized(final ComponentEvent e) {
				tagFilterPanel.revalidate();
				tagFilterPanel.repaint();
			}
		});
		groupCascade.installChipPanelDropTarget(tagFilterPanel);
		tagArea.add(tagScrollPane, BorderLayout.CENTER);
		shell.add(tagArea, BorderLayout.CENTER);
		rebuildTagButtons();
		return shell;
	}

	private void rebuildTagButtons() {
		tagFilterPanel.removeAll();
		tagFilterPanel.add(createTagButton(null, formatTagCountLabel(null,
				TextUtils.getText("workspace.favorites.filter.all"))));
		for (final Iterator it = getTagsForActiveGroup().iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			tagFilterPanel.add(createTagButton(tag, formatTagCountLabel(tag, tag)));
		}
		tagFilterPanel.revalidate();
		tagFilterPanel.repaint();
		if (filterShell != null) {
			filterShell.revalidate();
			filterShell.repaint();
		}
		revalidate();
		repaint();
	}

	private List getTagsForActiveGroup() {
		return groupCascade.filterTagsInActiveScope(getTagsSortedByCount());
	}

	private List getTagsSortedByCount() {
		final List tags = new ArrayList(store.getQuickSelectTags());
		Collections.sort(tags, new Comparator() {
			public int compare(final Object o1, final Object o2) {
				final String tag1 = (String) o1;
				final String tag2 = (String) o2;
				final int count1 = store.countFavoritesWithTag(tag1);
				final int count2 = store.countFavoritesWithTag(tag2);
				if (count1 != count2) {
					return count2 - count1;
				}
				return tag1.compareTo(tag2);
			}
		});
		return tags;
	}

	private String formatTagCountLabel(final String tag, final String baseLabel) {
		if (tag == null) {
			return baseLabel + " " + countFavoritesMatchingTags(new HashSet(getTagsForActiveGroup()));
		}
		return baseLabel + " " + store.countFavoritesWithTag(tag);
	}

	private int countFavoritesMatchingTags(final Set tags) {
		if (tags == null || tags.isEmpty()) {
			return 0;
		}
		int count = 0;
		final List favorites = store.getFavorites();
		for (int i = 0; i < favorites.size(); i++) {
			final FavoriteEntry entry = (FavoriteEntry) favorites.get(i);
			for (final Iterator it = entry.getTags().iterator(); it.hasNext();) {
				if (tags.contains(it.next())) {
					count++;
					break;
				}
			}
		}
		return count;
	}

	private JToggleButton createTagButton(final String tag, final String label) {
		final boolean selected = tag == null ? activeTagFilter == null : tag.equals(activeTagFilter);
		final JToggleButton button = TagChipFactory.createFilterChip(tag, label, selected);
		button.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				activeTagFilter = tag;
				rebuildTagButtons();
				refreshList();
			}
		});
		if (tag != null) {
			enableTagDrag(button, tag);
			button.addMouseListener(new MouseAdapter() {
				public void mousePressed(final MouseEvent e) {
					if (e.isPopupTrigger()) {
						showTagColorPopup(e, tag, button);
					}
				}

				public void mouseReleased(final MouseEvent e) {
					if (e.isPopupTrigger()) {
						showTagColorPopup(e, tag, button);
					}
				}
			});
		}
		return button;
	}

	private void enableTagDrag(final JComponent chip, final String tag) {
		final DragSource dragSource = DragSource.getDefaultDragSource();
		dragSource.createDefaultDragGestureRecognizer(chip, DnDConstants.ACTION_MOVE, new DragGestureListener() {
			public void dragGestureRecognized(final DragGestureEvent dge) {
				final Transferable transferable = new StringSelection(TagGroupCascadeBar.TAG_DND_PREFIX + tag);
				dge.startDrag(DragSource.DefaultMoveDrop, transferable);
			}
		});
	}

	private void showTagColorPopup(final MouseEvent e, final String tag, final JToggleButton button) {
		final JPopupMenu popup = new JPopupMenu();
		final JMenuItem setColorItem = new JMenuItem(TextUtils.getText("workspace.nodepins.action.set.color"));
		setColorItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				final Color current = TagColorStore.getInstance().getColor(tag);
				final Color chosen = JColorChooser.showDialog(FavoritesTabPanel.this,
						TextUtils.getText("workspace.nodepins.action.set.color"), current);
				if (chosen != null) {
					TagColorStore.getInstance().setColor(tag, chosen);
					rebuildTagButtons();
					refreshList();
				}
			}
		});
		popup.add(setColorItem);
		final JMenuItem resetColorItem = new JMenuItem(TextUtils.getText("workspace.nodepins.action.reset.color"));
		resetColorItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				TagColorStore.getInstance().clearColor(tag);
				rebuildTagButtons();
				refreshList();
			}
		});
		popup.add(resetColorItem);
		groupCascade.appendMoveToGroupMenuItems(popup, tag, new Runnable() {
			public void run() {
				rebuildTagButtons();
			}
		});
		popup.addSeparator();
		final JMenuItem openAllItem = new JMenuItem(TextUtils.getText("workspace.favorites.action.open.all"));
		openAllItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				openAllFavoritesWithTag(tag);
			}
		});
		popup.add(openAllItem);
		popup.show(button, e.getX(), e.getY());
	}

	private void openAllFavoritesWithTag(final String tag) {
		if (tag == null || tag.length() == 0) {
			return;
		}
		final List favorites = store.getFavorites();
		for (int i = 0; i < favorites.size(); i++) {
			final FavoriteEntry entry = (FavoriteEntry) favorites.get(i);
			if (entry.getTags().contains(tag)) {
				openFavorite(entry);
			}
		}
	}

	private void buildFavoritesList() {
		favoritesList.setCellRenderer(new FavoriteEntryRenderer());
		favoritesList.setDragEnabled(true);
		favoritesList.setTransferHandler(new FavoritesReorderTransferHandler());
		favoritesList.addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseDragged(final MouseEvent e) {
				updateDropIndex(e);
			}

			public void mouseMoved(final MouseEvent e) {
				updateDropIndex(e);
			}
		});
		favoritesList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() == 2) {
					openSelectedFavorite();
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

	private void updateDropIndex(final MouseEvent e) {
		dropIndex = favoritesList.locationToIndex(e.getPoint());
		if (dropIndex < 0) {
			dropIndex = listModel.size();
		}
	}

	private void showPopup(final MouseEvent e) {
		final int index = favoritesList.locationToIndex(e.getPoint());
		if (index < 0) {
			return;
		}
		favoritesList.setSelectedIndex(index);
		final FavoriteEntry entry = (FavoriteEntry) listModel.getElementAt(index);
		final JPopupMenu popup = new JPopupMenu();
		final JMenuItem openItem = new JMenuItem(TextUtils.getText("workspace.favorites.action.open"));
		openItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				openFavorite(entry);
			}
		});
		popup.add(openItem);
		final JMenuItem openLocationItem = new JMenuItem(TextUtils.getText("workspace.action.mindmap.open.location.label"));
		openLocationItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				final File file = entry.getFile();
				if (file != null) {
					MindMapOpenLocationAction.openContainingFolder(file);
				}
			}
		});
		popup.add(openLocationItem);
		final JMenuItem editTagsItem = new JMenuItem(TextUtils.getText("workspace.action.favorites.edit.tags.label"));
		editTagsItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				EditTagsDialog.showForUri(entry.getUri());
			}
		});
		popup.add(editTagsItem);
		popup.addSeparator();
		final JMenuItem removeItem = new JMenuItem(TextUtils.getText("workspace.action.favorites.remove.label"));
		removeItem.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent event) {
				store.removeFavorite(entry.getUri());
				persist();
			}
		});
		popup.add(removeItem);
		popup.show(favoritesList, e.getX(), e.getY());
	}

	private void openSelectedFavorite() {
		final FavoriteEntry entry = (FavoriteEntry) favoritesList.getSelectedValue();
		if (entry != null) {
			openFavorite(entry);
		}
	}

	private void openFavorite(final FavoriteEntry entry) {
		if (entry == null) {
			return;
		}
		final File file = entry.getFile();
		if (file == null || !file.exists()) {
			return;
		}
		try {
			final URL fileUrl = Compat.fileToUrl(file);
			if (WorkspaceMindMapUtils.isMindMapFileName(file.getName())) {
				final MapIO mapIO = (MapIO) Controller.getCurrentModeController().getExtension(MapIO.class);
				mapIO.newMap(fileUrl);
			}
			else {
				Controller.getCurrentController().getViewController().openDocument(fileUrl);
			}
		}
		catch (final Exception e) {
			LogUtils.severe(e);
		}
	}

	public void refreshView() {
		store.reloadIfChanged();
		groupCascade.rebuild();
		rebuildTagButtons();
		refreshList();
	}

	private void refreshList() {
		listModel.clear();
		final List favorites = store.getFavorites();
		for (int i = 0; i < favorites.size(); i++) {
			final FavoriteEntry entry = (FavoriteEntry) favorites.get(i);
			if (matchesFilters(entry)) {
				listModel.addElement(entry);
			}
		}
	}

	private boolean matchesFilters(final FavoriteEntry entry) {
		if (activeTagFilter != null && activeTagFilter.length() > 0) {
			return entry.getTags().contains(activeTagFilter);
		}
		final List scopeTags = getTagsForActiveGroup();
		if (scopeTags.isEmpty()) {
			return false;
		}
		for (final Iterator it = entry.getTags().iterator(); it.hasNext();) {
			if (scopeTags.contains(it.next())) {
				return true;
			}
		}
		return false;
	}

	private void persist() {
		store.saveAllProjects();
	}

	private class FavoriteEntryRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		public Component getListCellRendererComponent(final JList list, final Object value, final int index,
				final boolean isSelected, final boolean cellHasFocus) {
			final JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value instanceof FavoriteEntry) {
				final FavoriteEntry entry = (FavoriteEntry) value;
				label.setText(formatEntryLabelHtml(entry, isSelected));
			}
			return label;
		}

		private String formatEntryLabelHtml(final FavoriteEntry entry, final boolean isSelected) {
			final String name = escapeHtml(entry.getListDisplayName());
			final StringBuilder html = new StringBuilder("<html>");
			if (!entry.exists() && !isSelected) {
				html.append("<b><font color='#999999'>").append(name).append("</font></b>");
			}
			else if (isSelected) {
				html.append("<b>").append(name).append("</b>");
			}
			else {
				html.append("<b><font color='").append(FAVORITE_NAME_COLOR).append("'>").append(name).append("</font></b>");
			}
			if (!entry.getTags().isEmpty()) {
				html.append(' ');
				boolean first = true;
				for (final Iterator it = entry.getTags().iterator(); it.hasNext();) {
					final String tag = (String) it.next();
					if (!first) {
						html.append(' ');
					}
					first = false;
					if (isSelected) {
						html.append('[').append(escapeHtml(tag)).append(']');
					}
					else if (!entry.exists()) {
						html.append("<font color='#999999'>[").append(escapeHtml(tag)).append("]</font>");
					}
					else {
						final Color tagColor = TagColorStore.darkerVariant(TagColorStore.getInstance().getColor(tag), 0.55f);
						html.append("<font color='").append(TagColorStore.toHex(tagColor)).append("'>[")
								.append(escapeHtml(tag)).append("]</font>");
					}
				}
			}
			html.append("</html>");
			return html.toString();
		}

		private String escapeHtml(final String text) {
			if (text == null) {
				return "";
			}
			return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		}
	}

	private class FavoritesReorderTransferHandler extends TransferHandler {

		private static final long serialVersionUID = 1L;

		public int getSourceActions(final JComponent c) {
			return MOVE;
		}

		protected Transferable createTransferable(final JComponent c) {
			final JList list = (JList) c;
			draggedIndex = list.getSelectedIndex();
			return new StringSelection("favorite-reorder");
		}

		public boolean canImport(final JComponent comp, final DataFlavor[] transferFlavors) {
			if (comp != favoritesList) {
				return false;
			}
			return hasFlavor(transferFlavors, REORDER_FLAVOR)
					|| hasFlavor(transferFlavors, WorkspaceTransferable.WORKSPACE_URI_LIST_FLAVOR)
					|| hasFlavor(transferFlavors, WorkspaceTransferable.WORKSPACE_FILE_LIST_FLAVOR);
		}

		public boolean importData(final JComponent comp, final Transferable transferable) {
			try {
				if (hasTransferableFlavor(transferable, REORDER_FLAVOR) && draggedIndex >= 0) {
					final int targetIndex = dropIndex >= 0 ? dropIndex : listModel.size();
					reorderEntry((FavoriteEntry) listModel.getElementAt(draggedIndex), targetIndex);
					return true;
				}
				final String uri = FavoriteUriUtils.normalizeToStoredUri(extractUriFromTransfer(transferable));
				if (uri != null && WorkspaceMindMapUtils.isWorkspaceFileUri(uri)) {
					store.addFavorite(uri);
					persist();
					return true;
				}
			}
			catch (final Exception e) {
				LogUtils.warn(e);
			}
			return false;
		}

		protected void exportDone(final JComponent source, final Transferable data, final int action) {
			draggedIndex = -1;
			dropIndex = -1;
		}
	}

	private void reorderEntry(final FavoriteEntry entry, final int filteredDropIndex) {
		if (entry == null) {
			return;
		}
		final List allEntries = store.getFavorites();
		int fromIndex = -1;
		for (int i = 0; i < allEntries.size(); i++) {
			if (((FavoriteEntry) allEntries.get(i)).getUri().equals(entry.getUri())) {
				fromIndex = i;
				break;
			}
		}
		if (fromIndex < 0) {
			return;
		}
		int toIndex = allEntries.size();
		if (filteredDropIndex >= 0 && filteredDropIndex < listModel.size()) {
			final FavoriteEntry targetEntry = (FavoriteEntry) listModel.getElementAt(filteredDropIndex);
			for (int i = 0; i < allEntries.size(); i++) {
				if (((FavoriteEntry) allEntries.get(i)).getUri().equals(targetEntry.getUri())) {
					toIndex = i;
					break;
				}
			}
		}
		store.reorder(fromIndex, toIndex);
		persist();
	}

	private static boolean hasFlavor(final DataFlavor[] flavors, final DataFlavor flavor) {
		for (int i = 0; i < flavors.length; i++) {
			if (flavor.equals(flavors[i])) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasTransferableFlavor(final Transferable transferable, final DataFlavor flavor) {
		return transferable.isDataFlavorSupported(flavor);
	}

	private String extractUriFromTransfer(final Transferable transferable) throws Exception {
		if (transferable.isDataFlavorSupported(WorkspaceTransferable.WORKSPACE_URI_LIST_FLAVOR)) {
			return (String) transferable.getTransferData(WorkspaceTransferable.WORKSPACE_URI_LIST_FLAVOR);
		}
		if (transferable.isDataFlavorSupported(WorkspaceTransferable.WORKSPACE_FILE_LIST_FLAVOR)) {
			final List files = (List) transferable.getTransferData(WorkspaceTransferable.WORKSPACE_FILE_LIST_FLAVOR);
			if (files != null && !files.isEmpty()) {
				return ((File) files.get(0)).toURI().toString();
			}
		}
		return null;
	}

	private static final class TagFilterPanel extends JPanel implements Scrollable {
		private static final long serialVersionUID = 1L;

		TagFilterPanel() {
			super(new WrapFlowLayout());
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		}

		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		public int getScrollableUnitIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
			return 16;
		}

		public int getScrollableBlockIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
			return orientation == SwingConstants.VERTICAL ? Math.max(16, visibleRect.height - 16)
					: Math.max(16, visibleRect.width - 16);
		}

		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		public boolean getScrollableTracksViewportHeight() {
			if (getParent() instanceof JComponent) {
				return getPreferredSize().height <= getParent().getHeight();
			}
			return false;
		}
	}
}
