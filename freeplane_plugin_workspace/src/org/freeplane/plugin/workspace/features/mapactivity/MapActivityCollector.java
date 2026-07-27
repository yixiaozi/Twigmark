package org.freeplane.plugin.workspace.features.mapactivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.view.swing.features.pomodoro.PomodoroAttributes;
import org.freeplane.view.swing.features.pomodoro.PomodoroExtension;
import org.freeplane.view.swing.features.pomodoro.PomodoroFormatter;
import org.freeplane.view.swing.features.pomodoro.PomodoroLog;
import org.freeplane.view.swing.features.pomodoro.PomodoroSessionManager;
import org.freeplane.view.swing.features.pomodoro.PomodoroSessionRecord;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;

/**
 * Walks the open {@link MapModel} and collects todos / reminders / flags / pomodoro.
 */
public final class MapActivityCollector {

	private static final String TODO_ICON = "hourglass";
	private static final ThreadLocal DATE_FMT = new ThreadLocal() {
		protected Object initialValue() {
			return new SimpleDateFormat("M/d HH:mm");
		}
	};

	private MapActivityCollector() {
	}

	public static MapActivitySnapshot collectCurrentMap() {
		return collectCurrentMap(PomodoroRange.TODAY);
	}

	public static MapActivitySnapshot collectCurrentMap(final PomodoroRange range) {
		try {
			return collect(Controller.getCurrentController().getMap(), range);
		}
		catch (final Exception e) {
			return MapActivitySnapshot.empty();
		}
	}

	public static MapActivitySnapshot collect(final MapModel map) {
		return collect(map, PomodoroRange.TODAY);
	}

	public static MapActivitySnapshot collect(final MapModel map, final PomodoroRange range) {
		if (map == null || map.getRootNode() == null) {
			return MapActivitySnapshot.empty();
		}
		final long now = System.currentTimeMillis();
		final List overdue = new ArrayList();
		final List reminders = new ArrayList();
		final List todos = new ArrayList();
		final List flags = new ArrayList();
		walk(map.getRootNode(), now, overdue, reminders, todos, flags);

		Collections.sort(overdue, BY_SORT_KEY_ASC);
		Collections.sort(reminders, BY_SORT_KEY_ASC);
		Collections.sort(todos, BY_TITLE);
		Collections.sort(flags, BY_TITLE);

		boolean hasRunning = false;
		String runningTitle = "";
		final List pomodoro = collectPomodoro(map, now, range != null ? range : PomodoroRange.TODAY);
		for (int i = 0; i < pomodoro.size(); i++) {
			final MapActivityItem item = (MapActivityItem) pomodoro.get(i);
			if (item.live) {
				hasRunning = true;
				runningTitle = item.title;
				break;
			}
		}

		return new MapActivitySnapshot(pomodoro, overdue, reminders, todos, flags, hasRunning, runningTitle);
	}

	private static void walk(final NodeModel node, final long now, final List overdue, final List reminders,
	        final List todos, final List flags) {
		final String title = plain(node);
		if (!isBin(title)) {
			collectReminder(node, title, now, overdue, reminders);
			if (hasTodoIcon(node)) {
				todos.add(new MapActivityItem(MapActivityItem.Kind.TODO, node, title, "", false, false, 0L));
			}
			final String flagName = findFlagIcon(node);
			if (flagName != null) {
				flags.add(new MapActivityItem(MapActivityItem.Kind.FLAG, node, title, flagLabel(flagName), false,
				        false, 0L));
			}
		}
		final List children = node.getChildren();
		for (int i = 0; i < children.size(); i++) {
			walk((NodeModel) children.get(i), now, overdue, reminders, todos, flags);
		}
	}

	private static void collectReminder(final NodeModel node, final String title, final long now, final List overdue,
	        final List reminders) {
		final ReminderExtension ext = ReminderExtension.getExtension(node);
		if (ext == null) {
			return;
		}
		final long at = ext.getRemindUserAt();
		if (at <= 0) {
			return;
		}
		final boolean recurring = isRecurring(ext);
		final String meta = formatTime(at) + (recurring ? " 循环" : "");
		if (!recurring && at < now) {
			overdue.add(new MapActivityItem(MapActivityItem.Kind.OVERDUE, node, title, meta, false, true, at));
		}
		else {
			reminders.add(new MapActivityItem(MapActivityItem.Kind.REMINDER, node, title, meta, false, false, at));
		}
	}

