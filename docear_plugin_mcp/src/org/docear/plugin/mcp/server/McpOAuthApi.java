package org.docear.plugin.mcp.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonParser;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.docear.plugin.mcp.webchat.WebSecurity;
import org.freeplane.core.util.LogUtils;

import com.sun.net.httpserver.HttpExchange;

/** OAuth 2.1 metadata, authorize, token, and DCR for Grok / MCP clients. */
public final class McpOAuthApi {
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final String HTML_RESOURCE = "web/oauth-authorize.html";

	private McpOAuthApi() {
	}

	public static boolean handle(final HttpExchange exchange) throws IOException {
		final String path = exchange.getRequestURI().getPath();
		final String method = exchange.getRequestMethod();
		if (path == null) {
			return false;
		}
		if ("/.well-known/oauth-authorization-server".equals(path) && isGet(method)) {
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(authorizationServerMetadata(exchange))));
			return true;
		}
		if (("/.well-known/oauth-protected-resource".equals(path)
				|| "/.well-known/oauth-protected-resource/mcp".equals(path)) && isGet(method)) {
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(protectedResourceMetadata(exchange))));
			return true;
		}
		if ("/oauth/register".equals(path) && "POST".equalsIgnoreCase(method)) {
			handleRegister(exchange);
			return true;
		}
		if ("/oauth/authorize".equals(path) && isGet(method)) {
			handleAuthorizeGet(exchange);
			return true;
		}
		if ("/oauth/authorize".equals(path) && "POST".equalsIgnoreCase(method)) {
			handleAuthorizePost(exchange);
			return true;
		}
		if ("/oauth/token".equals(path) && "POST".equalsIgnoreCase(method)) {
			handleToken(exchange);
			return true;
		}
		return false;
	}

	public static String issuer(final HttpExchange exchange) {
		final String configured = DocearMcpConfig.getPublicBaseUrl();
		if (configured.length() > 0) {
			return configured;
		}
		final String proto = firstHeader(exchange, "X-Forwarded-Proto");
		final String host = firstHeader(exchange, "X-Forwarded-Host");
		final String hostHeader = host.length() > 0 ? host : firstHeader(exchange, "Host");
		if (hostHeader.length() > 0) {
			final String scheme = proto.length() > 0 ? proto : "https";
			return scheme + "://" + hostHeader;
		}
		return "http://" + DocearMcpConfig.getHost() + ":" + DocearMcpConfig.getPort();
	}

	public static String resourceMetadataUrl(final HttpExchange exchange) {
		return issuer(exchange) + "/.well-known/oauth-protected-resource";
	}

	public static String wwwAuthenticate(final HttpExchange exchange) {
		return "Bearer realm=\"Twigmark MCP\", resource_metadata=\"" + resourceMetadataUrl(exchange) + "\"";
	}

	private static Map<String, JsonValue> authorizationServerMetadata(final HttpExchange exchange) {
		final String iss = issuer(exchange);
		final Map<String, JsonValue> map = new LinkedHashMap<String, JsonValue>();
		map.put("issuer", JsonValue.ofString(iss));
		map.put("authorization_endpoint", JsonValue.ofString(iss + "/oauth/authorize"));
		map.put("token_endpoint", JsonValue.ofString(iss + "/oauth/token"));
		map.put("registration_endpoint", JsonValue.ofString(iss + "/oauth/register"));
		map.put("response_types_supported", JsonValue.ofList(stringList("code")));
		map.put("grant_types_supported", JsonValue.ofList(stringList("authorization_code", "refresh_token")));
		map.put("code_challenge_methods_supported", JsonValue.ofList(stringList("S256")));
		map.put("token_endpoint_auth_methods_supported", JsonValue.ofList(stringList("none")));
		map.put("scopes_supported", JsonValue.ofList(stringList("mcp", "mcp:read", "offline_access")));
		return map;
	}

	private static Map<String, JsonValue> protectedResourceMetadata(final HttpExchange exchange) {
		final String iss = issuer(exchange);
		final Map<String, JsonValue> map = new LinkedHashMap<String, JsonValue>();
		map.put("resource", JsonValue.ofString(iss + "/mcp"));
		map.put("authorization_servers", JsonValue.ofList(stringList(iss)));
		map.put("bearer_methods_supported", JsonValue.ofList(stringList("header")));
		map.put("scopes_supported", JsonValue.ofList(stringList("mcp", "mcp:read")));
		return map;
	}

	private static void handleRegister(final HttpExchange exchange) throws IOException {
		try {
			WebSecurity.requireRateLimit("oauth-register", exchange, 10);
			writeJson(exchange, 201, JsonWriter.write(JsonValue.ofMap(dcrResponse(exchange))));
		}
		catch (IllegalArgumentException e) {
			writeOAuthError(exchange, 400, "invalid_client_metadata", e.getMessage());
		}
		catch (Exception e) {
			writeOAuthError(exchange, 500, "server_error", "registration failed");
		}
	}

	private static Map<String, JsonValue> dcrResponse(final HttpExchange exchange) throws IOException {
		final Map<String, JsonValue> map = new LinkedHashMap<String, JsonValue>();
		map.put("client_id", JsonValue.ofString(McpOAuthService.CLIENT_ID));
		map.put("client_id_issued_at", JsonValue.ofNumber(Long.valueOf(System.currentTimeMillis() / 1000L)));
		map.put("token_endpoint_auth_method", JsonValue.ofString("none"));
		map.put("grant_types", JsonValue.ofList(stringList("authorization_code", "refresh_token")));
		map.put("response_types", JsonValue.ofList(stringList("code")));
		map.put("code_challenge_methods_supported", JsonValue.ofList(stringList("S256")));
		map.put("client_name", JsonValue.ofString("Twigmark"));
		final String body = readBody(exchange).trim();
		if (body.length() > 0) {
			try {
				final Map parsed = JsonParser.parse(body).asMap();
				if (parsed.containsKey("redirect_uris")) {
					map.put("redirect_uris", (JsonValue) parsed.get("redirect_uris"));
				}
			}
			catch (Exception ignored) {
			}
		}
		return map;
	}

	private static void handleAuthorizeGet(final HttpExchange exchange) throws IOException {
		final Map params = queryParams(exchange);
		final String error = validateAuthorizeParams(params);
		writeAuthorizeHtml(exchange, error, params);
	}

	private static void handleAuthorizePost(final HttpExchange exchange) throws IOException {
		try {
			WebSecurity.requireRateLimit("oauth-login", exchange, 12);
			final Map params = merge(queryParams(exchange), formParams(exchange));
			final String paramError = validateAuthorizeParams(params);
			if (paramError != null) {
				writeAuthorizeHtml(exchange, paramError, params);
				return;
			}
			final String username = str(params, "username");
			final String password = str(params, "password");
			final String clientId = str(params, "client_id");
			final String redirect = str(params, "redirect_uri");
			final String challenge = str(params, "code_challenge");
			final String state = str(params, "state");
			final String scope = str(params, "scope");
			final String code = McpOAuthService.get().loginAndCreateCode(username, password, clientId, redirect,
					challenge, scope);
			redirectWithCode(exchange, redirect, code, state);
		}
		catch (IllegalArgumentException e) {
			final String msg = e.getMessage() != null && e.getMessage().indexOf("invalid username") >= 0
					? "账号或密码不对"
					: (e.getMessage() != null ? e.getMessage() : "登录失败");
			writeAuthorizeHtml(exchange, msg, merge(queryParams(exchange), formParams(exchange)));
		}
		catch (Exception e) {
			LogUtils.warn("oauth authorize failed: " + e.getMessage());
			writeAuthorizeHtml(exchange, "登录失败，请重试", queryParams(exchange));
		}
	}

	private static void handleToken(final HttpExchange exchange) throws IOException {
		try {
			WebSecurity.requireRateLimit("oauth-token", exchange, 30);
			final Map params = tokenParams(exchange);
			final String grant = str(params, "grant_type");
			final Map<String, String> tokens;
			if ("refresh_token".equals(grant)) {
				tokens = McpOAuthService.get().refresh(str(params, "refresh_token"));
			}
			else if ("authorization_code".equals(grant) || grant.length() == 0) {
				tokens = McpOAuthService.get().exchangeCode(str(params, "code"), str(params, "client_id"),
						str(params, "redirect_uri"), str(params, "code_verifier"));
			}
			else {
				writeOAuthError(exchange, 400, "unsupported_grant_type", "use authorization_code or refresh_token");
				return;
			}
			final Map<String, JsonValue> body = new LinkedHashMap<String, JsonValue>();
			body.put("access_token", JsonValue.ofString(tokens.get("access_token")));
			body.put("token_type", JsonValue.ofString("Bearer"));
			body.put("expires_in", JsonValue.ofNumber(Integer.valueOf(tokens.get("expires_in"))));
			body.put("refresh_token", JsonValue.ofString(tokens.get("refresh_token")));
			body.put("scope", JsonValue.ofString(tokens.get("scope")));
			writeJson(exchange, 200, JsonWriter.write(JsonValue.ofMap(body)));
		}
		catch (IllegalArgumentException e) {
			writeOAuthError(exchange, 400, "invalid_grant", "authorization failed");
		}
		catch (Exception e) {
			LogUtils.warn("oauth token failed: " + e.getMessage());
			writeOAuthError(exchange, 500, "server_error", "token failed");
		}
	}

	private static String validateAuthorizeParams(final Map params) {
		final String responseType = str(params, "response_type");
		if (responseType.length() > 0 && !"code".equals(responseType)) {
			return "只支持 response_type=code";
		}
		final String clientId = str(params, "client_id");
		if (!McpOAuthService.validClientId(clientId)) {
			return "缺少有效的 client_id（填 twigmark）";
		}
		final String redirect = str(params, "redirect_uri");
		if (!McpOAuthRedirects.allowed(redirect)) {
			LogUtils.warn("oauth rejected redirect host=" + McpOAuthRedirects.hostOf(redirect));
			return "redirect_uri 不在允许名单（需要 grok.com / x.ai 的 HTTPS 回调）";
		}
		final String method = str(params, "code_challenge_method");
		if (method.length() > 0 && !"S256".equalsIgnoreCase(method)) {
			return "只支持 code_challenge_method=S256（PKCE）";
		}
		final String challenge = str(params, "code_challenge");
		if (challenge.length() < 20) {
			return "缺少 PKCE code_challenge";
		}
		return null;
	}

	private static void redirectWithCode(final HttpExchange exchange, final String redirectUri, final String code,
			final String state) throws IOException {
		final StringBuilder loc = new StringBuilder(redirectUri);
		loc.append(redirectUri.indexOf('?') >= 0 ? '&' : '?');
		loc.append("code=").append(urlEncode(code));
		if (state != null && state.length() > 0) {
			loc.append("&state=").append(urlEncode(state));
		}
		exchange.getResponseHeaders().set("Location", loc.toString());
		exchange.sendResponseHeaders(302, -1);
		exchange.close();
	}

	private static void writeAuthorizeHtml(final HttpExchange exchange, final String error, final Map params)
			throws IOException {
		String html = loadHtml();
		final String errBlock = error == null || error.length() == 0 ? ""
				: "<p class=\"err\">" + escapeHtml(error) + "</p>";
		html = replace(html, "{{error}}", errBlock);
		html = replace(html, "{{client_id}}", escapeHtml(str(params, "client_id")));
		html = replace(html, "{{redirect_uri}}", escapeHtml(str(params, "redirect_uri")));
		html = replace(html, "{{state}}", escapeHtml(str(params, "state")));
		html = replace(html, "{{scope}}", escapeHtml(str(params, "scope")));
		html = replace(html, "{{code_challenge}}", escapeHtml(str(params, "code_challenge")));
		html = replace(html, "{{code_challenge_method}}", escapeHtml(nz(str(params, "code_challenge_method"), "S256")));
		html = replace(html, "{{response_type}}", escapeHtml(nz(str(params, "response_type"), "code")));
		final byte[] bytes = html.getBytes(UTF8);
		exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(200, bytes.length);
		final OutputStream out = exchange.getResponseBody();
		out.write(bytes);
		out.close();
	}

	private static String loadHtml() throws IOException {
		final InputStream in = McpOAuthApi.class.getClassLoader().getResourceAsStream(HTML_RESOURCE);
		if (in == null) {
			return fallbackHtml();
		}
		final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
		final byte[] chunk = new byte[4096];
		int n;
		while ((n = in.read(chunk)) >= 0) {
			if (n > 0) {
				buf.write(chunk, 0, n);
			}
		}
		in.close();
		return new String(buf.toByteArray(), UTF8);
	}

	private static String fallbackHtml() {
		return "<!doctype html><meta charset=utf-8><title>Twigmark 登录</title>"
				+ "{{error}}<form method=post>"
				+ "<input name=username placeholder=账号>"
				+ "<input name=password type=password placeholder=密码>"
				+ "<input type=hidden name=client_id value=\"{{client_id}}\">"
				+ "<input type=hidden name=redirect_uri value=\"{{redirect_uri}}\">"
				+ "<input type=hidden name=state value=\"{{state}}\">"
				+ "<input type=hidden name=scope value=\"{{scope}}\">"
				+ "<input type=hidden name=code_challenge value=\"{{code_challenge}}\">"
				+ "<input type=hidden name=code_challenge_method value=\"{{code_challenge_method}}\">"
				+ "<input type=hidden name=response_type value=\"{{response_type}}\">"
				+ "<button type=submit>登录并授权改导图</button></form>";
	}

	private static Map tokenParams(final HttpExchange exchange) throws IOException {
		final String body = readBody(exchange);
		final String ct = firstHeader(exchange, "Content-Type").toLowerCase();
		if (ct.indexOf("json") >= 0 && body.trim().startsWith("{")) {
			try {
				final Map json = JsonParser.parse(body).asMap();
				final Map out = new LinkedHashMap();
				final Object[] keys = json.keySet().toArray();
				for (int i = 0; i < keys.length; i++) {
					final String key = String.valueOf(keys[i]);
					final Object val = json.get(key);
					out.put(key, val instanceof JsonValue ? ((JsonValue) val).asString() : String.valueOf(val));
				}
				return out;
			}
			catch (Exception e) {
				return parseForm(body);
			}
		}
		return merge(queryParams(exchange), parseForm(body));
	}

	private static Map formParams(final HttpExchange exchange) throws IOException {
		return parseForm(readBody(exchange));
	}

	private static Map queryParams(final HttpExchange exchange) {
		final String raw = exchange.getRequestURI().getRawQuery();
		return parseForm(raw == null ? "" : raw);
	}

	private static Map parseForm(final String raw) {
		final Map map = new LinkedHashMap();
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
			final String key = urlDecode(eq < 0 ? part : part.substring(0, eq));
			final String val = urlDecode(eq < 0 ? "" : part.substring(eq + 1));
			map.put(key, val);
		}
		return map;
	}

	private static Map merge(final Map a, final Map b) {
		final Map out = new LinkedHashMap();
		if (a != null) {
			out.putAll(a);
		}
		if (b != null) {
			out.putAll(b);
		}
		return out;
	}

	private static String str(final Map map, final String key) {
		if (map == null || !map.containsKey(key) || map.get(key) == null) {
			return "";
		}
		return String.valueOf(map.get(key)).trim();
	}

	private static String nz(final String value, final String fallback) {
		return value == null || value.length() == 0 ? fallback : value;
	}

	private static List stringList(final String a) {
		final List list = new ArrayList();
		list.add(JsonValue.ofString(a));
		return list;
	}

	private static List stringList(final String a, final String b) {
		final List list = stringList(a);
		list.add(JsonValue.ofString(b));
		return list;
	}

	private static List stringList(final String a, final String b, final String c) {
		final List list = stringList(a, b);
		list.add(JsonValue.ofString(c));
		return list;
	}

	private static boolean isGet(final String method) {
		return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
	}

	private static String firstHeader(final HttpExchange exchange, final String name) {
		if (exchange == null) {
			return "";
		}
		final java.util.List values = exchange.getRequestHeaders().get(name);
		if (values == null || values.isEmpty() || values.get(0) == null) {
			return "";
		}
		return String.valueOf(values.get(0)).trim();
	}

	private static String readBody(final HttpExchange exchange) throws IOException {
		final InputStream in = exchange.getRequestBody();
		final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
		final byte[] chunk = new byte[4096];
		int n;
		while ((n = in.read(chunk)) >= 0) {
			if (n > 0) {
				buf.write(chunk, 0, n);
			}
		}
		return new String(buf.toByteArray(), UTF8);
	}

	private static void writeJson(final HttpExchange exchange, final int status, final String body) throws IOException {
		final byte[] bytes = body.getBytes(UTF8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.getResponseBody().close();
	}

	private static void writeOAuthError(final HttpExchange exchange, final int status, final String error,
			final String desc) throws IOException {
		final Map<String, JsonValue> map = new LinkedHashMap<String, JsonValue>();
		map.put("error", JsonValue.ofString(error));
		if (desc != null && desc.length() > 0) {
			map.put("error_description", JsonValue.ofString(desc));
		}
		writeJson(exchange, status, JsonWriter.write(JsonValue.ofMap(map)));
	}

	private static String urlEncode(final String value) {
		try {
			return URLEncoder.encode(value == null ? "" : value, "UTF-8");
		}
		catch (Exception e) {
			return "";
		}
	}

	private static String urlDecode(final String value) {
		try {
			return URLDecoder.decode(value == null ? "" : value.replace('+', ' '), "UTF-8");
		}
		catch (Exception e) {
			return value == null ? "" : value;
		}
	}

	private static String replace(final String src, final String token, final String value) {
		final String with = value == null ? "" : value;
		int from = 0;
		final StringBuilder sb = new StringBuilder(src.length() + 16);
		while (true) {
			final int idx = src.indexOf(token, from);
			if (idx < 0) {
				sb.append(src.substring(from));
				return sb.toString();
			}
			sb.append(src.substring(from, idx)).append(with);
			from = idx + token.length();
		}
	}

	private static String escapeHtml(final String raw) {
		final String text = raw == null ? "" : raw;
		final StringBuilder sb = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			final char ch = text.charAt(i);
			if (ch == '&') {
				sb.append("&amp;");
			}
			else if (ch == '<') {
				sb.append("&lt;");
			}
			else if (ch == '>') {
				sb.append("&gt;");
			}
			else if (ch == '"') {
				sb.append("&quot;");
			}
			else {
				sb.append(ch);
			}
		}
		return sb.toString();
	}
}
