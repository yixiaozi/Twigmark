package org.docear.plugin.core.eagle;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;

/**
 * Offline Eagle item index: id → file, plus name/size/hash lookup for migration.
 */
public final class EagleItemIndex {
	private static final EagleItemIndex INSTANCE = new EagleItemIndex();
	private static final String INDEX_FILE_NAME = "eagle-index.tsv";
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private final Object lock = new Object();
	private final Map<String, EagleItem> byId = new HashMap<String, EagleItem>();
	private final Map<String, List<EagleItem>> byFileName = new HashMap<String, List<EagleItem>>();
	private final Map<String, List<EagleItem>> byBaseName = new HashMap<String, List<EagleItem>>();
	private final Map<String, EagleItem> bySha256 = new HashMap<String, EagleItem>();
	private final Map<String, EagleItem> byAbsolutePath = new HashMap<String, EagleItem>();
	private volatile boolean loaded;
	private volatile long builtAt;

	public static EagleItemIndex getInstance() {
		return INSTANCE;
	}

	private EagleItemIndex() {
	}

	public File resolveFile(final String itemId) {
		ensureLoaded(false);
		if (itemId == null) {
			return null;
		}
		synchronized (lock) {
			final EagleItem item = byId.get(itemId);
			if (item == null) {
				return null;
			}
			final File file = item.getFile();
			if (file != null && file.isFile()) {
				return file;
			}
		}
		// stale cache → rebuild once
		rebuild(true, null);
		synchronized (lock) {
			final EagleItem item = byId.get(itemId);
			return item == null ? null : item.getFile();
		}
	}

	public EagleItem getById(final String itemId) {
		ensureLoaded(false);
		synchronized (lock) {
			return byId.get(itemId);
		}
	}

	public EagleItem findBestMatch(final File sourceFile, final String uriPathHint) {
		ensureLoaded(true);
		synchronized (lock) {
			return findBestMatchUnlocked(sourceFile, uriPathHint);
		}
	}

	/**
	 * Filename-only lookup for broken paths / display fallback. Requires a unique match
	 * on {@code name.ext} or base name; returns null if ambiguous or libraries unset.
	 */
	public EagleItem findUniqueByFileNameHint(final String uriPathHint) {
		if (uriPathHint == null || uriPathHint.length() == 0) {
			return null;
		}
		if (!loaded && EagleConfig.existingLibraryRoots().isEmpty()) {
			return null;
		}
		ensureLoaded(false);
		synchronized (lock) {
			return findByFileNameHintUnlocked(uriPathHint);
		}
	}

	/** Test / tooling: rebuild index from explicit library roots (bypasses config). */
	public void rebuildFromRoots(final List<File> roots, final boolean computeHash) {
		synchronized (lock) {
			clearMaps();
			try {
				final List<EagleItem> items = EagleLibraryScanner.scanLibraries(roots, computeHash, null);
				for (EagleItem item : items) {
					putItem(item);
				}
				builtAt = System.currentTimeMillis();
				loaded = true;
			}
			catch (IOException e) {
				loaded = true;
				LogUtils.warn("Eagle rebuildFromRoots failed: " + e.getMessage());
			}
		}
	}

	private EagleItem findBestMatchUnlocked(final File sourceFile, final String uriPathHint) {
		if (sourceFile != null && sourceFile.isFile()) {
			final EagleItem byPath = byAbsolutePath.get(canonicalPath(sourceFile));
			if (byPath != null) {
				return byPath;
			}
			try {
				final String hash = EagleHash.sha256Hex(sourceFile);
				final EagleItem byHash = bySha256.get(hash);
				if (byHash != null) {
					return byHash;
				}
			}
			catch (IOException e) {
				LogUtils.warn("Eagle hash failed for " + sourceFile + ": " + e.getMessage());
			}
			final String fileName = sourceFile.getName().toLowerCase();
			final List<EagleItem> sameName = byFileName.get(fileName);
			if (sameName != null && sameName.size() == 1) {
				return sameName.get(0);
			}
			if (sameName != null) {
				EagleItem sizeMatch = null;
				int sizeHits = 0;
				for (EagleItem item : sameName) {
					if (item.getSize() == sourceFile.length()) {
						sizeMatch = item;
						sizeHits++;
					}
				}
				if (sizeHits == 1) {
					return sizeMatch;
				}
			}
		}
		return findByFileNameHintUnlocked(uriPathHint);
	}

	private EagleItem findByFileNameHintUnlocked(final String uriPathHint) {
		final String hintName = extractFileName(uriPathHint);
		if (hintName == null) {
			return null;
		}
		final List<EagleItem> sameName = byFileName.get(hintName.toLowerCase());
		if (sameName != null && sameName.size() == 1) {
			return sameName.get(0);
		}
		// Also match the real media file name on disk (may differ from metadata name)
		EagleItem diskHit = null;
		int diskHits = 0;
		for (EagleItem item : byId.values()) {
			if (item.getFile() != null && hintName.equalsIgnoreCase(item.getFile().getName())) {
				diskHit = item;
				diskHits++;
			}
		}
		if (diskHits == 1) {
			return diskHit;
		}
		final String base = stripExtension(hintName).toLowerCase();
		final List<EagleItem> sameBase = byBaseName.get(base);
		if (sameBase != null && sameBase.size() == 1) {
			return sameBase.get(0);
		}
		return null;
	}

