package org.docear.plugin.mermaid;

import javax.swing.Icon;

import org.freeplane.features.format.PatternFormat;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.AbstractContentTransformer;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.TransformationException;

/**
 * Shows Mermaid diagrams as {@link org.freeplane.view.swing.map.ZoomableLabel#TEXT_RENDERING_ICON}
 * while keeping the original Mermaid source in the node for editing.
 */
public final class MermaidContentTransformer extends AbstractContentTransformer {

	public MermaidContentTransformer() {
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
		if (transformedExtension != node.getUserObject()) {
			return null;
		}
		if (content == null) {
			return null;
		}
		final String nodeFormat = textController.getNodeFormat(node);
		if (PatternFormat.IDENTITY_PATTERN.equals(nodeFormat)) {
			return null;
		}
		final String source = MermaidParse.extractSource(content.toString(), nodeFormat);
		if (source == null || source.length() == 0) {
			return null;
		}
		return MermaidRenderService.getInstance().getIcon(source, node);
	}

	@Override
	public boolean markTransformation() {
		return true;
	}
}
