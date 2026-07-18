package org.docear.plugin.mcp.service;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.util.EdtRunner;
import org.docear.plugin.mcp.util.EdtRunner.Task;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.view.swing.features.finance.FinanceAttributes;
import org.freeplane.view.swing.features.finance.FinanceLedgerService;
import org.freeplane.view.swing.features.finance.FinanceReportEngine;
import org.freeplane.view.swing.features.finance.FinanceRules;
import org.freeplane.view.swing.features.finance.FinanceViewportService;
import org.freeplane.view.swing.features.reports.ReportKpi;
import org.freeplane.view.swing.features.reports.ReportViewModel;

/**
 * MCP access to the personal-finance ledger stored in {@code 个人财务.mm}.
 */
public final class McpFinanceService {
	private McpFinanceService() {
	}

	public static String ensureFinanceMap() throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final MapModel map = FinanceLedgerService.ensureFinanceMap();
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				if (map == null) {
					data.put("ok", JsonValue.ofBoolean(false));
					data.put("message", JsonValue.ofString("Failed to create or open finance map"));
					return JsonValue.ofMap(data).toJson();
				}
				final File file = FinanceLedgerService.resolveFinanceMapFile();
				data.put("ok", JsonValue.ofBoolean(true));
				data.put("mapFile", JsonValue.ofString(file == null ? "" : file.getAbsolutePath()));
				data.put("rootId", JsonValue.ofString(map.getRootNode() == null ? "" : map.getRootNode().createID()));
				data.put("title", JsonValue.ofString("个人财务"));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String getFinanceSummary(final String periodYyyyMm) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				FinanceLedgerService.ensureFinanceMap();
				final String period = resolvePeriod(periodYyyyMm);
				final FinanceLedgerService.MonthSummary summary = FinanceLedgerService.monthSummary(period);
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				data.put("period", JsonValue.ofString(period));
				data.put("mapFile", JsonValue.ofString(absMapPath()));
				data.put("incomeCents", JsonValue.ofNumber(Long.valueOf(summary.incomeCents)));
				data.put("expenseCents", JsonValue.ofNumber(Long.valueOf(summary.expenseCents)));
				data.put("netCents", JsonValue.ofNumber(Long.valueOf(summary.pnlNetCents())));
				data.put("borrowCents", JsonValue.ofNumber(Long.valueOf(summary.borrowCents)));
				data.put("lendCents", JsonValue.ofNumber(Long.valueOf(summary.lendCents)));
				data.put("creditCents", JsonValue.ofNumber(Long.valueOf(summary.creditCents)));
				data.put("transferCents", JsonValue.ofNumber(Long.valueOf(summary.transferCents)));
				data.put("incomeYuan", JsonValue.ofString(FinanceAttributes.formatYuan(summary.incomeCents)));
				data.put("expenseYuan", JsonValue.ofString(FinanceAttributes.formatYuan(summary.expenseCents)));
				data.put("netYuan", JsonValue.ofString(FinanceAttributes.formatYuan(summary.pnlNetCents())));
				final List<JsonValue> byCat = new ArrayList<JsonValue>();
				final Object[] keys = summary.byCategory.keySet().toArray();
				for (int i = 0; i < keys.length; i++) {
					final String cat = String.valueOf(keys[i]);
					final long cents = ((Long) summary.byCategory.get(cat)).longValue();
					final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
					row.put("category", JsonValue.ofString(cat));
					row.put("amountCents", JsonValue.ofNumber(Long.valueOf(cents)));
					row.put("amountYuan", JsonValue.ofString(FinanceAttributes.formatYuan(cents)));
					byCat.add(JsonValue.ofMap(row));
				}
				data.put("expenseByCategory", JsonValue.ofList(byCat));
				data.put("accounts", JsonValue.ofList(stringList(FinanceLedgerService.listAccounts())));
				data.put("expenseCategories",
						JsonValue.ofList(stringList(FinanceLedgerService.listCategories(FinanceAttributes.FLOW_EXPENSE))));
				data.put("incomeCategories",
						JsonValue.ofList(stringList(FinanceLedgerService.listCategories(FinanceAttributes.FLOW_INCOME))));
				data.put("subscriptionCount",
						JsonValue.ofNumber(Integer.valueOf(FinanceLedgerService.listSubscriptions().size())));
				data.put("couponCount", JsonValue.ofNumber(Integer.valueOf(FinanceLedgerService.listCoupons().size())));
				data.put("budgetCount",
						JsonValue.ofNumber(Integer.valueOf(FinanceLedgerService.listBudgets(period).size())));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String addFinanceTransaction(final String amount, final String flow, final String dateYmd,
			final String category, final String account, final String accountTo, final String merchant,
			final String note) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final long cents = FinanceAttributes.parseYuanToCents(amount);
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				if (!FinanceRules.isValidAmountCents(cents)) {
					data.put("ok", JsonValue.ofBoolean(false));
					data.put("message", JsonValue.ofString("amount must be a positive yuan value"));
					return JsonValue.ofMap(data).toJson();
				}
				final NodeModel node = FinanceLedgerService.addTransaction(amount, flow, dateYmd, category, account,
						accountTo, merchant, note);
				if (node == null) {
					data.put("ok", JsonValue.ofBoolean(false));
					data.put("message", JsonValue.ofString(
							"add_transaction failed (transfer needs account+accountTo; map must open)"));
					return JsonValue.ofMap(data).toJson();
				}
				data.put("ok", JsonValue.ofBoolean(true));
				data.put("mapFile", JsonValue.ofString(absMapPath()));
				data.put("nodeId", JsonValue.ofString(node.createID()));
				data.put("text", JsonValue.ofString(node.getText() == null ? "" : node.getText()));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String listFinanceTransactions(final String fromYmd, final String toYmd, final int limit)
			throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				FinanceLedgerService.ensureFinanceMap();
				final List txns = FinanceLedgerService.listTransactions(fromYmd, toYmd);
				final int max = limit > 0 ? limit : 200;
				final List<JsonValue> out = new ArrayList<JsonValue>();
				final int start = Math.max(0, txns.size() - max);
				for (int i = start; i < txns.size(); i++) {
					final FinanceLedgerService.FinanceTxn t = (FinanceLedgerService.FinanceTxn) txns.get(i);
					out.add(txnJson(t));
				}
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				data.put("mapFile", JsonValue.ofString(absMapPath()));
				data.put("from", JsonValue.ofString(fromYmd == null ? "" : fromYmd));
				data.put("to", JsonValue.ofString(toYmd == null ? "" : toYmd));
				data.put("count", JsonValue.ofNumber(Integer.valueOf(out.size())));
				data.put("totalMatched", JsonValue.ofNumber(Integer.valueOf(txns.size())));
				data.put("transactions", JsonValue.ofList(out));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String listFinanceCategories(final String flow) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				FinanceLedgerService.ensureFinanceMap();
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				data.put("mapFile", JsonValue.ofString(absMapPath()));
				data.put("flow", JsonValue.ofString(flow == null ? "all" : flow));
				data.put("categories", JsonValue.ofList(stringList(FinanceLedgerService.listCategories(flow))));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String addFinanceCategory(final String name, final String flow) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final NodeModel node = FinanceLedgerService.addCategory(name, flow);
				return nodeResult(node, "category");
			}
		});
	}

	public static String listFinanceAccounts() throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				FinanceLedgerService.ensureFinanceMap();
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				data.put("mapFile", JsonValue.ofString(absMapPath()));
				data.put("accounts", JsonValue.ofList(stringList(FinanceLedgerService.listAccounts())));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String addFinanceAccount(final String name) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final NodeModel node = FinanceLedgerService.addAccount(name);
				return nodeResult(node, "account");
			}
		});
	}

	public static String setFinanceBudget(final String period, final String category, final String amountYuan)
			throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final long cents = FinanceAttributes.parseYuanToCents(amountYuan);
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				if (!FinanceRules.isValidAmountCents(cents)) {
					data.put("ok", JsonValue.ofBoolean(false));
					data.put("message", JsonValue.ofString("amount must be a positive yuan value"));
					return JsonValue.ofMap(data).toJson();
				}
				final String p = resolvePeriod(period);
				final NodeModel node = FinanceLedgerService.setBudget(p, category, cents);
				return nodeResult(node, "budget");
			}
		});
	}

	public static String listFinanceBudgets(final String period) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				FinanceLedgerService.ensureFinanceMap();
				final String p = resolvePeriod(period);
				final FinanceLedgerService.MonthSummary summary = FinanceLedgerService.monthSummary(p);
				final List budgets = FinanceLedgerService.listBudgets(p);
				final List<JsonValue> out = new ArrayList<JsonValue>();
				for (int i = 0; i < budgets.size(); i++) {
					final FinanceLedgerService.FinanceBudget b = (FinanceLedgerService.FinanceBudget) budgets.get(i);
					final long spent = FinanceRules.budgetSpentCents(b.categoryName, summary.expenseCents,
							summary.byCategory);
					final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
					row.put("nodeId", JsonValue.ofString(b.node == null ? "" : b.node.createID()));
					row.put("period", JsonValue.ofString(b.period));
					row.put("category", JsonValue.ofString(b.categoryName));
					row.put("amountCents", JsonValue.ofNumber(Long.valueOf(b.amountCents)));
					row.put("amountYuan", JsonValue.ofString(FinanceAttributes.formatYuan(b.amountCents)));
					row.put("spentCents", JsonValue.ofNumber(Long.valueOf(spent)));
					row.put("spentYuan", JsonValue.ofString(FinanceAttributes.formatYuan(spent)));
					row.put("remainingCents", JsonValue.ofNumber(Long.valueOf(b.amountCents - spent)));
					out.add(JsonValue.ofMap(row));
				}
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				data.put("mapFile", JsonValue.ofString(absMapPath()));
				data.put("period", JsonValue.ofString(p));
				data.put("count", JsonValue.ofNumber(Integer.valueOf(out.size())));
				data.put("budgets", JsonValue.ofList(out));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String upsertFinanceSubscription(final String name, final String amountYuan, final String cycle,
			final String nextYmd, final String status, final String account, final String note) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final long cents = FinanceAttributes.parseYuanToCents(amountYuan);
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				if (!FinanceRules.isValidAmountCents(cents)) {
					data.put("ok", JsonValue.ofBoolean(false));
					data.put("message", JsonValue.ofString("amount must be a positive yuan value"));
					return JsonValue.ofMap(data).toJson();
				}
				final String cycleValue = cycle == null || cycle.trim().length() == 0 ? "monthly" : cycle.trim();
				final String next = nextYmd == null || nextYmd.trim().length() == 0
						? FinanceRules.nextDateForCycle(FinanceAttributes.todayYmd(), cycleValue)
						: nextYmd.trim();
				final NodeModel node = FinanceLedgerService.upsertSubscription(name, cents, cycleValue, next, status,
						account, note);
				return nodeResult(node, "subscription");
			}
		});
	}

	public static String listFinanceSubscriptions() throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				FinanceLedgerService.ensureFinanceMap();
				final List subs = FinanceLedgerService.listSubscriptions();
				final List<JsonValue> out = new ArrayList<JsonValue>();
				for (int i = 0; i < subs.size(); i++) {
					final FinanceLedgerService.FinanceSubscription s = (FinanceLedgerService.FinanceSubscription) subs
							.get(i);
					final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
					row.put("nodeId", JsonValue.ofString(s.node == null ? "" : s.node.createID()));
					row.put("name", JsonValue.ofString(s.name));
					row.put("amountCents", JsonValue.ofNumber(Long.valueOf(s.amountCents)));
					row.put("amountYuan", JsonValue.ofString(FinanceAttributes.formatYuan(s.amountCents)));
					row.put("cycle", JsonValue.ofString(s.cycle));
					row.put("next", JsonValue.ofString(s.nextYmd));
					row.put("status", JsonValue.ofString(s.status));
					row.put("account", JsonValue.ofString(s.accountName));
					row.put("note", JsonValue.ofString(s.note));
					out.add(JsonValue.ofMap(row));
				}
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				data.put("mapFile", JsonValue.ofString(absMapPath()));
				data.put("count", JsonValue.ofNumber(Integer.valueOf(out.size())));
				data.put("subscriptions", JsonValue.ofList(out));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String upsertFinanceCoupon(final String name, final String amountYuan, final String expiresYmd,
			final String status, final String merchant, final String note) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final long cents = FinanceAttributes.parseYuanToCents(amountYuan);
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				if (!FinanceRules.isValidAmountCents(cents)) {
					data.put("ok", JsonValue.ofBoolean(false));
					data.put("message", JsonValue.ofString("amount must be a positive yuan value"));
					return JsonValue.ofMap(data).toJson();
				}
				final NodeModel node = FinanceLedgerService.upsertCoupon(name, cents, expiresYmd, status, merchant,
						note);
				return nodeResult(node, "coupon");
			}
		});
	}

	public static String listFinanceCoupons() throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				FinanceLedgerService.ensureFinanceMap();
				final List coupons = FinanceLedgerService.listCoupons();
				final List<JsonValue> out = new ArrayList<JsonValue>();
				for (int i = 0; i < coupons.size(); i++) {
					final FinanceLedgerService.FinanceCoupon c = (FinanceLedgerService.FinanceCoupon) coupons.get(i);
					final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
					row.put("nodeId", JsonValue.ofString(c.node == null ? "" : c.node.createID()));
					row.put("name", JsonValue.ofString(c.name));
					row.put("amountCents", JsonValue.ofNumber(Long.valueOf(c.amountCents)));
					row.put("amountYuan", JsonValue.ofString(FinanceAttributes.formatYuan(c.amountCents)));
					row.put("expires", JsonValue.ofString(c.expiresYmd));
					row.put("status", JsonValue.ofString(c.status));
					row.put("merchant", JsonValue.ofString(c.merchant));
					row.put("note", JsonValue.ofString(c.note));
					out.add(JsonValue.ofMap(row));
				}
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				data.put("mapFile", JsonValue.ofString(absMapPath()));
				data.put("count", JsonValue.ofNumber(Integer.valueOf(out.size())));
				data.put("coupons", JsonValue.ofList(out));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String markFinanceCouponUsed(final String nodeId, final boolean used) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final NodeModel node = FinanceLedgerService.markCouponUsed(nodeId, used);
				return nodeResult(node, "coupon");
			}
		});
	}

	public static String deleteFinanceNode(final String nodeId) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final boolean ok = FinanceLedgerService.deleteFinanceNode(nodeId);
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				data.put("ok", JsonValue.ofBoolean(ok));
				data.put("nodeId", JsonValue.ofString(nodeId == null ? "" : nodeId));
				data.put("mapFile", JsonValue.ofString(absMapPath()));
				if (!ok) {
					data.put("message", JsonValue.ofString("delete failed (missing node or not a finance node)"));
				}
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	public static String getFinanceReport(final String reportId, final String fromYmd, final String toYmd,
			final boolean showInViewport) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				FinanceLedgerService.ensureFinanceMap();
				final String from = fromYmd == null || fromYmd.trim().length() == 0
						? resolvePeriod("") + "-01"
						: fromYmd.trim();
				final String to = toYmd == null || toYmd.trim().length() == 0 ? resolvePeriod("") + "-31" : toYmd.trim();
				final String id = reportId == null || reportId.trim().length() == 0
						? FinanceReportEngine.ID_MONTH_OVERVIEW
						: reportId.trim();
				final ReportViewModel model = FinanceReportEngine.generateView(id, from, to);
				boolean shown = false;
				if (showInViewport) {
					final FinanceViewportService viewport = FinanceViewportService.get();
					if (viewport != null) {
						viewport.show(model);
						shown = true;
					}
				}
				final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
				data.put("reportId", JsonValue.ofString(id));
				data.put("from", JsonValue.ofString(from));
				data.put("to", JsonValue.ofString(to));
				data.put("title", JsonValue.ofString(model.title == null ? "" : model.title));
				data.put("subtitle", JsonValue.ofString(model.subtitle == null ? "" : model.subtitle));
				data.put("decision", JsonValue.ofString(model.decision == null ? "" : model.decision));
				data.put("shownInViewport", JsonValue.ofBoolean(shown));
				final List<JsonValue> kpis = new ArrayList<JsonValue>();
				if (model.kpis != null) {
					for (int i = 0; i < model.kpis.size(); i++) {
						final Object o = model.kpis.get(i);
						if (o instanceof ReportKpi) {
							final ReportKpi k = (ReportKpi) o;
							final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
							row.put("label", JsonValue.ofString(k.label == null ? "" : k.label));
							row.put("value", JsonValue.ofString(k.value == null ? "" : k.value));
							row.put("hint", JsonValue.ofString(k.hint == null ? "" : k.hint));
							kpis.add(JsonValue.ofMap(row));
						}
					}
				}
				data.put("kpis", JsonValue.ofList(kpis));
				final List<JsonValue> details = new ArrayList<JsonValue>();
				if (model.details != null) {
					for (int i = 0; i < model.details.size(); i++) {
						details.add(JsonValue.ofString(String.valueOf(model.details.get(i))));
					}
				}
				data.put("details", JsonValue.ofList(details));
				return JsonValue.ofMap(data).toJson();
			}
		});
	}

	private static JsonValue txnJson(final FinanceLedgerService.FinanceTxn t) {
		final Map<String, JsonValue> row = new LinkedHashMap<String, JsonValue>();
		row.put("nodeId", JsonValue.ofString(t.node == null ? "" : t.node.createID()));
		row.put("date", JsonValue.ofString(t.dateYmd));
		row.put("flow", JsonValue.ofString(t.flow));
		row.put("amountCents", JsonValue.ofNumber(Long.valueOf(t.amountCents)));
		row.put("amountYuan", JsonValue.ofString(FinanceAttributes.formatYuan(t.amountCents)));
		row.put("category", JsonValue.ofString(t.categoryName));
		row.put("account", JsonValue.ofString(t.accountName));
		row.put("accountTo", JsonValue.ofString(t.accountTo));
		row.put("merchant", JsonValue.ofString(t.merchant));
		row.put("note", JsonValue.ofString(t.note));
		row.put("text", JsonValue.ofString(t.nodeText));
		return JsonValue.ofMap(row);
	}

	private static String nodeResult(final NodeModel node, final String kind) {
		final Map<String, JsonValue> data = new LinkedHashMap<String, JsonValue>();
		if (node == null) {
			data.put("ok", JsonValue.ofBoolean(false));
			data.put("message", JsonValue.ofString(kind + " write failed"));
			return JsonValue.ofMap(data).toJson();
		}
		data.put("ok", JsonValue.ofBoolean(true));
		data.put("kind", JsonValue.ofString(kind));
		data.put("mapFile", JsonValue.ofString(absMapPath()));
		data.put("nodeId", JsonValue.ofString(node.createID()));
		data.put("text", JsonValue.ofString(node.getText() == null ? "" : node.getText()));
		return JsonValue.ofMap(data).toJson();
	}

	private static List<JsonValue> stringList(final List values) {
		final List<JsonValue> out = new ArrayList<JsonValue>();
		if (values == null) {
			return out;
		}
		for (int i = 0; i < values.size(); i++) {
			out.add(JsonValue.ofString(String.valueOf(values.get(i))));
		}
		return out;
	}

	private static String resolvePeriod(final String periodYyyyMm) {
		if (periodYyyyMm != null && periodYyyyMm.trim().length() >= 7) {
			return periodYyyyMm.trim().substring(0, 7);
		}
		final String today = FinanceAttributes.todayYmd();
		return today.length() >= 7 ? today.substring(0, 7) : today;
	}

	private static String absMapPath() {
		final File file = FinanceLedgerService.resolveFinanceMapFile();
		return file == null ? "" : file.getAbsolutePath();
	}
}
