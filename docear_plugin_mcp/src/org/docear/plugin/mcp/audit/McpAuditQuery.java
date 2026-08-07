package org.docear.plugin.mcp.audit;

/**
 * Filter parameters for audit list / stats UI and MCP tools.
 */
public final class McpAuditQuery {

	public String text = "";
	public boolean searchPayload = false;
	public String machineId = "";
	public String actor = "";
	public String action = "";
	public String intent = "";
	public String traceId = "";
	/** empty / ok / fail */
	public String result = "";
	public long sinceMillis = 0L;
	public long untilMillis = 0L;
	public long minDurationMs = 0L;
	public int limit = 200;

	public static McpAuditQuery ofLimit(final int limit) {
		final McpAuditQuery q = new McpAuditQuery();
		q.limit = limit > 0 ? limit : 200;
		return q;
	}
}
