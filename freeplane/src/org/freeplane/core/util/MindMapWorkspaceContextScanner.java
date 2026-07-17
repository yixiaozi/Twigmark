package org.freeplane.core.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 无 UI 依赖的工作区扫描：从全部 .mm 文件中收集提醒与待办（与侧栏标签页逻辑一致）。
 */
public final class MindMapWorkspaceContextScanner {

	public static final class ReminderItem {
		public final File mapFile;
		public final String nodeId;
		public final String nodeText;
		public final long remindAt;
		public final boolean recurring;
		public final String remindType;

		public ReminderItem(File mapFile, String nodeId, String nodeText, long remindAt, boolean recurring,
				String remindType) {
			this.mapFile = mapFile;
			this.nodeId = nodeId;
			this.nodeText = nodeText;
			this.remindAt = remindAt;
			this.recurring = recurring;
			this.remindType = remindType;
		}
	}

	public static final class TodoItem {
		public final File mapFile;
		public final String nodeId;
		public final String nodeText;

		public TodoItem(File mapFile, String nodeId, String nodeText) {
			this.mapFile = mapFile;
			this.nodeId = nodeId;
			this.nodeText = nodeText;
		}
	}

	public static final class IconItem {
		public final File mapFile;
		public final String nodeId;
		public final String nodeText;

		public IconItem(File mapFile, String nodeId, String nodeText) {
			this.mapFile = mapFile;
			this.nodeId = nodeId;
			this.nodeText = nodeText;
		}
	}

	public static final class ModifiedItem {
		public final File mapFile;
		public final String nodeId;
		public final String nodeText;
		public final long modifiedAt;

		public ModifiedItem(File mapFile, String nodeId, String nodeText, long modifiedAt) {
			this.mapFile = mapFile;
			this.nodeId = nodeId;
			this.nodeText = nodeText;
			this.modifiedAt = modifiedAt;
		}
	}

	public static final class FileScanResult {
		public final List reminders;
		public final List todos;
		public final List published;
		public final List flags;
		public final List modifiedNodes;

		private FileScanResult(List reminders, List todos, List published, List flags, List modifiedNodes) {
			this.reminders = reminders;
			this.todos = todos;
			this.published = published;
			this.flags = flags;
			this.modifiedNodes = modifiedNodes;
		}
	}

	public static final class WorkspaceScanResult {
		public final List oneTimeReminders;
		public final List recurringReminders;
		public final List todos;

		public WorkspaceScanResult(List oneTimeReminders, List recurringReminders, List todos) {
			this.oneTimeReminders = oneTimeReminders;
			this.recurringReminders = recurringReminders;
			this.todos = todos;
		}
	}

	private static final class CachedFileScan {
		private final long modified;
		private final long length;
		private final FileScanResult result;

		private CachedFileScan(long modified, long length, FileScanResult result) {
			this.modified = modified;
			this.length = length;
			this.result = result;
		}
	}

	private static final String TODO_ICON_NAME = "hourglass";
	private static final String PUBLISH_ICON_NAME = "internet";
	private static final Map fileCache = new HashMap();

	private MindMapWorkspaceContextScanner() {
	}

	public static WorkspaceScanResult scanAll() {
		final List files = collectMindmapFiles();
		final List oneTimeReminders = new ArrayList();
		final List recurringReminders = new ArrayList();
		final List todos = new ArrayList();
		final Set seenReminderKeys = new HashSet();
		final Set seenTodoKeys = new HashSet();
		for (int i = 0; i < files.size(); i++) {
			File file = (File) files.get(i);
			FileScanResult result = scanFile(file);
			for (int j = 0; j < result.reminders.size(); j++) {
				ReminderItem item = (ReminderItem) result.reminders.get(j);
				String key = itemKey(item);
				if (!seenReminderKeys.add(key)) {
					continue;
				}
				if (item.recurring) {
					recurringReminders.add(item);
				}
				else {
					oneTimeReminders.add(item);
				}
			}
			for (int j = 0; j < result.todos.size(); j++) {
				TodoItem item = (TodoItem) result.todos.get(j);
				String key = itemKey(item);
				if (seenTodoKeys.add(key)) {
					todos.add(item);
				}
			}
		}
		sortReminders(oneTimeReminders);
		sortReminders(recurringReminders);
		sortTodos(todos);
		return new WorkspaceScanResult(oneTimeReminders, recurringReminders, todos);
	}

