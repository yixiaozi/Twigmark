package org.docear.plugin.core.todoist;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Writes unlinked Todoist tasks into the import mind map <strong>without opening it</strong>
 * in the UI. Used when {@code todolist.mm} (or configured import target) is closed.
 */
final class TodoistSilentImportWriter {
	private static final String TODOIST_BRANCH = "Todoist";
	private static final String NO_SECTION_KEY = "__no_section__";
	private static int idCounter = 1;

	private TodoistSilentImportWriter() {
	}

	static TodoistImportResult write(final File targetFile, final List tasks, final Map projectNames,
			final Map sectionNames, final boolean preserveLinkedInboxCopies) {
		return write(targetFile, tasks, projectNames, sectionNames, preserveLinkedInboxCopies, null);
	}

	static TodoistImportResult write(final File targetFile, final List tasks, final Map projectNames,
			final Map sectionNames, final boolean preserveLinkedInboxCopies, final TodoistTaskAnchorIndex anchors) {
		final TodoistImportResult result = new TodoistImportResult();
		result.targetFile = targetFile == null ? null : targetFile.getAbsolutePath();
		if (targetFile == null) {
			result.failed = 1;
			result.errorMessage = "Import target file is not configured.";
			result.addFailed(result.errorMessage);
			return result;
		}
		if (TodoistNodeLocator.findOpenMap(targetFile) != null) {
			result.failed = 1;
			result.errorMessage = "Import map opened during silent write.";
			result.addFailed(result.errorMessage);
			return result;
		}
		try {
			final File parent = targetFile.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			TodoistSilentMmUpdater.recoverInterruptedWrite(targetFile);
			final Document doc = targetFile.isFile() ? TodoistSilentMmUpdater.loadDocument(targetFile)
					: newEmptyMapDocument(targetFile);
			final Element root = doc.getDocumentElement();
			if (root == null || !"map".equals(root.getTagName())) {
				throw new Exception("Invalid mind map root in " + targetFile.getAbsolutePath());
			}
			final Element mapRootNode = firstChildElement(root, "node");
			if (mapRootNode == null) {
				throw new Exception("Mind map has no root node: " + targetFile.getAbsolutePath());
			}
			final Set usedIds = collectIds(mapRootNode);
			final Map existingByTaskId = indexTaskNodes(mapRootNode);
			final TodoistImportLayout layout = TodoistImportLayout.build(tasks, projectNames, sectionNames);
			final List projectKeys = layout.projectKeys();
			for (int p = 0; p < projectKeys.size(); p++) {
				final String projectKey = (String) projectKeys.get(p);
				final String projectName = layout.projectLabel(projectKey);
				final Element projectNode = ensureSection(doc, mapRootNode, projectName, usedIds);
				final List sectionKeys = layout.sectionKeys(projectKey);
				for (int s = 0; s < sectionKeys.size(); s++) {
					final String sectionKey = (String) sectionKeys.get(s);
					final String sectionName = layout.sectionLabel(sectionKey);
					final Element sectionNode = ensureSection(doc, projectNode, sectionName, usedIds);
					final List roots = layout.roots(sectionKey);
					final String linePrefix = projectName + " / " + sectionName;
					for (int t = 0; t < roots.size(); t++) {
						placeTask(doc, sectionNode, (TodoistImportTask) roots.get(t), layout, existingByTaskId,
								anchors, usedIds, result, linePrefix);
					}
				}
			}
			removeStaleTaskNodes(existingByTaskId, result, preserveLinkedInboxCopies);
			TodoistMindMapWriter.syncDoneIconsOnMovedNodes(tasks, anchors);
			pruneEmptyRootFolders(mapRootNode);
			final Element legacyBranch = findChildByText(mapRootNode, TODOIST_BRANCH);
			if (legacyBranch != null && !hasTaskId(legacyBranch)) {
				pruneEmptyFolders(legacyBranch);
				if (childNodes(legacyBranch).isEmpty()) {
					mapRootNode.removeChild(legacyBranch);
				}
			}
			if (!TodoistSilentMmUpdater.writeDocumentAtomically(targetFile, doc)) {
				result.failed++;
				result.errorMessage = "Could not save silent import map: " + targetFile.getAbsolutePath();
				result.addFailed(result.errorMessage);
			}
		}
		catch (Exception e) {
			result.failed++;
			result.errorMessage = e.getMessage();
			result.addFailed(e.getMessage());
			LogUtils.warn("Todoist silent import write failed", e);
		}
		return result;
	}

