package org.freeplane.features.hashphotos.mindmapmode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 离线写入 Hash Photos.mm：每天一个活动节点，无可见属性行。
 */
public final class HashPhotosEventsFileSynchronizer {

	private static final String ACTIVITIES_MARKER = "ID=\"ID_966018587\"";
	private static final Pattern NODE_TEXT = Pattern.compile("TEXT=\"([^\"]*)\"");
	private static final Pattern ATTR_SYNCED = Pattern.compile(
	        "<attribute NAME=\"hashPhotosTitle\"[^>]*/>", Pattern.CASE_INSENSITIVE);

	private HashPhotosEventsFileSynchronizer() {
	}

	public static void main(final String[] args) throws Exception {
		if (args.length == 0) {
			System.err.println("Usage: HashPhotosEventsFileSynchronizer <path-to-Hash Photos.mm>");
			System.exit(1);
		}
		final File mapFile = new File(args[0]);
		final SyncReport report = syncFile(mapFile, true);
		System.out.println("Hash Photos file sync: +" + report.added);
		System.out.println("File: " + mapFile.getAbsolutePath());
	}

	public static SyncReport syncFile(final File mapFile, final boolean force) throws Exception {
		final SyncReport report = new SyncReport();
		final File exportDir = HashPhotosEventsImporter.getExportDir(mapFile);
		final File csvFile = HashPhotosEventsImporter.findLatestCsv(exportDir);
		if (csvFile == null) {
			throw new IllegalStateException("No CSV in " + exportDir);
		}
		if (!force && !HashPhotosEventsImporter.needsSync(exportDir, csvFile)) {
			report.skipped = true;
			return report;
		}

		final Charset charset = detectMapCharset(mapFile);
		final String content = readFile(mapFile, charset);
		final int marker = content.indexOf(ACTIVITIES_MARKER);
		if (marker < 0) {
			throw new IllegalStateException("Activities node not found");
		}
		final int openEnd = content.indexOf('>', marker);
		final int closeStart = findActivitiesNodeClose(content, openEnd);
		if (openEnd < 0 || closeStart < 0) {
			throw new IllegalStateException("Malformed activities node");
		}

		final Map desired = HashPhotosEventsImporter.readCsv(csvFile);
		final String activitiesBody = content.substring(openEnd + 1, closeStart);
		final Map existing = parseExistingEvents(activitiesBody);
		final Map titleStore = HashPhotosTitleStore.load(exportDir);
		final Map newTitleStore = new HashMap();
		final String body = buildActivitiesBody(desired, existing, titleStore, newTitleStore, report,
		        System.currentTimeMillis());

		writeFile(mapFile, content.substring(0, openEnd + 1) + body + content.substring(closeStart), charset);
		HashPhotosEventsImporter.writeState(new File(exportDir, HashPhotosEventsImporter.SYNC_STATE_FILE), csvFile);
		HashPhotosTitleStore.save(exportDir, newTitleStore);
		return report;
	}

	private static final class ExistingEvent {
		private String fullText;
		private String childXml = "";
	}

	private static Map parseExistingEvents(final String body) {
		final Map result = new HashMap();
		final java.util.List path = new java.util.ArrayList();
		int i = 0;
		while (i < body.length()) {
			final int close = body.indexOf("</node>", i);
			final int nodeStart = body.indexOf("<node", i);
			if (nodeStart < 0) {
				break;
			}
			if (close >= 0 && close < nodeStart) {
				if (!path.isEmpty()) {
					path.remove(path.size() - 1);
				}
				i = close + 7;
				continue;
			}
			final int tagEnd = body.indexOf('>', nodeStart);
			if (tagEnd < 0) {
				break;
			}
			final String tag = body.substring(nodeStart, tagEnd + 1);
			final Matcher textM = NODE_TEXT.matcher(tag);
			if (!textM.find()) {
				i = tagEnd + 1;
				continue;
			}
			final String text = unescapeXml(textM.group(1));
			final boolean selfClosing = tag.endsWith("/>");
			if (path.size() == 3) {
				final ExistingEvent event = new ExistingEvent();
				event.fullText = text;
				if (!selfClosing) {
					final int innerStart = tagEnd + 1;
					final int innerEnd = findMatchingClose(body, innerStart);
					if (innerEnd > innerStart) {
						event.childXml = stripSyncedAttributes(body.substring(innerStart, innerEnd).trim());
					}
					i = innerEnd + 7;
				}
				else {
					i = tagEnd + 1;
				}
				final String dateKey = String.format("%s%02d%02d", path.get(0), Integer.parseInt((String) path.get(1)),
				        Integer.parseInt((String) path.get(2)));
				result.put(dateKey, event);
			}
			else if (path.size() < 3) {
				path.add(text);
				i = tagEnd + 1;
			}
			else {
				i = tagEnd + 1;
			}
		}
		return result;
	}