	private static List collectPomodoro(final MapModel map, final long now, final PomodoroRange range) {
		final List result = new ArrayList();
		final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
		NodeModel running = null;
		if (mgr != null) {
			running = mgr.getRunningNode();
			if (running != null && running.getMap() != map) {
				running = null;
			}
		}
		final List nodes;
		if (mgr != null) {
			nodes = mgr.listCurrentMapPomodoroNodes();
		}
		else {
			nodes = Collections.EMPTY_LIST;
		}
		final long since = range.sinceMs(now);
		for (int i = 0; i < nodes.size(); i++) {
			final NodeModel node = (NodeModel) nodes.get(i);
			final PomodoroExtension ext = PomodoroAttributes.read(node);
			if (ext == null || !ext.isEnabled()) {
				continue;
			}
			final String title = plain(node);
			if (isBin(title)) {
				continue;
			}
			final boolean live = running != null && running.equals(node);
			final long rangeFocus = focusInRange(ext, now, since);
			if (!live && range != PomodoroRange.ALL && rangeFocus <= 0L) {
				continue;
			}
			final String state = ext.getState();
			final long displayMs = range == PomodoroRange.ALL ? ext.liveTotalMs(now) : rangeFocus;
			final String meta = formatPomodoroMeta(ext, now, since, live, range, rangeFocus);
			final long sort = live ? 0L : (PomodoroExtension.STATE_PAUSED.equals(state) ? 1L : (Long.MAX_VALUE - displayMs));
			result.add(new MapActivityItem(MapActivityItem.Kind.POMODORO, node, title, meta, live, false, sort));
		}
		Collections.sort(result, BY_SORT_KEY_ASC);
		return result;
	}

	/**
	 * Format live/paused pomodoro meta without walking the map — used by the 1s overlay clock tick.
	 */
	public static String formatLivePomodoroMeta(final NodeModel node, final PomodoroRange range) {
		if (node == null) {
			return "";
		}
		final PomodoroExtension ext = PomodoroAttributes.read(node);
		if (ext == null || !ext.isEnabled()) {
			return "";
		}
		final long now = System.currentTimeMillis();
		final PomodoroRange effective = range != null ? range : PomodoroRange.TODAY;
		final long since = effective.sinceMs(now);
		final long rangeFocus = focusInRange(ext, now, since);
		return formatPomodoroMeta(ext, now, since, true, effective, rangeFocus);
	}

	private static String formatPomodoroMeta(final PomodoroExtension ext, final long now, final long since,
	        final boolean live, final PomodoroRange range, final long rangeFocus) {
		final String state = ext.getState();
		final long[] window = bestTimeWindow(ext, now, since, live);
		final long displayMs = range == PomodoroRange.ALL ? ext.liveTotalMs(now) : rangeFocus;
		String meta = formatTimeWindow(window[0], window[1], now);
		if (meta.length() == 0) {
			meta = PomodoroFormatter.formatDuration(displayMs);
		}
		else {
			meta = meta + " · " + PomodoroFormatter.formatDuration(displayMs);
		}
		if (live || PomodoroExtension.STATE_RUNNING.equals(state)) {
			meta = "进行中 " + meta;
		}
		else if (PomodoroExtension.STATE_PAUSED.equals(state)) {
			meta = "暂停 " + meta;
		}
		return meta;
	}

