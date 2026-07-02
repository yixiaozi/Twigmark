package org.docear.plugin.mcp.json;

import java.util.List;
import java.util.Map;

public final class JsonWriter {

	private JsonWriter() {
	}

	public static String write(final JsonValue value) {
		final StringBuilder builder = new StringBuilder();
		append(builder, value);
		return builder.toString();
	}

	public static String writeMap(final Map<String, JsonValue> map) {
		return write(JsonValue.ofMap(map));
	}

	private static void append(final StringBuilder builder, final JsonValue value) {
		if (value == null || value.isNull()) {
			builder.append("null");
			return;
		}
		final Object raw = value.raw();
		if (raw instanceof String) {
			builder.append('"');
			escape(builder, (String) raw);
			builder.append('"');
			return;
		}
		if (raw instanceof Boolean) {
			builder.append(((Boolean) raw).booleanValue() ? "true" : "false");
			return;
		}
		if (raw instanceof Number) {
			builder.append(raw);
			return;
		}
		if (raw instanceof Map) {
			builder.append('{');
			boolean first = true;
			for (final Map.Entry<String, JsonValue> entry : value.asMap().entrySet()) {
				if (!first) {
					builder.append(',');
				}
				first = false;
				builder.append('"');
				escape(builder, entry.getKey());
				builder.append('"').append(':');
				append(builder, entry.getValue());
			}
			builder.append('}');
			return;
		}
		if (raw instanceof List) {
			builder.append('[');
			boolean first = true;
			for (final JsonValue item : value.asList()) {
				if (!first) {
					builder.append(',');
				}
				first = false;
				append(builder, item);
			}
			builder.append(']');
		}
	}

	private static void escape(final StringBuilder builder, final String text) {
		for (int i = 0; i < text.length(); i++) {
			final char ch = text.charAt(i);
			if (ch == '"') {
				builder.append("\\\"");
			}
			else if (ch == '\\') {
				builder.append("\\\\");
			}
			else if (ch == '\n') {
				builder.append("\\n");
			}
			else if (ch == '\r') {
				builder.append("\\r");
			}
			else if (ch == '\t') {
				builder.append("\\t");
			}
			else {
				builder.append(ch);
			}
		}
	}
}
