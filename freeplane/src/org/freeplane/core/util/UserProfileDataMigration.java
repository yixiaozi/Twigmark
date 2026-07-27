package org.freeplane.core.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.main.application.LastOpenedList;

/**
 * Restores session and preference data when the active profile was reset
 * (e.g. after moving from {@code %APPDATA%\\Docear} to {@code {workingDirectory}/data}).
 */
public final class UserProfileDataMigration {

	private static final String LEGACY_CONFIG_DIR_NAME = "\u6b63\u5e38\u914d\u7f6e";
	private static final String EXTERNAL_BACKUP_DIR_NAME = "_data - \u526f\u672c";
	private static final String LAST_OPENED = "lastOpened_1.0.20";
	private static final String OPENED_NOW = "openedNow_1.0.20";

	private UserProfileDataMigration() {
	}

	public static void migrateSessionStateIfNeeded(final ResourceController resourceController) {
		if (resourceController == null) {
			return;
		}
		if (needsSessionRestore(resourceController)) {
			final Properties legacy = loadBestLegacyAutoProperties();
			if (legacy != null) {
				boolean changed = false;
				if (isEmptyProperty(resourceController, OPENED_NOW) && !isEmptyProperty(legacy, OPENED_NOW)) {
					resourceController.setProperty(OPENED_NOW, legacy.getProperty(OPENED_NOW));
					changed = true;
				}
				if (shouldRestoreLastOpened(resourceController, legacy)) {
					resourceController.setProperty(LAST_OPENED, legacy.getProperty(LAST_OPENED));
					changed = true;
				}
				if (!resourceController.getBooleanProperty(LastOpenedList.LOAD_LAST_MAPS)) {
					resourceController.setProperty(LastOpenedList.LOAD_LAST_MAPS, "true");
					changed = true;
				}
				if (!resourceController.getBooleanProperty(LastOpenedList.LOAD_LAST_MAP)) {
					resourceController.setProperty(LastOpenedList.LOAD_LAST_MAP, "true");
					changed = true;
				}
				if (!resourceController.getBooleanProperty("always_load_last_maps")) {
					resourceController.setProperty("always_load_last_maps", "true");
					changed = true;
				}
				if (changed) {
					LogUtils.info("UserProfileDataMigration: restored last session map list from legacy profile");
				}
			}
		}
		remapForeignOsRecentMapsIfNeeded(resourceController);
	}

	/**
	 * After copying a library from Windows, {@code lastOpened_*} still points at
	 * {@code E:\yixiaozi\...}. Rewrite entries that exist under the current working directory
	 * into the scoped recent-map key for this root path.
	 */
	public static void remapForeignOsRecentMapsIfNeeded(final ResourceController resourceController) {
		if (resourceController == null) {
			return;
		}
		boolean changed = false;
		changed |= remapForeignOsListProperty(resourceController, LAST_OPENED);
		changed |= remapForeignOsListProperty(resourceController, OPENED_NOW);
		if (changed) {
			LogUtils.info("UserProfileDataMigration: remapped foreign-OS recent map paths to working directory");
		}
	}

	private static boolean remapForeignOsListProperty(final ResourceController resourceController, final String baseKey) {
		final String scopedKey = WorkingDirectoryMapPaths.propertyKey(baseKey);
		final String rawScoped = resourceController.getProperty(scopedKey, null);
		final String rawLegacy = scopedKey.equals(baseKey) ? null : resourceController.getProperty(baseKey, null);
		final String raw = (rawScoped != null && rawScoped.trim().length() > 0) ? rawScoped : rawLegacy;
		if (raw == null || raw.trim().length() == 0) {
			return false;
		}
		final List values = ConfigurationUtils.decodeListValue(raw, true);
		if (values == null || values.isEmpty()) {
			return false;
		}
		boolean changed = false;
		final List remapped = new ArrayList();
		final LinkedHashSet seen = new LinkedHashSet();
		for (int i = 0; i < values.size(); i++) {
			final String entry = String.valueOf(values.get(i));
			final String next = remapRestoreableEntry(entry);
			if (next == null) {
				changed = true;
				continue;
			}
			if (!next.equals(entry)) {
				changed = true;
			}
			if (seen.add(next)) {
				remapped.add(next);
			}
			else {
				changed = true;
			}
		}
		if (!changed && scopedKey.equals(baseKey)) {
			return false;
		}
		if (!changed && rawScoped != null && rawScoped.trim().length() > 0) {
			return false;
		}
		resourceController.setProperty(scopedKey, ConfigurationUtils.encodeListValue(remapped, true));
		return true;
	}

