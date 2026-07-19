package org.freeplane.core.ui.theme;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.Enumeration;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.util.LogUtils;

/**
 * Shared product visual language for Docear (aligned with the calendar hub).
 * Light teal–slate direction with iOS-like tabs / scrollbars — not purple / cream / broadsheet.
 */
public final class DocearUiTheme {
	public static final Color CANVAS = new Color(0xF2, 0xF4, 0xF7);
	public static final Color SURFACE = Color.WHITE;
	public static final Color SURFACE_SOFT = new Color(0xE8, 0xEE, 0xF2);
	/** iOS-like grouped control well behind pill tabs. */
	public static final Color TAB_WELL = new Color(0xE5, 0xE9, 0xEF);
	public static final Color TAB_SELECTED = Color.WHITE;
	public static final Color TEXT = new Color(0x0F, 0x17, 0x2A);
	public static final Color TEXT_MUTED = new Color(0x64, 0x74, 0x8B);
	public static final Color TEXT_FAINT = new Color(0x94, 0xA3, 0xB8);
	public static final Color ACCENT = new Color(0x0D, 0x94, 0x88);
	public static final Color ACCENT_DEEP = new Color(0x0F, 0x76, 0x6E);
	public static final Color ACCENT_WASH = new Color(0xCC, 0xFB, 0xF1);
	public static final Color HAIRLINE = new Color(0xE2, 0xE8, 0xF0);
	public static final Color GRID = new Color(0xE8, 0xEE, 0xF2);
	public static final Color DANGER = new Color(0xDC, 0x26, 0x26);
	public static final Color SUCCESS = new Color(0x05, 0x96, 0x69);
	public static final Color WARNING = new Color(0xD9, 0x77, 0x06);
	public static final Color HEADER_TOP = new Color(0x0F, 0x76, 0x6E);
	public static final Color HEADER_BOTTOM = new Color(0x14, 0xB8, 0xA6);
	public static final Color SELECTION = new Color(0x99, 0xF6, 0xE4);
	public static final Color SCROLL_THUMB = new Color(0xB8, 0xC0, 0xCC);

	public static final String FLAT_LIGHT = "com.formdev.flatlaf.FlatLightLaf";
	public static final String FLAT_INTELLIJ = "com.formdev.flatlaf.FlatIntelliJLaf";
	public static final String FLAT_DARK = "com.formdev.flatlaf.FlatDarkLaf";

	private DocearUiTheme() {
	}

	/** Register FlatLaf entries if the jar is on the classpath. */
	public static void registerLookAndFeels() {
		try {
			Class.forName("com.formdev.flatlaf.FlatLightLaf");
			UIManager.installLookAndFeel("FlatLaf Light", FLAT_LIGHT);
			UIManager.installLookAndFeel("FlatLaf IntelliJ", FLAT_INTELLIJ);
			UIManager.installLookAndFeel("FlatLaf Dark", FLAT_DARK);
			LogUtils.info("DocearUiTheme: FlatLaf registered");
		}
		catch (Throwable t) {
			LogUtils.info("DocearUiTheme: FlatLaf not available (" + t.getMessage() + ")");
		}
	}

