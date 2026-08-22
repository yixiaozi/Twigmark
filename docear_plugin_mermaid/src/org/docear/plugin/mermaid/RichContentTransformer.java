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
		switch (block.kind) {
			case MERMAID:
				return MermaidRenderService.getInstance().getIcon(block.source, node);
			case PLANTUML:
				return PlantUmlRenderService.getInstance().getIcon(block.source, node);
			case TABLE:
				return SyncRichRenderCache.getOrRender("table", block.source,
						new java.util.concurrent.Callable<RichPreviewIcon>() {
							@Override
							public RichPreviewIcon call() {
								return TableRenderer.render(block.source);
							}
						});
			case CODE:
				return SyncRichRenderCache.getOrRender("code", block.language + "\0" + block.source,
						new java.util.concurrent.Callable<RichPreviewIcon>() {
							@Override
							public RichPreviewIcon call() {
								return CodeHighlightRenderer.render(block.language, block.source);
							}
						});
			case EXCALIDRAW:
				return ExcalidrawRenderService.getInstance().getIcon(block.source, node);
			case STRUCTURED:
				return SyncRichRenderCache.getOrRender("structured", block.language + "\0" + block.source,
						new java.util.concurrent.Callable<RichPreviewIcon>() {
							@Override
							public RichPreviewIcon call() {
								return StructuredDataRenderer.render(block.language, block.source);
							}
						});
			case MATH:
				return null;
			default:
				return null;
		}
	}

	@Override
	public boolean markTransformation() {
		return true;
	}
}
