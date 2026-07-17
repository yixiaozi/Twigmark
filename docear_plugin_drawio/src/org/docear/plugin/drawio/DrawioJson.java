package org.docear.plugin.drawio;

public final class DrawioJson {

	private DrawioJson() {
	}

	public static String getEvent(final String json) {
		return getStringField(json, "event");
	}

	public static String getXml(final String json) {
		return getStringField(json, "xml");
	}

	public static String quote(final String value) {
		if (value == null) {
			return "null";
		}
		final StringBuilder sb = new StringBuilder(value.length() + 16);
		sb.append('"');
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			switch (c) {
			case '"':
				sb.append("\\\"");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			default:
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c));
				}
				else {
					sb.append(c);
				}
			}
		}
		sb.append('"');
		return sb.toString();
	}

	public static String buildLoadMessage(final String xml, final String title) {
		final StringBuilder sb = new StringBuilder(256 + (xml != null ? xml.length() : 0));
		sb.append("{\"action\":\"load\",\"autosave\":1,\"saveAndExit\":\"1\",\"modified\":\"unsavedChanges\",\"title\":");
		sb.append(quote(title != null ? title : ""));
		sb.append(",\"xml\":");
		sb.append(quote(xml != null ? xml : ""));
		sb.append('}');
		return sb.toString();
	}

	private static String getStringField(final String json, final String key) {
		if (json == null || key == null) {
			return null;
		}
		final String needle = "\"" + key + "\":";
		int i = json.indexOf(needle);
		if (i < 0) {
			return null;
		}
		i += needle.length();
		while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
			i++;
		}
		if (i >= json.length() || json.charAt(i) != '"') {
			return null;
		}
		i++;
		final StringBuilder sb = new StringBuilder();
		while (i < json.length()) {
			final char c = json.charAt(i);
			if (c == '\\' && i + 1 < json.length()) {
				final char n = json.charAt(i + 1);
				if (n == 'n') {
					sb.append('\n');
				}
				else if (n == 'r') {
					sb.append('\r');
				}
				else if (n == 't') {
					sb.append('\t');
				}
				else if (n == '"') {
					sb.append('"');
				}
				else if (n == '\\') {
					sb.append('\\');
				}
				else {
					sb.append(n);
				}
				i += 2;
				continue;
			}
			if (c == '"') {
				break;
			}
			sb.append(c);
			i++;
		}
		return sb.toString();
	}
}
