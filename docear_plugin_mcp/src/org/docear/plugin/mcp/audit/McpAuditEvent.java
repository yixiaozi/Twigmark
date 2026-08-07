package org.docear.plugin.mcp.audit;

import java.util.UUID;

public final class McpAuditEvent {

	public final String eventId;
	public final String machineId;
	public final String machineName;
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

	public McpAuditEvent(final String eventId, final String machineId, final String machineName, final long ts,
	    final String tenant, final String actor, final String action, final String kind, final McpOperationIntent intent,
	    final String traceId, final String sessionId, final String clientName, final String osUser,
	    final String remoteAddress, final String questionSummary, final String operationGoal, final String requestJson,
	    final String responseJson, final int responseBytes, final boolean responseTruncated, final boolean success,
	    final long durationMs, final String errorMessage) {
		this.eventId = eventId != null && eventId.length() > 0 ? eventId : UUID.randomUUID().toString();
		this.machineId = machineId != null && machineId.length() > 0 ? machineId : McpAuditMachineId.getMachineId();
		this.machineName = machineName != null && machineName.length() > 0 ? machineName
		    : McpAuditMachineId.getMachineName();
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

	/** Local record helper: auto-assigns this machine's id and a fresh event UUID. */
	public static McpAuditEvent local(final long ts, final String tenant, final String actor, final String action,
	    final String kind, final McpOperationIntent intent, final String traceId, final String sessionId,
	    final String clientName, final String osUser, final String remoteAddress, final String questionSummary,
	    final String operationGoal, final String requestJson, final String responseJson, final int responseBytes,
	    final boolean responseTruncated, final boolean success, final long durationMs, final String errorMessage) {
		return new McpAuditEvent(UUID.randomUUID().toString(), McpAuditMachineId.getMachineId(),
		    McpAuditMachineId.getMachineName(), ts, tenant, actor, action, kind, intent, traceId, sessionId, clientName,
		    osUser, remoteAddress, questionSummary, operationGoal, requestJson, responseJson, responseBytes,
		    responseTruncated, success, durationMs, errorMessage);
	}
}
