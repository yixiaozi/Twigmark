package org.freeplane.view.swing.features.finance;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.view.swing.features.reports.ReportChartSeries;
import org.freeplane.view.swing.features.reports.ReportViewModel;
import org.freeplane.core.util.TextUtils;

/**
 * Builds finance {@link ReportViewModel}s for the center viewport.
 */
public final class FinanceReportEngine {
	public static final String ID_MONTH_OVERVIEW = "month_overview";
	public static final String ID_EXPENSE_BY_CATEGORY = "expense_by_category";
	public static final String ID_INCOME_BY_CATEGORY = "income_by_category";
	public static final String ID_TREND = "trend";
	public static final String ID_BUDGET_STATUS = "budget_status";
	public static final String ID_SUBSCRIPTIONS = "subscriptions";
	public static final String ID_COUPONS = "coupons";

	private FinanceReportEngine() {
	}

	public static ReportViewModel generateView(final String reportId, final String fromYmd, final String toYmd) {
		final String id = reportId == null ? "" : reportId.trim();
		try {
			if (ID_MONTH_OVERVIEW.equals(id)) {
				return viewMonthOverview(fromYmd, toYmd);
			}
			if (ID_EXPENSE_BY_CATEGORY.equals(id)) {
				return viewByCategory(fromYmd, toYmd, FinanceAttributes.FLOW_EXPENSE, TextUtils.getText("finance.report.title.expense_by_cat"));
			}
			if (ID_INCOME_BY_CATEGORY.equals(id)) {
				return viewByCategory(fromYmd, toYmd, FinanceAttributes.FLOW_INCOME, TextUtils.getText("finance.report.title.income_by_cat"));
			}
			if (ID_TREND.equals(id)) {
				return viewTrend(fromYmd, toYmd);
			}
			if (ID_BUDGET_STATUS.equals(id)) {
				return viewBudgetStatus(fromYmd, toYmd);
			}
			if (ID_SUBSCRIPTIONS.equals(id)) {
				return viewSubscriptions();
			}
			if (ID_COUPONS.equals(id)) {
				return viewCoupons();
			}
			final ReportViewModel unknown = new ReportViewModel(TextUtils.getText("finance.report.title.generic"), TextUtils.format("finance.report.unknown_type", id));
			unknown.emptyHint = TextUtils.getText("finance.report.empty_hint_pick");
			unknown.addDetail(TextUtils.format("finance.report.unknown_report", id));
			return unknown;
		}
		catch (Exception e) {
			LogUtils.warn("FinanceReportEngine.generateView failed: " + id, e);
			final ReportViewModel fail = new ReportViewModel(TextUtils.getText("finance.report.title.failed"), id);
			fail.addDetail(String.valueOf(e.getMessage()));
			fail.emptyHint = TextUtils.getText("finance.report.empty_hint_retry");
			return fail;
		}
	}

