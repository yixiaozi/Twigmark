/*
 * Standalone micro-benchmark: baseline full SAX vs MindMapNodeSearchIndex.
 * Not part of production runtime; invoke via scripts/benchmark-mcp-search.sh.
 */
package org.freeplane.core.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public final class MindMapNodeSearchIndexBenchmark {

	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final int DEFAULT_FILES = 2000;
	private static final int NODES_PER_FILE = 40;
	private static final int LIMIT = 50;
	private static final String NEEDLE = "keyword";

	private MindMapNodeSearchIndexBenchmark() {
	}

	public static void main(final String[] args) throws Exception {
		int fileCount = DEFAULT_FILES;
		File root = new File(System.getProperty("java.io.tmpdir"), "docear-mcp-search-bench");
		boolean keep = false;
		for (int i = 0; i < args.length; i++) {
			if ("--files".equals(args[i]) && i + 1 < args.length) {
				fileCount = Integer.parseInt(args[++i]);
			}
			else if ("--root".equals(args[i]) && i + 1 < args.length) {
				root = new File(args[++i]);
			}
			else if ("--keep".equals(args[i])) {
				keep = true;
			}
		}

		System.out.println("=== Docear MCP search benchmark ===");
		System.out.println("files=" + fileCount + " nodesPerFile=" + NODES_PER_FILE + " root=" + root.getAbsolutePath());

		if (!root.isDirectory()) {
			root.mkdirs();
		}
		final List files = generateCorpus(root, fileCount);
		sortByMtimeDesc(files);
		System.out.println("corpus ready: " + files.size() + " .mm files");

		final File diskDir = new File(root, "_index-cache");
		MindMapNodeSearchIndex.setDiskCacheDirForTests(diskDir);
		MindMapNodeSearchIndex.clearMemoryCache();
		MindMapNodeSearchIndex.resetStats();

		// Warm JVM a little
		baselineSearch(files.subList(0, Math.min(20, files.size())), NEEDLE, LIMIT, 0L);

		final long baselineCold = timeMs(new Runnable() {
			public void run() {
				baselineSearch(files, NEEDLE, LIMIT, 0L);
			}
		});
		final long baselineWarm = timeMs(new Runnable() {
			public void run() {
				baselineSearch(files, NEEDLE, LIMIT, 0L);
			}
		});

		MindMapNodeSearchIndex.clearMemoryCache();
		deleteRecursive(diskDir);
		diskDir.mkdirs();
		MindMapNodeSearchIndex.resetStats();

		final long indexedCold = timeMs(new Runnable() {
			public void run() {
				MindMapNodeSearchIndex.search(files, NEEDLE, LIMIT, 0L);
			}
		});
		final MindMapNodeSearchIndex.Stats coldStats = MindMapNodeSearchIndex.getStatsSnapshot();

		// Disk warm: empty RAM, indexes already on disk from cold pass.
		MindMapNodeSearchIndex.clearMemoryCache();
		MindMapNodeSearchIndex.resetStats();
		final long indexedWarmDisk = timeMs(new Runnable() {
			public void run() {
				MindMapNodeSearchIndex.search(files, NEEDLE, LIMIT, 0L);
			}
		});
		final MindMapNodeSearchIndex.Stats warmDiskStats = MindMapNodeSearchIndex.getStatsSnapshot();

		// Hot RAM: indexes just loaded; access-order keeps the working set.
		MindMapNodeSearchIndex.resetStats();
		final long indexedHotMem = timeMs(new Runnable() {
			public void run() {
				MindMapNodeSearchIndex.search(files, NEEDLE, LIMIT, 0L);
			}
		});
		final MindMapNodeSearchIndex.Stats hotMemStats = MindMapNodeSearchIndex.getStatsSnapshot();

		final long recentCutoff = System.currentTimeMillis() - 365L * 24L * 60L * 60L * 1000L;
		MindMapNodeSearchIndex.resetStats();
		final long indexedRecentWarm = timeMs(new Runnable() {
			public void run() {
				MindMapNodeSearchIndex.search(files, NEEDLE, LIMIT, recentCutoff);
			}
		});
		final MindMapNodeSearchIndex.Stats recentStats = MindMapNodeSearchIndex.getStatsSnapshot();

		final long baselineRecent = timeMs(new Runnable() {
			public void run() {
				baselineSearch(files, NEEDLE, LIMIT, recentCutoff);
			}
		});

		printResult("baseline SAX (cold)", baselineCold);
		printResult("baseline SAX (repeat)", baselineWarm);
		printResult("indexed (cold parse+disk write)", indexedCold);
		printResult("indexed (warm disk, RAM cleared)", indexedWarmDisk);
		printResult("indexed (hot RAM working set)", indexedHotMem);
		printResult("baseline +365d filter", baselineRecent);
		printResult("indexed hot +365d filter", indexedRecentWarm);

		System.out.println();
		System.out.println("--- Speedup vs baseline cold ---");
		printSpeedup("indexed cold", baselineCold, indexedCold);
		printSpeedup("indexed warm disk", baselineCold, indexedWarmDisk);
		printSpeedup("indexed hot RAM", baselineCold, indexedHotMem);
		System.out.println("--- Speedup vs baseline repeat ---");
		printSpeedup("indexed warm disk", baselineWarm, indexedWarmDisk);
		printSpeedup("indexed hot RAM", baselineWarm, indexedHotMem);
		System.out.println("--- Speedup with 365d window (vs baseline filtered) ---");
		printSpeedup("indexed hot +365d", baselineRecent, indexedRecentWarm);

		System.out.println();
		System.out.println("cold stats: parses=" + coldStats.parses + " memHits=" + coldStats.memoryHits
				+ " diskHits=" + coldStats.diskHits + " earlyStops=" + coldStats.earlyStops
				+ " filesScanned=" + coldStats.filesScanned);
		System.out.println("warm-disk stats: parses=" + warmDiskStats.parses + " memHits=" + warmDiskStats.memoryHits
				+ " diskHits=" + warmDiskStats.diskHits + " earlyStops=" + warmDiskStats.earlyStops);
		System.out.println("hot-RAM stats: parses=" + hotMemStats.parses + " memHits=" + hotMemStats.memoryHits
				+ " diskHits=" + hotMemStats.diskHits + " memEntries=" + hotMemStats.memoryEntries
				+ " estBytes=" + hotMemStats.estimatedMemoryBytes);
		System.out.println("recent-window stats: filesScanned=" + recentStats.filesScanned + " skippedByMtime="
				+ recentStats.filesSkippedByMtime + " earlyStops=" + recentStats.earlyStops);

		if (!keep) {
			deleteRecursive(root);
			System.out.println("cleaned " + root.getAbsolutePath());
		}
		else {
			System.out.println("kept corpus at " + root.getAbsolutePath());
		}
	}

	private static void printResult(final String label, final long ms) {
		System.out.println(String.format(Locale.US, "%-42s %8d ms", label, Long.valueOf(ms)));
	}

	private static void printSpeedup(final String label, final long baselineMs, final long optimizedMs) {
		if (baselineMs <= 0L) {
			System.out.println(label + ": n/a");
			return;
		}
		final double faster = (baselineMs - optimizedMs) * 100.0 / baselineMs;
		final double ratio = optimizedMs <= 0L ? Double.POSITIVE_INFINITY : (baselineMs * 1.0 / optimizedMs);
		System.out.println(String.format(Locale.US, "%-42s %6.1f%% faster (%.2fx)", label, Double.valueOf(faster),
				Double.valueOf(ratio)));
	}

	private static long timeMs(final Runnable r) {
		final long start = System.nanoTime();
		r.run();
		return (System.nanoTime() - start) / 1000000L;
	}

	private static List generateCorpus(final File root, final int fileCount) throws Exception {
		final List files = new ArrayList(fileCount);
		final Random random = new Random(42L);
		final long now = System.currentTimeMillis();
		for (int i = 0; i < fileCount; i++) {
			final File dir = new File(root, "proj" + (i % 20));
			if (!dir.isDirectory()) {
				dir.mkdirs();
			}
			final File mm = new File(dir, "map-" + i + ".mm");
			writeMindMap(mm, i, random, now - i * 3600L * 1000L);
			// Stagger file mtimes so newest-first ordering is meaningful.
			mm.setLastModified(now - i * 60L * 1000L);
			files.add(mm);
		}
		return files;
	}

	private static void writeMindMap(final File file, final int fileIndex, final Random random, final long baseModified)
			throws Exception {
		final BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), UTF8));
		try {
			w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			w.write("<map version=\"1.0.1\">\n");
			w.write("<node ID=\"ID_ROOT_" + fileIndex + "\" TEXT=\"Root " + fileIndex + "\" MODIFIED=\"" + baseModified
					+ "\">\n");
			for (int n = 0; n < NODES_PER_FILE; n++) {
				final boolean hit = n % 17 == 0;
				final String text = hit ? ("Item " + n + " has " + NEEDLE + " " + fileIndex)
						: ("Ordinary node " + n + " alpha" + random.nextInt(10000));
				final long modified = baseModified - n * 1000L;
				w.write("<node ID=\"ID_" + fileIndex + "_" + n + "\" TEXT=\"" + xmlEscape(text) + "\" MODIFIED=\""
						+ modified + "\"/>\n");
			}
			w.write("</node>\n</map>\n");
		}
		finally {
			w.close();
		}
	}

	private static String xmlEscape(final String s) {
		return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
	}

	private static void sortByMtimeDesc(final List files) {
		Collections.sort(files, new Comparator() {
			public int compare(final Object o1, final Object o2) {
				final long a = ((File) o1).lastModified();
				final long b = ((File) o2).lastModified();
				return a < b ? 1 : (a > b ? -1 : 0);
			}
		});
	}

	/** Pre-optimization algorithm: parse every file with SAX every call. */
	private static List baselineSearch(final List files, final String needle, final int limit,
			final long modifiedAfterMillis) {
		final List matches = new ArrayList();
		for (int i = 0; i < files.size(); i++) {
			final File file = (File) files.get(i);
			if (modifiedAfterMillis > 0L && file.lastModified() < modifiedAfterMillis) {
				continue;
			}
			baselineCollect(file, needle, matches, modifiedAfterMillis);
		}
		Collections.sort(matches, new Comparator() {
			public int compare(final Object o1, final Object o2) {
				final long a = ((MindMapNodeSearchIndex.Hit) o1).modifiedAt;
				final long b = ((MindMapNodeSearchIndex.Hit) o2).modifiedAt;
				return a < b ? 1 : (a > b ? -1 : 0);
			}
		});
		if (limit > 0 && matches.size() > limit) {
			return new ArrayList(matches.subList(0, limit));
		}
		return matches;
	}

	private static void baselineCollect(final File file, final String needle, final List matches,
			final long modifiedAfterMillis) {
		try {
			final SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse(file, new DefaultHandler() {
				private final List stack = new ArrayList();

				public void startElement(final String uri, final String localName, final String qName,
						final Attributes attributes) {
					if (!"node".equals(qName)) {
						return;
					}
					final String id = attributes.getValue("ID");
					final String text = attributes.getValue("TEXT");
					if (id == null || text == null) {
						stack.add(null);
						return;
					}
					final String nodeText = text.trim();
					long modifiedAt = file.lastModified();
					final String modifiedStr = attributes.getValue("MODIFIED");
					if (modifiedStr != null) {
						try {
							modifiedAt = Long.parseLong(modifiedStr);
						}
						catch (Exception e) {
						}
					}
					String parentId = "";
					final StringBuilder parentPath = new StringBuilder();
					for (int i = 0; i < stack.size(); i++) {
						final String[] a = (String[]) stack.get(i);
						if (a == null) {
							continue;
						}
						if (parentPath.length() > 0) {
							parentPath.append(" / ");
						}
						parentPath.append(a[1]);
						parentId = a[0];
					}
					final int depth = stack.size();
					stack.add(new String[] { id, nodeText });
					if (modifiedAfterMillis > 0L && modifiedAt < modifiedAfterMillis) {
						return;
					}
					if (needle.length() > 0 && nodeText.toLowerCase().indexOf(needle) < 0) {
						return;
					}
					matches.add(new MindMapNodeSearchIndex.Hit(file, id, nodeText, modifiedAt, parentId,
							parentPath.toString(), depth));
				}

				public void endElement(final String uri, final String localName, final String qName) {
					if ("node".equals(qName) && !stack.isEmpty()) {
						stack.remove(stack.size() - 1);
					}
				}
			});
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void deleteRecursive(final File file) {
		if (file == null || !file.exists()) {
			return;
		}
		if (file.isDirectory()) {
			final File[] children = file.listFiles();
			if (children != null) {
				for (int i = 0; i < children.length; i++) {
					deleteRecursive(children[i]);
				}
			}
		}
		file.delete();
	}
}
