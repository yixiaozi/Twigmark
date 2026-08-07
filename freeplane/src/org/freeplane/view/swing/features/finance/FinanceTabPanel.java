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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.MapChangeEvent;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.view.swing.features.reports.ReportViewModel;
import org.freeplane.core.util.TextUtils;

/**
 * Left-sidebar personal finance workspace: quick entry, lists, and viewport reports.
 * Data lives in {@code 个人财务.mm}.
 */
public final class FinanceTabPanel extends JPanel implements IMapViewChangeListener {
	private static final long serialVersionUID = 1L;
	private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
	private static final SimpleDateFormat DATETIME = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
	private static final SimpleDateFormat PERIOD = new SimpleDateFormat("yyyy-MM", Locale.CHINA);

	private String viewPeriod = todayPeriod();
	private boolean dayViewMode;
	private String viewDay = FinanceAttributes.todayYmd();

	private final JLabel monthLabel = DocearUiTheme.mutedLabel("—");
	private final JButton prevMonthButton = DocearUiTheme.ghostButton("‹");
	private final JButton nextMonthButton = DocearUiTheme.ghostButton("›");
	private final JButton thisPeriodButton = DocearUiTheme.ghostButton(TextUtils.getText("finance.period.this_month"));
	private final JLabel statusLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel dailySpendLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel incomeValue = kpiValue();
	private final JLabel expenseValue = kpiValue();
	private final JLabel netValue = kpiValue();
	private final JLabel budgetValue = kpiValue();

	private final JComboBox flowCombo = new JComboBox(new String[] { TextUtils.getText("finance.flow.expense"), TextUtils.getText("finance.flow.income"), TextUtils.getText("finance.flow.transfer"), TextUtils.getText("finance.flow.borrow"), TextUtils.getText("finance.flow.lend"), TextUtils.getText("finance.flow.credit") });
	private final JTextField amountField = new JTextField();
	private final JTextField merchantField = new JTextField();
	private final JTextArea noteField = new JTextArea(2, 20);
	private final JComboBox categoryCombo = new JComboBox();
	private final JComboBox accountCombo = new JComboBox();
	private final JComboBox accountToCombo = new JComboBox();
	private final JTextField dateField = new JTextField(FinanceAttributes.todayDateTime());
	private JPanel transferRow;
	private final JButton dayViewToggle = DocearUiTheme.ghostButton(TextUtils.getText("finance.view.by_day"));

