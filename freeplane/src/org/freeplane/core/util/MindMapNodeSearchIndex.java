/*
 *  Docear / Freeplane — bounded node-text search index for MCP and workspace queries.
 */
package org.freeplane.core.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Per-file node TEXT index for keyword search.
 * <p>
 * Memory: LRU of compact per-file indexes (default 1200 files / 64MB; lean MCP
 * 200 files / 16MB). Does <b>not</b> keep
 * full XML or notes — only node id/text/parent/depth/modified.
 * <p>
 * Disk spill: under {@code <userDir>/mcp-node-search-index/} (or tmp), keyed by
 * path hash + validated by mtime/length so cold MCP restarts stay fast without
 * holding thousands of maps in RAM.
 */
public final class MindMapNodeSearchIndex {

	public static final class Hit {
		public final File mapFile;
		public final String nodeId;
		public final String nodeText;
		public final long modifiedAt;
		public final String parentNodeId;
		public final String parentPath;
		public final int depth;

		public Hit(final File mapFile, final String nodeId, final String nodeText, final long modifiedAt,
				final String parentNodeId, final String parentPath, final int depth) {
			this.mapFile = mapFile;
			this.nodeId = nodeId;
			this.nodeText = nodeText;
			this.modifiedAt = modifiedAt;
			this.parentNodeId = parentNodeId != null ? parentNodeId : "";
			this.parentPath = parentPath != null ? parentPath : "";
			this.depth = depth;
		}
	}

	public static final class Stats {
		public long memoryHits;
		public long diskHits;
		public long parses;
		public long filesScanned;
		public long filesSkippedByMtime;
		public long earlyStops;
		public int memoryEntries;
		public long estimatedMemoryBytes;
	}

	private static final class IndexedNode {
		final String nodeId;
		final String nodeText;
		final String textLower;
		final long modifiedAt;
		final String parentNodeId;
		final String parentPath;
		final int depth;

		IndexedNode(final String nodeId, final String nodeText, final long modifiedAt, final String parentNodeId,
				final String parentPath, final int depth) {
			this.nodeId = nodeId;
			this.nodeText = nodeText;
			this.textLower = nodeText.toLowerCase();
			this.modifiedAt = modifiedAt;
			this.parentNodeId = parentNodeId;
			this.parentPath = parentPath;
			this.depth = depth;
		}
	}

	private static final class FileIndex {
		final long modified;
		final long length;
		final long maxNodeModified;
		final List nodes;
		final int approxBytes;

		FileIndex(final long modified, final long length, final long maxNodeModified, final List nodes) {
			this.modified = modified;
			this.length = length;
			this.maxNodeModified = maxNodeModified;
			this.nodes = nodes;
			int bytes = 64;
			for (int i = 0; i < nodes.size(); i++) {
				final IndexedNode n = (IndexedNode) nodes.get(i);
				bytes += 48 + len(n.nodeId) + len(n.nodeText) + len(n.parentNodeId) + len(n.parentPath);
			}
			this.approxBytes = bytes;
		}
	}

	private static final String DISK_MAGIC = "docear-node-index-v1";
	/**
	 * Working-set cap. Sized for a large filtered search (~1k maps) without pinning an
	 * entire multi-thousand library in RAM; overflow lives on disk spill.
	 */
	private static final int MAX_MEMORY_FILES = 1200;
	private static final long MAX_MEMORY_BYTES = 64L * 1024L * 1024L;
	private static final int LEAN_MAX_MEMORY_FILES = 200;
	private static final long LEAN_MAX_MEMORY_BYTES = 16L * 1024L * 1024L;
	private static final int MAX_TEXT_CHARS = 2000;
	private static final int MAX_PARENT_PATH_CHARS = 400;
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private static final Object LOCK = new Object();
	private static final LinkedHashMap MEMORY = new LinkedHashMap(64, 0.75f, true);
	private static final Stats STATS = new Stats();
	private static volatile File diskCacheDir;

	private MindMapNodeSearchIndex() {
	}

	public static Stats getStatsSnapshot() {
		synchronized (LOCK) {
			final Stats copy = new Stats();
			copy.memoryHits = STATS.memoryHits;
			copy.diskHits = STATS.diskHits;
			copy.parses = STATS.parses;
			copy.filesScanned = STATS.filesScanned;
			copy.filesSkippedByMtime = STATS.filesSkippedByMtime;
			copy.earlyStops = STATS.earlyStops;
			copy.memoryEntries = MEMORY.size();
			copy.estimatedMemoryBytes = estimateMemoryBytesLocked();
			return copy;
		}
	}

