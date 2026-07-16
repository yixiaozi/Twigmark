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
	private static final String COLOR_IDLE = "#C45C26";
	private static final String COLOR_RUN = "#D9480F";
	private static final String COLOR_PAUSE = "#8A6A4E";

	PomodoroTextTransformer() {
		super(250);
	}

	public Object transformContent(final TextController textController, final Object content, final NodeModel node,
			final Object transformedExtension) {
		if (!(content instanceof String)) {
			return content;
		}
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		final String suffix = PomodoroTotals.formatInline(node, System.currentTimeMillis());
		if (suffix.length() == 0) {
			return content;
		}
		String color = COLOR_IDLE;
		if (ext != null) {
			if (PomodoroExtension.STATE_RUNNING.equals(ext.getState())) {
				color = COLOR_RUN;
			}
			else if (PomodoroExtension.STATE_PAUSED.equals(ext.getState())) {
				color = COLOR_PAUSE;
			}
		}
		return appendSuffix((String) content, suffix, color);
	}

	private static String appendSuffix(final String text, final String suffix, final String color) {
		final String suffixHtml = "&nbsp;<span style=\"color:" + color
				+ ";font-size:90%;font-weight:bold;background-color:#FFF4EC;padding:1px 4px;border-radius:3px\">"
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
