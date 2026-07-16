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

/**
 * Global command palette: Shift+Space.
 */
final class QuickCommandDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static QuickCommandDialog current;

	private static final Color BG = new Color(0xF4, 0xF6, 0xF8);
	private static final Color PANEL = Color.WHITE;
	private static final Color TEXT = new Color(0x1F, 0x29, 0x37);
	private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
	private static final Color ACCENT = new Color(0x0F, 0x76, 0x6E);
	private static final Color ACCENT_SOFT = new Color(0xD1, 0xFA, 0xE5);
	private static final Color ROW_HOVER = new Color(0xEC, 0xFD, 0xF5);
	private static final Color BORDER = new Color(0xE5, 0xE7, 0xEB);

	private final JTextField input = new JTextField();
	private final DefaultListModel listModel = new DefaultListModel();
	private final JList list = new JList(listModel);
	private final JLabel hintLabel = new JLabel(" ");
	private boolean suppressFilter;

	QuickCommandDialog() {
		super((java.awt.Frame) null, "\u5feb\u901f\u547d\u4ee4", false);
		setAlwaysOnTop(true);
		setUndecorated(false);
		buildUi();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setSize(new Dimension(640, 460));
		setMinimumSize(new Dimension(480, 320));
		getContentPane().setBackground(BG);
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
		final JPanel shell = new JPanel(new BorderLayout(0, 0));
		shell.setBackground(BG);
		shell.setBorder(new EmptyBorder(14, 14, 12, 14));

		final JPanel card = new JPanel(new BorderLayout(0, 0));
		card.setBackground(PANEL);
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
		        new EmptyBorder(14, 14, 10, 14)));

		final JLabel title = new JLabel("快速命令");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
		title.setForeground(MUTED);
		title.setBorder(new EmptyBorder(0, 2, 8, 2));

		input.setFont(preferUiFont(18f));
		input.setForeground(TEXT);
		input.setCaretColor(ACCENT);
		input.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
		        new EmptyBorder(10, 12, 10, 12)));
		input.setBackground(new Color(0xFA, 0xFB, 0xFC));

		list.setFont(preferUiFont(15f));
		list.setBackground(PANEL);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setFixedCellHeight(44);
		list.setCellRenderer(new CandidateRenderer());
		list.setBorder(null);

		final JScrollPane scroll = new JScrollPane(list);
		scroll.setBorder(BorderFactory.createEmptyBorder(10, 0, 8, 0));
		scroll.getViewport().setBackground(PANEL);
		scroll.setBackground(PANEL);

		hintLabel.setFont(preferUiFont(12f));
		hintLabel.setForeground(MUTED);
		hintLabel.setBorder(new EmptyBorder(4, 2, 0, 2));

		final JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(title, BorderLayout.NORTH);
		north.add(input, BorderLayout.CENTER);

		card.add(north, BorderLayout.NORTH);
		card.add(scroll, BorderLayout.CENTER);
		card.add(hintLabel, BorderLayout.SOUTH);
		shell.add(card, BorderLayout.CENTER);
		setContentPane(shell);

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
			if (!"Dialog".equalsIgnoreCase(font.getFamily()) || "SansSerif".equals(prefer[i])) {
				if (font.canDisplay('快')) {
					return font.deriveFont(size);
				}
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

	private void updateHint() {
		final String text = input.getText() == null ? "" : input.getText();
		if (text.indexOf("@@") >= 0) {
			hintLabel.setText("@@ 图标节点 · Enter 打开/添加 · Shift+Enter 提醒任务 · 空查询显示最近使用");
		}
		else if (text.indexOf('@') >= 0) {
			hintLabel.setText("@ 导图 · Enter 打开/添加 · Shift+Enter 提醒任务 · 支持拼音/首字母 · 空查询显示最近");
		}
		else if (text.trim().length() == 0) {
			hintLabel.setText("最近使用的导图可直接 Enter 打开 · 输入 @ / @@ / 应用名继续");
		}
		else {
			hintLabel.setText("快速启动 · @ 打开导图 · @@ 打开图标节点 · Esc 关闭");
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
		current = new QuickCommandDialog();
		current.setVisible(true);
	}

	private static final class CandidateRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		public Component getListCellRendererComponent(final JList list, final Object value, final int index,
		        final boolean isSelected, final boolean cellHasFocus) {
			final JPanel row = new JPanel(new BorderLayout(10, 0)) {
				private static final long serialVersionUID = 1L;

				protected void paintComponent(final Graphics g) {
					final Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(getBackground());
					g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
					g2.dispose();
				}
			};
			row.setOpaque(false);
			row.setBorder(new EmptyBorder(4, 8, 4, 8));

			final QuickCommandCandidate item = value instanceof QuickCommandCandidate ? (QuickCommandCandidate) value
			        : null;
			final String label = item != null ? item.label : String.valueOf(value);
			final String detail = item != null ? item.detail : "";
			final String badge = item != null ? item.kindBadge() : "";

			final JLabel badgeLabel = new JLabel(badge);
			badgeLabel.setFont(preferUiFont(11f));
			badgeLabel.setForeground(isSelected ? ACCENT : MUTED);
			badgeLabel.setHorizontalAlignment(SwingConstants.CENTER);
			badgeLabel.setPreferredSize(new Dimension(40, 28));

			final JLabel main = new JLabel(item != null && item.recent ? "★ " + label : label);
			main.setFont(preferUiFont(15f));
			main.setForeground(TEXT);

			final JLabel sub = new JLabel(detail == null ? "" : detail);
			sub.setFont(preferUiFont(12f));
			sub.setForeground(MUTED);

			final JPanel texts = new JPanel(new BorderLayout(0, 1));
			texts.setOpaque(false);
			texts.add(main, BorderLayout.NORTH);
			if (detail != null && detail.length() > 0 && item != null && item.kind != QuickCommandCandidate.Kind.MAP) {
				texts.add(sub, BorderLayout.SOUTH);
			}
			else if (item != null && item.kind == QuickCommandCandidate.Kind.HINT) {
				texts.add(sub, BorderLayout.SOUTH);
			}

			row.add(badgeLabel, BorderLayout.WEST);
			row.add(texts, BorderLayout.CENTER);
			row.setBackground(isSelected ? ACCENT_SOFT : (index % 2 == 0 ? PANEL : ROW_HOVER));
			return row;
		}
	}
}
