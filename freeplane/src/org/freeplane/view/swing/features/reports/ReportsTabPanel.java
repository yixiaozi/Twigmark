package org.freeplane.view.swing.features.reports;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.factory.IconStoreFactory;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.usagestats.UsageStatsReportService;

/**
 * Left sidebar tab: pick a report → show charts in the mind-map viewport.
 */
public class ReportsTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

	private final JLabel statusLabel = new JLabel("点选报表 → 打开/切换 Tab");
	private final JComboBox rangeCombo = new JComboBox();
	private final JTextField startField = new JTextField(10);
	private final JTextField endField = new JTextField(10);
	private final JTextField includeField = new JTextField();
	private final JTextField excludeField = new JTextField();
	private final JPanel customRangePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
	private final DefaultListModel listModel = new DefaultListModel();
	private final JList reportList = new JList(listModel);
	private volatile int statusGeneration;
	private boolean suppressSelectionEvent;

	public ReportsTabPanel() {
		super(new BorderLayout(8, 8));
		DocearUiTheme.styleCanvas(this);
		setBorder(DocearUiTheme.pageBorder());
		buildUi();
		wireEvents();
		reloadCatalog();
		SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_REPORTS, ReportCatalog.all().size());
	}

	private void buildUi() {
		final JPanel north = new JPanel();
		north.setOpaque(false);
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
		statusLabel.setFont(DocearUiTheme.font(11f));
		statusLabel.setForeground(DocearUiTheme.TEXT_MUTED);
		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		north.add(statusLabel);

		final JPanel rangeRow = new JPanel(new BorderLayout(4, 0));
		rangeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		rangeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		final JLabel rangeLabel = new JLabel("时间");
		rangeCombo.addItem("今天");
		rangeCombo.addItem("昨天");
		rangeCombo.addItem("本周");
		rangeCombo.addItem("近7天");
		rangeCombo.addItem("本月");
		rangeCombo.addItem("近30天");
		rangeCombo.addItem("自定义…");
		rangeCombo.setSelectedIndex(ReportTimeRange.PRESET_THIS_WEEK);
		rangeRow.add(rangeLabel, BorderLayout.WEST);
		rangeRow.add(rangeCombo, BorderLayout.CENTER);
		north.add(rangeRow);

		customRangePanel.setVisible(false);
		customRangePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		customRangePanel.add(new JLabel("从"));
		customRangePanel.add(startField);
		customRangePanel.add(new JLabel("到"));
		customRangePanel.add(endField);
		final Calendar cal = Calendar.getInstance();
		endField.setText(DAY.format(cal.getTime()));
		cal.add(Calendar.DAY_OF_MONTH, -6);
		startField.setText(DAY.format(cal.getTime()));
		north.add(customRangePanel);

		final JPanel filterRow = new JPanel(new GridLayout(2, 1, 0, 2));
		filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		filterRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
		final JPanel includeRow = new JPanel(new BorderLayout(4, 0));
		includeField.setToolTipText("对应原版报表「搜索」：只保留包含该关键词的项");
		includeRow.add(new JLabel("包含"), BorderLayout.WEST);
		includeRow.add(includeField, BorderLayout.CENTER);
		final JPanel excludeRow = new JPanel(new BorderLayout(4, 0));
		excludeField.setToolTipText("对应原版报表「排除」：去掉包含该关键词的项");
		excludeRow.add(new JLabel("排除"), BorderLayout.WEST);
		excludeRow.add(excludeField, BorderLayout.CENTER);
		filterRow.add(includeRow);
		filterRow.add(excludeRow);
		north.add(filterRow);
		add(north, BorderLayout.NORTH);

		reportList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		reportList.setFixedCellHeight(58);
		reportList.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getListCellRendererComponent(final JList list, final Object value, final int index,
			        final boolean isSelected, final boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof ReportDefinition) {
					final ReportDefinition def = (ReportDefinition) value;
					setText("<html><b>" + escape(def.title) + "</b><br><font color='#0F766E' size='2'>"
					        + escape(def.decision) + "</font><br><font color='#64748B' size='2'>"
					        + escape(def.description) + "</font></html>");
					setIcon(loadIcon(def.iconName));
					setVerticalTextPosition(SwingConstants.CENTER);
					setHorizontalTextPosition(SwingConstants.RIGHT);
					setIconTextGap(8);
				}
				setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
				return this;
			}
		});
		final JScrollPane scroll = new JScrollPane(reportList);
		scroll.setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(), "报表（点选 → 打开/切换）"));
		add(scroll, BorderLayout.CENTER);
		setPreferredSize(new Dimension(280, 400));
	}

	private void wireEvents() {
		rangeCombo.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				customRangePanel.setVisible(rangeCombo.getSelectedIndex() == ReportTimeRange.PRESET_CUSTOM);
				revalidate();
			}
		});
		reportList.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(final ListSelectionEvent e) {
				if (e.getValueIsAdjusting() || suppressSelectionEvent) {
					return;
				}
				if (reportList.getSelectedIndex() >= 0) {
					runSelected();
				}
			}
		});
	}

	private void reloadCatalog() {
		suppressSelectionEvent = true;
		try {
			listModel.clear();
			final List all = ReportCatalog.all();
			for (int i = 0; i < all.size(); i++) {
				listModel.addElement(all.get(i));
			}
			SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_REPORTS, all.size());
			if (!all.isEmpty()) {
				reportList.setSelectedIndex(0);
			}
		}
		finally {
			suppressSelectionEvent = false;
		}
	}

	private void runSelected() {
		final Object value = reportList.getSelectedValue();
		if (!(value instanceof ReportDefinition)) {
			statusLabel.setText("请先选择一种报表");
			return;
		}
		final ReportDefinition def = (ReportDefinition) value;
		if (ReportCatalog.ID_ACTIVITY.equals(def.id)) {
			showActivityReport();
			return;
		}
		if (ReportCatalog.ID_MCP_AUDIT.equals(def.id)) {
			showMcpAudit();
			return;
		}
		final ReportTimeRange range;
		try {
			range = resolveRange(def);
		}
		catch (Exception e) {
			statusLabel.setText(e.getMessage());
			JOptionPane.showMessageDialog(this, e.getMessage(), "时间范围", JOptionPane.WARNING_MESSAGE);
			return;
		}
		final ReportViewportService service = ReportViewportService.get();
		if (service == null) {
			statusLabel.setText("无法打开报表视图");
			return;
		}
		final ReportQuery query = new ReportQuery(range, includeField.getText(), excludeField.getText());
		final String subtitle = buildLoadingSubtitle(def, query);
		// New tab each time; previous report tabs stay open and keep loading independently.
		final ReportViewportService.ReportLoadSession session = service.beginReport(def, subtitle);
		if (session == null || session.view == null) {
			statusLabel.setText("无法打开报表视图");
			return;
		}
		final int statusGen = ++statusGeneration;
		statusLabel.setText("已打开「" + def.title + "」，正在加载…");
		// Heartbeat: long scans rarely emit mid-progress; keep the bar alive with elapsed time.
		final Timer heartbeat = new Timer(300, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (session.isFinished()) {
					((Timer) e.getSource()).stop();
					return;
				}
				service.updateProgress(session, session.getLastMessage(), -1);
			}
		});
		heartbeat.setRepeats(true);
		heartbeat.start();
		final Thread thread = new Thread(new Runnable() {
			public void run() {
				ReportViewModel viewModel = null;
				ReportNodeSpec tree = null;
				Exception error = null;
				try {
					final ReportProgress progress = new ReportProgress() {
						public void update(final int percent, final String message) {
							service.updateProgress(session, message, percent);
							SwingUtilities.invokeLater(new Runnable() {
								public void run() {
									if (statusGen != statusGeneration) {
										return;
									}
									statusLabel.setText(message == null ? "加载中…" : message);
								}
							});
						}
					};
					viewModel = ReportEngine.generateView(def, query, progress);
					service.updateProgress(session, "正在整理写入树…", 90);
					tree = ReportEngine.toTree(viewModel);
					service.updateProgress(session, "正在渲染图表…", 97);
				}
				catch (Exception e) {
					error = e;
					LogUtils.warn("ReportsTabPanel generate failed", e);
				}
				final ReportViewModel resultView = viewModel;
				final ReportNodeSpec resultTree = tree;
				final Exception fail = error;
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						heartbeat.stop();
						try {
							if (fail != null) {
								service.showError(session, fail.getMessage());
								if (statusGen == statusGeneration) {
									statusLabel.setText("生成失败：" + fail.getMessage());
								}
								return;
							}
							service.showReport(session, resultView, resultTree);
							if (statusGen == statusGeneration) {
								statusLabel.setText("已显示「" + def.title + "」· 可切换/分组/关闭 Tab");
							}
						}
						catch (Exception e) {
							service.showError(session, e.getMessage());
							if (statusGen == statusGeneration) {
								statusLabel.setText(e.getMessage());
							}
						}
					}
				});
			}
		}, "Docear-Report-Generate-" + session.id);
		thread.setDaemon(true);
		thread.start();
	}

	private static String buildLoadingSubtitle(final ReportDefinition def, final ReportQuery query) {
		final StringBuilder sub = new StringBuilder();
		if (def != null && def.description != null && def.description.length() > 0) {
			sub.append(def.description);
		}
		if (def != null && def.usesTimeRange && query != null && query.range != null) {
			if (sub.length() > 0) {
				sub.append("  ·  ");
			}
			sub.append(query.range.label);
		}
		if (sub.length() == 0) {
			sub.append("正在加载…");
		}
		return sub.toString();
	}

	private void showActivityReport() {
		final UsageStatsReportService service = UsageStatsReportService.get();
		if (service == null) {
			statusLabel.setText("活动报表服务不可用");
			return;
		}
		service.setReportVisible(true);
		statusLabel.setText("已显示「活动报表」· 关 Tab 或点「返回导图」");
	}

	private void showMcpAudit() {
		try {
			final AFreeplaneAction action = Controller.getCurrentController().getAction("McpStatusAuditAction");
			if (action == null) {
				statusLabel.setText("MCP 插件未加载，无法打开审计");
				JOptionPane.showMessageDialog(this, "MCP 插件未加载，无法打开 MCP 审计。",
				        "MCP 审计", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			action.actionPerformed(null);
			statusLabel.setText("已打开「MCP 审计」· 关 Tab 或点「返回导图」");
		}
		catch (Exception e) {
			statusLabel.setText("打开 MCP 审计失败：" + e.getMessage());
			LogUtils.warn("showMcpAudit failed", e);
		}
	}

	private ReportTimeRange resolveRange(final ReportDefinition def) {
		if (!def.usesTimeRange) {
			return ReportTimeRange.ofPreset(ReportTimeRange.PRESET_THIS_WEEK);
		}
		final int idx = rangeCombo.getSelectedIndex();
		if (idx == ReportTimeRange.PRESET_CUSTOM) {
			final Date start = parseDay(startField.getText(), "开始日期");
			final Date end = parseDay(endField.getText(), "结束日期");
			return ReportTimeRange.custom(start, end);
		}
		return ReportTimeRange.ofPreset(idx);
	}

	private static Date parseDay(final String text, final String label) {
		if (text == null || text.trim().length() == 0) {
			throw new IllegalArgumentException(label + "不能为空（yyyy-MM-dd）");
		}
		try {
			DAY.setLenient(false);
			return DAY.parse(text.trim());
		}
		catch (ParseException e) {
			throw new IllegalArgumentException(label + "格式应为 yyyy-MM-dd");
		}
	}

	private static javax.swing.Icon loadIcon(final String name) {
		try {
			final MindIcon mind = IconStoreFactory.create().getMindIcon(name);
			if (mind != null) {
				return mind.getIcon();
			}
		}
		catch (Exception e) {
		}
		return null;
	}

	private static String escape(final String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