	public int size() {
		ensureLoaded(false);
		synchronized (lock) {
			return byId.size();
		}
	}

	public List<EagleItem> listAllItems() {
		ensureLoaded(false);
		synchronized (lock) {
			return new ArrayList<EagleItem>(byId.values());
		}
	}

	public void ensureLoaded(final boolean withHash) {
		if (loaded) {
			return;
		}
		synchronized (lock) {
			if (loaded) {
				return;
			}
			if (!loadFromDisk()) {
				rebuild(withHash, null);
			}
			else if (withHash && bySha256.isEmpty() && !byId.isEmpty()) {
				rebuild(true, null);
			}
			else {
				loaded = true;
			}
		}
	}

	public void rebuild(final boolean computeHash, final EagleLibraryScanner.Progress progress) {
		synchronized (lock) {
			final List<File> roots = EagleConfig.existingLibraryRoots();
			clearMaps();
			try {
				final List<EagleItem> items = EagleLibraryScanner.scanLibraries(roots, computeHash, progress);
				for (EagleItem item : items) {
					putItem(item);
				}
				builtAt = System.currentTimeMillis();
				loaded = true;
				saveToDisk();
				LogUtils.info("Eagle index rebuilt: " + byId.size() + " item(s) from " + roots.size() + " library(ies)");
			}
			catch (IOException e) {
				loaded = true;
				LogUtils.warn("Eagle index rebuild failed: " + e.getMessage());
			}
		}
	}

	/**
	 * Copy an on-disk image into the primary Eagle library and index it.
	 * Eagle app is not required; it will pick up the new {@code *.info} package on next open.
	 */
	public EagleItem importFile(final File sourceFile) throws IOException {
		if (sourceFile == null || !sourceFile.isFile()) {
			throw new IOException("source file missing");
		}
		final File library = EagleConfig.getPrimaryLibrary();
		if (library == null || !library.isDirectory()) {
			throw new IOException("no primary Eagle library configured");
		}
		ensureLoaded(true);
		synchronized (lock) {
			final EagleItem existing = findBestMatchUnlocked(sourceFile, sourceFile.getName());
			if (existing != null) {
				return existing;
			}
			final String id = newItemId();
			final String ext = extensionOf(sourceFile.getName());
			final String name = stripExtension(sourceFile.getName());
			final File images = new File(library, "images");
			if (!images.exists() && !images.mkdirs()) {
				throw new IOException("cannot create " + images);
			}
			final File infoDir = new File(images, id + ".info");
			if (!infoDir.mkdirs()) {
				throw new IOException("cannot create " + infoDir);
			}
			final String mediaName = name + (ext.length() == 0 ? "" : "." + ext);
			final File dest = new File(infoDir, mediaName);
			FileUtils.copyFile(sourceFile, dest);
			final long now = System.currentTimeMillis();
			final String meta = "{"
					+ "\"id\":\"" + escapeJson(id) + "\","
					+ "\"name\":\"" + escapeJson(name) + "\","
					+ "\"size\":" + dest.length() + ","
					+ "\"btime\":" + now + ","
					+ "\"mtime\":" + now + ","
					+ "\"ext\":\"" + escapeJson(ext) + "\","
					+ "\"tags\":[],"
					+ "\"folders\":[],"
					+ "\"isDeleted\":false,"
					+ "\"url\":\"\","
					+ "\"annotation\":\"imported-by-docear\","
					+ "\"modificationTime\":" + now
					+ "}";
			FileUtils.writeStringToFile(new File(infoDir, "metadata.json"), meta, "UTF-8");
			String hash = null;
			try {
				hash = EagleHash.sha256Hex(dest);
			}
			catch (IOException ignore) {
			}
			final EagleItem item = new EagleItem(id, name, ext, dest.length(), hash, dest, library);
			putItem(item);
			saveToDisk();
			return item;
		}
	}

	private void putItem(final EagleItem item) {
		byId.put(item.getId(), item);
		addMulti(byFileName, item.fileNameLower(), item);
		addMulti(byBaseName, item.baseNameLower(), item);
		if (item.getSha256() != null && item.getSha256().length() > 0) {
			bySha256.put(item.getSha256(), item);
		}
		if (item.getFile() != null) {
			byAbsolutePath.put(canonicalPath(item.getFile()), item);
		}
	}

	private void addMulti(final Map<String, List<EagleItem>> map, final String key, final EagleItem item) {
		if (key == null || key.length() == 0) {
			return;
		}
		List<EagleItem> list = map.get(key);
		if (list == null) {
			list = new ArrayList<EagleItem>();
			map.put(key, list);
		}
		list.add(item);
	}

	private void clearMaps() {
		byId.clear();
		byFileName.clear();
		byBaseName.clear();
		bySha256.clear();
		byAbsolutePath.clear();
	}

