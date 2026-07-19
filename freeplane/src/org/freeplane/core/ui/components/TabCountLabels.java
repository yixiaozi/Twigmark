package org.freeplane.core.ui.components;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.freeplane.core.ui.theme.DocearUiTheme;

public final class TabCountLabels {

	private static final int HORIZONTAL_PADDING = 8;
	private static final int VERTICAL_PADDING = 3;
	private static final int ICON_GAP = 2;

	private TabCountLabels() {
	}

	public static String format(final String title, final int count) {
		return title == null ? "" : title;
	}

	public static Dimension computeTabComponentSize(final Font font, final String title, final int count) {
		return computeTabComponentSize(font, title, count, null);
	}

	public static Dimension computeTabComponentSize(final Font font, final String title, final int count,
	        final Icon icon) {
		final Font titleFont = font != null ? font : new JLabel().getFont();
		final Font countFont = titleFont.deriveFont(Font.PLAIN, Math.max(9f, titleFont.getSize2D() - 1.5f));
		final JLabel scratch = new JLabel();
		scratch.setFont(titleFont);
		final FontMetrics titleFm = scratch.getFontMetrics(titleFont);
		scratch.setFont(countFont);
		final FontMetrics countFm = scratch.getFontMetrics(countFont);
		final String safeTitle = title != null ? title : "";
		final String countText = count >= 0 ? String.valueOf(count) : "";
		final int iconW = icon != null ? icon.getIconWidth() : 0;
		final int iconH = icon != null ? icon.getIconHeight() : 0;
		final int textWidth = Math.max(titleFm.stringWidth(safeTitle),
		    countText.length() > 0 ? countFm.stringWidth(countText) : 0);
		final int contentWidth = Math.max(iconW, textWidth);
		final int width = contentWidth + HORIZONTAL_PADDING;
		int height = titleFm.getAscent() + titleFm.getDescent() + VERTICAL_PADDING;
		if (count >= 0) {
			height += countFm.getAscent() + countFm.getDescent();
		}
		if (icon != null) {
			height += iconH + ICON_GAP;
		}
		return new Dimension(width, Math.max(height, 26));
	}

	public static int computeMinTabWidth(final Font font, final String title, final int count) {
		return computeTabComponentSize(font, title, count).width;
	}

	public static Component createTabComponent(final String title, final int count) {
		return createTabComponent(title, count, null, false);
	}

	public static Component createTabComponent(final String title, final int count, final Icon icon) {
		return createTabComponent(title, count, icon, false);
	}

	public static Component createTabComponent(final String title, final int count, final Icon icon,
	        final boolean selected) {
		if (title == null) {
			return new JLabel("");
		}
		final JPanel panel = new JPanel(new GridBagLayout());
		panel.setOpaque(false);
		final Dimension size = computeTabComponentSize(panel.getFont(), title, count, icon);
		panel.setPreferredSize(size);
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.anchor = GridBagConstraints.CENTER;
		int row = 0;
		if (icon != null) {
			gbc.gridy = row++;
			gbc.insets = new Insets(0, 0, 0, 0);
			final JLabel iconLabel = new JLabel(icon);
			iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
			panel.add(iconLabel, gbc);
		}
		gbc.gridy = row++;
		gbc.insets = new Insets(icon != null ? ICON_GAP : 0, 0, 0, 0);
		final JLabel titleLabel = new JLabel(title);
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		titleLabel.setForeground(selected ? DocearUiTheme.TEXT : DocearUiTheme.TEXT_MUTED);
		titleLabel.setFont(DocearUiTheme.font(11.5f, selected ? Font.BOLD : Font.PLAIN));
		panel.add(titleLabel, gbc);
		if (count >= 0) {
			gbc.gridy = row;
			gbc.insets = new Insets(1, 0, 0, 0);
			final JLabel countLabel = new JLabel(String.valueOf(count));
			countLabel.setFont(DocearUiTheme.font(10f, Font.PLAIN));
			countLabel.setForeground(selected ? DocearUiTheme.ACCENT_DEEP : DocearUiTheme.TEXT_FAINT);
			countLabel.setHorizontalAlignment(SwingConstants.CENTER);
			panel.add(countLabel, gbc);
		}
		return panel;
	}

	public static String stripHtml(final String title) {
		if (title == null) {
			return "";
		}
		String text = title;
		if (text.startsWith("<html>")) {
			text = text.replaceAll("(?s)<[^>]+>", "");
		}
		final int breakIndex = text.indexOf('\n');
		if (breakIndex >= 0) {
			text = text.substring(0, breakIndex);
		}
		return text.trim();
	}
}
