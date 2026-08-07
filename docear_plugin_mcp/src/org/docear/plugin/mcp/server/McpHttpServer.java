package org.docear.plugin.mcp.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.audit.McpRequestContext;
import org.docear.plugin.mcp.json.JsonParser;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.docear.plugin.mcp.webchat.WebMapsApi;
import org.docear.plugin.mcp.webchat.WebchatApi;
import org.freeplane.core.util.LogUtils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public final class McpHttpServer {

	private static final Charset UTF8 = Charset.forName("UTF-8");
	private final McpProtocol protocol = new McpProtocol();
	private final McpWebAgent webAgent = new McpWebAgent(protocol);
	private final WebchatApi webchatApi = new WebchatApi(webAgent);
	private HttpServer server;
	private ExecutorService executor;

	public void start() throws IOException {
		McpAuth.validateServerStart();
		final String host = DocearMcpConfig.getHost();
		final int port = DocearMcpConfig.getPort();
		server = HttpServer.create(new InetSocketAddress(host, port), 0);
		server.createContext("/mcp", new McpHandler());
		server.createContext("/health", new HealthHandler());
		server.createContext("/api", new ApiDispatcher());
		if (DocearMcpConfig.isWebEnabled()) {
			server.createContext("/web", new StaticWebHandler());
		}
		server.createContext("/", new RootHandler());
		// Keep health/list responsive even when a few write tools block on EDT.
		executor = Executors.newFixedThreadPool(16);
		server.setExecutor(executor);
		server.start();
		final StringBuilder msg = new StringBuilder();
		msg.append("Twigmark MCP server listening on http://").append(host).append(':').append(port);
		msg.append(" /mcp");
		if (DocearMcpConfig.isWebEnabled()) {
			msg.append(" and /web/");
		}
		if (McpAuth.isAuthRequired()) {
			msg.append(" (API key required)");
		}
		LogUtils.info(msg.toString());
	}

	public void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
	}

	private final class RootHandler implements HttpHandler {
		public void handle(final HttpExchange exchange) throws IOException {
			addCorsHeaders(exchange);
			if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.sendResponseHeaders(204, -1);
				exchange.close();
				return;
			}
			final String path = exchange.getRequestURI().getPath();
			if (DocearMcpConfig.isWebEnabled() && ("/".equals(path) || "".equals(path))
					&& "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.getResponseHeaders().set("Location", "/web/");
				exchange.sendResponseHeaders(302, -1);
				exchange.close();
				return;
			}
			writeJson(exchange, 200, healthJson());
		}
	}

	private final class HealthHandler implements HttpHandler {
		public void handle(final HttpExchange exchange) throws IOException {
			addCorsHeaders(exchange);
			writeJson(exchange, 200, healthJson());
		}
	}

	private final class ApiDispatcher implements HttpHandler {
		public void handle(final HttpExchange exchange) throws IOException {
			addCorsHeaders(exchange);
			if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.sendResponseHeaders(204, -1);
				exchange.close();
				return;
			}
			final String path = exchange.getRequestURI().getPath();
			final String method = exchange.getRequestMethod();
			if ("/api/status".equals(path) && "GET".equalsIgnoreCase(method)) {
				writeJson(exchange, 200, webchatApi.handleStatus());
				return;
			}
			if (!DocearMcpConfig.isWebEnabled() && !"/api/status".equals(path)) {
				writeJson(exchange, 403, apiError("Web UI is disabled"));
				return;
			}
			if ("/api/register".equals(path) && "POST".equalsIgnoreCase(method)) {
				webchatApi.handleRegister(exchange, readBody(exchange));
				return;
			}
			if ("/api/login".equals(path) && "POST".equalsIgnoreCase(method)) {
				webchatApi.handleLogin(exchange, readBody(exchange));
				return;
			}
			if ("/api/logout".equals(path) && "POST".equalsIgnoreCase(method)) {
				webchatApi.handleLogout(exchange);
				return;
			}
			if ("/api/me".equals(path) && "GET".equalsIgnoreCase(method)) {
				webchatApi.handleMe(exchange);
				return;
			}
			if ("/api/llm-profiles".equals(path) && "GET".equalsIgnoreCase(method)) {
				webchatApi.handleListProfiles(exchange);
				return;
			}
			if ("/api/llm-profiles".equals(path) && "POST".equalsIgnoreCase(method)) {
				webchatApi.handleSaveProfile(exchange, readBody(exchange));
				return;
			}
			if ("/api/llm-profiles/delete".equals(path) && "POST".equalsIgnoreCase(method)) {
				webchatApi.handleDeleteProfile(exchange, readBody(exchange));
				return;
			}
			if ("/api/conversations".equals(path) && "GET".equalsIgnoreCase(method)) {
				webchatApi.handleListConversations(exchange);
				return;
			}
			if ("/api/conversations".equals(path) && "POST".equalsIgnoreCase(method)) {
				webchatApi.handleCreateConversation(exchange, readBody(exchange));
				return;
			}
			if (path != null && path.startsWith("/api/conversations/") && "GET".equalsIgnoreCase(method)) {
				final String id = path.substring("/api/conversations/".length());
				webchatApi.handleGetConversation(exchange, id);
				return;
			}
			if ("/api/conversations/rename".equals(path) && "POST".equalsIgnoreCase(method)) {
				webchatApi.handleRenameConversation(exchange, readBody(exchange));
				return;
			}
			if ("/api/messages/share".equals(path) && "POST".equalsIgnoreCase(method)) {
				webchatApi.handleShareMessage(exchange, readBody(exchange));
				return;
			}
			if ("/api/messages/unshare".equals(path) && "POST".equalsIgnoreCase(method)) {
				webchatApi.handleUnshareMessage(exchange, readBody(exchange));
				return;
			}
			if (path != null && path.startsWith("/api/public/share/") && "GET".equalsIgnoreCase(method)) {
				final String token = path.substring("/api/public/share/".length());
				webchatApi.handlePublicShare(exchange, token);
				return;
			}
			if ("/api/chat".equals(path) && "POST".equalsIgnoreCase(method)) {
				webchatApi.handleChat(exchange, readBody(exchange));
				return;
			}
			if ("/api/maps".equals(path) && "GET".equalsIgnoreCase(method)) {
				WebMapsApi.handleListMaps(exchange);
				return;
			}
			if ("/api/maps/json".equals(path) && "GET".equalsIgnoreCase(method)) {
				WebMapsApi.handleGetMapJson(exchange);
				return;
			}
			if ("/api/maps/search".equals(path) && "GET".equalsIgnoreCase(method)) {
				WebMapsApi.handleSearch(exchange);
				return;
			}
			writeJson(exchange, 404, apiError("Unknown API path: " + path));
		}
	}

	private final class StaticWebHandler implements HttpHandler {
		public void handle(final HttpExchange exchange) throws IOException {
			addCorsHeaders(exchange);
			if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.sendResponseHeaders(204, -1);
				exchange.close();
				return;
			}
			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
					&& !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
				writeJson(exchange, 405, apiError("Only GET is supported on /web"));
				return;
			}
			String path = exchange.getRequestURI().getPath();
			if (path == null) {
				path = "/web/";
			}
			if ("/web".equals(path)) {
				exchange.getResponseHeaders().set("Location", "/web/");
				exchange.sendResponseHeaders(302, -1);
				exchange.close();
				return;
			}
			String relative = path.startsWith("/web/") ? path.substring("/web/".length()) : "";
			if (relative.length() == 0) {
				relative = "index.html";
			}
			try {
				relative = URLDecoder.decode(relative, "UTF-8");
			}
			catch (Exception e) {
				// keep raw
			}
			if (relative.indexOf("..") >= 0 || relative.startsWith("/") || relative.indexOf('\\') >= 0) {
				writeJson(exchange, 400, apiError("Invalid path"));
				return;
			}
			final String resourcePath = "web/" + relative;
			final InputStream stream = McpHttpServer.class.getClassLoader().getResourceAsStream(resourcePath);
			if (stream == null) {
				writeJson(exchange, 404, apiError("Not found: " + relative));
				return;
			}
			final byte[] bytes = readAllBytes(stream);
			exchange.getResponseHeaders().set("Content-Type", contentType(relative));
			exchange.getResponseHeaders().set("Cache-Control", "no-cache");
			if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.sendResponseHeaders(200, -1);
				exchange.close();
				return;
			}
			exchange.sendResponseHeaders(200, bytes.length);
			final OutputStream out = exchange.getResponseBody();
			out.write(bytes);
			out.close();
		}
	}

	private final class McpHandler implements HttpHandler {
		public void handle(final HttpExchange exchange) throws IOException {
			addCorsHeaders(exchange);
			if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.sendResponseHeaders(204, -1);
				exchange.close();
				return;
			}
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				writeJson(exchange, 405, errorBody(-32000, "Only POST is supported on /mcp"));
				return;
			}
			if (!McpAuth.isAuthorized(exchange)) {
				writeJson(exchange, 401, errorBody(-32001, "Unauthorized: provide Authorization: Bearer <mcp-api-key> or X-Api-Key"));
				return;
			}
			final String body = readBody(exchange);
			final long started = System.currentTimeMillis();
			String methodHint = "";
			try {
				McpRequestContext.begin(exchange);
				final JsonValue request = JsonParser.parse(body);
				methodHint = extractMethodHint(request);
				final String response = protocol.handle(request);
				writeJson(exchange, 200, response);
			}
			catch (Exception e) {
				LogUtils.warn("Docear MCP request failed: " + e.getMessage(), e);
				writeJson(exchange, 500, errorBody(-32603, e.getMessage()));
			}
			finally {
				final long elapsed = System.currentTimeMillis() - started;
				if (elapsed >= 3000L) {
					LogUtils.warn("Slow MCP request " + methodHint + " took " + elapsed + "ms");
				}
				McpRequestContext.end();
			}
		}
	}

	private static String healthJson() {
		final Map<String, JsonValue> map = new LinkedHashMap<String, JsonValue>();
		map.put("status", JsonValue.ofString("ok"));
		map.put("service", JsonValue.ofString("docear-mcp"));
		map.put("web", JsonValue.ofBoolean(DocearMcpConfig.isWebEnabled()));
		map.put("authRequired", JsonValue.ofBoolean(McpAuth.isAuthRequired()));
		return JsonWriter.write(JsonValue.ofMap(map));
	}

	private static void addCorsHeaders(final HttpExchange exchange) {
		final Map<String, String> headers = new LinkedHashMap<String, String>();
		headers.put("Access-Control-Allow-Origin", "*");
		headers.put("Access-Control-Allow-Methods", "POST, GET, OPTIONS, HEAD");
		headers.put("Access-Control-Allow-Headers",
				"Content-Type, Accept, Authorization, X-Api-Key, X-Session-Token, Mcp-Session-Id, X-Docear-Audit-Caller, X-Docear-Audit-Question");
		for (final Map.Entry<String, String> entry : headers.entrySet()) {
			exchange.getResponseHeaders().set(entry.getKey(), entry.getValue());
		}
	}

	private static String readBody(final HttpExchange exchange) throws IOException {
		return new String(readAllBytes(exchange.getRequestBody()), UTF8);
	}

	private static byte[] readAllBytes(final InputStream in) throws IOException {
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		final byte[] chunk = new byte[4096];
		int read;
		while ((read = in.read(chunk)) >= 0) {
			if (read > 0) {
				buffer.write(chunk, 0, read);
			}
		}
		in.close();
		return buffer.toByteArray();
	}

	private static void writeJson(final HttpExchange exchange, final int status, final String body) throws IOException {
		final byte[] bytes = body.getBytes(UTF8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length);
		final OutputStream stream = exchange.getResponseBody();
		stream.write(bytes);
		stream.close();
	}

	private static String apiError(final String message) {
		final Map<String, JsonValue> map = new LinkedHashMap<String, JsonValue>();
		map.put("error", JsonValue.ofString(message != null ? message : "error"));
		return JsonWriter.write(JsonValue.ofMap(map));
	}

	private static String errorBody(final int code, final String message) {
		final Map<String, JsonValue> error = new LinkedHashMap<String, JsonValue>();
		error.put("code", JsonValue.ofNumber(Integer.valueOf(code)));
		error.put("message", JsonValue.ofString(message != null ? message : "Unknown error"));
		final Map<String, JsonValue> response = new LinkedHashMap<String, JsonValue>();
		response.put("jsonrpc", JsonValue.ofString("2.0"));
		response.put("error", JsonValue.ofMap(error));
		return JsonWriter.write(JsonValue.ofMap(response));
	}

	private static String contentType(final String relative) {
		final String lower = relative.toLowerCase();
		if (lower.endsWith(".html") || lower.endsWith(".htm")) {
			return "text/html; charset=utf-8";
		}
		if (lower.endsWith(".js")) {
			return "application/javascript; charset=utf-8";
		}
		if (lower.endsWith(".css")) {
			return "text/css; charset=utf-8";
		}
		if (lower.endsWith(".png")) {
			return "image/png";
		}
		if (lower.endsWith(".svg")) {
			return "image/svg+xml";
		}
		if (lower.endsWith(".json")) {
			return "application/json; charset=utf-8";
		}
		return "application/octet-stream";
	}

	private static String extractMethodHint(final JsonValue request) {
		try {
			final Map<String, JsonValue> root = request.asMap();
			final JsonValue methodValue = root.get("method");
			final String method = methodValue != null ? methodValue.asString() : "";
			if ("tools/call".equals(method)) {
				final JsonValue paramsValue = root.get("params");
				final Map<String, JsonValue> params = paramsValue != null ? paramsValue.asMap() : null;
				final JsonValue nameValue = params != null ? params.get("name") : null;
				final String name = nameValue != null ? nameValue.asString() : "";
				return method + "/" + name;
			}
			return method != null ? method : "";
		}
		catch (Exception e) {
			return "";
		}
	}
}
