package org.docear.plugin.core.todoist;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;

public final class TodoistConfig {
	public static final String PROP_API_TOKEN = "todoist.api_token";
	public static final String PROP_PROJECT_NAME = "todoist.project_name";
	public static final String PROP_PROJECT_ID = "todoist.project_id";
	public static final String PROP_IMPORT_TARGET = "todoist.import_target";
	public static final String PROP_LABEL = "todoist.label";
	public static final String PROP_AUTO_SYNC = "todoist.auto_sync";
	public static final String PROP_AUTO_SYNC_INTERVAL_MINUTES = "todoist.auto_sync.interval_minutes";
	public static final String DEFAULT_PROJECT_NAME = "Docear";
	public static final String DEFAULT_LABEL = "Docear";
	/** Empty until the user configures a path (or a library root provides a default). */
	public static final String DEFAULT_IMPORT_TARGET = "";
	public static final String DEFAULT_IMPORT_FILENAME = "todolist.mm";
	public static final String DEFAULT_AUTO_SYNC = "true";
	public static final String DEFAULT_AUTO_SYNC_INTERVAL_MINUTES = "5";
	/** Node attribute linking an imported (or pushed) mind-map node to a Todoist task id. */
	public static final String ATTR_TASK_ID = "todoist_task_id";
	public static final String ATTR_CONTENT_HASH = "todoist_content_hash";

	private TodoistConfig() {
	}

	public static void registerDefaults() {
		final ResourceController resources = ResourceController.getResourceController();
		resources.setDefaultProperty(PROP_API_TOKEN, "");
		resources.setDefaultProperty(PROP_PROJECT_NAME, DEFAULT_PROJECT_NAME);
		resources.setDefaultProperty(PROP_IMPORT_TARGET, DEFAULT_IMPORT_TARGET);
		resources.setDefaultProperty(PROP_LABEL, DEFAULT_LABEL);
		resources.setDefaultProperty(PROP_AUTO_SYNC, DEFAULT_AUTO_SYNC);
		resources.setDefaultProperty(PROP_AUTO_SYNC_INTERVAL_MINUTES, DEFAULT_AUTO_SYNC_INTERVAL_MINUTES);
	}

	public static String getApiToken() {
		final ResourceController resources = ResourceController.getResourceController();
		String token = resources.getProperty(PROP_API_TOKEN, "").trim();
		if (token.length() > 0) {
			return token;
		}
		return loadTokenFromLocalFile();
	}

	public static void setApiToken(String token) {
		ResourceController.getResourceController().setProperty(PROP_API_TOKEN, token == null ? "" : token.trim());
		saveTokenToLocalFile(token);
	}

	public static String getLabel() {
		String label = ResourceController.getResourceController().getProperty(PROP_LABEL, DEFAULT_LABEL);
		if (label == null || label.trim().length() == 0) {
			return DEFAULT_LABEL;
		}
		return label.trim();
	}

	public static String getProjectName() {
		String name = ResourceController.getResourceController().getProperty(PROP_PROJECT_NAME, DEFAULT_PROJECT_NAME);
		if (name == null || name.trim().length() == 0) {
			return DEFAULT_PROJECT_NAME;
		}
		return name.trim();
	}

	public static void setProjectName(String name) {
		String trimmed = name == null ? DEFAULT_PROJECT_NAME : name.trim();
		if (trimmed.length() == 0) {
			trimmed = DEFAULT_PROJECT_NAME;
		}
		if (!trimmed.equals(getProjectName())) {
			clearStoredProjectId();
		}
		ResourceController.getResourceController().setProperty(PROP_PROJECT_NAME, trimmed);
	}

	public static String getProjectId() {
		return loadLocalProperty(PROP_PROJECT_ID, "").trim();
	}

	public static void setProjectId(String projectId, String projectName) {
		saveLocalProperties(PROP_PROJECT_ID, projectId == null ? "" : projectId.trim(), projectName);
	}

	public static void clearStoredProjectId() {
		saveLocalProperties(PROP_PROJECT_ID, "", null);
	}

	public static File getImportTargetFile() {
		String path = ResourceController.getResourceController().getProperty(PROP_IMPORT_TARGET, DEFAULT_IMPORT_TARGET);
		if (path != null) {
			path = path.trim();
		}
		String fromLocal = loadLocalProperty(PROP_IMPORT_TARGET, "").trim();
		if (fromLocal.length() > 0) {
			path = fromLocal;
		}
		if (path == null || path.length() == 0) {
			return resolveDefaultImportTargetFile();
		}
		return new File(path);
	}

	/**
	 * Default under the working directory when no path is stored in {@code data/}.
	 * Prefer configuring {@link #PROP_IMPORT_TARGET} in the data profile.
	 */
	public static File resolveDefaultImportTargetFile() {
		return new File(org.freeplane.core.util.MindMapDataRootResolver.getWorkingDirectory(),
		        DEFAULT_IMPORT_FILENAME);
	}

	public static void setImportTargetFile(String path) {
		String trimmed = path == null ? "" : path.trim();
		ResourceController.getResourceController().setProperty(PROP_IMPORT_TARGET, trimmed);
		saveLocalProperty(PROP_IMPORT_TARGET, trimmed);
	}

