package org.freeplane.core.ui.components;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.features.mode.Controller;

/**
 * Persist order / visibility for the right format sidebar tabs.
 * Right-click a tab header to hide it or open the manage dialog; drag to reorder.
 */
public final class FormatSideTabManager {
	public static final String ORDER_PROPERTY = "format.side.tab.order";
	public static final String HIDDEN_PROPERTY = "format.side.tab.hidden";

	private static final Map HIDDEN_TABS = new LinkedHashMap();
	private static boolean applying;
	private static boolean listenersInstalled;

	private FormatSideTabManager() {
	}

	public static void install(final JTabbedPane tabs) {
		if (tabs == null) {
			return;
		}
		if (tabs instanceof DraggableTabbedPane && !listenersInstalled) {
			final DraggableTabbedPane draggable = (DraggableTabbedPane) tabs;
			draggable.setTabReorderListener(new DraggableTabbedPane.TabReorderListener() {
				public void tabReordered(final int fromIndex, final int toIndex) {
					moveTab(tabs, fromIndex, toIndex);
					persistOrder(tabs);
				}
			});
		}
		if (!listenersInstalled) {
			listenersInstalled = true;
			tabs.addMouseListener(new MouseAdapter() {
				public void mousePressed(final MouseEvent e) {
					maybePopup(tabs, e);
				}

				public void mouseReleased(final MouseEvent e) {
					maybePopup(tabs, e);
				}
			});
		}
		applyPreferences(tabs);
	}

	public static void applyPreferences(final JTabbedPane tabs) {
		if (tabs == null || applying) {
			return;
		}
		applying = true;
		try {
			applyOrder(tabs);
			applyHidden(tabs);
		}
		finally {
			applying = false;
		}
	}

	public static void onTabsChanged(final JTabbedPane tabs) {
		applyPreferences(tabs);
	}

	private static void maybePopup(final JTabbedPane tabs, final MouseEvent e) {
		if (!e.isPopupTrigger()) {
			return;
		}
		final int index = tabs.indexAtLocation(e.getX(), e.getY());
		if (index < 0) {
			showBackgroundMenu(tabs, e);
			return;
		}
		showTabMenu(tabs, index, e);
	}

