package org.docear.plugin.core.todoist;

import org.freeplane.core.ui.IMenuContributor;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;

public final class TodoistIntegrationService {
	private TodoistIntegrationService() {
	}

	public static void install(final ModeController modeController) {
		TodoistConfig.registerDefaults();
		TodoistConfig.getApiToken();
		modeController.addAction(new TodoistSyncAction());
		modeController.addAction(new TodoistImportAction());
		modeController.addAction(new TodoistSettingsAction());
		Controller.getCurrentController().addAction(modeController.getAction(TodoistSyncAction.KEY));
		Controller.getCurrentController().addAction(modeController.getAction(TodoistImportAction.KEY));
		Controller.getCurrentController().addAction(modeController.getAction(TodoistSettingsAction.KEY));
		modeController.addMenuContributor(new IMenuContributor() {
			public void updateMenus(ModeController mc, MenuBuilder builder) {
				addMenuIfPresent(builder, "/menu_bar/extras/time", mc);
				addMenuIfPresent(builder, "/menu_bar/help", mc);
			}
		});
		TodoistAutoSyncService.getInstance().install(modeController);
		LogUtils.info("Todoist integration: sync menu + auto-sync registered.");
	}

	private static void addMenuIfPresent(MenuBuilder builder, String menuPath, ModeController modeController) {
		if (builder.get(menuPath) == null) {
			return;
		}
		builder.addSeparator(menuPath, MenuBuilder.AS_CHILD);
		builder.addAction(menuPath, modeController.getAction(TodoistSyncAction.KEY), MenuBuilder.AS_CHILD);
		builder.addAction(menuPath, modeController.getAction(TodoistImportAction.KEY), MenuBuilder.AS_CHILD);
		builder.addAction(menuPath, modeController.getAction(TodoistSettingsAction.KEY), MenuBuilder.AS_CHILD);
	}
}
