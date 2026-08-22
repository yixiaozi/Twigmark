package org.docear.plugin.mermaid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lightweight JSON/YAML parser for small structured node previews. */
final class StructuredDataParser {

	private static final int MAX_SOURCE_CHARS = 16000;
	private static final int MAX_ENTRIES = 80;

	private StructuredDataParser() {
	}

	static String normalizeSource(final String raw, final String formatHint) {
		if (raw == null) {
			throw new IllegalArgumentException("empty structured data");
		}
		String text = raw.trim();
		if (text.length() == 0) {
			throw new IllegalArgumentException("empty structured data");
		}
		if (text.startsWith("```")) {
			final int lineEnd = text.indexOf('\n');
			if (lineEnd > 0) {
				final int close = text.lastIndexOf("```");
				if (close > lineEnd) {
					text = text.substring(lineEnd + 1, close).trim();
				}
				else {
					text = text.substring(lineEnd + 1).trim();
				}
			}
		}
		if (text.length() > MAX_SOURCE_CHARS) {
			throw new IllegalArgumentException("structured data too large");
		}
		return text;
	}

	static StructuredValue parse(final String raw, final String formatHint) throws IllegalArgumentException {
		final String text = normalizeSource(raw, formatHint);
		final String fmt = formatHint != null ? formatHint.toLowerCase() : "";
		if ("yaml".equals(fmt) || "yml".equals(fmt)) {
			return parseYaml(text);
		}
		if ("json".equals(fmt) || "jsonc".equals(fmt)) {
			return parseJson(text);
		}
		if (text.startsWith("{") || text.startsWith("[")) {
			return parseJson(text);
		}
		return parseYaml(text);
	}

