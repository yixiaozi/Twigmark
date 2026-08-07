package org.freeplane.core.ui;

import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.KeyStroke;

import org.freeplane.core.util.Compat;

/**
 * Windows / macOS / Linux shortcut differences for the hot-key editor and
 * platform default migration (e.g. Insert → Tab on Mac).
 */
public final class PlatformHotKeyGuide {

	public static final String PLATFORM_WINDOWS = "windows";
	public static final String PLATFORM_MAC = "mac";
	public static final String PLATFORM_LINUX = "linux";

	private PlatformHotKeyGuide() {
	}

	public static String getPlatformId() {
		if (Compat.isMacOsX()) {
			return PLATFORM_MAC;
		}
		if (Compat.isWindowsOS()) {
			return PLATFORM_WINDOWS;
		}
		return PLATFORM_LINUX;
	}

	/** Short label for UI, e.g. {@code macOS} / {@code Windows}. */
	public static String getPlatformDisplayName() {
		final String id = getPlatformId();
		if (PLATFORM_MAC.equals(id)) {
			return "macOS";
		}
		if (PLATFORM_WINDOWS.equals(id)) {
			return "Windows";
		}
		return "Linux";
	}

	/** Preset filename segment: {@code accelerator-mac.properties}. */
	public static String getAcceleratorFileName() {
		return "accelerator-" + getPlatformId() + ".properties";
	}

	/**
	 * Modifier correspondence (shared legend). Values are display lines, not strokes.
	 */
	public static Map getModifierMapLines() {
		final Map map = new LinkedHashMap();
		map.put("Ctrl", "⌘ Command（Mac） / Ctrl（Windows·Linux）");
		map.put("Alt", "⌥ Option（Mac） / Alt（Windows·Linux）");
		map.put("Shift", "⇧ Shift（各平台相同）");
		map.put("Meta", "⌘ Command（仅 Mac；Windows 上会映射为 Ctrl）");
		return Collections.unmodifiableMap(map);
	}

	/**
	 * Keys that are missing or awkward on Mac keyboards, with recommended substitutes.
	 * Key = Windows/Linux stroke fragment; value = explanation for the editor.
	 */
	public static Map getMacMissingKeyNotes() {
		final Map map = new LinkedHashMap();
		map.put("INSERT", "Mac 无 Insert → 默认改用 Tab（新建子节点）");
		map.put("shift INSERT", "Mac 无 Insert → 默认改用 ⇧Tab（新建父节点）");
		map.put("alt shift INSERT", "Mac 无 Insert → 默认改用 ⌘⇧I（总结节点）");
		map.put("alt SPACE", "与输入法切换冲突 → 默认改用 ⌘⇧O（切换导图）");
		map.put("HOME / END / PgUp / PgDn", "笔记本常需按 Fn；外接键盘一般可用");
		map.put("Delete", "Mac 退格键是 Backspace；Forward Delete 在部分键盘为 Fn+⌫");
		return Collections.unmodifiableMap(map);
	}

	/** Action key → Mac default stroke string (parsed via RibbonAcceleratorManager.parseKeyStroke). */
	public static Map getMacDefaultAlternatives() {
		final Map map = new LinkedHashMap();
		map.put("NewChildAction", "TAB");
		map.put("NewParentNode", "shift TAB");
		map.put("NewSummaryAction", "meta shift I");
		map.put("MapSwitcherAction", "meta shift O");
		map.put("QuickCaptureAction", "meta shift SPACE");
		map.put("QuickCommandAction", "meta shift PERIOD");
		return Collections.unmodifiableMap(map);
	}

	/** True when the stroke uses a key Mac keyboards typically lack (Insert). */
	public static boolean usesMacUnavailableKey(final KeyStroke stroke) {
		if (stroke == null) {
			return false;
		}
		return stroke.getKeyCode() == KeyEvent.VK_INSERT;
	}

	/**
	 * Whether the current binding should be replaced by the Mac alternative for this action.
	 * Unbound → fill; Insert-based or Alt+Space MapSwitcher → migrate.
	 */
	public static boolean shouldApplyMacAlternative(final String actionKey, final KeyStroke current) {
		if (!Compat.isMacOsX() || actionKey == null) {
			return false;
		}
		if (!getMacDefaultAlternatives().containsKey(actionKey)) {
			return false;
		}
		if (current == null) {
			return true;
		}
		if (usesMacUnavailableKey(current)) {
			return true;
		}
		if ("MapSwitcherAction".equals(actionKey) && isAltSpace(current)) {
			return true;
		}
		return false;
	}

