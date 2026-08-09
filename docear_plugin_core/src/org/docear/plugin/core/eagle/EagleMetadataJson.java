package org.docear.plugin.core.eagle;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal JSON field extractor for Eagle {@code metadata.json} (flat object). Avoids a JSON dependency.
 */
final class EagleMetadataJson {
	private static final Pattern STRING_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
	private static final Pattern NUMBER_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(-?\\d+)");
	private static final Pattern BOOL_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(true|false)");

	private EagleMetadataJson() {
	}

	static Map<String, String> parseFlat(final String json) {
		final Map<String, String> map = new HashMap<String, String>();
		if (json == null || json.length() == 0) {
			return map;
		}
		Matcher m = STRING_FIELD.matcher(json);
		while (m.find()) {
			map.put(m.group(1), unescape(m.group(2)));
		}
		m = NUMBER_FIELD.matcher(json);
		while (m.find()) {
			if (!map.containsKey(m.group(1))) {
				map.put(m.group(1), m.group(2));
			}
		}
		m = BOOL_FIELD.matcher(json);
		while (m.find()) {
			if (!map.containsKey(m.group(1))) {
				map.put(m.group(1), m.group(2));
			}
		}
		return map;
	}

	private static String unescape(final String value) {
		final StringBuilder sb = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '\\' && i + 1 < value.length()) {
				char n = value.charAt(++i);
				if (n == 'n') {
					sb.append('\n');
				}
				else if (n == 'r') {
					sb.append('\r');
				}
				else if (n == 't') {
					sb.append('\t');
				}
				else if (n == '"' || n == '\\' || n == '/') {
					sb.append(n);
				}
				else if (n == 'u' && i + 4 < value.length()) {
					try {
						sb.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
						i += 4;
					}
					catch (NumberFormatException e) {
						sb.append(n);
					}
				}
				else {
					sb.append(n);
				}
			}
			else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
