package org.docear.plugin.mcp.webchat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.audit.McpAuditLabels;
import org.docear.plugin.mcp.audit.McpAuditQuery;

/**
 * Headless checks for web MCP audit filters (read vs write, query parsing).
 */
public final class WebAuditStandaloneTest {
	private WebAuditStandaloneTest() {
	}

	public static void main(final String[] args) {
		assertMutating();
		assertActionLists();
		assertParseQuery();
		assertWriteFilters();
		assertLabels();
		assertUngroupedMerge();
		assertWriteCount();
		System.out.println("WebAuditStandaloneTest OK");
	}

	private static void assertMutating() {
		if (!WebAuditFilters.isMutatingAction("add_node") || !WebAuditFilters.isMutatingAction("copy_nodes")
				|| !WebAuditFilters.isMutatingAction("undo_map") || !WebAuditFilters.isMutatingAction("set_node_style")) {
			throw new IllegalStateException("write tools must be mutating");
		}
		if (WebAuditFilters.isMutatingAction("search_nodes") || WebAuditFilters.isMutatingAction("get_selection_context")
				|| WebAuditFilters.isMutatingAction("list_audit_log") || WebAuditFilters.isMutatingAction("")) {
			throw new IllegalStateException("read tools must not be mutating");
		}
	}

	private static void assertActionLists() {
		if (!WebAuditFilters.actionsContainWrite("search_nodes,add_node")) {
			throw new IllegalStateException("comma list with write");
		}
		if (!WebAuditFilters.actionsContainWrite("search_nodes add_nodes")) {
			throw new IllegalStateException("space list with write");
		}
		if (WebAuditFilters.actionsContainWrite("search_nodes,get_mindmap_json")) {
			throw new IllegalStateException("read-only action list");
		}
		if (WebAuditFilters.actionsContainWrite("") || WebAuditFilters.actionsContainWrite(null)) {
			throw new IllegalStateException("empty actions");
		}
	}

	private static void assertParseQuery() {
		final Map params = new LinkedHashMap();
		params.put("q", " 打开导图 ");
		params.put("actor", "cursor-agent");
		params.put("sinceHours", "24");
		params.put("limit", "80");
		params.put("writes", "1");
		params.put("payload", "true");
		final McpAuditQuery q = WebAuditFilters.parseQuery(params);
		if (!"打开导图".equals(q.text) || !"cursor-agent".equals(q.actor) || q.limit != 80 || !q.searchPayload) {
			throw new IllegalStateException("parse fields: " + q.text + "/" + q.actor + "/" + q.limit);
		}
		if (!WebAuditFilters.writesOnly(params)) {
			throw new IllegalStateException("writes=1");
		}
		final long expectedMin = System.currentTimeMillis() - 24L * 3600000L - 5000L;
		final long expectedMax = System.currentTimeMillis() - 24L * 3600000L + 5000L;
		if (q.sinceMillis < expectedMin || q.sinceMillis > expectedMax) {
			throw new IllegalStateException("sinceHours 24: " + q.sinceMillis);
		}

		final Map allTime = new LinkedHashMap();
		allTime.put("sinceHours", "0");
		final McpAuditQuery all = WebAuditFilters.parseQuery(allTime);
		if (all.sinceMillis != 0L) {
			throw new IllegalStateException("sinceHours 0 should mean all, got " + all.sinceMillis);
		}
		if (all.limit != 200) {
			throw new IllegalStateException("default limit: " + all.limit);
		}

		final Map huge = new LinkedHashMap();
		huge.put("limit", "9999");
		if (WebAuditFilters.parseQuery(huge).limit != WebAuditFilters.MAX_LIMIT) {
			throw new IllegalStateException("limit cap");
		}

		final Map ungrouped = new LinkedHashMap();
		ungrouped.put("traceId", "ungrouped:yixiaozi");
		ungrouped.put("sinceHours", "0");
		final McpAuditQuery uq = WebAuditFilters.parseQuery(ungrouped);
		if (!uq.emptyTraceOnly || !"yixiaozi".equals(uq.actor) || uq.traceId.length() != 0) {
			throw new IllegalStateException("ungrouped parse: actor=" + uq.actor + " empty=" + uq.emptyTraceOnly
					+ " trace=" + uq.traceId);
		}
	}