	public static boolean isAltSpace(final KeyStroke stroke) {
		if (stroke == null || stroke.getKeyCode() != KeyEvent.VK_SPACE) {
			return false;
		}
		final int mods = stroke.getModifiers();
		final boolean alt = (mods & KeyEvent.ALT_MASK) != 0 || (mods & KeyEvent.ALT_DOWN_MASK) != 0;
		final boolean ctrl = (mods & KeyEvent.CTRL_MASK) != 0 || (mods & KeyEvent.CTRL_DOWN_MASK) != 0;
		final boolean meta = (mods & KeyEvent.META_MASK) != 0 || (mods & KeyEvent.META_DOWN_MASK) != 0;
		final boolean shift = (mods & KeyEvent.SHIFT_MASK) != 0 || (mods & KeyEvent.SHIFT_DOWN_MASK) != 0;
		return alt && !ctrl && !meta && !shift;
	}

	/** HTML legend for the shortcuts editor (platform-aware). */
	public static String buildEditorLegendHtml() {
		final String platform = getPlatformDisplayName();
		final StringBuffer sb = new StringBuffer();
		sb.append("<html><body style='width:760px;font-family:sans-serif;font-size:12pt'>");
		sb.append("<b>当前平台：").append(platform).append("</b>");
		sb.append(" · 在此修改只写入本机文件 <code>")
		        .append(getAcceleratorFileName())
		        .append("</code>（Win / Mac / Linux 互不覆盖）。<br/>");
		sb.append("<b>修饰键对应：</b> Ctrl ↔ Cmd　·　Alt ↔ Option　·　Shift 相同<br/>");
		if (Compat.isMacOsX()) {
			sb.append("<b>Mac 默认替代（无物理键 / 系统占用）：</b> ");
			sb.append("Insert→Tab 新建子节点；Shift+Insert→Shift+Tab 新建父节点；");
			sb.append("Alt+Shift+Insert→Cmd+Shift+I 总结；Alt+Space→Cmd+Shift+O 切换导图。");
		}
		else {
			sb.append("<b>Windows / Linux 出厂默认：</b> Insert 新建子节点；Shift+Insert 新建父节点；Alt+Space 切换导图。");
			sb.append(" 「默认快捷键」列可查看出厂键位（即使你已改绑）。");
		}
		sb.append("</body></html>");
		return sb.toString();
	}

	/**
	 * ASCII-safe stroke text for tables/notes (avoids □□ on Windows fonts that lack ⌘⌥⇧).
	 * {@code macStyle=true} names modifiers Cmd/Option; otherwise Ctrl/Alt.
	 */
	public static String formatStrokeReadable(final KeyStroke keyStroke, final boolean macStyle) {
		if (keyStroke == null) {
			return "";
		}
		final int mods = keyStroke.getModifiers();
		final StringBuffer sb = new StringBuffer();
		final boolean meta = (mods & KeyEvent.META_MASK) != 0 || (mods & KeyEvent.META_DOWN_MASK) != 0;
		final boolean ctrl = (mods & KeyEvent.CTRL_MASK) != 0 || (mods & KeyEvent.CTRL_DOWN_MASK) != 0;
		final boolean alt = (mods & KeyEvent.ALT_MASK) != 0 || (mods & KeyEvent.ALT_DOWN_MASK) != 0;
		final boolean shift = (mods & KeyEvent.SHIFT_MASK) != 0 || (mods & KeyEvent.SHIFT_DOWN_MASK) != 0;
		if (macStyle) {
			if (meta || ctrl) {
				appendMod(sb, "Cmd");
			}
			if (alt) {
				appendMod(sb, "Option");
			}
		}
		else {
			if (ctrl || meta) {
				appendMod(sb, "Ctrl");
			}
			if (alt) {
				appendMod(sb, "Alt");
			}
		}
		if (shift) {
			appendMod(sb, "Shift");
		}
		sb.append(simplifyKeyText(KeyEvent.getKeyText(keyStroke.getKeyCode())));
		return sb.toString();
	}

	/** Format a stroke string (e.g. {@code meta shift O}) for Mac-alternative notes. */
	public static String formatMacAltReadable(final String strokeString) {
		if (strokeString == null || strokeString.length() == 0) {
			return "";
		}
		final KeyStroke ks = KeyStroke.getKeyStroke(strokeString);
		if (ks == null) {
			return strokeString;
		}
		return formatStrokeReadable(ks, true);
	}

	private static void appendMod(final StringBuffer sb, final String mod) {
		sb.append(mod);
		sb.append('+');
	}

	private static String simplifyKeyText(final String keyText) {
		if (keyText == null) {
			return "";
		}
		if ("Back Slash".equalsIgnoreCase(keyText)) {
			return "\\";
		}
		if ("Period".equalsIgnoreCase(keyText)) {
			return ".";
		}
		if ("Comma".equalsIgnoreCase(keyText)) {
			return ",";
		}
		if ("Minus".equalsIgnoreCase(keyText)) {
			return "-";
		}
		if ("Equals".equalsIgnoreCase(keyText)) {
			return "=";
		}
		return keyText;
	}
}
