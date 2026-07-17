package org.freeplane.view.swing.features.finance;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.features.url.mindmapmode.MFileManager;

/**
 * Central business logic for the personal-finance mind map ledger.
 */
public final class FinanceLedgerService {
	public static final String PROP_MAP_PATH = "finance.map.path";
	public static final String DEFAULT_FILENAME = "个人财务.mm";

	public static final String SECTION_ACCOUNTS = "账户";
	public static final String SECTION_CATEGORIES = "分类";
	public static final String SECTION_BUDGETS = "预算";
	public static final String SECTION_TXNS = "交易";
	public static final String SECTION_SUBSCRIPTIONS = "订阅";
	public static final String SECTION_COUPONS = "优惠券";
	public static final String SUB_EXPENSE = "支出";
	public static final String SUB_INCOME = "收入";

	private FinanceLedgerService() {
	}

	public static File resolveFinanceMapFile() {
		String configured = DEFAULT_FILENAME;
		try {
			configured = ResourceController.getResourceController().getProperty(PROP_MAP_PATH, DEFAULT_FILENAME);
		}
		catch (Exception e) {
		}
		if (configured == null || configured.trim().length() == 0) {
			configured = DEFAULT_FILENAME;
		}
		configured = configured.trim();
		final File asAbsolute = new File(configured);
		if (asAbsolute.isAbsolute()) {
			return asAbsolute;
		}
		File dir = null;
		try {
			dir = MindMapDataRootResolver.getProjectDataDirectory();
		}
		catch (Exception e) {
		}
		if (dir == null || !dir.isDirectory()) {
			try {
				dir = new File(ResourceController.getResourceController().getFreeplaneUserDirectory());
			}
			catch (Exception e) {
				dir = new File(System.getProperty("user.home", "."));
			}
		}
		return new File(dir, configured);
	}

	/**
	 * Ensure the finance map exists (create skeleton if missing) and is open.
	 */
	public static MapModel ensureFinanceMap() {
		final File file = resolveFinanceMapFile();
		try {
			final MapModel open = findOpenFinanceMap(file);
			if (open != null) {
				ensureSkeleton(open);
				return open;
			}
			if (file.isFile()) {
				final URL url = Compat.fileToUrl(file);
				final ModeController modeController = Controller.getCurrentModeController();
				modeController.getMapController().newMap(url);
				final MapModel loaded = findOpenFinanceMap(file);
				if (loaded != null) {
					ensureSkeleton(loaded);
					return loaded;
				}
				return Controller.getCurrentController().getMap();
			}
			final File parent = file.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			final ModeController modeController = Controller.getCurrentModeController();
			final MFileManager fileManager = MFileManager.getController(modeController);
			final MapModel map = fileManager.newMapFromDefaultTemplate();
			if (map == null) {
				LogUtils.warn("Finance: failed to create map from template");
				return null;
			}
			final NodeModel root = map.getRootNode();
			setNodeText(root, "个人财务");
			final FinanceExtension rootExt = new FinanceExtension();
			rootExt.setKind(FinanceAttributes.KIND_ROOT);
			FinanceAttributes.writeSilent(root, rootExt);
			buildSkeletonSections(root);
			fileManager.save(map, file);
			LogUtils.info("Finance: created ledger map at " + file.getAbsolutePath());
			return map;
		}
		catch (Exception e) {
			LogUtils.warn("Finance: ensureFinanceMap failed", e);
			return null;
		}
	}

