package org.docear.plugin.mcp;

import java.awt.Frame;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.docear.plugin.core.settings.McpRuntimeFacade;
import org.docear.plugin.mcp.audit.McpAuditService;
import org.docear.plugin.mcp.ui.McpStatusAuditDialog;

final class McpRuntimeBackend implements McpRuntimeFacade.Backend {
	public boolean isConfigEnabled() {
		return DocearMcpConfig.isEnabled();
	}

	public boolean isServerRunning() {
		return Activator.isServerRunning();
	}

	public String getEndpoint() {
		return Activator.getEndpoint();
	}

	public String getLastError() {
		return Activator.getLastError();
	}

	public boolean probeHealth() {
		if (!DocearMcpConfig.isEnabled()) {
			return false;
		}
		HttpURLConnection connection = null;
		try {
			final URL url = new URL("http://" + DocearMcpConfig.getHost() + ":" + DocearMcpConfig.getPort() + "/health");
			connection = (HttpURLConnection) url.openConnection();
			connection.setConnectTimeout(800);
			connection.setReadTimeout(800);
			connection.setRequestMethod("GET");
			final int code = connection.getResponseCode();
			if (code != 200) {
				return false;
			}
			final InputStream in = connection.getInputStream();
			if (in != null) {
				final byte[] buf = new byte[64];
				in.read(buf);
				in.close();
			}
			return true;
		}
		catch (Exception e) {
			return false;
		}
		finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	public boolean isAuditEnabled() {
		return DocearMcpConfig.isAuditEnabled();
	}

	public String getAuditDbPath() {
		return DocearMcpConfig.getAuditDbFile().getAbsolutePath();
	}

	public int getAuditEventCount() {
		return McpAuditService.countAuditEvents();
	}

	public int getAuditPendingCount() {
		return McpAuditService.pendingAuditCount();
	}

	public String restartServer() {
		return Activator.restartServer();
	}

	public void openStatusAuditDialog(final Frame owner) {
		McpStatusAuditDialog.show(owner);
	}
}
