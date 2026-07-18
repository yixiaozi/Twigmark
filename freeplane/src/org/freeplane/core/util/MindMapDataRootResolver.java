package org.freeplane.core.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
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
 * Resolves directories used for mind-map library scan/search and optional profile layout.
 * <p>
 * Product-default behaviour is portable:
 * <ul>
 * <li>Library root: only when the user configures
 * {@code -Dorg.docear.data.root}, {@code DOCEAR_DATA_ROOT}, or property
 * {@link #SCAN_ROOT_PROPERTY} — never a personal absolute path.</li>
 * <li>Scan roots: configured library + workspace projects + open map folders.</li>
 * <li>App config: standard Freeplane/Docear profile ({@code ~/.docear} /
 * {@code %APPDATA%\\Docear} via {@link Compat}), unless the library already
 * contains a {@code _data} directory (opt-in layout).</li>
 * </ul>
 */
public final class MindMapDataRootResolver {

	/** ResourceController / freeplane.properties key for the mind-map library root. */
	public static final String SCAN_ROOT_PROPERTY = "mindmap_data_scan_root";
	/** JVM system property override for the library root. */
	public static final String DATA_ROOT_SYSTEM_PROPERTY = "org.docear.data.root";
	/** Environment variable override for the library root. */
	public static final String DATA_ROOT_ENV = "DOCEAR_DATA_ROOT";
	/** Default project id when none can be discovered from settings. */
	public static final String DEFAULT_PROJECT_ID = "default";

	/**
	 * @deprecated No longer a personal hard-coded path. Kept as an empty sentinel for
	 *             older call sites that string-concatenate; use {@link #getLibraryDataRoot()}.
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

	private MindMapDataRootResolver() {
	}

	/**
	 * Optional mind-map library root from user configuration.
	 * Returns {@code null} when unset (product default — no personal path).
	 * Configured paths need not exist yet (callers may create them).
	 */
	public static File getLibraryDataRoot() {
		final File fromSystem = directoryFromPath(System.getProperty(DATA_ROOT_SYSTEM_PROPERTY));
		if (fromSystem != null) {
			return fromSystem;
		}
		final File fromEnv = directoryFromPath(System.getenv(DATA_ROOT_ENV));
		if (fromEnv != null) {
			return fromEnv;
		}
		return getConfiguredRoot();
	}

	/**
	 * @deprecated Prefer {@link #getLibraryDataRoot()}. Same semantics.
	 */
	public static File getFixedDataRoot() {
		return getLibraryDataRoot();
	}

	/**
	 * Writable application profile (preferences, logs, workspace UI state).
	 * <p>
	 * Returns {@code null} so {@link Compat} uses the standard portable profile
	 * ({@code ~/.docear}). If the user keeps an opt-in {@code {library}/_data}
	 * directory, that directory is returned instead.
	 */
	public static File getApplicationConfigDirectory() {
		final File dataRoot = getLibraryDataRoot();
		if (dataRoot != null) {
			final File nested = new File(dataRoot, "_data");
			if (nested.isDirectory()) {
				return nested;
			}
		}
		return null;
	}

	/** Log files under the application config directory when available. */
	public static File getLogDirectory() {
		final File configDir = resolveWritableConfigDirectory();
		return new File(configDir, "logs");
	}

	/** Project settings directory under the application config directory. */
	public static File getProjectDataDirectory() {
		final File configDir = resolveWritableConfigDirectory();
		return new File(configDir, resolveProjectIdForConfigDir(configDir));
	}

	private static File resolveWritableConfigDirectory() {
		final File nested = getApplicationConfigDirectory();
		if (nested != null) {
			return nested;
		}
		final String userFpDir = System.getProperty("org.freeplane.userfpdir");
		if (userFpDir != null && userFpDir.trim().length() > 0) {
			return new File(userFpDir.trim());
		}
		return new File(System.getProperty("user.home"), ".docear");
	}

	/** Finds project id from configDir/settings.xml children; falls back to {@link #DEFAULT_PROJECT_ID}. */
	public static String resolveProjectIdForDataRoot(final File dataRoot) {
		if (dataRoot == null) {
			return DEFAULT_PROJECT_ID;
		}
		final File maybeData = new File(dataRoot, "_data");
		if (maybeData.isDirectory()) {
			return resolveProjectIdForConfigDir(maybeData);
		}
		return resolveProjectIdForConfigDir(dataRoot);
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

	public static File getPrimaryScanRoot() {
		final File[] roots = getScanRoots();
		return roots.length > 0 ? roots[0] : null;
	}

	/**
	 * Directories to scan for .mm files: configured library, workspace projects, open maps.
	 */
	public static File[] getScanRoots() {
		final Set roots = new LinkedHashSet();
		addCanonicalRoot(roots, getLibraryDataRoot());
		addCanonicalRoot(roots, getConfiguredRoot());
		collectProjectRootsFromWorkspace(roots);
		collectProjectRootsFromSettings(roots);
		addOpenMapDirectories(roots);
		final File selected = getSelectedProjectRoot();
		addCanonicalRoot(roots, selected);
		final File mapProject = getCurrentMapProjectRoot();
		addCanonicalRoot(roots, mapProject);
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
		final File parent = directory.getParentFile();
		return parent == null ? directory.getName() : directory.getName();
	}

	public static void collectMindmapFiles(final List files) {
		if (files == null) {
			return;
		}
		final Set seenPaths = new LinkedHashSet();
		final File[] roots = getScanRoots();
		for (int i = 0; i < roots.length; i++) {
			collectMindmapFilesRecursive(roots[i], files, seenPaths);
		}
	}

	public static void collectMindmapFilesRecursive(final File directory, final List files) {
		collectMindmapFilesRecursive(directory, files, new LinkedHashSet());
	}

	private static void collectMindmapFilesRecursive(final File directory, final List files, final Set seenPaths) {
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
				if ("bin".equalsIgnoreCase(child.getName()) || "_data".equalsIgnoreCase(child.getName())) {
					continue;
				}
				collectMindmapFilesRecursive(child, files, seenPaths);
			}
			else if (child.getName().toLowerCase().endsWith(".mm")) {
				try {
					final String key = child.getCanonicalPath();
					if (seenPaths.add(key)) {
						files.add(child);
					}
				}
				catch (final Exception e) {
					if (seenPaths.add(child.getAbsolutePath())) {
						files.add(child);
					}
				}
			}
		}
	}

	private static File directoryFromPath(final String path) {
		if (path == null || path.trim().length() == 0) {
			return null;
		}
		return new File(path.trim());
	}

	private static void addCanonicalRoot(final Set roots, final File root) {
		if (root == null || !root.exists()) {
			return;
		}
		try {
			roots.add(root.getCanonicalFile());
		}
		catch (final Exception e) {
			roots.add(root);
		}
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

	private static File getConfiguredRoot() {
		try {
			final ResourceController rc = ResourceController.getResourceController();
			if (rc == null) {
				return null;
			}
			final String configured = rc.getProperty(SCAN_ROOT_PROPERTY, "");
			return directoryFromPath(configured);
		}
		catch (final Exception e) {
			return null;
		}
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
		final File configDir = resolveWritableConfigDirectory();
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
