package org.docear.plugin.core.eagle;

import org.freeplane.core.ui.IMenuContributor;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;

public final class EagleIntegrationService {
	private EagleIntegrationService() {
	}

	public static void install(final ModeController modeController) {
		EagleConfig.registerDefaults();
		EagleDisplayFallback.install();
		EagleImagePicker.install();
		EagleNodeNameSync.install(modeController);
		modeController.addAction(new EagleSettingsAction());
		modeController.addAction(new EagleMigrateAction());
		Controller.getCurrentController().addAction(modeController.getAction(EagleSettingsAction.KEY));
		Controller.getCurrentController().addAction(modeController.getAction(EagleMigrateAction.KEY));
		modeController.addMenuContributor(new IMenuContributor() {
			public void updateMenus(final ModeController mc, final MenuBuilder builder) {
				// Same place as Todoist: 附加功能 → 时间
				addMenuIfPresent(builder, "/menu_bar/extras/time", mc);
				addMenuIfPresent(builder, "/menu_bar/extras", mc);
				addMenuIfPresent(builder, "/menu_bar/help", mc);
			}
		});
		// Warm index in background only when libraries are configured/detectable
		new Thread(new Runnable() {
			public void run() {
				try {
					if (!EagleConfig.existingLibraryRoots().isEmpty()) {
						EagleItemIndex.getInstance().ensureLoaded(false);
						LogUtils.info("Eagle index ready: " + EagleItemIndex.getInstance().size() + " item(s)");
					}
				}
				catch (Exception e) {
					LogUtils.warn("Eagle index warmup failed: " + e.getMessage());
				}
			}
		}, "eagle-index-warmup").start();
		LogUtils.info("Eagle integration: path-first display + filename fallback + node-name sync + migrate actions registered.");
	}

	private static void addMenuIfPresent(final MenuBuilder builder, final String menuPath,
			final ModeController modeController) {
		if (builder.get(menuPath) == null) {
			return;
		}
		builder.addSeparator(menuPath, MenuBuilder.AS_CHILD);
		builder.addAction(menuPath, modeController.getAction(EagleMigrateAction.KEY), MenuBuilder.AS_CHILD);
		builder.addAction(menuPath, modeController.getAction(EagleSettingsAction.KEY), MenuBuilder.AS_CHILD);
	}
}
