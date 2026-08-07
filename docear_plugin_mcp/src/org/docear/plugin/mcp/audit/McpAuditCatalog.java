package org.docear.plugin.mcp.audit;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.freeplane.core.util.LogUtils;

/**
 * Read-side aggregation over every {@code audit*.db} in the data directory
 * (local MAC file + peers synced into the same folder).
 */
final class McpAuditCatalog {

	private McpAuditCatalog() {
	}

	static List listDatabases() {
		final File[] files = DocearMcpConfig.listAuditDbFiles();
		final List dbs = new ArrayList();
		for (int i = 0; i < files.length; i++) {
			try {
				dbs.add(new McpAuditDatabase(files[i]));
			}
			catch (Exception e) {
				LogUtils.warn("Skip audit db " + files[i] + ": " + e.getMessage(), e);
			}
		}
		return dbs;
	}

	static int countAllEvents() {
		int total = 0;
		final List dbs = listDatabases();
		for (int i = 0; i < dbs.size(); i++) {
			try {
				total += ((McpAuditDatabase) dbs.get(i)).countEvents();
			}
			catch (Exception ignored) {
			}
		}
		return total;
	}

	static int databaseCount() {
		return DocearMcpConfig.listAuditDbFiles().length;
	}

	static List queryEventRows(final McpAuditQuery query) throws Exception {
		final McpAuditQuery q = query != null ? query : McpAuditQuery.ofLimit(500);
		final int limit = q.limit > 0 ? q.limit : 500;
		final List merged = new ArrayList();
		final List dbs = listDatabases();
		for (int i = 0; i < dbs.size(); i++) {
			final McpAuditQuery per = copyQuery(q);
			per.limit = limit;
			merged.addAll(((McpAuditDatabase) dbs.get(i)).queryEventRows(per));
		}
		sortByLongDesc(merged, "ts");
		return trim(merged, limit);
	}

	static List queryTraceRows(final McpAuditQuery query) throws Exception {
		final McpAuditQuery q = query != null ? query : McpAuditQuery.ofLimit(500);
		final int limit = q.limit > 0 ? q.limit : 500;
		final List merged = new ArrayList();
		final List dbs = listDatabases();
		for (int i = 0; i < dbs.size(); i++) {
			final McpAuditQuery per = copyQuery(q);
			per.limit = limit;
			merged.addAll(((McpAuditDatabase) dbs.get(i)).queryTraceRows(per));
		}
		sortByLongDesc(merged, "lastTs");
		return trim(merged, limit);
	}

	static List listSlowEvents(final McpAuditQuery query) throws Exception {
		final McpAuditQuery q = query != null ? query : McpAuditQuery.ofLimit(50);
		final int limit = q.limit > 0 ? q.limit : 50;
		final List merged = new ArrayList();
		final List dbs = listDatabases();
		for (int i = 0; i < dbs.size(); i++) {
			final McpAuditQuery per = copyQuery(q);
			per.limit = limit;
			merged.addAll(((McpAuditDatabase) dbs.get(i)).listSlowEvents(per));
		}
		sortByLongDesc(merged, "durationMs");
		return trim(merged, limit);
	}

