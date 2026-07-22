package org.docear.plugin.mcp.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.util.EdtRunner;
import org.docear.plugin.mcp.util.EdtRunner.Task;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.text.TextController;
import org.freeplane.view.swing.features.pomodoro.PomodoroAttributes;
import org.freeplane.view.swing.features.pomodoro.PomodoroExtension;
import org.freeplane.view.swing.features.pomodoro.PomodoroFormatter;
import org.freeplane.view.swing.features.pomodoro.PomodoroLog;
import org.freeplane.view.swing.features.pomodoro.PomodoroPauseInterval;
import org.freeplane.view.swing.features.pomodoro.PomodoroSessionManager;
import org.freeplane.view.swing.features.pomodoro.PomodoroSessionRecord;
import org.freeplane.view.swing.features.pomodoro.PomodoroTotals;

/**
 * MCP access to Docear pomodoro / focus-time tracking so AI can see what the user
 * is working on and when sessions happened.
 */
public final class McpPomodoroService {
	private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

	static {
		TIME_FMT.setTimeZone(TimeZone.getDefault());
	}

	private McpPomodoroService() {
	}

	public static String getRunningPomodoro() throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				if (manager == null) {
					data.put("available", JsonValue.ofBoolean(false));
					data.put("running", JsonValue.ofBoolean(false));
					data.put("message", JsonValue.ofString("Pomodoro is not installed in this session."));
					return JsonValue.ofMap(data).toJson();
				}
				final NodeModel running = manager.getRunningNode();
				data.put("available", JsonValue.ofBoolean(true));
				if (running == null) {
					data.put("running", JsonValue.ofBoolean(false));
					data.put("node", JsonValue.ofNull());
					return JsonValue.ofMap(data).toJson();
				}
				data.put("running", JsonValue.ofBoolean(true));
				data.put("node", sessionJson(running, System.currentTimeMillis()));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String listPomodoroSessions(final boolean allMaps, final String stateFilter) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final PomodoroSessionManager manager = requireManager();
				final long now = System.currentTimeMillis();
				final List nodes = allMaps ? manager.listOpenPomodoroNodes() : manager.listCurrentMapPomodoroNodes();
				final List<JsonValue> out = new ArrayList<JsonValue>();
				final String filter = stateFilter == null ? "" : stateFilter.trim().toLowerCase(Locale.ENGLISH);
				for (int i = 0; i < nodes.size(); i++) {
					final NodeModel node = (NodeModel) nodes.get(i);
					final PomodoroExtension ext = PomodoroAttributes.read(node);
					if (ext == null || !ext.isEnabled()) {
						continue;
					}
					if (filter.length() > 0 && !filter.equals(ext.getState())) {
						continue;
					}
					out.add(sessionJson(node, now));
				}
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				data.put("allMaps", JsonValue.ofBoolean(allMaps));
				data.put("stateFilter", JsonValue.ofString(filter));
				data.put("count", JsonValue.ofNumber(Integer.valueOf(out.size())));
				data.put("sessions", JsonValue.ofList(out));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String getPomodoroStats(final boolean allMaps) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final PomodoroSessionManager manager = requireManager();
				final long[] stats = manager.computeStats(allMaps);
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				data.put("allMaps", JsonValue.ofBoolean(allMaps));
				data.put("todayMs", JsonValue.ofNumber(Long.valueOf(stats[0])));
				data.put("today", JsonValue.ofString(PomodoroFormatter.formatDuration(stats[0])));
				data.put("weekMs", JsonValue.ofNumber(Long.valueOf(stats[1])));
				data.put("week", JsonValue.ofString(PomodoroFormatter.formatDuration(stats[1])));
				data.put("totalMs", JsonValue.ofNumber(Long.valueOf(stats[2])));
				data.put("total", JsonValue.ofString(PomodoroFormatter.formatDuration(stats[2])));
				data.put("enabledNodes", JsonValue.ofNumber(Long.valueOf(stats[3])));
				data.put("runningCount", JsonValue.ofNumber(Long.valueOf(stats[4])));
				data.put("pausedCount", JsonValue.ofNumber(Long.valueOf(stats[5])));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String getPomodoroHistory(final String filePath, final String nodeId, final long sinceMillis,
			final int limit) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final long now = System.currentTimeMillis();
				final List recordsOut = new ArrayList();
				final List<JsonValue> sessionsOut = new ArrayList<JsonValue>();
				if (nodeId != null && nodeId.trim().length() > 0) {
					final NodeModel node = session.requireNode(nodeId);
					appendHistory(node, sinceMillis, recordsOut);
					sessionsOut.add(sessionJson(node, now));
				}
				else {
					collectHistoryRecursive(session.getMap().getRootNode(), sinceMillis, recordsOut, sessionsOut, now);
				}
				Collections.sort(recordsOut, new Comparator() {
					public int compare(final Object a, final Object b) {
						final long endA = ((HistoryRow) a).endMs;
						final long endB = ((HistoryRow) b).endMs;
						return endA < endB ? -1 : (endA > endB ? 1 : 0);
					}
				});
				int max = limit > 0 ? limit : 100;
				final List slice;
				if (recordsOut.size() > max) {
					slice = recordsOut.subList(recordsOut.size() - max, recordsOut.size());
				}
				else {
					slice = recordsOut;
				}
				final List<JsonValue> recordsJson = new ArrayList<JsonValue>();
				for (int i = 0; i < slice.size(); i++) {
					recordsJson.add(((HistoryRow) slice.get(i)).toJson());
				}
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				data.put("mapFile", JsonValue.ofString(session.getFile().getAbsolutePath()));
				data.put("nodeId", JsonValue.ofString(nodeId == null ? "" : nodeId));
				data.put("sinceMillis", JsonValue.ofNumber(Long.valueOf(sinceMillis)));
				data.put("sessionCount", JsonValue.ofNumber(Integer.valueOf(sessionsOut.size())));
				data.put("recordCount", JsonValue.ofNumber(Integer.valueOf(recordsJson.size())));
				data.put("sessions", JsonValue.ofList(sessionsOut));
				data.put("records", JsonValue.ofList(recordsJson));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String startPomodoro(final String filePath, final String nodeId) throws Exception {
		return control("start", filePath, nodeId);
	}

