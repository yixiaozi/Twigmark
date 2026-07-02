package org.docear.plugin.mcp.service;

import org.docear.plugin.mcp.json.JsonParser;

final class JsonParserHelper {
	private JsonParserHelper() {
	}

	static org.docear.plugin.mcp.json.JsonValue parse(final String json) {
		return JsonParser.parse(json);
	}
}