	static Map summarize(final McpAuditQuery query) throws Exception {
		long count = 0;
		long ok = 0;
		long fail = 0;
		long sumMs = 0;
		long maxMs = 0;
		long minMs = Long.MAX_VALUE;
		final Map machines = new HashMap();
		final List dbs = listDatabases();
		for (int i = 0; i < dbs.size(); i++) {
			final Map part = ((McpAuditDatabase) dbs.get(i)).summarize(query);
			count += longVal(part.get("count"));
			ok += longVal(part.get("successCount"));
			fail += longVal(part.get("failCount"));
			sumMs += longVal(part.get("totalDurationMs"));
			maxMs = Math.max(maxMs, longVal(part.get("maxDurationMs")));
			final long partMin = longVal(part.get("minDurationMs"));
			if (partMin > 0L && partMin < minMs) {
				minMs = partMin;
			}
			final List mlist = ((McpAuditDatabase) dbs.get(i)).listMachines();
			for (int j = 0; j < mlist.size(); j++) {
				final Map m = (Map) mlist.get(j);
				machines.put(String.valueOf(m.get("machineId")), Boolean.TRUE);
			}
		}
		final Map row = new LinkedHashMap();
		row.put("count", Integer.valueOf((int) count));
		row.put("successCount", Integer.valueOf((int) ok));
		row.put("failCount", Integer.valueOf((int) fail));
		row.put("totalDurationMs", Long.valueOf(sumMs));
		row.put("avgDurationMs", Long.valueOf(count > 0L ? Math.round(sumMs * 1.0 / count) : 0L));
		row.put("maxDurationMs", Long.valueOf(maxMs));
		row.put("minDurationMs", Long.valueOf(minMs == Long.MAX_VALUE ? 0L : minMs));
		row.put("machineCount", Integer.valueOf(machines.size()));
		row.put("databaseCount", Integer.valueOf(dbs.size()));
		return row;
	}

