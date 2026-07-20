package org.freeplane.view.swing.features.finance;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

/**
 * Pure finance rules (no Swing / Freeplane UI). Used by ledger, reports, UI, MCP.
 */
public final class FinanceRules {
	public static final String TOTAL_BUDGET_CATEGORY = "总预算";

	private FinanceRules() {
	}

	public static boolean isPnlIncome(final String flow) {
		return FinanceAttributes.FLOW_INCOME.equals(flow);
	}

	public static boolean isPnlExpense(final String flow) {
		return FinanceAttributes.FLOW_EXPENSE.equals(flow);
	}

	public static boolean isTransfer(final String flow) {
		return FinanceAttributes.FLOW_TRANSFER.equals(flow);
	}

	public static boolean isBorrow(final String flow) {
		return FinanceAttributes.FLOW_BORROW.equals(flow);
	}

	public static boolean isLend(final String flow) {
		return FinanceAttributes.FLOW_LEND.equals(flow);
	}

	public static boolean isCredit(final String flow) {
		return FinanceAttributes.FLOW_CREDIT.equals(flow);
	}

	public static boolean isTotalBudgetCategory(final String categoryName) {
		if (categoryName == null) {
			return true;
		}
		final String c = categoryName.trim();
		return c.length() == 0 || TOTAL_BUDGET_CATEGORY.equals(c);
	}

	/**
	 * Spent amount against a budget line: total budget uses all P&amp;L expense;
	 * category budget uses that category only.
	 */
	public static long budgetSpentCents(final String categoryName, final long pnlExpenseCents,
			final Map expenseByCategory) {
		if (isTotalBudgetCategory(categoryName)) {
			return Math.abs(pnlExpenseCents);
		}
		if (expenseByCategory == null) {
			return 0L;
		}
		final Object v = expenseByCategory.get(categoryName.trim());
		if (v instanceof Long) {
			return Math.abs(((Long) v).longValue());
		}
		return 0L;
	}

	public static long budgetRemainingCents(final String categoryName, final long limitCents,
			final long pnlExpenseCents, final Map expenseByCategory) {
		return Math.abs(limitCents) - budgetSpentCents(categoryName, pnlExpenseCents, expenseByCategory);
	}

	/**
	 * Parse start/end dates from labels like
	 * "总预算 · 2026-07-01~2026-07-21 · ¥13.00" or "房租 · 2026-07-20~2027-07-20 · 每月 · ¥3000".
	 * Returns String[2] {start, end}; missing parts are "".
	 */
	public static String[] parseDateRangeFromLabel(final String label) {
		final String[] out = new String[] { "", "" };
		if (label == null || label.trim().length() == 0) {
			return out;
		}
		final java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("(20\\d{2}-\\d{2}-\\d{2})\\s*[~～-]\\s*(20\\d{2}-\\d{2}-\\d{2})")
				.matcher(label);
		if (m.find()) {
			out[0] = m.group(1);
			out[1] = m.group(2);
			return out;
		}
		final java.util.regex.Matcher single = java.util.regex.Pattern
				.compile("(20\\d{2}-\\d{2}-\\d{2})")
				.matcher(label);
		if (single.find()) {
			out[0] = single.group(1);
		}
		else {
			final java.util.regex.Matcher month = java.util.regex.Pattern
					.compile("(20\\d{2}-\\d{2})(?!\\d)")
					.matcher(label);
			if (month.find()) {
				out[0] = month.group(1) + "-01";
				out[1] = month.group(1) + "-31";
			}
		}
		return out;
	}

	/** Detect cycle token in a subscription label (每月/每年/每周/每天). */
	public static String parseCycleFromLabel(final String label) {
		if (label == null) {
			return "";
		}
		if (label.indexOf("每天") >= 0 || label.indexOf("每日") >= 0) {
			return "daily";
		}
		if (label.indexOf("每周") >= 0) {
			return "weekly";
		}
		if (label.indexOf("每年") >= 0) {
			return "yearly";
		}
		if (label.indexOf("每月") >= 0 || label.indexOf("月付") >= 0) {
			return "monthly";
		}
		return "";
	}

