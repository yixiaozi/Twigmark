package org.docear.plugin.mcp.server;

/**
 * Authenticated caller for one MCP/HTTP request.
 */
public final class McpPrincipal {
	public static final String SOURCE_LOCAL = "local";
	public static final String SOURCE_KEY = "key";
	public static final String SOURCE_WEB = "web";
	public static final String SOURCE_OAUTH = "oauth";

	private final String id;
	private final String name;
	private final McpRole role;
	private final String source;

	public McpPrincipal(final String id, final String name, final McpRole role, final String source) {
		this.id = id == null || id.length() == 0 ? "anonymous" : id;
		this.name = name == null || name.length() == 0 ? this.id : name;
		this.role = role == null ? McpRole.READ : role;
		this.source = source == null ? SOURCE_KEY : source;
	}

	public static McpPrincipal localOwner() {
		return new McpPrincipal("local", "local-owner", McpRole.OWNER, SOURCE_LOCAL);
	}

	public static McpPrincipal anonymousRead() {
		return new McpPrincipal("anonymous", "anonymous", McpRole.READ, SOURCE_LOCAL);
	}

	public static McpPrincipal web(final String username, final McpRole role) {
		final String id = username == null || username.length() == 0 ? "web" : username;
		return new McpPrincipal("web:" + id, id, role, SOURCE_WEB);
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public McpRole getRole() {
		return role;
	}

	public String getSource() {
		return source;
	}
}
