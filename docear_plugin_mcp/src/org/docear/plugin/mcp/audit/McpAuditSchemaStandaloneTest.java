package org.docear.plugin.mcp.audit;

import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.json.JsonValue;

/**
 * Headless checks: every tool advertises required {@code _audit.questionSummary}.
 */
public final class McpAuditSchemaStandaloneTest {
	private McpAuditSchemaStandaloneTest() {
	}

	public static void main(final String[] args) {
		assertSchema();
		assertSummaryGate();
		System.out.println("McpAuditSchemaStandaloneTest OK");
	}

	private static void assertSchema() {
		final JsonValue property = McpAuditSchema.toolProperty();
		final Map<String, JsonValue> map = property.asMap();
		if (!"_audit".equals(map.get("name").asString()) || !map.get("required").asBoolean()) {
			throw new IllegalStateException("tool property: " + map);
		}
		final Map<String, JsonValue> schema = map.get("schema").asMap();
		final Map<String, JsonValue> props = schema.get("properties").asMap();
		if (!props.containsKey("questionSummary") || !props.containsKey("traceId")
				|| !props.containsKey("operationGoal") || !props.containsKey("caller")) {
			throw new IllegalStateException("audit fields: " + props.keySet());
		}
		final List<JsonValue> required = schema.get("required").asList();
		boolean hasSummary = false;
		for (int i = 0; i < required.size(); i++) {
			if ("questionSummary".equals(required.get(i).asString())) {
				hasSummary = true;
			}
		}
		if (!hasSummary) {
			throw new IllegalStateException("questionSummary not required: " + required);
		}
		if (McpAuditSchema.SERVER_INSTRUCTIONS.indexOf("questionSummary") < 0) {
			throw new IllegalStateException("initialize instructions");
		}
	}

	private static void assertSummaryGate() {
		if (McpAuditSchema.hasQuestionSummary(null) || McpAuditSchema.hasQuestionSummary("")
				|| McpAuditSchema.hasQuestionSummary("   ")) {
			throw new IllegalStateException("blank summary must fail");
		}
		if (!McpAuditSchema.hasQuestionSummary("把相册日记记到导图")) {
			throw new IllegalStateException("real summary must pass");
		}
		if (McpAuditSchema.MISSING_QUESTION_SUMMARY.indexOf("_audit.questionSummary") < 0) {
			throw new IllegalStateException("reject message");
		}
	}
}