	/** Parse amount in cents from a node label such as "关东煮 · ¥8.58" or "总预算 · ¥333.00". */
	public static Long parseAmountFromLabel(final String label) {
		if (label == null || label.trim().length() == 0) {
			return null;
		}
		int yen = label.lastIndexOf('¥');
		if (yen < 0) {
			yen = label.lastIndexOf('￥');
		}
		if (yen < 0) {
			return null;
		}
		String tail = label.substring(yen + 1).trim();
		final int sep = tail.indexOf(' ');
		if (sep > 0) {
			tail = tail.substring(0, sep).trim();
		}
		final long cents = FinanceAttributes.parseYuanToCents(tail);
		return isValidAmountCents(cents) ? Long.valueOf(cents) : null;
	}

	/** Category portion of a budget label "餐饮 · ¥100.00" or "总预算 · 2026-07 · ¥1000.00". */
	public static String budgetCategoryFromLabel(final String label, final String fallback) {
		if (label == null || label.trim().length() == 0) {
			return fallback == null ? "" : fallback.trim();
		}
		final String t = label.trim();
		final int sep = t.indexOf(" ·");
		if (sep > 0) {
			final String head = t.substring(0, sep).trim();
			if (head.matches("20\\d{2}-\\d{2}(-\\d{2})?")) {
				return fallback == null ? TOTAL_BUDGET_CATEGORY : fallback.trim();
			}
			return head;
		}
		if (fallback != null && fallback.trim().length() > 0) {
			return fallback.trim();
		}
		return t;
	}

	/** Normalize bare coupon/subscription name from a labeled node text "Name · ¥12.00". */
	public static String bareNameFromLabel(final String label, final String fallback) {
		if (fallback != null && fallback.trim().length() > 0) {
			return fallback.trim();
		}
		if (label == null) {
			return "";
		}
		final String t = label.trim();
		final int sep = t.indexOf(" ·");
		if (sep > 0) {
			return t.substring(0, sep).trim();
		}
		return t;
	}

	public static String nextDateForCycle(final String fromYmd, final String cycle) {
		final String base = fromYmd == null || fromYmd.trim().length() == 0
				? FinanceAttributes.todayYmd()
				: fromYmd.trim();
		final String c = normalizeCycle(cycle);
		try {
			final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
			fmt.setLenient(false);
			final Calendar cal = Calendar.getInstance();
			cal.setTime(fmt.parse(base));
			if ("weekly".equals(c)) {
				cal.add(Calendar.DAY_OF_MONTH, 7);
			}
			else if ("yearly".equals(c)) {
				cal.add(Calendar.YEAR, 1);
			}
			else if ("daily".equals(c)) {
				cal.add(Calendar.DAY_OF_MONTH, 1);
			}
			else {
				cal.add(Calendar.MONTH, 1);
			}
			return fmt.format(cal.getTime());
		}
		catch (Exception e) {
			return base;
		}
	}

	/**
	 * Approximate days in one billing cycle for daily-average math.
	 * monthly≈30, yearly=365, weekly=7, daily=1.
	 */
	public static int cycleDays(final String cycle) {
		final String c = normalizeCycle(cycle);
		if ("weekly".equals(c)) {
			return 7;
		}
		if ("yearly".equals(c)) {
			return 365;
		}
		if ("daily".equals(c)) {
			return 1;
		}
		return 30;
	}

