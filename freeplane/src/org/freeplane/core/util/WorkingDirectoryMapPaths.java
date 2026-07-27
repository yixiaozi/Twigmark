package org.freeplane.core.util;

import java.io.File;
import java.io.IOException;

/**
 * Paths for recent/session maps scoped to the current working directory.
 * <p>
 * Each working-directory root (e.g. {@code E:\yixiaozi} vs
 * {@code /Users/you/Develop/yixiaozi}) gets its own recent-map property key so
 * Mac/Windows (or two different Windows roots) do not overwrite each other when
 * {@code auto.properties} is synced. Maps under the working directory are stored
 * as relative paths so machines that share the same root path can share one set.
 */
public final class WorkingDirectoryMapPaths {

	private static final String MODE_PREFIX = "MindMap:";

	private WorkingDirectoryMapPaths() {
	}

	/** Property key like {@code lastOpened_1.0.20@E_yixiaozi}. */
	public static String propertyKey(final String baseKey) {
		if (baseKey == null || baseKey.length() == 0) {
			return baseKey;
		}
		final String scope = workingDirectoryKey();
		if (scope == null || scope.length() == 0) {
			return baseKey;
		}
		return baseKey + "@" + scope;
	}

	/**
	 * Stable, properties-safe fingerprint of the working directory absolute path.
	 * Same path on different PCs yields the same key (e.g. both {@code E:\yixiaozi}).
	 */
	public static String workingDirectoryKey() {
		final File workingDirectory = safeWorkingDirectory();
		if (workingDirectory == null) {
			return null;
		}
		String path = workingDirectory.getAbsolutePath();
		try {
			path = workingDirectory.getCanonicalPath();
		}
		catch (final IOException e) {
			// keep absolute
		}
		path = path.replace('\\', '/');
		while (path.endsWith("/") && path.length() > 1) {
			path = path.substring(0, path.length() - 1);
		}
		if (Compat.isWindowsOS()) {
			path = path.toLowerCase();
		}
		final StringBuilder key = new StringBuilder(path.length());
		for (int i = 0; i < path.length(); i++) {
			final char c = path.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '.') {
				key.append(c);
			}
			else if (c == '/' || c == ':' || c == ' ' || c == '\\') {
				key.append('_');
			}
			else {
				key.append('_');
			}
		}
		return key.toString();
	}

	/**
	 * Prefer a path relative to the working directory when the file lives under it;
	 * otherwise keep absolute. Returned without {@code MindMap:} prefix.
	 */
	public static String toStoragePath(final File file) {
		if (file == null) {
			return null;
		}
		File absolute = file.getAbsoluteFile();
		try {
			if (absolute.exists()) {
				absolute = absolute.getCanonicalFile();
			}
		}
		catch (final IOException e) {
			// keep absolute
		}
		final File workingDirectory = safeWorkingDirectory();
		if (workingDirectory == null) {
			return absolute.getAbsolutePath();
		}
		final String relative = relativizeUnder(workingDirectory, absolute);
		if (relative != null) {
			return relative.replace('\\', '/');
		}
		return absolute.getAbsolutePath();
	}

	public static String toMindMapRestoreable(final File file) {
		final String path = toStoragePath(file);
		if (path == null) {
			return null;
		}
		return MODE_PREFIX + path;
	}

	/**
	 * Resolve a stored path (relative or absolute, with or without {@code MindMap:})
	 * to an existing file when possible.
	 */
	public static File resolveStoredFile(final String storedPathOrRestoreable) {
		if (storedPathOrRestoreable == null || storedPathOrRestoreable.trim().length() == 0) {
			return null;
		}
		String path = storedPathOrRestoreable.trim();
		if (path.regionMatches(true, 0, MODE_PREFIX, 0, MODE_PREFIX.length())) {
			path = path.substring(MODE_PREFIX.length());
		}
		// Portable Windows form MindMap::\folder\file — keep leading empty drive skip elsewhere
		if (path.startsWith(":") && path.length() > 1) {
			path = path.substring(1);
		}
		File file = new File(path);
		if (!file.isAbsolute()) {
			final File workingDirectory = safeWorkingDirectory();
			if (workingDirectory != null) {
				file = new File(workingDirectory, path.replace('\\', '/'));
			}
		}
		if (file.isFile()) {
			return file;
		}
		try {
			final File canonical = file.getCanonicalFile();
			if (canonical.isFile()) {
				return canonical;
			}
		}
		catch (final IOException e) {
			// ignore
		}
		return remapForeignAbsoluteUnderWorkingDirectory(path);
	}

	/** True when the restoreable/path clearly belongs under the current working directory. */
	public static boolean belongsToCurrentWorkingDirectory(final String storedPathOrRestoreable) {
		final File resolved = resolveStoredFile(storedPathOrRestoreable);
		if (resolved == null || !resolved.isFile()) {
			return false;
		}
		final File workingDirectory = safeWorkingDirectory();
		if (workingDirectory == null) {
			return false;
		}
		return relativizeUnder(workingDirectory, resolved) != null;
	}

	static File remapForeignAbsoluteUnderWorkingDirectory(final String path) {
		if (path == null || path.length() == 0) {
			return null;
		}
		final File workingDirectory = safeWorkingDirectory();
		if (workingDirectory == null) {
			return null;
		}
		final String workName = workingDirectory.getName();
		if (workName == null || workName.length() == 0) {
			return null;
		}
		final String normalized = path.replace('\\', '/');
		final String marker = "/" + workName + "/";
		final int idx = normalized.toLowerCase().indexOf(marker.toLowerCase());
		if (idx < 0) {
			return null;
		}
		final String relative = normalized.substring(idx + marker.length());
		if (relative.length() == 0) {
			return null;
		}
		final File remapped = new File(workingDirectory, relative);
		return remapped.isFile() ? remapped : null;
	}

	private static String relativizeUnder(final File root, final File file) {
		if (root == null || file == null) {
			return null;
		}
		String rootPath;
		String filePath;
		try {
			rootPath = root.getCanonicalPath();
			filePath = file.getCanonicalPath();
		}
		catch (final IOException e) {
			rootPath = root.getAbsolutePath();
			filePath = file.getAbsolutePath();
		}
		rootPath = rootPath.replace('\\', '/');
		filePath = filePath.replace('\\', '/');
		if (Compat.isWindowsOS()) {
			if (!filePath.regionMatches(true, 0, rootPath, 0, rootPath.length())) {
				return null;
			}
		}
		else if (!filePath.startsWith(rootPath)) {
			return null;
		}
		if (filePath.length() == rootPath.length()) {
			return "";
		}
		if (filePath.charAt(rootPath.length()) != '/') {
			return null;
		}
		return filePath.substring(rootPath.length() + 1);
	}

	private static File safeWorkingDirectory() {
		try {
			return MindMapDataRootResolver.getWorkingDirectory();
		}
		catch (final Exception e) {
			return null;
		}
	}
}
