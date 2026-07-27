package org.freeplane.core.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.features.url.UrlManager;

/**
 * Portable layout:
 * <pre>
 * {appRoot}/                      software install directory
 *   working-directory.txt         the only primary product setting
 * {workingDirectory}/             mind maps and user content
 *   data/                         all application configuration (R/W)
 * </pre>
 * Working directory may be overridden by {@code -Dorg.docear.working.directory}
 * or {@code DOCEAR_WORKING_DIRECTORY}. Legacy aliases
 * {@code org.docear.data.root} / {@code DOCEAR_DATA_ROOT} are still accepted.
 */
public final class MindMapDataRootResolver {

	/** Filename under the software root that stores the working directory path. */
	public static final String WORKING_DIRECTORY_FILE_NAME = "working-directory.txt";
	/** Config folder name under the working directory. */
	public static final String CONFIG_DIR_NAME = "data";
	/** Legacy config folder name (accepted when {@link #CONFIG_DIR_NAME} is absent). */
	public static final String LEGACY_CONFIG_DIR_NAME = "_data";
	/** Default working directory name relative to the software root. */
	public static final String DEFAULT_WORKING_DIR_NAME = "workspace";

	/** JVM system property for the working directory. */
	public static final String WORKING_DIRECTORY_SYSTEM_PROPERTY = "org.docear.working.directory";
	/** Environment variable for the working directory. */
	public static final String WORKING_DIRECTORY_ENV = "DOCEAR_WORKING_DIRECTORY";

	/**
	 * @deprecated Use {@link #WORKING_DIRECTORY_SYSTEM_PROPERTY}.
	 */
	public static final String DATA_ROOT_SYSTEM_PROPERTY = "org.docear.data.root";
	/**
	 * @deprecated Use {@link #WORKING_DIRECTORY_ENV}.
	 */
	public static final String DATA_ROOT_ENV = "DOCEAR_DATA_ROOT";
	/**
	 * @deprecated Persisted under {@code data/auto.properties}; prefer {@link #WORKING_DIRECTORY_FILE_NAME}.
	 */
	public static final String SCAN_ROOT_PROPERTY = "mindmap_data_scan_root";

	public static final String DEFAULT_PROJECT_ID = "default";
	/**
	 * @deprecated Empty sentinel; use {@link #getWorkingDirectory()}.
	 */
	public static final String FIXED_DATA_ROOT_PATH = "";
	/**
	 * @deprecated Use {@link #DEFAULT_PROJECT_ID}.
	 */
	public static final String FIXED_PROJECT_ID = DEFAULT_PROJECT_ID;

	private static final String WORKSPACE_CONTROLLER = "org.freeplane.plugin.workspace.WorkspaceController";
	private static final String WORKSPACE_SETTINGS_PROJECTS_KEY =
	    "org.freeplane.plugin.workspace.mindmapmode.model.projects";
	private static final String WORKSPACE_SETTINGS_PROJECTS_SEPARATOR = ",";
	private static final String GLOBAL_RESOURCE_DIR_PROPERTY = "org.freeplane.globalresourcedir";

	private static volatile File cachedWorkingDirectory;
	private static volatile File cachedConfigDirectory;
	/** Set when the user just picked a brand-new / empty working directory. */
	private static volatile boolean pendingDefaultContentSeed;
	private static volatile EmptyDirectorySeeder emptyDirectorySeeder;
	private static volatile boolean configuringInteractively;

	/**
	 * Optional hook (registered by Docear core) to copy sample mind maps into an empty working directory.
	 * Must never delete or overwrite existing user files.
	 */
	public interface EmptyDirectorySeeder {
		void seedDefaults(File workingDirectory);
	}

	private MindMapDataRootResolver() {
	}

	public static void setEmptyDirectorySeeder(final EmptyDirectorySeeder seeder) {
		emptyDirectorySeeder = seeder;
	}

	/** True when {@code working-directory.txt} (or env/sys override) already points at a usable directory. */
	public static boolean isWorkingDirectoryConfigured() {
		if (usableDirectoryFromPath(System.getProperty(WORKING_DIRECTORY_SYSTEM_PROPERTY)) != null) {
			return true;
		}
		if (usableDirectoryFromPath(System.getProperty(DATA_ROOT_SYSTEM_PROPERTY)) != null) {
			return true;
		}
		if (usableDirectoryFromPath(System.getenv(WORKING_DIRECTORY_ENV)) != null) {
			return true;
		}
		if (usableDirectoryFromPath(System.getenv(DATA_ROOT_ENV)) != null) {
			return true;
		}
		return readWorkingDirectoryFile() != null;
	}

