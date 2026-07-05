package org.freeplane.plugin.workspace.features.favorites;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.freeplane.core.util.FileUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.UserProfileDataMigration;
import org.freeplane.plugin.workspace.URIUtils;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.features.IWorkspaceSettingsHandler;
import org.freeplane.plugin.workspace.mindmapmode.MModeWorkspaceController;
import org.freeplane.plugin.workspace.model.WorkspaceModel;
import org.freeplane.plugin.workspace.model.project.AWorkspaceProject;

public final class FavoritesAndTagsStore {

	public static final String FAVORITES_FILE = "favorites.settings";
	public static final String PROP_FAVORITES = "favorites";
	public static final String PROP_TAGS = "tags";
	public static final String FAVORITES_SEPARATOR = "\n";
	public static final String TAGS_ENTRY_SEPARATOR = "\n";
	public static final String TAGS_VALUE_SEPARATOR = "\t";

	public static final String LEGACY_FAVORITES_KEY = MModeWorkspaceController.class.getPackage().getName().toLowerCase(Locale.ENGLISH) + ".favorites";
	public static final String LEGACY_TAGS_KEY = MModeWorkspaceController.class.getPackage().getName().toLowerCase(Locale.ENGLISH) + ".tags";

	public static final String[] PRESET_TAGS = { "工作", "生活", "学习", "重要" };

	private static FavoritesAndTagsStore instance;

	private final List favoriteUris = new ArrayList();
	private final Map tagsByUri = new LinkedHashMap();
	private final Map projectLastModified = new HashMap();
	private final List changeListeners = new ArrayList();

	private FavoritesAndTagsStore() {
	}

	public static synchronized FavoritesAndTagsStore getInstance() {
		if (instance == null) {
			instance = new FavoritesAndTagsStore();
		}
		return instance;
	}

	public void reloadAllProjects() {
		favoriteUris.clear();
		tagsByUri.clear();
		final WorkspaceModel model = WorkspaceController.getCurrentModel();
		if (model == null) {
			fireChanged();
			return;
		}
		final List projects = model.getProjects();
		synchronized (projects) {
			for (final Iterator it = projects.iterator(); it.hasNext();) {
				loadProject((AWorkspaceProject) it.next(), false);
			}
		}
		rebuildMergedFavorites();
		fireChanged();
	}

	public void reloadIfChanged() {
		final WorkspaceModel model = WorkspaceController.getCurrentModel();
		if (model == null) {
			return;
		}
		boolean changed = false;
		final List projects = model.getProjects();
		synchronized (projects) {
			for (final Iterator it = projects.iterator(); it.hasNext();) {
				final AWorkspaceProject project = (AWorkspaceProject) it.next();
				final File favFile = getFavoritesFile(project);
				final long lastMod = favFile.exists() ? favFile.lastModified() : 0L;
				final Long known = (Long) projectLastModified.get(project.getProjectID());
				if (known == null || known.longValue() != lastMod) {
					changed = true;
					break;
				}
			}
		}
		if (changed) {
			reloadAllProjects();
		}
	}

	public void loadProject(final AWorkspaceProject project, final boolean fireChange) {
		if (project == null) {
			return;
		}
		final File favFile = getFavoritesFile(project);
		tryRestoreFavoritesFromBackup(project, favFile);
		if (!favFile.exists()) {
			migrateLegacySettingsForProject(project);
			projectLastModified.put(project.getProjectID(), Long.valueOf(0L));
			if (fireChange) {
				rebuildMergedFavorites();
				fireChanged();
			}
			return;
		}
		final Properties properties = new Properties();
		InputStream in = null;
		try {
			in = new FileInputStream(favFile);
			properties.load(in);
			loadProjectData(project, properties);
			projectLastModified.put(project.getProjectID(), Long.valueOf(favFile.lastModified()));
		}
		catch (final Exception e) {
			LogUtils.warn("could not load favorites for project " + project.getProjectID(), e);
		}
		finally {
			FileUtils.silentlyClose(in);
		}
		if (fireChange) {
			rebuildMergedFavorites();
			fireChanged();
		}
	}

