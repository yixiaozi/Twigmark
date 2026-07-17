package org.freeplane.view.swing.features.reports;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapWorkspaceContextScanner;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.usagestats.MapUsageSummary;
import org.freeplane.features.usagestats.UsageRecord;
import org.freeplane.features.usagestats.UsageStatsManager;
import org.freeplane.view.swing.features.pomodoro.PomodoroSessionManager;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderCalendarBridge;

/**
 * Builds report trees from workspace reminder / todo / pomodoro / usage data.
 */
public final class ReportEngine {
	private static final SimpleDateFormat TIME = new SimpleDateFormat("M/d HH:mm", Locale.CHINA);
	private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

	private ReportEngine() {
	}

	public static ReportNodeSpec generate(final ReportDefinition def, final ReportTimeRange range) {
		if (def == null) {
			return new ReportNodeSpec("（未知报表）", "messagebox_warning");
		}
		final ReportTimeRange effective = range == null ? ReportTimeRange.ofPreset(ReportTimeRange.PRESET_THIS_WEEK)
		        : range;
		try {
			if (ReportCatalog.ID_TODAY_DASHBOARD.equals(def.id)) {
				return todayDashboard(effective);
			}
			if (ReportCatalog.ID_WORK_HOURS.equals(def.id)) {
				return workHours(effective);
			}
			if (ReportCatalog.ID_SCHEDULE.equals(def.id)) {
				return scheduleList(effective);
			}
			if (ReportCatalog.ID_OVERDUE.equals(def.id)) {
				return overdue();
			}
			if (ReportCatalog.ID_URGENT.equals(def.id)) {
				return urgent(effective);
			}
			if (ReportCatalog.ID_RECURRING.equals(def.id)) {
				return recurringLedger();
			}
			if (ReportCatalog.ID_TODOS.equals(def.id)) {
				return todoSummary();
			}
			if (ReportCatalog.ID_FLAGS.equals(def.id)) {
				return flagItems();
			}
			if (ReportCatalog.ID_POMODORO.equals(def.id)) {
				return pomodoroReport(effective);
			}
			if (ReportCatalog.ID_MAP_LOAD.equals(def.id)) {
				return mapLoad(effective);
			}
			if (ReportCatalog.ID_DURATION_BUCKETS.equals(def.id)) {
				return durationBuckets(effective);
			}
			if (ReportCatalog.ID_USAGE.equals(def.id)) {
				return usageReport(effective);
			}
			if (ReportCatalog.ID_PUBLISHED.equals(def.id)) {
				return publishedItems();
			}
			if (ReportCatalog.ID_RECENT.equals(def.id)) {
				return recentChanges(effective);
			}
			return new ReportNodeSpec("未实现：" + def.title, "messagebox_warning");
		}
		catch (Exception e) {
			LogUtils.warn("ReportEngine.generate failed: " + def.id, e);
			return new ReportNodeSpec("报表生成失败：" + e.getMessage(), "messagebox_warning");
		}
	}

	private static ReportNodeSpec root(final ReportDefinition def, final ReportTimeRange range, final String icon) {
		final String rangePart = def.usesTimeRange && range != null ? " · " + range.label : "";
		return new ReportNodeSpec("报表 · " + def.title + rangePart, icon);
	}

	private static ReportNodeSpec todayDashboard(final ReportTimeRange range) {
		final ReportTimeRange today = ReportTimeRange.ofPreset(ReportTimeRange.PRESET_TODAY);
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TODAY_DASHBOARD);
		final ReportNodeSpec root = root(def, today, "idea");
		final List overdue = filterOneTimeBefore(System.currentTimeMillis());
		final List occ = loadOccurrences(today.startMs, today.endMs);
		final long planned = sumPlannedMinutes(occ);
		final long pomoMs = sumPomodoroMs(today.startMs, today.endMs);
		final List todos = MindMapWorkspaceContextScanner.scanAllTodos();

