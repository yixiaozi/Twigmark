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
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
import org.freeplane.core.ui.PlatformHotKeyGuide;
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
 * {@link RibbonAcceleratorManager}; presets are stored per OS.
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
	private final JCheckBox platformNotesOnly = new JCheckBox();
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
		super(owner, TextUtils.getText("hot_keys_editor.title", "快捷键设置") + " · "
		        + PlatformHotKeyGuide.getPlatformDisplayName(), true);
		this.modeController = modeController;
		this.acceleratorManager = acceleratorManager;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		buildUi();
		reload();
		pack();
		setSize(Math.max(960, getWidth()), Math.max(640, getHeight()));
		setLocationRelativeTo(owner != null ? owner : UITools.getFrame());
	}

	private void buildUi() {
		final JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

		final JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		final JEditorPane legend = new JEditorPane("text/html", PlatformHotKeyGuide.buildEditorLegendHtml());
		legend.setEditable(false);
		legend.setOpaque(false);
		legend.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		legend.setAlignmentX(0f);
		top.add(legend);

		final JPanel filter = new JPanel(new BorderLayout(8, 0));
		filter.setAlignmentX(0f);
		searchField.putClientProperty("JTextField.placeholderText",
		        TextUtils.getText("hot_keys_editor.search", "搜索名称 / 快捷键 / 动作…"));
		filter.add(searchField, BorderLayout.CENTER);
		final JPanel checks = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		boundOnly.setText(TextUtils.getText("hot_keys_editor.bound_only", "仅显示已绑定"));
		boundOnly.setSelected(true);
		platformNotesOnly.setText(TextUtils.getText("hot_keys_editor.platform_notes_only", "仅跨平台差异"));
		checks.add(boundOnly);
		checks.add(platformNotesOnly);
		filter.add(checks, BorderLayout.EAST);
		top.add(filter);
		root.add(top, BorderLayout.NORTH);

		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setRowHeight(Math.max(22, table.getRowHeight()));
		table.getTableHeader().setReorderingAllowed(false);
		sorter = new TableRowSorter(tableModel);
		table.setRowSorter(sorter);
		table.getColumnModel().getColumn(0).setPreferredWidth(150);
		table.getColumnModel().getColumn(1).setPreferredWidth(200);
		table.getColumnModel().getColumn(2).setPreferredWidth(120);
		table.getColumnModel().getColumn(3).setPreferredWidth(280);
		table.getColumnModel().getColumn(4).setPreferredWidth(110);
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
		final JButton mapBtn = new JButton(TextUtils.getText("hot_keys_editor.platform_map", "平台键位对照…"));
		final JButton editBtn = new JButton(TextUtils.getText("hot_keys_editor.edit", "修改…"));
		final JButton clearBtn = new JButton(TextUtils.getText("hot_keys_editor.clear", "清除"));
		final JButton refreshBtn = new JButton(TextUtils.getText("hot_keys_editor.refresh", "刷新"));
		final JButton closeBtn = new JButton(TextUtils.getText("ok", "确定"));
		mapBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				showPlatformMapDialog();
			}
		});
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
		buttons.add(mapBtn);
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
		final ActionListener checkListener = new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				applyFilter();
			}
		};
		boundOnly.addActionListener(checkListener);
		platformNotesOnly.addActionListener(checkListener);

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

	private void showPlatformMapDialog() {
		final StringBuffer sb = new StringBuffer();
		sb.append("<html><body style='width:520px;font-family:sans-serif'>");
		sb.append("<h3>修饰键对应</h3><ul>");
		sb.append("<li><b>Ctrl</b>（Windows/Linux）↔ <b>⌘ Command</b>（Mac）</li>");
		sb.append("<li><b>Alt</b>（Windows/Linux）↔ <b>⌥ Option</b>（Mac）</li>");
		sb.append("<li><b>Shift</b> ↔ <b>⇧ Shift</b>（各平台相同）</li>");
		sb.append("</ul><h3>Mac 没有 / 易冲突的键 → 默认替代</h3><table border='1' cellpadding='4' cellspacing='0'>");
		sb.append("<tr><th>Windows / Linux</th><th>动作</th><th>Mac 默认</th></tr>");
		sb.append("<tr><td>Insert</td><td>新建子节点</td><td>Tab</td></tr>");
		sb.append("<tr><td>⇧Insert</td><td>新建父节点</td><td>⇧Tab</td></tr>");
		sb.append("<tr><td>Alt+⇧Insert</td><td>总结节点</td><td>⌘⇧I</td></tr>");
		sb.append("<tr><td>Alt+Space</td><td>切换导图</td><td>⌘⇧O（避开输入法）</td></tr>");
		sb.append("<tr><td>Ctrl+⇧Space</td><td>快速捕获</td><td>⌘⇧Space</td></tr>");
		sb.append("<tr><td>⇧Space</td><td>快速命令</td><td>⌘⇧.</td></tr>");
		sb.append("</table>");
		sb.append("<p>Home / End / PgUp / PgDn 在 Mac 笔记本上通常需按 <b>Fn</b>。</p>");
		sb.append("<p>本机配置文件：<code>ribbons/")
		        .append(PlatformHotKeyGuide.getAcceleratorFileName())
		        .append("</code>（按操作系统分开，同步目录下互不覆盖）。</p>");
		sb.append("</body></html>");
		final JEditorPane pane = new JEditorPane("text/html", sb.toString());
		pane.setEditable(false);
		JOptionPane.showMessageDialog(this, new JScrollPane(pane),
		        TextUtils.getText("hot_keys_editor.platform_map", "平台键位对照"), JOptionPane.INFORMATION_MESSAGE);
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
				final int note = (rb.platformNote.length() == 0 ? 0 : 1) - (ra.platformNote.length() == 0 ? 0 : 1);
				if (note != 0) {
					return note;
				}
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
		row.platformNote = platformNoteFor(row.actionKey, row.keyStroke);
	}

	private KeyStroke resolveStroke(final AFreeplaneAction action) {
		if (acceleratorManager != null) {
			return acceleratorManager.getAccelerator(action.getKey());
		}
		return null;
	}

	static String platformNoteFor(final String actionKey, final KeyStroke stroke) {
		final String macAlt = (String) PlatformHotKeyGuide.getMacDefaultAlternatives().get(actionKey);
		if (Compat.isMacOsX()) {
			if (PlatformHotKeyGuide.usesMacUnavailableKey(stroke)) {
				return macAlt != null
				        ? TextUtils.getText("hot_keys_editor.note.insert_mac", "Mac 无 Insert → 建议 ") + formatAlt(macAlt)
				        : TextUtils.getText("hot_keys_editor.note.insert_generic", "Mac 无 Insert，请改绑");
			}
			if (PlatformHotKeyGuide.isAltSpace(stroke)) {
				return TextUtils.getText("hot_keys_editor.note.alt_space", "与输入法冲突 → 建议 ⌘⇧O");
			}
			if (macAlt != null && stroke == null) {
				return TextUtils.getText("hot_keys_editor.note.mac_default", "Mac 默认 ") + formatAlt(macAlt);
			}
			return "";
		}
		// Windows / Linux: explain what Mac will use
		if (PlatformHotKeyGuide.usesMacUnavailableKey(stroke) && macAlt != null) {
			return TextUtils.getText("hot_keys_editor.note.win_insert", "Mac 无此键 → 默认 ") + formatAlt(macAlt);
		}
		if (PlatformHotKeyGuide.isAltSpace(stroke)) {
			return TextUtils.getText("hot_keys_editor.note.win_alt_space", "Mac 上改为 ⌘⇧O");
		}
		if (macAlt != null && stroke != null) {
			final KeyStroke macStroke = RibbonAcceleratorManager.parseKeyStroke(macAlt);
			if (macStroke != null && !macStroke.equals(stroke)) {
				return TextUtils.getText("hot_keys_editor.note.mac_uses", "Mac 默认 ") + formatAlt(macAlt);
			}
		}
		return "";
	}

	/** Format a Mac-alternative stroke for display without remapping meta↔control. */
	private static String formatAlt(final String strokeString) {
		if (strokeString == null) {
			return "";
		}
		final KeyStroke ks = KeyStroke.getKeyStroke(strokeString);
		if (ks == null) {
			return strokeString;
		}
		return formatMacStroke(ks);
	}

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
		final boolean onlyNotes = platformNotesOnly.isSelected();
		sorter.setRowFilter(new RowFilter() {
			public boolean include(final Entry entry) {
				final HotKeyRow row = (HotKeyRow) rows.get(((Integer) entry.getIdentifier()).intValue());
				if (onlyBound && row.keyStroke == null) {
					return false;
				}
				if (onlyNotes && (row.platformNote == null || row.platformNote.length() == 0)) {
					return false;
				}
				if (q.length() == 0) {
					return true;
				}
				final String stroke = row.keyStroke == null ? "" : formatStroke(row.keyStroke).toLowerCase();
				final String note = row.platformNote == null ? "" : row.platformNote.toLowerCase();
				return row.label.toLowerCase().indexOf(q) >= 0 || row.category.toLowerCase().indexOf(q) >= 0
				        || row.actionKey.toLowerCase().indexOf(q) >= 0 || stroke.indexOf(q) >= 0
				        || note.indexOf(q) >= 0;
			}
		});
		updateStatus();
	}

	private void updateStatus() {
		final int visible = table.getRowCount();
		final int bound = countBound();
		final int notes = countNotes();
		final String file = PlatformHotKeyGuide.getAcceleratorFileName();
		statusLabel.setText("显示 " + visible + " · 已绑定 " + bound + " · 跨平台差异 " + notes + " · "
		        + PlatformHotKeyGuide.getPlatformDisplayName() + " · " + file);
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

	private int countNotes() {
		int n = 0;
		for (int i = 0; i < rows.size(); i++) {
			final String note = ((HotKeyRow) rows.get(i)).platformNote;
			if (note != null && note.length() > 0) {
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
		refreshStrokes();
		reselect(row.actionKey);
	}

	private void clearSelected() {
		final HotKeyRow row = selectedRow();
		if (row == null || row.action == null || acceleratorManager == null) {
			return;
		}
		acceleratorManager.clearAccelerator(row.action);
		refreshStrokes();
		reselect(row.actionKey);
	}

	private void refreshStrokes() {
		for (int i = 0; i < rows.size(); i++) {
			final HotKeyRow r = (HotKeyRow) rows.get(i);
			r.keyStroke = resolveStroke(r.action);
			r.platformNote = platformNoteFor(r.actionKey, r.keyStroke);
		}
		tableModel.fireTableDataChanged();
		applyFilter();
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
		        TextUtils.getText("hot_keys_editor.col.shortcut", "本机快捷键"),
		        TextUtils.getText("hot_keys_editor.col.platform_note", "平台说明"),
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
					return row.platformNote;
				case 4:
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
		String platformNote = "";
		AFreeplaneAction action;
		KeyStroke keyStroke;
	}
}