	private void loadProjectData(final AWorkspaceProject project, final Properties properties) {
		final Map projectFavorites = new LinkedHashMap();
		final String favoritesValue = properties.getProperty(PROP_FAVORITES, "");
		if (favoritesValue.length() > 0) {
			final String[] paths = favoritesValue.split(FAVORITES_SEPARATOR);
			for (int i = 0; i < paths.length; i++) {
				final String path = paths[i].trim();
				if (path.length() == 0) {
					continue;
				}
				final String storedUri = FavoriteUriUtils.fromRelativePath(project, path);
				if (storedUri != null && WorkspaceMindMapUtils.isWorkspaceFileUri(storedUri) && !projectFavorites.containsKey(storedUri)) {
					projectFavorites.put(storedUri, Boolean.TRUE);
				}
			}
		}
		final String tagsValue = properties.getProperty(PROP_TAGS, "");
		if (tagsValue.length() > 0) {
			final String[] entries = tagsValue.split(TAGS_ENTRY_SEPARATOR);
			for (int i = 0; i < entries.length; i++) {
				final String entry = entries[i];
				if (entry.length() == 0) {
					continue;
				}
				final int tab = entry.indexOf(TAGS_VALUE_SEPARATOR);
				if (tab <= 0) {
					continue;
				}
				final String path = entry.substring(0, tab).trim();
				final String storedUri = FavoriteUriUtils.fromRelativePath(project, path);
				if (storedUri == null) {
					continue;
				}
				final LinkedHashSet tags = parseTags(entry.substring(tab + 1));
				if (!tags.isEmpty()) {
					tagsByUri.put(storedUri, tags);
				}
			}
		}
		project.addExtension(ProjectFavoritesExtension.class, new ProjectFavoritesExtension(projectFavorites));
	}

	private void rebuildMergedFavorites() {
		favoriteUris.clear();
		final WorkspaceModel model = WorkspaceController.getCurrentModel();
		if (model == null) {
			return;
		}
		final List projects = model.getProjects();
		synchronized (projects) {
			for (final Iterator it = projects.iterator(); it.hasNext();) {
				final AWorkspaceProject project = (AWorkspaceProject) it.next();
				final ProjectFavoritesExtension ext = (ProjectFavoritesExtension) project.getExtensions(ProjectFavoritesExtension.class);
				if (ext == null) {
					continue;
				}
				for (final Iterator uriIt = ext.getOrderedUris().iterator(); uriIt.hasNext();) {
					final String uri = (String) uriIt.next();
					if (!favoriteUris.contains(uri)) {
						favoriteUris.add(uri);
					}
				}
			}
		}
	}

	public void saveAllProjects() {
		final WorkspaceModel model = WorkspaceController.getCurrentModel();
		if (model == null) {
			return;
		}
		final List projects = model.getProjects();
		synchronized (projects) {
			for (final Iterator it = projects.iterator(); it.hasNext();) {
				saveProject((AWorkspaceProject) it.next());
			}
		}
	}

	public void saveProject(final AWorkspaceProject project) {
		if (project == null) {
			return;
		}
		ProjectFavoritesExtension ext = (ProjectFavoritesExtension) project.getExtensions(ProjectFavoritesExtension.class);
		if (ext == null) {
			ext = new ProjectFavoritesExtension(new LinkedHashMap());
			project.addExtension(ProjectFavoritesExtension.class, ext);
		}
		final Properties properties = new Properties();
		final StringBuilder favoritesBuilder = new StringBuilder();
		for (final Iterator it = ext.getOrderedUris().iterator(); it.hasNext();) {
			final String storedUri = (String) it.next();
			final String relativePath = FavoriteUriUtils.toRelativePath(storedUri, project);
			if (relativePath == null || relativePath.length() == 0) {
				continue;
			}
			if (favoritesBuilder.length() > 0) {
				favoritesBuilder.append(FAVORITES_SEPARATOR);
			}
			favoritesBuilder.append(relativePath);
		}
		properties.setProperty(PROP_FAVORITES, favoritesBuilder.toString());

		final StringBuilder tagsBuilder = new StringBuilder();
		for (final Iterator it = ext.getOrderedUris().iterator(); it.hasNext();) {
			final String storedUri = (String) it.next();
			final LinkedHashSet tags = (LinkedHashSet) tagsByUri.get(storedUri);
			if (tags == null || tags.isEmpty()) {
				continue;
			}
			final String relativePath = FavoriteUriUtils.toRelativePath(storedUri, project);
			if (relativePath == null || relativePath.length() == 0) {
				continue;
			}
			if (tagsBuilder.length() > 0) {
				tagsBuilder.append(TAGS_ENTRY_SEPARATOR);
			}
			tagsBuilder.append(relativePath);
			tagsBuilder.append(TAGS_VALUE_SEPARATOR);
			tagsBuilder.append(joinTags(tags));
		}
		properties.setProperty(PROP_TAGS, tagsBuilder.toString());

		final File favFile = getFavoritesFile(project);
		OutputStream out = null;
		try {
			final File parent = favFile.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			out = new FileOutputStream(favFile);
			properties.store(out, "project favorites and tags");
			projectLastModified.put(project.getProjectID(), Long.valueOf(favFile.lastModified()));
		}
		catch (final Exception e) {
			LogUtils.severe("could not store favorites for project " + project.getProjectID(), e);
		}
		finally {
			FileUtils.silentlyClose(out);
		}
	}

