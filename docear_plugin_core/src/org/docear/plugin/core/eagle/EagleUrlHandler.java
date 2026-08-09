package org.docear.plugin.core.eagle;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.osgi.service.url.AbstractURLStreamHandlerService;

/**
 * Resolves {@code eagle://item/{id}.{ext}} to the current on-disk Eagle media file (offline).
 */
public class EagleUrlHandler extends AbstractURLStreamHandlerService {

	public URLConnection openConnection(final URL url) throws IOException {
		if (url == null) {
			throw new IOException("eagle url is null");
		}
		String itemId = EagleUri.parseItemIdFromUrlPath(url.getPath());
		if (itemId == null || itemId.length() == 0) {
			itemId = url.getHost();
			if ("item".equalsIgnoreCase(itemId)) {
				itemId = EagleUri.parseItemIdFromUrlPath(url.getFile());
			}
		}
		if (itemId == null || itemId.length() == 0) {
			throw new IOException("eagle item id missing in " + url);
		}
		final File file = EagleItemIndex.getInstance().resolveFile(itemId);
		if (file == null || !file.isFile()) {
			throw new IOException("Eagle item not found: " + itemId + " (configure eagle.library.paths / rebuild index)");
		}
		return file.toURI().toURL().openConnection();
	}
}