	/**
	 * Paths that are absolute on another OS (e.g. {@code E:\yixiaozi} on macOS) must not be used:
	 * Java treats them as relative and creates a literal folder under the install directory.
	 */
	public static boolean isUsableWorkingDirectoryPath(final String path) {
		if (path == null) {
			return false;
		}
		final String trimmed = path.trim();
		if (trimmed.length() == 0 || trimmed.startsWith("#")) {
			return false;
		}
		if (!Compat.isWindowsOS()) {
			if (looksLikeWindowsAbsolutePath(trimmed)) {
				return false;
			}
			// After absolutizing on Unix, "E:\yixiaozi" becomes ".../E:\yixiaozi" — reject that name.
			final String name = new File(trimmed).getName();
			if (looksLikeWindowsAbsolutePath(name)) {
				return false;
			}
			return true;
		}
		if (looksLikeUnixAbsolutePath(trimmed) && trimmed.startsWith("/") && !trimmed.startsWith("//")) {
			return false;
		}
		return true;
	}

	public static boolean looksLikeWindowsAbsolutePath(final String path) {
		if (path == null || path.length() < 2) {
			return false;
		}
		final char c0 = path.charAt(0);
		final char c1 = path.charAt(1);
		if (((c0 >= 'A' && c0 <= 'Z') || (c0 >= 'a' && c0 <= 'z')) && c1 == ':') {
			return true;
		}
		return path.startsWith("\\\\") || path.startsWith("//");
	}

	public static boolean looksLikeUnixAbsolutePath(final String path) {
		return path != null && path.startsWith("/") && !looksLikeWindowsAbsolutePath(path);
	}

	public static boolean isEffectivelyEmptyWorkingDirectory(final File directory) {
		if (directory == null || !directory.exists()) {
			return true;
		}
		if (!directory.isDirectory()) {
			return false;
		}
		final File[] children = directory.listFiles();
		if (children == null || children.length == 0) {
			return true;
		}
		for (int i = 0; i < children.length; i++) {
			final File child = children[i];
			if (child == null) {
				continue;
			}
			final String name = child.getName();
			if (name.startsWith(".") || name.equalsIgnoreCase("Thumbs.db") || name.equalsIgnoreCase("desktop.ini")) {
				continue;
			}
			if (isConfigDirectoryName(name)) {
				continue;
			}
			return false;
		}
		return true;
	}

	/** Software install / root directory (parent of {@code resources/}). */
	public static File getApplicationRoot() {
		final String resourceDir = System.getProperty(GLOBAL_RESOURCE_DIR_PROPERTY);
		if (resourceDir != null && resourceDir.trim().length() > 0) {
			final File resources = new File(resourceDir.trim());
			final File parent = resources.getAbsoluteFile().getParentFile();
			if (parent != null) {
				return parent;
			}
		}
		try {
			final ResourceController rc = ResourceController.getResourceController();
			if (rc != null) {
				final String base = rc.getInstallationBaseDir();
				if (base != null && base.trim().length() > 0) {
					return new File(base.trim());
				}
			}
		}
		catch (final Exception e) {
			// early startup
		}
		return new File(System.getProperty("user.dir"));
	}

	/**
	 * The single primary product setting: where mind maps and {@code data/} live.
	 * Always non-null; created on demand.
	 * When {@code working-directory.txt} is missing (and no env override), prompts once.
	 */
	public static File getWorkingDirectory() {
		File cached = cachedWorkingDirectory;
		if (cached != null) {
			return cached;
		}
		synchronized (MindMapDataRootResolver.class) {
			if (cachedWorkingDirectory != null) {
				return cachedWorkingDirectory;
			}
			final File resolved = resolveWorkingDirectory();
			ensureDirectory(resolved);
			cachedWorkingDirectory = resolved;
			return resolved;
		}
	}

	/**
	 * Application configuration directory: {@code {workingDirectory}/data}.
	 * Accepts legacy {@code _data} when {@code data} does not exist yet.
	 * Always non-null; created on demand.
	 */
	public static File getApplicationConfigDirectory() {
		File cached = cachedConfigDirectory;
		if (cached != null) {
			return cached;
		}
		synchronized (MindMapDataRootResolver.class) {
			if (cachedConfigDirectory != null) {
				return cachedConfigDirectory;
			}
			final File config = resolveConfigDirectory(getWorkingDirectory());
			ensureDirectory(config);
			cachedConfigDirectory = config;
			return config;
		}
	}

