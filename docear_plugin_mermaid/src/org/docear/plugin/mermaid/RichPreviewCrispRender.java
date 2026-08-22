package org.docear.plugin.mermaid;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Icon;

import org.freeplane.features.map.NodeModel;

/** Background crisp re-render after zoom settles (keeps interim thumbnail visible). */
final class RichPreviewCrispRender {

	private static final Set<String> QUEUED = ConcurrentHashMap.newKeySet();

	private RichPreviewCrispRender() {
	}

	static Icon tryCached(final FenceParse.Block block, final float zoom) {
		if (block == null) {
			return null;
		}
		switch (block.kind) {
			case MERMAID:
				return MermaidRenderService.getInstance().peekCached(block.source, zoom);
			case PLANTUML:
				return PlantUmlRenderService.getInstance().peekCached(block.source, zoom);
			case EXCALIDRAW:
				return ExcalidrawRenderService.getInstance().peekCached(block.source, zoom);
			case TABLE:
				return SyncRichRenderCache.peek("table", block.source, zoom);
			case CODE:
				return SyncRichRenderCache.peek("code", block.language + "\0" + block.source, zoom);
			case STRUCTURED:
				return SyncRichRenderCache.peek("structured", block.language + "\0" + block.source, zoom);
			case CITATION:
				return SyncRichRenderCache.peek("cite", block.source, zoom);
			default:
				return null;
		}
	}

	static void ensureQueued(final NodeModel node, final FenceParse.Block block, final float zoom) {
		if (node == null || block == null) {
			return;
		}
		final String key = RichPreviewScale.nodeKey(node);
		if (!QUEUED.add(key)) {
			return;
		}
		try {
			switch (block.kind) {
				case MERMAID:
					MermaidRenderService.getInstance().getIcon(block.source, node, zoom);
					break;
				case PLANTUML:
					PlantUmlRenderService.getInstance().getIcon(block.source, node, zoom);
					break;
				case EXCALIDRAW:
					ExcalidrawRenderService.getInstance().getIcon(block.source, node, zoom);
					break;
				case TABLE:
					finishSync(node, SyncRichRenderCache.getOrRender("table", block.source, zoom,
					        new java.util.concurrent.Callable<RichPreviewIcon>() {
						        @Override
						        public RichPreviewIcon call() {
							        return TableRenderer.render(block.source, zoom);
						        }
					        }), zoom, key);
					break;
				case CODE:
					finishSync(node, SyncRichRenderCache.getOrRender("code", block.language + "\0" + block.source, zoom,
					        new java.util.concurrent.Callable<RichPreviewIcon>() {
						        @Override
						        public RichPreviewIcon call() {
							        return CodeHighlightRenderer.render(block.language, block.source, zoom);
						        }
					        }), zoom, key);
					break;
				case STRUCTURED:
					finishSync(node, SyncRichRenderCache.getOrRender("structured", block.language + "\0" + block.source,
					        zoom, new java.util.concurrent.Callable<RichPreviewIcon>() {
						        @Override
						        public RichPreviewIcon call() {
							        return StructuredDataRenderer.render(block.language, block.source, zoom);
						        }
					        }), zoom, key);
					break;
				case CITATION:
					finishSync(node, SyncRichRenderCache.getOrRender("cite", block.source, zoom,
					        new java.util.concurrent.Callable<RichPreviewIcon>() {
						        @Override
						        public RichPreviewIcon call() {
							        return CitationCardRenderer.render(block.source, zoom);
						        }
					        }), zoom, key);
					break;
				case TODO:
					finishSync(node, TodoChecklistRenderer.render(block.source, zoom), zoom, key);
					break;
				default:
					QUEUED.remove(key);
					break;
			}
		}
		catch (Throwable t) {
			QUEUED.remove(key);
		}
	}

	static void onAsyncComplete(final NodeModel node) {
		QUEUED.remove(RichPreviewScale.nodeKey(node));
		RichPreviewScale.clearAwaitingCrisp(node);
	}

	private static void finishSync(final NodeModel node, final Icon icon, final float zoom, final String queueKey) {
		QUEUED.remove(queueKey);
		if (icon != null && hasFullImage(icon)) {
			RichPreviewScale.clearAwaitingCrisp(node);
			RichPreviewScale.rememberCrisp(node, icon, zoom);
			RichPreviewScale.refreshPreview(node, zoom, zoom);
		}
	}

	static boolean hasFullImage(final Icon icon) {
		if (icon instanceof RichPreviewIcon) {
			return ((RichPreviewIcon) icon).getFullImage() != null;
		}
		if (icon instanceof InteractiveTodoIcon) {
			return ((InteractiveTodoIcon) icon).getFullImage() != null;
		}
		return false;
	}
}