	static List statsByAction(final McpAuditQuery query) throws Exception {
		final Map byKey = new LinkedHashMap();
		final List dbs = listDatabases();
		for (int i = 0; i < dbs.size(); i++) {
			final List part = ((McpAuditDatabase) dbs.get(i)).statsByAction(query);
			for (int j = 0; j < part.size(); j++) {
				final Map row = (Map) part.get(j);
				final String key = str(row.get("action")) + "\t" + str(row.get("intent"));
				Map agg = (Map) byKey.get(key);
				if (agg == null) {
					agg = new LinkedHashMap();
					agg.put("action", str(row.get("action")));
					agg.put("intent", str(row.get("intent")));
					agg.put("count", Integer.valueOf(0));
					agg.put("successCount", Integer.valueOf(0));
					agg.put("failCount", Integer.valueOf(0));
					agg.put("totalDurationMs", Long.valueOf(0L));
					agg.put("maxDurationMs", Long.valueOf(0L));
					byKey.put(key, agg);
				}
				final int c = intVal(row.get("count"));
				agg.put("count", Integer.valueOf(intVal(agg.get("count")) + c));
				agg.put("successCount", Integer.valueOf(intVal(agg.get("successCount")) + intVal(row.get("successCount"))));
				agg.put("failCount", Integer.valueOf(intVal(agg.get("failCount")) + intVal(row.get("failCount"))));
				long addTotal = longVal(row.get("totalDurationMs"));
				if (addTotal <= 0L) {
					addTotal = longVal(row.get("avgDurationMs")) * c;
				}
				agg.put("totalDurationMs", Long.valueOf(longVal(agg.get("totalDurationMs")) + addTotal));
				agg.put("maxDurationMs",
				    Long.valueOf(Math.max(longVal(agg.get("maxDurationMs")), longVal(row.get("maxDurationMs")))));
			}
		}
		final List result = new ArrayList(byKey.values());
		for (int i = 0; i < result.size(); i++) {
			final Map row = (Map) result.get(i);
			final int c = intVal(row.get("count"));
			final long total = longVal(row.get("totalDurationMs"));
			row.put("avgDurationMs", Long.valueOf(c > 0 ? Math.round(total * 1.0 / c) : 0L));
		}
		Collections.sort(result, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long av = longVal(((Map) a).get("avgDurationMs"));
				final long bv = longVal(((Map) b).get("avgDurationMs"));
				if (av == bv) {
					return intVal(((Map) b).get("count")) - intVal(((Map) a).get("count"));
				}
				return av > bv ? -1 : 1;
			}
		});
		final int limit = query != null && query.limit > 0 ? query.limit : 100;
		return trim(result, limit);
	}

	static List statsByMachine(final McpAuditQuery query) throws Exception {
		final Map byKey = new LinkedHashMap();
		final List dbs = listDatabases();
		for (int i = 0; i < dbs.size(); i++) {
			final List part = ((McpAuditDatabase) dbs.get(i)).statsByMachine(query);
			for (int j = 0; j < part.size(); j++) {
				final Map row = (Map) part.get(j);
				final String key = str(row.get("machineId"));
				Map agg = (Map) byKey.get(key);
				if (agg == null) {
					agg = new LinkedHashMap();
					agg.put("machineId", key);
					agg.put("machineName", str(row.get("machineName")));
					agg.put("count", Integer.valueOf(0));
					agg.put("successCount", Integer.valueOf(0));
					agg.put("failCount", Integer.valueOf(0));
					agg.put("totalDurationMs", Long.valueOf(0L));
					agg.put("maxDurationMs", Long.valueOf(0L));
					byKey.put(key, agg);
				}
				agg.put("count", Integer.valueOf(intVal(agg.get("count")) + intVal(row.get("count"))));
				agg.put("successCount", Integer.valueOf(intVal(agg.get("successCount")) + intVal(row.get("successCount"))));
				agg.put("failCount", Integer.valueOf(intVal(agg.get("failCount")) + intVal(row.get("failCount"))));
				agg.put("totalDurationMs",
				    Long.valueOf(longVal(agg.get("totalDurationMs")) + longVal(row.get("totalDurationMs"))));
				agg.put("maxDurationMs",
				    Long.valueOf(Math.max(longVal(agg.get("maxDurationMs")), longVal(row.get("maxDurationMs")))));
				if (str(agg.get("machineName")).length() == 0) {
					agg.put("machineName", str(row.get("machineName")));
				}
			}
		}
		final List result = new ArrayList(byKey.values());
		for (int i = 0; i < result.size(); i++) {
			final Map row = (Map) result.get(i);
			final int c = intVal(row.get("count"));
			row.put("avgDurationMs", Long.valueOf(c > 0 ? Math.round(longVal(row.get("totalDurationMs")) * 1.0 / c) : 0L));
		}
		Collections.sort(result, new Comparator() {
			public int compare(final Object a, final Object b) {
				return intVal(((Map) b).get("count")) - intVal(((Map) a).get("count"));
			}
		});
		return result;
	}

	static List statsByDay(final McpAuditQuery query) throws Exception {
		final Map byKey = new LinkedHashMap();
		final List dbs = listDatabases();
		for (int i = 0; i < dbs.size(); i++) {
			final List part = ((McpAuditDatabase) dbs.get(i)).statsByDay(query);
			for (int j = 0; j < part.size(); j++) {
				final Map row = (Map) part.get(j);
				final Long bucket = Long.valueOf(longVal(row.get("bucketTs")));
				Map agg = (Map) byKey.get(bucket);
				if (agg == null) {
					agg = new LinkedHashMap();
					agg.put("bucketTs", bucket);
					agg.put("count", Integer.valueOf(0));
					agg.put("successCount", Integer.valueOf(0));
					agg.put("failCount", Integer.valueOf(0));
					agg.put("totalDurationMs", Long.valueOf(0L));
					agg.put("maxDurationMs", Long.valueOf(0L));
					byKey.put(bucket, agg);
				}
				agg.put("count", Integer.valueOf(intVal(agg.get("count")) + intVal(row.get("count"))));
				agg.put("successCount", Integer.valueOf(intVal(agg.get("successCount")) + intVal(row.get("successCount"))));
				agg.put("failCount", Integer.valueOf(intVal(agg.get("failCount")) + intVal(row.get("failCount"))));
				agg.put("totalDurationMs",
				    Long.valueOf(longVal(agg.get("totalDurationMs")) + longVal(row.get("totalDurationMs"))));
				agg.put("maxDurationMs",
				    Long.valueOf(Math.max(longVal(agg.get("maxDurationMs")), longVal(row.get("maxDurationMs")))));
			}
		}
		final List result = new ArrayList(byKey.values());
		for (int i = 0; i < result.size(); i++) {
			final Map row = (Map) result.get(i);
			final int c = intVal(row.get("count"));
			row.put("avgDurationMs", Long.valueOf(c > 0 ? Math.round(longVal(row.get("totalDurationMs")) * 1.0 / c) : 0L));
		}
		Collections.sort(result, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long av = longVal(((Map) a).get("bucketTs"));
				final long bv = longVal(((Map) b).get("bucketTs"));
				return av > bv ? -1 : (av == bv ? 0 : 1);
			}
		});
		final int limit = query != null && query.limit > 0 ? Math.min(query.limit, 366) : 90;
		return trim(result, limit);
	}

	static List listMachines() throws Exception {
		final Map byKey = new LinkedHashMap();
		final List dbs = listDatabases();
		for (int i = 0; i < dbs.size(); i++) {
			final McpAuditDatabase db = (McpAuditDatabase) dbs.get(i);
			final List part = db.listMachines();
			for (int j = 0; j < part.size(); j++) {
				final Map row = (Map) part.get(j);
				final String key = str(row.get("machineId"));
				Map agg = (Map) byKey.get(key);
				if (agg == null) {
					agg = new LinkedHashMap();
					agg.put("machineId", key);
					agg.put("machineName", str(row.get("machineName")));
					agg.put("count", Integer.valueOf(0));
					agg.put("lastTs", Long.valueOf(0L));
					agg.put("dbPath", db.getDbFile().getAbsolutePath());
					byKey.put(key, agg);
				}
				agg.put("count", Integer.valueOf(intVal(agg.get("count")) + intVal(row.get("count"))));
				agg.put("lastTs", Long.valueOf(Math.max(longVal(agg.get("lastTs")), longVal(row.get("lastTs")))));
				if (str(agg.get("machineName")).length() == 0) {
					agg.put("machineName", str(row.get("machineName")));
				}
			}
		}
		return new ArrayList(byKey.values());
	}

	static List distinctValues(final String column) throws Exception {
		final Map seen = new LinkedHashMap();
		final List dbs = listDatabases();
		for (int i = 0; i < dbs.size(); i++) {
			final List part = ((McpAuditDatabase) dbs.get(i)).distinctValues(column);
			for (int j = 0; j < part.size(); j++) {
				seen.put(String.valueOf(part.get(j)), Boolean.TRUE);
			}
		}
		return new ArrayList(seen.keySet());
	}

	private static McpAuditQuery copyQuery(final McpAuditQuery src) {
		final McpAuditQuery q = new McpAuditQuery();
		q.text = src.text;
		q.searchPayload = src.searchPayload;
		q.machineId = src.machineId;
		q.actor = src.actor;
		q.action = src.action;
		q.intent = src.intent;
		q.traceId = src.traceId;
		q.result = src.result;
		q.sinceMillis = src.sinceMillis;
		q.untilMillis = src.untilMillis;
		q.minDurationMs = src.minDurationMs;
		q.limit = src.limit;
		return q;
	}

	private static void sortByLongDesc(final List rows, final String key) {
		Collections.sort(rows, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long av = longVal(((Map) a).get(key));
				final long bv = longVal(((Map) b).get(key));
				return av > bv ? -1 : (av == bv ? 0 : 1);
			}
		});
	}

	private static List trim(final List rows, final int limit) {
		if (rows.size() <= limit) {
			return rows;
		}
		return new ArrayList(rows.subList(0, limit));
	}

	private static String str(final Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static long longVal(final Object value) {
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		try {
			return Long.parseLong(str(value));
		}
		catch (Exception e) {
			return 0L;
		}
	}

	private static int intVal(final Object value) {
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		try {
			return Integer.parseInt(str(value));
		}
		catch (Exception e) {
			return 0;
		}
	}
}
