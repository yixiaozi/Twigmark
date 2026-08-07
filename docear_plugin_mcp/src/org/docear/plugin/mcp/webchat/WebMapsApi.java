package org.docear.plugin.mcp.webchat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.docear.plugin.mcp.json.JsonParser;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.docear.plugin.mcp.service.McpMindMapService;
import org.freeplane.core.util.LogUtils;

import com.sun.net.httpserver.HttpExchange;

/** Session-authenticated read-only mind-map library APIs for the web UI. */
public final class WebMapsApi {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private WebMapsApi() {
	}

	public static void handleListMaps(final HttpExchange exchange) throws IOException {
		try {
			WebchatService.requireUsername(WebchatApi.extractSessionToken(exchange));
			final Map q = queryParams(exchange);
			final String query = str(q, "q");
			final int limit = intParam(q, "limit", 500);
			final String json = McpMindMapService.listMindMapsForWeb(query, limit);
			writeJson(exchange, 200, json);
		}
		catch (IllegalArgumentException e) {
			writeJson(exchange, 401, error(e.getMessage()));
		}
		catch (IOException e) {
			// Client aborted (e.g. navigated away) — avoid noisy stack traces.
			LogUtils.warn("list maps aborted: " + e.getMessage());
		}
		catch (Exception e) {
			LogUtils.warn("list maps failed: " + e.getMessage(), e);
			try {
				writeJson(exchange, 500, error(e.getMessage()));
			}
			catch (IOException ignored) {
			}
		}
	}

	public static void handleGetMapJson(final HttpExchange exchange) throws IOException {
		try {
			WebchatService.requireUsername(WebchatApi.extractSessionToken(exchange));
			final Map q = queryParams(exchange);
			final String path = str(q, "path");
			if (path.length() == 0) {
				writeJson(exchange, 400, error("path is required"));
				return;
			}
			final int maxDepth = intParam(q, "maxDepth", 16);
			final boolean includeFolded = !"false".equalsIgnoreCase(str(q, "includeFolded"));
			final String json = McpMindMapService.getMindmapJson(path, maxDepth, includeFolded);
			writeJson(exchange, 200, json);
		}
		catch (IllegalArgumentException e) {
			final boolean auth = e.getMessage() != null && (e.getMessage().indexOf("login") >= 0
					|| e.getMessage().indexOf("session") >= 0);
			writeJson(exchange, auth ? 401 : 400, error(e.getMessage()));
		}
		catch (Exception e) {
			LogUtils.warn("get map json failed: " + e.getMessage(), e);
			writeJson(exchange, 500, error(WebSecurity.safePublicError()));
		}
	}

	public static void handleSearch(final HttpExchange exchange) throws IOException {
		try {
			WebchatService.requireUsername(WebchatApi.extractSessionToken(exchange));
			final Map q = queryParams(exchange);
			final String query = str(q, "q");
			final String filePath = str(q, "path");
			final int limit = intParam(q, "limit", 40);
			final int days = intParam(q, "modifiedWithinDays", 0);
			if (query.length() == 0 && filePath.length() == 0) {
				writeJson(exchange, 400, error("q or path is required"));
				return;
			}
			final String json = McpMindMapService.searchNodes(query, limit, days,
					filePath.length() == 0 ? null : filePath, null);
			final JsonValue hits = JsonParser.parse(json);
			final Map out = new LinkedHashMap();
			out.put("hits", hits);
			out.put("count", JsonValue.ofNumber(Integer.valueOf(hits.asList().size())));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(out)));
		}
		catch (IllegalArgumentException e) {
			final boolean auth = e.getMessage() != null && (e.getMessage().indexOf("login") >= 0
					|| e.getMessage().indexOf("session") >= 0);
			writeJson(exchange, auth ? 401 : 400, error(e.getMessage()));
		}
		catch (Exception e) {
			LogUtils.warn("map search failed: " + e.getMessage(), e);
			writeJson(exchange, 500, error(e.getMessage()));
		}
	}

	private static Map queryParams(final HttpExchange exchange) {
		final Map map = new LinkedHashMap();
		final URI uri = exchange.getRequestURI();
		final String raw = uri == null ? null : uri.getRawQuery();
		if (raw == null || raw.length() == 0) {
			return map;
		}
		final String[] parts = raw.split("&");
		for (int i = 0; i < parts.length; i++) {
			final String part = parts[i];
			if (part.length() == 0) {
				continue;
			}
			final int eq = part.indexOf('=');
			String key;
			String value;
			if (eq < 0) {
				key = decode(part);
				value = "";
			}
			else {
				key = decode(part.substring(0, eq));
				value = decode(part.substring(eq + 1));
			}
			if (key.length() > 0) {
				map.put(key, value);
			}
		}
		return map;
	}

	private static String decode(final String value) {
		try {
			return URLDecoder.decode(value, "UTF-8");
		}
		catch (Exception e) {
			return value;
		}
	}

	private static String str(final Map q, final String key) {
		final Object v = q.get(key);
		return v == null ? "" : String.valueOf(v).trim();
	}

	private static int intParam(final Map q, final String key, final int fallback) {
		final String raw = str(q, key);
		if (raw.length() == 0) {
			return fallback;
		}
		try {
			return Integer.parseInt(raw);
		}
		catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static String error(final String message) {
		final Map map = new LinkedHashMap();
		map.put("error", JsonValue.ofString(message == null ? "error" : message));
		return JsonWriter.write(JsonValue.ofMap(map));
	}

	private static void writeJson(final HttpExchange exchange, final int status, final String body) throws IOException {
		final byte[] bytes = body.getBytes(UTF8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length);
		final OutputStream stream = exchange.getResponseBody();
		stream.write(bytes);
		stream.close();
	}
}