	private static void assertWriteFilters() {
		final List events = new ArrayList();
		events.add(row("action", "search_nodes"));
		events.add(row("action", "add_node"));
		events.add(row("action", "set_node_cloud"));
		final List writes = WebAuditFilters.filterWrites(events, 10);
		if (writes.size() != 2) {
			throw new IllegalStateException("filterWrites size " + writes.size());
		}
		WebAuditFilters.annotate(events);
		if (Boolean.TRUE.equals(((Map) events.get(0)).get("mutating"))) {
			throw new IllegalStateException("search should be read");
		}
		if (!"write".equals(((Map) events.get(1)).get("changeKind"))) {
			throw new IllegalStateException("add_node changeKind");
		}

		final List traces = new ArrayList();
		traces.add(row("actions", "search_nodes,get_selection_context"));
		traces.add(row("actions", "search_nodes,add_node"));
		final List writeTraces = WebAuditFilters.filterWriteTraces(traces, 10);
		if (writeTraces.size() != 1) {
			throw new IllegalStateException("filterWriteTraces size " + writeTraces.size());
		}
		WebAuditFilters.annotateTraces(traces);
		if (Boolean.TRUE.equals(((Map) traces.get(0)).get("mutating"))) {
			throw new IllegalStateException("read trace mutating");
		}
		if (!Boolean.TRUE.equals(((Map) traces.get(1)).get("mutating"))) {
			throw new IllegalStateException("write trace mutating");
		}
		if (WebAuditFilters.expandLimitForWriteFilter(80) < 200) {
			throw new IllegalStateException("expand write scan limit");
		}
	}

	private static void assertLabels() {
		if (!"Grok".equals(McpAuditLabels.inferClientName("grok-connectors-manager/0.1.0"))) {
			throw new IllegalStateException("infer grok ua");
		}
		if (!"Grok".equals(McpAuditLabels.inferClientName("Grok"))) {
			throw new IllegalStateException("infer grok name");
		}
		if (!"Cursor".equals(McpAuditLabels.inferClientName("Cursor/1.0"))) {
			throw new IllegalStateException("infer cursor");
		}
		final String trace = McpAuditLabels.syntheticTraceId("oauth:yixiaozi", "yixiaozi", 1L);
		if (trace.indexOf("oauth:yixiaozi") < 0 || !trace.startsWith("auto:")) {
			throw new IllegalStateException("synthetic trace: " + trace);
		}
		if (!McpAuditLabels.isUngroupedTraceId("ungrouped:yixiaozi")
				|| !"yixiaozi".equals(McpAuditLabels.actorFromUngroupedTraceId("ungrouped:yixiaozi"))) {
			throw new IllegalStateException("ungrouped id");
		}
		if (McpAuditLabels.fallbackQuestionSummary("Grok", "yixiaozi").indexOf("Grok") < 0) {
			throw new IllegalStateException("fallback summary");
		}
	}

	private static void assertUngroupedMerge() {
		final List traces = new ArrayList();
		final Map existing = new LinkedHashMap();
		existing.put("traceId", "web-1");
		existing.put("actor", "twigmark-web");
		existing.put("questionSummary", "web chat");
		traces.add(existing);
		final List events = new ArrayList();
		final Map grok = new LinkedHashMap();
		grok.put("traceId", "");
		grok.put("actor", "yixiaozi");
		grok.put("clientName", "");
		grok.put("action", "search_nodes");
		grok.put("ts", Long.valueOf(1000L));
		events.add(grok);
		final Map grokWrite = new LinkedHashMap();
		grokWrite.put("traceId", "");
		grokWrite.put("actor", "yixiaozi");
		grokWrite.put("action", "add_node");
		grokWrite.put("ts", Long.valueOf(2000L));
		events.add(grokWrite);
		final List merged = WebAuditFilters.mergeUngroupedTraces(traces, events);
		if (merged.size() != 2) {
			throw new IllegalStateException("merged size " + merged.size());
		}
		final Map first = (Map) merged.get(0);
		if (!"ungrouped:yixiaozi".equals(first.get("traceId")) || !Integer.valueOf(2).equals(first.get("callCount"))) {
			throw new IllegalStateException("ungrouped row: " + first);
		}
		if (String.valueOf(first.get("actions")).indexOf("add_node") < 0) {
			throw new IllegalStateException("ungrouped actions: " + first.get("actions"));
		}
	}

	private static void assertWriteCount() {
		final List byAction = new ArrayList();
		final Map read = new LinkedHashMap();
		read.put("action", "search_nodes");
		read.put("count", Integer.valueOf(10));
		byAction.add(read);
		final Map write = new LinkedHashMap();
		write.put("action", "add_node");
		write.put("count", Integer.valueOf(2));
		byAction.add(write);
		if (WebAuditFilters.writeCount(byAction) != 2) {
			throw new IllegalStateException("writeCount");
		}
	}

	private static Map row(final String key, final String value) {
		final Map map = new LinkedHashMap();
		map.put(key, value);
		return map;
	}
}