	public static void resetStats() {
		synchronized (LOCK) {
			STATS.memoryHits = 0;
			STATS.diskHits = 0;
			STATS.parses = 0;
			STATS.filesScanned = 0;
			STATS.filesSkippedByMtime = 0;
			STATS.earlyStops = 0;
		}
	}

	public static void clearMemoryCache() {
		synchronized (LOCK) {
			MEMORY.clear();
		}
	}

	public static void invalidate(final File file) {
		if (file == null) {
			return;
		}
		final String key = cacheKey(file);
		synchronized (LOCK) {
			MEMORY.remove(key);
		}
		final File disk = diskFileForKey(key);
		if (disk != null && disk.isFile()) {
			disk.delete();
		}
	}

	/**
	 * Search {@code files} (preferably newest-first) for node TEXT containing {@code needle}
	 * (already lower-cased; empty = match all with time filter). Returns up to {@code limit}
	 * hits sorted by node {@code MODIFIED} descending.
	 */
	/** Predicate for {@link #searchFiltered}; kept free of plugin types so core stays dependency-free. */
	public interface NodeFilter {
		boolean matches(File mapFile, String nodeText);
	}

	/**
	 * Like {@link #search} but uses {@code filter} instead of a literal substring.
	 * {@code files} should be newest-first for best early-stop behaviour.
	 */
	public static List searchFiltered(final List files, final NodeFilter filter, final int limit,
			final long modifiedAfterMillis) {
		if (filter == null) {
			return Collections.EMPTY_LIST;
		}
		final int want = limit > 0 ? limit : Integer.MAX_VALUE;
		final List candidates = new ArrayList();
		long worstKeptModified = Long.MIN_VALUE;
		boolean trackingTop = false;

		for (int i = 0; i < files.size(); i++) {
			final File file = (File) files.get(i);
			if (file == null || !file.isFile()) {
				continue;
			}
			synchronized (LOCK) {
				STATS.filesScanned++;
			}
			if (modifiedAfterMillis > 0L && file.lastModified() < modifiedAfterMillis) {
				synchronized (LOCK) {
					STATS.filesSkippedByMtime++;
				}
				continue;
			}
			if (trackingTop && want != Integer.MAX_VALUE && file.lastModified() < worstKeptModified) {
				synchronized (LOCK) {
					STATS.earlyStops++;
				}
				break;
			}

			final FileIndex index = getOrBuildIndex(file);
			if (index == null) {
				continue;
			}
			if (modifiedAfterMillis > 0L && index.maxNodeModified < modifiedAfterMillis) {
				continue;
			}
			for (int j = 0; j < index.nodes.size(); j++) {
				final IndexedNode node = (IndexedNode) index.nodes.get(j);
				if (modifiedAfterMillis > 0L && node.modifiedAt < modifiedAfterMillis) {
					continue;
				}
				if (trackingTop && node.modifiedAt < worstKeptModified) {
					continue;
				}
				if (!filter.matches(file, node.nodeText)) {
					continue;
				}
				candidates.add(new Hit(file, node.nodeId, node.nodeText, node.modifiedAt, node.parentNodeId,
						node.parentPath, node.depth));
			}

			if (want != Integer.MAX_VALUE && candidates.size() >= want) {
				sortHitsByModifiedDesc(candidates);
				while (candidates.size() > want) {
					candidates.remove(candidates.size() - 1);
				}
				worstKeptModified = ((Hit) candidates.get(candidates.size() - 1)).modifiedAt;
				trackingTop = true;
			}
		}

		sortHitsByModifiedDesc(candidates);
		if (want != Integer.MAX_VALUE && candidates.size() > want) {
			return new ArrayList(candidates.subList(0, want));
		}
		return candidates;
	}

