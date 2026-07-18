package org.docear.plugin.mcp.audit;

/**
 * One intent category per MCP service class, for aggregated statistics.
 */
public enum McpOperationIntent {
	CONTEXT("McpContextService"),
	MINDMAP("McpMindMapService"),
	NODE("McpNodeService"),
	TASK("McpTaskService"),
	WORKSPACE("McpWorkspaceService"),
	GRAPH("McpRelationshipGraphService"),
	TAG("McpTagService"),
	POMODORO("McpPomodoroService"),
	FINANCE("McpFinanceService"),
	RESOURCE("McpResource"),
	PROMPT("McpPrompt"),
	UNKNOWN("Unknown");

	private final String serviceClass;

	McpOperationIntent(final String serviceClass) {
		this.serviceClass = serviceClass;
	}

	public String getServiceClass() {
		return serviceClass;
	}
}