	public static String pausePomodoro(final String filePath, final String nodeId) throws Exception {
		return control("pause", filePath, nodeId);
	}

	public static String stopPomodoro(final String filePath, final String nodeId) throws Exception {
		return control("stop", filePath, nodeId);
	}

	private static String control(final String action, final String filePath, final String nodeId) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final PomodoroSessionManager manager = requireManager();
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node;
				if (nodeId == null || nodeId.trim().length() == 0) {
					final NodeModel selected = Controller.getCurrentController().getSelection().getSelected();
					if (selected == null) {
						throw new IllegalArgumentException("Provide nodeId or select a node in Docear.");
					}
					node = selected;
				}
				else {
					node = session.requireNode(nodeId);
				}
				if ("start".equals(action)) {
					manager.start(node);
				}
				else if ("pause".equals(action)) {
					manager.pause(node);
				}
				else if ("stop".equals(action)) {
					manager.stop(node);
				}
				else {
					throw new IllegalArgumentException("Unknown action: " + action);
				}
				if (session.isHeadlessLoad()) {
					session.save();
				}
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				data.put("ok", JsonValue.ofBoolean(true));
				data.put("action", JsonValue.ofString(action));
				data.put("mapFile", JsonValue.ofString(session.getFile().getAbsolutePath()));
				data.put("headlessLoad", JsonValue.ofBoolean(session.isHeadlessLoad()));
				data.put("node", sessionJson(node, System.currentTimeMillis()));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	private static void ensureWritable() {
		if (DocearMcpConfig.isReadOnly()) {
			throw new SecurityException("Docear MCP is running in read-only mode.");
		}
	}

	private static PomodoroSessionManager requireManager() {
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		if (manager == null) {
			throw new IllegalStateException("Pomodoro is not available (Docear mindmap mode not ready).");
		}
		return manager;
	}

	private static void collectHistoryRecursive(final NodeModel node, final long sinceMillis, final List recordsOut,
			final List sessionsOut, final long now) {
		if (node == null) {
			return;
		}
		final PomodoroExtension ext = PomodoroAttributes.read(node);
		if (ext != null && ext.isEnabled()) {
			sessionsOut.add(sessionJson(node, now));
			appendHistory(node, sinceMillis, recordsOut);
		}
		final List children = node.getChildren();
		if (children != null) {
			for (int i = 0; i < children.size(); i++) {
				collectHistoryRecursive((NodeModel) children.get(i), sinceMillis, recordsOut, sessionsOut, now);
			}
		}
	}

