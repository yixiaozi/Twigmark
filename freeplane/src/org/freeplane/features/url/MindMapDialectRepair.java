package org.freeplane.features.url;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.freeplane.core.util.FreeplaneVersion;
import org.freeplane.core.util.LogUtils;

/**
 * Repairs .mm files whose header was altered by external writers (e.g. Todoist silent sync)
 * so Docear can open them without the "unknown program" dialect warning.
 * <p>
 * Only rewrites the leading {@code <map …>} region; the rest of the file bytes are preserved.
 */
public final class MindMapDialectRepair {
	private static final byte[] UTF8_BOM = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	private MindMapDialectRepair() {
	}

	/**
	 * @return true if the file was modified on disk
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
			final byte[] repaired = repairBytes(original);
			if (repaired == null || sameBytes(original, repaired)) {
				return false;
			}
			if (!writeAtomically(file, repaired)) {
				return false;
			}
			LogUtils.info("Repaired mind-map dialect header: " + file.getAbsolutePath());
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("Could not repair mind-map dialect header: " + file.getAbsolutePath(), e);
			return false;
		}
	}

	/**
	 * Returns repaired file bytes, or {@code null} if unchanged / not a recognizable map.
	 */
	public static byte[] repairBytes(final byte[] original) {
		int pos = 0;
		if (hasUtf8Bom(original, 0)) {
			pos = 3;
		}
		pos = skipAsciiWhitespace(original, pos);
		// Drop one or more XML declarations.
		while (pos + 1 < original.length && original[pos] == '<' && original[pos + 1] == '?') {
			final int end = indexOf(original, pos + 2, (byte) '?', (byte) '>');
			if (end < 0) {
				return null;
			}
			pos = skipAsciiWhitespace(original, end + 2);
		}
		// Drop leading HTML/XML comments (Freeplane usage comments sometimes precede <map>).
		while (pos + 3 < original.length && original[pos] == '<' && original[pos + 1] == '!'
		        && original[pos + 2] == '-' && original[pos + 3] == '-') {
			final int end = indexOf(original, pos + 4, (byte) '-', (byte) '-', (byte) '>');
			if (end < 0) {
				return null;
			}
			pos = skipAsciiWhitespace(original, end + 3);
		}
		if (!startsWithAscii(original, pos, "<map")) {
			return null;
		}
		final int tagEnd = indexOfByte(original, pos, (byte) '>');
		if (tagEnd < 0) {
			return null;
		}
		final String openTag = new String(original, pos, tagEnd - pos + 1, java.nio.charset.Charset.forName("US-ASCII"));
		final String rebuilt = rebuildMapOpenTag(openTag);
		if (rebuilt == null) {
			return null;
		}
		final boolean preambleChanged = pos != 0 || hasUtf8Bom(original, 0);
		final boolean tagChanged = !rebuilt.equals(openTag);
		if (!preambleChanged && !tagChanged) {
			// Still ensure file starts at <map (no BOM) — if pos==0 and tag unchanged, nothing to do.
			return null;
		}
		final byte[] tagBytes = rebuilt.getBytes(java.nio.charset.Charset.forName("US-ASCII"));
		final byte[] out = new byte[tagBytes.length + (original.length - tagEnd - 1)];
		System.arraycopy(tagBytes, 0, out, 0, tagBytes.length);
		System.arraycopy(original, tagEnd + 1, out, tagBytes.length, original.length - tagEnd - 1);
		return out;
	}

	private static String rebuildMapOpenTag(final String openTag) {
		if (openTag == null || !openTag.startsWith("<map")) {
			return null;
		}
		String version = extractAttribute(openTag, "version");
		if (!isAcceptedVersion(version)) {
			version = FreeplaneVersion.XML_VERSION;
		}
		final StringBuilder sb = new StringBuilder();
		sb.append("<map version=\"");
		sb.append(escapeXmlAttr(version));
		sb.append('"');
		appendOtherAttributes(sb, openTag, "version");
		if (openTag.endsWith("/>")) {
			sb.append("/>");
		}
		else {
			sb.append('>');
		}
		return sb.toString();
	}

	/** Versions Docear/Freeplane open without the unknown-program warning. */
	static boolean isAcceptedVersion(final String version) {
		if (version == null) {
			return false;
		}
		final String v = version.trim();
		if (v.length() == 0) {
			return false;
		}
		final String lower = v.toLowerCase();
		if (lower.startsWith("freeplane")) {
			return true;
		}
		if (lower.startsWith("docear")) {
			return true;
		}
		if (lower.equals("0.9.0") || lower.startsWith("0.8")) {
			return true;
		}
		// Transformer / generic XML often writes version="1.0" or "1.0.1" — treat as broken.
		return false;
	}