	private static ReportViewModel viewMonthOverview(final String fromYmd, final String toYmd) {
		final String period = periodFromRange(fromYmd, toYmd);
		final ReportViewModel view = new ReportViewModel(TextUtils.getText("finance.report.title.month"), rangeLabel(fromYmd, toYmd));
		view.decision = TextUtils.getText("finance.report.decision.month");
		view.dataSource = TextUtils.getText("finance.report.source.txns");
		final FinanceLedgerService.MonthSummary summary = FinanceLedgerService.monthSummary(period);
		final long income = summary.incomeCents;
		final long expense = summary.expenseCents;
		final long net = summary.pnlNetCents();
		view.addKpi(TextUtils.getText("finance.flow.income"), "¥" + FinanceAttributes.formatYuan(income), TextUtils.getText("finance.report.kpi.pnl"));
		view.addKpi(TextUtils.getText("finance.flow.expense"), "¥" + FinanceAttributes.formatYuan(expense), TextUtils.getText("finance.report.kpi.pnl"));
		view.addKpi(TextUtils.getText("finance.kpi.net"), "¥" + FinanceAttributes.formatYuan(net), net >= 0 ? TextUtils.getText("finance.report.kpi.surplus") : TextUtils.getText("finance.report.kpi.deficit"));
		if (summary.borrowCents + summary.lendCents + summary.creditCents + summary.transferCents > 0L) {
			view.addKpi(TextUtils.getText("finance.flow.borrow"), "¥" + FinanceAttributes.formatYuan(summary.borrowCents), TextUtils.getText("finance.report.kpi.liability_in"));
			view.addKpi(TextUtils.getText("finance.flow.lend"), "¥" + FinanceAttributes.formatYuan(summary.lendCents), TextUtils.getText("finance.report.kpi.receivable_out"));
			view.addKpi(TextUtils.getText("finance.flow.credit"), "¥" + FinanceAttributes.formatYuan(summary.creditCents), TextUtils.getText("finance.report.kpi.credit_use"));
			view.addKpi(TextUtils.getText("finance.flow.transfer"), "¥" + FinanceAttributes.formatYuan(summary.transferCents), TextUtils.getText("finance.report.kpi.not_pnl"));
		}

		final ReportChartSeries pie = new ReportChartSeries(TextUtils.getText("finance.report.chart.expense_by_cat"), ReportChartSeries.TYPE_PIE);
		final Iterator it = summary.byCategory.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry e = (Map.Entry) it.next();
			pie.add(String.valueOf(e.getKey()), ((Long) e.getValue()).doubleValue() / 100.0);
			view.addDetail(e.getKey() + " · ¥" + FinanceAttributes.formatYuan(((Long) e.getValue()).longValue()));
		}
		view.addChart(pie);

		final ReportChartSeries byDay = new ReportChartSeries(TextUtils.getText("finance.report.chart.daily_expense"), ReportChartSeries.TYPE_BAR);
		final Map dayExpense = sumByDay(fromYmd, toYmd, FinanceAttributes.FLOW_EXPENSE);
		final Iterator dit = dayExpense.entrySet().iterator();
		while (dit.hasNext()) {
			final Map.Entry e = (Map.Entry) dit.next();
			byDay.add(shortDay((String) e.getKey()), ((Long) e.getValue()).doubleValue() / 100.0);
		}
		view.addChart(byDay);

