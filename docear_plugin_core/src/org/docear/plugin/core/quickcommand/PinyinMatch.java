package org.docear.plugin.core.quickcommand;

import java.util.Locale;

import com.github.promeg.pinyinhelper.Pinyin;

/**
 * Chinese fuzzy match: plain text, full pinyin, initials, and subsequence on initials
 * (same idea as DocearReminder StationInfo.Search + NPinyin fields).
 */
final class PinyinMatch {
	private PinyinMatch() {
	}

	static String fullPinyin(final String text) {
		if (text == null || text.length() == 0) {
			return "";
		}
		try {
			return Pinyin.toPinyin(text, "").toLowerCase(Locale.ROOT);
		}
		catch (Exception e) {
			return text.toLowerCase(Locale.ROOT);
		}
	}

	static String initials(final String text) {
		if (text == null || text.length() == 0) {
			return "";
		}
		final StringBuilder sb = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			if (Character.isWhitespace(c)) {
				continue;
			}
			try {
				if (Pinyin.isChinese(c)) {
					final String py = Pinyin.toPinyin(c);
					if (py != null && py.length() > 0) {
						sb.append(Character.toLowerCase(py.charAt(0)));
					}
				}
				else {
					sb.append(Character.toLowerCase(c));
				}
			}
			catch (Exception e) {
				sb.append(Character.toLowerCase(c));
			}
		}
		return sb.toString();
	}

	static boolean matches(final String text, final String fullPinyin, final String initials, final String query) {
		if (query == null || query.length() == 0) {
			return true;
		}
		final String q = query.toLowerCase(Locale.ROOT);
		final String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
		final String full = fullPinyin == null ? "" : fullPinyin;
		final String jx = initials == null ? "" : initials;
		if (t.contains(q) || full.contains(q) || jx.contains(q)) {
			return true;
		}
		return subsequence(jx, q) || subsequence(full, q);
	}

	/** DocearReminder StationInfo.Search: filter chars appear in order. */
	static boolean subsequence(final String text, final String filter) {
		if (filter == null || filter.length() == 0) {
			return true;
		}
		if (text == null || text.length() == 0) {
			return false;
		}
		int index = 0;
		for (int i = 0; i < filter.length(); i++) {
			final char c = filter.charAt(i);
			boolean has = false;
			for (int j = index; j < text.length(); j++) {
				if (text.charAt(j) == c) {
					index = j + 1;
					has = true;
					break;
				}
			}
			if (!has) {
				return false;
			}
		}
		return true;
	}
}
