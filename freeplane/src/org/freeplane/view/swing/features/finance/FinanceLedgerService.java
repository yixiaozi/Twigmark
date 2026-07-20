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
import org.freeplane.features.ui.IMapViewManager;
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
			dir = MindMapDataRootResolver.getWorkingDirectory();
		}
		catch (Exception e) {
		}
		if (dir == null || !dir.isDirectory()) {
			try {
				dir = new File(Compat.getApplicationUserDirectory());
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
				// Never fall back to an arbitrary current map — that would write ledger data elsewhere.
				LogUtils.warn("Finance: opened URL but could not resolve finance map: " + file.getAbsolutePath());
				return null;
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

	/** Focus an existing finance map tab instead of opening duplicates. */
	public static boolean activateFinanceMapView(final MapModel map) {
		if (map == null) {
			return false;
		}
		final Controller controller = Controller.getCurrentController();
		if (controller == null) {
			return false;
		}
		final IMapViewManager views = controller.getMapViewManager();
		try {
			if (map.getURL() != null && views.tryToChangeToMapView(map.getURL())) {
				return true;
			}
		}
		catch (Exception e) {
		}
		try {
			final java.util.List existing = views.getViews(map);
			if (existing != null && !existing.isEmpty()) {
				views.changeToMapView((java.awt.Component) existing.get(0));
				return true;
			}
		}
		catch (Exception e) {
		}
		if (controller.getMap() == map) {
			try {
				views.changeToMapView(views.getMapViewComponent());
				return true;
			}
			catch (Exception e) {
			}
		}
		views.newMapView(map, Controller.getCurrentModeController());
		return true;
	}

	public static boolean focusFinanceNode(final String nodeId) {
		if (nodeId == null || nodeId.trim().length() == 0) {
			return false;
		}
		final MapModel map = ensureFinanceMap();
		if (map == null) {
			return false;
		}
		activateFinanceMapView(map);
		final NodeModel node = map.getNodeForID(nodeId.trim());
		if (node == null) {
			return false;
		}
		try {
			final Controller controller = Controller.getCurrentController();
			controller.getSelection().selectAsTheOnlyOneSelected(node);
			controller.getModeController().getMapController().centerNode(node);
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("Finance: focusFinanceNode failed", e);
			return false;
		}
	}

	public static NodeModel addTransaction(final String amountYuanOrCents, final String flow, final String dateYmd,
			final String categoryName, final String accountName, final String merchant, final String note) {
		return addTransaction(amountYuanOrCents, flow, dateYmd, categoryName, accountName, null, merchant, note);
	}

	public static NodeModel addTransaction(final String amountYuanOrCents, final String flow, final String dateYmd,
			final String categoryName, final String accountName, final String accountTo, final String merchant,
			final String note) {
		final MapModel map = ensureFinanceMap();
		if (map == null) {
			return null;
		}
		final NodeModel txnSection = findSection(map, SECTION_TXNS);
		if (txnSection == null) {
			return null;
		}
		final String date = FinanceAttributes.normalizeDateTime(dateYmd);
		final String dayKey = FinanceAttributes.datePart(date);
		final NodeModel dayFolder = findOrCreateChild(txnSection, dayKey);
		if (dayFolder == null) {
			return null;
		}
		final long cents = resolveAmountCents(amountYuanOrCents);
		if (!FinanceRules.isValidAmountCents(cents)) {
			LogUtils.warn("Finance: reject non-positive amount: " + amountYuanOrCents);
			return null;
		}
		final String flowValue = flow == null || flow.trim().length() == 0 ? FinanceAttributes.FLOW_EXPENSE
				: flow.trim();
		if (FinanceRules.isTransfer(flowValue)
				&& (accountName == null || accountName.trim().length() == 0
						|| accountTo == null || accountTo.trim().length() == 0)) {
			LogUtils.warn("Finance: transfer requires account and accountTo");
			return null;
		}
		final String catId = FinanceNodeRef.normalizeCategoryRef(map, categoryName);
		final String accId = FinanceNodeRef.normalizeAccountRef(map, accountName);
		final String accToId = FinanceNodeRef.normalizeAccountRef(map, accountTo);
		final String catLabel = FinanceNodeRef.resolveCategoryPath(map, catId);
		final String accLabel = FinanceNodeRef.resolveAccountPath(map, accId);
		final String accToLabel = FinanceNodeRef.resolveAccountPath(map, accToId);
		final String label = buildTxnLabel(merchant, note, catLabel, cents, flowValue, accLabel, accToLabel);
		final NodeModel node = addChildNode(dayFolder, label);
		if (node == null) {
			return null;
		}
		final FinanceExtension ext = new FinanceExtension();
		ext.setKind(FinanceAttributes.KIND_TXN);
		ext.setAmountCents(cents);
		ext.setCurrency(FinanceAttributes.DEFAULT_CURRENCY);
		ext.setDate(date);
		ext.setFlow(flowValue);
		ext.setCatId(catId);
		ext.setAccountId(accId);
		ext.setAccountTo(accToId);
		ext.setMerchant(merchant == null ? "" : merchant.trim());
		ext.setNote(note == null ? "" : note.trim());
		FinanceAttributes.writeSilent(node, ext);
		persistFinanceMap(map);
		FinanceChangeNotifier.fireChanged();
		return node;
	}

	public static FinanceTxn getTransactionByNodeId(final String nodeId) {
		if (nodeId == null || nodeId.trim().length() == 0) {
			return null;
		}
		final MapModel map = preferOpenFinanceMap();
		if (map == null) {
			return null;
		}
		final NodeModel node = map.getNodeForID(nodeId.trim());
		if (node == null) {
			return null;
		}
		final FinanceExtension ext = FinanceExtension.getExtension(node);
		if (ext == null || !FinanceAttributes.KIND_TXN.equals(ext.getKind())) {
			return null;
		}
		return toFinanceTxn(node, ext);
	}

	public static NodeModel updateTransaction(final String nodeId, final String amountYuanOrCents, final String flow,
			final String dateYmd, final String categoryName, final String accountName, final String accountTo,
			final String merchant, final String note) {
		final MapModel map = preferOpenFinanceMap();
		if (map == null || nodeId == null) {
			return null;
		}
		final NodeModel node = map.getNodeForID(nodeId.trim());
		if (node == null) {
			return null;
		}
		final FinanceExtension existing = FinanceExtension.getExtension(node);
		if (existing == null || !FinanceAttributes.KIND_TXN.equals(existing.getKind())) {
			return null;
		}
		final long cents = resolveAmountCents(amountYuanOrCents);
		if (!FinanceRules.isValidAmountCents(cents)) {
			return null;
		}
		final String flowValue = flow == null || flow.trim().length() == 0 ? FinanceAttributes.FLOW_EXPENSE
				: flow.trim();
		final String date = FinanceAttributes.normalizeDateTime(dateYmd);
		final String cat = FinanceNodeRef.normalizeCategoryRef(map, categoryName);
		final String acc = FinanceNodeRef.normalizeAccountRef(map, accountName);
		final String accTo = FinanceNodeRef.normalizeAccountRef(map, accountTo);
		final String merch = merchant == null ? "" : merchant.trim();
		final String noteValue = note == null ? "" : note.trim();
		if (FinanceRules.isTransfer(flowValue) && (acc.length() == 0 || accTo.length() == 0)) {
			return null;
		}
		final NodeModel txnSection = findSection(map, SECTION_TXNS);
		if (txnSection != null) {
			final NodeModel dayFolder = findOrCreateChild(txnSection, FinanceAttributes.datePart(date));
			if (dayFolder != null && node.getParent() != dayFolder) {
				try {
					final MMapController mapController = (MMapController) Controller.getCurrentModeController()
							.getMapController();
					mapController.moveNode(node, dayFolder, dayFolder.getChildCount());
				}
				catch (Exception e) {
					LogUtils.warn("Finance: move txn to day folder failed", e);
				}
			}
		}
		final String label = buildTxnLabel(merch, noteValue, FinanceNodeRef.resolveCategoryPath(map, cat), cents,
				flowValue, FinanceNodeRef.resolveAccountPath(map, acc),
				FinanceNodeRef.resolveAccountPath(map, accTo));
		setNodeText(node, label);
		final FinanceExtension ext = existing.copy();
		ext.setAmountCents(cents);
		ext.setDate(date);
		ext.setFlow(flowValue);
		ext.setCatId(cat);
		ext.setAccountId(acc);
		ext.setAccountTo(accTo);
		ext.setMerchant(merch);
		ext.setNote(noteValue);
		FinanceAttributes.writeSilent(node, ext);
		persistFinanceMap(map);
		FinanceChangeNotifier.fireChanged();
		return node;
	}

	private static FinanceTxn toFinanceTxn(final NodeModel node, final FinanceExtension ext) {
		final MapModel map = node == null ? null : node.getMap();
		final FinanceTxn txn = new FinanceTxn();
		txn.node = node;
		txn.amountCents = ext.getAmountCents();
		txn.flow = ext.getFlow();
		txn.dateYmd = ext.getDate();
		txn.categoryNodeId = ext.getCatId();
		txn.accountNodeId = ext.getAccountId();
		txn.accountToNodeId = ext.getAccountTo();
		txn.categoryName = FinanceNodeRef.resolveCategoryPath(map, ext.getCatId());
		txn.accountName = FinanceNodeRef.resolveAccountPath(map, ext.getAccountId());
		txn.accountTo = FinanceNodeRef.resolveAccountPath(map, ext.getAccountTo());
		txn.merchant = ext.getMerchant();
		txn.note = ext.getNote();
		txn.currency = ext.getCurrency();
		txn.nodeText = nodePlainText(node);
		return txn;
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
			out.add(toFinanceTxn(node, ext));
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

	public static List listCategoryRefs(final String flow) {
		return FinanceNodeRef.listCategoryRefs(flow);
	}

	public static List listAccountRefs() {
		return FinanceNodeRef.listAccountRefs();
	}

	public static List listCategories(final String flow) {
		final List out = new ArrayList();
		final List refs = FinanceNodeRef.listCategoryRefs(flow);
		for (int i = 0; i < refs.size(); i++) {
			out.add(((FinanceNodeRef.Ref) refs.get(i)).path);
		}
		return out;
	}

	public static List listAccounts() {
		final List out = new ArrayList();
		final List refs = FinanceNodeRef.listAccountRefs();
		for (int i = 0; i < refs.size(); i++) {
			out.add(((FinanceNodeRef.Ref) refs.get(i)).path);
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
		final NodeModel node = addCategoryNode(parent, name.trim(), flowValue);
		if (node != null) {
			persistFinanceMap(map);
		}
		return node;
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
		final NodeModel node = addAccountNode(accounts, name.trim());
		if (node != null) {
			persistFinanceMap(map);
		}
		return node;
	}

	public static NodeModel setBudget(final String periodYyyyMm, final String categoryName, final long amountCents) {
		return setBudget(periodYyyyMm, periodYyyyMm, categoryName, amountCents);
	}

	public static NodeModel setBudget(final String periodStart, final String periodEnd, final String categoryRef,
			final long amountCents) {
		final MapModel map = ensureFinanceMap();
		if (map == null || periodStart == null) {
			return null;
		}
		final NodeModel budgets = findSection(map, SECTION_BUDGETS);
		if (budgets == null) {
			return null;
		}
		final String start = FinanceAttributes.datePart(FinanceAttributes.normalizeDateTime(periodStart.trim()));
		final String endRaw = periodEnd == null || periodEnd.trim().length() == 0 ? start : periodEnd.trim();
		final String end = FinanceAttributes.datePart(FinanceAttributes.normalizeDateTime(endRaw));
		final String catId = FinanceRules.isTotalBudgetCategory(categoryRef) ? FinanceNodeRef.TOTAL_BUDGET_NODE_ID
				: FinanceNodeRef.normalizeCategoryRef(map, categoryRef);
		final String catLabel = FinanceNodeRef.isTotalBudgetRef(catId)
				? FinanceRules.TOTAL_BUDGET_CATEGORY
				: FinanceNodeRef.resolveCategoryPath(map, catId);
		final NodeModel periodFolder = findOrCreateChild(budgets, start);
		NodeModel node = findBudgetNode(periodFolder, start, end, catId);
		final String rangeLabel = start.equals(end) ? start : start + "~" + end;
		final String label = catLabel + " · " + rangeLabel + " · ¥" + FinanceAttributes.formatYuan(amountCents);
		if (node == null) {
			node = addChildNode(periodFolder, label);
		}
		else {
			setNodeText(node, label);
		}
		if (node == null) {
			return null;
		}
		final FinanceExtension ext = new FinanceExtension();
		ext.setKind(FinanceAttributes.KIND_BUDGET);
		ext.setPeriod(start);
		ext.setExpires(end);
		ext.setCatId(catId);
		ext.setAmountCents(amountCents);
		ext.setCurrency(FinanceAttributes.DEFAULT_CURRENCY);
		ext.setFlow(FinanceAttributes.FLOW_EXPENSE);
		FinanceAttributes.writeSilent(node, ext);
		persistFinanceMap(map);
		FinanceChangeNotifier.fireChanged();
		return node;
	}

	private static NodeModel findBudgetNode(final NodeModel periodFolder, final String start, final String end,
			final String categoryNodeId) {
		if (periodFolder == null) {
			return null;
		}
		for (int i = 0; i < periodFolder.getChildCount(); i++) {
			final NodeModel child = (NodeModel) periodFolder.getChildAt(i);
			final FinanceExtension ext = FinanceExtension.getExtension(child);
			if (ext != null && FinanceAttributes.KIND_BUDGET.equals(ext.getKind())) {
				final boolean startMatch = start.equals(ext.getPeriod()) || ext.getPeriod().length() == 0;
				final boolean endMatch = end.equals(ext.getExpires()) || ext.getExpires().length() == 0;
				final boolean catMatch = categoryNodeId.equals(ext.getCatId())
						|| FinanceNodeRef.isTotalBudgetRef(categoryNodeId)
								&& FinanceNodeRef.isTotalBudgetRef(ext.getCatId());
				if (startMatch && endMatch && catMatch) {
					return child;
				}
			}
		}
		return null;
	}

	public static List listBudgets(final String period) {
		if (period == null || period.trim().length() == 0) {
			return listBudgetsForRange("", "");
		}
		final String p = period.trim();
		if (p.length() == 7) {
			return listBudgetsForRange(p + "-01", p + "-31");
		}
		return listBudgetsForRange(p, p);
	}

	public static List listBudgetsForRange(final String fromYmd, final String toYmd) {
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
			if (fromYmd != null && fromYmd.trim().length() > 0 && toYmd != null && toYmd.trim().length() > 0
					&& !FinanceNodeRef.budgetOverlapsRange(ext, fromYmd.trim(), toYmd.trim())) {
				continue;
			}
			final FinanceBudget budget = new FinanceBudget();
			budget.node = node;
			budget.period = ext.getPeriod();
			budget.periodEnd = ext.getExpires();
			budget.categoryNodeId = ext.getCatId();
			budget.categoryName = FinanceNodeRef.isTotalBudgetRef(ext.getCatId())
					? FinanceRules.TOTAL_BUDGET_CATEGORY
					: FinanceNodeRef.resolveCategoryPath(map, ext.getCatId());
			budget.amountCents = ext.getAmountCents();
			out.add(budget);
		}
		return out;
	}

	public static NodeModel upsertSubscription(final String name, final long amountCents, final String cycle,
			final String nextYmd, final String status, final String accountName, final String note) {
		return upsertSubscription(name, amountCents, cycle, nextYmd, status, accountName, note, "", "");
	}

	public static NodeModel upsertSubscription(final String name, final long amountCents, final String cycle,
			final String nextYmd, final String status, final String accountName, final String note,
			final String startYmd, final String endYmd) {
		final MapModel map = ensureFinanceMap();
		if (map == null || name == null || name.trim().length() == 0) {
			return null;
		}
		final NodeModel section = findSection(map, SECTION_SUBSCRIPTIONS);
		if (section == null) {
			return null;
		}
		NodeModel node = findSubscriptionByName(section, name.trim());
		final String cycleValue = FinanceRules.normalizeCycle(cycle);
		final String start = startYmd == null ? "" : FinanceAttributes.datePart(startYmd.trim());
		final String end = endYmd == null ? "" : FinanceAttributes.datePart(endYmd.trim());
		final String label = buildSubscriptionLabel(name.trim(), amountCents, cycleValue, start, end);
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
		ext.setCycle(cycleValue);
		ext.setNext(nextYmd == null ? "" : FinanceAttributes.datePart(nextYmd.trim()));
		ext.setStatus(status == null ? "active" : status.trim());
		ext.setAccountId(FinanceNodeRef.normalizeAccountRef(map, accountName));
		ext.setNote(note == null ? "" : note.trim());
		ext.setMerchant(name.trim());
		ext.setDate(start);
		ext.setExpires(end);
		FinanceAttributes.writeSilent(node, ext);
		persistFinanceMap(map);
		FinanceChangeNotifier.fireChanged();
		return node;
	}

	public static String buildSubscriptionLabel(final String name, final long amountCents, final String cycle,
			final String startYmd, final String endYmd) {
		final StringBuilder sb = new StringBuilder();
		sb.append(name == null ? "" : name.trim());
		sb.append(" · ").append(FinanceRules.cycleLabelZh(cycle));
		if (startYmd != null && startYmd.trim().length() > 0) {
			sb.append(" · ").append(FinanceAttributes.datePart(startYmd));
			if (endYmd != null && endYmd.trim().length() > 0) {
				sb.append("~").append(FinanceAttributes.datePart(endYmd));
			}
		}
		sb.append(" · ¥").append(FinanceAttributes.formatYuan(amountCents));
		return sb.toString();
	}

	public static NodeModel updateSubscription(final String nodeId, final String name, final long amountCents,
			final String cycle, final String nextYmd, final String status, final String accountName,
			final String note, final String startYmd, final String endYmd) {
		final MapModel map = preferOpenFinanceMap();
		if (map == null || nodeId == null || name == null || name.trim().length() == 0) {
			return null;
		}
		final NodeModel node = map.getNodeForID(nodeId.trim());
		if (node == null) {
			return null;
		}
		final FinanceExtension existing = FinanceExtension.getExtension(node);
		if (existing == null || !FinanceAttributes.KIND_SUBSCRIPTION.equals(existing.getKind())) {
			return null;
		}
		final String cycleValue = FinanceRules.normalizeCycle(cycle);
		final String start = startYmd == null ? "" : FinanceAttributes.datePart(startYmd.trim());
		final String end = endYmd == null ? "" : FinanceAttributes.datePart(endYmd.trim());
		final FinanceExtension ext = existing.copy();
		ext.setAmountCents(amountCents);
		ext.setCycle(cycleValue);
		ext.setNext(nextYmd == null ? "" : FinanceAttributes.datePart(nextYmd.trim()));
		ext.setStatus(status == null ? "active" : status.trim());
		ext.setAccountId(FinanceNodeRef.normalizeAccountRef(map, accountName));
		ext.setNote(note == null ? "" : note.trim());
		ext.setMerchant(name.trim());
		ext.setDate(start);
		ext.setExpires(end);
		setNodeText(node, buildSubscriptionLabel(name.trim(), amountCents, cycleValue, start, end));
		FinanceAttributes.writeSilent(node, ext);
		persistFinanceMap(map);
		FinanceChangeNotifier.fireChanged();
		return node;
	}

	public static FinanceSubscription getSubscriptionByNodeId(final String nodeId) {
		final MapModel map = preferOpenFinanceMap();
		if (map == null || nodeId == null || nodeId.trim().length() == 0) {
			return null;
		}
		final NodeModel node = map.getNodeForID(nodeId.trim());
		if (node == null) {
			return null;
		}
		final FinanceExtension ext = FinanceExtension.getExtension(node);
		if (ext == null || !FinanceAttributes.KIND_SUBSCRIPTION.equals(ext.getKind())) {
			return null;
		}
		return toSubscription(node, ext);
	}

	public static FinanceBudget getBudgetByNodeId(final String nodeId) {
		final MapModel map = preferOpenFinanceMap();
		if (map == null || nodeId == null || nodeId.trim().length() == 0) {
			return null;
		}
		final NodeModel node = map.getNodeForID(nodeId.trim());
		if (node == null) {
			return null;
		}
		final FinanceExtension ext = FinanceExtension.getExtension(node);
		if (ext == null || !FinanceAttributes.KIND_BUDGET.equals(ext.getKind())) {
			return null;
		}
		final FinanceBudget budget = new FinanceBudget();
		budget.node = node;
		budget.period = ext.getPeriod();
		budget.periodEnd = ext.getExpires();
		budget.categoryNodeId = ext.getCatId();
		budget.categoryName = FinanceNodeRef.isTotalBudgetRef(ext.getCatId())
				? FinanceRules.TOTAL_BUDGET_CATEGORY
				: FinanceNodeRef.resolveCategoryPath(map, ext.getCatId());
		budget.amountCents = ext.getAmountCents();
		return budget;
	}

	/**
	 * Record one subscription payment: writes a real expense txn + a payment child under the
	 * subscription node, then advances {@code next} by one cycle.
	 */
	public static NodeModel recordSubscriptionPayment(final String subscriptionNodeId, final String payDateYmd) {
		final MapModel map = preferOpenFinanceMap();
		if (map == null || subscriptionNodeId == null) {
			return null;
		}
		final NodeModel subNode = map.getNodeForID(subscriptionNodeId.trim());
		if (subNode == null) {
			return null;
		}
		final FinanceExtension subExt = FinanceExtension.getExtension(subNode);
		if (subExt == null || !FinanceAttributes.KIND_SUBSCRIPTION.equals(subExt.getKind())) {
			return null;
		}
		final String payDate = payDateYmd == null || payDateYmd.trim().length() == 0
				? FinanceAttributes.todayYmd()
				: FinanceAttributes.datePart(payDateYmd.trim());
		final String end = FinanceAttributes.datePart(subExt.getExpires());
		if (end.length() > 0 && payDate.compareTo(end) > 0) {
			LogUtils.warn("Finance: payment date after subscription end: " + payDate + " > " + end);
			return null;
		}
		final String name = FinanceRules.bareNameFromLabel(plainText(subNode), subExt.getMerchant());
		final NodeModel txn = addTransaction(
				FinanceAttributes.formatYuan(subExt.getAmountCents()),
				FinanceAttributes.FLOW_EXPENSE,
				payDate + " 12:00",
				"",
				subExt.getAccountId(),
				"",
				name,
				"订阅付款 · " + FinanceRules.cycleLabelZh(subExt.getCycle()));
		final String payLabel = "付款 · " + payDate + " · ¥" + FinanceAttributes.formatYuan(subExt.getAmountCents());
		final NodeModel payNode = addChildNode(subNode, payLabel);
		if (payNode != null) {
			final FinanceExtension payExt = new FinanceExtension();
			payExt.setKind(FinanceAttributes.KIND_PAYMENT);
			payExt.setAmountCents(subExt.getAmountCents());
			payExt.setDate(payDate);
			payExt.setFlow(FinanceAttributes.FLOW_EXPENSE);
			payExt.setMerchant(name);
			payExt.setNote(txn == null ? "" : txn.createID());
			payExt.setAccountId(subExt.getAccountId());
			FinanceAttributes.writeSilent(payNode, payExt);
		}
		final String nextBase = payDate.length() > 0 ? payDate : FinanceAttributes.todayYmd();
		final String next = FinanceRules.nextDateForCycle(nextBase, subExt.getCycle());
		final FinanceExtension updated = subExt.copy();
		updated.setNext(next);
		if (end.length() > 0 && next.compareTo(end) > 0) {
			updated.setStatus("completed");
		}
		setNodeText(subNode, buildSubscriptionLabel(name, updated.getAmountCents(), updated.getCycle(),
				updated.getDate(), updated.getExpires()));
		FinanceAttributes.writeSilent(subNode, updated);
		persistFinanceMap(map);
		FinanceChangeNotifier.fireChanged();
		return txn != null ? txn : payNode;
	}

	public static int countSubscriptionPayments(final NodeModel subscriptionNode) {
		if (subscriptionNode == null) {
			return 0;
		}
		int count = 0;
		for (int i = 0; i < subscriptionNode.getChildCount(); i++) {
			final NodeModel child = (NodeModel) subscriptionNode.getChildAt(i);
			final FinanceExtension ext = FinanceExtension.getExtension(child);
			if (ext != null && FinanceAttributes.KIND_PAYMENT.equals(ext.getKind())) {
				count++;
			}
			else if (plainText(child).startsWith("付款")) {
				count++;
			}
		}
		return count;
	}

	private static FinanceSubscription toSubscription(final NodeModel node, final FinanceExtension ext) {
		final FinanceSubscription sub = new FinanceSubscription();
		sub.node = node;
		sub.name = FinanceRules.bareNameFromLabel(plainText(node), ext.getMerchant());
		sub.amountCents = ext.getAmountCents();
		sub.cycle = ext.getCycle();
		sub.nextYmd = ext.getNext();
		sub.status = ext.getStatus();
		sub.accountName = FinanceNodeRef.resolveAccountPath(node.getMap(), ext.getAccountId());
		sub.accountNodeId = ext.getAccountId();
		sub.note = ext.getNote();
		sub.startYmd = ext.getDate();
		sub.endYmd = ext.getExpires();
		sub.paymentCount = countSubscriptionPayments(node);
		return sub;
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
			out.add(toSubscription(node, ext));
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
		final String bare = FinanceRules.bareNameFromLabel(name.trim(), null);
		NodeModel node = findCouponByName(section, bare);
		final String label = bare + " · ¥" + FinanceAttributes.formatYuan(amountCents);
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
		// merchant field stores the bare coupon name for stable upsert matching
		ext.setMerchant(bare);
		ext.setNote(note == null ? "" : note.trim());
		if (merchant != null && merchant.trim().length() > 0 && !merchant.trim().equals(bare)) {
			ext.setNote((ext.getNote().length() == 0 ? "" : ext.getNote() + " · ") + "商家:" + merchant.trim());
		}
		FinanceAttributes.writeSilent(node, ext);
		persistFinanceMap(map);
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
			coupon.name = FinanceRules.bareNameFromLabel(plainText(node), ext.getMerchant());
			coupon.amountCents = ext.getAmountCents();
			coupon.expiresYmd = ext.getExpires();
			coupon.status = ext.getStatus();
			coupon.merchant = ext.getMerchant();
			coupon.note = ext.getNote();
			out.add(coupon);
		}
		return out;
	}

	public static NodeModel markCouponUsed(final String nodeId, final boolean used) {
		final MapModel map = preferOpenFinanceMap();
		if (map == null || nodeId == null || nodeId.trim().length() == 0) {
			return null;
		}
		final NodeModel node = map.getNodeForID(nodeId.trim());
		if (node == null) {
			return null;
		}
		final FinanceExtension existing = FinanceExtension.getExtension(node);
		if (existing == null || !FinanceAttributes.KIND_COUPON.equals(existing.getKind())) {
			return null;
		}
		final FinanceExtension ext = existing.copy();
		ext.setStatus(used ? "used" : "active");
		FinanceAttributes.writeSilent(node, ext);
		persistFinanceMap(map);
		return node;
	}

	public static boolean deleteFinanceNode(final String nodeId) {
		final MapModel map = preferOpenFinanceMap();
		if (map == null || nodeId == null || nodeId.trim().length() == 0) {
			return false;
		}
		final NodeModel node = map.getNodeForID(nodeId.trim());
		if (node == null || node.isRoot()) {
			return false;
		}
		final FinanceExtension ext = FinanceExtension.getExtension(node);
		if (ext == null || ext.isEmpty()) {
			return false;
		}
		try {
			final ModeController modeController = Controller.getCurrentModeController();
			final MMapController mapController = (MMapController) modeController.getMapController();
			mapController.deleteNode(node);
			persistFinanceMap(map);
			FinanceChangeNotifier.fireChanged();
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("Finance: deleteFinanceNode failed", e);
			return false;
		}
	}

	public static MonthSummary daySummary(final String yyyyMmDd) {
		final MonthSummary summary = new MonthSummary();
		summary.period = yyyyMmDd == null ? "" : FinanceAttributes.datePart(yyyyMmDd);
		summary.byCategory = new TreeMap();
		if (summary.period.length() < 10) {
			return summary;
		}
		final List txns = listTransactions(summary.period, summary.period);
		accumulateSummary(summary, txns, summary.period, true);
		return summary;
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
		accumulateSummary(summary, txns, summary.period, false);
		return summary;
	}

	private static void accumulateSummary(final MonthSummary summary, final List txns, final String period,
			final boolean exactDay) {
		for (int i = 0; i < txns.size(); i++) {
			final FinanceTxn txn = (FinanceTxn) txns.get(i);
			if (txn.dateYmd == null) {
				continue;
			}
			if (exactDay) {
				if (!period.equals(FinanceAttributes.datePart(txn.dateYmd))) {
					continue;
				}
			}
			else if (!FinanceAttributes.datePart(txn.dateYmd).startsWith(period)) {
				continue;
			}
			final long cents = Math.abs(txn.amountCents);
			if (FinanceRules.isPnlIncome(txn.flow)) {
				summary.incomeCents += cents;
			}
			else if (FinanceRules.isPnlExpense(txn.flow)) {
				summary.expenseCents += cents;
				final String cat = txn.categoryName == null || txn.categoryName.length() == 0 ? "未分类"
						: txn.categoryName;
				final Long prev = (Long) summary.byCategory.get(cat);
				summary.byCategory.put(cat, Long.valueOf((prev == null ? 0L : prev.longValue()) + cents));
			}
			else if (FinanceRules.isBorrow(txn.flow)) {
				summary.borrowCents += cents;
			}
			else if (FinanceRules.isLend(txn.flow)) {
				summary.lendCents += cents;
			}
			else if (FinanceRules.isCredit(txn.flow)) {
				summary.creditCents += cents;
			}
			else if (FinanceRules.isTransfer(txn.flow)) {
				summary.transferCents += cents;
			}
		}
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
			}
		}
		catch (Exception e) {
		}
		return ensureFinanceMap();
	}

	public static MapModel preferOpenFinanceMapPublic() {
		return preferOpenFinanceMap();
	}

	public static boolean persistFinanceMap(final MapModel map) {
		if (map == null) {
			return false;
		}
		try {
			final File file = map.getFile() != null ? map.getFile() : resolveFinanceMapFile();
			if (file == null) {
				return false;
			}
			final ModeController modeController = Controller.getCurrentModeController();
			final MFileManager fileManager = MFileManager.getController(modeController);
			return fileManager.save(map, file);
		}
		catch (Exception e) {
			LogUtils.warn("Finance: persistFinanceMap failed", e);
			return false;
		}
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
		final String key = FinanceAttributes.datePart(date);
		final String from = fromYmd == null || fromYmd.trim().length() == 0 ? "" : FinanceAttributes.datePart(fromYmd.trim());
		final String to = toYmd == null || toYmd.trim().length() == 0 ? "" : FinanceAttributes.datePart(toYmd.trim());
		if (from.length() > 0 && key.compareTo(from) < 0) {
			return false;
		}
		if (to.length() > 0 && key.compareTo(to) > 0) {
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
			final long cents, final String flow, final String accountFrom, final String accountTo) {
		String title = merchant;
		if (title == null || title.trim().length() == 0) {
			title = note;
		}
		if (title == null || title.trim().length() == 0) {
			title = categoryName;
		}
		if ((title == null || title.trim().length() == 0) && FinanceRules.isTransfer(flow)
				&& accountFrom != null && accountTo != null) {
			title = accountFrom.trim() + "→" + accountTo.trim();
		}
		if (title == null || title.trim().length() == 0) {
			title = FinanceRules.flowLabelZh(flow);
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
			final FinanceExtension ext = FinanceExtension.getExtension(child);
			if (ext != null && FinanceAttributes.KIND_COUPON.equals(ext.getKind())
					&& name.equals(ext.getMerchant())) {
				return child;
			}
			final String text = plainText(child);
			final String bare = FinanceRules.bareNameFromLabel(text, null);
			if (text.equals(name) || text.startsWith(name + " ·") || name.equals(bare)) {
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
		return nodePlainText(node);
	}

	public static String nodePlainText(final NodeModel node) {
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
		public String categoryNodeId = "";
		public String accountNodeId = "";
		public String accountToNodeId = "";
		public String categoryName = "";
		public String accountName = "";
		public String accountTo = "";
		public String merchant = "";
		public String note = "";
		public String currency = "";
		public String nodeText = "";
	}

	public static final class FinanceBudget {
		public NodeModel node;
		public String period = "";
		public String periodEnd = "";
		public String categoryNodeId = "";
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
		public String accountNodeId = "";
		public String note = "";
		public String startYmd = "";
		public String endYmd = "";
		public int paymentCount;
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

	/**
	 * Monthly numbers: {@link #incomeCents}/{@link #expenseCents} are P&amp;L only.
	 * Liability/credit/transfer are tracked separately so reports stay consistent.
	 */
	public static final class MonthSummary {
		public String period = "";
		public long incomeCents;
		public long expenseCents;
		public long borrowCents;
		public long lendCents;
		public long creditCents;
		public long transferCents;
		public Map byCategory = new HashMap();

		public long pnlNetCents() {
			return incomeCents - expenseCents;
		}
	}
}
