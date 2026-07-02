package org.docear.plugin.mcp;

import org.freeplane.features.mode.ModeController;

public final class DocearMcpController {

	private static DocearMcpController instance;

	private DocearMcpController() {
	}

	public static void install(final ModeController modeController) {
		if (instance == null) {
			instance = new DocearMcpController();
		}
	}

	public static DocearMcpController getInstance() {
		return instance;
	}
}
