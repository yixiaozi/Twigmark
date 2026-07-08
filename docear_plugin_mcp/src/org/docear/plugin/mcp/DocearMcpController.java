package org.docear.plugin.mcp;

import org.docear.plugin.mcp.client.CursorAiClientSync;
import org.freeplane.features.mode.ModeController;

public final class DocearMcpController {

	private static DocearMcpController instance;

	private DocearMcpController() {
	}

	public static void install(final ModeController modeController) {
		if (instance == null) {
			instance = new DocearMcpController();
			CursorAiClientSync.install();
		}
	}

	public static DocearMcpController getInstance() {
		return instance;
	}
}