	/**
	 * Apply accent colors, fonts, and denser/modern component defaults after L&amp;F is set.
	 */
	public static void applyAfterLookAndFeel() {
		try {
			// Re-assert after L&F install: Substance otherwise registers SubstanceRibbonUI
			// (24px empty taskbar). ZeroTaskbarRibbonUI.createUI is required for this to work.
			UIManager.put("RibbonUI", "org.freeplane.core.ui.ribbon.ZeroTaskbarRibbonUI");

			final Font ui = font(13f);
			final Font uiBold = font(13f, Font.BOLD);
			setUiFont("Label.font", ui);
			setUiFont("Button.font", ui);
			setUiFont("ToggleButton.font", ui);
			setUiFont("RadioButton.font", ui);
			setUiFont("CheckBox.font", ui);
			setUiFont("ComboBox.font", ui);
			setUiFont("List.font", ui);
			setUiFont("Table.font", ui);
			setUiFont("TableHeader.font", uiBold);
			setUiFont("TextField.font", ui);
			setUiFont("FormattedTextField.font", ui);
			setUiFont("TextArea.font", ui);
			setUiFont("TextPane.font", ui);
			setUiFont("EditorPane.font", ui);
			setUiFont("TitledBorder.font", uiBold);
			setUiFont("ToolTip.font", font(12f));
			setUiFont("TabbedPane.font", font(12f, Font.PLAIN));
			setUiFont("Menu.font", ui);
			setUiFont("MenuItem.font", ui);
			setUiFont("CheckBoxMenuItem.font", ui);
			setUiFont("RadioButtonMenuItem.font", ui);
			setUiFont("PopupMenu.font", ui);
			setUiFont("OptionPane.font", ui);
			setUiFont("Panel.font", ui);
			setUiFont("Tree.font", ui);
			setUiFont("ToolBar.font", ui);

			UIManager.put("Panel.background", CANVAS);
			UIManager.put("Viewport.background", CANVAS);
			UIManager.put("OptionPane.background", CANVAS);
			UIManager.put("ToolBar.background", SURFACE);
			UIManager.put("ToolBar.border", BorderFactory.createMatteBorder(0, 0, 1, 0, HAIRLINE));

			// Shared tab chrome (safe for any L&F). FlatLaf-only keys applied below.
			UIManager.put("TabbedPane.background", TAB_WELL);
			UIManager.put("TabbedPane.contentAreaColor", SURFACE);
			UIManager.put("TabbedPane.selected", TAB_SELECTED);
			UIManager.put("TabbedPane.selectedBackground", TAB_SELECTED);
			UIManager.put("TabbedPane.foreground", TEXT_MUTED);
			UIManager.put("TabbedPane.tabInsets", new Insets(5, 8, 5, 8));
			UIManager.put("TabbedPane.selectedTabPadInsets", new Insets(0, 0, 0, 0));
			UIManager.put("TabbedPane.tabAreaInsets", new Insets(4, 4, 3, 4));
			UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
			UIManager.put("TabbedPane.tabRunOverlay", Integer.valueOf(0));

			UIManager.put("Button.arc", Integer.valueOf(10));
			UIManager.put("Component.arc", Integer.valueOf(10));
			UIManager.put("TextComponent.arc", Integer.valueOf(10));
			UIManager.put("Component.focusWidth", Integer.valueOf(1));
			UIManager.put("Component.innerFocusWidth", Integer.valueOf(0));
			UIManager.put("Component.accentColor", ACCENT);
			UIManager.put("@accentColor", ACCENT);

			// IMPORTANT: never put ScrollBarUI class name into UIManager — OSGi / system L&F
			// classloaders often cannot load org.freeplane.* and the first JScrollBar then aborts startup.
			if (isFlatLafActive()) {
				UIManager.put("TabbedPane.underlineColor", ACCENT);
				UIManager.put("TabbedPane.focusColor", ACCENT);
				UIManager.put("TabbedPane.hoverColor", SURFACE_SOFT);
				UIManager.put("TabbedPane.selectedForeground", TEXT);
				UIManager.put("TabbedPane.showTabSeparators", Boolean.FALSE);
				UIManager.put("TabbedPane.showContentSeparator", Boolean.FALSE);
				UIManager.put("TabbedPane.tabType", "card");
				UIManager.put("TabbedPane.tabHeight", Integer.valueOf(30));
				UIManager.put("TabbedPane.tabArc", Integer.valueOf(12));
				UIManager.put("TabbedPane.cardTabArc", Integer.valueOf(12));
				UIManager.put("TabbedPane.tabSelectionHeight", Integer.valueOf(0));
				UIManager.put("TabbedPane.cardTabSelectionHeight", Integer.valueOf(0));

				UIManager.put("ScrollBar.thumbArc", Integer.valueOf(999));
				UIManager.put("ScrollBar.trackArc", Integer.valueOf(999));
				UIManager.put("ScrollBar.width", Integer.valueOf(6));
				UIManager.put("ScrollBar.showButtons", Boolean.FALSE);
				UIManager.put("ScrollBar.trackInsets", new Insets(1, 1, 1, 1));
				UIManager.put("ScrollBar.thumbInsets", new Insets(1, 1, 1, 1));
				UIManager.put("ScrollBar.track", CANVAS);
				UIManager.put("ScrollBar.thumb", SCROLL_THUMB);
				UIManager.put("ScrollBar.hoverThumbColor", new Color(0x8E, 0x99, 0xA8));
				UIManager.put("ScrollBar.pressedThumbColor", new Color(0x78, 0x84, 0x94));
				UIManager.put("ScrollBar.minimumThumbSize", new java.awt.Dimension(18, 18));
			}

			UIManager.put("Button.background", SURFACE);
			UIManager.put("Button.foreground", TEXT);
			UIManager.put("Button.default.background", ACCENT);
			UIManager.put("Button.default.foreground", Color.WHITE);
			UIManager.put("Button.default.focusColor", ACCENT_DEEP);

			UIManager.put("List.selectionBackground", ACCENT_WASH);
			UIManager.put("List.selectionForeground", TEXT);
			UIManager.put("Tree.selectionBackground", ACCENT_WASH);
			UIManager.put("Tree.selectionForeground", TEXT);
			UIManager.put("Table.selectionBackground", ACCENT_WASH);
			UIManager.put("Table.selectionForeground", TEXT);
			UIManager.put("TextField.selectionBackground", SELECTION);
			UIManager.put("TextArea.selectionBackground", SELECTION);

			UIManager.put("Separator.foreground", HAIRLINE);
			UIManager.put("ToolTip.background", SURFACE);
			UIManager.put("ToolTip.foreground", TEXT);
			UIManager.put("ToolTip.border", BorderFactory.createLineBorder(HAIRLINE));

			scaleLegacyFontsIfNeeded();
		}
		catch (Throwable t) {
			LogUtils.warn("DocearUiTheme.applyAfterLookAndFeel failed", t);
		}
	}

