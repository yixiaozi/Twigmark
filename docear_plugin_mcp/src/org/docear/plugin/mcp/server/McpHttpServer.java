package org.docear.plugin.mcp.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.audit.McpRequestContext;
import org.docear.plugin.mcp.json.JsonParser;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.freeplane.core.util.LogUtils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public final class McpHttpServer {

	private static final Charset UTF8 = Charset.forName("UTF-8");
	private final McpProtocol protocol = new McpProtocol();
	private HttpServer server;
	private ExecutorService executor;

	public void start() throws IOException {
		final String host = DocearMcpConfig.getHost();
		final int port = DocearMcpConfig.getPort();
		server = HttpServer.create(new InetSocketAddress(host, port), 0);
		server.createContext("/mcp", new McpHandler());
		server.createContext("/health", new HealthHandler());
		server.createContext("/", new HealthHandler());
		executor = Executors.newFixedThreadPool(8);
		server.setExecutor(executor);
		server.start();
		LogUtils.info("Docear MCP server listening on http://" + host + ":" + port + "/mcp");
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

	private final class HealthHandler implements HttpHandler {
		public void handle(final HttpExchange exchange) throws IOException {
			writeJson(exchange, 200, "{\"status\":\"ok\",\"service\":\"docear-mcp\"}");
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
			final String body = readBody(exchange);
			try {
				McpRequestContext.begin(exchange);
				final JsonValue request = JsonParser.parse(body);
				final String response = protocol.handle(request);
				writeJson(exchange, 200, response);
			}
			catch (Exception e) {
				LogUtils.warn("Docear MCP request failed: " + e.getMessage(), e);
				writeJson(exchange, 500, errorBody(-32603, e.getMessage()));
			}
			finally {
				McpRequestContext.end();
			}
		}
	}

	private static void addCorsHeaders(final HttpExchange exchange) {
		final Map<String, String> headers = new LinkedHashMap<String, String>();
		headers.put("Access-Control-Allow-Origin", "*");
		headers.put("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
		headers.put("Access-Control-Allow-Headers",
		    "Content-Type, Accept, Mcp-Session-Id, X-Docear-Audit-Caller, X-Docear-Audit-Question");
		for (final Map.Entry<String, String> entry : headers.entrySet()) {
			exchange.getResponseHeaders().set(entry.getKey(), entry.getValue());
		}
	}

	private static String readBody(final HttpExchange exchange) throws IOException {
		final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
		final byte[] chunk = new byte[4096];
		int read;
		while ((read = exchange.getRequestBody().read(chunk)) >= 0) {
			if (read > 0) {
				buffer.write(chunk, 0, read);
			}
		}
		return new String(buffer.toByteArray(), UTF8);
	}

	private static void writeJson(final HttpExchange exchange, final int status, final String body) throws IOException {
		final byte[] bytes = body.getBytes(UTF8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length);
		final OutputStream stream = exchange.getResponseBody();
		stream.write(bytes);
		stream.close();
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
}
