package org.docear.plugin.mcp.server;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Headless checks for MCP roles, tool ACL, and mcp-access.json key store.
 */
public final class McpAccessStandaloneTest {
	private McpAccessStandaloneTest() {
	}

	public static void main(final String[] args) throws Exception {
		assertRoleParse();
		assertToolAcl();
		assertKeysFile();
		assertLegacyKey();
		System.out.println("McpAccessStandaloneTest OK");
	}

	private static void assertRoleParse() {
		if (McpRole.parse("read") != McpRole.READ) {
			throw new IllegalStateException("parse read");
		}
		if (McpRole.parse("WRITE") != McpRole.WRITE) {
			throw new IllegalStateException("parse write");
		}
		if (McpRole.parse("admin") != McpRole.OWNER) {
			throw new IllegalStateException("parse admin");
		}
		if (!McpRole.OWNER.atLeast(McpRole.WRITE) || McpRole.READ.atLeast(McpRole.WRITE)) {
			throw new IllegalStateException("rank");
		}
	}

	private static void assertToolAcl() {
		System.clearProperty("mcp.readonly");
		if (!McpPermissions.canCall(McpRole.READ, "search_nodes")) {
			throw new IllegalStateException("read can search");
		}
		if (McpPermissions.canCall(McpRole.READ, "add_node")) {
			throw new IllegalStateException("read cannot add_node");
		}
		if (!McpPermissions.canCall(McpRole.WRITE, "add_node")) {
			throw new IllegalStateException("write can add_node");
		}
		if (!McpPermissions.canCall(McpRole.WRITE, "copy_nodes")
				|| !McpPermissions.canCall(McpRole.WRITE, "clone_nodes")
				|| !McpPermissions.canCall(McpRole.WRITE, "undo_map")
				|| !McpPermissions.canCall(McpRole.WRITE, "add_arrow_link")
				|| !McpPermissions.canCall(McpRole.WRITE, "set_node_cloud")
				|| !McpPermissions.canCall(McpRole.WRITE, "set_node_style")
				|| !McpPermissions.canCall(McpRole.WRITE, "set_node_details")
				|| !McpPermissions.canCall(McpRole.WRITE, "set_node_privacy")
				|| !McpPermissions.canCall(McpRole.WRITE, "set_node_image")
				|| !McpPermissions.canCall(McpRole.WRITE, "clear_reminder")) {
			throw new IllegalStateException("write can new node-edit tools");
		}
		if (McpPermissions.canCall(McpRole.READ, "clone_nodes")
				|| McpPermissions.canCall(McpRole.READ, "set_node_privacy")) {
			throw new IllegalStateException("read cannot new write tools");
		}
		if (McpPermissions.canCall(McpRole.WRITE, "git_sync")) {
			throw new IllegalStateException("write cannot git_sync");
		}
		if (!McpPermissions.canCall(McpRole.OWNER, "git_sync")) {
			throw new IllegalStateException("owner can git_sync");
		}
		if (!McpPermissions.canCall(McpRole.OWNER, "list_audit_log")) {
			throw new IllegalStateException("owner can audit");
		}
		if (McpPermissions.canCall(McpRole.WRITE, "list_audit_log")) {
			throw new IllegalStateException("write cannot audit");
		}
		System.setProperty("mcp.readonly", "true");
		try {
			if (McpPermissions.canCall(McpRole.OWNER, "add_node")) {
				throw new IllegalStateException("readonly blocks owner write");
			}
			if (!McpPermissions.canCall(McpRole.OWNER, "list_audit_log")) {
				throw new IllegalStateException("readonly still allows audit");
			}
			if (!McpPermissions.canCall(McpRole.OWNER, "search_nodes")) {
				throw new IllegalStateException("readonly still allows search");
			}
		}
		finally {
			System.clearProperty("mcp.readonly");
		}
		if (!McpPermissions.isWriteTool("add_node") || !McpPermissions.isWriteTool("git_sync")) {
			throw new IllegalStateException("isWriteTool");
		}
		if (McpPermissions.isWriteTool("search_nodes")) {
			throw new IllegalStateException("search is not write");
		}
	}

	private static void assertKeysFile() throws Exception {
		final File dir = new File(System.getProperty("java.io.tmpdir"), "docear-mcp-access-test");
		wipe(dir);
		dir.mkdirs();
		final File keys = new File(dir, "mcp-access.json");
		write(keys, "{\"keys\":["
				+ "{\"id\":\"guest\",\"name\":\"Guest\",\"role\":\"read\",\"secret\":\"tm_read_secret_001\",\"enabled\":true},"
				+ "{\"id\":\"editor\",\"name\":\"Editor\",\"role\":\"write\",\"key\":\"tm_write_secret_002\"},"
				+ "{\"id\":\"off\",\"name\":\"Off\",\"role\":\"owner\",\"secret\":\"tm_off\",\"enabled\":false}"
				+ "]}");
		final McpAccessStore store = McpAccessStore.forTests(keys, "tm_legacy_owner", McpRole.OWNER);
		final McpPrincipal guest = store.resolve("tm_read_secret_001");
		if (guest == null || guest.getRole() != McpRole.READ || !"guest".equals(guest.getId())) {
			throw new IllegalStateException("guest key: " + guest);
		}
		final McpPrincipal editor = store.resolve("tm_write_secret_002");
		if (editor == null || editor.getRole() != McpRole.WRITE) {
			throw new IllegalStateException("editor key");
		}
		if (store.resolve("tm_off") != null) {
			throw new IllegalStateException("disabled key must not resolve");
		}
		if (store.resolve("tm_unknown") != null) {
			throw new IllegalStateException("unknown key");
		}
		if (store.resolve("tm_legacy_owner") == null
				|| store.resolve("tm_legacy_owner").getRole() != McpRole.OWNER) {
			throw new IllegalStateException("legacy key");
		}
	}

	private static void assertLegacyKey() {
		final McpAccessStore store = McpAccessStore.forTests(new File("/tmp/does-not-exist-mcp-access.json"),
				"tm_only_legacy", McpRole.WRITE);
		final McpPrincipal p = store.resolve("tm_only_legacy");
		if (p == null || p.getRole() != McpRole.WRITE) {
			throw new IllegalStateException("legacy-only write role");
		}
	}

	private static void write(final File file, final String text) throws Exception {
		final FileOutputStream out = new FileOutputStream(file);
		try {
			out.write(text.getBytes("UTF-8"));
		}
		finally {
			out.close();
		}
	}

	private static void wipe(final File dir) {
		if (dir == null || !dir.exists()) {
			return;
		}
		final File[] kids = dir.listFiles();
		if (kids != null) {
			for (int i = 0; i < kids.length; i++) {
				if (kids[i].isDirectory()) {
					wipe(kids[i]);
				}
				kids[i].delete();
			}
		}
		dir.delete();
	}
}