	private static void appendHistory(final NodeModel node, final long sinceMillis, final List recordsOut) {
		final PomodoroExtension ext = PomodoroAttributes.read(node);
		if (ext == null) {
			return;
		}
		final List records = PomodoroLog.decode(ext.getLog());
		final String mapFile = node.getMap() != null && node.getMap().getFile() != null
				? node.getMap().getFile().getAbsolutePath()
				: "";
		final String text = plainText(node);
		for (int i = 0; i < records.size(); i++) {
			final PomodoroSessionRecord rec = (PomodoroSessionRecord) records.get(i);
			if (sinceMillis > 0 && rec.endMs < sinceMillis) {
				continue;
			}
			recordsOut.add(new HistoryRow(mapFile, node.getID(), text, rec));
		}
	}

	public static JsonValue sessionJson(final NodeModel node, final long now) {
		final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
		final PomodoroExtension ext = PomodoroAttributes.read(node);
		final String mapFile = node.getMap() != null && node.getMap().getFile() != null
				? node.getMap().getFile().getAbsolutePath()
				: "";
		data.put("mapFile", JsonValue.ofString(mapFile));
		data.put("nodeId", JsonValue.ofString(node.getID()));
		data.put("text", JsonValue.ofString(plainText(node)));
		if (ext == null) {
			data.put("enabled", JsonValue.ofBoolean(false));
			return JsonValue.ofMap(data);
		}
		data.put("enabled", JsonValue.ofBoolean(ext.isEnabled()));
		data.put("state", JsonValue.ofString(ext.getState()));
		data.put("stateLabel", JsonValue.ofString(PomodoroAttributes.stateLabel(ext.getState())));
		data.put("totalMs", JsonValue.ofNumber(Long.valueOf(ext.getTotalMs())));
		data.put("total", JsonValue.ofString(PomodoroFormatter.formatDuration(ext.getTotalMs())));
		data.put("activeMs", JsonValue.ofNumber(Long.valueOf(ext.getActiveMs())));
		data.put("liveSegmentMs", JsonValue.ofNumber(Long.valueOf(ext.liveSegmentMs(now))));
		data.put("liveSegment", JsonValue.ofString(PomodoroFormatter.formatDuration(ext.liveSegmentMs(now))));
		data.put("liveTotalMs", JsonValue.ofNumber(Long.valueOf(ext.liveTotalMs(now))));
		data.put("liveTotal", JsonValue.ofString(PomodoroFormatter.formatDuration(ext.liveTotalMs(now))));
		data.put("subtreeMs", JsonValue.ofNumber(Long.valueOf(PomodoroTotals.subtreeMs(node, now))));
		data.put("subtree", JsonValue.ofString(PomodoroFormatter.formatDuration(PomodoroTotals.subtreeMs(node, now))));
		data.put("sessionAt", JsonValue.ofNumber(Long.valueOf(ext.getSessionAt())));
		data.put("startedAt", JsonValue.ofNumber(Long.valueOf(ext.getStartedAt())));
		data.put("pausedAt", JsonValue.ofNumber(Long.valueOf(ext.getPausedAt())));
		data.put("sessionCount", JsonValue.ofNumber(Integer.valueOf(ext.sessionCount())));
		final List openPauses = new ArrayList(PomodoroPauseInterval.decodeList(ext.getSessionPauses()));
		if (PomodoroExtension.STATE_PAUSED.equals(ext.getState()) && ext.getPausedAt() > 0 && now > ext.getPausedAt()) {
			openPauses.add(new PomodoroPauseInterval(ext.getPausedAt(), now));
		}
		data.put("pauseRanges", JsonValue.ofString(PomodoroPauseInterval.formatRanges(openPauses)));
		final List pauseJson = new ArrayList();
		for (int i = 0; i < openPauses.size(); i++) {
			final PomodoroPauseInterval p = (PomodoroPauseInterval) openPauses.get(i);
			final Map<String, JsonValue> one = new LinkedHashMap<String, JsonValue>();
			one.put("startMs", JsonValue.ofNumber(Long.valueOf(p.startMs)));
			one.put("endMs", JsonValue.ofNumber(Long.valueOf(p.endMs)));
			one.put("startAt", JsonValue.ofString(formatTime(p.startMs)));
			one.put("endAt", JsonValue.ofString(formatTime(p.endMs)));
			one.put("durationMs", JsonValue.ofNumber(Long.valueOf(p.durationMs())));
			one.put("open", JsonValue.ofBoolean(Boolean.valueOf(
					PomodoroExtension.STATE_PAUSED.equals(ext.getState()) && p.startMs == ext.getPausedAt())));
			pauseJson.add(JsonValue.ofMap(one));
		}
		data.put("pauses", JsonValue.ofList(pauseJson));
		final List records = PomodoroLog.decode(ext.getLog());
		final long todayMs = PomodoroLog.sumFocusSince(records, PomodoroLog.startOfToday())
				+ contribLive(ext, now, PomodoroLog.startOfToday());
		data.put("todayMs", JsonValue.ofNumber(Long.valueOf(todayMs)));
		data.put("today", JsonValue.ofString(PomodoroFormatter.formatDuration(todayMs)));
		return JsonValue.ofMap(data);
	}

