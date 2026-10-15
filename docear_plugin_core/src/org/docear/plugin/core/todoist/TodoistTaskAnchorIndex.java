package org.docear.plugin.core.todoist;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Finds mind-map nodes that already carry a Todoist task id, including nodes the user
 * cut out of the import map into another file.
 */
final class TodoistTaskAnchorIndex {
	private final Map fileByTaskId = new HashMap();

	private TodoistTaskAnchorIndex() {
	}

	/** Every library map except the import target. Key is the Todoist task id. */
	static TodoistTaskAnchorIndex scan(final File importTarget) {
		final TodoistTaskAnchorIndex index = new TodoistTaskAnchorIndex();
		final File[] roots = MindMapDataRootResolver.getScanRoots();
		final List files = new java.util.ArrayList();
		for (int i = 0; i < roots.length; i++) {
			collectMindmapFiles(roots[i], files);
		}
		final SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setValidating(false);
		factory.setNamespaceAware(false);
		for (int i = 0; i < files.size(); i++) {
			final File file = (File) files.get(i);
			if (sameFile(file, importTarget)) {
				continue;
			}
			index.scanFile(factory, file);
		}
		return index;
	}

	File fileFor(final String taskId) {
		if (taskId == null) {
			return null;
		}
		return (File) fileByTaskId.get(taskId);
	}

	/** Open non-import map that already contains this task id. */
	static NodeModel findOpenNode(final String taskId, final File importTarget) {
		if (taskId == null || taskId.length() == 0) {
			return null;
		}
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null || controller.getMapViewManager() == null) {
				return null;
			}
			final Map maps = controller.getMapViewManager().getMaps();
			if (maps == null) {
				return null;
			}
			for (Iterator it = maps.values().iterator(); it.hasNext();) {
				final MapModel map = (MapModel) it.next();
				final File file = map.getFile();
				if (file == null || sameFile(file, importTarget) || map.getRootNode() == null) {
					continue;
				}
				final NodeModel found = findInTree(map.getRootNode(), taskId);
				if (found != null) {
					return found;
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn("Todoist: open-map anchor lookup failed", e);
		}
		return null;
	}

	private static NodeModel findInTree(final NodeModel node, final String taskId) {
		if (node == null) {
			return null;
		}
		if (taskId.equals(TodoistReminderFactory.getTaskId(node))) {
			return node;
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			final NodeModel found = findInTree((NodeModel) node.getChildAt(i), taskId);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private void scanFile(final SAXParserFactory factory, final File file) {
		try {
			final SAXParser parser = factory.newSAXParser();
			parser.parse(MindMapReminderScanner.sanitizedStream(file), new DefaultHandler() {
				public void startElement(final String uri, final String localName, final String qName,
						final Attributes attributes) {
					if (!"node".equals(qName)) {
						return;
					}
					final String taskId = attributes.getValue(TodoistNodeMetaIo.XML_TASK_ID);
					if (taskId != null && taskId.length() > 0 && !fileByTaskId.containsKey(taskId)) {
						fileByTaskId.put(taskId, file);
					}
				}
			});
		}
		catch (Exception e) {
			LogUtils.warn("Todoist: anchor scan skipped " + file.getPath(), e);
		}
	}

	private static void collectMindmapFiles(final File dir, final List out) {
		if (dir == null || !dir.exists() || !dir.isDirectory()) {
			return;
		}
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (int i = 0; i < children.length; i++) {
			final File file = children[i];
			if (file.isDirectory()) {
				if (!file.isHidden() && !file.getName().startsWith(".")) {
					collectMindmapFiles(file, out);
				}
			}
			else {
				final String name = file.getName();
				final String lower = name.toLowerCase();
				if (lower.endsWith(".mm") && !name.startsWith("~") && name.indexOf("\u51b2\u7a81\u526f\u672c") < 0) {
					out.add(file);
				}
			}
		}
	}

	private static boolean sameFile(final File left, final File right) {
		if (left == null || right == null) {
			return false;
		}
		try {
			return left.getCanonicalFile().equals(right.getCanonicalFile());
		}
		catch (Exception e) {
			return left.getAbsolutePath().equalsIgnoreCase(right.getAbsolutePath());
		}
	}
}