	public static List scanAllReminders() {
		return scanReminders(false, false);
	}

	public static List scanOneTimeReminders() {
		return scanReminders(true, false);
	}

	public static List scanRecurringReminders() {
		return scanReminders(false, true);
	}

	public static List scanAllTodos() {
		final List files = collectMindmapFiles();
		final List todos = new ArrayList();
		final Set seenKeys = new HashSet();
		for (int i = 0; i < files.size(); i++) {
			File file = (File) files.get(i);
			FileScanResult result = scanFile(file);
			for (int j = 0; j < result.todos.size(); j++) {
				TodoItem item = (TodoItem) result.todos.get(j);
				String key = itemKey(item);
				if (seenKeys.add(key)) {
					todos.add(item);
				}
			}
		}
		sortTodos(todos);
		return todos;
	}

	public static List scanPublishedItems() {
		final List files = collectMindmapFiles();
		final List published = new ArrayList();
		final Set seenKeys = new HashSet();
		for (int i = 0; i < files.size(); i++) {
			File file = (File) files.get(i);
			FileScanResult result = scanFile(file);
			for (int j = 0; j < result.published.size(); j++) {
				IconItem item = (IconItem) result.published.get(j);
				String key = iconItemKey(item);
				if (seenKeys.add(key)) {
					published.add(item);
				}
			}
		}
		sortIconItems(published);
		return published;
	}

	/** Nodes with flag / flag-* icons (matches left「红旗」tab). */
	public static List scanFlagItems() {
		final List files = collectMindmapFiles();
		final List flags = new ArrayList();
		final Set seenKeys = new HashSet();
		for (int i = 0; i < files.size(); i++) {
			File file = (File) files.get(i);
			FileScanResult result = scanFile(file);
			for (int j = 0; j < result.flags.size(); j++) {
				IconItem item = (IconItem) result.flags.get(j);
				String key = iconItemKey(item);
				if (seenKeys.add(key)) {
					flags.add(item);
				}
			}
		}
		sortIconItems(flags);
		return flags;
	}

	public static List scanRecentlyModified(final int limit) {
		final List files = collectMindmapFiles();
		final List modified = new ArrayList();
		final Set seenKeys = new HashSet();
		for (int i = 0; i < files.size(); i++) {
			File file = (File) files.get(i);
			FileScanResult result = scanFile(file);
			for (int j = 0; j < result.modifiedNodes.size(); j++) {
				ModifiedItem item = (ModifiedItem) result.modifiedNodes.get(j);
				String key = modifiedItemKey(item);
				if (seenKeys.add(key)) {
					modified.add(item);
				}
			}
		}
		Collections.sort(modified, new Comparator() {
			public int compare(Object o1, Object o2) {
				ModifiedItem a = (ModifiedItem) o1;
				ModifiedItem b = (ModifiedItem) o2;
				return a.modifiedAt < b.modifiedAt ? 1 : (a.modifiedAt == b.modifiedAt ? 0 : -1);
			}
		});
		if (limit > 0 && modified.size() > limit) {
			return new ArrayList(modified.subList(0, limit));
		}
		return modified;
	}

