package org.freeplane.core.ui.components;

import java.awt.LayoutManager;

import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 * Keeps wrapped {@link JTabbedPane} rows in original top-to-bottom order.
 * Default Windows/Basic L&amp;F rotate the selected row next to the content area,
 * which makes the first tabs (e.g. 工作区) appear on the bottom row after wrapping.
 */
public final class TabbedPaneStableOrder {
	private static final String APPLYING = "docear.stableTabOrder.applying";
	private static final String MARKER = "docear.stableTabOrder.installed";

	private TabbedPaneStableOrder() {
	}

	public static void install(final JTabbedPane tabs) {
		if (tabs == null) {
			return;
		}
		UIManager.getDefaults().put("TabbedPane.tabRunOverlay", Integer.valueOf(0));
		apply(tabs);
	}

	private static void apply(final JTabbedPane tabs) {
		if (Boolean.TRUE.equals(tabs.getClientProperty(APPLYING))) {
			return;
		}
		final TabbedPaneUI current = tabs.getUI();
		if (isStableUi(current)) {
			tabs.putClientProperty(MARKER, Boolean.TRUE);
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

	private static boolean isStableUi(final TabbedPaneUI ui) {
		if (ui == null) {
			return false;
		}
		if (ui instanceof StableOrderTabbedPaneUI) {
			return true;
		}
		final String name = ui.getClass().getName();
		// Anonymous Windows override installed by us (subclass name contains '$').
		return name.indexOf("WindowsTabbedPaneUI") >= 0 && name.indexOf('$') >= 0;
	}

	private static boolean installWindowsUi(final JTabbedPane tabs) {
		try {
			tabs.setUI(new com.sun.java.swing.plaf.windows.WindowsTabbedPaneUI() {
				protected boolean shouldRotateTabRuns(final int tabPlacement) {
					return false;
				}

				protected LayoutManager createLayoutManager() {
					if (tabPane.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT) {
						return super.createLayoutManager();
					}
					return new TabbedPaneLayout() {
						protected void rotateTabRuns(final int tabPlacement, final int selectedRun) {
							// no-op
						}
					};
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

		protected LayoutManager createLayoutManager() {
			if (tabPane.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT) {
				return super.createLayoutManager();
			}
			return new TabbedPaneLayout() {
				protected void rotateTabRuns(final int tabPlacement, final int selectedRun) {
					// no-op
				}
			};
		}
	}
}
