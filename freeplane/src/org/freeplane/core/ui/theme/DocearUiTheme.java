package org.freeplane.core.ui.theme;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.awt.event.HierarchyEvent;
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
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;

/**
 * Shared product visual language for Twigmark / Docear (aligned with the calendar hub).
 * Light teal–slate direction with iOS-like tabs / scrollbars — not purple / cream / broadsheet.
 * FlatLaf Dark switches chrome tokens via {@link #applyAfterLookAndFeel()}.
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

	/** Dark chrome tokens (FlatLaf Dark / IntelliJ dark variants). */
	public static final Color DARK_CANVAS = new Color(0x1A, 0x1F, 0x26);
	public static final Color DARK_SURFACE = new Color(0x24, 0x2A, 0x33);
	public static final Color DARK_SURFACE_SOFT = new Color(0x2E, 0x36, 0x40);
	public static final Color DARK_TAB_WELL = new Color(0x1E, 0x24, 0x2C);
	public static final Color DARK_TAB_SELECTED = new Color(0x2E, 0x36, 0x40);
	public static final Color DARK_TEXT = new Color(0xE8, 0xEE, 0xF4);
	public static final Color DARK_TEXT_MUTED = new Color(0x9A, 0xA6, 0xB5);
	public static final Color DARK_TEXT_FAINT = new Color(0x6B, 0x78, 0x8A);
	public static final Color DARK_ACCENT = new Color(0x2D, 0xD4, 0xBF);
	public static final Color DARK_ACCENT_DEEP = new Color(0x14, 0xB8, 0xA6);
	public static final Color DARK_ACCENT_WASH = new Color(0x13, 0x4E, 0x4A);
	public static final Color DARK_HAIRLINE = new Color(0x3A, 0x44, 0x52);
	public static final Color DARK_SELECTION = new Color(0x11, 0x5E, 0x59);
	public static final Color DARK_SCROLL_THUMB = new Color(0x5B, 0x68, 0x78);

	public static final String FLAT_LIGHT = "com.formdev.flatlaf.FlatLightLaf";
	public static final String FLAT_INTELLIJ = "com.formdev.flatlaf.FlatIntelliJLaf";
	public static final String FLAT_DARK = "com.formdev.flatlaf.FlatDarkLaf";

	/** Preference key: comfortable (default) | compact */
	public static final String UI_DENSITY_PROPERTY = "ui_density";
	public static final String UI_DENSITY_COMFORTABLE = "comfortable";
	public static final String UI_DENSITY_COMPACT = "compact";

	private static boolean globalScrollBarStylerInstalled;

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
	 * Chooses light or dark chrome tokens when FlatLaf Dark is active.
	 */
	public static void applyAfterLookAndFeel() {
		try {
			// Re-assert after L&F install: Substance otherwise registers SubstanceRibbonUI
			// (24px empty taskbar). ZeroTaskbarRibbonUI.createUI is required for this to work.
			UIManager.put("RibbonUI", "org.freeplane.core.ui.ribbon.ZeroTaskbarRibbonUI");

			final boolean dark = isDarkLafActive();
			final boolean compact = isCompactDensity();
			final Color canvas = dark ? DARK_CANVAS : CANVAS;
			final Color surface = dark ? DARK_SURFACE : SURFACE;
			final Color surfaceSoft = dark ? DARK_SURFACE_SOFT : SURFACE_SOFT;
			final Color tabWell = dark ? DARK_TAB_WELL : TAB_WELL;
			final Color tabSelected = dark ? DARK_TAB_SELECTED : TAB_SELECTED;
			final Color text = dark ? DARK_TEXT : TEXT;
			final Color textMuted = dark ? DARK_TEXT_MUTED : TEXT_MUTED;
			final Color accent = dark ? DARK_ACCENT : ACCENT;
			final Color accentDeep = dark ? DARK_ACCENT_DEEP : ACCENT_DEEP;
			final Color accentWash = dark ? DARK_ACCENT_WASH : ACCENT_WASH;
			final Color hairline = dark ? DARK_HAIRLINE : HAIRLINE;
			final Color selection = dark ? DARK_SELECTION : SELECTION;
			final Color scrollThumb = dark ? DARK_SCROLL_THUMB : SCROLL_THUMB;

			final float base = compact ? 12f : 13f;
			final float tabFont = compact ? 11f : 12f;
			final Font ui = font(base);
			final Font uiBold = font(base, Font.BOLD);
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
			setUiFont("ToolTip.font", font(compact ? 11f : 12f));
			setUiFont("TabbedPane.font", font(tabFont, Font.PLAIN));
			setUiFont("Menu.font", ui);
			setUiFont("MenuItem.font", ui);
			setUiFont("CheckBoxMenuItem.font", ui);
			setUiFont("RadioButtonMenuItem.font", ui);
			setUiFont("PopupMenu.font", ui);
			setUiFont("OptionPane.font", ui);
			setUiFont("Panel.font", ui);
			setUiFont("Tree.font", ui);
			setUiFont("ToolBar.font", ui);

			UIManager.put("Panel.background", canvas);
			UIManager.put("Viewport.background", canvas);
			UIManager.put("OptionPane.background", canvas);
			UIManager.put("OptionPane.messageForeground", text);
			final int optPad = compact ? 10 : 16;
			UIManager.put("OptionPane.border", new EmptyBorder(optPad, optPad + 2, compact ? 8 : 12, optPad + 2));
			UIManager.put("OptionPane.messageAreaBorder", new EmptyBorder(0, 0, compact ? 8 : 12, 0));
			UIManager.put("OptionPane.buttonAreaBorder", new EmptyBorder(compact ? 4 : 8, 0, 0, 0));
			localizeOptionPaneButtons();
			UIManager.put("ToolBar.background", surface);
			UIManager.put("ToolBar.border", BorderFactory.createMatteBorder(0, 0, 1, 0, hairline));

			UIManager.put("PopupMenu.background", surface);
			UIManager.put("PopupMenu.border", BorderFactory.createCompoundBorder(
			        BorderFactory.createLineBorder(hairline), new EmptyBorder(3, 2, 3, 4)));
			UIManager.put("MenuItem.selectionBackground", accentWash);
			UIManager.put("MenuItem.selectionForeground", text);
			final int menuPadV = compact ? 1 : 2;
			final int menuPadH = compact ? 3 : 4;
			UIManager.put("MenuItem.margin", new Insets(menuPadV, menuPadH, menuPadV, compact ? 6 : 8));
			UIManager.put("Menu.margin", new Insets(menuPadV, menuPadH, menuPadV, compact ? 6 : 8));
			UIManager.put("MenuItem.iconTextGap", Integer.valueOf(compact ? 2 : 4));
			UIManager.put("CheckBoxMenuItem.selectionBackground", accentWash);
			UIManager.put("CheckBoxMenuItem.selectionForeground", text);
			UIManager.put("RadioButtonMenuItem.selectionBackground", accentWash);
			UIManager.put("RadioButtonMenuItem.selectionForeground", text);
			UIManager.put("Menu.selectionBackground", accentWash);
			UIManager.put("Menu.selectionForeground", text);

			// Shared tab chrome (safe for any L&F). FlatLaf-only keys applied below.
			UIManager.put("TabbedPane.background", tabWell);
			UIManager.put("TabbedPane.contentAreaColor", surface);
			UIManager.put("TabbedPane.selected", tabSelected);
			UIManager.put("TabbedPane.selectedBackground", tabSelected);
			UIManager.put("TabbedPane.foreground", textMuted);
			final int tabInsetV = compact ? 3 : 5;
			final int tabInsetH = compact ? 6 : 8;
			UIManager.put("TabbedPane.tabInsets", new Insets(tabInsetV, tabInsetH, tabInsetV, tabInsetH));
			UIManager.put("TabbedPane.selectedTabPadInsets", new Insets(0, 0, 0, 0));
			UIManager.put("TabbedPane.tabAreaInsets", new Insets(compact ? 2 : 4, compact ? 2 : 4, 3, compact ? 2 : 4));
			UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
			UIManager.put("TabbedPane.tabRunOverlay", Integer.valueOf(0));

			final int arc = compact ? 8 : 10;
			UIManager.put("Button.arc", Integer.valueOf(arc));
			UIManager.put("Component.arc", Integer.valueOf(arc));
			UIManager.put("TextComponent.arc", Integer.valueOf(arc));
			UIManager.put("Component.focusWidth", Integer.valueOf(1));
			UIManager.put("Component.innerFocusWidth", Integer.valueOf(0));
			UIManager.put("Component.accentColor", accent);
			UIManager.put("@accentColor", accent);

			// IMPORTANT: never put ScrollBarUI class name into UIManager — OSGi / system L&F
			// classloaders often cannot load org.freeplane.* and the first JScrollBar then aborts startup.
			if (isFlatLafActive()) {
				// Collapse empty icon columns in popup menus (workspace context menus have almost no icons).
				UIManager.put("MenuItem.minimumIconSize", new java.awt.Dimension(0, 0));
				UIManager.put("Menu.minimumIconSize", new java.awt.Dimension(0, 0));
				UIManager.put("CheckBoxMenuItem.minimumIconSize", new java.awt.Dimension(0, 0));
				UIManager.put("RadioButtonMenuItem.minimumIconSize", new java.awt.Dimension(0, 0));
				UIManager.put("MenuItem.iconTextGap", Integer.valueOf(2));
				UIManager.put("Menu.iconTextGap", Integer.valueOf(2));
				UIManager.put("TabbedPane.underlineColor", accent);
				UIManager.put("TabbedPane.focusColor", accent);
				UIManager.put("TabbedPane.hoverColor", surfaceSoft);
				UIManager.put("TabbedPane.selectedForeground", text);
				UIManager.put("TabbedPane.showTabSeparators", Boolean.FALSE);
				UIManager.put("TabbedPane.showContentSeparator", Boolean.FALSE);
				UIManager.put("TabbedPane.tabType", "card");
				UIManager.put("TabbedPane.tabHeight", Integer.valueOf(compact ? 26 : 30));
				UIManager.put("TabbedPane.tabArc", Integer.valueOf(compact ? 10 : 12));
				UIManager.put("TabbedPane.cardTabArc", Integer.valueOf(compact ? 10 : 12));
				UIManager.put("TabbedPane.tabSelectionHeight", Integer.valueOf(0));
				UIManager.put("TabbedPane.cardTabSelectionHeight", Integer.valueOf(0));

				UIManager.put("ScrollBar.thumbArc", Integer.valueOf(999));
				UIManager.put("ScrollBar.trackArc", Integer.valueOf(999));
				UIManager.put("ScrollBar.width", Integer.valueOf(compact ? 5 : 6));
				UIManager.put("ScrollBar.showButtons", Boolean.FALSE);
				UIManager.put("ScrollBar.trackInsets", new Insets(1, 1, 1, 1));
				UIManager.put("ScrollBar.thumbInsets", new Insets(1, 1, 1, 1));
				UIManager.put("ScrollBar.track", canvas);
				UIManager.put("ScrollBar.thumb", scrollThumb);
				UIManager.put("ScrollBar.hoverThumbColor", dark ? new Color(0x7A, 0x88, 0x9A) : new Color(0x8E, 0x99, 0xA8));
				UIManager.put("ScrollBar.pressedThumbColor", dark ? new Color(0x8E, 0x9C, 0xAE) : new Color(0x78, 0x84, 0x94));
				UIManager.put("ScrollBar.minimumThumbSize", new java.awt.Dimension(18, 18));
			}

			UIManager.put("Button.background", surface);
			UIManager.put("Button.foreground", text);
			UIManager.put("Button.default.background", accent);
			UIManager.put("Button.default.foreground", dark ? DARK_CANVAS : Color.WHITE);
			UIManager.put("Button.default.focusColor", accentDeep);

			UIManager.put("List.selectionBackground", accentWash);
			UIManager.put("List.selectionForeground", text);
			UIManager.put("Tree.selectionBackground", accentWash);
			UIManager.put("Tree.selectionForeground", text);
			UIManager.put("Table.selectionBackground", accentWash);
			UIManager.put("Table.selectionForeground", text);
			UIManager.put("TextField.selectionBackground", selection);
			UIManager.put("TextArea.selectionBackground", selection);

			UIManager.put("Separator.foreground", hairline);
			UIManager.put("ToolTip.background", surface);
			UIManager.put("ToolTip.foreground", text);
			UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
			        BorderFactory.createLineBorder(hairline), new EmptyBorder(6, 8, 6, 8)));

			scaleLegacyFontsIfNeeded();
			installGlobalScrollBarStyler();
		}
		catch (Throwable t) {
			LogUtils.warn("DocearUiTheme.applyAfterLookAndFeel failed", t);
		}
	}

	/**
	 * Apply the thin pill scrollbar to every {@link JScrollPane}/{@link JScrollBar}
	 * that appears later. Avoids putting ScrollBarUI class names into UIManager
	 * (OSGi classloader issues).
	 */
	public static synchronized void installGlobalScrollBarStyler() {
		if (globalScrollBarStylerInstalled) {
			return;
		}
		globalScrollBarStylerInstalled = true;
		final AWTEventListener listener = new AWTEventListener() {
			public void eventDispatched(final AWTEvent event) {
				try {
					if (event instanceof ContainerEvent) {
						final ContainerEvent ce = (ContainerEvent) event;
						if (ce.getID() == ContainerEvent.COMPONENT_ADDED) {
							styleScrollBarsUnder(ce.getChild());
						}
						return;
					}
					if (event instanceof HierarchyEvent) {
						final HierarchyEvent he = (HierarchyEvent) event;
						if ((he.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
								&& he.getComponent().isDisplayable()) {
							styleScrollBarsUnder(he.getComponent());
						}
					}
				}
				catch (Throwable ignore) {
				}
			}
		};
		Toolkit.getDefaultToolkit().addAWTEventListener(listener,
				AWTEvent.CONTAINER_EVENT_MASK | AWTEvent.HIERARCHY_EVENT_MASK);
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				for (final java.awt.Window w : java.awt.Window.getWindows()) {
					styleScrollBarsUnder(w);
				}
			}
		});
	}

	private static void styleScrollBarsUnder(final Component root) {
		if (root == null) {
			return;
		}
		if (root instanceof JScrollBar) {
			styleScrollBar((JScrollBar) root);
			return;
		}
		if (root instanceof JScrollPane) {
			final JScrollPane scroll = (JScrollPane) root;
			scroll.putClientProperty("JScrollBar.showButtons", Boolean.FALSE);
			styleScrollBar(scroll.getVerticalScrollBar());
			styleScrollBar(scroll.getHorizontalScrollBar());
		}
		if (root instanceof Container) {
			final Container container = (Container) root;
			final int n = container.getComponentCount();
			for (int i = 0; i < n; i++) {
				styleScrollBarsUnder(container.getComponent(i));
			}
		}
	}

	/** Prefer Freeplane locale strings over L&amp;F English Yes/No/Cancel. */
	public static void localizeOptionPaneButtons() {
		try {
			putOptionButton("OptionPane.yesButtonText", "yes", "Yes");
			putOptionButton("OptionPane.noButtonText", "no", "No");
			putOptionButton("OptionPane.cancelButtonText", "cancel", "Cancel");
			putOptionButton("OptionPane.okButtonText", "ok", "OK");
		}
		catch (Throwable t) {
			// resources may not be ready yet
		}
	}

	private static void putOptionButton(final String uiKey, final String textKey, final String fallback) {
		String label = null;
		try {
			label = TextUtils.getRawText(textKey, null);
		}
		catch (Throwable t) {
		}
		if (label == null || label.length() == 0 || label.startsWith("[")) {
			label = fallback;
		}
		else {
			label = TextUtils.removeMnemonic(TextUtils.removeTranslateComment(label));
		}
		UIManager.put(uiKey, label);
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
		final String os = System.getProperty("os.name", "").toLowerCase();
		final String[] prefer;
		if (os.indexOf("mac") >= 0) {
			// Microsoft YaHei (often from Office) canDisplay CJK but drops Latin glyphs
			// (o/r/s/…) under Java 8 on macOS — prefer system CJK fonts first.
			prefer = new String[] { "PingFang SC", "Hiragino Sans GB", "Heiti SC", "Helvetica Neue",
					"Lucida Grande", "SansSerif" };
		}
		else if (os.indexOf("win") >= 0) {
			prefer = new String[] { "Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI", "SansSerif" };
		}
		else {
			prefer = new String[] { "Noto Sans CJK SC", "Source Han Sans SC", "SansSerif" };
		}
		for (int i = 0; i < prefer.length; i++) {
			try {
				final Font font = new Font(prefer[i], style, Math.round(size));
				if (isUsableUiFont(font)) {
					return font.deriveFont(style, size);
				}
			}
			catch (Throwable ignored) {
			}
		}
		return new Font("SansSerif", style, Math.round(size));
	}

	/** CJK + Latin sample must both render; YaHei-on-Mac often fails the Latin half. */
	private static boolean isUsableUiFont(final Font font) {
		if (font == null) {
			return false;
		}
		if (!font.canDisplay('导') || !font.canDisplay('图')) {
			return false;
		}
		final String latin = "Docear Users/folder maps";
		for (int i = 0; i < latin.length(); i++) {
			final char c = latin.charAt(i);
			if (c >= 'A' && c <= 'z' && !font.canDisplay(c)) {
				return false;
			}
		}
		return true;
	}

	public static void styleCanvas(final JComponent panel) {
		if (panel == null) {
			return;
		}
		panel.setOpaque(true);
		panel.setBackground(chromeCanvas());
		panel.setForeground(chromeText());
		panel.setFont(font(isCompactDensity() ? 12f : 13f));
	}

	public static void styleSurface(final JComponent panel) {
		if (panel == null) {
			return;
		}
		panel.setOpaque(true);
		panel.setBackground(chromeSurface());
		panel.setForeground(chromeText());
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
		final boolean dark = isDarkLafActive();
		final boolean compact = isCompactDensity();
		tabs.setOpaque(true);
		tabs.setBackground(dark ? DARK_TAB_WELL : TAB_WELL);
		tabs.setForeground(dark ? DARK_TEXT_MUTED : TEXT_MUTED);
		tabs.setFont(font(compact ? 11f : 12f, Font.PLAIN));
		tabs.setBorder(new EmptyBorder(compact ? 2 : 4, compact ? 2 : 4, 2, compact ? 2 : 4));
		tabs.putClientProperty("JTabbedPane.tabType", "card");
		tabs.putClientProperty("JTabbedPane.showTabSeparators", Boolean.FALSE);
		tabs.putClientProperty("JTabbedPane.showContentSeparator", Boolean.FALSE);
		tabs.putClientProperty("JTabbedPane.tabHeight", Integer.valueOf(compact ? 26 : 30));
		tabs.putClientProperty("JTabbedPane.tabArc", Integer.valueOf(compact ? 10 : 12));
		tabs.putClientProperty("JTabbedPane.cardTabArc", Integer.valueOf(compact ? 10 : 12));
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
			return name != null && name.toLowerCase().indexOf("flatlaf") >= 0;
		}
		catch (Throwable t) {
			return false;
		}
	}

	/** True for FlatDark / Darcula-like FlatLaf skins. */
	public static boolean isDarkLafActive() {
		try {
			final String name = UIManager.getLookAndFeel().getClass().getName();
			if (name == null) {
				return false;
			}
			final String lower = name.toLowerCase();
			return lower.indexOf("flatdark") >= 0 || lower.indexOf("flatdarcula") >= 0
					|| lower.indexOf("flatmacdark") >= 0;
		}
		catch (Throwable t) {
			return false;
		}
	}

	/** Read {@link #UI_DENSITY_PROPERTY}; default comfortable. */
	public static boolean isCompactDensity() {
		try {
			final String sys = System.getProperty("twigmark.ui_density");
			if (sys != null && UI_DENSITY_COMPACT.equalsIgnoreCase(sys.trim())) {
				return true;
			}
			final org.freeplane.features.mode.Controller controller = org.freeplane.features.mode.Controller
					.getCurrentController();
			if (controller != null && controller.getResourceController() != null) {
				final String v = controller.getResourceController().getProperty(UI_DENSITY_PROPERTY,
						UI_DENSITY_COMFORTABLE);
				return v != null && UI_DENSITY_COMPACT.equalsIgnoreCase(v.trim());
			}
		}
		catch (Throwable t) {
		}
		return false;
	}

	public static Color chromeCanvas() {
		return isDarkLafActive() ? DARK_CANVAS : CANVAS;
	}

	public static Color chromeSurface() {
		return isDarkLafActive() ? DARK_SURFACE : SURFACE;
	}

	public static Color chromeText() {
		return isDarkLafActive() ? DARK_TEXT : TEXT;
	}

	public static Color chromeAccent() {
		return isDarkLafActive() ? DARK_ACCENT : ACCENT;
	}

	public static Color chromeHairline() {
		return isDarkLafActive() ? DARK_HAIRLINE : HAIRLINE;
	}
}
