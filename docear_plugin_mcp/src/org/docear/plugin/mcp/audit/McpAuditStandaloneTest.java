package org.docear.plugin.mcp.audit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.DocearMcpConfig;

/**
 * Headless smoke test: MAC id, per-machine db files, multi-db catalog avg latency.
 */
public final class McpAuditStandaloneTest {

	public static void main(final String[] args) throws Exception {
		final File dataDir = new File(System.getProperty("java.io.tmpdir"), "docear-audit-mac-multidb");
		wipeDir(dataDir);
		dataDir.mkdirs();
		DocearMcpConfig.setAuditDataDirForTests(dataDir);

		McpAuditMachineId.setForTests("mac-aabbccddeeff", "pc-a");
		if (!"aabbccddeeff".equals(McpAuditMachineId.getMacHex())) {
			throw new IllegalStateException("mac hex: " + McpAuditMachineId.getMacHex());
		}

		final File local = DocearMcpConfig.getAuditDbFile();
		if (!"audit-aabbccddeeff.db".equals(local.getName())) {
			throw new IllegalStateException("local db name: " + local.getName());
		}
		McpAuditDatabase.resetForTests(local);
		final McpAuditDatabase a = McpAuditDatabase.getInstance();
		final long ts = System.currentTimeMillis();
		final List batchA = new ArrayList();
		batchA.add(McpAuditEvent.local(ts, "default", "agent", "search_nodes", "tool", McpOperationIntent.MINDMAP,
		    "t1", "s", "c", "u", "127.0.0.1", "q1", "g1", "{}", "{}", 1, false, true, 100L, ""));
		batchA.add(McpAuditEvent.local(ts + 1, "default", "agent", "search_nodes", "tool", McpOperationIntent.MINDMAP,
		    "t1", "s", "c", "u", "127.0.0.1", "q2", "g2", "{}", "{}", 1, false, true, 300L, ""));
		batchA.add(McpAuditEvent.local(ts + 2, "default", "agent", "get_selection_context", "tool",
		    McpOperationIntent.CONTEXT, "t2", "s", "c", "u", "127.0.0.1", "q3", "g3", "{}", "{}", 1, false, true, 10L,
		    ""));
		a.mergeEvents(batchA);

		// Peer machine file already present in synced folder
		final File dbB = new File(dataDir, "audit-112233445566.db");
		McpAuditMachineId.setForTests("mac-112233445566", "pc-b");
		final McpAuditDatabase b = new McpAuditDatabase(dbB);
		final List batchB = new ArrayList();
		batchB.add(new McpAuditEvent("e-b-1", "mac-112233445566", "pc-b", ts + 3, "default", "agent", "search_nodes",
		    "tool", McpOperationIntent.MINDMAP, "tb", "s", "c", "u", "127.0.0.1", "qb", "gb", "{}", "{}", 1, false, true,
		    200L, ""));
		b.mergeEvents(batchB);

		// Restore local machine identity for catalog write target naming
		McpAuditMachineId.setForTests("mac-aabbccddeeff", "pc-a");

		final File[] files = DocearMcpConfig.listAuditDbFiles();
		if (files.length < 2) {
			throw new IllegalStateException("expected >=2 audit db files, got " + files.length);
		}
		if (McpAuditCatalog.databaseCount() < 2) {
			throw new IllegalStateException("catalog db count: " + McpAuditCatalog.databaseCount());
		}

		final McpAuditQuery q = new McpAuditQuery();
		q.limit = 100;
		final List byAction = McpAuditCatalog.statsByAction(q);
		Map search = null;
		for (int i = 0; i < byAction.size(); i++) {
			final Map row = (Map) byAction.get(i);
			if ("search_nodes".equals(String.valueOf(row.get("action")))) {
				search = row;
				break;
			}
		}
		if (search == null) {
			throw new IllegalStateException("search_nodes missing from catalog stats: " + byAction);
		}
		final long avg = ((Number) search.get("avgDurationMs")).longValue();
		final int count = ((Number) search.get("count")).intValue();
		if (count != 3 || avg != 200L) {
			throw new IllegalStateException("search_nodes count=" + count + " avg=" + avg + " row=" + search);
		}

		final Map sum = McpAuditCatalog.summarize(q);
		if (((Number) sum.get("databaseCount")).intValue() < 2) {
			throw new IllegalStateException("summarize databaseCount: " + sum);
		}

		// Legacy audit.db rename into audit-<mac>.db
		final File legacyDir = new File(dataDir, "legacy");
		legacyDir.mkdirs();
		DocearMcpConfig.setAuditDataDirForTests(legacyDir);
		McpAuditMachineId.setForTests("mac-deadbeefcafe", "legacy-host");
		final File legacy = new File(legacyDir, "audit.db");
		createMinimalDb(legacy);
		final File migrated = DocearMcpConfig.getAuditDbFile();
		if (!migrated.getName().equals("audit-deadbeefcafe.db") || !migrated.isFile()) {
			throw new IllegalStateException("legacy migrate failed: " + migrated);
		}
		if (legacy.isFile()) {
			throw new IllegalStateException("legacy audit.db should have been renamed");
		}

		DocearMcpConfig.setAuditDataDirForTests(null);
		System.out.println("PASS McpAuditStandaloneTest");
		System.out.println("search_nodes avgMs=" + avg + " dbs=" + files.length);
	}

	private static void wipeDir(final File dir) {
		if (!dir.exists()) {
			return;
		}
		final File[] kids = dir.listFiles();
		if (kids != null) {
			for (int i = 0; i < kids.length; i++) {
				if (kids[i].isDirectory()) {
					wipeDir(kids[i]);
				}
				kids[i].delete();
			}
		}
		dir.delete();
	}

	private static void createMinimalDb(final File dbFile) throws Exception {
		Class.forName("org.sqlite.JDBC");
		final java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
		try {
			final java.sql.Statement s = c.createStatement();
			s.execute("CREATE TABLE audit_event (id INTEGER PRIMARY KEY, ts INTEGER, action TEXT)");
			s.execute("INSERT INTO audit_event(ts,action) VALUES (1,'x')");
			s.close();
		}
		finally {
			c.close();
		}
	}
}
