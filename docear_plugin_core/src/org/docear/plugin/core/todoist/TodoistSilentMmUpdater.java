package org.docear.plugin.core.todoist;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.freeplane.core.util.LogUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Applies Todoist→mind-map updates to closed {@code .mm} files without opening them in the UI.
 */
final class TodoistSilentMmUpdater {
	private TodoistSilentMmUpdater() {
	}

	static final class Patch {
		final String nodeId;
		final String plainText;
		final long remindAtMillis;
		final int durationMinutes;
		final int jinji;
		final String taskId;
		final String contentHash;
		final boolean recurring;
		final int period;
		final String periodUnit;

		Patch(String nodeId, String plainText, long remindAtMillis, int durationMinutes, int jinji, String taskId,
				String contentHash, boolean recurring, int period, String periodUnit) {
			this.nodeId = nodeId;
			this.plainText = plainText;
			this.remindAtMillis = remindAtMillis;
			this.durationMinutes = durationMinutes;
			this.jinji = jinji;
			this.taskId = taskId;
			this.contentHash = contentHash;
			this.recurring = recurring;
			this.period = period <= 0 ? 1 : period;
			this.periodUnit = periodUnit == null || periodUnit.length() == 0 ? "DAY" : periodUnit;
		}
	}

	/** @return number of nodes changed */
	static int applyPatches(final File mmFile, final List patches) {
		if (mmFile == null || !mmFile.isFile() || patches == null || patches.isEmpty()) {
			return 0;
		}
		final Map byId = new HashMap();
		for (int i = 0; i < patches.size(); i++) {
			Patch p = (Patch) patches.get(i);
			if (p != null && p.nodeId != null) {
				byId.put(p.nodeId, p);
			}
		}
		if (byId.isEmpty()) {
			return 0;
		}
		InputStream in = null;
		OutputStream out = null;
		try {
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(false);
			factory.setIgnoringElementContentWhitespace(false);
			in = new FileInputStream(mmFile);
			final Document doc = factory.newDocumentBuilder().parse(in);
			in.close();
			in = null;
			int changed = 0;
			final NodeList nodes = doc.getElementsByTagName("node");
			for (int i = 0; i < nodes.getLength(); i++) {
				final Element node = (Element) nodes.item(i);
				final String id = node.getAttribute("ID");
				final Patch patch = (Patch) byId.get(id);
				if (patch == null) {
					continue;
				}
				if (applyPatchToElement(node, patch)) {
					changed++;
				}
			}
			if (changed == 0) {
				return 0;
			}
			final Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.setOutputProperty(OutputKeys.INDENT, "no");
			out = new FileOutputStream(mmFile);
			transformer.transform(new DOMSource(doc), new StreamResult(out));
			return changed;
		}
		catch (Exception e) {
			LogUtils.warn("Todoist: silent .mm update failed for " + mmFile.getPath(), e);
			return 0;
		}
		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (Exception e) {
				}
			}
			if (out != null) {
				try {
					out.close();
				}
				catch (Exception e) {
				}
			}
		}
	}

	private static boolean applyPatchToElement(final Element node, final Patch patch) {
		boolean changed = false;
		if (patch.plainText != null) {
			final String current = node.getAttribute("TEXT");
			final String currentPlain = htmlToPlainSafe(current);
			if (!patch.plainText.equals(currentPlain)) {
				node.setAttribute("TEXT", patch.plainText);
				changed = true;
			}
		}
		if (patch.durationMinutes > 0) {
			if (!Integer.toString(patch.durationMinutes).equals(node.getAttribute("TASKTIME"))) {
				node.setAttribute("TASKTIME", Integer.toString(patch.durationMinutes));
				changed = true;
			}
		}
		else if (node.hasAttribute("TASKTIME") && !"0".equals(node.getAttribute("TASKTIME"))) {
			node.setAttribute("TASKTIME", "0");
			changed = true;
		}
		if (patch.jinji > 0) {
			if (!Integer.toString(patch.jinji).equals(node.getAttribute("JINJI"))) {
				node.setAttribute("JINJI", Integer.toString(patch.jinji));
				changed = true;
			}
		}
		if (patch.taskId != null && patch.taskId.length() > 0) {
			if (!patch.taskId.equals(node.getAttribute(TodoistNodeMetaIo.XML_TASK_ID))) {
				node.setAttribute(TodoistNodeMetaIo.XML_TASK_ID, patch.taskId);
				changed = true;
			}
		}
		if (patch.contentHash != null && patch.contentHash.length() > 0) {
			if (!patch.contentHash.equals(node.getAttribute(TodoistNodeMetaIo.XML_CONTENT_HASH))) {
				node.setAttribute(TodoistNodeMetaIo.XML_CONTENT_HASH, patch.contentHash);
				changed = true;
			}
		}
		if (patch.remindAtMillis > 0) {
			if (ensureReminderParameters(node, patch)) {
				changed = true;
			}
		}
		return changed;
	}

	private static boolean ensureReminderParameters(final Element node, final Patch patch) {
		Element parameters = findReminderParameters(node);
		if (parameters == null) {
			final Element hook = node.getOwnerDocument().createElement("hook");
			hook.setAttribute("NAME", "plugins/TimeManagementReminder.xml");
			parameters = node.getOwnerDocument().createElement("Parameters");
			hook.appendChild(parameters);
			node.appendChild(hook);
		}
		boolean changed = false;
		final String due = Long.toString(patch.remindAtMillis);
		if (!due.equals(parameters.getAttribute("REMINDUSERAT"))) {
			parameters.setAttribute("REMINDUSERAT", due);
			changed = true;
		}
		final String period = Integer.toString(patch.period);
		if (!period.equals(parameters.getAttribute("PERIOD"))) {
			parameters.setAttribute("PERIOD", period);
			changed = true;
		}
		if (!patch.periodUnit.equalsIgnoreCase(String.valueOf(parameters.getAttribute("UNIT")))) {
			parameters.setAttribute("UNIT", patch.periodUnit);
			changed = true;
		}
		return changed;
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
				if (hc.getNodeType() == Node.ELEMENT_NODE && "Parameters".equals(((Element) hc).getTagName())) {
					return (Element) hc;
				}
			}
		}
		return null;
	}

	private static String htmlToPlainSafe(String raw) {
		if (raw == null) {
			return "";
		}
		try {
			return org.freeplane.core.util.HtmlUtils.htmlToPlain(raw).trim();
		}
		catch (Exception e) {
			return raw.trim();
		}
	}
}
