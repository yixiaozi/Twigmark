package org.docear.plugin.drawio;

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
		LogUtils.info("Docear Draw.io plugin starting...");
		final Hashtable<String, String[]> props = new Hashtable<String, String[]>();
		props.put("mode", new String[] { MModeController.MODENAME });
		context.registerService(IModeControllerExtensionProvider.class.getName(),
				new IModeControllerExtensionProvider() {
					@Override
					public void installExtension(final ModeController modeController) {
						DocearDrawioController.install(modeController);
						LogUtils.info("Docear Draw.io controller installed.");
					}
				}, props);
	}

	@Override
	public void stop(final BundleContext context) throws Exception {
		DocearDrawioController.uninstall();
		DrawioEmbedServer.stopServer();
		LogUtils.info("Docear Draw.io plugin stopped.");
	}
}
