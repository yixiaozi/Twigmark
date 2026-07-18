package org.freeplane.core.ui.components;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import org.freeplane.core.resources.ResourceController;

public final class TabbedPaneWidthUtils {

	private static final int TAB_HORIZONTAL_INSET = 10;

	private TabbedPaneWidthUtils() {
	}

	/** Sum of per-tab minimum widths so all tabs fit on one row at startup. */
	public static int computeMinimumWidth(final JTabbedPane tabs) {
		if (tabs == null || tabs.getTabCount() == 0) {
			return 0;
		}
		final FontMetrics fm = tabs.getFontMetrics(tabs.getFont());
		int total = 0;
		for (int i = 0; i < tabs.getTabCount(); i++) {
			total += measureTabWidth(tabs, i, fm);
		}
		return total;
	}

	private static int measureTabWidth(final JTabbedPane tabs, final int index, final FontMetrics fm) {
		final Component tabComponent = tabs.getTabComponentAt(index);
		if (tabComponent != null) {
			final Dimension preferred = tabComponent.getPreferredSize();
			if (preferred != null && preferred.width > 0) {
				return preferred.width + TAB_HORIZONTAL_INSET;
			}
			final Dimension min = tabComponent.getMinimumSize();
			if (min != null && min.width > 0) {
				return min.width + TAB_HORIZONTAL_INSET;
			}
		}
		final String title = TabCountLabels.stripHtml(tabs.getTitleAt(index));
		int count = -1;
		if (tabComponent instanceof JPanel) {
			count = readCountFromTabComponent(tabComponent);
		}
		return TabCountLabels.computeMinTabWidth(tabs.getFont(), title, count) + TAB_HORIZONTAL_INSET;
	}

	private static int readCountFromTabComponent(final Component tabComponent) {
		if (!(tabComponent instanceof java.awt.Container)) {
			return -1;
		}
		final java.awt.Container container = (java.awt.Container) tabComponent;
		for (int i = 0; i < container.getComponentCount(); i++) {
			final Component child = container.getComponent(i);
			if (child instanceof JLabel) {
				final String text = ((JLabel) child).getText();
				if (text != null && text.matches("\\d+")) {
					try {
						return Integer.parseInt(text);
					}
					catch (final NumberFormatException e) {
						// ignore
					}
				}
			}
		}
		return -1;
	}

	public static void applyPreferredWidth(final JTabbedPane tabs, final String widthPropertyKey, final int fallbackDefault) {
		if (tabs == null) {
			return;
		}
		int width = fallbackDefault;
		try {
			width = Integer.parseInt(ResourceController.getResourceController().getProperty(widthPropertyKey,
			    String.valueOf(fallbackDefault)));
			if (width <= 10) {
				width = computeMinimumWidth(tabs);
			}
		}
		catch (final Exception e) {
			width = computeMinimumWidth(tabs);
		}
		final int height = tabs.getPreferredSize().height > 0 ? tabs.getPreferredSize().height : 32;
		tabs.setPreferredSize(new Dimension(width, height));
		tabs.revalidate();
	}
}
