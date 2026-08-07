package org.freeplane.core.ui;

import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

/**
 * Run: {@code java -cp ... org.freeplane.core.ui.PlatformHotKeyGuideStandaloneTest}
 */
public final class PlatformHotKeyGuideStandaloneTest {

	public static void main(final String[] args) {
		if (!"accelerator-linux.properties".equals(PlatformHotKeyGuide.getAcceleratorFileName())
		        && !"accelerator-windows.properties".equals(PlatformHotKeyGuide.getAcceleratorFileName())
		        && !"accelerator-mac.properties".equals(PlatformHotKeyGuide.getAcceleratorFileName())) {
			throw new IllegalStateException("file name: " + PlatformHotKeyGuide.getAcceleratorFileName());
		}
		final KeyStroke insert = KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0);
		if (!PlatformHotKeyGuide.usesMacUnavailableKey(insert)) {
			throw new IllegalStateException("INSERT should be unavailable on Mac keyboards");
		}
		final KeyStroke tab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);
		if (PlatformHotKeyGuide.usesMacUnavailableKey(tab)) {
			throw new IllegalStateException("TAB should be fine");
		}
		if (!"TAB".equals(PlatformHotKeyGuide.getMacDefaultAlternatives().get("NewChildAction"))) {
			throw new IllegalStateException("NewChild Mac default");
		}
		final String html = PlatformHotKeyGuide.buildEditorLegendHtml();
		if (html.indexOf("Ctrl") < 0 || html.indexOf("Command") < 0) {
			throw new IllegalStateException("legend missing modifier map");
		}
		System.out.println("PlatformHotKeyGuideStandaloneTest OK platform="
		        + PlatformHotKeyGuide.getPlatformDisplayName());
	}
}
