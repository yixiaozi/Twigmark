package org.docear.plugin.mermaid;

import java.util.concurrent.ConcurrentHashMap;

/** Memory cache for synchronous rich previews (table, code). */
final class SyncRichRenderCache {

	private static final ConcurrentHashMap<String, RichPreviewIcon> CACHE =
			new ConcurrentHashMap<String, RichPreviewIcon>();

	private SyncRichRenderCache() {
	}

	static RichPreviewIcon getOrRender(final String kind, final String source,
			final java.util.concurrent.Callable<RichPreviewIcon> renderer) {
		final String key = RichCache.hash(kind, source);
		final RichPreviewIcon cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		try {
			final RichPreviewIcon icon = renderer.call();
			if (icon != null && icon.getFullImage() != null) {
				CACHE.put(key, icon);
			}
			return icon;
		}
		catch (Exception e) {
			return RichPreviewIcon.error(kind, e.getMessage());
		}
	}
}
