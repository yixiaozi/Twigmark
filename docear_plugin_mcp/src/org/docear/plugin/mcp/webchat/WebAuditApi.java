package org.docear.plugin.mcp.webchat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.audit.McpAuditQuery;
import org.docear.plugin.mcp.audit.McpAuditService;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.freeplane.core.util.LogUtils;

import com.sun.net.httpserver.HttpExchange;

/** Login-required MCP audit explorer for the web UI. */
public final class WebAuditApi {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private WebAuditApi() {
	}

	public static void handleMeta(final HttpExchange exchange) throws IOException {
		try {
			WebchatService.requireUsername(WebchatApi.extractSessionToken(exchange));
			final McpAuditQuery q = WebAuditFilters.parseQuery(queryMap(exchange));
			final Map body = new LinkedHashMap();
			body.put("summary", WebchatService.plainToJson(McpAuditService.summarizeForUi(q)));
			body.put("actors", JsonValue.ofList(stringList(McpAuditService.distinctForUi("actor"))));
			body.put("actions", JsonValue.ofList(stringList(McpAuditService.distinctForUi("action"))));
			body.put("dbCount", JsonValue.ofNumber(Integer.valueOf(McpAuditService.loadedAuditDatabaseCount())));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(body)));
		}
		catch (IllegalArgumentException e) {
			writeJson(exchange, 401, error(e.getMessage()));
		}
		catch (Exception e) {
			LogUtils.warn("audit meta failed: " + e.getMessage(), e);
			writeJson(exchange, 500, error(WebSecurity.safePublicError()));
		}
	}

	public static void handleEvents(final HttpExchange exchange) throws IOException {
		try {
			WebchatService.requireUsername(WebchatApi.extractSessionToken(exchange));
			final Map params = queryMap(exchange);
			final McpAuditQuery q = WebAuditFilters.parseQuery(params);
			final boolean writesOnly = WebAuditFilters.writesOnly(params);
			final int requested = WebAuditFilters.intParam(params, "limit", 200);
			if (writesOnly) {
				q.limit = WebAuditFilters.expandLimitForWriteFilter(requested);
			}
			List rows = McpAuditService.queryEventsForUi(q);
			if (writesOnly) {
				rows = WebAuditFilters.filterWrites(rows, requested);
			}
			WebAuditFilters.annotate(rows);
			final Map body = new LinkedHashMap();
			body.put("events", JsonValue.ofList(WebchatService.toJsonMaps(rows)));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(body)));
		}
		catch (IllegalArgumentException e) {
			writeJson(exchange, 401, error(e.getMessage()));
		}
		catch (Exception e) {
			LogUtils.warn("audit events failed: " + e.getMessage(), e);
			writeJson(exchange, 500, error(WebSecurity.safePublicError()));
		}
	}

	public static void handleTraces(final HttpExchange exchange) throws IOException {
		try {
			WebchatService.requireUsername(WebchatApi.extractSessionToken(exchange));
			final Map params = queryMap(exchange);
			final McpAuditQuery q = WebAuditFilters.parseQuery(params);
			final boolean writesOnly = WebAuditFilters.writesOnly(params);
			final int requested = WebAuditFilters.intParam(params, "limit", 80);
			if (writesOnly) {
				q.limit = WebAuditFilters.expandLimitForWriteFilter(requested);
			}
			List rows = McpAuditService.queryTracesForUi(q);
			if (writesOnly) {
				rows = WebAuditFilters.filterWriteTraces(rows, requested);
			}
			WebAuditFilters.annotateTraces(rows);
			final Map body = new LinkedHashMap();
			body.put("traces", JsonValue.ofList(WebchatService.toJsonMaps(rows)));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(body)));
		}
		catch (IllegalArgumentException e) {
			writeJson(exchange, 401, error(e.getMessage()));
		}
		catch (Exception e) {
			LogUtils.warn("audit traces failed: " + e.getMessage(), e);
			writeJson(exchange, 500, error(WebSecurity.safePublicError()));
		}
	}

	private static List stringList(final List values) {
		final List list = new ArrayList();
		if (values == null) {
			return list;
		}
		for (int i = 0; i < values.size(); i++) {
			list.add(JsonValue.ofString(String.valueOf(values.get(i))));
		}
		return list;
	}

	private static Map queryMap(final HttpExchange exchange) {
		final Map map = new LinkedHashMap();
		final java.net.URI uri = exchange.getRequestURI();
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
			final String key = eq < 0 ? decode(part) : decode(part.substring(0, eq));
			final String value = eq < 0 ? "" : decode(part.substring(eq + 1));
			if (key.length() > 0) {
				map.put(key, value);
			}
		}
		return map;
	}

	private static String decode(final String value) {
		try {
			return java.net.URLDecoder.decode(value == null ? "" : value, "UTF-8");
		}
		catch (Exception e) {
			return value == null ? "" : value;
		}
	}

	private static String error(final String message) {
		final Map map = new LinkedHashMap();
		map.put("error", JsonValue.ofString(message == null ? "error" : message));
		return JsonWriter.write(JsonValue.ofMap(map));
	}

	private static void writeJson(final HttpExchange exchange, final int code, final String json) throws IOException {
		final byte[] bytes = json.getBytes(UTF8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(code, bytes.length);
		final OutputStream out = exchange.getResponseBody();
		out.write(bytes);
		out.close();
	}
}
