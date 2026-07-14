package org.freeplane.plugin.workspace.components.favorites;

import java.awt.Color;
import java.awt.Insets;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.border.Border;

import org.freeplane.plugin.workspace.features.nodepins.TagColorStore;

public final class TagChipFactory {

	private static final Insets CHIP_MARGIN = new Insets(1, 8, 1, 8);

	private TagChipFactory() {
	}

	public static JToggleButton createFilterChip(final String tag, final String label, final boolean selected) {
		final Color bg = tag == null ? TagColorStore.getInstance().getNeutralColor()
				: TagColorStore.getInstance().getColor(tag);
		final JToggleButton button = new JToggleButton(label != null ? label : "");
		button.setSelected(selected);
		applyChipStyle(button, bg, selected);
		return button;
	}

	public static JButton createPresetChip(final String tag) {
		final Color bg = tag == null ? TagColorStore.getInstance().getNeutralColor()
				: TagColorStore.getInstance().getColor(tag);
		final JButton button = new JButton(tag != null ? tag : "");
		applyChipStyle(button, bg, false);
		return button;
	}

	public static JToggleButton createEditChip(final String tag, final boolean selected) {
		final Color bg = tag == null ? TagColorStore.getInstance().getNeutralColor()
				: TagColorStore.getInstance().getColor(tag);
		final JToggleButton button = new JToggleButton(tag != null ? tag : "");
		button.setSelected(selected);
		applyChipStyle(button, bg, selected);
		return button;
	}

	public static void applyChipStyle(final AbstractButton button, final Color background, final boolean selected) {
		final Color bg = background != null ? background : TagColorStore.getInstance().getNeutralColor();
		final Color fg = TagColorStore.contrastingTextColor(bg);
		button.setFocusPainted(false);
		button.setMargin(CHIP_MARGIN);
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.setBackground(selected ? TagColorStore.darkerVariant(bg, 0.88f) : bg);
		button.setForeground(fg);
		final Border outer = selected
				? BorderFactory.createLineBorder(TagColorStore.getInstance().getSelectedBorderColor(), 2)
				: BorderFactory.createLineBorder(TagColorStore.darkerVariant(bg, 0.75f), 1);
		button.setBorder(BorderFactory.createCompoundBorder(outer, BorderFactory.createEmptyBorder(1, 4, 1, 4)));
	}
}
