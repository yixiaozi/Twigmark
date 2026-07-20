package org.freeplane.core.util;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves when two {@link File} paths refer to the same on-disk file or directory —
 * including Windows junctions / directory symlinks such as {@code E:\yixiaozi} →
 * {@code D:\Dropbox\yixiaozi}, where {@link File#getCanonicalPath()} may still differ.
 *
 * <p>User-facing settings should keep the path the user typed; use these helpers only for
 * deduplication, equality checks, and scan indexes.</p>
 */
public final class MindMapFileIdentity {

	private static final Method FILE_TO_PATH;
	private static final Method FILES_IS_SAME_FILE;
	private static final Method FILES_READ_ATTRIBUTES;
	private static final Class BASIC_FILE_ATTRIBUTES_CLASS;
	private static final Method BASIC_FILE_KEY;
	private static final Object EMPTY_LINK_OPTIONS;
	private static final boolean NIO_AVAILABLE;

	static {
		Method toPath = null;
		Method isSameFile = null;
		Method readAttributes = null;
		Class basicAttrs = null;
		Method fileKey = null;
		Object emptyLinkOptions = null;
		boolean nio = false;
		try {
			toPath = File.class.getMethod("toPath", new Class[0]);
			final Class pathClass = Class.forName("java.nio.file.Path");
			final Class filesClass = Class.forName("java.nio.file.Files");
			final Class linkOptionClass = Class.forName("java.nio.file.LinkOption");
			isSameFile = filesClass.getMethod("isSameFile", new Class[] { pathClass, pathClass });
			basicAttrs = Class.forName("java.nio.file.attribute.BasicFileAttributes");
			emptyLinkOptions = Array.newInstance(linkOptionClass, 0);
			readAttributes = filesClass.getMethod("readAttributes", new Class[] { pathClass, Class.class,
			        emptyLinkOptions.getClass() });
			fileKey = basicAttrs.getMethod("fileKey", new Class[0]);
			nio = true;
		}
		catch (final Throwable t) {
			// Java 6 runtime or incomplete NIO — fall back to canonical paths.
		}
		FILE_TO_PATH = toPath;
		FILES_IS_SAME_FILE = isSameFile;
		FILES_READ_ATTRIBUTES = readAttributes;
		BASIC_FILE_ATTRIBUTES_CLASS = basicAttrs;
		BASIC_FILE_KEY = fileKey;
		EMPTY_LINK_OPTIONS = emptyLinkOptions;
		NIO_AVAILABLE = nio;
	}

	private MindMapFileIdentity() {
	}

	/** {@code true} when both paths denote the same file or directory (junction-safe on Java 7+). */
	public static boolean isSameFile(final File a, final File b) {
		if (a == null || b == null) {
			return false;
		}
		final File absA = a.getAbsoluteFile();
		final File absB = b.getAbsoluteFile();
		if (absA.equals(absB)) {
			return true;
		}
		if (NIO_AVAILABLE) {
			try {
				final Object pathA = FILE_TO_PATH.invoke(absA, new Object[0]);
				final Object pathB = FILE_TO_PATH.invoke(absB, new Object[0]);
				final Boolean same = (Boolean) FILES_IS_SAME_FILE.invoke(null, new Object[] { pathA, pathB });
				if (same != null) {
					return same.booleanValue();
				}
			}
			catch (final Exception e) {
				LogUtils.warn("MindMapFileIdentity.isSameFile failed: " + e.getMessage());
			}
		}
		try {
			return absA.getCanonicalFile().equals(absB.getCanonicalFile());
		}
		catch (final Exception e) {
			return absA.equals(absB);
		}
	}

	/**
	 * Stable key for maps / sets: two aliases of the same file or directory resolve to the same key.
	 * Prefer the first path seen in {@code known} when present so user-chosen junction paths are kept.
	 */
	public static String storageKey(final File file) {
		return storageKey(file, null);
	}

	public static String storageKey(final File file, final List knownRepresentatives) {
		if (file == null) {
			return "";
		}
		final File absolute = file.getAbsoluteFile();
		if (knownRepresentatives != null) {
			for (int i = 0; i < knownRepresentatives.size(); i++) {
				final File representative = (File) knownRepresentatives.get(i);
				if (representative != null && isSameFile(absolute, representative)) {
					return buildKey(representative);
				}
			}
		}
		return buildKey(absolute);
	}

	/** Adds {@code file} to {@code files} only when no equivalent path is already present. */
	public static void addFileIfNew(final File file, final List files) {
		addMindmapFileIfNew(file, files);
	}

	public static void addMindmapFileIfNew(final File file, final List files) {
		if (file == null || files == null) {
			return;
		}
		for (int i = 0; i < files.size(); i++) {
			if (isSameFile(file, (File) files.get(i))) {
				return;
			}
		}
		files.add(file);
	}

	/** Removes duplicate scan roots that point at the same directory tree. */
	public static List dedupeRootDirectories(final List roots) {
		if (roots == null || roots.isEmpty()) {
			return roots == null ? new ArrayList() : roots;
		}
		final List deduped = new ArrayList();
		for (int i = 0; i < roots.size(); i++) {
			final File candidate = (File) roots.get(i);
			if (candidate == null || !candidate.exists()) {
				continue;
			}
			boolean seen = false;
			for (int j = 0; j < deduped.size(); j++) {
				if (isSameFile(candidate, (File) deduped.get(j))) {
					seen = true;
					break;
				}
			}
			if (!seen) {
				deduped.add(candidate);
			}
		}
		return deduped;
	}

	/** Returns {@code true} when {@code candidate} is the same directory or inside {@code root}. */
	public static boolean isSameOrUnderRoot(final File candidate, final File root) {
		if (candidate == null || root == null) {
			return false;
		}
		if (isSameFile(candidate, root)) {
			return true;
		}
		try {
			final String candidatePath = candidate.getCanonicalPath();
			final String rootPath = root.getCanonicalPath();
			return candidatePath.startsWith(rootPath + File.separator);
		}
		catch (final Exception e) {
			final String candidatePath = candidate.getAbsolutePath();
			final String rootPath = root.getAbsolutePath();
			return candidatePath.startsWith(rootPath + File.separator);
		}
	}

	private static String buildKey(final File absolute) {
		final Object nioFileKey = tryNioFileKey(absolute);
		if (nioFileKey != null) {
			return "id:" + nioFileKey.toString();
		}
		if (absolute.isFile()) {
			return "f:" + absolute.length() + "|" + absolute.lastModified() + "|" + quickContentHash(absolute);
		}
		try {
			return "d:" + absolute.getCanonicalPath();
		}
		catch (final Exception e) {
			return "p:" + absolute.getAbsolutePath();
		}
	}

	private static Object tryNioFileKey(final File file) {
		if (!NIO_AVAILABLE || file == null || !file.exists()) {
			return null;
		}
		try {
			final Object path = FILE_TO_PATH.invoke(file, new Object[0]);
			final Object attrs = FILES_READ_ATTRIBUTES.invoke(null, new Object[] { path, BASIC_FILE_ATTRIBUTES_CLASS,
			        EMPTY_LINK_OPTIONS });
			return BASIC_FILE_KEY.invoke(attrs, new Object[0]);
		}
		catch (final Exception e) {
			return null;
		}
	}

	private static String quickContentHash(final File file) {
		FileInputStream in = null;
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-1");
			in = new FileInputStream(file);
			final byte[] buffer = new byte[4096];
			int read = in.read(buffer);
			if (read > 0) {
				digest.update(buffer, 0, read);
			}
			final byte[] hash = digest.digest();
			final StringBuilder sb = new StringBuilder(16);
			for (int i = 0; i < Math.min(8, hash.length); i++) {
				final int b = hash[i] & 0xff;
				if (b < 16) {
					sb.append('0');
				}
				sb.append(Integer.toHexString(b));
			}
			return sb.toString();
		}
		catch (final Exception e) {
			return "0";
		}
		finally {
			FileUtils.silentlyClose(in);
		}
	}
}