	/** Persist the working directory under the software root and refresh caches. */
	public static void setWorkingDirectory(final File workingDirectory) {
		if (workingDirectory == null) {
			return;
		}
		final File absolute = workingDirectory.getAbsoluteFile();
		if (!isUsableWorkingDirectoryPath(absolute.getAbsolutePath())) {
			throw new IllegalArgumentException(
			        "Working directory path is not valid on this operating system: " + absolute.getAbsolutePath());
		}
		ensureDirectory(absolute);
		writeWorkingDirectoryFile(absolute);
		synchronized (MindMapDataRootResolver.class) {
			cachedWorkingDirectory = absolute;
			cachedConfigDirectory = null;
		}
		System.setProperty(WORKING_DIRECTORY_SYSTEM_PROPERTY, absolute.getAbsolutePath());
		System.setProperty("org.freeplane.userfpdir", getApplicationConfigDirectory().getAbsolutePath());
		try {
			final ResourceController rc = ResourceController.getResourceController();
			if (rc != null) {
				rc.setProperty(WORKING_DIRECTORY_SYSTEM_PROPERTY, absolute.getAbsolutePath());
			}
		}
		catch (final Exception e) {
			// ResourceController may not be ready during very early startup
		}
	}

	/** Marker file under {@code data/} written after first-run setup. */
	public static final String SETUP_COMPLETED_MARKER = "setup.completed";

	public static File getWorkingDirectoryMarkerFile() {
		final File[] markers = workingDirectoryMarkerCandidates();
		for (int i = 0; i < markers.length; i++) {
			if (markers[i] != null && markers[i].isFile()) {
				return markers[i];
			}
		}
		return resolveWritableWorkingDirectoryMarker();
	}

	public static boolean needsFirstRunSetup() {
		final File marker = new File(getApplicationConfigDirectory(), SETUP_COMPLETED_MARKER);
		if (marker.isFile()) {
			return false;
		}
		// Existing profiles already have preferences — treat as set up once.
		final File autoProperties = new File(getApplicationConfigDirectory(), "auto.properties");
		if (autoProperties.isFile()) {
			markSetupCompleted();
			return false;
		}
		return true;
	}

