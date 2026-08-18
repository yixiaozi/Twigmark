package org.docear.plugin.mcp.server;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.webchat.WebchatDatabase;
import org.docear.plugin.mcp.webchat.WebchatService;
import org.freeplane.core.util.LogUtils;

/**
 * Authorization-code + PKCE for remote MCP clients (Grok custom connector).
 */
public final class McpOAuthService {
	public static final String CLIENT_ID = "twigmark";
	public static final String SCOPE_MCP = "mcp";

	private static final McpOAuthService INSTANCE = new McpOAuthService(true);

	private final Map codes = new ConcurrentHashMap();
	private final Map tokens = new ConcurrentHashMap();
	private final Map refresh = new ConcurrentHashMap();
	private final boolean persist;
	private PasswordGate gate = new DefaultGate();

	public interface PasswordGate {
		String verify(String username, String password) throws Exception;
	}

	private static final class DefaultGate implements PasswordGate {
		public String verify(final String username, final String password) throws Exception {
			return WebchatService.authenticateUser(username, password);
		}
	}

	private static final class AuthCode {
		String clientId;
		String redirectUri;
		String challenge;
		String username;
		String scope;
		long expiresAt;
	}

	static final class AccessGrant {
		String username;
		String clientId;
		String scope;
		McpRole role;
		long expiresAt;
	}

	private McpOAuthService(final boolean persist) {
		this.persist = persist;
	}

	public static McpOAuthService get() {
		return INSTANCE;
	}

	static McpOAuthService forTests(final PasswordGate gate) {
		final McpOAuthService svc = new McpOAuthService(false);
		svc.gate = gate;
		return svc;
	}

	public void setPasswordGate(final PasswordGate gate) {
		this.gate = gate == null ? new DefaultGate() : gate;
	}

