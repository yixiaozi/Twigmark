package org.freeplane.plugin.workspace.components.favorites;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JToggleButton;

import org.freeplane.plugin.workspace.features.nodepins.TagColorStore;

/**
 * Soft filled pill chips — color on the chip body only, no outer double-border frame.
 */
public final class TagChipFactory {

	private static final Insets CHIP_PADDING = new Insets(3, 10, 3, 10);
	private static final int ARC = 14;

	private TagChipFactory() {
	}

	public static JToggleButton createFilterChip(final String tag, final String label, final boolean selected) {
		final Color bg = tag == null ? TagColorStore.getInstance().getNeutralColor()
				: TagColorStore.getInstance().getColor(tag);
		final JToggleButton button = new RoundedChipToggle(label != null ? label : "", bg, selected);
		button.setSelected(selected);
		applyChipStyle(button, bg, selected);
		return button;
	}

	public static JButton createPresetChip(final String tag) {
		final Color bg = tag == null ? TagColorStore.getInstance().getNeutralColor()
				: TagColorStore.getInstance().getColor(tag);
		final JButton button = new RoundedChipButton(tag != null ? tag : "", bg, false);
		applyChipStyle(button, bg, false);
		return button;
	}

	public static JToggleButton createEditChip(final String tag, final boolean selected) {
		final Color bg = tag == null ? TagColorStore.getInstance().getNeutralColor()
				: TagColorStore.getInstance().getColor(tag);
		final JToggleButton button = new RoundedChipToggle(tag != null ? tag : "", bg, selected);
		button.setSelected(selected);
		applyChipStyle(button, bg, selected);
		return button;
	}

	public static void applyChipStyle(final AbstractButton button, final Color background, final boolean selected) {
		final Color bg = background != null ? background : TagColorStore.getInstance().getNeutralColor();
		final Color fg = TagColorStore.contrastingTextColor(bg);
		button.setFocusPainted(false);
		button.setMargin(CHIP_PADDING);
		button.setOpaque(false);
		button.setContentAreaFilled(false);
		button.setBorderPainted(false);
		button.setForeground(fg);
		button.setFont(button.getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN));
		button.setBorder(BorderFactory.createEmptyBorder(CHIP_PADDING.top, CHIP_PADDING.left, CHIP_PADDING.bottom,
				CHIP_PADDING.right));
		if (button instanceof RoundedChip) {
			((RoundedChip) button).setChipColors(bg, selected);
		}
		else {
			button.setBackground(selected ? TagColorStore.darkerVariant(bg, 0.9f) : bg);
		}
	}

	private interface RoundedChip {
		void setChipColors(Color background, boolean selected);
	}

	private static final class RoundedChipToggle extends JToggleButton implements RoundedChip {
		private static final long serialVersionUID = 1L;
		private Color chipBackground;
		private boolean chipSelected;

		RoundedChipToggle(final String text, final Color background, final boolean selected) {
			super(text);
			setChipColors(background, selected);
		}

		public void setChipColors(final Color background, final boolean selected) {
			chipBackground = background != null ? background : TagColorStore.getInstance().getNeutralColor();
			chipSelected = selected;
			repaint();
		}

		protected void paintComponent(final Graphics g) {
			paintChip(g, this, chipBackground, chipSelected || isSelected());
			super.paintComponent(g);
		}

		public Dimension getPreferredSize() {
			final Dimension d = super.getPreferredSize();
			d.height = Math.max(d.height, 24);
			return d;
		}
	}

	private static final class RoundedChipButton extends JButton implements RoundedChip {
		private static final long serialVersionUID = 1L;
		private Color chipBackground;
		private boolean chipSelected;

		RoundedChipButton(final String text, final Color background, final boolean selected) {
			super(text);
			setChipColors(background, selected);
		}

		public void setChipColors(final Color background, final boolean selected) {
			chipBackground = background != null ? background : TagColorStore.getInstance().getNeutralColor();
			chipSelected = selected;
			repaint();
		}

		protected void paintComponent(final Graphics g) {
			paintChip(g, this, chipBackground, chipSelected);
			super.paintComponent(g);
		}
	}

	private static void paintChip(final Graphics g, final AbstractButton button, final Color background,
			final boolean selected) {
		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final Color base = background != null ? background : TagColorStore.getInstance().getNeutralColor();
		final Color fill = selected ? TagColorStore.darkerVariant(base, 0.88f) : base;
		g2.setColor(fill);
		g2.fillRoundRect(0, 0, button.getWidth(), button.getHeight(), ARC, ARC);
		if (selected) {
			g2.setColor(TagColorStore.getInstance().getSelectedBorderColor());
			g2.drawRoundRect(0, 0, button.getWidth() - 1, button.getHeight() - 1, ARC, ARC);
		}
		g2.dispose();
	}
}
