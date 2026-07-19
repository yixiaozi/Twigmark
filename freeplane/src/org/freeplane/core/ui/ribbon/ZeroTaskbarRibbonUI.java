package org.freeplane.core.ui.ribbon;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;

import org.pushingpixels.flamingo.internal.ui.ribbon.BasicRibbonUI;

/**
 * Removes the fixed top taskbar strip in Flamingo ribbon (the empty row between
 * the window title bar and the Home/Nodes/… task tabs).
 * <p>
 * Must declare {@link #createUI}: Swing resolves UI delegates via that static
 * factory. Without it, {@code UIManager.put("RibbonUI", …)} still ends up
 * calling {@link BasicRibbonUI#createUI}, which returns a plain BasicRibbonUI
 * with the default 24px taskbar — leaving a blank strip.
 */
public class ZeroTaskbarRibbonUI extends BasicRibbonUI {

	public static ComponentUI createUI(final JComponent c) {
		return new ZeroTaskbarRibbonUI();
	}

	@Override
	public int getTaskbarHeight() {
		return 0;
	}

	@Override
	protected boolean isUsingTitlePane() {
		return true;
	}

	@Override
	protected void installComponents() {
		super.installComponents();
		// Remove taskbar panel and application menu button from the component tree
		// so they don't participate in layout calculations
		if (taskBarPanel != null) {
			ribbon.remove(taskBarPanel);
		}
		if (applicationMenuButton != null) {
			ribbon.remove(applicationMenuButton);
		}
		// Ensure the client property is set so layout/paint logic skips taskbar area
		ribbon.putClientProperty(IS_USING_TITLE_PANE, Boolean.TRUE);
	}
}
