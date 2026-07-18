package org.freeplane.view.swing.features.finance;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.view.swing.features.reports.ReportViewModel;

/**
 * Left-sidebar personal finance workspace: quick entry, lists, and viewport reports.
 * Data lives in {@code 个人财务.mm}.
 */
public final class FinanceTabPanel extends JPanel implements IMapViewChangeListener {
	private static final long serialVersionUID = 1L;
	private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
	private static final SimpleDateFormat PERIOD = new SimpleDateFormat("yyyy-MM", Locale.CHINA);

	private String viewPeriod = todayPeriod();

	private final JLabel monthLabel = DocearUiTheme.mutedLabel("—");
	private final JLabel statusLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel incomeValue = kpiValue();
	private final JLabel expenseValue = kpiValue();
	private final JLabel netValue = kpiValue();
	private final JLabel budgetValue = kpiValue();

	private final JComboBox flowCombo = new JComboBox(new String[] { "支出", "收入", "转账", "借入", "借出", "信用卡" });
	private final JTextField amountField = new JTextField();
	private final JTextField noteField = new JTextField();
	private final JComboBox categoryCombo = new JComboBox();
	private final JComboBox accountCombo = new JComboBox();
	private final JComboBox accountToCombo = new JComboBox();
	private final JTextField dateField = new JTextField(FinanceAttributes.todayYmd());
	private JPanel transferRow;

	private final JList txnList = new JList();
	private final JList budgetList = new JList();
	private final JList subList = new JList();
	private final JList couponList = new JList();

	private boolean listening;
	private boolean refreshing;

	public FinanceTabPanel() {
		super(new BorderLayout(0, 0));
		DocearUiTheme.styleCanvas(this);
		setBorder(DocearUiTheme.pageBorder());
		add(buildHeader(), BorderLayout.NORTH);
		add(buildBody(), BorderLayout.CENTER);
		wireRenderers();
		startListening();
		refreshAll();
	}

	public void startListening() {
		if (listening) {
			return;
		}
		listening = true;
		try {
			Controller.getCurrentController().getMapViewManager().addMapViewChangeListener(this);
		}
		catch (Exception e) {
		}
	}

	public void stopListening() {
		if (!listening) {
			return;
		}
		listening = false;
		try {
			Controller.getCurrentController().getMapViewManager().removeMapViewChangeListener(this);
		}
		catch (Exception e) {
		}
	}

