package org.docear.plugin.mcp;

import java.util.Hashtable;

import org.docear.plugin.mcp.server.McpHttpServer;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.main.osgi.IModeControllerExtensionProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	private static McpHttpServer httpServer;

	@Override
	public void start(final BundleContext context) throws Exception {
		LogUtils.info("Docear MCP plugin starting...");

		final Hashtable<String, String[]> props = new Hashtable<String, String[]>();
		props.put("mode", new String[] { MModeController.MODENAME });

		context.registerService(IModeControllerExtensionProvider.class.getName(),
				new IModeControllerExtensionProvider() {
					@Override
					public void installExtension(final ModeController modeController) {
						DocearMcpController.install(modeController);
						startServerIfNeeded();
						LogUtils.info("Docear MCP controller installed.");
					}
				}, props);
	}

	@Override
	public void stop(final BundleContext context) throws Exception {
		stopServer();
		LogUtils.info("Docear MCP plugin stopped.");
	}

	private static synchronized void startServerIfNeeded() {
		if (!DocearMcpConfig.isEnabled()) {
			LogUtils.info("Docear MCP server is disabled by configuration.");
			return;
		}
		if (httpServer != null) {
			return;
		}
		try {
			httpServer = new McpHttpServer();
			httpServer.start();
		}
		catch (Exception e) {
			LogUtils.warn("Docear MCP server failed to start: " + e.getMessage(), e);
			httpServer = null;
		}
	}

	private static synchronized void stopServer() {
		if (httpServer != null) {
			httpServer.stop();
			httpServer = null;
		}
	}
}
