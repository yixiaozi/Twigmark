package org.docear.plugin.mcp.service;

import java.io.File;

/**
 * Resolve {@code create_mindmap} destinations against the mind-map library root.
 * Relative paths must not land in the process working directory.
 */
public final class McpMindMapPaths {

	private McpMindMapPaths() {
	}

	public static File resolveCreateTarget(final File libraryRoot, final String filePath) {
		return resolveCreateTarget(libraryRoot == null ? new File[0] : new File[] { libraryRoot }, filePath);
	}

	public static File resolveCreateTarget(final File[] scanRoots, final String filePath) {
		if (filePath == null || filePath.trim().length() == 0) {
			throw new IllegalArgumentException("filePath is required.");
		}
		final String trimmed = filePath.trim().replace('\\', '/');
		if (!trimmed.toLowerCase().endsWith(".mm")) {
			throw new IllegalArgumentException("filePath must end with .mm");
		}
		if (scanRoots == null || scanRoots.length == 0 || scanRoots[0] == null) {
			throw new IllegalArgumentException("No mind map library root is configured.");
		}
		final File primary = scanRoots[0];
		final File raw = new File(trimmed);
		final File target = canonical(raw.isAbsolute() ? raw : new File(primary, trimmed));
		if (!isUnderAnyRoot(target, scanRoots)) {
			throw new IllegalArgumentException("create_mindmap must write inside the mind map library ("
					+ canonical(primary).getAbsolutePath() + "), not " + target.getAbsolutePath());
		}
		return target;
	}

	public static boolean isUnderAnyRoot(final File file, final File[] roots) {
		if (file == null || roots == null) {
			return false;
		}
		for (int i = 0; i < roots.length; i++) {
			if (isUnderRoot(file, roots[i])) {
				return true;
			}
		}
		return false;
	}

	public static boolean isUnderRoot(final File file, final File root) {
		if (file == null || root == null) {
			return false;
		}
		try {
			final String filePath = canonical(file).getPath();
			String rootPath = canonical(root).getPath();
			if (filePath.equals(rootPath)) {
				return true;
			}
			if (!rootPath.endsWith(File.separator)) {
				rootPath = rootPath + File.separator;
			}
			return filePath.startsWith(rootPath);
		}
		catch (Exception e) {
			return false;
		}
	}

	public static File canonical(final File file) {
		if (file == null) {
			return null;
		}
		try {
			return file.getCanonicalFile();
		}
		catch (Exception e) {
			return file.getAbsoluteFile();
		}
	}
}
