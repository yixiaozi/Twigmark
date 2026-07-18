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
		final String c = cycle == null ? "monthly" : cycle.trim().toLowerCase(Locale.ENGLISH);
		try {
			final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
			fmt.setLenient(false);
			final Calendar cal = Calendar.getInstance();
			cal.setTime(fmt.parse(base));
			if ("weekly".equals(c) || "week".equals(c)) {
				cal.add(Calendar.DAY_OF_MONTH, 7);
			}
			else if ("yearly".equals(c) || "year".equals(c) || "annual".equals(c)) {
				cal.add(Calendar.YEAR, 1);
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
