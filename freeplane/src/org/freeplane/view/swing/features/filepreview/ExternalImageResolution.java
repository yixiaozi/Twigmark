package org.freeplane.view.swing.features.filepreview;

import java.io.File;
import java.net.URI;

import org.freeplane.features.map.MapModel;

/**
 * Optional display-time fallback when a stored ExternalObject URI does not resolve to a readable file.
 * Plugins (e.g. Eagle) may register a {@link Fallback}; if none is set, behavior is unchanged.
 */
public final class ExternalImageResolution {
	public interface Fallback {
		/**
		 * @param map mind map
		 * @param storedUri URI saved on the node (may be relative / file / custom scheme)
		 * @param resolvedUri result of normal UrlManager resolution (may be null or point to a missing file)
		 * @return alternate absolute URI to display, or null to keep {@code resolvedUri}
		 */
		URI fallback(MapModel map, URI storedUri, URI resolvedUri);
	}

	private static volatile Fallback fallback;

	private ExternalImageResolution() {
	}

	public static void setFallback(final Fallback value) {
		fallback = value;
	}

	public static Fallback getFallback() {
		return fallback;
	}

	/** Prefer a readable resolved file; otherwise ask the optional fallback. */
	public static URI resolveDisplayUri(final MapModel map, final URI storedUri, final URI resolvedUri) {
		if (isReadableFileUri(resolvedUri)) {
			return resolvedUri;
		}
		final Fallback fb = fallback;
		if (fb != null) {
			try {
				final URI alt = fb.fallback(map, storedUri, resolvedUri);
				if (isReadableFileUri(alt)) {
					return alt;
				}
			}
			catch (Exception ignore) {
			}
		}
		return resolvedUri;
	}

	public static boolean isReadableFileUri(final URI uri) {
		if (uri == null) {
			return false;
		}
		try {
			if (!"file".equalsIgnoreCase(uri.getScheme())) {
				// Custom schemes (eagle://) are readable if openConnection succeeds — handled by caller factories.
				// For fallback selection we only treat existing local files as "already OK".
				return false;
			}
			final File file = new File(uri);
			return file.isFile() && file.canRead();
		}
		catch (Exception e) {
			return false;
		}
	}
}