	public static List search(final List files, final String needle, final int limit, final long modifiedAfterMillis) {
		final String needleLower = needle == null ? "" : needle;
		final int want = limit > 0 ? limit : Integer.MAX_VALUE;
		final List candidates = new ArrayList();
		long worstKeptModified = Long.MIN_VALUE;
		boolean trackingTop = false;

		for (int i = 0; i < files.size(); i++) {
			final File file = (File) files.get(i);
			if (file == null || !file.isFile()) {
				continue;
			}
			synchronized (LOCK) {
				STATS.filesScanned++;
			}
			if (modifiedAfterMillis > 0L && file.lastModified() < modifiedAfterMillis) {
				synchronized (LOCK) {
					STATS.filesSkippedByMtime++;
				}
				continue;
			}
			// Early-stop: remaining files cannot beat the limit-th match (file mtime
			// lower bound for any node MODIFIED written into that file).
			if (trackingTop && want != Integer.MAX_VALUE && file.lastModified() < worstKeptModified) {
				synchronized (LOCK) {
					STATS.earlyStops++;
				}
				break;
			}

			final FileIndex index = getOrBuildIndex(file);
			if (index == null) {
				continue;
			}
			if (modifiedAfterMillis > 0L && index.maxNodeModified < modifiedAfterMillis) {
				continue;
			}
			for (int j = 0; j < index.nodes.size(); j++) {
				final IndexedNode node = (IndexedNode) index.nodes.get(j);
				if (modifiedAfterMillis > 0L && node.modifiedAt < modifiedAfterMillis) {
					continue;
				}
				if (trackingTop && node.modifiedAt < worstKeptModified) {
					continue;
				}
				if (needleLower.length() > 0 && node.textLower.indexOf(needleLower) < 0) {
					continue;
				}
				candidates.add(new Hit(file, node.nodeId, node.nodeText, node.modifiedAt, node.parentNodeId,
						node.parentPath, node.depth));
			}

			if (want != Integer.MAX_VALUE && candidates.size() >= want) {
				sortHitsByModifiedDesc(candidates);
				while (candidates.size() > want) {
					candidates.remove(candidates.size() - 1);
				}
				worstKeptModified = ((Hit) candidates.get(candidates.size() - 1)).modifiedAt;
				trackingTop = true;
			}
		}

		sortHitsByModifiedDesc(candidates);
		if (want != Integer.MAX_VALUE && candidates.size() > want) {
			return new ArrayList(candidates.subList(0, want));
		}
		return candidates;
	}

	private static FileIndex getOrBuildIndex(final File file) {
		final long modified = file.lastModified();
		final long length = file.length();
		final String key = cacheKey(file);

		synchronized (LOCK) {
			final FileIndex mem = (FileIndex) MEMORY.get(key);
			if (mem != null && mem.modified == modified && mem.length == length) {
				STATS.memoryHits++;
				return mem;
			}
		}

		final FileIndex fromDisk = readDiskIndex(key, file, modified, length);
		if (fromDisk != null) {
			synchronized (LOCK) {
				STATS.diskHits++;
				putMemoryLocked(key, fromDisk);
			}
			return fromDisk;
		}

		final FileIndex parsed = parseFile(file, modified, length);
		synchronized (LOCK) {
			STATS.parses++;
			if (parsed != null) {
				putMemoryLocked(key, parsed);
			}
		}
		if (parsed != null) {
			writeDiskIndex(key, parsed);
		}
		return parsed;
	}

	private static void putMemoryLocked(final String key, final FileIndex index) {
		MEMORY.put(key, index);
		evictMemoryLocked();
	}

	private static int maxMemoryFiles() {
		return McpHeadlessFlags.isLeanMemory() ? LEAN_MAX_MEMORY_FILES : MAX_MEMORY_FILES;
	}

	private static long maxMemoryBytes() {
		return McpHeadlessFlags.isLeanMemory() ? LEAN_MAX_MEMORY_BYTES : MAX_MEMORY_BYTES;
	}

	private static void evictMemoryLocked() {
		while (!MEMORY.isEmpty()) {
			if (MEMORY.size() <= maxMemoryFiles() && estimateMemoryBytesLocked() <= maxMemoryBytes()) {
				return;
			}
			final Object eldest = MEMORY.keySet().iterator().next();
			MEMORY.remove(eldest);
		}
	}

	private static long estimateMemoryBytesLocked() {
		long total = 0L;
		for (final Object value : MEMORY.values()) {
			total += ((FileIndex) value).approxBytes;
		}
		return total;
	}