	private static void setUiFont(final String key, final Font font) {
		if (font != null) {
			UIManager.put(key, font);
		}
	}

	private static void scaleLegacyFontsIfNeeded() {
		try {
			final Enumeration keys = UIManager.getDefaults().keys();
			while (keys.hasMoreElements()) {
				keys.nextElement();
			}
		}
		catch (Throwable t) {
		}
	}

	public static Font font(final float size) {
		return font(size, Font.PLAIN);
	}

	public static Font font(final float size, final int style) {
		final String[] prefer = new String[] { "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
				"Noto Sans CJK SC", "Source Han Sans SC", "Segoe UI", "SansSerif" };
		for (int i = 0; i < prefer.length; i++) {
			try {
				final Font font = new Font(prefer[i], style, Math.round(size));
				// canDisplay may trigger macOS font manager stderr noise; keep probing quiet.
				if (font.canDisplay('导') && font.canDisplay('图')) {
					return font.deriveFont(style, size);
				}
			}
			catch (Throwable ignored) {
			}
		}
		return new Font("SansSerif", style, Math.round(size));
	}

	public static void styleCanvas(final JComponent panel) {
		if (panel == null) {
			return;
		}
		panel.setOpaque(true);
		panel.setBackground(CANVAS);
		panel.setForeground(TEXT);
		panel.setFont(font(13f));
	}

	public static void styleSurface(final JComponent panel) {
		if (panel == null) {
			return;
		}
		panel.setOpaque(true);
		panel.setBackground(SURFACE);
		panel.setForeground(TEXT);
	}

	public static void styleSurfaceCard(final JPanel panel) {
		if (panel == null) {
			return;
		}
		styleSurface(panel);
		panel.setBorder(cardBorder());
	}

