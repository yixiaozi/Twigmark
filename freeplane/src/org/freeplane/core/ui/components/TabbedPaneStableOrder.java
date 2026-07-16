package org.freeplane.core.ui.components;

import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 * Keeps wrapped {@link JTabbedPane} rows in original top-to-bottom order.
 * Default Windows/Basic L&amp;F rotate the selected row next to the content area,
 * which makes the first tabs appear on the bottom row after wrapping.
 */
public final class TabbedPaneStableOrder {
	private static final String APPLYING = "docear.stableTabOrder.applying";
	private static final String MARKER = "docear.stableTabOrder.marker";

	private TabbedPaneStableOrder() {
	}

	public static void install(final JTabbedPane tabs) {
		if (tabs == null) {
			return;
		}
		UIManager.getDefaults().put("TabbedPane.tabRunOverlay", Integer.valueOf(0));
		UIManager.getDefaults().put("TabbedPane.rotateTabRuns", Boolean.FALSE);
		apply(tabs);
	}

	private static void apply(final JTabbedPane tabs) {
		if (Boolean.TRUE.equals(tabs.getClientProperty(APPLYING))) {
			return;
		}
		final TabbedPaneUI current = tabs.getUI();
		if (current instanceof StableOrderTabbedPaneUI
		        || (current != null && Boolean.TRUE.equals(tabs.getClientProperty(MARKER))
		                && current.getClass().getName().indexOf("WindowsTabbedPaneUI") >= 0
		                && current.getClass().getName().indexOf("$") >= 0)) {
			return;
		}
		tabs.putClientProperty(APPLYING, Boolean.TRUE);
		try {
			final String className = current == null ? "" : current.getClass().getName().toLowerCase();
			if (className.indexOf("windows") >= 0 && installWindowsUi(tabs)) {
				tabs.putClientProperty(MARKER, Boolean.TRUE);
				return;
			}
			tabs.setUI(new StableOrderTabbedPaneUI());
			tabs.putClientProperty(MARKER, Boolean.TRUE);
		}
		catch (Exception e) {
			// leave default UI if customization fails
		}
		finally {
			tabs.putClientProperty(APPLYING, Boolean.FALSE);
		}
	}

	private static boolean installWindowsUi(final JTabbedPane tabs) {
		try {
			tabs.setUI(new com.sun.java.swing.plaf.windows.WindowsTabbedPaneUI() {
				protected boolean shouldRotateTabRuns(final int tabPlacement) {
					return false;
				}
			});
			return true;
		}
		catch (Throwable t) {
			return false;
		}
	}

	private static class StableOrderTabbedPaneUI extends BasicTabbedPaneUI {
		protected boolean shouldRotateTabRuns(final int tabPlacement) {
			return false;
		}
	}
}
