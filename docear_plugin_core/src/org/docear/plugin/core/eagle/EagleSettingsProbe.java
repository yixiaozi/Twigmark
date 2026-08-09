package org.docear.plugin.core.eagle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;

/**
 * Best-effort discovery of Eagle {@code *.library} paths from the local Eagle app settings.
 */
final class EagleSettingsProbe {
	private static final Pattern LIBRARY_PATH = Pattern.compile("(/[^\\x00-\\x1f\"]+\\.library|[A-Za-z]:\\\\[^\\x00-\\x1f\"]+\\.library)");

	private EagleSettingsProbe() {
	}

	static List<File> findLibraryRoots() {
		final Set<String> paths = new LinkedHashSet<String>();
		for (File settingsFile : candidateSettingsFiles()) {
			if (settingsFile == null || !settingsFile.isFile()) {
				continue;
			}
			try {
				extractLibraryPaths(readBytes(settingsFile), paths);
			}
			catch (IOException e) {
				LogUtils.warn("Eagle settings probe failed for " + settingsFile + ": " + e.getMessage());
			}
		}
		final List<File> roots = new ArrayList<File>();
		for (String path : paths) {
			final File file = new File(path);
			if (file.isDirectory()) {
				roots.add(file);
			}
		}
		return roots;
	}

	private static List<File> candidateSettingsFiles() {
		final List<File> files = new ArrayList<File>();
		final String home = System.getProperty("user.home");
		if (Compat.isMacOsX()) {
			files.add(new File(home, "Library/Application Support/Eagle/Settings"));
		}
		else if (Compat.isWindowsOS()) {
			final String appData = System.getenv("APPDATA");
			if (appData != null && appData.length() > 0) {
				files.add(new File(appData, "Eagle/Settings"));
			}
		}
		else {
			files.add(new File(home, ".config/Eagle/Settings"));
		}
		return files;
	}

	private static void extractLibraryPaths(final byte[] data, final Set<String> out) {
		final String text = new String(data);
		final Matcher m = LIBRARY_PATH.matcher(text);
		while (m.find()) {
			String path = m.group(1);
			if (path == null) {
				continue;
			}
			path = path.replace('\\', '/');
			// Drop truncated / relative junk
			if (path.startsWith("/") || (path.length() > 2 && path.charAt(1) == ':')) {
				out.add(new File(path).getAbsolutePath());
			}
		}
	}

	private static byte[] readBytes(final File file) throws IOException {
		final FileInputStream in = new FileInputStream(file);
		try {
			final long len = file.length();
			if (len <= 0 || len > 5 * 1024 * 1024) {
				return new byte[0];
			}
			final byte[] data = new byte[(int) len];
			int off = 0;
			while (off < data.length) {
				final int n = in.read(data, off, data.length - off);
				if (n < 0) {
					break;
				}
				off += n;
			}
			if (off == data.length) {
				return data;
			}
			final byte[] trimmed = new byte[off];
			System.arraycopy(data, 0, trimmed, 0, off);
			return trimmed;
		}
		finally {
			try {
				in.close();
			}
			catch (IOException ignore) {
			}
		}
	}
}