	public static boolean validClientId(final String clientId) {
		if (clientId == null) {
			return false;
		}
		final String id = clientId.trim();
		if (id.length() < 2 || id.length() > 128) {
			return false;
		}
		for (int i = 0; i < id.length(); i++) {
			final char ch = id.charAt(i);
			if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '-'
					|| ch == '_' || ch == '.' || ch == '~') {
				continue;
			}
			return false;
		}
		return true;
	}

	public String createCode(final String username, final String clientId, final String redirectUri,
			final String challenge, final String scope) {
		if (username == null || username.length() == 0) {
			throw new IllegalArgumentException("login required");
		}
		if (!validClientId(clientId)) {
			throw new IllegalArgumentException("invalid client_id");
		}
		if (!McpOAuthRedirects.allowed(redirectUri)) {
			throw new IllegalArgumentException("redirect_uri is not allowed");
		}
		if (challenge == null || challenge.trim().length() < 20) {
			throw new IllegalArgumentException("code_challenge required (S256)");
		}
		pruneCodes();
		final AuthCode row = new AuthCode();
		row.clientId = clientId.trim();
		row.redirectUri = redirectUri.trim();
		row.challenge = challenge.trim();
		row.username = username;
		row.scope = normalizeScope(scope);
		row.expiresAt = System.currentTimeMillis() + 10L * 60L * 1000L;
		final String code = "mc_" + McpOAuthPkce.randomHex(24);
		codes.put(code, row);
		return code;
	}

	public String loginAndCreateCode(final String username, final String password, final String clientId,
			final String redirectUri, final String challenge, final String scope) throws Exception {
		final String user = gate.verify(username, password);
		return createCode(user, clientId, redirectUri, challenge, scope);
	}

	public Map<String, String> exchangeCode(final String code, final String clientId, final String redirectUri,
			final String verifier) {
		if (code == null || code.length() == 0) {
			throw new IllegalArgumentException("invalid_grant");
		}
		final AuthCode row = (AuthCode) codes.get(code);
		if (row == null || row.expiresAt < System.currentTimeMillis()) {
			codes.remove(code);
			throw new IllegalArgumentException("invalid_grant");
		}
		if (clientId != null && clientId.trim().length() > 0 && !row.clientId.equals(clientId.trim())) {
			throw new IllegalArgumentException("invalid_grant");
		}
		if (redirectUri != null && redirectUri.trim().length() > 0 && !row.redirectUri.equals(redirectUri.trim())) {
			throw new IllegalArgumentException("invalid_grant");
		}
		if (!McpOAuthPkce.matches(verifier, row.challenge)) {
			throw new IllegalArgumentException("invalid_grant");
		}
		codes.remove(code);
		return issueTokens(row.username, row.clientId, row.scope);
	}

	public Map<String, String> refresh(final String refreshToken) {
		if (refreshToken == null || refreshToken.length() == 0) {
			throw new IllegalArgumentException("invalid_grant");
		}
		final String hash = McpOAuthPkce.sha256Hex(refreshToken.trim());
		AccessGrant grant = (AccessGrant) this.refresh.remove(hash);
		if (grant == null && persist) {
			grant = loadByRefresh(hash);
		}
		if (grant == null || grant.expiresAt < System.currentTimeMillis()) {
			throw new IllegalArgumentException("invalid_grant");
		}
		deleteRefresh(hash);
		return issueTokens(grant.username, grant.clientId, grant.scope);
	}

	public McpPrincipal resolveAccessToken(final String token) {
		if (token == null || !token.startsWith("mto_")) {
			return null;
		}
		final String hash = McpOAuthPkce.sha256Hex(token.trim());
		AccessGrant grant = (AccessGrant) tokens.get(hash);
		if (grant == null && persist) {
			grant = loadByAccess(hash);
			if (grant != null) {
				tokens.put(hash, grant);
			}
		}
		if (grant == null || grant.expiresAt < System.currentTimeMillis()) {
			return null;
		}
		return new McpPrincipal("oauth:" + grant.username, grant.username, grant.role, "oauth");
	}

	private Map<String, String> issueTokens(final String username, final String clientId, final String scope) {
		final int ttl = DocearMcpConfig.getOauthAccessTtlSeconds();
		final long now = System.currentTimeMillis();
		final AccessGrant grant = new AccessGrant();
		grant.username = username;
		grant.clientId = clientId;
		grant.scope = scope;
		grant.role = DocearMcpConfig.getOauthRole();
		grant.expiresAt = now + ttl * 1000L;
		final String access = "mto_" + McpOAuthPkce.randomHex(24);
		final String refreshTok = "mtr_" + McpOAuthPkce.randomHex(24);
		final String accessHash = McpOAuthPkce.sha256Hex(access);
		final String refreshHash = McpOAuthPkce.sha256Hex(refreshTok);
		tokens.put(accessHash, grant);
		refresh.put(refreshHash, grant);
		if (persist) {
			try {
				WebchatDatabase.local().insertOauthToken(accessHash, refreshHash, username, clientId, scope,
						grant.role.getName(), grant.expiresAt);
			}
			catch (Exception e) {
				LogUtils.warn("oauth persist token failed: " + e.getMessage());
			}
		}
		final Map<String, String> out = new java.util.LinkedHashMap<String, String>();
		out.put("access_token", access);
		out.put("token_type", "Bearer");
		out.put("expires_in", String.valueOf(ttl));
		out.put("refresh_token", refreshTok);
		out.put("scope", scope);
		return out;
	}

	private static String normalizeScope(final String scope) {
		if (scope == null || scope.trim().length() == 0) {
			return SCOPE_MCP;
		}
		return scope.trim();
	}

	private void pruneCodes() {
		final long now = System.currentTimeMillis();
		final Iterator it = codes.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry e = (Map.Entry) it.next();
			final AuthCode row = (AuthCode) e.getValue();
			if (row.expiresAt < now) {
				it.remove();
			}
		}
	}

	private AccessGrant loadByAccess(final String accessHash) {
		try {
			return fromRow(WebchatDatabase.local().findOauthTokenByAccess(accessHash));
		}
		catch (Exception e) {
			return null;
		}
	}

	private AccessGrant loadByRefresh(final String refreshHash) {
		try {
			return fromRow(WebchatDatabase.local().findOauthTokenByRefresh(refreshHash));
		}
		catch (Exception e) {
			return null;
		}
	}

	private void deleteRefresh(final String refreshHash) {
		if (!persist) {
			return;
		}
		try {
			WebchatDatabase.local().deleteOauthTokenByRefresh(refreshHash);
		}
		catch (Exception ignored) {
		}
	}

	private AccessGrant fromRow(final Map row) {
		if (row == null) {
			return null;
		}
		final AccessGrant grant = new AccessGrant();
		grant.username = String.valueOf(row.get("username"));
		grant.clientId = String.valueOf(row.get("clientId"));
		grant.scope = String.valueOf(row.get("scope"));
		grant.role = McpRole.parse(String.valueOf(row.get("role")));
		final Object exp = row.get("expiresAt");
		grant.expiresAt = exp instanceof Number ? ((Number) exp).longValue() : 0L;
		return grant;
	}
}
