package org.docear.plugin.mcp.server;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonParser;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.freeplane.core.util.LogUtils;

/**
 * OpenAI-compatible chat + tool loop that drives {@link McpProtocol} in-process.
 */
public final class McpWebAgent {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private final McpProtocol protocol;

	public McpWebAgent(final McpProtocol protocol) {
		this.protocol = protocol;
	}

	public Map<String, JsonValue> chat(final String userMessage, final List<JsonValue> history) throws Exception {
		if (!DocearMcpConfig.isWebLlmConfigured()) {
			throw new IllegalStateException(
					"Web LLM API key is not configured. Set it in Product Settings → MCP → Web.");
		}
		final String message = userMessage == null ? "" : userMessage.trim();
		if (message.length() == 0) {
			throw new IllegalArgumentException("message is required");
		}

		final List<JsonValue> messages = new ArrayList<JsonValue>();
		messages.add(message("system", systemPrompt()));
		if (history != null) {
			for (int i = 0; i < history.size(); i++) {
				final Map<String, JsonValue> item = history.get(i).asMap();
				if (!item.containsKey("role") || !item.containsKey("content")) {
					continue;
				}
				final String role = item.get("role").asString();
				if (!"user".equals(role) && !"assistant".equals(role)) {
					continue;
				}
				messages.add(message(role, item.get("content").asString()));
			}
		}
		messages.add(message("user", message));

		final List<JsonValue> openaiTools = toOpenAiTools(protocol.getToolDefinitions());
		final List<JsonValue> toolTrace = new ArrayList<JsonValue>();
		String finalReply = "";
		final int maxRounds = DocearMcpConfig.getWebLlmMaxToolRounds();

		for (int round = 0; round < maxRounds; round++) {
			final JsonValue response = callChatCompletions(messages, openaiTools);
			final Map<String, JsonValue> choice = firstChoice(response);
			final JsonValue messageValue = choice.get("message");
			if (messageValue == null || messageValue.isNull()) {
				throw new IllegalStateException("LLM returned empty message");
			}
			final Map<String, JsonValue> assistantMessage = messageValue.asMap();
			messages.add(JsonValue.ofMap(copyMessage(assistantMessage)));

			final List<JsonValue> toolCalls = assistantMessage.containsKey("tool_calls")
					? assistantMessage.get("tool_calls").asList()
					: new ArrayList<JsonValue>();
			final String content = assistantMessage.containsKey("content") && !assistantMessage.get("content").isNull()
					? nullToEmpty(assistantMessage.get("content").asString())
					: "";

			if (toolCalls.isEmpty()) {
				finalReply = content;
				break;
			}

			for (int t = 0; t < toolCalls.size(); t++) {
				final Map<String, JsonValue> call = toolCalls.get(t).asMap();
				final String callId = call.containsKey("id") ? call.get("id").asString() : ("call_" + t);
				final Map<String, JsonValue> fn = call.containsKey("function") ? call.get("function").asMap()
						: new LinkedHashMap<String, JsonValue>();
				final String toolName = fn.containsKey("name") ? fn.get("name").asString() : "";
				final String argsJson = fn.containsKey("arguments") ? nullToEmpty(fn.get("arguments").asString()) : "{}";
				Map<String, JsonValue> args = new LinkedHashMap<String, JsonValue>();
				try {
					final JsonValue parsed = JsonParser.parse(argsJson.length() == 0 ? "{}" : argsJson);
					args = parsed.asMap();
				}
				catch (Exception e) {
					args = new LinkedHashMap<String, JsonValue>();
					args.put("error", JsonValue.ofString("invalid tool arguments JSON: " + e.getMessage()));
				}

				String toolText;
				boolean ok = true;
				try {
					injectWebAudit(args);
					toolText = protocol.invokeToolText(toolName, args);
				}
				catch (Exception e) {
					ok = false;
					toolText = "Tool error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
					LogUtils.warn("Web agent tool failed: " + toolName + " - " + toolText);
				}

				final Map<String, JsonValue> trace = new LinkedHashMap<String, JsonValue>();
				trace.put("name", JsonValue.ofString(toolName));
				trace.put("ok", JsonValue.ofBoolean(ok));
				trace.put("arguments", JsonValue.ofString(argsJson));
				final String preview = toolText.length() > 4000 ? toolText.substring(0, 4000) + "..." : toolText;
				trace.put("resultPreview", JsonValue.ofString(preview));
				toolTrace.add(JsonValue.ofMap(trace));

				final Map<String, JsonValue> toolMessage = new LinkedHashMap<String, JsonValue>();
				toolMessage.put("role", JsonValue.ofString("tool"));
				toolMessage.put("tool_call_id", JsonValue.ofString(callId));
				toolMessage.put("content", JsonValue.ofString(toolText));
				messages.add(JsonValue.ofMap(toolMessage));
			}

			if (round == maxRounds - 1 && finalReply.length() == 0) {
				finalReply = content.length() > 0 ? content
						: "Reached max tool rounds. Partial tool results are in the trace.";
			}
		}

		if (finalReply == null || finalReply.length() == 0) {
			finalReply = "(empty model reply)";
		}

		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("reply", JsonValue.ofString(finalReply));
		result.put("model", JsonValue.ofString(DocearMcpConfig.getWebLlmModel()));
		result.put("toolTrace", JsonValue.ofList(toolTrace));
		return result;
	}

