package org.docear.plugin.core.todoist;

import org.freeplane.core.ui.IMenuContributor;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;

public final class TodoistIntegrationService {
	private TodoistIntegrationService() {
	}

	public static void install(final ModeController modeController) {
		TodoistConfig.registerDefaults();
		TodoistConfig.getApiToken();
		modeController.addAction(new TodoistSyncAction());
		modeController.addAction(new TodoistSettingsAction());
		Controller.getCurrentController().addAction(modeController.getAction(TodoistSyncAction.KEY));
		Controller.getCurrentController().addAction(modeController.getAction(TodoistSettingsAction.KEY));
		modeController.addMenuContributor(new IMenuContributor() {
			public void updateMenus(ModeController mc, MenuBuilder builder) {
				addMenuIfPresent(builder, "/menu_bar/extras/time", mc);
				addMenuIfPresent(builder, "/menu_bar/help", mc);
			}
		});
		TodoistNodeMetaIo.install(modeController);
		TodoistAutoSyncService.getInstance().install(modeController);
		installMapOpenStamping(modeController);
		LogUtils.info("Todoist integration: unified sync + auto-sync registered.");
	}

	/** When a map opens, stamp node↔task links from the local mapping store (hidden XML). */
	private static void installMapOpenStamping(final ModeController modeController) {
		modeController.getMapController().addMapLifeCycleListener(new IMapLifeCycleListener() {
			public void onCreate(final MapModel map) {
				try {
					TodoistNodeLocator.stampMapFromStore(map, TodoistMappingStore.get());
				}
				catch (Exception e) {
					LogUtils.warn("Todoist: stamp on map open failed", e);
				}
			}

			public void onRemove(MapModel map) {
			}

			public void onSavedAs(MapModel map) {
			}

			public void onSaved(MapModel map) {
			}
		});
	}

	private static void addMenuIfPresent(MenuBuilder builder, String menuPath, ModeController modeController) {
		if (builder.get(menuPath) == null) {
			return;
		}
		builder.addSeparator(menuPath, MenuBuilder.AS_CHILD);
		builder.addAction(menuPath, modeController.getAction(TodoistSyncAction.KEY), MenuBuilder.AS_CHILD);
		builder.addAction(menuPath, modeController.getAction(TodoistSettingsAction.KEY), MenuBuilder.AS_CHILD);
	}
}
