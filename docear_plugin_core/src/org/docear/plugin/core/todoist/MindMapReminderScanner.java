package org.docear.plugin.core.todoist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

final class MindMapReminderScanner {
	private final Set unreadableMapNames = new HashSet();
	/** Node ids that still carry a Todoist task id, whether or not they have a reminder. */
	private final Set linkedNodeIds = new HashSet();
	/** Todoist task ids still stored on some mind-map node. */
	private final Set linkedTaskIds = new HashSet();
	/** Task ids explicitly marked as original Todoist tasks. */
	private final Set importedTaskIds = new HashSet();

	/** Map file names whose XML could not be read. Their Todoist tasks must not be deleted. */
	Set unreadableMapNames() {
		return unreadableMapNames;
	}

	Set linkedNodeIds() {
		return linkedNodeIds;
	}

	Set linkedTaskIds() {
		return linkedTaskIds;
	}

	Set importedTaskIds() {
		return importedTaskIds;
	}

	List scanAllReminders() {
		final File[] roots = MindMapDataRootResolver.getScanRoots();
		final List files = new ArrayList();
		for (int i = 0; i < roots.length; i++) {
			collectMindmapFiles(roots[i], files);
		}
		final List reminders = new ArrayList();
		for (int i = 0; i < files.size(); i++) {
			File file = (File) files.get(i);
			if (TodoistConfig.isImportTargetFile(file)) {
				scanFile(file);
				continue;
			}
			reminders.addAll(scanFile(file));
		}
		overlayOpenMaps(reminders);
		return reminders;
	}