	private File indexFile() {
		try {
			final File data = MindMapDataRootResolver.getApplicationConfigDirectory();
			if (data != null) {
				return new File(data, INDEX_FILE_NAME);
			}
		}
		catch (Exception ignore) {
		}
		return new File(Compat.getApplicationUserDirectory(), INDEX_FILE_NAME);
	}

	private boolean loadFromDisk() {
		final File file = indexFile();
		if (!file.isFile()) {
			return false;
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
			String line;
			int count = 0;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("#") || line.trim().length() == 0) {
					continue;
				}
				final String[] parts = splitTsv(line);
				if (parts.length < 6) {
					continue;
				}
				final String id = parts[0];
				final String name = parts[1];
				final String ext = parts[2];
				long size = 0L;
				try {
					size = Long.parseLong(parts[3]);
				}
				catch (NumberFormatException ignore) {
				}
				final String hash = emptyToNull(parts[4]);
				final File media = new File(parts[5]);
				final File library = parts.length > 6 ? new File(parts[6]) : media.getParentFile();
				if (!media.isFile()) {
					continue;
				}
				putItem(new EagleItem(id, name, ext, size, hash, media, library));
				count++;
			}
			loaded = count > 0;
			return loaded;
		}
		catch (IOException e) {
			LogUtils.warn("Eagle index load failed: " + e.getMessage());
			clearMaps();
			return false;
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (IOException ignore) {
				}
			}
		}
	}

	private void saveToDisk() {
		final File file = indexFile();
		final File parent = file.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), UTF8));
			writer.write("# id\tname\text\tsize\tsha256\tfile\tlibrary");
			writer.newLine();
			writer.write("# builtAt=" + builtAt);
			writer.newLine();
			final List<String> ids = new ArrayList<String>(byId.keySet());
			Collections.sort(ids);
			for (String id : ids) {
				final EagleItem item = byId.get(id);
				writer.write(safe(item.getId()));
				writer.write('\t');
				writer.write(safe(item.getName()));
				writer.write('\t');
				writer.write(safe(item.getExt()));
				writer.write('\t');
				writer.write(Long.toString(item.getSize()));
				writer.write('\t');
				writer.write(safe(item.getSha256()));
				writer.write('\t');
				writer.write(safe(item.getFile() == null ? "" : item.getFile().getAbsolutePath()));
				writer.write('\t');
				writer.write(safe(item.getLibraryRoot() == null ? "" : item.getLibraryRoot().getAbsolutePath()));
				writer.newLine();
			}
		}
		catch (IOException e) {
			LogUtils.warn("Eagle index save failed: " + e.getMessage());
		}
		finally {
			if (writer != null) {
				try {
					writer.close();
				}
				catch (IOException ignore) {
				}
			}
		}
	}

	private static String[] splitTsv(final String line) {
		final List<String> parts = new ArrayList<String>();
		int start = 0;
		for (int i = 0; i < line.length(); i++) {
			if (line.charAt(i) == '\t') {
				parts.add(line.substring(start, i));
				start = i + 1;
			}
		}
		parts.add(line.substring(start));
		return parts.toArray(new String[parts.size()]);
	}

	private static String safe(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}

	private static String emptyToNull(final String value) {
		return value == null || value.length() == 0 ? null : value;
	}

	private static String canonicalPath(final File file) {
		try {
			return file.getCanonicalPath();
		}
		catch (IOException e) {
			return file.getAbsolutePath();
		}
	}

	private static String extractFileName(final String path) {
		if (path == null || path.length() == 0) {
			return null;
		}
		String p = path;
		final int q = p.indexOf('?');
		if (q >= 0) {
			p = p.substring(0, q);
		}
		p = p.replace('\\', '/');
		final int slash = p.lastIndexOf('/');
		if (slash >= 0) {
			p = p.substring(slash + 1);
		}
		return p.length() == 0 ? null : p;
	}

	private static String stripExtension(final String fileName) {
		final int dot = fileName.lastIndexOf('.');
		if (dot <= 0) {
			return fileName;
		}
		return fileName.substring(0, dot);
	}

	private static String extensionOf(final String fileName) {
		final int dot = fileName.lastIndexOf('.');
		if (dot < 0 || dot == fileName.length() - 1) {
			return "";
		}
		return fileName.substring(dot + 1);
	}

	private String newItemId() {
		final Random random = new Random();
		final char[] alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
		for (int attempt = 0; attempt < 20; attempt++) {
			final StringBuilder sb = new StringBuilder(13);
			for (int i = 0; i < 13; i++) {
				sb.append(alphabet[random.nextInt(alphabet.length)]);
			}
			final String id = sb.toString();
			if (!byId.containsKey(id)) {
				return id;
			}
		}
		return "D" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
	}

	private static String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		final StringBuilder sb = new StringBuilder(value.length() + 8);
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if (c == '\\' || c == '"') {
				sb.append('\\').append(c);
			}
			else if (c == '\n') {
				sb.append("\\n");
			}
			else if (c == '\r') {
				sb.append("\\r");
			}
			else if (c == '\t') {
				sb.append("\\t");
			}
			else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