	static boolean looksLikeJson(final String text) {
		if (text == null) {
			return false;
		}
		final String t = text.trim();
		if (!t.startsWith("{") && !t.startsWith("[")) {
			return false;
		}
		if (ExcalidrawNormalize.looksLikeJson(t)) {
			return false;
		}
		try {
			parseJson(t);
			return true;
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}

	static boolean looksLikeYaml(final String text) {
		if (text == null) {
			return false;
		}
		final String t = text.trim();
		if (t.startsWith("{") || t.startsWith("[") || t.startsWith("```")) {
			return false;
		}
		if (ExcalidrawNormalize.looksLikeJson(t)) {
			return false;
		}
		int kvLines = 0;
		final String[] lines = t.split("\n");
		for (int i = 0; i < lines.length && i < 12; i++) {
			final String line = lines[i].trim();
			if (line.length() == 0 || line.startsWith("#")) {
				continue;
			}
			if (line.startsWith("- ") || line.indexOf(':') > 0) {
				kvLines++;
			}
		}
		if (kvLines < 2) {
			return false;
		}
		try {
			parseYaml(t);
			return true;
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static StructuredValue parseJson(final String text) {
		final JsonReader reader = new JsonReader(text);
		final StructuredValue value = reader.readValue();
		reader.skipWs();
		if (!reader.eof()) {
			throw new IllegalArgumentException("invalid JSON: trailing content");
		}
		return limitDepth(value, 0);
	}

	private static StructuredValue parseYaml(final String text) {
		final String[] rawLines = text.split("\n", -1);
		final List<YamlLine> lines = new ArrayList<YamlLine>();
		for (int i = 0; i < rawLines.length; i++) {
			final YamlLine line = parseYamlLine(rawLines[i]);
			if (line != null) {
				lines.add(line);
			}
		}
		if (lines.isEmpty()) {
			throw new IllegalArgumentException("empty YAML");
		}
		final StructuredValue root = parseYamlBlock(lines, 0, lines.get(0).indent, lines.size());
		return limitDepth(root, 0);
	}

	private static YamlLine parseYamlLine(final String raw) {
		if (raw == null) {
			return null;
		}
		String line = raw;
		if (line.endsWith("\r")) {
			line = line.substring(0, line.length() - 1);
		}
		final int indent = countIndent(line);
		final String trimmed = line.trim();
		if (trimmed.length() == 0 || trimmed.startsWith("#")) {
			return null;
		}
		if (trimmed.equals("---") || trimmed.equals("...")) {
			return null;
		}
		return new YamlLine(indent, trimmed);
	}

	private static int countIndent(final String line) {
		int n = 0;
		for (int i = 0; i < line.length(); i++) {
			if (line.charAt(i) == ' ') {
				n++;
			}
			else {
				break;
			}
		}
		return n;
	}

	private static StructuredValue parseYamlBlock(final List<YamlLine> lines, final int start, final int indent,
			final int end) {
		int i = start;
		if (i < end && lines.get(i).trimmed.startsWith("- ")) {
			final List<StructuredValue> items = new ArrayList<StructuredValue>();
			while (i < end) {
				final YamlLine line = lines.get(i);
				if (line.indent < indent) {
					break;
				}
				if (line.indent != indent || !line.trimmed.startsWith("- ")) {
					break;
				}
				final String payload = line.trimmed.substring(2).trim();
				if (payload.length() == 0) {
					items.add(parseYamlBlock(lines, i + 1, indent + 2, end));
					i = skipYamlBlock(lines, i + 1, indent + 2, end);
				}
				else if (payload.indexOf(':') >= 0) {
					final int colon = payload.indexOf(':');
					final String key = payload.substring(0, colon).trim();
					final String rest = payload.substring(colon + 1).trim();
					if (rest.length() == 0) {
						final Map<String, StructuredValue> map = new LinkedHashMap<String, StructuredValue>();
						final StructuredValue nested = parseYamlBlock(lines, i + 1, indent + 2, end);
						map.put(key, nested);
						items.add(StructuredValue.object(map));
						i = skipYamlBlock(lines, i + 1, indent + 2, end);
					}
					else {
						final Map<String, StructuredValue> map = new LinkedHashMap<String, StructuredValue>();
						map.put(key, yamlScalar(rest));
						items.add(StructuredValue.object(map));
						i++;
					}
				}
				else {
					items.add(yamlScalar(payload));
					i++;
				}
				if (items.size() > MAX_ENTRIES) {
					break;
				}
			}
			return StructuredValue.array(items);
		}
		final Map<String, StructuredValue> map = new LinkedHashMap<String, StructuredValue>();
		while (i < end) {
			final YamlLine line = lines.get(i);
			if (line.indent < indent) {
				break;
			}
			if (line.indent != indent) {
				break;
			}
			final int colon = line.trimmed.indexOf(':');
			if (colon <= 0) {
				throw new IllegalArgumentException("invalid YAML line: " + line.trimmed);
			}
			final String key = line.trimmed.substring(0, colon).trim();
			final String rest = line.trimmed.substring(colon + 1).trim();
			if (rest.length() == 0) {
				map.put(key, parseYamlBlock(lines, i + 1, indent + 2, end));
				i = skipYamlBlock(lines, i + 1, indent + 2, end);
			}
			else {
				map.put(key, yamlScalar(rest));
				i++;
			}
			if (map.size() > MAX_ENTRIES) {
				break;
			}
		}
		return StructuredValue.object(map);
	}

	private static int skipYamlBlock(final List<YamlLine> lines, final int start, final int indent, final int end) {
		int i = start;
		while (i < end && lines.get(i).indent >= indent) {
			i++;
		}
		return i;
	}

	private static StructuredValue yamlScalar(final String raw) {
		String text = raw.trim();
		if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
			text = text.substring(1, text.length() - 1);
		}
		if ("null".equals(text) || "~".equals(text)) {
			return StructuredValue.scalar("null");
		}
		if ("true".equals(text) || "false".equals(text)) {
			return StructuredValue.scalar(text);
		}
		return StructuredValue.scalar(text);
	}

	private static StructuredValue limitDepth(final StructuredValue value, final int depth) {
		if (depth >= 6) {
			return StructuredValue.scalar("…");
		}
		if (value.kind == StructuredValue.Kind.SCALAR) {
			return StructuredValue.scalar(truncate(value.scalar, 120));
		}
		if (value.kind == StructuredValue.Kind.ARRAY) {
			final List<StructuredValue> items = new ArrayList<StructuredValue>();
			final int n = Math.min(value.items.size(), MAX_ENTRIES);
			for (int i = 0; i < n; i++) {
				items.add(limitDepth(value.items.get(i), depth + 1));
			}
			if (value.items.size() > n) {
				items.add(StructuredValue.scalar("… +" + (value.items.size() - n)));
			}
			return StructuredValue.array(items);
		}
		final Map<String, StructuredValue> fields = new LinkedHashMap<String, StructuredValue>();
		int count = 0;
		for (final Map.Entry<String, StructuredValue> e : value.fields.entrySet()) {
			if (count >= MAX_ENTRIES) {
				fields.put("…", StructuredValue.scalar("+" + (value.fields.size() - count)));
				break;
			}
			fields.put(e.getKey(), limitDepth(e.getValue(), depth + 1));
			count++;
		}
		return StructuredValue.object(fields);
	}

	private static String truncate(final String s, final int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}

	private static final class YamlLine {
		final int indent;
		final String trimmed;

		YamlLine(final int indent, final String trimmed) {
			this.indent = indent;
			this.trimmed = trimmed;
		}
	}

	private static final class JsonReader {
		private final String text;
		private int pos;

		JsonReader(final String text) {
			this.text = text;
		}

		boolean eof() {
			return pos >= text.length();
		}

		void skipWs() {
			while (!eof()) {
				final char c = text.charAt(pos);
				if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
					pos++;
				}
				else {
					break;
				}
			}
		}

		char peek() {
			return eof() ? '\0' : text.charAt(pos);
		}

		char next() {
			return eof() ? '\0' : text.charAt(pos++);
		}

		StructuredValue readValue() {
			skipWs();
			final char c = peek();
			if (c == '{') {
				return readObject();
			}
			if (c == '[') {
				return readArray();
			}
			if (c == '"') {
				return StructuredValue.scalar(readString());
			}
			return StructuredValue.scalar(readLiteral());
		}

		StructuredValue readObject() {
			expect('{');
			skipWs();
			final Map<String, StructuredValue> map = new LinkedHashMap<String, StructuredValue>();
			if (peek() == '}') {
				next();
				return StructuredValue.object(map);
			}
			while (!eof()) {
				skipWs();
				final String key = readString();
				skipWs();
				expect(':');
				map.put(key, readValue());
				skipWs();
				final char c = next();
				if (c == '}') {
					break;
				}
				if (c != ',') {
					throw new IllegalArgumentException("invalid JSON object");
				}
				if (map.size() > MAX_ENTRIES) {
					break;
				}
			}
			return StructuredValue.object(map);
		}

		StructuredValue readArray() {
			expect('[');
			skipWs();
			final List<StructuredValue> items = new ArrayList<StructuredValue>();
			if (peek() == ']') {
				next();
				return StructuredValue.array(items);
			}
			while (!eof()) {
				items.add(readValue());
				skipWs();
				final char c = next();
				if (c == ']') {
					break;
				}
				if (c != ',') {
					throw new IllegalArgumentException("invalid JSON array");
				}
				if (items.size() > MAX_ENTRIES) {
					break;
				}
			}
			return StructuredValue.array(items);
		}

		String readString() {
			expect('"');
			final StringBuilder sb = new StringBuilder();
			while (!eof()) {
				char c = next();
				if (c == '"') {
					return sb.toString();
				}
				if (c == '\\') {
					c = next();
					switch (c) {
						case '"':
						case '\\':
						case '/':
							sb.append(c);
							break;
						case 'b':
							sb.append('\b');
							break;
						case 'f':
							sb.append('\f');
							break;
						case 'n':
							sb.append('\n');
							break;
						case 'r':
							sb.append('\r');
							break;
						case 't':
							sb.append('\t');
							break;
						case 'u':
							sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
							pos += 4;
							break;
						default:
							sb.append(c);
					}
				}
				else {
					sb.append(c);
				}
			}
			throw new IllegalArgumentException("unterminated JSON string");
		}

		String readLiteral() {
			final int start = pos;
			while (!eof()) {
				final char c = text.charAt(pos);
				if (c == ',' || c == '}' || c == ']' || c == ' ' || c == '\n' || c == '\r' || c == '\t') {
					break;
				}
				pos++;
			}
			if (start == pos) {
				throw new IllegalArgumentException("invalid JSON value");
			}
			return text.substring(start, pos);
		}

		void expect(final char ch) {
			if (next() != ch) {
				throw new IllegalArgumentException("invalid JSON near " + Math.max(0, pos - 1));
			}
		}
	}
}
