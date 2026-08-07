package org.docear.plugin.mcp.webchat;

import java.security.MessageDigest;
import java.security.SecureRandom;

/** Salted iterated SHA-256 password hashing (Java 1.6 friendly). */
public final class WebchatPassword {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int ITERATIONS = 12000;
	private static final int SALT_BYTES = 16;

	private WebchatPassword() {
	}

	public static String newSalt() {
		final byte[] salt = new byte[SALT_BYTES];
		RANDOM.nextBytes(salt);
		return toHex(salt);
	}

	public static String hash(final String password, final String saltHex) {
		final String pwd = password == null ? "" : password;
		try {
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] value = (saltHex + ":" + pwd).getBytes("UTF-8");
			for (int i = 0; i < ITERATIONS; i++) {
				digest.reset();
				value = digest.digest(value);
			}
			return toHex(value);
		}
		catch (Exception e) {
			throw new IllegalStateException("password hash failed: " + e.getMessage(), e);
		}
	}

	public static boolean matches(final String password, final String saltHex, final String expectedHash) {
		if (expectedHash == null || saltHex == null) {
			return false;
		}
		final String actual = hash(password, saltHex);
		if (actual.length() != expectedHash.length()) {
			return false;
		}
		int diff = 0;
		for (int i = 0; i < actual.length(); i++) {
			diff |= actual.charAt(i) ^ expectedHash.charAt(i);
		}
		return diff == 0;
	}

	public static String newToken() {
		final byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		return "ws_" + toHex(bytes);
	}

	public static String newId() {
		return java.util.UUID.randomUUID().toString().replace("-", "");
	}

	private static String toHex(final byte[] bytes) {
		final StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (int i = 0; i < bytes.length; i++) {
			final int v = bytes[i] & 0xff;
			if (v < 16) {
				sb.append('0');
			}
			sb.append(Integer.toHexString(v));
		}
		return sb.toString();
	}
}