	public void afterViewChange(final Component oldView, final Component newView) {
		if (listening) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					refreshAll();
				}
			});
		}
	}

	public void afterViewClose(final Component oldView) {
	}

	public void afterViewCreated(final Component mapView) {
	}

	public void beforeViewChange(final Component oldView, final Component newView) {
	}

	private JPanel buildHeader() {
		final JPanel header = new JPanel(new BorderLayout(8, 8));
		header.setOpaque(false);
		final JLabel title = DocearUiTheme.titleLabel("个人财务");

		final JButton prev = DocearUiTheme.ghostButton("‹");
		final JButton next = DocearUiTheme.ghostButton("›");
		final JButton thisMonth = DocearUiTheme.ghostButton("本月");
		prev.setToolTipText("上一月");
		next.setToolTipText("下一月");
		thisMonth.setToolTipText("回到本月");
		prev.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				shiftPeriod(-1);
			}
		});
		next.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				shiftPeriod(1);
			}
		});
		thisMonth.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				viewPeriod = todayPeriod();
				refreshAll();
			}
		});

		final JPanel monthRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		monthRow.setOpaque(false);
		monthRow.add(prev);
		monthRow.add(monthLabel);
		monthRow.add(next);
		monthRow.add(thisMonth);

		final JPanel titleCol = new JPanel();
		titleCol.setOpaque(false);
		titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
		titleCol.add(title);
		titleCol.add(Box.createVerticalStrut(2));
		titleCol.add(monthRow);
		statusLabel.setBorder(new EmptyBorder(2, 0, 0, 0));
		titleCol.add(statusLabel);

		final JButton refresh = softButton("刷新");
		refresh.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				refreshAll();
			}
		});
		final JButton openMap = softButton("打开账本");
		openMap.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				openFinanceMap();
			}
		});
		final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		actions.setOpaque(false);
		actions.add(refresh);
		actions.add(openMap);

		header.add(titleCol, BorderLayout.CENTER);
		header.add(actions, BorderLayout.EAST);
		header.add(buildKpiRow(), BorderLayout.SOUTH);
		return header;
	}

	private JPanel buildKpiRow() {
		final JPanel row = new JPanel(new GridLayout(1, 4, 8, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(10, 0, 8, 0));
		row.add(kpiCard("收入", incomeValue, DocearUiTheme.SUCCESS));
		row.add(kpiCard("支出", expenseValue, DocearUiTheme.DANGER));
		row.add(kpiCard("结余", netValue, DocearUiTheme.ACCENT));
		row.add(kpiCard("预算余", budgetValue, DocearUiTheme.WARNING));
		return row;
	}

	private static JPanel kpiCard(final String label, final JLabel value, final Color accent) {
		final JPanel card = new JPanel(new BorderLayout(0, 2));
		card.setBackground(DocearUiTheme.SURFACE);
		card.setBorder(BorderFactory.createCompoundBorder(DocearUiTheme.hairlineBorder(), new EmptyBorder(8, 8, 8, 8)));
		final JLabel l = DocearUiTheme.mutedLabel(label);
		l.setFont(DocearUiTheme.font(11f));
		value.setForeground(accent);
		card.add(l, BorderLayout.NORTH);
		card.add(value, BorderLayout.CENTER);
		return card;
	}

	private static JLabel kpiValue() {
		final JLabel l = new JLabel("—");
		l.setFont(DocearUiTheme.font(14f, Font.BOLD));
		l.setForeground(DocearUiTheme.TEXT);
		return l;
	}

	private JPanel buildBody() {
		final JPanel body = new JPanel(new BorderLayout(0, 8));
		body.setOpaque(false);
		body.add(buildQuickEntry(), BorderLayout.NORTH);
		final JTabbedPane tabs = new JTabbedPane();
		tabs.setFont(DocearUiTheme.font(12f, Font.BOLD));
		tabs.addTab("流水", wrapList(txnList));
		tabs.addTab("预算", wrapList(budgetList));
		tabs.addTab("订阅", wrapList(subList));
		tabs.addTab("券", wrapList(couponList));
		tabs.addTab("报表", buildReportPanel());
		body.add(tabs, BorderLayout.CENTER);
		return body;
	}

	private JPanel buildQuickEntry() {
		final JPanel box = new JPanel();
		box.setOpaque(true);
		box.setBackground(DocearUiTheme.SURFACE);
		box.setBorder(BorderFactory.createCompoundBorder(DocearUiTheme.hairlineBorder(), new EmptyBorder(10, 10, 10, 10)));
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

		final JLabel h = DocearUiTheme.sectionLabel("快速记账");
		h.setFont(DocearUiTheme.font(13f, Font.BOLD));
		h.setForeground(DocearUiTheme.TEXT);
		h.setAlignmentX(LEFT_ALIGNMENT);
		box.add(h);
		box.add(Box.createVerticalStrut(8));

		final JPanel row1 = fieldRow();
		row1.add(labeled("流向", flowCombo));
		row1.add(Box.createHorizontalStrut(8));
		row1.add(labeled("金额", amountField));
		box.add(row1);
		box.add(Box.createVerticalStrut(6));

		final JPanel row2 = fieldRow();
		row2.add(labeled("分类", categoryCombo));
		row2.add(Box.createHorizontalStrut(8));
		row2.add(labeled("账户", accountCombo));
		box.add(row2);
		box.add(Box.createVerticalStrut(6));

		transferRow = fieldRow();
		transferRow.add(labeled("转入账户", accountToCombo));
		transferRow.add(Box.createHorizontalStrut(8));
		transferRow.add(Box.createHorizontalGlue());
		box.add(transferRow);
		box.add(Box.createVerticalStrut(6));

		final JPanel row3 = fieldRow();
		row3.add(labeled("日期", dateField));
		row3.add(Box.createHorizontalStrut(8));
		row3.add(labeled("备注", noteField));
		box.add(row3);
		box.add(Box.createVerticalStrut(8));

		final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		actions.setOpaque(false);
		actions.setAlignmentX(LEFT_ALIGNMENT);
		actions.add(primaryButton("记一笔", new Runnable() {
			public void run() {
				saveTransaction();
			}
		}));
		final JButton more = softButton("更多 ▾");
		final JPopupMenu menu = buildMoreMenu();
		more.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				menu.show(more, 0, more.getHeight());
			}
		});
		actions.add(more);
		actions.add(softButton("删选中", new Runnable() {
			public void run() {
				deleteSelected();
			}
		}));
		actions.add(softButton("券已用", new Runnable() {
			public void run() {
				markSelectedCouponUsed();
			}
		}));
		box.add(actions);

		flowCombo.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				reloadCategoryCombo();
				updateTransferUi();
			}
		});
		updateTransferUi();
		return box;
	}

	private JPopupMenu buildMoreMenu() {
		final JPopupMenu menu = new JPopupMenu();
		menu.add(menuItem("加分类", new Runnable() {
			public void run() {
				promptAddCategory();
			}
		}));
		menu.add(menuItem("加账户", new Runnable() {
			public void run() {
				promptAddAccount();
			}
		}));
		menu.add(menuItem("加预算", new Runnable() {
			public void run() {
				promptAddBudget();
			}
		}));
		menu.addSeparator();
		menu.add(menuItem("加订阅", new Runnable() {
			public void run() {
				promptAddSubscription();
			}
		}));
		menu.add(menuItem("加优惠券", new Runnable() {
			public void run() {
				promptAddCoupon();
			}
		}));
		return menu;
	}

	private static JMenuItem menuItem(final String text, final Runnable action) {
		final JMenuItem item = new JMenuItem(text);
		item.setFont(DocearUiTheme.font(12f));
		item.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				action.run();
			}
		});
		return item;
	}

	private JPanel buildReportPanel() {
		final JPanel p = new JPanel();
		p.setOpaque(false);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBorder(new EmptyBorder(8, 4, 8, 4));
		p.add(reportButton("本月总览", FinanceReportEngine.ID_MONTH_OVERVIEW));
		p.add(Box.createVerticalStrut(6));
		p.add(reportButton("支出分类", FinanceReportEngine.ID_EXPENSE_BY_CATEGORY));
		p.add(Box.createVerticalStrut(6));
		p.add(reportButton("收入分类", FinanceReportEngine.ID_INCOME_BY_CATEGORY));
		p.add(Box.createVerticalStrut(6));
		p.add(reportButton("收支趋势", FinanceReportEngine.ID_TREND));
		p.add(Box.createVerticalStrut(6));
		p.add(reportButton("预算执行", FinanceReportEngine.ID_BUDGET_STATUS));
		p.add(Box.createVerticalStrut(6));
		p.add(reportButton("订阅清单", FinanceReportEngine.ID_SUBSCRIPTIONS));
		p.add(Box.createVerticalStrut(6));
		p.add(reportButton("优惠券", FinanceReportEngine.ID_COUPONS));
		p.add(Box.createVerticalStrut(10));
		final JLabel tip = DocearUiTheme.mutedLabel(
				"<html><body style='width:220px'>报表在导图视口展示，不写入节点。<br/>数据保存在「个人财务.mm」。</body></html>");
		tip.setAlignmentX(LEFT_ALIGNMENT);
		p.add(tip);
		return p;
	}

	private JButton reportButton(final String label, final String reportId) {
		final JButton b = softButton(label, new Runnable() {
			public void run() {
				showReport(reportId);
			}
		});
		b.setAlignmentX(LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		return b;
	}

	private void wireRenderers() {
		txnList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		txnList.setCellRenderer(rowRenderer());
		budgetList.setCellRenderer(rowRenderer());
		subList.setCellRenderer(rowRenderer());
		couponList.setCellRenderer(rowRenderer());
		txnList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() == 2) {
					final Object value = txnList.getSelectedValue();
					if (value instanceof ListRow && !((ListRow) value).placeholder) {
						focusNode(((ListRow) value).nodeId);
					}
				}
			}
		});
	}

	private static DefaultListCellRenderer rowRenderer() {
		return new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getListCellRendererComponent(final JList list, final Object value, final int index,
					final boolean isSelected, final boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setFont(DocearUiTheme.font(12f));
				if (value instanceof ListRow) {
					final ListRow row = (ListRow) value;
					setText(row.label);
					if (row.placeholder) {
						setForeground(isSelected ? getForeground() : DocearUiTheme.TEXT_FAINT);
						setEnabled(true);
					}
					else if (!isSelected) {
						setForeground(DocearUiTheme.TEXT);
					}
				}
				return this;
			}
		};
	}

	private void refreshAll() {
		if (refreshing) {
			return;
		}
		refreshing = true;
		try {
			FinanceLedgerService.ensureFinanceMap();
			final String period = viewPeriod;
			monthLabel.setText(period);
			final FinanceLedgerService.MonthSummary summary = FinanceLedgerService.monthSummary(period);
			incomeValue.setText("¥" + FinanceAttributes.formatYuan(summary.incomeCents));
			expenseValue.setText("¥" + FinanceAttributes.formatYuan(summary.expenseCents));
			netValue.setText("¥" + FinanceAttributes.formatYuan(summary.pnlNetCents()));
			budgetValue.setText("¥" + FinanceAttributes.formatYuan(budgetRemaining(period, summary)));
			reloadAccountCombo();
			reloadCategoryCombo();
			updateTransferUi();
			reloadTxnList(period);
			reloadBudgetList(period, summary);
			reloadSubList();
			reloadCouponList();
			SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_FINANCE, txnCountForPeriod(period));
			setStatus(todayPeriod().equals(period) ? "账本 · 个人财务.mm" : "浏览 " + period + " · 个人财务.mm", false);
		}
		catch (Exception ex) {
			LogUtils.warn("Finance tab refresh failed", ex);
			setStatus("刷新失败：" + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()), true);
		}
		finally {
			refreshing = false;
		}
	}

	private void setStatus(final String text, final boolean error) {
		statusLabel.setText(text == null || text.length() == 0 ? " " : text);
		statusLabel.setForeground(error ? DocearUiTheme.DANGER : DocearUiTheme.TEXT_MUTED);
	}

	private void shiftPeriod(final int deltaMonths) {
		try {
			final Calendar cal = Calendar.getInstance();
			cal.setTime(PERIOD.parse(viewPeriod));
			cal.add(Calendar.MONTH, deltaMonths);
			viewPeriod = PERIOD.format(cal.getTime());
			refreshAll();
		}
		catch (Exception e) {
			viewPeriod = todayPeriod();
			refreshAll();
		}
	}

	private static long budgetRemaining(final String period, final FinanceLedgerService.MonthSummary summary) {
		final List budgets = FinanceLedgerService.listBudgets(period);
		if (budgets.isEmpty()) {
			return 0L;
		}
		long remaining = 0L;
		for (int i = 0; i < budgets.size(); i++) {
			final FinanceLedgerService.FinanceBudget b = (FinanceLedgerService.FinanceBudget) budgets.get(i);
			remaining += FinanceRules.budgetRemainingCents(b.categoryName, b.amountCents, summary.expenseCents,
					summary.byCategory);
		}
		return remaining;
	}

	private static int txnCountForPeriod(final String period) {
		final String from = period + "-01";
		final String to = period + "-31";
		return FinanceLedgerService.listTransactions(from, to).size();
	}

	private void reloadAccountCombo() {
		final Object selected = accountCombo.getSelectedItem();
		final Object selectedTo = accountToCombo.getSelectedItem();
		accountCombo.removeAllItems();
		accountToCombo.removeAllItems();
		final List accounts = FinanceLedgerService.listAccounts();
		for (int i = 0; i < accounts.size(); i++) {
			final String name = String.valueOf(accounts.get(i));
			accountCombo.addItem(name);
			accountToCombo.addItem(name);
		}
		if (selected != null) {
			accountCombo.setSelectedItem(selected);
		}
		if (selectedTo != null) {
			accountToCombo.setSelectedItem(selectedTo);
		}
		else if (accountToCombo.getItemCount() > 1) {
			accountToCombo.setSelectedIndex(Math.min(1, accountToCombo.getItemCount() - 1));
		}
	}

	private void updateTransferUi() {
		final boolean transfer = FinanceAttributes.FLOW_TRANSFER.equals(flowFromCombo());
		accountToCombo.setEnabled(transfer);
		if (transferRow != null) {
			transferRow.setVisible(transfer);
			transferRow.getParent().revalidate();
			transferRow.getParent().repaint();
		}
	}

	private void reloadCategoryCombo() {
		final String flow = flowFromCombo();
		final Object selected = categoryCombo.getSelectedItem();
		categoryCombo.removeAllItems();
		categoryCombo.addItem("（未分类）");
		final String listFlow = FinanceAttributes.FLOW_INCOME.equals(flow)
				? FinanceAttributes.FLOW_INCOME
				: FinanceAttributes.FLOW_EXPENSE;
		final List cats = FinanceLedgerService.listCategories(listFlow);
		for (int i = 0; i < cats.size(); i++) {
			categoryCombo.addItem(String.valueOf(cats.get(i)));
		}
		if (selected != null) {
			categoryCombo.setSelectedItem(selected);
		}
	}

	private void reloadTxnList(final String period) {
		final List txns = FinanceLedgerService.listTransactions(period + "-01", period + "-31");
		if (txns.isEmpty()) {
			txnList.setListData(new ListRow[] { ListRow.empty("本月暂无流水 · 上方记一笔即可") });
			return;
		}
		final ListRow[] rows = new ListRow[txns.size()];
		for (int i = 0; i < txns.size(); i++) {
			final FinanceLedgerService.FinanceTxn t = (FinanceLedgerService.FinanceTxn) txns.get(i);
			final String sign = FinanceRules.flowSign(t.flow);
			final String flowZh = FinanceRules.flowLabelZh(t.flow);
			final String cat = t.categoryName == null || t.categoryName.length() == 0 ? "未分类" : t.categoryName;
			String extra = t.note == null || t.note.length() == 0
					? (t.merchant == null ? "" : t.merchant)
					: t.note;
			if (FinanceRules.isTransfer(t.flow)) {
				extra = t.accountName + "→" + t.accountTo + (extra.length() == 0 ? "" : " · " + extra);
			}
			final String nodeId = t.node == null ? "" : t.node.createID();
			rows[i] = new ListRow(nodeId, t.dateYmd + "  " + sign + "¥" + FinanceAttributes.formatYuan(t.amountCents)
					+ "  [" + flowZh + "] " + cat + (extra.length() == 0 ? "" : " · " + extra));
		}
		txnList.setListData(rows);
	}

	private void reloadBudgetList(final String period, final FinanceLedgerService.MonthSummary summary) {
		final List budgets = FinanceLedgerService.listBudgets(period);
		if (budgets.isEmpty()) {
			budgetList.setListData(new ListRow[] { ListRow.empty("暂无预算 · 「更多」里可添加") });
			return;
		}
		final ListRow[] rows = new ListRow[budgets.size()];
		for (int i = 0; i < budgets.size(); i++) {
			final FinanceLedgerService.FinanceBudget b = (FinanceLedgerService.FinanceBudget) budgets.get(i);
			final long spent = FinanceRules.budgetSpentCents(b.categoryName, summary.expenseCents, summary.byCategory);
			final String name = FinanceRules.isTotalBudgetCategory(b.categoryName)
					? FinanceRules.TOTAL_BUDGET_CATEGORY
					: b.categoryName;
			final String nodeId = b.node == null ? "" : b.node.createID();
			rows[i] = new ListRow(nodeId, name + "  ¥" + FinanceAttributes.formatYuan(spent)
					+ " / ¥" + FinanceAttributes.formatYuan(b.amountCents)
					+ "  余 ¥" + FinanceAttributes.formatYuan(b.amountCents - spent));
		}
		budgetList.setListData(rows);
	}

	private void reloadSubList() {
		final List subs = FinanceLedgerService.listSubscriptions();
		if (subs.isEmpty()) {
			subList.setListData(new ListRow[] { ListRow.empty("暂无订阅 · 「更多 → 加订阅」") });
			return;
		}
		final ListRow[] rows = new ListRow[subs.size()];
		for (int i = 0; i < subs.size(); i++) {
			final FinanceLedgerService.FinanceSubscription s = (FinanceLedgerService.FinanceSubscription) subs.get(i);
			final String nodeId = s.node == null ? "" : s.node.createID();
			rows[i] = new ListRow(nodeId, s.name + "  ¥" + FinanceAttributes.formatYuan(s.amountCents)
					+ " / " + s.cycle + (s.nextYmd.length() == 0 ? "" : "  下次 " + s.nextYmd));
		}
		subList.setListData(rows);
	}

	private void reloadCouponList() {
		final List coupons = FinanceLedgerService.listCoupons();
		if (coupons.isEmpty()) {
			couponList.setListData(new ListRow[] { ListRow.empty("暂无优惠券 · 「更多 → 加优惠券」") });
			return;
		}
		final ListRow[] rows = new ListRow[coupons.size()];
		for (int i = 0; i < coupons.size(); i++) {
			final FinanceLedgerService.FinanceCoupon c = (FinanceLedgerService.FinanceCoupon) coupons.get(i);
			final String nodeId = c.node == null ? "" : c.node.createID();
			final boolean used = "used".equalsIgnoreCase(c.status);
			rows[i] = new ListRow(nodeId, c.name + "  ¥" + FinanceAttributes.formatYuan(c.amountCents)
					+ (c.expiresYmd.length() == 0 ? "" : "  截止 " + c.expiresYmd)
					+ (used ? "  [已用]" : ""));
		}
		couponList.setListData(rows);
	}

	private void saveTransaction() {
		try {
			final long cents = FinanceAttributes.parseYuanToCents(amountField.getText());
			if (!FinanceRules.isValidAmountCents(cents)) {
				JOptionPane.showMessageDialog(this, "请输入有效金额", "记账", JOptionPane.WARNING_MESSAGE);
				return;
			}
			final String flow = flowFromCombo();
			final String cat = selectedComboText(categoryCombo);
			final String acc = selectedComboText(accountCombo);
			final String accTo = selectedComboText(accountToCombo);
			if (FinanceAttributes.FLOW_TRANSFER.equals(flow)) {
				if (acc.length() == 0 || accTo.length() == 0) {
					JOptionPane.showMessageDialog(this, "转账需要选择转出账户和转入账户", "记账",
							JOptionPane.WARNING_MESSAGE);
					return;
				}
				if (acc.equals(accTo)) {
					JOptionPane.showMessageDialog(this, "转出与转入账户不能相同", "记账", JOptionPane.WARNING_MESSAGE);
					return;
				}
			}
			final DateParse dateParse = parseDate(dateField.getText());
			if (!dateParse.valid) {
				JOptionPane.showMessageDialog(this, "日期格式应为 yyyy-MM-dd", "记账", JOptionPane.WARNING_MESSAGE);
				return;
			}
			final String note = noteField.getText() == null ? "" : noteField.getText().trim();
			final NodeModel node = FinanceLedgerService.addTransaction(
					FinanceAttributes.formatYuan(cents),
					flow,
					dateParse.ymd,
					"（未分类）".equals(cat) ? "" : cat,
					acc,
					FinanceAttributes.FLOW_TRANSFER.equals(flow) ? accTo : "",
					"",
					note);
			if (node == null) {
				JOptionPane.showMessageDialog(this, "记账失败：无法写入财务导图（转账需双方账户，金额须>0）", "记账",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			amountField.setText("");
			noteField.setText("");
			if (dateParse.ymd.length() >= 7) {
				viewPeriod = dateParse.ymd.substring(0, 7);
			}
			refreshAll();
			setStatus("已记账 · " + FinanceRules.flowLabelZh(flow) + " ¥" + FinanceAttributes.formatYuan(cents), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "记账失败: " + ex.getMessage(), "记账", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void promptAddAccount() {
		final String name = JOptionPane.showInputDialog(this, "账户名称（如 微信/支付宝/信用卡）", "");
		if (name == null || name.trim().length() == 0) {
			return;
		}
		try {
			FinanceLedgerService.addAccount(name.trim());
			refreshAll();
			setStatus("已添加账户 · " + name.trim(), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "添加账户失败: " + ex.getMessage(), "账户", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void deleteSelected() {
		final ListRow row = selectedListRow();
		if (row == null || row.placeholder || row.nodeId.length() == 0) {
			JOptionPane.showMessageDialog(this, "请先在流水/预算/订阅/券列表中选中一项", "删除",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final int ok = JOptionPane.showConfirmDialog(this, "确认删除？\n" + row.label, "删除",
				JOptionPane.OK_CANCEL_OPTION);
		if (ok != JOptionPane.OK_OPTION) {
			return;
		}
		if (!FinanceLedgerService.deleteFinanceNode(row.nodeId)) {
			JOptionPane.showMessageDialog(this, "删除失败", "删除", JOptionPane.ERROR_MESSAGE);
			return;
		}
		refreshAll();
		setStatus("已删除", false);
	}

	private void markSelectedCouponUsed() {
		final Object value = couponList.getSelectedValue();
		if (!(value instanceof ListRow) || ((ListRow) value).placeholder) {
			JOptionPane.showMessageDialog(this, "请先在「券」列表选中一项", "优惠券", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final String nodeId = ((ListRow) value).nodeId;
		if (FinanceLedgerService.markCouponUsed(nodeId, true) == null) {
			JOptionPane.showMessageDialog(this, "标记失败", "优惠券", JOptionPane.ERROR_MESSAGE);
			return;
		}
		refreshAll();
		setStatus("已标记优惠券为已用", false);
	}

	private ListRow selectedListRow() {
		Object value = txnList.getSelectedValue();
		if (!(value instanceof ListRow) || ((ListRow) value).placeholder) {
			value = budgetList.getSelectedValue();
		}
		if (!(value instanceof ListRow) || ((ListRow) value).placeholder) {
			value = subList.getSelectedValue();
		}
		if (!(value instanceof ListRow) || ((ListRow) value).placeholder) {
			value = couponList.getSelectedValue();
		}
		return value instanceof ListRow && !((ListRow) value).placeholder ? (ListRow) value : null;
	}

	private void promptAddCategory() {
		final String name = JOptionPane.showInputDialog(this, "分类名称", "");
		if (name == null || name.trim().length() == 0) {
			return;
		}
		try {
			final String flow = flowFromCombo();
			final String catFlow = FinanceAttributes.FLOW_INCOME.equals(flow)
					? FinanceAttributes.FLOW_INCOME
					: FinanceAttributes.FLOW_EXPENSE;
			FinanceLedgerService.addCategory(name.trim(), catFlow);
			refreshAll();
			setStatus("已添加分类 · " + name.trim(), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "添加分类失败: " + ex.getMessage(), "分类", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void promptAddBudget() {
		final String cat = selectedComboText(categoryCombo);
		final String category = "（未分类）".equals(cat) || cat.length() == 0
				? FinanceRules.TOTAL_BUDGET_CATEGORY
				: cat;
		final String amount = JOptionPane.showInputDialog(this,
				"预算金额（元）· " + category + " · " + viewPeriod, "1000");
		if (amount == null || amount.trim().length() == 0) {
			return;
		}
		try {
			final long cents = FinanceAttributes.parseYuanToCents(amount);
			FinanceLedgerService.setBudget(viewPeriod, category, cents);
			refreshAll();
			setStatus("已设置预算 · " + category, false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "添加预算失败: " + ex.getMessage(), "预算", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void promptAddSubscription() {
		final JTextField nameField = new JTextField("会员订阅", 18);
		final JTextField amountFieldLocal = new JTextField("15", 10);
		final JComboBox cycleBox = new JComboBox(new String[] { "monthly", "yearly", "weekly" });
		final JPanel form = formPanel(
				labeled("名称", nameField),
				labeled("每期金额（元）", amountFieldLocal),
				labeled("周期", cycleBox));
		final int option = JOptionPane.showConfirmDialog(this, form, "加订阅", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (option != JOptionPane.OK_OPTION) {
			return;
		}
		final String name = nameField.getText() == null ? "" : nameField.getText().trim();
		if (name.length() == 0) {
			return;
		}
		try {
			final long cents = FinanceAttributes.parseYuanToCents(amountFieldLocal.getText());
			final String cycleValue = selectedComboText(cycleBox);
			FinanceLedgerService.upsertSubscription(
					name,
					cents,
					cycleValue.length() == 0 ? "monthly" : cycleValue,
					FinanceRules.nextDateForCycle(FinanceAttributes.todayYmd(),
							cycleValue.length() == 0 ? "monthly" : cycleValue),
					"active",
					selectedComboText(accountCombo),
					"");
			refreshAll();
			setStatus("已添加订阅 · " + name, false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "添加订阅失败: " + ex.getMessage(), "订阅", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void promptAddCoupon() {
		final JTextField nameField = new JTextField("满减券", 18);
		final JTextField amountFieldLocal = new JTextField("10", 10);
		final JTextField expiresField = new JTextField(plusMonths(FinanceAttributes.todayYmd(), 1), 12);
		final JPanel form = formPanel(
				labeled("名称", nameField),
				labeled("面值（元）", amountFieldLocal),
				labeled("截止日期", expiresField));
		final int option = JOptionPane.showConfirmDialog(this, form, "加优惠券", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (option != JOptionPane.OK_OPTION) {
			return;
		}
		final String name = nameField.getText() == null ? "" : nameField.getText().trim();
		if (name.length() == 0) {
			return;
		}
		try {
			final long cents = FinanceAttributes.parseYuanToCents(amountFieldLocal.getText());
			final DateParse expires = parseDate(expiresField.getText());
			FinanceLedgerService.upsertCoupon(
					name,
					cents,
					expires.valid ? expires.ymd : plusMonths(FinanceAttributes.todayYmd(), 1),
					"active",
					"",
					"");
			refreshAll();
			setStatus("已添加优惠券 · " + name, false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "添加优惠券失败: " + ex.getMessage(), "优惠券", JOptionPane.ERROR_MESSAGE);
		}
	}

	private static JPanel formPanel(final JPanel... rows) {
		final JPanel form = new JPanel();
		DocearUiTheme.styleSurface(form);
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.setBorder(new EmptyBorder(4, 4, 4, 4));
		for (int i = 0; i < rows.length; i++) {
			if (i > 0) {
				form.add(Box.createVerticalStrut(8));
			}
			rows[i].setAlignmentX(LEFT_ALIGNMENT);
			form.add(rows[i]);
		}
		return form;
	}

	private void showReport(final String reportId) {
		try {
			final String[] range = monthRange();
			final FinanceViewportService viewport = FinanceViewportService.get();
			if (viewport == null) {
				JOptionPane.showMessageDialog(this, "报表视口未就绪", "报表", JOptionPane.WARNING_MESSAGE);
				return;
			}
			final ReportViewModel model = FinanceReportEngine.generateView(reportId, range[0], range[1]);
			viewport.show(model);
			setStatus("已打开报表 · " + viewPeriod, false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "报表失败: " + ex.getMessage(), "报表", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void openFinanceMap() {
		try {
			final MapModel map = FinanceLedgerService.ensureFinanceMap();
			if (map == null) {
				JOptionPane.showMessageDialog(this, "无法创建/打开账本", "财务", JOptionPane.ERROR_MESSAGE);
				return;
			}
			Controller.getCurrentController().getMapViewManager().newMapView(map,
					Controller.getCurrentModeController());
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "无法打开账本: " + ex.getMessage(), "财务", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void focusNode(final String nodeId) {
		if (nodeId == null || nodeId.length() == 0) {
			return;
		}
		try {
			final MapModel map = FinanceLedgerService.ensureFinanceMap();
			if (map == null) {
				return;
			}
			Controller.getCurrentController().getMapViewManager().newMapView(map,
					Controller.getCurrentModeController());
			final NodeModel node = map.getNodeForID(nodeId);
			if (node != null) {
				Controller.getCurrentModeController().getMapController().select(node);
			}
		}
		catch (Exception ex) {
			LogUtils.warn("Focus finance node failed", ex);
		}
	}

	private String flowFromCombo() {
		final int i = flowCombo.getSelectedIndex();
		if (i == 1) {
			return FinanceAttributes.FLOW_INCOME;
		}
		if (i == 2) {
			return FinanceAttributes.FLOW_TRANSFER;
		}
		if (i == 3) {
			return FinanceAttributes.FLOW_BORROW;
		}
		if (i == 4) {
			return FinanceAttributes.FLOW_LEND;
		}
		if (i == 5) {
			return FinanceAttributes.FLOW_CREDIT;
		}
		return FinanceAttributes.FLOW_EXPENSE;
	}

	private static String selectedComboText(final JComboBox combo) {
		final Object v = combo.getSelectedItem();
		return v == null ? "" : String.valueOf(v).trim();
	}

	private static DateParse parseDate(final String text) {
		if (text == null || text.trim().length() == 0) {
			return DateParse.of(FinanceAttributes.todayYmd(), true);
		}
		try {
			DAY.setLenient(false);
			return DateParse.of(DAY.format(DAY.parse(text.trim())), true);
		}
		catch (Exception e) {
			return DateParse.of(FinanceAttributes.todayYmd(), false);
		}
	}

	private static String todayPeriod() {
		final String today = FinanceAttributes.todayYmd();
		return today.length() >= 7 ? today.substring(0, 7) : today;
	}

	private String[] monthRange() {
		return new String[] { viewPeriod + "-01", viewPeriod + "-31" };
	}

	private static String plusMonths(final String ymd, final int months) {
		try {
			final Calendar cal = Calendar.getInstance();
			cal.setTime(DAY.parse(ymd));
			cal.add(Calendar.MONTH, months);
			return DAY.format(cal.getTime());
		}
		catch (Exception e) {
			return ymd;
		}
	}

	private static JPanel fieldRow() {
		final JPanel row = new JPanel();
		row.setOpaque(false);
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		return row;
	}

	private static JPanel labeled(final String label, final Component field) {
		final JPanel p = new JPanel(new BorderLayout(0, 2));
		p.setOpaque(false);
		final JLabel l = DocearUiTheme.mutedLabel(label);
		l.setFont(DocearUiTheme.font(11f));
		if (field instanceof JTextField) {
			((JTextField) field).setColumns(8);
			((JTextField) field).setFont(DocearUiTheme.font(13f));
		}
		else if (field instanceof JComboBox) {
			((JComboBox) field).setFont(DocearUiTheme.font(12f));
		}
		p.add(l, BorderLayout.NORTH);
		p.add(field, BorderLayout.CENTER);
		return p;
	}

	private static JScrollPane wrapList(final JList list) {
		DocearUiTheme.styleList(list);
		list.setFixedCellHeight(28);
		final JScrollPane sp = new JScrollPane(list);
		DocearUiTheme.styleScrollPane(sp);
		return sp;
	}

	private static JButton softButton(final String text) {
		return DocearUiTheme.softButton(text);
	}

	private static JButton softButton(final String text, final Runnable action) {
		final JButton b = softButton(text);
		b.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				action.run();
			}
		});
		return b;
	}

	private static JButton primaryButton(final String text, final Runnable action) {
		final JButton b = DocearUiTheme.primaryButton(text);
		if (action != null) {
			b.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					action.run();
				}
			});
		}
		return b;
	}

	private static final class DateParse {
		final String ymd;
		final boolean valid;

		private DateParse(final String ymd, final boolean valid) {
			this.ymd = ymd;
			this.valid = valid;
		}

		static DateParse of(final String ymd, final boolean valid) {
			return new DateParse(ymd, valid);
		}
	}

	private static final class ListRow {
		final String nodeId;
		final String label;
		final boolean placeholder;

		ListRow(final String nodeId, final String label) {
			this(nodeId, label, false);
		}

		private ListRow(final String nodeId, final String label, final boolean placeholder) {
			this.nodeId = nodeId == null ? "" : nodeId;
			this.label = label == null ? "" : label;
			this.placeholder = placeholder;
		}

		static ListRow empty(final String hint) {
			return new ListRow("", hint, true);
		}

		public String toString() {
			return label;
		}
	}
}
