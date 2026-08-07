package org.freeplane.features.help;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultMutableTreeNode;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.ui.components.FreeplaneMenuBar;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.ui.ribbon.RibbonAcceleratorManager;
import org.freeplane.core.ui.ribbon.RibbonActionContributorFactory;
import org.freeplane.core.ui.ribbon.RibbonBuilder;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.MenuUtils;
import org.freeplane.core.util.MenuUtils.MenuEntry;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;

/**
 * Editable, searchable shortcuts editor. Source of truth is
 * {@link RibbonAcceleratorManager} (Docear ribbon); menu tree supplies categories.
 */
public final class HotKeyEditorDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private final ModeController modeController;
	private final RibbonAcceleratorManager acceleratorManager;
	private final List rows = new ArrayList();
	private final HotKeyTableModel tableModel = new HotKeyTableModel();
	private final JTable table = new JTable(tableModel);
	private TableRowSorter sorter;
	private final JTextField searchField = new JTextField();
	private final JCheckBox boundOnly = new JCheckBox();
	private final JLabel statusLabel = new JLabel(" ");

	public static void showDialog(final Frame owner) {
		final ModeController modeController = resolveModeController();
		if (modeController == null) {
			return;
		}
		RibbonAcceleratorManager accel = null;
		try {
			final RibbonBuilder ribbon = modeController.getUserInputListenerFactory().getRibbonBuilder();
			if (ribbon != null) {
				accel = ribbon.getAcceleratorManager();
			}
		}
		catch (Exception e) {
		}
		final HotKeyEditorDialog dialog = new HotKeyEditorDialog(owner, modeController, accel);
		dialog.setVisible(true);
	}

	private HotKeyEditorDialog(final Frame owner, final ModeController modeController,
	        final RibbonAcceleratorManager acceleratorManager) {
		super(owner, TextUtils.getText("hot_keys_editor.title", TextUtils.getText("hot_keys_table")), true);
		this.modeController = modeController;
		this.acceleratorManager = acceleratorManager;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		buildUi();
		reload();
		pack();
		setSize(Math.max(820, getWidth()), Math.max(560, getHeight()));
		setLocationRelativeTo(owner != null ? owner : UITools.getFrame());
	}

	private void buildUi() {
		final JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

		final JPanel top = new JPanel(new BorderLayout(8, 4));
		final JLabel hint = new JLabel(Compat.isMacOsX()
		        ? TextUtils.getText("hot_keys_editor.hint_mac",
		                "双击或点「修改」可重新绑定。Mac 上 ⌘ 对应 Command；已避开 Option+Space 等系统占用键。")
		        : TextUtils.getText("hot_keys_editor.hint",
		                "双击或点「修改」可重新绑定。列表来自当前软件全部动作（含 Ribbon 与菜单）。"));
		top.add(hint, BorderLayout.NORTH);

		final JPanel filter = new JPanel(new BorderLayout(8, 0));
		searchField.putClientProperty("JTextField.placeholderText",
		        TextUtils.getText("hot_keys_editor.search", "搜索名称 / 快捷键 / 动作…"));
		filter.add(searchField, BorderLayout.CENTER);
		boundOnly.setText(TextUtils.getText("hot_keys_editor.bound_only", "仅显示已绑定"));
		boundOnly.setSelected(true);
		filter.add(boundOnly, BorderLayout.EAST);
		top.add(filter, BorderLayout.SOUTH);
		root.add(top, BorderLayout.NORTH);

		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setRowHeight(Math.max(22, table.getRowHeight()));
		table.getTableHeader().setReorderingAllowed(false);
		sorter = new TableRowSorter(tableModel);
		table.setRowSorter(sorter);
		table.getColumnModel().getColumn(0).setPreferredWidth(180);
		table.getColumnModel().getColumn(1).setPreferredWidth(260);
		table.getColumnModel().getColumn(2).setPreferredWidth(160);
		table.getColumnModel().getColumn(3).setPreferredWidth(120);
		table.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() == 2) {
					editSelected();
				}
			}
		});
		table.addKeyListener(new KeyAdapter() {
			public void keyPressed(final KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					editSelected();
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
					clearSelected();
					e.consume();
				}
			}
		});
		root.add(new JScrollPane(table), BorderLayout.CENTER);

		final JPanel bottom = new JPanel(new BorderLayout());
		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		final JButton editBtn = new JButton(TextUtils.getText("hot_keys_editor.edit", "修改…"));
		final JButton clearBtn = new JButton(TextUtils.getText("hot_keys_editor.clear", "清除"));
		final JButton refreshBtn = new JButton(TextUtils.getText("hot_keys_editor.refresh", "刷新"));
		final JButton closeBtn = new JButton(TextUtils.getText("ok", "确定"));
		editBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				editSelected();
			}
		});
		clearBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				clearSelected();
			}
		});
		refreshBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				reload();
			}
		});
		closeBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				dispose();
			}
		});
		buttons.add(editBtn);
		buttons.add(clearBtn);
		buttons.add(refreshBtn);
		buttons.add(closeBtn);
		bottom.add(statusLabel, BorderLayout.WEST);
		bottom.add(buttons, BorderLayout.EAST);
		root.add(bottom, BorderLayout.SOUTH);

		final DocumentListener filterListener = new DocumentListener() {
			public void insertUpdate(final DocumentEvent e) {
				applyFilter();
			}

			public void removeUpdate(final DocumentEvent e) {
				applyFilter();
			}

			public void changedUpdate(final DocumentEvent e) {
				applyFilter();
			}
		};
		searchField.getDocument().addDocumentListener(filterListener);
		boundOnly.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				applyFilter();
			}
		});

		getRootPane().setDefaultButton(closeBtn);
		getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(
		        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
		getRootPane().getActionMap().put("close", new AbstractAction() {
			public void actionPerformed(final ActionEvent e) {
				dispose();
			}
		});

		setContentPane(root);
	}

	private void reload() {
		rows.clear();
		final Map byAction = new HashMap();
		collectFromMenu(byAction);
		collectFromRibbonAndActions(byAction);
		rows.addAll(byAction.values());
		Collections.sort(rows, new Comparator() {
			public int compare(final Object a, final Object b) {
				final HotKeyRow ra = (HotKeyRow) a;
				final HotKeyRow rb = (HotKeyRow) b;
				final int c = ra.category.compareToIgnoreCase(rb.category);
				if (c != 0) {
					return c;
				}
				return ra.label.compareToIgnoreCase(rb.label);
			}
		});
		tableModel.fireTableDataChanged();
		applyFilter();
		updateStatus();
	}

	private void collectFromMenu(final Map byAction) {
		try {
			final MenuBuilder menuBuilder = modeController.getUserInputListenerFactory().getMenuBuilder();
			if (menuBuilder == null) {
				return;
			}
			final DefaultMutableTreeNode tree = MenuUtils.createMenuEntryTree(FreeplaneMenuBar.MENU_BAR_PREFIX,
			        menuBuilder);
			walkMenu(tree, "", byAction);
		}
		catch (Exception e) {
		}
	}

	private void walkMenu(final DefaultMutableTreeNode node, final String parentCategory, final Map byAction) {
		if (node == null) {
			return;
		}
		final Object uo = node.getUserObject();
		String category = parentCategory;
		if (uo instanceof MenuEntry) {
			final MenuEntry entry = (MenuEntry) uo;
			if (!node.isLeaf()) {
				category = parentCategory.length() == 0 ? entry.getLabel() : parentCategory + " → " + entry.getLabel();
			}
			else {
				final String actionKey = guessActionKey(entry.getKey());
				final AFreeplaneAction action = actionKey == null ? null : modeController.getAction(actionKey);
				if (action != null) {
					addOrMerge(byAction, action, parentCategory.length() == 0 ? TextUtils.getText("menu_bar", "菜单")
					        : parentCategory, entry.getLabel());
				}
			}
		}
		final Enumeration children = node.children();
		while (children.hasMoreElements()) {
			walkMenu((DefaultMutableTreeNode) children.nextElement(), category, byAction);
		}
	}

	private void collectFromRibbonAndActions(final Map byAction) {
		final Set seen = new HashSet(byAction.keySet());
		if (acceleratorManager != null) {
			final Map accel = acceleratorManager.getActionAccelerators();
			for (final Iterator it = accel.keySet().iterator(); it.hasNext();) {
				final String actionKey = (String) it.next();
				if (seen.contains(actionKey)) {
					continue;
				}
				final AFreeplaneAction action = modeController.getAction(actionKey);
				if (action == null) {
					continue;
				}
				addOrMerge(byAction, action, TextUtils.getText("hot_keys_editor.category_other", "其他 / Ribbon"),
				        RibbonActionContributorFactory.getActionTitle(action));
				seen.add(actionKey);
			}
		}
		final Set keys = modeController.getActionKeys();
		for (final Iterator it = keys.iterator(); it.hasNext();) {
			final String actionKey = (String) it.next();
			if (seen.contains(actionKey)) {
				continue;
			}
			final AFreeplaneAction action = modeController.getAction(actionKey);
			if (action == null || actionKey.startsWith("LoadAcceleratorPresetsAction.")) {
				continue;
			}
			// Skip noise: internal / property-toggle actions without a readable title.
			final String title = RibbonActionContributorFactory.getActionTitle(action);
			if (title == null || title.length() == 0 || title.startsWith("[")) {
				continue;
			}
			addOrMerge(byAction, action, TextUtils.getText("hot_keys_editor.category_unbound", "未分类动作"), title);
		}
	}

	private void addOrMerge(final Map byAction, final AFreeplaneAction action, final String category,
	        final String label) {
		final String key = action.getKey();
		HotKeyRow row = (HotKeyRow) byAction.get(key);
		if (row == null) {
			row = new HotKeyRow();
			row.actionKey = key;
			row.action = action;
			row.category = category == null ? "" : category;
			row.label = label == null ? key : label;
			byAction.put(key, row);
		}
		else if ((row.category == null || row.category.length() == 0
		        || row.category.startsWith(TextUtils.getText("hot_keys_editor.category_other", "其他")))
		        && category != null && category.length() > 0) {
			row.category = category;
		}
		row.keyStroke = resolveStroke(action);
	}

	private KeyStroke resolveStroke(final AFreeplaneAction action) {
		if (acceleratorManager != null) {
			final KeyStroke ribbon = acceleratorManager.getAccelerator(action.getKey());
			if (ribbon != null) {
				return ribbon;
			}
		}
		return null;
	}

	/** Menu path keys end with the action key segment. */
	private static String guessActionKey(final String menuPath) {
		if (menuPath == null || menuPath.length() == 0) {
			return null;
		}
		final int slash = menuPath.lastIndexOf('/');
		return slash >= 0 ? menuPath.substring(slash + 1) : menuPath;
	}

	private void applyFilter() {
		final String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
		final boolean onlyBound = boundOnly.isSelected();
		sorter.setRowFilter(new RowFilter() {
			public boolean include(final Entry entry) {
				final HotKeyRow row = (HotKeyRow) rows.get(((Integer) entry.getIdentifier()).intValue());
				if (onlyBound && row.keyStroke == null) {
					return false;
				}
				if (q.length() == 0) {
					return true;
				}
				final String stroke = row.keyStroke == null ? "" : formatStroke(row.keyStroke).toLowerCase();
				return row.label.toLowerCase().indexOf(q) >= 0 || row.category.toLowerCase().indexOf(q) >= 0
				        || row.actionKey.toLowerCase().indexOf(q) >= 0 || stroke.indexOf(q) >= 0;
			}
		});
		updateStatus();
	}

	private void updateStatus() {
		final int visible = table.getRowCount();
		final int bound = countBound();
		final String os = Compat.isMacOsX() ? "macOS" : (Compat.isWindowsOS() ? "Windows" : "Linux");
		statusLabel.setText(TextUtils.getText("hot_keys_editor.status", "显示 {0} 条 · 已绑定 {1} · 平台 {2}")
		        .replace("{0}", String.valueOf(visible)).replace("{1}", String.valueOf(bound))
		        .replace("{2}", os));
	}

	private int countBound() {
		int n = 0;
		for (int i = 0; i < rows.size(); i++) {
			if (((HotKeyRow) rows.get(i)).keyStroke != null) {
				n++;
			}
		}
		return n;
	}

	private HotKeyRow selectedRow() {
		final int viewRow = table.getSelectedRow();
		if (viewRow < 0) {
			return null;
		}
		final int modelRow = table.convertRowIndexToModel(viewRow);
		if (modelRow < 0 || modelRow >= rows.size()) {
			return null;
		}
		return (HotKeyRow) rows.get(modelRow);
	}

	private void editSelected() {
		final HotKeyRow row = selectedRow();
		if (row == null || row.action == null) {
			return;
		}
		if (acceleratorManager == null) {
			statusLabel.setText(TextUtils.getText("hot_keys_editor.no_ribbon", "当前模式没有 Ribbon 快捷键管理器，无法修改。"));
			return;
		}
		acceleratorManager.newAccelerator(row.action, null);
		row.keyStroke = resolveStroke(row.action);
		// Refresh all strokes — conflict replace may clear another row.
		for (int i = 0; i < rows.size(); i++) {
			final HotKeyRow r = (HotKeyRow) rows.get(i);
			r.keyStroke = resolveStroke(r.action);
		}
		tableModel.fireTableDataChanged();
		applyFilter();
		reselect(row.actionKey);
	}

	private void clearSelected() {
		final HotKeyRow row = selectedRow();
		if (row == null || row.action == null || acceleratorManager == null) {
			return;
		}
		acceleratorManager.clearAccelerator(row.action);
		row.keyStroke = null;
		tableModel.fireTableDataChanged();
		applyFilter();
		reselect(row.actionKey);
	}

	private void reselect(final String actionKey) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				for (int i = 0; i < table.getRowCount(); i++) {
					final int modelRow = table.convertRowIndexToModel(i);
					final HotKeyRow r = (HotKeyRow) rows.get(modelRow);
					if (actionKey.equals(r.actionKey)) {
						table.getSelectionModel().setSelectionInterval(i, i);
						table.scrollRectToVisible(table.getCellRect(i, 0, true));
						break;
					}
				}
			}
		});
	}

	static String formatStroke(final KeyStroke keyStroke) {
		if (keyStroke == null) {
			return "";
		}
		if (Compat.isMacOsX()) {
			return formatMacStroke(keyStroke);
		}
		return MenuUtils.formatKeyStroke(keyStroke);
	}

	private static String formatMacStroke(final KeyStroke keyStroke) {
		final int mods = keyStroke.getModifiers();
		final StringBuffer sb = new StringBuffer();
		if ((mods & KeyEvent.META_MASK) != 0 || (mods & KeyEvent.META_DOWN_MASK) != 0) {
			sb.append('\u2318');
		}
		if ((mods & KeyEvent.CTRL_MASK) != 0 || (mods & KeyEvent.CTRL_DOWN_MASK) != 0) {
			sb.append('\u2303');
		}
		if ((mods & KeyEvent.ALT_MASK) != 0 || (mods & KeyEvent.ALT_DOWN_MASK) != 0) {
			sb.append('\u2325');
		}
		if ((mods & KeyEvent.SHIFT_MASK) != 0 || (mods & KeyEvent.SHIFT_DOWN_MASK) != 0) {
			sb.append('\u21E7');
		}
		sb.append(KeyEvent.getKeyText(keyStroke.getKeyCode()));
		return sb.toString();
	}

	private static ModeController resolveModeController() {
		try {
			if (ResourceControllerIsApplet()) {
				return Controller.getCurrentModeController();
			}
			return MModeController.getMModeController();
		}
		catch (Exception e) {
			return Controller.getCurrentModeController();
		}
	}

	private static boolean ResourceControllerIsApplet() {
		try {
			return org.freeplane.core.resources.ResourceController.getResourceController().isApplet();
		}
		catch (Exception e) {
			return false;
		}
	}

	private final class HotKeyTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private final String[] columns = new String[] {
		        TextUtils.getText("hot_keys_editor.col.category", "分类"),
		        TextUtils.getText("hot_keys_editor.col.action", "动作"),
		        TextUtils.getText("hot_keys_editor.col.shortcut", "快捷键"),
		        TextUtils.getText("hot_keys_editor.col.action_key", "动作 ID") };

		public int getRowCount() {
			return rows.size();
		}

		public int getColumnCount() {
			return columns.length;
		}

		public String getColumnName(final int column) {
			return columns[column];
		}

		public Object getValueAt(final int rowIndex, final int columnIndex) {
			final HotKeyRow row = (HotKeyRow) rows.get(rowIndex);
			switch (columnIndex) {
				case 0:
					return row.category;
				case 1:
					return row.label;
				case 2:
					return formatStroke(row.keyStroke);
				case 3:
					return row.actionKey;
				default:
					return "";
			}
		}
	}

	private static final class HotKeyRow {
		String actionKey;
		String category = "";
		String label = "";
		AFreeplaneAction action;
		KeyStroke keyStroke;
	}
}
