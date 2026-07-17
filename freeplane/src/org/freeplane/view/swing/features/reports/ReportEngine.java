package org.freeplane.view.swing.features.reports;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
		return generate(def, new ReportQuery(range, "", ""));
	}

	public static ReportNodeSpec generate(final ReportDefinition def, final ReportQuery query) {
		if (def == null) {
			return new ReportNodeSpec("（未知报表）", "messagebox_warning");
		}
		final ReportQuery q = query == null ? new ReportQuery(null, "", "") : query;
		try {
			if (ReportCatalog.ID_TIME_BLOCK.equals(def.id)) {
				return timeBlockReport(q);
			}
			if (ReportCatalog.ID_USE_TIME.equals(def.id)) {
				return useTimeReport(q);
			}
			if (ReportCatalog.ID_KEYBOARD.equals(def.id)) {
				return keyboardReport(q);
			}
			if (ReportCatalog.ID_TREND.equals(def.id)) {
				return trendReport(q);
			}
			if (ReportCatalog.ID_TARGET.equals(def.id)) {
				return targetReport(q);
			}
			if (ReportCatalog.ID_MINDMAP_ANALYSIS.equals(def.id)) {
				return mindmapAnalysisReport(q);
			}
			if (ReportCatalog.ID_TODAY_DASHBOARD.equals(def.id)) {
				return todayDashboard(q.range);
			}
			if (ReportCatalog.ID_WORK_HOURS.equals(def.id)) {
				return workHours(q);
			}
			if (ReportCatalog.ID_SCHEDULE.equals(def.id)) {
				return scheduleList(q);
			}
			if (ReportCatalog.ID_OVERDUE.equals(def.id)) {
				return overdue(q);
			}
			if (ReportCatalog.ID_URGENT.equals(def.id)) {
				return urgent(q);
			}
			if (ReportCatalog.ID_RECURRING.equals(def.id)) {
				return recurringLedger(q);
			}
			if (ReportCatalog.ID_TODOS.equals(def.id)) {
				return todoSummary(q);
			}
			if (ReportCatalog.ID_FLAGS.equals(def.id)) {
				return flagItems(q);
			}
			if (ReportCatalog.ID_POMODORO.equals(def.id)) {
				return pomodoroReport(q);
			}
			if (ReportCatalog.ID_MAP_LOAD.equals(def.id)) {
				return mapLoad(q);
			}
			if (ReportCatalog.ID_DURATION_BUCKETS.equals(def.id)) {
				return durationBuckets(q);
			}
			if (ReportCatalog.ID_PUBLISHED.equals(def.id)) {
				return publishedItems(q);
			}
			if (ReportCatalog.ID_RECENT.equals(def.id)) {
				return recentChanges(q);
			}
			return new ReportNodeSpec("未实现：" + def.title, "messagebox_warning");
		}
		catch (Exception e) {
			LogUtils.warn("ReportEngine.generate failed: " + def.id, e);
			return new ReportNodeSpec("报表生成失败：" + e.getMessage(), "messagebox_warning");
		}
	}

	private static ReportNodeSpec root(final ReportDefinition def, final ReportQuery q, final String icon) {
		final StringBuilder title = new StringBuilder("报表 · ").append(def.title);
		if (def.usesTimeRange && q != null && q.range != null) {
			title.append(" · ").append(q.range.label);
		}
		if (q != null && q.includeKeyword.length() > 0) {
			title.append(" · 含「").append(q.includeKeyword).append("」");
		}
		if (q != null && q.excludeKeyword.length() > 0) {
			title.append(" · 除「").append(q.excludeKeyword).append("」");
		}
		return new ReportNodeSpec(title.toString(), icon);
	}

	private static ReportNodeSpec root(final ReportDefinition def, final ReportTimeRange range, final String icon) {
		return root(def, new ReportQuery(range, "", ""), icon);
	}

	/** DocearReminder TimeBlockReport：按分类汇总计划工时占比. */
	private static ReportNodeSpec timeBlockReport(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TIME_BLOCK);
		final ReportNodeSpec root = root(def, q, "clock");
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		long total = sumPlannedMinutes(occ);
		root.add("合计计划工时 · " + formatHours(total) + " · " + occ.size() + " 条", "info");

		final Map byCat = new HashMap();
		for (int i = 0; i < occ.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
			final String cat = categoryOf(ref);
			long[] row = (long[]) byCat.get(cat);
			if (row == null) {
				row = new long[] { 0L, 0L };
				byCat.put(cat, row);
			}
			row[0] += Math.max(0, ref.taskTimeMinutes);
			row[1]++;
		}
		final List rows = sortedLongPairs(byCat, 0);
		final ReportNodeSpec pie = root.add("分类占比（类时间块饼图）", "full-7");
		for (int i = 0; i < rows.size(); i++) {
			final Object[] row = (Object[]) rows.get(i);
			final String cat = (String) row[0];
			final long mins = ((long[]) row[1])[0];
			final long count = ((long[]) row[1])[1];
			final int pct = total <= 0 ? 0 : (int) Math.round(mins * 100.0 / total);
			final ReportNodeSpec catNode = pie.add(cat + " · " + formatHours(mins) + " · " + pct + "% · " + count + "条",
			        "full-" + Math.min(9, Math.max(1, pct / 10)));
			final List catOcc = new ArrayList();
			for (int j = 0; j < occ.size(); j++) {
				final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(j);
				if (cat.equals(categoryOf(ref))) {
					catOcc.add(ref);
				}
			}
			addOccurrences(catNode, catOcc, 25);
		}
		if (occ.isEmpty()) {
			root.add("该时段无安排（原版统计 mindmap=TimeBlock；此处用全部提醒计划工时）", "smiley-neutral");
		}
		return root;
	}

	/** DocearReminder UseTime：使用时长按小时 / 导图. */
	private static ReportNodeSpec useTimeReport(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_USE_TIME);
		final ReportNodeSpec root = root(def, q, "wizard");
		final List records = UsageStatsManager.getInstance().loadAllRecords();
		final Map byHour = new TreeMap();
		final Map byMap = new HashMap();
		long totalEffective = 0L;
		int sessionCount = 0;
		for (int i = 0; i < records.size(); i++) {
			final UsageRecord rec = (UsageRecord) records.get(i);
			if (!UsageStatsManager.isSignificantSession(rec)) {
				continue;
			}
			if (rec.getEndTime() < q.range.startMs || rec.getStartTime() >= q.range.endMs) {
				continue;
			}
			final String path = rec.getMapPath() == null ? "" : rec.getMapPath();
			final String mapName = path.length() == 0 ? "（未知）" : new File(path).getName();
			if (!q.matches(mapName, path, "")) {
				continue;
			}
			sessionCount++;
			final long eff = rec.getEffectiveDurationMs();
			totalEffective += eff;
			final Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(rec.getStartTime());
			final Integer hour = Integer.valueOf(cal.get(Calendar.HOUR_OF_DAY));
			Long hourSum = (Long) byHour.get(hour);
			byHour.put(hour, Long.valueOf((hourSum == null ? 0L : hourSum.longValue()) + eff));
			MapUsageSummary summary = (MapUsageSummary) byMap.get(path.length() == 0 ? mapName : path);
			if (summary == null) {
				summary = new MapUsageSummary(path.length() == 0 ? mapName : path);
				byMap.put(path.length() == 0 ? mapName : path, summary);
			}
			summary.addRecord(rec);
		}
		final ReportNodeSpec summaryNode = root.add("汇总", "info");
		summaryNode.add("会话 · " + sessionCount, "list");
		summaryNode.add("有效时长 · " + formatDurationMs(totalEffective), "wizard");

		final ReportNodeSpec hourNode = root.add("按时段（类使用记录柱状）", "clock");
		final Iterator hourIt = byHour.entrySet().iterator();
		while (hourIt.hasNext()) {
			final Map.Entry e = (Map.Entry) hourIt.next();
			hourNode.add(String.format("%02d:00", ((Integer) e.getKey()).intValue()) + " · "
			        + formatDurationMs(((Long) e.getValue()).longValue()), "full-2");
		}
		final List mapRows = new ArrayList(byMap.values());
		Collections.sort(mapRows, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long la = ((MapUsageSummary) a).getEffectiveDurationMs();
				final long lb = ((MapUsageSummary) b).getEffectiveDurationMs();
				return la < lb ? 1 : (la > lb ? -1 : 0);
			}
		});
		final ReportNodeSpec maps = root.add("按导图/窗体", "folder");
		final int limit = Math.min(40, mapRows.size());
		for (int i = 0; i < limit; i++) {
			final MapUsageSummary s = (MapUsageSummary) mapRows.get(i);
			maps.add(s.getDisplayName() + " · " + formatDurationMs(s.getEffectiveDurationMs()) + " · "
			        + s.getSessionCount() + " 次", "folder");
		}
		if (sessionCount == 0) {
			root.add("该时段无使用记录（对应原版 UsedTimer）", "smiley-neutral");
		}
		return root;
	}

	/** DocearReminder KeyHours：扫描 key.txt. */
	private static ReportNodeSpec keyboardReport(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_KEYBOARD);
		final ReportNodeSpec root = root(def, q, "pencil");
		final List keyFiles = findKeyLogFiles();
		if (keyFiles.isEmpty()) {
			root.add("未找到 key.txt（原版键盘分析数据源）", "messagebox_warning");
			root.add("可将 DocearReminder 的 key.txt 放到用户目录或工作区后重试", "info");
			return root;
		}
		final Map byDay = new TreeMap();
		final Map byHour = new TreeMap();
		long totalKeys = 0L;
		for (int f = 0; f < keyFiles.size(); f++) {
			final File file = (File) keyFiles.get(f);
			root.add("日志 · " + file.getAbsolutePath(), "attach");
			try {
				final java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
				        new java.io.FileInputStream(file), "UTF-8"));
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.length() < 16) {
						continue;
					}
					Date dt = null;
					String key = "";
					try {
						dt = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA).parse(line.substring(0, 16));
						key = line.length() > 17 ? line.substring(17).trim() : "";
					}
					catch (Exception e1) {
						try {
							dt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).parse(line.substring(0, 16));
							key = line.length() > 17 ? line.substring(17).trim() : "";
						}
						catch (Exception e2) {
							continue;
						}
					}
					if (dt == null || !q.range.contains(dt.getTime())) {
						continue;
					}
					if (!q.matches(key, file.getName(), "")) {
						continue;
					}
					totalKeys++;
					final String day = DAY.format(dt);
					Long dayCount = (Long) byDay.get(day);
					byDay.put(day, Long.valueOf((dayCount == null ? 0L : dayCount.longValue()) + 1L));
					final Calendar cal = Calendar.getInstance();
					cal.setTime(dt);
					final Integer hour = Integer.valueOf(cal.get(Calendar.HOUR_OF_DAY));
					Long hourCount = (Long) byHour.get(hour);
					byHour.put(hour, Long.valueOf((hourCount == null ? 0L : hourCount.longValue()) + 1L));
				}
				reader.close();
			}
			catch (Exception e) {
				root.add("读取失败：" + e.getMessage(), "messagebox_warning");
			}
		}
		root.add("击键合计 · " + totalKeys, "info");
		final ReportNodeSpec dayNode = root.add("按日", "calendar");
		final Iterator dayIt = byDay.entrySet().iterator();
		while (dayIt.hasNext()) {
			final Map.Entry e = (Map.Entry) dayIt.next();
			dayNode.add(e.getKey() + " · " + e.getValue() + " 次", "pencil");
		}
		final ReportNodeSpec hourNode = root.add("按时段", "clock");
		final Iterator hourIt = byHour.entrySet().iterator();
		while (hourIt.hasNext()) {
			final Map.Entry e = (Map.Entry) hourIt.next();
			hourNode.add(String.format("%02d:00", ((Integer) e.getKey()).intValue()) + " · " + e.getValue() + " 次",
			        "full-1");
		}
		return root;
	}

	/** DocearReminder TimeBlockTrend：每日走势与均值. */
	private static ReportNodeSpec trendReport(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TREND);
		final ReportNodeSpec root = root(def, q, "up");
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		final Map plannedByDay = new TreeMap();
		for (int i = 0; i < occ.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
			final String day = DAY.format(new Date(ref.occurrenceAt));
			Long sum = (Long) plannedByDay.get(day);
			plannedByDay.put(day, Long.valueOf((sum == null ? 0L : sum.longValue()) + Math.max(0, ref.taskTimeMinutes)));
		}
		final Map pomoByDay = new TreeMap();
		final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
		if (mgr != null) {
			final List sessions = mgr.collectSessionsInRange(q.range.startMs, q.range.endMs);
			for (int i = 0; i < sessions.size(); i++) {
				final PomodoroSessionManager.CalendarSession s = (PomodoroSessionManager.CalendarSession) sessions
				        .get(i);
				final String day = DAY.format(new Date(s.startMs));
				Long sum = (Long) pomoByDay.get(day);
				pomoByDay.put(day, Long.valueOf((sum == null ? 0L : sum.longValue()) + s.focusMs));
			}
		}
		final long spanDays = Math.max(1L, (q.range.endMs - q.range.startMs) / (24L * 60L * 60L * 1000L));
		final long plannedTotal = sumMapLong(plannedByDay);
		final long pomoTotal = sumMapLong(pomoByDay);
		final ReportNodeSpec summary = root.add("汇总", "info");
		summary.add("总计划工时 · " + formatHours(plannedTotal), "clock");
		summary.add("平均每天（总天数 " + spanDays + "）· " + formatHours(plannedTotal / spanDays), "full-3");
		summary.add("有记录天数 · " + plannedByDay.size() + " · 日均 "
		        + formatHours(plannedByDay.isEmpty() ? 0 : plannedTotal / plannedByDay.size()), "full-5");
		summary.add("番茄合计 · " + formatDurationMs(pomoTotal), "clock2");

		final ReportNodeSpec planned = root.add("每日计划工时", "calendar");
		final Iterator it = plannedByDay.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry e = (Map.Entry) it.next();
			planned.add(e.getKey() + " · " + formatHours(((Long) e.getValue()).longValue()), "up");
		}
		final ReportNodeSpec pomo = root.add("每日番茄", "clock2");
		final Iterator pit = pomoByDay.entrySet().iterator();
		while (pit.hasNext()) {
			final Map.Entry e = (Map.Entry) pit.next();
			pomo.add(e.getKey() + " · " + formatDurationMs(((Long) e.getValue()).longValue()), "up");
		}
		if (plannedByDay.isEmpty() && pomoByDay.isEmpty()) {
			root.add("该时段无趋势数据", "smiley-neutral");
		}
		return root;
	}

	/** DocearReminder Target：扫描「目标」节点并匹配安排. */
	private static ReportNodeSpec targetReport(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TARGET);
		final ReportNodeSpec root = root(def, q, "launch");
		final List goals = discoverGoalNames();
		root.add("发现目标 · " + goals.size() + " 个（扫描含「目标」的父节点子项）", "info");
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		if (goals.isEmpty()) {
			root.add("未找到「目标」节点；请在导图中建「目标」父节点并添加子目标", "messagebox_warning");
			root.add("回退：按导图分类展示计划工时", "info");
			return timeBlockReport(q);
		}
		long matchedMinutes = 0L;
		for (int g = 0; g < goals.size(); g++) {
			final String goal = (String) goals.get(g);
			if (!q.matches(goal, "", "")) {
				continue;
			}
			long mins = 0L;
			int count = 0;
			final List matched = new ArrayList();
			for (int i = 0; i < occ.size(); i++) {
				final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
				final String text = ref.nodeText == null ? "" : ref.nodeText;
				final String path = ref.file == null ? "" : ref.file.getAbsolutePath();
				if (text.indexOf(goal) >= 0 || path.indexOf(goal) >= 0 || categoryOf(ref).indexOf(goal) >= 0) {
					mins += Math.max(0, ref.taskTimeMinutes);
					count++;
					matched.add(ref);
				}
			}
			matchedMinutes += mins;
			final ReportNodeSpec goalNode = root.add(goal + " · " + formatHours(mins) + " · " + count + " 条", "launch");
			addOccurrences(goalNode, matched, 20);
		}
		root.add("目标相关计划工时合计 · " + formatHours(matchedMinutes), "clock");
		return root;
	}

	/** DocearReminder MindMapDataReport：节点修改分布. */
	private static ReportNodeSpec mindmapAnalysisReport(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_MINDMAP_ANALYSIS);
		final ReportNodeSpec root = root(def, q, "folder");
		final List all = MindMapWorkspaceContextScanner.scanRecentlyModified(2000);
		final List filtered = new ArrayList();
		for (int i = 0; i < all.size(); i++) {
			final MindMapWorkspaceContextScanner.ModifiedItem item = (MindMapWorkspaceContextScanner.ModifiedItem) all
			        .get(i);
			if (!q.range.contains(item.modifiedAt)) {
				continue;
			}
			if (!q.matches(item.nodeText, mapLabel(item.mapFile), "")) {
				continue;
			}
			filtered.add(item);
		}
		root.add("变更节点 · " + filtered.size(), "info");

		final Map byDay = new TreeMap();
		final Map byHour = new TreeMap();
		final Map byWeekday = new TreeMap();
		final Map byMap = new TreeMap();
		final String[] weekNames = { "周日", "周一", "周二", "周三", "周四", "周五", "周六" };
		for (int i = 0; i < filtered.size(); i++) {
			final MindMapWorkspaceContextScanner.ModifiedItem item = (MindMapWorkspaceContextScanner.ModifiedItem) filtered
			        .get(i);
			final String day = DAY.format(new Date(item.modifiedAt));
			byDay.put(day, Integer.valueOf(intVal(byDay.get(day)) + 1));
			final Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(item.modifiedAt);
			final Integer hour = Integer.valueOf(cal.get(Calendar.HOUR_OF_DAY));
			byHour.put(hour, Integer.valueOf(intVal(byHour.get(hour)) + 1));
			final String wd = weekNames[cal.get(Calendar.DAY_OF_WEEK) - 1];
			byWeekday.put(wd, Integer.valueOf(intVal(byWeekday.get(wd)) + 1));
			final String map = mapLabel(item.mapFile);
			byMap.put(map, Integer.valueOf(intVal(byMap.get(map)) + 1));
		}

		final ReportNodeSpec dayNode = root.add("按日（柱状）", "calendar");
		addIntMap(dayNode, byDay, "full-4");
		final ReportNodeSpec hourNode = root.add("按时段", "clock");
		final Iterator hit = byHour.entrySet().iterator();
		while (hit.hasNext()) {
			final Map.Entry e = (Map.Entry) hit.next();
			hourNode.add(String.format("%02d:00", ((Integer) e.getKey()).intValue()) + " · " + e.getValue() + " 次",
			        "full-2");
		}
		final ReportNodeSpec weekNode = root.add("按星期", "prepare");
		addIntMap(weekNode, byWeekday, "full-3");
		final ReportNodeSpec mapNode = root.add("按导图（饼图式）", "folder");
		final List mapRows = new ArrayList();
		final Iterator mit = byMap.entrySet().iterator();
		while (mit.hasNext()) {
			final Map.Entry e = (Map.Entry) mit.next();
			mapRows.add(new Object[] { e.getKey(), e.getValue() });
		}
		Collections.sort(mapRows, new Comparator() {
			public int compare(final Object a, final Object b) {
				final int ia = ((Integer) ((Object[]) a)[1]).intValue();
				final int ib = ((Integer) ((Object[]) b)[1]).intValue();
				return ia < ib ? 1 : (ia > ib ? -1 : 0);
			}
		});
		final int total = filtered.size();
		for (int i = 0; i < mapRows.size(); i++) {
			final Object[] row = (Object[]) mapRows.get(i);
			final int c = ((Integer) row[1]).intValue();
			final int pct = total <= 0 ? 0 : (int) Math.round(c * 100.0 / total);
			mapNode.add(row[0] + " · " + c + " 次 · " + pct + "%", "folder");
		}
		if (filtered.isEmpty()) {
			root.add("该时段无节点变更", "smiley-neutral");
		}
		return root;
	}

	private static void addIntMap(final ReportNodeSpec parent, final Map map, final String icon) {
		final Iterator it = map.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry e = (Map.Entry) it.next();
			parent.add(e.getKey() + " · " + e.getValue() + " 次", icon);
		}
	}

	private static int intVal(final Object o) {
		return o == null ? 0 : ((Integer) o).intValue();
	}

	private static long sumMapLong(final Map map) {
		long sum = 0L;
		final Iterator it = map.values().iterator();
		while (it.hasNext()) {
			sum += ((Long) it.next()).longValue();
		}
		return sum;
	}

	private static List sortedLongPairs(final Map byCat, final int sortIndex) {
		final List rows = new ArrayList();
		final Iterator it = byCat.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry e = (Map.Entry) it.next();
			rows.add(new Object[] { e.getKey(), e.getValue() });
		}
		Collections.sort(rows, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long la = ((long[]) ((Object[]) a)[1])[sortIndex];
				final long lb = ((long[]) ((Object[]) b)[1])[sortIndex];
				return la < lb ? 1 : (la > lb ? -1 : 0);
			}
		});
		return rows;
	}

	private static String categoryOf(final ReminderCalendarBridge.OccurrenceRef ref) {
		if (ref == null || ref.file == null) {
			return "（未分类）";
		}
		final File parent = ref.file.getParentFile();
		if (parent != null && parent.getName() != null && parent.getName().length() > 0) {
			return parent.getName();
		}
		return mapLabel(ref.file);
	}

	private static List filterOcc(final List occ, final ReportQuery q) {
		if (q == null || (q.includeKeyword.length() == 0 && q.excludeKeyword.length() == 0)) {
			return occ;
		}
		final List out = new ArrayList();
		for (int i = 0; i < occ.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
			final String path = ref.file == null ? "" : ref.file.getAbsolutePath();
			if (q.matches(ref.nodeText, path, categoryOf(ref))) {
				out.add(ref);
			}
		}
		return out;
	}

	private static List discoverGoalNames() {
		final List goals = new ArrayList();
		final List seen = new ArrayList();
		try {
			final org.freeplane.features.ui.IMapViewManager views = org.freeplane.features.mode.Controller
			        .getCurrentController().getMapViewManager();
			final java.util.Map maps = views.getMaps();
			if (maps != null) {
				final Iterator it = maps.values().iterator();
				while (it.hasNext()) {
					final org.freeplane.features.map.MapModel map = (org.freeplane.features.map.MapModel) it.next();
					if (map != null && map.getRootNode() != null) {
						collectGoals(map.getRootNode(), false, goals, seen);
					}
				}
			}
		}
		catch (Exception e) {
		}
		return goals;
	}

	private static void collectGoals(final NodeModel node, final boolean underGoal, final List goals,
	        final List seen) {
		if (node == null) {
			return;
		}
		String text = "";
		try {
			text = org.freeplane.features.text.TextController.getController().getPlainTextContent(node);
		}
		catch (Exception e) {
			text = node.getText() == null ? "" : node.getText();
		}
		if (text == null) {
			text = "";
		}
		text = text.replaceAll("\\s+", " ").trim();
		final boolean isGoalParent = text.equals("目标") || text.startsWith("目标");
		if (underGoal && text.length() > 0 && !text.equals("目标") && !seen.contains(text)) {
			seen.add(text);
			goals.add(text);
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			collectGoals((NodeModel) node.getChildAt(i), underGoal || isGoalParent, goals, seen);
		}
	}

	private static List findKeyLogFiles() {
		final List out = new ArrayList();
		final List roots = new ArrayList();
		try {
			roots.add(new File(org.freeplane.core.util.Compat.getApplicationUserDirectory()));
		}
		catch (Exception e) {
		}
		try {
			final File project = org.freeplane.core.util.MindMapDataRootResolver.getProjectDataDirectory();
			if (project != null) {
				roots.add(project);
			}
		}
		catch (Exception e) {
		}
		try {
			final File[] scanRoots = org.freeplane.core.util.MindMapDataRootResolver.getScanRoots();
			if (scanRoots != null) {
				for (int i = 0; i < scanRoots.length; i++) {
					if (scanRoots[i] != null) {
						roots.add(scanRoots[i]);
					}
				}
			}
		}
		catch (Exception e) {
		}
		for (int i = 0; i < roots.size(); i++) {
			collectNamedFiles((File) roots.get(i), "key.txt", out, 0);
		}
		return out;
	}

	private static void collectNamedFiles(final File dir, final String name, final List out, final int depth) {
		if (dir == null || !dir.isDirectory() || depth > 4 || out.size() > 20) {
			return;
		}
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (int i = 0; i < children.length; i++) {
			final File child = children[i];
			if (child.isFile() && name.equalsIgnoreCase(child.getName())) {
				out.add(child);
			}
			else if (child.isDirectory() && !child.getName().startsWith(".")) {
				collectNamedFiles(child, name, out, depth + 1);
			}
		}
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

	private static ReportNodeSpec workHours(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_WORK_HOURS);
		final ReportNodeSpec root = root(def, q, "clock");
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
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

	private static ReportNodeSpec scheduleList(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_SCHEDULE);
		final ReportNodeSpec root = root(def, q, "calendar");
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
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

	private static ReportNodeSpec overdue(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_OVERDUE);
		final ReportNodeSpec root = root(def, q, "messagebox_warning");
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

	private static ReportNodeSpec urgent(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_URGENT);
		final ReportNodeSpec root = root(def, q, "flag-orange");
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
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

	private static ReportNodeSpec recurringLedger(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_RECURRING);
		final ReportNodeSpec root = root(def, q, "prepare");
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

	private static ReportNodeSpec todoSummary(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TODOS);
		final ReportNodeSpec root = root(def, q, "hourglass");
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

	private static ReportNodeSpec flagItems(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_FLAGS);
		final ReportNodeSpec root = root(def, q, "flag");
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

	private static ReportNodeSpec pomodoroReport(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_POMODORO);
		final ReportNodeSpec root = root(def, q, "clock2");
		final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
		if (mgr == null) {
			root.add("番茄钟未初始化", "messagebox_warning");
			return root;
		}
		final List sessions = mgr.collectSessionsInRange(q.range.startMs, q.range.endMs);
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

	private static ReportNodeSpec mapLoad(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_MAP_LOAD);
		final ReportNodeSpec root = root(def, q, "list");
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
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

	private static ReportNodeSpec durationBuckets(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_DURATION_BUCKETS);
		final ReportNodeSpec root = root(def, q, "full-5");
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
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


	private static ReportNodeSpec publishedItems(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_PUBLISHED);
		final ReportNodeSpec root = root(def, q, "internet");
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

	private static ReportNodeSpec recentChanges(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_RECENT);
		final ReportNodeSpec root = root(def, q, "up");
		final List all = MindMapWorkspaceContextScanner.scanRecentlyModified(500);
		final List filtered = new ArrayList();
		for (int i = 0; i < all.size(); i++) {
			final MindMapWorkspaceContextScanner.ModifiedItem item = (MindMapWorkspaceContextScanner.ModifiedItem) all
			        .get(i);
			if (q.range.contains(item.modifiedAt) && q.matches(item.nodeText, mapLabel(item.mapFile), "")) {
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
