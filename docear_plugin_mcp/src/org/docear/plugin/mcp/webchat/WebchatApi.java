package org.docear.plugin.mcp.webchat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.audit.McpAuditMachineId;
import org.docear.plugin.mcp.json.JsonParser;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.docear.plugin.mcp.server.McpWebAgent;
import org.freeplane.core.util.LogUtils;

import com.sun.net.httpserver.HttpExchange;

/** JSON API helpers for web account / history / LLM profiles / chat. */
public final class WebchatApi {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private final McpWebAgent webAgent;

	public WebchatApi(final McpWebAgent webAgent) {
		this.webAgent = webAgent;
	}

	public String handleStatus() {
		final Map map = new LinkedHashMap();
		map.put("service", JsonValue.ofString("twigmark-mcp"));
		map.put("webEnabled", JsonValue.ofBoolean(DocearMcpConfig.isWebEnabled()));
		final boolean hasUsers = WebchatService.anyUserExists();
		map.put("hasUsers", JsonValue.ofBoolean(hasUsers));
		map.put("registrationOpen", JsonValue.ofBoolean(!hasUsers));
		map.put("accountRequired", JsonValue.ofBoolean(true));
		return JsonWriter.write(JsonValue.ofMap(map));
	}

	public void handleRegister(final HttpExchange exchange, final String body) throws IOException {
		try {
			WebSecurity.requireRateLimit("register", exchange, 6);
			final Map args = JsonParser.parse(body).asMap();
			final String username = str(args, "username");
			final String password = str(args, "password");
			final String registrationToken = str(args, "registrationToken");
			final Map result = WebchatService.register(username, password, registrationToken);
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(WebchatService.plainToJson(result))));
		}
		catch (IllegalArgumentException e) {
			final int code = e.getMessage() != null && e.getMessage().indexOf("too many") >= 0 ? 429 : 400;
			writeJson(exchange, code, error(e.getMessage()));
		}
		catch (Exception e) {
			WebSecurity.logAndSanitize("register failed", e);
			writeJson(exchange, 500, error(WebSecurity.safePublicError()));
		}
	}

	public void handleLogin(final HttpExchange exchange, final String body) throws IOException {
		try {
			WebSecurity.requireRateLimit("login", exchange, 20);
			final Map args = JsonParser.parse(body).asMap();
			final Map result = WebchatService.login(str(args, "username"), str(args, "password"));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(WebchatService.plainToJson(result))));
		}
		catch (IllegalArgumentException e) {
			final int code = e.getMessage() != null && e.getMessage().indexOf("too many") >= 0 ? 429 : 401;
			writeJson(exchange, code, error(e.getMessage()));
		}
		catch (Exception e) {
			WebSecurity.logAndSanitize("login failed", e);
			writeJson(exchange, 500, error(WebSecurity.safePublicError()));
		}
	}

	public void handleLogout(final HttpExchange exchange) throws IOException {
		WebchatService.logout(extractSessionToken(exchange));
		writeJson(exchange, 200, "{\"ok\":true}");
	}

	public void handleMe(final HttpExchange exchange) throws IOException {
		try {
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			final Map map = new LinkedHashMap();
			map.put("username", JsonValue.ofString(username));
			map.put("webchatDbCount", JsonValue.ofNumber(Integer.valueOf(WebchatService.loadedDatabaseCount())));
			map.put("machineId", JsonValue.ofString(McpAuditMachineId.getMachineId()));
			map.put("machineName", JsonValue.ofString(McpAuditMachineId.getMachineName()));
			map.put("llmConfigured", JsonValue.ofBoolean(WebchatService.isSharedLlmConfigured()));
			map.put("readonly", JsonValue.ofBoolean(DocearMcpConfig.isReadOnly()));
			map.put("webReadOnlyTools", JsonValue.ofBoolean(DocearMcpConfig.isWebReadOnlyTools()));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(map)));
		}
		catch (IllegalArgumentException e) {
			writeJson(exchange, 401, error(e.getMessage()));
		}
		catch (Exception e) {
			writeJson(exchange, 500, error(e.getMessage()));
		}
	}

	public void handleListProfiles(final HttpExchange exchange) throws IOException {
		try {
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			final List profiles = WebchatService.listProfiles(username);
			final Map map = new LinkedHashMap();
			map.put("profiles", JsonValue.ofList(WebchatService.toJsonMaps(profiles)));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(map)));
		}
		catch (IllegalArgumentException e) {
			writeJson(exchange, 401, error(e.getMessage()));
		}
		catch (Exception e) {
			writeJson(exchange, 500, error(e.getMessage()));
		}
	}

	public void handleSaveProfile(final HttpExchange exchange, final String body) throws IOException {
		try {
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			final Map args = JsonParser.parse(body).asMap();
			final Map saved = WebchatService.saveProfile(username, args);
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(WebchatService.plainToJson(saved))));
		}
		catch (IllegalArgumentException e) {
			writeJson(exchange, 400, error(e.getMessage()));
		}
		catch (Exception e) {
			writeJson(exchange, 500, error(e.getMessage()));
		}
	}

	public void handleDeleteProfile(final HttpExchange exchange, final String body) throws IOException {
		try {
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			final Map args = JsonParser.parse(body).asMap();
			WebchatService.deleteProfile(username, str(args, "id"));
			writeJson(exchange, 200, "{\"ok\":true}");
		}
		catch (IllegalArgumentException e) {
			writeJson(exchange, 400, error(e.getMessage()));
		}
		catch (Exception e) {
			writeJson(exchange, 500, error(e.getMessage()));
		}
	}

	public void handleListConversations(final HttpExchange exchange) throws IOException {
		try {
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			final List items = WebchatService.listConversations(username, 200);
			final Map map = new LinkedHashMap();
			map.put("conversations", JsonValue.ofList(WebchatService.toJsonMaps(items)));
			map.put("dbCount", JsonValue.ofNumber(Integer.valueOf(WebchatService.loadedDatabaseCount())));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(map)));
		}
		catch (IllegalArgumentException e) {
			writeJson(exchange, 401, error(e.getMessage()));
		}
		catch (Exception e) {
			writeJson(exchange, 500, error(e.getMessage()));
		}
	}

	public void handleGetConversation(final HttpExchange exchange, final String id) throws IOException {
		try {
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			final Map bundle = WebchatService.getConversationBundle(username, id);
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(WebchatService.plainToJson(bundle))));
		}
		catch (IllegalArgumentException e) {
			final int code = e.getMessage() != null && e.getMessage().indexOf("login") >= 0 ? 401 : 404;
			writeJson(exchange, code, error(e.getMessage()));
		}
		catch (Exception e) {
			writeJson(exchange, 500, error(e.getMessage()));
		}
	}

	public void handleCreateConversation(final HttpExchange exchange, final String body) throws IOException {
		try {
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			String title = "";
			if (body != null && body.trim().length() > 0) {
				final Map args = JsonParser.parse(body).asMap();
				title = str(args, "title");
			}
			final String id = WebchatService.createConversation(username, title);
			final Map map = new LinkedHashMap();
			map.put("id", JsonValue.ofString(id));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(map)));
		}
		catch (IllegalArgumentException e) {
			writeJson(exchange, 401, error(e.getMessage()));
		}
		catch (Exception e) {
			writeJson(exchange, 500, error(e.getMessage()));
		}
	}

	public void handleRenameConversation(final HttpExchange exchange, final String body) throws IOException {
		try {
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			final Map args = JsonParser.parse(body).asMap();
			final String id = str(args, "id");
			final String title = str(args, "title");
			if (id.length() == 0) {
				writeJson(exchange, 400, error("id is required"));
				return;
			}
			WebchatService.renameConversation(username, id, title);
			final Map map = new LinkedHashMap();
			map.put("ok", JsonValue.ofBoolean(true));
			map.put("id", JsonValue.ofString(id));
			map.put("title", JsonValue.ofString(title == null || title.trim().length() == 0 ? "未命名对话" : title.trim()));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(map)));
		}
		catch (IllegalArgumentException e) {
			final boolean auth = e.getMessage() != null && (e.getMessage().indexOf("login") >= 0
					|| e.getMessage().indexOf("session") >= 0);
			writeJson(exchange, auth ? 401 : 400, error(e.getMessage()));
		}
		catch (Exception e) {
			writeJson(exchange, 500, error(e.getMessage()));
		}
	}

	public void handleShareMessage(final HttpExchange exchange, final String body) throws IOException {
		try {
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			final Map args = JsonParser.parse(body).asMap();
			final String messageId = str(args, "messageId");
			if (messageId.length() == 0) {
				writeJson(exchange, 400, error("messageId is required"));
				return;
			}
			final Map share = WebchatService.createMessageShare(username, messageId, args);
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(WebchatService.plainToJson(share))));
		}
		catch (IllegalArgumentException e) {
			final boolean auth = e.getMessage() != null && (e.getMessage().indexOf("login") >= 0
					|| e.getMessage().indexOf("session") >= 0);
			writeJson(exchange, auth ? 401 : 400, error(e.getMessage()));
		}
		catch (Exception e) {
			LogUtils.warn("share message failed: " + e.getMessage(), e);
			writeJson(exchange, 500, error(WebSecurity.safePublicError()));
		}
	}

	public void handleUpdateShareMessage(final HttpExchange exchange, final String body) throws IOException {
		try {
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			final Map args = JsonParser.parse(body).asMap();
			final String token = str(args, "token");
			if (token.length() == 0) {
				writeJson(exchange, 400, error("token is required"));
				return;
			}
			final Map share = WebchatService.updateMessageShare(username, token, args);
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(WebchatService.plainToJson(share))));
		}
		catch (IllegalArgumentException e) {
			final boolean auth = e.getMessage() != null && (e.getMessage().indexOf("login") >= 0
					|| e.getMessage().indexOf("session") >= 0);
			writeJson(exchange, auth ? 401 : 400, error(e.getMessage()));
		}
		catch (Exception e) {
			WebSecurity.logAndSanitize("update share failed", e);
			writeJson(exchange, 500, error(WebSecurity.safePublicError()));
		}
	}

	public void handleListOwnShares(final HttpExchange exchange) throws IOException {
		try {
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			final List items = WebchatService.listOwnMessageShares(username);
			final Map out = new LinkedHashMap();
			out.put("shares", JsonValue.ofList(WebchatService.toJsonMaps(items)));
			out.put("count", JsonValue.ofNumber(Integer.valueOf(items.size())));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(out)));
		}
		catch (IllegalArgumentException e) {
			writeJson(exchange, 401, error(e.getMessage()));
		}
		catch (Exception e) {
			WebSecurity.logAndSanitize("list own shares failed", e);
			writeJson(exchange, 500, error(WebSecurity.safePublicError()));
		}
	}

	public void handleUnshareMessage(final HttpExchange exchange, final String body) throws IOException {
		try {
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			final Map args = JsonParser.parse(body).asMap();
			final String token = str(args, "token");
			WebchatService.revokeMessageShare(username, token);
			final Map map = new LinkedHashMap();
			map.put("ok", JsonValue.ofBoolean(true));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(map)));
		}
		catch (IllegalArgumentException e) {
			final boolean auth = e.getMessage() != null && (e.getMessage().indexOf("login") >= 0
					|| e.getMessage().indexOf("session") >= 0);
			writeJson(exchange, auth ? 401 : 400, error(e.getMessage()));
		}
		catch (Exception e) {
			writeJson(exchange, 500, error(e.getMessage()));
		}
	}

	public void handlePublicShare(final HttpExchange exchange, final String token) throws IOException {
		try {
			WebSecurity.requireRateLimit("public-share", exchange, 120);
			final Map share = WebchatService.getPublicShare(token);
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(WebchatService.plainToJson(share))));
		}
		catch (IllegalArgumentException e) {
			final int code = e.getMessage() != null && e.getMessage().indexOf("too many") >= 0 ? 429 : 404;
			writeJson(exchange, code, error(code == 404 ? "share not found" : e.getMessage()));
		}
		catch (Exception e) {
			WebSecurity.logAndSanitize("public share failed", e);
			writeJson(exchange, 500, error(WebSecurity.safePublicError()));
		}
	}

	public void handlePublicShareUnlock(final HttpExchange exchange, final String token, final String body)
			throws IOException {
		try {
			WebSecurity.requireRateLimit("public-share-unlock", exchange, 30);
			final Map args = body != null && body.trim().length() > 0 ? JsonParser.parse(body).asMap()
					: new LinkedHashMap();
			final Map share = WebchatService.unlockPublicShare(token, str(args, "answer"));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(WebchatService.plainToJson(share))));
		}
		catch (IllegalArgumentException e) {
			final int code;
			if (e.getMessage() != null && e.getMessage().indexOf("too many") >= 0) {
				code = 429;
			}
			else if (e.getMessage() != null && e.getMessage().indexOf("incorrect") >= 0) {
				code = 403;
			}
			else {
				code = 404;
			}
			writeJson(exchange, code, error(code == 404 ? "share not found" : e.getMessage()));
		}
		catch (Exception e) {
			WebSecurity.logAndSanitize("public share unlock failed", e);
			writeJson(exchange, 500, error(WebSecurity.safePublicError()));
		}
	}

	public void handleListPublicShares(final HttpExchange exchange) throws IOException {
		try {
			WebSecurity.requireRateLimit("public-shares", exchange, 60);
			final Map q = queryParams(exchange);
			final int limit = intParam(q, "limit", 50);
			final int offset = intParam(q, "offset", 0);
			final List items = WebchatService.listPublicShares(limit, offset);
			final Map out = new LinkedHashMap();
			out.put("shares", JsonValue.ofList(WebchatService.toJsonMaps(items)));
			out.put("count", JsonValue.ofNumber(Integer.valueOf(items.size())));
			out.put("offset", JsonValue.ofNumber(Integer.valueOf(offset)));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(out)));
		}
		catch (IllegalArgumentException e) {
			final int code = e.getMessage() != null && e.getMessage().indexOf("too many") >= 0 ? 429 : 400;
			writeJson(exchange, code, error(e.getMessage()));
		}
		catch (Exception e) {
			WebSecurity.logAndSanitize("list public shares failed", e);
			writeJson(exchange, 500, error(WebSecurity.safePublicError()));
		}
	}

	private static Map queryParams(final HttpExchange exchange) {
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
			return java.net.URLDecoder.decode(value, "UTF-8");
		}
		catch (Exception e) {
			return value;
		}
	}

	private static int intParam(final Map q, final String key, final int fallback) {
		final Object v = q.get(key);
		if (v == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(String.valueOf(v).trim());
		}
		catch (NumberFormatException e) {
			return fallback;
		}
	}

	public void handleChat(final HttpExchange exchange, final String body) throws IOException {
		try {
			WebSecurity.requireRateLimit("chat", exchange, 30);
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			final Map args = JsonParser.parse(body).asMap();
			final String message = str(args, "message");
			String conversationId = str(args, "conversationId");
			final String profileId = str(args, "profileId");
			final String mapFile = str(args, "mapFile");
			if (conversationId.length() == 0) {
				final String title = mapFile.length() > 0 ? ("关于 " + shortMapName(mapFile)) : "";
				conversationId = WebchatService.createConversation(username, title);
			}
			final Map endpoint = WebchatService.resolveLlmEndpoint(username, profileId);
			final List history = buildHistoryFromDb(username, conversationId);
			final Map<String, JsonValue> result = webAgent.chat(message, history,
					nullToEmpty((String) endpoint.get("baseUrl")), nullToEmpty((String) endpoint.get("apiKey")),
					nullToEmpty((String) endpoint.get("model")), mapFile.length() == 0 ? null : mapFile);
			final String reply = result.containsKey("reply") && result.get("reply") != null
					? nullToEmpty(result.get("reply").asString())
					: "";
			final String model = result.containsKey("model") && result.get("model") != null
					? nullToEmpty(result.get("model").asString())
					: "";
			final JsonValue thoughtTrace = result.get("thoughtTrace");
			final JsonValue toolTrace = result.get("toolTrace");
			final String toolTraceJson = thoughtTrace != null && !thoughtTrace.isNull()
					? JsonWriter.write(thoughtTrace)
					: (toolTrace != null && !toolTrace.isNull() ? JsonWriter.write(toolTrace) : "[]");
			final String assistantMessageId = WebchatService.appendChatTurn(username, conversationId, message, reply,
					toolTraceJson, model);
			final Map<String, JsonValue> out = new LinkedHashMap<String, JsonValue>();
			out.put("reply", result.get("reply"));
			out.put("model", result.get("model"));
			out.put("conversationId", JsonValue.ofString(conversationId));
			out.put("assistantMessageId", JsonValue.ofString(assistantMessageId));
			if (toolTrace != null) {
				out.put("toolTrace", toolTrace);
			}
			if (result.containsKey("reasoning") && result.get("reasoning") != null) {
				out.put("reasoning", result.get("reasoning"));
			}
			if (thoughtTrace != null) {
				out.put("thoughtTrace", thoughtTrace);
			}
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(out)));
		}
		catch (IllegalArgumentException e) {
			final boolean auth = e.getMessage() != null && (e.getMessage().indexOf("login") >= 0
					|| e.getMessage().indexOf("session") >= 0);
			final int code = e.getMessage() != null && e.getMessage().indexOf("too many") >= 0 ? 429
					: (auth ? 401 : 400);
			writeJson(exchange, code, error(e.getMessage()));
		}
		catch (IllegalStateException e) {
			writeJson(exchange, 400, error(e.getMessage()));
		}
		catch (Exception e) {
			WebSecurity.logAndSanitize("webchat chat failed", e);
			writeJson(exchange, 500, error(WebSecurity.safePublicError()));
		}
	}

	private List buildHistoryFromDb(final String username, final String conversationId) {
		final List history = new ArrayList();
		try {
			final Map bundle = WebchatService.getConversationBundle(username, conversationId);
			final List messages = (List) bundle.get("messages");
			if (messages == null) {
				return history;
			}
			// Keep last 24 user/assistant turns for the model context window.
			final int start = messages.size() > 24 ? messages.size() - 24 : 0;
			for (int i = start; i < messages.size(); i++) {
				final Map m = (Map) messages.get(i);
				final String role = nullToEmpty((String) m.get("role"));
				if (!"user".equals(role) && !"assistant".equals(role)) {
					continue;
				}
				final Map item = new LinkedHashMap();
				item.put("role", JsonValue.ofString(role));
				item.put("content", JsonValue.ofString(nullToEmpty((String) m.get("content"))));
				history.add(JsonValue.ofMap(item));
			}
		}
		catch (Exception e) {
			LogUtils.warn("buildHistoryFromDb: " + e.getMessage());
		}
		return history;
	}

	public static String extractSessionToken(final HttpExchange exchange) {
		final String auth = header(exchange, "Authorization");
		if (auth != null && auth.length() > 0) {
			final String trimmed = auth.trim();
			if (trimmed.length() >= 7 && "bearer ".equalsIgnoreCase(trimmed.substring(0, 7))) {
				final String token = trimmed.substring(7).trim();
				if (token.startsWith("tm_")) {
					return "";
				}
				return token;
			}
		}
		final String session = header(exchange, "X-Session-Token");
		if (session != null) {
			final String token = session.trim();
			if (token.startsWith("tm_")) {
				return "";
			}
			return token;
		}
		return "";
	}

	private static String header(final HttpExchange exchange, final String name) {
		final java.util.List values = exchange.getRequestHeaders().get(name);
		if (values == null || values.isEmpty()) {
			return null;
		}
		final Object first = values.get(0);
		return first != null ? String.valueOf(first) : null;
	}

	private static String str(final Map args, final String key) {
		if (args == null || !args.containsKey(key) || args.get(key) == null) {
			return "";
		}
		final Object v = args.get(key);
		if (v instanceof JsonValue) {
			return nullToEmpty(((JsonValue) v).asString());
		}
		return nullToEmpty(String.valueOf(v));
	}

	private static String nullToEmpty(final String value) {
		return value == null ? "" : value;
	}

	private static String shortMapName(final String path) {
		if (path == null || path.length() == 0) {
			return "导图";
		}
		String p = path.replace('\\', '/');
		final int slash = p.lastIndexOf('/');
		if (slash >= 0 && slash + 1 < p.length()) {
			p = p.substring(slash + 1);
		}
		if (p.toLowerCase().endsWith(".mm")) {
			p = p.substring(0, p.length() - 3);
		}
		return p.length() == 0 ? "导图" : p;
	}

	private static String error(final String message) {
		final Map map = new LinkedHashMap();
		map.put("error", JsonValue.ofString(message != null ? message : "error"));
		return JsonWriter.write(JsonValue.ofMap(map));
	}

	private static void writeJson(final HttpExchange exchange, final int status, final String body) throws IOException {
		final byte[] bytes = body.getBytes(UTF8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.getResponseBody().close();
	}
}
