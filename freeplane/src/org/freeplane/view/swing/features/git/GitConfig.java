package org.freeplane.view.swing.features.git;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IMapViewManager;

public final class GitConfig {
	public static final String PROP_REPO_PATH = "git.repo.path";
	public static final String PROP_SYNC_INTERVAL = "git.sync.interval.seconds";
	public static final String PROP_AUTO_SYNC = "git.auto.sync";
	/** Auto-pull remote commits each sync interval (default on). */
	public static final String PROP_AUTO_PULL = "git.auto.pull";

	private static final int DEFAULT_SYNC_INTERVAL_SECONDS = 60;
	private static final int MIN_SYNC_INTERVAL_SECONDS = 30;
	private static final int MAX_SYNC_INTERVAL_SECONDS = 3600;

	private GitConfig() {
	}

	public static File locateRepository() {
		final File fixedDataRoot = MindMapDataRootResolver.getFixedDataRoot();
		if (fixedDataRoot != null && isGitRepository(fixedDataRoot)) {
			return fixedDataRoot;
		}
		final File configured = getConfiguredRepositoryPath();
		if (configured != null) {
			return configured;
		}
		for (final File start : candidateStartDirectories()) {
			final File found = walkUpToRepository(start);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	public static String getConfiguredRepositoryPathRaw() {
		final String propertyPath = System.getProperty(PROP_REPO_PATH);
		if (propertyPath != null && propertyPath.trim().length() > 0) {
			return propertyPath.trim();
		}
		final String envPath = System.getenv("DOCEAR_GIT_REPO");
		if (envPath != null && envPath.trim().length() > 0) {
			return envPath.trim();
		}
		return readProperty(PROP_REPO_PATH, "");
	}

	public static int getSyncIntervalSeconds() {
		final String raw = readProperty(PROP_SYNC_INTERVAL, String.valueOf(DEFAULT_SYNC_INTERVAL_SECONDS));
		try {
			final int seconds = Integer.parseInt(raw.trim());
			if (seconds < MIN_SYNC_INTERVAL_SECONDS) {
				return MIN_SYNC_INTERVAL_SECONDS;
			}
			if (seconds > MAX_SYNC_INTERVAL_SECONDS) {
				return MAX_SYNC_INTERVAL_SECONDS;
			}
			return seconds;
		}
		catch (NumberFormatException e) {
			return DEFAULT_SYNC_INTERVAL_SECONDS;
		}
	}

	public static boolean isAutoSyncEnabled() {
		return isTruthyProperty(PROP_AUTO_SYNC, "false");
	}

	/** Prefer remote updates every minute; default enabled. */
	public static boolean isAutoPullEnabled() {
		return isTruthyProperty(PROP_AUTO_PULL, "true");
	}

	private static boolean isTruthyProperty(final String key, final String defaultValue) {
		final String raw = readProperty(key, defaultValue);
		return "true".equalsIgnoreCase(raw.trim()) || "yes".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim());
	}

	private static String readProperty(final String key, final String defaultValue) {
		final File file = localPropertiesFile();
		if (!file.isFile()) {
			return defaultValue;
		}
		FileInputStream in = null;
		try {
			final Properties props = new Properties();
			in = new FileInputStream(file);
			props.load(in);
			return props.getProperty(key, defaultValue).trim();
		}
		catch (IOException e) {
			LogUtils.warn("Git: could not read " + file.getPath(), e);
			return defaultValue;
		}
		finally {
			closeQuietly(in);
		}
	}

	public static void setRepositoryPath(final String path) {
		final File file = localPropertiesFile();
		FileOutputStream out = null;
		try {
			final Properties props = new Properties();
			if (file.isFile()) {
				FileInputStream in = new FileInputStream(file);
				try {
					props.load(in);
				}
				finally {
					in.close();
				}
			}
			props.setProperty(PROP_REPO_PATH, path == null ? "" : path.trim());
			out = new FileOutputStream(file);
			props.store(out, "Docear Git panel settings (local only)");
		}
		catch (IOException e) {
			LogUtils.warn("Git: could not write " + file.getPath(), e);
		}
		finally {
			closeQuietly(out);
		}
	}

	private static File getConfiguredRepositoryPath() {
		final String path = getConfiguredRepositoryPathRaw();
		if (path.length() == 0) {
			return null;
		}
		final File dir = new File(path);
		return isGitRepository(dir) ? dir : null;
	}

	private static List<File> candidateStartDirectories() {
		final Set<File> candidates = new LinkedHashSet<File>();
		addIfDirectory(candidates, new File(System.getProperty("user.dir")));
		addIfDirectory(candidates, MindMapDataRootResolver.getFixedDataRoot());
		addIfDirectory(candidates, MindMapDataRootResolver.getPrimaryScanRoot());
		final File[] scanRoots = MindMapDataRootResolver.getScanRoots();
		if (scanRoots != null) {
			for (int i = 0; i < scanRoots.length; i++) {
				addIfDirectory(candidates, scanRoots[i]);
			}
		}
		addIfDirectory(candidates, getCurrentMapDirectory());
		return new ArrayList<File>(candidates);
	}

	private static File getCurrentMapDirectory() {
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null) {
				return null;
			}
			final IMapViewManager mapViewManager = controller.getMapViewManager();
			if (mapViewManager == null) {
				return null;
			}
			final MapModel map = mapViewManager.getModel();
			if (map == null || map.getURL() == null) {
				return null;
			}
			final URI uri = map.getURL().toURI();
			final File mapFile = new File(uri);
			if (mapFile.isFile()) {
				return mapFile.getParentFile();
			}
			if (mapFile.isDirectory()) {
				return mapFile;
			}
		}
		catch (Exception e) {
			LogUtils.warn("Git: could not resolve current map directory", e);
		}
		return null;
	}

	private static void addIfDirectory(final Set<File> candidates, final File dir) {
		if (dir != null && dir.isDirectory()) {
			try {
				candidates.add(dir.getCanonicalFile());
			}
			catch (IOException e) {
				candidates.add(dir.getAbsoluteFile());
			}
		}
	}

	private static File walkUpToRepository(File dir) {
		while (dir != null) {
			if (isGitRepository(dir)) {
				return dir;
			}
			dir = dir.getParentFile();
		}
		return null;
	}

	static boolean isGitRepository(final File dir) {
		if (dir == null || !dir.isDirectory()) {
			return false;
		}
		final File gitDir = new File(dir, ".git");
		return gitDir.exists();
	}

	private static File localPropertiesFile() {
		final File dir = new File(Compat.getApplicationUserDirectory());
		return org.freeplane.core.util.LocalMachineId.migrateLegacyFile(dir, "git.local.properties", "git.local",
		        ".properties");
	}

	private static void closeQuietly(final java.io.Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			}
			catch (IOException e) {
			}
		}
	}
}
