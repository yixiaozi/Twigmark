package org.freeplane.view.swing.features.finance;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;

/**
 * When the user edits finance node labels in the mind map, sync hidden {@link FinanceExtension}
 * metadata so the sidebar and reports stay consistent. Data is keyed by node ID, so moving /
 * cutting a node does not break associations.
 */
public final class FinanceNodeSync {
	private static boolean installed;
	private static boolean syncing;
	private static boolean refreshQueued;

	private FinanceNodeSync() {
	}

	public static void install(final ModeController modeController) {
		if (installed || modeController == null) {
			return;
		}
		installed = true;
		modeController.getMapController().addNodeChangeListener(new INodeChangeListener() {
			public void nodeChanged(final NodeChangeEvent event) {
				if (!NodeModel.NODE_TEXT.equals(event.getProperty())) {
					return;
				}
				if (syncing) {
					return;
				}
				final NodeModel node = event.getNode();
				if (node == null || !isFinanceMap(node.getMap())) {
					return;
				}
				syncFromLabel(node);
				queueRefresh();
			}
		});
	}

	private static void queueRefresh() {
		if (refreshQueued) {
			return;
		}
		refreshQueued = true;
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				refreshQueued = false;
				FinanceChangeNotifier.fireChanged();
			}
		});
	}

	private static boolean isFinanceMap(final MapModel map) {
		if (map == null || map.getRootNode() == null) {
			return false;
		}
		final FinanceExtension rootExt = FinanceExtension.getExtension(map.getRootNode());
		return rootExt != null && FinanceAttributes.KIND_ROOT.equals(rootExt.getKind());
	}

	static void syncFromLabel(final NodeModel node) {
		if (node == null) {
			return;
		}
		final FinanceExtension ext = FinanceExtension.getExtension(node);
		if (ext == null || ext.isEmpty()) {
			return;
		}
		final String label = FinanceLedgerService.nodePlainText(node);
		final String kind = ext.getKind();
		syncing = true;
		try {
			final FinanceExtension updated = ext.copy();
			if (FinanceAttributes.KIND_TXN.equals(kind) || FinanceAttributes.KIND_PAYMENT.equals(kind)) {
				syncTxn(updated, label);
			}
			else if (FinanceAttributes.KIND_BUDGET.equals(kind)) {
				syncBudget(updated, label);
			}
			else if (FinanceAttributes.KIND_SUBSCRIPTION.equals(kind)) {
				syncSubscription(updated, label);
			}
			else if (FinanceAttributes.KIND_COUPON.equals(kind)) {
				syncCoupon(updated, label);
			}
			else {
				return;
			}
			if (!extensionEquals(ext, updated)) {
				FinanceAttributes.writeSilent(node, updated);
				try {
					FinanceLedgerService.persistFinanceMap(node.getMap());
				}
				catch (Exception e) {
					LogUtils.warn("Finance: persist after label sync failed", e);
				}
			}
		}
		finally {
			syncing = false;
		}
	}

	private static void syncTxn(final FinanceExtension ext, final String label) {
		final Long cents = FinanceRules.parseAmountFromLabel(label);
		if (cents != null && cents.longValue() > 0L) {
			ext.setAmountCents(cents.longValue());
		}
		final String title = FinanceRules.bareNameFromLabel(label, ext.getMerchant());
		if (title.length() > 0 && title.indexOf("付款") < 0) {
			ext.setMerchant(title);
		}
		final String[] range = FinanceRules.parseDateRangeFromLabel(label);
		if (range[0].length() > 0) {
			final String existing = FinanceAttributes.datePart(ext.getDate());
			if (!range[0].equals(existing)) {
				final String time = ext.getDate() != null && ext.getDate().length() > 10
						? ext.getDate().substring(10)
						: " 00:00";
				ext.setDate(range[0] + (time.startsWith(" ") ? time : " " + time.trim()));
			}
		}
	}

	private static void syncBudget(final FinanceExtension ext, final String label) {
		final Long cents = FinanceRules.parseAmountFromLabel(label);
		if (cents != null && cents.longValue() > 0L) {
			ext.setAmountCents(cents.longValue());
		}
		final String[] range = FinanceRules.parseDateRangeFromLabel(label);
		if (range[0].length() > 0) {
			ext.setPeriod(range[0]);
		}
		if (range[1].length() > 0) {
			ext.setExpires(range[1]);
		}
		else if (range[0].length() == 10 && range[0].endsWith("-01") == false && ext.getExpires().length() == 0) {
			// single day budget
			ext.setExpires(range[0]);
		}
		final String catLabel = FinanceRules.budgetCategoryFromLabel(label, "");
		if (FinanceRules.isTotalBudgetCategory(catLabel) || "总预算".equals(catLabel)) {
			ext.setCatId(FinanceNodeRef.TOTAL_BUDGET_NODE_ID);
		}
		else if (catLabel.length() > 0 && !looksLikeDate(catLabel)) {
			final MapModel map = FinanceLedgerService.preferOpenFinanceMapPublic();
			if (map != null) {
				ext.setCatId(FinanceNodeRef.normalizeCategoryRef(map, catLabel));
			}
		}
	}

	private static void syncSubscription(final FinanceExtension ext, final String label) {
		final Long cents = FinanceRules.parseAmountFromLabel(label);
		if (cents != null && cents.longValue() > 0L) {
			ext.setAmountCents(cents.longValue());
		}
		final String cycle = FinanceRules.parseCycleFromLabel(label);
		if (cycle.length() > 0) {
			ext.setCycle(cycle);
		}
		final String[] range = FinanceRules.parseDateRangeFromLabel(label);
		if (range[0].length() > 0) {
			ext.setDate(range[0]);
		}
		if (range[1].length() > 0) {
			ext.setExpires(range[1]);
		}
		String name = FinanceRules.bareNameFromLabel(label, ext.getMerchant());
		name = stripTrailingMeta(name);
		if (name.length() > 0) {
			ext.setMerchant(name);
		}
	}

	private static void syncCoupon(final FinanceExtension ext, final String label) {
		final Long cents = FinanceRules.parseAmountFromLabel(label);
		if (cents != null && cents.longValue() > 0L) {
			ext.setAmountCents(cents.longValue());
		}
		final String[] range = FinanceRules.parseDateRangeFromLabel(label);
		if (range[0].length() > 0) {
			ext.setExpires(range[0]);
		}
		final String name = FinanceRules.bareNameFromLabel(label, ext.getMerchant());
		if (name.length() > 0) {
			ext.setMerchant(name);
		}
	}

	private static boolean looksLikeDate(final String text) {
		return text != null && text.matches("20\\d{2}-\\d{2}(-\\d{2})?");
	}

	private static String stripTrailingMeta(final String name) {
		if (name == null) {
			return "";
		}
		String n = name.trim();
		final String[] cycles = new String[] { "每月", "每年", "每周", "每天", "每日", "月付" };
		for (int i = 0; i < cycles.length; i++) {
			if (n.endsWith(" · " + cycles[i])) {
				n = n.substring(0, n.length() - (" · " + cycles[i]).length()).trim();
			}
			else if (n.endsWith(cycles[i]) && n.length() > cycles[i].length()) {
				n = n.substring(0, n.length() - cycles[i].length()).trim();
				if (n.endsWith("·") || n.endsWith("· ")) {
					n = n.substring(0, n.length() - 1).trim();
				}
			}
		}
		return n;
	}

	private static boolean extensionEquals(final FinanceExtension a, final FinanceExtension b) {
		if (a == null || b == null) {
			return a == b;
		}
		return a.getAmountCents() == b.getAmountCents()
				&& a.getCatId().equals(b.getCatId())
				&& a.getMerchant().equals(b.getMerchant())
				&& a.getDate().equals(b.getDate())
				&& a.getPeriod().equals(b.getPeriod())
				&& a.getExpires().equals(b.getExpires())
				&& a.getCycle().equals(b.getCycle())
				&& a.getNext().equals(b.getNext())
				&& a.getNote().equals(b.getNote());
	}
}
