package org.docear.plugin.core.mapswitcher;

import org.freeplane.core.ui.theme.DocearUiTheme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
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
 * Alt+Space overlay: compact open-map switcher with ←→/↑↓, Enter, Delete.
 */
final class MapSwitcherDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static MapSwitcherDialog current;

	private static final Color PANEL = DocearUiTheme.CANVAS;
	private static final Color TEXT = DocearUiTheme.TEXT;
	private static final Color MUTED = DocearUiTheme.TEXT_MUTED;
	private static final Color CARD = DocearUiTheme.SURFACE;
	private static final Color CARD_BORDER = DocearUiTheme.HAIRLINE;
	private static final Color SELECTED_BG = DocearUiTheme.ACCENT_WASH;
	private static final Color SELECTED_BORDER = DocearUiTheme.ACCENT_DEEP;
	private static final Color HAIRLINE = DocearUiTheme.HAIRLINE;

	private static final int CARD_H_GAP = 6;
	private static final int CARD_V_GAP = 6;
	private static final int CARD_HEIGHT = 34;
	private static final int CARD_MIN_WIDTH = 64;
	private static final int CARD_MAX_WIDTH = 140;
	private static final int DIALOG_MAX_WIDTH = 920;

	private final JPanel strip = new JPanel(new FlowLayout(FlowLayout.CENTER, CARD_H_GAP, CARD_V_GAP));
	private final JLabel hintLabel = new JLabel(" ");
	private final List entries = new ArrayList();
	private int selectedIndex;
	private boolean closing;
	/** Columns in the first wrapped row; recomputed after layout. */
	private int columnsPerRow = 1;

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
		root.setBorder(new EmptyBorder(10, 12, 8, 12));

		final JLabel title = new JLabel("切换导图");
		title.setFont(preferUiFont(12f));
		title.setForeground(MUTED);
		title.setBorder(new EmptyBorder(0, 2, 6, 2));

		strip.setOpaque(false);
		strip.setBorder(new EmptyBorder(0, 0, 2, 0));

		hintLabel.setFont(preferUiFont(11f));
		hintLabel.setForeground(MUTED);
		hintLabel.setBorder(new EmptyBorder(6, 2, 0, 2));
		hintLabel.setText("← → ↑ ↓ 选择 · Enter 打开 · Delete 关闭 · Esc 取消");

		root.add(title, BorderLayout.NORTH);
		root.add(strip, BorderLayout.CENTER);
		root.add(hintLabel, BorderLayout.SOUTH);
		setContentPane(root);

		bindKey(KeyEvent.VK_ESCAPE, "mapswitch.cancel", new Runnable() {
			public void run() {
				dispose();
			}
		});
		bindKey(KeyEvent.VK_LEFT, "mapswitch.left", new Runnable() {
			public void run() {
				moveSelection(-1);
			}
		});
		bindKey(KeyEvent.VK_RIGHT, "mapswitch.right", new Runnable() {
			public void run() {
				moveSelection(1);
			}
		});
		bindKey(KeyEvent.VK_UP, "mapswitch.up", new Runnable() {
			public void run() {
				moveVertical(-1);
			}
		});
		bindKey(KeyEvent.VK_DOWN, "mapswitch.down", new Runnable() {
			public void run() {
				moveVertical(1);
			}
		});
		bindKey(KeyEvent.VK_ENTER, "mapswitch.open", new Runnable() {
			public void run() {
				activateSelected();
			}
		});
		bindKey(KeyEvent.VK_DELETE, "mapswitch.close", new Runnable() {
			public void run() {
				closeSelected();
			}
		});
		bindKey(KeyEvent.VK_BACK_SPACE, "mapswitch.close", new Runnable() {
			public void run() {
				closeSelected();
			}
		});
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
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
				else if (e.getKeyCode() == KeyEvent.VK_UP) {
					moveVertical(-1);
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
					moveVertical(1);
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

	private void bindKey(final int keyCode, final String name, final Runnable action) {
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, 0), name);
		getRootPane().getActionMap().put(name, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent e) {
				action.run();
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
			empty.setFont(preferUiFont(13f));
			empty.setForeground(MUTED);
			strip.add(empty);
			selectedIndex = 0;
			columnsPerRow = 1;
			packAndLayout();
			return;
		}
		if (preferIndex >= 0 && preferIndex < entries.size()) {
			selectedIndex = preferIndex;
		}
		else if (entries.size() > 1) {
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
		final Font nameFont = preferUiFont(12f);
		final FontMetrics metrics = getFontMetrics(nameFont);
		for (int i = 0; i < entries.size(); i++) {
			final Entry entry = (Entry) entries.get(i);
			final int index = i;
			final boolean selected = i == selectedIndex;
			final JPanel card = new JPanel(new BorderLayout(0, 0));
			card.setOpaque(true);
			card.setBackground(selected ? SELECTED_BG : CARD);
			card.setBorder(BorderFactory.createCompoundBorder(
			        BorderFactory.createLineBorder(selected ? SELECTED_BORDER : CARD_BORDER, selected ? 2 : 1),
			        new EmptyBorder(4, 8, 4, 8)));

			final String labelText = entry.active ? entry.title + " ·" : entry.title;
			final JLabel name = new JLabel(labelText, SwingConstants.CENTER);
			name.setFont(nameFont);
			name.setForeground(selected ? SELECTED_BORDER : TEXT);
			card.add(name, BorderLayout.CENTER);

			int textW = metrics.stringWidth(labelText) + 20;
			if (textW < CARD_MIN_WIDTH) {
				textW = CARD_MIN_WIDTH;
			}
			if (textW > CARD_MAX_WIDTH) {
				textW = CARD_MAX_WIDTH;
				name.setToolTipText(entry.title);
			}
			card.setPreferredSize(new Dimension(textW, CARD_HEIGHT));

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

	private void moveVertical(final int rowDelta) {
		if (entries.isEmpty()) {
			return;
		}
		refreshColumnsPerRow();
		final int cols = Math.max(1, columnsPerRow);
		final int size = entries.size();
		int next = selectedIndex + rowDelta * cols;
		if (next < 0) {
			final int col = selectedIndex % cols;
			final int lastRowStart = (size - 1) / cols * cols;
			next = Math.min(size - 1, lastRowStart + col);
		}
		else if (next >= size) {
			next = selectedIndex % cols;
			if (next >= size) {
				next = size - 1;
			}
		}
		if (next == selectedIndex) {
			return;
		}
		selectedIndex = next;
		rebuildCards();
		packAndLayout();
	}

	private void refreshColumnsPerRow() {
		final int count = strip.getComponentCount();
		if (count <= 1) {
			columnsPerRow = Math.max(1, count);
			return;
		}
		final int firstY = strip.getComponent(0).getY();
		int cols = 0;
		for (int i = 0; i < count; i++) {
			if (Math.abs(strip.getComponent(i).getY() - firstY) <= 2) {
				cols++;
			}
			else {
				break;
			}
		}
		columnsPerRow = Math.max(1, cols);
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
			final Component currentView = manager.getMapViewComponent();
			final String currentName = currentView == null ? null : currentView.getName();
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
		reload(Math.max(0, keepIndex));
		if (entries.isEmpty()) {
			dispose();
			return;
		}
		toFront();
		focusRoot();
	}

	private void packAndLayout() {
		final int estimateCols = Math.max(1, Math.min(entries.size(), 10));
		final int preferWidth = Math.min(DIALOG_MAX_WIDTH, Math.max(360, estimateCols * 88 + 40));
		strip.setPreferredSize(null);
		pack();
		int width = Math.max(preferWidth, getWidth());
		if (width > DIALOG_MAX_WIDTH) {
			width = DIALOG_MAX_WIDTH;
		}
		// Force wrap width so FlowLayout can form multiple rows when many maps are open.
		strip.setPreferredSize(new Dimension(width - 28, strip.getPreferredSize().height));
		pack();
		refreshColumnsPerRow();
		final int rows = Math.max(1, (entries.size() + columnsPerRow - 1) / Math.max(1, columnsPerRow));
		final int stripHeight = rows * (CARD_HEIGHT + CARD_V_GAP) + 8;
		strip.setPreferredSize(new Dimension(width - 28, stripHeight));
		pack();
		setSize(new Dimension(width, Math.max(96, getHeight())));
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
		return DocearUiTheme.font(size);
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
