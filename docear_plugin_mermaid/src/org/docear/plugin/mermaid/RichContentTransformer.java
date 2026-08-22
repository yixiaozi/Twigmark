package org.docear.plugin.mermaid;

import javax.swing.Icon;

import org.freeplane.features.format.PatternFormat;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.AbstractContentTransformer;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.TransformationException;

/**
 * Unified rich-content preview: Mermaid, PlantUML, Markdown tables, code blocks.
 * Math/LaTeX is handled by the LaTeX plugin ({@code ```math} fences).
 */
public final class RichContentTransformer extends AbstractContentTransformer {

	public RichContentTransformer() {
		super(25);
	}

	@Override
	public Object transformContent(final TextController textController, final Object content, final NodeModel node,
			final Object transformedExtension) throws TransformationException {
		// Keep original source in node text for editing; preview is via TEXT_RENDERING_ICON only.
		return content;
	}

	@Override
	public Icon getIcon(final TextController textController, final Object content, final NodeModel node,
			final Object transformedExtension) {
		if (transformedExtension != node.getUserObject() || content == null) {
			return null;
		}
		// getIcon may receive HighlightedTransformedObject wrapper; always parse stored node text.
		final String raw = node.getUserObject() != null ? node.getUserObject().toString() : content.toString();
		final String nodeFormat = textController.getNodeFormat(node);
		if (PatternFormat.IDENTITY_PATTERN.equals(nodeFormat)) {
			return null;
		}
		final FenceParse.Block block = FenceParse.parse(raw, nodeFormat);
		if (block == null || block.source == null || block.source.length() == 0) {
			return null;
		}
		final float zoom = RichPreviewScale.get(node);
		if (RichPreviewScale.isShowingInterim(node)) {
			final Icon cached = RichPreviewCrispRender.tryCached(block, zoom);
			if (cached != null && RichPreviewCrispRender.hasFullImage(cached)) {
				RichPreviewScale.rememberCrisp(node, cached, zoom);
				return cached;
			}
			final Icon interim = RichPreviewScale.applyInterim(node, zoom);
			if (interim != null) {
				if (RichPreviewScale.isAwaitingCrisp(node)) {
					RichPreviewCrispRender.ensureQueued(node, block, zoom);
				}
				return interim;
			}
		}
		Icon icon = null;
		switch (block.kind) {
			case MERMAID:
				icon = MermaidRenderService.getInstance().getIcon(block.source, node, zoom);
				break;
			case PLANTUML:
				icon = PlantUmlRenderService.getInstance().getIcon(block.source, node, zoom);
				break;
			case TABLE:
				icon = SyncRichRenderCache.getOrRender("table", block.source, zoom,
						new java.util.concurrent.Callable<RichPreviewIcon>() {
							@Override
							public RichPreviewIcon call() {
								return TableRenderer.render(block.source, zoom);
							}
						});
				break;
			case CODE:
				icon = SyncRichRenderCache.getOrRender("code", block.language + "\0" + block.source, zoom,
						new java.util.concurrent.Callable<RichPreviewIcon>() {
							@Override
							public RichPreviewIcon call() {
								return CodeHighlightRenderer.render(block.language, block.source, zoom);
							}
						});
				break;
			case EXCALIDRAW:
				icon = ExcalidrawRenderService.getInstance().getIcon(block.source, node, zoom);
				break;
			case STRUCTURED:
				icon = SyncRichRenderCache.getOrRender("structured", block.language + "\0" + block.source, zoom,
						new java.util.concurrent.Callable<RichPreviewIcon>() {
							@Override
							public RichPreviewIcon call() {
								return StructuredDataRenderer.render(block.language, block.source, zoom);
							}
						});
				break;
			case CITATION:
				icon = SyncRichRenderCache.getOrRender("cite", block.source, zoom,
						new java.util.concurrent.Callable<RichPreviewIcon>() {
							@Override
							public RichPreviewIcon call() {
								return CitationCardRenderer.render(block.source, zoom);
							}
						});
				break;
			case TODO:
				icon = TodoChecklistRenderer.render(block.source, zoom);
				break;
			case MATH:
				return null;
			default:
				return null;
		}
		RichPreviewScale.rememberCrisp(node, icon, zoom);
		return icon;
	}

	@Override
	public boolean markTransformation() {
		return true;
	}
}