	public List getFavorites() {
		final List entries = new ArrayList();
		for (int i = 0; i < favoriteUris.size(); i++) {
			final String uri = (String) favoriteUris.get(i);
			entries.add(new FavoriteEntry(uri, getTags(uri)));
		}
		return entries;
	}

	public boolean isFavorite(final String uri) {
		final String storedUri = FavoriteUriUtils.normalizeToStoredUri(uri);
		return storedUri != null && favoriteUris.contains(storedUri);
	}

	public void addFavorite(final String uri) {
		final String storedUri = FavoriteUriUtils.normalizeToStoredUri(uri);
		if (storedUri == null || !WorkspaceMindMapUtils.isWorkspaceFileUri(storedUri) || favoriteUris.contains(storedUri)) {
			return;
		}
		final AWorkspaceProject project = FavoriteUriUtils.resolveProject(storedUri);
		if (project == null) {
			return;
		}
		ProjectFavoritesExtension ext = getOrCreateExtension(project);
		ext.addFirst(storedUri);
		favoriteUris.add(0, storedUri);
		saveProject(project);
		fireChanged();
	}

	public void removeFavorite(final String uri) {
		final String storedUri = FavoriteUriUtils.normalizeToStoredUri(uri);
		if (storedUri == null || !favoriteUris.remove(storedUri)) {
			return;
		}
		tagsByUri.remove(storedUri);
		final AWorkspaceProject project = FavoriteUriUtils.resolveProject(storedUri);
		if (project != null) {
			final ProjectFavoritesExtension ext = (ProjectFavoritesExtension) project.getExtensions(ProjectFavoritesExtension.class);
			if (ext != null) {
				ext.remove(storedUri);
			}
			saveProject(project);
		}
		fireChanged();
	}

	public void toggleFavorite(final String uri) {
		if (isFavorite(uri)) {
			removeFavorite(uri);
		}
		else {
			addFavorite(uri);
		}
	}

	public void reorder(final int fromIndex, final int toIndex) {
		if (fromIndex < 0 || fromIndex >= favoriteUris.size()) {
			return;
		}
		int targetIndex = toIndex;
		if (targetIndex < 0) {
			targetIndex = 0;
		}
		if (targetIndex > favoriteUris.size()) {
			targetIndex = favoriteUris.size();
		}
		if (fromIndex == targetIndex || (fromIndex < targetIndex && fromIndex + 1 == targetIndex)) {
			return;
		}
		final String uri = (String) favoriteUris.remove(fromIndex);
		if (fromIndex < targetIndex) {
			targetIndex--;
		}
		favoriteUris.add(targetIndex, uri);
		syncAllProjectsFromGlobalOrder();
		fireChanged();
	}

	public Set getTags(final String uri) {
		final String storedUri = FavoriteUriUtils.normalizeToStoredUri(uri);
		final LinkedHashSet tags = (LinkedHashSet) tagsByUri.get(storedUri);
		if (tags == null || tags.isEmpty()) {
			return Collections.EMPTY_SET;
		}
		return Collections.unmodifiableSet(new LinkedHashSet(tags));
	}

