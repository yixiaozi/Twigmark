package org.freeplane.view.swing.features.time.mindmapmode;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Scans workspace mind maps for reminder entries.
 */
final class ReminderWorkspaceScanHelper {

	private ReminderWorkspaceScanHelper() {
	}

	static List collectAllMindmapFiles() {
		final Set roots = new HashSet();
		final File[] scanRoots = MindMapDataRootResolver.getScanRoots();
		for (int i = 0; i < scanRoots.length; i++) {
			if (scanRoots[i] != null && scanRoots[i].exists() && scanRoots[i].isDirectory()) {
				roots.add(scanRoots[i]);
			}
		}
		final List files = new ArrayList();
		for (final Object root : normalizeRoots(roots)) {
			collectMindmapFiles((File) root, files);
		}
		return files;
	}

	/**
	 * Walk an open {@link MapModel} so unsaved reminder creates/edits appear in
	 * calendar / timeline without requiring a disk save first.
	 */
	static List scanRemindersFromOpenMap(final MapModel map, final File file) {
		final List reminders = new ArrayList();
		if (map == null || map.getRootNode() == null) {
			return reminders;
		}
		collectRemindersFromNode(map.getRootNode(), file != null ? file : map.getFile(), reminders);
		return reminders;
	}

	private static void collectRemindersFromNode(final NodeModel node, final File file, final List reminders) {
		if (node == null) {
			return;
		}
		final ReminderExtension extension = ReminderExtension.getExtension(node);
		if (extension != null && extension.getRemindUserAt() > 0L) {
			final String nodeText = plainNodeText(node);
			if (nodeText.length() > 0 && !"bin".equalsIgnoreCase(nodeText)) {
				final ReminderCycleAttributes.CycleConfig cycleConfig = ReminderCycleAttributes.readFromNode(node);
				final ReminderTaskAttributes.TaskConfig taskConfig = ReminderTaskAttributes.readFromNode(node);
				final String nodeId = node.getID() != null ? node.getID() : node.createID();
				reminders.add(new ReminderCalendarEntry(file, nodeId, nodeText, extension.getRemindUserAt(),
				        cycleConfig.isRecurring(), cycleConfig, taskConfig.taskTime, taskConfig.taskLevel,
				        taskConfig.jinji));
			}
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			collectRemindersFromNode((NodeModel) node.getChildAt(i), file, reminders);
		}
	}

	private static String plainNodeText(final NodeModel node) {
		// Use raw node text — getPlainTextContent applies ReminderTextTransformer and
		// injects "7月17日 13:00 [每2天] 90" prefixes that clutter calendar labels.
		if (node == null || node.getText() == null) {
			return "";
		}
		return HtmlUtils.htmlToPlain(node.getText()).replaceAll("\\s+", " ").trim();
	}

	static List scanRemindersFromFile(final File file) {
		final List reminders = new ArrayList();
		if (file == null || !file.isFile() || !file.getName().toLowerCase().endsWith(".mm")) {
			return reminders;
		}
		try {
			final SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(false);
			final SAXParser saxParser = factory.newSAXParser();
			saxParser.parse(file, new DefaultHandler() {
				private final List nodeStack = new ArrayList();

				public void startElement(final String uri, final String localName, final String qName,
						final Attributes attributes) {
					if ("node".equals(qName)) {
						final String id = attributes.getValue("ID");
						final String text = attributes.getValue("TEXT");
						final ReminderCycleAttributes.CycleConfig cycleConfig = ReminderCycleAttributes
								.readFromSaxAttributes(attributes);
						final ReminderTaskAttributes.TaskConfig taskConfig = ReminderTaskAttributes
								.readFromSaxAttributes(attributes);
						nodeStack.add(new Object[] { id, text == null ? "" : text, cycleConfig, taskConfig });
					}
					else if ("Parameters".equals(qName) && !nodeStack.isEmpty()) {
						final String remindAt = attributes.getValue("REMINDUSERAT");
						if (remindAt != null) {
							try {
								final long remindTs = Long.parseLong(remindAt);
								if (remindTs > 0) {
									final Object[] nodeInfo = (Object[]) nodeStack.get(nodeStack.size() - 1);
									final String nodeText = nodeInfo[1] == null ? "" : ((String) nodeInfo[1]).trim();
									final ReminderCycleAttributes.CycleConfig cycleConfig = (ReminderCycleAttributes.CycleConfig) nodeInfo[2];
									final ReminderTaskAttributes.TaskConfig taskConfig = (ReminderTaskAttributes.TaskConfig) nodeInfo[3];
									if (nodeText.length() > 0 && !"bin".equalsIgnoreCase(nodeText)) {
										final boolean recurring = cycleConfig.isRecurring();
										reminders.add(new ReminderCalendarEntry(file, (String) nodeInfo[0], nodeText,
												remindTs, recurring, cycleConfig, taskConfig.taskTime,
												taskConfig.taskLevel, taskConfig.jinji));
									}
								}
							}
							catch (Exception e) {
							}
						}
					}
				}

				public void endElement(final String uri, final String localName, final String qName) {
					if ("node".equals(qName) && !nodeStack.isEmpty()) {
						nodeStack.remove(nodeStack.size() - 1);
					}
				}
			});
		}
		catch (Exception e) {
			LogUtils.warn(e);
		}
		return reminders;
	}

	static List buildTimelineOccurrences(final List entries, final long rangeStart, final long rangeEnd) {
		final List result = new ArrayList();
		for (int i = 0; i < entries.size(); i++) {
			final ReminderCalendarEntry entry = (ReminderCalendarEntry) entries.get(i);
			final List occurrences = ReminderCalendarPanel.enumerateOccurrences(entry, rangeStart, rangeEnd);
			for (int j = 0; j < occurrences.size(); j++) {
				final long occurrenceAt = ((Long) occurrences.get(j)).longValue();
				result.add(new TimelineOccurrence(entry, occurrenceAt));
			}
		}
		Collections.sort(result, new Comparator() {
			public int compare(final Object o1, final Object o2) {
				final TimelineOccurrence a = (TimelineOccurrence) o1;
				final TimelineOccurrence b = (TimelineOccurrence) o2;
				final int byTime = Long.compare(a.occurrenceAt, b.occurrenceAt);
				if (byTime != 0) {
					return byTime;
				}
				final String ta = a.entry.nodeText == null ? "" : a.entry.nodeText;
				final String tb = b.entry.nodeText == null ? "" : b.entry.nodeText;
				return ta.compareTo(tb);
			}
		});
		return result;
	}

	private static List normalizeRoots(final Set roots) {
		final List normalizedRoots = new ArrayList();
		for (final Object root : roots) {
			final File file = (File) root;
			if (file.exists()) {
				try {
					normalizedRoots.add(file.getCanonicalFile());
				}
				catch (Exception e) {
					normalizedRoots.add(file.getAbsoluteFile());
				}
			}
		}
		return normalizedRoots;
	}

	private static void collectMindmapFiles(final File directory, final List files) {
		if (!directory.exists() || !directory.isDirectory()) {
			return;
		}
		final File[] children = directory.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			if (child.getName().startsWith(".")) {
				continue;
			}
			if (child.isDirectory()) {
				collectMindmapFiles(child, files);
			}
			else if (child.getName().toLowerCase().endsWith(".mm")) {
				files.add(child);
			}
		}
	}

	static final class TimelineOccurrence {
		final ReminderCalendarEntry entry;
		final long occurrenceAt;

		TimelineOccurrence(final ReminderCalendarEntry entry, final long occurrenceAt) {
			this.entry = entry;
			this.occurrenceAt = occurrenceAt;
		}
	}
}
