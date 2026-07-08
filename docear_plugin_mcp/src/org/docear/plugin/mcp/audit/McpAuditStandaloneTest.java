package org.docear.plugin.mcp.audit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class McpAuditStandaloneTest {

	public static void main(final String[] args) throws Exception {
		final File dataDir = new File("_data");
		final File dbFile = new File(dataDir, "audit_standalone.db");
		if (dbFile.exists()) {
			dbFile.delete();
		}
		McpAuditDatabase.resetForTests(dbFile);
		final McpAuditDatabase database = McpAuditDatabase.getInstance();
		final McpAuditWriter writer = new McpAuditWriter(database, new File(dataDir, "audit_overflow.jsonl"));
		writer.start();

		final long ts = System.currentTimeMillis();
		final McpAuditEvent event = new McpAuditEvent(ts, "default", "standalone-java", "get_selection_context", "tool",
		    McpOperationIntent.CONTEXT, "trace-java-1", "session-1", "audit-test", "tester", "127.0.0.1", "Java 审计测试",
		    "写入一条测试事件", "{\"includeFolded\":true}", "{\"mapFile\":\"demo.mm\"}", 18, false, true, 15L, "");
		writer.enqueue(event);
		writer.shutdown();

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
		if (database.countEvents() < 1) {
			throw new IllegalStateException("countEvents returned zero");
		}
		System.out.println("PASS McpAuditStandaloneTest");
		System.out.println("db=" + dbFile.getAbsolutePath());
	}
}
