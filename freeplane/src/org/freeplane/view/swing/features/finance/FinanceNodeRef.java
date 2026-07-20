package org.freeplane.view.swing.features.finance;

import java.util.ArrayList;
import java.util.List;

import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;

/**
 * Resolves finance category/account nodes by stable node ID, with multi-level path labels.
 */
public final class FinanceNodeRef {
	public static final String TOTAL_BUDGET_NODE_ID = "";

	public static final class Ref {
		public final String nodeId;
		public final String path;

		public Ref(final String nodeId, final String path) {
			this.nodeId = nodeId == null ? "" : nodeId;
			this.path = path == null ? "" : path;
		}

		public String toString() {
			return path;
		}
	}

	private FinanceNodeRef() {
	}

	public static List listCategoryRefs(final String flow) {
		final List out = new ArrayList();
		final MapModel map = FinanceLedgerService.preferOpenFinanceMapPublic();
		if (map == null) {
			return out;
		}
		final NodeModel categories = FinanceLedgerService.findSection(map, FinanceLedgerService.SECTION_CATEGORIES);
		if (categories == null) {
			return out;
		}
		final String flowValue = flow == null ? "" : flow.trim();
		final boolean all = flowValue.length() == 0 || "all".equalsIgnoreCase(flowValue)
				|| "both".equalsIgnoreCase(flowValue);
		if (all || FinanceAttributes.FLOW_EXPENSE.equals(flowValue)) {
			collectCategoryNodes(findChild(categories, FinanceLedgerService.SUB_EXPENSE), out, FinanceLedgerService.SUB_EXPENSE);
		}
		if (all || FinanceAttributes.FLOW_INCOME.equals(flowValue)) {
			collectCategoryNodes(findChild(categories, FinanceLedgerService.SUB_INCOME), out, FinanceLedgerService.SUB_INCOME);
		}
		return out;
	}

	public static List listAccountRefs() {
		final List out = new ArrayList();
		final MapModel map = FinanceLedgerService.preferOpenFinanceMapPublic();
		if (map == null) {
			return out;
		}
		final NodeModel accounts = FinanceLedgerService.findSection(map, FinanceLedgerService.SECTION_ACCOUNTS);
		collectAccountNodes(accounts, out, "");
		return out;
	}

	public static String normalizeCategoryRef(final MapModel map, final String value) {
		if (map == null || value == null || value.trim().length() == 0) {
			return "";
		}
		final String trimmed = value.trim();
		if (FinanceRules.isTotalBudgetCategory(trimmed)) {
			return TOTAL_BUDGET_NODE_ID;
		}
		final NodeModel byId = map.getNodeForID(trimmed);
		if (byId != null && isCategoryNode(byId)) {
			return trimmed;
		}
		final List refs = listCategoryRefs("all");
		for (int i = 0; i < refs.size(); i++) {
			final Ref ref = (Ref) refs.get(i);
			if (trimmed.equals(ref.path) || trimmed.equals(ref.nodeId)) {
				return ref.nodeId;
			}
		}
		for (int i = 0; i < refs.size(); i++) {
			final Ref ref = (Ref) refs.get(i);
			if (ref.path.endsWith(" / " + trimmed) || ref.path.equals(trimmed)) {
				return ref.nodeId;
			}
		}
		final NodeModel byName = findCategoryByBareName(map, trimmed);
		return byName == null ? trimmed : byName.createID();
	}

	public static String normalizeAccountRef(final MapModel map, final String value) {
		if (map == null || value == null || value.trim().length() == 0) {
			return "";
		}
		final String trimmed = value.trim();
		final NodeModel byId = map.getNodeForID(trimmed);
		if (byId != null && isAccountNode(byId)) {
			return trimmed;
		}
		final List refs = listAccountRefs();
		for (int i = 0; i < refs.size(); i++) {
			final Ref ref = (Ref) refs.get(i);
			if (trimmed.equals(ref.path) || trimmed.equals(ref.nodeId)) {
				return ref.nodeId;
			}
		}
		for (int i = 0; i < refs.size(); i++) {
			final Ref ref = (Ref) refs.get(i);
			if (ref.path.endsWith(" / " + trimmed) || ref.path.equals(trimmed)) {
				return ref.nodeId;
			}
		}
		return trimmed;
	}

