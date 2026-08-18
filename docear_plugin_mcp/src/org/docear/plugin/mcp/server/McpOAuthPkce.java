package org.docear.plugin.mcp.server;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** RFC 7636 S256 helpers. */
public final class McpOAuthPkce {
	private static final Charset US_ASCII = Charset.forName("US-ASCII");
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final char[] HEX = "0123456789abcdef".toCharArray();

	private McpOAuthPkce() {
	}

	public static String challengeS256(final String verifier) {
		if (verifier == null) {
			return "";
		}
		return base64Url(sha256(verifier.getBytes(US_ASCII)));
	}

	public static boolean matches(final String verifier, final String challenge) {
		if (verifier == null || challenge == null || verifier.length() < 43 || challenge.length() == 0) {
			return false;
		}
		final String actual = challengeS256(verifier.trim());
		final String expected = challenge.trim();
		if (actual.length() != expected.length()) {
			return false;
		}
		int diff = 0;
		for (int i = 0; i < actual.length(); i++) {
			diff |= actual.charAt(i) ^ expected.charAt(i);
		}
		return diff == 0;
	}

	public static byte[] sha256(final byte[] input) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(input);
		}
		catch (Exception e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	public static String sha256Hex(final String text) {
		final byte[] digest = sha256((text == null ? "" : text).getBytes(Charset.forName("UTF-8")));
		return toHex(digest);
	}

	public static String randomHex(final int bytes) {
		final byte[] buf = new byte[bytes];
		RANDOM.nextBytes(buf);
		return toHex(buf);
	}

	public static String base64Url(final byte[] raw) {
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
	}

	public static String toHex(final byte[] bytes) {
		final char[] out = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			final int v = bytes[i] & 0xff;
			out[i * 2] = HEX[v >>> 4];
			out[i * 2 + 1] = HEX[v & 0x0f];
		}
		return new String(out);
	}
}
