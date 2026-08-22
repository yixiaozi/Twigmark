package org.docear.plugin.mermaid;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Shared disk cache for rich preview PNGs. */
final class RichCache {

	private RichCache() {
	}

	static File cacheDir() {
		final File dir = new File(System.getProperty("user.home"), ".docear/rich-cache");
		if (!dir.isDirectory()) {
			dir.mkdirs();
		}
		return dir;
	}

	static String hash(final String kind, final String source) {
		try {
			final MessageDigest md = MessageDigest.getInstance("SHA-256");
			final byte[] dig = md.digest((kind + "\0" + source).getBytes(StandardCharsets.UTF_8));
			final StringBuilder sb = new StringBuilder(dig.length * 2);
			for (int i = 0; i < dig.length; i++) {
				sb.append(String.format("%02x", dig[i] & 0xff));
			}
			return kind + "-" + sb.toString();
		}
		catch (Exception e) {
			return kind + "-" + Integer.toHexString(source.hashCode());
		}
	}

	static File pngFile(final String cacheKey) {
		return new File(cacheDir(), cacheKey + ".png");
	}
}
