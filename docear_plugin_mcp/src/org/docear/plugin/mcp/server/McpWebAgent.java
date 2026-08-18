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
import org.docear.plugin.mcp.audit.McpRequestContext;
import org.docear.plugin.mcp.json.JsonParser;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.json.JsonWriter;
import org.docear.plugin.mcp.webchat.GuestCatalog;
import org.docear.plugin.mcp.webchat.WebchatSystemPrompt;
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
		return chat(userMessage, history, DocearMcpConfig.getWebLlmBaseUrl(), DocearMcpConfig.getWebLlmApiKey(),
				DocearMcpConfig.getWebLlmModel());
	}

	public Map<String, JsonValue> chat(final String userMessage, final List<JsonValue> history, final String baseUrl,
			final String apiKey, final String model) throws Exception {
		return chat(userMessage, history, baseUrl, apiKey, model, null);
	}

	public Map<String, JsonValue> chat(final String userMessage, final List<JsonValue> history, final String baseUrl,
			final String apiKey, final String model, final String focusMapFile) throws Exception {
		return chat(userMessage, history, baseUrl, apiKey, model, focusMapFile, true, null);
	}

	public Map<String, JsonValue> chatFaq(final String userMessage, final String baseUrl, final String apiKey,
			final String model) throws Exception {
		return chat(userMessage, null, baseUrl, apiKey, model, null, false, GuestCatalog.systemPrompt());
	}

	public Map<String, JsonValue> chat(final String userMessage, final List<JsonValue> history, final String baseUrl,
			final String apiKey, final String model, final String focusMapFile, final boolean enableTools,
			final String systemPromptOverride) throws Exception {
		final String key = apiKey == null ? "" : apiKey.trim();
		if (key.length() == 0) {
			throw new IllegalStateException(
					"LLM API key is not configured. Add a profile in the web UI or Product Settings → MCP → Web.");
		}
		String endpointBase = baseUrl == null || baseUrl.trim().length() == 0 ? "https://api.openai.com/v1"
				: baseUrl.trim();
		if (endpointBase.endsWith("/")) {
			endpointBase = endpointBase.substring(0, endpointBase.length() - 1);
		}
		final String modelName = model == null || model.trim().length() == 0 ? "gpt-4o-mini" : model.trim();
		final String message = userMessage == null ? "" : userMessage.trim();
		if (message.length() == 0) {
			throw new IllegalArgumentException("message is required");
		}
		final String auditTraceId = "web-" + System.currentTimeMillis();

		final List<JsonValue> messages = new ArrayList<JsonValue>();
		final String system = systemPromptOverride != null && systemPromptOverride.trim().length() > 0
				? systemPromptOverride.trim()
				: systemPromptForFocus(focusMapFile);
		messages.add(message("system", system));
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

		final List<JsonValue> openaiTools = enableTools ? toOpenAiTools(protocol.getWebToolDefinitions())
				: new ArrayList<JsonValue>();
		final List<JsonValue> toolTrace = new ArrayList<JsonValue>();
		final List<JsonValue> rounds = new ArrayList<JsonValue>();
		final StringBuilder allReasoning = new StringBuilder();
		String finalReply = "";
		final int maxRounds = DocearMcpConfig.getWebLlmMaxToolRounds();

		for (int round = 0; round < maxRounds; round++) {
			final JsonValue response = callChatCompletions(messages, openaiTools, endpointBase, key, modelName);
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
			final String rawContent = assistantMessage.containsKey("content")
					? LlmThoughtTrace.textFrom(assistantMessage.get("content"))
					: "";
			final StringBuilder taggedThink = new StringBuilder();
			final String content = LlmThoughtTrace.visibleContent(rawContent, taggedThink);
			String reasoning = LlmThoughtTrace.extractReasoning(choice, assistantMessage);
			if (taggedThink.length() > 0) {
				final StringBuilder merged = new StringBuilder();
				LlmThoughtTrace.appendParagraph(merged, reasoning);
				LlmThoughtTrace.appendParagraph(merged, taggedThink.toString());
				reasoning = merged.toString();
			}
			LlmThoughtTrace.appendParagraph(allReasoning, reasoning);

			if (toolCalls.isEmpty() || !enableTools) {
				finalReply = content;
				if (!enableTools && toolCalls.size() > 0 && finalReply.length() == 0) {
					finalReply = "游客对话不能查询导图。请点选预设问题，或登录后再提问。";
				}
				rounds.add(JsonValue.ofMap(LlmThoughtTrace.roundMap(reasoning, content, new ArrayList<JsonValue>())));
				break;
			}

			final List<JsonValue> roundTools = new ArrayList<JsonValue>();
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
					if (!McpPermissions.canCall(McpRequestContext.currentPrincipal(), toolName)) {
						throw new IllegalStateException(McpPermissions.denyMessage(
								McpRequestContext.currentPrincipal(), toolName));
					}
					injectWebAudit(args, message, auditTraceId, toolName);
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
				final JsonValue traceValue = JsonValue.ofMap(trace);
				toolTrace.add(traceValue);
				roundTools.add(traceValue);

				final Map<String, JsonValue> toolMessage = new LinkedHashMap<String, JsonValue>();
				toolMessage.put("role", JsonValue.ofString("tool"));
				toolMessage.put("tool_call_id", JsonValue.ofString(callId));
				toolMessage.put("content", JsonValue.ofString(toolText));
				messages.add(JsonValue.ofMap(toolMessage));
			}

			rounds.add(JsonValue.ofMap(LlmThoughtTrace.roundMap(reasoning, content, roundTools)));

			if (round == maxRounds - 1 && finalReply.length() == 0) {
				finalReply = content.length() > 0 ? content
						: "Reached max tool rounds. Partial tool results are in the trace.";
			}
		}

		if (finalReply == null || finalReply.length() == 0) {
			finalReply = "(empty model reply)";
		}

		final String reasoningText = allReasoning.toString().trim();
		final Map<String, JsonValue> thoughtTrace = LlmThoughtTrace.bundle(reasoningText, toolTrace, rounds);
		final Map<String, JsonValue> result = new LinkedHashMap<String, JsonValue>();
		result.put("reply", JsonValue.ofString(finalReply));
		result.put("model", JsonValue.ofString(modelName));
		result.put("toolTrace", JsonValue.ofList(toolTrace));
		result.put("reasoning", JsonValue.ofString(reasoningText));
		result.put("thoughtTrace", JsonValue.ofMap(thoughtTrace));
		return result;
	}

	private static void injectWebAudit(final Map<String, JsonValue> args, final String userMessage,
			final String traceId, final String toolName) {
		final Map<String, JsonValue> audit = args.containsKey("_audit")
				? new LinkedHashMap<String, JsonValue>(args.get("_audit").asMap())
				: new LinkedHashMap<String, JsonValue>();
		if (!hasAuditText(audit, "caller")) {
			audit.put("caller", JsonValue.ofString("twigmark-web"));
		}
		if (!hasAuditText(audit, "traceId")) {
			audit.put("traceId", JsonValue.ofString(traceId));
		}
		final String existingSummary = auditText(audit, "questionSummary");
		if (existingSummary.length() == 0 || "web chat".equals(existingSummary)) {
			audit.put("questionSummary", JsonValue.ofString(truncateAudit(userMessage, 240)));
		}
		if (!hasAuditText(audit, "operationGoal")) {
			final String goal = toolName != null && toolName.length() > 0 ? ("web: " + toolName) : "web agent tool call";
			audit.put("operationGoal", JsonValue.ofString(truncateAudit(goal, 160)));
		}
		args.put("_audit", JsonValue.ofMap(audit));
	}

	private static boolean hasAuditText(final Map<String, JsonValue> audit, final String key) {
		return auditText(audit, key).length() > 0;
	}

	private static String auditText(final Map<String, JsonValue> audit, final String key) {
		if (audit == null || !audit.containsKey(key) || audit.get(key) == null || audit.get(key).isNull()) {
			return "";
		}
		final String value = audit.get(key).asString();
		return value != null ? value.trim() : "";
	}

	private static String truncateAudit(final String text, final int max) {
		final String value = text != null ? text.trim() : "";
		if (value.length() <= max) {
			return value;
		}
		return value.substring(0, max);
	}

	private JsonValue callChatCompletions(final List<JsonValue> messages, final List<JsonValue> tools,
			final String baseUrl, final String apiKey, final String model) throws Exception {
		final Map<String, JsonValue> body = new LinkedHashMap<String, JsonValue>();
		body.put("model", JsonValue.ofString(model));
		body.put("messages", JsonValue.ofList(messages));
		if (tools != null && !tools.isEmpty()) {
			body.put("tools", JsonValue.ofList(tools));
			body.put("tool_choice", JsonValue.ofString("auto"));
		}
		if (isOpenRouterBase(baseUrl)) {
			body.put("include_reasoning", JsonValue.ofBoolean(true));
			if (modelLooksLikeThinking(model)) {
				final Map<String, JsonValue> reasoning = new LinkedHashMap<String, JsonValue>();
				reasoning.put("effort", JsonValue.ofString("medium"));
				body.put("reasoning", JsonValue.ofMap(reasoning));
			}
		}
		final String payload = JsonWriter.write(JsonValue.ofMap(body));
		final URL url = new URL(baseUrl + "/chat/completions");
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) url.openConnection();
			connection.setConnectTimeout(30000);
			connection.setReadTimeout(120000);
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			connection.setRequestProperty("Authorization", "Bearer " + apiKey);
			// OpenRouter (OpenAI-compatible) recommends these headers.
			if (isOpenRouterBase(baseUrl)) {
				connection.setRequestProperty("HTTP-Referer", "https://yixiaozi.github.io/Twigmark/");
				connection.setRequestProperty("X-Title", "Twigmark Web");
			}
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
		return WebchatSystemPrompt.build();
	}

	private static String systemPromptForFocus(final String focusMapFile) {
		final String base = WebchatSystemPrompt.build();
		if (focusMapFile == null || focusMapFile.trim().length() == 0) {
			return base;
		}
		final String path = focusMapFile.trim();
		final StringBuilder sb = new StringBuilder(base.length() + 2048);
		sb.append(base);
		sb.append("\n\n## 当前网页聚焦导图\n");
		sb.append("用户正在网页里查看：").append(path).append('\n');
		sb.append("默认把这张图当作当前上下文：优先 search_nodes(query, filePath=\"").append(path);
		sb.append("\") 与 get_mindmap_json(filePath=\"").append(path).append("\")。\n");
		sb.append("不要 open_mindmap。不要编造节点。");
		sb.append("若问题明显属于全库（待办、财务、最近修改、其它项目），可以出图检索，但先用一句话说明已离开当前图。\n");
		try {
			final String outline = org.docear.plugin.mcp.service.McpMindMapService.getMindmapOutlineForWeb(path, 3,
					10000);
			sb.append("\n### 导图大纲摘要（已截断）\n");
			sb.append(outline);
			sb.append('\n');
		}
		catch (Exception e) {
			LogUtils.warn("focus map outline failed: " + e.getMessage());
		}
		return sb.toString();
	}

	private static boolean isOpenRouterBase(final String baseUrl) {
		if (baseUrl == null) {
			return false;
		}
		final String u = baseUrl.toLowerCase();
		return u.indexOf("openrouter.ai") >= 0;
	}

	private static boolean modelLooksLikeThinking(final String model) {
		if (model == null) {
			return false;
		}
		final String m = model.toLowerCase();
		return m.indexOf("reasoner") >= 0 || m.indexOf("thinking") >= 0 || m.indexOf("qwen3") >= 0
				|| m.indexOf("deepseek-r1") >= 0 || m.indexOf("/r1") >= 0 || m.indexOf("o1-") >= 0
				|| m.indexOf("o3-") >= 0 || m.indexOf("o4-mini") >= 0 || m.indexOf("gpt-5") >= 0;
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
