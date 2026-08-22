package org.docear.plugin.mermaid;

import org.freeplane.core.util.HtmlUtils;

/**
 * Detects Mermaid source in node text: fenced {@code ```mermaid} blocks or format=mermaid.
 */
public final class MermaidParse {

	private MermaidParse() {
	}

	/**
	 * @return Mermaid source without fence, or {@code null} if not a Mermaid node
	 */
	public static String extractSource(final String rawText, final String nodeFormat) {
		if (rawText == null) {
			return null;
		}
		String text = rawText.trim();
		if (HtmlUtils.isHtmlNode(text)) {
			text = HtmlUtils.htmlToPlain(text).trim();
		}
		final String fenced = extractFenced(text);
		if (fenced != null) {
			return fenced;
		}
		if (MermaidFormat.MERMAID_FORMAT.equals(nodeFormat) && text.length() > 0) {
			return text;
		}
		return null;
	}

	private static String extractFenced(final String text) {
		if (!text.startsWith("```")) {
			return null;
		}
		int lineEnd = text.indexOf('\n');
		if (lineEnd < 0) {
			return null;
		}
		final String firstLine = text.substring(0, lineEnd).trim().toLowerCase();
		if (!firstLine.startsWith("```mermaid") && !firstLine.equals("```mmd")) {
			return null;
		}
		final String rest = text.substring(lineEnd + 1);
		final int close = rest.lastIndexOf("```");
		if (close < 0) {
			return rest.trim();
		}
		return rest.substring(0, close).trim();
	}

	public static boolean looksLikeMermaid(final String rawText, final String nodeFormat) {
		return extractSource(rawText, nodeFormat) != null;
	}
}
