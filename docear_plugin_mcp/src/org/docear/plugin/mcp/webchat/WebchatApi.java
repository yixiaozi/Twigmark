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
import org.docear.plugin.mcp.server.McpAuth;
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
		map.put("authRequired", JsonValue.ofBoolean(McpAuth.isAuthRequired()));
		map.put("authConfigured", JsonValue.ofBoolean(McpAuth.hasConfiguredApiKey()));
		map.put("webEnabled", JsonValue.ofBoolean(DocearMcpConfig.isWebEnabled()));
		map.put("accountRequired", JsonValue.ofBoolean(true));
		map.put("hasUsers", JsonValue.ofBoolean(WebchatService.anyUserExists()));
		map.put("llmConfigured", JsonValue.ofBoolean(DocearMcpConfig.isWebLlmConfigured()));
		map.put("llmModel", JsonValue.ofString(DocearMcpConfig.getWebLlmModel()));
		map.put("publicBind", JsonValue.ofBoolean(DocearMcpConfig.isPublicBind()));
		map.put("readonly", JsonValue.ofBoolean(DocearMcpConfig.isReadOnly()));
		map.put("host", JsonValue.ofString(DocearMcpConfig.getHost()));
		map.put("port", JsonValue.ofNumber(Integer.valueOf(DocearMcpConfig.getPort())));
		map.put("webchatDb", JsonValue.ofString(WebchatService.localDbPath()));
		map.put("webchatDbCount", JsonValue.ofNumber(Integer.valueOf(WebchatService.loadedDatabaseCount())));
		map.put("machineId", JsonValue.ofString(McpAuditMachineId.getMachineId()));
		map.put("machineName", JsonValue.ofString(McpAuditMachineId.getMachineName()));
		return JsonWriter.write(JsonValue.ofMap(map));
	}

	public void handleRegister(final HttpExchange exchange, final String body) throws IOException {
		try {
			final Map args = JsonParser.parse(body).asMap();
			final String username = str(args, "username");
			final String password = str(args, "password");
			final Map result = WebchatService.register(username, password);
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(WebchatService.plainToJson(result))));
		}
		catch (IllegalArgumentException e) {
			writeJson(exchange, 400, error(e.getMessage()));
		}
		catch (Exception e) {
			LogUtils.warn("register failed: " + e.getMessage(), e);
			writeJson(exchange, 500, error(e.getMessage()));
		}
	}

	public void handleLogin(final HttpExchange exchange, final String body) throws IOException {
		try {
			final Map args = JsonParser.parse(body).asMap();
			final Map result = WebchatService.login(str(args, "username"), str(args, "password"));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(WebchatService.plainToJson(result))));
		}
		catch (IllegalArgumentException e) {
			writeJson(exchange, 401, error(e.getMessage()));
		}
		catch (Exception e) {
			LogUtils.warn("login failed: " + e.getMessage(), e);
			writeJson(exchange, 500, error(e.getMessage()));
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
			map.put("webchatDb", JsonValue.ofString(WebchatService.localDbPath()));
			map.put("webchatDbCount", JsonValue.ofNumber(Integer.valueOf(WebchatService.loadedDatabaseCount())));
			map.put("machineId", JsonValue.ofString(McpAuditMachineId.getMachineId()));
			map.put("machineName", JsonValue.ofString(McpAuditMachineId.getMachineName()));
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

	public void handleChat(final HttpExchange exchange, final String body) throws IOException {
		try {
			final String username = WebchatService.requireUsername(extractSessionToken(exchange));
			final Map args = JsonParser.parse(body).asMap();
			final String message = str(args, "message");
			String conversationId = str(args, "conversationId");
			final String profileId = str(args, "profileId");
			if (conversationId.length() == 0) {
				conversationId = WebchatService.createConversation(username, "");
			}
			final Map endpoint = WebchatService.resolveLlmEndpoint(username, profileId);
			final List history = buildHistoryFromDb(username, conversationId);
			final Map<String, JsonValue> result = webAgent.chat(message, history,
					nullToEmpty((String) endpoint.get("baseUrl")), nullToEmpty((String) endpoint.get("apiKey")),
					nullToEmpty((String) endpoint.get("model")));
			final String reply = result.containsKey("reply") && result.get("reply") != null
					? nullToEmpty(result.get("reply").asString())
					: "";
			final String model = result.containsKey("model") && result.get("model") != null
					? nullToEmpty(result.get("model").asString())
					: "";
			final String toolTraceJson = result.containsKey("toolTrace") && result.get("toolTrace") != null
					? JsonWriter.write(result.get("toolTrace"))
					: "[]";
			WebchatService.appendChatTurn(username, conversationId, message, reply, toolTraceJson, model);
			final Map<String, JsonValue> out = new LinkedHashMap<String, JsonValue>();
			out.put("reply", result.get("reply"));
			out.put("model", result.get("model"));
			out.put("toolTrace", result.get("toolTrace"));
			out.put("conversationId", JsonValue.ofString(conversationId));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(out)));
		}
		catch (IllegalArgumentException e) {
			final boolean auth = e.getMessage() != null && (e.getMessage().indexOf("login") >= 0
					|| e.getMessage().indexOf("session") >= 0);
			writeJson(exchange, auth ? 401 : 400, error(e.getMessage()));
		}
		catch (IllegalStateException e) {
			writeJson(exchange, 400, error(e.getMessage()));
		}
		catch (Exception e) {
			LogUtils.warn("webchat chat failed: " + e.getMessage(), e);
			writeJson(exchange, 500, error(e.getMessage() != null ? e.getMessage() : "chat failed"));
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
				return trimmed.substring(7).trim();
			}
		}
		final String session = header(exchange, "X-Session-Token");
		return session != null ? session.trim() : "";
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
