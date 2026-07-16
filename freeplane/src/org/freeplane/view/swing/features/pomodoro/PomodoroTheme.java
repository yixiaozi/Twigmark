package org.freeplane.view.swing.features.pomodoro;

import java.awt.Color;

import org.freeplane.core.resources.ResourceController;

/**
 * Compact skins for the floating pomodoro dock.
 */
final class PomodoroTheme {
	static final String PROP_SKIN = "pomodoro_skin";
	static final String SKIN_EMBER = "ember";
	static final String SKIN_TOMATO = "tomato";
	static final String SKIN_MINT = "mint";

	final Color bg;
	final Color card;
	final Color accent;
	final Color text;
	final Color muted;
	final Color border;
	final String name;

	private PomodoroTheme(final String name, final Color bg, final Color card, final Color accent, final Color text,
			final Color muted, final Color border) {
		this.name = name;
		this.bg = bg;
		this.card = card;
		this.accent = accent;
		this.text = text;
		this.muted = muted;
		this.border = border;
	}

	static PomodoroTheme current() {
		final String skin = ResourceController.getResourceController().getProperty(PROP_SKIN, SKIN_EMBER);
		return forName(skin);
	}

	static PomodoroTheme forName(final String skin) {
		if (SKIN_TOMATO.equals(skin)) {
			return new PomodoroTheme(SKIN_TOMATO, new Color(0x2A1210), new Color(0x3A1C18), new Color(0xE84B3C),
					new Color(0xFFF1ED), new Color(0xC49A92), new Color(0x5A2E28));
		}
		if (SKIN_MINT.equals(skin)) {
			return new PomodoroTheme(SKIN_MINT, new Color(0x15201C), new Color(0x1E2C27), new Color(0x3DBE8B),
					new Color(0xE8F5EF), new Color(0x8AA89A), new Color(0x2F433B));
		}
		return new PomodoroTheme(SKIN_EMBER, new Color(0x22201E), new Color(0x2E2A28), new Color(0xE07A3D),
				new Color(0xF2EBE4), new Color(0x9A9188), new Color(0x4A433C));
	}

	static void setSkin(final String skin) {
		ResourceController.getResourceController().setProperty(PROP_SKIN, skin == null ? SKIN_EMBER : skin);
	}

	static String nextSkin(final String current) {
		if (SKIN_EMBER.equals(current)) {
			return SKIN_TOMATO;
		}
		if (SKIN_TOMATO.equals(current)) {
			return SKIN_MINT;
		}
		return SKIN_EMBER;
	}
}
