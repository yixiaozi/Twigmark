package org.freeplane.core.ui.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public final class TabCountLabels {

	private static final int HORIZONTAL_PADDING = 8;
	private static final int VERTICAL_PADDING = 1;

	private TabCountLabels() {
	}

	public static String format(final String title, final int count) {
		return title == null ? "" : title;
	}

	public static Dimension computeTabComponentSize(final Font font, final String title, final int count) {
		final Font titleFont = font != null ? font : new JLabel().getFont();
		final Font countFont = titleFont.deriveFont(Font.PLAIN, Math.max(9f, titleFont.getSize2D() - 2f));
		final JLabel scratch = new JLabel();
		scratch.setFont(titleFont);
		final FontMetrics titleFm = scratch.getFontMetrics(titleFont);
		scratch.setFont(countFont);
		final FontMetrics countFm = scratch.getFontMetrics(countFont);
		final String safeTitle = title != null ? title : "";
		final String countText = count >= 0 ? String.valueOf(count) : "";
		final int textWidth = Math.max(titleFm.stringWidth(safeTitle),
		    countText.length() > 0 ? countFm.stringWidth(countText) : 0);
		final int width = textWidth + HORIZONTAL_PADDING;
		final int height = titleFm.getAscent() + titleFm.getDescent() + countFm.getAscent() + countFm.getDescent()
		    + VERTICAL_PADDING;
		return new Dimension(width, height);
	}

	public static int computeMinTabWidth(final Font font, final String title, final int count) {
		return computeTabComponentSize(font, title, count).width;
	}

	public static Component createTabComponent(final String title, final int count) {
		if (title == null) {
			return new JLabel("");
		}
		if (count < 0) {
			final JLabel titleOnly = new JLabel(title);
			titleOnly.setHorizontalAlignment(SwingConstants.CENTER);
			return titleOnly;
		}
		final JPanel panel = new JPanel(new GridBagLayout());
		panel.setOpaque(false);
		final Dimension size = computeTabComponentSize(panel.getFont(), title, count);
		panel.setPreferredSize(size);
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1;
		gbc.insets = new Insets(0, 0, 0, 0);
		gbc.anchor = GridBagConstraints.CENTER;
		final JLabel titleLabel = new JLabel(title);
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		panel.add(titleLabel, gbc);
		gbc.gridy = 1;
		gbc.insets = new Insets(0, 0, 0, 0);
		final JLabel countLabel = new JLabel(String.valueOf(count));
		final Font font = countLabel.getFont();
		countLabel.setFont(font.deriveFont(Font.PLAIN, Math.max(9f, font.getSize2D() - 2f)));
		countLabel.setForeground(new Color(102, 102, 102));
		countLabel.setHorizontalAlignment(SwingConstants.CENTER);
		panel.add(countLabel, gbc);
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
