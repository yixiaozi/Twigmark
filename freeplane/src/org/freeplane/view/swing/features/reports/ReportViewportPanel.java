package org.freeplane.view.swing.features.reports;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import org.freeplane.core.ui.theme.DocearUiTheme;

/**
 * Center viewport: KPI strip + charts + details. Product copy explains decision + data.
 */
public final class ReportViewportPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private final JLabel titleLabel = new JLabel("报表");
	private final JLabel subtitleLabel = new JLabel(" ");
	private final JLabel decisionLabel = new JLabel(" ");
	private final JLabel dataLabel = new JLabel(" ");
	private final JPanel kpiHost = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
	private final JPanel chartsHost = new JPanel();
	private final DefaultListModel detailModel = new DefaultListModel();
	private final JList detailList = new JList(detailModel);
	private final JButton closeButton = DocearUiTheme.softButton("返回导图");
	private final JButton writeButton = DocearUiTheme.primaryButton("写入选中节点");
	private ReportViewModel current;
	private ReportNodeSpec currentTree;
	private Runnable onClose;
	private Runnable onWrite;

	public ReportViewportPanel() {
		super(new BorderLayout(8, 8));
		DocearUiTheme.styleCanvas(this);
		setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		titleLabel.setFont(DocearUiTheme.font(18f, Font.BOLD));
		titleLabel.setForeground(DocearUiTheme.TEXT);
		subtitleLabel.setFont(DocearUiTheme.font(12f));
		subtitleLabel.setForeground(DocearUiTheme.TEXT_MUTED);
		decisionLabel.setFont(DocearUiTheme.font(12f, Font.BOLD));
		decisionLabel.setForeground(DocearUiTheme.ACCENT_DEEP);
		dataLabel.setFont(DocearUiTheme.font(11f));
		dataLabel.setForeground(DocearUiTheme.TEXT_FAINT);
		kpiHost.setOpaque(false);
		chartsHost.setOpaque(false);
		buildUi();
	}

	public void setOnClose(final Runnable onClose) {
		this.onClose = onClose;
	}

	public void setOnWrite(final Runnable onWrite) {
		this.onWrite = onWrite;
	}

	public ReportNodeSpec getCurrentTree() {
		return currentTree;
	}

	public ReportViewModel getCurrentModel() {
		return current;
	}

	public void showModel(final ReportViewModel model, final ReportNodeSpec tree) {
		this.current = model;
		this.currentTree = tree;
		titleLabel.setText(model == null ? "报表" : model.title);
		subtitleLabel.setText(model == null ? "" : model.subtitle);
		decisionLabel.setText(model == null || model.decision.length() == 0 ? "" : "作用：" + model.decision);
		dataLabel.setText(model == null || model.dataSource.length() == 0 ? "" : "数据：" + model.dataSource);

		kpiHost.removeAll();
		chartsHost.removeAll();
		detailModel.clear();

		if (model != null) {
			for (int i = 0; i < model.kpis.size(); i++) {
				kpiHost.add(kpiCard((ReportKpi) model.kpis.get(i)));
			}
			if (model.kpis.isEmpty()) {
				kpiHost.add(hintLabel("无 KPI"));
			}

			final int chartCount = model.charts.size();
			if (chartCount == 0) {
				chartsHost.setLayout(new BorderLayout());
				final String hint = model.emptyHint.length() > 0 ? model.emptyHint : "暂无足够数值可画图；下方为明细。";
				chartsHost.add(hintLabel(hint), BorderLayout.CENTER);
			}
			else if (chartCount == 1) {
				chartsHost.setLayout(new BorderLayout());
				chartsHost.add(wrapChart((ReportChartSeries) model.charts.get(0)), BorderLayout.CENTER);
			}
			else {
				final int cols = Math.min(2, chartCount);
				final int rows = (chartCount + cols - 1) / cols;
				chartsHost.setLayout(new GridLayout(rows, cols, 8, 8));
				for (int i = 0; i < chartCount && i < 4; i++) {
					chartsHost.add(wrapChart((ReportChartSeries) model.charts.get(i)));
				}
			}
			for (int i = 0; i < model.details.size(); i++) {
				detailModel.addElement(model.details.get(i));
			}
			if (model.details.isEmpty() && model.emptyHint.length() > 0) {
				detailModel.addElement(model.emptyHint);
			}
		}
		writeButton.setEnabled(currentTree != null);
		revalidate();
		repaint();
	}

	private JPanel kpiCard(final ReportKpi kpi) {
		final JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setOpaque(true);
		card.setBackground(DocearUiTheme.SURFACE);
		card.setBorder(BorderFactory.createCompoundBorder(
		        BorderFactory.createLineBorder(DocearUiTheme.HAIRLINE),
		        BorderFactory.createEmptyBorder(8, 12, 8, 12)));
		final JLabel value = new JLabel(kpi.value);
		value.setFont(value.getFont().deriveFont(Font.BOLD, 18f));
		final JLabel label = new JLabel(kpi.label);
		label.setForeground(DocearUiTheme.TEXT_MUTED);
		label.setFont(label.getFont().deriveFont(11f));
		final JLabel hint = new JLabel(kpi.hint);
		hint.setForeground(DocearUiTheme.TEXT_FAINT);
		hint.setFont(hint.getFont().deriveFont(10f));
		card.add(value);
		card.add(Box.createVerticalStrut(2));
		card.add(label);
		if (kpi.hint.length() > 0) {
			card.add(hint);
		}
		return card;
	}

	private JLabel hintLabel(final String text) {
		final JLabel empty = new JLabel("<html><div style='padding:16px;color:#666'>" + escape(text) + "</div></html>");
		empty.setForeground(DocearUiTheme.TEXT_MUTED);
		return empty;
	}

	private JPanel wrapChart(final ReportChartSeries series) {
		final JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(true);
		wrap.setBackground(DocearUiTheme.SURFACE);
		wrap.setBorder(BorderFactory.createCompoundBorder(
		        BorderFactory.createLineBorder(DocearUiTheme.HAIRLINE),
		        BorderFactory.createEmptyBorder(4, 4, 4, 4)));
		final ReportChartPanel chart = new ReportChartPanel(series);
		chart.setPreferredSize(new Dimension(480, 250));
		wrap.add(chart, BorderLayout.CENTER);
		return wrap;
	}

	private void buildUi() {
		final JPanel north = new JPanel(new BorderLayout(8, 0));
		north.setOpaque(false);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
		subtitleLabel.setForeground(DocearUiTheme.TEXT_MUTED);
		subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(12f));
		decisionLabel.setForeground(DocearUiTheme.ACCENT_DEEP);
		decisionLabel.setFont(decisionLabel.getFont().deriveFont(Font.PLAIN, 12f));
		dataLabel.setForeground(DocearUiTheme.TEXT_FAINT);
		dataLabel.setFont(dataLabel.getFont().deriveFont(11f));

		final JPanel titles = new JPanel();
		titles.setOpaque(false);
		titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
		titles.add(titleLabel);
		titles.add(Box.createVerticalStrut(2));
		titles.add(subtitleLabel);
		titles.add(Box.createVerticalStrut(2));
		titles.add(decisionLabel);
		titles.add(dataLabel);
		north.add(titles, BorderLayout.CENTER);

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.setOpaque(false);
		writeButton.setToolTipText("把当前报表写入导图选中节点（可选）");
		closeButton.setToolTipText("关闭报表，回到思维导图");
		buttons.add(writeButton);
		buttons.add(closeButton);
		north.add(buttons, BorderLayout.EAST);

		final JPanel topBlock = new JPanel(new BorderLayout(0, 6));
		topBlock.setOpaque(false);
		topBlock.add(north, BorderLayout.NORTH);
		kpiHost.setOpaque(false);
		topBlock.add(kpiHost, BorderLayout.CENTER);
		add(topBlock, BorderLayout.NORTH);

		chartsHost.setOpaque(false);
		chartsHost.setPreferredSize(new Dimension(640, 280));
		final JScrollPane chartScroll = new JScrollPane(chartsHost);
		chartScroll.setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(), "图表"));
		chartScroll.getVerticalScrollBar().setUnitIncrement(16);

		detailList.setFont(detailList.getFont().deriveFont(12f));
		detailList.setVisibleRowCount(12);
		final JScrollPane detailScroll = new JScrollPane(detailList);
		detailScroll.setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(), "明细（可执行清单）"));

		final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartScroll, detailScroll);
		split.setResizeWeight(0.58);
		split.setBorder(null);
		add(split, BorderLayout.CENTER);

		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (onClose != null) {
					onClose.run();
				}
			}
		});
		writeButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (onWrite != null) {
					onWrite.run();
				}
			}
		});
	}

	private static String escape(final String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
