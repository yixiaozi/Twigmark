package org.docear.plugin.mcp.server;

/**
 * MCP access roles for server-side keys and web sessions.
 * <ul>
 * <li>{@link #READ} — search/read maps, todos, tags, graph; no mutations</li>
 * <li>{@link #WRITE} — read plus map/todo/tag/pomodoro/finance edits</li>
 * <li>{@link #OWNER} — write plus git, encryption, audit, snapshot export</li>
 * </ul>
 */
public final class McpRole {
	public static final McpRole READ = new McpRole("read", 1);
	public static final McpRole WRITE = new McpRole("write", 2);
	public static final McpRole OWNER = new McpRole("owner", 3);

	private final String name;
	private final int rank;

	private McpRole(final String name, final int rank) {
		this.name = name;
		this.rank = rank;
	}

	public String getName() {
		return name;
	}

	public int getRank() {
		return rank;
	}

	public boolean atLeast(final McpRole required) {
		return required != null && rank >= required.rank;
	}

	public static McpRole parse(final String raw) {
		if (raw == null) {
			return OWNER;
		}
		final String value = raw.trim().toLowerCase();
		if ("read".equals(value) || "readonly".equals(value) || "viewer".equals(value)) {
			return READ;
		}
		if ("write".equals(value) || "editor".equals(value)) {
			return WRITE;
		}
		if ("owner".equals(value) || "admin".equals(value) || "full".equals(value)) {
			return OWNER;
		}
		return OWNER;
	}

	public String toString() {
		return name;
	}
}