	private static void saveLocalProperty(String key, String value) {
		File file = localPropertiesFile();
		FileOutputStream out = null;
		try {
			Properties props = new Properties();
			if (file.isFile()) {
				FileInputStream in = new FileInputStream(file);
				try {
					props.load(in);
				}
				finally {
					in.close();
				}
			}
			props.setProperty(key, value == null ? "" : value);
			out = new FileOutputStream(file);
			props.store(out, "Todoist integration (local only, do not commit)");
		}
		catch (IOException e) {
			LogUtils.warn("Todoist: could not write " + file.getPath(), e);
		}
		finally {
			if (out != null) {
				try {
					out.close();
				}
				catch (IOException e) {
				}
			}
		}
	}

	public static boolean isImportTargetFile(File file) {
		if (file == null) {
			return false;
		}
		try {
			return file.getCanonicalFile().equals(getImportTargetFile().getCanonicalFile());
		}
		catch (IOException e) {
			return file.getAbsolutePath().equalsIgnoreCase(getImportTargetFile().getAbsolutePath());
		}
	}

	public static boolean isAutoSyncEnabled() {
		return ResourceController.getResourceController().getBooleanProperty(PROP_AUTO_SYNC);
	}

	public static void setAutoSyncEnabled(boolean enabled) {
		ResourceController.getResourceController().setProperty(PROP_AUTO_SYNC, enabled ? "true" : "false");
	}

	public static int getAutoSyncIntervalMinutes() {
		int minutes = ResourceController.getResourceController().getIntProperty(PROP_AUTO_SYNC_INTERVAL_MINUTES, 5);
		if (minutes < 1) {
			return 1;
		}
		if (minutes > 120) {
			return 120;
		}
		return minutes;
	}

	public static void setAutoSyncIntervalMinutes(int minutes) {
		if (minutes < 1) {
			minutes = 1;
		}
		if (minutes > 120) {
			minutes = 120;
		}
		ResourceController.getResourceController().setProperty(PROP_AUTO_SYNC_INTERVAL_MINUTES, Integer.toString(minutes));
	}

	private static String loadLocalProperty(String key, String defaultValue) {
		File file = localPropertiesFile();
		if (!file.isFile()) {
			return defaultValue;
		}
		FileInputStream in = null;
		try {
			Properties props = new Properties();
			in = new FileInputStream(file);
			props.load(in);
			return props.getProperty(key, defaultValue);
		}
		catch (IOException e) {
			return defaultValue;
		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (IOException e) {
				}
			}
		}
	}

	private static void saveLocalProperties(String projectIdKey, String projectId, String projectNameForStore) {
		File file = localPropertiesFile();
		FileOutputStream out = null;
		try {
			Properties props = new Properties();
			if (file.isFile()) {
				FileInputStream in = new FileInputStream(file);
				try {
					props.load(in);
				}
				finally {
					in.close();
				}
			}
			props.setProperty(projectIdKey, projectId == null ? "" : projectId);
			if (projectNameForStore != null) {
				props.setProperty("todoist.project_id.name", projectNameForStore.trim());
			}
			out = new FileOutputStream(file);
			props.store(out, "Todoist integration (local only, do not commit)");
		}
		catch (IOException e) {
			LogUtils.warn("Todoist: could not write " + file.getPath(), e);
		}
		finally {
			if (out != null) {
				try {
					out.close();
				}
				catch (IOException e) {
				}
			}
		}
	}

	private static File localPropertiesFile() {
		return new File(Compat.getApplicationUserDirectory(), "todoist.local.properties");
	}

	private static String loadTokenFromLocalFile() {
		File file = localPropertiesFile();
		if (!file.isFile()) {
			return "";
		}
		FileInputStream in = null;
		try {
			Properties props = new Properties();
			in = new FileInputStream(file);
			props.load(in);
			String token = props.getProperty("todoist.api_token", "").trim();
			if (token.length() == 0) {
				token = props.getProperty("key", "").trim();
			}
			if (token.length() > 0) {
				ResourceController.getResourceController().setProperty(PROP_API_TOKEN, token);
			}
			return token;
		}
		catch (IOException e) {
			LogUtils.warn("Todoist: could not read " + file.getPath(), e);
			return "";
		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (IOException e) {
				}
			}
		}
	}

	private static void saveTokenToLocalFile(String token) {
		if (token == null) {
			return;
		}
		token = token.trim();
		File file = localPropertiesFile();
		FileOutputStream out = null;
		try {
			Properties props = new Properties();
			if (file.isFile()) {
				FileInputStream in = new FileInputStream(file);
				try {
					props.load(in);
				}
				finally {
					in.close();
				}
			}
			props.setProperty("todoist.api_token", token);
			out = new FileOutputStream(file);
			props.store(out, "Todoist integration (local only, do not commit)");
		}
		catch (IOException e) {
			LogUtils.warn("Todoist: could not write " + file.getPath(), e);
		}
		finally {
			if (out != null) {
				try {
					out.close();
				}
				catch (IOException e) {
				}
			}
		}
	}
}
