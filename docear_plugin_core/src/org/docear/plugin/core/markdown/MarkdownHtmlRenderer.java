package org.docear.plugin.core.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown → HTML for the in-app preview (Java 8 / Swing HTMLEditorKit).
 */
public final class MarkdownHtmlRenderer {

	private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]+)\\)");
	private static final Pattern CODE = Pattern.compile("`([^`]+)`");
	private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
	private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)");
	private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.+)\\|$");
	private static final Pattern TABLE_SEP = Pattern.compile("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$");

	private MarkdownHtmlRenderer() {
	}

	public static String toHtml(final String markdown) {
		if (markdown == null || markdown.length() == 0) {
			return wrap("");
		}
		final String text = markdown.replace("\r\n", "\n").replace('\r', '\n');
		final String[] lines = text.split("\n", -1);
		final StringBuilder body = new StringBuilder();
		boolean inCode = false;
		final StringBuilder codeBlock = new StringBuilder();
		final List<String[]> tableRows = new ArrayList<String[]>();
		boolean inTable = false;

		for (int i = 0; i < lines.length; i++) {
			final String raw = lines[i];

			if (raw.trim().startsWith("```")) {
				flushTable(body, tableRows, inTable);
				inTable = false;
				if (!inCode) {
					inCode = true;
					codeBlock.setLength(0);
				}
				else {
					inCode = false;
					body.append("<pre class='code'><code>")
					        .append(escapeHtml(codeBlock.toString()))
					        .append("</code></pre>\n");
				}
				continue;
			}
			if (inCode) {
				if (codeBlock.length() > 0) {
					codeBlock.append('\n');
				}
				codeBlock.append(raw);
				continue;
			}

			final String trimmed = raw.trim();
			if (trimmed.length() == 0) {
				flushTable(body, tableRows, inTable);
				inTable = false;
				continue;
			}

			if (isTableRow(trimmed)) {
				if (TABLE_SEP.matcher(trimmed).matches()) {
					inTable = true;
					continue;
				}
				final String[] cells = splitTableRow(trimmed);
				tableRows.add(cells);
				inTable = true;
				continue;
			}
			flushTable(body, tableRows, inTable);
			inTable = false;

			if (trimmed.startsWith("###### ")) {
				body.append("<h6>").append(formatInline(trimmed.substring(7))).append("</h6>\n");
			}
			else if (trimmed.startsWith("##### ")) {
				body.append("<h5>").append(formatInline(trimmed.substring(6))).append("</h5>\n");
			}
			else if (trimmed.startsWith("#### ")) {
				body.append("<h4>").append(formatInline(trimmed.substring(5))).append("</h4>\n");
			}
			else if (trimmed.startsWith("### ")) {
				body.append("<h3>").append(formatInline(trimmed.substring(4))).append("</h3>\n");
			}
			else if (trimmed.startsWith("## ")) {
				body.append("<h2>").append(formatInline(trimmed.substring(3))).append("</h2>\n");
			}
			else if (trimmed.startsWith("# ")) {
				body.append("<h1>").append(formatInline(trimmed.substring(2))).append("</h1>\n");
			}
			else if (trimmed.startsWith("> ")) {
				body.append("<blockquote><p>").append(formatInline(trimmed.substring(2))).append("</p></blockquote>\n");
			}
			else if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
				body.append("<ul><li>").append(formatInline(trimmed.substring(2))).append("</li></ul>\n");
			}
			else if (trimmed.matches("\\d+\\.\\s+.*")) {
				final int dot = trimmed.indexOf('.');
				body.append("<ol start='").append(trimmed.substring(0, dot)).append("'><li>")
				        .append(formatInline(trimmed.substring(dot + 1).trim())).append("</li></ol>\n");
			}
			else if (trimmed.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
				body.append("<hr/>\n");
			}
			else {
				body.append("<p>").append(formatInline(trimmed)).append("</p>\n");
			}
		}
		flushTable(body, tableRows, inTable);
		if (inCode && codeBlock.length() > 0) {
			body.append("<pre class='code'><code>")
			        .append(escapeHtml(codeBlock.toString()))
			        .append("</code></pre>\n");
		}
		return wrap(body.toString());
	}

	private static void flushTable(final StringBuilder body, final List<String[]> rows, final boolean inTable) {
		if (!inTable || rows.isEmpty()) {
			rows.clear();
			return;
		}
		body.append("<table>\n");
		for (int r = 0; r < rows.size(); r++) {
			final String[] cells = rows.get(r);
			body.append("<tr>");
			final String tag = r == 0 ? "th" : "td";
			for (int c = 0; c < cells.length; c++) {
				body.append('<').append(tag).append('>')
				        .append(formatInline(cells[c].trim()))
				        .append("</").append(tag).append('>');
			}
			body.append("</tr>\n");
		}
		body.append("</table>\n");
		rows.clear();
	}

	private static boolean isTableRow(final String trimmed) {
		return trimmed.indexOf('|') >= 0 && (trimmed.startsWith("|") || trimmed.endsWith("|"));
	}

	private static String[] splitTableRow(final String trimmed) {
		String row = trimmed;
		if (row.startsWith("|")) {
			row = row.substring(1);
		}
		if (row.endsWith("|")) {
			row = row.substring(0, row.length() - 1);
		}
		return row.split("\\|", -1);
	}

	private static String wrap(final String body) {
		return "<html><head><style type='text/css'>"
		        + "body{font-family:'Segoe UI','Microsoft YaHei UI','Microsoft YaHei',sans-serif;"
		        + "font-size:14px;line-height:1.65;color:#1f2328;background:#ffffff;"
		        + "margin:0;padding:20px 56px 28px 28px;}"
		        + "h1{font-size:1.75em;font-weight:700;margin:0.4em 0 0.6em;padding-bottom:0.3em;"
		        + "border-bottom:1px solid #d0d7de;color:#1f2328;}"
		        + "h2{font-size:1.35em;font-weight:650;margin:1.2em 0 0.5em;padding-bottom:0.25em;"
		        + "border-bottom:1px solid #d8dee4;color:#1f2328;}"
		        + "h3{font-size:1.15em;font-weight:600;margin:1em 0 0.4em;color:#1f2328;}"
		        + "h4,h5,h6{font-size:1em;font-weight:600;margin:0.9em 0 0.35em;color:#1f2328;}"
		        + "p{margin:0.55em 0;}"
		        + "ul,ol{margin:0.4em 0 0.6em 1.2em;padding:0;}"
		        + "li{margin:0.25em 0;}"
		        + "blockquote{margin:0.7em 0;padding:0.2em 0 0.2em 0.9em;border-left:4px solid #d0d7de;"
		        + "color:#656d76;}"
		        + "hr{border:none;border-top:1px solid #d0d7de;margin:1.2em 0;}"
		        + "code{font-family:Consolas,'Cascadia Mono','Courier New',monospace;font-size:0.92em;"
		        + "background:#f6f8fa;color:#1f2328;padding:0.15em 0.4em;border-radius:4px;}"
		        + "pre.code{background:#f6f8fa;border:1px solid #d0d7de;border-radius:6px;"
		        + "padding:12px 14px;overflow:auto;margin:0.8em 0;}"
		        + "pre.code code{background:transparent;padding:0;}"
		        + "a{color:#0969da;text-decoration:none;background-color:transparent;}"
		        + "a:hover{text-decoration:underline;}"
		        + "table{border-collapse:collapse;margin:0.8em 0;width:auto;}"
		        + "th,td{border:1px solid #d0d7de;padding:6px 12px;text-align:left;}"
		        + "th{background:#f6f8fa;font-weight:600;}"
		        + "</style></head><body>" + body + "</body></html>";
	}

	private static String formatInline(final String line) {
		String result = escapeHtml(line);
		result = apply(CODE, result, true);
		result = apply(BOLD, result, false);
		result = apply(ITALIC, result, false);
		final Matcher m = LINK.matcher(result);
		final StringBuffer sb = new StringBuffer();
		while (m.find()) {
			final String label = m.group(1);
			final String href = m.group(2).replace("\"", "&quot;");
			final String repl = "<a href=\"" + href + "\">" + label + "</a>";
			m.appendReplacement(sb, Matcher.quoteReplacement(repl));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/** @param asCode wrap group1 in {@code <code>}; else bold/italic tags from pattern kind */
	private static String apply(final Pattern pattern, final String input, final boolean asCode) {
		final Matcher m = pattern.matcher(input);
		final StringBuffer sb = new StringBuffer();
		while (m.find()) {
			final String g1 = m.group(1);
			final String repl;
			if (asCode) {
				repl = "<code>" + g1 + "</code>";
			}
			else if (pattern == BOLD) {
				repl = "<b>" + g1 + "</b>";
			}
			else {
				repl = "<i>" + g1 + "</i>";
			}
			m.appendReplacement(sb, Matcher.quoteReplacement(repl));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private static String escapeHtml(final String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
