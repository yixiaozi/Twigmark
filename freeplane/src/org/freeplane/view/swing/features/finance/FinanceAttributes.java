package org.freeplane.view.swing.features.finance;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.freeplane.core.undo.IActor;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

/**
 * Hidden XML attribute names and undoable read/write for personal-finance node state.
 * Amounts are stored as integer cents (string form in XML).
 */
public final class FinanceAttributes {
	public static final String FINANCE_KIND = "FINANCE_KIND";
	public static final String FINANCE_AMOUNT = "FINANCE_AMOUNT";
	public static final String FINANCE_CURRENCY = "FINANCE_CURRENCY";
	public static final String FINANCE_DATE = "FINANCE_DATE";
	public static final String FINANCE_FLOW = "FINANCE_FLOW";
	public static final String FINANCE_PERIOD = "FINANCE_PERIOD";
	public static final String FINANCE_CAT_ID = "FINANCE_CAT_ID";
	public static final String FINANCE_ACCOUNT_ID = "FINANCE_ACCOUNT_ID";
	public static final String FINANCE_CYCLE = "FINANCE_CYCLE";
	public static final String FINANCE_STATUS = "FINANCE_STATUS";
	public static final String FINANCE_MERCHANT = "FINANCE_MERCHANT";
	public static final String FINANCE_NOTE = "FINANCE_NOTE";
	public static final String FINANCE_NEXT = "FINANCE_NEXT";
	public static final String FINANCE_EXPIRES = "FINANCE_EXPIRES";

	public static final String KIND_ROOT = "root";
	public static final String KIND_ACCOUNT = "account";
	public static final String KIND_CATEGORY = "category";
	public static final String KIND_BUDGET = "budget";
	public static final String KIND_TXN = "txn";
	public static final String KIND_SUBSCRIPTION = "subscription";
	public static final String KIND_COUPON = "coupon";

	public static final String FLOW_INCOME = "income";
	public static final String FLOW_EXPENSE = "expense";
	public static final String FLOW_TRANSFER = "transfer";
	public static final String FLOW_BORROW = "borrow";
	public static final String FLOW_LEND = "lend";
	public static final String FLOW_CREDIT = "credit";

	public static final String DEFAULT_CURRENCY = "CNY";

	private FinanceAttributes() {
	}

	public static FinanceExtension read(final NodeModel node) {
		return FinanceExtension.getExtension(node);
	}

	public static void write(final NodeModel node, final FinanceExtension desired) {
		if (node == null || desired == null) {
			return;
		}
		final FinanceExtension before = copyOrNull(FinanceExtension.getExtension(node));
		final FinanceExtension after = desired.copy();
		final MapController mapController = Controller.getCurrentModeController().getMapController();
		Controller.getCurrentModeController().execute(new IActor() {
			public void act() {
				applyToNode(node, after);
				mapController.nodeChanged(node, FinanceExtension.class, before, after);
			}

			public String getDescription() {
				return "finance";
			}

			public void undo() {
				if (before == null || before.isEmpty()) {
					final FinanceExtension existing = FinanceExtension.getExtension(node);
					if (existing != null) {
						node.removeExtension(existing);
					}
				}
				else {
					FinanceExtension.getOrCreateExtension(node).apply(before);
				}
				mapController.nodeChanged(node, FinanceExtension.class, after, before);
			}
		}, node.getMap());
	}

	public static void writeSilent(final NodeModel node, final FinanceExtension desired) {
		if (node == null || desired == null) {
			return;
		}
		final FinanceExtension before = copyOrNull(FinanceExtension.getExtension(node));
		final FinanceExtension after = desired.copy();
		applyToNode(node, after);
		try {
			Controller.getCurrentModeController().getMapController()
					.nodeChanged(node, FinanceExtension.class, before, after);
		}
		catch (Exception e) {
		}
	}

	static void applyToNode(final NodeModel node, final FinanceExtension value) {
		if (value == null || value.isEmpty()) {
			final FinanceExtension existing = FinanceExtension.getExtension(node);
			if (existing != null) {
				node.removeExtension(existing);
			}
			return;
		}
		FinanceExtension.getOrCreateExtension(node).apply(value);
	}

	private static FinanceExtension copyOrNull(final FinanceExtension source) {
		return source == null ? null : source.copy();
	}

	public static long parseLong(final String value, final long defaultValue) {
		if (value == null || value.trim().length() == 0) {
			return defaultValue;
		}
		try {
			return Long.parseLong(value.trim());
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public static boolean parseBoolean(final String value) {
		if (value == null) {
			return false;
		}
		final String v = value.trim();
		return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
	}

	/** Format cents as yuan with 2 decimals, e.g. 2800 → "28.00". */
	public static String formatYuan(final long cents) {
		final boolean neg = cents < 0;
		final long abs = neg ? -cents : cents;
		final long yuan = abs / 100L;
		final long fen = abs % 100L;
		final String body = yuan + "." + (fen < 10 ? "0" : "") + fen;
		return neg ? "-" + body : body;
	}

	/**
	 * Parse a yuan amount string (e.g. "28", "28.5", "28.50", "¥28.00") into integer cents.
	 * Pure digit strings without a decimal point are treated as yuan (not cents).
	 */
	public static long parseYuanToCents(final String text) {
		if (text == null) {
			return 0L;
		}
		String s = text.trim();
		if (s.length() == 0) {
			return 0L;
		}
		if (s.startsWith("¥") || s.startsWith("￥")) {
			s = s.substring(1).trim();
		}
		s = s.replace(",", "");
		boolean neg = false;
		if (s.startsWith("-")) {
			neg = true;
			s = s.substring(1).trim();
		}
		else if (s.startsWith("+")) {
			s = s.substring(1).trim();
		}
		if (s.length() == 0) {
			return 0L;
		}
		try {
			final int dot = s.indexOf('.');
			long cents;
			if (dot < 0) {
				cents = Long.parseLong(s) * 100L;
			}
			else {
				final String whole = dot == 0 ? "0" : s.substring(0, dot);
				String frac = s.substring(dot + 1);
				if (frac.length() == 0) {
					frac = "00";
				}
				else if (frac.length() == 1) {
					frac = frac + "0";
				}
				else if (frac.length() > 2) {
					frac = frac.substring(0, 2);
				}
				cents = Long.parseLong(whole) * 100L + Long.parseLong(frac);
			}
			return neg ? -cents : cents;
		}
		catch (NumberFormatException e) {
			return 0L;
		}
	}

	public static String todayYmd() {
		return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
	}
}
