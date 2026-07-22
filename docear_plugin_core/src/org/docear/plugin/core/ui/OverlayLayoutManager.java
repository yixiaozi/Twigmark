package org.docear.plugin.core.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;

public class OverlayLayoutManager implements LayoutManager {
	public static String WRAPPED_LAYOUT = "overlay_orig_layout|";
	public static String ALIGN_TOP = "overlay_align_top|";
	public static String ALIGN_CENTER = "overlay_align_center|";
	public static String ALIGN_BOTTOM = "overlay_align_bottom|";
	public static String FLOAT_LEFT = "overlay_float_left|";
	public static String FLOAT_RIGHT = "overlay_float_right|";
	public static String FLOAT_MIDDLE = "overlay_float_middle|";
	
	
	private final LayoutManager wrappedLayout;
	private final List<Component> overlayComponents = new ArrayList<Component>();

	/***********************************************************************************
	 * CONSTRUCTORS
	 **********************************************************************************/

	public OverlayLayoutManager(LayoutManager layout) {
		wrappedLayout = layout;
	}

	/***********************************************************************************
	 * METHODS
	 **********************************************************************************/
	public static boolean instanceOf(LayoutManager layout) {
		if(layout instanceof OverlayLayoutManager) {
			return true;
		}
		return false;
	}
	/***********************************************************************************
	 * REQUIRED METHODS FOR INTERFACES
	 **********************************************************************************/
	
	@Override
	public void addLayoutComponent(String name, Component comp) {
		if(name != null) {
			if(name.contains("overlay_")) {
				addLayoutComponent(name.split("[|]"), comp);
			}
			else {
				if(wrappedLayout != null) {
					wrappedLayout.addLayoutComponent(name, comp);
				}
			}
		}
	}
	
	public void addLayoutComponent(String[] positionConstraints, Component comp) {
		overlayComponents.add(comp);
	}

	@Override
	public void removeLayoutComponent(Component comp) {
		if(!overlayComponents.remove(comp)) {
			if(wrappedLayout != null) {
				wrappedLayout.removeLayoutComponent(comp);
			}
		}
	}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		if(wrappedLayout == null) {
			return parent.getPreferredSize();
		}
		return wrappedLayout.preferredLayoutSize(parent);
	}

	@Override
	public Dimension minimumLayoutSize(Container parent) {
		if(wrappedLayout == null) {
			return parent.getMinimumSize();
		}
		return wrappedLayout.minimumLayoutSize(parent);
	}

	@Override
	public void layoutContainer(Container parent) {
		if (wrappedLayout != null) {
			wrappedLayout.layoutContainer(parent);
		}
		final Insets insets = parent.getInsets() != null ? parent.getInsets() : new Insets(5, 5, 5, 5);
		final int gap = 8;
		final int top = Math.max(insets.top, 0) + 8;
		final int parentW = Math.max(1, parent.getWidth());
		final int parentH = Math.max(1, parent.getHeight());
		// Never let overlays cover most of the map — keep them as floating cards.
		final int maxOverlayW = Math.max(96, Math.min(360, parentW / 3));
		// Cap height so overlays stay floating cards, not full-height sheets.
		final int maxOverlayH = Math.max(48, Math.min(420, parentH - top - Math.max(insets.bottom, 0) - 16));
		int tr = parentW - insets.right;
		for (Component overlayComp : overlayComponents) {
			if (!overlayComp.isVisible()) {
				continue;
			}
			final Dimension prefSize = overlayComp.getPreferredSize();
			int width = Math.max(1, prefSize != null ? prefSize.width : 120);
			int height = Math.max(1, prefSize != null ? prefSize.height : 32);
			width = Math.min(width, maxOverlayW);
			height = Math.min(height, maxOverlayH);
			// Also respect component-reported maximum when smaller than the soft cap.
			final Dimension max = overlayComp.getMaximumSize();
			if (max != null) {
				if (max.width > 0 && max.width < Integer.MAX_VALUE / 2) {
					width = Math.min(width, max.width);
				}
				if (max.height > 0 && max.height < Integer.MAX_VALUE / 2) {
					height = Math.min(height, max.height);
				}
			}
			tr -= width + gap;
			overlayComp.setBounds(Math.max(insets.left, tr), top, width, height);
		}
	}
}
