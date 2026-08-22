package org.docear.plugin.mermaid;

import java.net.URL;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.format.FormatController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.TextController;

/**
 * Registers Mermaid text transformer and format pattern.
 */
public final class MermaidController {

	private MermaidController() {
	}

	public static void install(final ModeController modeController) {
		if (modeController == null) {
			return;
		}
		addLanguageResources();
		final TextController textController = modeController.getExtension(TextController.class);
		if (textController != null) {
			textController.addTextTransformer(new MermaidContentTransformer());
		}
		try {
			final FormatController formatController = modeController.getController()
					.getExtension(FormatController.class);
			if (formatController != null) {
				formatController.addPatternFormat(new MermaidFormat());
			}
		}
		catch (Throwable t) {
			LogUtils.warn("Mermaid: could not register format pattern", t);
		}
		MermaidRenderService.getInstance().ensureStarted();
	}

	private static void addLanguageResources() {
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null) {
				return;
			}
			final ResourceController rc = controller.getResourceController();
			final URL en = MermaidController.class.getResource("/translations/Resources_en.properties");
			if (en != null) {
				rc.addLanguageResources("en", en);
			}
			final URL zh = MermaidController.class.getResource("/translations/Resources_zh_CN.properties");
			if (zh != null) {
				rc.addLanguageResources("zh_CN", zh);
			}
			final URL props = MermaidController.class.getResource("/mermaid.properties");
			if (props != null) {
				rc.addLanguageResources("en", props);
			}
		}
		catch (Throwable t) {
			LogUtils.warn("Mermaid: could not load language resources", t);
		}
	}

	public static void uninstall() {
		// Transformers stay for process lifetime; cache cleared on shutdown.
	}
}
