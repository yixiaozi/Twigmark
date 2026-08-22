package org.docear.plugin.mermaid;

import org.freeplane.core.util.HtmlUtils;

/**
 * Unified fenced-block parser for Twigmark rich node content.
 * Supports {@code ```twigmark type=…}, legacy {@code ```mermaid} fences, and bare markdown tables.
 */
public final class FenceParse {

	public enum Kind {
		MERMAID, PLANTUML, MATH, TABLE, CODE, NONE
	}

	public static final class Block {
		public final Kind kind;
		public final String language;
		public final String source;

		Block(final Kind kind, final String language, final String source) {
			this.kind = kind;
			this.language = language;
			this.source = source;
		}
	}

	private FenceParse() {
	}

	public static Block parse(final String rawText, final String nodeFormat) {
		if (rawText == null) {
			return null;
		}
		String text = rawText.trim();
		if (HtmlUtils.isHtmlNode(text)) {
			text = HtmlUtils.htmlToPlain(text).trim();
		}
		if (text.startsWith("```")) {
			final Block fenced = parseFenced(text);
			if (fenced != null) {
				return fenced;
			}
		}
		if (MermaidFormat.MERMAID_FORMAT.equals(nodeFormat) && text.length() > 0) {
			return new Block(Kind.MERMAID, "mermaid", text);
		}
		if (looksLikeMarkdownTable(text)) {
			return new Block(Kind.TABLE, "table", text);
		}
		return null;
	}

	private static Block parseFenced(final String text) {
		final int lineEnd = text.indexOf('\n');
		if (lineEnd < 0) {
			return null;
		}
		final String firstLine = text.substring(0, lineEnd).trim();
		final String rest;
		final int close = text.lastIndexOf("```");
		if (close > lineEnd) {
			rest = text.substring(lineEnd + 1, close).trim();
		}
		else {
			rest = text.substring(lineEnd + 1).trim();
		}
		final String lower = firstLine.toLowerCase();
		if (lower.startsWith("```twigmark")) {
			final String type = extractTwigmarkType(firstLine);
			return blockForType(type, rest);
		}
		if (lower.startsWith("```mermaid") || lower.equals("```mmd")) {
			return new Block(Kind.MERMAID, "mermaid", rest);
		}
		if (lower.startsWith("```plantuml") || lower.equals("```puml") || lower.equals("```uml")) {
			return new Block(Kind.PLANTUML, "plantuml", rest);
		}
		if (lower.startsWith("```math") || lower.startsWith("```latex") || lower.equals("```tex")) {
			return new Block(Kind.MATH, "math", rest);
		}
		if (lower.startsWith("```table") || lower.equals("```md-table")) {
			return new Block(Kind.TABLE, "table", rest);
		}
		if (firstLine.startsWith("```") && firstLine.length() > 3) {
			final String lang = firstLine.substring(3).trim();
			if (lang.length() > 0 && !lang.contains(" ")) {
				return new Block(Kind.CODE, lang.toLowerCase(), rest);
			}
		}
		return null;
	}

	private static Block blockForType(final String type, final String source) {
		if (type == null || type.length() == 0) {
			return null;
		}
		final String t = type.toLowerCase();
		if ("mermaid".equals(t) || "mmd".equals(t)) {
			return new Block(Kind.MERMAID, "mermaid", source);
		}
		if ("plantuml".equals(t) || "puml".equals(t) || "uml".equals(t)) {
			return new Block(Kind.PLANTUML, "plantuml", source);
		}
		if ("math".equals(t) || "latex".equals(t) || "tex".equals(t)) {
			return new Block(Kind.MATH, "math", source);
		}
		if ("table".equals(t)) {
			return new Block(Kind.TABLE, "table", source);
		}
		if ("code".equals(t)) {
			return new Block(Kind.CODE, "text", source);
		}
		return new Block(Kind.CODE, t, source);
	}

	private static String extractTwigmarkType(final String firstLine) {
		final String lower = firstLine.toLowerCase();
		final int typeIdx = lower.indexOf("type=");
		if (typeIdx < 0) {
			return "";
		}
		String tail = firstLine.substring(typeIdx + 5).trim();
		final int space = tail.indexOf(' ');
		if (space >= 0) {
			tail = tail.substring(0, space);
		}
		return tail.trim();
	}

	static boolean looksLikeMarkdownTable(final String text) {
		final String[] lines = text.split("\n");
		int tableLines = 0;
		for (int i = 0; i < lines.length; i++) {
			final String line = lines[i].trim();
			if (line.length() == 0) {
				continue;
			}
			if (line.indexOf('|') >= 0 && line.replace("|", "").trim().length() > 0) {
				tableLines++;
				if (tableLines >= 2) {
					return true;
				}
			}
			else if (tableLines > 0) {
				return tableLines >= 2;
			}
		}
		return false;
	}
}
