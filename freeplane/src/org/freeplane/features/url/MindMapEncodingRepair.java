package org.freeplane.features.url;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.freeplane.core.util.LogUtils;

/**
 * Converts {@code .mm} files that contain raw non-ASCII bytes (UTF-8/GBK Chinese, etc.)
 * into Freeplane's ASCII-safe form using {@code &#x…;} numeric character references.
 * Also sanitizes illegal XML 1.0 character references that strict parsers (Xerces) reject
 * — e.g. {@code &#x0;}, {@code &#xb;}, and UTF-16 surrogate-pair NCRs from char-wise writers.
 */
public final class MindMapEncodingRepair {
	private static final Pattern HEX_NCR = Pattern.compile("&#x([0-9a-fA-F]+);");
	private static final Pattern DEC_NCR = Pattern.compile("&#([0-9]+);");
	private static final Pattern SURROGATE_PAIR_NCR = Pattern.compile(
	        "&#x([dD][89A-Fa-f][0-9A-Fa-f]{2});&#x([dD][C-Fc-f][0-9A-Fa-f]{2});");
	/** Decimal UTF-16 surrogate pairs, e.g. {@code &#55356;&#57119;} (emoji). */
	private static final Pattern SURROGATE_PAIR_DEC_NCR = Pattern.compile("&#([0-9]+);&#([0-9]+);");
	/** Same pairs HTML-escaped inside TEXT attributes: {@code &amp;#55356;&amp;#57119;}. */
	private static final Pattern SURROGATE_PAIR_AMP_DEC_NCR = Pattern.compile(
	        "&amp;#([0-9]+);&amp;#([0-9]+);");
	private static final Pattern SURROGATE_PAIR_AMP_HEX_NCR = Pattern.compile(
	        "&amp;#x([dD][89A-Fa-f][0-9A-Fa-f]{2});&amp;#x([dD][C-Fc-f][0-9A-Fa-f]{2});");

	private MindMapEncodingRepair() {
	}

	/** True when file bytes contain the classic {@code &[x} NCR corruption. */
	public static boolean containsBrokenNcrMarkers(final File file) {
		if (file == null || !file.isFile()) {
			return false;
		}
		try {
			final byte[] bytes = readAllBytes(file);
			return indexOfAscii(bytes, "&[x") >= 0 || indexOfAscii(bytes, "&[X") >= 0;
		}
		catch (Exception e) {
			return false;
		}
	}

