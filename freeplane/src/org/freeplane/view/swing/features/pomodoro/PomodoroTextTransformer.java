package org.freeplane.view.swing.features.pomodoro;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.AbstractContentTransformer;
import org.freeplane.features.text.TextController;

/**
 * Appends pomodoro duration chip text at the end of the node label (display only).
 * Priority lower than tag chips (30) so tags stay ahead in the icon pipeline; this
 * transformer mutates HTML string so it runs after reminder prefix (200) → use 250.
 */
final class PomodoroTextTransformer extends AbstractContentTransformer {
	private static final String COLOR = "#C45C26";

	PomodoroTextTransformer() {
		super(250);
	}

	public Object transformContent(final TextController textController, final Object content, final NodeModel node,
			final Object transformedExtension) {
		if (!(content instanceof String)) {
			return content;
		}
		final String suffix = PomodoroTotals.formatInline(node, System.currentTimeMillis());
		if (suffix.length() == 0) {
			return content;
		}
		return appendSuffix((String) content, suffix);
	}

	private static String appendSuffix(final String text, final String suffix) {
		final String suffixHtml = "&nbsp;<span style=\"color:" + COLOR + ";font-size:90%\">"
				+ HtmlUtils.toHTMLEscapedText(suffix) + "</span>";
		if (HtmlUtils.isHtmlNode(text)) {
			final String lower = text.toLowerCase();
			final int bodyEnd = lower.lastIndexOf("</body>");
			if (bodyEnd >= 0) {
				return text.substring(0, bodyEnd) + suffixHtml + text.substring(bodyEnd);
			}
			return text + suffixHtml;
		}
		return "<html><body>" + HtmlUtils.toHTMLEscapedText(text) + suffixHtml + "</body></html>";
	}
}
