package org.docear.plugin.core.settings;

import java.awt.Frame;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Bridge so product settings / AI sidebar (core & ai bundles) can use MCP webchat
 * LLM profiles and conversation storage without a hard dependency on the MCP bundle.
 * The MCP plugin registers a {@link Backend} on install.
 */
public final class McpRuntimeFacade {
	public interface Backend {
		boolean isConfigEnabled();

		boolean isServerRunning();

		String getEndpoint();

		String getLastError();

		boolean probeHealth();

		boolean isAuditEnabled();

		String getAuditDbPath();

		int getAuditEventCount();

		int getAuditPendingCount();

		String restartServer();

		void openStatusAuditDialog(Frame owner);

		/** Shared webchat owner username used for LLM profiles / desktop history. */
		String getSharedOwnerUsername();

		boolean isSharedLlmConfigured();

		/**
		 * Profile rows without API secrets. Keys: id, name, baseUrl, model, isDefault,
		 * apiKeyConfigured, apiKeyPreview.
		 */
		List listLlmProfiles();

		/**
		 * Resolve endpoint including apiKey. Keys: id, name, baseUrl, apiKey, model.
		 */
		Map resolveLlmEndpoint(String profileId);

		/**
		 * Upsert a profile for the shared owner. Empty id creates a new one.
		 * Empty apiKey keeps the previous secret when updating.
		 */
		Map upsertLlmProfile(String id, String name, String baseUrl, String apiKey, String model,
				boolean isDefault);

		/** Seed / refresh the default shared profile from Product Settings. */
		void syncLlmFromProductSettings(String baseUrl, String apiKey, String model);

		/**
		 * Load desktop sidebar messages for a mind-map key.
		 * Each row: role, content, model, createdAt.
		 */
		List loadDesktopMessages(String mapKey);

		/** Append one user/assistant turn for a desktop map conversation. */
		void appendDesktopChatTurn(String mapKey, String title, String userText, String assistantText,
				String model);

		void clearDesktopConversation(String mapKey);
	}

	private static volatile Backend backend;

	private McpRuntimeFacade() {
	}

	public static void register(final Backend value) {
		backend = value;
	}

	public static Backend get() {
		return backend;
	}

	public static boolean isAvailable() {
		return backend != null;
	}

	public static List safeListProfiles() {
		final Backend b = backend;
		if (b == null) {
			return Collections.EMPTY_LIST;
		}
		try {
			final List list = b.listLlmProfiles();
			return list == null ? Collections.EMPTY_LIST : list;
		}
		catch (Exception e) {
			return Collections.EMPTY_LIST;
		}
	}

	public static Map safeResolveEndpoint(final String profileId) {
		final Backend b = backend;
		if (b == null) {
			return null;
		}
		try {
			return b.resolveLlmEndpoint(profileId);
		}
		catch (Exception e) {
			return null;
		}
	}

	public static boolean safeIsLlmConfigured() {
		final Backend b = backend;
		if (b == null) {
			return false;
		}
		try {
			return b.isSharedLlmConfigured();
		}
		catch (Throwable e) {
			// sqlite-jdbc 3.4x needs slf4j; missing deps must not abort OSGi Activator
			return false;
		}
	}
}