	private static List scanReminders(final boolean oneTimeOnly, final boolean recurringOnly) {
		final List files = collectMindmapFiles();
		final List reminders = new ArrayList();
		final Set seenKeys = new HashSet();
		for (int i = 0; i < files.size(); i++) {
			File file = (File) files.get(i);
			FileScanResult result = scanFile(file);
			for (int j = 0; j < result.reminders.size(); j++) {
				ReminderItem item = (ReminderItem) result.reminders.get(j);
				if (oneTimeOnly && item.recurring) {
					continue;
				}
				if (recurringOnly && !item.recurring) {
					continue;
				}
				String key = itemKey(item);
				if (seenKeys.add(key)) {
					reminders.add(item);
				}
			}
		}
		sortReminders(reminders);
		return reminders;
	}

	private static String itemKey(ReminderItem item) {
		return canonicalPath(item.mapFile) + "|" + item.nodeId + "|" + item.remindAt;
	}

	private static String itemKey(TodoItem item) {
		return canonicalPath(item.mapFile) + "|" + item.nodeId;
	}

	private static String iconItemKey(IconItem item) {
		return canonicalPath(item.mapFile) + "|" + item.nodeId;
	}

	private static String modifiedItemKey(ModifiedItem item) {
		return canonicalPath(item.mapFile) + "|" + item.nodeId + "|" + item.modifiedAt;
	}

