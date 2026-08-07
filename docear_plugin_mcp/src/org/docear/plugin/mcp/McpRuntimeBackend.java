package org.docear.plugin.mcp;

import java.awt.Frame;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.core.settings.McpRuntimeFacade;
import org.docear.plugin.mcp.audit.McpAuditService;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.ui.McpStatusAuditDialog;
import org.docear.plugin.mcp.webchat.WebchatService;
import org.freeplane.core.util.LogUtils;

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
			// When bound to 0.0.0.0 / LAN IP, probe via loopback.
			String host = DocearMcpConfig.getHost();
			if (DocearMcpConfig.isPublicBind()) {
				host = "127.0.0.1";
			}
			final URL url = new URL("http://" + host + ":" + DocearMcpConfig.getPort() + "/health");
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

	public String getSharedOwnerUsername() {
		return WebchatService.getSharedOwnerUsername();
	}

	public boolean isSharedLlmConfigured() {
		return WebchatService.isSharedLlmConfigured();
	}

	public List listLlmProfiles() {
		try {
			return WebchatService.listProfiles(WebchatService.getSharedOwnerUsername());
		}
		catch (Exception e) {
			LogUtils.warn("listLlmProfiles: " + e.getMessage());
			return Collections.EMPTY_LIST;
		}
	}

	public Map resolveLlmEndpoint(final String profileId) {
		try {
			return WebchatService.resolveLlmEndpoint(WebchatService.getSharedOwnerUsername(), profileId);
		}
		catch (Exception e) {
			LogUtils.warn("resolveLlmEndpoint: " + e.getMessage());
			return null;
		}
	}

	public Map upsertLlmProfile(final String id, final String name, final String baseUrl, final String apiKey,
			final String model, final boolean isDefault) {
		try {
			final Map body = new LinkedHashMap();
			if (id != null && id.trim().length() > 0) {
				body.put("id", JsonValue.ofString(id.trim()));
			}
			body.put("name", JsonValue.ofString(name == null ? "OpenRouter" : name));
			body.put("baseUrl", JsonValue.ofString(baseUrl == null ? "" : baseUrl));
			body.put("apiKey", JsonValue.ofString(apiKey == null ? "" : apiKey));
			body.put("model", JsonValue.ofString(model == null ? "" : model));
			body.put("isDefault", JsonValue.ofBoolean(isDefault));
			return WebchatService.saveProfile(WebchatService.getSharedOwnerUsername(), body);
		}
		catch (Exception e) {
			LogUtils.warn("upsertLlmProfile: " + e.getMessage());
			return null;
		}
	}

	public void syncLlmFromProductSettings(final String baseUrl, final String apiKey, final String model) {
		WebchatService.syncLlmFromProductSettings(baseUrl, apiKey, model);
	}

	public List loadDesktopMessages(final String mapKey) {
		try {
			return WebchatService.loadDesktopMessages(mapKey);
		}
		catch (Exception e) {
			LogUtils.warn("loadDesktopMessages: " + e.getMessage());
			return new ArrayList();
		}
	}

	public void appendDesktopChatTurn(final String mapKey, final String title, final String userText,
			final String assistantText, final String model) {
		try {
			WebchatService.appendDesktopChatTurn(mapKey, title, userText, assistantText, model);
		}
		catch (Exception e) {
			LogUtils.warn("appendDesktopChatTurn: " + e.getMessage());
		}
	}

	public void clearDesktopConversation(final String mapKey) {
		try {
			WebchatService.clearDesktopConversation(mapKey);
		}
		catch (Exception e) {
			LogUtils.warn("clearDesktopConversation: " + e.getMessage());
		}
	}
}
