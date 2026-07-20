package org.freeplane.plugin.workspace;

import java.io.File;
import java.io.IOException;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapFileIdentity;

/**
 * Helpers for workspace paths on Windows (junctions / symlinks) and similar cases
 * where {@link File#listFiles()} or child resolution behaves more reliably on the
 * canonical target directory.
 */
public final class WorkspacePathUtils {

	private WorkspacePathUtils() {
	}

	/**
	 * If {@code raw} exists, returns {@link File#getCanonicalFile()} so junctions and
	 * directory symlinks resolve to their target; otherwise returns
	 * {@link File#getAbsoluteFile()}. On {@link IOException}, returns the absolute file.
	 *
	 * <p>Settings and project URIs should keep the user-entered path (e.g. {@code E:\yixiaozi});
	 * call this only when listing {@code _data} or other children under a linked root.</p>
	 */
	public static File resolveWorkspaceRootDirectory(final File raw) {
		if (raw == null) {
			return null;
		}
		final File absolute = raw.getAbsoluteFile();
		try {
			if (absolute.exists()) {
				return absolute.getCanonicalFile();
			}
		}
		catch (IOException e) {
			LogUtils.warn(e);
		}
		return absolute;
	}

	/** {@code true} when two paths refer to the same directory or file (junction-safe). */
	public static boolean isSamePath(final File a, final File b) {
		return MindMapFileIdentity.isSameFile(a, b);
	}
}
