package org.docear.plugin.core.quickcommand;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * In-memory indexes for maps, icon nodes, and quick-launch files.
 */
final class QuickCommandIndex {
	private static final String PROP_LAUNCH_FOLDERS = "quickcommand.launch_folders";
	private static final String SKIP_ICON = "button_ok";
	private static final int MAX_ICON_NODES = 20000;
	private static final int MAX_LAUNCH = 5000;

	private static final QuickCommandIndex INSTANCE = new QuickCommandIndex();

	private final Object lock = new Object();
	private List mapEntries = Collections.EMPTY_LIST;
	private List iconEntries = Collections.EMPTY_LIST;
	private List launchEntries = Collections.EMPTY_LIST;
	private final AtomicBoolean mapsReady = new AtomicBoolean();
	private final AtomicBoolean iconsReady = new AtomicBoolean();
	private final AtomicBoolean launchReady = new AtomicBoolean();
	private final AtomicBoolean iconsScanning = new AtomicBoolean();

	private QuickCommandIndex() {
	}

	static QuickCommandIndex getInstance() {
		return INSTANCE;
	}

	void ensureMaps() {
		if (!mapsReady.get()) {
			rebuildMaps();
		}
	}

	void ensureLaunch() {
		if (!launchReady.get()) {
			rebuildLaunch();
		}
	}