	private static FileIndex parseFile(final File file, final long modified, final long length) {
		final List nodes = new ArrayList();
		final long[] maxModified = new long[] { 0L };
		try {
			final SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(false);
			final SAXParser saxParser = factory.newSAXParser();
			saxParser.parse(file, new DefaultHandler() {
				private final List stack = new ArrayList();

				public void startElement(final String uri, final String localName, final String qName,
						final Attributes attributes) {
					if (!"node".equals(qName)) {
						return;
					}
					final String id = attributes.getValue("ID");
					final String textAttr = attributes.getValue("TEXT");
					if (id == null || textAttr == null) {
						stack.add(null);
						return;
					}
					String nodeText = HtmlUtils.removeHtmlTagsFromString(textAttr);
					if (nodeText == null) {
						nodeText = "";
					}
					nodeText = nodeText.trim();
					if (nodeText.length() > MAX_TEXT_CHARS) {
						nodeText = nodeText.substring(0, MAX_TEXT_CHARS);
					}
					long modifiedAt = modified;
					final String modifiedStr = attributes.getValue("MODIFIED");
					if (modifiedStr != null) {
						try {
							modifiedAt = Long.parseLong(modifiedStr);
						}
						catch (Exception e) {
						}
					}
					if (modifiedAt > maxModified[0]) {
						maxModified[0] = modifiedAt;
					}
					String parentNodeId = "";
					final StringBuilder parentPath = new StringBuilder();
					for (int i = 0; i < stack.size(); i++) {
						final String[] ancestor = (String[]) stack.get(i);
						if (ancestor == null) {
							continue;
						}
						if (parentPath.length() > 0) {
							parentPath.append(" / ");
						}
						parentPath.append(ancestor[1]);
						parentNodeId = ancestor[0];
					}
					String parentPathText = parentPath.toString();
					if (parentPathText.length() > MAX_PARENT_PATH_CHARS) {
						parentPathText = parentPathText.substring(parentPathText.length() - MAX_PARENT_PATH_CHARS);
					}
					final int depth = stack.size();
					stack.add(new String[] { id, nodeText });
					nodes.add(new IndexedNode(id, nodeText, modifiedAt, parentNodeId, parentPathText, depth));
				}

				public void endElement(final String uri, final String localName, final String qName) {
					if ("node".equals(qName) && !stack.isEmpty()) {
						stack.remove(stack.size() - 1);
					}
				}
			});
		}
		catch (Exception e) {
			LogUtils.warn("MindMapNodeSearchIndex parse failed: " + file.getAbsolutePath() + ": " + e.getMessage());
			return null;
		}
		return new FileIndex(modified, length, maxModified[0], nodes);
	}

	private static void sortHitsByModifiedDesc(final List hits) {
		Collections.sort(hits, new Comparator() {
			public int compare(final Object o1, final Object o2) {
				final long a = ((Hit) o1).modifiedAt;
				final long b = ((Hit) o2).modifiedAt;
				return a < b ? 1 : (a > b ? -1 : 0);
			}
		});
	}

	private static String cacheKey(final File file) {
		try {
			return file.getCanonicalPath();
		}
		catch (Exception e) {
			return file.getAbsolutePath();
		}
	}

	private static int len(final String s) {
		return s == null ? 0 : s.length() * 2;
	}

	private static File resolveDiskCacheDir() {
		File dir = diskCacheDir;
		if (dir != null) {
			return dir;
		}
		synchronized (LOCK) {
			if (diskCacheDir != null) {
				return diskCacheDir;
			}
			File base = null;
			try {
				final org.freeplane.core.resources.ResourceController rc = org.freeplane.core.resources.ResourceController
						.getResourceController();
				if (rc != null) {
					final String user = rc.getFreeplaneUserDirectory();
					if (user != null && user.length() > 0) {
						// Per-PC cache under synced data dir (avoid WAL/lock fights across machines).
						base = new File(new File(user, "mcp-node-search-index"),
						        org.freeplane.core.util.LocalMachineId.getId());
					}
				}
			}
			catch (Throwable t) {
			}
			if (base == null) {
				base = new File(new File(System.getProperty("java.io.tmpdir"), "docear-mcp-node-search-index"),
				        org.freeplane.core.util.LocalMachineId.getId());
			}
			if (!base.isDirectory()) {
				base.mkdirs();
			}
			diskCacheDir = base;
			return diskCacheDir;
		}
	}

	/** Test hook: force disk cache directory. */
	public static void setDiskCacheDirForTests(final File dir) {
		synchronized (LOCK) {
			diskCacheDir = dir;
			if (dir != null && !dir.isDirectory()) {
				dir.mkdirs();
			}
		}
	}

	private static File diskFileForKey(final String key) {
		try {
			final MessageDigest md = MessageDigest.getInstance("SHA-1");
			final byte[] digest = md.digest(key.getBytes(UTF8));
			final StringBuilder sb = new StringBuilder(40);
			for (int i = 0; i < digest.length; i++) {
				sb.append(Integer.toHexString((digest[i] & 0xff) | 0x100).substring(1));
			}
			return new File(resolveDiskCacheDir(), sb.toString() + ".idx");
		}
		catch (Exception e) {
			return null;
		}
	}