	private static String extractAttribute(final String tag, final String name) {
		final String pattern = name + "=\"";
		final int start = indexOfIgnoreCase(tag, pattern);
		if (start < 0) {
			final String pattern2 = name + "='";
			final int start2 = indexOfIgnoreCase(tag, pattern2);
			if (start2 < 0) {
				return null;
			}
			final int valueStart = start2 + pattern2.length();
			final int valueEnd = tag.indexOf('\'', valueStart);
			if (valueEnd < 0) {
				return null;
			}
			return tag.substring(valueStart, valueEnd);
		}
		final int valueStart = start + pattern.length();
		final int valueEnd = tag.indexOf('"', valueStart);
		if (valueEnd < 0) {
			return null;
		}
		return tag.substring(valueStart, valueEnd);
	}

	private static void appendOtherAttributes(final StringBuilder sb, final String openTag, final String skipName) {
		int i = 4; // after <map
		final int end = openTag.endsWith("/>") ? openTag.length() - 2 : openTag.length() - 1;
		while (i < end) {
			while (i < end && Character.isWhitespace(openTag.charAt(i))) {
				i++;
			}
			if (i >= end) {
				break;
			}
			final int nameStart = i;
			while (i < end && openTag.charAt(i) != '=' && !Character.isWhitespace(openTag.charAt(i))) {
				i++;
			}
			final String name = openTag.substring(nameStart, i);
			while (i < end && Character.isWhitespace(openTag.charAt(i))) {
				i++;
			}
			if (i >= end || openTag.charAt(i) != '=') {
				break;
			}
			i++;
			while (i < end && Character.isWhitespace(openTag.charAt(i))) {
				i++;
			}
			if (i >= end) {
				break;
			}
			final char quote = openTag.charAt(i);
			if (quote != '"' && quote != '\'') {
				break;
			}
			i++;
			final int valueStart = i;
			while (i < end && openTag.charAt(i) != quote) {
				i++;
			}
			final String value = openTag.substring(valueStart, Math.min(i, end));
			if (i < end) {
				i++;
			}
			if (skipName.equalsIgnoreCase(name)) {
				continue;
			}
			sb.append(' ');
			sb.append(name);
			sb.append("=\"");
			sb.append(escapeXmlAttr(value));
			sb.append('"');
		}
	}

	private static String escapeXmlAttr(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
	}

	private static int indexOfIgnoreCase(final String haystack, final String needle) {
		return haystack.toLowerCase().indexOf(needle.toLowerCase());
	}

	private static boolean hasUtf8Bom(final byte[] bytes, final int offset) {
		return offset + 2 < bytes.length && bytes[offset] == UTF8_BOM[0] && bytes[offset + 1] == UTF8_BOM[1]
		        && bytes[offset + 2] == UTF8_BOM[2];
	}

	private static int skipAsciiWhitespace(final byte[] bytes, int pos) {
		while (pos < bytes.length) {
			final int b = bytes[pos] & 0xff;
			if (b == ' ' || b == '\t' || b == '\n' || b == '\r') {
				pos++;
				continue;
			}
			break;
		}
		return pos;
	}

	private static boolean startsWithAscii(final byte[] bytes, final int pos, final String ascii) {
		if (pos + ascii.length() > bytes.length) {
			return false;
		}
		for (int i = 0; i < ascii.length(); i++) {
			if ((bytes[pos + i] & 0xff) != ascii.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	private static int indexOfByte(final byte[] bytes, final int from, final byte target) {
		for (int i = from; i < bytes.length; i++) {
			if (bytes[i] == target) {
				return i;
			}
		}
		return -1;
	}

	private static int indexOf(final byte[] bytes, final int from, final byte a, final byte b) {
		for (int i = from; i + 1 < bytes.length; i++) {
			if (bytes[i] == a && bytes[i + 1] == b) {
				return i;
			}
		}
		return -1;
	}

	private static int indexOf(final byte[] bytes, final int from, final byte a, final byte b, final byte c) {
		for (int i = from; i + 2 < bytes.length; i++) {
			if (bytes[i] == a && bytes[i + 1] == b && bytes[i + 2] == c) {
				return i;
			}
		}
		return -1;
	}

	private static boolean sameBytes(final byte[] a, final byte[] b) {
		if (a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
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
		final File tmp = new File(file.getParentFile(), "~dialect-" + file.getName());
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