	private static void injectWebAudit(final Map<String, JsonValue> args) {
		if (args.containsKey("_audit")) {
			return;
		}
		final Map<String, JsonValue> audit = new LinkedHashMap<String, JsonValue>();
		audit.put("caller", JsonValue.ofString("twigmark-web"));
		audit.put("traceId", JsonValue.ofString("web-" + System.currentTimeMillis()));
		audit.put("questionSummary", JsonValue.ofString("web chat"));
		audit.put("operationGoal", JsonValue.ofString("web agent tool call"));
		args.put("_audit", JsonValue.ofMap(audit));
	}

	private JsonValue callChatCompletions(final List<JsonValue> messages, final List<JsonValue> tools)
			throws Exception {
		final Map<String, JsonValue> body = new LinkedHashMap<String, JsonValue>();
		body.put("model", JsonValue.ofString(DocearMcpConfig.getWebLlmModel()));
		body.put("messages", JsonValue.ofList(messages));
		if (tools != null && !tools.isEmpty()) {
			body.put("tools", JsonValue.ofList(tools));
			body.put("tool_choice", JsonValue.ofString("auto"));
		}
		final String payload = JsonWriter.write(JsonValue.ofMap(body));
		final URL url = new URL(DocearMcpConfig.getWebLlmBaseUrl() + "/chat/completions");
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) url.openConnection();
			connection.setConnectTimeout(30000);
			connection.setReadTimeout(120000);
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			connection.setRequestProperty("Authorization", "Bearer " + DocearMcpConfig.getWebLlmApiKey());
			final byte[] bytes = payload.getBytes(UTF8);
			connection.setFixedLengthStreamingMode(bytes.length);
			final OutputStream out = connection.getOutputStream();
			out.write(bytes);
			out.close();
			final int code = connection.getResponseCode();
			final InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
			final String responseBody = readStream(stream);
			if (code >= 400) {
				throw new IllegalStateException("LLM HTTP " + code + ": " + trimForError(responseBody));
			}
			return JsonParser.parse(responseBody);
		}
		finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private static Map<String, JsonValue> firstChoice(final JsonValue response) {
		final Map<String, JsonValue> root = response.asMap();
		if (!root.containsKey("choices")) {
			throw new IllegalStateException("LLM response missing choices");
		}
		final List<JsonValue> choices = root.get("choices").asList();
		if (choices.isEmpty()) {
			throw new IllegalStateException("LLM response has empty choices");
		}
		return choices.get(0).asMap();
	}

	private static List<JsonValue> toOpenAiTools(final List<JsonValue> mcpTools) {
		final List<JsonValue> tools = new ArrayList<JsonValue>();
		if (mcpTools == null) {
			return tools;
		}
		for (int i = 0; i < mcpTools.size(); i++) {
			final Map<String, JsonValue> tool = mcpTools.get(i).asMap();
			final Map<String, JsonValue> fn = new LinkedHashMap<String, JsonValue>();
			fn.put("name", tool.get("name"));
			fn.put("description", tool.containsKey("description") ? tool.get("description")
					: JsonValue.ofString(""));
			fn.put("parameters", tool.containsKey("inputSchema") ? tool.get("inputSchema")
					: JsonValue.ofMap(new LinkedHashMap<String, JsonValue>()));
			final Map<String, JsonValue> wrapper = new LinkedHashMap<String, JsonValue>();
			wrapper.put("type", JsonValue.ofString("function"));
			wrapper.put("function", JsonValue.ofMap(fn));
			tools.add(JsonValue.ofMap(wrapper));
		}
		return tools;
	}

	private static Map<String, JsonValue> copyMessage(final Map<String, JsonValue> source) {
		final Map<String, JsonValue> copy = new LinkedHashMap<String, JsonValue>();
		copy.put("role", source.get("role"));
		if (source.containsKey("content")) {
			copy.put("content", source.get("content"));
		}
		if (source.containsKey("tool_calls")) {
			copy.put("tool_calls", source.get("tool_calls"));
		}
		return copy;
	}

	private static JsonValue message(final String role, final String content) {
		final Map<String, JsonValue> map = new LinkedHashMap<String, JsonValue>();
		map.put("role", JsonValue.ofString(role));
		map.put("content", JsonValue.ofString(content == null ? "" : content));
		return JsonValue.ofMap(map);
	}

	private static String systemPrompt() {
		return "You are Twigmark Web Assistant, helping the user operate local mind maps via MCP tools. "
				+ "Before writing, call get_selection_context (or search_nodes) to know the active map and node. "
				+ "Prefer add_nodes for batch writes. Keep answers concise in the user's language. "
				+ "Never invent node IDs or file paths.";
	}

	private static String readStream(final InputStream stream) throws Exception {
		if (stream == null) {
			return "";
		}
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		final byte[] chunk = new byte[4096];
		int read;
		while ((read = stream.read(chunk)) >= 0) {
			if (read > 0) {
				buffer.write(chunk, 0, read);
			}
		}
		stream.close();
		return new String(buffer.toByteArray(), UTF8);
	}

	private static String trimForError(final String body) {
		if (body == null) {
			return "";
		}
		return body.length() > 800 ? body.substring(0, 800) + "..." : body;
	}

	private static String nullToEmpty(final String value) {
		return value == null ? "" : value;
	}
}