	/** @return remapped restoreable, same entry, or {@code null} to drop */
	private static String remapRestoreableEntry(final String restoreable) {
		if (restoreable == null || restoreable.length() == 0) {
			return null;
		}
		final String lower = restoreable.toLowerCase();
		if (lower.indexOf("doceardist") >= 0 || lower.indexOf("freeplaneapplications.mm") >= 0
		        || lower.indexOf("freeplanetutorial.mm") >= 0) {
			return null;
		}
		final File resolved = WorkingDirectoryMapPaths.resolveStoredFile(restoreable);
		if (resolved != null && resolved.isFile()) {
			if (!WorkingDirectoryMapPaths.belongsToCurrentWorkingDirectory(resolved.getAbsolutePath())) {
				return null;
			}
			return WorkingDirectoryMapPaths.toMindMapRestoreable(resolved);
		}
		final int sep = restoreable.indexOf(':');
		if (sep <= 0 || sep >= restoreable.length() - 1) {
			return restoreable;
		}
		final String path = restoreable.substring(sep + 1);
		if (MindMapDataRootResolver.looksLikeWindowsAbsolutePath(path) && !Compat.isWindowsOS()) {
			return null;
		}
		if (MindMapDataRootResolver.looksLikeUnixAbsolutePath(path) && Compat.isWindowsOS()) {
			return null;
		}
		return restoreable;
	}

	public static File findBestFavoritesBackup(final String projectId) {
		if (projectId == null || projectId.length() == 0) {
			return null;
		}
		File bestFile = null;
		int bestCount = 0;
		for (int i = 0; i < getFavoritesBackupCandidates(projectId).size(); i++) {
			final File candidate = (File) getFavoritesBackupCandidates(projectId).get(i);
			if (!candidate.isFile()) {
				continue;
			}
			final int count = countFavoritesInFile(candidate);
			if (count > bestCount) {
				bestCount = count;
				bestFile = candidate;
			}
		}
		return bestFile;
	}

	public static int countFavoritesInFile(final File favoritesFile) {
		if (favoritesFile == null || !favoritesFile.isFile()) {
			return 0;
		}
		final Properties properties = new Properties();
		InputStream in = null;
		try {
			in = new FileInputStream(favoritesFile);
			properties.load(in);
			final String favoritesValue = properties.getProperty("favorites", "");
			if (favoritesValue.length() == 0) {
				return 0;
			}
			int count = 0;
			final String[] paths = favoritesValue.split("\n");
			for (int i = 0; i < paths.length; i++) {
				if (paths[i].trim().length() > 0) {
					count++;
				}
			}
			return count;
		}
		catch (final Exception e) {
			return 0;
		}
		finally {
			FileUtils.silentlyClose(in);
		}
	}

	private static List getFavoritesBackupCandidates(final String projectId) {
		final List candidates = new ArrayList();
		final File configDir = MindMapDataRootResolver.getApplicationConfigDirectory();
		if (configDir != null) {
			candidates.add(new File(configDir, projectId + File.separator + "favorites.settings.bak"));
			candidates.add(new File(new File(configDir, LEGACY_CONFIG_DIR_NAME), projectId + File.separator + "favorites.settings"));
		}
		final File dataRoot = MindMapDataRootResolver.getFixedDataRoot();
		if (dataRoot != null) {
			final File driveRoot = dataRoot.getParentFile() != null ? dataRoot.getParentFile().getParentFile() : null;
			if (driveRoot != null) {
				candidates.add(new File(new File(driveRoot, EXTERNAL_BACKUP_DIR_NAME), projectId + File.separator + "favorites.settings"));
			}
		}
		final String appData = System.getenv("APPDATA");
		if (appData != null) {
			candidates.add(new File(new File(appData, "Docear"), projectId + File.separator + "favorites.settings"));
		}
		final File legacyDocear = new File(System.getProperty("user.home", ""), ".docear");
		candidates.add(new File(new File(legacyDocear, projectId), "favorites.settings"));
		return candidates;
	}

