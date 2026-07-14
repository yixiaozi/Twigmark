package org.freeplane.plugin.workspace.features.nodepins;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public final class NodeDetailsTagScanner {

	private NodeDetailsTagScanner() {
	}

	public static List scanAllProjects() {
		final List files = new ArrayList();
		final File[] roots = MindMapDataRootResolver.getScanRoots();
		for (int i = 0; i < roots.length; i++) {
			collectMindmapFiles(roots[i], files);
		}
		final List result = new ArrayList();
		for (int i = 0; i < files.size(); i++) {
			result.addAll(scanFile((File) files.get(i)));
		}
		return result;
	}

	private static void collectMindmapFiles(File dir, List out) {
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
			} else {
				String lower = file.getName().toLowerCase();
				if (lower.endsWith(".mm") && !file.getName().startsWith("~") && file.getName().indexOf("\u51b2\u7a81\u526f\u672c") < 0) {
					out.add(file);
				}
			}
		}
	}

	public static List scanFile(final File file) {
		final List entries = new ArrayList();
		if (file == null || !file.exists()) {
			return entries;
		}
		try {
			final SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(false);
			final SAXParser saxParser = factory.newSAXParser();
			saxParser.parse(file, new DefaultHandler() {
				private final List stack = new ArrayList();
				private NodeFrame current;
				private boolean inNodeRichContent;

				public void startElement(final String uri, final String localName, final String qName,
						final Attributes attributes) {
					if ("node".equals(qName)) {
						current = new NodeFrame(attributes.getValue("ID"), attributes.getValue("TEXT"));
						stack.add(current);
						inNodeRichContent = false;
					} else if ("richcontent".equals(qName) && current != null) {
						final String type = attributes.getValue("TYPE");
						if ("NODE".equalsIgnoreCase(type)) {
							inNodeRichContent = true;
							current.richContent = new StringBuilder();
						}
					}
				}

				public void characters(final char[] ch, final int start, final int length) {
					if (inNodeRichContent && current != null && current.richContent != null) {
						current.richContent.append(ch, start, length);
					}
				}

				public void endElement(final String uri, final String localName, final String qName) {
					if ("richcontent".equals(qName) && inNodeRichContent) {
						inNodeRichContent = false;
					} else if ("node".equals(qName) && !stack.isEmpty()) {
						final NodeFrame finished = (NodeFrame) stack.remove(stack.size() - 1);
						addEntryIfTagged(finished);
						current = stack.isEmpty() ? null : (NodeFrame) stack.get(stack.size() - 1);
						inNodeRichContent = false;
					}
				}

				private void addEntryIfTagged(final NodeFrame frame) {
					if (frame == null || frame.id == null) {
						return;
					}
					final StringBuilder combinedText = new StringBuilder();
					if (frame.text != null && frame.text.trim().length() > 0) {
						combinedText.append(frame.text);
					}
					if (frame.richContent != null && frame.richContent.length() > 0) {
						if (combinedText.length() > 0) {
							combinedText.append(' ');
						}
						combinedText.append(frame.richContent.toString());
					}
					final String nodeText = combinedText.toString();
					if (!NodeDetailsTagUtils.mayContainBracketTags(nodeText)) {
						return;
					}
					final Set allTags = NodeDetailsTagUtils.parseAllTags(nodeText);
					if (allTags.isEmpty()) {
						return;
					}
					final boolean pinned = allTags.contains(NodeDetailsTagUtils.PIN_TAG);
					final LinkedHashSet userTags = new LinkedHashSet();
					for (final Iterator it = allTags.iterator(); it.hasNext();) {
						final String tag = (String) it.next();
						if (!NodeDetailsTagUtils.PIN_TAG.equals(tag) && NodeDetailsTagUtils.isValidTagName(tag)) {
							userTags.add(tag);
						}
					}
					final String label = NodeDetailsTagUtils.extractNodeTitle(nodeText);
					entries.add(new NodePinEntry(file.getAbsolutePath() + "#" + frame.id, userTags, pinned,
							label.length() > 0 ? label : HtmlUtils.unescapeHTMLUnicodeEntity(nodeText.trim())));
				}
			});
		} catch (final Exception e) {
			LogUtils.warn("could not scan tags in " + file.getAbsolutePath(), e);
		}
		return entries;
	}

	private static final class NodeFrame {
		final String id;
		final String text;
		StringBuilder richContent;

		NodeFrame(final String id, final String text) {
			this.id = id;
			this.text = text;
		}
	}
}
