package org.docear.plugin.core.eagle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class EagleHash {
	private EagleHash() {
	}

	static String sha256Hex(final File file) throws IOException {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException e) {
			throw new IOException("SHA-256 unavailable", e);
		}
		final FileInputStream in = new FileInputStream(file);
		try {
			final byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) >= 0) {
				if (n > 0) {
					digest.update(buf, 0, n);
				}
			}
		}
		finally {
			try {
				in.close();
			}
			catch (IOException ignore) {
			}
		}
		return toHex(digest.digest());
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