	public static String resolveCategoryPath(final MapModel map, final String nodeIdOrLegacy) {
		if (nodeIdOrLegacy == null || nodeIdOrLegacy.trim().length() == 0) {
			return "";
		}
		if (FinanceRules.isTotalBudgetCategory(nodeIdOrLegacy)) {
			return FinanceRules.TOTAL_BUDGET_CATEGORY;
		}
		if (map == null) {
			return nodeIdOrLegacy;
		}
		final NodeModel node = map.getNodeForID(nodeIdOrLegacy.trim());
		if (node != null && isCategoryNode(node)) {
			return categoryPathOf(node);
		}
		return nodeIdOrLegacy.trim();
	}

	public static String resolveAccountPath(final MapModel map, final String nodeIdOrLegacy) {
		if (nodeIdOrLegacy == null || nodeIdOrLegacy.trim().length() == 0) {
			return "";
		}
		if (map == null) {
			return nodeIdOrLegacy;
		}
		final NodeModel node = map.getNodeForID(nodeIdOrLegacy.trim());
		if (node != null && isAccountNode(node)) {
			return accountPathOf(node);
		}
		return nodeIdOrLegacy.trim();
	}

	public static boolean isTotalBudgetRef(final String categoryNodeId) {
		return categoryNodeId == null || categoryNodeId.trim().length() == 0
				|| FinanceRules.isTotalBudgetCategory(categoryNodeId);
	}

	public static boolean categoryMatchesBudget(final MapModel map, final String budgetCatNodeId,
			final String txnCatNodeId) {
		if (isTotalBudgetRef(budgetCatNodeId)) {
			return true;
		}
		if (txnCatNodeId == null || txnCatNodeId.trim().length() == 0) {
			return false;
		}
		if (budgetCatNodeId.equals(txnCatNodeId)) {
			return true;
		}
		if (map == null) {
			return false;
		}
		final NodeModel budgetNode = map.getNodeForID(budgetCatNodeId);
		final NodeModel txnNode = map.getNodeForID(txnCatNodeId);
		if (budgetNode == null || txnNode == null) {
			return budgetCatNodeId.equals(txnCatNodeId);
		}
		NodeModel cur = txnNode;
		while (cur != null) {
			if (cur == budgetNode) {
				return true;
			}
			cur = cur.getParentNode();
		}
		return false;
	}

	public static long sumExpenseForBudgetCategory(final MapModel map, final String budgetCatNodeId, final List txns) {
		if (txns == null || txns.isEmpty()) {
			return 0L;
		}
		long total = 0L;
		for (int i = 0; i < txns.size(); i++) {
			final Object item = txns.get(i);
			if (!(item instanceof FinanceLedgerService.FinanceTxn)) {
				continue;
			}
			final FinanceLedgerService.FinanceTxn txn = (FinanceLedgerService.FinanceTxn) item;
			if (!FinanceRules.isPnlExpense(txn.flow)) {
				continue;
			}
			if (isTotalBudgetRef(budgetCatNodeId)
					|| categoryMatchesBudget(map, budgetCatNodeId, txn.categoryNodeId)) {
				total += Math.abs(txn.amountCents);
			}
		}
		return total;
	}

	public static boolean budgetOverlapsRange(final FinanceExtension ext, final String fromYmd, final String toYmd) {
		if (ext == null) {
			return false;
		}
		String bStart = FinanceAttributes.datePart(ext.getPeriod());
		String bEnd = FinanceAttributes.datePart(ext.getExpires());
		if (bStart.length() == 0) {
			return true;
		}
		if (bEnd.length() == 0) {
			bEnd = bStart;
		}
		if (bStart.length() == 7) {
			bStart = bStart + "-01";
		}
		if (bEnd.length() == 7) {
			bEnd = bEnd + "-31";
		}
		final String from = FinanceAttributes.datePart(fromYmd);
		final String to = FinanceAttributes.datePart(toYmd);
		if (from.length() == 0 || to.length() == 0) {
			return true;
		}
		return bStart.compareTo(to) <= 0 && bEnd.compareTo(from) >= 0;
	}

	private static void collectCategoryNodes(final NodeModel parent, final List out, final String prefix) {
		if (parent == null || out == null) {
			return;
		}
		for (int i = 0; i < parent.getChildCount(); i++) {
			final NodeModel child = (NodeModel) parent.getChildAt(i);
			final String name = FinanceLedgerService.nodePlainText(child);
			if (name.length() == 0) {
				continue;
			}
			if (FinanceLedgerService.SUB_EXPENSE.equals(name) || FinanceLedgerService.SUB_INCOME.equals(name)) {
				collectCategoryNodes(child, out, prefix);
				continue;
			}
			final String path = prefix.length() == 0 ? name : prefix + " / " + name;
			out.add(new Ref(child.createID(), path));
			collectCategoryNodes(child, out, path);
		}
	}

