package org.docear.plugin.core.eagle;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * {@code eagle://item/{id}.{ext}} — item id is the stable identity; extension is only for viewer factory matching.
 */
public final class EagleUri {
	public static final String SCHEME = EagleConfig.PROP_PROTOCOL;
	public static final String HOST = "item";

	private EagleUri() {
	}

	public static boolean isEagleUri(final URI uri) {
		return uri != null && SCHEME.equalsIgnoreCase(uri.getScheme());
	}

	public static boolean isEagleUriString(final String value) {
		return value != null && value.regionMatches(true, 0, SCHEME + ":", 0, SCHEME.length() + 1);
	}

	public static URI create(final String itemId, final String ext) {
		if (itemId == null || itemId.trim().length() == 0) {
			throw new IllegalArgumentException("itemId required");
		}
		String suffix = ext == null ? "" : ext.trim();
		if (suffix.startsWith(".")) {
			suffix = suffix.substring(1);
		}
		if (suffix.length() == 0) {
			suffix = "png";
		}
		try {
			return new URI(SCHEME, HOST, "/" + itemId.trim() + "." + suffix.toLowerCase(), null);
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException("invalid eagle uri for id=" + itemId, e);
		}
	}

	public static String parseItemId(final URI uri) {
		if (!isEagleUri(uri)) {
			return null;
		}
		String path = uri.getPath();
		if (path == null || path.length() == 0) {
			path = uri.getSchemeSpecificPart();
		}
		if (path == null) {
			return null;
		}
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		// host form eagle://item/ID.ext already strips host; also accept eagle:ID.ext
		final int slash = path.lastIndexOf('/');
		if (slash >= 0) {
			path = path.substring(slash + 1);
		}
		final int dot = path.lastIndexOf('.');
		if (dot > 0) {
			return path.substring(0, dot);
		}
		return path.length() == 0 ? null : path;
	}

	public static String parseItemIdFromUrlPath(final String path) {
		if (path == null || path.length() == 0) {
			return null;
		}
		String p = path.startsWith("/") ? path.substring(1) : path;
		final int slash = p.lastIndexOf('/');
		if (slash >= 0) {
			p = p.substring(slash + 1);
		}
		final int dot = p.lastIndexOf('.');
		if (dot > 0) {
			return p.substring(0, dot);
		}
		return p.length() == 0 ? null : p;
	}
}
