package org.freeplane.view.swing.features.reports;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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
import javax.swing.JButton;
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
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.factory.IconStoreFactory;
import org.freeplane.features.map.NodeModel;

/**
 * Left sidebar tab: pick a report → show charts in the mind-map viewport.
 */
public class ReportsTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

	private final JLabel statusLabel = new JLabel("每种报表回答一个决策问题；点选后在导图区出图");
	private final JComboBox rangeCombo = new JComboBox();
	private final JTextField startField = new JTextField(10);
	private final JTextField endField = new JTextField(10);
	private final JTextField includeField = new JTextField();
	private final JTextField excludeField = new JTextField();
	private final JPanel customRangePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
	private final DefaultListModel listModel = new DefaultListModel();
	private final JList reportList = new JList(listModel);
	private final JButton showButton = new JButton("显示图表");
	private final JButton writeButton = new JButton("写入节点");
	private final JButton refreshButton = new JButton("刷新");
	private volatile boolean generating;
	private boolean suppressSelectionEvent;

	public ReportsTabPanel() {
		super(new BorderLayout(4, 4));
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		buildUi();
		wireEvents();
		reloadCatalog();
		SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_REPORTS, ReportCatalog.all().size());
	}

	private void buildUi() {
		final JPanel north = new JPanel();
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
		statusLabel.setForeground(new Color(0x55, 0x55, 0x55));
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
					setText("<html><b>" + escape(def.title) + "</b><br><font color='#2F6FED' size='2'>"
					        + escape(def.decision) + "</font><br><font color='#666666' size='2'>"
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
		scroll.setBorder(BorderFactory.createTitledBorder("报表（点选 → 中间出图）"));
		add(scroll, BorderLayout.CENTER);

		final JPanel south = new JPanel(new GridLayout(1, 3, 4, 0));
		showButton.setToolTipText("在中间导图位置显示折线/饼图/柱状图");
		writeButton.setToolTipText("可选：把报表树写入当前选中导图节点");
		refreshButton.setToolTipText("重新加载报表目录");
		south.add(showButton);
		south.add(writeButton);
		south.add(refreshButton);
		add(south, BorderLayout.SOUTH);
		setPreferredSize(new Dimension(220, 400));
	}

	private void wireEvents() {
		rangeCombo.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				customRangePanel.setVisible(rangeCombo.getSelectedIndex() == ReportTimeRange.PRESET_CUSTOM);
				revalidate();
			}
		});
		showButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				runSelected(false);
			}
		});
		writeButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				runSelected(true);
			}
		});
		refreshButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				reloadCatalog();
				statusLabel.setText("已刷新 · 共 " + ReportCatalog.all().size() + " 种报表");
			}
		});
		reportList.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(final ListSelectionEvent e) {
				if (e.getValueIsAdjusting() || suppressSelectionEvent) {
					return;
				}
				if (reportList.getSelectedIndex() >= 0) {
					runSelected(false);
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

	private void runSelected(final boolean writeToMindMap) {
		if (generating) {
			return;
		}
		final Object value = reportList.getSelectedValue();
		if (!(value instanceof ReportDefinition)) {
			statusLabel.setText("请先选择一种报表");
			return;
		}
		final ReportDefinition def = (ReportDefinition) value;
		final ReportTimeRange range;
		try {
			range = resolveRange(def);
		}
		catch (Exception e) {
			statusLabel.setText(e.getMessage());
			JOptionPane.showMessageDialog(this, e.getMessage(), "时间范围", JOptionPane.WARNING_MESSAGE);
			return;
		}
		final ReportQuery query = new ReportQuery(range, includeField.getText(), excludeField.getText());
		generating = true;
		showButton.setEnabled(false);
		writeButton.setEnabled(false);
		statusLabel.setText("正在生成「" + def.title + "」…");
		final Thread thread = new Thread(new Runnable() {
			public void run() {
				ReportViewModel viewModel = null;
				ReportNodeSpec tree = null;
				Exception error = null;
				try {
					viewModel = ReportEngine.generateView(def, query);
					tree = ReportEngine.toTree(viewModel);
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
						try {
							if (fail != null) {
								statusLabel.setText("生成失败：" + fail.getMessage());
								JOptionPane.showMessageDialog(ReportsTabPanel.this, fail.getMessage(), "报表",
								        JOptionPane.ERROR_MESSAGE);
								return;
							}
							final ReportViewportService service = ReportViewportService.get();
							if (service == null) {
								statusLabel.setText("无法打开报表视图");
								return;
							}
							service.showReport(resultView, resultTree);
							if (writeToMindMap) {
								final NodeModel written = ReportMindMapWriter.writeUnderSelection(resultTree);
								if (written == null) {
									statusLabel.setText("图表已显示；写入失败：未选中节点");
								}
								else {
									statusLabel.setText("已显示「" + def.title + "」，并写入节点");
								}
							}
							else {
								statusLabel.setText("已显示「" + def.title + "」· " + def.decision);
							}
						}
						catch (Exception e) {
							statusLabel.setText(e.getMessage());
							JOptionPane.showMessageDialog(ReportsTabPanel.this, e.getMessage(), "报表",
							        JOptionPane.WARNING_MESSAGE);
						}
						finally {
							generating = false;
							showButton.setEnabled(true);
							writeButton.setEnabled(true);
						}
					}
				});
			}
		}, "Docear-Report-Generate");
		thread.setDaemon(true);
		thread.start();
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