	private static void collectAccountNodes(final NodeModel parent, final List out, final String prefix) {
		if (parent == null || out == null) {
			return;
		}
		for (int i = 0; i < parent.getChildCount(); i++) {
			walkAccountNode((NodeModel) parent.getChildAt(i), prefix, out);
		}
	}

	private static void walkAccountNode(final NodeModel node, final String prefix, final List out) {
		if (node == null) {
			return;
		}
		final String name = FinanceLedgerService.nodePlainText(node);
		if (name.length() == 0) {
			return;
		}
		final String path = prefix.length() == 0 ? name : prefix + " / " + name;
		out.add(new Ref(node.createID(), path));
		for (int i = 0; i < node.getChildCount(); i++) {
			walkAccountNode((NodeModel) node.getChildAt(i), path, out);
		}
	}

	private static boolean isUnderAccounts(final NodeModel node) {
		NodeModel cur = node;
		while (cur != null) {
			if (FinanceLedgerService.SECTION_ACCOUNTS.equals(FinanceLedgerService.nodePlainText(cur))) {
				return true;
			}
			cur = cur.getParentNode();
		}
		return false;
	}

	private static boolean isCategoryNode(final NodeModel node) {
		NodeModel cur = node;
		while (cur != null) {
			final String text = FinanceLedgerService.nodePlainText(cur);
			if (FinanceLedgerService.SECTION_CATEGORIES.equals(text)) {
				return true;
			}
			if (FinanceLedgerService.SECTION_ACCOUNTS.equals(text)
					|| FinanceLedgerService.SECTION_TXNS.equals(text)) {
				return false;
			}
			cur = cur.getParentNode();
		}
		return false;
	}

	private static boolean isAccountNode(final NodeModel node) {
		return isUnderAccounts(node)
				&& !FinanceLedgerService.SECTION_ACCOUNTS.equals(FinanceLedgerService.nodePlainText(node));
	}

	private static String categoryPathOf(final NodeModel node) {
		final StringBuilder sb = new StringBuilder();
		NodeModel cur = node;
		while (cur != null) {
			final String text = FinanceLedgerService.nodePlainText(cur);
			if (FinanceLedgerService.SECTION_CATEGORIES.equals(text)) {
				break;
			}
			if (FinanceLedgerService.SUB_EXPENSE.equals(text) || FinanceLedgerService.SUB_INCOME.equals(text)) {
				cur = cur.getParentNode();
				continue;
			}
			if (text.length() > 0 && !FinanceLedgerService.SECTION_CATEGORIES.equals(text)) {
				if (sb.length() > 0) {
					sb.insert(0, " / ");
				}
				sb.insert(0, text);
			}
			cur = cur.getParentNode();
		}
		return sb.toString();
	}

	private static String accountPathOf(final NodeModel node) {
		final StringBuilder sb = new StringBuilder();
		NodeModel cur = node;
		while (cur != null) {
			final String text = FinanceLedgerService.nodePlainText(cur);
			if (FinanceLedgerService.SECTION_ACCOUNTS.equals(text)) {
				break;
			}
			if (text.length() > 0) {
				if (sb.length() > 0) {
					sb.insert(0, " / ");
				}
				sb.insert(0, text);
			}
			cur = cur.getParentNode();
		}
		return sb.toString();
	}

	private static NodeModel findCategoryByBareName(final MapModel map, final String bareName) {
		final List refs = listCategoryRefs("all");
		NodeModel match = null;
		for (int i = 0; i < refs.size(); i++) {
			final Ref ref = (Ref) refs.get(i);
			if (bareName.equals(ref.path) || ref.path.endsWith(" / " + bareName)) {
				if (match != null) {
					return null;
				}
				match = map.getNodeForID(ref.nodeId);
			}
		}
		return match;
	}

	private static NodeModel findChild(final NodeModel parent, final String text) {
		if (parent == null) {
			return null;
		}
		for (int i = 0; i < parent.getChildCount(); i++) {
			final NodeModel child = (NodeModel) parent.getChildAt(i);
			if (text.equals(FinanceLedgerService.nodePlainText(child))) {
				return child;
			}
		}
		return null;
	}
}
