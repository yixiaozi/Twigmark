package org.docear.plugin.mcp;

import org.docear.plugin.mcp.ui.McpStatusAuditAction;
import org.freeplane.core.ui.IMenuContributor;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;

public final class DocearMcpController {

	private static DocearMcpController instance;

	private DocearMcpController() {
	}

	public static void install(final ModeController modeController) {
		if (instance == null) {
			instance = new DocearMcpController();
		}
		modeController.addAction(new McpStatusAuditAction());
		Controller.getCurrentController().addAction(modeController.getAction(McpStatusAuditAction.KEY));
		modeController.addMenuContributor(new IMenuContributor() {
			public void updateMenus(final ModeController mc, final MenuBuilder builder) {
				addMenuIfPresent(builder, "/menu_bar/extras", mc);
				addMenuIfPresent(builder, "/menu_bar/help", mc);
			}
		});
		org.docear.plugin.mcp.client.CursorAiClientSync.install();
	}

	public static DocearMcpController getInstance() {
		return instance;
	}

	private static void addMenuIfPresent(final MenuBuilder builder, final String menuPath,
	        final ModeController modeController) {
		if (builder.get(menuPath) == null) {
			return;
		}
		builder.addSeparator(menuPath, MenuBuilder.AS_CHILD);
		builder.addAction(menuPath, modeController.getAction(McpStatusAuditAction.KEY), MenuBuilder.AS_CHILD);
	}
}