	private static Document newEmptyMapDocument(final File targetFile) throws Exception {
		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		final Element map = doc.createElement("map");
		map.setAttribute("version", "docear 1.1");
		doc.appendChild(map);
		final Element root = doc.createElement("node");
		String title = targetFile.getName();
		if (title.toLowerCase().endsWith(".mm")) {
			title = title.substring(0, title.length() - 3);
		}
		root.setAttribute("TEXT", title);
		root.setAttribute("ID", newId(new HashSet()));
		root.setAttribute("FOLDED", "false");
		map.appendChild(root);
		return doc;
	}

	private static Map indexTaskNodes(final Element todoistBranch) {
		final Map byId = new HashMap();
		collectTaskNodes(todoistBranch, byId);
		return byId;
	}

	private static void collectTaskNodes(final Element node, final Map byId) {
		if (node == null) {
			return;
		}
		final String taskId = node.getAttribute(TodoistNodeMetaIo.XML_TASK_ID);
		if (taskId != null && taskId.length() > 0) {
			byId.put(taskId, node);
		}
		final NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			final Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE && "node".equals(child.getNodeName())) {
				collectTaskNodes((Element) child, byId);
			}
		}
	}

	private static void removeStaleTaskNodes(final Map leftoverByTaskId, final TodoistImportResult result,
			final boolean preserveLinkedInboxCopies) {
		if (leftoverByTaskId.isEmpty()) {
			return;
		}
		final TodoistMappingStore store = preserveLinkedInboxCopies ? TodoistMappingStore.get() : null;
		for (Iterator it = leftoverByTaskId.entrySet().iterator(); it.hasNext();) {
			final Map.Entry entry = (Map.Entry) it.next();
			final String taskId = (String) entry.getKey();
			final Element stale = (Element) entry.getValue();
			final String text = plainText(stale);
			if ("true".equalsIgnoreCase(stale.getAttribute(TodoistNodeMetaIo.XML_USER_PLACED))) {
				if (ensureDoneIcon(stale.getOwnerDocument(), stale)) {
					result.addUpdated("[checked] " + text);
				}
				continue;
			}
			if (store != null && store.isLinkedToSourceMap(taskId)) {
				continue;
			}
			if (stale.getParentNode() != null) {
				stale.getParentNode().removeChild(stale);
				result.addUpdated("[removed closed task] " + text);
			}
		}
	}

	static boolean ensureDoneIcon(final Document doc, final Element node) {
		if (doc == null || node == null || hasDoneIcon(node)) {
			return false;
		}
		final Element icon = doc.createElement("icon");
		icon.setAttribute("BUILTIN", TodoistMindMapWriter.DONE_ICON);
		node.appendChild(icon);
		return true;
	}

	static boolean removeDoneIcon(final Element node) {
		if (node == null) {
			return false;
		}
		boolean removed = false;
		final NodeList children = node.getChildNodes();
		for (int i = children.getLength() - 1; i >= 0; i--) {
			final Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE && "icon".equals(child.getNodeName())
					&& TodoistMindMapWriter.DONE_ICON.equals(((Element) child).getAttribute("BUILTIN"))) {
				node.removeChild(child);
				removed = true;
			}
		}
		return removed;
	}

	private static boolean hasDoneIcon(final Element node) {
		final NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			final Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE && "icon".equals(child.getNodeName())
					&& TodoistMindMapWriter.DONE_ICON.equals(((Element) child).getAttribute("BUILTIN"))) {
				return true;
			}
		}
		return false;
	}

	private static void pruneEmptyFolders(final Element todoistBranch) {
		final List projects = childNodes(todoistBranch);
		for (int p = projects.size() - 1; p >= 0; p--) {
			final Element project = (Element) projects.get(p);
			if (hasTaskId(project)) {
				continue;
			}
			final List sections = childNodes(project);
			for (int s = sections.size() - 1; s >= 0; s--) {
				final Element section = (Element) sections.get(s);
				if (!hasTaskId(section) && childNodes(section).isEmpty()) {
					project.removeChild(section);
				}
			}
			if (childNodes(project).isEmpty()) {
				todoistBranch.removeChild(project);
			}
		}
	}

	private static boolean applyTaskElement(final Document doc, final Element node, final TodoistImportTask task) {
		boolean changed = "true".equalsIgnoreCase(node.getAttribute(TodoistNodeMetaIo.XML_USER_PLACED))
				&& removeDoneIcon(node);
		final TodoistContentParser parsed = TodoistContentParser.parse(task.content);
		final String desiredText = parsed.nodeText;
		final String currentText = plainText(node);
		if (!desiredText.equals(currentText)) {
			final long localModified = parseMillis(node.getAttribute("MODIFIED"));
			final long syncedLocal = parseMillis(node.getAttribute(TodoistNodeMetaIo.XML_SYNCED_LOCAL));
			final long syncedRemote = parseMillis(node.getAttribute(TodoistNodeMetaIo.XML_SYNCED_REMOTE));
			if (TodoistSyncService.localTitleWins(localModified, task.updatedAtMillis, syncedLocal, syncedRemote)) {
				try {
					final long remote = TodoistSyncService.pushLinkedTitle(task.id, currentText);
					if (remote > 0) {
						node.setAttribute(TodoistNodeMetaIo.XML_SYNCED_REMOTE, Long.toString(remote));
					}
					if (localModified > 0) {
						node.setAttribute(TodoistNodeMetaIo.XML_SYNCED_LOCAL, Long.toString(localModified));
					}
					changed = true;
				}
				catch (Exception e) {
					LogUtils.warn("Todoist: could not push newer mind-map title for " + task.id, e);
				}
			}
			else if (TodoistSyncClock.choose(localModified, task.updatedAtMillis, syncedLocal,
					syncedRemote) == TodoistSyncClock.PULL) {
				node.setAttribute("TEXT", desiredText);
				changed = true;
			}
		}
		if (!task.id.equals(node.getAttribute(TodoistNodeMetaIo.XML_TASK_ID))) {
			node.setAttribute(TodoistNodeMetaIo.XML_TASK_ID, task.id);
			changed = true;
		}
		if (ensureTodoistMark(node, task)) {
			changed = true;
		}
		if (parsed.linkUri != null && parsed.linkUri.length() > 0) {
			if (!parsed.linkUri.equals(node.getAttribute("LINK"))) {
				node.setAttribute("LINK", parsed.linkUri);
				changed = true;
			}
		}
		final TodoistCycleMapper.Cycle cycle = TodoistCycleMapper.fromTodoistDue(task.dueString, task.recurring);
		final String hash = Integer.toString((task.content + "|" + task.dueAtMillis + "|" + cycle.recurring + "|"
				+ cycle.interval + "|" + cycle.periodUnit() + "|" + cycle.remindType + "|" + cycle.weekDays + "|"
				+ task.durationMinutes + "|" + TodoistPriority.toTodoistApi(task.priority)).hashCode());
		if (!hash.equals(node.getAttribute(TodoistNodeMetaIo.XML_CONTENT_HASH))) {
			node.setAttribute(TodoistNodeMetaIo.XML_CONTENT_HASH, hash);
			changed = true;
		}
		final int jinji = TodoistPriority.toJinji(task.priority, 0);
		if (task.durationMinutes > 0) {
			if (!Integer.toString(task.durationMinutes).equals(node.getAttribute("TASKTIME"))) {
				node.setAttribute("TASKTIME", Integer.toString(task.durationMinutes));
				changed = true;
			}
		}
		if (jinji > 0) {
			if (!Integer.toString(jinji).equals(node.getAttribute("JINJI"))) {
				node.setAttribute("JINJI", Integer.toString(jinji));
				changed = true;
			}
		}
		final boolean cycleTrusted = !TodoistCycleMapper.isWeakRecurringFallback(task.dueString, task.recurring);
		if (cycleTrusted && applyCycleAttrs(node, cycle)) {
			changed = true;
		}
		if (task.dueAtMillis > 0) {
			if (ensureReminder(doc, node, task.dueAtMillis, cycle, cycleTrusted)) {
				changed = true;
			}
		}
		// Map-protect: never remove local reminder when Todoist has no due.
		if (applyNote(doc, node, task.description)) {
			changed = true;
		}
		return changed;
	}

	private static long parseMillis(final String value) {
		if (value == null || value.trim().length() == 0) {
			return 0L;
		}
		try {
			return Long.parseLong(value.trim());
		}
		catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static boolean applyCycleAttrs(final Element node, final TodoistCycleMapper.Cycle cycle) {
		boolean changed = false;
		if (!cycle.recurring || "onetime".equalsIgnoreCase(cycle.remindType)) {
			final String[] attrs = new String[] { "REMINDERTYPE", "RHOUR", "RDAYS", "RWEEK", "RMONTH", "RYEAR", "RWEEKS",
					"EBSTRING" };
			for (int i = 0; i < attrs.length; i++) {
				if (node.hasAttribute(attrs[i])) {
					node.removeAttribute(attrs[i]);
					changed = true;
				}
			}
			return changed;
		}
		if (!cycle.remindType.equals(node.getAttribute("REMINDERTYPE"))) {
			node.setAttribute("REMINDERTYPE", cycle.remindType);
			changed = true;
		}
		final String intervalAttr = intervalAttr(cycle.remindType);
		final String[] all = new String[] { "RHOUR", "RDAYS", "RWEEK", "RMONTH", "RYEAR" };
		for (int i = 0; i < all.length; i++) {
			if (intervalAttr != null && intervalAttr.equals(all[i])) {
				final String value = Integer.toString(cycle.interval);
				if (!value.equals(node.getAttribute(all[i]))) {
					node.setAttribute(all[i], value);
					changed = true;
				}
			}
			else if (node.hasAttribute(all[i])) {
				node.removeAttribute(all[i]);
				changed = true;
			}
		}
		if ("week".equalsIgnoreCase(cycle.remindType)) {
			final String days = cycle.weekDays.length() > 0 ? cycle.weekDays : "1";
			if (!days.equals(node.getAttribute("RWEEKS"))) {
				node.setAttribute("RWEEKS", days);
				changed = true;
			}
		}
		else if (node.hasAttribute("RWEEKS")) {
			node.removeAttribute("RWEEKS");
			changed = true;
		}
		return changed;
	}

	private static String intervalAttr(final String remindType) {
		if ("hour".equalsIgnoreCase(remindType)) {
			return "RHOUR";
		}
		if ("day".equalsIgnoreCase(remindType)) {
			return "RDAYS";
		}
		if ("week".equalsIgnoreCase(remindType)) {
			return "RWEEK";
		}
		if ("month".equalsIgnoreCase(remindType)) {
			return "RMONTH";
		}
		if ("year".equalsIgnoreCase(remindType)) {
			return "RYEAR";
		}
		return null;
	}

	private static boolean ensureReminder(final Document doc, final Element node, final long dueAt,
			final TodoistCycleMapper.Cycle cycle, final boolean cycleTrusted) {
		Element parameters = findReminderParameters(node);
		if (parameters == null) {
			final Element hook = doc.createElement("hook");
			hook.setAttribute("NAME", "plugins/TimeManagementReminder.xml");
			parameters = doc.createElement("Parameters");
			hook.appendChild(parameters);
			node.appendChild(hook);
		}
		boolean changed = false;
		final String due = Long.toString(dueAt);
		if (!due.equals(parameters.getAttribute("REMINDUSERAT"))) {
			parameters.setAttribute("REMINDUSERAT", due);
			changed = true;
		}
		if (cycleTrusted) {
			final String period = Integer.toString(cycle.interval);
			if (!period.equals(parameters.getAttribute("PERIOD"))) {
				parameters.setAttribute("PERIOD", period);
				changed = true;
			}
			final String unit = cycle.periodUnit();
			if (!unit.equalsIgnoreCase(String.valueOf(parameters.getAttribute("UNIT")))) {
				parameters.setAttribute("UNIT", unit);
				changed = true;
			}
		}
		else if (!parameters.hasAttribute("PERIOD") || parameters.getAttribute("PERIOD").length() == 0) {
			parameters.setAttribute("PERIOD", "1");
			parameters.setAttribute("UNIT", "DAY");
			changed = true;
		}
		return changed;
	}

	private static boolean removeReminder(final Element node) {
		boolean removed = false;
		final NodeList children = node.getChildNodes();
		for (int i = children.getLength() - 1; i >= 0; i--) {
			final Node child = children.item(i);
			if (child.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			final Element el = (Element) child;
			if ("hook".equals(el.getTagName())) {
				final String name = el.getAttribute("NAME");
				if (name != null && name.indexOf("TimeManagementReminder") >= 0) {
					node.removeChild(el);
					removed = true;
				}
			}
		}
		return removed;
	}

	private static Element findReminderParameters(final Element node) {
		final NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			final Node child = children.item(i);
			if (child.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			final Element el = (Element) child;
			if (!"hook".equals(el.getTagName())) {
				continue;
			}
			final String name = el.getAttribute("NAME");
			if (name == null || name.indexOf("TimeManagementReminder") < 0) {
				continue;
			}
			final NodeList hookChildren = el.getChildNodes();
			for (int j = 0; j < hookChildren.getLength(); j++) {
				final Node hc = hookChildren.item(j);
				if (hc.getNodeType() == Node.ELEMENT_NODE && "Parameters".equals(hc.getNodeName())) {
					return (Element) hc;
				}
			}
		}
		return null;
	}

	private static boolean applyNote(final Document doc, final Element node, final String description) {
		final String desired = description == null ? "" : description.trim();
		Element rich = findNoteRichContent(node);
		final String existing = rich == null ? "" : plainFromNote(rich);
		if (desired.equals(existing)) {
			return false;
		}
		if (desired.length() == 0) {
			if (rich != null) {
				node.removeChild(rich);
				return true;
			}
			return false;
		}
		if (rich == null) {
			rich = doc.createElement("richcontent");
			rich.setAttribute("TYPE", "NOTE");
			node.appendChild(rich);
		}
		while (rich.hasChildNodes()) {
			rich.removeChild(rich.getFirstChild());
		}
		final Element html = doc.createElement("html");
		final Element head = doc.createElement("head");
		final Element body = doc.createElement("body");
		final Element p = doc.createElement("p");
		p.appendChild(doc.createTextNode(desired));
		body.appendChild(p);
		html.appendChild(head);
		html.appendChild(body);
		rich.appendChild(html);
		return true;
	}

	private static Element findNoteRichContent(final Element node) {
		final NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			final Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE && "richcontent".equals(child.getNodeName())) {
				final Element el = (Element) child;
				if ("NOTE".equals(el.getAttribute("TYPE"))) {
					return el;
				}
			}
		}
		return null;
	}

	private static String plainFromNote(final Element rich) {
		final StringBuilder sb = new StringBuilder();
		appendText(rich, sb);
		return sb.toString().trim();
	}

	private static void appendText(final Node node, final StringBuilder sb) {
		if (node.getNodeType() == Node.TEXT_NODE) {
			sb.append(node.getNodeValue());
			return;
		}
		final NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			appendText(children.item(i), sb);
		}
	}

	private static void placeTask(final Document doc, final Element canonicalParent, final TodoistImportTask task,
			final TodoistImportLayout layout, final Map existingByTaskId, final TodoistTaskAnchorIndex anchors,
			final Set usedIds, final TodoistImportResult result, final String sectionName) {
		result.totalFetched++;
		final String line = "[" + sectionName + "] " + parsedLine(task);
		Element existing = (Element) existingByTaskId.remove(task.id);
		final File anchoredFile = anchors == null ? null : anchors.fileFor(task.id);
		if (existing != null) {
			final boolean userPlaced = "true".equalsIgnoreCase(existing.getAttribute(TodoistNodeMetaIo.XML_USER_PLACED));
			if (!userPlaced && existing.getParentNode() != canonicalParent) {
				existing.getParentNode().removeChild(existing);
				canonicalParent.appendChild(existing);
			}
			if (applyTaskElement(doc, existing, task)) {
				result.addUpdated(line);
			}
			else {
				result.addSkipped(line);
			}
		}
		else if (anchoredFile != null) {
			result.addSkipped(line + " (kept in " + anchoredFile.getName() + ")");
			existing = null;
		}
		else {
			existing = createChildNode(doc, canonicalParent, "", usedIds);
			applyTaskElement(doc, existing, task);
			result.addCreated(line);
		}
		final Element childParent = existing != null ? existing : canonicalParent;
		final List children = layout.childrenOf(task.id);
		for (int i = 0; i < children.size(); i++) {
			placeTask(doc, childParent, (TodoistImportTask) children.get(i), layout, existingByTaskId, anchors,
					usedIds, result, sectionName);
		}
	}

	private static void pruneEmptyRootFolders(final Element root) {
		final List projects = childNodes(root);
		for (int p = projects.size() - 1; p >= 0; p--) {
			final Element project = (Element) projects.get(p);
			if (hasTaskId(project)) {
				continue;
			}
			final List sections = childNodes(project);
			for (int s = sections.size() - 1; s >= 0; s--) {
				final Element section = (Element) sections.get(s);
				if (!hasTaskId(section) && childNodes(section).isEmpty()) {
					project.removeChild(section);
				}
			}
			if (childNodes(project).isEmpty()) {
				root.removeChild(project);
			}
		}
	}

	private static Element ensureSection(final Document doc, final Element root, final String label, final Set usedIds) {
		final List children = childNodes(root);
		for (int i = 0; i < children.size(); i++) {
			final Element child = (Element) children.get(i);
			if (!hasTaskId(child) && label.equals(plainText(child))) {
				return child;
			}
		}
		return createChildNode(doc, root, label, usedIds);
	}

	private static Element ensureChildByText(final Document doc, final Element parent, final String text,
			final Set usedIds) {
		final Element existing = findChildByText(parent, text);
		if (existing != null) {
			return existing;
		}
		return createChildNode(doc, parent, text, usedIds);
	}

	private static Element createChildNode(final Document doc, final Element parent, final String text,
			final Set usedIds) {
		final Element node = doc.createElement("node");
		node.setAttribute("TEXT", text == null ? "" : text);
		node.setAttribute("ID", newId(usedIds));
		parent.appendChild(node);
		return node;
	}

	private static Element findChildByText(final Element parent, final String text) {
		final List children = childNodes(parent);
		for (int i = 0; i < children.size(); i++) {
			final Element child = (Element) children.get(i);
			if (text.equals(plainText(child))) {
				return child;
			}
		}
		return null;
	}

	private static List childNodes(final Element parent) {
		final List out = new ArrayList();
		final NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			final Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE && "node".equals(child.getNodeName())) {
				out.add(child);
			}
		}
		return out;
	}

	private static Element firstChildElement(final Element parent, final String tag) {
		final NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			final Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE && tag.equals(child.getNodeName())) {
				return (Element) child;
			}
		}
		return null;
	}

	private static Set collectIds(final Element root) {
		final Set ids = new HashSet();
		collectIdsRecursive(root, ids);
		return ids;
	}

	private static void collectIdsRecursive(final Element node, final Set ids) {
		final String id = node.getAttribute("ID");
		if (id != null && id.length() > 0) {
			ids.add(id);
		}
		final List children = childNodes(node);
		for (int i = 0; i < children.size(); i++) {
			collectIdsRecursive((Element) children.get(i), ids);
		}
	}

	private static synchronized String newId(final Set usedIds) {
		String id;
		do {
			id = "ID_" + (System.currentTimeMillis() % 1000000000L) + (idCounter++);
		}
		while (usedIds.contains(id));
		usedIds.add(id);
		return id;
	}

	private static boolean hasTaskId(final Element node) {
		final String taskId = node.getAttribute(TodoistNodeMetaIo.XML_TASK_ID);
		return taskId != null && taskId.length() > 0;
	}

	private static String plainText(final Element node) {
		final String text = node.getAttribute("TEXT");
		if (text == null) {
			return "";
		}
		try {
			return HtmlUtils.htmlToPlain(text).trim();
		}
		catch (Exception e) {
			return text.trim();
		}
	}

	private static Map groupTasks(final List tasks) {
		final Map grouped = new HashMap();
		for (int i = 0; i < tasks.size(); i++) {
			final TodoistImportTask task = (TodoistImportTask) tasks.get(i);
			final String projectId = task.projectId == null ? "" : task.projectId;
			final String sectionId = task.sectionId == null || task.sectionId.length() == 0 ? NO_SECTION_KEY
					: task.sectionId;
			Map sectionMap = (Map) grouped.get(projectId);
			if (sectionMap == null) {
				sectionMap = new HashMap();
				grouped.put(projectId, sectionMap);
			}
			List sectionTasks = (List) sectionMap.get(sectionId);
			if (sectionTasks == null) {
				sectionTasks = new ArrayList();
				sectionMap.put(sectionId, sectionTasks);
			}
			sectionTasks.add(task);
		}
		return grouped;
	}

	private static String parsedLine(final TodoistImportTask task) {
		final TodoistContentParser parsed = TodoistContentParser.parse(task.content);
		if (parsed.linkUri != null) {
			return parsed.nodeText + " -> " + parsed.linkUri;
		}
		return parsed.nodeText;
	}

	private static String resolveName(final Map names, final String id, final String fallback) {
		if (id == null || id.length() == 0) {
			return fallback;
		}
		final String name = (String) names.get(id);
		return name != null && name.length() > 0 ? name : fallback + " (" + id + ")";
	}

	private static final class NameComparator implements Comparator {
		private final Map names;

		NameComparator(Map names) {
			this.names = names;
		}

		public int compare(Object a, Object b) {
			return resolveName(names, (String) a, "").compareToIgnoreCase(resolveName(names, (String) b, ""));
		}
	}

	/** Yellow background is the visible reminder. Identity stays in TODOIST_* attributes. */
	private static boolean ensureTodoistMark(final Element node, final TodoistImportTask task) {
		boolean changed = false;
		if (!TodoistReminderFactory.MARK_BACKGROUND.equalsIgnoreCase(node.getAttribute("BACKGROUND_COLOR"))) {
			node.setAttribute("BACKGROUND_COLOR", TodoistReminderFactory.MARK_BACKGROUND);
			changed = true;
		}
		final NodeList children = node.getChildNodes();
		for (int i = children.getLength() - 1; i >= 0; i--) {
			final Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE && "icon".equals(child.getNodeName())
					&& "bookmark".equals(((Element) child).getAttribute("BUILTIN"))) {
				node.removeChild(child);
				changed = true;
			}
		}
		if (!"true".equals(node.getAttribute(TodoistNodeMetaIo.XML_LINKED))) {
			node.setAttribute(TodoistNodeMetaIo.XML_LINKED, "true");
			changed = true;
		}
		if (task.projectId != null && task.projectId.length() > 0
				&& !task.projectId.equals(node.getAttribute(TodoistNodeMetaIo.XML_PROJECT_ID))) {
			node.setAttribute(TodoistNodeMetaIo.XML_PROJECT_ID, task.projectId);
			changed = true;
		}
		if (task.sectionId != null && task.sectionId.length() > 0
				&& !task.sectionId.equals(node.getAttribute(TodoistNodeMetaIo.XML_SECTION_ID))) {
			node.setAttribute(TodoistNodeMetaIo.XML_SECTION_ID, task.sectionId);
			changed = true;
		}
		return changed;
	}

	private static final class SectionComparator implements Comparator {
		private final Map names;

		SectionComparator(Map names) {
			this.names = names;
		}

		public int compare(Object a, Object b) {
			if (NO_SECTION_KEY.equals(a)) {
				return 1;
			}
			if (NO_SECTION_KEY.equals(b)) {
				return -1;
			}
			return resolveName(names, (String) a, "").compareToIgnoreCase(resolveName(names, (String) b, ""));
		}
	}
}
