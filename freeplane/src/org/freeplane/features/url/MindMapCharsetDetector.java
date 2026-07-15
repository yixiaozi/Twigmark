package org.freeplane.features.url;

import java.nio.charset.Charset;

import org.freeplane.core.util.FileUtils;

/**
 * Chooses UTF-8 vs the platform/default charset for {@code .mm} files.
 * <p>
 * Freeplane normally stores non-ASCII as {@code &#x…;} (ASCII-safe). External writers
 * (Todoist silent sync, editors, etc.) often leave raw UTF-8 Chinese without a BOM.
 * Loading those with GBK (common on Chinese Windows) produces 乱码.
 */
public final class MindMapCharsetDetector {
	private static final Charset UTF_8 = Charset.forName("UTF-8");

	private MindMapCharsetDetector() {
	}

	public static Charset detect(final byte[] data, final int length) {
		if (data == null || length <= 0) {
			return FileUtils.defaultCharset();
		}
		int offset = 0;
		if (length >= 3 && (data[0] & 0xff) == 0xEF && (data[1] & 0xff) == 0xBB && (data[2] & 0xff) == 0xBF) {
			return UTF_8;
		}
		final String declared = readXmlDeclaredEncoding(data, length);
		if (declared != null) {
			try {
				return Charset.forName(declared);
			}
			catch (Exception e) {
			}
		}
		final Charset platform = FileUtils.defaultCharset();
		if ("UTF-8".equalsIgnoreCase(platform.name()) || "UTF8".equalsIgnoreCase(platform.name())) {
			return UTF_8;
		}
		if (!looksLikeValidUtf8(data, offset, length)) {
			return platform;
		}
		if (!hasUtf8Multibyte(data, offset, length)) {
			// Pure ASCII (typical Freeplane NCR maps) — charset does not matter.
			return platform;
		}
		// Valid UTF-8 with non-ASCII: prefer UTF-8. External .mm writers (Todoist, editors, AI
		// history) use UTF-8; GBK mis-decoding often still yields CJK glyphs and can falsely
		// "win" a score comparison while showing 乱码 in the UI.
		return UTF_8;
	}

	/** True when the file has non-ASCII bytes that should be normalized to Freeplane NCR. */
	public static boolean hasRawNonAsciiBytes(final byte[] data, final int length) {
		if (data == null) {
			return false;
		}
		for (int i = 0; i < length; i++) {
			if ((data[i] & 0xff) >= 0x80) {
				return true;
			}
		}
		return false;
	}

	private static String readXmlDeclaredEncoding(final byte[] data, final int length) {
		final int probe = Math.min(length, 180);
		final String head = new String(data, 0, probe, Charset.forName("US-ASCII"));
		final String lower = head.toLowerCase();
		final int enc = lower.indexOf("encoding");
		if (enc < 0) {
			return null;
		}
		int i = enc + "encoding".length();
		while (i < head.length() && Character.isWhitespace(head.charAt(i))) {
			i++;
		}
		if (i >= head.length() || head.charAt(i) != '=') {
			return null;
		}
		i++;
		while (i < head.length() && Character.isWhitespace(head.charAt(i))) {
			i++;
		}
		if (i >= head.length()) {
			return null;
		}
		final char q = head.charAt(i);
		if (q != '"' && q != '\'') {
			return null;
		}
		i++;
		final int end = head.indexOf(q, i);
		if (end <= i) {
			return null;
		}
		return head.substring(i, end).trim();
	}

	static boolean looksLikeValidUtf8(final byte[] data, final int offset, final int length) {
		int i = offset;
		while (i < length) {
			final int b = data[i] & 0xff;
			if (b < 0x80) {
				i++;
				continue;
			}
			final int need;
			if ((b & 0xE0) == 0xC0) {
				need = 1;
			}
			else if ((b & 0xF0) == 0xE0) {
				need = 2;
			}
			else if ((b & 0xF8) == 0xF0) {
				need = 3;
			}
			else {
				return false;
			}
			if (i + need >= length) {
				return false;
			}
			for (int j = 1; j <= need; j++) {
				if ((data[i + j] & 0xC0) != 0x80) {
					return false;
				}
			}
			i += need + 1;
		}
		return true;
	}

	private static boolean hasUtf8Multibyte(final byte[] data, final int offset, final int length) {
		for (int i = offset; i < length; i++) {
			if ((data[i] & 0xff) >= 0x80) {
				return true;
			}
		}
		return false;
	}

	/** Higher is better: CJK / common punctuation, fewer replacement / control chars. */
	public static int contentScore(final String text) {
		if (text == null || text.length() == 0) {
			return 0;
		}
		int score = 0;
		final int limit = Math.min(text.length(), 200000);
		for (int i = 0; i < limit; ) {
			final int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			if (cp == 0xFFFD) {
				score -= 8;
				continue;
			}
			if (cp >= 0x4E00 && cp <= 0x9FFF) {
				score += 3;
				continue;
			}
			if (cp >= 0x3400 && cp <= 0x4DBF) {
				score += 2;
				continue;
			}
			if (cp == 0x3000 || cp == 0x3001 || cp == 0x3002 || cp == 0xFF0C || cp == 0xFF1A || cp == 0xFF1B) {
				score += 1;
				continue;
			}
			if (cp < 32 && cp != '\t' && cp != '\n' && cp != '\r') {
				score -= 2;
			}
		}
		return score;
	}

	/** Typical UTF-8-as-Latin1/GBK mojibake markers. */
	static int mojibakeScore(final String text) {
		if (text == null) {
			return 0;
		}
		int score = 0;
		final int limit = Math.min(text.length(), 200000);
		for (int i = 0; i < limit; i++) {
			final char c = text.charAt(i);
			if (c == 0xFFFD) {
				score += 5;
			}
			// Common leftovers when UTF-8 Chinese is decoded as Latin-1/Windows-1252
			else if (c == 0x00C3 || c == 0x00E2 || c == 0x00C2) {
				score += 1;
			}
			// 锟斤拷 style replacement often seen in wrong GBK/UTF round-trips
			else if (c == 0x951F || c == 0x65A4 || c == 0x7801) {
				score += 2;
			}
		}
		return score;
	}
}
