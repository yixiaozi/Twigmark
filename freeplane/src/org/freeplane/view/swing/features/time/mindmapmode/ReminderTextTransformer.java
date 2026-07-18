package org.freeplane.view.swing.features.time.mindmapmode;

import java.util.Date;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.AbstractContentTransformer;
import org.freeplane.features.text.TextController;

/**
 * Prepends reminder date/time and cycle type to the node label (display only).
 */
final class ReminderTextTransformer extends AbstractContentTransformer {

	ReminderTextTransformer() {
		super(200);
	}

	public Object transformContent(final TextController textController, final Object content,
			final NodeModel node, final Object transformedExtension) {
		if (!(content instanceof String)) {
			return content;
		}
		final ReminderExtension reminder = ReminderExtension.getExtension(node);
		if (reminder == null) {
			return content;
		}
		final String prefix = ReminderTaskFormatter.appendInlineDuration(ReminderDateTimeFormatter.formatInlineNodePrefix(
				new Date(reminder.getRemindUserAt()), ReminderCycleAttributes.readFromNode(node)), ReminderTaskAttributes
				.readFromNode(node));
		if (prefix.length() == 0) {
			return content;
		}
		return prependPrefix((String) content, prefix);
	}

	private static final String REMINDER_INLINE_COLOR = "#0F766E";

	private static String prependPrefix(final String text, final String prefix) {
		final String prefixHtml = "<span style=\"color:" + REMINDER_INLINE_COLOR + "\">"
				+ HtmlUtils.toHTMLEscapedText(prefix) + "</span>&nbsp;";
		if (HtmlUtils.isHtmlNode(text)) {
			final String lower = text.toLowerCase();
			final int bodyIdx = lower.indexOf("<body>");
			if (bodyIdx >= 0) {
				final int insertAt = bodyIdx + 6;
				return text.substring(0, insertAt) + prefixHtml + text.substring(insertAt);
			}
			return "<html><body>" + prefixHtml + HtmlUtils.extractRawBody(text) + "</body></html>";
		}
		return "<html><body>" + prefixHtml + HtmlUtils.toHTMLEscapedText(text) + "</body></html>";
	}
}
