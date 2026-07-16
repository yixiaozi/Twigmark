package org.docear.plugin.core.quickcommand;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
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
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Global command palette: Shift+Space.
 */
final class QuickCommandDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static QuickCommandDialog current;

	private final JTextField input = new JTextField();
	private final DefaultListModel listModel = new DefaultListModel();
	private final JList list = new JList(listModel);
	private final JLabel hintLabel = new JLabel(" ");
	private boolean suppressFilter;

	QuickCommandDialog() {
		super((java.awt.Frame) null, "\u5feb\u901f\u547d\u4ee4", false);
		setAlwaysOnTop(true);
		buildUi();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setSize(new Dimension(720, 420));
		setMinimumSize(new Dimension(520, 280));
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
		final JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		input.setFont(input.getFont().deriveFont(Font.PLAIN, 18f));
		list.setFont(list.getFont().deriveFont(Font.PLAIN, 14f));
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		hintLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));

		content.add(input, BorderLayout.NORTH);
		content.add(new JScrollPane(list), BorderLayout.CENTER);
		content.add(hintLabel, BorderLayout.SOUTH);
		setContentPane(content);

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
			hintLabel.setText("@@ 图标节点：Enter 打开/添加子节点；Shift+Enter 添加提醒任务；allicons 重建索引");
		}
		else if (text.indexOf('@') >= 0) {
			hintLabel.setText("@ 导图：Enter 打开/添加节点；Shift+Enter 添加提醒任务；mindmaps 重建导图列表");
		}
		else {
			hintLabel.setText("输入关键字快速启动；@ 打开导图；@@ 打开图标节点；Esc 关闭");
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
}
