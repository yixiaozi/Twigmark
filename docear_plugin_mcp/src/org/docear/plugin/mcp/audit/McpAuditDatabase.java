package org.docear.plugin.mcp.audit;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.freeplane.core.util.LogUtils;

final class McpAuditDatabase {

	private static final String JDBC_PREFIX = "jdbc:sqlite:";
	private static volatile McpAuditDatabase INSTANCE;

	private final File dbFile;

	static synchronized McpAuditDatabase getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new McpAuditDatabase(DocearMcpConfig.getAuditDbFile());
		}
		return INSTANCE;
	}

	static synchronized void resetForTests(final File dbFile) {
		if (INSTANCE != null) {
			INSTANCE.closeQuietly();
		}
		INSTANCE = new McpAuditDatabase(dbFile);
	}

	McpAuditDatabase(final File dbFile) {
		this.dbFile = dbFile;
		ensureSchema();
	}

	File getDbFile() {
		return dbFile;
	}

	void insertBatch(final List<McpAuditEvent> events) throws SQLException {
		if (events == null || events.isEmpty()) {
			return;
		}
		Connection connection = null;
		try {
			connection = openConnection();
			connection.setAutoCommit(false);
			insertEvents(connection, events);
			for (int i = 0; i < events.size(); i++) {
				updateTraceSummary(connection, events.get(i));
				updateAggregates(connection, events.get(i));
			}
			connection.commit();
		}
		catch (SQLException e) {
			if (connection != null) {
				try {
					connection.rollback();
				}
				catch (SQLException rollbackError) {
					LogUtils.warn("Docear MCP audit rollback failed: " + rollbackError.getMessage(), rollbackError);
				}
			}
			throw e;
		}
		finally {
			closeQuietly(connection);
		}
	}

	String listEvents(final int limit, final String intentFilter, final String traceIdFilter,
	    final String questionQuery, final String actionFilter, final long sinceMillis) throws SQLException {
		final StringBuilder sql = new StringBuilder(
		    "SELECT id, ts, tenant, actor, action, kind, intent, trace_id, session_id, client_name, os_user,"
		        + " remote_address, question_summary, operation_goal, request_json, response_json, response_bytes,"
		        + " response_truncated, success, duration_ms, error_message FROM audit_event WHERE 1=1");
		final List<Object> params = new ArrayList<Object>();
		appendFilter(sql, params, "intent", intentFilter);
		appendFilter(sql, params, "trace_id", traceIdFilter);
		appendFilter(sql, params, "action", actionFilter);
		if (questionQuery != null && questionQuery.length() > 0) {
			sql.append(" AND question_summary LIKE ?");
			params.add("%" + questionQuery + "%");
		}
		if (sinceMillis > 0L) {
			sql.append(" AND ts >= ?");
			params.add(Long.valueOf(sinceMillis));
		}
		sql.append(" ORDER BY ts DESC LIMIT ?");
		params.add(Integer.valueOf(limit > 0 ? limit : 50));

		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(sql.toString());
			bindParams(statement, params);
			rs = statement.executeQuery();
			final List<JsonValue> items = new ArrayList<JsonValue>();
			while (rs.next()) {
				items.add(JsonValue.ofMap(readEventRow(rs)));
			}
			final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
			result.put("dbPath", JsonValue.ofString(dbFile.getAbsolutePath()));
			result.put("count", JsonValue.ofNumber(Integer.valueOf(items.size())));
			result.put("entries", JsonValue.ofList(items));
			return JsonWriter.write(JsonValue.ofMap(result));
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	String listTraces(final int limit, final String questionQuery, final long sinceMillis) throws SQLException {
		final StringBuilder sql = new StringBuilder(
		    "SELECT trace_id, tenant, question_summary, actor, first_ts, last_ts, call_count, actions"
		        + " FROM audit_trace WHERE trace_id <> ''");
		final List<Object> params = new ArrayList<Object>();
		if (questionQuery != null && questionQuery.length() > 0) {
			sql.append(" AND question_summary LIKE ?");
			params.add("%" + questionQuery + "%");
		}
		if (sinceMillis > 0L) {
			sql.append(" AND last_ts >= ?");
			params.add(Long.valueOf(sinceMillis));
		}
		sql.append(" ORDER BY last_ts DESC LIMIT ?");
		params.add(Integer.valueOf(limit > 0 ? limit : 50));

		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(sql.toString());
			bindParams(statement, params);
			rs = statement.executeQuery();
			final List<JsonValue> items = new ArrayList<JsonValue>();
			while (rs.next()) {
				final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
				row.put("traceId", JsonValue.ofString(rs.getString("trace_id")));
				row.put("tenant", JsonValue.ofString(rs.getString("tenant")));
				row.put("questionSummary", JsonValue.ofString(nullToEmpty(rs.getString("question_summary"))));
				row.put("actor", JsonValue.ofString(nullToEmpty(rs.getString("actor"))));
				row.put("firstTs", JsonValue.ofNumber(Long.valueOf(rs.getLong("first_ts"))));
				row.put("lastTs", JsonValue.ofNumber(Long.valueOf(rs.getLong("last_ts"))));
				row.put("callCount", JsonValue.ofNumber(Integer.valueOf(rs.getInt("call_count"))));
				row.put("actions", JsonValue.ofString(nullToEmpty(rs.getString("actions"))));
				items.add(JsonValue.ofMap(row));
			}
			final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
			result.put("dbPath", JsonValue.ofString(dbFile.getAbsolutePath()));
			result.put("count", JsonValue.ofNumber(Integer.valueOf(items.size())));
			result.put("traces", JsonValue.ofList(items));
			return JsonWriter.write(JsonValue.ofMap(result));
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	String getStats(final String granularity, final int limit, final String intentFilter, final String actionFilter,
	    final long sinceMillis) throws SQLException {
		final String table = aggregateTable(granularity);
		final StringBuilder sql = new StringBuilder(
		    "SELECT bucket_ts, tenant, actor, action, intent, call_count, success_count, fail_count,"
		        + " total_duration_ms, total_response_bytes FROM ");
		sql.append(table).append(" WHERE 1=1");
		final List<Object> params = new ArrayList<Object>();
		appendFilter(sql, params, "intent", intentFilter);
		appendFilter(sql, params, "action", actionFilter);
		if (sinceMillis > 0L) {
			sql.append(" AND bucket_ts >= ?");
			params.add(Long.valueOf(sinceMillis));
		}
		sql.append(" ORDER BY call_count DESC, bucket_ts DESC LIMIT ?");
		params.add(Integer.valueOf(limit > 0 ? limit : 100));

		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(sql.toString());
			bindParams(statement, params);
			rs = statement.executeQuery();
			final List<JsonValue> items = new ArrayList<JsonValue>();
			while (rs.next()) {
				final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
				row.put("bucketTs", JsonValue.ofNumber(Long.valueOf(rs.getLong("bucket_ts"))));
				row.put("tenant", JsonValue.ofString(rs.getString("tenant")));
				row.put("actor", JsonValue.ofString(rs.getString("actor")));
				row.put("action", JsonValue.ofString(rs.getString("action")));
				row.put("intent", JsonValue.ofString(rs.getString("intent")));
				row.put("callCount", JsonValue.ofNumber(Integer.valueOf(rs.getInt("call_count"))));
				row.put("successCount", JsonValue.ofNumber(Integer.valueOf(rs.getInt("success_count"))));
				row.put("failCount", JsonValue.ofNumber(Integer.valueOf(rs.getInt("fail_count"))));
				row.put("totalDurationMs", JsonValue.ofNumber(Long.valueOf(rs.getLong("total_duration_ms"))));
				row.put("totalResponseBytes", JsonValue.ofNumber(Long.valueOf(rs.getLong("total_response_bytes"))));
				items.add(JsonValue.ofMap(row));
			}
			final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
			result.put("dbPath", JsonValue.ofString(dbFile.getAbsolutePath()));
			result.put("granularity", JsonValue.ofString(granularity));
			result.put("count", JsonValue.ofNumber(Integer.valueOf(items.size())));
			result.put("buckets", JsonValue.ofList(items));
			return JsonWriter.write(JsonValue.ofMap(result));
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	int countEvents() throws SQLException {
		Connection connection = null;
		Statement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.createStatement();
			rs = statement.executeQuery("SELECT COUNT(*) FROM audit_event");
			return rs.next() ? rs.getInt(1) : 0;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	List<Map<String, Object>> listEventRows(final int limit) throws SQLException {
		final String sql = "SELECT id, ts, tenant, actor, action, kind, intent, trace_id, session_id, client_name, os_user,"
		    + " remote_address, question_summary, operation_goal, request_json, response_json, response_bytes,"
		    + " response_truncated, success, duration_ms, error_message FROM audit_event ORDER BY ts DESC LIMIT ?";
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(sql);
			statement.setInt(1, limit > 0 ? limit : 50);
			rs = statement.executeQuery();
			final List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
			while (rs.next()) {
				items.add(toPlainMap(readEventRow(rs)));
			}
			return items;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	List<Map<String, Object>> listTraceRows(final int limit) throws SQLException {
		final String sql = "SELECT trace_id, tenant, question_summary, actor, first_ts, last_ts, call_count, actions"
		    + " FROM audit_trace WHERE trace_id <> '' ORDER BY last_ts DESC LIMIT ?";
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(sql);
			statement.setInt(1, limit > 0 ? limit : 50);
			rs = statement.executeQuery();
			final List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
			while (rs.next()) {
				final Map<String, Object> row = new LinkedHashMap<String, Object>();
				row.put("traceId", nullToEmpty(rs.getString("trace_id")));
				row.put("tenant", nullToEmpty(rs.getString("tenant")));
				row.put("questionSummary", nullToEmpty(rs.getString("question_summary")));
				row.put("actor", nullToEmpty(rs.getString("actor")));
				row.put("firstTs", Long.valueOf(rs.getLong("first_ts")));
				row.put("lastTs", Long.valueOf(rs.getLong("last_ts")));
				row.put("callCount", Integer.valueOf(rs.getInt("call_count")));
				row.put("actions", nullToEmpty(rs.getString("actions")));
				items.add(row);
			}
			return items;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	private static Map<String, Object> toPlainMap(final Map<String, JsonValue> source) {
		final Map<String, Object> row = new LinkedHashMap<String, Object>();
		if (source == null) {
			return row;
		}
		row.put("id", Long.valueOf(num(source, "id")));
		row.put("ts", Long.valueOf(num(source, "ts")));
		row.put("tenant", str(source, "tenant"));
		row.put("actor", str(source, "actor"));
		row.put("action", str(source, "action"));
		row.put("kind", str(source, "kind"));
		row.put("intent", str(source, "intent"));
		row.put("traceId", str(source, "traceId"));
		row.put("sessionId", str(source, "sessionId"));
		row.put("clientName", str(source, "clientName"));
		row.put("osUser", str(source, "osUser"));
		row.put("remoteAddress", str(source, "remoteAddress"));
		row.put("questionSummary", str(source, "questionSummary"));
		row.put("operationGoal", str(source, "operationGoal"));
		row.put("requestJson", str(source, "requestJson"));
		row.put("responseJson", str(source, "responseJson"));
		row.put("responseBytes", Integer.valueOf(source.containsKey("responseBytes") ? source.get("responseBytes").asInt(0) : 0));
		row.put("responseTruncated", Boolean.valueOf(bool(source, "responseTruncated")));
		row.put("success", Boolean.valueOf(bool(source, "success")));
		row.put("durationMs", Long.valueOf(num(source, "durationMs")));
		row.put("error", str(source, "error"));
		return row;
	}

	private static String str(final Map<String, JsonValue> source, final String key) {
		return source.containsKey(key) ? nullToEmpty(source.get(key).asString()) : "";
	}

	private static long num(final Map<String, JsonValue> source, final String key) {
		return source.containsKey(key) ? source.get(key).asLong(0L) : 0L;
	}

	private static boolean bool(final Map<String, JsonValue> source, final String key) {
		return source.containsKey(key) && source.get(key).asBoolean();
	}

	private void ensureSchema() {
		Connection connection = null;
		Statement statement = null;
		try {
			if (!dbFile.getParentFile().exists()) {
				dbFile.getParentFile().mkdirs();
			}
			connection = openConnection();
			statement = connection.createStatement();
			statement.execute("PRAGMA journal_mode=WAL");
			statement.execute("PRAGMA synchronous=NORMAL");
			statement.execute("CREATE TABLE IF NOT EXISTS audit_event ("
			    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
			    + "ts INTEGER NOT NULL,"
			    + "tenant TEXT NOT NULL DEFAULT 'default',"
			    + "actor TEXT NOT NULL DEFAULT '',"
			    + "action TEXT NOT NULL,"
			    + "kind TEXT NOT NULL,"
			    + "intent TEXT NOT NULL,"
			    + "trace_id TEXT NOT NULL DEFAULT '',"
			    + "session_id TEXT,"
			    + "client_name TEXT,"
			    + "os_user TEXT,"
			    + "remote_address TEXT,"
			    + "question_summary TEXT,"
			    + "operation_goal TEXT,"
			    + "request_json TEXT,"
			    + "response_json TEXT,"
			    + "response_bytes INTEGER NOT NULL DEFAULT 0,"
			    + "response_truncated INTEGER NOT NULL DEFAULT 0,"
			    + "success INTEGER NOT NULL DEFAULT 1,"
			    + "duration_ms INTEGER NOT NULL DEFAULT 0,"
			    + "error_message TEXT)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_ts ON audit_event(ts)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_tenant ON audit_event(tenant)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_event(actor)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_event(action)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_trace ON audit_event(trace_id)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_intent ON audit_event(intent)");
			statement.execute("CREATE TABLE IF NOT EXISTS audit_trace ("
			    + "trace_id TEXT PRIMARY KEY,"
			    + "tenant TEXT NOT NULL DEFAULT 'default',"
			    + "question_summary TEXT,"
			    + "actor TEXT,"
			    + "first_ts INTEGER NOT NULL,"
			    + "last_ts INTEGER NOT NULL,"
			    + "call_count INTEGER NOT NULL DEFAULT 0,"
			    + "actions TEXT)");
			createAggregateTable(statement, "audit_agg_minute");
			createAggregateTable(statement, "audit_agg_hour");
			createAggregateTable(statement, "audit_agg_day");
			LogUtils.info("Docear MCP audit database ready: " + dbFile.getAbsolutePath());
		}
		catch (SQLException e) {
			LogUtils.warn("Docear MCP audit database init failed: " + e.getMessage(), e);
		}
		finally {
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	private static void createAggregateTable(final Statement statement, final String tableName) throws SQLException {
		statement.execute("CREATE TABLE IF NOT EXISTS " + tableName + " ("
		    + "bucket_ts INTEGER NOT NULL,"
		    + "tenant TEXT NOT NULL DEFAULT 'default',"
		    + "actor TEXT NOT NULL DEFAULT '',"
		    + "action TEXT NOT NULL,"
		    + "intent TEXT NOT NULL,"
		    + "call_count INTEGER NOT NULL DEFAULT 0,"
		    + "success_count INTEGER NOT NULL DEFAULT 0,"
		    + "fail_count INTEGER NOT NULL DEFAULT 0,"
		    + "total_duration_ms INTEGER NOT NULL DEFAULT 0,"
		    + "total_response_bytes INTEGER NOT NULL DEFAULT 0,"
		    + "PRIMARY KEY (bucket_ts, tenant, actor, action, intent))");
		statement.execute("CREATE INDEX IF NOT EXISTS idx_" + tableName + "_bucket ON " + tableName + "(bucket_ts)");
	}

	private void insertEvents(final Connection connection, final List<McpAuditEvent> events) throws SQLException {
		PreparedStatement statement = null;
		try {
			statement = connection.prepareStatement("INSERT INTO audit_event (ts, tenant, actor, action, kind, intent,"
			    + " trace_id, session_id, client_name, os_user, remote_address, question_summary, operation_goal,"
			    + " request_json, response_json, response_bytes, response_truncated, success, duration_ms, error_message)"
			    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
			for (int i = 0; i < events.size(); i++) {
				final McpAuditEvent event = events.get(i);
				int index = 1;
				statement.setLong(index++, event.ts);
				statement.setString(index++, event.tenant);
				statement.setString(index++, event.actor);
				statement.setString(index++, event.action);
				statement.setString(index++, event.kind);
				statement.setString(index++, event.intent.name());
				statement.setString(index++, event.traceId);
				statement.setString(index++, event.sessionId);
				statement.setString(index++, event.clientName);
				statement.setString(index++, event.osUser);
				statement.setString(index++, event.remoteAddress);
				statement.setString(index++, event.questionSummary);
				statement.setString(index++, event.operationGoal);
				statement.setString(index++, event.requestJson);
				statement.setString(index++, event.responseJson);
				statement.setInt(index++, event.responseBytes);
				statement.setInt(index++, event.responseTruncated ? 1 : 0);
				statement.setInt(index++, event.success ? 1 : 0);
				statement.setLong(index++, event.durationMs);
				statement.setString(index++, event.errorMessage);
				statement.addBatch();
			}
			statement.executeBatch();
		}
		finally {
			closeQuietly(statement);
		}
	}

	private void updateTraceSummary(final Connection connection, final McpAuditEvent event) throws SQLException {
		if (event.traceId.length() == 0) {
			return;
		}
		PreparedStatement select = null;
		PreparedStatement update = null;
		PreparedStatement insert = null;
		ResultSet rs = null;
		try {
			select = connection.prepareStatement(
			    "SELECT call_count, actions FROM audit_trace WHERE trace_id = ?");
			select.setString(1, event.traceId);
			rs = select.executeQuery();
			if (rs.next()) {
				final int callCount = rs.getInt("call_count") + 1;
				final String actions = mergeActions(rs.getString("actions"), event.action);
				update = connection.prepareStatement(
				    "UPDATE audit_trace SET last_ts = ?, call_count = ?, actions = ?, question_summary = CASE WHEN length(?) > 0 THEN ? ELSE question_summary END, actor = CASE WHEN length(?) > 0 THEN ? ELSE actor END WHERE trace_id = ?");
				update.setLong(1, event.ts);
				update.setInt(2, callCount);
				update.setString(3, actions);
				update.setString(4, event.questionSummary);
				update.setString(5, event.questionSummary);
				update.setString(6, event.actor);
				update.setString(7, event.actor);
				update.setString(8, event.traceId);
				update.executeUpdate();
			}
			else {
				insert = connection.prepareStatement(
				    "INSERT INTO audit_trace (trace_id, tenant, question_summary, actor, first_ts, last_ts, call_count, actions)"
				        + " VALUES (?,?,?,?,?,?,?,?)");
				insert.setString(1, event.traceId);
				insert.setString(2, event.tenant);
				insert.setString(3, event.questionSummary);
				insert.setString(4, event.actor);
				insert.setLong(5, event.ts);
				insert.setLong(6, event.ts);
				insert.setInt(7, 1);
				insert.setString(8, event.action);
				insert.executeUpdate();
			}
		}
		finally {
			closeQuietly(rs);
			closeQuietly(select);
			closeQuietly(update);
			closeQuietly(insert);
		}
	}

	private void updateAggregates(final Connection connection, final McpAuditEvent event) throws SQLException {
		upsertAggregate(connection, "audit_agg_minute", bucketMinute(event.ts), event);
		upsertAggregate(connection, "audit_agg_hour", bucketHour(event.ts), event);
		upsertAggregate(connection, "audit_agg_day", bucketDay(event.ts), event);
	}

	private void upsertAggregate(final Connection connection, final String table, final long bucketTs,
	    final McpAuditEvent event) throws SQLException {
		PreparedStatement update = null;
		PreparedStatement insert = null;
		try {
			update = connection.prepareStatement("UPDATE " + table
			    + " SET call_count = call_count + 1, success_count = success_count + ?, fail_count = fail_count + ?,"
			    + " total_duration_ms = total_duration_ms + ?, total_response_bytes = total_response_bytes + ?"
			    + " WHERE bucket_ts = ? AND tenant = ? AND actor = ? AND action = ? AND intent = ?");
			update.setInt(1, event.success ? 1 : 0);
			update.setInt(2, event.success ? 0 : 1);
			update.setLong(3, event.durationMs);
			update.setLong(4, event.responseBytes);
			update.setLong(5, bucketTs);
			update.setString(6, event.tenant);
			update.setString(7, event.actor);
			update.setString(8, event.action);
			update.setString(9, event.intent.name());
			final int updated = update.executeUpdate();
			if (updated > 0) {
				return;
			}
			insert = connection.prepareStatement("INSERT INTO " + table
			    + " (bucket_ts, tenant, actor, action, intent, call_count, success_count, fail_count,"
			    + " total_duration_ms, total_response_bytes) VALUES (?,?,?,?,?,?,?,?,?,?)");
			insert.setLong(1, bucketTs);
			insert.setString(2, event.tenant);
			insert.setString(3, event.actor);
			insert.setString(4, event.action);
			insert.setString(5, event.intent.name());
			insert.setInt(6, 1);
			insert.setInt(7, event.success ? 1 : 0);
			insert.setInt(8, event.success ? 0 : 1);
			insert.setLong(9, event.durationMs);
			insert.setLong(10, event.responseBytes);
			insert.executeUpdate();
		}
		finally {
			closeQuietly(update);
			closeQuietly(insert);
		}
	}

	private static Map<String, JsonValue> readEventRow(final ResultSet rs) throws SQLException {
		final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
		row.put("id", JsonValue.ofNumber(Long.valueOf(rs.getLong("id"))));
		row.put("ts", JsonValue.ofNumber(Long.valueOf(rs.getLong("ts"))));
		row.put("tenant", JsonValue.ofString(rs.getString("tenant")));
		row.put("actor", JsonValue.ofString(rs.getString("actor")));
		row.put("action", JsonValue.ofString(rs.getString("action")));
		row.put("kind", JsonValue.ofString(rs.getString("kind")));
		row.put("intent", JsonValue.ofString(rs.getString("intent")));
		row.put("traceId", JsonValue.ofString(nullToEmpty(rs.getString("trace_id"))));
		row.put("sessionId", JsonValue.ofString(nullToEmpty(rs.getString("session_id"))));
		row.put("clientName", JsonValue.ofString(nullToEmpty(rs.getString("client_name"))));
		row.put("osUser", JsonValue.ofString(nullToEmpty(rs.getString("os_user"))));
		row.put("remoteAddress", JsonValue.ofString(nullToEmpty(rs.getString("remote_address"))));
		row.put("questionSummary", JsonValue.ofString(nullToEmpty(rs.getString("question_summary"))));
		row.put("operationGoal", JsonValue.ofString(nullToEmpty(rs.getString("operation_goal"))));
		row.put("requestJson", JsonValue.ofString(nullToEmpty(rs.getString("request_json"))));
		row.put("responseJson", JsonValue.ofString(nullToEmpty(rs.getString("response_json"))));
		row.put("responseBytes", JsonValue.ofNumber(Integer.valueOf(rs.getInt("response_bytes"))));
		row.put("responseTruncated", JsonValue.ofBoolean(rs.getInt("response_truncated") == 1));
		row.put("success", JsonValue.ofBoolean(rs.getInt("success") == 1));
		row.put("durationMs", JsonValue.ofNumber(Long.valueOf(rs.getLong("duration_ms"))));
		row.put("error", JsonValue.ofString(nullToEmpty(rs.getString("error_message"))));
		return row;
	}

	private Connection openConnection() throws SQLException {
		ClassLoader loader = McpAuditDatabase.class.getClassLoader();
		try {
			Class.forName("org.sqlite.JDBC", true, loader);
		}
		catch (ClassNotFoundException e) {
			throw new SQLException("SQLite JDBC driver not found", e);
		}
		return DriverManager.getConnection(JDBC_PREFIX + dbFile.getAbsolutePath());
	}

	private static long bucketMinute(final long ts) {
		return (ts / 60000L) * 60000L;
	}

	private static long bucketHour(final long ts) {
		return (ts / 3600000L) * 3600000L;
	}

	private static long bucketDay(final long ts) {
		return (ts / 86400000L) * 86400000L;
	}

	private static String aggregateTable(final String granularity) {
		if ("hour".equalsIgnoreCase(granularity)) {
			return "audit_agg_hour";
		}
		if ("day".equalsIgnoreCase(granularity)) {
			return "audit_agg_day";
		}
		return "audit_agg_minute";
	}

	private static String mergeActions(final String existing, final String action) {
		final Set<String> actions = new LinkedHashSet<String>();
		if (existing != null && existing.length() > 0) {
			final String[] parts = existing.split(",");
			for (int i = 0; i < parts.length; i++) {
				final String part = parts[i].trim();
				if (part.length() > 0) {
					actions.add(part);
				}
			}
		}
		if (action != null && action.length() > 0) {
			actions.add(action);
		}
		final StringBuilder sb = new StringBuilder();
		for (final String item : actions) {
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(item);
		}
		return sb.toString();
	}

	private static void appendFilter(final StringBuilder sql, final List<Object> params, final String column,
	    final String value) {
		if (value != null && value.length() > 0) {
			sql.append(" AND ").append(column).append(" = ?");
			params.add(value);
		}
	}

	private static void bindParams(final PreparedStatement statement, final List<Object> params) throws SQLException {
		for (int i = 0; i < params.size(); i++) {
			final Object param = params.get(i);
			if (param instanceof Long) {
				statement.setLong(i + 1, ((Long) param).longValue());
			}
			else if (param instanceof Integer) {
				statement.setInt(i + 1, ((Integer) param).intValue());
			}
			else {
				statement.setString(i + 1, String.valueOf(param));
			}
		}
	}

	private static String nullToEmpty(final String value) {
		return value != null ? value : "";
	}

	private void closeQuietly() {
		// no pooled connection to close
	}

	private static void closeQuietly(final ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			}
			catch (SQLException e) {
				// ignore
			}
		}
	}

	private static void closeQuietly(final Statement statement) {
		if (statement != null) {
			try {
				statement.close();
			}
			catch (SQLException e) {
				// ignore
			}
		}
	}

	private static void closeQuietly(final Connection connection) {
		if (connection != null) {
			try {
				connection.close();
			}
			catch (SQLException e) {
				// ignore
			}
		}
	}
}
