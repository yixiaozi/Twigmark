package org.freeplane.core.util;

import java.awt.Color;

/**
 * Defines a color with some utility methods.
 * 
 * @author robert.ladstaetter
 */
public class ColorUtils {
	public static final String BLACK = "#000000";

	public static String colorToString(final Color col) {
		if (col == null) {
			return null;
		}
		return String.format("#%02x%02x%02x", col.getRed(), col.getGreen(), col.getBlue());
	}

	public static Color stringToColor(final String str) {
		if (str == null) {
			return null;
		}
		String value = str.trim();
		if (value.length() == 6 && value.charAt(0) != '#') {
			value = "#" + value;
		}
		if (value.length() != 7 || value.charAt(0) != '#') {
			throw new NumberFormatException("wrong color format in " + str);
		}
		return new Color(Integer.parseInt(value.substring(1, 3), 16), Integer.parseInt(value.substring(3, 5), 16),
		        Integer.parseInt(value.substring(5, 7), 16));
	}

	/** Like {@link #stringToColor(String)} but never throws — returns {@code fallback} on bad input. */
	public static Color stringToColor(final String str, final Color fallback) {
		try {
			final Color color = stringToColor(str);
			return color != null ? color : fallback;
		}
		catch (RuntimeException e) {
			return fallback;
		}
	}

	public static Color createColor(final Color color, final int alpha) {
        if(color.getAlpha() == alpha)
    		return color;
    	return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