	public void setTags(final String uri, final Set tags) {
		final String storedUri = FavoriteUriUtils.normalizeToStoredUri(uri);
		if (storedUri == null) {
			return;
		}
		if (tags == null || tags.isEmpty()) {
			tagsByUri.remove(storedUri);
		}
		else {
			tagsByUri.put(storedUri, parseTags(joinTags(tags)));
		}
		final AWorkspaceProject project = FavoriteUriUtils.resolveProject(storedUri);
		if (project != null) {
			saveProject(project);
		}
		fireChanged();
	}

	public Set getAllTags() {
		final LinkedHashSet allTags = new LinkedHashSet();
		for (final Iterator it = tagsByUri.values().iterator(); it.hasNext();) {
			allTags.addAll((LinkedHashSet) it.next());
		}
		return allTags;
	}

	public Set getQuickSelectTags() {
		final LinkedHashSet quickTags = new LinkedHashSet();
		for (int i = 0; i < PRESET_TAGS.length; i++) {
			quickTags.add(PRESET_TAGS[i]);
		}
		quickTags.addAll(getAllTags());
		return quickTags;
	}

	public int countFavoritesWithTag(final String tag) {
		if (tag == null || tag.length() == 0) {
			return favoriteUris.size();
		}
		int count = 0;
		for (int i = 0; i < favoriteUris.size(); i++) {
			final String uri = (String) favoriteUris.get(i);
			if (getTags(uri).contains(tag)) {
				count++;
			}
		}
		return count;
	}

	public void addChangeListener(final Runnable listener) {
		if (listener != null && !changeListeners.contains(listener)) {
			changeListeners.add(listener);
		}
	}

	public void removeChangeListener(final Runnable listener) {
		changeListeners.remove(listener);
	}

	private void syncAllProjectsFromGlobalOrder() {
		final WorkspaceModel model = WorkspaceController.getCurrentModel();
		if (model == null) {
			return;
		}
		final List projects = model.getProjects();
		synchronized (projects) {
			for (final Iterator it = projects.iterator(); it.hasNext();) {
				final AWorkspaceProject project = (AWorkspaceProject) it.next();
				final ProjectFavoritesExtension ext = getOrCreateExtension(project);
				ext.syncFromGlobalOrder(favoriteUris, project);
				saveProject(project);
			}
		}
	}

	private ProjectFavoritesExtension getOrCreateExtension(final AWorkspaceProject project) {
		ProjectFavoritesExtension ext = (ProjectFavoritesExtension) project.getExtensions(ProjectFavoritesExtension.class);
		if (ext == null) {
			ext = new ProjectFavoritesExtension(new LinkedHashMap());
			project.addExtension(ProjectFavoritesExtension.class, ext);
		}
		return ext;
	}

	private void tryRestoreFavoritesFromBackup(final AWorkspaceProject project, final File favFile) {
		final int currentCount = UserProfileDataMigration.countFavoritesInFile(favFile);
		final File backupFile = UserProfileDataMigration.findBestFavoritesBackup(project.getProjectID());
		if (backupFile == null) {
			return;
		}
		final int backupCount = UserProfileDataMigration.countFavoritesInFile(backupFile);
		if (backupCount <= currentCount) {
			return;
		}
		try {
			final File parent = favFile.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			FileUtils.copyFile(backupFile, favFile);
			LogUtils.info("Restored favorites.settings from backup (" + backupCount + " entries): "
			        + backupFile.getAbsolutePath());
		}
		catch (final Exception e) {
			LogUtils.warn("could not restore favorites from backup " + backupFile, e);
		}
	}

	private File getFavoritesFile(final AWorkspaceProject project) {
		return new File(URIUtils.getAbsoluteFile(project.getProjectDataPath()), FAVORITES_FILE);
	}

