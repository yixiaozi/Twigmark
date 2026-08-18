package org.docear.plugin.mcp.webchat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.audit.McpAuditQuery;
import org.docear.plugin.mcp.server.McpPermissions;

/** Query / write-filter helpers for the web MCP audit explorer. */
public final class WebAuditFilters {
	static final int MAX_LIMIT = 500;

	private WebAuditFilters() {
	}

	static McpAuditQuery parseQuery(final Map params) {
		final McpAuditQuery q = new McpAuditQuery();
		q.text = str(params, "q");
		q.searchPayload = truthy(str(params, "payload"));
		q.actor = str(params, "actor");
		q.action = str(params, "action");
		q.intent = str(params, "intent");
		q.traceId = str(params, "traceId");
		q.result = str(params, "result");
		q.limit = Math.min(MAX_LIMIT, Math.max(1, intParam(params, "limit", 200)));
		final int sinceHours = intParam(params, "sinceHours", 168);
		if (sinceHours > 0) {
			q.sinceMillis = System.currentTimeMillis() - sinceHours * 3600000L;
		}
		return q;
	}

	static boolean isMutatingAction(final String action) {
		return McpPermissions.isWriteTool(action);
	}

	static boolean writesOnly(final Map params) {
		return truthy(str(params, "writes"));
	}

	static int expandLimitForWriteFilter(final int requested) {
		final int base = requested > 0 ? requested : 200;
		return Math.min(MAX_LIMIT, Math.max(base * 3, 200));
	}

	static List filterWrites(final List rows, final int limit) {
		final List out = new ArrayList();
		if (rows == null) {
			return out;
		}
		final int cap = limit > 0 ? Math.min(limit, MAX_LIMIT) : 200;
		for (int i = 0; i < rows.size() && out.size() < cap; i++) {
			final Map row = (Map) rows.get(i);
			if (isMutatingAction(str(row, "action"))) {
				out.add(row);
			}
		}
		return out;
	}

	static List filterWriteTraces(final List rows, final int limit) {
		final List out = new ArrayList();
		if (rows == null) {
			return out;
		}
		final int cap = limit > 0 ? Math.min(limit, MAX_LIMIT) : 80;
		for (int i = 0; i < rows.size() && out.size() < cap; i++) {
			final Map row = (Map) rows.get(i);
			if (actionsContainWrite(str(row, "actions"))) {
				out.add(row);
			}
		}
		return out;
	}

	static boolean actionsContainWrite(final String actions) {
		if (actions == null || actions.length() == 0) {
			return false;
		}
		final String[] parts = actions.split("[,;\\s]+");
		for (int i = 0; i < parts.length; i++) {
			if (isMutatingAction(parts[i])) {
				return true;
			}
		}
		return false;
	}

	static void annotate(final List rows) {
		if (rows == null) {
			return;
		}
		for (int i = 0; i < rows.size(); i++) {
			final Map row = (Map) rows.get(i);
			final boolean mutating = isMutatingAction(str(row, "action"));
			row.put("mutating", Boolean.valueOf(mutating));
			row.put("changeKind", mutating ? "write" : "read");
		}
	}

	static void annotateTraces(final List rows) {
		if (rows == null) {
			return;
		}
		for (int i = 0; i < rows.size(); i++) {
			final Map row = (Map) rows.get(i);
			row.put("mutating", Boolean.valueOf(actionsContainWrite(str(row, "actions"))));
		}
	}

	static String str(final Map map, final String key) {
		if (map == null || !map.containsKey(key) || map.get(key) == null) {
			return "";
		}
		return String.valueOf(map.get(key)).trim();
	}

	static int intParam(final Map map, final String key, final int fallback) {
		final String raw = str(map, key);
		if (raw.length() == 0) {
			return fallback;
		}
		try {
			return Integer.parseInt(raw);
		}
		catch (Exception e) {
			return fallback;
		}
	}

	static boolean truthy(final String value) {
		return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
	}
}
