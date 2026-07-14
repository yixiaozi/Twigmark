package org.freeplane.plugin.workspace.features.nodepins;

import java.awt.Color;
import java.awt.Font;
import java.util.Set;

import javax.swing.Icon;

import org.freeplane.core.ui.components.UITools;
import org.freeplane.features.format.PatternFormat;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.nodestyle.NodeStyleController;
import org.freeplane.features.text.AbstractContentTransformer;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.TransformationException;

/**
 * Display-only: render {@code 【tag】} segments as colored pills on the mind-map canvas.
 * Edit / model text stay plain (title + 【tag】…).
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
		final Set tags = NodeDetailsTagUtils.parseUserTags(source);
		if (tags.isEmpty()) {
			return null;
		}
		final String title = NodeDetailsTagUtils.extractNodeTitle(source);
		final NodeStyleController style = NodeStyleController.getController(textController.getModeController());
		final Font styleFont = style.getFont(node);
		final float size = Math.max(9f, Math.round(style.getFontSize(node) * UITools.FONT_SCALE_FACTOR));
		final Font baseFont = styleFont.deriveFont(size);
		final Color fg = style.getColor(node);
		return new NodeTagChipIcon(title, tags, baseFont, fg);
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
