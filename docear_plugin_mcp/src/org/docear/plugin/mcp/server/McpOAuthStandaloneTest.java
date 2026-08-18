package org.docear.plugin.mcp.server;

/**
 * Headless PKCE + redirect allowlist + code/token exchange for Grok OAuth.
 */
public final class McpOAuthStandaloneTest {
	private McpOAuthStandaloneTest() {
	}

	public static void main(final String[] args) {
		assertPkce();
		assertRedirects();
		assertClientId();
		assertCodeFlow();
		System.out.println("McpOAuthStandaloneTest OK");
	}

	private static void assertPkce() {
		final String verifier = McpOAuthPkce.randomHex(32);
		final String challenge = McpOAuthPkce.challengeS256(verifier);
		if (!McpOAuthPkce.matches(verifier, challenge)) {
			throw new IllegalStateException("pkce match");
		}
		if (McpOAuthPkce.matches(verifier + "x", challenge)) {
			throw new IllegalStateException("pkce must reject wrong verifier");
		}
		if (McpOAuthPkce.matches("short", challenge)) {
			throw new IllegalStateException("pkce short verifier");
		}
	}

	private static void assertRedirects() {
		if (!McpOAuthRedirects.allowed("https://grok.com/oauth/callback")) {
			throw new IllegalStateException("grok.com");
		}
		if (!McpOAuthRedirects.allowed("https://accounts.x.ai/callback")) {
			throw new IllegalStateException("x.ai");
		}
		if (!McpOAuthRedirects.allowed("http://127.0.0.1:56121/callback")) {
			throw new IllegalStateException("loopback");
		}
		if (McpOAuthRedirects.allowed("http://evil.example/cb")) {
			throw new IllegalStateException("http non-loopback must fail");
		}
		if (McpOAuthRedirects.allowed("https://evil.example/cb")) {
			throw new IllegalStateException("random https must fail");
		}
	}

	private static void assertClientId() {
		if (!McpOAuthService.validClientId("twigmark") || !McpOAuthService.validClientId("Grok-1")) {
			throw new IllegalStateException("valid client");
		}
		if (McpOAuthService.validClientId("bad id") || McpOAuthService.validClientId("")) {
			throw new IllegalStateException("invalid client");
		}
	}

	private static void assertCodeFlow() {
		final McpOAuthService svc = McpOAuthService.forTests(new McpOAuthService.PasswordGate() {
			public String verify(final String username, final String password) {
				if ("alice".equals(username) && "secret-pass".equals(password)) {
					return "alice";
				}
				throw new IllegalArgumentException("invalid username or password");
			}
		});
		final String verifier = McpOAuthPkce.randomHex(32);
		final String challenge = McpOAuthPkce.challengeS256(verifier);
		final String code;
		try {
			code = svc.loginAndCreateCode("alice", "secret-pass", "twigmark",
					"https://grok.com/oauth/callback", challenge, "mcp");
		}
		catch (Exception e) {
			throw new IllegalStateException("login create code", e);
		}
		try {
			svc.loginAndCreateCode("alice", "nope", "twigmark", "https://grok.com/oauth/callback", challenge, "mcp");
			throw new IllegalStateException("bad password must fail");
		}
		catch (IllegalArgumentException expected) {
			// ok
		}
		catch (Exception e) {
			throw new IllegalStateException("unexpected", e);
		}
		try {
			svc.exchangeCode(code, "twigmark", "https://grok.com/oauth/callback",
					"wrong-verifier-wrong-verifier-wrong-xx");
			throw new IllegalStateException("wrong verifier must fail");
		}
		catch (IllegalArgumentException expected) {
			// ok, code still usable
		}
		final java.util.Map tokens = svc.exchangeCode(code, "twigmark", "https://grok.com/oauth/callback", verifier);
		final String access = (String) tokens.get("access_token");
		if (access == null || !access.startsWith("mto_")) {
			throw new IllegalStateException("access token " + access);
		}
		final McpPrincipal principal = svc.resolveAccessToken(access);
		if (principal == null || !"alice".equals(principal.getName())) {
			throw new IllegalStateException("resolve " + principal);
		}
		if (principal.getRole() != McpRole.READ) {
			throw new IllegalStateException("oauth default role must be read: " + principal.getRole());
		}
		if (svc.resolveAccessToken("mto_deadbeef") != null) {
			throw new IllegalStateException("unknown token");
		}
	}
}