	private static FileIndex readDiskIndex(final String key, final File sourceFile, final long modified,
			final long length) {
		final File disk = diskFileForKey(key);
		if (disk == null || !disk.isFile()) {
			return null;
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(disk), UTF8));
			final String magic = reader.readLine();
			if (!DISK_MAGIC.equals(magic)) {
				return null;
			}
			final long fileModified = Long.parseLong(readRequired(reader, "mtime"));
			final long fileLength = Long.parseLong(readRequired(reader, "length"));
			if (fileModified != modified || fileLength != length) {
				return null;
			}
			final int count = Integer.parseInt(readRequired(reader, "count"));
			final String sep = reader.readLine();
			if (sep == null || !sep.startsWith("---")) {
				return null;
			}
			final List nodes = new ArrayList(Math.max(16, count));
			long maxNodeModified = 0L;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.length() == 0) {
					continue;
				}
				final String[] parts = splitTab(line, 6);
				if (parts == null) {
					continue;
				}
				final String id = unescape(parts[0]);
				final long nodeModified = Long.parseLong(parts[1]);
				final int depth = Integer.parseInt(parts[2]);
				final String parentId = unescape(parts[3]);
				final String text = unescape(parts[4]);
				final String parentPath = unescape(parts[5]);
				if (nodeModified > maxNodeModified) {
					maxNodeModified = nodeModified;
				}
				nodes.add(new IndexedNode(id, text, nodeModified, parentId, parentPath, depth));
			}
			return new FileIndex(modified, length, maxNodeModified, nodes);
		}
		catch (Exception e) {
			return null;
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (Exception e) {
				}
			}
		}
	}

	private static void writeDiskIndex(final String key, final FileIndex index) {
		final File disk = diskFileForKey(key);
		if (disk == null) {
			return;
		}
		final File tmp = new File(disk.getAbsolutePath() + ".tmp");
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), UTF8));
			writer.write(DISK_MAGIC);
			writer.newLine();
			writer.write("mtime=");
			writer.write(Long.toString(index.modified));
			writer.newLine();
			writer.write("length=");
			writer.write(Long.toString(index.length));
			writer.newLine();
			writer.write("count=");
			writer.write(Integer.toString(index.nodes.size()));
			writer.newLine();
			writer.write("---");
			writer.newLine();
			for (int i = 0; i < index.nodes.size(); i++) {
				final IndexedNode n = (IndexedNode) index.nodes.get(i);
				writer.write(escape(n.nodeId));
				writer.write('\t');
				writer.write(Long.toString(n.modifiedAt));
				writer.write('\t');
				writer.write(Integer.toString(n.depth));
				writer.write('\t');
				writer.write(escape(n.parentNodeId));
				writer.write('\t');
				writer.write(escape(n.nodeText));
				writer.write('\t');
				writer.write(escape(n.parentPath));
				writer.newLine();
			}
			writer.close();
			writer = null;
			if (disk.exists()) {
				disk.delete();
			}
			tmp.renameTo(disk);
		}
		catch (Exception e) {
			if (tmp.exists()) {
				tmp.delete();
			}
		}
		finally {
			if (writer != null) {
				try {
					writer.close();
				}
				catch (Exception e) {
				}
			}
		}
	}

	private static String readRequired(final BufferedReader reader, final String key) throws Exception {
		final String line = reader.readLine();
		if (line == null || !line.startsWith(key + "=")) {
			throw new IllegalStateException("missing " + key);
		}
		return line.substring(key.length() + 1);
	}

	private static String[] splitTab(final String line, final int expect) {
		final List parts = new ArrayList(expect);
		int start = 0;
		for (int i = 0; i < line.length(); i++) {
			if (line.charAt(i) == '\t') {
				parts.add(line.substring(start, i));
				start = i + 1;
				if (parts.size() == expect - 1) {
					parts.add(line.substring(start));
					break;
				}
			}
		}
		if (parts.size() != expect) {
			return null;
		}
		return (String[]) parts.toArray(new String[expect]);
	}

	private static String escape(final String value) {
		if (value == null || value.length() == 0) {
			return "";
		}
		final StringBuffer sb = new StringBuffer(value.length() + 8);
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if (c == '\\') {
				sb.append("\\\\");
			}
			else if (c == '\t') {
				sb.append("\\t");
			}
			else if (c == '\n') {
				sb.append("\\n");
			}
			else if (c == '\r') {
				sb.append("\\r");
			}
			else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static String unescape(final String value) {
		if (value == null || value.indexOf('\\') < 0) {
			return value == null ? "" : value;
		}
		final StringBuffer sb = new StringBuffer(value.length());
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if (c == '\\' && i + 1 < value.length()) {
				final char n = value.charAt(++i);
				if (n == 't') {
					sb.append('\t');
				}
				else if (n == 'n') {
					sb.append('\n');
				}
				else if (n == 'r') {
					sb.append('\r');
				}
				else {
					sb.append(n);
				}
			}
			else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