	private static String buildActivitiesBody(final Map desired, final Map existing, final Map titleStore,
	        final Map newTitleStore, final SyncReport report, final long baseTime) {
		final TreeMap sorted = new TreeMap(desired);
		final StringBuilder sb = new StringBuilder();
		sb.append("\n<edge COLOR=\"#ff0000\"/>");

		String currentYear = null;
		String currentMonth = null;
		int seq = 0;

		for (Iterator it = sorted.entrySet().iterator(); it.hasNext();) {
			final Map.Entry entry = (Map.Entry) it.next();
			final String dateKey = (String) entry.getKey();
			final String csvTitle = (String) entry.getValue();
			newTitleStore.put(dateKey, csvTitle);

			final String year = dateKey.substring(0, 4);
			final String month = Integer.toString(Integer.parseInt(dateKey.substring(4, 6)));
			final String day = Integer.toString(Integer.parseInt(dateKey.substring(6, 8)));

			if (!year.equals(currentYear)) {
				if (currentMonth != null) {
					sb.append("\n</node>");
				}
				if (currentYear != null) {
					sb.append("\n</node>");
				}
				sb.append(openNode(year, baseTime, seq++));
				currentYear = year;
				currentMonth = null;
			}
			if (!month.equals(currentMonth)) {
				if (currentMonth != null) {
					sb.append("\n</node>");
				}
				sb.append(openNode(month, baseTime, seq++));
				currentMonth = month;
			}

			sb.append(openNode(day, baseTime, seq++));
			final ExistingEvent old = (ExistingEvent) existing.get(dateKey);
			String merged = csvTitle;
			String childXml = "";
			if (old != null) {
				merged = HashPhotosTitleMerge.mergeTitle(csvTitle, old.fullText, (String) titleStore.get(dateKey));
				childXml = old.childXml;
			}
			appendEventNode(sb, merged, childXml, baseTime, seq++);
			sb.append("\n</node>");
			report.added++;
		}
		if (currentMonth != null) {
			sb.append("\n</node>");
		}
		if (currentYear != null) {
			sb.append("\n</node>");
		}
		sb.append('\n');
		return sb.toString();
	}

	private static void appendEventNode(final StringBuilder sb, final String text, final String childXml,
	        final long baseTime, final int seq) {
		final String id = "ID_" + (baseTime + seq);
		if (childXml == null || childXml.length() == 0) {
			sb.append("\n<node TEXT=\"").append(escapeXml(text)).append("\" POSITION=\"right\" ID=\"").append(id)
			        .append("\" CREATED=\"").append(baseTime).append("\" MODIFIED=\"").append(baseTime).append("\"/>");
		}
		else {
			sb.append("\n<node TEXT=\"").append(escapeXml(text)).append("\" POSITION=\"right\" ID=\"").append(id)
			        .append("\" CREATED=\"").append(baseTime).append("\" MODIFIED=\"").append(baseTime).append("\">");
			sb.append('\n').append(childXml);
			sb.append("\n</node>");
		}
	}

	private static String openNode(final String text, final long baseTime, final int seq) {
		return "\n<node TEXT=\"" + escapeXml(text) + "\" POSITION=\"right\" ID=\"ID_" + (baseTime + seq)
		        + "\" CREATED=\"" + baseTime + "\" MODIFIED=\"" + baseTime + "\">";
	}

	private static String stripSyncedAttributes(final String xml) {
		if (xml == null) {
			return "";
		}
		return ATTR_SYNCED.matcher(xml).replaceAll("").trim();
	}

