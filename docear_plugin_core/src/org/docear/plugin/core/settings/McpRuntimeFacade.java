package org.docear.plugin.core.settings;

import java.awt.Frame;

/**
 * Bridge so product settings (core) can show MCP status/audit without depending on
 * the MCP bundle. The MCP plugin registers a {@link Backend} on install.
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
}
