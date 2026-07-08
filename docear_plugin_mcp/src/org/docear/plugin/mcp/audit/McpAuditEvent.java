package org.docear.plugin.mcp.audit;

public final class McpAuditEvent {

	public final long ts;
	public final String tenant;
	public final String actor;
	public final String action;
	public final String kind;
	public final McpOperationIntent intent;
	public final String traceId;
	public final String sessionId;
	public final String clientName;
	public final String osUser;
	public final String remoteAddress;
	public final String questionSummary;
	public final String operationGoal;
	public final String requestJson;
	public final String responseJson;
	public final int responseBytes;
	public final boolean responseTruncated;
	public final boolean success;
	public final long durationMs;
	public final String errorMessage;

	public McpAuditEvent(final long ts, final String tenant, final String actor, final String action, final String kind,
	    final McpOperationIntent intent, final String traceId, final String sessionId, final String clientName,
	    final String osUser, final String remoteAddress, final String questionSummary, final String operationGoal,
	    final String requestJson, final String responseJson, final int responseBytes, final boolean responseTruncated,
	    final boolean success, final long durationMs, final String errorMessage) {
		this.ts = ts;
		this.tenant = tenant != null ? tenant : "default";
		this.actor = actor != null ? actor : "";
		this.action = action != null ? action : "";
		this.kind = kind != null ? kind : "";
		this.intent = intent != null ? intent : McpOperationIntent.UNKNOWN;
		this.traceId = traceId != null ? traceId : "";
		this.sessionId = sessionId != null ? sessionId : "";
		this.clientName = clientName != null ? clientName : "";
		this.osUser = osUser != null ? osUser : "";
		this.remoteAddress = remoteAddress != null ? remoteAddress : "";
		this.questionSummary = questionSummary != null ? questionSummary : "";
		this.operationGoal = operationGoal != null ? operationGoal : "";
		this.requestJson = requestJson != null ? requestJson : "{}";
		this.responseJson = responseJson != null ? responseJson : "";
		this.responseBytes = responseBytes;
		this.responseTruncated = responseTruncated;
		this.success = success;
		this.durationMs = durationMs;
		this.errorMessage = errorMessage != null ? errorMessage : "";
	}
}
