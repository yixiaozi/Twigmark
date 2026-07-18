package org.freeplane.view.swing.features.finance;

import java.util.HashMap;
import java.util.Map;

/**
 * Headless self-check for {@link FinanceRules}. Run:
 * {@code java -cp ... org.freeplane.view.swing.features.finance.FinanceRulesStandaloneTest}
 */
public final class FinanceRulesStandaloneTest {
	public static void main(final String[] args) {
		int failed = 0;
		failed += assertTrue("total budget empty", FinanceRules.isTotalBudgetCategory(""));
		failed += assertTrue("total budget 总预算", FinanceRules.isTotalBudgetCategory("总预算"));
		failed += assertTrue("not total", !FinanceRules.isTotalBudgetCategory("餐饮"));

		final Map byCat = new HashMap();
		byCat.put("餐饮", Long.valueOf(2800L));
		byCat.put("交通", Long.valueOf(1200L));
		failed += assertEq("total spent", 4000L, FinanceRules.budgetSpentCents("总预算", 4000L, byCat));
		failed += assertEq("cat spent", 2800L, FinanceRules.budgetSpentCents("餐饮", 4000L, byCat));
		failed += assertEq("remaining", 7200L, FinanceRules.budgetRemainingCents("总预算", 10000L, 2800L, byCat));

		failed += assertEq("bare", "满减券", FinanceRules.bareNameFromLabel("满减券 · ¥10.00", null));
		failed += assertEq("bare fallback", "A", FinanceRules.bareNameFromLabel("满减券 · ¥10.00", "A"));
		failed += assertEq("sign income", "+", FinanceRules.flowSign(FinanceAttributes.FLOW_INCOME));
		failed += assertEq("sign borrow", "+", FinanceRules.flowSign(FinanceAttributes.FLOW_BORROW));
		failed += assertEq("sign transfer", "↔", FinanceRules.flowSign(FinanceAttributes.FLOW_TRANSFER));
		failed += assertTrue("valid amount", FinanceRules.isValidAmountCents(1L));
		failed += assertTrue("invalid amount", !FinanceRules.isValidAmountCents(0L));

		failed += assertEq("parse yuan", 2850L, FinanceAttributes.parseYuanToCents("28.5"));
		failed += assertEq("parse yuan ¥", 2800L, FinanceAttributes.parseYuanToCents("¥28.00"));
		failed += assertEq("format", "28.50", FinanceAttributes.formatYuan(2850L));

		final String weekly = FinanceRules.nextDateForCycle("2026-07-01", "weekly");
		failed += assertEq("weekly next", "2026-07-08", weekly);
		final String yearly = FinanceRules.nextDateForCycle("2026-07-01", "yearly");
		failed += assertEq("yearly next", "2027-07-01", yearly);

		if (failed == 0) {
			System.out.println("FinanceRulesStandaloneTest OK");
		}
		else {
			System.err.println("FinanceRulesStandaloneTest FAILED: " + failed);
			System.exit(1);
		}
	}

	private static int assertTrue(final String name, final boolean cond) {
		if (!cond) {
			System.err.println("FAIL: " + name);
			return 1;
		}
		return 0;
	}

	private static int assertEq(final String name, final Object expected, final Object actual) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			System.err.println("FAIL: " + name + " expected=" + expected + " actual=" + actual);
			return 1;
		}
		return 0;
	}
}
