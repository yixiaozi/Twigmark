package org.freeplane.features.url;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;

import org.freeplane.core.util.LogUtils;

/**
 * Converts {@code .mm} files that contain raw non-ASCII bytes (UTF-8/GBK Chinese, etc.)
 * into Freeplane's ASCII-safe form using {@code &#x…;} numeric character references.
 * After conversion the map opens correctly regardless of the JVM default charset.
 */
public final class MindMapEncodingRepair {
	private MindMapEncodingRepair() {
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
			if (original.length < 8 || !MindMapCharsetDetector.hasRawNonAsciiBytes(original, original.length)) {
				return false;
			}
			final Charset cs = MindMapCharsetDetector.detect(original, original.length);
			final String decoded = new String(original, cs);
			if (!containsLiteralNonAscii(decoded)) {
				return false;
			}
			final String repaired = encodeNonAsciiAsNcr(decoded);
			if (repaired.equals(decoded)) {
				return false;
			}
			// After NCR encoding the document is ASCII; write UTF-8 (ASCII subset) without BOM.
			final byte[] out = repaired.getBytes(Charset.forName("UTF-8"));
			if (!writeAtomically(file, out)) {
				return false;
			}
			LogUtils.info("Repaired mind-map encoding (" + cs.name() + " → NCR): " + file.getAbsolutePath());
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("Could not repair mind-map encoding: " + file.getAbsolutePath(), e);
			return false;
		}
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
				sb.append("&#x");
				sb.append(Integer.toHexString(cp));
				sb.append(';');
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