	private static boolean needsSessionRestore(final ResourceController resourceController) {
		if (isEmptyProperty(resourceController, OPENED_NOW) && isWeakLastOpened(resourceController.getProperty(LAST_OPENED, ""))) {
			return true;
		}
		return isWeakLastOpened(resourceController.getProperty(LAST_OPENED, ""))
		        && isEmptyProperty(resourceController, OPENED_NOW);
	}

	private static boolean shouldRestoreLastOpened(final ResourceController current, final Properties legacy) {
		if (isEmptyProperty(legacy, LAST_OPENED)) {
			return false;
		}
		final String currentValue = current.getProperty(LAST_OPENED, "");
		if (isEmptyProperty(current, LAST_OPENED)) {
			return true;
		}
		return isWeakLastOpened(currentValue) && !isWeakLastOpened(legacy.getProperty(LAST_OPENED, ""));
	}

	private static boolean isWeakLastOpened(final String value) {
		if (value == null || value.trim().length() == 0) {
			return true;
		}
		final String lower = value.toLowerCase();
		return lower.contains("freeplaneapplications.mm")
		        || lower.contains("docear-welcome.mm")
		        || lower.contains("doceardist")
		        || lower.contains("\\doc\\");
	}

	private static boolean isEmptyProperty(final ResourceController resourceController, final String key) {
		final String value = resourceController.getProperty(key, "");
		return value == null || value.trim().length() == 0;
	}

	private static boolean isEmptyProperty(final Properties properties, final String key) {
		final String value = properties.getProperty(key, "");
		return value == null || value.trim().length() == 0;
	}

	private static Properties loadBestLegacyAutoProperties() {
		Properties best = null;
		int bestScore = 0;
		for (int i = 0; i < getAutoPropertiesCandidates().size(); i++) {
			final File candidate = (File) getAutoPropertiesCandidates().get(i);
			final Properties loaded = loadAutoProperties(candidate);
			if (loaded == null) {
				continue;
			}
			final int score = scoreAutoProperties(loaded);
			if (score > bestScore) {
				bestScore = score;
				best = loaded;
			}
		}
		return best;
	}

	private static List getAutoPropertiesCandidates() {
		final List candidates = new ArrayList();
		final File configDir = MindMapDataRootResolver.getApplicationConfigDirectory();
		if (configDir != null) {
			candidates.add(new File(new File(configDir, LEGACY_CONFIG_DIR_NAME), "auto.properties"));
		}
		final String appData = System.getenv("APPDATA");
		if (appData != null) {
			candidates.add(new File(new File(appData, "Docear"), "auto.properties"));
		}
		candidates.add(new File(new File(System.getProperty("user.home", ""), ".docear"), "auto.properties"));
		return candidates;
	}

	private static int scoreAutoProperties(final Properties properties) {
		int score = 0;
		if (!isEmptyProperty(properties, OPENED_NOW)) {
			score += 10;
		}
		if (!isEmptyProperty(properties, LAST_OPENED) && !isWeakLastOpened(properties.getProperty(LAST_OPENED, ""))) {
			score += 5;
		}
		return score;
	}

	private static Properties loadAutoProperties(final File file) {
		if (file == null || !file.isFile()) {
			return null;
		}
		final Properties properties = new Properties();
		InputStream in = null;
		try {
			in = new FileInputStream(file);
			properties.load(in);
			return properties;
		}
		catch (final IOException e) {
			return null;
		}
		finally {
			FileUtils.silentlyClose(in);
		}
	}
}
