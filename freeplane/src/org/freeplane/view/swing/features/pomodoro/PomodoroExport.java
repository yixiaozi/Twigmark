package org.freeplane.view.swing.features.pomodoro;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.swing.JOptionPane;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.text.TextController;

/**
 * Export pomodoro stats + session rows to CSV (or clipboard fallback).
 */
final class PomodoroExport {
	private PomodoroExport() {
	}

	static void exportInteractive(final boolean allMaps) {
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		if (manager == null) {
			return;
		}
		final String csv = buildCsv(allMaps);
		final String[] options = new String[] { "保存 CSV", "复制到剪贴板", "取消" };
		final int choice = JOptionPane.showOptionDialog(null, "导出番茄钟统计与会话明细", "导出番茄钟",
				JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (choice == 0) {
			saveCsv(csv);
		}
		else if (choice == 1) {
			copyClipboard(csv);
			JOptionPane.showMessageDialog(null, "已复制到剪贴板。");
		}
	}

	static String buildCsv(final boolean allMaps) {
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		final StringBuilder sb = new StringBuilder();
		sb.append("section,key,value\n");
		if (manager != null) {
			final long[] stats = manager.computeStats(allMaps);
			sb.append("stats,todayMs,").append(stats[0]).append('\n');
			sb.append("stats,today,").append(csv(PomodoroFormatter.formatDuration(stats[0]))).append('\n');
			sb.append("stats,weekMs,").append(stats[1]).append('\n');
			sb.append("stats,week,").append(csv(PomodoroFormatter.formatDuration(stats[1]))).append('\n');
			sb.append("stats,totalMs,").append(stats[2]).append('\n');
			sb.append("stats,total,").append(csv(PomodoroFormatter.formatDuration(stats[2]))).append('\n');
			sb.append("stats,enabledNodes,").append(stats[3]).append('\n');
			sb.append("stats,running,").append(stats[4]).append('\n');
			sb.append("stats,paused,").append(stats[5]).append('\n');
		}
		sb.append('\n');
		sb.append("mapFile,nodeId,nodeText,startAt,endAt,focusMs,focus,pauseMs,live\n");
		final List nodes = manager == null ? java.util.Collections.EMPTY_LIST
				: (allMaps ? manager.listOpenPomodoroNodes() : manager.listCurrentMapPomodoroNodes());
		final long now = System.currentTimeMillis();
		final List today = PomodoroTodayEntry.collect(nodes, now);
		final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
		fmt.setTimeZone(TimeZone.getDefault());
		for (int i = 0; i < today.size(); i++) {
			final PomodoroTodayEntry e = (PomodoroTodayEntry) today.get(i);
			final String mapFile = e.node.getMap() != null && e.node.getMap().getFile() != null
					? e.node.getMap().getFile().getAbsolutePath() : "";
			sb.append(csv(mapFile)).append(',');
			sb.append(csv(e.node.getID())).append(',');
			sb.append(csv(plain(e.node))).append(',');
			sb.append(csv(fmt.format(new Date(e.startMs)))).append(',');
			sb.append(csv(e.live ? "" : fmt.format(new Date(e.endMs)))).append(',');
			sb.append(e.focusMs).append(',');
			sb.append(csv(PomodoroFormatter.formatDuration(e.focusMs))).append(',');
			sb.append(Math.max(0L, e.endMs - e.startMs - e.focusMs)).append(',');
			sb.append(e.live).append('\n');
		}
		// Full history (all days) for enabled nodes
		sb.append('\n');
		sb.append("# full_history\n");
		sb.append("mapFile,nodeId,nodeText,startAt,endAt,focusMs,focus,pauseMs\n");
		for (int i = 0; i < nodes.size(); i++) {
			final NodeModel node = (NodeModel) nodes.get(i);
			final PomodoroExtension ext = PomodoroAttributes.read(node);
			if (ext == null) {
				continue;
			}
			final String mapFile = node.getMap() != null && node.getMap().getFile() != null
					? node.getMap().getFile().getAbsolutePath() : "";
			final List records = PomodoroLog.decode(ext.getLog());
			for (int r = 0; r < records.size(); r++) {
				final PomodoroSessionRecord rec = (PomodoroSessionRecord) records.get(r);
				sb.append(csv(mapFile)).append(',');
				sb.append(csv(node.getID())).append(',');
				sb.append(csv(plain(node))).append(',');
				sb.append(csv(fmt.format(new Date(rec.startMs)))).append(',');
				sb.append(csv(fmt.format(new Date(rec.endMs)))).append(',');
				sb.append(rec.focusMs).append(',');
				sb.append(csv(PomodoroFormatter.formatDuration(rec.focusMs))).append(',');
				sb.append(rec.pauseMs()).append('\n');
			}
		}
		return sb.toString();
	}

	private static void saveCsv(final String csv) {
		try {
			Frame owner = null;
			try {
				owner = Controller.getCurrentController().getViewController().getFrame();
			}
			catch (Exception e) {
			}
			final FileDialog dialog = new FileDialog(owner, "保存番茄钟 CSV", FileDialog.SAVE);
			dialog.setFile("pomodoro-" + new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()) + ".csv");
			dialog.setVisible(true);
			if (dialog.getFile() == null) {
				return;
			}
			File file = new File(dialog.getDirectory(), dialog.getFile());
			if (!file.getName().toLowerCase(Locale.ENGLISH).endsWith(".csv")) {
				file = new File(file.getParentFile(), file.getName() + ".csv");
			}
			final OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
			try {
				writer.write('\ufeff'); // BOM for Excel
				writer.write(csv);
			}
			finally {
				writer.close();
			}
			JOptionPane.showMessageDialog(owner, "已保存：\n" + file.getAbsolutePath());
		}
		catch (Exception e) {
			LogUtils.warn("Pomodoro export failed", e);
			JOptionPane.showMessageDialog(null, "导出失败：" + e.getMessage());
		}
	}

	private static void copyClipboard(final String text) {
		try {
			final java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(text);
			java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
		}
		catch (Exception e) {
			LogUtils.warn("Pomodoro clipboard failed", e);
		}
	}

	private static String csv(final String value) {
		if (value == null) {
			return "";
		}
		final String v = value.replace("\"", "\"\"");
		if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0) {
			return "\"" + v + "\"";
		}
		return v;
	}

	private static String plain(final NodeModel node) {
		try {
			final String text = TextController.getController().getPlainTextContent(node);
			if (text != null) {
				return HtmlUtils.htmlToPlain(text).replaceAll("\\s+", " ").trim();
			}
		}
		catch (Exception e) {
		}
		return node.getText() == null ? "" : HtmlUtils.htmlToPlain(node.getText());
	}
}
