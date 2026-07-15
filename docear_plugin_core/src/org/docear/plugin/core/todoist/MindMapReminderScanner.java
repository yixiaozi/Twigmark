package org.docear.plugin.core.todoist;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

final class MindMapReminderScanner {

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
				continue;
			}
			reminders.addAll(scanFile(file));
		}
		return reminders;
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

	private List scanFile(final File file) {
		final List reminders = new ArrayList();
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(false);
			SAXParser saxParser = factory.newSAXParser();
			saxParser.parse(file, new DefaultHandler() {
				private final List nodeStack = new ArrayList();

				public void startElement(String uri, String localName, String qName, Attributes attributes) {
					if ("node".equals(qName)) {
						String id = attributes.getValue("ID");
						String text = attributes.getValue("TEXT");
						String remindType = attributes.getValue("REMINDERTYPE");
						int taskTime = parseInt(attributes.getValue("TASKTIME"), 0);
						int jinji = parseInt(attributes.getValue("JINJI"), 0);
						nodeStack.add(new Object[] { id, text == null ? "" : text, remindType,
								Integer.valueOf(taskTime), Integer.valueOf(jinji) });
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
									String remindType = nodeInfo.length > 2 ? (String) nodeInfo[2] : null;
									int period = parseInt(attributes.getValue("PERIOD"), 1);
									String unit = attributes.getValue("UNIT");
									if (unit == null || unit.trim().length() == 0) {
										unit = "DAY";
									}
									boolean recurring = remindType != null && !"onetime".equalsIgnoreCase(remindType);
									int duration = nodeInfo.length > 3 ? ((Integer) nodeInfo[3]).intValue() : 0;
									int jinji = nodeInfo.length > 4 ? ((Integer) nodeInfo[4]).intValue() : 0;
									reminders.add(new TodoistReminderRecord(file, (String) nodeInfo[0], nodeText,
											remindTs, period, unit, recurring, duration, jinji));
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
			LogUtils.warn("Todoist: failed to scan " + file.getPath(), e);
		}
		return reminders;
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