	public static String normalizeCycle(final String cycle) {
		if (cycle == null || cycle.trim().length() == 0) {
			return "monthly";
		}
		final String c = cycle.trim().toLowerCase(Locale.ENGLISH);
		if ("weekly".equals(c) || "week".equals(c) || "周".equals(c) || "每周".equals(cycle.trim())) {
			return "weekly";
		}
		if ("yearly".equals(c) || "year".equals(c) || "annual".equals(c) || "年".equals(c)
				|| "每年".equals(cycle.trim())) {
			return "yearly";
		}
		if ("daily".equals(c) || "day".equals(c) || "日".equals(c) || "每天".equals(cycle.trim())) {
			return "daily";
		}
		if ("monthly".equals(c) || "month".equals(c) || "月".equals(c) || "每月".equals(cycle.trim())) {
			return "monthly";
		}
		return "monthly";
	}

	public static String cycleLabelZh(final String cycle) {
		final String c = normalizeCycle(cycle);
		if ("weekly".equals(c)) {
			return "每周";
		}
		if ("yearly".equals(c)) {
			return "每年";
		}
		if ("daily".equals(c)) {
			return "每天";
		}
		return "每月";
	}

	/** Active subscriptions count toward fixed daily spend (paused/cancelled excluded). */
	public static boolean isActiveSubscription(final String status) {
		if (status == null || status.trim().length() == 0) {
			return true;
		}
		final String s = status.trim().toLowerCase(Locale.ENGLISH);
		if ("paused".equals(s) || "pause".equals(s) || "cancelled".equals(s) || "canceled".equals(s)
				|| "inactive".equals(s) || "stopped".equals(s) || "停用".equals(status.trim())
				|| "暂停".equals(status.trim())) {
			return false;
		}
		return true;
	}

	/**
	 * Average cost per day for one subscription amount/cycle (rounded half-up, cents).
	 * Example: ¥3000/month → 10000 cents/day; ¥98/year → 27 cents/day.
	 */
	public static long dailyAverageCents(final long amountCents, final String cycle) {
		final long amount = Math.abs(amountCents);
		if (amount <= 0L) {
			return 0L;
		}
		final int days = cycleDays(cycle);
		return (amount + days / 2) / days;
	}

	/**
	 * Sum of daily averages for active items in a list of
	 * {@link FinanceLedgerService.FinanceSubscription}.
	 */
	public static long totalDailySpendCents(final java.util.List subscriptions) {
		if (subscriptions == null || subscriptions.isEmpty()) {
			return 0L;
		}
		long total = 0L;
		for (int i = 0; i < subscriptions.size(); i++) {
			final Object item = subscriptions.get(i);
			if (!(item instanceof FinanceLedgerService.FinanceSubscription)) {
				continue;
			}
			final FinanceLedgerService.FinanceSubscription s = (FinanceLedgerService.FinanceSubscription) item;
			if (!isActiveSubscription(s.status)) {
				continue;
			}
			total += dailyAverageCents(s.amountCents, s.cycle);
		}
		return total;
	}

	public static int countActiveSubscriptions(final java.util.List subscriptions) {
		if (subscriptions == null || subscriptions.isEmpty()) {
			return 0;
		}
		int count = 0;
		for (int i = 0; i < subscriptions.size(); i++) {
			final Object item = subscriptions.get(i);
			if (!(item instanceof FinanceLedgerService.FinanceSubscription)) {
				continue;
			}
			final FinanceLedgerService.FinanceSubscription s = (FinanceLedgerService.FinanceSubscription) item;
			if (isActiveSubscription(s.status)) {
				count++;
			}
		}
		return count;
	}

	public static String flowSign(final String flow) {
		if (isPnlIncome(flow) || isBorrow(flow)) {
			return "+";
		}
		if (isTransfer(flow)) {
			return "↔";
		}
		return "-";
	}

	public static String flowLabelZh(final String flow) {
		if (isPnlIncome(flow)) {
			return "收入";
		}
		if (isTransfer(flow)) {
			return "转账";
		}
		if (isBorrow(flow)) {
			return "借入";
		}
		if (isLend(flow)) {
			return "借出";
		}
		if (isCredit(flow)) {
			return "信用卡";
		}
		return "支出";
	}

	public static boolean isValidAmountCents(final long cents) {
		return cents > 0L;
	}
}
