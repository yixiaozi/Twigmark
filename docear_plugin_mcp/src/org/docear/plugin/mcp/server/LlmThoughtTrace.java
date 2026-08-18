package org.docear.plugin.mcp.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.docear.plugin.mcp.json.JsonValue;

/**
 * Pulls model "thinking" / reasoning out of OpenAI-compatible chat responses
 * (DeepSeek reasoner, OpenRouter reasoning, Qwen thinking, {@code <think>} tags).
 */
final class LlmThoughtTrace {
	private static final Pattern THINK_TAG = Pattern
			.compile("(?is)<think>(.*?)</think>|<thinking>(.*?)</thinking>");

	private LlmThoughtTrace() {
	}

	static String extractReasoning(final Map<String, JsonValue> choice, final Map<String, JsonValue> message) {
		final StringBuilder out = new StringBuilder();
		appendReasoningFrom(out, message);
		appendReasoningFrom(out, choice);
		return out.toString().trim();
	}

	static String visibleContent(final String content, final StringBuilder thinkSink) {
		if (content == null || content.length() == 0) {
			return "";
		}
		final Matcher matcher = THINK_TAG.matcher(content);
		if (!matcher.find()) {
			return content;
		}
		matcher.reset();
		final StringBuffer visible = new StringBuffer();
		while (matcher.find()) {
			final String inner = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
			if (inner != null) {
				final String trimmed = inner.trim();
				if (trimmed.length() > 0) {
					if (thinkSink.length() > 0) {
						thinkSink.append("\n\n");
					}
					thinkSink.append(trimmed);
				}
			}
			matcher.appendReplacement(visible, "");
		}
		matcher.appendTail(visible);
		return visible.toString().trim();
	}

	static String textFrom(final JsonValue value) {
		if (value == null || value.isNull()) {
			return "";
		}
		final Object raw = value.raw();
		if (raw instanceof List) {
			final StringBuilder sb = new StringBuilder();
			final List<JsonValue> parts = value.asList();
			for (int i = 0; i < parts.size(); i++) {
				final String piece = textFrom(parts.get(i));
				if (piece.length() == 0) {
					continue;
				}
				if (sb.length() > 0) {
					sb.append('\n');
				}
				sb.append(piece);
			}
			return sb.toString();
		}
		if (raw instanceof Map) {
			final Map<String, JsonValue> map = value.asMap();
			if (map.containsKey("text")) {
				return textFrom(map.get("text"));
			}
			if (map.containsKey("content")) {
				return textFrom(map.get("content"));
			}
			if (map.containsKey("summary")) {
				return textFrom(map.get("summary"));
			}
			return "";
		}
		final String text = value.asString();
		return text == null ? "" : text;
	}

	static void appendParagraph(final StringBuilder sink, final String text) {
		if (sink == null) {
			return;
		}
		final String trimmed = text == null ? "" : text.trim();
		if (trimmed.length() == 0) {
			return;
		}
		if (sink.length() > 0) {
			sink.append("\n\n");
		}
		sink.append(trimmed);
	}

	static Map<String, JsonValue> roundMap(final String reasoning, final String content,
			final List<JsonValue> tools) {
		final Map<String, JsonValue> round = new LinkedHashMap<String, JsonValue>();
		if (reasoning != null && reasoning.length() > 0) {
			round.put("reasoning", JsonValue.ofString(reasoning));
		}
		if (content != null && content.length() > 0) {
			round.put("content", JsonValue.ofString(content));
		}
		round.put("tools", JsonValue.ofList(tools == null ? new ArrayList<JsonValue>() : tools));
		return round;
	}

	static Map<String, JsonValue> bundle(final String reasoning, final List<JsonValue> tools,
			final List<JsonValue> rounds) {
		final Map<String, JsonValue> bundle = new LinkedHashMap<String, JsonValue>();
		bundle.put("tools", JsonValue.ofList(tools == null ? new ArrayList<JsonValue>() : tools));
		if (reasoning != null && reasoning.length() > 0) {
			bundle.put("reasoning", JsonValue.ofString(reasoning));
		}
		bundle.put("rounds", JsonValue.ofList(rounds == null ? new ArrayList<JsonValue>() : rounds));
		return bundle;
	}

	private static void appendReasoningFrom(final StringBuilder out, final Map<String, JsonValue> map) {
		if (map == null) {
			return;
		}
		appendParagraph(out, textFrom(map.get("reasoning_content")));
		appendParagraph(out, textFrom(map.get("reasoning")));
		if (map.containsKey("reasoning_details")) {
			appendParagraph(out, textFrom(map.get("reasoning_details")));
		}
	}
}
