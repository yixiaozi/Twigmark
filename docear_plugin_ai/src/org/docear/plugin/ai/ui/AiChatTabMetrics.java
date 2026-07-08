package org.docear.plugin.ai.ui;

import org.docear.plugin.ai.chat.AiChatSession;
import org.docear.plugin.ai.chat.AiChatSessionManager;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;

/**
 * Publishes the current mind map's AI chat message count to the right side tab subtitle.
 */
public final class AiChatTabMetrics {

	private AiChatTabMetrics() {
	}

	public static void publishForMap(final MapModel map, final AiChatSessionManager sessionManager) {
		int count = 0;
		if (map != null && sessionManager != null) {
			final AiChatSession session = sessionManager.getOrCreateSession(map);
			if (session != null) {
				count = session.getMessages().size();
			}
		}
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_AI_CHAT, count);
	}

	public static void publishForCurrentMap(final AiChatSessionManager sessionManager) {
		final Controller controller = Controller.getCurrentController();
		publishForMap(controller != null ? controller.getMap() : null, sessionManager);
	}
}
