package org.docear.plugin.core.quickcommand;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;

/**
 * Persists recent @ map and @@ icon-node selections under {@code _data/quickcommand/}.
 */
final class QuickCommandHistory {
	private static final String DIR_NAME = "quickcommand";
	private static final String MAPS_FILE = "recent-maps.txt";
	private static final String NODES_FILE = "recent-icon-nodes.txt";
	private static final int MAX_MAPS = 40;
	private static final int MAX_NODES = 40;
	private static final String SEP = "\u001f";

	private static final QuickCommandHistory INSTANCE = new QuickCommandHistory();

	private final Object lock = new Object();
	private List recentMaps = null;
	private List recentNodes = null;

	private QuickCommandHistory() {
	}

	static QuickCommandHistory getInstance() {
		return INSTANCE;
	}

	List getRecentMaps() {
		ensureLoaded();
		synchronized (lock) {
			return new ArrayList(recentMaps);
		}
	}

	List getRecentIconNodes() {
		ensureLoaded();
		synchronized (lock) {
			return new ArrayList(recentNodes);
		}
	}

	int mapRank(final String mapName) {
		if (mapName == null) {
			return -1;
		}
		ensureLoaded();
		synchronized (lock) {
			for (int i = recentMaps.size() - 1; i >= 0; i--) {
				if (mapName.equalsIgnoreCase((String) recentMaps.get(i))) {
					return i;
				}
			}
		}
		return -1;
	}

	int iconRank(final String nodeId, final File mapFile) {
		if (nodeId == null) {
			return -1;
		}
		ensureLoaded();
		final String path = mapFile != null ? mapFile.getAbsolutePath() : "";
		synchronized (lock) {
			for (int i = recentNodes.size() - 1; i >= 0; i--) {
				final RecentIcon node = (RecentIcon) recentNodes.get(i);
				if (nodeId.equals(node.nodeId)
				        && (path.length() == 0 || path.equalsIgnoreCase(node.mapPath))) {
					return i;
				}
			}
		}
		return -1;
	}

	void recordMap(final String mapName) {
		if (mapName == null || mapName.trim().length() == 0) {
			return;
		}
		final String name = mapName.trim();
		ensureLoaded();
		synchronized (lock) {
			recentMaps.remove(name);
			for (int i = recentMaps.size() - 1; i >= 0; i--) {
				if (name.equalsIgnoreCase((String) recentMaps.get(i))) {
					recentMaps.remove(i);
				}
			}
			recentMaps.add(name);
			trim(recentMaps, MAX_MAPS);
			saveLines(MAPS_FILE, recentMaps);
		}
	}

	void recordIconNode(final String text, final String mapName, final File mapFile, final String nodeId) {
		if (text == null || nodeId == null || mapFile == null) {
			return;
		}
		ensureLoaded();
		final RecentIcon item = new RecentIcon(text, mapName == null ? "" : mapName, mapFile.getAbsolutePath(), nodeId);
		synchronized (lock) {
			for (int i = recentNodes.size() - 1; i >= 0; i--) {
				final RecentIcon existing = (RecentIcon) recentNodes.get(i);
				if (nodeId.equals(existing.nodeId) && item.mapPath.equalsIgnoreCase(existing.mapPath)) {
					recentNodes.remove(i);
				}
			}
			recentNodes.add(item);
			trim(recentNodes, MAX_NODES);
			final List lines = new ArrayList(recentNodes.size());
			for (int i = 0; i < recentNodes.size(); i++) {
				lines.add(((RecentIcon) recentNodes.get(i)).serialize());
			}
			saveLines(NODES_FILE, lines);
		}
	}

	private void ensureLoaded() {
		synchronized (lock) {
			if (recentMaps != null && recentNodes != null) {
				return;
			}
			recentMaps = loadMapLines();
			recentNodes = loadNodeLines();
		}
	}

	private List loadMapLines() {
		final List lines = readLines(MAPS_FILE);
		final List out = new ArrayList();
		for (int i = 0; i < lines.size(); i++) {
			final String line = ((String) lines.get(i)).trim();
			if (line.length() > 0) {
				out.add(line);
			}
		}
		return out;
	}

	private List loadNodeLines() {
		final List lines = readLines(NODES_FILE);
		final List out = new ArrayList();
		for (int i = 0; i < lines.size(); i++) {
			final RecentIcon item = RecentIcon.parse((String) lines.get(i));
			if (item != null) {
				out.add(item);
			}
		}
		return out;
	}

	private static void trim(final List list, final int max) {
		while (list.size() > max) {
			list.remove(0);
		}
	}

	private static File dir() {
		final File root = MindMapDataRootResolver.getApplicationConfigDirectory();
		if (root == null) {
			return null;
		}
		final File dir = new File(root, DIR_NAME);
		if (!dir.isDirectory() && !dir.mkdirs()) {
			return null;
		}
		return dir;
	}

	private static List readLines(final String fileName) {
		final File dir = dir();
		if (dir == null) {
			return Collections.EMPTY_LIST;
		}
		final File file = new File(dir, fileName);
		if (!file.isFile()) {
			return new ArrayList();
		}
		final List lines = new ArrayList();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		}
		catch (Exception e) {
			LogUtils.warn("QuickCommand: read history failed " + fileName, e);
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (Exception e) {
					// ignore
				}
			}
		}
		return lines;
	}

	private static void saveLines(final String fileName, final List lines) {
		final File dir = dir();
		if (dir == null) {
			return;
		}
		final File file = new File(dir, fileName);
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
			for (int i = 0; i < lines.size(); i++) {
				writer.write(String.valueOf(lines.get(i)));
				writer.newLine();
			}
		}
		catch (Exception e) {
			LogUtils.warn("QuickCommand: save history failed " + fileName, e);
		}
		finally {
			if (writer != null) {
				try {
					writer.close();
				}
				catch (Exception e) {
					// ignore
				}
			}
		}
	}

	static final class RecentIcon {
		final String text;
		final String mapName;
		final String mapPath;
		final String nodeId;

		RecentIcon(final String text, final String mapName, final String mapPath, final String nodeId) {
			this.text = text;
			this.mapName = mapName;
			this.mapPath = mapPath;
			this.nodeId = nodeId;
		}

		String serialize() {
			return text + SEP + mapName + SEP + mapPath + SEP + nodeId;
		}

		static RecentIcon parse(final String line) {
			if (line == null || line.trim().length() == 0) {
				return null;
			}
			final String[] parts = line.split(SEP, -1);
			if (parts.length < 4) {
				return null;
			}
			return new RecentIcon(parts[0], parts[1], parts[2], parts[3]);
		}
	}
}
