package org.docear.plugin.core.eagle;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.freeplane.core.util.LogUtils;

/**
 * Offline scan of Eagle {@code *.library/images/{id}.info/} packages.
 */
final class EagleLibraryScanner {
	interface Progress {
		void onProgress(String message);
	}

	private EagleLibraryScanner() {
	}

	static List<EagleItem> scanLibraries(final List<File> libraryRoots, final boolean computeHash, final Progress progress)
			throws IOException {
		final List<EagleItem> items = new ArrayList<EagleItem>();
		if (libraryRoots == null) {
			return items;
		}
		for (File root : libraryRoots) {
			if (root == null || !root.isDirectory()) {
				continue;
			}
			if (progress != null) {
				progress.onProgress("Scanning " + root.getName());
			}
			items.addAll(scanLibrary(root, computeHash, progress));
		}
		return items;
	}

	static List<EagleItem> scanLibrary(final File libraryRoot, final boolean computeHash, final Progress progress)
			throws IOException {
		final List<EagleItem> items = new ArrayList<EagleItem>();
		final File images = new File(libraryRoot, "images");
		if (!images.isDirectory()) {
			LogUtils.warn("Eagle library has no images/: " + libraryRoot);
			return items;
		}
		final File[] infoDirs = images.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name != null && name.endsWith(".info") && new File(dir, name).isDirectory();
			}
		});
		if (infoDirs == null) {
			return items;
		}
		for (int i = 0; i < infoDirs.length; i++) {
			final File infoDir = infoDirs[i];
			// Skip Dropbox/cloud sync conflict clones
			if (infoDir.getName().indexOf("冲突") >= 0 || infoDir.getName().toLowerCase().indexOf("conflict") >= 0) {
				continue;
			}
			try {
				final EagleItem item = readItem(libraryRoot, infoDir, computeHash);
				if (item != null) {
					items.add(item);
				}
			}
			catch (Exception e) {
				LogUtils.warn("Skip Eagle item " + infoDir + ": " + e.getMessage());
			}
			if (progress != null && (i % 50 == 0)) {
				progress.onProgress(libraryRoot.getName() + ": " + (i + 1) + "/" + infoDirs.length);
			}
		}
		return items;
	}

	static EagleItem readItem(final File libraryRoot, final File infoDir, final boolean computeHash) throws IOException {
		final File metaFile = new File(infoDir, "metadata.json");
		if (!metaFile.isFile()) {
			return null;
		}
		final String json = FileUtils.readFileToString(metaFile, "UTF-8");
		final Map<String, String> fields = EagleMetadataJson.parseFlat(json);
		String id = fields.get("id");
		if (id == null || id.length() == 0) {
			final String dirName = infoDir.getName();
			id = dirName.endsWith(".info") ? dirName.substring(0, dirName.length() - 5) : dirName;
		}
		if ("true".equalsIgnoreCase(fields.get("isDeleted"))) {
			return null;
		}
		String name = fields.get("name");
		String ext = fields.get("ext");
		long size = 0L;
		try {
			if (fields.get("size") != null) {
				size = Long.parseLong(fields.get("size"));
			}
		}
		catch (NumberFormatException ignore) {
		}
		final File media = findMediaFile(infoDir, name, ext);
		if (media == null || !media.isFile()) {
			return null;
		}
		if (ext == null || ext.length() == 0) {
			ext = extensionOf(media.getName());
		}
		if (name == null || name.length() == 0) {
			name = stripExtension(media.getName());
		}
		if (size <= 0L) {
			size = media.length();
		}
		String hash = null;
		if (computeHash) {
			hash = EagleHash.sha256Hex(media);
		}
		return new EagleItem(id, name, ext, size, hash, media, libraryRoot);
	}

	private static File findMediaFile(final File infoDir, final String name, final String ext) {
		final File[] children = infoDir.listFiles();
		if (children == null) {
			return null;
		}
		File fallback = null;
		final String expected = (name != null && ext != null) ? (name + "." + ext) : null;
		for (File child : children) {
			if (!child.isFile()) {
				continue;
			}
			final String n = child.getName();
			if ("metadata.json".equalsIgnoreCase(n)) {
				continue;
			}
			if (n.toLowerCase().contains("thumbnail")) {
				continue;
			}
			if (expected != null && expected.equalsIgnoreCase(n)) {
				return child;
			}
			if (fallback == null) {
				fallback = child;
			}
		}
		return fallback;
	}

	private static String extensionOf(final String fileName) {
		final int dot = fileName.lastIndexOf('.');
		if (dot < 0 || dot == fileName.length() - 1) {
			return "";
		}
		return fileName.substring(dot + 1);
	}

	private static String stripExtension(final String fileName) {
		final int dot = fileName.lastIndexOf('.');
		if (dot <= 0) {
			return fileName;
		}
		return fileName.substring(0, dot);
	}
}