	private static int indexOfAscii(final byte[] haystack, final String needle) {
		if (haystack == null || needle == null || needle.length() == 0) {
			return -1;
		}
		final byte[] n = needle.getBytes(Charset.forName("US-ASCII"));
		final int limit = haystack.length - n.length;
		outer: for (int i = 0; i <= limit; i++) {
			for (int j = 0; j < n.length; j++) {
				if (haystack[i + j] != n[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}

	/**
	 * @return true if the file was rewritten
	 */
	public static boolean repairIfNeeded(final File file) {
		if (file == null || !file.isFile() || !file.canWrite()) {
			return false;
		}
		try {
			final byte[] original = readAllBytes(file);
			if (original.length < 8) {
				return false;
			}
			boolean changed = false;
			byte[] working = original;
			String text = null;
			final boolean brokenMarkers = indexOfAscii(working, "&[x") >= 0 || indexOfAscii(working, "&[X") >= 0;

			if (MindMapCharsetDetector.hasRawNonAsciiBytes(working, working.length)) {
				final Charset cs = MindMapCharsetDetector.detect(working, working.length);
				text = new String(working, cs);
				if (containsLiteralNonAscii(text)) {
					final String repaired = encodeNonAsciiAsNcr(text);
					if (!repaired.equals(text)) {
						text = repaired;
						changed = true;
						LogUtils.info("Repaired mind-map encoding (" + cs.name() + " → NCR): "
						        + file.getAbsolutePath());
					}
				}
			}

			if (text == null) {
				// ASCII / already-NCR maps: still scan for illegal character references.
				text = new String(working, Charset.forName("UTF-8"));
			}
			final String sanitized = sanitizeIllegalXmlCharacterReferences(text);
			if (!sanitized.equals(text)) {
				text = sanitized;
				changed = true;
				if (brokenMarkers) {
					LogUtils.severe("Repaired &[x corrupt NCR markers (likely convert_tags/#hashtag migration): "
					        + file.getAbsolutePath());
				}
				else {
					LogUtils.info("Sanitized illegal XML character references: " + file.getAbsolutePath());
				}
			}

			if (!changed) {
				return false;
			}
			final byte[] out = text.getBytes(Charset.forName("UTF-8"));
			if (!writeAtomically(file, out)) {
				return false;
			}
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("Could not repair mind-map encoding: " + file.getAbsolutePath(), e);
			return false;
		}
	}

	/**
	 * In-memory sanitize for SAX / MCP paths that read without rewriting the file first.
	 */
	public static String sanitizeIllegalXmlCharacterReferences(final String text) {
		if (text == null || text.length() == 0 || text.indexOf('&') < 0) {
			return text;
		}
		String result = text;
		// Hashtag/bracket migration glitch: leading "&#x" became "&[x" and maps refuse to open.
		if (result.indexOf("&[x") >= 0 || result.indexOf("&[X") >= 0) {
			result = result.replace("&[x", "&#x").replace("&[X", "&#x");
		}
		result = combineSurrogatePairs(result, SURROGATE_PAIR_AMP_HEX_NCR, 16, true);
		result = combineSurrogatePairs(result, SURROGATE_PAIR_AMP_DEC_NCR, 10, true);
		result = combineSurrogatePairs(result, SURROGATE_PAIR_NCR, 16, false);
		result = combineSurrogatePairs(result, SURROGATE_PAIR_DEC_NCR, 10, false);

		result = dropIllegalNcrs(result, HEX_NCR, 16);
		result = dropIllegalNcrs(result, DEC_NCR, 10);
		// Drop HTML-escaped illegal singles left inside rich text (&amp;#x0; etc.)
		result = dropIllegalNcrs(result, Pattern.compile("&amp;#x([0-9a-fA-F]+);"), 16);
		result = dropIllegalNcrs(result, Pattern.compile("&amp;#([0-9]+);"), 10);
		return result;
	}

	private static String combineSurrogatePairs(final String text, final Pattern pattern, final int radix,
	        final boolean htmlEscaped) {
		final Matcher pair = pattern.matcher(text);
		final StringBuffer pairBuf = new StringBuffer(text.length() + 16);
		boolean pairChanged = false;
		while (pair.find()) {
			final int hi;
			final int lo;
			try {
				hi = Integer.parseInt(pair.group(1), radix);
				lo = Integer.parseInt(pair.group(2), radix);
			}
			catch (NumberFormatException e) {
				pair.appendReplacement(pairBuf, Matcher.quoteReplacement(pair.group(0)));
				continue;
			}
			if (hi <= 0xFFFF && lo <= 0xFFFF && Character.isSurrogatePair((char) hi, (char) lo)) {
				final int cp = Character.toCodePoint((char) hi, (char) lo);
				if (isXml10Char(cp)) {
					final String ncr = (htmlEscaped ? "&amp;#x" : "&#x") + Integer.toHexString(cp) + ";";
					pair.appendReplacement(pairBuf, Matcher.quoteReplacement(ncr));
					pairChanged = true;
					continue;
				}
			}
			pair.appendReplacement(pairBuf, Matcher.quoteReplacement(pair.group(0)));
		}
		if (!pairChanged) {
			return text;
		}
		pair.appendTail(pairBuf);
		return pairBuf.toString();
	}

	private static String dropIllegalNcrs(final String text, final Pattern pattern, final int radix) {
		final Matcher m = pattern.matcher(text);
		final StringBuffer buf = new StringBuffer(text.length());
		boolean changed = false;
		while (m.find()) {
			int cp;
			try {
				cp = Integer.parseInt(m.group(1), radix);
			}
			catch (NumberFormatException e) {
				m.appendReplacement(buf, "");
				changed = true;
				continue;
			}
			if (isXml10Char(cp)) {
				m.appendReplacement(buf, Matcher.quoteReplacement(m.group(0)));
			}
			else {
				m.appendReplacement(buf, "");
				changed = true;
			}
		}
		if (!changed) {
			return text;
		}
		m.appendTail(buf);
		return buf.toString();
	}

	/** XML 1.0 Char production (excludes surrogates and most C0 controls). */
	public static boolean isXml10Char(final int cp) {
		return cp == 0x9 || cp == 0xA || cp == 0xD
		        || (cp >= 0x20 && cp <= 0xD7FF)
		        || (cp >= 0xE000 && cp <= 0xFFFD)
		        || (cp >= 0x10000 && cp <= 0x10FFFF);
	}

	static boolean containsLiteralNonAscii(final String text) {
		if (text == null) {
			return false;
		}
		for (int i = 0; i < text.length(); ) {
			final int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			if (cp > 0x7E) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Replaces every code point {@code > 0x7E} with a Freeplane-style hex character reference.
	 * Existing {@code &#x…;} / {@code &#…;} sequences are ASCII and left untouched.
	 * Skips code points that are illegal in XML 1.0.
	 */
	public static String encodeNonAsciiAsNcr(final String text) {
		if (text == null || text.length() == 0) {
			return text;
		}
		final StringBuilder sb = new StringBuilder(text.length() + 64);
		for (int i = 0; i < text.length(); ) {
			final int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			if (cp > 0x7E) {
				if (isXml10Char(cp)) {
					sb.append("&#x");
					sb.append(Integer.toHexString(cp));
					sb.append(';');
				}
			}
			else if (cp < 0x20 && cp != 0x9 && cp != 0xA && cp != 0xD) {
				// drop illegal C0 controls
			}
			else {
				sb.append((char) cp);
			}
		}
		return sb.toString();
	}

	private static byte[] readAllBytes(final File file) throws IOException {
		final long length = file.length();
		if (length > Integer.MAX_VALUE - 8) {
			throw new IOException("file too large");
		}
		final byte[] bytes = new byte[(int) length];
		final FileInputStream in = new FileInputStream(file);
		try {
			int off = 0;
			while (off < bytes.length) {
				final int n = in.read(bytes, off, bytes.length - off);
				if (n < 0) {
					break;
				}
				off += n;
			}
			if (off == bytes.length) {
				return bytes;
			}
			final byte[] truncated = new byte[off];
			System.arraycopy(bytes, 0, truncated, 0, off);
			return truncated;
		}
		finally {
			in.close();
		}
	}

	private static boolean writeAtomically(final File file, final byte[] bytes) throws IOException {
		final File tmp = new File(file.getParentFile(), "~encoding-" + file.getName());
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(tmp);
			out.write(bytes);
			out.flush();
			out.close();
			out = null;
			if (!isValidMapTail(tmp)) {
				tmp.delete();
				return false;
			}
			if (file.exists() && !file.delete()) {
				tmp.delete();
				return false;
			}
			if (!tmp.renameTo(file)) {
				tmp.delete();
				return false;
			}
			return true;
		}
		finally {
			if (out != null) {
				try {
					out.close();
				}
				catch (IOException e) {
				}
			}
		}
	}

	private static boolean isValidMapTail(final File file) {
		try {
			final RandomAccessFile raf = new RandomAccessFile(file, "r");
			try {
				final long length = raf.length();
				if (length < 7) {
					return false;
				}
				raf.seek(Math.max(0, length - 32));
				final byte[] buf = new byte[(int) Math.min(32, length)];
				raf.readFully(buf);
				return new String(buf, "US-ASCII").indexOf("/map>") >= 0;
			}
			finally {
				raf.close();
			}
		}
		catch (Exception e) {
			return false;
		}
	}
}
