package org.freeplane.view.swing.features.pomodoro;

import java.util.List;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.note.NoteModel;
import org.freeplane.features.note.mindmapmode.MNoteController;

/**
 * Keeps a human-readable session history section inside the node note.
 */
final class PomodoroNoteSync {
	private static final String BEGIN = "===== 番茄钟记录 =====";
	private static final String END = "===== 番茄钟记录结束 =====";

	private PomodoroNoteSync() {
	}

	static void sync(final NodeModel node, final PomodoroExtension ext) {
		if (node == null || ext == null || !ext.isEnabled()) {
			return;
		}
		try {
			final MNoteController notes = (MNoteController) org.freeplane.features.note.NoteController.getController();
			if (notes == null) {
				return;
			}
			final String history = PomodoroLog.formatHistoryPreview(ext.getLog(), 30);
			final String block = BEGIN + "\n累计 " + PomodoroFormatter.formatDuration(ext.getTotalMs())
					+ " · 共 " + ext.sessionCount() + " 次\n"
					+ (history.length() == 0 ? "（暂无完成会话）\n" : history + "\n") + END;
			String existing = "";
			final NoteModel note = NoteModel.getNote(node);
			if (note != null && note.getHtml() != null) {
				existing = note.getHtml();
			}
			final String plain = org.freeplane.core.util.HtmlUtils.htmlToPlain(existing == null ? "" : existing);
			final String mergedPlain = replaceOrAppendSection(plain, block);
			notes.setNoteText(node, toSimpleHtml(mergedPlain));
		}
		catch (Exception e) {
			LogUtils.warn("Pomodoro: note sync failed", e);
		}
	}

	private static String replaceOrAppendSection(final String plain, final String block) {
		final String src = plain == null ? "" : plain;
		final int begin = src.indexOf(BEGIN);
		final int end = src.indexOf(END);
		if (begin >= 0 && end > begin) {
			return src.substring(0, begin) + block + src.substring(end + END.length());
		}
		if (src.trim().length() == 0) {
			return block;
		}
		return src.replaceAll("\\s+$", "") + "\n\n" + block;
	}

	private static String toSimpleHtml(final String plain) {
		final String escaped = org.freeplane.core.util.HtmlUtils.toHTMLEscapedText(plain).replace("\n", "<br>\n");
		return "<html><body>" + escaped + "</body></html>";
	}
}
