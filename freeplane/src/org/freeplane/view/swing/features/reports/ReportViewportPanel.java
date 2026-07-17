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

/**
 * Center viewport content: charts on top, detail list below.
 */
public final class ReportViewportPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private final JLabel titleLabel = new JLabel("报表");
	private final JLabel subtitleLabel = new JLabel(" ");
	private final JPanel chartsHost = new JPanel();
	private final DefaultListModel detailModel = new DefaultListModel();
	private final JList detailList = new JList(detailModel);
	private final JButton closeButton = new JButton("返回导图");
	private final JButton writeButton = new JButton("写入选中节点");
	private ReportViewModel current;
	private ReportNodeSpec currentTree;
	private Runnable onClose;
	private Runnable onWrite;

	public ReportViewportPanel() {
		super(new BorderLayout(8, 8));
		setOpaque(true);
		setBackground(new Color(0xF7, 0xF8, 0xFA));
		setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
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
		chartsHost.removeAll();
		detailModel.clear();
		if (model != null) {
			final int chartCount = model.charts.size();
			if (chartCount == 0) {
				chartsHost.setLayout(new BorderLayout());
				final JLabel empty = new JLabel("暂无足够数值可画图；下方为明细列表。");
				empty.setForeground(new Color(0x88, 0x88, 0x88));
				empty.setBorder(BorderFactory.createEmptyBorder(24, 16, 24, 16));
				chartsHost.add(empty, BorderLayout.CENTER);
			}
			else if (chartCount == 1) {
				chartsHost.setLayout(new BorderLayout());
				chartsHost.add(wrapChart((ReportChartSeries) model.charts.get(0)), BorderLayout.CENTER);
			}
			else {
				chartsHost.setLayout(new GridLayout(1, Math.min(2, chartCount), 8, 8));
				for (int i = 0; i < chartCount && i < 4; i++) {
					chartsHost.add(wrapChart((ReportChartSeries) model.charts.get(i)));
				}
			}
			for (int i = 0; i < model.details.size(); i++) {
				detailModel.addElement(model.details.get(i));
			}
		}
		writeButton.setEnabled(currentTree != null);
		revalidate();
		repaint();
	}

	private JPanel wrapChart(final ReportChartSeries series) {
		final JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(true);
		wrap.setBackground(Color.WHITE);
		wrap.setBorder(BorderFactory.createCompoundBorder(
		        BorderFactory.createLineBorder(new Color(0xE2, 0xE5, 0xEA)),
		        BorderFactory.createEmptyBorder(4, 4, 4, 4)));
		final ReportChartPanel chart = new ReportChartPanel(series);
		chart.setPreferredSize(new Dimension(480, 260));
		wrap.add(chart, BorderLayout.CENTER);
		return wrap;
	}

	private void buildUi() {
		final JPanel north = new JPanel(new BorderLayout(8, 0));
		north.setOpaque(false);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
		subtitleLabel.setForeground(new Color(0x66, 0x66, 0x66));
		subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(12f));
		final JPanel titles = new JPanel();
		titles.setOpaque(false);
		titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
		titles.add(titleLabel);
		titles.add(Box.createVerticalStrut(2));
		titles.add(subtitleLabel);
		north.add(titles, BorderLayout.CENTER);

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.setOpaque(false);
		writeButton.setToolTipText("把当前报表树写入导图选中节点（可选）");
		closeButton.setToolTipText("关闭报表，回到思维导图");
		buttons.add(writeButton);
		buttons.add(closeButton);
		north.add(buttons, BorderLayout.EAST);
		add(north, BorderLayout.NORTH);

		chartsHost.setOpaque(false);
		chartsHost.setPreferredSize(new Dimension(640, 280));
		final JScrollPane chartScroll = new JScrollPane(chartsHost);
		chartScroll.setBorder(BorderFactory.createTitledBorder("图表"));
		chartScroll.getVerticalScrollBar().setUnitIncrement(16);

		detailList.setFont(detailList.getFont().deriveFont(12f));
		detailList.setVisibleRowCount(12);
		final JScrollPane detailScroll = new JScrollPane(detailList);
		detailScroll.setBorder(BorderFactory.createTitledBorder("明细"));

		final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartScroll, detailScroll);
		split.setResizeWeight(0.62);
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
}
