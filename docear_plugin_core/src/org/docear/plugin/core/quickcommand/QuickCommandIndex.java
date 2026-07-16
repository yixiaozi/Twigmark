package org.docear.plugin.core.quickcommand;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
			entries.add(new MapEntry(name, file));
		}
		Collections.sort(entries, new Comparator() {
			public int compare(final Object a, final Object b) {
				return ((MapEntry) a).name.compareToIgnoreCase(((MapEntry) b).name);
			}
		});
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
		final List out = new ArrayList();
		for (int i = 0; i < source.size() && out.size() < limit; i++) {
			final MapEntry entry = (MapEntry) source.get(i);
			if (q.length() == 0 || matches(entry.name, q)) {
				out.add(QuickCommandCandidate.map(entry.name, entry.file));
			}
		}
		return out;
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
				if (matches(entry.name, want)) {
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
		final List out = new ArrayList();
		for (int i = 0; i < source.size() && out.size() < limit; i++) {
			final IconEntry entry = (IconEntry) source.get(i);
			if (q.length() == 0 || matches(entry.text, q) || matches(entry.mapName, q)) {
				out.add(QuickCommandCandidate.iconNode(entry.text, entry.mapName, entry.file, entry.nodeId));
			}
		}
		return out;
	}

	IconEntry findIconNode(final String query, final QuickCommandCandidate selected) {
		if (selected != null && selected.kind == QuickCommandCandidate.Kind.ICON_NODE && selected.mapFile != null
		        && selected.nodeId != null) {
			return new IconEntry(selected.label, stripMm(selected.mapFile.getName()), selected.mapFile, selected.nodeId);
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
				if (contains == null && matches(entry.text, q)) {
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
		for (int i = 0; i < source.size() && out.size() < limit; i++) {
			final LaunchEntry entry = (LaunchEntry) source.get(i);
			if (matches(entry.label, q) || matches(entry.file.getName(), q)) {
				out.add(QuickCommandCandidate.launch(entry.label, entry.file));
			}
		}
		return out;
	}

	boolean iconsReady() {
		return iconsReady.get();
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
							entries.add(new IconEntry(node[1], mapName, file, node[0]));
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
				entries.add(new LaunchEntry(label, child));
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

	private static boolean matches(final String text, final String query) {
		if (text == null) {
			return false;
		}
		final String t = text.toLowerCase(Locale.ROOT);
		final String q = query.toLowerCase(Locale.ROOT);
		if (t.contains(q)) {
			return true;
		}
		return subsequence(t, q);
	}

	/** Pinyin-style loose match: query chars appear in order inside text. */
	private static boolean subsequence(final String text, final String query) {
		int ti = 0;
		for (int qi = 0; qi < query.length(); qi++) {
			final char c = query.charAt(qi);
			boolean found = false;
			while (ti < text.length()) {
				if (text.charAt(ti++) == c) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
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

		MapEntry(final String name, final File file) {
			this.name = name;
			this.file = file;
		}
	}

	static final class IconEntry {
		final String text;
		final String mapName;
		final File file;
		final String nodeId;

		IconEntry(final String text, final String mapName, final File file, final String nodeId) {
			this.text = text;
			this.mapName = mapName;
			this.file = file;
			this.nodeId = nodeId;
		}
	}

	static final class LaunchEntry {
		final String label;
		final File file;

		LaunchEntry(final String label, final File file) {
			this.label = label;
			this.file = file;
		}
	}
}