	private static MapModel findOpenFinanceMap(final File file) {
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller == null) {
				return null;
			}
			final MapModel current = controller.getMap();
			if (isFinanceMap(current, file)) {
				return current;
			}
			final Map maps = controller.getMapViewManager().getMaps();
			if (maps == null) {
				return null;
			}
			final Iterator it = maps.values().iterator();
			while (it.hasNext()) {
				final MapModel map = (MapModel) it.next();
				if (isFinanceMap(map, file)) {
					return map;
				}
			}
		}
		catch (Exception e) {
		}
		return null;
	}

	private static boolean isFinanceMap(final MapModel map, final File file) {
		if (map == null || map.getRootNode() == null) {
			return false;
		}
		if (file != null && map.getFile() != null) {
			try {
				if (file.getCanonicalFile().equals(map.getFile().getCanonicalFile())) {
					return true;
				}
			}
			catch (Exception e) {
				if (file.getAbsolutePath().equals(map.getFile().getAbsolutePath())) {
					return true;
				}
			}
		}
		final FinanceExtension ext = FinanceExtension.getExtension(map.getRootNode());
		return ext != null && FinanceAttributes.KIND_ROOT.equals(ext.getKind());
	}

	private static void ensureSkeleton(final MapModel map) {
		if (map == null || map.getRootNode() == null) {
			return;
		}
		final NodeModel root = map.getRootNode();
		final FinanceExtension rootExt = FinanceExtension.getExtension(root);
		if (rootExt == null || rootExt.isEmpty()) {
			final FinanceExtension e = new FinanceExtension();
			e.setKind(FinanceAttributes.KIND_ROOT);
			FinanceAttributes.writeSilent(root, e);
		}
		if (findSection(map, SECTION_ACCOUNTS) == null || findSection(map, SECTION_CATEGORIES) == null
				|| findSection(map, SECTION_BUDGETS) == null || findSection(map, SECTION_TXNS) == null
				|| findSection(map, SECTION_SUBSCRIPTIONS) == null || findSection(map, SECTION_COUPONS) == null) {
			buildSkeletonSections(root);
		}
	}

	private static void buildSkeletonSections(final NodeModel root) {
		final NodeModel accounts = findOrCreateChild(root, SECTION_ACCOUNTS);
		ensureSampleAccounts(accounts);
		final NodeModel categories = findOrCreateChild(root, SECTION_CATEGORIES);
		final NodeModel expense = findOrCreateChild(categories, SUB_EXPENSE);
		ensureSampleCategories(expense, new String[] { "餐饮", "交通", "住房", "订阅", "其他" },
				FinanceAttributes.FLOW_EXPENSE);
		final NodeModel income = findOrCreateChild(categories, SUB_INCOME);
		ensureSampleCategories(income, new String[] { "工资", "奖金", "其他" }, FinanceAttributes.FLOW_INCOME);
		findOrCreateChild(root, SECTION_BUDGETS);
		findOrCreateChild(root, SECTION_TXNS);
		findOrCreateChild(root, SECTION_SUBSCRIPTIONS);
		findOrCreateChild(root, SECTION_COUPONS);
	}

	private static void ensureSampleAccounts(final NodeModel accounts) {
		final String[] names = { "现金", "银行卡", "信用卡" };
		for (int i = 0; i < names.length; i++) {
			if (findChildByText(accounts, names[i]) == null) {
				addAccountNode(accounts, names[i]);
			}
		}
	}

	private static void ensureSampleCategories(final NodeModel parent, final String[] names, final String flow) {
		for (int i = 0; i < names.length; i++) {
			if (findChildByText(parent, names[i]) == null) {
				addCategoryNode(parent, names[i], flow);
			}
		}
	}

	public static NodeModel findSection(final MapModel map, final String name) {
		if (map == null || map.getRootNode() == null || name == null) {
			return null;
		}
		return findChildByText(map.getRootNode(), name);
	}

	public static NodeModel findOrCreateMonthFolder(final NodeModel txnSection, final String yyyyMm) {
		if (txnSection == null || yyyyMm == null || yyyyMm.trim().length() == 0) {
			return null;
		}
		return findOrCreateChild(txnSection, yyyyMm.trim());
	}

	public static NodeModel addTransaction(final String amountYuanOrCents, final String flow, final String dateYmd,
			final String categoryName, final String accountName, final String merchant, final String note) {
		final MapModel map = ensureFinanceMap();
		if (map == null) {
			return null;
		}
		final NodeModel txnSection = findSection(map, SECTION_TXNS);
		if (txnSection == null) {
			return null;
		}
		final String date = dateYmd == null || dateYmd.trim().length() == 0 ? FinanceAttributes.todayYmd()
				: dateYmd.trim();
		final String month = date.length() >= 7 ? date.substring(0, 7) : date;
		final NodeModel monthFolder = findOrCreateMonthFolder(txnSection, month);
		if (monthFolder == null) {
			return null;
		}
		final long cents = resolveAmountCents(amountYuanOrCents);
		final String flowValue = flow == null || flow.trim().length() == 0 ? FinanceAttributes.FLOW_EXPENSE
				: flow.trim();
		final String label = buildTxnLabel(merchant, note, categoryName, cents);
		final NodeModel node = addChildNode(monthFolder, label);
		if (node == null) {
			return null;
		}
		final FinanceExtension ext = new FinanceExtension();
		ext.setKind(FinanceAttributes.KIND_TXN);
		ext.setAmountCents(cents);
		ext.setCurrency(FinanceAttributes.DEFAULT_CURRENCY);
		ext.setDate(date);
		ext.setFlow(flowValue);
		ext.setCatId(categoryName == null ? "" : categoryName.trim());
		ext.setAccountId(accountName == null ? "" : accountName.trim());
		ext.setMerchant(merchant == null ? "" : merchant.trim());
		ext.setNote(note == null ? "" : note.trim());
		FinanceAttributes.writeSilent(node, ext);
		return node;
	}

	public static List listTransactions(final String fromYmd, final String toYmd) {
		final List out = new ArrayList();
		final MapModel map = preferOpenFinanceMap();
		if (map == null) {
			return out;
		}
		final List nodes = new ArrayList();
		collectByKind(map.getRootNode(), FinanceAttributes.KIND_TXN, nodes);
		for (int i = 0; i < nodes.size(); i++) {
			final NodeModel node = (NodeModel) nodes.get(i);
			final FinanceExtension ext = FinanceExtension.getExtension(node);
			if (ext == null) {
				continue;
			}
			final String date = ext.getDate();
			if (!inDateRange(date, fromYmd, toYmd)) {
				continue;
			}
			final FinanceTxn txn = new FinanceTxn();
			txn.node = node;
			txn.amountCents = ext.getAmountCents();
			txn.flow = ext.getFlow();
			txn.dateYmd = date;
			txn.categoryName = ext.getCatId();
			txn.accountName = ext.getAccountId();
			txn.merchant = ext.getMerchant();
			txn.note = ext.getNote();
			txn.currency = ext.getCurrency();
			txn.nodeText = plainText(node);
			out.add(txn);
		}
		Collections.sort(out, new Comparator() {
			public int compare(final Object a, final Object b) {
				final String da = ((FinanceTxn) a).dateYmd;
				final String db = ((FinanceTxn) b).dateYmd;
				final int c = (da == null ? "" : da).compareTo(db == null ? "" : db);
				return c == 0 ? 0 : c;
			}
		});
		return out;
	}

	public static List listCategories(final String flow) {
		final List out = new ArrayList();
		final MapModel map = preferOpenFinanceMap();
		if (map == null) {
			return out;
		}
		final NodeModel categories = findSection(map, SECTION_CATEGORIES);
		if (categories == null) {
			return out;
		}
		NodeModel parent = categories;
		if (FinanceAttributes.FLOW_EXPENSE.equals(flow)) {
			parent = findChildByText(categories, SUB_EXPENSE);
		}
		else if (FinanceAttributes.FLOW_INCOME.equals(flow)) {
			parent = findChildByText(categories, SUB_INCOME);
		}
		if (parent == null) {
			return out;
		}
		for (int i = 0; i < parent.getChildCount(); i++) {
			final NodeModel child = (NodeModel) parent.getChildAt(i);
			final FinanceExtension ext = FinanceExtension.getExtension(child);
			if (ext != null && FinanceAttributes.KIND_CATEGORY.equals(ext.getKind())) {
				out.add(plainText(child));
			}
			else if (ext == null) {
				final String text = plainText(child);
				if (text.length() > 0 && !SUB_EXPENSE.equals(text) && !SUB_INCOME.equals(text)) {
					out.add(text);
				}
			}
		}
		return out;
	}

	public static List listAccounts() {
		final List out = new ArrayList();
		final MapModel map = preferOpenFinanceMap();
		if (map == null) {
			return out;
		}
		final NodeModel accounts = findSection(map, SECTION_ACCOUNTS);
		if (accounts == null) {
			return out;
		}
		for (int i = 0; i < accounts.getChildCount(); i++) {
			final NodeModel child = (NodeModel) accounts.getChildAt(i);
			final FinanceExtension ext = FinanceExtension.getExtension(child);
			if (ext != null && FinanceAttributes.KIND_ACCOUNT.equals(ext.getKind())) {
				out.add(plainText(child));
			}
			else if (ext == null) {
				final String text = plainText(child);
				if (text.length() > 0) {
					out.add(text);
				}
			}
		}
		return out;
	}

	public static NodeModel addCategory(final String name, final String flow) {
		final MapModel map = ensureFinanceMap();
		if (map == null || name == null || name.trim().length() == 0) {
			return null;
		}
		final NodeModel categories = findSection(map, SECTION_CATEGORIES);
		if (categories == null) {
			return null;
		}
		final String flowValue = flow == null ? FinanceAttributes.FLOW_EXPENSE : flow.trim();
		final String folderName = FinanceAttributes.FLOW_INCOME.equals(flowValue) ? SUB_INCOME : SUB_EXPENSE;
		final NodeModel parent = findOrCreateChild(categories, folderName);
		final NodeModel existing = findChildByText(parent, name.trim());
		if (existing != null) {
			return existing;
		}
		return addCategoryNode(parent, name.trim(), flowValue);
	}

	public static NodeModel addAccount(final String name) {
		final MapModel map = ensureFinanceMap();
		if (map == null || name == null || name.trim().length() == 0) {
			return null;
		}
		final NodeModel accounts = findSection(map, SECTION_ACCOUNTS);
		if (accounts == null) {
			return null;
		}
		final NodeModel existing = findChildByText(accounts, name.trim());
		if (existing != null) {
			return existing;
		}
		return addAccountNode(accounts, name.trim());
	}

	public static NodeModel setBudget(final String periodYyyyMm, final String categoryName, final long amountCents) {
		final MapModel map = ensureFinanceMap();
		if (map == null || periodYyyyMm == null || categoryName == null) {
			return null;
		}
		final NodeModel budgets = findSection(map, SECTION_BUDGETS);
		if (budgets == null) {
			return null;
		}
		final NodeModel periodFolder = findOrCreateChild(budgets, periodYyyyMm.trim());
		NodeModel node = findChildByText(periodFolder, categoryName.trim());
		if (node == null) {
			node = addChildNode(periodFolder, categoryName.trim() + " · ¥" + FinanceAttributes.formatYuan(amountCents));
		}
		else {
			setNodeText(node, categoryName.trim() + " · ¥" + FinanceAttributes.formatYuan(amountCents));
		}
		if (node == null) {
			return null;
		}
		final FinanceExtension ext = new FinanceExtension();
		ext.setKind(FinanceAttributes.KIND_BUDGET);
		ext.setPeriod(periodYyyyMm.trim());
		ext.setCatId(categoryName.trim());
		ext.setAmountCents(amountCents);
		ext.setCurrency(FinanceAttributes.DEFAULT_CURRENCY);
		ext.setFlow(FinanceAttributes.FLOW_EXPENSE);
		FinanceAttributes.writeSilent(node, ext);
		return node;
	}

	public static List listBudgets(final String period) {
		final List out = new ArrayList();
		final MapModel map = preferOpenFinanceMap();
		if (map == null) {
			return out;
		}
		final List nodes = new ArrayList();
		collectByKind(map.getRootNode(), FinanceAttributes.KIND_BUDGET, nodes);
		for (int i = 0; i < nodes.size(); i++) {
			final NodeModel node = (NodeModel) nodes.get(i);
			final FinanceExtension ext = FinanceExtension.getExtension(node);
			if (ext == null) {
				continue;
			}
			if (period != null && period.trim().length() > 0 && !period.trim().equals(ext.getPeriod())) {
				continue;
			}
			final FinanceBudget budget = new FinanceBudget();
			budget.node = node;
			budget.period = ext.getPeriod();
			budget.categoryName = ext.getCatId();
			budget.amountCents = ext.getAmountCents();
			out.add(budget);
		}
		return out;
	}

	public static NodeModel upsertSubscription(final String name, final long amountCents, final String cycle,
			final String nextYmd, final String status, final String accountName, final String note) {
		final MapModel map = ensureFinanceMap();
		if (map == null || name == null || name.trim().length() == 0) {
			return null;
		}
		final NodeModel section = findSection(map, SECTION_SUBSCRIPTIONS);
		if (section == null) {
			return null;
		}
		NodeModel node = findSubscriptionByName(section, name.trim());
		final String label = name.trim() + " · ¥" + FinanceAttributes.formatYuan(amountCents);
		if (node == null) {
			node = addChildNode(section, label);
		}
		else {
			setNodeText(node, label);
		}
		if (node == null) {
			return null;
		}
		final FinanceExtension ext = new FinanceExtension();
		ext.setKind(FinanceAttributes.KIND_SUBSCRIPTION);
		ext.setAmountCents(amountCents);
		ext.setCurrency(FinanceAttributes.DEFAULT_CURRENCY);
		ext.setCycle(cycle == null ? "" : cycle.trim());
		ext.setNext(nextYmd == null ? "" : nextYmd.trim());
		ext.setStatus(status == null ? "active" : status.trim());
		ext.setAccountId(accountName == null ? "" : accountName.trim());
		ext.setNote(note == null ? "" : note.trim());
		ext.setMerchant(name.trim());
		FinanceAttributes.writeSilent(node, ext);
		return node;
	}

	public static List listSubscriptions() {
		final List out = new ArrayList();
		final MapModel map = preferOpenFinanceMap();
		if (map == null) {
			return out;
		}
		final List nodes = new ArrayList();
		collectByKind(map.getRootNode(), FinanceAttributes.KIND_SUBSCRIPTION, nodes);
		for (int i = 0; i < nodes.size(); i++) {
			final NodeModel node = (NodeModel) nodes.get(i);
			final FinanceExtension ext = FinanceExtension.getExtension(node);
			if (ext == null) {
				continue;
			}
			final FinanceSubscription sub = new FinanceSubscription();
			sub.node = node;
			sub.name = ext.getMerchant().length() > 0 ? ext.getMerchant() : plainText(node);
			sub.amountCents = ext.getAmountCents();
			sub.cycle = ext.getCycle();
			sub.nextYmd = ext.getNext();
			sub.status = ext.getStatus();
			sub.accountName = ext.getAccountId();
			sub.note = ext.getNote();
			out.add(sub);
		}
		return out;
	}

	public static NodeModel upsertCoupon(final String name, final long amountCents, final String expiresYmd,
			final String status, final String merchant, final String note) {
		final MapModel map = ensureFinanceMap();
		if (map == null || name == null || name.trim().length() == 0) {
			return null;
		}
		final NodeModel section = findSection(map, SECTION_COUPONS);
		if (section == null) {
			return null;
		}
		NodeModel node = findCouponByName(section, name.trim());
		final String label = name.trim() + " · ¥" + FinanceAttributes.formatYuan(amountCents);
		if (node == null) {
			node = addChildNode(section, label);
		}
		else {
			setNodeText(node, label);
		}
		if (node == null) {
			return null;
		}
		final FinanceExtension ext = new FinanceExtension();
		ext.setKind(FinanceAttributes.KIND_COUPON);
		ext.setAmountCents(amountCents);
		ext.setCurrency(FinanceAttributes.DEFAULT_CURRENCY);
		ext.setExpires(expiresYmd == null ? "" : expiresYmd.trim());
		ext.setStatus(status == null ? "active" : status.trim());
		ext.setMerchant(merchant == null ? name.trim() : merchant.trim());
		ext.setNote(note == null ? "" : note.trim());
		FinanceAttributes.writeSilent(node, ext);
		return node;
	}

	public static List listCoupons() {
		final List out = new ArrayList();
		final MapModel map = preferOpenFinanceMap();
		if (map == null) {
			return out;
		}
		final List nodes = new ArrayList();
		collectByKind(map.getRootNode(), FinanceAttributes.KIND_COUPON, nodes);
		for (int i = 0; i < nodes.size(); i++) {
			final NodeModel node = (NodeModel) nodes.get(i);
			final FinanceExtension ext = FinanceExtension.getExtension(node);
			if (ext == null) {
				continue;
			}
			final FinanceCoupon coupon = new FinanceCoupon();
			coupon.node = node;
			coupon.name = plainText(node);
			coupon.amountCents = ext.getAmountCents();
			coupon.expiresYmd = ext.getExpires();
			coupon.status = ext.getStatus();
			coupon.merchant = ext.getMerchant();
			coupon.note = ext.getNote();
			out.add(coupon);
		}
		return out;
	}

	public static MonthSummary monthSummary(final String yyyyMm) {
		final MonthSummary summary = new MonthSummary();
		summary.period = yyyyMm == null ? "" : yyyyMm.trim();
		summary.byCategory = new TreeMap();
		if (summary.period.length() < 7) {
			return summary;
		}
		final String from = summary.period + "-01";
		final String to = summary.period + "-31";
		final List txns = listTransactions(from, to);
		for (int i = 0; i < txns.size(); i++) {
			final FinanceTxn txn = (FinanceTxn) txns.get(i);
			if (txn.dateYmd == null || !txn.dateYmd.startsWith(summary.period)) {
				continue;
			}
			if (FinanceAttributes.FLOW_INCOME.equals(txn.flow)
					|| FinanceAttributes.FLOW_BORROW.equals(txn.flow)) {
				// Borrow increases available cash (liability), counted with income for net cash view.
				summary.incomeCents += Math.abs(txn.amountCents);
			}
			else if (FinanceAttributes.FLOW_EXPENSE.equals(txn.flow)
					|| FinanceAttributes.FLOW_CREDIT.equals(txn.flow)
					|| FinanceAttributes.FLOW_LEND.equals(txn.flow)) {
				summary.expenseCents += Math.abs(txn.amountCents);
				final String cat = txn.categoryName == null || txn.categoryName.length() == 0 ? "其他"
						: txn.categoryName;
				final Long prev = (Long) summary.byCategory.get(cat);
				summary.byCategory.put(cat,
						Long.valueOf((prev == null ? 0L : prev.longValue()) + Math.abs(txn.amountCents)));
			}
		}
		return summary;
	}

	private static MapModel preferOpenFinanceMap() {
		final File file = resolveFinanceMapFile();
		final MapModel open = findOpenFinanceMap(file);
		if (open != null) {
			return open;
		}
		try {
			final MapModel current = Controller.getCurrentController().getMap();
			if (current != null && current.getRootNode() != null) {
				final FinanceExtension ext = FinanceExtension.getExtension(current.getRootNode());
				if (ext != null && FinanceAttributes.KIND_ROOT.equals(ext.getKind())) {
					return current;
				}
				if (hasAnyFinanceNode(current.getRootNode())) {
					return current;
				}
			}
		}
		catch (Exception e) {
		}
		return ensureFinanceMap();
	}

	private static boolean hasAnyFinanceNode(final NodeModel node) {
		if (node == null) {
			return false;
		}
		if (FinanceExtension.getExtension(node) != null) {
			return true;
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			if (hasAnyFinanceNode((NodeModel) node.getChildAt(i))) {
				return true;
			}
		}
		return false;
	}

	private static void collectByKind(final NodeModel node, final String kind, final List out) {
		if (node == null) {
			return;
		}
		final FinanceExtension ext = FinanceExtension.getExtension(node);
		if (ext != null && kind.equals(ext.getKind())) {
			out.add(node);
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			collectByKind((NodeModel) node.getChildAt(i), kind, out);
		}
	}

	private static boolean inDateRange(final String date, final String fromYmd, final String toYmd) {
		if (date == null || date.length() == 0) {
			return false;
		}
		if (fromYmd != null && fromYmd.trim().length() > 0 && date.compareTo(fromYmd.trim()) < 0) {
			return false;
		}
		if (toYmd != null && toYmd.trim().length() > 0 && date.compareTo(toYmd.trim()) > 0) {
			return false;
		}
		return true;
	}

	private static long resolveAmountCents(final String amountYuanOrCents) {
		if (amountYuanOrCents == null || amountYuanOrCents.trim().length() == 0) {
			return 0L;
		}
		final String s = amountYuanOrCents.trim();
		if (s.indexOf('.') >= 0 || s.indexOf('¥') >= 0 || s.indexOf('￥') >= 0) {
			return FinanceAttributes.parseYuanToCents(s);
		}
		// Pure digits: treat as yuan text (user-facing), convert to cents.
		return FinanceAttributes.parseYuanToCents(s);
	}

	private static String buildTxnLabel(final String merchant, final String note, final String categoryName,
			final long cents) {
		String title = merchant;
		if (title == null || title.trim().length() == 0) {
			title = note;
		}
		if (title == null || title.trim().length() == 0) {
			title = categoryName;
		}
		if (title == null || title.trim().length() == 0) {
			title = "交易";
		}
		return title.trim() + " · ¥" + FinanceAttributes.formatYuan(Math.abs(cents));
	}

	private static NodeModel addAccountNode(final NodeModel parent, final String name) {
		final NodeModel node = addChildNode(parent, name);
		if (node == null) {
			return null;
		}
		final FinanceExtension ext = new FinanceExtension();
		ext.setKind(FinanceAttributes.KIND_ACCOUNT);
		ext.setCurrency(FinanceAttributes.DEFAULT_CURRENCY);
		FinanceAttributes.writeSilent(node, ext);
		return node;
	}

	private static NodeModel addCategoryNode(final NodeModel parent, final String name, final String flow) {
		final NodeModel node = addChildNode(parent, name);
		if (node == null) {
			return null;
		}
		final FinanceExtension ext = new FinanceExtension();
		ext.setKind(FinanceAttributes.KIND_CATEGORY);
		ext.setFlow(flow);
		FinanceAttributes.writeSilent(node, ext);
		return node;
	}

	private static NodeModel findSubscriptionByName(final NodeModel section, final String name) {
		for (int i = 0; i < section.getChildCount(); i++) {
			final NodeModel child = (NodeModel) section.getChildAt(i);
			final FinanceExtension ext = FinanceExtension.getExtension(child);
			if (ext != null && FinanceAttributes.KIND_SUBSCRIPTION.equals(ext.getKind())
					&& name.equals(ext.getMerchant())) {
				return child;
			}
			final String text = plainText(child);
			if (text.equals(name) || text.startsWith(name + " ·")) {
				return child;
			}
		}
		return null;
	}

	private static NodeModel findCouponByName(final NodeModel section, final String name) {
		for (int i = 0; i < section.getChildCount(); i++) {
			final NodeModel child = (NodeModel) section.getChildAt(i);
			final String text = plainText(child);
			if (text.equals(name) || text.startsWith(name + " ·")) {
				return child;
			}
		}
		return null;
	}

	private static NodeModel findOrCreateChild(final NodeModel parent, final String text) {
		final NodeModel existing = findChildByText(parent, text);
		if (existing != null) {
			return existing;
		}
		return addChildNode(parent, text);
	}

	private static NodeModel findChildByText(final NodeModel parent, final String text) {
		if (parent == null || text == null) {
			return null;
		}
		for (int i = 0; i < parent.getChildCount(); i++) {
			final NodeModel child = (NodeModel) parent.getChildAt(i);
			if (text.equals(plainText(child))) {
				return child;
			}
		}
		return null;
	}

	private static NodeModel addChildNode(final NodeModel parent, final String text) {
		if (parent == null) {
			return null;
		}
		try {
			final ModeController modeController = Controller.getCurrentModeController();
			final MMapController mapController = (MMapController) modeController.getMapController();
			final NodeModel node = mapController.addNewNode(parent, parent.getChildCount(), parent.isNewChildLeft());
			if (node == null) {
				return null;
			}
			setNodeText(node, text);
			return node;
		}
		catch (Exception e) {
			LogUtils.warn("Finance: addChildNode failed", e);
			return null;
		}
	}

	private static void setNodeText(final NodeModel node, final String text) {
		if (node == null) {
			return;
		}
		try {
			final MTextController textController = MTextController.getController();
			if (textController != null) {
				textController.setNodeText(node, text == null ? "" : text);
				return;
			}
		}
		catch (Exception e) {
		}
		node.setText(text == null ? "" : text);
	}

	private static String plainText(final NodeModel node) {
		if (node == null) {
			return "";
		}
		try {
			final String text = TextController.getController().getPlainTextContent(node);
			if (text != null) {
				return text.replaceAll("\\s+", " ").trim();
			}
		}
		catch (Exception e) {
		}
		return node.getText() == null ? "" : node.getText().replaceAll("\\s+", " ").trim();
	}

	public static final class FinanceTxn {
		public NodeModel node;
		public long amountCents;
		public String flow = "";
		public String dateYmd = "";
		public String categoryName = "";
		public String accountName = "";
		public String merchant = "";
		public String note = "";
		public String currency = "";
		public String nodeText = "";
	}

	public static final class FinanceBudget {
		public NodeModel node;
		public String period = "";
		public String categoryName = "";
		public long amountCents;
	}

	public static final class FinanceSubscription {
		public NodeModel node;
		public String name = "";
		public long amountCents;
		public String cycle = "";
		public String nextYmd = "";
		public String status = "";
		public String accountName = "";
		public String note = "";
	}

	public static final class FinanceCoupon {
		public NodeModel node;
		public String name = "";
		public long amountCents;
		public String expiresYmd = "";
		public String status = "";
		public String merchant = "";
		public String note = "";
	}

	public static final class MonthSummary {
		public String period = "";
		public long incomeCents;
		public long expenseCents;
		public Map byCategory = new HashMap();
	}
}