	/**
	 * Open maps win over the last saved file, so a manual sync cannot push stale disk text
	 * and then pull that stale text back over unsaved edits.
	 */
	private void overlayOpenMaps(final List reminders) {
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null || controller.getMapViewManager() == null) {
				return;
			}
			final Map maps = controller.getMapViewManager().getMaps();
			if (maps == null || maps.isEmpty()) {
				return;
			}
			final Set openPaths = new HashSet();
			final List live = new ArrayList();
			for (Iterator it = maps.values().iterator(); it.hasNext();) {
				final MapModel map = (MapModel) it.next();
				final File file = map.getFile();
				if (file == null || map.getRootNode() == null) {
					continue;
				}
				collectLiveLinks(map.getRootNode());
				if (TodoistConfig.isImportTargetFile(file)) {
					continue;
				}
				openPaths.add(pathKey(file));
				collectLiveReminders(map.getRootNode(), live);
				collectLiveLinks(map.getRootNode());
			}
			if (openPaths.isEmpty()) {
				return;
			}
			for (Iterator it = reminders.iterator(); it.hasNext();) {
				final TodoistReminderRecord record = (TodoistReminderRecord) it.next();
				if (record.file != null && openPaths.contains(pathKey(record.file))) {
					it.remove();
				}
			}
			reminders.addAll(live);
		}
		catch (Exception e) {
			LogUtils.warn("Todoist: could not overlay open maps onto reminder scan", e);
		}
	}

	private static void collectLiveReminders(final NodeModel node, final List out) {
		final TodoistReminderRecord record = TodoistReminderFactory.fromNode(node);
		if (record != null) {
			record.linkedTaskId = TodoistReminderFactory.getTaskId(node);
			TodoistReminderFactory.copyClock(node, record);
			out.add(record);
		}
		if (node.getChildren() == null) {
			return;
		}
		for (Iterator it = node.getChildren().iterator(); it.hasNext();) {
			collectLiveReminders((NodeModel) it.next(), out);
		}
	}

	private void collectLiveLinks(final NodeModel node) {
		if (node == null) {
			return;
		}
		final String taskId = TodoistReminderFactory.getTaskId(node);
		if (taskId != null && taskId.length() > 0) {
			linkedTaskIds.add(taskId);
			if (TodoistReminderFactory.isTodoistOrigin(node)) {
				importedTaskIds.add(taskId);
			}
			if (node.getID() != null && node.getID().length() > 0) {
				linkedNodeIds.add(node.getID());
			}
		}
		if (node.getChildren() == null) {
			return;
		}
		for (Iterator it = node.getChildren().iterator(); it.hasNext();) {
			collectLiveLinks((NodeModel) it.next());
		}
	}

	private static String pathKey(final File file) {
		try {
			return file.getCanonicalPath();
		}
		catch (IOException e) {
			return file.getAbsolutePath();
		}
	}

	/** Package-visible for integration tests. */
	List scanFile(final File file) {
		final List reminders = new ArrayList();
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(false);
			SAXParser saxParser = factory.newSAXParser();
			saxParser.parse(sanitizedStream(file), new DefaultHandler() {
				private final List nodeStack = new ArrayList();

				public void startElement(String uri, String localName, String qName, Attributes attributes) {
					if ("node".equals(qName)) {
						String id = attributes.getValue("ID");
						String text = attributes.getValue("TEXT");
						String remindType = attributes.getValue("REMINDERTYPE");
						int taskTime = parseInt(attributes.getValue("TASKTIME"), 0);
						int jinji = parseInt(attributes.getValue("JINJI"), 0);
						int rHour = parseInt(attributes.getValue("RHOUR"), 1);
						int rDays = parseInt(attributes.getValue("RDAYS"), 1);
						int rWeek = parseInt(attributes.getValue("RWEEK"), 1);
						int rMonth = parseInt(attributes.getValue("RMONTH"), 1);
						int rYear = parseInt(attributes.getValue("RYEAR"), 1);
						String weekDays = attributes.getValue("RWEEKS");
						String linkedTaskId = attributes.getValue("TODOIST_TASK_ID");
						if (linkedTaskId != null && linkedTaskId.length() > 0) {
							linkedTaskIds.add(linkedTaskId);
							if ("todoist".equals(attributes.getValue("TODOIST_ORIGIN"))) {
								importedTaskIds.add(linkedTaskId);
							}
							if (id != null && id.length() > 0) {
								linkedNodeIds.add(id);
							}
						}
						nodeStack.add(new Object[] { id, text == null ? "" : text, remindType,
								Integer.valueOf(taskTime), Integer.valueOf(jinji), Integer.valueOf(rHour),
								Integer.valueOf(rDays), Integer.valueOf(rWeek), Integer.valueOf(rMonth),
								Integer.valueOf(rYear), weekDays == null ? "" : weekDays,
								linkedTaskId == null ? "" : linkedTaskId, attributes.getValue("MODIFIED"),
								attributes.getValue("TODOIST_SYNCED_LOCAL"),
								attributes.getValue("TODOIST_SYNCED_REMOTE"),
								attributes.getValue("TODOIST_USER_PLACED") });
					}
					else if ("Parameters".equals(qName) && !nodeStack.isEmpty()) {
						String remindAt = attributes.getValue("REMINDUSERAT");
						if (remindAt != null) {
							try {
								long remindTs = Long.parseLong(remindAt);
								if (remindTs > 0) {
									Object[] nodeInfo = (Object[]) nodeStack.get(nodeStack.size() - 1);
									String nodeText = plainNodeText((String) nodeInfo[1]);
									if (nodeText.length() == 0 || "bin".equalsIgnoreCase(nodeText)) {
										return;
									}
									String remindType = (String) nodeInfo[2];
									int duration = ((Integer) nodeInfo[3]).intValue();
									int jinji = ((Integer) nodeInfo[4]).intValue();
									TodoistCycleMapper.Cycle cycle = TodoistCycleMapper.fromNodeAttributes(remindType,
											((Integer) nodeInfo[5]).intValue(), ((Integer) nodeInfo[6]).intValue(),
											((Integer) nodeInfo[7]).intValue(), ((Integer) nodeInfo[8]).intValue(),
											((Integer) nodeInfo[9]).intValue(), (String) nodeInfo[10]);
									final TodoistReminderRecord record = new TodoistReminderRecord(file, (String) nodeInfo[0],
											nodeText, remindTs, cycle.interval, cycle.periodUnit(), cycle.recurring,
											duration, jinji, cycle.remindType, cycle.weekDays);
									final String linkedTaskId = (String) nodeInfo[11];
									if (linkedTaskId != null && linkedTaskId.length() > 0) {
										record.linkedTaskId = linkedTaskId;
									}
									record.localModified = parseLong((String) nodeInfo[12]);
									record.syncedLocal = parseLong((String) nodeInfo[13]);
									record.syncedRemote = parseLong((String) nodeInfo[14]);
									record.userPlaced = "true".equals(nodeInfo[15]);
									reminders.add(record);
								}
							}
							catch (Exception e) {
							}
						}
					}
				}

				public void endElement(String uri, String localName, String qName) {
					if ("node".equals(qName) && !nodeStack.isEmpty()) {
						nodeStack.remove(nodeStack.size() - 1);
					}
				}
			});
		}
		catch (Exception e) {
			if (file != null) {
				unreadableMapNames.add(file.getName());
			}
			LogUtils.warn("Todoist: failed to scan " + file.getPath(), e);
		}
		return reminders;
	}

	/** UTF-16 surrogate character references are illegal in XML and abort the whole map. */
	static InputStream sanitizedStream(final File file) throws IOException {
		final FileInputStream in = new FileInputStream(file);
		try {
			final ByteArrayOutputStream buf = new ByteArrayOutputStream();
			final byte[] chunk = new byte[8192];
			int n;
			while ((n = in.read(chunk)) >= 0) {
				buf.write(chunk, 0, n);
			}
			final String xml = new String(buf.toByteArray(), "UTF-8");
			final String cleaned = xml.replaceAll("(?i)&#xd[8-9a-f][0-9a-f]{2};", "");
			return new ByteArrayInputStream(cleaned.getBytes("UTF-8"));
		}
		finally {
			in.close();
		}
	}

	private void collectMindmapFiles(File dir, List out) {
		if (dir == null || !dir.exists() || !dir.isDirectory()) {
			return;
		}
		File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (int i = 0; i < children.length; i++) {
			File file = children[i];
			if (file.isDirectory()) {
				if (!file.isHidden() && !file.getName().startsWith(".")) {
					collectMindmapFiles(file, out);
				}
			}
			else {
				String lower = file.getName().toLowerCase();
				if (lower.endsWith(".mm") && !file.getName().startsWith("~") && file.getName().indexOf("\u51b2\u7a81\u526f\u672c") < 0) {
					out.add(file);
				}
			}
		}
	}

	private static long parseLong(String value) {
		if (value == null) {
			return 0L;
		}
		try {
			return Long.parseLong(value.trim());
		}
		catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static int parseInt(String value, int defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static String plainNodeText(String raw) {
		if (raw == null) {
			return "";
		}
		String text = raw.trim();
		if (text.length() == 0) {
			return "";
		}
		try {
			return HtmlUtils.htmlToPlain(text).trim();
		}
		catch (Exception e) {
			return text;
		}
	}
}
