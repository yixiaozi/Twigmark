package org.docear.plugin.mcp.audit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Headless DB merge/migration smoke test (no Freeplane ResourceController).
 */
public final class McpAuditStandaloneTest {

	public static void main(final String[] args) throws Exception {
		final File dataDir = new File(System.getProperty("java.io.tmpdir"), "docear-audit-standalone");
		dataDir.mkdirs();
		final File dbFile = new File(dataDir, "audit_standalone.db");
		if (dbFile.exists()) {
			dbFile.delete();
		}
		final File wal = new File(dbFile.getAbsolutePath() + "-wal");
		final File shm = new File(dbFile.getAbsolutePath() + "-shm");
		if (wal.exists()) {
			wal.delete();
		}
		if (shm.exists()) {
			shm.delete();
		}

		McpAuditMachineId.setForTests("m-test-aaaa", "test-host");
		McpAuditDatabase.resetForTests(dbFile);
		final McpAuditDatabase database = McpAuditDatabase.getInstance();

		final long ts = System.currentTimeMillis();
		final List<McpAuditEvent> batch = new ArrayList<McpAuditEvent>();
		batch.add(McpAuditEvent.local(ts, "default", "standalone-java", "get_selection_context", "tool",
		    McpOperationIntent.CONTEXT, "trace-java-1", "session-1", "audit-test", "tester", "127.0.0.1", "Java 审计测试",
		    "写入一条测试事件", "{\"includeFolded\":true}", "{\"mapFile\":\"demo.mm\"}", 18, false, true, 15L, ""));
		batch.add(new McpAuditEvent("evt-other-1", "m-other-bbbb", "other-host", ts + 1, "default", "cursor-agent",
		    "search_nodes", "tool", McpOperationIntent.MINDMAP, "trace-other", "s2", "c", "u", "127.0.0.1", "跨机器导入",
		    "搜节点", "{}", "{}", 2, false, true, 120L, ""));
		final int inserted = database.mergeEvents(batch);
		if (inserted != 2) {
			throw new IllegalStateException("expected insert 2, got " + inserted);
		}
		final int again = database.mergeEvents(batch);
		if (again != 0) {
			throw new IllegalStateException("duplicate merge should insert 0, got " + again);
		}

		final String log = database.listEvents(5, "CONTEXT", "trace-java-1", "Java", "", 0L);
		if (log.indexOf("get_selection_context") < 0 || log.indexOf("demo.mm") < 0) {
			throw new IllegalStateException("listEvents missing expected payload: " + log);
		}
		final String traces = database.listTraces(5, "Java", 0L);
		if (traces.indexOf("trace-java-1") < 0) {
			throw new IllegalStateException("listTraces missing trace: " + traces);
		}
		final String stats = database.getStats("minute", 5, "CONTEXT", "get_selection_context", 0L);
		if (stats.indexOf("callCount") < 0) {
			throw new IllegalStateException("getStats missing bucket: " + stats);
		}
		if (database.countEvents() != 2) {
			throw new IllegalStateException("expected 2 events, got " + database.countEvents());
		}
		final List<Map<String, Object>> machines = database.listMachines();
		if (machines.size() != 2) {
			throw new IllegalStateException("expected 2 machines, got " + machines.size());
		}

		final File export = new File(dataDir, "export.jsonl");
		final McpAuditQuery q = new McpAuditQuery();
		q.limit = 1000;
		final int exported = database.exportJsonl(export, q);
		if (exported != 2) {
			throw new IllegalStateException("export count: " + exported);
		}

		final File db2 = new File(dataDir, "audit2.db");
		if (db2.exists()) {
			db2.delete();
		}
		McpAuditMachineId.setForTests("m-import-cccc", "import-host");
		McpAuditDatabase.resetForTests(db2);
		final int imported = McpAuditDatabase.getInstance().importJsonl(export);
		if (imported != 2) {
			throw new IllegalStateException("import count: " + imported);
		}
		if (McpAuditDatabase.getInstance().listMachines().size() != 2) {
			throw new IllegalStateException("imported db should keep original machine ids");
		}

		// legacy migration: create old-shaped db without event_id/machine_id
		final File legacy = new File(dataDir, "legacy.db");
		if (legacy.exists()) {
			legacy.delete();
		}
		createLegacyDb(legacy);
		McpAuditMachineId.setForTests("m-legacy-dddd", "legacy-host");
		McpAuditDatabase.resetForTests(legacy);
		final McpAuditDatabase legacyDb = McpAuditDatabase.getInstance();
		if (legacyDb.countEvents() < 1) {
			throw new IllegalStateException("legacy migration lost rows");
		}
		final Map<String, Object> first = legacyDb.listEventRows(1).get(0);
		if (String.valueOf(first.get("eventId")).indexOf("legacy-") < 0) {
			throw new IllegalStateException("legacy eventId not backfilled: " + first.get("eventId"));
		}
		if (!"m-legacy-dddd".equals(String.valueOf(first.get("machineId")))) {
			throw new IllegalStateException("legacy machineId not backfilled: " + first.get("machineId"));
		}

		System.out.println("PASS McpAuditStandaloneTest");
		System.out.println("db=" + dbFile.getAbsolutePath());
		System.out.println("exported=" + exported + " imported=" + imported + " machines=" + machines.size());
	}

	private static void createLegacyDb(final File dbFile) throws Exception {
		Class.forName("org.sqlite.JDBC");
		final java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
		try {
			final java.sql.Statement s = c.createStatement();
			s.execute("CREATE TABLE audit_event ("
			    + "id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL,"
			    + "tenant TEXT NOT NULL DEFAULT 'default', actor TEXT NOT NULL DEFAULT '',"
			    + "action TEXT NOT NULL, kind TEXT NOT NULL, intent TEXT NOT NULL,"
			    + "trace_id TEXT NOT NULL DEFAULT '', session_id TEXT, client_name TEXT, os_user TEXT,"
			    + "remote_address TEXT, question_summary TEXT, operation_goal TEXT,"
			    + "request_json TEXT, response_json TEXT, response_bytes INTEGER NOT NULL DEFAULT 0,"
			    + "response_truncated INTEGER NOT NULL DEFAULT 0, success INTEGER NOT NULL DEFAULT 1,"
			    + "duration_ms INTEGER NOT NULL DEFAULT 0, error_message TEXT)");
			s.execute("CREATE TABLE audit_trace ("
			    + "trace_id TEXT PRIMARY KEY, tenant TEXT, question_summary TEXT, actor TEXT,"
			    + "first_ts INTEGER, last_ts INTEGER, call_count INTEGER, actions TEXT)");
			s.execute("INSERT INTO audit_event (ts, tenant, actor, action, kind, intent, trace_id, question_summary,"
			    + " operation_goal, request_json, response_json, success, duration_ms)"
			    + " VALUES (" + System.currentTimeMillis()
			    + ",'default','old','list_todos','tool','TASK','t-old','旧数据','goal','{}','{}',1,9)");
			s.close();
		}
		finally {
			c.close();
		}
	}
}
