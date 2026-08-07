package org.docear.plugin.ai.backend;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.docear.plugin.ai.DocearAiConfig;
import org.docear.plugin.core.settings.McpRuntimeFacade;
import org.freeplane.core.util.LogUtils;

/**
 * OpenAI-compatible chat backend (OpenRouter / OpenAI / custom).
 * Reads shared LLM profiles from the MCP webchat database via {@link McpRuntimeFacade}.
 */
public class OpenAiCompatibleBackend implements AiBackend {

	private static final Charset UTF8 = Charset.forName("UTF-8");
	private volatile boolean cancelRequested;
	private volatile HttpURLConnection activeConnection;

	public List<String> generateSubNodes(String prompt, int count) {
		String output = chat(prompt);
		return parseLines(output, count);
	}

	public String chat(String message) {
		final StringBuilder full = new StringBuilder();
		chatStreaming(message, new AiChatStreamListener() {
			public void onChunk(String chunk) {
				if (chunk != null) {
					full.append(chunk);
				}
			}

			public void onComplete(String fullText) {
			}

			public void onError(String message) {
			}

			public boolean isCancelled() {
				return false;
			}
		});
		return full.toString().trim();
	}

	public void chatStreaming(String message, AiChatStreamListener listener) {
		cancelRequested = false;
		if (message == null || message.trim().length() == 0) {
			if (listener != null) {
				listener.onComplete("");
			}
			return;
		}
		final Map endpoint = resolveEndpoint();
		if (endpoint == null) {
			if (listener != null) {
				listener.onError("\u672a\u914d\u7f6e\u5927\u6a21\u578b\u3002\u8bf7\u5728\u4ea7\u54c1\u8bbe\u7f6e \u2192 MCP \u6216\u7f51\u9875\u804a\u5929\u4e2d\u914d\u7f6e OpenRouter\u3002");
				listener.onComplete("");
			}
			return;
		}
		final String baseUrl = trimSlash(String.valueOf(endpoint.get("baseUrl")));
		final String apiKey = String.valueOf(endpoint.get("apiKey"));
		final String model = String.valueOf(endpoint.get("model"));
		if (apiKey == null || apiKey.trim().length() == 0 || "null".equals(apiKey)) {
			if (listener != null) {
				listener.onError("LLM API Key \u4e3a\u7a7a\u3002");
				listener.onComplete("");
			}
			return;
		}
		try {
			final String reply = callChatCompletions(baseUrl, apiKey, model, message.trim(), listener);
			if (listener != null) {
				if (cancelRequested || (listener.isCancelled())) {
					listener.onComplete(reply == null ? "" : reply);
				}
				else {
					listener.onComplete(reply == null ? "" : reply);
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn("OpenAI-compatible chat failed: " + e.getMessage());
			if (listener != null) {
				listener.onError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
				listener.onComplete("");
			}
		}
		finally {
			activeConnection = null;
		}
	}

	public void cancelCurrentRequest() {
		cancelRequested = true;
		final HttpURLConnection c = activeConnection;
		if (c != null) {
			try {
				c.disconnect();
			}
			catch (Exception ignored) {
			}
		}
	}

	public boolean isAvailable() {
		return McpRuntimeFacade.safeIsLlmConfigured() || resolveEndpoint() != null;
	}

	public String getSelectedModelLabel() {
		final Map endpoint = resolveEndpoint();
		if (endpoint == null) {
			return "";
		}
		final Object model = endpoint.get("model");
		return model == null ? "" : String.valueOf(model);
	}

	private Map resolveEndpoint() {
		final DocearAiConfig config = new DocearAiConfig();
		final String profileId = config.getLlmProfileId();
		Map endpoint = McpRuntimeFacade.safeResolveEndpoint(profileId);
		if (endpoint == null && profileId != null && profileId.length() > 0) {
			endpoint = McpRuntimeFacade.safeResolveEndpoint("");
		}
		return endpoint;
	}

	private String callChatCompletions(final String baseUrl, final String apiKey, final String model,
			final String userMessage, final AiChatStreamListener listener) throws Exception {
		final StringBuilder json = new StringBuilder();
		json.append('{');
		json.append("\"model\":").append(jsonString(model)).append(',');
		json.append("\"messages\":[");
		json.append("{\"role\":\"system\",\"content\":").append(jsonString(systemPrompt())).append("},");
		json.append("{\"role\":\"user\",\"content\":").append(jsonString(userMessage)).append('}');
		json.append("]");
		json.append('}');

		final URL url = new URL(baseUrl + "/chat/completions");
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) url.openConnection();
			activeConnection = connection;
			connection.setConnectTimeout(30000);
			connection.setReadTimeout(180000);
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			connection.setRequestProperty("Authorization", "Bearer " + apiKey);
			if (isOpenRouter(baseUrl)) {
				connection.setRequestProperty("HTTP-Referer", "https://yixiaozi.github.io/Twigmark/");
				connection.setRequestProperty("X-Title", "Twigmark Desktop");
			}
			final byte[] bytes = json.toString().getBytes(UTF8);
			connection.setFixedLengthStreamingMode(bytes.length);
			final OutputStream out = connection.getOutputStream();
			out.write(bytes);
			out.close();

			if (cancelRequested || (listener != null && listener.isCancelled())) {
				return "";
			}
			final int code = connection.getResponseCode();
			final InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
			final String body = readStream(stream);
			if (code >= 400) {
				throw new IllegalStateException("LLM HTTP " + code + ": " + trim(body, 800));
			}
			final String content = extractAssistantContent(body);
			if (listener != null && content.length() > 0) {
				listener.onChunk(content);
			}
			return content;
		}
		finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private static String systemPrompt() {
		return "You are Twigmark desktop AI assistant. Answer in the user's language. "
				+ "The user message already includes mind-map context when available. "
				+ "Be concise and actionable. Do not invent node IDs.";
	}

	private static String extractAssistantContent(final String body) {
		if (body == null || body.length() == 0) {
			return "";
		}
		// Prefer choices[0].message.content to avoid picking unrelated "content" keys.
		int from = body.indexOf("\"choices\"");
		if (from < 0) {
			from = 0;
		}
		final int messageIdx = body.indexOf("\"message\"", from);
		if (messageIdx >= 0) {
			from = messageIdx;
		}
		final String marker = "\"content\"";
		while (true) {
			final int idx = body.indexOf(marker, from);
			if (idx < 0) {
				return "";
			}
			int i = idx + marker.length();
			while (i < body.length() && Character.isWhitespace(body.charAt(i))) {
				i++;
			}
			if (i >= body.length() || body.charAt(i) != ':') {
				from = idx + 1;
				continue;
			}
			i++;
			while (i < body.length() && Character.isWhitespace(body.charAt(i))) {
				i++;
			}
			if (i >= body.length()) {
				return "";
			}
			if (body.charAt(i) == '"') {
				return unescapeJsonString(body, i + 1);
			}
			from = idx + 1;
		}
	}

	private static String unescapeJsonString(final String body, final int start) {
		final StringBuilder sb = new StringBuilder();
		boolean escape = false;
		for (int i = start; i < body.length(); i++) {
			final char c = body.charAt(i);
			if (escape) {
				if (c == 'n') {
					sb.append('\n');
				}
				else if (c == 'r') {
					sb.append('\r');
				}
				else if (c == 't') {
					sb.append('\t');
				}
				else if (c == 'u' && i + 4 < body.length()) {
					try {
						sb.append((char) Integer.parseInt(body.substring(i + 1, i + 5), 16));
						i += 4;
					}
					catch (Exception e) {
						sb.append('u');
					}
				}
				else {
					sb.append(c);
				}
				escape = false;
			}
			else if (c == '\\') {
				escape = true;
			}
			else if (c == '"') {
				break;
			}
			else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static List<String> parseLines(final String output, final int count) {
		final List<String> lines = new ArrayList<String>();
		if (output == null) {
			return lines;
		}
		final String[] parts = output.replace("\r\n", "\n").split("\n");
		for (int i = 0; i < parts.length; i++) {
			String line = parts[i].trim();
			if (line.length() == 0) {
				continue;
			}
			if (line.matches("^\\d+[\\.)、]\\s*.*")) {
				line = line.replaceFirst("^\\d+[\\.)、]\\s*", "");
			}
			if (line.startsWith("- ") || line.startsWith("* ")) {
				line = line.substring(2).trim();
			}
			if (line.length() > 0) {
				lines.add(line);
			}
			if (count > 0 && lines.size() >= count) {
				break;
			}
		}
		return lines;
	}

	private static String jsonString(final String value) {
		final String v = value == null ? "" : value;
		final StringBuilder sb = new StringBuilder();
		sb.append('"');
		for (int i = 0; i < v.length(); i++) {
			final char c = v.charAt(i);
			if (c == '\\' || c == '"') {
				sb.append('\\').append(c);
			}
			else if (c == '\n') {
				sb.append("\\n");
			}
			else if (c == '\r') {
				sb.append("\\r");
			}
			else if (c == '\t') {
				sb.append("\\t");
			}
			else if (c < 0x20) {
				sb.append(String.format("\\u%04x", Integer.valueOf(c)));
			}
			else {
				sb.append(c);
			}
		}
		sb.append('"');
		return sb.toString();
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

	private static String trimSlash(final String url) {
		if (url == null) {
			return "https://openrouter.ai/api/v1";
		}
		String u = url.trim();
		if ("null".equals(u) || u.length() == 0) {
			return "https://openrouter.ai/api/v1";
		}
		while (u.endsWith("/")) {
			u = u.substring(0, u.length() - 1);
		}
		return u;
	}

	private static boolean isOpenRouter(final String baseUrl) {
		return baseUrl != null && baseUrl.toLowerCase().indexOf("openrouter.ai") >= 0;
	}

	private static String trim(final String body, final int max) {
		if (body == null) {
			return "";
		}
		return body.length() > max ? body.substring(0, max) + "..." : body;
	}
}