	private static int findMatchingClose(final String body, final int from) {
		int depth = 1;
		int i = from;
		while (i < body.length()) {
			final int nodeStart = body.indexOf("<node", i);
			final int closeStart = body.indexOf("</node>", i);
			if (closeStart < 0) {
				return -1;
			}
			if (nodeStart >= 0 && nodeStart < closeStart) {
				final int tagEnd = body.indexOf('>', nodeStart);
				if (tagEnd < 0) {
					return -1;
				}
				if (body.charAt(tagEnd - 1) != '/') {
					depth++;
				}
				i = tagEnd + 1;
			}
			else {
				depth--;
				if (depth == 0) {
					return closeStart;
				}
				i = closeStart + 7;
			}
		}
		return -1;
	}

	private static int findActivitiesNodeClose(final String content, final int from) {
		int depth = 1;
		int i = from + 1;
		while (i < content.length()) {
			final int nodeStart = content.indexOf("<node", i);
			final int closeStart = content.indexOf("</node>", i);
			if (closeStart < 0 && nodeStart < 0) {
				return -1;
			}
			if (nodeStart >= 0 && (closeStart < 0 || nodeStart < closeStart)) {
				final int tagEnd = content.indexOf('>', nodeStart);
				if (tagEnd < 0) {
					return -1;
				}
				if (content.charAt(tagEnd - 1) != '/') {
					depth++;
				}
				i = tagEnd + 1;
			}
			else {
				depth--;
				if (depth == 0) {
					return closeStart;
				}
				i = closeStart + 7;
			}
		}
		return -1;
	}

	private static Charset detectMapCharset(final File mapFile) throws Exception {
		final byte[] head = new byte[4096];
		final FileInputStream in = new FileInputStream(mapFile);
		try {
			final int n = in.read(head);
			final String utf8 = new String(head, 0, n, "UTF-8");
			if (utf8.indexOf("活动数据") >= 0 || utf8.indexOf("&#x6d3b;&#x52a8;&#x6570;&#x636e;") >= 0) {
				return Charset.forName("UTF-8");
			}
		}
		finally {
			in.close();
		}
		return Charset.forName("GBK");
	}

	private static String unescapeXml(final String text) {
		if (text == null) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length();) {
			if (text.charAt(i) == '&') {
				final int semi = text.indexOf(';', i);
				if (semi < 0) {
					break;
				}
				final String ent = text.substring(i + 1, semi);
				if (ent.startsWith("#x")) {
					sb.append((char) Integer.parseInt(ent.substring(2), 16));
				}
				else if (ent.startsWith("#")) {
					sb.append((char) Integer.parseInt(ent.substring(1)));
				}
				else if ("amp".equals(ent)) {
					sb.append('&');
				}
				else if ("lt".equals(ent)) {
					sb.append('<');
				}
				else if ("gt".equals(ent)) {
					sb.append('>');
				}
				else if ("quot".equals(ent)) {
					sb.append('"');
				}
				else {
					sb.append('&').append(ent).append(';');
				}
				i = semi + 1;
			}
			else {
				sb.append(text.charAt(i++));
			}
		}
		return sb.toString();
	}

	private static String escapeXml(final String text) {
		if (text == null) {
			return "";
		}
		final StringBuilder result = new StringBuilder(text.length() * 2);
		for (int i = 0; i < text.length(); i++) {
			final char ch = text.charAt(i);
			switch (ch) {
				case '&':
					result.append("&amp;");
					break;
				case '<':
					result.append("&lt;");
					break;
				case '>':
					result.append("&gt;");
					break;
				case '"':
					result.append("&quot;");
					break;
				default:
					if (ch > 127) {
						result.append("&#x").append(Integer.toHexString(ch)).append(";");
					}
					else {
						result.append(ch);
					}
			}
		}
		return result.toString();
	}

	private static String readFile(final File file, final Charset charset) throws Exception {
		final StringBuilder sb = new StringBuilder();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append('\n');
			}
		}
		finally {
			if (reader != null) {
				reader.close();
			}
		}
		return sb.toString();
	}

	private static void writeFile(final File file, final String content, final Charset charset) throws Exception {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), charset));
			writer.write(content);
		}
		finally {
			if (writer != null) {
				writer.close();
			}
		}
	}

	public static final class SyncReport {
		public int added;
		public int updated;
		public int deleted;
		public boolean skipped;
	}
}