	private static void showTabMenu(final JTabbedPane tabs, final int index, final MouseEvent e) {
		final String title = SideTabTitleUpdater.baseTitleAt(tabs, index);
		final JPopupMenu menu = new JPopupMenu();
		final JMenuItem hide = new JMenuItem("隐藏「" + title + "」");
		hide.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent ev) {
				hideTab(tabs, index);
			}
		});
		menu.add(hide);
		menu.addSeparator();
		addCommonItems(menu, tabs);
		menu.show(tabs, e.getX(), e.getY());
	}

	private static void showBackgroundMenu(final JTabbedPane tabs, final MouseEvent e) {
		final JPopupMenu menu = new JPopupMenu();
		addCommonItems(menu, tabs);
		menu.show(tabs, e.getX(), e.getY());
	}

	private static void addCommonItems(final JPopupMenu menu, final JTabbedPane tabs) {
		final JMenuItem manage = new JMenuItem("管理右侧标签…");
		manage.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				showManageDialog(tabs);
			}
		});
		menu.add(manage);
		if (!HIDDEN_TABS.isEmpty()) {
			final JMenuItem showAll = new JMenuItem("显示全部隐藏的标签");
			showAll.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					showAllHidden(tabs);
				}
			});
			menu.add(showAll);
		}
	}

	private static void hideTab(final JTabbedPane tabs, final int index) {
		if (index < 0 || index >= tabs.getTabCount()) {
			return;
		}
		if (tabs.getTabCount() <= 1) {
			JOptionPane.showMessageDialog(tabs, "至少保留一个标签。", "右侧标签", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final String title = SideTabTitleUpdater.baseTitleAt(tabs, index);
		final HiddenTab hidden = new HiddenTab();
		hidden.title = title;
		hidden.component = tabs.getComponentAt(index);
		hidden.tip = tabs.getToolTipTextAt(index);
		hidden.icon = tabs.getIconAt(index);
		HIDDEN_TABS.put(title, hidden);
		tabs.remove(index);
		persistHidden();
		persistOrder(tabs);
	}

	private static void showAllHidden(final JTabbedPane tabs) {
		ResourceController.getResourceController().setProperty(HIDDEN_PROPERTY, "");
		restoreHiddenTabs(tabs);
		persistOrder(tabs);
	}

	private static void applyOrder(final JTabbedPane tabs) {
		final List desired = parseCsv(ORDER_PROPERTY);
		if (desired.isEmpty() || tabs.getTabCount() < 2) {
			return;
		}
		final List current = currentTitles(tabs);
		boolean same = current.size() == desired.size();
		if (same) {
			for (int i = 0; i < desired.size(); i++) {
				if (!desired.get(i).equals(current.get(i))) {
					same = false;
					break;
				}
			}
		}
		if (same) {
			return;
		}
		final List remaining = new ArrayList(current);
		final List ordered = new ArrayList();
		for (int i = 0; i < desired.size(); i++) {
			final String title = (String) desired.get(i);
			if (remaining.remove(title)) {
				ordered.add(title);
			}
		}
		ordered.addAll(remaining);
		for (int target = 0; target < ordered.size(); target++) {
			final String title = (String) ordered.get(target);
			final int from = indexOfTitle(tabs, title);
			if (from >= 0 && from != target) {
				moveTab(tabs, from, target);
			}
		}
	}

	private static void applyHidden(final JTabbedPane tabs) {
		final Set hiddenNames = new LinkedHashSet(parseCsv(HIDDEN_PROPERTY));
		if (hiddenNames.isEmpty()) {
			return;
		}
		for (int i = tabs.getTabCount() - 1; i >= 0; i--) {
			final String title = SideTabTitleUpdater.baseTitleAt(tabs, i);
			if (!hiddenNames.contains(title)) {
				continue;
			}
			if (tabs.getTabCount() <= 1) {
				break;
			}
			final HiddenTab hidden = new HiddenTab();
			hidden.title = title;
			hidden.component = tabs.getComponentAt(i);
			hidden.tip = tabs.getToolTipTextAt(i);
			hidden.icon = tabs.getIconAt(i);
			HIDDEN_TABS.put(title, hidden);
			tabs.remove(i);
		}
	}

	private static void restoreHiddenTabs(final JTabbedPane tabs) {
		final List keys = new ArrayList(HIDDEN_TABS.keySet());
		for (int i = 0; i < keys.size(); i++) {
			final String title = (String) keys.get(i);
			final HiddenTab hidden = (HiddenTab) HIDDEN_TABS.remove(title);
			if (hidden == null || hidden.component == null) {
				continue;
			}
			if (indexOfTitle(tabs, title) >= 0) {
				continue;
			}
			tabs.addTab(title, hidden.icon, hidden.component, hidden.tip);
		}
		HIDDEN_TABS.clear();
	}

	private static void showManageDialog(final JTabbedPane tabs) {
		final Frame frame = Controller.getCurrentController().getViewController().getFrame();
		final JDialog dialog = new JDialog(frame, "管理右侧标签", true);
		final JPanel root = new JPanel(new BorderLayout(8, 8));
		DocearUiTheme.styleCanvas(root);
		root.setBorder(DocearUiTheme.pageBorder());
		root.add(new JLabel("<html>勾选要显示的标签。拖动标签头可调整顺序。</html>"), BorderLayout.NORTH);

		final JPanel list = new JPanel();
		list.setOpaque(false);
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		final Map checkboxes = new LinkedHashMap();
		final List visible = currentTitles(tabs);
		for (int i = 0; i < visible.size(); i++) {
			final String title = (String) visible.get(i);
			final JCheckBox box = new JCheckBox(title, true);
			checkboxes.put(title, box);
			list.add(box);
			list.add(Box.createVerticalStrut(4));
		}
		final List hiddenKeys = new ArrayList(HIDDEN_TABS.keySet());
		for (int i = 0; i < hiddenKeys.size(); i++) {
			final String title = (String) hiddenKeys.get(i);
			if (checkboxes.containsKey(title)) {
				continue;
			}
			final JCheckBox box = new JCheckBox(title, false);
			checkboxes.put(title, box);
			list.add(box);
			list.add(Box.createVerticalStrut(4));
		}

		root.add(new JScrollPane(list), BorderLayout.CENTER);
		final JPanel south = new JPanel();
		final JButton ok = DocearUiTheme.primaryButton("确定");
		final JButton cancel = DocearUiTheme.softButton("取消");
		ok.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final Set keepHidden = new LinkedHashSet();
				final List titles = new ArrayList(checkboxes.keySet());
				for (int i = 0; i < titles.size(); i++) {
					final String title = (String) titles.get(i);
					final JCheckBox box = (JCheckBox) checkboxes.get(title);
					if (!box.isSelected()) {
						keepHidden.add(title);
						final int idx = indexOfTitle(tabs, title);
						if (idx >= 0 && tabs.getTabCount() > 1) {
							final HiddenTab hidden = new HiddenTab();
							hidden.title = title;
							hidden.component = tabs.getComponentAt(idx);
							hidden.tip = tabs.getToolTipTextAt(idx);
							hidden.icon = tabs.getIconAt(idx);
							HIDDEN_TABS.put(title, hidden);
							tabs.remove(idx);
						}
					}
					else if (HIDDEN_TABS.containsKey(title)) {
						final HiddenTab hidden = (HiddenTab) HIDDEN_TABS.remove(title);
						if (hidden != null && hidden.component != null && indexOfTitle(tabs, title) < 0) {
							tabs.addTab(title, hidden.icon, hidden.component, hidden.tip);
						}
					}
				}
				ResourceController.getResourceController().setProperty(HIDDEN_PROPERTY, joinCsv(keepHidden));
				persistOrder(tabs);
				dialog.dispose();
			}
		});
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				dialog.dispose();
			}
		});
		south.add(ok);
		south.add(cancel);
		root.add(south, BorderLayout.SOUTH);
		dialog.getContentPane().add(root);
		dialog.setSize(new Dimension(360, 420));
		dialog.setLocationRelativeTo(frame);
		dialog.setVisible(true);
	}

	private static void moveTab(final JTabbedPane tabs, final int fromIndex, final int toIndex) {
		if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0) {
			return;
		}
		final Component component = tabs.getComponentAt(fromIndex);
		final String title = tabs.getTitleAt(fromIndex);
		final javax.swing.Icon icon = tabs.getIconAt(fromIndex);
		final String tip = tabs.getToolTipTextAt(fromIndex);
		final Component tabComponent = tabs.getTabComponentAt(fromIndex);
		final boolean enabled = tabs.isEnabledAt(fromIndex);
		final int selected = tabs.getSelectedIndex();
		tabs.remove(fromIndex);
		tabs.insertTab(title, icon, component, tip, toIndex);
		tabs.setEnabledAt(toIndex, enabled);
		if (tabComponent != null) {
			tabs.setTabComponentAt(toIndex, tabComponent);
		}
		if (selected == fromIndex) {
			tabs.setSelectedIndex(toIndex);
		}
		else if (fromIndex < selected && toIndex >= selected) {
			tabs.setSelectedIndex(selected - 1);
		}
		else if (fromIndex > selected && toIndex <= selected) {
			tabs.setSelectedIndex(selected + 1);
		}
	}

	private static void persistOrder(final JTabbedPane tabs) {
		ResourceController.getResourceController().setProperty(ORDER_PROPERTY, joinCsv(currentTitles(tabs)));
	}

	private static void persistHidden() {
		ResourceController.getResourceController().setProperty(HIDDEN_PROPERTY, joinCsv(HIDDEN_TABS.keySet()));
	}

	private static List currentTitles(final JTabbedPane tabs) {
		final List titles = new ArrayList();
		for (int i = 0; i < tabs.getTabCount(); i++) {
			titles.add(SideTabTitleUpdater.baseTitleAt(tabs, i));
		}
		return titles;
	}

	private static int indexOfTitle(final JTabbedPane tabs, final String title) {
		for (int i = 0; i < tabs.getTabCount(); i++) {
			if (title.equals(SideTabTitleUpdater.baseTitleAt(tabs, i))) {
				return i;
			}
		}
		return -1;
	}

	private static List parseCsv(final String propertyKey) {
		final List out = new ArrayList();
		final String raw = ResourceController.getResourceController().getProperty(propertyKey, "");
		if (raw == null || raw.trim().length() == 0) {
			return out;
		}
		final String[] parts = raw.split(",");
		final Set seen = new LinkedHashSet();
		for (int i = 0; i < parts.length; i++) {
			final String part = parts[i].trim();
			if (part.length() > 0 && seen.add(part)) {
				out.add(part);
			}
		}
		return out;
	}

	private static String joinCsv(final Collection values) {
		final StringBuffer sb = new StringBuffer();
		boolean first = true;
		final Iterator it = values.iterator();
		while (it.hasNext()) {
			final Object value = it.next();
			if (value == null) {
				continue;
			}
			final String s = String.valueOf(value).trim();
			if (s.length() == 0) {
				continue;
			}
			if (!first) {
				sb.append(',');
			}
			sb.append(s);
			first = false;
		}
		return sb.toString();
	}

	private static final class HiddenTab {
		String title;
		Component component;
		String tip;
		javax.swing.Icon icon;
	}
}
