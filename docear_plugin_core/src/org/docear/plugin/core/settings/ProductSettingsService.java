package org.docear.plugin.core.settings;

import java.awt.EventQueue;
import java.io.File;

import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.IMenuContributor;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.view.swing.features.finance.FinanceLedgerService;

/**
 * Registers the product settings dialog, prefs defaults, and optional first-run setup.
 */
public final class ProductSettingsService {
	private ProductSettingsService() {
	}

	public static void install(final ModeController modeController) {
		registerDefaults();
		modeController.addAction(new ProductSettingsAction());
		Controller.getCurrentController().addAction(modeController.getAction(ProductSettingsAction.KEY));
		modeController.addMenuContributor(new IMenuContributor() {
			public void updateMenus(final ModeController mc, final MenuBuilder builder) {
				addMenuIfPresent(builder, "/menu_bar/extras", mc);
				addMenuIfPresent(builder, "/menu_bar/help", mc);
				addMenuIfPresent(builder, "/menu_bar/file", mc);
			}
		});
		ResourceController.getResourceController().addPropertyChangeListener(new IFreeplanePropertyListener() {
			public void propertyChanged(final String propertyName, final String newValue, final String oldValue) {
				if (MindMapDataRootResolver.WORKING_DIRECTORY_SYSTEM_PROPERTY.equals(propertyName)) {
					applyWorkingDirectoryFromPreferences(newValue);
					return;
				}
				if (MindMapDataRootResolver.CONFIG_DIRECTORY_SYSTEM_PROPERTY.equals(propertyName)) {
					applyConfigDirectoryFromPreferences(newValue);
				}
			}
		});
		scheduleFirstRunIfNeeded();
		LogUtils.info("Product settings hub registered.");
	}

	private static void registerDefaults() {
		final ResourceController resources = ResourceController.getResourceController();
		resources.setDefaultProperty(MindMapDataRootResolver.WORKING_DIRECTORY_SYSTEM_PROPERTY,
		        MindMapDataRootResolver.getWorkingDirectory().getAbsolutePath());
		resources.setDefaultProperty(MindMapDataRootResolver.CONFIG_DIRECTORY_SYSTEM_PROPERTY,
		        MindMapDataRootResolver.getApplicationConfigDirectory().getAbsolutePath());
		resources.setDefaultProperty("mcp.enabled", "true");
		resources.setDefaultProperty("mcp.host", "127.0.0.1");
		resources.setDefaultProperty("mcp.port", "7720");
		resources.setDefaultProperty("mcp.readonly", "false");
		resources.setDefaultProperty("mcp.cursorPlugin.sync.enabled", "true");
		resources.setDefaultProperty("mcp.audit.enabled", "true");
		resources.setDefaultProperty(FinanceLedgerService.PROP_MAP_PATH, FinanceLedgerService.DEFAULT_FILENAME);
		resources.setDefaultProperty("quickcapture.inbox_directory", "");
		resources.setDefaultProperty("quickcapture.inbox_filename", "\u6536\u4ef6\u7bb1.mm");
		resources.setProperty(MindMapDataRootResolver.WORKING_DIRECTORY_SYSTEM_PROPERTY,
		        MindMapDataRootResolver.getWorkingDirectory().getAbsolutePath());
		resources.setProperty(MindMapDataRootResolver.CONFIG_DIRECTORY_SYSTEM_PROPERTY,
		        MindMapDataRootResolver.getApplicationConfigDirectory().getAbsolutePath());
	}

	private static void applyWorkingDirectoryFromPreferences(final String newValue) {
		if (newValue == null || newValue.trim().length() == 0) {
			return;
		}
		if (!MindMapDataRootResolver.isUsableWorkingDirectoryPath(newValue)) {
			LogUtils.warn("Ignoring unusable working directory from preferences: " + newValue);
			return;
		}
		final File dir = new File(newValue.trim());
		final File current = MindMapDataRootResolver.getWorkingDirectory();
		if (samePath(current, dir)) {
			return;
		}
		MindMapDataRootResolver.setWorkingDirectory(dir);
		LogUtils.info("Working directory updated from preferences: " + dir.getAbsolutePath());
	}

	private static void applyConfigDirectoryFromPreferences(final String newValue) {
		if (newValue == null || newValue.trim().length() == 0) {
			return;
		}
		if (!MindMapDataRootResolver.isUsableWorkingDirectoryPath(newValue)) {
			LogUtils.warn("Ignoring unusable config directory from preferences: " + newValue);
			return;
		}
		final File dir = MindMapDataRootResolver.normalizeChosenConfigDirectory(new File(newValue.trim()));
		final File current = MindMapDataRootResolver.getApplicationConfigDirectory();
		if (samePath(current, dir)) {
			return;
		}
		MindMapDataRootResolver.setConfigDirectory(dir);
		LogUtils.info("Config directory updated from preferences: " + dir.getAbsolutePath());
	}

	private static boolean samePath(final File current, final File dir) {
		try {
			if (current.getCanonicalFile().equals(dir.getCanonicalFile())) {
				return true;
			}
		}
		catch (Exception e) {
			if (current.getAbsolutePath().equalsIgnoreCase(dir.getAbsolutePath())) {
				return true;
			}
		}
		return false;
	}

	private static void addMenuIfPresent(final MenuBuilder builder, final String menuPath,
	        final ModeController modeController) {
		if (builder.get(menuPath) == null) {
			return;
		}
		builder.addSeparator(menuPath, MenuBuilder.AS_CHILD);
		builder.addAction(menuPath, modeController.getAction(ProductSettingsAction.KEY), MenuBuilder.AS_CHILD);
	}

	private static void scheduleFirstRunIfNeeded() {
		if (!MindMapDataRootResolver.needsFirstRunSetup()) {
			return;
		}
		EventQueue.invokeLater(new Runnable() {
			private int attempts;

			public void run() {
				final Controller controller = Controller.getCurrentController();
				if (controller == null || controller.getViewController() == null
				        || controller.getViewController().getFrame() == null
				        || !controller.getViewController().getFrame().isDisplayable()) {
					if (attempts++ < 120) {
						EventQueue.invokeLater(this);
					}
					return;
				}
				try {
					ProductSettingsDialog.showDialog(true);
				}
				catch (Exception e) {
					LogUtils.warn("First-run product settings failed", e);
					MindMapDataRootResolver.markSetupCompleted();
				}
			}
		});
	}
}