	private static long[] bestTimeWindow(final PomodoroExtension ext, final long now, final long since,
	        final boolean live) {
		if (live || PomodoroExtension.STATE_RUNNING.equals(ext.getState())
		        || PomodoroExtension.STATE_PAUSED.equals(ext.getState())) {
			final long start = ext.getSessionAt() > 0 ? ext.getSessionAt() : ext.getStartedAt();
			if (start > 0) {
				return new long[] { start, now };
			}
		}
		final List records = PomodoroLog.decode(ext.getLog());
		PomodoroSessionRecord best = null;
		for (int i = 0; i < records.size(); i++) {
			final PomodoroSessionRecord rec = (PomodoroSessionRecord) records.get(i);
			if (since > 0 && rec.endMs < since) {
				continue;
			}
			if (best == null || rec.endMs > best.endMs) {
				best = rec;
			}
		}
		if (best != null) {
			return new long[] { best.startMs, best.endMs };
		}
		return new long[] { 0L, 0L };
	}

	private static String formatTimeWindow(final long startMs, final long endMs, final long now) {
		if (startMs <= 0) {
			return "";
		}
		final long end = endMs > 0 ? endMs : now;
		try {
			final SimpleDateFormat fmt = new SimpleDateFormat("H:mm");
			return fmt.format(new Date(startMs)) + "–" + fmt.format(new Date(end));
		}
		catch (final Exception e) {
			return "";
		}
	}

	private static long focusInRange(final PomodoroExtension ext, final long now, final long since) {
		long sum = PomodoroLog.sumFocusSince(PomodoroLog.decode(ext.getLog()), since);
		if (PomodoroExtension.STATE_RUNNING.equals(ext.getState())
		        || PomodoroExtension.STATE_PAUSED.equals(ext.getState())) {
			final long liveSeg = ext.liveSegmentMs(now);
			final long anchor = ext.getSessionAt() > 0 ? ext.getSessionAt() : ext.getStartedAt();
			if (liveSeg > 0 && (since <= 0 || anchor >= since || now >= since)) {
				sum += liveSeg;
			}
		}
		return sum;
	}

	private static boolean isRecurring(final ReminderExtension ext) {
		if (ext.getPeriod() > 0) {
			return true;
		}
		final String unit = ext.getPeriodUnitAsString();
		return unit != null && unit.trim().length() > 0;
	}

	private static boolean hasTodoIcon(final NodeModel node) {
		final Collection icons = IconController.getController().getIcons(node);
		for (final Object iconObj : icons) {
			final MindIcon icon = (MindIcon) iconObj;
			if (TODO_ICON.equalsIgnoreCase(icon.getName())) {
				return true;
			}
		}
		return false;
	}

	private static String findFlagIcon(final NodeModel node) {
		final Collection icons = IconController.getController().getIcons(node);
		for (final Object iconObj : icons) {
			final MindIcon icon = (MindIcon) iconObj;
			final String name = icon.getName();
			if (name == null) {
				continue;
			}
			if ("flag".equalsIgnoreCase(name) || name.toLowerCase().startsWith("flag-")) {
				return name;
			}
		}
		return null;
	}

	private static String flagLabel(final String iconName) {
		if (iconName == null || "flag".equalsIgnoreCase(iconName)) {
			return "红旗";
		}
		final String lower = iconName.toLowerCase();
		if (lower.startsWith("flag-")) {
			return "旗·" + iconName.substring(5);
		}
		return iconName;
	}

	private static String plain(final NodeModel node) {
		if (node == null || node.getText() == null) {
			return "";
		}
		return HtmlUtils.htmlToPlain(node.getText()).replaceAll("\\s+", " ").trim();
	}

	private static boolean isBin(final String title) {
		return "bin".equalsIgnoreCase(title);
	}

	private static String formatTime(final long millis) {
		try {
			return ((SimpleDateFormat) DATE_FMT.get()).format(new Date(millis));
		}
		catch (final Exception e) {
			return String.valueOf(millis);
		}
	}

	private static final Comparator BY_SORT_KEY_ASC = new Comparator() {
		public int compare(final Object a, final Object b) {
			final long ka = ((MapActivityItem) a).sortKey;
			final long kb = ((MapActivityItem) b).sortKey;
			return ka < kb ? -1 : (ka == kb ? 0 : 1);
		}
	};

	private static final Comparator BY_TITLE = new Comparator() {
		public int compare(final Object a, final Object b) {
			return ((MapActivityItem) a).title.compareToIgnoreCase(((MapActivityItem) b).title);
		}
	};
}