	public static void markSetupCompleted() {
		final File marker = new File(getApplicationConfigDirectory(), SETUP_COMPLETED_MARKER);
		BufferedWriter writer = null;
		try {
			ensureDirectory(marker.getParentFile());
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(marker), "UTF-8"));
			writer.write(String.valueOf(System.currentTimeMillis()));
			writer.newLine();
		}
		catch (final Exception e) {
			LogUtils.warn("Could not write setup marker: " + e.getMessage());
		}
		finally {
			FileUtils.silentlyClose(writer);
		}
	}

	/**
	 * @deprecated Alias of {@link #getWorkingDirectory()}.
	 */
	public static File getLibraryDataRoot() {
		return getWorkingDirectory();
	}

	/**
	 * @deprecated Alias of {@link #getWorkingDirectory()}.
	 */
	public static File getFixedDataRoot() {
		return getWorkingDirectory();
	}

	public static File getLogDirectory() {
		return new File(getApplicationConfigDirectory(), "logs");
	}

	public static File getProjectDataDirectory() {
		final File configDir = getApplicationConfigDirectory();
		return new File(configDir, resolveProjectIdForConfigDir(configDir));
	}

	/**
	 * Project settings base under the working directory: prefer {@code data/},
	 * fall back to legacy {@code _data/}.
	 */
	public static File getProjectSettingsBaseDirectory(final File workingDirectory) {
		if (workingDirectory == null) {
			return getApplicationConfigDirectory();
		}
		return resolveConfigDirectory(workingDirectory);
	}

	public static boolean isConfigDirectoryName(final String name) {
		return CONFIG_DIR_NAME.equalsIgnoreCase(name) || LEGACY_CONFIG_DIR_NAME.equalsIgnoreCase(name);
	}

	/** Finds project id from {@code data/} (or legacy {@code _data/}) children. */
	public static String resolveProjectIdForDataRoot(final File workingDirectory) {
		if (workingDirectory == null) {
			return DEFAULT_PROJECT_ID;
		}
		return resolveProjectIdForConfigDir(resolveConfigDirectory(workingDirectory));
	}

	public static File getPrimaryScanRoot() {
		final File[] roots = getScanRoots();
		return roots.length > 0 ? roots[0] : getWorkingDirectory();
	}

	/**
	 * Directories to scan for .mm files: working directory, workspace projects, open maps.
	 */
	public static File[] getScanRoots() {
		final Set roots = new LinkedHashSet();
		addCanonicalRoot(roots, getWorkingDirectory());
		collectProjectRootsFromWorkspace(roots);
		collectProjectRootsFromSettings(roots);
		addOpenMapDirectories(roots);
		addCanonicalRoot(roots, getSelectedProjectRoot());
		addCanonicalRoot(roots, getCurrentMapProjectRoot());
		return normalizeScanRoots(roots);
	}

	public static String getRelativePathWithinScanRoots(final File directory) {
		if (directory == null) {
			return null;
		}
		try {
			final String dirPath = directory.getCanonicalPath();
			final File[] scanRoots = getScanRoots();
			for (int i = 0; i < scanRoots.length; i++) {
				final File root = scanRoots[i];
				if (root == null) {
					continue;
				}
				final String rootPath = root.getCanonicalPath();
				if (dirPath.equals(rootPath)) {
					return "";
				}
				final String prefix = rootPath + File.separator;
				if (dirPath.startsWith(prefix)) {
					return dirPath.substring(prefix.length()).replace('\\', '/');
				}
			}
		}
		catch (final Exception e) {
			LogUtils.warn(e);
		}
		return directory.getName();
	}

	public static void collectMindmapFiles(final List files) {
		if (files == null) {
			return;
		}
		final File[] roots = getScanRoots();
		for (int i = 0; i < roots.length; i++) {
			collectMindmapFilesRecursive(roots[i], files);
		}
	}

	public static void collectMindmapFilesRecursive(final File directory, final List files) {
		if (directory == null || !directory.exists() || !directory.isDirectory()) {
			return;
		}
		final File[] children = directory.listFiles();
		if (children == null) {
			return;
		}
		for (int i = 0; i < children.length; i++) {
			final File child = children[i];
			if (child.getName().startsWith(".")) {
				continue;
			}
			if (child.isDirectory()) {
				if ("bin".equalsIgnoreCase(child.getName()) || isConfigDirectoryName(child.getName())) {
					continue;
				}
				collectMindmapFilesRecursive(child, files);
			}
			else if (child.getName().toLowerCase().endsWith(".mm")) {
				MindMapFileIdentity.addMindmapFileIfNew(child, files);
			}
		}
	}

	private static File resolveWorkingDirectory() {
		final File fromSystem = usableDirectoryFromPath(System.getProperty(WORKING_DIRECTORY_SYSTEM_PROPERTY));
		if (fromSystem != null) {
			return fromSystem.getAbsoluteFile();
		}
		final File fromLegacySystem = usableDirectoryFromPath(System.getProperty(DATA_ROOT_SYSTEM_PROPERTY));
		if (fromLegacySystem != null) {
			return fromLegacySystem.getAbsoluteFile();
		}
		final File fromEnv = usableDirectoryFromPath(System.getenv(WORKING_DIRECTORY_ENV));
		if (fromEnv != null) {
			return fromEnv.getAbsoluteFile();
		}
		final File fromLegacyEnv = usableDirectoryFromPath(System.getenv(DATA_ROOT_ENV));
		if (fromLegacyEnv != null) {
			return fromLegacyEnv.getAbsoluteFile();
		}
		final File fromFile = readWorkingDirectoryFile();
		if (fromFile != null) {
			// Existing marker: use as-is, never wipe contents.
			return fromFile;
		}
		return configureWorkingDirectoryInteractively();
	}

	/**
	 * First launch without {@code working-directory.txt}: ask the user for a folder.
	 * Empty folders get default sample content (via optional seeder); non-empty folders are left untouched.
	 */
	private static File configureWorkingDirectoryInteractively() {
		if (configuringInteractively) {
			final File fallback = defaultSuggestedWorkingDirectory();
			ensureDirectory(fallback);
			return fallback;
		}
		configuringInteractively = true;
		try {
			final File suggested = defaultSuggestedWorkingDirectory();
			LogUtils.info("No usable " + WORKING_DIRECTORY_FILE_NAME + " — prompting for working directory");
			final File chosen = WorkingDirectoryChooser.choose(suggested);
			if (chosen == null) {
				LogUtils.warn("Working directory selection cancelled — exiting");
				System.err.println("Docear requires a working directory. Exiting.");
				System.exit(1);
			}
			ensureDirectory(chosen);
			final boolean empty = isEffectivelyEmptyWorkingDirectory(chosen);
			writeWorkingDirectoryFile(chosen);
			System.setProperty(WORKING_DIRECTORY_SYSTEM_PROPERTY, chosen.getAbsolutePath());
			if (empty) {
				pendingDefaultContentSeed = true;
				runEmptyDirectorySeeder(chosen);
			}
			else {
				pendingDefaultContentSeed = false;
				LogUtils.info("Using existing working directory without modifying files: "
				        + chosen.getAbsolutePath());
			}
			markSetupCompletedQuietly(chosen);
			return chosen.getAbsoluteFile();
		}
		finally {
			configuringInteractively = false;
		}
	}

	/** Prefer a user-owned folder on macOS; under the install root elsewhere. */
	private static File defaultSuggestedWorkingDirectory() {
		if (Compat.isMacOsX()) {
			return new File(System.getProperty("user.home", "."), "Docear").getAbsoluteFile();
		}
		return new File(getApplicationRoot(), DEFAULT_WORKING_DIR_NAME).getAbsoluteFile();
	}

	private static void runEmptyDirectorySeeder(final File workingDirectory) {
		final EmptyDirectorySeeder seeder = emptyDirectorySeeder;
		if (seeder == null) {
			LogUtils.info("Working directory is empty; default content will be seeded when Docear core loads: "
			        + workingDirectory.getAbsolutePath());
			return;
		}
		try {
			seeder.seedDefaults(workingDirectory);
			pendingDefaultContentSeed = false;
			LogUtils.info("Seeded default content into empty working directory: "
			        + workingDirectory.getAbsolutePath());
		}
		catch (final Exception e) {
			LogUtils.warn("Could not seed default working-directory content: " + e.getMessage());
		}
	}

	/** Write setup.completed under the chosen dir's data/ without requiring the cache. */
	private static void markSetupCompletedQuietly(final File workingDirectory) {
		final File marker = new File(resolveConfigDirectory(workingDirectory), SETUP_COMPLETED_MARKER);
		BufferedWriter writer = null;
		try {
			ensureDirectory(marker.getParentFile());
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(marker), "UTF-8"));
			writer.write(String.valueOf(System.currentTimeMillis()));
			writer.newLine();
		}
		catch (final Exception e) {
			LogUtils.warn("Could not write setup marker: " + e.getMessage());
		}
		finally {
			FileUtils.silentlyClose(writer);
		}
	}

	/** Called after Docear core registers a seeder, in case the early prompt ran first. */
	public static void seedDefaultsIfPending() {
		if (!pendingDefaultContentSeed) {
			return;
		}
		final File dir = getWorkingDirectory();
		if (!isEffectivelyEmptyWorkingDirectory(dir)) {
			pendingDefaultContentSeed = false;
			return;
		}
		runEmptyDirectorySeeder(dir);
	}

	private static File resolveConfigDirectory(final File workingDirectory) {
		final File preferred = new File(workingDirectory, CONFIG_DIR_NAME);
		if (preferred.isDirectory()) {
			return preferred;
		}
		final File legacy = new File(workingDirectory, LEGACY_CONFIG_DIR_NAME);
		if (legacy.isDirectory()) {
			return legacy;
		}
		return preferred;
	}

	private static File readWorkingDirectoryFile() {
		final File[] markers = workingDirectoryMarkerCandidates();
		for (int i = 0; i < markers.length; i++) {
			final File marker = markers[i];
			if (marker == null || !marker.isFile()) {
				continue;
			}
			final File parsed = parseWorkingDirectoryMarker(marker);
			if (parsed != null) {
				return parsed;
			}
		}
		return null;
	}

	private static File parseWorkingDirectoryMarker(final File marker) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(marker), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				// PowerShell Set-Content -Encoding UTF8 may prepend a BOM.
				if (line.length() > 0 && line.charAt(0) == '\uFEFF') {
					line = line.substring(1).trim();
				}
				if (line.length() == 0 || line.startsWith("#")) {
					continue;
				}
				if (!isUsableWorkingDirectoryPath(line)) {
					LogUtils.warn("Ignoring unusable working directory in " + marker.getAbsolutePath()
					        + ": " + line + " (not valid on this OS — please choose a local folder)");
					continue;
				}
				File dir = new File(line);
				if (!dir.isAbsolute()) {
					dir = new File(getApplicationRoot(), line);
				}
				return dir.getAbsoluteFile();
			}
		}
		catch (final Exception e) {
			LogUtils.warn("Could not read " + marker.getAbsolutePath() + ": " + e.getMessage());
		}
		finally {
			FileUtils.silentlyClose(reader);
		}
		return null;
	}

	/**
	 * Preferred marker next to the install root; on macOS / Linux also
	 * {@code ~/Library/Application Support/Docear/working-directory.txt}
	 * (or {@code ~/.config/Docear/}) when the .app bundle is not writable.
	 */
	private static File[] workingDirectoryMarkerCandidates() {
		final File appMarker = new File(getApplicationRoot(), WORKING_DIRECTORY_FILE_NAME);
		final File supportMarker = getUserSupportWorkingDirectoryMarker();
		if (supportMarker == null) {
			return new File[] { appMarker };
		}
		return new File[] { appMarker, supportMarker };
	}

	private static File getUserSupportWorkingDirectoryMarker() {
		if (Compat.isWindowsOS()) {
			return null;
		}
		final File home = new File(System.getProperty("user.home", "."));
		if (Compat.isMacOsX()) {
			return new File(new File(new File(home, "Library"), "Application Support"),
			        "Docear" + File.separator + WORKING_DIRECTORY_FILE_NAME);
		}
		final String xdg = System.getenv("XDG_CONFIG_HOME");
		final File configHome = (xdg != null && xdg.trim().length() > 0) ? new File(xdg.trim())
		        : new File(home, ".config");
		return new File(new File(configHome, "Docear"), WORKING_DIRECTORY_FILE_NAME);
	}

	private static void writeWorkingDirectoryFile(final File workingDirectory) {
		final File appRoot = getApplicationRoot();
		String value = workingDirectory.getAbsolutePath();
		try {
			final String appPath = appRoot.getCanonicalPath();
			final String workPath = workingDirectory.getCanonicalPath();
			if (workPath.equals(appPath + File.separator + DEFAULT_WORKING_DIR_NAME)
			        || workPath.equals(new File(appRoot, DEFAULT_WORKING_DIR_NAME).getCanonicalPath())) {
				value = DEFAULT_WORKING_DIR_NAME;
			}
		}
		catch (final Exception e) {
			// keep absolute
		}
		final File preferred = resolveWritableWorkingDirectoryMarker();
		if (writeWorkingDirectoryMarker(preferred, value)) {
			return;
		}
		final File support = getUserSupportWorkingDirectoryMarker();
		if (support != null && !support.equals(preferred) && writeWorkingDirectoryMarker(support, value)) {
			LogUtils.info("Working directory saved to user support path: " + support.getAbsolutePath());
			return;
		}
		LogUtils.warn("Could not persist working directory path");
	}

	private static File resolveWritableWorkingDirectoryMarker() {
		final File appMarker = new File(getApplicationRoot(), WORKING_DIRECTORY_FILE_NAME);
		if (canWriteMarker(appMarker)) {
			return appMarker;
		}
		final File support = getUserSupportWorkingDirectoryMarker();
		if (support != null) {
			return support;
		}
		return appMarker;
	}

	private static boolean canWriteMarker(final File marker) {
		if (marker == null) {
			return false;
		}
		final File parent = marker.getParentFile();
		if (parent == null) {
			return false;
		}
		if (marker.isFile() && marker.canWrite()) {
			return true;
		}
		if (parent.isDirectory()) {
			return parent.canWrite();
		}
		// Parent may not exist yet (user support dir) — treat as writable if we can create it later.
		return !Compat.isWindowsOS();
	}

	private static boolean writeWorkingDirectoryMarker(final File marker, final String value) {
		if (marker == null) {
			return false;
		}
		BufferedWriter writer = null;
		try {
			ensureDirectory(marker.getParentFile());
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(marker), "UTF-8"));
			writer.write("# Docear working directory (mind maps). Config lives in <this>/data/");
			writer.newLine();
			writer.write("# Use a path valid on this OS. On macOS choose via Docear Settings.");
			writer.newLine();
			writer.write(value);
			writer.newLine();
			return true;
		}
		catch (final Exception e) {
			LogUtils.warn("Could not write " + marker.getAbsolutePath() + ": " + e.getMessage());
			return false;
		}
		finally {
			FileUtils.silentlyClose(writer);
		}
	}

	private static void ensureDirectory(final File directory) {
		if (directory == null) {
			return;
		}
		if (!directory.isDirectory() && !directory.mkdirs()) {
			LogUtils.warn("Unable to create directory: " + directory.getAbsolutePath());
		}
	}

	private static String resolveProjectIdForConfigDir(final File configDir) {
		if (configDir == null || !configDir.isDirectory()) {
			return DEFAULT_PROJECT_ID;
		}
		final File[] children = configDir.listFiles();
		if (children == null) {
			return DEFAULT_PROJECT_ID;
		}
		File bestDataDir = null;
		long bestModified = 0L;
		for (int i = 0; i < children.length; i++) {
			final File child = children[i];
			if (child == null || !child.isDirectory()) {
				continue;
			}
			final File settingsFile = new File(child, "settings.xml");
			if (!settingsFile.isFile()) {
				continue;
			}
			final long modified = settingsFile.lastModified();
			if (bestDataDir == null || modified >= bestModified) {
				bestDataDir = child;
				bestModified = modified;
			}
		}
		if (bestDataDir == null) {
			return DEFAULT_PROJECT_ID;
		}
		final String projectId = readProjectIdFromSettings(new File(bestDataDir, "settings.xml"));
		if (projectId != null && projectId.length() > 0) {
			return projectId;
		}
		return bestDataDir.getName();
	}

	private static String readProjectIdFromSettings(final File settingsFile) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(settingsFile), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				final int idIndex = line.indexOf("ID=\"");
				if (idIndex >= 0) {
					final int start = idIndex + 4;
					final int end = line.indexOf('\"', start);
					if (end > start) {
						return line.substring(start, end);
					}
				}
			}
		}
		catch (final Exception e) {
			LogUtils.warn("could not read project id from " + settingsFile.getAbsolutePath() + ": " + e.getMessage());
		}
		finally {
			FileUtils.silentlyClose(reader);
		}
		return null;
	}

	private static File directoryFromPath(final String path) {
		if (path == null || path.trim().length() == 0) {
			return null;
		}
		return new File(path.trim());
	}

	private static File usableDirectoryFromPath(final String path) {
		if (!isUsableWorkingDirectoryPath(path)) {
			if (path != null && path.trim().length() > 0) {
				LogUtils.warn("Ignoring unusable working directory override: " + path.trim());
			}
			return null;
		}
		return directoryFromPath(path);
	}

	private static void addCanonicalRoot(final Set roots, final File root) {
		if (root == null || !root.exists()) {
			return;
		}
		final File absolute = root.getAbsoluteFile();
		for (final Iterator it = roots.iterator(); it.hasNext();) {
			final File existing = (File) it.next();
			if (existing != null && MindMapFileIdentity.isSameFile(absolute, existing)) {
				return;
			}
		}
		roots.add(absolute);
	}

	private static File[] normalizeScanRoots(final Set roots) {
		if (roots == null || roots.isEmpty()) {
			return new File[0];
		}
		final List candidates = new ArrayList(roots);
		Collections.sort(candidates, new Comparator() {
			public int compare(final Object o1, final Object o2) {
				return ((File) o1).getAbsolutePath().length() - ((File) o2).getAbsolutePath().length();
			}
		});
		final List normalized = new ArrayList();
		for (int i = 0; i < candidates.size(); i++) {
			final File candidate = (File) candidates.get(i);
			if (candidate == null || !candidate.exists()) {
				continue;
			}
			String candidatePath;
			try {
				candidatePath = candidate.getCanonicalPath();
			}
			catch (final Exception e) {
				candidatePath = candidate.getAbsolutePath();
			}
			boolean covered = false;
			for (int j = 0; j < normalized.size(); j++) {
				final File existing = (File) normalized.get(j);
				if (MindMapFileIdentity.isSameFile(candidate, existing)) {
					covered = true;
					break;
				}
				String existingPath;
				try {
					existingPath = existing.getCanonicalPath();
				}
				catch (final Exception e) {
					existingPath = existing.getAbsolutePath();
				}
				if (candidatePath.equals(existingPath)
				        || candidatePath.startsWith(existingPath + File.separator)) {
					covered = true;
					break;
				}
			}
			if (!covered) {
				normalized.add(candidate);
			}
		}
		return (File[]) normalized.toArray(new File[normalized.size()]);
	}

	private static File getSelectedProjectRoot() {
		final Object project = invokeWorkspaceStatic("getSelectedProject");
		return projectRoot(project);
	}

	private static File getCurrentMapProjectRoot() {
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null || controller.getMap() == null) {
				return null;
			}
			final Object project = invokeWorkspaceStatic("getMapProject", new Class[] { MapModel.class },
			    new Object[] { controller.getMap() });
			return projectRoot(project);
		}
		catch (final Exception e) {
			return null;
		}
	}

	private static void addOpenMapDirectories(final Set roots) {
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null) {
				return;
			}
			final IMapViewManager mapViewManager = controller.getMapViewManager();
			if (mapViewManager == null) {
				return;
			}
			final Map maps = mapViewManager.getMaps();
			if (maps == null) {
				return;
			}
			for (final Iterator it = maps.values().iterator(); it.hasNext();) {
				final MapModel map = (MapModel) it.next();
				if (map == null || map.getFile() == null) {
					continue;
				}
				final File parent = map.getFile().getParentFile();
				addCanonicalRoot(roots, parent);
			}
		}
		catch (final Exception e) {
			LogUtils.warn(e);
		}
	}

	private static void collectProjectRootsFromWorkspace(final Set roots) {
		try {
			final Object model = invokeWorkspaceStatic("getCurrentModel");
			if (model == null) {
				return;
			}
			final Collection projects = (Collection) model.getClass().getMethod("getProjects", new Class[0]).invoke(model,
			    new Object[0]);
			if (projects == null) {
				return;
			}
			for (final Iterator it = projects.iterator(); it.hasNext();) {
				addCanonicalRoot(roots, projectRoot(it.next()));
			}
		}
		catch (final Exception e) {
			// workspace may not be loaded yet
		}
	}

	private static void collectProjectRootsFromSettings(final Set roots) {
		InputStream in = null;
		try {
			final File settingsFile = findWorkspaceUserSettingsFile();
			if (settingsFile == null || !settingsFile.exists()) {
				return;
			}
			final Properties properties = new Properties();
			in = new FileInputStream(settingsFile);
			properties.load(in);
			final String projectIds = properties.getProperty(WORKSPACE_SETTINGS_PROJECTS_KEY, "");
			if (projectIds == null || projectIds.trim().length() == 0) {
				return;
			}
			final String[] ids = projectIds.split(WORKSPACE_SETTINGS_PROJECTS_SEPARATOR);
			for (int i = 0; i < ids.length; i++) {
				final String projectId = ids[i] == null ? "" : ids[i].trim();
				if (projectId.length() == 0) {
					continue;
				}
				final String projectHome = properties.getProperty(projectId);
				if (projectHome == null || projectHome.trim().length() == 0) {
					continue;
				}
				addCanonicalRoot(roots, uriStringToExistingDirectory(projectHome.trim()));
			}
		}
		catch (final Exception e) {
			LogUtils.warn(e);
		}
		finally {
			FileUtils.silentlyClose(in);
		}
	}

	private static File findWorkspaceUserSettingsFile() {
		final File configDir = getApplicationConfigDirectory();
		final File usersDir = new File(configDir, "users");
		if (!usersDir.isDirectory()) {
			return null;
		}
		final File localSettings = new File(new File(usersDir, "local"), "user.settings");
		if (localSettings.exists()) {
			return localSettings;
		}
		final File[] userDirs = usersDir.listFiles();
		if (userDirs == null) {
			return null;
		}
		for (int i = 0; i < userDirs.length; i++) {
			final File candidate = new File(userDirs[i], "user.settings");
			if (candidate.exists()) {
				return candidate;
			}
		}
		return null;
	}

	private static File projectRoot(final Object project) {
		if (project == null) {
			return null;
		}
		try {
			final URI home = (URI) project.getClass().getMethod("getProjectHome", new Class[0]).invoke(project,
			    new Object[0]);
			return uriToExistingDirectory(home);
		}
		catch (final Exception e) {
			return null;
		}
	}

	private static File uriStringToExistingDirectory(final String uriString) {
		if (uriString == null || uriString.length() == 0) {
			return null;
		}
		try {
			return uriToExistingDirectory(new URI(uriString));
		}
		catch (final Exception e) {
			final File direct = new File(uriString);
			return direct.exists() ? direct : null;
		}
	}

	private static File uriToExistingDirectory(final URI uri) {
		if (uri == null) {
			return null;
		}
		try {
			final File fromWorkspaceUtils = invokeWorkspaceUriUtils(uri);
			if (fromWorkspaceUtils != null) {
				return fromWorkspaceUtils;
			}
			final String scheme = uri.getScheme();
			if (scheme == null || "file".equalsIgnoreCase(scheme)) {
				final File direct = scheme == null ? new File(uri.getPath()) : new File(uri);
				if (direct.exists()) {
					return direct;
				}
			}
			final URI absolute = UrlManager.getController().getAbsoluteUri(null, uri);
			if (absolute != null) {
				final File resolved = uriToFile(absolute);
				if (resolved != null && resolved.exists()) {
					return resolved;
				}
			}
			final String raw = uri.toString();
			if (raw.regionMatches(true, 0, "file:", 0, 5)) {
				final File fileUri = new File(URI.create(raw));
				if (fileUri.exists()) {
					return fileUri;
				}
			}
			final String path = uri.getPath();
			if (path != null && path.length() > 0) {
				String localPath = path;
				if (localPath.startsWith("/") && localPath.length() > 2 && localPath.charAt(2) == ':') {
					localPath = localPath.substring(1);
				}
				final File pathFile = new File(localPath);
				if (pathFile.exists()) {
					return pathFile;
				}
			}
		}
		catch (final Exception e) {
			LogUtils.warn(e);
		}
		return null;
	}

	private static File uriToFile(final URI uri) {
		if (uri == null) {
			return null;
		}
		if (uri.getScheme() == null || "file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getScheme() == null ? new File(uri.getPath()) : new File(uri);
		}
		return null;
	}

	private static File invokeWorkspaceUriUtils(final URI uri) {
		try {
			final Class uriUtils = Class.forName("org.freeplane.plugin.workspace.URIUtils");
			final Method method = uriUtils.getMethod("getAbsoluteFile", new Class[] { URI.class });
			final Object result = method.invoke(null, new Object[] { uri });
			if (result instanceof File) {
				final File file = (File) result;
				return file.exists() ? file : null;
			}
		}
		catch (final Exception e) {
			// workspace plugin may not be loaded yet
		}
		return null;
	}

	private static Object invokeWorkspaceStatic(final String methodName) {
		return invokeWorkspaceStatic(methodName, new Class[0], new Object[0]);
	}

	private static Object invokeWorkspaceStatic(final String methodName, final Class[] paramTypes,
	        final Object[] args) {
		try {
			final Class workspace = Class.forName(WORKSPACE_CONTROLLER);
			final Method method = workspace.getMethod(methodName, paramTypes);
			return method.invoke(null, args);
		}
		catch (final Exception e) {
			return null;
		}
	}
}
