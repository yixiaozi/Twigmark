package org.docear.plugin.core.quickcommand;

import java.util.Locale;

import com.github.promeg.pinyinhelper.Pinyin;

/**
 * Chinese fuzzy match aligned with DocearReminder (CN / full pinyin / initials /
 * subsequence), plus mixed Chinese+pinyin segment matching so queries like
 * {@code wuxi}, {@code wx}, {@code wui}, {@code wi}, {@code 吴xi} hit {@code 无锡}.
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
			final String py = charPinyin(c);
			if (py.length() > 0) {
				sb.append(py.charAt(0));
			}
		}
		return sb.toString();
	}

	static boolean matches(final String text, final String fullPinyin, final String initials, final String query) {
		if (query == null || query.length() == 0) {
			return true;
		}
		if (text == null || text.length() == 0) {
			return false;
		}
		final String q = query.toLowerCase(Locale.ROOT).trim();
		if (q.length() == 0) {
			return true;
		}
		final String t = text.toLowerCase(Locale.ROOT);
		final String full = fullPinyin == null || fullPinyin.length() == 0 ? fullPinyin(text) : fullPinyin;
		final String jx = initials == null || initials.length() == 0 ? initials(text) : initials;
		if (t.contains(q) || full.contains(q) || jx.contains(q)) {
			return true;
		}
		if (subsequence(jx, q) || subsequence(full, q)) {
			return true;
		}
		return mixedMatch(text, q);
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

	/**
	 * Walk text chars; query may use Chinese glyph, same-pinyin Chinese, full pinyin,
	 * or pinyin prefix. Text characters may be skipped (so {@code xi} matches {@code 无锡}).
	 */
	static boolean mixedMatch(final String text, final String query) {
		if (query == null || query.length() == 0) {
			return true;
		}
		if (text == null || text.length() == 0) {
			return false;
		}
		return matchAt(text, 0, query.toLowerCase(Locale.ROOT), 0);
	}

	private static boolean matchAt(final String text, final int ti, final String query, final int qi) {
		if (qi >= query.length()) {
			return true;
		}
		if (ti >= text.length()) {
			return false;
		}
		final char tc = text.charAt(ti);
		if (Character.isWhitespace(tc)) {
			return matchAt(text, ti + 1, query, qi);
		}
		// Skip this text character (subsequence / mid-string match).
		if (matchAt(text, ti + 1, query, qi)) {
			return true;
		}
		final int[] consumed = new int[8];
		final int n = waysToConsume(tc, query, qi, consumed);
		for (int i = 0; i < n; i++) {
			if (matchAt(text, ti + 1, query, qi + consumed[i])) {
				return true;
			}
		}
		return false;
	}

	private static int waysToConsume(final char textChar, final String query, final int qi, final int[] out) {
		int n = 0;
		if (qi >= query.length()) {
			return 0;
		}
		final char qc = query.charAt(qi);
		final char tcLower = Character.toLowerCase(textChar);
		// Exact glyph / latin char.
		if (tcLower == qc || textChar == qc) {
			out[n++] = 1;
		}
		final String textPy = charPinyin(textChar);
		if (textPy.length() == 0) {
			return n;
		}
		// Query Chinese with same pinyin (吴 ≈ 无 → wu).
		if (isChinese(qc)) {
			final String queryPy = charPinyin(qc);
			if (queryPy.length() > 0 && queryPy.equals(textPy) && !contains(out, n, 1)) {
				out[n++] = 1;
			}
			return n;
		}
		// Full pinyin or any non-empty prefix (w / wu for 无, x / xi for 锡).
		final int remain = query.length() - qi;
		final int max = Math.min(remain, textPy.length());
		for (int len = max; len >= 1; len--) {
			if (textPy.regionMatches(0, query, qi, len) && !contains(out, n, len)) {
				if (n < out.length) {
					out[n++] = len;
				}
			}
		}
		return n;
	}

	private static boolean contains(final int[] arr, final int n, final int value) {
		for (int i = 0; i < n; i++) {
			if (arr[i] == value) {
				return true;
			}
		}
		return false;
	}

	private static String charPinyin(final char c) {
		if (Character.isWhitespace(c)) {
			return "";
		}
		try {
			if (Pinyin.isChinese(c)) {
				final String py = Pinyin.toPinyin(c);
				return py == null ? "" : py.toLowerCase(Locale.ROOT);
			}
		}
		catch (Exception e) {
			// fall through
		}
		if (isAsciiLetterOrDigit(c)) {
			return String.valueOf(Character.toLowerCase(c));
		}
		return "";
	}

	private static boolean isChinese(final char c) {
		try {
			return Pinyin.isChinese(c);
		}
		catch (Exception e) {
			return false;
		}
	}

	private static boolean isAsciiLetterOrDigit(final char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
	}
}
