package org.docear.plugin.mcp.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonValue {
	private final Object value;

	private JsonValue(final Object value) {
		this.value = value;
	}

	public static JsonValue ofNull() {
		return new JsonValue(null);
	}

	public static JsonValue ofString(final String value) {
		return new JsonValue(value);
	}

	public static JsonValue ofNumber(final Number value) {
		return new JsonValue(value);
	}

	public static JsonValue ofBoolean(final boolean value) {
		return new JsonValue(Boolean.valueOf(value));
	}

	public static JsonValue ofMap(final Map<String, JsonValue> map) {
		return new JsonValue(map);
	}

	public static JsonValue ofList(final List<JsonValue> list) {
		return new JsonValue(list);
	}

	public boolean isNull() {
		return value == null;
	}

	public String asString() {
		return value == null ? null : String.valueOf(value);
	}

	public boolean asBoolean() {
		if (value instanceof Boolean) {
			return ((Boolean) value).booleanValue();
		}
		return "true".equalsIgnoreCase(asString());
	}

	public int asInt(final int defaultValue) {
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		try {
			return Integer.parseInt(asString());
		}
		catch (Exception e) {
			return defaultValue;
		}
	}

	public long asLong(final long defaultValue) {
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		try {
			return Long.parseLong(asString());
		}
		catch (Exception e) {
			return defaultValue;
		}
	}

	@SuppressWarnings("unchecked")
	public Map<String, JsonValue> asMap() {
		if (value instanceof Map) {
			return (Map<String, JsonValue>) value;
		}
		return new LinkedHashMap<String, JsonValue>();
	}

	@SuppressWarnings("unchecked")
	public List<JsonValue> asList() {
		if (value instanceof List) {
			return (List<JsonValue>) value;
		}
		return new ArrayList<JsonValue>();
	}

	public Object raw() {
		return value;
	}

	public String toJson() {
		return JsonWriter.write(this);
	}
}