	private static long contribLive(final PomodoroExtension ext, final long now, final long since) {
		if (ext.liveSegmentMs(now) <= 0) {
			return 0L;
		}
		final long anchor = ext.getSessionAt() > 0 ? ext.getSessionAt() : ext.getStartedAt();
		return anchor >= since ? ext.liveSegmentMs(now) : 0L;
	}

	private static String plainText(final NodeModel node) {
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

	private static synchronized String formatTime(final long millis) {
		return TIME_FMT.format(new Date(millis));
	}

	private static final class HistoryRow {
		final String mapFile;
		final String nodeId;
		final String text;
		final long startMs;
		final long endMs;
		final long focusMs;
		final long pauseMs;
		final String display;
		final List pauseIntervals;

		HistoryRow(final String mapFile, final String nodeId, final String text, final PomodoroSessionRecord rec) {
			this.mapFile = mapFile;
			this.nodeId = nodeId;
			this.text = text;
			this.startMs = rec.startMs;
			this.endMs = rec.endMs;
			this.focusMs = rec.focusMs;
			this.pauseMs = rec.pauseMs();
			this.display = rec.toDisplayLine();
			this.pauseIntervals = rec.pauseIntervals;
		}

		JsonValue toJson() {
			final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
			row.put("mapFile", JsonValue.ofString(mapFile));
			row.put("nodeId", JsonValue.ofString(nodeId));
			row.put("text", JsonValue.ofString(text));
			row.put("startMs", JsonValue.ofNumber(Long.valueOf(startMs)));
			row.put("endMs", JsonValue.ofNumber(Long.valueOf(endMs)));
			row.put("focusMs", JsonValue.ofNumber(Long.valueOf(focusMs)));
			row.put("pauseMs", JsonValue.ofNumber(Long.valueOf(pauseMs)));
			row.put("startAt", JsonValue.ofString(formatTime(startMs)));
			row.put("endAt", JsonValue.ofString(formatTime(endMs)));
			row.put("focus", JsonValue.ofString(PomodoroFormatter.formatDuration(focusMs)));
			row.put("pauseRanges", JsonValue.ofString(PomodoroPauseInterval.formatRanges(pauseIntervals)));
			final List pauseJson = new ArrayList();
			if (pauseIntervals != null) {
				for (int i = 0; i < pauseIntervals.size(); i++) {
					final PomodoroPauseInterval p = (PomodoroPauseInterval) pauseIntervals.get(i);
					final Map<String, JsonValue> one = new LinkedHashMap<String, JsonValue>();
					one.put("startMs", JsonValue.ofNumber(Long.valueOf(p.startMs)));
					one.put("endMs", JsonValue.ofNumber(Long.valueOf(p.endMs)));
					one.put("startAt", JsonValue.ofString(formatTime(p.startMs)));
					one.put("endAt", JsonValue.ofString(formatTime(p.endMs)));
					one.put("durationMs", JsonValue.ofNumber(Long.valueOf(p.durationMs())));
					pauseJson.add(JsonValue.ofMap(one));
				}
			}
			row.put("pauses", JsonValue.ofList(pauseJson));
			row.put("display", JsonValue.ofString(display));
			return JsonValue.ofMap(row);
		}
	}
}
