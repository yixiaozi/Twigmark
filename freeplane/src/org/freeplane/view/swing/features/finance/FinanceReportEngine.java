package org.freeplane.view.swing.features.finance;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.freeplane.core.util.LogUtils;
import org.freeplane.view.swing.features.reports.ReportChartSeries;
import org.freeplane.view.swing.features.reports.ReportViewModel;

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
				return viewByCategory(fromYmd, toYmd, FinanceAttributes.FLOW_EXPENSE, "支出按分类");
			}
			if (ID_INCOME_BY_CATEGORY.equals(id)) {
				return viewByCategory(fromYmd, toYmd, FinanceAttributes.FLOW_INCOME, "收入按分类");
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
			final ReportViewModel unknown = new ReportViewModel("财务报表", "未知类型：" + id);
			unknown.emptyHint = "请选择有效的财务报表";
			unknown.addDetail("未知报表：" + id);
			return unknown;
		}
		catch (Exception e) {
			LogUtils.warn("FinanceReportEngine.generateView failed: " + id, e);
			final ReportViewModel fail = new ReportViewModel("财务报表失败", id);
			fail.addDetail(String.valueOf(e.getMessage()));
			fail.emptyHint = "请检查财务导图后重试";
			return fail;
		}
	}

	private static ReportViewModel viewMonthOverview(final String fromYmd, final String toYmd) {
		final String period = periodFromRange(fromYmd, toYmd);
		final ReportViewModel view = new ReportViewModel("财务 · 月度概览", rangeLabel(fromYmd, toYmd));
		view.decision = "看清本月收支净额与支出构成";
		view.dataSource = "个人财务导图 · 交易";
		final FinanceLedgerService.MonthSummary summary = FinanceLedgerService.monthSummary(period);
		final long income = summary.incomeCents;
		final long expense = summary.expenseCents;
		final long net = summary.pnlNetCents();
		view.addKpi("收入", "¥" + FinanceAttributes.formatYuan(income), "损益");
		view.addKpi("支出", "¥" + FinanceAttributes.formatYuan(expense), "损益");
		view.addKpi("结余", "¥" + FinanceAttributes.formatYuan(net), net >= 0 ? "盈余" : "透支");
		if (summary.borrowCents + summary.lendCents + summary.creditCents + summary.transferCents > 0L) {
			view.addKpi("借入", "¥" + FinanceAttributes.formatYuan(summary.borrowCents), "负债流入");
			view.addKpi("借出", "¥" + FinanceAttributes.formatYuan(summary.lendCents), "债权流出");
			view.addKpi("信用卡", "¥" + FinanceAttributes.formatYuan(summary.creditCents), "额度占用");
			view.addKpi("转账", "¥" + FinanceAttributes.formatYuan(summary.transferCents), "不计入损益");
		}

		final ReportChartSeries pie = new ReportChartSeries("支出按分类", ReportChartSeries.TYPE_PIE);
		final Iterator it = summary.byCategory.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry e = (Map.Entry) it.next();
			pie.add(String.valueOf(e.getKey()), ((Long) e.getValue()).doubleValue() / 100.0);
			view.addDetail(e.getKey() + " · ¥" + FinanceAttributes.formatYuan(((Long) e.getValue()).longValue()));
		}
		view.addChart(pie);

		final ReportChartSeries byDay = new ReportChartSeries("每日支出（元）", ReportChartSeries.TYPE_BAR);
		final Map dayExpense = sumByDay(fromYmd, toYmd, FinanceAttributes.FLOW_EXPENSE);
		final Iterator dit = dayExpense.entrySet().iterator();
		while (dit.hasNext()) {
			final Map.Entry e = (Map.Entry) dit.next();
			byDay.add(shortDay((String) e.getKey()), ((Long) e.getValue()).doubleValue() / 100.0);
		}
		view.addChart(byDay);

		if (income == 0L && expense == 0L) {
			view.emptyHint = "本月暂无损益交易。在左侧「财务」记一笔或用 add_finance_transaction 后再看。";
		}
		return view;
	}

	private static ReportViewModel viewByCategory(final String fromYmd, final String toYmd, final String flow,
			final String title) {
		final ReportViewModel view = new ReportViewModel("财务 · " + title, rangeLabel(fromYmd, toYmd));
		view.decision = "找出最大分类";
		view.dataSource = "个人财务导图 · 交易";
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
			final String cat = txn.categoryName == null || txn.categoryName.length() == 0 ? "其他" : txn.categoryName;
			final Long prev = (Long) byCat.get(cat);
			byCat.put(cat, Long.valueOf((prev == null ? 0L : prev.longValue()) + cents));
		}
		view.addKpi("合计", "¥" + FinanceAttributes.formatYuan(total), title);
		view.addKpi("分类数", String.valueOf(byCat.size()), "");
		final ReportChartSeries pie = new ReportChartSeries(title + "（元）", ReportChartSeries.TYPE_PIE);
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
			view.emptyHint = "该时段无" + title + "数据。";
		}
		return view;
	}

	private static ReportViewModel viewTrend(final String fromYmd, final String toYmd) {
		final ReportViewModel view = new ReportViewModel("财务 · 收支趋势", rangeLabel(fromYmd, toYmd));
		view.decision = "看每日收入与支出节奏";
		view.dataSource = "个人财务导图 · 交易";
		final Map dayExpense = sumByDay(fromYmd, toYmd, FinanceAttributes.FLOW_EXPENSE);
		final Map dayIncome = sumByDay(fromYmd, toYmd, FinanceAttributes.FLOW_INCOME);
		long expenseTotal = sumMap(dayExpense);
		long incomeTotal = sumMap(dayIncome);
		view.addKpi("支出合计", "¥" + FinanceAttributes.formatYuan(expenseTotal), "");
		view.addKpi("收入合计", "¥" + FinanceAttributes.formatYuan(incomeTotal), "");
		final ReportChartSeries expenseLine = new ReportChartSeries("每日支出（元）", ReportChartSeries.TYPE_LINE);
		fillDaySeries(expenseLine, dayExpense);
		view.addChart(expenseLine);
		final ReportChartSeries incomeLine = new ReportChartSeries("每日收入（元）", ReportChartSeries.TYPE_LINE);
		fillDaySeries(incomeLine, dayIncome);
		view.addChart(incomeLine);
		if (dayExpense.isEmpty() && dayIncome.isEmpty()) {
			view.emptyHint = "该时段无交易，无法画趋势。";
		}
		return view;
	}

	private static ReportViewModel viewBudgetStatus(final String fromYmd, final String toYmd) {
		final String period = periodFromRange(fromYmd, toYmd);
		final ReportViewModel view = new ReportViewModel("财务 · 预算执行", period);
		view.decision = "对比预算与实际支出";
		view.dataSource = "个人财务导图 · 预算/交易";
		final List budgets = FinanceLedgerService.listBudgets(period);
		final FinanceLedgerService.MonthSummary summary = FinanceLedgerService.monthSummary(period);
		view.addKpi("预算条目", String.valueOf(budgets.size()), period);
		view.addKpi("实际支出", "¥" + FinanceAttributes.formatYuan(summary.expenseCents), "损益支出");
		final ReportChartSeries bar = new ReportChartSeries("预算 vs 已花（元）", ReportChartSeries.TYPE_BAR);
		for (int i = 0; i < budgets.size(); i++) {
			final FinanceLedgerService.FinanceBudget b = (FinanceLedgerService.FinanceBudget) budgets.get(i);
			final long spent = FinanceRules.budgetSpentCents(b.categoryName, summary.expenseCents, summary.byCategory);
			bar.add(b.categoryName + "预算", b.amountCents / 100.0);
			bar.add(b.categoryName + "已花", spent / 100.0);
			view.addDetail(b.categoryName + " · 预算 ¥" + FinanceAttributes.formatYuan(b.amountCents) + " · 已花 ¥"
					+ FinanceAttributes.formatYuan(spent) + " · 余 ¥"
					+ FinanceAttributes.formatYuan(b.amountCents - spent));
		}
		view.addChart(bar);
		if (budgets.isEmpty()) {
			view.emptyHint = "本月未设预算。在「财务」Tab 点「加预算」或用 set_finance_budget。";
		}
		return view;
	}

	private static ReportViewModel viewSubscriptions() {
		final ReportViewModel view = new ReportViewModel("财务 · 订阅", "即将扣费与金额构成");
		view.decision = "清理不必要的订阅";
		view.dataSource = "个人财务导图 · 订阅";
		final List subs = FinanceLedgerService.listSubscriptions();
		long total = 0L;
		view.addKpi("订阅数", String.valueOf(subs.size()), "");
		final ReportChartSeries pie = new ReportChartSeries("订阅金额（元）", ReportChartSeries.TYPE_PIE);
		Collections.sort(subs, new Comparator() {
			public int compare(final Object a, final Object b) {
				final String na = ((FinanceLedgerService.FinanceSubscription) a).nextYmd;
				final String nb = ((FinanceLedgerService.FinanceSubscription) b).nextYmd;
				return (na == null ? "" : na).compareTo(nb == null ? "" : nb);
			}
		});
		view.addDetail("—— 即将扣费 ——");
		for (int i = 0; i < subs.size(); i++) {
			final FinanceLedgerService.FinanceSubscription s = (FinanceLedgerService.FinanceSubscription) subs.get(i);
			total += Math.abs(s.amountCents);
			pie.add(trim(s.name, 14), Math.abs(s.amountCents) / 100.0);
			view.addDetail(s.name + " · ¥" + FinanceAttributes.formatYuan(s.amountCents) + " · 下次 "
					+ (s.nextYmd.length() == 0 ? "—" : s.nextYmd) + " · " + s.cycle);
		}
		view.addKpi("金额合计", "¥" + FinanceAttributes.formatYuan(total), "周期内");
		view.addChart(pie);
		if (subs.isEmpty()) {
			view.emptyHint = "暂无订阅。在「财务」Tab 点「加订阅」或用 upsert_finance_subscription。";
		}
		return view;
	}

	private static ReportViewModel viewCoupons() {
		final ReportViewModel view = new ReportViewModel("财务 · 优惠券", "有效与即将过期");
		view.decision = "优先用快过期的券";
		view.dataSource = "个人财务导图 · 优惠券";
		final List coupons = FinanceLedgerService.listCoupons();
		final String today = FinanceAttributes.todayYmd();
		int active = 0;
		int expiring = 0;
		view.addDetail("—— 有效 / 即将过期 ——");
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
			final String flag = soon ? " · 将过期" : "";
			view.addDetail(c.name + " · ¥" + FinanceAttributes.formatYuan(c.amountCents) + " · 到期 "
					+ (c.expiresYmd.length() == 0 ? "—" : c.expiresYmd) + " · " + c.status + flag);
		}
		view.addKpi("优惠券", String.valueOf(coupons.size()), "");
		view.addKpi("有效", String.valueOf(active), "");
		view.addKpi("14天内到期", String.valueOf(expiring), "抓紧用");
		final ReportChartSeries pie = new ReportChartSeries("券面额（元）", ReportChartSeries.TYPE_PIE);
		for (int i = 0; i < coupons.size(); i++) {
			final FinanceLedgerService.FinanceCoupon c = (FinanceLedgerService.FinanceCoupon) coupons.get(i);
			pie.add(trim(c.name, 14), Math.abs(c.amountCents) / 100.0);
		}
		view.addChart(pie);
		if (coupons.isEmpty()) {
			view.emptyHint = "暂无优惠券。在「财务」Tab 点「加优惠券」或用 upsert_finance_coupon。";
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
