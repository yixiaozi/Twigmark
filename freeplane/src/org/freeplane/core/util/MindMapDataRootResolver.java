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
 * Resolves directories used for cross-map scan/search (reminders, todos, global search, etc.).
 * Fixed to {@link #FIXED_DATA_ROOT_PATH} for this Docear fork.
 */
public final class MindMapDataRootResolver {

	public static final String SCAN_ROOT_PROPERTY = "mindmap_data_scan_root";
	/** Unified data directory for workspace, search, reminders, and side tabs. */
	public static final String FIXED_DATA_ROOT_PATH = "E:\\yixiaozi";
	public static final String FIXED_PROJECT_ID = "yixiaozi";
	private static final String WORKSPACE_CONTROLLER = "org.freeplane.plugin.workspace.WorkspaceController";
	private static final String WORKSPACE_SETTINGS_PROJECTS_KEY =
	    "org.freeplane.plugin.workspace.mindmapmode.model.projects";
	private static final String WORKSPACE_SETTINGS_PROJECTS_SEPARATOR = ",";

	private MindMapDataRootResolver() {
	}

	public static File getFixedDataRoot() {
		final File root = new File(FIXED_DATA_ROOT_PATH);
		return root.isDirectory() ? root : null;
	}

	/**
	 * Writable application profile (preferences, logs, workspace UI state).
	 * Same tree as project metadata parent: {@code {dataRoot}/_data}.
	 */
	public static File getApplicationConfigDirectory() {
		final File dataRoot = getFixedDataRoot();
		if (dataRoot == null) {
			return null;
		}
		return new File(dataRoot, "_data");
	}

	/** Log files: {@code {dataRoot}/_data/logs}. */
	public static File getLogDirectory() {
		final File configDir = getApplicationConfigDirectory();
		if (configDir == null) {
			return null;
		}
		return new File(configDir, "logs");
	}

	/** Project settings directory: {@code {dataRoot}/_data/{projectId}}. */
	public static File getProjectDataDirectory() {
		final File dataRoot = getFixedDataRoot();
		if (dataRoot == null) {
			return null;
		}
		return new File(new File(dataRoot, "_data"), resolveProjectIdForDataRoot(dataRoot));
	}

	/** Finds project id from dataRoot/_data/settings.xml; falls back to FIXED_PROJECT_ID. */
	public static String resolveProjectIdForDataRoot(final File dataRoot) {
		if (dataRoot == null || !dataRoot.isDirectory()) {
			return FIXED_PROJECT_ID;
		}
		final File dataParent = new File(dataRoot, "_data");
		if (!dataParent.isDirectory()) {
			return FIXED_PROJECT_ID;
		}
		final File[] children = dataParent.listFiles();
		if (children == null) {
			return FIXED_PROJECT_ID;
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
			return FIXED_PROJECT_ID;
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
		return getFixedDataRoot();
	}

	public static File[] getScanRoots() {
		final File fixed = getFixedDataRoot();
		if (fixed != null) {
			return new File[] { fixed };
		}
		return new File[0];
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
				if ("bin".equalsIgnoreCase(child.getName())) {
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
		final String configured = ResourceController.getResourceController().getProperty(SCAN_ROOT_PROPERTY, "");
		if (configured == null || configured.trim().length() == 0) {
			return null;
		}
		final File file = new File(configured.trim());
		return file.exists() ? file : null;
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

	private static File getCurrentMapFileDirectory() {
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null || controller.getMap() == null) {
				return null;
			}
			final File mapFile = controller.getMap().getFile();
			if (mapFile == null) {
				return null;
			}
			final File parent = mapFile.getParentFile();
			return parent != null && parent.exists() ? parent : null;
		}
		catch (final Exception e) {
			return null;
		}
	}

	private static File getFirstOpenMapDirectory() {
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null) {
				return null;
			}
			final IMapViewManager mapViewManager = controller.getMapViewManager();
			if (mapViewManager == null) {
				return null;
			}
			final Map maps = mapViewManager.getMaps();
			if (maps == null) {
				return null;
			}
			for (final Iterator it = maps.values().iterator(); it.hasNext();) {
				final MapModel map = (MapModel) it.next();
				if (map == null || map.getFile() == null) {
					continue;
				}
				final File parent = map.getFile().getParentFile();
				if (parent != null && parent.exists()) {
					return parent;
				}
			}
		}
		catch (final Exception e) {
			LogUtils.warn(e);
		}
		return null;
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

	private static File[] getAllProjectRoots() {
		final Set roots = new LinkedHashSet();
		collectProjectRootsFromWorkspace(roots);
		collectProjectRootsFromSettings(roots);
		return (File[]) roots.toArray(new File[roots.size()]);
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
			LogUtils.warn(e);
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
		final String appUserDir = Compat.getApplicationUserDirectory();
		if (appUserDir == null || appUserDir.length() == 0) {
			return null;
		}
		final File usersDir = new File(appUserDir, "users");
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

	private static File getDefaultApplicationRoot() {
		final String userDir = Compat.getApplicationUserDirectory();
		if (userDir != null && userDir.length() > 0) {
			final File appDir = new File(userDir);
			if (appDir.exists()) {
				return appDir;
			}
		}
		final Object defaultHome = invokeWorkspaceStatic("getDefaultProjectHome");
		if (defaultHome instanceof URI) {
			final File file = uriToExistingDirectory((URI) defaultHome);
			if (file != null) {
				return file;
			}
			try {
				final File projectsParent = new File((URI) defaultHome);
				final File parent = projectsParent.getParentFile();
				if (parent != null && parent.exists()) {
					return parent;
				}
			}
			catch (final Exception e) {
				// ignore
			}
		}
		return new File(System.getProperty("user.home"));
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
