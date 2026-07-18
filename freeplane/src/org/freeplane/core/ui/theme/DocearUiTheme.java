package org.freeplane.core.ui.theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.util.Enumeration;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.util.LogUtils;

/**
 * Shared visual language for Docear side panels and shell chrome.
 * Light, teal-slate direction (not purple / cream / newspaper defaults).
 */
public final class DocearUiTheme {
	public static final Color CANVAS = new Color(0xF4, 0xF7, 0xF8);
	public static final Color SURFACE = Color.WHITE;
	public static final Color SURFACE_SOFT = new Color(0xEE, 0xF4, 0xF3);
	public static final Color TEXT = new Color(0x0F, 0x17, 0x2A);
	public static final Color TEXT_MUTED = new Color(0x64, 0x74, 0x8B);
	public static final Color TEXT_FAINT = new Color(0x94, 0xA3, 0xB8);
	public static final Color ACCENT = new Color(0x0D, 0x94, 0x88);
	public static final Color ACCENT_DEEP = new Color(0x0F, 0x76, 0x6E);
	public static final Color ACCENT_WASH = new Color(0xCC, 0xFB, 0xF1);
	public static final Color HAIRLINE = new Color(0xE2, 0xE8, 0xF0);
	public static final Color DANGER = new Color(0xDC, 0x26, 0x26);
	public static final Color SUCCESS = new Color(0x05, 0x96, 0x69);
	public static final Color WARNING = new Color(0xD9, 0x77, 0x06);

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
			setUiFont("TabbedPane.font", font(12f, Font.BOLD));
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

			UIManager.put("TabbedPane.background", CANVAS);
			UIManager.put("TabbedPane.contentAreaColor", SURFACE);
			UIManager.put("TabbedPane.selected", SURFACE);
			UIManager.put("TabbedPane.underlineColor", ACCENT);
			UIManager.put("TabbedPane.selectedBackground", SURFACE);
			UIManager.put("TabbedPane.focusColor", ACCENT);
			UIManager.put("TabbedPane.hoverColor", ACCENT_WASH);
			UIManager.put("TabbedPane.tabInsets", new Insets(6, 10, 6, 10));
			UIManager.put("TabbedPane.selectedTabPadInsets", new Insets(6, 10, 6, 10));
			UIManager.put("TabbedPane.showTabSeparators", Boolean.TRUE);

			UIManager.put("Button.arc", Integer.valueOf(8));
			UIManager.put("Component.arc", Integer.valueOf(8));
			UIManager.put("TextComponent.arc", Integer.valueOf(8));
			UIManager.put("ScrollBar.thumbArc", Integer.valueOf(999));
			UIManager.put("ScrollBar.width", Integer.valueOf(12));
			UIManager.put("Component.focusWidth", Integer.valueOf(1));
			UIManager.put("Component.innerFocusWidth", Integer.valueOf(0));
			UIManager.put("Component.accentColor", ACCENT);
			UIManager.put("@accentColor", ACCENT);

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

	/** Keep relative sizes for keys already set by UITools scaling. */
	private static void scaleLegacyFontsIfNeeded() {
		try {
			final Enumeration keys = UIManager.getDefaults().keys();
			while (keys.hasMoreElements()) {
				final Object key = keys.nextElement();
				final Object value = UIManager.get(key);
				if (value instanceof Font && key != null && String.valueOf(key).endsWith(".font")) {
					// leave existing scaled fonts; we already set primary keys above
				}
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
			final Font font = new Font(prefer[i], style, Math.round(size));
			if (font.canDisplay('导') && font.canDisplay('图')) {
				return font.deriveFont(style, size);
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

	public static void styleSurfaceCard(final JPanel panel) {
		if (panel == null) {
			return;
		}
		panel.setOpaque(true);
		panel.setBackground(SURFACE);
		panel.setBorder(cardBorder());
	}

	public static Border cardBorder() {
		return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(HAIRLINE),
				new EmptyBorder(10, 10, 10, 10));
	}

	public static Border pageBorder() {
		return new EmptyBorder(10, 10, 10, 10);
	}

	public static JButton softButton(final String text) {
		final JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.setFont(font(12f));
		b.setBackground(SURFACE);
		b.setForeground(TEXT);
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
		return b;
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
