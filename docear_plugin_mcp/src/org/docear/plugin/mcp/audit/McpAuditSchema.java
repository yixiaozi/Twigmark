package org.docear.plugin.mcp.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.json.JsonValue;

/**
 * MCP {@code _audit} argument: advertised on every tool schema and checked before dispatch.
 */
public final class McpAuditSchema {
	public static final String MISSING_QUESTION_SUMMARY = "Missing required argument _audit.questionSummary. "
			+ "Every tools/call MUST include arguments._audit = "
			+ "{ caller, traceId, questionSummary, operationGoal }. "
			+ "questionSummary must be the user's actual question (same value for this turn, <=240 chars). "
			+ "Retry this tool call with _audit filled; do not omit it.";

	public static final String SERVER_INSTRUCTIONS = "Every tools/call must include arguments._audit with: "
			+ "caller (client id, e.g. grok or cursor-agent), "
			+ "traceId (stable id shared by all calls for one user question), "
			+ "questionSummary (the user's actual question, <=240 chars, same for the turn), "
			+ "operationGoal (why this one call, <=160 chars). "
			+ "The server rejects calls that omit questionSummary. "
			+ "Relative mind-map paths resolve under the mind-map library, not the process working directory.";

	private McpAuditSchema() {
	}

	/** Property wrapper used by {@code McpProtocol.tool(...)}. Always required. */
	public static JsonValue toolProperty() {
		final Map<String, JsonValue> property = new LinkedHashMap<String, JsonValue>();
		property.put("name", JsonValue.ofString("_audit"));
		property.put("schema", inputObjectSchema());
		property.put("required", JsonValue.ofBoolean(true));
		return JsonValue.ofMap(property);
	}

	public static JsonValue inputObjectSchema() {
		final Map<String, JsonValue> schema = new LinkedHashMap<String, JsonValue>();
		schema.put("type", JsonValue.ofString("object"));
		schema.put("description", JsonValue.ofString(
				"Required access audit. questionSummary MUST be the user's actual question, "
						+ "not a generic phrase. Reuse the same traceId and questionSummary "
						+ "for every tool call in this question. operationGoal is why THIS call."));
		final Map<String, JsonValue> props = new LinkedHashMap<String, JsonValue>();
		props.put("caller", stringField("Client id, e.g. grok, cursor-agent, twigmark-web"));
		props.put("traceId", stringField("Shared id for one user question; keep stable within the turn"));
		props.put("questionSummary", stringField("The user's actual question, <=240 chars; same within the turn"));
		props.put("operationGoal", stringField("Why this one tool call, <=160 chars"));
		schema.put("properties", JsonValue.ofMap(props));
		final List<JsonValue> required = new ArrayList<JsonValue>();
		required.add(JsonValue.ofString("caller"));
		required.add(JsonValue.ofString("traceId"));
		required.add(JsonValue.ofString("questionSummary"));
		required.add(JsonValue.ofString("operationGoal"));
		schema.put("required", JsonValue.ofList(required));
		return JsonValue.ofMap(schema);
	}

	public static boolean hasQuestionSummary(final String questionSummary) {
		return questionSummary != null && questionSummary.trim().length() > 0;
	}

	private static JsonValue stringField(final String description) {
		final Map<String, JsonValue> field = new LinkedHashMap<String, JsonValue>();
		field.put("type", JsonValue.ofString("string"));
		field.put("description", JsonValue.ofString(description));
		return JsonValue.ofMap(field);
	}
}
