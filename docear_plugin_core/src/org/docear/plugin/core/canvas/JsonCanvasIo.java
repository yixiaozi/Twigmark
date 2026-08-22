package org.docear.plugin.core.canvas;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read/write JSON Canvas 1.0 without an external JSON library. */
public final class JsonCanvasIo {

	private static final Charset UTF8 = Charset.forName("UTF-8");

	private JsonCanvasIo() {
	}

	public static JsonCanvasDocument read(final File file) throws Exception {
		final InputStreamReader reader = new InputStreamReader(new FileInputStream(file), UTF8);
		final StringBuilder sb = new StringBuilder();
		try {
			final char[] buf = new char[4096];
			int n;
			while ((n = reader.read(buf)) >= 0) {
				sb.append(buf, 0, n);
			}
		}
		finally {
			reader.close();
		}
		return parse(sb.toString());
	}

	public static void write(final File file, final JsonCanvasDocument doc) throws Exception {
		final File parent = file.getParentFile();
		if (parent != null && !parent.isDirectory()) {
			parent.mkdirs();
		}
		final OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), UTF8);
		try {
			writer.write(toJson(doc));
		}
		finally {
			writer.close();
		}
	}

	public static JsonCanvasDocument parse(final String raw) {
		final JsonCanvasDocument doc = new JsonCanvasDocument();
		if (raw == null || raw.trim().length() == 0) {
			return doc;
		}
		final JsonReader reader = new JsonReader(raw);
		final Object root = reader.readValue();
		if (!(root instanceof Map)) {
			throw new IllegalArgumentException("canvas root must be an object");
		}
		@SuppressWarnings("unchecked")
		final Map<String, Object> map = (Map<String, Object>) root;
		final Object nodesObj = map.get("nodes");
		if (nodesObj instanceof List) {
			final List<?> list = (List<?>) nodesObj;
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i) instanceof Map) {
					@SuppressWarnings("unchecked")
					final Map<String, Object> n = (Map<String, Object>) list.get(i);
					doc.getNodes().add(nodeFromMap(n));
				}
			}
		}
		final Object edgesObj = map.get("edges");
		if (edgesObj instanceof List) {
			final List<?> list = (List<?>) edgesObj;
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i) instanceof Map) {
					@SuppressWarnings("unchecked")
					final Map<String, Object> e = (Map<String, Object>) list.get(i);
					doc.getEdges().add(edgeFromMap(e));
				}
			}
		}
		return doc;
	}

	public static String toJson(final JsonCanvasDocument doc) {
		final StringBuilder sb = new StringBuilder();
		sb.append("{\n  \"nodes\": [\n");
		final List<JsonCanvasNode> nodes = doc.getNodes();
		for (int i = 0; i < nodes.size(); i++) {
			if (i > 0) {
				sb.append(",\n");
			}
			appendNode(sb, nodes.get(i));
		}
		sb.append("\n  ],\n  \"edges\": [\n");
		final List<JsonCanvasEdge> edges = doc.getEdges();
		for (int i = 0; i < edges.size(); i++) {
			if (i > 0) {
				sb.append(",\n");
			}
			appendEdge(sb, edges.get(i));
		}
		sb.append("\n  ]\n}\n");
		return sb.toString();
	}

	private static JsonCanvasNode nodeFromMap(final Map<String, Object> map) {
		final JsonCanvasNode n = new JsonCanvasNode();
		n.setId(str(map.get("id")));
		n.setType(str(map.get("type")));
		n.setX(num(map.get("x")));
		n.setY(num(map.get("y")));
		if (map.get("width") != null) {
			n.setWidth(num(map.get("width")));
		}
		if (map.get("height") != null) {
			n.setHeight(num(map.get("height")));
		}
		n.setColor(str(map.get("color")));
		n.setText(str(map.get("text")));
		n.setFile(str(map.get("file")));
		n.setSubpath(str(map.get("subpath")));
		n.setUrl(str(map.get("url")));
		n.setLabel(str(map.get("label")));
		return n;
	}

	private static JsonCanvasEdge edgeFromMap(final Map<String, Object> map) {
		final JsonCanvasEdge e = new JsonCanvasEdge();
		e.setId(str(map.get("id")));
		e.setFromNode(str(map.get("fromNode")));
		e.setToNode(str(map.get("toNode")));
		e.setFromSide(str(map.get("fromSide")));
		e.setToSide(str(map.get("toSide")));
		e.setFromEnd(str(map.get("fromEnd")));
		e.setToEnd(str(map.get("toEnd")));
		e.setColor(str(map.get("color")));
		e.setLabel(str(map.get("label")));
		return e;
	}

	private static void appendNode(final StringBuilder sb, final JsonCanvasNode n) {
		sb.append("    {");
		field(sb, "id", n.getId(), true);
		field(sb, "type", n.getType(), false);
		numField(sb, "x", n.getX());
		numField(sb, "y", n.getY());
		numField(sb, "width", n.getWidth());
		numField(sb, "height", n.getHeight());
		opt(sb, "color", n.getColor());
		opt(sb, "text", n.getText());
		opt(sb, "file", n.getFile());
		opt(sb, "subpath", n.getSubpath());
		opt(sb, "url", n.getUrl());
		opt(sb, "label", n.getLabel());
		sb.append(" }");
	}

	private static void appendEdge(final StringBuilder sb, final JsonCanvasEdge e) {
		sb.append("    {");
		field(sb, "id", e.getId(), true);
		field(sb, "fromNode", e.getFromNode(), false);
		field(sb, "toNode", e.getToNode(), false);
		opt(sb, "fromSide", e.getFromSide());
		opt(sb, "toSide", e.getToSide());
		opt(sb, "fromEnd", e.getFromEnd());
		opt(sb, "toEnd", e.getToEnd());
		opt(sb, "color", e.getColor());
		opt(sb, "label", e.getLabel());
		sb.append(" }");
	}

	private static void field(final StringBuilder sb, final String key, final String value, final boolean first) {
		if (!first) {
			sb.append(", ");
		}
		sb.append('"').append(key).append("\": ").append(quote(value));
	}

	private static void numField(final StringBuilder sb, final String key, final int value) {
		sb.append(", \"").append(key).append("\": ").append(value);
	}

	private static void opt(final StringBuilder sb, final String key, final String value) {
		if (value != null && value.length() > 0) {
			sb.append(", \"").append(key).append("\": ").append(quote(value));
		}
	}

	private static String str(final Object o) {
		return o == null ? null : String.valueOf(o);
	}

	private static int num(final Object o) {
		if (o instanceof Number) {
			return ((Number) o).intValue();
		}
		if (o == null) {
			return 0;
		}
		try {
			return (int) Double.parseDouble(String.valueOf(o));
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

	static String quote(final String raw) {
		if (raw == null) {
			return "null";
		}
		final StringBuilder sb = new StringBuilder();
		sb.append('"');
		for (int i = 0; i < raw.length(); i++) {
			final char c = raw.charAt(i);
			switch (c) {
				case '"':
					sb.append("\\\"");
					break;
				case '\\':
					sb.append("\\\\");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				default:
					if (c < 32) {
						sb.append(String.format("\\u%04x", (int) c));
					}
					else {
						sb.append(c);
					}
			}
		}
		sb.append('"');
		return sb.toString();
	}

	private static final class JsonReader {
		private final String text;
		private int pos;

		JsonReader(final String text) {
			this.text = text;
		}

		Object readValue() {
			skipWs();
			if (pos >= text.length()) {
				throw new IllegalArgumentException("unexpected end of JSON");
			}
			final char c = text.charAt(pos);
			if (c == '{') {
				return readObject();
			}
			if (c == '[') {
				return readArray();
			}
			if (c == '"') {
				return readString();
			}
			if (c == 't' || c == 'f' || c == 'n' || c == '-' || (c >= '0' && c <= '9')) {
				return readLiteral();
			}
			throw new IllegalArgumentException("invalid JSON at " + pos);
		}

		Map<String, Object> readObject() {
			expect('{');
			final Map<String, Object> map = new LinkedHashMap<String, Object>();
			skipWs();
			if (peek() == '}') {
				pos++;
				return map;
			}
			while (pos < text.length()) {
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
			}
			return map;
		}

		List<Object> readArray() {
			expect('[');
			final List<Object> list = new ArrayList<Object>();
			skipWs();
			if (peek() == ']') {
				pos++;
				return list;
			}
			while (pos < text.length()) {
				list.add(readValue());
				skipWs();
				final char c = next();
				if (c == ']') {
					break;
				}
				if (c != ',') {
					throw new IllegalArgumentException("invalid JSON array");
				}
			}
			return list;
		}

		String readString() {
			expect('"');
			final StringBuilder sb = new StringBuilder();
			while (pos < text.length()) {
				char c = next();
				if (c == '"') {
					return sb.toString();
				}
				if (c == '\\') {
					c = next();
					if (c == 'u') {
						sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
						pos += 4;
					}
					else if (c == 'n') {
						sb.append('\n');
					}
					else if (c == 'r') {
						sb.append('\r');
					}
					else if (c == 't') {
						sb.append('\t');
					}
					else {
						sb.append(c);
					}
				}
				else {
					sb.append(c);
				}
			}
			throw new IllegalArgumentException("unterminated string");
		}

		Object readLiteral() {
			final int start = pos;
			while (pos < text.length()) {
				final char c = text.charAt(pos);
				if (c == ',' || c == '}' || c == ']' || c == ' ' || c == '\n' || c == '\r' || c == '\t') {
					break;
				}
				pos++;
			}
			final String token = text.substring(start, pos);
			if ("true".equals(token)) {
				return Boolean.TRUE;
			}
			if ("false".equals(token)) {
				return Boolean.FALSE;
			}
			if ("null".equals(token)) {
				return null;
			}
			try {
				if (token.indexOf('.') >= 0 || token.indexOf('e') >= 0 || token.indexOf('E') >= 0) {
					return Double.valueOf(token);
				}
				return Long.valueOf(token);
			}
			catch (NumberFormatException e) {
				return token;
			}
		}

		void skipWs() {
			while (pos < text.length()) {
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
			return pos >= text.length() ? '\0' : text.charAt(pos);
		}

		char next() {
			return pos >= text.length() ? '\0' : text.charAt(pos++);
		}

		void expect(final char ch) {
			if (next() != ch) {
				throw new IllegalArgumentException("expected '" + ch + "' at " + Math.max(0, pos - 1));
			}
		}
	}
}
