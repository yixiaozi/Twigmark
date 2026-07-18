package org.docear.plugin.mcp;

import java.util.Hashtable;

import org.docear.plugin.core.settings.McpRuntimeFacade;
import org.docear.plugin.mcp.audit.McpAuditService;
import org.docear.plugin.mcp.server.McpHttpServer;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.main.osgi.IModeControllerExtensionProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	private static McpHttpServer httpServer;
	private static volatile String lastError = "";

	@Override
	public void start(final BundleContext context) throws Exception {
		LogUtils.info("Docear MCP plugin starting...");
		McpRuntimeFacade.register(new McpRuntimeBackend());

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
		McpRuntimeFacade.register(null);
		LogUtils.info("Docear MCP plugin stopped.");
	}

	public static synchronized boolean isServerRunning() {
		return httpServer != null;
	}

	public static String getEndpoint() {
		return "http://" + DocearMcpConfig.getHost() + ":" + DocearMcpConfig.getPort() + "/mcp";
	}

	public static String getLastError() {
		return lastError == null ? "" : lastError;
	}

	/** Lightweight HTTP /health probe against configured host/port. */
	public static boolean probeHealth() {
		return new McpRuntimeBackend().probeHealth();
	}

	public static synchronized String restartServer() {
		stopServer();
		if (!DocearMcpConfig.isEnabled()) {
			lastError = "";
			return "disabled";
		}
		try {
			McpAuditService.start();
			httpServer = new McpHttpServer();
			httpServer.start();
			lastError = "";
			return "running";
		}
		catch (Exception e) {
			httpServer = null;
			lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			LogUtils.warn("Docear MCP server failed to start: " + lastError, e);
			return "error";
		}
	}

	private static synchronized void startServerIfNeeded() {
		if (!DocearMcpConfig.isEnabled()) {
			LogUtils.info("Docear MCP server is disabled by configuration.");
			lastError = "";
			return;
		}
		if (httpServer != null) {
			return;
		}
		try {
			McpAuditService.start();
			httpServer = new McpHttpServer();
			httpServer.start();
			lastError = "";
		}
		catch (Exception e) {
			lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			LogUtils.warn("Docear MCP server failed to start: " + lastError, e);
			httpServer = null;
		}
	}

	private static synchronized void stopServer() {
		if (httpServer != null) {
			httpServer.stop();
			httpServer = null;
		}
		McpAuditService.shutdown();
	}
}