	public static void styleToolbar(final JComponent bar) {
		if (bar == null) {
			return;
		}
		bar.setOpaque(true);
		bar.setBackground(SURFACE);
		bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, HAIRLINE),
				new EmptyBorder(6, 8, 6, 8)));
		bar.setFont(font(12f));
		if (bar instanceof JToolBar) {
			((JToolBar) bar).setFloatable(false);
		}
	}

	public static void styleScrollPane(final JScrollPane scroll) {
		if (scroll == null) {
			return;
		}
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(SURFACE);
		scroll.setBackground(SURFACE);
		scroll.putClientProperty("JScrollBar.showButtons", Boolean.FALSE);
		scroll.putClientProperty("JScrollPane.smoothScrolling", Boolean.TRUE);
		styleScrollBar(scroll.getVerticalScrollBar());
		styleScrollBar(scroll.getHorizontalScrollBar());
	}

	public static void styleScrollBar(final JScrollBar bar) {
		if (bar == null) {
			return;
		}
		bar.setOpaque(false);
		bar.setUnitIncrement(16);
		bar.putClientProperty("JScrollBar.showButtons", Boolean.FALSE);
		try {
			if (!(bar.getUI() instanceof DocearScrollBarUI)) {
				bar.setUI(new DocearScrollBarUI());
			}
		}
		catch (Throwable t) {
		}
	}

	/** Soft well + FlatLaf card client props for panes that keep FlatLaf tab UI. */
	public static void styleTabbedPane(final JTabbedPane tabs) {
		if (tabs == null) {
			return;
		}
		tabs.setOpaque(true);
		tabs.setBackground(TAB_WELL);
		tabs.setForeground(TEXT_MUTED);
		tabs.setFont(font(12f, Font.PLAIN));
		tabs.setBorder(new EmptyBorder(4, 4, 2, 4));
		tabs.putClientProperty("JTabbedPane.tabType", "card");
		tabs.putClientProperty("JTabbedPane.showTabSeparators", Boolean.FALSE);
		tabs.putClientProperty("JTabbedPane.showContentSeparator", Boolean.FALSE);
		tabs.putClientProperty("JTabbedPane.tabHeight", Integer.valueOf(30));
		tabs.putClientProperty("JTabbedPane.tabArc", Integer.valueOf(12));
		tabs.putClientProperty("JTabbedPane.cardTabArc", Integer.valueOf(12));
	}

	public static void styleSearchField(final JTextField field) {
		if (field == null) {
			return;
		}
		field.setFont(font(13f));
		field.setForeground(TEXT);
		field.setBackground(SURFACE);
		field.setCaretColor(ACCENT_DEEP);
		field.setSelectionColor(SELECTION);
		field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(HAIRLINE),
				new EmptyBorder(6, 10, 6, 10)));
	}

	public static void styleList(final JComponent list) {
		if (list == null) {
			return;
		}
		list.setFont(font(13f));
		list.setBackground(SURFACE);
		list.setForeground(TEXT);
		list.setBorder(new EmptyBorder(4, 4, 4, 4));
	}

	public static Border hairlineBorder() {
		return BorderFactory.createLineBorder(HAIRLINE);
	}

	public static Border cardBorder() {
		return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(HAIRLINE),
				new EmptyBorder(10, 10, 10, 10));
	}

	public static Border pageBorder() {
		return new EmptyBorder(10, 10, 10, 10);
	}

	public static Border sectionBorder() {
		return BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, HAIRLINE),
				new EmptyBorder(8, 10, 8, 10));
	}

	public static JLabel sectionLabel(final String text) {
		final JLabel label = new JLabel(text);
		label.setFont(font(11f, Font.BOLD));
		label.setForeground(TEXT_MUTED);
		return label;
	}

	public static JLabel mutedLabel(final String text) {
		final JLabel label = new JLabel(text);
		label.setFont(font(12f));
		label.setForeground(TEXT_MUTED);
		return label;
	}

	public static JLabel titleLabel(final String text) {
		final JLabel label = new JLabel(text);
		label.setFont(font(16f, Font.BOLD));
		label.setForeground(TEXT);
		return label;
	}

	public static JButton softButton(final String text) {
		final JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.setFont(font(12f));
		b.setBackground(SURFACE);
		b.setForeground(TEXT);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return b;
	}

	public static JButton primaryButton(final String text) {
		final JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.setFont(font(12f, Font.BOLD));
		b.setBackground(ACCENT);
		b.setForeground(Color.WHITE);
		b.setOpaque(true);
		b.setBorderPainted(false);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return b;
	}

	/** Calendar-style ghost control on light surfaces. */
	public static JButton ghostButton(final String text) {
		final JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setContentAreaFilled(false);
		b.setOpaque(false);
		b.setFont(font(12f));
		b.setForeground(TEXT_MUTED);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return b;
	}

	/** Calendar-style segment toggle. */
	public static JToggleButton segmentToggle(final String text) {
		final JToggleButton b = new JToggleButton(text);
		b.setFocusPainted(false);
		b.setFont(font(12f, Font.BOLD));
		b.setForeground(TEXT_MUTED);
		b.setBackground(SURFACE_SOFT);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return b;
	}

	public static void paintHeaderBand(final Graphics2D g2, final Rectangle bounds) {
		if (g2 == null || bounds == null) {
			return;
		}
		final GradientPaint paint = new GradientPaint(bounds.x, bounds.y, HEADER_TOP, bounds.x,
				bounds.y + bounds.height, HEADER_BOTTOM);
		g2.setPaint(paint);
		g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
	}

	public static boolean isFlatLafActive() {
		try {
			final String name = UIManager.getLookAndFeel().getClass().getName();
			return name != null && name.indexOf("flatlaf") >= 0;
		}
		catch (Throwable t) {
			return false;
		}
	}
}
