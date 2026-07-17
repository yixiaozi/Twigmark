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
		return toTree(generateView(def, query));
	}

	/** Convert a viewport model into a mind-map write tree. */
	public static ReportNodeSpec toTree(final ReportViewModel view) {
		return treeFromView(view);
	}

	/**
	 * Primary entry for the viewport: KPIs + structured charts + actionable details.
	 */
	public static ReportViewModel generateView(final ReportDefinition def, final ReportQuery query) {
		if (def == null) {
			final ReportViewModel empty = new ReportViewModel("（未知报表）", "");
			empty.addDetail("未知报表类型");
			empty.emptyHint = "请从左侧重新选择一种报表";
			return empty;
		}
		final ReportQuery q = query == null ? new ReportQuery(null, "", "") : query;
		try {
			final ReportViewModel view;
			if (ReportCatalog.ID_TODAY.equals(def.id)) {
				view = viewToday(q);
			}
			else if (ReportCatalog.ID_PLAN_VS_ACTUAL.equals(def.id)) {
				view = viewPlanVsActual(q);
			}
			else if (ReportCatalog.ID_USE_TIME.equals(def.id)) {
				view = viewUseTime(q);
			}
			else if (ReportCatalog.ID_TIME_BLOCK.equals(def.id)) {
				view = viewTimeBlock(q);
			}
			else if (ReportCatalog.ID_TREND.equals(def.id)) {
				view = viewTrend(q);
			}
			else if (ReportCatalog.ID_MAP_LOAD.equals(def.id)) {
				view = viewMapLoad(q);
			}
			else if (ReportCatalog.ID_OVERDUE.equals(def.id)) {
				view = viewOverdue(q);
			}
			else if (ReportCatalog.ID_URGENT.equals(def.id)) {
				view = viewUrgent(q);
			}
			else if (ReportCatalog.ID_TODOS.equals(def.id)) {
				view = viewTodos(q);
			}
			else if (ReportCatalog.ID_FLAGS.equals(def.id)) {
				view = viewFlags(q);
			}
			else if (ReportCatalog.ID_POMODORO.equals(def.id)) {
				view = viewPomodoro(q);
			}
			else if (ReportCatalog.ID_DURATION.equals(def.id)) {
				view = viewDuration(q);
			}
			else if (ReportCatalog.ID_TARGET.equals(def.id)) {
				view = viewTarget(q);
			}
			else if (ReportCatalog.ID_MIND_PULSE.equals(def.id)) {
				view = viewMindPulse(q);
			}
			else if (ReportCatalog.ID_KEYBOARD.equals(def.id)) {
				view = viewKeyboard(q);
			}
			else if (ReportCatalog.ID_RECURRING.equals(def.id)) {
				view = viewRecurring(q);
			}
			else if (ReportCatalog.ID_PUBLISHED.equals(def.id)) {
				view = viewPublished(q);
			}
			else {
				view = new ReportViewModel(def.title, def.description);
				view.addDetail("未实现：" + def.title);
			}
			view.decision = def.decision == null ? "" : def.decision;
			view.dataSource = def.dataSource == null ? "" : def.dataSource;
			return view;
		}
		catch (Exception e) {
			LogUtils.warn("ReportEngine.generateView failed: " + def.id, e);
			final ReportViewModel fail = new ReportViewModel("报表生成失败", def.title);
			fail.addDetail(String.valueOf(e.getMessage()));
			fail.emptyHint = "请检查数据源后重试";
			return fail;
		}
	}

	private static ReportNodeSpec treeFromView(final ReportViewModel view) {
		if (view == null) {
			return new ReportNodeSpec("（空报表）", "messagebox_warning");
		}
		final ReportNodeSpec root = new ReportNodeSpec(view.title, "idea");
		if (view.decision.length() > 0) {
			root.add(view.decision, "info");
		}
		if (view.dataSource.length() > 0) {
			root.add("数据：" + view.dataSource, "attach");
		}
		for (int i = 0; i < view.kpis.size(); i++) {
			final ReportKpi kpi = (ReportKpi) view.kpis.get(i);
			root.add(kpi.label + " · " + kpi.value + (kpi.hint.length() == 0 ? "" : " · " + kpi.hint), "full-5");
		}
		for (int i = 0; i < view.charts.size(); i++) {
			final ReportChartSeries series = (ReportChartSeries) view.charts.get(i);
			final ReportNodeSpec chartNode = root.add(series.title, "full-7");
			for (int j = 0; j < series.size(); j++) {
				chartNode.add(series.labelAt(j) + " · " + formatChartNumber(series.valueAt(j)), "full-3");
			}
		}
		final ReportNodeSpec details = root.add("明细", "list");
		for (int i = 0; i < view.details.size(); i++) {
			details.add((String) view.details.get(i), "list");
		}
		if (view.details.isEmpty() && view.charts.isEmpty()) {
			root.add(view.emptyHint.length() == 0 ? "暂无数据" : view.emptyHint, "smiley-neutral");
		}
		return root;
	}

	private static String formatChartNumber(final double v) {
		if (Math.abs(v - Math.rint(v)) < 0.05) {
			return String.valueOf((long) Math.round(v));
		}
		return String.format("%.1f", Double.valueOf(v));
	}

	private static ReportViewModel baseView(final ReportDefinition def, final ReportQuery q) {
		final StringBuilder sub = new StringBuilder();
		if (def.description != null) {
			sub.append(def.description);
		}
		if (def.usesTimeRange && q != null && q.range != null) {
			if (sub.length() > 0) {
				sub.append("  ·  ");
			}
			sub.append(q.range.label);
		}
		final ReportViewModel view = new ReportViewModel("报表 · " + def.title, sub.toString());
		view.decision = def.decision;
		view.dataSource = def.dataSource;
		return view;
	}

	private static ReportViewModel viewToday(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TODAY);
		final ReportTimeRange today = ReportTimeRange.ofPreset(ReportTimeRange.PRESET_TODAY);
		final ReportQuery todayQ = new ReportQuery(today, q.includeKeyword, q.excludeKeyword);
		final ReportViewModel view = baseView(def, todayQ);
		final List overdue = filterOneTimeBefore(System.currentTimeMillis());
		final List occ = filterOcc(loadOccurrences(today.startMs, today.endMs), todayQ);
		final long planned = sumPlannedMinutes(occ);
		final long pomoMs = sumPomodoroMs(today.startMs, today.endMs);
		final List todos = MindMapWorkspaceContextScanner.scanAllTodos();

		view.addKpi("逾期", String.valueOf(overdue.size()), "先清债");
		view.addKpi("今日安排", String.valueOf(occ.size()), formatHours(planned));
		view.addKpi("待办库存", String.valueOf(todos.size()), "全局");
		view.addKpi("今日番茄", formatDurationMs(pomoMs), "专注");

		final ReportChartSeries load = new ReportChartSeries("今日负荷对比", ReportChartSeries.TYPE_BAR);
		load.add("逾期", overdue.size());
		load.add("今日安排", occ.size());
		load.add("待办", Math.min(todos.size(), 200));
		load.add("番茄(刻度=10分钟)", Math.round(pomoMs / 600000.0));
		view.addChart(load);

		final Map byHour = new TreeMap();
		for (int i = 0; i < occ.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
			final Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(ref.occurrenceAt);
			final String hour = String.format("%02d:00", Integer.valueOf(cal.get(Calendar.HOUR_OF_DAY)));
			Long sum = (Long) byHour.get(hour);
			byHour.put(hour, Long.valueOf((sum == null ? 0L : sum.longValue()) + Math.max(0, ref.taskTimeMinutes)));
		}
		final ReportChartSeries hourChart = new ReportChartSeries("今日计划工时（按时段）", ReportChartSeries.TYPE_LINE);
		final Iterator hit = byHour.entrySet().iterator();
		while (hit.hasNext()) {
			final Map.Entry e = (Map.Entry) hit.next();
			hourChart.add((String) e.getKey(), ((Long) e.getValue()).doubleValue());
		}
		view.addChart(hourChart);

		view.addDetail("—— 逾期（最多 15 条，建议先处理）——");
		appendReminderDetails(view, overdue, 15, true);
		view.addDetail("—— 今日安排 ——");
		appendOccDetails(view, occ, 40);
		if (overdue.isEmpty() && occ.isEmpty()) {
			view.emptyHint = "今天很干净：无逾期、无安排。可以主动挑一张导图推进。";
		}
		return view;
	}

	private static ReportViewModel viewPlanVsActual(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_PLAN_VS_ACTUAL);
		final ReportViewModel view = baseView(def, q);
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		final Map plannedByDay = new TreeMap();
		for (int i = 0; i < occ.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
			final String day = DAY.format(new Date(ref.occurrenceAt));
			Long sum = (Long) plannedByDay.get(day);
			plannedByDay.put(day, Long.valueOf((sum == null ? 0L : sum.longValue()) + Math.max(0, ref.taskTimeMinutes)));
		}
		final Map usageByDay = new TreeMap();
		final List records = UsageStatsManager.getInstance().loadAllRecords();
		for (int i = 0; i < records.size(); i++) {
			final UsageRecord rec = (UsageRecord) records.get(i);
			if (!UsageStatsManager.isSignificantSession(rec)) {
				continue;
			}
			if (rec.getEndTime() < q.range.startMs || rec.getStartTime() >= q.range.endMs) {
				continue;
			}
			final String day = DAY.format(new Date(rec.getStartTime()));
			Long sum = (Long) usageByDay.get(day);
			usageByDay.put(day, Long.valueOf((sum == null ? 0L : sum.longValue()) + rec.getEffectiveDurationMs()));
		}
		final Map pomoByDay = new TreeMap();
		final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
		if (mgr != null) {
			final List sessions = mgr.collectSessionsInRange(q.range.startMs, q.range.endMs);
			for (int i = 0; i < sessions.size(); i++) {
				final PomodoroSessionManager.CalendarSession s = (PomodoroSessionManager.CalendarSession) sessions.get(i);
				final String day = DAY.format(new Date(s.startMs));
				Long sum = (Long) pomoByDay.get(day);
				pomoByDay.put(day, Long.valueOf((sum == null ? 0L : sum.longValue()) + s.focusMs));
			}
		}
		final long plannedMin = sumMapLong(plannedByDay);
		final long usageMs = sumMapLong(usageByDay);
		final long pomoMs = sumMapLong(pomoByDay);
		view.addKpi("计划工时", formatHours(plannedMin), "日历安排");
		view.addKpi("实际使用", formatDurationMs(usageMs), "有效会话");
		view.addKpi("番茄专注", formatDurationMs(pomoMs), "深度工作");
		final double planHours = plannedMin / 60.0;
		final double useHours = usageMs / 3600000.0;
		final int fill = planHours <= 0 ? 0 : (int) Math.round(Math.min(999, useHours * 100.0 / planHours));
		view.addKpi("兑现率", fill + "%", "使用÷计划");

		final ReportChartSeries compare = new ReportChartSeries("计划 vs 使用 vs 番茄（小时）", ReportChartSeries.TYPE_BAR);
		compare.add("计划", planHours);
		compare.add("使用", useHours);
		compare.add("番茄", pomoMs / 3600000.0);
		view.addChart(compare);

		final ReportChartSeries dailyPlan = new ReportChartSeries("每日计划工时（分钟）", ReportChartSeries.TYPE_LINE);
		fillLongSeries(dailyPlan, plannedByDay);
		view.addChart(dailyPlan);
		final ReportChartSeries dailyUse = new ReportChartSeries("每日有效使用（分钟）", ReportChartSeries.TYPE_LINE);
		final Iterator uit = usageByDay.entrySet().iterator();
		while (uit.hasNext()) {
			final Map.Entry e = (Map.Entry) uit.next();
			dailyUse.add(shortDay((String) e.getKey()), ((Long) e.getValue()).doubleValue() / 60000.0);
		}
		view.addChart(dailyUse);

		if (plannedMin == 0 && usageMs == 0) {
			view.emptyHint = "该时段既无计划也无使用记录。先在日历里排几块时间，或打开导图产生使用记录。";
		}
		else if (plannedMin > 0 && usageMs < plannedMin * 30000L) {
			view.addDetail("洞察：计划远高于实际使用 → 可能「排了但没做」，建议砍计划或设保护时段。");
		}
		else if (usageMs > plannedMin * 90000L && plannedMin > 0) {
			view.addDetail("洞察：实际使用远超计划 → 大量临时工作，建议把常做的事写进安排。");
		}
		view.addDetail("计划条目 · " + occ.size() + " · 使用会话已计入显著会话");
		return view;
	}

	private static void fillLongSeries(final ReportChartSeries series, final Map byDay) {
		final Iterator it = byDay.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry e = (Map.Entry) it.next();
			series.add(shortDay((String) e.getKey()), ((Long) e.getValue()).doubleValue());
		}
	}

	private static String shortDay(final String ymd) {
		if (ymd == null || ymd.length() < 10) {
			return ymd == null ? "" : ymd;
		}
		return ymd.substring(5);
	}

	private static ReportViewModel viewUseTime(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_USE_TIME);
		final ReportViewModel view = baseView(def, q);
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
			final String hour = String.format("%02d:00", Integer.valueOf(cal.get(Calendar.HOUR_OF_DAY)));
			Long hourSum = (Long) byHour.get(hour);
			byHour.put(hour, Long.valueOf((hourSum == null ? 0L : hourSum.longValue()) + eff));
			MapUsageSummary summary = (MapUsageSummary) byMap.get(path.length() == 0 ? mapName : path);
			if (summary == null) {
				summary = new MapUsageSummary(path.length() == 0 ? mapName : path);
				byMap.put(path.length() == 0 ? mapName : path, summary);
			}
			summary.addRecord(rec);
		}
		view.addKpi("显著会话", String.valueOf(sessionCount), "过滤噪音");
		view.addKpi("有效时长", formatDurationMs(totalEffective), "真正在用");
		final ReportChartSeries hourChart = new ReportChartSeries("注意力按时段（分钟）", ReportChartSeries.TYPE_LINE);
		final Iterator hourIt = byHour.entrySet().iterator();
		while (hourIt.hasNext()) {
			final Map.Entry e = (Map.Entry) hourIt.next();
			hourChart.add((String) e.getKey(), ((Long) e.getValue()).doubleValue() / 60000.0);
		}
		view.addChart(hourChart);
		final List mapRows = new ArrayList(byMap.values());
		Collections.sort(mapRows, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long la = ((MapUsageSummary) a).getEffectiveDurationMs();
				final long lb = ((MapUsageSummary) b).getEffectiveDurationMs();
				return la < lb ? 1 : (la > lb ? -1 : 0);
			}
		});
		final ReportChartSeries mapChart = new ReportChartSeries("注意力按导图（分钟）", ReportChartSeries.TYPE_PIE);
		final int limit = Math.min(12, mapRows.size());
		for (int i = 0; i < limit; i++) {
			final MapUsageSummary s = (MapUsageSummary) mapRows.get(i);
			mapChart.add(trimLabel(s.getDisplayName(), 16), s.getEffectiveDurationMs() / 60000.0);
			view.addDetail(s.getDisplayName() + " · " + formatDurationMs(s.getEffectiveDurationMs()) + " · "
			        + s.getSessionCount() + " 次");
		}
		view.addChart(mapChart);
		if (sessionCount == 0) {
			view.emptyHint = "该时段无使用记录。打开导图并实际编辑一段时间后会自动累计。";
		}
		return view;
	}

	private static ReportViewModel viewTimeBlock(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TIME_BLOCK);
		final ReportViewModel view = baseView(def, q);
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		long total = sumPlannedMinutes(occ);
		view.addKpi("计划合计", formatHours(total), occ.size() + " 条安排");
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
		final ReportChartSeries pie = new ReportChartSeries("计划工时构成（分钟）", ReportChartSeries.TYPE_PIE);
		final ReportChartSeries bar = new ReportChartSeries("分类计划工时（分钟）", ReportChartSeries.TYPE_BAR);
		for (int i = 0; i < rows.size(); i++) {
			final Object[] row = (Object[]) rows.get(i);
			final String cat = (String) row[0];
			final long mins = ((long[]) row[1])[0];
			final long count = ((long[]) row[1])[1];
			final int pct = total <= 0 ? 0 : (int) Math.round(mins * 100.0 / total);
			pie.add(trimLabel(cat, 14), mins);
			bar.add(trimLabel(cat, 10), mins);
			view.addDetail(cat + " · " + formatHours(mins) + " · " + pct + "% · " + count + " 条");
		}
		view.addChart(pie);
		view.addChart(bar);
		if (occ.isEmpty()) {
			view.emptyHint = "该时段无安排。在提醒/日历里给任务填上计划工时后，这里会出现构成图。";
		}
		return view;
	}

	private static ReportViewModel viewTrend(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TREND);
		final ReportViewModel view = baseView(def, q);
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
				final PomodoroSessionManager.CalendarSession s = (PomodoroSessionManager.CalendarSession) sessions.get(i);
				final String day = DAY.format(new Date(s.startMs));
				Long sum = (Long) pomoByDay.get(day);
				pomoByDay.put(day, Long.valueOf((sum == null ? 0L : sum.longValue()) + s.focusMs));
			}
		}
		final long spanDays = Math.max(1L, (q.range.endMs - q.range.startMs) / (24L * 60L * 60L * 1000L));
		final long plannedTotal = sumMapLong(plannedByDay);
		final long pomoTotal = sumMapLong(pomoByDay);
		view.addKpi("计划合计", formatHours(plannedTotal), "日均 " + formatHours(plannedTotal / spanDays));
		view.addKpi("有记录天数", String.valueOf(plannedByDay.size()), "非空日");
		view.addKpi("番茄合计", formatDurationMs(pomoTotal), "专注");
		final ReportChartSeries planLine = new ReportChartSeries("每日计划（分钟）", ReportChartSeries.TYPE_LINE);
		fillLongSeries(planLine, plannedByDay);
		view.addChart(planLine);
		final ReportChartSeries pomoLine = new ReportChartSeries("每日番茄（分钟）", ReportChartSeries.TYPE_LINE);
		final Iterator pit = pomoByDay.entrySet().iterator();
		while (pit.hasNext()) {
			final Map.Entry e = (Map.Entry) pit.next();
			pomoLine.add(shortDay((String) e.getKey()), ((Long) e.getValue()).doubleValue() / 60000.0);
		}
		view.addChart(pomoLine);
		if (plannedByDay.isEmpty() && pomoByDay.isEmpty()) {
			view.emptyHint = "该时段无趋势数据。有安排或番茄后，这里会画出节奏曲线。";
		}
		return view;
	}

	private static ReportViewModel viewMapLoad(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_MAP_LOAD);
		final ReportViewModel view = baseView(def, q);
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
		view.addKpi("涉及导图", String.valueOf(load.size()), "有安排或待办");
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
		final ReportChartSeries hours = new ReportChartSeries("导图计划工时（分钟）", ReportChartSeries.TYPE_BAR);
		final ReportChartSeries todos = new ReportChartSeries("导图待办数", ReportChartSeries.TYPE_PIE);
		final int limit = Math.min(14, rows.size());
		for (int i = 0; i < limit; i++) {
			final Object[] row = (Object[]) rows.get(i);
			final String name = trimLabel((String) row[0], 14);
			hours.add(name, ((Integer) row[3]).doubleValue());
			todos.add(name, ((Integer) row[2]).doubleValue());
			view.addDetail(row[0] + " · 安排" + row[1] + " · 待办" + row[2] + " · 计划"
			        + formatHours(((Integer) row[3]).intValue()));
		}
		view.addChart(hours);
		view.addChart(todos);
		if (rows.isEmpty()) {
			view.emptyHint = "没有导图负荷数据。给提醒加计划工时、或在图里放 hourglass 待办后再看。";
		}
		return view;
	}

	private static ReportViewModel viewOverdue(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_OVERDUE);
		final ReportViewModel view = baseView(def, q);
		final long now = System.currentTimeMillis();
		final List items = filterOneTimeBefore(now);
		view.addKpi("逾期", String.valueOf(items.size()), "一次性提醒");
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
		final ReportChartSeries pie = new ReportChartSeries("逾期按导图", ReportChartSeries.TYPE_PIE);
		final Iterator it = byMap.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final List list = (List) entry.getValue();
			pie.add(trimLabel((String) entry.getKey(), 14), list.size());
		}
		view.addChart(pie);
		appendReminderDetails(view, items, 80, true);
		if (items.isEmpty()) {
			view.emptyHint = "太好了，暂无逾期。保持这个状态。";
		}
		return view;
	}

	private static ReportViewModel viewUrgent(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_URGENT);
		final ReportViewModel view = baseView(def, q);
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		final List urgent = new ArrayList();
		for (int i = 0; i < occ.size(); i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
			if (ref.jinji >= 1) {
				urgent.add(ref);
			}
		}
		view.addKpi("紧急安排", String.valueOf(urgent.size()), "jinji≥1");
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
		final ReportChartSeries bar = new ReportChartSeries("按紧急度分层", ReportChartSeries.TYPE_BAR);
		final Iterator it = byJinji.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final List list = (List) entry.getValue();
			bar.add("紧急度 " + entry.getKey(), list.size());
			view.addDetail("—— 紧急度 " + entry.getKey() + " · " + list.size() + " 条 ——");
			appendOccDetails(view, list, 40);
		}
		view.addChart(bar);
		if (urgent.isEmpty()) {
			view.emptyHint = "该时段无紧急安排。若需要分层，给提醒设置紧急度（jinji）。";
		}
		return view;
	}

	private static ReportViewModel viewTodos(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TODOS);
		final ReportViewModel view = baseView(def, q);
		final List todos = MindMapWorkspaceContextScanner.scanAllTodos();
		view.addKpi("待办总数", String.valueOf(todos.size()), "hourglass");
		final Map byMap = new TreeMap();
		for (int i = 0; i < todos.size(); i++) {
			final MindMapWorkspaceContextScanner.TodoItem item = (MindMapWorkspaceContextScanner.TodoItem) todos.get(i);
			if (!q.matches(item.nodeText, mapLabel(item.mapFile), "")) {
				continue;
			}
			final String map = mapLabel(item.mapFile);
			List list = (List) byMap.get(map);
			if (list == null) {
				list = new ArrayList();
				byMap.put(map, list);
			}
			list.add(item);
		}
		final ReportChartSeries pie = new ReportChartSeries("待办库存按导图", ReportChartSeries.TYPE_PIE);
		final Iterator it = byMap.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final List list = (List) entry.getValue();
			pie.add(trimLabel((String) entry.getKey(), 14), list.size());
			view.addDetail("—— " + entry.getKey() + " · " + list.size() + " ——");
			for (int i = 0; i < list.size() && i < 40; i++) {
				final MindMapWorkspaceContextScanner.TodoItem item = (MindMapWorkspaceContextScanner.TodoItem) list
				        .get(i);
				view.addDetail("· " + item.nodeText);
			}
		}
		view.addChart(pie);
		if (todos.isEmpty()) {
			view.emptyHint = "无待办。需要追踪时，给节点加 hourglass 图标。";
		}
		return view;
	}

	private static ReportViewModel viewFlags(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_FLAGS);
		final ReportViewModel view = baseView(def, q);
		final List flags = MindMapWorkspaceContextScanner.scanFlagItems();
		view.addKpi("红旗", String.valueOf(flags.size()), "行动钉");
		final Map byMap = new TreeMap();
		for (int i = 0; i < flags.size(); i++) {
			final MindMapWorkspaceContextScanner.IconItem item = (MindMapWorkspaceContextScanner.IconItem) flags.get(i);
			if (!q.matches(item.nodeText, mapLabel(item.mapFile), "")) {
				continue;
			}
			final String map = mapLabel(item.mapFile);
			List list = (List) byMap.get(map);
			if (list == null) {
				list = new ArrayList();
				byMap.put(map, list);
			}
			list.add(item);
		}
		final ReportChartSeries pie = new ReportChartSeries("红旗按导图", ReportChartSeries.TYPE_PIE);
		final Iterator it = byMap.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final List list = (List) entry.getValue();
			pie.add(trimLabel((String) entry.getKey(), 14), list.size());
			view.addDetail("—— " + entry.getKey() + " · " + list.size() + " ——");
			for (int i = 0; i < list.size() && i < 40; i++) {
				final MindMapWorkspaceContextScanner.IconItem item = (MindMapWorkspaceContextScanner.IconItem) list
				        .get(i);
				view.addDetail("· " + item.nodeText);
			}
		}
		view.addChart(pie);
		if (flags.isEmpty()) {
			view.emptyHint = "无红旗。把「下一步必须推进」的节点钉上 flag。";
		}
		return view;
	}

	private static ReportViewModel viewPomodoro(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_POMODORO);
		final ReportViewModel view = baseView(def, q);
		final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
		if (mgr == null) {
			view.emptyHint = "番茄钟未初始化。";
			return view;
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
		view.addKpi("会话段", String.valueOf(sessions.size()), "番茄段");
		view.addKpi("专注合计", formatDurationMs(total), "深度工作");
		final ReportChartSeries dayLine = new ReportChartSeries("每日专注（分钟）", ReportChartSeries.TYPE_LINE);
		final Iterator dayIt = byDay.entrySet().iterator();
		while (dayIt.hasNext()) {
			final Map.Entry e = (Map.Entry) dayIt.next();
			dayLine.add(shortDay((String) e.getKey()), ((Long) e.getValue()).doubleValue() / 60000.0);
		}
		view.addChart(dayLine);
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
		final ReportChartSeries nodePie = new ReportChartSeries("专注按节点（分钟）", ReportChartSeries.TYPE_PIE);
		final int limit = Math.min(12, nodeRows.size());
		for (int i = 0; i < limit; i++) {
			final Object[] row = (Object[]) nodeRows.get(i);
			nodePie.add(trimLabel((String) row[0], 14), ((Long) row[1]).doubleValue() / 60000.0);
			view.addDetail(row[0] + " · " + formatDurationMs(((Long) row[1]).longValue()));
		}
		view.addChart(nodePie);
		if (sessions.isEmpty()) {
			view.emptyHint = "该时段无番茄记录。在节点上开番茄钟后会出现在这里。";
		}
		return view;
	}

	private static ReportViewModel viewDuration(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_DURATION);
		final ReportViewModel view = baseView(def, q);
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		final int[] buckets = new int[6];
		final String[] labels = { "未设时长", "≤15分", "16–30分", "31–60分", "61–120分", ">120分" };
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
		view.addKpi("样本", String.valueOf(occ.size()), "安排条数");
		view.addKpi("未估时长", String.valueOf(buckets[0]), "建议补填");
		final ReportChartSeries pie = new ReportChartSeries("时长切片习惯", ReportChartSeries.TYPE_PIE);
		final ReportChartSeries bar = new ReportChartSeries("分桶数量", ReportChartSeries.TYPE_BAR);
		for (int i = 0; i < buckets.length; i++) {
			pie.add(labels[i], buckets[i]);
			bar.add(labels[i], buckets[i]);
			view.addDetail(labels[i] + " · " + buckets[i] + " 条");
		}
		view.addChart(pie);
		view.addChart(bar);
		if (buckets[0] > occ.size() / 2 && occ.size() > 0) {
			view.addDetail("洞察：超过一半安排没填时长 → 计划对照会失真，建议养成填 taskTime 的习惯。");
		}
		if (occ.isEmpty()) {
			view.emptyHint = "无安排样本。";
		}
		return view;
	}

	private static ReportViewModel viewTarget(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TARGET);
		final ReportViewModel view = baseView(def, q);
		final List goals = discoverGoalNames();
		view.addKpi("目标数", String.valueOf(goals.size()), "「目标」子项");
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		if (goals.isEmpty()) {
			view.emptyHint = "未找到「目标」节点。在导图建父节点「目标」，下面挂子目标名称。";
			view.addDetail("回退提示：先看「计划构成」了解时间预算分布。");
			return view;
		}
		final ReportChartSeries pie = new ReportChartSeries("目标相关计划工时（分钟）", ReportChartSeries.TYPE_PIE);
		long matchedMinutes = 0L;
		for (int g = 0; g < goals.size(); g++) {
			final String goal = (String) goals.get(g);
			if (!q.matches(goal, "", "")) {
				continue;
			}
			long mins = 0L;
			int count = 0;
			for (int i = 0; i < occ.size(); i++) {
				final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
				final String text = ref.nodeText == null ? "" : ref.nodeText;
				final String path = ref.file == null ? "" : ref.file.getAbsolutePath();
				if (text.indexOf(goal) >= 0 || path.indexOf(goal) >= 0 || categoryOf(ref).indexOf(goal) >= 0) {
					mins += Math.max(0, ref.taskTimeMinutes);
					count++;
				}
			}
			matchedMinutes += mins;
			pie.add(trimLabel(goal, 14), mins);
			view.addDetail(goal + " · " + formatHours(mins) + " · " + count + " 条");
		}
		view.addKpi("目标工时", formatHours(matchedMinutes), "已匹配安排");
		view.addChart(pie);
		return view;
	}

	private static ReportViewModel viewMindPulse(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_MIND_PULSE);
		final ReportViewModel view = baseView(def, q);
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
		view.addKpi("变更节点", String.valueOf(filtered.size()), "思考痕迹");
		final Map byDay = new TreeMap();
		final Map byHour = new TreeMap();
		final Map byMap = new TreeMap();
		for (int i = 0; i < filtered.size(); i++) {
			final MindMapWorkspaceContextScanner.ModifiedItem item = (MindMapWorkspaceContextScanner.ModifiedItem) filtered
			        .get(i);
			final String day = DAY.format(new Date(item.modifiedAt));
			byDay.put(day, Integer.valueOf(intVal(byDay.get(day)) + 1));
			final Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(item.modifiedAt);
			final String hour = String.format("%02d:00", Integer.valueOf(cal.get(Calendar.HOUR_OF_DAY)));
			byHour.put(hour, Integer.valueOf(intVal(byHour.get(hour)) + 1));
			final String map = mapLabel(item.mapFile);
			byMap.put(map, Integer.valueOf(intVal(byMap.get(map)) + 1));
		}
		final ReportChartSeries dayLine = new ReportChartSeries("按日修改次数", ReportChartSeries.TYPE_LINE);
		final Iterator dit = byDay.entrySet().iterator();
		while (dit.hasNext()) {
			final Map.Entry e = (Map.Entry) dit.next();
			dayLine.add(shortDay((String) e.getKey()), ((Integer) e.getValue()).doubleValue());
		}
		view.addChart(dayLine);
		final ReportChartSeries hourLine = new ReportChartSeries("按时段修改次数", ReportChartSeries.TYPE_BAR);
		final Iterator hit = byHour.entrySet().iterator();
		while (hit.hasNext()) {
			final Map.Entry e = (Map.Entry) hit.next();
			hourLine.add((String) e.getKey(), ((Integer) e.getValue()).doubleValue());
		}
		view.addChart(hourLine);
		final ReportChartSeries mapPie = new ReportChartSeries("按导图修改占比", ReportChartSeries.TYPE_PIE);
		final Iterator mit = byMap.entrySet().iterator();
		while (mit.hasNext()) {
			final Map.Entry e = (Map.Entry) mit.next();
			mapPie.add(trimLabel((String) e.getKey(), 14), ((Integer) e.getValue()).doubleValue());
		}
		view.addChart(mapPie);
		final int limit = Math.min(50, filtered.size());
		for (int i = 0; i < limit; i++) {
			final MindMapWorkspaceContextScanner.ModifiedItem item = (MindMapWorkspaceContextScanner.ModifiedItem) filtered
			        .get(i);
			view.addDetail(TIME.format(new Date(item.modifiedAt)) + "  " + item.nodeText + "  〔"
			        + mapLabel(item.mapFile) + "〕");
		}
		if (filtered.isEmpty()) {
			view.emptyHint = "该时段无节点变更。";
		}
		return view;
	}

	private static ReportViewModel viewKeyboard(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_KEYBOARD);
		final ReportViewModel view = baseView(def, q);
		final List keyFiles = findKeyLogFiles();
		if (keyFiles.isEmpty()) {
			view.emptyHint = "未找到 key.txt。把 DocearReminder 的击键日志放到用户目录或工作区后重试。";
			view.addDetail("可放置位置：用户 Freeplane/Docear 目录、项目 _data、工作区扫描根。");
			return view;
		}
		final Map byDay = new TreeMap();
		final Map byHour = new TreeMap();
		long totalKeys = 0L;
		for (int f = 0; f < keyFiles.size(); f++) {
			final File file = (File) keyFiles.get(f);
			view.addDetail("日志 · " + file.getAbsolutePath());
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
					final String hour = String.format("%02d:00", Integer.valueOf(cal.get(Calendar.HOUR_OF_DAY)));
					Long hourCount = (Long) byHour.get(hour);
					byHour.put(hour, Long.valueOf((hourCount == null ? 0L : hourCount.longValue()) + 1L));
				}
				reader.close();
			}
			catch (Exception e) {
				view.addDetail("读取失败：" + e.getMessage());
			}
		}
		view.addKpi("击键", String.valueOf(totalKeys), "合计");
		final ReportChartSeries dayLine = new ReportChartSeries("按日击键", ReportChartSeries.TYPE_LINE);
		fillLongSeries(dayLine, byDay);
		view.addChart(dayLine);
		final ReportChartSeries hourBar = new ReportChartSeries("按时段击键", ReportChartSeries.TYPE_BAR);
		final Iterator hit = byHour.entrySet().iterator();
		while (hit.hasNext()) {
			final Map.Entry e = (Map.Entry) hit.next();
			hourBar.add((String) e.getKey(), ((Long) e.getValue()).doubleValue());
		}
		view.addChart(hourBar);
		return view;
	}

	private static ReportViewModel viewRecurring(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_RECURRING);
		final ReportViewModel view = baseView(def, q);
		final List items = MindMapWorkspaceContextScanner.scanRecurringReminders();
		view.addKpi("周期提醒", String.valueOf(items.size()), "例行事务");
		final Map byType = new TreeMap();
		for (int i = 0; i < items.size(); i++) {
			final MindMapWorkspaceContextScanner.ReminderItem item = (MindMapWorkspaceContextScanner.ReminderItem) items
			        .get(i);
			if (!q.matches(item.nodeText, mapLabel(item.mapFile), item.remindType)) {
				continue;
			}
			final String type = item.remindType == null || item.remindType.length() == 0 ? "周期" : item.remindType;
			List list = (List) byType.get(type);
			if (list == null) {
				list = new ArrayList();
				byType.put(type, list);
			}
			list.add(item);
		}
		final ReportChartSeries pie = new ReportChartSeries("周期类型分布", ReportChartSeries.TYPE_PIE);
		final Iterator it = byType.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final List list = (List) entry.getValue();
			pie.add(String.valueOf(entry.getKey()), list.size());
			view.addDetail("—— " + entry.getKey() + " · " + list.size() + " ——");
			appendReminderDetails(view, list, 40, false);
		}
		view.addChart(pie);
		if (items.isEmpty()) {
			view.emptyHint = "无周期提醒。";
		}
		return view;
	}

	private static ReportViewModel viewPublished(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_PUBLISHED);
		final ReportViewModel view = baseView(def, q);
		final List items = MindMapWorkspaceContextScanner.scanPublishedItems();
		view.addKpi("发布项", String.valueOf(items.size()), "internet 图标");
		final Map byMap = new TreeMap();
		for (int i = 0; i < items.size(); i++) {
			final MindMapWorkspaceContextScanner.IconItem item = (MindMapWorkspaceContextScanner.IconItem) items.get(i);
			if (!q.matches(item.nodeText, mapLabel(item.mapFile), "")) {
				continue;
			}
			final String map = mapLabel(item.mapFile);
			List list = (List) byMap.get(map);
			if (list == null) {
				list = new ArrayList();
				byMap.put(map, list);
			}
			list.add(item);
		}
		final ReportChartSeries pie = new ReportChartSeries("发布项按导图", ReportChartSeries.TYPE_PIE);
		final Iterator it = byMap.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final List list = (List) entry.getValue();
			pie.add(trimLabel((String) entry.getKey(), 14), list.size());
			view.addDetail("—— " + entry.getKey() + " · " + list.size() + " ——");
			for (int i = 0; i < list.size() && i < 40; i++) {
				final MindMapWorkspaceContextScanner.IconItem item = (MindMapWorkspaceContextScanner.IconItem) list
				        .get(i);
				view.addDetail("· " + item.nodeText);
			}
		}
		view.addChart(pie);
		if (items.isEmpty()) {
			view.emptyHint = "无发布项。对外输出的节点可加 internet 图标。";
		}
		return view;
	}

	private static void appendOccDetails(final ReportViewModel view, final List occ, final int limit) {
		final int n = Math.min(limit, occ.size());
		for (int i = 0; i < n; i++) {
			final ReminderCalendarBridge.OccurrenceRef ref = (ReminderCalendarBridge.OccurrenceRef) occ.get(i);
			view.addDetail(TIME.format(new Date(ref.occurrenceAt)) + "  " + safeText(ref.nodeText) + "  〔"
			        + mapLabel(ref.file) + "〕 · " + formatHours(Math.max(0, ref.taskTimeMinutes)));
		}
	}

	private static void appendReminderDetails(final ReportViewModel view, final List items, final int limit,
	        final boolean showWhen) {
		final int n = Math.min(limit, items.size());
		for (int i = 0; i < n; i++) {
			final MindMapWorkspaceContextScanner.ReminderItem item = (MindMapWorkspaceContextScanner.ReminderItem) items
			        .get(i);
			final String when = showWhen && item.remindAt > 0 ? TIME.format(new Date(item.remindAt)) + "  " : "";
			view.addDetail(when + safeText(item.nodeText) + "  〔" + mapLabel(item.mapFile) + "〕");
		}
	}

	private static String safeText(final String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").trim();
	}

	private static String trimLabel(final String s, final int max) {
		if (s == null) {
			return "";
		}
		if (s.length() <= max) {
			return s;
		}
		return s.substring(0, Math.max(1, max - 1)) + "…";
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
