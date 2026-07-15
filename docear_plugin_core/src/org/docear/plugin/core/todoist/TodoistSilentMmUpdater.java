package org.docear.plugin.core.todoist;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.freeplane.core.util.LogUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Applies Todoist→mind-map updates to closed {@code .mm} files without opening them in the UI.
 * <p>
 * Serialization matches Freeplane's {@code XMLWriter}: characters {@code > 0x7E} become
 * {@code &#x…;} so files stay ASCII-safe. Freeplane loads without a UTF-8 BOM using
 * {@code FileUtils.defaultCharset()} (often GBK on Chinese Windows); raw UTF-8 Chinese from a
 * DOM {@code Transformer} is the usual cause of 乱码 after silent Todoist sync.
 * Writes are atomic (temp + rename) and refused while the map is open in Docear.
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
		/** Docear cycle type (day/week/…); empty/onetime clears REMINDERTYPE attrs. */
		final String remindType;
		/** Weekday codes for weekly cycles ({@code RWEEKS}). */
		final String weekDays;

		Patch(String nodeId, String plainText, long remindAtMillis, int durationMinutes, int jinji, String taskId,
				String contentHash, boolean recurring, int period, String periodUnit) {
			this(nodeId, plainText, remindAtMillis, durationMinutes, jinji, taskId, contentHash, recurring, period,
					periodUnit, recurring ? unitToRemindType(periodUnit) : "onetime", "");
		}

		Patch(String nodeId, String plainText, long remindAtMillis, int durationMinutes, int jinji, String taskId,
				String contentHash, boolean recurring, int period, String periodUnit, String remindType,
				String weekDays) {
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
			this.remindType = remindType == null || remindType.length() == 0
					? (recurring ? unitToRemindType(this.periodUnit) : "onetime")
					: remindType;
			this.weekDays = weekDays == null ? "" : weekDays;
		}

		private static String unitToRemindType(String periodUnit) {
			if (periodUnit == null) {
				return "day";
			}
			final String u = periodUnit.toUpperCase();
			if ("HOUR".equals(u)) {
				return "hour";
			}
			if ("WEEK".equals(u)) {
				return "week";
			}
			if ("MONTH".equals(u)) {
				return "month";
			}
			if ("YEAR".equals(u)) {
				return "year";
			}
			return "day";
		}
	}

	/** @return number of nodes changed */
	static int applyPatches(final File mmFile, final List patches) {
		if (mmFile == null || !mmFile.isFile() || patches == null || patches.isEmpty()) {
			return 0;
		}
		if (TodoistNodeLocator.findOpenMap(mmFile) != null) {
			LogUtils.warn("Todoist: refusing silent .mm update while map is open: " + mmFile.getPath());
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
			if (!writeDocumentAtomically(mmFile, doc)) {
				return 0;
			}
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
		}
	}

	private static boolean writeDocumentAtomically(final File mmFile, final Document doc) throws Exception {
		final File parent = mmFile.getParentFile();
		final File tmpFile = new File(parent, "~todoist-" + mmFile.getName());
		Writer writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), Charset.forName("UTF-8")));
			writeFreeplaneCompatibleXml(doc, writer);
			writer.flush();
			writer.close();
			writer = null;
			if (!isValidMapFile(tmpFile)) {
				LogUtils.warn("Todoist: silent .mm update produced invalid map, keeping original: " + mmFile.getPath());
				tmpFile.delete();
				return false;
			}
			if (mmFile.exists() && !mmFile.delete()) {
				LogUtils.warn("Todoist: could not replace open/locked .mm: " + mmFile.getPath());
				tmpFile.delete();
				return false;
			}
			if (!tmpFile.renameTo(mmFile)) {
				LogUtils.warn("Todoist: could not rename temp .mm into place: " + mmFile.getPath());
				tmpFile.delete();
				return false;
			}
			return true;
		}
		finally {
			if (writer != null) {
				try {
					writer.close();
				}
				catch (Exception e) {
				}
			}
			if (tmpFile.exists() && !tmpFile.equals(mmFile)) {
				// left behind only on failure paths above that did not delete
			}
		}
	}

	private static boolean isValidMapFile(final File file) {
		try {
			final RandomAccessFile raf = new RandomAccessFile(file, "r");
			try {
				final long length = raf.length();
				if (length < 7) {
					return false;
				}
				raf.seek(Math.max(0, length - 32));
				final byte[] buf = new byte[(int) Math.min(32, length)];
				raf.readFully(buf);
				return new String(buf, "US-ASCII").indexOf("/map>") >= 0;
			}
			finally {
				raf.close();
			}
		}
		catch (Exception e) {
			return false;
		}
	}

	/**
	 * Emits XML the way Freeplane/Docear save maps: the file must start with
	 * {@code <map version="…">} (no {@code <?xml …?>} preamble). Dialect detection in
	 * {@code MapVersionInterpreter} uses {@code startsWith("<map version=\"…")}, so an XML
	 * declaration makes Docear treat the file as an unknown program and warn on every reload.
	 * Non-ASCII is written as {@code &#x…;} like Freeplane's {@code XMLWriter}.
	 */
	private static void writeFreeplaneCompatibleXml(final Document doc, final Writer writer) throws Exception {
		final Element root = doc.getDocumentElement();
		if (root != null) {
			ensureMapVersionAttribute(root);
			writeElement(root, writer);
			writer.write('\n');
		}
	}

	/** Dialect detection requires a recognizable {@code version} on {@code <map>}. */
	private static void ensureMapVersionAttribute(final Element root) {
		if (!"map".equals(root.getTagName())) {
			return;
		}
		final String version = root.getAttribute("version");
		if (version == null || version.trim().length() == 0) {
			root.setAttribute("version", org.freeplane.core.util.FreeplaneVersion.XML_VERSION);
		}
	}

	private static void writeElement(final Element el, final Writer writer) throws Exception {
		writer.write('<');
		writer.write(el.getTagName());
		final NamedNodeMap attrs = el.getAttributes();
		if ("map".equals(el.getTagName())) {
			// version must be the first attribute: MapVersionInterpreter matches
			// startsWith("<map version=\"…").
			writeAttribute(writer, "version", el.getAttribute("version"));
			if (attrs != null) {
				for (int i = 0; i < attrs.getLength(); i++) {
					final Attr attr = (Attr) attrs.item(i);
					if ("version".equals(attr.getName())) {
						continue;
					}
					writeAttribute(writer, attr.getName(), attr.getValue());
				}
			}
		}
		else if (attrs != null) {
			for (int i = 0; i < attrs.getLength(); i++) {
				final Attr attr = (Attr) attrs.item(i);
				writeAttribute(writer, attr.getName(), attr.getValue());
			}
		}
		final NodeList children = el.getChildNodes();
		boolean hasElementChild = false;
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
				hasElementChild = true;
				break;
			}
		}
		if (!hasElementChild && isBlankTextOnly(children)) {
			writer.write("/>");
			return;
		}
		writer.write('>');
		for (int i = 0; i < children.getLength(); i++) {
			final Node child = children.item(i);
			final short type = child.getNodeType();
			if (type == Node.ELEMENT_NODE) {
				writeElement((Element) child, writer);
			}
			else if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
				writeEncoded(writer, child.getNodeValue(), false);
			}
			else if (type == Node.COMMENT_NODE) {
				writer.write("<!--");
				writer.write(child.getNodeValue());
				writer.write("-->");
			}
		}
		writer.write("</");
		writer.write(el.getTagName());
		writer.write('>');
	}

	private static void writeAttribute(final Writer writer, final String name, final String value) throws Exception {
		writer.write(' ');
		writer.write(name);
		writer.write("=\"");
		writeEncoded(writer, value == null ? "" : value, true);
		writer.write('"');
	}

	private static boolean isBlankTextOnly(final NodeList children) {
		for (int i = 0; i < children.getLength(); i++) {
			final Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				return false;
			}
			if (child.getNodeType() == Node.TEXT_NODE) {
				final String v = child.getNodeValue();
				if (v != null && v.trim().length() > 0) {
					return false;
				}
			}
			else if (child.getNodeType() != Node.COMMENT_NODE) {
				return false;
			}
		}
		return true;
	}

	/** Same rules as {@code org.freeplane.core.io.xml.XMLWriter#writeEncoded}. */
	private static void writeEncoded(final Writer writer, final String str, final boolean attributeValue)
			throws Exception {
		if (str == null) {
			return;
		}
		for (int i = 0; i < str.length(); i++) {
			final char c = str.charAt(i);
			if (c > 0x7E) {
				writer.write("&#x");
				writer.write(Integer.toString(c, 16));
				writer.write(';');
				continue;
			}
			switch (c) {
				case '<':
					writer.write("&lt;");
					continue;
				case '>':
					writer.write("&gt;");
					continue;
				case '&':
					writer.write("&amp;");
					continue;
				case '\'':
					writer.write("&apos;");
					continue;
				case '"':
					writer.write("&quot;");
					continue;
				case 0x0A:
					if (attributeValue) {
						writer.write("&#xa;");
					}
					else {
						writer.write(c);
					}
					continue;
				default:
					if (c < ' ') {
						writer.write("&#x");
						writer.write(Integer.toString(c, 16));
						writer.write(';');
						continue;
					}
					writer.write(c);
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
		else if (node.hasAttribute("JINJI") && !"0".equals(node.getAttribute("JINJI"))) {
			node.setAttribute("JINJI", "0");
			changed = true;
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
			if (applyCycleAttributes(node, patch)) {
				changed = true;
			}
		}
		else {
			if (removeReminderHook(node)) {
				changed = true;
			}
			if (clearCycleAttributes(node)) {
				changed = true;
			}
		}
		return changed;
	}

	private static boolean applyCycleAttributes(final Element node, final Patch patch) {
		if (!patch.recurring || "onetime".equalsIgnoreCase(patch.remindType)) {
			return clearCycleAttributes(node);
		}
		boolean changed = false;
		if (!patch.remindType.equals(node.getAttribute("REMINDERTYPE"))) {
			node.setAttribute("REMINDERTYPE", patch.remindType);
			changed = true;
		}
		clearIntervalAttrsExcept(node, patch.remindType);
		final String intervalAttr = intervalAttributeFor(patch.remindType);
		if (intervalAttr != null) {
			final String value = Integer.toString(patch.period);
			if (!value.equals(node.getAttribute(intervalAttr))) {
				node.setAttribute(intervalAttr, value);
				changed = true;
			}
		}
		if ("week".equalsIgnoreCase(patch.remindType)) {
			final String days = patch.weekDays.length() > 0 ? patch.weekDays : "1";
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

	private static boolean clearCycleAttributes(final Element node) {
		boolean changed = false;
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

	private static void clearIntervalAttrsExcept(final Element node, final String remindType) {
		final String keep = intervalAttributeFor(remindType);
		final String[] attrs = new String[] { "RHOUR", "RDAYS", "RWEEK", "RMONTH", "RYEAR" };
		for (int i = 0; i < attrs.length; i++) {
			if (keep != null && keep.equals(attrs[i])) {
				continue;
			}
			if (node.hasAttribute(attrs[i])) {
				node.removeAttribute(attrs[i]);
			}
		}
	}

	private static String intervalAttributeFor(final String remindType) {
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

	private static boolean removeReminderHook(final Element node) {
		final NodeList children = node.getChildNodes();
		boolean removed = false;
		for (int i = children.getLength() - 1; i >= 0; i--) {
			final Node child = children.item(i);
			if (child.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			final Element el = (Element) child;
			if (!"hook".equals(el.getTagName())) {
				continue;
			}
			final String name = el.getAttribute("NAME");
			if (name != null && name.indexOf("TimeManagementReminder") >= 0) {
				node.removeChild(el);
				removed = true;
			}
		}
		return removed;
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
		long remindAt = patch.remindAtMillis;
		if (patch.recurring) {
			final String existingRaw = parameters.getAttribute("REMINDUSERAT");
			if (existingRaw != null && existingRaw.length() > 0) {
				try {
					final long existingAt = Long.parseLong(existingRaw);
					if (existingAt > 0 && sameLocalTimeOfDay(existingAt, patch.remindAtMillis)) {
						// Keep Docear anchor date; Todoist only has the next occurrence.
						remindAt = existingAt;
					}
					else if (existingAt > 0) {
						remindAt = replaceLocalTimeOfDay(existingAt, patch.remindAtMillis);
					}
				}
				catch (NumberFormatException e) {
				}
			}
		}
		final String due = Long.toString(remindAt);
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

	private static boolean sameLocalTimeOfDay(final long a, final long b) {
		final java.util.Calendar ca = java.util.Calendar.getInstance();
		ca.setTimeInMillis(a);
		final java.util.Calendar cb = java.util.Calendar.getInstance();
		cb.setTimeInMillis(b);
		return ca.get(java.util.Calendar.HOUR_OF_DAY) == cb.get(java.util.Calendar.HOUR_OF_DAY)
				&& ca.get(java.util.Calendar.MINUTE) == cb.get(java.util.Calendar.MINUTE);
	}

	private static long replaceLocalTimeOfDay(final long dateMillis, final long timeSourceMillis) {
		final java.util.Calendar date = java.util.Calendar.getInstance();
		date.setTimeInMillis(dateMillis);
		final java.util.Calendar time = java.util.Calendar.getInstance();
		time.setTimeInMillis(timeSourceMillis);
		date.set(java.util.Calendar.HOUR_OF_DAY, time.get(java.util.Calendar.HOUR_OF_DAY));
		date.set(java.util.Calendar.MINUTE, time.get(java.util.Calendar.MINUTE));
		date.set(java.util.Calendar.SECOND, 0);
		date.set(java.util.Calendar.MILLISECOND, 0);
		return date.getTimeInMillis();
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