	void ensureIconsAsync() {
		if (iconsReady.get() || !iconsScanning.compareAndSet(false, true)) {
			return;
		}
		final Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					rebuildIcons();
				}
				finally {
					iconsScanning.set(false);
				}
			}
		}, "QuickCommand-IconIndex");
		thread.setDaemon(true);
		thread.start();
	}

	void rebuildMaps() {
		final List files = new ArrayList();
		MindMapDataRootResolver.collectMindmapFiles(files);
		final List entries = new ArrayList(files.size());
		for (int i = 0; i < files.size(); i++) {
			final File file = (File) files.get(i);
			if (file == null || !file.isFile()) {
				continue;
			}
			final String name = stripMm(file.getName());
			if (name.length() == 0 || name.startsWith("~")) {
				continue;
			}
			entries.add(new MapEntry(name, file, PinyinMatch.fullPinyin(name), PinyinMatch.initials(name),
			        file.lastModified()));
		}
		synchronized (lock) {
			mapEntries = entries;
			mapsReady.set(true);
		}
	}

	void rebuildIcons() {
		final List files = new ArrayList();
		MindMapDataRootResolver.collectMindmapFiles(files);
		final List entries = new ArrayList();
		for (int i = 0; i < files.size() && entries.size() < MAX_ICON_NODES; i++) {
			final File file = (File) files.get(i);
			scanIconNodes(file, entries);
		}
		Collections.sort(entries, new Comparator() {
			public int compare(final Object a, final Object b) {
				return ((IconEntry) a).text.compareToIgnoreCase(((IconEntry) b).text);
			}
		});
		synchronized (lock) {
			iconEntries = entries;
			iconsReady.set(true);
		}
		LogUtils.info("QuickCommand: indexed " + entries.size() + " icon nodes.");
	}

	void rebuildLaunch() {
		final List entries = new ArrayList();
		final List roots = new ArrayList();
		addIfDir(roots, windowsStartMenu(true));
		addIfDir(roots, windowsStartMenu(false));
		addIfDir(roots, new File(System.getProperty("user.home", ""), "Desktop"));
		final String configured = ResourceController.getResourceController().getProperty(PROP_LAUNCH_FOLDERS, "");
		if (configured != null && configured.trim().length() > 0) {
			final String[] parts = configured.split(";");
			for (int i = 0; i < parts.length; i++) {
				addIfDir(roots, new File(parts[i].trim()));
			}
		}
		for (int i = 0; i < roots.size() && entries.size() < MAX_LAUNCH; i++) {
			collectLaunchFiles((File) roots.get(i), entries, 0);
		}
		Collections.sort(entries, new Comparator() {
			public int compare(final Object a, final Object b) {
				return ((LaunchEntry) a).label.compareToIgnoreCase(((LaunchEntry) b).label);
			}
		});
		synchronized (lock) {
			launchEntries = entries;
			launchReady.set(true);
		}
	}

	List filterMaps(final String query, final int limit) {
		ensureMaps();
		final String q = normalize(query);
		final List source;
		synchronized (lock) {
			source = mapEntries;
		}
		final QuickCommandHistory history = QuickCommandHistory.getInstance();
		final List scored = new ArrayList();
		final Set seen = new HashSet();
		if (q.length() == 0) {
			final List recent = history.getRecentMaps();
			for (int i = recent.size() - 1; i >= 0; i--) {
				final String name = (String) recent.get(i);
				final MapEntry entry = findMapEntryByName(source, name);
				if (entry != null && seen.add(key(entry.name))) {
					scored.add(QuickCommandCandidate.map(entry.name, entry.file, true, i, entry.modifiedAt));
				}
			}
			sortByModifiedDesc(scored);
			return take(scored, limit);
		}
		for (int i = 0; i < source.size(); i++) {
			final MapEntry entry = (MapEntry) source.get(i);
			if (!PinyinMatch.matches(entry.name, entry.fullPinyin, entry.initials, q)) {
				continue;
			}
			if (!seen.add(key(entry.name))) {
				continue;
			}
			final int rank = history.mapRank(entry.name);
			scored.add(QuickCommandCandidate.map(entry.name, entry.file, rank >= 0, rank, entry.modifiedAt));
		}
		sortByModifiedDesc(scored);
		return take(scored, limit);
	}

	MapEntry findMapExact(final String mapName) {
		ensureMaps();
		final String want = stripMm(normalize(mapName));
		if (want.length() == 0) {
			return null;
		}
		synchronized (lock) {
			for (int i = 0; i < mapEntries.size(); i++) {
				final MapEntry entry = (MapEntry) mapEntries.get(i);
				if (entry.name.equalsIgnoreCase(want)) {
					return entry;
				}
			}
			for (int i = 0; i < mapEntries.size(); i++) {
				final MapEntry entry = (MapEntry) mapEntries.get(i);
				if (PinyinMatch.matches(entry.name, entry.fullPinyin, entry.initials, want)) {
					return entry;
				}
			}
		}
		return null;
	}

	List filterIconNodes(final String query, final int limit) {
		final String q = normalize(query);
		final List source;
		synchronized (lock) {
			source = iconEntries;
		}
		if (!iconsReady.get()) {
			ensureIconsAsync();
			final List pending = new ArrayList();
			pending.add(QuickCommandCandidate.hint("正在索引图标节点…", "完成后继续输入即可筛选；或输入 allicons 强制重建"));
			return pending;
		}
		final QuickCommandHistory history = QuickCommandHistory.getInstance();
		final List scored = new ArrayList();
		final Set seen = new HashSet();
		if (q.length() == 0) {
			final List recent = history.getRecentIconNodes();
			for (int i = recent.size() - 1; i >= 0; i--) {
				final QuickCommandHistory.RecentIcon recentIcon = (QuickCommandHistory.RecentIcon) recent.get(i);
				final IconEntry entry = findIconEntry(source, recentIcon);
				if (entry != null && seen.add(iconKey(entry))) {
					scored.add(QuickCommandCandidate.iconNode(entry.text, entry.mapName, entry.file, entry.nodeId, true,
					        i, entry.modifiedAt));
				}
				else if (entry == null && recentIcon.mapPath != null) {
					final File file = new File(recentIcon.mapPath);
					if (file.isFile() && seen.add(recentIcon.nodeId + "|" + recentIcon.mapPath)) {
						scored.add(QuickCommandCandidate.iconNode(recentIcon.text, recentIcon.mapName, file,
						        recentIcon.nodeId, true, i, file.lastModified()));
					}
				}
			}
			sortByModifiedDesc(scored);
			return take(scored, limit);
		}
		for (int i = 0; i < source.size(); i++) {
			final IconEntry entry = (IconEntry) source.get(i);
			if (!PinyinMatch.matches(entry.text, entry.fullPinyin, entry.initials, q)
			        && !PinyinMatch.matches(entry.mapName, entry.mapFullPinyin, entry.mapInitials, q)) {
				continue;
			}
			if (!seen.add(iconKey(entry))) {
				continue;
			}
			final int rank = history.iconRank(entry.nodeId, entry.file);
			scored.add(QuickCommandCandidate.iconNode(entry.text, entry.mapName, entry.file, entry.nodeId, rank >= 0,
			        rank, entry.modifiedAt));
		}
		sortByModifiedDesc(scored);
		return take(scored, limit);
	}

	IconEntry findIconNode(final String query, final QuickCommandCandidate selected) {
		if (selected != null && selected.kind == QuickCommandCandidate.Kind.ICON_NODE && selected.mapFile != null
		        && selected.nodeId != null) {
			return new IconEntry(selected.label, selected.detail, selected.mapFile, selected.nodeId,
			        PinyinMatch.fullPinyin(selected.label), PinyinMatch.initials(selected.label),
			        PinyinMatch.fullPinyin(selected.detail), PinyinMatch.initials(selected.detail),
			        selected.mapFile.lastModified());
		}
		final String q = normalize(query);
		if (q.length() == 0) {
			return null;
		}
		synchronized (lock) {
			IconEntry contains = null;
			for (int i = 0; i < iconEntries.size(); i++) {
				final IconEntry entry = (IconEntry) iconEntries.get(i);
				if (entry.text.equalsIgnoreCase(q)) {
					return entry;
				}
				if (contains == null && PinyinMatch.matches(entry.text, entry.fullPinyin, entry.initials, q)) {
					contains = entry;
				}
			}
			return contains;
		}
	}

	List filterLaunch(final String query, final int limit) {
		ensureLaunch();
		final String q = normalize(query);
		if (q.length() < 1) {
			return Collections.EMPTY_LIST;
		}
		final List source;
		synchronized (lock) {
			source = launchEntries;
		}
		final List out = new ArrayList();
		for (int i = 0; i < source.size(); i++) {
			final LaunchEntry entry = (LaunchEntry) source.get(i);
			if (PinyinMatch.matches(entry.label, entry.fullPinyin, entry.initials, q)
			        || PinyinMatch.matches(entry.file.getName(), entry.fullPinyin, entry.initials, q)) {
				out.add(QuickCommandCandidate.launch(entry.label, entry.file));
			}
		}
		sortByModifiedDesc(out);
		return take(out, limit);
	}

	boolean iconsReady() {
		return iconsReady.get();
	}

	private static MapEntry findMapEntryByName(final List source, final String name) {
		for (int i = 0; i < source.size(); i++) {
			final MapEntry entry = (MapEntry) source.get(i);
			if (entry.name.equalsIgnoreCase(name)) {
				return entry;
			}
		}
		return null;
	}

	private static IconEntry findIconEntry(final List source, final QuickCommandHistory.RecentIcon recent) {
		for (int i = 0; i < source.size(); i++) {
			final IconEntry entry = (IconEntry) source.get(i);
			if (recent.nodeId.equals(entry.nodeId)
			        && (recent.mapPath.length() == 0 || recent.mapPath.equalsIgnoreCase(entry.file.getAbsolutePath()))) {
				return entry;
			}
		}
		return null;
	}

	private static void sortByModifiedDesc(final List scored) {
		Collections.sort(scored, new Comparator() {
			public int compare(final Object a, final Object b) {
				final QuickCommandCandidate ca = (QuickCommandCandidate) a;
				final QuickCommandCandidate cb = (QuickCommandCandidate) b;
				if (ca.modifiedAt != cb.modifiedAt) {
					return ca.modifiedAt > cb.modifiedAt ? -1 : 1;
				}
				return ca.label.compareToIgnoreCase(cb.label);
			}
		});
	}

	private static List take(final List scored, final int limit) {
		if (scored.size() <= limit) {
			return scored;
		}
		return new ArrayList(scored.subList(0, limit));
	}

	private static String key(final String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT);
	}

	private static String iconKey(final IconEntry entry) {
		return entry.nodeId + "|" + entry.file.getAbsolutePath();
	}

	private static void scanIconNodes(final File file, final List entries) {
		if (file == null || !file.isFile() || file.getName().startsWith("~")) {
			return;
		}
		try {
			final SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(false);
			final SAXParser parser = factory.newSAXParser();
			final String mapName = stripMm(file.getName());
			final String mapFull = PinyinMatch.fullPinyin(mapName);
			final String mapInit = PinyinMatch.initials(mapName);
			parser.parse(file, new DefaultHandler() {
				private final List stack = new ArrayList();

				public void startElement(final String uri, final String localName, final String qName,
				        final Attributes attributes) {
					if ("node".equals(qName)) {
						final String id = attributes.getValue("ID");
						final String text = normalizeText(attributes.getValue("TEXT"));
						stack.add(new String[] { id, text, "0" });
					}
					else if ("icon".equals(qName) && !stack.isEmpty()) {
						final String builtin = attributes.getValue("BUILTIN");
						if (builtin == null || SKIP_ICON.equalsIgnoreCase(builtin)) {
							return;
						}
						final String[] node = (String[]) stack.get(stack.size() - 1);
						if ("1".equals(node[2])) {
							return;
						}
						node[2] = "1";
						if (node[0] != null && node[1] != null && node[1].length() > 0
						        && !"bin".equalsIgnoreCase(node[1]) && entries.size() < MAX_ICON_NODES) {
							entries.add(new IconEntry(node[1], mapName, file, node[0], PinyinMatch.fullPinyin(node[1]),
							        PinyinMatch.initials(node[1]), mapFull, mapInit, file.lastModified()));
						}
					}
				}

				public void endElement(final String uri, final String localName, final String qName) {
					if ("node".equals(qName) && !stack.isEmpty()) {
						stack.remove(stack.size() - 1);
					}
				}
			});
		}
		catch (Exception e) {
			LogUtils.warn("QuickCommand: icon scan failed for " + file, e);
		}
	}

	private static void collectLaunchFiles(final File dir, final List entries, final int depth) {
		if (dir == null || !dir.isDirectory() || depth > 4 || entries.size() >= MAX_LAUNCH) {
			return;
		}
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (int i = 0; i < children.length && entries.size() < MAX_LAUNCH; i++) {
			final File child = children[i];
			if (child.isDirectory()) {
				collectLaunchFiles(child, entries, depth + 1);
				continue;
			}
			final String name = child.getName().toLowerCase(Locale.ROOT);
			if (name.endsWith(".lnk") || name.endsWith(".exe") || name.endsWith(".bat") || name.endsWith(".cmd")
			        || name.endsWith(".url")) {
				String label = child.getName();
				final int dot = label.lastIndexOf('.');
				if (dot > 0) {
					label = label.substring(0, dot);
				}
				entries.add(new LaunchEntry(label, child, PinyinMatch.fullPinyin(label), PinyinMatch.initials(label)));
			}
		}
	}

	private static File windowsStartMenu(final boolean user) {
		if (user) {
			final String appdata = System.getenv("APPDATA");
			if (appdata != null) {
				return new File(appdata, "Microsoft\\Windows\\Start Menu\\Programs");
			}
		}
		else {
			final String programData = System.getenv("PROGRAMDATA");
			if (programData != null) {
				return new File(programData, "Microsoft\\Windows\\Start Menu\\Programs");
			}
		}
		return null;
	}

	private static void addIfDir(final List roots, final File dir) {
		if (dir != null && dir.isDirectory()) {
			roots.add(dir);
		}
	}

	private static String normalize(final String value) {
		return value == null ? "" : value.trim();
	}

	private static String normalizeText(final String text) {
		if (text == null) {
			return "";
		}
		return HtmlUtils.htmlToPlain(text).replaceAll("\\s+", " ").trim();
	}

	private static String stripMm(final String name) {
		if (name == null) {
			return "";
		}
		final String n = name.trim();
		if (n.toLowerCase(Locale.ROOT).endsWith(".mm")) {
			return n.substring(0, n.length() - 3);
		}
		return n;
	}

	static final class MapEntry {
		final String name;
		final File file;
		final String fullPinyin;
		final String initials;
		final long modifiedAt;

		MapEntry(final String name, final File file, final String fullPinyin, final String initials,
		        final long modifiedAt) {
			this.name = name;
			this.file = file;
			this.fullPinyin = fullPinyin;
			this.initials = initials;
			this.modifiedAt = modifiedAt;
		}
	}

	static final class IconEntry {
		final String text;
		final String mapName;
		final File file;
		final String nodeId;
		final String fullPinyin;
		final String initials;
		final String mapFullPinyin;
		final String mapInitials;
		final long modifiedAt;

		IconEntry(final String text, final String mapName, final File file, final String nodeId,
		        final String fullPinyin, final String initials, final String mapFullPinyin, final String mapInitials,
		        final long modifiedAt) {
			this.text = text;
			this.mapName = mapName;
			this.file = file;
			this.nodeId = nodeId;
			this.fullPinyin = fullPinyin;
			this.initials = initials;
			this.mapFullPinyin = mapFullPinyin;
			this.mapInitials = mapInitials;
			this.modifiedAt = modifiedAt;
		}
	}

	static final class LaunchEntry {
		final String label;
		final File file;
		final String fullPinyin;
		final String initials;

		LaunchEntry(final String label, final File file, final String fullPinyin, final String initials) {
			this.label = label;
			this.file = file;
			this.fullPinyin = fullPinyin;
			this.initials = initials;
		}
	}
}