	private void migrateLegacySettingsForProject(final AWorkspaceProject project) {
		try {
			final MModeWorkspaceController controller = (MModeWorkspaceController) WorkspaceController.getCurrentModeExtension();
			if (controller == null) {
				return;
			}
			final IWorkspaceSettingsHandler settings = controller.getWorkspaceSettings();
			if (settings == null) {
				return;
			}
			final String favoritesValue = settings.getProperty(LEGACY_FAVORITES_KEY, "");
			final String tagsValue = settings.getProperty(LEGACY_TAGS_KEY, "");
			if (favoritesValue.length() == 0 && tagsValue.length() == 0) {
				return;
			}
			final Map projectFavorites = new LinkedHashMap();
			if (favoritesValue.length() > 0) {
				final String[] uris = favoritesValue.split(FAVORITES_SEPARATOR);
				for (int i = 0; i < uris.length; i++) {
					final String storedUri = FavoriteUriUtils.normalizeToStoredUri(uris[i]);
					final AWorkspaceProject owner = FavoriteUriUtils.resolveProject(storedUri);
					if (storedUri == null || owner == null || !owner.getProjectID().equals(project.getProjectID())) {
						continue;
					}
					projectFavorites.put(storedUri, Boolean.TRUE);
				}
			}
			if (tagsValue.length() > 0) {
				final String[] entries = tagsValue.split(TAGS_ENTRY_SEPARATOR);
				for (int i = 0; i < entries.length; i++) {
					final String entry = entries[i];
					if (entry.length() == 0) {
						continue;
					}
					final int tab = entry.indexOf(TAGS_VALUE_SEPARATOR);
					if (tab <= 0) {
						continue;
					}
					final String storedUri = FavoriteUriUtils.normalizeToStoredUri(entry.substring(0, tab));
					final AWorkspaceProject owner = FavoriteUriUtils.resolveProject(storedUri);
					if (storedUri == null || owner == null || !owner.getProjectID().equals(project.getProjectID())) {
						continue;
					}
					final LinkedHashSet tags = parseTags(entry.substring(tab + 1));
					if (!tags.isEmpty()) {
						tagsByUri.put(storedUri, tags);
					}
				}
			}
			if (!projectFavorites.isEmpty()) {
				project.addExtension(ProjectFavoritesExtension.class, new ProjectFavoritesExtension(projectFavorites));
				saveProject(project);
				settings.setProperty(LEGACY_FAVORITES_KEY, "");
				settings.setProperty(LEGACY_TAGS_KEY, "");
				settings.store();
			}
		}
		catch (final Exception e) {
			LogUtils.warn("could not migrate legacy favorites settings", e);
		}
	}

	private void fireChanged() {
		for (int i = 0; i < changeListeners.size(); i++) {
			((Runnable) changeListeners.get(i)).run();
		}
	}

	private static LinkedHashSet parseTags(final String value) {
		final LinkedHashSet tags = new LinkedHashSet();
		if (value == null || value.length() == 0) {
			return tags;
		}
		final String[] parts = TagTextUtils.normalizeSeparators(value).split(",");
		for (int i = 0; i < parts.length; i++) {
			final String trimmed = parts[i].trim();
			if (trimmed.length() > 0) {
				tags.add(trimmed);
			}
		}
		return tags;
	}

	private static String joinTags(final Set tags) {
		final StringBuilder builder = new StringBuilder();
		for (final Iterator it = tags.iterator(); it.hasNext();) {
			final String tag = (String) it.next();
			if (tag == null || tag.trim().length() == 0) {
				continue;
			}
			if (builder.length() > 0) {
				builder.append(',');
			}
			builder.append(tag.trim());
		}
		return builder.toString();
	}

	static final class ProjectFavoritesExtension implements org.freeplane.plugin.workspace.model.project.IWorkspaceProjectExtension {
		private final LinkedHashMap orderedUris;

		ProjectFavoritesExtension(final Map initial) {
			orderedUris = new LinkedHashMap(initial);
		}

		List getOrderedUris() {
			return new ArrayList(orderedUris.keySet());
		}

		void addFirst(final String uri) {
			final LinkedHashMap updated = new LinkedHashMap();
			updated.put(uri, Boolean.TRUE);
			for (final Iterator it = orderedUris.keySet().iterator(); it.hasNext();) {
				final String existing = (String) it.next();
				if (!existing.equals(uri)) {
					updated.put(existing, Boolean.TRUE);
				}
			}
			orderedUris.clear();
			orderedUris.putAll(updated);
		}

		void remove(final String uri) {
			orderedUris.remove(uri);
		}

		void syncFromGlobalOrder(final List globalOrder, final AWorkspaceProject project) {
			orderedUris.clear();
			for (int i = 0; i < globalOrder.size(); i++) {
				final String uri = (String) globalOrder.get(i);
				final AWorkspaceProject owner = FavoriteUriUtils.resolveProject(uri);
				if (owner != null && owner.getProjectID().equals(project.getProjectID())) {
					orderedUris.put(uri, Boolean.TRUE);
				}
			}
		}
	}
}
