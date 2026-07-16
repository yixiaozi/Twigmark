package org.docear.plugin.core.quickcommand;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Global command palette: Shift+Space. Minimal launcher-style overlay.
 */
final class QuickCommandDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static QuickCommandDialog current;

	private static final Color PANEL = new Color(0xFA, 0xFB, 0xFC);
	private static final Color TEXT = new Color(0x11, 0x18, 0x27);
	private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
	private static final Color ACCENT = new Color(0x0F, 0x76, 0x6E);
	private static final Color ACCENT_SOFT = new Color(0xCC, 0xF2, 0xE9);
	private static final Color ROW_ALT = new Color(0xF3, 0xF4, 0xF6);
	private static final Color HAIRLINE = new Color(0xE5, 0xE7, 0xEB);
	private static final String MATCH_RED = "#DC2626";
	private static final String HIGHLIGHT_QUERY_KEY = "quickcommand.highlightQuery";

	private final JTextField input = new JTextField();
	private final DefaultListModel listModel = new DefaultListModel();
	private final JList list = new JList(listModel);
	private final JLabel hintLabel = new JLabel(" ");
	private boolean suppressFilter;

	QuickCommandDialog() {
		super((java.awt.Frame) null, "", false);
		setUndecorated(true);
		setAlwaysOnTop(true);
		buildUi();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		// Content-only footprint roughly matching the old inner card width.
		setSize(new Dimension(720, 480));
		setMinimumSize(new Dimension(560, 320));
		getRootPane().setBorder(BorderFactory.createLineBorder(new Color(0xD1, 0xD5, 0xDB), 1));
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(final WindowEvent e) {
				centerOnPointerScreen();
				focusInput();
			}

			@Override
			public void windowClosed(final WindowEvent e) {
				if (current == QuickCommandDialog.this) {
					current = null;
				}
			}
		});
	}

	private void buildUi() {
		final JPanel root = new JPanel(new BorderLayout(0, 0));
		root.setBackground(PANEL);
		root.setBorder(new EmptyBorder(10, 14, 8, 10));

		input.setFont(preferUiFont(20f));
		input.setForeground(TEXT);
		input.setCaretColor(ACCENT);
		input.setBorder(new EmptyBorder(2, 2, 8, 2));
		input.setBackground(PANEL);
		input.setOpaque(true);

		final JPanel inputWrap = new JPanel(new BorderLayout());
		inputWrap.setOpaque(true);
		inputWrap.setBackground(PANEL);
		inputWrap.setBorder(BorderFactory.createCompoundBorder(
		        BorderFactory.createMatteBorder(0, 0, 1, 0, HAIRLINE),
		        new EmptyBorder(0, 0, 10, 0)));
		inputWrap.add(input, BorderLayout.CENTER);

		list.setFont(preferUiFont(15f));
		list.setBackground(PANEL);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setFixedCellHeight(40);
		list.setCellRenderer(new CandidateRenderer());
		list.setBorder(null);

		final JScrollPane scroll = new JScrollPane(list);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(PANEL);
		scroll.setBackground(PANEL);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(28);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(5, 0));
		scroll.getVerticalScrollBar().setUI(new ThinScrollBarUI());
		scroll.getVerticalScrollBar().setOpaque(false);

		hintLabel.setFont(preferUiFont(12f));
		hintLabel.setForeground(MUTED);
		hintLabel.setBorder(new EmptyBorder(6, 2, 2, 2));

		root.add(inputWrap, BorderLayout.NORTH);
		root.add(scroll, BorderLayout.CENTER);
		root.add(hintLabel, BorderLayout.SOUTH);
		setContentPane(root);

		input.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(final DocumentEvent e) {
				refreshSuggestions();
			}

			public void removeUpdate(final DocumentEvent e) {
				refreshSuggestions();
			}

			public void changedUpdate(final DocumentEvent e) {
				refreshSuggestions();
			}
		});

		input.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(final KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_DOWN) {
					moveList(1);
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_UP) {
					moveList(-1);
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					submit((e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0);
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					dispose();
					e.consume();
				}
			}
		});

		list.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(final KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					submit((e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0);
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					dispose();
					e.consume();
				}
			}
		});
		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() >= 2) {
					submit(false);
				}
			}
		});

		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
		        "quickcommand.cancel");
		getRootPane().getActionMap().put("quickcommand.cancel", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent e) {
				dispose();
			}
		});

		refreshSuggestions();
		updateHint();
	}

	private static Font preferUiFont(final float size) {
		final String[] prefer = new String[] { "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC",
		        "SansSerif" };
		for (int i = 0; i < prefer.length; i++) {
			final Font font = new Font(prefer[i], Font.PLAIN, Math.round(size));
			if (font.canDisplay('快')) {
				return font.deriveFont(size);
			}
		}
		return new JLabel().getFont().deriveFont(size);
	}

	private void moveList(final int delta) {
		if (listModel.isEmpty()) {
			return;
		}
		int index = list.getSelectedIndex();
		if (index < 0) {
			index = 0;
		}
		else {
			index = Math.max(0, Math.min(listModel.size() - 1, index + delta));
		}
		list.setSelectedIndex(index);
		list.ensureIndexIsVisible(index);
	}

	private void refreshSuggestions() {
		if (suppressFilter) {
			return;
		}
		final String text = input.getText();
		list.putClientProperty(HIGHLIGHT_QUERY_KEY, extractHighlightQuery(text));
		final List suggestions = QuickCommandController.suggest(text);
		listModel.clear();
		for (int i = 0; i < suggestions.size(); i++) {
			listModel.addElement(suggestions.get(i));
		}
		if (!listModel.isEmpty()) {
			list.setSelectedIndex(0);
		}
		updateHint();
	}

	/** Query portion used for red match highlighting (@ / @@ / # / * suffix, else whole input). */
	private static String extractHighlightQuery(final String raw) {
		if (raw == null) {
			return "";
		}
		final int atAt = raw.indexOf("@@");
		if (atAt >= 0) {
			return raw.substring(atAt + 2).trim();
		}
		final int star = raw.lastIndexOf('*');
		if (star >= 0) {
			return raw.substring(star + 1).trim();
		}
		final int hash = raw.lastIndexOf('#');
		if (hash >= 0) {
			return raw.substring(hash + 1).trim();
		}
		final int at = raw.lastIndexOf('@');
		if (at >= 0) {
			return raw.substring(at + 1).trim();
		}
		return raw.trim();
	}

	private void updateHint() {
		final String text = input.getText() == null ? "" : input.getText();
		if (text.indexOf("@@") >= 0) {
			hintLabel.setText("@@ 图标节点 · Enter 打开/添加 · Shift+Enter 提醒 · 空查询仅最近使用 · Esc 关闭");
		}
		else if (text.indexOf('*') >= 0) {
			hintLabel.setText("* 全库节点 · Enter 打开 · 左侧写文字可添加子节点 · Esc 关闭");
		}
		else if (text.indexOf('#') >= 0) {
			hintLabel.setText("# 系统内文件 · Enter 打开 · 空查询最近文件 · Esc 关闭");
		}
		else if (text.indexOf('@') >= 0) {
			hintLabel.setText("@ 导图 · 拼音/首字母/混输均可 · 结果按修改时间倒序 · Esc 关闭");
		}
		else if (text.trim().length() == 0) {
			hintLabel.setText("最近选择 · @ 导图 · @@ 图标节点 · # 文件 · * 全库节点 · Esc 关闭");
		}
		else {
			hintLabel.setText("快速启动 · 支持拼音模糊 · Esc 关闭");
		}
	}

	private void submit(final boolean asTask) {
		final QuickCommandCandidate selected = (QuickCommandCandidate) list.getSelectedValue();
		final String text = input.getText();
		final String completed = QuickCommandController.completeIntoInput(text, selected);
		if (completed != null) {
			suppressFilter = true;
			input.setText(completed);
			suppressFilter = false;
			refreshSuggestions();
			input.setCaretPosition(input.getText().length());
			return;
		}
		final boolean close = QuickCommandController.execute(text, selected, asTask);
		if (close) {
			dispose();
		}
		else {
			refreshSuggestions();
			hintLabel.setText("已执行。可继续输入，或 Esc 关闭。");
		}
	}

	private void centerOnPointerScreen() {
		final Rectangle bounds = getBounds();
		java.awt.Point mouseLocation;
		try {
			mouseLocation = java.awt.MouseInfo.getPointerInfo().getLocation();
		}
		catch (Exception e) {
			mouseLocation = new java.awt.Point(bounds.x, bounds.y);
		}
		final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		Rectangle screenBounds = ge.getMaximumWindowBounds();
		final java.awt.GraphicsDevice[] screens = ge.getScreenDevices();
		for (int i = 0; i < screens.length; i++) {
			final Rectangle screenRect = screens[i].getDefaultConfiguration().getBounds();
			if (screenRect.contains(mouseLocation)) {
				screenBounds = screenRect;
				break;
			}
		}
		setLocation(screenBounds.x + (screenBounds.width - bounds.width) / 2,
		        screenBounds.y + (screenBounds.height - bounds.height) / 3);
	}

	private void focusInput() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				input.requestFocusInWindow();
				input.selectAll();
			}
		});
	}

	static void openDialog() {
		if (current != null && current.isDisplayable()) {
			if (current.isVisible() && current.isFocused()) {
				current.dispose();
				return;
			}
			current.setVisible(true);
			current.toFront();
			current.centerOnPointerScreen();
			current.input.setText("");
			current.refreshSuggestions();
			current.focusInput();
			return;
		}
		QuickCommandIndex.getInstance().ensureMaps();
		QuickCommandIndex.getInstance().ensureLaunch();
		QuickCommandIndex.getInstance().ensureIconsAsync();
		QuickCommandIndex.getInstance().ensureFilesAsync();
		QuickCommandIndex.getInstance().ensureAllNodesAsync();
		current = new QuickCommandDialog();
		current.setVisible(true);
	}

	private static final class ThinScrollBarUI extends BasicScrollBarUI {
		protected void configureScrollBarColors() {
			thumbColor = new Color(0xC4, 0xC4, 0xC4);
			thumbDarkShadowColor = thumbColor;
			thumbHighlightColor = thumbColor;
			thumbLightShadowColor = thumbColor;
			trackColor = PANEL;
			trackHighlightColor = PANEL;
		}

		protected JButton createDecreaseButton(final int orientation) {
			return zeroButton();
		}

		protected JButton createIncreaseButton(final int orientation) {
			return zeroButton();
		}

		private JButton zeroButton() {
			final JButton button = new JButton();
			button.setPreferredSize(new Dimension(0, 0));
			button.setMinimumSize(new Dimension(0, 0));
			button.setMaximumSize(new Dimension(0, 0));
			button.setOpaque(false);
			button.setBorder(null);
			return button;
		}

		protected void paintTrack(final Graphics g, final JComponent c, final Rectangle trackBounds) {
			g.setColor(PANEL);
			g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
		}

		protected void paintThumb(final Graphics g, final JComponent c, final Rectangle thumbBounds) {
			if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
				return;
			}
			final Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(thumbColor);
			final int gap = 1;
			g2.fillRoundRect(thumbBounds.x + gap, thumbBounds.y + gap, Math.max(2, thumbBounds.width - gap * 2),
			        Math.max(8, thumbBounds.height - gap * 2), 6, 6);
			g2.dispose();
		}
	}

	private static final class CandidateRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		public Component getListCellRendererComponent(final JList list, final Object value, final int index,
		        final boolean isSelected, final boolean cellHasFocus) {
			final JPanel row = new JPanel(new BorderLayout(10, 0));
			row.setOpaque(true);
			row.setBorder(new EmptyBorder(0, 4, 0, 8));

			final QuickCommandCandidate item = value instanceof QuickCommandCandidate ? (QuickCommandCandidate) value
			        : null;
			final String label = item != null ? item.label : String.valueOf(value);
			final String detail = item != null ? item.detail : "";
			final String badge = item != null ? item.kindBadge() : "";
			final Object queryObj = list.getClientProperty(HIGHLIGHT_QUERY_KEY);
			final String query = queryObj == null ? "" : String.valueOf(queryObj);

			final JLabel badgeLabel = new JLabel(badge);
			badgeLabel.setFont(preferUiFont(11f));
			badgeLabel.setForeground(isSelected ? ACCENT : MUTED);
			badgeLabel.setHorizontalAlignment(SwingConstants.CENTER);
			badgeLabel.setPreferredSize(new Dimension(36, 28));

			final String highlighted = PinyinMatch.highlightHtml(label, query, MATCH_RED);
			final boolean useHtml = query.length() > 0 && highlighted.startsWith("<html>");
			final String mainText;
			if (useHtml) {
				final String body = highlighted.substring("<html>".length());
				mainText = item != null && item.recent ? "<html>★ " + body : highlighted;
			}
			else {
				mainText = item != null && item.recent ? "★ " + label : label;
			}
			final JLabel main = new JLabel(mainText);
			main.setFont(preferUiFont(15f));
			main.setForeground(TEXT);

			final JLabel sub = new JLabel(detail == null ? "" : detail);
			sub.setFont(preferUiFont(12f));
			sub.setForeground(MUTED);

			final JPanel texts = new JPanel(new BorderLayout(0, 0));
			texts.setOpaque(false);
			texts.add(main, BorderLayout.CENTER);
			if (item != null && (item.kind == QuickCommandCandidate.Kind.ICON_NODE
			        || item.kind == QuickCommandCandidate.Kind.FILE) && detail != null && detail.length() > 0) {
				texts.add(sub, BorderLayout.EAST);
			}
			else if (item != null && item.kind == QuickCommandCandidate.Kind.HINT) {
				texts.add(sub, BorderLayout.EAST);
			}

			row.add(badgeLabel, BorderLayout.WEST);
			row.add(texts, BorderLayout.CENTER);
			row.setBackground(isSelected ? ACCENT_SOFT : (index % 2 == 0 ? PANEL : ROW_ALT));
			return row;
		}
	}
}
