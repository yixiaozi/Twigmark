package org.docear.plugin.core.eagle;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.LogUtils;

/**
 * Eagle library paths and migration preferences (offline; Eagle app need not be running).
 */
public final class EagleConfig {
	public static final String PROP_LIBRARY_PATHS = "eagle.library.paths";
	public static final String PROP_PRIMARY_LIBRARY = "eagle.library.primary";
	public static final String PROP_AUTO_IMPORT = "eagle.migrate.auto_import";
	public static final String PROP_PROTOCOL = "eagle";
	/** Off by default: path-first workflow should not require writing into Eagle. */
	public static final String DEFAULT_AUTO_IMPORT = "false";

	private EagleConfig() {
	}

	public static void registerDefaults() {
		final ResourceController resources = ResourceController.getResourceController();
		resources.setDefaultProperty(PROP_LIBRARY_PATHS, "");
		resources.setDefaultProperty(PROP_PRIMARY_LIBRARY, "");
		resources.setDefaultProperty(PROP_AUTO_IMPORT, DEFAULT_AUTO_IMPORT);
	}

	/** Newline- or semicolon-separated absolute paths to {@code *.library} directories. */
	public static List<File> getLibraryRoots() {
		String raw = ResourceController.getResourceController().getProperty(PROP_LIBRARY_PATHS, "");
		if (raw == null) {
			raw = "";
		}
		raw = raw.trim();
		final List<File> roots = new ArrayList<File>();
		if (raw.length() == 0) {
			for (File auto : EagleSettingsProbe.findLibraryRoots()) {
				if (auto != null && auto.isDirectory() && !containsPath(roots, auto)) {
					roots.add(auto);
				}
			}
			return roots;
		}
		final String[] parts = raw.split("[\\r\\n;]+");
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i].trim();
			if (part.length() == 0) {
				continue;
			}
			final File file = new File(part);
			if (!containsPath(roots, file)) {
				roots.add(file);
			}
		}
		return roots;
	}

	public static void setLibraryPaths(final List<File> roots) {
		if (roots == null || roots.isEmpty()) {
			ResourceController.getResourceController().setProperty(PROP_LIBRARY_PATHS, "");
			return;
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < roots.size(); i++) {
			if (i > 0) {
				sb.append('\n');
			}
			sb.append(roots.get(i).getAbsolutePath());
		}
		ResourceController.getResourceController().setProperty(PROP_LIBRARY_PATHS, sb.toString());
	}

	public static void setLibraryPathsText(final String text) {
		ResourceController.getResourceController().setProperty(PROP_LIBRARY_PATHS, text == null ? "" : text.trim());
	}

	public static String getLibraryPathsText() {
		final List<File> roots = getLibraryRoots();
		if (roots.isEmpty()) {
			return ResourceController.getResourceController().getProperty(PROP_LIBRARY_PATHS, "");
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < roots.size(); i++) {
			if (i > 0) {
				sb.append('\n');
			}
			sb.append(roots.get(i).getAbsolutePath());
		}
		return sb.toString();
	}

	/** Library used when auto-importing unmatched images that still exist on disk. */
	public static File getPrimaryLibrary() {
		String path = ResourceController.getResourceController().getProperty(PROP_PRIMARY_LIBRARY, "");
		if (path != null) {
			path = path.trim();
		}
		if (path != null && path.length() > 0) {
			final File file = new File(path);
			if (file.isDirectory()) {
				return file;
			}
		}
		final List<File> roots = getLibraryRoots();
		if (roots.isEmpty()) {
			return null;
		}
		return roots.get(0);
	}

	public static void setPrimaryLibrary(final File library) {
		ResourceController.getResourceController().setProperty(PROP_PRIMARY_LIBRARY,
				library == null ? "" : library.getAbsolutePath());
	}

	public static boolean isAutoImportEnabled() {
		return ResourceController.getResourceController().getBooleanProperty(PROP_AUTO_IMPORT);
	}

	public static void setAutoImportEnabled(final boolean enabled) {
		ResourceController.getResourceController().setProperty(PROP_AUTO_IMPORT, Boolean.toString(enabled));
	}

	public static List<File> existingLibraryRoots() {
		final List<File> all = getLibraryRoots();
		final List<File> existing = new ArrayList<File>();
		for (File root : all) {
			if (root != null && root.isDirectory()) {
				existing.add(root);
			}
			else if (root != null) {
				LogUtils.warn("Eagle library path missing: " + root.getAbsolutePath());
			}
		}
		return existing.isEmpty() ? Collections.<File> emptyList() : existing;
	}

	private static boolean containsPath(final List<File> roots, final File file) {
		final String abs = file.getAbsolutePath();
		for (File root : roots) {
			if (root.getAbsolutePath().equals(abs)) {
				return true;
			}
		}
		return false;
	}
}
