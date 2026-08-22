package org.docear.plugin.mermaid;

/** Validates and normalizes Excalidraw JSON for export. */
final class ExcalidrawNormalize {

	private ExcalidrawNormalize() {
	}

	static String normalize(final String raw) throws IllegalArgumentException {
		if (raw == null) {
			throw new IllegalArgumentException("empty excalidraw source");
		}
		String text = raw.trim();
		if (text.length() == 0) {
			throw new IllegalArgumentException("empty excalidraw source");
		}
		if (text.startsWith("```")) {
			final int lineEnd = text.indexOf('\n');
			if (lineEnd > 0) {
				final int close = text.lastIndexOf("```");
				if (close > lineEnd) {
					text = text.substring(lineEnd + 1, close).trim();
				}
				else {
					text = text.substring(lineEnd + 1).trim();
				}
			}
		}
		if (!text.startsWith("{") && !text.startsWith("[")) {
			throw new IllegalArgumentException("excalidraw source must be JSON");
		}
		if (text.startsWith("[")) {
			text = wrapElementsArray(text);
		}
		else if (text.indexOf("\"elements\"") < 0) {
			throw new IllegalArgumentException("excalidraw JSON missing elements");
		}
		return text;
	}

	static boolean looksLikeJson(final String text) {
		if (text == null) {
			return false;
		}
		final String t = text.trim();
		if (!t.startsWith("{")) {
			return false;
		}
		return t.indexOf("\"elements\"") >= 0
				&& (t.indexOf("\"excalidraw\"") >= 0 || t.indexOf("\"type\":\"excalidraw\"") >= 0
						|| t.indexOf("\"type\": \"excalidraw\"") >= 0);
	}

	private static String wrapElementsArray(final String arrayJson) {
		final StringBuilder sb = new StringBuilder();
		sb.append("{\"type\":\"excalidraw\",\"version\":2,\"source\":\"twigmark\",\"elements\":");
		sb.append(arrayJson);
		sb.append(",\"appState\":{\"viewBackgroundColor\":\"#ffffff\",\"gridSize\":null},\"files\":{}}");
		return sb.toString();
	}
}
