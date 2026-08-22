package org.docear.plugin.mermaid;

import java.util.Hashtable;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.main.osgi.IModeControllerExtensionProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	@Override
	public void start(final BundleContext context) throws Exception {
		LogUtils.info("Docear Mermaid plugin starting...");
		final Hashtable<String, String[]> props = new Hashtable<String, String[]>();
		props.put("mode", new String[] { MModeController.MODENAME });
		context.registerService(IModeControllerExtensionProvider.class.getName(),
				new IModeControllerExtensionProvider() {
					@Override
					public void installExtension(final ModeController modeController) {
						MermaidController.install(modeController);
						LogUtils.info("Docear Mermaid controller installed.");
					}
				}, props);
	}

	@Override
	public void stop(final BundleContext context) throws Exception {
		MermaidController.uninstall();
		MermaidRenderService.shutdown();
		LogUtils.info("Docear Mermaid plugin stopped.");
	}
}
