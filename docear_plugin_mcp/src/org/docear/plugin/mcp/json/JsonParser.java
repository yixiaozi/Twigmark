package org.docear.plugin.mcp.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonParser {

	private final String json;
	private int index;

	private JsonParser(final String json) {
		this.json = json == null ? "" : json;
		this.index = 0;
	}

	public static JsonValue parse(final String json) {
		return new JsonParser(json).parseValue();
	}

	private JsonValue parseValue() {
		skipWhitespace();
		if (index >= json.length()) {
			return JsonValue.ofNull();
		}
		final char ch = json.charAt(index);
		if (ch == '{') {
			return parseObject();
		}
		if (ch == '[') {
			return parseArray();
		}
		if (ch == '"') {
			return JsonValue.ofString(parseString());
		}
		if (ch == 't' && json.startsWith("true", index)) {
			index += 4;
			return JsonValue.ofBoolean(true);
		}
		if (ch == 'f' && json.startsWith("false", index)) {
			index += 5;
			return JsonValue.ofBoolean(false);
		}
		if (ch == 'n' && json.startsWith("null", index)) {
			index += 4;
			return JsonValue.ofNull();
		}
		return parseNumber();
	}

	private JsonValue parseObject() {
		final Map<String, JsonValue> map = new LinkedHashMap<String, JsonValue>();
		index++;
		skipWhitespace();
		if (peek('}')) {
			index++;
			return JsonValue.ofMap(map);
		}
		while (index < json.length()) {
			skipWhitespace();
			final String key = parseString();
			skipWhitespace();
			expect(':');
			final JsonValue value = parseValue();
			map.put(key, value);
			skipWhitespace();
			if (peek('}')) {
				index++;
				break;
			}
			expect(',');
		}
		return JsonValue.ofMap(map);
	}

	private JsonValue parseArray() {
		final List<JsonValue> list = new ArrayList<JsonValue>();
		index++;
		skipWhitespace();
		if (peek(']')) {
			index++;
			return JsonValue.ofList(list);
		}
		while (index < json.length()) {
			list.add(parseValue());
			skipWhitespace();
			if (peek(']')) {
				index++;
				break;
			}
			expect(',');
		}
		return JsonValue.ofList(list);
	}

	private String parseString() {
		expect('"');
		final StringBuilder builder = new StringBuilder();
		while (index < json.length()) {
			final char ch = json.charAt(index++);
			if (ch == '"') {
				return builder.toString();
			}
			if (ch == '\\' && index < json.length()) {
				final char escaped = json.charAt(index++);
				if (escaped == 'n') {
					builder.append('\n');
				}
				else if (escaped == 'r') {
					builder.append('\r');
				}
				else if (escaped == 't') {
					builder.append('\t');
				}
				else {
					builder.append(escaped);
				}
			}
			else {
				builder.append(ch);
			}
		}
		return builder.toString();
	}

	private JsonValue parseNumber() {
		final int start = index;
		if (peek('-')) {
			index++;
		}
		while (index < json.length()) {
			final char ch = json.charAt(index);
			if ((ch >= '0' && ch <= '9') || ch == '.' || ch == 'e' || ch == 'E' || ch == '+' || ch == '-') {
				index++;
			}
			else {
				break;
			}
		}
		final String number = json.substring(start, index);
		if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
			return JsonValue.ofNumber(Double.valueOf(number));
		}
		try {
			return JsonValue.ofNumber(Long.valueOf(number));
		}
		catch (NumberFormatException e) {
			return JsonValue.ofNumber(Integer.valueOf(0));
		}
	}

	private void skipWhitespace() {
		while (index < json.length()) {
			final char ch = json.charAt(index);
			if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
				index++;
			}
			else {
				break;
			}
		}
	}

	private boolean peek(final char expected) {
		return index < json.length() && json.charAt(index) == expected;
	}

	private void expect(final char expected) {
		if (!peek(expected)) {
			throw new IllegalArgumentException("Expected '" + expected + "' at position " + index);
		}
		index++;
	}
}