	private static boolean isNumericOnlyText(final String text) {
		if (text == null || text.length() == 0) {
			return false;
		}
		for (int i = 0; i < text.length(); i++) {
			if (!Character.isDigit(text.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static String canonicalPath(File file) {
		if (file == null) {
			return "";
		}
		try {
			return file.getCanonicalPath();
		}
		catch (Exception e) {
			return file.getAbsolutePath();
		}
	}

	private static void sortReminders(List reminders) {
		Collections.sort(reminders, new Comparator() {
			public int compare(Object o1, Object o2) {
				ReminderItem a = (ReminderItem) o1;
				ReminderItem b = (ReminderItem) o2;
				return a.remindAt < b.remindAt ? -1 : (a.remindAt == b.remindAt ? 0 : 1);
			}
		});
	}

	private static void sortTodos(List todos) {
		Collections.sort(todos, new Comparator() {
			public int compare(Object o1, Object o2) {
				TodoItem a = (TodoItem) o1;
				TodoItem b = (TodoItem) o2;
				int pathCompare = canonicalPath(a.mapFile).compareTo(canonicalPath(b.mapFile));
				if (pathCompare != 0) {
					return pathCompare;
				}
				String textA = a.nodeText == null ? "" : a.nodeText;
				String textB = b.nodeText == null ? "" : b.nodeText;
				return textA.compareTo(textB);
			}
		});
	}

	private static void sortIconItems(List items) {
		Collections.sort(items, new Comparator() {
			public int compare(Object o1, Object o2) {
				IconItem a = (IconItem) o1;
				IconItem b = (IconItem) o2;
				int pathCompare = canonicalPath(a.mapFile).compareTo(canonicalPath(b.mapFile));
				if (pathCompare != 0) {
					return pathCompare;
				}
				String textA = a.nodeText == null ? "" : a.nodeText;
				String textB = b.nodeText == null ? "" : b.nodeText;
				return textA.compareTo(textB);
			}
		});
	}

	private static List collectMindmapFiles() {
		final List files = new ArrayList();
		MindMapDataRootResolver.collectMindmapFiles(files);
		for (int i = files.size() - 1; i >= 0; i--) {
			if (!isValidMindmapFile((File) files.get(i))) {
				files.remove(i);
			}
		}
		return files;
	}

	private static boolean isValidMindmapFile(File file) {
		if (file == null || !file.isFile() || !file.exists()) {
			return false;
		}
		String name = file.getName();
		if (name.startsWith("~") || name.contains("\u51b2\u7a81\u526f\u672c")) {
			return false;
		}
		return name.toLowerCase().endsWith(".mm");
	}

	private static FileScanResult scanFile(final File file) {
		if (!isValidMindmapFile(file)) {
			return new FileScanResult(Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST,
					Collections.EMPTY_LIST, Collections.EMPTY_LIST);
		}
		long modified = file.lastModified();
		long length = file.length();
		String cacheKey = file.getAbsolutePath();
		synchronized (fileCache) {
			CachedFileScan cached = (CachedFileScan) fileCache.get(cacheKey);
			if (cached != null && cached.modified == modified && cached.length == length) {
				return cached.result;
			}
		}

		final List reminders = new ArrayList();
		final List todos = new ArrayList();
		final List published = new ArrayList();
		final List flags = new ArrayList();
		final List modifiedNodes = new ArrayList();
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
						nodeStack.add(new String[] { id, text == null ? "" : text, remindType });
						String modifiedStr = attributes.getValue("MODIFIED");
						if (id != null && modifiedStr != null && text != null) {
							try {
								long modifiedAt = Long.parseLong(modifiedStr);
								String nodeText = normalizeNodeText(text);
								if (nodeText.length() > 0 && !"bin".equalsIgnoreCase(nodeText)
										&& !isNumericOnlyText(nodeText)) {
									modifiedNodes.add(new ModifiedItem(file, id, nodeText, modifiedAt));
								}
							}
							catch (Exception e) {
							}
						}
					}
					else if ("icon".equals(qName) && !nodeStack.isEmpty()) {
						String iconName = attributes.getValue("BUILTIN");
						if (iconName != null) {
							String[] nodeInfo = (String[]) nodeStack.get(nodeStack.size() - 1);
							String nodeText = normalizeNodeText(nodeInfo[1]);
							if (nodeText.length() > 0 && !"bin".equalsIgnoreCase(nodeText)) {
								if (TODO_ICON_NAME.equalsIgnoreCase(iconName)) {
									todos.add(new TodoItem(file, nodeInfo[0], nodeText));
								}
								else if (PUBLISH_ICON_NAME.equalsIgnoreCase(iconName)) {
									published.add(new IconItem(file, nodeInfo[0], nodeText));
								}
								else if (isFlagIconName(iconName)) {
									flags.add(new IconItem(file, nodeInfo[0], nodeText));
								}
							}
						}
					}
					else if ("Parameters".equals(qName) && !nodeStack.isEmpty()) {
						String remindAt = attributes.getValue("REMINDUSERAT");
						if (remindAt != null) {
							try {
								long remindTs = Long.parseLong(remindAt);
								if (remindTs > 0) {
									String[] nodeInfo = (String[]) nodeStack.get(nodeStack.size() - 1);
									String nodeText = normalizeNodeText(nodeInfo[1]);
									String remindType = nodeInfo.length > 2 ? nodeInfo[2] : null;
									boolean isRecurring = remindType != null
											&& !"onetime".equalsIgnoreCase(remindType);
									if (nodeText.length() > 0 && !"bin".equalsIgnoreCase(nodeText)) {
										reminders.add(new ReminderItem(file, nodeInfo[0], nodeText, remindTs,
												isRecurring, remindType));
									}
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
			LogUtils.warn("Failed to scan mind map for workspace context: " + file.getAbsolutePath() + ", "
					+ e.getMessage());
		}

		FileScanResult result = new FileScanResult(reminders, todos, published, flags, modifiedNodes);
		synchronized (fileCache) {
			fileCache.put(cacheKey, new CachedFileScan(modified, length, result));
		}
		return result;
	}

	private static boolean isFlagIconName(final String iconName) {
		if (iconName == null || iconName.length() == 0) {
			return false;
		}
		final String lower = iconName.toLowerCase();
		return "flag".equals(lower) || lower.startsWith("flag-");
	}

	private static String normalizeNodeText(String text) {
		if (text == null) {
			return "";
		}
		return HtmlUtils.removeHtmlTagsFromString(text).replaceAll("\\s+", " ").trim();
	}
}
