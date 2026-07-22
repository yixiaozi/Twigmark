package org.freeplane.plugin.workspace.features.nodepins;

import java.awt.Color;
import java.awt.Font;
import java.util.List;

import javax.swing.Icon;

import org.freeplane.core.ui.components.UITools;
import org.freeplane.features.format.PatternFormat;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.nodestyle.NodeStyleController;
import org.freeplane.features.text.AbstractContentTransformer;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.TransformationException;

/**
 * Display-only: render {@code 【tag】} as colored pills in their original in-text order.
 * Edit / model text stay plain ({@code 【tag】} markers preserved where typed).
 */
public final class TagChipContentTransformer extends AbstractContentTransformer {

	public TagChipContentTransformer() {
		super(30);
	}

	public Object transformContent(final TextController textController, final Object content, final NodeModel node,
			final Object transformedExtension) throws TransformationException {
		return content;
	}

	public Icon getIcon(final TextController textController, final Object content, final NodeModel node,
			final Object transformedExtension) {
		if (node == null || transformedExtension != node.getUserObject()) {
			return null;
		}
		if (textController.isTextFormattingDisabled(node)
				|| PatternFormat.IDENTITY_PATTERN.equals(textController.getNodeFormat(node))) {
			return null;
		}
		if (textController.isMinimized(node)) {
			return null;
		}
		final String source = resolveSourceText(content, transformedExtension);
		if (source == null || !NodeDetailsTagUtils.mayContainBracketTags(source)) {
			return null;
		}
		final List segments = NodeDetailsTagUtils.parseDisplaySegments(source);
		boolean hasTag = false;
		for (int i = 0; i < segments.size(); i++) {
			if (((TagDisplaySegment) segments.get(i)).isTag()) {
				hasTag = true;
				break;
			}
		}
		if (!hasTag) {
			return null;
		}
		final NodeStyleController style = NodeStyleController.getController(textController.getModeController());
		final Font styleFont = style.getFont(node);
		final float size = Math.max(9f, Math.round(style.getFontSize(node) * UITools.FONT_SCALE_FACTOR));
		final Font baseFont = styleFont.deriveFont(size);
		final Color fg = style.getColor(node);
		final int maxWidth = Math.max(40, style.getMaxWidth(node));
		return new NodeTagChipIcon(segments, baseFont, fg, maxWidth);
	}

	private static String resolveSourceText(final Object content, final Object transformedExtension) {
		if (transformedExtension instanceof String) {
			return (String) transformedExtension;
		}
		if (content instanceof String) {
			return (String) content;
		}
		return content != null ? content.toString() : null;
	}
}