		final ReportNodeSpec summary = root.add("汇总", "info");
		summary.add("逾期提醒 · " + overdue.size() + " 条", "messagebox_warning");
		summary.add("今日安排 · " + occ.size() + " 条 · 计划 " + formatHours(planned), "calendar");
		summary.add("待办总数 · " + todos.size() + " 条", "hourglass");
		summary.add("今日番茄 · " + formatDurationMs(pomoMs), "clock2");

		final ReportNodeSpec overdueNode = root.add("逾期（最多 12 条）", "messagebox_warning");
		addReminderItems(overdueNode, overdue, 12, true);

		final ReportNodeSpec todayNode = root.add("今日安排", "calendar");
		addOccurrences(todayNode, occ, 40);

		return root;
	}

	private static ReportNodeSpec workHours(final ReportTimeRange range) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_WORK_HOURS);
		final ReportNodeSpec root = root(def, range, "clock");
		final List occ = loadOccurrences(range.startMs, range.endMs);
		final long totalMin = sumPlannedMinutes(occ);
		final ReportNodeSpec summary = root.add("汇总", "info");
		summary.add("安排条数 · " + occ.size(), "list");
		summary.add("计划工时合计 · " + formatHours(totalMin), "clock");
		summary.add("有工时条目 · " + countWithMinutes(occ), "full-3");

		final Map byDay = groupOccByDay(occ);
		final ReportNodeSpec byDayNode = root.add("按日", "calendar");
		final List dayKeys = new ArrayList(byDay.keySet());
		Collections.sort(dayKeys);
		for (int i = 0; i < dayKeys.size(); i++) {
			final String day = (String) dayKeys.get(i);
			final List dayList = (List) byDay.get(day);
			final long mins = sumPlannedMinutes(dayList);
			final ReportNodeSpec dayNode = byDayNode.add(day + " · " + formatHours(mins) + " · " + dayList.size() + " 条",
			        "full-" + Math.min(9, Math.max(1, (int) (mins / 60) + 1)));
			addOccurrences(dayNode, dayList, 30);
		}

		final Map byMap = groupOccByMap(occ);
		final ReportNodeSpec byMapNode = root.add("按导图", "folder");
		final List mapEntries = sortedMapEntriesByMinutes(byMap);
		for (int i = 0; i < mapEntries.size(); i++) {
			final Object[] row = (Object[]) mapEntries.get(i);
			final String mapName = (String) row[0];
			final List mapList = (List) row[1];
			final long mins = ((Long) row[2]).longValue();
			final ReportNodeSpec mapNode = byMapNode.add(mapName + " · " + formatHours(mins) + " · " + mapList.size()
			        + " 条", "folder");
			addOccurrences(mapNode, mapList, 40);
		}
		if (occ.isEmpty()) {
			root.add("（该时段无安排）", "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec scheduleList(final ReportTimeRange range) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_SCHEDULE);
		final ReportNodeSpec root = root(def, range, "calendar");
		final List occ = loadOccurrences(range.startMs, range.endMs);
		root.add("共 " + occ.size() + " 条发生", "info");
		final Map byDay = groupOccByDay(occ);
		final List dayKeys = new ArrayList(byDay.keySet());
		Collections.sort(dayKeys);
		for (int i = 0; i < dayKeys.size(); i++) {
			final String day = (String) dayKeys.get(i);
			final List dayList = (List) byDay.get(day);
			final ReportNodeSpec dayNode = root.add(day + " · " + dayList.size() + " 条", "calendar");
			addOccurrences(dayNode, dayList, 80);
		}
		if (occ.isEmpty()) {
			root.add("（该时段无安排）", "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec overdue() {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_OVERDUE);
		final ReportNodeSpec root = root(def, null, "messagebox_warning");
		final long now = System.currentTimeMillis();
		final List items = filterOneTimeBefore(now);
		root.add("逾期 " + items.size() + " 条 · 截止 " + TIME.format(new Date(now)), "info");
		final Map byMap = new TreeMap();
		for (int i = 0; i < items.size(); i++) {
			final MindMapWorkspaceContextScanner.ReminderItem item = (MindMapWorkspaceContextScanner.ReminderItem) items
			        .get(i);
			final String map = mapLabel(item.mapFile);
			List list = (List) byMap.get(map);
			if (list == null) {
				list = new ArrayList();
				byMap.put(map, list);
			}
			list.add(item);
		}
		final Iterator it = byMap.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final ReportNodeSpec mapNode = root.add(entry.getKey() + " · " + ((List) entry.getValue()).size() + " 条",
			        "folder");
			addReminderItems(mapNode, (List) entry.getValue(), 100, true);
		}
		if (items.isEmpty()) {
			root.add("太好了，暂无逾期", "button_ok");
		}
		return root;
	}

	private static ReportNodeSpec urgent(final ReportTimeRange range) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_URGENT);
		final ReportNodeSpec root = root(def, range, "flag-orange");
		final List occ = loadOccurrences(range.startMs, range.endMs);
		final List urgent = new ArrayList();
		for (int i = 0; i < occ.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
			if (ref.jinji >= 1) {
				urgent.add(ref);
			}
		}
		root.add("紧急安排 " + urgent.size() + " 条（jinji≥1）", "info");
		Collections.sort(urgent, occComparator());
		final Map byJinji = new TreeMap(Collections.reverseOrder());
		for (int i = 0; i < urgent.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) urgent.get(i);
			final Integer key = Integer.valueOf(ref.jinji);
			List list = (List) byJinji.get(key);
			if (list == null) {
				list = new ArrayList();
				byJinji.put(key, list);
			}
			list.add(ref);
		}
		final Iterator it = byJinji.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final ReportNodeSpec level = root.add("紧急度 " + entry.getKey() + " · " + ((List) entry.getValue()).size()
			        + " 条", "flag");
			addOccurrences(level, (List) entry.getValue(), 60);
		}
		if (urgent.isEmpty()) {
			root.add("该时段无紧急安排", "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec recurringLedger() {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_RECURRING);
		final ReportNodeSpec root = root(def, null, "prepare");
		final List items = MindMapWorkspaceContextScanner.scanRecurringReminders();
		root.add("周期提醒 " + items.size() + " 条", "info");
		final Map byType = new TreeMap();
		for (int i = 0; i < items.size(); i++) {
			final MindMapWorkspaceContextScanner.ReminderItem item = (MindMapWorkspaceContextScanner.ReminderItem) items
			        .get(i);
			final String type = item.remindType == null || item.remindType.length() == 0 ? "周期" : item.remindType;
			List list = (List) byType.get(type);
			if (list == null) {
				list = new ArrayList();
				byType.put(type, list);
			}
			list.add(item);
		}
		final Iterator it = byType.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final ReportNodeSpec typeNode = root.add(entry.getKey() + " · " + ((List) entry.getValue()).size() + " 条",
			        "prepare");
			addReminderItems(typeNode, (List) entry.getValue(), 80, false);
		}
		if (items.isEmpty()) {
			root.add("（无周期提醒）", "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec todoSummary() {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TODOS);
		final ReportNodeSpec root = root(def, null, "hourglass");
		final List todos = MindMapWorkspaceContextScanner.scanAllTodos();
		root.add("待办 " + todos.size() + " 条", "info");
		final Map byMap = new TreeMap();
		for (int i = 0; i < todos.size(); i++) {
			final MindMapWorkspaceContextScanner.TodoItem item = (MindMapWorkspaceContextScanner.TodoItem) todos.get(i);
			final String map = mapLabel(item.mapFile);
			List list = (List) byMap.get(map);
			if (list == null) {
				list = new ArrayList();
				byMap.put(map, list);
			}
			list.add(item);
		}
		final Iterator it = byMap.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final List list = (List) entry.getValue();
			final ReportNodeSpec mapNode = root.add(entry.getKey() + " · " + list.size() + " 条", "folder");
			for (int i = 0; i < list.size(); i++) {
				final MindMapWorkspaceContextScanner.TodoItem item = (MindMapWorkspaceContextScanner.TodoItem) list
				        .get(i);
				mapNode.add(item.nodeText, "hourglass");
			}
		}
		if (todos.isEmpty()) {
			root.add("（无待办）", "button_ok");
		}
		return root;
	}

	private static ReportNodeSpec flagItems() {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_FLAGS);
		final ReportNodeSpec root = root(def, null, "flag");
		final List flags = MindMapWorkspaceContextScanner.scanFlagItems();
		root.add("红旗 " + flags.size() + " 条", "info");
		final Map byMap = new TreeMap();
		for (int i = 0; i < flags.size(); i++) {
			final MindMapWorkspaceContextScanner.IconItem item = (MindMapWorkspaceContextScanner.IconItem) flags.get(i);
			final String map = mapLabel(item.mapFile);
			List list = (List) byMap.get(map);
			if (list == null) {
				list = new ArrayList();
				byMap.put(map, list);
			}
			list.add(item);
		}
		final Iterator it = byMap.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final List list = (List) entry.getValue();
			final ReportNodeSpec mapNode = root.add(entry.getKey() + " · " + list.size() + " 条", "folder");
			for (int i = 0; i < list.size(); i++) {
				final MindMapWorkspaceContextScanner.IconItem item = (MindMapWorkspaceContextScanner.IconItem) list
				        .get(i);
				mapNode.add(item.nodeText, "flag");
			}
		}
		if (flags.isEmpty()) {
			root.add("（无红旗节点）", "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec pomodoroReport(final ReportTimeRange range) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_POMODORO);
		final ReportNodeSpec root = root(def, range, "clock2");
		final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
		if (mgr == null) {
			root.add("番茄钟未初始化", "messagebox_warning");
			return root;
		}
		final List sessions = mgr.collectSessionsInRange(range.startMs, range.endMs);
		long total = 0L;
		final Map byDay = new TreeMap();
		final Map byNode = new HashMap();
		for (int i = 0; i < sessions.size(); i++) {
			final PomodoroSessionManager.CalendarSession s = (PomodoroSessionManager.CalendarSession) sessions.get(i);
			total += s.focusMs;
			final String day = DAY.format(new Date(s.startMs));
			Long daySum = (Long) byDay.get(day);
			byDay.put(day, Long.valueOf((daySum == null ? 0L : daySum.longValue()) + s.focusMs));
			final String nodeKey = nodeLabel(s.node);
			Long nodeSum = (Long) byNode.get(nodeKey);
			byNode.put(nodeKey, Long.valueOf((nodeSum == null ? 0L : nodeSum.longValue()) + s.focusMs));
		}
		final ReportNodeSpec summary = root.add("汇总", "info");
		summary.add("会话段数 · " + sessions.size(), "list");
		summary.add("专注合计 · " + formatDurationMs(total), "clock2");

		final ReportNodeSpec dayNode = root.add("按日", "calendar");
		final Iterator dayIt = byDay.entrySet().iterator();
		while (dayIt.hasNext()) {
			final Map.Entry e = (Map.Entry) dayIt.next();
			dayNode.add(e.getKey() + " · " + formatDurationMs(((Long) e.getValue()).longValue()), "full-4");
		}

		final List nodeRows = new ArrayList();
		final Iterator nodeIt = byNode.entrySet().iterator();
		while (nodeIt.hasNext()) {
			final Map.Entry e = (Map.Entry) nodeIt.next();
			nodeRows.add(new Object[] { e.getKey(), e.getValue() });
		}
		Collections.sort(nodeRows, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long la = ((Long) ((Object[]) a)[1]).longValue();
				final long lb = ((Long) ((Object[]) b)[1]).longValue();
				return la < lb ? 1 : (la > lb ? -1 : 0);
			}
		});
		final ReportNodeSpec nodeRoot = root.add("按节点（开图内）", "group");
		final int limit = Math.min(40, nodeRows.size());
		for (int i = 0; i < limit; i++) {
			final Object[] row = (Object[]) nodeRows.get(i);
			nodeRoot.add(row[0] + " · " + formatDurationMs(((Long) row[1]).longValue()), "clock2");
		}
		if (sessions.isEmpty()) {
			root.add("该时段无番茄记录（需打开含番茄节点的导图）", "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec mapLoad(final ReportTimeRange range) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_MAP_LOAD);
		final ReportNodeSpec root = root(def, range, "list");
		final List occ = loadOccurrences(range.startMs, range.endMs);
		final MindMapWorkspaceContextScanner.WorkspaceScanResult scan = MindMapWorkspaceContextScanner.scanAll();
		final Map load = new TreeMap();
		for (int i = 0; i < occ.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
			final String map = mapLabel(ref.file);
			int[] row = (int[]) load.get(map);
			if (row == null) {
				row = new int[] { 0, 0, 0 };
				load.put(map, row);
			}
			row[0]++;
			row[2] += Math.max(0, ref.taskTimeMinutes);
		}
		for (int i = 0; i < scan.todos.size(); i++) {
			final MindMapWorkspaceContextScanner.TodoItem item = (MindMapWorkspaceContextScanner.TodoItem) scan.todos
			        .get(i);
			final String map = mapLabel(item.mapFile);
			int[] row = (int[]) load.get(map);
			if (row == null) {
				row = new int[] { 0, 0, 0 };
				load.put(map, row);
			}
			row[1]++;
		}
		root.add("导图数 · " + load.size(), "info");
		final List rows = new ArrayList();
		final Iterator it = load.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry e = (Map.Entry) it.next();
			final int[] row = (int[]) e.getValue();
			rows.add(new Object[] { e.getKey(), Integer.valueOf(row[0]), Integer.valueOf(row[1]),
			        Integer.valueOf(row[2]) });
		}
		Collections.sort(rows, new Comparator() {
			public int compare(final Object a, final Object b) {
				final int ma = ((Integer) ((Object[]) a)[3]).intValue();
				final int mb = ((Integer) ((Object[]) b)[3]).intValue();
				return ma < mb ? 1 : (ma > mb ? -1 : 0);
			}
		});
		for (int i = 0; i < rows.size(); i++) {
			final Object[] row = (Object[]) rows.get(i);
			root.add(row[0] + " · 安排" + row[1] + " · 待办" + row[2] + " · 计划" + formatHours(((Integer) row[3]).intValue()),
			        "folder");
		}
		if (rows.isEmpty()) {
			root.add("（无数据）", "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec durationBuckets(final ReportTimeRange range) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_DURATION_BUCKETS);
		final ReportNodeSpec root = root(def, range, "full-5");
		final List occ = loadOccurrences(range.startMs, range.endMs);
		final int[] buckets = new int[6];
		final String[] labels = { "未设时长", "≤15分", "16–30分", "31–60分", "61–120分", ">120分" };
		final String[] icons = { "full-0", "full-1", "full-3", "full-5", "full-7", "full-9" };
		for (int i = 0; i < occ.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
			final int m = ref.taskTimeMinutes;
			if (m <= 0) {
				buckets[0]++;
			}
			else if (m <= 15) {
				buckets[1]++;
			}
			else if (m <= 30) {
				buckets[2]++;
			}
			else if (m <= 60) {
				buckets[3]++;
			}
			else if (m <= 120) {
				buckets[4]++;
			}
			else {
				buckets[5]++;
			}
		}
		root.add("样本 " + occ.size() + " 条安排", "info");
		for (int i = 0; i < buckets.length; i++) {
			root.add(labels[i] + " · " + buckets[i] + " 条", icons[i]);
		}
		return root;
	}

	private static ReportNodeSpec usageReport(final ReportTimeRange range) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_USAGE);
		final ReportNodeSpec root = root(def, range, "wizard");
		final List records = UsageStatsManager.getInstance().loadAllRecords();
		final Map byMap = new HashMap();
		long totalEffective = 0L;
		int sessionCount = 0;
		for (int i = 0; i < records.size(); i++) {
			final UsageRecord rec = (UsageRecord) records.get(i);
			if (!UsageStatsManager.isSignificantSession(rec)) {
				continue;
			}
			if (rec.getEndTime() < range.startMs || rec.getStartTime() >= range.endMs) {
				continue;
			}
			sessionCount++;
			totalEffective += rec.getEffectiveDurationMs();
			final String path = rec.getMapPath() == null ? "（未知导图）" : rec.getMapPath();
			MapUsageSummary summary = (MapUsageSummary) byMap.get(path);
			if (summary == null) {
				summary = new MapUsageSummary(path);
				byMap.put(path, summary);
			}
			summary.addRecord(rec);
		}
		final ReportNodeSpec summaryNode = root.add("汇总", "info");
		summaryNode.add("会话 · " + sessionCount, "list");
		summaryNode.add("有效时长 · " + formatDurationMs(totalEffective), "wizard");

		final List rows = new ArrayList(byMap.values());
		Collections.sort(rows, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long la = ((MapUsageSummary) a).getEffectiveDurationMs();
				final long lb = ((MapUsageSummary) b).getEffectiveDurationMs();
				return la < lb ? 1 : (la > lb ? -1 : 0);
			}
		});
		final ReportNodeSpec maps = root.add("按导图", "folder");
		final int limit = Math.min(40, rows.size());
		for (int i = 0; i < limit; i++) {
			final MapUsageSummary s = (MapUsageSummary) rows.get(i);
			maps.add(s.getDisplayName() + " · " + formatDurationMs(s.getEffectiveDurationMs()) + " · "
			        + s.getSessionCount() + " 次打开", "folder");
		}
		if (rows.isEmpty()) {
			root.add("该时段无活动记录", "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec publishedItems() {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_PUBLISHED);
		final ReportNodeSpec root = root(def, null, "internet");
		final List items = MindMapWorkspaceContextScanner.scanPublishedItems();
		root.add("发布项 " + items.size() + " 条", "info");
		final Map byMap = new TreeMap();
		for (int i = 0; i < items.size(); i++) {
			final MindMapWorkspaceContextScanner.IconItem item = (MindMapWorkspaceContextScanner.IconItem) items.get(i);
			final String map = mapLabel(item.mapFile);
			List list = (List) byMap.get(map);
			if (list == null) {
				list = new ArrayList();
				byMap.put(map, list);
			}
			list.add(item);
		}
		final Iterator it = byMap.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final List list = (List) entry.getValue();
			final ReportNodeSpec mapNode = root.add(entry.getKey() + " · " + list.size() + " 条", "folder");
			for (int i = 0; i < list.size(); i++) {
				final MindMapWorkspaceContextScanner.IconItem item = (MindMapWorkspaceContextScanner.IconItem) list
				        .get(i);
				mapNode.add(item.nodeText, "internet");
			}
		}
		if (items.isEmpty()) {
			root.add("（无发布项）", "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec recentChanges(final ReportTimeRange range) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_RECENT);
		final ReportNodeSpec root = root(def, range, "up");
		final List all = MindMapWorkspaceContextScanner.scanRecentlyModified(500);
		final List filtered = new ArrayList();
		for (int i = 0; i < all.size(); i++) {
			final MindMapWorkspaceContextScanner.ModifiedItem item = (MindMapWorkspaceContextScanner.ModifiedItem) all
			        .get(i);
			if (range.contains(item.modifiedAt)) {
				filtered.add(item);
			}
		}
		root.add("变更节点 " + filtered.size() + " 条", "info");
		final int limit = Math.min(80, filtered.size());
		for (int i = 0; i < limit; i++) {
			final MindMapWorkspaceContextScanner.ModifiedItem item = (MindMapWorkspaceContextScanner.ModifiedItem) filtered
			        .get(i);
			root.add(TIME.format(new Date(item.modifiedAt)) + "  " + item.nodeText + "  〔" + mapLabel(item.mapFile)
			        + "〕", "up");
		}
		if (filtered.isEmpty()) {
			root.add("该时段无节点变更", "smiley-neutral");
		}
		return root;
	}

	private static List loadOccurrences(final long start, final long end) {
		final ReminderCalendarBridge.LoadBundle bundle = ReminderCalendarBridge.loadBundle(start, end, start, end);
		final List list = new ArrayList(bundle.occurrences);
		Collections.sort(list, occComparator());
		return list;
	}

	private static Comparator occComparator() {
		return new Comparator() {
			public int compare(final Object a, final Object b) {
				final long la = ((ReminderCalendarBridge.OccurrenceRef) a).occurrenceAt;
				final long lb = ((ReminderCalendarBridge.OccurrenceRef) b).occurrenceAt;
				return la < lb ? -1 : (la > lb ? 1 : 0);
			}
		};
	}

	private static List filterOneTimeBefore(final long now) {
		final List all = MindMapWorkspaceContextScanner.scanOneTimeReminders();
		final List out = new ArrayList();
		for (int i = 0; i < all.size(); i++) {
			final MindMapWorkspaceContextScanner.ReminderItem item = (MindMapWorkspaceContextScanner.ReminderItem) all
			        .get(i);
			if (item.remindAt > 0 && item.remindAt < now) {
				out.add(item);
			}
		}
		return out;
	}

	private static long sumPlannedMinutes(final List occ) {
		long sum = 0L;
		for (int i = 0; i < occ.size(); i++) {
			sum += Math.max(0, ((ReminderCalendarBridge.OccurrenceRef) occ.get(i)).taskTimeMinutes);
		}
		return sum;
	}

	private static int countWithMinutes(final List occ) {
		int n = 0;
		for (int i = 0; i < occ.size(); i++) {
			if (((ReminderCalendarBridge.OccurrenceRef) occ.get(i)).taskTimeMinutes > 0) {
				n++;
			}
		}
		return n;
	}

	private static long sumPomodoroMs(final long start, final long end) {
		final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
		if (mgr == null) {
			return 0L;
		}
		final List sessions = mgr.collectSessionsInRange(start, end);
		long total = 0L;
		for (int i = 0; i < sessions.size(); i++) {
			total += ((PomodoroSessionManager.CalendarSession) sessions.get(i)).focusMs;
		}
		return total;
	}

	private static Map groupOccByDay(final List occ) {
		final Map map = new TreeMap();
		for (int i = 0; i < occ.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
			final String day = DAY.format(new Date(ref.occurrenceAt));
			List list = (List) map.get(day);
			if (list == null) {
				list = new ArrayList();
				map.put(day, list);
			}
			list.add(ref);
		}
		return map;
	}

	private static Map groupOccByMap(final List occ) {
		final Map map = new HashMap();
		for (int i = 0; i < occ.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
			final String key = mapLabel(ref.file);
			List list = (List) map.get(key);
			if (list == null) {
				list = new ArrayList();
				map.put(key, list);
			}
			list.add(ref);
		}
		return map;
	}

	private static List sortedMapEntriesByMinutes(final Map byMap) {
		final List rows = new ArrayList();
		final Iterator it = byMap.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry e = (Map.Entry) it.next();
			final List list = (List) e.getValue();
			rows.add(new Object[] { e.getKey(), list, Long.valueOf(sumPlannedMinutes(list)) });
		}
		Collections.sort(rows, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long la = ((Long) ((Object[]) a)[2]).longValue();
				final long lb = ((Long) ((Object[]) b)[2]).longValue();
				return la < lb ? 1 : (la > lb ? -1 : 0);
			}
		});
		return rows;
	}

	private static void addOccurrences(final ReportNodeSpec parent, final List occ, final int limit) {
		final int n = Math.min(limit, occ.size());
		for (int i = 0; i < n; i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
			final StringBuilder sb = new StringBuilder();
			sb.append(TIME.format(new Date(ref.occurrenceAt)));
			if (ref.taskTimeMinutes > 0) {
				sb.append(" · ").append(ref.taskTimeMinutes).append("分");
			}
			if (ref.jinji > 0) {
				sb.append(" · 急").append(ref.jinji);
			}
			if (ref.recurring) {
				sb.append(" · 周期");
			}
			sb.append("  ").append(ref.nodeText == null ? "" : ref.nodeText);
			sb.append("  〔").append(mapLabel(ref.file)).append("〕");
			final String icon = ref.jinji >= 2 ? "flag" : (ref.recurring ? "prepare" : "clock");
			parent.add(sb.toString(), icon);
		}
		if (occ.size() > limit) {
			parent.add("…另有 " + (occ.size() - limit) + " 条", "list");
		}
	}

	private static void addReminderItems(final ReportNodeSpec parent, final List items, final int limit,
	        final boolean showOverdueAge) {
		final long now = System.currentTimeMillis();
		final int n = Math.min(limit, items.size());
		for (int i = 0; i < n; i++) {
			final MindMapWorkspaceContextScanner.ReminderItem item = (MindMapWorkspaceContextScanner.ReminderItem) items
			        .get(i);
			final StringBuilder sb = new StringBuilder();
			sb.append(TIME.format(new Date(item.remindAt)));
			if (showOverdueAge && item.remindAt < now) {
				final long days = Math.max(0L, (now - item.remindAt) / (24L * 60L * 60L * 1000L));
				sb.append(" · 逾期").append(days).append("天");
			}
			if (item.remindType != null && item.remindType.length() > 0) {
				sb.append(" · ").append(item.remindType);
			}
			sb.append("  ").append(item.nodeText == null ? "" : item.nodeText);
			sb.append("  〔").append(mapLabel(item.mapFile)).append("〕");
			parent.add(sb.toString(), item.recurring ? "prepare" : "messagebox_warning");
		}
		if (items.size() > limit) {
			parent.add("…另有 " + (items.size() - limit) + " 条", "list");
		}
	}

	private static String mapLabel(final File file) {
		if (file == null) {
			return "（未命名导图）";
		}
		final String name = file.getName();
		return name == null || name.length() == 0 ? file.getAbsolutePath() : name;
	}

	private static String nodeLabel(final NodeModel node) {
		if (node == null) {
			return "（节点）";
		}
		try {
			final String text = org.freeplane.features.text.TextController.getController().getPlainTextContent(node);
			if (text != null && text.trim().length() > 0) {
				return text.replaceAll("\\s+", " ").trim();
			}
		}
		catch (Exception e) {
		}
		return node.getText() == null ? "（节点）" : node.getText();
	}

	private static String formatHours(final long minutes) {
		if (minutes <= 0) {
			return "0分";
		}
		final long h = minutes / 60;
		final long m = minutes % 60;
		if (h <= 0) {
			return m + "分";
		}
		if (m == 0) {
			return h + "小时";
		}
		return h + "小时" + m + "分";
	}

	private static String formatDurationMs(final long ms) {
		if (ms <= 0) {
			return "0分";
		}
		final long minutes = Math.round(ms / 60000.0);
		return formatHours(minutes);
	}
}
