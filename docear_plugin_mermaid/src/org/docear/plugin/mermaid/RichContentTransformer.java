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
		return content;
	}

	@Override
	public Icon getIcon(final TextController textController, final Object content, final NodeModel node,
			final Object transformedExtension) {
		if (transformedExtension != node.getUserObject() || content == null) {
			return null;
		}
		final String nodeFormat = textController.getNodeFormat(node);
		if (PatternFormat.IDENTITY_PATTERN.equals(nodeFormat)) {
			return null;
		}
		final FenceParse.Block block = FenceParse.parse(content.toString(), nodeFormat);
		if (block == null || block.source == null || block.source.length() == 0) {
			return null;
		}
		switch (block.kind) {
			case MERMAID:
				return MermaidRenderService.getInstance().getIcon(block.source, node);
			case PLANTUML:
				return PlantUmlRenderService.getInstance().getIcon(block.source, node);
			case TABLE:
				return TableRenderer.render(block.source);
			case CODE:
				return CodeHighlightRenderer.render(block.language, block.source);
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
