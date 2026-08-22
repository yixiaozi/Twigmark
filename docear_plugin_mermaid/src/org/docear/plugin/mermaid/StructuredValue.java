package org.docear.plugin.mermaid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parsed JSON/YAML value tree for small-data preview. */
final class StructuredValue {

	enum Kind {
		OBJECT, ARRAY, SCALAR
	}

	final Kind kind;
	final String scalar;
	final Map<String, StructuredValue> fields;
	final List<StructuredValue> items;

	private StructuredValue(final Kind kind, final String scalar, final Map<String, StructuredValue> fields,
			final List<StructuredValue> items) {
		this.kind = kind;
		this.scalar = scalar;
		this.fields = fields;
		this.items = items;
	}

	static StructuredValue object(final Map<String, StructuredValue> fields) {
		return new StructuredValue(Kind.OBJECT, null, fields, null);
	}

	static StructuredValue array(final List<StructuredValue> items) {
		return new StructuredValue(Kind.ARRAY, null, null, items);
	}

	static StructuredValue scalar(final String value) {
		return new StructuredValue(Kind.SCALAR, value, null, null);
	}

	static StructuredValue emptyObject() {
		return object(new LinkedHashMap<String, StructuredValue>());
	}

	static StructuredValue emptyArray() {
		return array(new ArrayList<StructuredValue>());
	}

	int entryCount() {
		if (kind == Kind.OBJECT) {
			return fields.size();
		}
		if (kind == Kind.ARRAY) {
			return items.size();
		}
		return 1;
	}
}