		if (income == 0L && expense == 0L) {
			view.emptyHint = TextUtils.getText("finance.report.empty.month");
		}
		return view;
	}

	private static ReportViewModel viewByCategory(final String fromYmd, final String toYmd, final String flow,
			final String title) {
		final ReportViewModel view = new ReportViewModel(TextUtils.format("finance.report.title.prefixed", title), rangeLabel(fromYmd, toYmd));
		view.decision = TextUtils.getText("finance.report.decision.top_cat");
		view.dataSource = TextUtils.getText("finance.report.source.txns");
		final Map byCat = new TreeMap();
		long total = 0L;
		final List txns = FinanceLedgerService.listTransactions(fromYmd, toYmd);
		for (int i = 0; i < txns.size(); i++) {
			final FinanceLedgerService.FinanceTxn txn = (FinanceLedgerService.FinanceTxn) txns.get(i);
			if (!flow.equals(txn.flow)) {
				continue;
			}
			final long cents = Math.abs(txn.amountCents);
			total += cents;
			final String cat = txn.categoryName == null || txn.categoryName.length() == 0 ? TextUtils.getText("finance.category.other") : txn.categoryName;
			final Long prev = (Long) byCat.get(cat);
			byCat.put(cat, Long.valueOf((prev == null ? 0L : prev.longValue()) + cents));
		}
		view.addKpi(TextUtils.getText("finance.report.kpi.total"), "¥" + FinanceAttributes.formatYuan(total), title);
		view.addKpi(TextUtils.getText("finance.report.kpi.cat_count"), String.valueOf(byCat.size()), "");
		final ReportChartSeries pie = new ReportChartSeries(TextUtils.format("finance.report.chart.yuan_suffix", title), ReportChartSeries.TYPE_PIE);
		final Iterator it = byCat.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry e = (Map.Entry) it.next();
			final long cents = ((Long) e.getValue()).longValue();
			pie.add(String.valueOf(e.getKey()), cents / 100.0);
			final int pct = total <= 0 ? 0 : (int) Math.round(cents * 100.0 / total);
			view.addDetail(e.getKey() + " · ¥" + FinanceAttributes.formatYuan(cents) + " · " + pct + "%");
		}
		view.addChart(pie);
		if (byCat.isEmpty()) {
			view.emptyHint = TextUtils.format("finance.report.empty.no_data", title);
		}
		return view;
	}

	private static ReportViewModel viewTrend(final String fromYmd, final String toYmd) {
		final ReportViewModel view = new ReportViewModel(TextUtils.getText("finance.report.title.trend"), rangeLabel(fromYmd, toYmd));
		view.decision = TextUtils.getText("finance.report.decision.trend");
		view.dataSource = TextUtils.getText("finance.report.source.txns");
		final Map dayExpense = sumByDay(fromYmd, toYmd, FinanceAttributes.FLOW_EXPENSE);
		final Map dayIncome = sumByDay(fromYmd, toYmd, FinanceAttributes.FLOW_INCOME);
		long expenseTotal = sumMap(dayExpense);
		long incomeTotal = sumMap(dayIncome);
		view.addKpi(TextUtils.getText("finance.report.kpi.expense_total"), "¥" + FinanceAttributes.formatYuan(expenseTotal), "");
		view.addKpi(TextUtils.getText("finance.report.kpi.income_total"), "¥" + FinanceAttributes.formatYuan(incomeTotal), "");
		final ReportChartSeries expenseLine = new ReportChartSeries(TextUtils.getText("finance.report.chart.daily_expense"), ReportChartSeries.TYPE_LINE);
		fillDaySeries(expenseLine, dayExpense);
		view.addChart(expenseLine);
		final ReportChartSeries incomeLine = new ReportChartSeries(TextUtils.getText("finance.report.chart.daily_income"), ReportChartSeries.TYPE_LINE);
		fillDaySeries(incomeLine, dayIncome);
		view.addChart(incomeLine);
		if (dayExpense.isEmpty() && dayIncome.isEmpty()) {
			view.emptyHint = TextUtils.getText("finance.report.empty.trend");
		}
		return view;
	}

	private static ReportViewModel viewBudgetStatus(final String fromYmd, final String toYmd) {
		final String period = periodFromRange(fromYmd, toYmd);
		final ReportViewModel view = new ReportViewModel(TextUtils.getText("finance.report.title.budget"), period);
		view.decision = TextUtils.getText("finance.report.decision.budget");
		view.dataSource = TextUtils.getText("finance.report.source.budget");
		final List budgets = FinanceLedgerService.listBudgets(period);
		final List txns = FinanceLedgerService.listTransactions(fromYmd, toYmd);
		final MapModel map = FinanceLedgerService.preferOpenFinanceMapPublic();
		view.addKpi(TextUtils.getText("finance.report.kpi.budget_items"), String.valueOf(budgets.size()), period);
		view.addKpi(TextUtils.getText("finance.report.kpi.actual_expense"), "¥" + FinanceAttributes.formatYuan(
				FinanceNodeRef.sumExpenseForBudgetCategory(map, FinanceNodeRef.TOTAL_BUDGET_NODE_ID, txns)), TextUtils.getText("finance.report.kpi.pnl_expense"));
		final ReportChartSeries bar = new ReportChartSeries(TextUtils.getText("finance.report.chart.budget_vs"), ReportChartSeries.TYPE_BAR);
		for (int i = 0; i < budgets.size(); i++) {
			final FinanceLedgerService.FinanceBudget b = (FinanceLedgerService.FinanceBudget) budgets.get(i);
			final long spent = FinanceNodeRef.sumExpenseForBudgetCategory(map, b.categoryNodeId, txns);
			bar.add(TextUtils.format("finance.report.budget_label", b.categoryName), b.amountCents / 100.0);
			bar.add(TextUtils.format("finance.report.spent_label", b.categoryName), spent / 100.0);
			view.addDetail(TextUtils.format("finance.report.detail.budget_line", b.categoryName, FinanceAttributes.formatYuan(b.amountCents),
					FinanceAttributes.formatYuan(spent), FinanceAttributes.formatYuan(b.amountCents - spent)));
		}
		view.addChart(bar);
		if (budgets.isEmpty()) {
			view.emptyHint = TextUtils.getText("finance.report.empty.budget");
		}
		return view;
	}

	private static ReportViewModel viewSubscriptions() {
		final ReportViewModel view = new ReportViewModel(TextUtils.getText("finance.report.title.subs"), TextUtils.getText("finance.report.subtitle.subs"));
		view.decision = TextUtils.getText("finance.report.decision.subs");
		view.dataSource = TextUtils.getText("finance.report.source.subs");
		final List subs = FinanceLedgerService.listSubscriptions();
		final int activeCount = FinanceRules.countActiveSubscriptions(subs);
		final long totalDaily = FinanceRules.totalDailySpendCents(subs);
		view.addKpi(TextUtils.getText("finance.report.kpi.active_subs"), String.valueOf(activeCount), "");
		view.addKpi(TextUtils.getText("finance.report.kpi.daily_total"), "¥" + FinanceAttributes.formatYuan(totalDaily), TextUtils.getText("finance.report.kpi.per_day"));
		view.addKpi(TextUtils.getText("finance.report.kpi.monthly_equiv"), "¥" + FinanceAttributes.formatYuan(totalDaily * 30L), TextUtils.getText("finance.report.kpi.approx"));
		final ReportChartSeries pie = new ReportChartSeries(TextUtils.getText("finance.report.chart.daily_share"), ReportChartSeries.TYPE_PIE);
		Collections.sort(subs, new Comparator() {
			public int compare(final Object a, final Object b) {
				final FinanceLedgerService.FinanceSubscription sa = (FinanceLedgerService.FinanceSubscription) a;
				final FinanceLedgerService.FinanceSubscription sb = (FinanceLedgerService.FinanceSubscription) b;
				final long da = FinanceRules.dailyAverageCents(sa.amountCents, sa.cycle);
				final long db = FinanceRules.dailyAverageCents(sb.amountCents, sb.cycle);
				if (da != db) {
					return da > db ? -1 : 1;
				}
				final String na = sa.nextYmd;
				final String nb = sb.nextYmd;
				return (na == null ? "" : na).compareTo(nb == null ? "" : nb);
			}
		});
		view.addDetail(TextUtils.getText("finance.report.detail.rank_header"));
		for (int i = 0; i < subs.size(); i++) {
			final FinanceLedgerService.FinanceSubscription s = (FinanceLedgerService.FinanceSubscription) subs.get(i);
			final boolean active = FinanceRules.isActiveSubscription(s.status);
			final long daily = FinanceRules.dailyAverageCents(s.amountCents, s.cycle);
			if (active && daily > 0L) {
				pie.add(trim(s.name, 14), daily / 100.0);
			}
			view.addDetail(TextUtils.format("finance.report.detail.sub_line", s.name, FinanceRules.cycleLabelZh(s.cycle), FinanceAttributes.formatYuan(daily), s.nextYmd.length() == 0 ? TextUtils.getText("finance.report.dash") : s.nextYmd));
		}
		view.addChart(pie);
		if (subs.isEmpty()) {
			view.emptyHint = TextUtils.getText("finance.report.empty.subs");
		}
		return view;
	}

	private static ReportViewModel viewCoupons() {
		final ReportViewModel view = new ReportViewModel(TextUtils.getText("finance.report.title.coupons"), TextUtils.getText("finance.report.subtitle.coupons"));
		view.decision = TextUtils.getText("finance.report.decision.coupons");
		view.dataSource = TextUtils.getText("finance.report.source.coupons");
		final List coupons = FinanceLedgerService.listCoupons();
		final String today = FinanceAttributes.todayYmd();
		int active = 0;
		int expiring = 0;
		view.addDetail(TextUtils.getText("finance.report.detail.coupon_header"));
		for (int i = 0; i < coupons.size(); i++) {
			final FinanceLedgerService.FinanceCoupon c = (FinanceLedgerService.FinanceCoupon) coupons.get(i);
			final boolean isActive = !"used".equalsIgnoreCase(c.status) && !"expired".equalsIgnoreCase(c.status);
			if (isActive) {
				active++;
			}
			final boolean soon = c.expiresYmd != null && c.expiresYmd.length() > 0 && c.expiresYmd.compareTo(today) >= 0
					&& c.expiresYmd.compareTo(plusDays(today, 14)) <= 0;
			if (soon) {
				expiring++;
			}
			final String flag = soon ? TextUtils.getText("finance.report.detail.expiring_flag") : "";
			view.addDetail(TextUtils.format("finance.report.detail.coupon_line", c.name,
					FinanceAttributes.formatYuan(c.amountCents),
					c.expiresYmd.length() == 0 ? TextUtils.getText("finance.report.dash") : c.expiresYmd,
					c.status, flag));
		}
		view.addKpi(TextUtils.getText("finance.report.kpi.coupons"), String.valueOf(coupons.size()), "");
		view.addKpi(TextUtils.getText("finance.report.kpi.active"), String.valueOf(active), "");
		view.addKpi(TextUtils.getText("finance.report.kpi.expire_14d"), String.valueOf(expiring), TextUtils.getText("finance.report.kpi.use_soon"));
		final ReportChartSeries pie = new ReportChartSeries(TextUtils.getText("finance.report.chart.coupon_face"), ReportChartSeries.TYPE_PIE);
		for (int i = 0; i < coupons.size(); i++) {
			final FinanceLedgerService.FinanceCoupon c = (FinanceLedgerService.FinanceCoupon) coupons.get(i);
			pie.add(trim(c.name, 14), Math.abs(c.amountCents) / 100.0);
		}
		view.addChart(pie);
		if (coupons.isEmpty()) {
			view.emptyHint = TextUtils.getText("finance.report.empty.coupons");
		}
		return view;
	}

	private static Map sumByDay(final String fromYmd, final String toYmd, final String flow) {
		final Map byDay = new TreeMap();
		final List txns = FinanceLedgerService.listTransactions(fromYmd, toYmd);
		for (int i = 0; i < txns.size(); i++) {
			final FinanceLedgerService.FinanceTxn txn = (FinanceLedgerService.FinanceTxn) txns.get(i);
			if (!flow.equals(txn.flow) || txn.dateYmd == null || txn.dateYmd.length() == 0) {
				continue;
			}
			final Long prev = (Long) byDay.get(txn.dateYmd);
			byDay.put(txn.dateYmd,
					Long.valueOf((prev == null ? 0L : prev.longValue()) + Math.abs(txn.amountCents)));
		}
		return byDay;
	}

	private static void fillDaySeries(final ReportChartSeries series, final Map byDay) {
		final Iterator it = byDay.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry e = (Map.Entry) it.next();
			series.add(shortDay((String) e.getKey()), ((Long) e.getValue()).doubleValue() / 100.0);
		}
	}

	private static long sumMap(final Map map) {
		long sum = 0L;
		final Iterator it = map.values().iterator();
		while (it.hasNext()) {
			sum += ((Long) it.next()).longValue();
		}
		return sum;
	}

	private static String periodFromRange(final String fromYmd, final String toYmd) {
		if (fromYmd != null && fromYmd.length() >= 7) {
			return fromYmd.substring(0, 7);
		}
		if (toYmd != null && toYmd.length() >= 7) {
			return toYmd.substring(0, 7);
		}
		final String today = FinanceAttributes.todayYmd();
		return today.substring(0, 7);
	}

	private static String rangeLabel(final String fromYmd, final String toYmd) {
		final String from = fromYmd == null || fromYmd.length() == 0 ? "…" : fromYmd;
		final String to = toYmd == null || toYmd.length() == 0 ? "…" : toYmd;
		return from + " → " + to;
	}

	private static String shortDay(final String ymd) {
		if (ymd == null || ymd.length() < 10) {
			return ymd == null ? "" : ymd;
		}
		return ymd.substring(5);
	}

	private static String trim(final String s, final int max) {
		if (s == null) {
			return "";
		}
		if (s.length() <= max) {
			return s;
		}
		return s.substring(0, Math.max(1, max - 1)) + "…";
	}

	private static String plusDays(final String ymd, final int days) {
		try {
			final java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA);
			final java.util.Calendar cal = java.util.Calendar.getInstance();
			cal.setTime(fmt.parse(ymd));
			cal.add(java.util.Calendar.DAY_OF_MONTH, days);
			return fmt.format(cal.getTime());
		}
		catch (Exception e) {
			return ymd;
		}
	}
}
