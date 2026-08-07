package org.docear.plugin.mcp.audit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
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
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private static final int SCHEMA_VERSION = 2;
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
		mergeEvents(events);
	}

	/**
	 * Insert events; skip duplicates by event_id. Update traces/aggregates only for newly inserted rows.
	 */
	int mergeEvents(final List<McpAuditEvent> events) throws SQLException {
		if (events == null || events.isEmpty()) {
			return 0;
		}
		Connection connection = null;
		int merged = 0;
		try {
			connection = openConnection();
			connection.setAutoCommit(false);
			for (int i = 0; i < events.size(); i++) {
				final McpAuditEvent event = events.get(i);
				if (eventExists(connection, event.eventId)) {
					continue;
				}
				insertOne(connection, event);
				updateTraceSummary(connection, event);
				updateAggregates(connection, event);
				merged++;
			}
			connection.commit();
			return merged;
		}
		catch (SQLException e) {
			if (connection != null) {
				try {
					connection.rollback();
				}
				catch (SQLException ignored) {
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
		final McpAuditQuery q = new McpAuditQuery();
		q.limit = limit;
		q.intent = intentFilter != null ? intentFilter : "";
		q.traceId = traceIdFilter != null ? traceIdFilter : "";
		q.text = questionQuery != null ? questionQuery : "";
		q.action = actionFilter != null ? actionFilter : "";
		q.sinceMillis = sinceMillis;
		final List<Map<String, Object>> rows = queryEventRows(q);
		final List<JsonValue> items = new ArrayList<JsonValue>();
		for (int i = 0; i < rows.size(); i++) {
			items.add(JsonValue.ofMap(plainToJsonMap(rows.get(i))));
		}
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("dbPath", JsonValue.ofString(dbFile.getAbsolutePath()));
		result.put("machineId", JsonValue.ofString(McpAuditMachineId.getMachineId()));
		result.put("count", JsonValue.ofNumber(Integer.valueOf(items.size())));
		result.put("entries", JsonValue.ofList(items));
		return JsonWriter.write(JsonValue.ofMap(result));
	}

	String listTraces(final int limit, final String questionQuery, final long sinceMillis) throws SQLException {
		final McpAuditQuery q = McpAuditQuery.ofLimit(limit);
		q.text = questionQuery != null ? questionQuery : "";
		q.sinceMillis = sinceMillis;
		final List<Map<String, Object>> rows = queryTraceRows(q);
		final List<JsonValue> items = new ArrayList<JsonValue>();
		for (int i = 0; i < rows.size(); i++) {
			items.add(JsonValue.ofMap(plainToJsonMap(rows.get(i))));
		}
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("dbPath", JsonValue.ofString(dbFile.getAbsolutePath()));
		result.put("count", JsonValue.ofNumber(Integer.valueOf(items.size())));
		result.put("traces", JsonValue.ofList(items));
		return JsonWriter.write(JsonValue.ofMap(result));
	}

	String getStats(final String granularity, final int limit, final String intentFilter, final String actionFilter,
	    final long sinceMillis) throws SQLException {
		final String table = aggregateTable(granularity);
		final StringBuilder sql = new StringBuilder(
		    "SELECT bucket_ts, machine_id, machine_name, tenant, actor, action, intent, call_count, success_count, fail_count,"
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
				row.put("machineId", JsonValue.ofString(nullToEmpty(rs.getString("machine_id"))));
				row.put("machineName", JsonValue.ofString(nullToEmpty(rs.getString("machine_name"))));
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
		return queryEventRows(McpAuditQuery.ofLimit(limit));
	}

	List<Map<String, Object>> listTraceRows(final int limit) throws SQLException {
		return queryTraceRows(McpAuditQuery.ofLimit(limit));
	}

	List<Map<String, Object>> queryEventRows(final McpAuditQuery query) throws SQLException {
		final McpAuditQuery q = query != null ? query : McpAuditQuery.ofLimit(200);
		final StringBuilder sql = new StringBuilder(
		    "SELECT id, event_id, machine_id, machine_name, ts, tenant, actor, action, kind, intent, trace_id, session_id,"
		        + " client_name, os_user, remote_address, question_summary, operation_goal, request_json, response_json,"
		        + " response_bytes, response_truncated, success, duration_ms, error_message FROM audit_event WHERE 1=1");
		final List<Object> params = new ArrayList<Object>();
		appendCommonFilters(sql, params, q, true);
		sql.append(" ORDER BY ts DESC LIMIT ?");
		params.add(Integer.valueOf(q.limit > 0 ? q.limit : 200));
		return runPlainQuery(sql.toString(), params, true);
	}

	List<Map<String, Object>> queryTraceRows(final McpAuditQuery query) throws SQLException {
		final McpAuditQuery q = query != null ? query : McpAuditQuery.ofLimit(200);
		final StringBuilder sql = new StringBuilder(
		    "SELECT machine_id, machine_name, trace_id, tenant, question_summary, actor, first_ts, last_ts, call_count, actions"
		        + " FROM audit_trace WHERE trace_id <> ''");
		final List<Object> params = new ArrayList<Object>();
		if (q.machineId != null && q.machineId.length() > 0) {
			sql.append(" AND machine_id = ?");
			params.add(q.machineId);
		}
		if (q.actor != null && q.actor.length() > 0) {
			sql.append(" AND actor = ?");
			params.add(q.actor);
		}
		if (q.text != null && q.text.length() > 0) {
			sql.append(" AND (question_summary LIKE ? OR actions LIKE ? OR trace_id LIKE ?)");
			final String like = "%" + q.text + "%";
			params.add(like);
			params.add(like);
			params.add(like);
		}
		if (q.sinceMillis > 0L) {
			sql.append(" AND last_ts >= ?");
			params.add(Long.valueOf(q.sinceMillis));
		}
		if (q.untilMillis > 0L) {
			sql.append(" AND last_ts <= ?");
			params.add(Long.valueOf(q.untilMillis));
		}
		sql.append(" ORDER BY last_ts DESC LIMIT ?");
		params.add(Integer.valueOf(q.limit > 0 ? q.limit : 200));

		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(sql.toString());
			bindParams(statement, params);
			rs = statement.executeQuery();
			final List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
			while (rs.next()) {
				final Map<String, Object> row = new LinkedHashMap<String, Object>();
				row.put("machineId", nullToEmpty(rs.getString("machine_id")));
				row.put("machineName", nullToEmpty(rs.getString("machine_name")));
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

	Map<String, Object> summarize(final McpAuditQuery query) throws SQLException {
		final McpAuditQuery q = query != null ? query : new McpAuditQuery();
		final StringBuilder sql = new StringBuilder(
		    "SELECT COUNT(*) AS c, SUM(CASE WHEN success=1 THEN 1 ELSE 0 END) AS ok_c,"
		        + " SUM(CASE WHEN success=0 THEN 1 ELSE 0 END) AS fail_c, SUM(duration_ms) AS sum_ms,"
		        + " AVG(duration_ms) AS avg_ms, MAX(duration_ms) AS max_ms, MIN(duration_ms) AS min_ms,"
		        + " COUNT(DISTINCT machine_id) AS machines FROM audit_event WHERE 1=1");
		final List<Object> params = new ArrayList<Object>();
		appendCommonFilters(sql, params, q, true);
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(sql.toString());
			bindParams(statement, params);
			rs = statement.executeQuery();
			final Map<String, Object> row = new LinkedHashMap<String, Object>();
			if (rs.next()) {
				row.put("count", Integer.valueOf(rs.getInt("c")));
				row.put("successCount", Integer.valueOf(rs.getInt("ok_c")));
				row.put("failCount", Integer.valueOf(rs.getInt("fail_c")));
				row.put("totalDurationMs", Long.valueOf(rs.getLong("sum_ms")));
				row.put("avgDurationMs", Long.valueOf(Math.round(rs.getDouble("avg_ms"))));
				row.put("maxDurationMs", Long.valueOf(rs.getLong("max_ms")));
				row.put("minDurationMs", Long.valueOf(rs.getLong("min_ms")));
				row.put("machineCount", Integer.valueOf(rs.getInt("machines")));
			}
			return row;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	List<Map<String, Object>> statsByMachine(final McpAuditQuery query) throws SQLException {
		return groupStats(query,
		    "machine_id, machine_name",
		    "machine_id, machine_name");
	}

	List<Map<String, Object>> statsByAction(final McpAuditQuery query) throws SQLException {
		return groupStats(query, "action, intent", "action, intent");
	}

	List<Map<String, Object>> statsByDay(final McpAuditQuery query) throws SQLException {
		final McpAuditQuery q = query != null ? query : new McpAuditQuery();
		final StringBuilder sql = new StringBuilder(
		    "SELECT ((ts / 86400000) * 86400000) AS bucket_ts, COUNT(*) AS c,"
		        + " SUM(CASE WHEN success=1 THEN 1 ELSE 0 END) AS ok_c,"
		        + " SUM(CASE WHEN success=0 THEN 1 ELSE 0 END) AS fail_c,"
		        + " SUM(duration_ms) AS sum_ms, AVG(duration_ms) AS avg_ms, MAX(duration_ms) AS max_ms"
		        + " FROM audit_event WHERE 1=1");
		final List<Object> params = new ArrayList<Object>();
		appendCommonFilters(sql, params, q, true);
		sql.append(" GROUP BY bucket_ts ORDER BY bucket_ts DESC LIMIT ?");
		params.add(Integer.valueOf(q.limit > 0 ? Math.min(q.limit, 366) : 90));
		return runGroupQuery(sql.toString(), params, true);
	}

	List<Map<String, Object>> listSlowEvents(final McpAuditQuery query) throws SQLException {
		final McpAuditQuery q = query != null ? query : McpAuditQuery.ofLimit(50);
		final StringBuilder sql = new StringBuilder(
		    "SELECT id, event_id, machine_id, machine_name, ts, tenant, actor, action, kind, intent, trace_id, session_id,"
		        + " client_name, os_user, remote_address, question_summary, operation_goal, request_json, response_json,"
		        + " response_bytes, response_truncated, success, duration_ms, error_message FROM audit_event WHERE 1=1");
		final List<Object> params = new ArrayList<Object>();
		appendCommonFilters(sql, params, q, true);
		sql.append(" ORDER BY duration_ms DESC LIMIT ?");
		params.add(Integer.valueOf(q.limit > 0 ? q.limit : 50));
		return runPlainQuery(sql.toString(), params, true);
	}

	List<Map<String, Object>> listMachines() throws SQLException {
		Connection connection = null;
		Statement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.createStatement();
			rs = statement.executeQuery(
			    "SELECT machine_id, MAX(machine_name) AS machine_name, COUNT(*) AS c, MAX(ts) AS last_ts"
			        + " FROM audit_event GROUP BY machine_id ORDER BY last_ts DESC");
			final List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
			while (rs.next()) {
				final Map<String, Object> row = new LinkedHashMap<String, Object>();
				row.put("machineId", nullToEmpty(rs.getString("machine_id")));
				row.put("machineName", nullToEmpty(rs.getString("machine_name")));
				row.put("count", Integer.valueOf(rs.getInt("c")));
				row.put("lastTs", Long.valueOf(rs.getLong("last_ts")));
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

	List<String> distinctValues(final String column) throws SQLException {
		final String col;
		if ("actor".equals(column)) {
			col = "actor";
		}
		else if ("action".equals(column)) {
			col = "action";
		}
		else if ("intent".equals(column)) {
			col = "intent";
		}
		else {
			return new ArrayList<String>();
		}
		Connection connection = null;
		Statement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.createStatement();
			rs = statement.executeQuery(
			    "SELECT DISTINCT " + col + " FROM audit_event WHERE " + col + " <> '' ORDER BY " + col + " LIMIT 500");
			final List<String> items = new ArrayList<String>();
			while (rs.next()) {
				items.add(nullToEmpty(rs.getString(1)));
			}
			return items;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	int exportJsonl(final File target, final McpAuditQuery query) throws Exception {
		final McpAuditQuery q = query != null ? query : new McpAuditQuery();
		q.limit = q.limit > 0 ? q.limit : 100000;
		final List<Map<String, Object>> rows = queryEventRows(q);
		final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(target), UTF8));
		try {
			for (int i = 0; i < rows.size(); i++) {
				writer.write(JsonWriter.write(JsonValue.ofMap(plainToJsonMap(rows.get(i)))));
				writer.write('\n');
			}
		}
		finally {
			writer.close();
		}
		return rows.size();
	}

	int importJsonl(final File source) throws Exception {
		if (source == null || !source.isFile()) {
			return 0;
		}
		final List<McpAuditEvent> batch = new ArrayList<McpAuditEvent>();
		final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(source), UTF8));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0) {
					continue;
				}
				try {
					final JsonValue value = org.docear.plugin.mcp.json.JsonParser.parse(line);
					batch.add(McpAuditService.eventFromMap(value.asMap()));
				}
				catch (Exception e) {
					LogUtils.warn("Skip bad audit JSONL line: " + e.getMessage());
				}
			}
		}
		finally {
			reader.close();
		}
		return mergeEvents(batch);
	}

	int importFromDbFile(final File otherDb) throws Exception {
		if (otherDb == null || !otherDb.isFile()) {
			return 0;
		}
		if (otherDb.getCanonicalPath().equals(dbFile.getCanonicalPath())) {
			return 0;
		}
		final McpAuditDatabase other = new McpAuditDatabase(otherDb);
		final McpAuditQuery q = new McpAuditQuery();
		q.limit = 500000;
		final List<Map<String, Object>> rows = other.queryEventRows(q);
		final List<McpAuditEvent> events = new ArrayList<McpAuditEvent>();
		for (int i = 0; i < rows.size(); i++) {
			events.add(McpAuditService.eventFromMap(plainToJsonMap(rows.get(i))));
		}
		return mergeEvents(events);
	}

	private List<Map<String, Object>> groupStats(final McpAuditQuery query, final String selectCols,
	    final String groupCols) throws SQLException {
		final McpAuditQuery q = query != null ? query : new McpAuditQuery();
		final StringBuilder sql = new StringBuilder("SELECT ").append(selectCols)
		    .append(", COUNT(*) AS c, SUM(CASE WHEN success=1 THEN 1 ELSE 0 END) AS ok_c,"
		        + " SUM(CASE WHEN success=0 THEN 1 ELSE 0 END) AS fail_c,"
		        + " SUM(duration_ms) AS sum_ms, AVG(duration_ms) AS avg_ms, MAX(duration_ms) AS max_ms"
		        + " FROM audit_event WHERE 1=1");
		final List<Object> params = new ArrayList<Object>();
		appendCommonFilters(sql, params, q, true);
		sql.append(" GROUP BY ").append(groupCols).append(" ORDER BY c DESC LIMIT ?");
		params.add(Integer.valueOf(q.limit > 0 ? q.limit : 100));
		return runGroupQuery(sql.toString(), params, selectCols.indexOf("machine") >= 0);
	}

	private List<Map<String, Object>> runGroupQuery(final String sql, final List<Object> params,
	    final boolean hasMachine) throws SQLException {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(sql);
			bindParams(statement, params);
			rs = statement.executeQuery();
			final List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
			while (rs.next()) {
				final Map<String, Object> row = new LinkedHashMap<String, Object>();
				try {
					if (hasColumn(rs, "machine_id")) {
						row.put("machineId", nullToEmpty(rs.getString("machine_id")));
						row.put("machineName", nullToEmpty(rs.getString("machine_name")));
					}
				}
				catch (Exception ignored) {
				}
				try {
					if (hasColumn(rs, "action")) {
						row.put("action", nullToEmpty(rs.getString("action")));
					}
				}
				catch (Exception ignored) {
				}
				try {
					if (hasColumn(rs, "intent")) {
						row.put("intent", nullToEmpty(rs.getString("intent")));
					}
				}
				catch (Exception ignored) {
				}
				try {
					if (hasColumn(rs, "bucket_ts")) {
						row.put("bucketTs", Long.valueOf(rs.getLong("bucket_ts")));
					}
				}
				catch (Exception ignored) {
				}
				row.put("count", Integer.valueOf(rs.getInt("c")));
				row.put("successCount", Integer.valueOf(rs.getInt("ok_c")));
				row.put("failCount", Integer.valueOf(rs.getInt("fail_c")));
				row.put("totalDurationMs", Long.valueOf(rs.getLong("sum_ms")));
				row.put("avgDurationMs", Long.valueOf(Math.round(rs.getDouble("avg_ms"))));
				row.put("maxDurationMs", Long.valueOf(rs.getLong("max_ms")));
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

	private static boolean hasColumn(final ResultSet rs, final String name) {
		try {
			rs.findColumn(name);
			return true;
		}
		catch (SQLException e) {
			return false;
		}
	}

	private List<Map<String, Object>> runPlainQuery(final String sql, final List<Object> params, final boolean events)
	    throws SQLException {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			connection = openConnection();
			statement = connection.prepareStatement(sql);
			bindParams(statement, params);
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

	private void appendCommonFilters(final StringBuilder sql, final List<Object> params, final McpAuditQuery q,
	    final boolean eventTable) {
		if (q.machineId != null && q.machineId.length() > 0) {
			sql.append(" AND machine_id = ?");
			params.add(q.machineId);
		}
		appendFilter(sql, params, "actor", q.actor);
		appendFilter(sql, params, "action", q.action);
		appendFilter(sql, params, "intent", q.intent);
		appendFilter(sql, params, "trace_id", q.traceId);
		if ("ok".equalsIgnoreCase(q.result)) {
			sql.append(" AND success = 1");
		}
		else if ("fail".equalsIgnoreCase(q.result)) {
			sql.append(" AND success = 0");
		}
		if (q.sinceMillis > 0L) {
			sql.append(" AND ts >= ?");
			params.add(Long.valueOf(q.sinceMillis));
		}
		if (q.untilMillis > 0L) {
			sql.append(" AND ts <= ?");
			params.add(Long.valueOf(q.untilMillis));
		}
		if (q.minDurationMs > 0L) {
			sql.append(" AND duration_ms >= ?");
			params.add(Long.valueOf(q.minDurationMs));
		}
		if (q.text != null && q.text.length() > 0) {
			if (q.searchPayload) {
				sql.append(" AND (question_summary LIKE ? OR operation_goal LIKE ? OR action LIKE ?"
				    + " OR actor LIKE ? OR trace_id LIKE ? OR request_json LIKE ? OR response_json LIKE ?"
				    + " OR error_message LIKE ? OR machine_name LIKE ? OR machine_id LIKE ?)");
				final String like = "%" + q.text + "%";
				for (int i = 0; i < 10; i++) {
					params.add(like);
				}
			}
			else {
				sql.append(" AND (question_summary LIKE ? OR operation_goal LIKE ? OR action LIKE ?"
				    + " OR actor LIKE ? OR trace_id LIKE ? OR machine_name LIKE ? OR error_message LIKE ?)");
				final String like = "%" + q.text + "%";
				for (int i = 0; i < 7; i++) {
					params.add(like);
				}
			}
		}
	}

	private static Map<String, Object> toPlainMap(final Map<String, JsonValue> source) {
		final Map<String, Object> row = new LinkedHashMap<String, Object>();
		if (source == null) {
			return row;
		}
		row.put("id", Long.valueOf(num(source, "id")));
		row.put("eventId", str(source, "eventId"));
		row.put("machineId", str(source, "machineId"));
		row.put("machineName", str(source, "machineName"));
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

	private static Map<String, JsonValue> plainToJsonMap(final Map<String, Object> source) {
		final Map<String, JsonValue> map = new LinkedHashMap<String, JsonValue>();
		if (source == null) {
			return map;
		}
		for (final Map.Entry<String, Object> e : source.entrySet()) {
			final Object v = e.getValue();
			if (v == null) {
				map.put(e.getKey(), JsonValue.ofString(""));
			}
			else if (v instanceof Boolean) {
				map.put(e.getKey(), JsonValue.ofBoolean(((Boolean) v).booleanValue()));
			}
			else if (v instanceof Integer) {
				map.put(e.getKey(), JsonValue.ofNumber((Integer) v));
			}
			else if (v instanceof Long) {
				map.put(e.getKey(), JsonValue.ofNumber((Long) v));
			}
			else if (v instanceof Number) {
				map.put(e.getKey(), JsonValue.ofNumber(Long.valueOf(((Number) v).longValue())));
			}
			else {
				map.put(e.getKey(), JsonValue.ofString(String.valueOf(v)));
			}
		}
		// normalize errorMessage alias for import
		if (map.containsKey("error") && !map.containsKey("errorMessage")) {
			map.put("errorMessage", map.get("error"));
		}
		return map;
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
			statement.execute("CREATE TABLE IF NOT EXISTS audit_meta (key TEXT PRIMARY KEY, value TEXT)");
			statement.execute("CREATE TABLE IF NOT EXISTS audit_event ("
			    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
			    + "event_id TEXT NOT NULL,"
			    + "machine_id TEXT NOT NULL DEFAULT '',"
			    + "machine_name TEXT NOT NULL DEFAULT '',"
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
			migrateEventColumns(connection, statement);
			statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_audit_event_id ON audit_event(event_id)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_ts ON audit_event(ts)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_tenant ON audit_event(tenant)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_event(actor)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_event(action)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_trace ON audit_event(trace_id)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_intent ON audit_event(intent)");
			statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_machine ON audit_event(machine_id)");
			migrateTraceAndAggregates(connection, statement);
			backfillLegacyEventIds(connection, statement);
			setMeta(statement, "schema_version", String.valueOf(SCHEMA_VERSION));
			LogUtils.info("Docear MCP audit database ready (v" + SCHEMA_VERSION + "): " + dbFile.getAbsolutePath()
			    + " machine=" + McpAuditMachineId.getMachineId());
		}
		catch (SQLException e) {
			LogUtils.warn("Docear MCP audit database init failed: " + e.getMessage(), e);
		}
		finally {
			closeQuietly(statement);
			closeQuietly(connection);
		}
	}

	private void migrateEventColumns(final Connection connection, final Statement statement) throws SQLException {
		final Set<String> cols = tableColumns(connection, "audit_event");
		if (!cols.contains("event_id")) {
			statement.execute("ALTER TABLE audit_event ADD COLUMN event_id TEXT");
		}
		if (!cols.contains("machine_id")) {
			statement.execute("ALTER TABLE audit_event ADD COLUMN machine_id TEXT NOT NULL DEFAULT ''");
		}
		if (!cols.contains("machine_name")) {
			statement.execute("ALTER TABLE audit_event ADD COLUMN machine_name TEXT NOT NULL DEFAULT ''");
		}
	}

	private void backfillLegacyEventIds(final Connection connection, final Statement statement) throws SQLException {
		final String machineId = McpAuditMachineId.getMachineId();
		final String machineName = McpAuditMachineId.getMachineName();
		statement.executeUpdate("UPDATE audit_event SET machine_id = '" + sqlEscape(machineId)
		    + "' WHERE machine_id IS NULL OR machine_id = ''");
		statement.executeUpdate("UPDATE audit_event SET machine_name = '" + sqlEscape(machineName)
		    + "' WHERE machine_name IS NULL OR machine_name = ''");
		ResultSet rs = null;
		PreparedStatement update = null;
		try {
			rs = statement.executeQuery(
			    "SELECT id FROM audit_event WHERE event_id IS NULL OR event_id = ''");
			update = connection.prepareStatement("UPDATE audit_event SET event_id = ? WHERE id = ?");
			while (rs.next()) {
				final long id = rs.getLong(1);
				update.setString(1, "legacy-" + machineId + "-" + id);
				update.setLong(2, id);
				update.addBatch();
			}
			update.executeBatch();
		}
		finally {
			closeQuietly(rs);
			closeQuietly(update);
		}
	}

	private void migrateTraceAndAggregates(final Connection connection, final Statement statement) throws SQLException {
		final boolean needTraceRebuild = !tableExists(connection, "audit_trace")
		    || !tableColumns(connection, "audit_trace").contains("machine_id")
		    || !isTraceCompositePk(connection);
		if (needTraceRebuild) {
			rebuildTraceTable(connection, statement);
		}
		rebuildAggregateIfNeeded(connection, statement, "audit_agg_minute");
		rebuildAggregateIfNeeded(connection, statement, "audit_agg_hour");
		rebuildAggregateIfNeeded(connection, statement, "audit_agg_day");
	}

	private boolean isTraceCompositePk(final Connection connection) {
		// Heuristic: new table has machine_id column and we store schema_version>=2
		try {
			final String ver = getMeta(connection, "schema_version");
			return ver != null && Integer.parseInt(ver) >= 2
			    && tableColumns(connection, "audit_trace").contains("machine_id");
		}
		catch (Exception e) {
			return tableColumns(connection, "audit_trace").contains("machine_id");
		}
	}

	private void rebuildTraceTable(final Connection connection, final Statement statement) throws SQLException {
		final String machineId = McpAuditMachineId.getMachineId();
		final String machineName = McpAuditMachineId.getMachineName();
		statement.execute("DROP TABLE IF EXISTS audit_trace_new");
		statement.execute("CREATE TABLE audit_trace_new ("
		    + "machine_id TEXT NOT NULL,"
		    + "machine_name TEXT NOT NULL DEFAULT '',"
		    + "trace_id TEXT NOT NULL,"
		    + "tenant TEXT NOT NULL DEFAULT 'default',"
		    + "question_summary TEXT,"
		    + "actor TEXT,"
		    + "first_ts INTEGER NOT NULL,"
		    + "last_ts INTEGER NOT NULL,"
		    + "call_count INTEGER NOT NULL DEFAULT 0,"
		    + "actions TEXT,"
		    + "PRIMARY KEY (machine_id, trace_id))");
		if (tableExists(connection, "audit_trace")) {
			final Set<String> cols = tableColumns(connection, "audit_trace");
			if (cols.contains("machine_id")) {
				statement.execute("INSERT OR IGNORE INTO audit_trace_new"
				    + " (machine_id, machine_name, trace_id, tenant, question_summary, actor, first_ts, last_ts, call_count, actions)"
				    + " SELECT machine_id, COALESCE(machine_name,''), trace_id, tenant, question_summary, actor, first_ts, last_ts, call_count, actions"
				    + " FROM audit_trace");
			}
			else {
				statement.execute("INSERT OR IGNORE INTO audit_trace_new"
				    + " (machine_id, machine_name, trace_id, tenant, question_summary, actor, first_ts, last_ts, call_count, actions)"
				    + " SELECT '" + sqlEscape(machineId) + "', '" + sqlEscape(machineName)
				    + "', trace_id, tenant, question_summary, actor, first_ts, last_ts, call_count, actions FROM audit_trace");
			}
			statement.execute("DROP TABLE audit_trace");
		}
		statement.execute("ALTER TABLE audit_trace_new RENAME TO audit_trace");
		statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_trace_last ON audit_trace(last_ts)");
	}

	private void rebuildAggregateIfNeeded(final Connection connection, final Statement statement, final String tableName)
	    throws SQLException {
		if (!tableExists(connection, tableName)) {
			createAggregateTable(statement, tableName);
			return;
		}
		final Set<String> cols = tableColumns(connection, tableName);
		if (cols.contains("machine_id")) {
			return;
		}
		final String machineId = McpAuditMachineId.getMachineId();
		final String machineName = McpAuditMachineId.getMachineName();
		final String tmp = tableName + "_new";
		statement.execute("DROP TABLE IF EXISTS " + tmp);
		createAggregateTableNamed(statement, tmp);
		statement.execute("INSERT OR IGNORE INTO " + tmp
		    + " (bucket_ts, machine_id, machine_name, tenant, actor, action, intent, call_count, success_count, fail_count,"
		    + " total_duration_ms, total_response_bytes)"
		    + " SELECT bucket_ts, '" + sqlEscape(machineId) + "', '" + sqlEscape(machineName)
		    + "', tenant, actor, action, intent, call_count, success_count, fail_count, total_duration_ms, total_response_bytes"
		    + " FROM " + tableName);
		statement.execute("DROP TABLE " + tableName);
		statement.execute("ALTER TABLE " + tmp + " RENAME TO " + tableName);
		statement.execute("CREATE INDEX IF NOT EXISTS idx_" + tableName + "_bucket ON " + tableName + "(bucket_ts)");
	}

	private static void createAggregateTable(final Statement statement, final String tableName) throws SQLException {
		createAggregateTableNamed(statement, tableName);
		statement.execute("CREATE INDEX IF NOT EXISTS idx_" + tableName + "_bucket ON " + tableName + "(bucket_ts)");
	}

	private static void createAggregateTableNamed(final Statement statement, final String tableName) throws SQLException {
		statement.execute("CREATE TABLE IF NOT EXISTS " + tableName + " ("
		    + "bucket_ts INTEGER NOT NULL,"
		    + "machine_id TEXT NOT NULL DEFAULT '',"
		    + "machine_name TEXT NOT NULL DEFAULT '',"
		    + "tenant TEXT NOT NULL DEFAULT 'default',"
		    + "actor TEXT NOT NULL DEFAULT '',"
		    + "action TEXT NOT NULL,"
		    + "intent TEXT NOT NULL,"
		    + "call_count INTEGER NOT NULL DEFAULT 0,"
		    + "success_count INTEGER NOT NULL DEFAULT 0,"
		    + "fail_count INTEGER NOT NULL DEFAULT 0,"
		    + "total_duration_ms INTEGER NOT NULL DEFAULT 0,"
		    + "total_response_bytes INTEGER NOT NULL DEFAULT 0,"
		    + "PRIMARY KEY (bucket_ts, machine_id, tenant, actor, action, intent))");
	}

	private void insertOne(final Connection connection, final McpAuditEvent event) throws SQLException {
		PreparedStatement statement = null;
		try {
			statement = connection.prepareStatement(
			    "INSERT INTO audit_event (event_id, machine_id, machine_name, ts, tenant, actor, action, kind, intent,"
			        + " trace_id, session_id, client_name, os_user, remote_address, question_summary, operation_goal,"
			        + " request_json, response_json, response_bytes, response_truncated, success, duration_ms, error_message)"
			        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
			int index = 1;
			statement.setString(index++, event.eventId);
			statement.setString(index++, event.machineId);
			statement.setString(index++, event.machineName);
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
			statement.executeUpdate();
		}
		finally {
			closeQuietly(statement);
		}
	}

	private boolean eventExists(final Connection connection, final String eventId) throws SQLException {
		if (eventId == null || eventId.length() == 0) {
			return false;
		}
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			statement = connection.prepareStatement("SELECT 1 FROM audit_event WHERE event_id = ? LIMIT 1");
			statement.setString(1, eventId);
			rs = statement.executeQuery();
			return rs.next();
		}
		finally {
			closeQuietly(rs);
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
			    "SELECT call_count, actions FROM audit_trace WHERE machine_id = ? AND trace_id = ?");
			select.setString(1, event.machineId);
			select.setString(2, event.traceId);
			rs = select.executeQuery();
			if (rs.next()) {
				final int callCount = rs.getInt("call_count") + 1;
				final String actions = mergeActions(rs.getString("actions"), event.action);
				update = connection.prepareStatement(
				    "UPDATE audit_trace SET last_ts = ?, call_count = ?, actions = ?, machine_name = ?,"
				        + " question_summary = CASE WHEN length(?) > 0 THEN ? ELSE question_summary END,"
				        + " actor = CASE WHEN length(?) > 0 THEN ? ELSE actor END"
				        + " WHERE machine_id = ? AND trace_id = ?");
				update.setLong(1, event.ts);
				update.setInt(2, callCount);
				update.setString(3, actions);
				update.setString(4, event.machineName);
				update.setString(5, event.questionSummary);
				update.setString(6, event.questionSummary);
				update.setString(7, event.actor);
				update.setString(8, event.actor);
				update.setString(9, event.machineId);
				update.setString(10, event.traceId);
				update.executeUpdate();
			}
			else {
				insert = connection.prepareStatement(
				    "INSERT INTO audit_trace (machine_id, machine_name, trace_id, tenant, question_summary, actor,"
				        + " first_ts, last_ts, call_count, actions) VALUES (?,?,?,?,?,?,?,?,?,?)");
				insert.setString(1, event.machineId);
				insert.setString(2, event.machineName);
				insert.setString(3, event.traceId);
				insert.setString(4, event.tenant);
				insert.setString(5, event.questionSummary);
				insert.setString(6, event.actor);
				insert.setLong(7, event.ts);
				insert.setLong(8, event.ts);
				insert.setInt(9, 1);
				insert.setString(10, event.action);
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
			    + " total_duration_ms = total_duration_ms + ?, total_response_bytes = total_response_bytes + ?,"
			    + " machine_name = ?"
			    + " WHERE bucket_ts = ? AND machine_id = ? AND tenant = ? AND actor = ? AND action = ? AND intent = ?");
			update.setInt(1, event.success ? 1 : 0);
			update.setInt(2, event.success ? 0 : 1);
			update.setLong(3, event.durationMs);
			update.setLong(4, event.responseBytes);
			update.setString(5, event.machineName);
			update.setLong(6, bucketTs);
			update.setString(7, event.machineId);
			update.setString(8, event.tenant);
			update.setString(9, event.actor);
			update.setString(10, event.action);
			update.setString(11, event.intent.name());
			final int updated = update.executeUpdate();
			if (updated > 0) {
				return;
			}
			insert = connection.prepareStatement("INSERT INTO " + table
			    + " (bucket_ts, machine_id, machine_name, tenant, actor, action, intent, call_count, success_count, fail_count,"
			    + " total_duration_ms, total_response_bytes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");
			insert.setLong(1, bucketTs);
			insert.setString(2, event.machineId);
			insert.setString(3, event.machineName);
			insert.setString(4, event.tenant);
			insert.setString(5, event.actor);
			insert.setString(6, event.action);
			insert.setString(7, event.intent.name());
			insert.setInt(8, 1);
			insert.setInt(9, event.success ? 1 : 0);
			insert.setInt(10, event.success ? 0 : 1);
			insert.setLong(11, event.durationMs);
			insert.setLong(12, event.responseBytes);
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
		row.put("eventId", JsonValue.ofString(nullToEmpty(safeGetString(rs, "event_id"))));
		row.put("machineId", JsonValue.ofString(nullToEmpty(safeGetString(rs, "machine_id"))));
		row.put("machineName", JsonValue.ofString(nullToEmpty(safeGetString(rs, "machine_name"))));
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
		row.put("errorMessage", JsonValue.ofString(nullToEmpty(rs.getString("error_message"))));
		return row;
	}

	private static String safeGetString(final ResultSet rs, final String col) {
		try {
			return rs.getString(col);
		}
		catch (SQLException e) {
			return "";
		}
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

	private static Set<String> tableColumns(final Connection connection, final String table) {
		final Set<String> cols = new LinkedHashSet<String>();
		Statement statement = null;
		ResultSet rs = null;
		try {
			statement = connection.createStatement();
			rs = statement.executeQuery("PRAGMA table_info(" + table + ")");
			while (rs.next()) {
				cols.add(nullToEmpty(rs.getString("name")).toLowerCase());
			}
		}
		catch (SQLException e) {
			// ignore
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
		}
		return cols;
	}

	private static boolean tableExists(final Connection connection, final String table) {
		Statement statement = null;
		ResultSet rs = null;
		try {
			statement = connection.createStatement();
			rs = statement.executeQuery(
			    "SELECT name FROM sqlite_master WHERE type='table' AND name='" + sqlEscape(table) + "'");
			return rs.next();
		}
		catch (SQLException e) {
			return false;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
		}
	}

	private static void setMeta(final Statement statement, final String key, final String value) throws SQLException {
		statement.executeUpdate("INSERT OR REPLACE INTO audit_meta(key,value) VALUES ('" + sqlEscape(key) + "','"
		    + sqlEscape(value) + "')");
	}

	private static String getMeta(final Connection connection, final String key) {
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			statement = connection.prepareStatement("SELECT value FROM audit_meta WHERE key = ?");
			statement.setString(1, key);
			rs = statement.executeQuery();
			return rs.next() ? rs.getString(1) : null;
		}
		catch (SQLException e) {
			return null;
		}
		finally {
			closeQuietly(rs);
			closeQuietly(statement);
		}
	}

	private static String sqlEscape(final String value) {
		return value == null ? "" : value.replace("'", "''");
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