	private final Runnable financeRefreshListener = new Runnable() {
		public void run() {
			if (FinanceLedgerService.isClosingAllMaps()) {
				return;
			}
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					refreshAll();
				}
			});
		}
	};

	private final JList txnList = new JList();
	private final JList budgetList = new JList();
	private final JList subList = new JList();
	private final JList couponList = new JList();

	private JTabbedPane mainTabs;
	private JButton couponUsedButton;

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
			FinanceChangeNotifier.addListener(financeRefreshListener);
			final MapController mapController = Controller.getCurrentModeController().getMapController();
			mapController.addNodeChangeListener(new INodeChangeListener() {
				public void nodeChanged(final NodeChangeEvent event) {
					final NodeModel node = event.getNode();
					if (node != null && isFinanceRootMap(node.getMap())) {
						financeRefreshListener.run();
					}
				}
			});
			mapController.addMapChangeListener(new IMapChangeListener() {
				public void mapChanged(final MapChangeEvent event) {
					if (event.getMap() != null && isFinanceRootMap(event.getMap())) {
						financeRefreshListener.run();
					}
				}

				public void onNodeInserted(final NodeModel parent, final NodeModel child, final int newIndex) {
					if (parent != null && isFinanceRootMap(parent.getMap())) {
						financeRefreshListener.run();
					}
				}

				public void onNodeDeleted(final NodeModel parent, final NodeModel child, final int index) {
					if (parent != null && isFinanceRootMap(parent.getMap())) {
						financeRefreshListener.run();
					}
				}

				public void onNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
						final NodeModel child, final int newIndex) {
					if (newParent != null && isFinanceRootMap(newParent.getMap())) {
						financeRefreshListener.run();
					}
				}

				public void onPreNodeDelete(final NodeModel oldParent, final NodeModel selectedNode, final int index) {
				}

				public void onPreNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
						final NodeModel child, final int newIndex) {
				}
			});
		}
		catch (Exception e) {
		}
	}

	private static boolean isFinanceRootMap(final MapModel map) {
		if (map == null || map.getRootNode() == null) {
			return false;
		}
		final FinanceExtension ext = FinanceExtension.getExtension(map.getRootNode());
		return ext != null && FinanceAttributes.KIND_ROOT.equals(ext.getKind());
	}

	public void stopListening() {
		if (!listening) {
			return;
		}
		listening = false;
		try {
			Controller.getCurrentController().getMapViewManager().removeMapViewChangeListener(this);
			FinanceChangeNotifier.removeListener(financeRefreshListener);
		}
		catch (Exception e) {
		}
	}

	public void afterViewChange(final Component oldView, final Component newView) {
		if (listening && !FinanceLedgerService.isClosingAllMaps()) {
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
		final JPanel header = new JPanel(new BorderLayout(8, 6));
		header.setOpaque(false);

		final JButton prev = prevMonthButton;
		final JButton next = nextMonthButton;
		final JButton thisMonth = thisPeriodButton;
		prev.setToolTipText(TextUtils.getText("finance.tooltip.prev_month"));
		next.setToolTipText(TextUtils.getText("finance.tooltip.next_month"));
		thisMonth.setToolTipText(TextUtils.getText("finance.tooltip.this_month"));
		dayViewToggle.setToolTipText(TextUtils.getText("finance.tooltip.day_view"));
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
				if (dayViewMode) {
					viewDay = FinanceAttributes.todayYmd();
				}
				viewPeriod = todayPeriod();
				refreshAll();
			}
		});
		dayViewToggle.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				toggleDayView();
			}
		});

		monthLabel.setFont(DocearUiTheme.font(15f, Font.BOLD));
		monthLabel.setForeground(DocearUiTheme.TEXT);
		final JPanel monthRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		monthRow.setOpaque(false);
		monthRow.add(prev);
		monthRow.add(monthLabel);
		monthRow.add(next);
		monthRow.add(thisMonth);
		monthRow.add(dayViewToggle);

		final JButton refresh = softButton(TextUtils.getText("finance.refresh"));
		refresh.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				refreshAll();
			}
		});
		final JButton openMap = softButton(TextUtils.getText("finance.open_ledger"));
		openMap.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				openFinanceMap();
			}
		});
		final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		actions.setOpaque(false);
		actions.add(refresh);
		actions.add(openMap);

		final JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setOpaque(false);
		top.add(monthRow, BorderLayout.CENTER);
		top.add(actions, BorderLayout.EAST);
		statusLabel.setBorder(new EmptyBorder(2, 2, 0, 2));
		statusLabel.setFont(DocearUiTheme.font(11f));

		final JPanel north = new JPanel(new BorderLayout(0, 2));
		north.setOpaque(false);
		north.add(top, BorderLayout.CENTER);
		north.add(statusLabel, BorderLayout.SOUTH);

		header.add(north, BorderLayout.NORTH);
		final JPanel south = new JPanel(new BorderLayout(0, 4));
		south.setOpaque(false);
		south.add(buildKpiRow(), BorderLayout.CENTER);
		dailySpendLabel.setFont(DocearUiTheme.font(11f));
		dailySpendLabel.setBorder(new EmptyBorder(2, 2, 2, 2));
		south.add(dailySpendLabel, BorderLayout.SOUTH);
		header.add(south, BorderLayout.SOUTH);
		return header;
	}

	private JPanel buildKpiRow() {
		final JPanel row = new JPanel(new GridLayout(1, 4, 6, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(8, 0, 6, 0));
		row.add(kpiCard(TextUtils.getText("finance.kpi.income"), incomeValue, DocearUiTheme.SUCCESS));
		row.add(kpiCard(TextUtils.getText("finance.kpi.expense"), expenseValue, DocearUiTheme.DANGER));
		row.add(kpiCard(TextUtils.getText("finance.kpi.net"), netValue, DocearUiTheme.ACCENT));
		row.add(kpiCard(TextUtils.getText("finance.kpi.budget_left"), budgetValue, DocearUiTheme.WARNING));
		return row;
	}

	private static JPanel kpiCard(final String label, final JLabel value, final Color accent) {
		final JPanel card = new JPanel(new BorderLayout(0, 2));
		card.setBackground(DocearUiTheme.SURFACE);
		card.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 0, 3, accent),
				new EmptyBorder(8, 10, 8, 8)));
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
		mainTabs = tabs;
		DocearUiTheme.styleTabbedPane(tabs);
		tabs.addTab(TextUtils.getText("finance.tab.transactions"), wrapList(txnList));
		tabs.addTab(TextUtils.getText("finance.tab.budgets"), wrapList(budgetList));
		tabs.addTab(TextUtils.getText("finance.tab.subscriptions"), wrapList(subList));
		tabs.addTab(TextUtils.getText("finance.tab.coupons"), wrapList(couponList));
		tabs.addTab(TextUtils.getText("finance.tab.reports"), buildReportPanel());
		tabs.addChangeListener(new javax.swing.event.ChangeListener() {
			public void stateChanged(final javax.swing.event.ChangeEvent e) {
				updateTabActions();
			}
		});
		body.add(tabs, BorderLayout.CENTER);
		updateTabActions();
		return body;
	}

	private void updateTabActions() {
		if (mainTabs == null || couponUsedButton == null) {
			return;
		}
		final int index = mainTabs.getSelectedIndex();
		final String title = index >= 0 ? mainTabs.getTitleAt(index) : "";
		couponUsedButton.setVisible(TextUtils.getText("finance.tab.coupons").equals(title));
	}

	private JPanel buildQuickEntry() {
		final JPanel box = new JPanel();
		box.setOpaque(true);
		box.setBackground(DocearUiTheme.SURFACE);
		box.setBorder(BorderFactory.createCompoundBorder(DocearUiTheme.hairlineBorder(), new EmptyBorder(10, 10, 10, 10)));
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

		final JLabel h = DocearUiTheme.sectionLabel(TextUtils.getText("finance.quick_entry"));
		h.setFont(DocearUiTheme.font(13f, Font.BOLD));
		h.setForeground(DocearUiTheme.TEXT);
		h.setAlignmentX(LEFT_ALIGNMENT);
		box.add(h);
		box.add(Box.createVerticalStrut(8));

		final JPanel row1 = fieldRow();
		row1.add(labeled(TextUtils.getText("finance.label.flow"), flowCombo));
		row1.add(Box.createHorizontalStrut(8));
		row1.add(labeled(TextUtils.getText("finance.label.amount"), amountField));
		box.add(row1);
		box.add(Box.createVerticalStrut(6));

		final JPanel row2 = fieldRow();
		row2.add(labeled(TextUtils.getText("finance.label.category"), categoryCombo));
		row2.add(Box.createHorizontalStrut(8));
		row2.add(labeled(TextUtils.getText("finance.label.account"), accountCombo));
		box.add(row2);
		box.add(Box.createVerticalStrut(6));

		transferRow = fieldRow();
		transferRow.add(labeled(TextUtils.getText("finance.label.account_to"), accountToCombo));
		transferRow.add(Box.createHorizontalStrut(8));
		transferRow.add(Box.createHorizontalGlue());
		box.add(transferRow);
		box.add(Box.createVerticalStrut(6));

		final JPanel row3 = fieldRow();
		row3.add(labeled(TextUtils.getText("finance.label.datetime"), dateField));
		box.add(row3);
		box.add(Box.createVerticalStrut(6));

		merchantField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		box.add(labeled(TextUtils.getText("finance.label.merchant"), merchantField));
		box.add(Box.createVerticalStrut(6));
		noteField.setRows(3);
		noteField.setLineWrap(true);
		noteField.setWrapStyleWord(true);
		noteField.setFont(DocearUiTheme.font(12f));
		final JScrollPane noteScroll = new JScrollPane(noteField);
		noteScroll.setAlignmentX(LEFT_ALIGNMENT);
		noteScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
		noteScroll.setPreferredSize(new Dimension(200, 64));
		box.add(labeled(TextUtils.getText("finance.label.note"), noteScroll));
		box.add(Box.createVerticalStrut(8));

		final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		actions.setOpaque(false);
		actions.setAlignmentX(LEFT_ALIGNMENT);
		actions.add(primaryButton(TextUtils.getText("finance.action.save"), new Runnable() {
			public void run() {
				saveTransaction();
			}
		}));
		final JButton more = softButton(TextUtils.getText("finance.action.more"));
		more.setToolTipText(TextUtils.getText("finance.action.more.tip"));
		final JPopupMenu menu = buildMoreMenu();
		more.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				menu.show(more, 0, more.getHeight());
			}
		});
		actions.add(more);
		actions.add(softButton(TextUtils.getText("finance.action.delete_selected"), new Runnable() {
			public void run() {
				deleteSelected();
			}
		}));
		couponUsedButton = softButton(TextUtils.getText("finance.action.coupon_used"), new Runnable() {
			public void run() {
				markSelectedCouponUsed();
			}
		});
		couponUsedButton.setVisible(false);
		actions.add(couponUsedButton);
		box.add(actions);

		final JLabel tip = DocearUiTheme.mutedLabel(TextUtils.getText("finance.tip.sync"));
		tip.setFont(DocearUiTheme.font(10f));
		tip.setAlignmentX(LEFT_ALIGNMENT);
		tip.setBorder(new EmptyBorder(6, 2, 0, 2));
		box.add(tip);

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
		menu.add(menuItem(TextUtils.getText("finance.menu.add_category"), new Runnable() {
			public void run() {
				promptAddCategory();
			}
		}));
		menu.add(menuItem(TextUtils.getText("finance.menu.add_account"), new Runnable() {
			public void run() {
				promptAddAccount();
			}
		}));
		menu.add(menuItem(TextUtils.getText("finance.dialog.add_budget_title"), new Runnable() {
			public void run() {
				promptAddBudget();
			}
		}));
		menu.addSeparator();
		menu.add(menuItem(TextUtils.getText("finance.menu.add_subscription"), new Runnable() {
			public void run() {
				promptAddSubscription();
			}
		}));
		menu.add(menuItem(TextUtils.getText("finance.dialog.add_coupon_title"), new Runnable() {
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
		p.add(reportButton(TextUtils.getText("finance.report.month_overview"), FinanceReportEngine.ID_MONTH_OVERVIEW));
		p.add(Box.createVerticalStrut(6));
		p.add(reportButton(TextUtils.getText("finance.report.expense_by_category"), FinanceReportEngine.ID_EXPENSE_BY_CATEGORY));
		p.add(Box.createVerticalStrut(6));
		p.add(reportButton(TextUtils.getText("finance.report.income_by_category"), FinanceReportEngine.ID_INCOME_BY_CATEGORY));
		p.add(Box.createVerticalStrut(6));
		p.add(reportButton(TextUtils.getText("finance.report.trend"), FinanceReportEngine.ID_TREND));
		p.add(Box.createVerticalStrut(6));
		p.add(reportButton(TextUtils.getText("finance.report.budget_status"), FinanceReportEngine.ID_BUDGET_STATUS));
		p.add(Box.createVerticalStrut(6));
		p.add(reportButton(TextUtils.getText("finance.report.subscriptions"), FinanceReportEngine.ID_SUBSCRIPTIONS));
		p.add(Box.createVerticalStrut(6));
		p.add(reportButton(TextUtils.getText("finance.report.coupons"), FinanceReportEngine.ID_COUPONS));
		p.add(Box.createVerticalStrut(10));
		final JLabel tip = DocearUiTheme.mutedLabel(
				TextUtils.getText("finance.report.hint_html"));
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
		budgetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		subList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		couponList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		txnList.setCellRenderer(rowRenderer());
		budgetList.setCellRenderer(rowRenderer());
		subList.setCellRenderer(rowRenderer());
		couponList.setCellRenderer(rowRenderer());
		wireListPopup(txnList, new ListPopupHandler() {
			public void onEdit(final ListRow row) {
				editTransaction(row);
			}

			public void onDelete(final ListRow row) {
				deleteTransactionRow(row);
			}

			public void onExtra(final JPopupMenu menu, final ListRow row) {
			}
		});
		wireListPopup(budgetList, new ListPopupHandler() {
			public void onEdit(final ListRow row) {
				editBudget(row);
			}

			public void onDelete(final ListRow row) {
				deleteTransactionRow(row);
			}

			public void onExtra(final JPopupMenu menu, final ListRow row) {
			}
		});
		wireListPopup(subList, new ListPopupHandler() {
			public void onEdit(final ListRow row) {
				editSubscription(row);
			}

			public void onDelete(final ListRow row) {
				deleteTransactionRow(row);
			}

			public void onExtra(final JPopupMenu menu, final ListRow row) {
				menu.add(menuItem(TextUtils.getText("finance.menu.pay_subscription"), new Runnable() {
					public void run() {
						recordSubscriptionPayment(row);
					}
				}));
			}
		});
		wireListPopup(couponList, new ListPopupHandler() {
			public void onEdit(final ListRow row) {
				editCoupon(row);
			}

			public void onDelete(final ListRow row) {
				deleteTransactionRow(row);
			}

			public void onExtra(final JPopupMenu menu, final ListRow row) {
				menu.add(menuItem(TextUtils.getText("finance.menu.mark_used"), new Runnable() {
					public void run() {
						if (FinanceLedgerService.markCouponUsed(row.nodeId, true) != null) {
							refreshAll();
							setStatus(TextUtils.getText("finance.status.coupon_marked"), false);
						}
					}
				}));
			}
		});
	}

	private interface ListPopupHandler {
		void onEdit(ListRow row);

		void onDelete(ListRow row);

		void onExtra(JPopupMenu menu, ListRow row);
	}

	private void wireListPopup(final JList list, final ListPopupHandler handler) {
		list.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() == 2) {
					final Object value = list.getSelectedValue();
					if (value instanceof ListRow && !((ListRow) value).placeholder) {
						handler.onEdit((ListRow) value);
					}
				}
			}

			public void mousePressed(final MouseEvent e) {
				showPopup(e);
			}

			public void mouseReleased(final MouseEvent e) {
				showPopup(e);
			}

			private void showPopup(final MouseEvent e) {
				if (!e.isPopupTrigger()) {
					return;
				}
				final int index = list.locationToIndex(e.getPoint());
				if (index < 0) {
					return;
				}
				list.setSelectedIndex(index);
				final Object value = list.getSelectedValue();
				if (!(value instanceof ListRow) || ((ListRow) value).placeholder) {
					return;
				}
				final ListRow row = (ListRow) value;
				final JPopupMenu menu = new JPopupMenu();
				menu.add(menuItem(TextUtils.getText("finance.menu.edit"), new Runnable() {
					public void run() {
						handler.onEdit(row);
					}
				}));
				handler.onExtra(menu, row);
				menu.add(menuItem(TextUtils.getText("finance.menu.locate"), new Runnable() {
					public void run() {
						focusNode(row.nodeId);
					}
				}));
				menu.addSeparator();
				menu.add(menuItem(TextUtils.getText("finance.menu.delete"), new Runnable() {
					public void run() {
						handler.onDelete(row);
					}
				}));
				menu.show(list, e.getX(), e.getY());
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
		if (refreshing || FinanceLedgerService.isClosingAllMaps()) {
			return;
		}
		refreshing = true;
		try {
			// Read-only: never ensureFinanceMap() here — that reopens 个人财务.mm during quit.
			if (dayViewMode) {
				monthLabel.setText(viewDay);
				prevMonthButton.setToolTipText(TextUtils.getText("finance.tooltip.prev_day"));
				nextMonthButton.setToolTipText(TextUtils.getText("finance.tooltip.next_day"));
				thisPeriodButton.setText(TextUtils.getText("finance.period.today"));
				thisPeriodButton.setToolTipText(TextUtils.getText("finance.tooltip.today"));
				dayViewToggle.setText(TextUtils.getText("finance.view.by_month"));
			}
			else {
				monthLabel.setText(viewPeriod);
				prevMonthButton.setToolTipText(TextUtils.getText("finance.tooltip.prev_month"));
				nextMonthButton.setToolTipText(TextUtils.getText("finance.tooltip.next_month"));
				thisPeriodButton.setText(TextUtils.getText("finance.period.this_month"));
				thisPeriodButton.setToolTipText(TextUtils.getText("finance.tooltip.this_month"));
				dayViewToggle.setText(TextUtils.getText("finance.view.by_day"));
			}
			final FinanceLedgerService.MonthSummary summary = dayViewMode
					? FinanceLedgerService.daySummary(viewDay)
					: FinanceLedgerService.monthSummary(viewPeriod);
			incomeValue.setText("¥" + FinanceAttributes.formatYuan(summary.incomeCents));
			expenseValue.setText("¥" + FinanceAttributes.formatYuan(summary.expenseCents));
			netValue.setText("¥" + FinanceAttributes.formatYuan(summary.pnlNetCents()));
			budgetValue.setText("¥" + FinanceAttributes.formatYuan(budgetRemaining(viewPeriod, summary)));
			reloadAccountCombo();
			reloadCategoryCombo();
			updateTransferUi();
			if (dayViewMode) {
				reloadTxnListForDay(viewDay);
			}
			else {
				reloadTxnList(viewPeriod);
			}
			reloadBudgetList(viewPeriod, summary);
			reloadSubList();
			reloadCouponList();
			refreshDailySpendStrip();
			SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_FINANCE,
					dayViewMode ? txnCountForDay(viewDay) : txnCountForPeriod(viewPeriod));
			setStatus(" ", false);
		}
		catch (Exception ex) {
			LogUtils.warn("Finance tab refresh failed", ex);
			setStatus(TextUtils.format("finance.status.refresh_failed", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()), true);
		}
		finally {
			refreshing = false;
		}
	}

	private void setStatus(final String text, final boolean error) {
		statusLabel.setText(text == null || text.length() == 0 ? " " : text);
		statusLabel.setForeground(error ? DocearUiTheme.DANGER : DocearUiTheme.TEXT_MUTED);
	}

	private void shiftPeriod(final int delta) {
		if (dayViewMode) {
			shiftDay(delta);
			return;
		}
		try {
			final Calendar cal = Calendar.getInstance();
			cal.setTime(PERIOD.parse(viewPeriod));
			cal.add(Calendar.MONTH, delta);
			viewPeriod = PERIOD.format(cal.getTime());
			refreshAll();
		}
		catch (Exception e) {
			viewPeriod = todayPeriod();
			refreshAll();
		}
	}

	private void shiftDay(final int deltaDays) {
		try {
			final Calendar cal = Calendar.getInstance();
			cal.setTime(DAY.parse(viewDay));
			cal.add(Calendar.DAY_OF_MONTH, deltaDays);
			viewDay = DAY.format(cal.getTime());
			if (viewDay.length() >= 7) {
				viewPeriod = viewDay.substring(0, 7);
			}
			refreshAll();
		}
		catch (Exception e) {
			viewDay = FinanceAttributes.todayYmd();
			viewPeriod = todayPeriod();
			refreshAll();
		}
	}

	private void toggleDayView() {
		dayViewMode = !dayViewMode;
		if (dayViewMode) {
			if (todayPeriod().equals(viewPeriod)) {
				viewDay = FinanceAttributes.todayYmd();
			}
			else {
				viewDay = viewPeriod + "-01";
			}
		}
		refreshAll();
	}

	private static long budgetRemaining(final String period, final FinanceLedgerService.MonthSummary summary) {
		final MapModel map = FinanceLedgerService.preferOpenFinanceMapPublic();
		final String from = period + "-01";
		final String to = period + "-31";
		final List txns = FinanceLedgerService.listTransactions(from, to);
		final List budgets = FinanceLedgerService.listBudgets(period);
		if (budgets.isEmpty()) {
			return 0L;
		}
		long remaining = 0L;
		for (int i = 0; i < budgets.size(); i++) {
			final FinanceLedgerService.FinanceBudget b = (FinanceLedgerService.FinanceBudget) budgets.get(i);
			final long spent = FinanceNodeRef.sumExpenseForBudgetCategory(map, b.categoryNodeId, txns);
			remaining += b.amountCents - spent;
		}
		return remaining;
	}

	private static int txnCountForPeriod(final String period) {
		final String from = period + "-01";
		final String to = period + "-31";
		return FinanceLedgerService.listTransactions(from, to).size();
	}

	private static int txnCountForDay(final String day) {
		return FinanceLedgerService.listTransactions(day, day).size();
	}

	private void reloadAccountCombo() {
		final String selected = selectedRefNodeId(accountCombo);
		final String selectedTo = selectedRefNodeId(accountToCombo);
		accountCombo.removeAllItems();
		accountToCombo.removeAllItems();
		final List accounts = FinanceLedgerService.listAccountRefs();
		for (int i = 0; i < accounts.size(); i++) {
			final FinanceNodeRef.Ref ref = (FinanceNodeRef.Ref) accounts.get(i);
			accountCombo.addItem(ref);
			accountToCombo.addItem(ref);
		}
		selectRefByNodeId(accountCombo, selected);
		selectRefByNodeId(accountToCombo, selectedTo);
		if (selectedTo.length() == 0 && accountToCombo.getItemCount() > 1) {
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
		final String selected = selectedRefNodeId(categoryCombo);
		categoryCombo.removeAllItems();
		categoryCombo.addItem(new FinanceNodeRef.Ref("", TextUtils.getText("finance.category.uncategorized_paren")));
		final String listFlow = FinanceAttributes.FLOW_INCOME.equals(flow)
				? FinanceAttributes.FLOW_INCOME
				: FinanceAttributes.FLOW_EXPENSE;
		final List cats = FinanceLedgerService.listCategoryRefs(listFlow);
		for (int i = 0; i < cats.size(); i++) {
			categoryCombo.addItem(cats.get(i));
		}
		selectRefByNodeId(categoryCombo, selected);
	}

	private void reloadTxnListForDay(final String day) {
		final List txns = FinanceLedgerService.listTransactions(day, day);
		if (txns.isEmpty()) {
			txnList.setListData(new ListRow[] { ListRow.empty(TextUtils.getText("finance.empty.txns_day")) });
			return;
		}
		fillTxnListRows(txns);
	}

	private void reloadTxnList(final String period) {
		final List txns = FinanceLedgerService.listTransactions(period + "-01", period + "-31");
		if (txns.isEmpty()) {
			txnList.setListData(new ListRow[] { ListRow.empty(TextUtils.getText("finance.empty.txns_month")) });
			return;
		}
		fillTxnListRows(txns);
	}

	private void fillTxnListRows(final List txns) {
		final ListRow[] rows = new ListRow[txns.size()];
		for (int i = 0; i < txns.size(); i++) {
			final FinanceLedgerService.FinanceTxn t = (FinanceLedgerService.FinanceTxn) txns.get(i);
			final String sign = FinanceRules.flowSign(t.flow);
			final String flowZh = FinanceRules.flowLabelZh(t.flow);
			final String cat = t.categoryName == null || t.categoryName.length() == 0 ? TextUtils.getText("finance.category.uncategorized") : t.categoryName;
			String extra = t.merchant == null || t.merchant.length() == 0
					? (t.note == null ? "" : t.note)
					: t.merchant;
			if (t.merchant != null && t.merchant.length() > 0 && t.note != null && t.note.length() > 0) {
				extra = t.merchant + " · " + t.note;
			}
			if (FinanceRules.isTransfer(t.flow)) {
				extra = t.accountName + "→" + t.accountTo + (extra.length() == 0 ? "" : " · " + extra);
			}
			final String nodeId = t.node == null ? "" : t.node.createID();
			final String when = t.dateYmd == null ? "" : t.dateYmd;
			rows[i] = new ListRow(nodeId, when + "  " + sign + "¥" + FinanceAttributes.formatYuan(t.amountCents)
					+ "  [" + flowZh + "] " + cat + (extra.length() == 0 ? "" : " · " + extra));
		}
		txnList.setListData(rows);
	}

	private void reloadBudgetList(final String period, final FinanceLedgerService.MonthSummary summary) {
		final MapModel map = FinanceLedgerService.preferOpenFinanceMapPublic();
		final List budgets = dayViewMode
				? FinanceLedgerService.listBudgetsForRange(viewDay, viewDay)
				: FinanceLedgerService.listBudgets(period);
		if (budgets.isEmpty()) {
			budgetList.setListData(new ListRow[] { ListRow.empty(TextUtils.getText("finance.empty.budgets")) });
			return;
		}
		final String from = dayViewMode ? viewDay : period + "-01";
		final String to = dayViewMode ? viewDay : period + "-31";
		final List txns = FinanceLedgerService.listTransactions(from, to);
		final ListRow[] rows = new ListRow[budgets.size()];
		for (int i = 0; i < budgets.size(); i++) {
			final FinanceLedgerService.FinanceBudget b = (FinanceLedgerService.FinanceBudget) budgets.get(i);
			final long spent = FinanceNodeRef.sumExpenseForBudgetCategory(map, b.categoryNodeId, txns);
			final String name = b.categoryName;
			final String range = b.period.equals(b.periodEnd) || b.periodEnd.length() == 0
					? b.period
					: b.period + "~" + b.periodEnd;
			final String nodeId = b.node == null ? "" : b.node.createID();
			rows[i] = new ListRow(nodeId, name + "  " + range + "  ¥" + FinanceAttributes.formatYuan(spent)
					+ " / ¥" + FinanceAttributes.formatYuan(b.amountCents)
					+ TextUtils.format("finance.list.budget_left", FinanceAttributes.formatYuan(b.amountCents - spent)));
		}
		budgetList.setListData(rows);
	}

	private void reloadSubList() {
		final List subs = FinanceLedgerService.listSubscriptions();
		if (subs.isEmpty()) {
			subList.setListData(new ListRow[] {
					ListRow.empty(TextUtils.getText("finance.empty.subscriptions")) });
			return;
		}
		final ListRow[] rows = new ListRow[subs.size()];
		for (int i = 0; i < subs.size(); i++) {
			final FinanceLedgerService.FinanceSubscription s = (FinanceLedgerService.FinanceSubscription) subs.get(i);
			final String nodeId = s.node == null ? "" : s.node.createID();
			final long daily = FinanceRules.dailyAverageCents(s.amountCents, s.cycle);
			final boolean active = FinanceRules.isActiveSubscription(s.status);
			final String cycleZh = FinanceRules.cycleLabelZh(s.cycle);
			rows[i] = new ListRow(nodeId, s.name + "  ¥" + FinanceAttributes.formatYuan(s.amountCents)
					+ TextUtils.format("finance.list.sub_daily", cycleZh, FinanceAttributes.formatYuan(daily))
					+ TextUtils.format("finance.list.sub_paid", Integer.valueOf(s.paymentCount))
					+ (s.startYmd.length() == 0 ? "" : TextUtils.format("finance.list.sub_start", s.startYmd))
					+ (s.endYmd.length() == 0 ? "" : TextUtils.format("finance.list.sub_end", s.endYmd))
					+ (s.nextYmd.length() == 0 ? "" : TextUtils.format("finance.list.sub_next", s.nextYmd))
					+ (active ? "" : "  [" + s.status + "]"));
		}
		subList.setListData(rows);
	}

	private void refreshDailySpendStrip() {
		final List subs = FinanceLedgerService.listSubscriptions();
		final int active = FinanceRules.countActiveSubscriptions(subs);
		final long daily = FinanceRules.totalDailySpendCents(subs);
		if (active == 0) {
			dailySpendLabel.setText(TextUtils.getText("finance.daily.none"));
			dailySpendLabel.setForeground(DocearUiTheme.TEXT_FAINT);
			return;
		}
		dailySpendLabel.setText(TextUtils.format("finance.daily.summary", FinanceAttributes.formatYuan(daily), Integer.valueOf(active)));
		dailySpendLabel.setForeground(DocearUiTheme.ACCENT_DEEP);
	}

	private void reloadCouponList() {
		final List coupons = FinanceLedgerService.listCoupons();
		if (coupons.isEmpty()) {
			couponList.setListData(new ListRow[] { ListRow.empty(TextUtils.getText("finance.empty.coupons")) });
			return;
		}
		final ListRow[] rows = new ListRow[coupons.size()];
		for (int i = 0; i < coupons.size(); i++) {
			final FinanceLedgerService.FinanceCoupon c = (FinanceLedgerService.FinanceCoupon) coupons.get(i);
			final String nodeId = c.node == null ? "" : c.node.createID();
			final boolean used = "used".equalsIgnoreCase(c.status);
			rows[i] = new ListRow(nodeId, c.name + "  ¥" + FinanceAttributes.formatYuan(c.amountCents)
					+ (c.expiresYmd.length() == 0 ? "" : TextUtils.format("finance.list.coupon_expires", c.expiresYmd))
					+ (used ? TextUtils.getText("finance.list.coupon_used") : ""));
		}
		couponList.setListData(rows);
	}

	private void saveTransaction() {
		try {
			final long cents = FinanceAttributes.parseYuanToCents(amountField.getText());
			if (!FinanceRules.isValidAmountCents(cents)) {
				JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.invalid_amount"), TextUtils.getText("finance.dialog.entry_title"), JOptionPane.WARNING_MESSAGE);
				return;
			}
			final String flow = flowFromCombo();
			final String cat = selectedCategoryNodeId();
			final String acc = selectedAccountNodeId(accountCombo);
			final String accTo = selectedAccountNodeId(accountToCombo);
			if (FinanceAttributes.FLOW_TRANSFER.equals(flow)) {
				if (acc.length() == 0 || accTo.length() == 0) {
					JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.transfer_need_accounts"), TextUtils.getText("finance.dialog.entry_title"),
							JOptionPane.WARNING_MESSAGE);
					return;
				}
				if (acc.equals(accTo)) {
					JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.transfer_same_account"), TextUtils.getText("finance.dialog.entry_title"), JOptionPane.WARNING_MESSAGE);
					return;
				}
			}
			final DateParse dateParse = parseDate(dateField.getText());
			if (!dateParse.valid) {
				JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.bad_datetime"), TextUtils.getText("finance.dialog.entry_title"),
						JOptionPane.WARNING_MESSAGE);
				return;
			}
			final String merchant = merchantField.getText() == null ? "" : merchantField.getText().trim();
			final String note = noteField.getText() == null ? "" : noteField.getText().trim();
			final NodeModel node = FinanceLedgerService.addTransaction(
					FinanceAttributes.formatYuan(cents),
					flow,
					dateParse.ymd,
					cat,
					acc,
					FinanceAttributes.FLOW_TRANSFER.equals(flow) ? accTo : "",
					merchant,
					note);
			if (node == null) {
				JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.save_failed_map"), TextUtils.getText("finance.dialog.entry_title"),
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			amountField.setText("");
			merchantField.setText("");
			noteField.setText("");
			if (dateParse.ymd.length() >= 10) {
				viewDay = FinanceAttributes.datePart(dateParse.ymd);
			}
			if (dateParse.ymd.length() >= 7) {
				viewPeriod = dateParse.ymd.substring(0, 7);
			}
			refreshAll();
			setStatus(TextUtils.format("finance.status.saved", FinanceRules.flowLabelZh(flow), FinanceAttributes.formatYuan(cents)), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, TextUtils.format("finance.dialog.save_failed", ex.getMessage()), TextUtils.getText("finance.dialog.entry_title"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void promptAddAccount() {
		final String name = JOptionPane.showInputDialog(this, TextUtils.getText("finance.dialog.account_prompt"), "");
		if (name == null || name.trim().length() == 0) {
			return;
		}
		try {
			FinanceLedgerService.addAccount(name.trim());
			refreshAll();
			setStatus(TextUtils.format("finance.status.account_added", name.trim()), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, TextUtils.format("finance.dialog.account_failed", ex.getMessage()), TextUtils.getText("finance.label.account"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void deleteTransactionRow(final ListRow row) {
		if (row == null || row.placeholder || row.nodeId.length() == 0) {
			return;
		}
		final int ok = JOptionPane.showConfirmDialog(this, TextUtils.format("finance.dialog.confirm_delete", row.label), TextUtils.getText("finance.menu.delete"),
				JOptionPane.OK_CANCEL_OPTION);
		if (ok != JOptionPane.OK_OPTION) {
			return;
		}
		if (!FinanceLedgerService.deleteFinanceNode(row.nodeId)) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.delete_failed"), TextUtils.getText("finance.menu.delete"), JOptionPane.ERROR_MESSAGE);
			return;
		}
		refreshAll();
		setStatus(TextUtils.getText("finance.status.deleted"), false);
	}

	private void editTransaction(final ListRow row) {
		if (row == null || row.placeholder || row.nodeId.length() == 0) {
			return;
		}
		final FinanceLedgerService.FinanceTxn txn = FinanceLedgerService.getTransactionByNodeId(row.nodeId);
		if (txn == null) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.cannot_read_txn"), TextUtils.getText("finance.menu.edit"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		final JComboBox flowBox = new JComboBox(new String[] { TextUtils.getText("finance.flow.expense"), TextUtils.getText("finance.flow.income"), TextUtils.getText("finance.flow.transfer"), TextUtils.getText("finance.flow.borrow"), TextUtils.getText("finance.flow.lend"), TextUtils.getText("finance.flow.credit") });
		flowBox.setSelectedIndex(flowComboIndex(txn.flow));
		final JTextField amountLocal = new JTextField(FinanceAttributes.formatYuan(txn.amountCents), 10);
		final JTextField dateLocal = new JTextField(
				txn.dateYmd == null || txn.dateYmd.length() == 0 ? FinanceAttributes.todayDateTime() : txn.dateYmd, 16);
		final JTextField merchantLocal = new JTextField(txn.merchant, 16);
		final JTextField noteLocal = new JTextField(txn.note, 16);
		final JComboBox categoryLocal = new JComboBox();
		categoryLocal.addItem(new FinanceNodeRef.Ref("", TextUtils.getText("finance.category.uncategorized_paren")));
		final List cats = FinanceLedgerService.listCategoryRefs("all");
		for (int i = 0; i < cats.size(); i++) {
			categoryLocal.addItem(cats.get(i));
		}
		selectRefByNodeId(categoryLocal, txn.categoryNodeId);
		final JComboBox accountLocal = new JComboBox();
		final JComboBox accountToLocal = new JComboBox();
		final List accounts = FinanceLedgerService.listAccountRefs();
		for (int i = 0; i < accounts.size(); i++) {
			final FinanceNodeRef.Ref ref = (FinanceNodeRef.Ref) accounts.get(i);
			accountLocal.addItem(ref);
			accountToLocal.addItem(ref);
		}
		selectRefByNodeId(accountLocal, txn.accountNodeId);
		selectRefByNodeId(accountToLocal, txn.accountToNodeId);
		final JPanel form = formPanel(
				labeled(TextUtils.getText("finance.label.flow"), flowBox),
				labeled(TextUtils.getText("finance.label.amount_yuan"), amountLocal),
				labeled(TextUtils.getText("finance.label.datetime"), dateLocal),
				labeled(TextUtils.getText("finance.label.category"), categoryLocal),
				labeled(TextUtils.getText("finance.label.account"), accountLocal),
				labeled(TextUtils.getText("finance.label.account_to"), accountToLocal),
				labeled(TextUtils.getText("finance.label.merchant"), merchantLocal),
				labeled(TextUtils.getText("finance.label.note"), noteLocal));
		final int option = JOptionPane.showConfirmDialog(this, form, TextUtils.getText("finance.dialog.edit_txn_title"), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (option != JOptionPane.OK_OPTION) {
			return;
		}
		try {
			final String flow = flowFromComboIndex(flowBox.getSelectedIndex());
			final String cat = selectedRefNodeId(categoryLocal);
			final String acc = selectedRefNodeId(accountLocal);
			final String accTo = selectedRefNodeId(accountToLocal);
			if (FinanceAttributes.FLOW_TRANSFER.equals(flow) && (acc.length() == 0 || accTo.length() == 0)) {
				JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.transfer_need_accounts"), TextUtils.getText("finance.menu.edit"), JOptionPane.WARNING_MESSAGE);
				return;
			}
			final DateParse dateParse = parseDate(dateLocal.getText());
			if (!dateParse.valid) {
				JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.bad_datetime"), TextUtils.getText("finance.menu.edit"),
						JOptionPane.WARNING_MESSAGE);
				return;
			}
			final NodeModel node = FinanceLedgerService.updateTransaction(
					row.nodeId,
					amountLocal.getText(),
					flow,
					dateParse.ymd,
					cat,
					acc,
					FinanceAttributes.FLOW_TRANSFER.equals(flow) ? accTo : "",
					merchantLocal.getText(),
					noteLocal.getText());
			if (node == null) {
				JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.edit_failed"), TextUtils.getText("finance.menu.edit"), JOptionPane.ERROR_MESSAGE);
				return;
			}
			refreshAll();
			setStatus(TextUtils.getText("finance.status.txn_edited"), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, TextUtils.format("finance.dialog.edit_failed_ex", ex.getMessage()), TextUtils.getText("finance.menu.edit"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void editBudget(final ListRow row) {
		if (row == null || row.placeholder || row.nodeId.length() == 0) {
			return;
		}
		final FinanceLedgerService.FinanceBudget budget = FinanceLedgerService.getBudgetByNodeId(row.nodeId);
		if (budget == null) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.cannot_read_budget"), TextUtils.getText("finance.menu.edit"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		final JComboBox categoryBox = new JComboBox();
		categoryBox.addItem(new FinanceNodeRef.Ref(FinanceNodeRef.TOTAL_BUDGET_NODE_ID, FinanceRules.TOTAL_BUDGET_CATEGORY));
		final List cats = FinanceLedgerService.listCategoryRefs(FinanceAttributes.FLOW_EXPENSE);
		for (int i = 0; i < cats.size(); i++) {
			categoryBox.addItem(cats.get(i));
		}
		selectRefByNodeId(categoryBox, budget.categoryNodeId);
		final JTextField startField = new JTextField(
				budget.period.length() == 0 ? FinanceAttributes.todayYmd() : budget.period, 12);
		final JTextField endField = new JTextField(
				budget.periodEnd.length() == 0 ? budget.period : budget.periodEnd, 12);
		final JTextField amountFieldLocal = new JTextField(FinanceAttributes.formatYuan(budget.amountCents), 10);
		final JPanel form = formPanel(
				labeled(TextUtils.getText("finance.label.category"), categoryBox),
				labeled(TextUtils.getText("finance.label.start_date"), startField),
				labeled(TextUtils.getText("finance.label.end_date"), endField),
				labeled(TextUtils.getText("finance.label.budget_amount"), amountFieldLocal));
		final int option = JOptionPane.showConfirmDialog(this, form, TextUtils.getText("finance.dialog.edit_budget_title"), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (option != JOptionPane.OK_OPTION) {
			return;
		}
		try {
			final DateParse start = parseDate(startField.getText());
			final DateParse end = parseDate(endField.getText());
			if (!start.valid || !end.valid) {
				JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.bad_date"), TextUtils.getText("finance.tab.budgets"), JOptionPane.WARNING_MESSAGE);
				return;
			}
			final long cents = FinanceAttributes.parseYuanToCents(amountFieldLocal.getText());
			final String catRef = selectedRefNodeId(categoryBox);
			FinanceLedgerService.deleteFinanceNode(row.nodeId);
			FinanceLedgerService.setBudget(FinanceAttributes.datePart(start.ymd), FinanceAttributes.datePart(end.ymd),
					catRef.length() == 0 ? FinanceRules.TOTAL_BUDGET_CATEGORY : catRef, cents);
			refreshAll();
			setStatus(TextUtils.getText("finance.status.budget_edited"), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, TextUtils.format("finance.dialog.budget_failed", ex.getMessage()), TextUtils.getText("finance.tab.budgets"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void editSubscription(final ListRow row) {
		if (row == null || row.placeholder || row.nodeId.length() == 0) {
			return;
		}
		final FinanceLedgerService.FinanceSubscription sub = FinanceLedgerService.getSubscriptionByNodeId(row.nodeId);
		if (sub == null) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.cannot_read_sub"), TextUtils.getText("finance.menu.edit"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		final JTextField nameField = new JTextField(sub.name, 18);
		final JTextField amountFieldLocal = new JTextField(FinanceAttributes.formatYuan(sub.amountCents), 10);
		final JComboBox cycleBox = new JComboBox(new String[] { TextUtils.getText("finance.cycle.monthly"), TextUtils.getText("finance.cycle.yearly"), TextUtils.getText("finance.cycle.weekly"), TextUtils.getText("finance.cycle.daily") });
		final String cycleZh = FinanceRules.cycleLabelZh(sub.cycle);
		cycleBox.setSelectedItem(cycleZh);
		final JTextField startField = new JTextField(sub.startYmd, 12);
		final JTextField endField = new JTextField(sub.endYmd, 12);
		final JTextField nextField = new JTextField(sub.nextYmd, 12);
		final JComboBox accountBox = new JComboBox();
		final List accounts = FinanceLedgerService.listAccountRefs();
		for (int i = 0; i < accounts.size(); i++) {
			accountBox.addItem(accounts.get(i));
		}
		selectRefByNodeId(accountBox, sub.accountNodeId);
		final JPanel form = formPanel(
				labeled(TextUtils.getText("finance.label.name"), nameField),
				labeled(TextUtils.getText("finance.label.period_amount"), amountFieldLocal),
				labeled(TextUtils.getText("finance.label.cycle"), cycleBox),
				labeled(TextUtils.getText("finance.label.start_date"), startField),
				labeled(TextUtils.getText("finance.label.end_date_optional"), endField),
				labeled(TextUtils.getText("finance.label.next_payment"), nextField),
				labeled(TextUtils.getText("finance.label.debit_account"), accountBox));
		final int option = JOptionPane.showConfirmDialog(this, form, TextUtils.getText("finance.dialog.edit_sub_title"), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (option != JOptionPane.OK_OPTION) {
			return;
		}
		try {
			final String name = nameField.getText() == null ? "" : nameField.getText().trim();
			if (name.length() == 0) {
				return;
			}
			final long cents = FinanceAttributes.parseYuanToCents(amountFieldLocal.getText());
			final String cycleValue = FinanceRules.normalizeCycle(selectedComboText(cycleBox));
			final DateParse start = parseOptionalDate(startField.getText());
			final DateParse end = parseOptionalDate(endField.getText());
			final DateParse next = parseOptionalDate(nextField.getText());
			if (FinanceLedgerService.updateSubscription(
					row.nodeId,
					name,
					cents,
					cycleValue,
					next.valid ? FinanceAttributes.datePart(next.ymd) : FinanceRules.nextDateForCycle(
							start.valid ? FinanceAttributes.datePart(start.ymd) : FinanceAttributes.todayYmd(),
							cycleValue),
					sub.status,
					selectedRefNodeId(accountBox),
					sub.note,
					start.valid ? FinanceAttributes.datePart(start.ymd) : "",
					end.valid ? FinanceAttributes.datePart(end.ymd) : "") == null) {
				JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.edit_failed"), TextUtils.getText("finance.tab.subscriptions"), JOptionPane.ERROR_MESSAGE);
				return;
			}
			refreshAll();
			setStatus(TextUtils.format("finance.status.sub_edited", name), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, TextUtils.format("finance.dialog.sub_failed", ex.getMessage()), TextUtils.getText("finance.tab.subscriptions"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void recordSubscriptionPayment(final ListRow row) {
		if (row == null || row.placeholder || row.nodeId.length() == 0) {
			return;
		}
		final FinanceLedgerService.FinanceSubscription sub = FinanceLedgerService.getSubscriptionByNodeId(row.nodeId);
		if (sub == null) {
			return;
		}
		final String defaultDate = sub.nextYmd.length() > 0 ? sub.nextYmd : FinanceAttributes.todayYmd();
		final String payDate = JOptionPane.showInputDialog(this,
				TextUtils.getText("finance.dialog.pay_prompt"), defaultDate);
		if (payDate == null) {
			return;
		}
		final DateParse parsed = parseOptionalDate(payDate);
		if (!parsed.valid) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.bad_date"), TextUtils.getText("finance.dialog.pay_title"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		final NodeModel node = FinanceLedgerService.recordSubscriptionPayment(row.nodeId,
				FinanceAttributes.datePart(parsed.ymd));
		if (node == null) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.pay_failed"), TextUtils.getText("finance.dialog.pay_title"),
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		refreshAll();
		setStatus(TextUtils.format("finance.status.sub_paid", sub.name, FinanceAttributes.formatYuan(sub.amountCents)), false);
	}

	private void editCoupon(final ListRow row) {
		if (row == null || row.placeholder || row.nodeId.length() == 0) {
			return;
		}
		final List coupons = FinanceLedgerService.listCoupons();
		FinanceLedgerService.FinanceCoupon coupon = null;
		for (int i = 0; i < coupons.size(); i++) {
			final FinanceLedgerService.FinanceCoupon c = (FinanceLedgerService.FinanceCoupon) coupons.get(i);
			if (c.node != null && row.nodeId.equals(c.node.createID())) {
				coupon = c;
				break;
			}
		}
		if (coupon == null) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.cannot_read_coupon"), TextUtils.getText("finance.menu.edit"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		final JTextField nameField = new JTextField(coupon.name, 18);
		final JTextField amountFieldLocal = new JTextField(FinanceAttributes.formatYuan(coupon.amountCents), 10);
		final JTextField expiresField = new JTextField(coupon.expiresYmd, 12);
		final JPanel form = formPanel(
				labeled(TextUtils.getText("finance.label.name"), nameField),
				labeled(TextUtils.getText("finance.label.face_value"), amountFieldLocal),
				labeled(TextUtils.getText("finance.label.expires_optional"), expiresField));
		final int option = JOptionPane.showConfirmDialog(this, form, TextUtils.getText("finance.dialog.edit_coupon_title"), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (option != JOptionPane.OK_OPTION) {
			return;
		}
		try {
			final String name = nameField.getText() == null ? "" : nameField.getText().trim();
			if (name.length() == 0) {
				return;
			}
			final long cents = FinanceAttributes.parseYuanToCents(amountFieldLocal.getText());
			final DateParse expires = parseOptionalDate(expiresField.getText());
			FinanceLedgerService.upsertCoupon(name, cents, expires.valid ? FinanceAttributes.datePart(expires.ymd) : "",
					coupon.status, "", coupon.note);
			refreshAll();
			setStatus(TextUtils.format("finance.status.coupon_edited", name), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, TextUtils.format("finance.dialog.coupon_failed", ex.getMessage()), TextUtils.getText("finance.report.coupons"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private static int flowComboIndex(final String flow) {
		if (FinanceAttributes.FLOW_INCOME.equals(flow)) {
			return 1;
		}
		if (FinanceAttributes.FLOW_TRANSFER.equals(flow)) {
			return 2;
		}
		if (FinanceAttributes.FLOW_BORROW.equals(flow)) {
			return 3;
		}
		if (FinanceAttributes.FLOW_LEND.equals(flow)) {
			return 4;
		}
		if (FinanceAttributes.FLOW_CREDIT.equals(flow)) {
			return 5;
		}
		return 0;
	}

	private static String flowFromComboIndex(final int index) {
		if (index == 1) {
			return FinanceAttributes.FLOW_INCOME;
		}
		if (index == 2) {
			return FinanceAttributes.FLOW_TRANSFER;
		}
		if (index == 3) {
			return FinanceAttributes.FLOW_BORROW;
		}
		if (index == 4) {
			return FinanceAttributes.FLOW_LEND;
		}
		if (index == 5) {
			return FinanceAttributes.FLOW_CREDIT;
		}
		return FinanceAttributes.FLOW_EXPENSE;
	}

	private void deleteSelected() {
		final ListRow row = selectedListRow();
		if (row == null || row.placeholder || row.nodeId.length() == 0) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.select_row"), TextUtils.getText("finance.menu.delete"),
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final int ok = JOptionPane.showConfirmDialog(this, TextUtils.format("finance.dialog.confirm_delete", row.label), TextUtils.getText("finance.menu.delete"),
				JOptionPane.OK_CANCEL_OPTION);
		if (ok != JOptionPane.OK_OPTION) {
			return;
		}
		if (!FinanceLedgerService.deleteFinanceNode(row.nodeId)) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.delete_failed"), TextUtils.getText("finance.menu.delete"), JOptionPane.ERROR_MESSAGE);
			return;
		}
		refreshAll();
		setStatus(TextUtils.getText("finance.status.deleted"), false);
	}

	private void markSelectedCouponUsed() {
		final Object value = couponList.getSelectedValue();
		if (!(value instanceof ListRow) || ((ListRow) value).placeholder) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.select_coupon"), TextUtils.getText("finance.report.coupons"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final String nodeId = ((ListRow) value).nodeId;
		if (FinanceLedgerService.markCouponUsed(nodeId, true) == null) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.mark_failed"), TextUtils.getText("finance.report.coupons"), JOptionPane.ERROR_MESSAGE);
			return;
		}
		refreshAll();
		setStatus(TextUtils.getText("finance.status.coupon_marked"), false);
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
		final String name = JOptionPane.showInputDialog(this, TextUtils.getText("finance.dialog.category_prompt"), "");
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
			setStatus(TextUtils.format("finance.status.category_added", name.trim()), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, TextUtils.format("finance.dialog.add_category_failed", ex.getMessage()), TextUtils.getText("finance.label.category"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void promptAddBudget() {
		final JComboBox categoryBox = new JComboBox();
		categoryBox.addItem(new FinanceNodeRef.Ref(FinanceNodeRef.TOTAL_BUDGET_NODE_ID, FinanceRules.TOTAL_BUDGET_CATEGORY));
		final List cats = FinanceLedgerService.listCategoryRefs(FinanceAttributes.FLOW_EXPENSE);
		for (int i = 0; i < cats.size(); i++) {
			categoryBox.addItem(cats.get(i));
		}
		if (selectedCategoryNodeId().length() > 0) {
			selectRefByNodeId(categoryBox, selectedCategoryNodeId());
		}
		final String monthStart = dayViewMode ? viewDay : viewPeriod + "-01";
		final String monthEnd = dayViewMode ? viewDay : viewPeriod + "-31";
		final JTextField startField = new JTextField(monthStart, 12);
		final JTextField endField = new JTextField(monthEnd, 12);
		final JTextField amountFieldLocal = new JTextField("1000", 10);
		final JPanel form = formPanel(
				labeled(TextUtils.getText("finance.label.category"), categoryBox),
				labeled(TextUtils.getText("finance.label.start_date"), startField),
				labeled(TextUtils.getText("finance.label.end_date"), endField),
				labeled(TextUtils.getText("finance.label.budget_amount"), amountFieldLocal));
		final int option = JOptionPane.showConfirmDialog(this, form, TextUtils.getText("finance.dialog.add_budget_title"),
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (option != JOptionPane.OK_OPTION) {
			return;
		}
		try {
			final long cents = FinanceAttributes.parseYuanToCents(amountFieldLocal.getText());
			final DateParse start = parseDate(startField.getText());
			final DateParse end = parseDate(endField.getText());
			if (!start.valid || !end.valid) {
				JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.bad_datetime"), TextUtils.getText("finance.tab.budgets"),
						JOptionPane.WARNING_MESSAGE);
				return;
			}
			final String catRef = selectedRefNodeId(categoryBox);
			FinanceLedgerService.setBudget(FinanceAttributes.datePart(start.ymd), FinanceAttributes.datePart(end.ymd),
					catRef.length() == 0 ? FinanceRules.TOTAL_BUDGET_CATEGORY : catRef, cents);
			refreshAll();
			setStatus(TextUtils.getText("finance.status.budget_set"), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, TextUtils.format("finance.dialog.add_budget_failed", ex.getMessage()), TextUtils.getText("finance.tab.budgets"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void promptAddSubscription() {
		final JTextField nameField = new JTextField(TextUtils.getText("finance.default.sub_name"), 18);
		final JTextField amountFieldLocal = new JTextField("3000", 10);
		final JComboBox cycleBox = new JComboBox(new String[] { TextUtils.getText("finance.cycle.monthly"), TextUtils.getText("finance.cycle.yearly"), TextUtils.getText("finance.cycle.weekly"), TextUtils.getText("finance.cycle.daily") });
		final JTextField startField = new JTextField(FinanceAttributes.todayYmd(), 12);
		final JTextField endField = new JTextField("", 12);
		final JLabel preview = DocearUiTheme.mutedLabel(TextUtils.getText("finance.daily.preview_empty"));
		preview.setFont(DocearUiTheme.font(12f));
		final Runnable updatePreview = new Runnable() {
			public void run() {
				try {
					final long cents = FinanceAttributes.parseYuanToCents(amountFieldLocal.getText());
					final String cycle = FinanceRules.normalizeCycle(selectedComboText(cycleBox));
					final long daily = FinanceRules.dailyAverageCents(cents, cycle);
					preview.setText(TextUtils.format("finance.daily.preview", FinanceAttributes.formatYuan(daily),
							FinanceRules.cycleLabelZh(cycle),
							Integer.valueOf(FinanceRules.cycleDays(cycle))));
				}
				catch (Exception e) {
					preview.setText(TextUtils.getText("finance.daily.preview_empty"));
				}
			}
		};
		amountFieldLocal.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(final javax.swing.event.DocumentEvent e) {
				updatePreview.run();
			}

			public void removeUpdate(final javax.swing.event.DocumentEvent e) {
				updatePreview.run();
			}

			public void changedUpdate(final javax.swing.event.DocumentEvent e) {
				updatePreview.run();
			}
		});
		cycleBox.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				updatePreview.run();
			}
		});
		updatePreview.run();
		final JPanel form = formPanel(
				labeled(TextUtils.getText("finance.label.sub_name"), nameField),
				labeled(TextUtils.getText("finance.label.period_amount"), amountFieldLocal),
				labeled(TextUtils.getText("finance.label.cycle"), cycleBox),
				labeled(TextUtils.getText("finance.label.start_date"), startField),
				labeled(TextUtils.getText("finance.label.end_date_optional"), endField));
		preview.setAlignmentX(LEFT_ALIGNMENT);
		form.add(Box.createVerticalStrut(8));
		form.add(preview);
		final int option = JOptionPane.showConfirmDialog(this, form, TextUtils.getText("finance.menu.add_subscription"), JOptionPane.OK_CANCEL_OPTION,
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
			final String cycleValue = FinanceRules.normalizeCycle(selectedComboText(cycleBox));
			final DateParse start = parseOptionalDate(startField.getText());
			final DateParse end = parseOptionalDate(endField.getText());
			final String startYmd = start.valid ? FinanceAttributes.datePart(start.ymd) : FinanceAttributes.todayYmd();
			FinanceLedgerService.upsertSubscription(
					name,
					cents,
					cycleValue,
					FinanceRules.nextDateForCycle(startYmd, cycleValue),
					"active",
					selectedAccountNodeId(accountCombo),
					"",
					startYmd,
					end.valid ? FinanceAttributes.datePart(end.ymd) : "");
			refreshAll();
			setStatus(TextUtils.format("finance.status.sub_added", name,
					FinanceAttributes.formatYuan(FinanceRules.dailyAverageCents(cents, cycleValue))), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, TextUtils.format("finance.dialog.add_sub_failed", ex.getMessage()), TextUtils.getText("finance.tab.subscriptions"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void promptAddCoupon() {
		final JTextField nameField = new JTextField(TextUtils.getText("finance.default.coupon_name"), 18);
		final JTextField amountFieldLocal = new JTextField("10", 10);
		final JTextField expiresField = new JTextField(plusMonths(FinanceAttributes.todayYmd(), 1), 12);
		final JPanel form = formPanel(
				labeled(TextUtils.getText("finance.label.name"), nameField),
				labeled(TextUtils.getText("finance.label.face_value"), amountFieldLocal),
				labeled(TextUtils.getText("finance.label.expires"), expiresField));
		final int option = JOptionPane.showConfirmDialog(this, form, TextUtils.getText("finance.dialog.add_coupon_title"), JOptionPane.OK_CANCEL_OPTION,
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
			setStatus(TextUtils.format("finance.status.coupon_added", name), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, TextUtils.format("finance.dialog.add_coupon_failed", ex.getMessage()), TextUtils.getText("finance.report.coupons"), JOptionPane.ERROR_MESSAGE);
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
				JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.report_not_ready"), TextUtils.getText("finance.tab.reports"), JOptionPane.WARNING_MESSAGE);
				return;
			}
			final ReportViewModel model = FinanceReportEngine.generateView(reportId, range[0], range[1]);
			viewport.show(model);
			setStatus(TextUtils.format("finance.status.report_opened", viewPeriod), false);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, TextUtils.format("finance.dialog.report_failed", ex.getMessage()), TextUtils.getText("finance.tab.reports"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void openFinanceMap() {
		try {
			final MapModel map = FinanceLedgerService.ensureFinanceMap();
			if (map == null) {
				JOptionPane.showMessageDialog(this, TextUtils.getText("finance.dialog.open_ledger_failed"), TextUtils.getText("finance.dialog.finance_title"), JOptionPane.ERROR_MESSAGE);
				return;
			}
			FinanceLedgerService.activateFinanceMapView(map);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, TextUtils.format("finance.dialog.open_ledger_failed_ex", ex.getMessage()), TextUtils.getText("finance.dialog.finance_title"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void focusNode(final String nodeId) {
		if (nodeId == null || nodeId.length() == 0) {
			return;
		}
		if (!FinanceLedgerService.focusFinanceNode(nodeId)) {
			LogUtils.warn("Focus finance node failed: " + nodeId);
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

	private static String selectedRefNodeId(final JComboBox combo) {
		final Object v = combo.getSelectedItem();
		if (v instanceof FinanceNodeRef.Ref) {
			return ((FinanceNodeRef.Ref) v).nodeId;
		}
		return "";
	}

	private String selectedCategoryNodeId() {
		return selectedRefNodeId(categoryCombo);
	}

	private static String selectedAccountNodeId(final JComboBox combo) {
		return selectedRefNodeId(combo);
	}

	private static void selectRefByNodeId(final JComboBox combo, final String nodeId) {
		if (combo == null || nodeId == null) {
			return;
		}
		for (int i = 0; i < combo.getItemCount(); i++) {
			final Object item = combo.getItemAt(i);
			if (item instanceof FinanceNodeRef.Ref && nodeId.equals(((FinanceNodeRef.Ref) item).nodeId)) {
				combo.setSelectedIndex(i);
				return;
			}
		}
	}

	private static DateParse parseDate(final String text) {
		if (text == null || text.trim().length() == 0) {
			return DateParse.of(FinanceAttributes.todayDateTime(), true);
		}
		return parseOptionalDate(text);
	}

	/** Empty input → invalid (for optional end dates). Non-empty must parse. */
	private static DateParse parseOptionalDate(final String text) {
		if (text == null || text.trim().length() == 0) {
			return DateParse.of("", false);
		}
		final String s = text.trim();
		try {
			DATETIME.setLenient(false);
			return DateParse.of(DATETIME.format(DATETIME.parse(s)), true);
		}
		catch (Exception e) {
		}
		try {
			DAY.setLenient(false);
			return DateParse.of(DAY.format(DAY.parse(s)) + " 00:00", true);
		}
		catch (Exception e) {
			return DateParse.of("", false);
		}
	}

	private static String todayPeriod() {
		final String today = FinanceAttributes.todayYmd();
		return today.length() >= 7 ? today.substring(0, 7) : today;
	}

	private String[] monthRange() {
		if (dayViewMode) {
			return new String[] { viewDay, viewDay };
		}
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
