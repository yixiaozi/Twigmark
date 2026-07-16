package org.docear.plugin.core.mapswitcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.view.swing.map.MapView;

/**
 * Alt+Space overlay: browse open maps with ←/→, Enter to switch, Delete to close.
 */
final class MapSwitcherDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static MapSwitcherDialog current;

	private static final Color PANEL = new Color(0xF8, 0xFA, 0xFC);
	private static final Color TEXT = new Color(0x11, 0x18, 0x27);
	private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
	private static final Color CARD = new Color(0xFF, 0xFF, 0xFF);
	private static final Color CARD_BORDER = new Color(0xE5, 0xE7, 0xEB);
	private static final Color SELECTED_BG = new Color(0xCC, 0xF2, 0xE9);
	private static final Color SELECTED_BORDER = new Color(0x0F, 0x76, 0x6E);
	private static final Color HAIRLINE = new Color(0xD1, 0xD5, 0xDB);

	private final JPanel strip = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
	private final JLabel hintLabel = new JLabel(" ");
	private final List entries = new ArrayList();
	private int selectedIndex;
	private boolean closing;

	MapSwitcherDialog() {
		super((java.awt.Frame) null, "", false);
		setUndecorated(true);
		setAlwaysOnTop(true);
		buildUi();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		getRootPane().setBorder(BorderFactory.createLineBorder(HAIRLINE, 1));
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(final WindowEvent e) {
				centerOnPointerScreen();
				focusRoot();
			}

			@Override
			public void windowClosed(final WindowEvent e) {
				if (current == MapSwitcherDialog.this) {
					current = null;
				}
			}
		});
	}

	private void buildUi() {
		final JPanel root = new JPanel(new BorderLayout(0, 0));
		root.setBackground(PANEL);
		root.setBorder(new EmptyBorder(16, 18, 12, 18));

		final JLabel title = new JLabel("切换导图");
		title.setFont(preferUiFont(14f));
		title.setForeground(MUTED);
		title.setBorder(new EmptyBorder(0, 2, 10, 2));

		strip.setOpaque(false);
		strip.setBorder(new EmptyBorder(4, 0, 8, 0));

		hintLabel.setFont(preferUiFont(12f));
		hintLabel.setForeground(MUTED);
		hintLabel.setBorder(new EmptyBorder(8, 2, 0, 2));
		hintLabel.setText("← → 选择 · Enter 打开 · Delete 关闭 · Esc 取消");

		root.add(title, BorderLayout.NORTH);
		root.add(strip, BorderLayout.CENTER);
		root.add(hintLabel, BorderLayout.SOUTH);
		setContentPane(root);

		final JComponent glass = getRootPane();
		glass.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
		        "mapswitch.cancel");
		glass.getActionMap().put("mapswitch.cancel", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent e) {
				dispose();
			}
		});
		glass.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
		        "mapswitch.left");
		glass.getActionMap().put("mapswitch.left", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent e) {
				moveSelection(-1);
			}
		});
		glass.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
		        "mapswitch.right");
		glass.getActionMap().put("mapswitch.right", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent e) {
				moveSelection(1);
			}
		});
		glass.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
		        "mapswitch.open");
		glass.getActionMap().put("mapswitch.open", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent e) {
				activateSelected();
			}
		});
		glass.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
		        "mapswitch.close");
		glass.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0),
		        "mapswitch.close");
		glass.getActionMap().put("mapswitch.close", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent e) {
				closeSelected();
			}
		});
		// Repeat Alt+Space while open → step right (Alt+Tab style).
		glass.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
		        KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.ALT_DOWN_MASK), "mapswitch.right");

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(final KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_LEFT) {
					moveSelection(-1);
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
					moveSelection(1);
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					activateSelected();
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
					closeSelected();
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					dispose();
					e.consume();
				}
			}
		});
	}

	private void reload(final int preferIndex) {
		entries.clear();
		strip.removeAll();
		final Controller controller = Controller.getCurrentController();
		if (controller == null || controller.getMapViewManager() == null) {
			packAndLayout();
			return;
		}
		final IMapViewManager manager = controller.getMapViewManager();
		final List views = manager.getMapViewVector();
		final Component active = manager.getMapViewComponent();
		int activeIndex = 0;
		for (int i = 0; i < views.size(); i++) {
			final Object view = views.get(i);
			if (!(view instanceof MapView)) {
				continue;
			}
			final MapView mapView = (MapView) view;
			final String key = mapView.getName();
			if (key == null || key.length() == 0) {
				continue;
			}
			final Entry entry = new Entry(key, formatTitle(key), mapView == active);
			entries.add(entry);
			if (mapView == active) {
				activeIndex = entries.size() - 1;
			}
		}
		if (entries.isEmpty()) {
			final JLabel empty = new JLabel("没有已打开的导图");
			empty.setFont(preferUiFont(15f));
			empty.setForeground(MUTED);
			strip.add(empty);
			selectedIndex = 0;
			packAndLayout();
			return;
		}
		if (preferIndex >= 0 && preferIndex < entries.size()) {
			selectedIndex = preferIndex;
		}
		else if (entries.size() > 1) {
			// Alt+Tab style: land on the next map after the active one.
			selectedIndex = (activeIndex + 1) % entries.size();
		}
		else {
			selectedIndex = activeIndex;
		}
		rebuildCards();
		packAndLayout();
	}

	private void rebuildCards() {
		strip.removeAll();
		for (int i = 0; i < entries.size(); i++) {
			final Entry entry = (Entry) entries.get(i);
			final int index = i;
			final JPanel card = new JPanel(new BorderLayout(0, 4));
			card.setPreferredSize(new Dimension(132, 72));
			card.setOpaque(true);
			final boolean selected = i == selectedIndex;
			card.setBackground(selected ? SELECTED_BG : CARD);
			card.setBorder(BorderFactory.createCompoundBorder(
			        BorderFactory.createLineBorder(selected ? SELECTED_BORDER : CARD_BORDER, selected ? 2 : 1),
			        new EmptyBorder(10, 10, 10, 10)));

			final JLabel name = new JLabel(entry.title, SwingConstants.CENTER);
			name.setFont(preferUiFont(selected ? 15f : 14f));
			name.setForeground(TEXT);
			final JLabel mark = new JLabel(entry.active ? "当前" : " ", SwingConstants.CENTER);
			mark.setFont(preferUiFont(11f));
			mark.setForeground(selected ? SELECTED_BORDER : MUTED);
			card.add(name, BorderLayout.CENTER);
			card.add(mark, BorderLayout.SOUTH);

			card.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(final MouseEvent e) {
					selectedIndex = index;
					rebuildCards();
					packAndLayout();
					if (e.getClickCount() >= 2) {
						activateSelected();
					}
				}
			});
			strip.add(card);
		}
		strip.revalidate();
		strip.repaint();
	}

	private void moveSelection(final int delta) {
		if (entries.isEmpty()) {
			return;
		}
		final int size = entries.size();
		selectedIndex = ((selectedIndex + delta) % size + size) % size;
		rebuildCards();
		packAndLayout();
	}

	private void activateSelected() {
		if (closing || entries.isEmpty() || selectedIndex < 0 || selectedIndex >= entries.size()) {
			dispose();
			return;
		}
		final Entry entry = (Entry) entries.get(selectedIndex);
		closing = true;
		try {
			final IMapViewManager manager = Controller.getCurrentController().getMapViewManager();
			if (manager != null) {
				manager.changeToMapView(entry.key);
			}
		}
		catch (Exception e) {
			LogUtils.warn("MapSwitcher: could not switch to " + entry.key, e);
		}
		finally {
			dispose();
		}
	}

	private void closeSelected() {
		if (closing || entries.isEmpty() || selectedIndex < 0 || selectedIndex >= entries.size()) {
			return;
		}
		final Entry entry = (Entry) entries.get(selectedIndex);
		final int keepIndex = selectedIndex;
		setAlwaysOnTop(false);
		try {
			final Controller controller = Controller.getCurrentController();
			final IMapViewManager manager = controller.getMapViewManager();
			if (manager == null) {
				return;
			}
			final Component current = manager.getMapViewComponent();
			final String currentName = current == null ? null : current.getName();
			if (currentName == null || !entry.key.equals(currentName)) {
				manager.changeToMapView(entry.key);
			}
			controller.close(false);
		}
		catch (Exception e) {
			LogUtils.warn("MapSwitcher: could not close " + entry.key, e);
		}
		finally {
			if (isDisplayable()) {
				setAlwaysOnTop(true);
			}
		}
		if (!isDisplayable()) {
			return;
		}
		final int nextPrefer = Math.max(0, keepIndex);
		reload(nextPrefer);
		if (entries.isEmpty()) {
			dispose();
			return;
		}
		toFront();
		focusRoot();
	}

	private void packAndLayout() {
		pack();
		final Dimension size = getSize();
		final int width = Math.max(420, Math.min(960, size.width + 8));
		final int height = Math.max(140, size.height);
		setSize(new Dimension(width, height));
		centerOnPointerScreen();
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

	private void focusRoot() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				requestFocusInWindow();
				getRootPane().requestFocusInWindow();
			}
		});
	}

	private static Font preferUiFont(final float size) {
		final String[] prefer = new String[] { "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
		        "Noto Sans CJK SC", "SansSerif" };
		for (int i = 0; i < prefer.length; i++) {
			final Font font = new Font(prefer[i], Font.PLAIN, Math.round(size));
			if (font.canDisplay('导')) {
				return font.deriveFont(size);
			}
		}
		return new JLabel().getFont().deriveFont(size);
	}

	private static String formatTitle(final String name) {
		if (name == null) {
			return "";
		}
		final String lower = name.toLowerCase();
		if (lower.endsWith(".mm")) {
			return name.substring(0, name.length() - 3);
		}
		return name;
	}

	static void openDialog() {
		if (current != null && current.isDisplayable()) {
			if (current.isVisible()) {
				current.moveSelection(1);
				current.toFront();
				current.focusRoot();
				return;
			}
			current.dispose();
		}
		final Controller controller = Controller.getCurrentController();
		if (controller == null || controller.getMapViewManager() == null) {
			return;
		}
		final List views = controller.getMapViewManager().getMapViewVector();
		if (views == null || views.isEmpty()) {
			return;
		}
		current = new MapSwitcherDialog();
		current.reload(-1);
		current.setVisible(true);
	}

	private static final class Entry {
		final String key;
		final String title;
		final boolean active;

		Entry(final String key, final String title, final boolean active) {
			this.key = key;
			this.title = title;
			this.active = active;
		}
	}
}
