package org.freeplane.core.ui.components;

import java.awt.Dimension;
import java.awt.FontMetrics;

import javax.swing.JTabbedPane;

import org.freeplane.core.resources.ResourceController;

public final class TabbedPaneWidthUtils {

	private TabbedPaneWidthUtils() {
	}

	public static int computeMinimumWidth(final JTabbedPane tabs) {
		if (tabs == null || tabs.getTabCount() == 0) {
			return 360;
		}
		final FontMetrics fm = tabs.getFontMetrics(tabs.getFont());
		int total = 20;
		for (int i = 0; i < tabs.getTabCount(); i++) {
			total += fm.stringWidth(tabs.getTitleAt(i)) + 34;
		}
		return Math.max(360, total);
	}

	public static void applyPreferredWidth(final JTabbedPane tabs, final String widthPropertyKey, final int fallbackDefault) {
		if (tabs == null) {
			return;
		}
		tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		int width = fallbackDefault;
		try {
			width = Integer.parseInt(ResourceController.getResourceController().getProperty(widthPropertyKey,
			    String.valueOf(fallbackDefault)));
			if (width <= 10) {
				width = fallbackDefault;
			}
		}
		catch (Exception e) {
			width = fallbackDefault;
		}
		width = Math.max(width, computeMinimumWidth(tabs));
		final int height = tabs.getPreferredSize().height > 0 ? tabs.getPreferredSize().height : 40;
		tabs.setPreferredSize(new Dimension(width, height));
		tabs.revalidate();
	}
}
