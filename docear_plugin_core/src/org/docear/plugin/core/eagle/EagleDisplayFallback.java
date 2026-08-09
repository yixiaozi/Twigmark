package org.docear.plugin.core.eagle;

import java.io.File;
import java.net.URI;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.view.swing.features.filepreview.ExternalImageResolution;

/**
 * Display fallback: original path resolution stays primary (handled before this runs).
 * Only when the file is missing do we look up Eagle by id ({@code eagle://}) or unique filename.
 * If no Eagle library is configured, this is a no-op.
 */
public final class EagleDisplayFallback implements ExternalImageResolution.Fallback {
	private EagleDisplayFallback() {
	}

	public static void install() {
		ExternalImageResolution.setFallback(new EagleDisplayFallback());
	}

	public URI fallback(final MapModel map, final URI storedUri, final URI resolvedUri) {
		if (EagleConfig.existingLibraryRoots().isEmpty()) {
			return null;
		}
		try {
			if (EagleUri.isEagleUri(storedUri)) {
				final File file = EagleItemIndex.getInstance().resolveFile(EagleUri.parseItemId(storedUri));
				return toFileUri(file);
			}
			final String hint = pathHint(storedUri, resolvedUri);
			final EagleItem item = EagleItemIndex.getInstance().findUniqueByFileNameHint(hint);
			return item == null ? null : toFileUri(item.getFile());
		}
		catch (Exception e) {
			LogUtils.warn("Eagle display fallback failed: " + e.getMessage());
			return null;
		}
	}

	private static URI toFileUri(final File file) {
		if (file == null || !file.isFile()) {
			return null;
		}
		return file.toURI();
	}

	private static String pathHint(final URI storedUri, final URI resolvedUri) {
		if (resolvedUri != null) {
			if (resolvedUri.getPath() != null && resolvedUri.getPath().length() > 0) {
				return resolvedUri.getPath();
			}
			return resolvedUri.toString();
		}
		if (storedUri == null) {
			return null;
		}
		if (storedUri.getPath() != null && storedUri.getPath().length() > 0) {
			return storedUri.getPath();
		}
		return storedUri.getSchemeSpecificPart();
	}
}
