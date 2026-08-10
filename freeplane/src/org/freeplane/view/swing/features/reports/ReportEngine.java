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
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.usagestats.MapUsageSummary;
import org.freeplane.features.usagestats.UsageRecord;
import org.freeplane.features.usagestats.UsageStatsManager;
import org.freeplane.view.swing.features.keylog.KeyLogService;
import org.freeplane.view.swing.features.pomodoro.PomodoroSessionManager;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderCalendarBridge;

/**
 * Builds report trees from workspace reminder / todo / pomodoro / usage data.
 */
public final class ReportEngine {
	private static String tr(final String key) {
		return TextUtils.getText(key);
	}


	private static final SimpleDateFormat TIME = new SimpleDateFormat("M/d HH:mm", Locale.CHINA);
	private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
	/** Bound for the duration of {@link #generateView} so nested scanners can emit stage text. */
	private static final ThreadLocal PROGRESS = new ThreadLocal();

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
		return generateView(def, query, null);
	}

	/**
	 * Same as {@link #generateView(ReportDefinition, ReportQuery)} with optional progress.
	 * Heavy collection stages use indeterminate progress ({@code percent &lt; 0}); only the
	 * final assemble steps report 90–100, because mid-scan percentages would be misleading.
	 */
	public static ReportViewModel generateView(final ReportDefinition def, final ReportQuery query,
	        final ReportProgress progress) {
		if (def == null) {
			notifyProgress(progress, 100, TextUtils.getText("ReportEngine.unknownType"));
			final ReportViewModel empty = new ReportViewModel(TextUtils.getText("ReportEngine.unknownTitle"), "");
			empty.addDetail(TextUtils.getText("ReportEngine.unknownType"));
			empty.emptyHint = TextUtils.getText("ReportEngine.unknownHint");
			return empty;
		}
		final ReportQuery q = query == null ? new ReportQuery(null, "", "") : query;
		PROGRESS.set(progress);
		try {
			notifyProgress(progress, -1, TextUtils.format("ReportEngine.preparing", def.title));
			final ReportViewModel view;
			if (ReportCatalog.ID_TODAY.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.today"));
				view = viewToday(q);
			}
			else if (ReportCatalog.ID_PLAN_VS_ACTUAL.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.planVsActual"));
				view = viewPlanVsActual(q);
			}
			else if (ReportCatalog.ID_USE_TIME.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.useTime"));
				view = viewUseTime(q);
			}
			else if (ReportCatalog.ID_TIME_BLOCK.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.timeBlock"));
				view = viewTimeBlock(q);
			}
			else if (ReportCatalog.ID_TREND.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.trend"));
				view = viewTrend(q);
			}
			else if (ReportCatalog.ID_MAP_LOAD.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.mapLoad"));
				view = viewMapLoad(q);
			}
			else if (ReportCatalog.ID_OVERDUE.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.overdue"));
				view = viewOverdue(q);
			}
			else if (ReportCatalog.ID_URGENT.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.urgent"));
				view = viewUrgent(q);
			}
			else if (ReportCatalog.ID_TODOS.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.todos"));
				view = viewTodos(q);
			}
			else if (ReportCatalog.ID_FLAGS.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.flags"));
				view = viewFlags(q);
			}
			else if (ReportCatalog.ID_POMODORO.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.pomodoro"));
				view = viewPomodoro(q);
			}
			else if (ReportCatalog.ID_DURATION.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.duration"));
				view = viewDuration(q);
			}
			else if (ReportCatalog.ID_TARGET.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.target"));
				view = viewTarget(q);
			}
			else if (ReportCatalog.ID_MIND_PULSE.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.mindPulse"));
				view = viewMindPulse(q);
			}
			else if (ReportCatalog.ID_KEYBOARD.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.keyboard"));
				view = viewKeyboard(q);
			}
			else if (ReportCatalog.ID_RECURRING.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.recurring"));
				view = viewRecurring(q);
			}
			else if (ReportCatalog.ID_PUBLISHED.equals(def.id)) {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.progress.published"));
				view = viewPublished(q);
			}
			else {
				notifyProgress(progress, -1, TextUtils.getText("ReportEngine.unimplemented"));
				view = new ReportViewModel(def.title, def.description);
				view.addDetail(TextUtils.format("ReportEngine.unimplementedDetail", def.title));
			}
			notifyProgress(progress, 90, TextUtils.getText("ReportEngine.organizing"));
			view.decision = def.decision == null ? "" : def.decision;
			view.dataSource = def.dataSource == null ? "" : def.dataSource;
			notifyProgress(progress, 96, TextUtils.getText("ReportEngine.summaryDone"));
			return view;
		}
		catch (Exception e) {
			LogUtils.warn("ReportEngine.generateView failed: " + def.id, e);
			notifyProgress(progress, 100, TextUtils.getText("ReportEngine.generateFailed"));
			final ReportViewModel fail = new ReportViewModel(TextUtils.getText("ReportEngine.generateFailedTitle"),
			        def.title);
			fail.addDetail(String.valueOf(e.getMessage()));
			fail.emptyHint = TextUtils.getText("ReportEngine.retryHint");
			return fail;
		}
		finally {
			PROGRESS.remove();
		}
	}

	private static void stage(final String message) {
		notifyProgress((ReportProgress) PROGRESS.get(), -1, message);
	}

	private static void notifyProgress(final ReportProgress progress, final int percent, final String message) {
		if (progress == null) {
			return;
		}
		try {
			progress.update(percent, message);
		}
		catch (Exception e) {
			// Progress UI must never break report generation.
		}
	}

	private static ReportNodeSpec treeFromView(final ReportViewModel view) {
		if (view == null) {
			return new ReportNodeSpec(tr("ReportEngine.content.0001"), "messagebox_warning");
		}
		final ReportNodeSpec root = new ReportNodeSpec(view.title, "idea");
		if (view.decision.length() > 0) {
			root.add(view.decision, "info");
		}
		if (view.dataSource.length() > 0) {
			root.add(tr("ReportEngine.content.0002") + view.dataSource, "attach");
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
		final ReportNodeSpec details = root.add(tr("ReportEngine.content.0003"), "list");
		for (int i = 0; i < view.details.size(); i++) {
			details.add((String) view.details.get(i), "list");
		}
		if (view.details.isEmpty() && view.charts.isEmpty()) {
			root.add(view.emptyHint.length() == 0 ? tr("ReportEngine.content.0004") : view.emptyHint, "smiley-neutral");
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
		final ReportViewModel view = new ReportViewModel(tr("ReportEngine.content.0005") + def.title, sub.toString());
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

		view.addKpi(tr("ReportEngine.content.0006"), String.valueOf(overdue.size()), tr("ReportEngine.content.0007"));
		view.addKpi(tr("ReportEngine.content.0008"), String.valueOf(occ.size()), formatHours(planned));
		view.addKpi(tr("ReportEngine.content.0009"), String.valueOf(todos.size()), tr("ReportEngine.content.0010"));
		view.addKpi(tr("ReportEngine.content.0011"), formatDurationMs(pomoMs), tr("ReportEngine.content.0012"));

		final ReportChartSeries load = new ReportChartSeries(tr("ReportEngine.content.0013"), ReportChartSeries.TYPE_BAR);
		load.add(tr("ReportEngine.content.0006"), overdue.size());
		load.add(tr("ReportEngine.content.0008"), occ.size());
		load.add(tr("ReportEngine.content.0014"), Math.min(todos.size(), 200));
		load.add(tr("ReportEngine.content.0015"), Math.round(pomoMs / 600000.0));
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
		final ReportChartSeries hourChart = new ReportChartSeries(tr("ReportEngine.content.0016"), ReportChartSeries.TYPE_LINE);
		final Iterator hit = byHour.entrySet().iterator();
		while (hit.hasNext()) {
			final Map.Entry e = (Map.Entry) hit.next();
			hourChart.add((String) e.getKey(), ((Long) e.getValue()).doubleValue());
		}
		view.addChart(hourChart);

		view.addDetail(tr("ReportEngine.content.0017"));
		appendReminderDetails(view, overdue, 15, true);
		view.addDetail(tr("ReportEngine.content.0018"));
		appendOccDetails(view, occ, 40);
		if (overdue.isEmpty() && occ.isEmpty()) {
			view.emptyHint = tr("ReportEngine.content.0019");
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
		stage(TextUtils.getText("ReportEngine.stage.readingActivity"));
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
		view.addKpi(tr("ReportEngine.content.0020"), formatHours(plannedMin), tr("ReportEngine.content.0021"));
		view.addKpi(tr("ReportEngine.content.0022"), formatDurationMs(usageMs), tr("ReportEngine.content.0023"));
		view.addKpi(tr("ReportEngine.content.0024"), formatDurationMs(pomoMs), tr("ReportEngine.content.0025"));
		final double planHours = plannedMin / 60.0;
		final double useHours = usageMs / 3600000.0;
		final int fill = planHours <= 0 ? 0 : (int) Math.round(Math.min(999, useHours * 100.0 / planHours));
		view.addKpi(tr("ReportEngine.content.0026"), fill + "%", tr("ReportEngine.content.0027"));

		final ReportChartSeries compare = new ReportChartSeries(tr("ReportEngine.content.0028"), ReportChartSeries.TYPE_BAR);
		compare.add(tr("ReportEngine.content.0029"), planHours);
		compare.add(tr("ReportEngine.content.0030"), useHours);
		compare.add(tr("ReportEngine.content.0031"), pomoMs / 3600000.0);
		view.addChart(compare);

		final ReportChartSeries dailyPlan = new ReportChartSeries(tr("ReportEngine.content.0032"), ReportChartSeries.TYPE_LINE);
		fillLongSeries(dailyPlan, plannedByDay);
		view.addChart(dailyPlan);
		final ReportChartSeries dailyUse = new ReportChartSeries(tr("ReportEngine.content.0033"), ReportChartSeries.TYPE_LINE);
		final Iterator uit = usageByDay.entrySet().iterator();
		while (uit.hasNext()) {
			final Map.Entry e = (Map.Entry) uit.next();
			dailyUse.add(shortDay((String) e.getKey()), ((Long) e.getValue()).doubleValue() / 60000.0);
		}
		view.addChart(dailyUse);

		if (plannedMin == 0 && usageMs == 0) {
			view.emptyHint = tr("ReportEngine.content.0034");
		}
		else if (plannedMin > 0 && usageMs < plannedMin * 30000L) {
			view.addDetail(tr("ReportEngine.content.0035"));
		}
		else if (usageMs > plannedMin * 90000L && plannedMin > 0) {
			view.addDetail(tr("ReportEngine.content.0036"));
		}
		view.addDetail(tr("ReportEngine.content.0037") + occ.size() + tr("ReportEngine.content.0038"));
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
		stage(TextUtils.getText("ReportEngine.stage.readingActivity"));
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
			final String mapName = path.length() == 0 ? tr("ReportEngine.content.0039") : new File(path).getName();
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
		view.addKpi(tr("ReportEngine.content.0040"), String.valueOf(sessionCount), tr("ReportEngine.content.0041"));
		view.addKpi(tr("ReportEngine.content.0042"), formatDurationMs(totalEffective), tr("ReportEngine.content.0043"));
		final ReportChartSeries hourChart = new ReportChartSeries(tr("ReportEngine.content.0044"), ReportChartSeries.TYPE_LINE);
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
		final ReportChartSeries mapChart = new ReportChartSeries(tr("ReportEngine.content.0045"), ReportChartSeries.TYPE_PIE);
		final int limit = Math.min(12, mapRows.size());
		for (int i = 0; i < limit; i++) {
			final MapUsageSummary s = (MapUsageSummary) mapRows.get(i);
			mapChart.add(trimLabel(s.getDisplayName(), 16), s.getEffectiveDurationMs() / 60000.0);
			view.addDetail(s.getDisplayName() + " · " + formatDurationMs(s.getEffectiveDurationMs()) + " · "
			        + s.getSessionCount() + tr("ReportEngine.content.0046"));
		}
		view.addChart(mapChart);
		if (sessionCount == 0) {
			view.emptyHint = tr("ReportEngine.content.0047");
		}
		return view;
	}

	private static ReportViewModel viewTimeBlock(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TIME_BLOCK);
		final ReportViewModel view = baseView(def, q);
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		long total = sumPlannedMinutes(occ);
		view.addKpi(tr("ReportEngine.content.0048"), formatHours(total), occ.size() + tr("ReportEngine.content.0049"));
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
		final ReportChartSeries pie = new ReportChartSeries(tr("ReportEngine.content.0050"), ReportChartSeries.TYPE_PIE);
		final ReportChartSeries bar = new ReportChartSeries(tr("ReportEngine.content.0051"), ReportChartSeries.TYPE_BAR);
		for (int i = 0; i < rows.size(); i++) {
			final Object[] row = (Object[]) rows.get(i);
			final String cat = (String) row[0];
			final long mins = ((long[]) row[1])[0];
			final long count = ((long[]) row[1])[1];
			final int pct = total <= 0 ? 0 : (int) Math.round(mins * 100.0 / total);
			pie.add(trimLabel(cat, 14), mins);
			bar.add(trimLabel(cat, 10), mins);
			view.addDetail(cat + " · " + formatHours(mins) + " · " + pct + "% · " + count + tr("ReportEngine.content.0052"));
		}
		view.addChart(pie);
		view.addChart(bar);
		if (occ.isEmpty()) {
			view.emptyHint = tr("ReportEngine.content.0053");
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
		view.addKpi(tr("ReportEngine.content.0048"), formatHours(plannedTotal), tr("ReportEngine.content.0054") + formatHours(plannedTotal / spanDays));
		view.addKpi(tr("ReportEngine.content.0055"), String.valueOf(plannedByDay.size()), tr("ReportEngine.content.0056"));
		view.addKpi(tr("ReportEngine.content.0057"), formatDurationMs(pomoTotal), tr("ReportEngine.content.0012"));
		final ReportChartSeries planLine = new ReportChartSeries(tr("ReportEngine.content.0058"), ReportChartSeries.TYPE_LINE);
		fillLongSeries(planLine, plannedByDay);
		view.addChart(planLine);
		final ReportChartSeries pomoLine = new ReportChartSeries(tr("ReportEngine.content.0059"), ReportChartSeries.TYPE_LINE);
		final Iterator pit = pomoByDay.entrySet().iterator();
		while (pit.hasNext()) {
			final Map.Entry e = (Map.Entry) pit.next();
			pomoLine.add(shortDay((String) e.getKey()), ((Long) e.getValue()).doubleValue() / 60000.0);
		}
		view.addChart(pomoLine);
		if (plannedByDay.isEmpty() && pomoByDay.isEmpty()) {
			view.emptyHint = tr("ReportEngine.content.0060");
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
		view.addKpi(tr("ReportEngine.content.0061"), String.valueOf(load.size()), tr("ReportEngine.content.0062"));
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
		final ReportChartSeries hours = new ReportChartSeries(tr("ReportEngine.content.0063"), ReportChartSeries.TYPE_BAR);
		final ReportChartSeries todos = new ReportChartSeries(tr("ReportEngine.content.0064"), ReportChartSeries.TYPE_PIE);
		final int limit = Math.min(14, rows.size());
		for (int i = 0; i < limit; i++) {
			final Object[] row = (Object[]) rows.get(i);
			final String name = trimLabel((String) row[0], 14);
			hours.add(name, ((Integer) row[3]).doubleValue());
			todos.add(name, ((Integer) row[2]).doubleValue());
			view.addDetail(row[0] + tr("ReportEngine.content.0065") + row[1] + tr("ReportEngine.content.0066") + row[2] + tr("ReportEngine.content.0067")
			        + formatHours(((Integer) row[3]).intValue()));
		}
		view.addChart(hours);
		view.addChart(todos);
		if (rows.isEmpty()) {
			view.emptyHint = tr("ReportEngine.content.0068");
		}
		return view;
	}

	private static ReportViewModel viewOverdue(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_OVERDUE);
		final ReportViewModel view = baseView(def, q);
		final long now = System.currentTimeMillis();
		final List items = filterOneTimeBefore(now);
		view.addKpi(tr("ReportEngine.content.0006"), String.valueOf(items.size()), tr("ReportEngine.content.0069"));
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
		final ReportChartSeries pie = new ReportChartSeries(tr("ReportEngine.content.0070"), ReportChartSeries.TYPE_PIE);
		final Iterator it = byMap.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final List list = (List) entry.getValue();
			pie.add(trimLabel((String) entry.getKey(), 14), list.size());
		}
		view.addChart(pie);
		appendReminderDetails(view, items, 80, true);
		if (items.isEmpty()) {
			view.emptyHint = tr("ReportEngine.content.0071");
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
		view.addKpi(tr("ReportEngine.content.0072"), String.valueOf(urgent.size()), "jinji≥1");
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
		final ReportChartSeries bar = new ReportChartSeries(tr("ReportEngine.content.0073"), ReportChartSeries.TYPE_BAR);
		final Iterator it = byJinji.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry entry = (Map.Entry) it.next();
			final List list = (List) entry.getValue();
			bar.add(tr("ReportEngine.content.0074") + entry.getKey(), list.size());
			view.addDetail(tr("ReportEngine.content.0075") + entry.getKey() + " · " + list.size() + tr("ReportEngine.content.0076"));
			appendOccDetails(view, list, 40);
		}
		view.addChart(bar);
		if (urgent.isEmpty()) {
			view.emptyHint = tr("ReportEngine.content.0077");
		}
		return view;
	}

	private static ReportViewModel viewTodos(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TODOS);
		final ReportViewModel view = baseView(def, q);
		final List todos = MindMapWorkspaceContextScanner.scanAllTodos();
		view.addKpi(tr("ReportEngine.content.0078"), String.valueOf(todos.size()), "hourglass");
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
		final ReportChartSeries pie = new ReportChartSeries(tr("ReportEngine.content.0079"), ReportChartSeries.TYPE_PIE);
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
			view.emptyHint = tr("ReportEngine.content.0080");
		}
		return view;
	}

	private static ReportViewModel viewFlags(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_FLAGS);
		final ReportViewModel view = baseView(def, q);
		final List flags = MindMapWorkspaceContextScanner.scanFlagItems();
		view.addKpi(tr("ReportEngine.content.0081"), String.valueOf(flags.size()), tr("ReportEngine.content.0082"));
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
		final ReportChartSeries pie = new ReportChartSeries(tr("ReportEngine.content.0083"), ReportChartSeries.TYPE_PIE);
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
			view.emptyHint = tr("ReportEngine.content.0084");
		}
		return view;
	}

	private static ReportViewModel viewPomodoro(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_POMODORO);
		final ReportViewModel view = baseView(def, q);
		final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
		if (mgr == null) {
			view.emptyHint = tr("ReportEngine.content.0085");
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
		view.addKpi(tr("ReportEngine.content.0086"), String.valueOf(sessions.size()), tr("ReportEngine.content.0087"));
		view.addKpi(tr("ReportEngine.content.0088"), formatDurationMs(total), tr("ReportEngine.content.0025"));
		final ReportChartSeries dayLine = new ReportChartSeries(tr("ReportEngine.content.0089"), ReportChartSeries.TYPE_LINE);
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
		final ReportChartSeries nodePie = new ReportChartSeries(tr("ReportEngine.content.0090"), ReportChartSeries.TYPE_PIE);
		final int limit = Math.min(12, nodeRows.size());
		for (int i = 0; i < limit; i++) {
			final Object[] row = (Object[]) nodeRows.get(i);
			nodePie.add(trimLabel((String) row[0], 14), ((Long) row[1]).doubleValue() / 60000.0);
			view.addDetail(row[0] + " · " + formatDurationMs(((Long) row[1]).longValue()));
		}
		view.addChart(nodePie);
		if (sessions.isEmpty()) {
			view.emptyHint = tr("ReportEngine.content.0091");
		}
		return view;
	}

	private static ReportViewModel viewDuration(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_DURATION);
		final ReportViewModel view = baseView(def, q);
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		final int[] buckets = new int[6];
		final String[] labels = { tr("ReportEngine.content.0092"), tr("ReportEngine.content.0093"), tr("ReportEngine.content.0094"), tr("ReportEngine.content.0095"), tr("ReportEngine.content.0096"), tr("ReportEngine.content.0097") };
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
		view.addKpi(tr("ReportEngine.content.0098"), String.valueOf(occ.size()), tr("ReportEngine.content.0099"));
		view.addKpi(tr("ReportEngine.content.0100"), String.valueOf(buckets[0]), tr("ReportEngine.content.0101"));
		final ReportChartSeries pie = new ReportChartSeries(tr("ReportEngine.content.0102"), ReportChartSeries.TYPE_PIE);
		final ReportChartSeries bar = new ReportChartSeries(tr("ReportEngine.content.0103"), ReportChartSeries.TYPE_BAR);
		for (int i = 0; i < buckets.length; i++) {
			pie.add(labels[i], buckets[i]);
			bar.add(labels[i], buckets[i]);
			view.addDetail(labels[i] + " · " + buckets[i] + tr("ReportEngine.content.0052"));
		}
		view.addChart(pie);
		view.addChart(bar);
		if (buckets[0] > occ.size() / 2 && occ.size() > 0) {
			view.addDetail(tr("ReportEngine.content.0104"));
		}
		if (occ.isEmpty()) {
			view.emptyHint = tr("ReportEngine.content.0105");
		}
		return view;
	}

	private static ReportViewModel viewTarget(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TARGET);
		final ReportViewModel view = baseView(def, q);
		final List goals = discoverGoalNames();
		view.addKpi(tr("ReportEngine.content.0106"), String.valueOf(goals.size()), tr("ReportEngine.content.0107"));
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		if (goals.isEmpty()) {
			view.emptyHint = tr("ReportEngine.content.0108");
			view.addDetail(tr("ReportEngine.content.0109"));
			return view;
		}
		final ReportChartSeries pie = new ReportChartSeries(tr("ReportEngine.content.0110"), ReportChartSeries.TYPE_PIE);
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
			view.addDetail(goal + " · " + formatHours(mins) + " · " + count + tr("ReportEngine.content.0052"));
		}
		view.addKpi(tr("ReportEngine.content.0111"), formatHours(matchedMinutes), tr("ReportEngine.content.0112"));
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
		view.addKpi(tr("ReportEngine.content.0113"), String.valueOf(filtered.size()), tr("ReportEngine.content.0114"));
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
		final ReportChartSeries dayLine = new ReportChartSeries(tr("ReportEngine.content.0115"), ReportChartSeries.TYPE_LINE);
		final Iterator dit = byDay.entrySet().iterator();
		while (dit.hasNext()) {
			final Map.Entry e = (Map.Entry) dit.next();
			dayLine.add(shortDay((String) e.getKey()), ((Integer) e.getValue()).doubleValue());
		}
		view.addChart(dayLine);
		final ReportChartSeries hourLine = new ReportChartSeries(tr("ReportEngine.content.0116"), ReportChartSeries.TYPE_BAR);
		final Iterator hit = byHour.entrySet().iterator();
		while (hit.hasNext()) {
			final Map.Entry e = (Map.Entry) hit.next();
			hourLine.add((String) e.getKey(), ((Integer) e.getValue()).doubleValue());
		}
		view.addChart(hourLine);
		final ReportChartSeries mapPie = new ReportChartSeries(tr("ReportEngine.content.0117"), ReportChartSeries.TYPE_PIE);
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
			view.emptyHint = tr("ReportEngine.content.0118");
		}
		return view;
	}

	private static ReportViewModel viewKeyboard(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_KEYBOARD);
		final ReportViewModel view = baseView(def, q);
		final KeyboardStats stats = loadKeyboardStats(q);
		if (stats.totalKeys <= 0L) {
			view.emptyHint = tr("ReportEngine.content.0119");
			view.addDetail(tr("ReportEngine.content.0120"));
			return view;
		}
		view.addDetail(stats.sourceNote);
		view.addKpi(tr("ReportEngine.content.0123"), String.valueOf(stats.totalKeys), tr("ReportEngine.content.0124"));
		final ReportChartSeries dayLine = new ReportChartSeries(tr("ReportEngine.content.0125"), ReportChartSeries.TYPE_LINE);
		fillLongSeries(dayLine, stats.byDay);
		view.addChart(dayLine);
		final ReportChartSeries hourBar = new ReportChartSeries(tr("ReportEngine.content.0126"), ReportChartSeries.TYPE_BAR);
		final Iterator hit = stats.byHour.entrySet().iterator();
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
		view.addKpi(tr("ReportEngine.content.0127"), String.valueOf(items.size()), tr("ReportEngine.content.0128"));
		final Map byType = new TreeMap();
		for (int i = 0; i < items.size(); i++) {
			final MindMapWorkspaceContextScanner.ReminderItem item = (MindMapWorkspaceContextScanner.ReminderItem) items
			        .get(i);
			if (!q.matches(item.nodeText, mapLabel(item.mapFile), item.remindType)) {
				continue;
			}
			final String type = item.remindType == null || item.remindType.length() == 0 ? tr("ReportEngine.content.0129") : item.remindType;
			List list = (List) byType.get(type);
			if (list == null) {
				list = new ArrayList();
				byType.put(type, list);
			}
			list.add(item);
		}
		final ReportChartSeries pie = new ReportChartSeries(tr("ReportEngine.content.0130"), ReportChartSeries.TYPE_PIE);
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
			view.emptyHint = tr("ReportEngine.content.0131");
		}
		return view;
	}

	private static ReportViewModel viewPublished(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_PUBLISHED);
		final ReportViewModel view = baseView(def, q);
		final List items = MindMapWorkspaceContextScanner.scanPublishedItems();
		view.addKpi(tr("ReportEngine.content.0132"), String.valueOf(items.size()), tr("ReportEngine.content.0133"));
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
		final ReportChartSeries pie = new ReportChartSeries(tr("ReportEngine.content.0134"), ReportChartSeries.TYPE_PIE);
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
			view.emptyHint = tr("ReportEngine.content.0135");
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
		final StringBuilder title = new StringBuilder(tr("ReportEngine.content.0005")).append(def.title);
		if (def.usesTimeRange && q != null && q.range != null) {
			title.append(" · ").append(q.range.label);
		}
		if (q != null && q.includeKeyword.length() > 0) {
			title.append(tr("ReportEngine.content.0136")).append(q.includeKeyword).append("」");
		}
		if (q != null && q.excludeKeyword.length() > 0) {
			title.append(tr("ReportEngine.content.0137")).append(q.excludeKeyword).append("」");
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
		root.add(tr("ReportEngine.content.0138") + formatHours(total) + " · " + occ.size() + tr("ReportEngine.content.0052"), "info");

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
		final ReportNodeSpec pie = root.add(tr("ReportEngine.content.0139"), "full-7");
		for (int i = 0; i < rows.size(); i++) {
			final Object[] row = (Object[]) rows.get(i);
			final String cat = (String) row[0];
			final long mins = ((long[]) row[1])[0];
			final long count = ((long[]) row[1])[1];
			final int pct = total <= 0 ? 0 : (int) Math.round(mins * 100.0 / total);
			final ReportNodeSpec catNode = pie.add(cat + " · " + formatHours(mins) + " · " + pct + "% · " + count + tr("ReportEngine.content.0140"),
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
			root.add(tr("ReportEngine.content.0141"), "smiley-neutral");
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
			final String mapName = path.length() == 0 ? tr("ReportEngine.content.0039") : new File(path).getName();
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
		final ReportNodeSpec summaryNode = root.add(tr("ReportEngine.content.0142"), "info");
		summaryNode.add(tr("ReportEngine.content.0143") + sessionCount, "list");
		summaryNode.add(tr("ReportEngine.content.0144") + formatDurationMs(totalEffective), "wizard");

		final ReportNodeSpec hourNode = root.add(tr("ReportEngine.content.0145"), "clock");
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
		final ReportNodeSpec maps = root.add(tr("ReportEngine.content.0146"), "folder");
		final int limit = Math.min(40, mapRows.size());
		for (int i = 0; i < limit; i++) {
			final MapUsageSummary s = (MapUsageSummary) mapRows.get(i);
			maps.add(s.getDisplayName() + " · " + formatDurationMs(s.getEffectiveDurationMs()) + " · "
			        + s.getSessionCount() + tr("ReportEngine.content.0046"), "folder");
		}
		if (sessionCount == 0) {
			root.add(tr("ReportEngine.content.0147"), "smiley-neutral");
		}
		return root;
	}

	/** Keystroke rhythm: prefer TwigMark keylog DB; fall back to legacy key.txt. */
	private static ReportNodeSpec keyboardReport(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_KEYBOARD);
		final ReportNodeSpec root = root(def, q, "pencil");
		final KeyboardStats stats = loadKeyboardStats(q);
		if (stats.totalKeys <= 0L) {
			root.add(tr("ReportEngine.content.0148"), "messagebox_warning");
			root.add(tr("ReportEngine.content.0149"), "info");
			return root;
		}
		root.add(stats.sourceNote, "attach");
		root.add(tr("ReportEngine.content.0150") + stats.totalKeys, "info");
		final ReportNodeSpec dayNode = root.add(tr("ReportEngine.content.0151"), "calendar");
		final Iterator dayIt = stats.byDay.entrySet().iterator();
		while (dayIt.hasNext()) {
			final Map.Entry e = (Map.Entry) dayIt.next();
			dayNode.add(e.getKey() + " · " + e.getValue() + tr("ReportEngine.content.0046"), "pencil");
		}
		final ReportNodeSpec hourNode = root.add(tr("ReportEngine.content.0152"), "clock");
		final Iterator hourIt = stats.byHour.entrySet().iterator();
		while (hourIt.hasNext()) {
			final Map.Entry e = (Map.Entry) hourIt.next();
			hourNode.add(e.getKey() + " · " + e.getValue() + tr("ReportEngine.content.0046"), "full-1");
		}
		return root;
	}

	private static final class KeyboardStats {
		long totalKeys;
		final Map byDay = new TreeMap();
		final Map byHour = new TreeMap();
		String sourceNote = "";
	}

	private static KeyboardStats loadKeyboardStats(final ReportQuery q) {
		final KeyboardStats stats = new KeyboardStats();
		final long from = q.range.startMs;
		final long to = q.range.endMs;
		try {
			// Prefer SQLite hour stats. Never fall back to scanning Dropbox key.txt when
			// any keylog-*.db exists — that path can hang for minutes on large archives.
			if (KeyLogService.getInstance().hasAnyDatabase()) {
				notifyProgress((ReportProgress) PROGRESS.get(), -1,
				        TextUtils.getText("ReportEngine.progress.keyboard"));
				final Map hourTs = KeyLogService.getInstance().aggregateByHour(from, to);
				for (final Iterator it = hourTs.entrySet().iterator(); it.hasNext();) {
					final Map.Entry e = (Map.Entry) it.next();
					final long ts = ((Long) e.getKey()).longValue();
					final long count = ((Long) e.getValue()).longValue();
					if (count <= 0L) {
						continue;
					}
					stats.totalKeys += count;
					final Date dt = new Date(ts);
					final String day = DAY.format(dt);
					final Long dayPrev = (Long) stats.byDay.get(day);
					stats.byDay.put(day, Long.valueOf(dayPrev == null ? count : dayPrev.longValue() + count));
					final Calendar cal = Calendar.getInstance();
					cal.setTime(dt);
					final String hour = String.format("%02d:00", Integer.valueOf(cal.get(Calendar.HOUR_OF_DAY)));
					final Long hourPrev = (Long) stats.byHour.get(hour);
					stats.byHour.put(hour, Long.valueOf(hourPrev == null ? count : hourPrev.longValue() + count));
				}
				stats.sourceNote = stats.totalKeys > 0L ? "TwigMark keylog (keylog-*.db)"
				        : "TwigMark keylog（该时段无击键）";
				return stats;
			}
		}
		catch (Exception e) {
			LogUtils.warn("Keylog report failed: " + e.getMessage(), e);
			stats.sourceNote = "keylog error: " + e.getMessage();
			return stats;
		}
		notifyProgress((ReportProgress) PROGRESS.get(), -1, TextUtils.getText("ReportEngine.progress.keyboard"));
		return loadKeyboardStatsFromLegacyTxt(q, stats);
	}

	/** Legacy DocearReminder key.txt: count semicolon-separated keys; time from line stamp. */
	private static KeyboardStats loadKeyboardStatsFromLegacyTxt(final ReportQuery q, final KeyboardStats stats) {
		final List keyFiles = findKeyLogFiles();
		if (keyFiles.isEmpty()) {
			return stats;
		}
		final StringBuffer note = new StringBuffer("legacy key.txt ×").append(keyFiles.size());
		for (int f = 0; f < keyFiles.size(); f++) {
			final File file = (File) keyFiles.get(f);
			try {
				final java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
				        new java.io.FileInputStream(file), "UTF-8"));
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.length() < 16) {
						continue;
					}
					final ParsedKeyLine parsed = parseLegacyKeyLine(line);
					if (parsed == null || !q.range.contains(parsed.ts)) {
						continue;
					}
					if (!q.matches(parsed.body, file.getName(), "")) {
						continue;
					}
					final int keyCount = countLegacyKeys(parsed.body);
					if (keyCount <= 0) {
						continue;
					}
					stats.totalKeys += keyCount;
					final Date dt = new Date(parsed.ts);
					final String day = DAY.format(dt);
					final Long dayPrev = (Long) stats.byDay.get(day);
					stats.byDay.put(day, Long.valueOf(dayPrev == null ? keyCount : dayPrev.longValue() + keyCount));
					final Calendar cal = Calendar.getInstance();
					cal.setTime(dt);
					final String hour = String.format("%02d:00", Integer.valueOf(cal.get(Calendar.HOUR_OF_DAY)));
					final Long hourPrev = (Long) stats.byHour.get(hour);
					stats.byHour.put(hour, Long.valueOf(hourPrev == null ? keyCount : hourPrev.longValue() + keyCount));
				}
				reader.close();
			}
			catch (Exception e) {
				note.append("; error ").append(file.getName());
			}
		}
		stats.sourceNote = note.toString();
		return stats;
	}

	private static final class ParsedKeyLine {
		long ts;
		String body;
	}

	private static ParsedKeyLine parseLegacyKeyLine(final String line) {
		// 2025/11/03 09:54:53KEYS...  or 2025/11/03 09:54:53   KEYS
		java.util.regex.Matcher m = java.util.regex.Pattern
		        .compile("^(\\d{4}/\\d{1,2}/\\d{1,2} \\d{1,2}:\\d{2}:\\d{2})\\s*(.*)$").matcher(line);
		if (!m.find()) {
			m = java.util.regex.Pattern.compile("^(\\d{1,2}/\\d{1,2}/\\d{4} \\d{1,2}:\\d{2}:\\d{2} [AP]M)\\s*(.*)$",
			        java.util.regex.Pattern.CASE_INSENSITIVE).matcher(line);
			if (!m.find()) {
				return null;
			}
		}
		try {
			Date dt;
			try {
				dt = new SimpleDateFormat("yyyy/M/d H:mm:ss", Locale.CHINA).parse(m.group(1));
			}
			catch (Exception e1) {
				dt = new SimpleDateFormat("M/d/yyyy h:mm:ss a", Locale.US).parse(m.group(1));
			}
			final ParsedKeyLine parsed = new ParsedKeyLine();
			parsed.ts = dt.getTime();
			parsed.body = m.group(2) == null ? "" : m.group(2).trim();
			return parsed;
		}
		catch (Exception e) {
			return null;
		}
	}

	private static int countLegacyKeys(final String body) {
		if (body == null || body.length() == 0) {
			return 0;
		}
		int n = 0;
		int start = 0;
		for (int i = 0; i <= body.length(); i++) {
			if (i == body.length() || body.charAt(i) == ';') {
				if (i > start) {
					n++;
				}
				start = i + 1;
			}
		}
		return n;
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
		final ReportNodeSpec summary = root.add(tr("ReportEngine.content.0142"), "info");
		summary.add(tr("ReportEngine.content.0153") + formatHours(plannedTotal), "clock");
		summary.add(tr("ReportEngine.content.0154") + spanDays + "）· " + formatHours(plannedTotal / spanDays), "full-3");
		summary.add(tr("ReportEngine.content.0155") + plannedByDay.size() + tr("ReportEngine.content.0156")
		        + formatHours(plannedByDay.isEmpty() ? 0 : plannedTotal / plannedByDay.size()), "full-5");
		summary.add(tr("ReportEngine.content.0157") + formatDurationMs(pomoTotal), "clock2");

		final ReportNodeSpec planned = root.add(tr("ReportEngine.content.0158"), "calendar");
		final Iterator it = plannedByDay.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry e = (Map.Entry) it.next();
			planned.add(e.getKey() + " · " + formatHours(((Long) e.getValue()).longValue()), "up");
		}
		final ReportNodeSpec pomo = root.add(tr("ReportEngine.content.0159"), "clock2");
		final Iterator pit = pomoByDay.entrySet().iterator();
		while (pit.hasNext()) {
			final Map.Entry e = (Map.Entry) pit.next();
			pomo.add(e.getKey() + " · " + formatDurationMs(((Long) e.getValue()).longValue()), "up");
		}
		if (plannedByDay.isEmpty() && pomoByDay.isEmpty()) {
			root.add(tr("ReportEngine.content.0160"), "smiley-neutral");
		}
		return root;
	}

	/** DocearReminder Target：扫描「目标」节点并匹配安排. */
	private static ReportNodeSpec targetReport(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TARGET);
		final ReportNodeSpec root = root(def, q, "launch");
		final List goals = discoverGoalNames();
		root.add(tr("ReportEngine.content.0161") + goals.size() + tr("ReportEngine.content.0162"), "info");
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		if (goals.isEmpty()) {
			root.add(tr("ReportEngine.content.0163"), "messagebox_warning");
			root.add(tr("ReportEngine.content.0164"), "info");
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
			final ReportNodeSpec goalNode = root.add(goal + " · " + formatHours(mins) + " · " + count + tr("ReportEngine.content.0052"), "launch");
			addOccurrences(goalNode, matched, 20);
		}
		root.add(tr("ReportEngine.content.0165") + formatHours(matchedMinutes), "clock");
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
		root.add(tr("ReportEngine.content.0166") + filtered.size(), "info");

		final Map byDay = new TreeMap();
		final Map byHour = new TreeMap();
		final Map byWeekday = new TreeMap();
		final Map byMap = new TreeMap();
		final String[] weekNames = { tr("ReportEngine.content.0167"), tr("ReportEngine.content.0168"), tr("ReportEngine.content.0169"), tr("ReportEngine.content.0170"), tr("ReportEngine.content.0171"), tr("ReportEngine.content.0172"), tr("ReportEngine.content.0173") };
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

		final ReportNodeSpec dayNode = root.add(tr("ReportEngine.content.0174"), "calendar");
		addIntMap(dayNode, byDay, "full-4");
		final ReportNodeSpec hourNode = root.add(tr("ReportEngine.content.0152"), "clock");
		final Iterator hit = byHour.entrySet().iterator();
		while (hit.hasNext()) {
			final Map.Entry e = (Map.Entry) hit.next();
			hourNode.add(String.format("%02d:00", ((Integer) e.getKey()).intValue()) + " · " + e.getValue() + tr("ReportEngine.content.0046"),
			        "full-2");
		}
		final ReportNodeSpec weekNode = root.add(tr("ReportEngine.content.0175"), "prepare");
		addIntMap(weekNode, byWeekday, "full-3");
		final ReportNodeSpec mapNode = root.add(tr("ReportEngine.content.0176"), "folder");
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
			mapNode.add(row[0] + " · " + c + tr("ReportEngine.content.0177") + pct + "%", "folder");
		}
		if (filtered.isEmpty()) {
			root.add(tr("ReportEngine.content.0178"), "smiley-neutral");
		}
		return root;
	}

	private static void addIntMap(final ReportNodeSpec parent, final Map map, final String icon) {
		final Iterator it = map.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry e = (Map.Entry) it.next();
			parent.add(e.getKey() + " · " + e.getValue() + tr("ReportEngine.content.0046"), icon);
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
			return tr("ReportEngine.content.0179");
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
		final boolean isGoalParent = text.equals(tr("ReportEngine.content.0180")) || text.startsWith(tr("ReportEngine.content.0180"));
		if (underGoal && text.length() > 0 && !text.equals(tr("ReportEngine.content.0180")) && !seen.contains(text)) {
			seen.add(text);
			goals.add(text);
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			collectGoals((NodeModel) node.getChildAt(i), underGoal || isGoalParent, goals, seen);
		}
	}

	private static List findKeyLogFiles() {
		// Legacy fallback only: keep the scan tiny (data dir). Never walk Dropbox / all scan roots.
		final List out = new ArrayList();
		try {
			collectNamedFiles(new File(org.freeplane.core.util.Compat.getApplicationUserDirectory()), "key.txt", out, 0);
		}
		catch (Exception e) {
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

		final ReportNodeSpec summary = root.add(tr("ReportEngine.content.0142"), "info");
		summary.add(tr("ReportEngine.content.0181") + overdue.size() + tr("ReportEngine.content.0052"), "messagebox_warning");
		summary.add(tr("ReportEngine.content.0182") + occ.size() + tr("ReportEngine.content.0183") + formatHours(planned), "calendar");
		summary.add(tr("ReportEngine.content.0184") + todos.size() + tr("ReportEngine.content.0052"), "hourglass");
		summary.add(tr("ReportEngine.content.0185") + formatDurationMs(pomoMs), "clock2");

		final ReportNodeSpec overdueNode = root.add(tr("ReportEngine.content.0186"), "messagebox_warning");
		addReminderItems(overdueNode, overdue, 12, true);

		final ReportNodeSpec todayNode = root.add(tr("ReportEngine.content.0008"), "calendar");
		addOccurrences(todayNode, occ, 40);

		return root;
	}

	private static ReportNodeSpec workHours(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_WORK_HOURS);
		final ReportNodeSpec root = root(def, q, "clock");
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		final long totalMin = sumPlannedMinutes(occ);
		final ReportNodeSpec summary = root.add(tr("ReportEngine.content.0142"), "info");
		summary.add(tr("ReportEngine.content.0187") + occ.size(), "list");
		summary.add(tr("ReportEngine.content.0188") + formatHours(totalMin), "clock");
		summary.add(tr("ReportEngine.content.0189") + countWithMinutes(occ), "full-3");

		final Map byDay = groupOccByDay(occ);
		final ReportNodeSpec byDayNode = root.add(tr("ReportEngine.content.0151"), "calendar");
		final List dayKeys = new ArrayList(byDay.keySet());
		Collections.sort(dayKeys);
		for (int i = 0; i < dayKeys.size(); i++) {
			final String day = (String) dayKeys.get(i);
			final List dayList = (List) byDay.get(day);
			final long mins = sumPlannedMinutes(dayList);
			final ReportNodeSpec dayNode = byDayNode.add(day + " · " + formatHours(mins) + " · " + dayList.size() + tr("ReportEngine.content.0052"),
			        "full-" + Math.min(9, Math.max(1, (int) (mins / 60) + 1)));
			addOccurrences(dayNode, dayList, 30);
		}

		final Map byMap = groupOccByMap(occ);
		final ReportNodeSpec byMapNode = root.add(tr("ReportEngine.content.0190"), "folder");
		final List mapEntries = sortedMapEntriesByMinutes(byMap);
		for (int i = 0; i < mapEntries.size(); i++) {
			final Object[] row = (Object[]) mapEntries.get(i);
			final String mapName = (String) row[0];
			final List mapList = (List) row[1];
			final long mins = ((Long) row[2]).longValue();
			final ReportNodeSpec mapNode = byMapNode.add(mapName + " · " + formatHours(mins) + " · " + mapList.size()
			        + tr("ReportEngine.content.0052"), "folder");
			addOccurrences(mapNode, mapList, 40);
		}
		if (occ.isEmpty()) {
			root.add(tr("ReportEngine.content.0191"), "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec scheduleList(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_SCHEDULE);
		final ReportNodeSpec root = root(def, q, "calendar");
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		root.add(tr("ReportEngine.content.0192") + occ.size() + tr("ReportEngine.content.0193"), "info");
		final Map byDay = groupOccByDay(occ);
		final List dayKeys = new ArrayList(byDay.keySet());
		Collections.sort(dayKeys);
		for (int i = 0; i < dayKeys.size(); i++) {
			final String day = (String) dayKeys.get(i);
			final List dayList = (List) byDay.get(day);
			final ReportNodeSpec dayNode = root.add(day + " · " + dayList.size() + tr("ReportEngine.content.0052"), "calendar");
			addOccurrences(dayNode, dayList, 80);
		}
		if (occ.isEmpty()) {
			root.add(tr("ReportEngine.content.0191"), "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec overdue(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_OVERDUE);
		final ReportNodeSpec root = root(def, q, "messagebox_warning");
		final long now = System.currentTimeMillis();
		final List items = filterOneTimeBefore(now);
		root.add(tr("ReportEngine.content.0194") + items.size() + tr("ReportEngine.content.0195") + TIME.format(new Date(now)), "info");
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
			final ReportNodeSpec mapNode = root.add(entry.getKey() + " · " + ((List) entry.getValue()).size() + tr("ReportEngine.content.0052"),
			        "folder");
			addReminderItems(mapNode, (List) entry.getValue(), 100, true);
		}
		if (items.isEmpty()) {
			root.add(tr("ReportEngine.content.0196"), "button_ok");
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
		root.add(tr("ReportEngine.content.0197") + urgent.size() + tr("ReportEngine.content.0198"), "info");
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
			final ReportNodeSpec level = root.add(tr("ReportEngine.content.0074") + entry.getKey() + " · " + ((List) entry.getValue()).size()
			        + tr("ReportEngine.content.0052"), "flag");
			addOccurrences(level, (List) entry.getValue(), 60);
		}
		if (urgent.isEmpty()) {
			root.add(tr("ReportEngine.content.0199"), "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec recurringLedger(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_RECURRING);
		final ReportNodeSpec root = root(def, q, "prepare");
		final List items = MindMapWorkspaceContextScanner.scanRecurringReminders();
		root.add(tr("ReportEngine.content.0200") + items.size() + tr("ReportEngine.content.0052"), "info");
		final Map byType = new TreeMap();
		for (int i = 0; i < items.size(); i++) {
			final MindMapWorkspaceContextScanner.ReminderItem item = (MindMapWorkspaceContextScanner.ReminderItem) items
			        .get(i);
			final String type = item.remindType == null || item.remindType.length() == 0 ? tr("ReportEngine.content.0129") : item.remindType;
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
			final ReportNodeSpec typeNode = root.add(entry.getKey() + " · " + ((List) entry.getValue()).size() + tr("ReportEngine.content.0052"),
			        "prepare");
			addReminderItems(typeNode, (List) entry.getValue(), 80, false);
		}
		if (items.isEmpty()) {
			root.add(tr("ReportEngine.content.0201"), "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec todoSummary(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_TODOS);
		final ReportNodeSpec root = root(def, q, "hourglass");
		final List todos = MindMapWorkspaceContextScanner.scanAllTodos();
		root.add(tr("ReportEngine.content.0202") + todos.size() + tr("ReportEngine.content.0052"), "info");
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
			final ReportNodeSpec mapNode = root.add(entry.getKey() + " · " + list.size() + tr("ReportEngine.content.0052"), "folder");
			for (int i = 0; i < list.size(); i++) {
				final MindMapWorkspaceContextScanner.TodoItem item = (MindMapWorkspaceContextScanner.TodoItem) list
				        .get(i);
				mapNode.add(item.nodeText, "hourglass");
			}
		}
		if (todos.isEmpty()) {
			root.add(tr("ReportEngine.content.0203"), "button_ok");
		}
		return root;
	}

	private static ReportNodeSpec flagItems(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_FLAGS);
		final ReportNodeSpec root = root(def, q, "flag");
		final List flags = MindMapWorkspaceContextScanner.scanFlagItems();
		root.add(tr("ReportEngine.content.0204") + flags.size() + tr("ReportEngine.content.0052"), "info");
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
			final ReportNodeSpec mapNode = root.add(entry.getKey() + " · " + list.size() + tr("ReportEngine.content.0052"), "folder");
			for (int i = 0; i < list.size(); i++) {
				final MindMapWorkspaceContextScanner.IconItem item = (MindMapWorkspaceContextScanner.IconItem) list
				        .get(i);
				mapNode.add(item.nodeText, "flag");
			}
		}
		if (flags.isEmpty()) {
			root.add(tr("ReportEngine.content.0205"), "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec pomodoroReport(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_POMODORO);
		final ReportNodeSpec root = root(def, q, "clock2");
		final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
		if (mgr == null) {
			root.add(tr("ReportEngine.content.0206"), "messagebox_warning");
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
		final ReportNodeSpec summary = root.add(tr("ReportEngine.content.0142"), "info");
		summary.add(tr("ReportEngine.content.0207") + sessions.size(), "list");
		summary.add(tr("ReportEngine.content.0208") + formatDurationMs(total), "clock2");

		final ReportNodeSpec dayNode = root.add(tr("ReportEngine.content.0151"), "calendar");
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
		final ReportNodeSpec nodeRoot = root.add(tr("ReportEngine.content.0209"), "group");
		final int limit = Math.min(40, nodeRows.size());
		for (int i = 0; i < limit; i++) {
			final Object[] row = (Object[]) nodeRows.get(i);
			nodeRoot.add(row[0] + " · " + formatDurationMs(((Long) row[1]).longValue()), "clock2");
		}
		if (sessions.isEmpty()) {
			root.add(tr("ReportEngine.content.0210"), "smiley-neutral");
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
		root.add(tr("ReportEngine.content.0211") + load.size(), "info");
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
			root.add(row[0] + tr("ReportEngine.content.0065") + row[1] + tr("ReportEngine.content.0066") + row[2] + tr("ReportEngine.content.0067") + formatHours(((Integer) row[3]).intValue()),
			        "folder");
		}
		if (rows.isEmpty()) {
			root.add(tr("ReportEngine.content.0212"), "smiley-neutral");
		}
		return root;
	}

	private static ReportNodeSpec durationBuckets(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_DURATION_BUCKETS);
		final ReportNodeSpec root = root(def, q, "full-5");
		final List occ = filterOcc(loadOccurrences(q.range.startMs, q.range.endMs), q);
		final int[] buckets = new int[6];
		final String[] labels = { tr("ReportEngine.content.0092"), tr("ReportEngine.content.0093"), tr("ReportEngine.content.0094"), tr("ReportEngine.content.0095"), tr("ReportEngine.content.0096"), tr("ReportEngine.content.0097") };
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
		root.add(tr("ReportEngine.content.0213") + occ.size() + tr("ReportEngine.content.0049"), "info");
		for (int i = 0; i < buckets.length; i++) {
			root.add(labels[i] + " · " + buckets[i] + tr("ReportEngine.content.0052"), icons[i]);
		}
		return root;
	}


	private static ReportNodeSpec publishedItems(final ReportQuery q) {
		final ReportDefinition def = ReportCatalog.byId(ReportCatalog.ID_PUBLISHED);
		final ReportNodeSpec root = root(def, q, "internet");
		final List items = MindMapWorkspaceContextScanner.scanPublishedItems();
		root.add(tr("ReportEngine.content.0214") + items.size() + tr("ReportEngine.content.0052"), "info");
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
			final ReportNodeSpec mapNode = root.add(entry.getKey() + " · " + list.size() + tr("ReportEngine.content.0052"), "folder");
			for (int i = 0; i < list.size(); i++) {
				final MindMapWorkspaceContextScanner.IconItem item = (MindMapWorkspaceContextScanner.IconItem) list
				        .get(i);
				mapNode.add(item.nodeText, "internet");
			}
		}
		if (items.isEmpty()) {
			root.add(tr("ReportEngine.content.0215"), "smiley-neutral");
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
		root.add(tr("ReportEngine.content.0216") + filtered.size() + tr("ReportEngine.content.0052"), "info");
		final int limit = Math.min(80, filtered.size());
		for (int i = 0; i < limit; i++) {
			final MindMapWorkspaceContextScanner.ModifiedItem item = (MindMapWorkspaceContextScanner.ModifiedItem) filtered
			        .get(i);
			root.add(TIME.format(new Date(item.modifiedAt)) + "  " + item.nodeText + "  〔" + mapLabel(item.mapFile)
			        + "〕", "up");
		}
		if (filtered.isEmpty()) {
			root.add(tr("ReportEngine.content.0178"), "smiley-neutral");
		}
		return root;
	}

	private static List loadOccurrences(final long start, final long end) {
		stage(TextUtils.getText("ReportEngine.stage.loadingOccurrences"));
		final ReminderCalendarBridge.LoadBundle bundle = ReminderCalendarBridge.loadBundle(start, end, start, end);
		final List list = new ArrayList(bundle.occurrences);
		Collections.sort(list, occComparator());
		stage(TextUtils.format("ReportEngine.stage.occurrencesLoaded", Integer.valueOf(list.size())));
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
		stage(TextUtils.getText("ReportEngine.stage.scanningOneTime"));
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
				sb.append(" · ").append(ref.taskTimeMinutes).append(tr("ReportEngine.content.0217"));
			}
			if (ref.jinji > 0) {
				sb.append(tr("ReportEngine.content.0218")).append(ref.jinji);
			}
			if (ref.recurring) {
				sb.append(tr("ReportEngine.content.0219"));
			}
			sb.append("  ").append(ref.nodeText == null ? "" : ref.nodeText);
			sb.append("  〔").append(mapLabel(ref.file)).append("〕");
			final String icon = ref.jinji >= 2 ? "flag" : (ref.recurring ? "prepare" : "clock");
			parent.add(sb.toString(), icon);
		}
		if (occ.size() > limit) {
			parent.add(tr("ReportEngine.content.0220") + (occ.size() - limit) + tr("ReportEngine.content.0052"), "list");
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
				sb.append(tr("ReportEngine.content.0221")).append(days).append(tr("ReportEngine.content.0222"));
			}
			if (item.remindType != null && item.remindType.length() > 0) {
				sb.append(" · ").append(item.remindType);
			}
			sb.append("  ").append(item.nodeText == null ? "" : item.nodeText);
			sb.append("  〔").append(mapLabel(item.mapFile)).append("〕");
			parent.add(sb.toString(), item.recurring ? "prepare" : "messagebox_warning");
		}
		if (items.size() > limit) {
			parent.add(tr("ReportEngine.content.0220") + (items.size() - limit) + tr("ReportEngine.content.0052"), "list");
		}
	}

	private static String mapLabel(final File file) {
		if (file == null) {
			return tr("ReportEngine.content.0223");
		}
		final String name = file.getName();
		return name == null || name.length() == 0 ? file.getAbsolutePath() : name;
	}

	private static String nodeLabel(final NodeModel node) {
		if (node == null) {
			return tr("ReportEngine.content.0224");
		}
		try {
			final String text = org.freeplane.features.text.TextController.getController().getPlainTextContent(node);
			if (text != null && text.trim().length() > 0) {
				return text.replaceAll("\\s+", " ").trim();
			}
		}
		catch (Exception e) {
		}
		return node.getText() == null ? tr("ReportEngine.content.0224") : node.getText();
	}

	private static String formatHours(final long minutes) {
		if (minutes <= 0) {
			return tr("ReportEngine.content.0225");
		}
		final long h = minutes / 60;
		final long m = minutes % 60;
		if (h <= 0) {
			return m + tr("ReportEngine.content.0217");
		}
		if (m == 0) {
			return h + tr("ReportEngine.content.0226");
		}
		return h + tr("ReportEngine.content.0226") + m + tr("ReportEngine.content.0217");
	}

	private static String formatDurationMs(final long ms) {
		if (ms <= 0) {
			return tr("ReportEngine.content.0225");
		}
		final long minutes = Math.round(ms / 60000.0);
		return formatHours(minutes);
	}
}
