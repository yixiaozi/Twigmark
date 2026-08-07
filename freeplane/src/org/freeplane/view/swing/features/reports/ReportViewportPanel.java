package org.freeplane.view.swing.features.reports;

import java.awt.BorderLayout;
import java.awt.CardLayout;
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
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.TextUtils;

/**
 * Center viewport: KPI strip + charts + details. Product copy explains decision + data.
 * Opens immediately with a loading card; data fills in when generation finishes.
 */
public final class ReportViewportPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final String CARD_CONTENT = "content";
	private static final String CARD_LOADING = "loading";

	private final JLabel titleLabel = new JLabel();
	private final JLabel subtitleLabel = new JLabel(" ");
	private final JLabel decisionLabel = new JLabel(" ");
	private final JLabel dataLabel = new JLabel(" ");
	private final JPanel kpiHost = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
	private final JPanel chartsHost = new JPanel();
	private final DefaultListModel detailModel = new DefaultListModel();
	private final JList detailList = new JList(detailModel);
	private final JButton closeButton;
	private final JButton writeButton;
	private final CardLayout bodyCards = new CardLayout();
	private final JPanel bodyHost = new JPanel(bodyCards);
	private final JProgressBar progressBar = new JProgressBar();
	private final JLabel loadingStatusLabel;
	private ReportViewModel current;
	private ReportNodeSpec currentTree;
	private Runnable onClose;
	private Runnable onWrite;
	private boolean loading;

	public ReportViewportPanel() {
		super(new BorderLayout(8, 8));
		DocearUiTheme.styleCanvas(this);
		setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
		titleLabel.setText(TextUtils.getText("ReportViewport.title.default"));
		closeButton = DocearUiTheme.softButton(TextUtils.getText("ReportViewport.button.backToMap"));
		writeButton = DocearUiTheme.primaryButton(TextUtils.getText("ReportViewport.button.writeToNode"));
		loadingStatusLabel = new JLabel(TextUtils.getText("ReportViewport.loading.status"), SwingConstants.CENTER);
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

	public boolean isLoading() {
		return loading;
	}

	/**
	 * Open the shell immediately (title / decision / data source) while data is still generating.
	 */
	public void showLoading(final String title, final String subtitle, final String decision,
	        final String dataSource) {
		loading = true;
		current = null;
		currentTree = null;
		titleLabel.setText(title == null || title.length() == 0 ? TextUtils.getText("ReportViewport.title.default")
		        : title);
		subtitleLabel.setText(subtitle == null ? "" : subtitle);
		decisionLabel.setText(decision == null || decision.length() == 0 ? ""
		        : TextUtils.format("ReportViewport.decision.prefix", decision));
		dataLabel.setText(dataSource == null || dataSource.length() == 0 ? ""
		        : TextUtils.format("ReportViewport.data.prefix", dataSource));
		kpiHost.removeAll();
		kpiHost.add(hintLabel(TextUtils.getText("ReportViewport.loading.dataHint")));
		detailModel.clear();
		detailModel.addElement(TextUtils.getText("ReportViewport.loading.detailPlaceholder"));
		writeButton.setEnabled(false);
		setLoadProgress(TextUtils.getText("ReportViewport.loading.scanning"), -1);
		bodyCards.show(bodyHost, CARD_LOADING);
		revalidate();
		repaint();
	}

	/**
	 * @param percent 0–100 for determinate progress; negative keeps/sets indeterminate
	 */
	public void setLoadProgress(final String message, final int percent) {
		if (message != null && message.length() > 0) {
			loadingStatusLabel.setText(message);
		}
		if (percent < 0) {
			progressBar.setIndeterminate(true);
			progressBar.setString(TextUtils.getText("ReportViewport.progress.generating"));
		}
		else {
			final int clamped = Math.max(0, Math.min(100, percent));
			progressBar.setIndeterminate(false);
			progressBar.setValue(clamped);
			progressBar.setString(clamped + "%");
		}
	}

	public void showError(final String title, final String message) {
		loading = false;
		current = null;
		currentTree = null;
		titleLabel.setText(title == null || title.length() == 0 ? TextUtils.getText("ReportViewport.title.default")
		        : title);
		subtitleLabel.setText(message == null ? TextUtils.getText("ReportViewport.error.generateFailed") : message);
		decisionLabel.setText("");
		dataLabel.setText("");
		kpiHost.removeAll();
		chartsHost.removeAll();
		chartsHost.setLayout(new BorderLayout());
		chartsHost.add(hintLabel(message == null ? TextUtils.getText("ReportViewport.error.generateFailed") : message),
		        BorderLayout.CENTER);
		detailModel.clear();
		writeButton.setEnabled(false);
		bodyCards.show(bodyHost, CARD_CONTENT);
		revalidate();
		repaint();
	}

	public void showModel(final ReportViewModel model, final ReportNodeSpec tree) {
		loading = false;
		this.current = model;
		this.currentTree = tree;
		titleLabel.setText(model == null ? TextUtils.getText("ReportViewport.title.default") : model.title);
		subtitleLabel.setText(model == null ? "" : model.subtitle);
		decisionLabel.setText(model == null || model.decision.length() == 0 ? ""
		        : TextUtils.format("ReportViewport.decision.prefix", model.decision));
		dataLabel.setText(model == null || model.dataSource.length() == 0 ? ""
		        : TextUtils.format("ReportViewport.data.prefix", model.dataSource));

		kpiHost.removeAll();
		chartsHost.removeAll();
		detailModel.clear();

		if (model != null) {
			for (int i = 0; i < model.kpis.size(); i++) {
				kpiHost.add(kpiCard((ReportKpi) model.kpis.get(i)));
			}
			if (model.kpis.isEmpty()) {
				kpiHost.add(hintLabel(TextUtils.getText("ReportViewport.kpi.none")));
			}

			final int chartCount = model.charts.size();
			if (chartCount == 0) {
				chartsHost.setLayout(new BorderLayout());
				final String hint = model.emptyHint.length() > 0 ? model.emptyHint
				        : TextUtils.getText("ReportViewport.charts.emptyHint");
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
		bodyCards.show(bodyHost, CARD_CONTENT);
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
		writeButton.setToolTipText(TextUtils.getText("ReportViewport.write.tooltip"));
		closeButton.setToolTipText(TextUtils.getText("ReportViewport.close.tooltip"));
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
		chartScroll.setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(),
		        TextUtils.getText("ReportViewport.charts.border")));
		chartScroll.getVerticalScrollBar().setUnitIncrement(16);

		detailList.setFont(detailList.getFont().deriveFont(12f));
		detailList.setVisibleRowCount(12);
		final JScrollPane detailScroll = new JScrollPane(detailList);
		detailScroll.setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(),
		        TextUtils.getText("ReportViewport.details.border")));

		final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartScroll, detailScroll);
		split.setResizeWeight(0.58);
		split.setBorder(null);

		final JPanel loadingCard = new JPanel(new BorderLayout());
		loadingCard.setOpaque(false);
		loadingCard.setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(),
		        TextUtils.getText("ReportViewport.loading.border")));
		final JPanel loadingCenter = new JPanel();
		loadingCenter.setOpaque(false);
		loadingCenter.setLayout(new BoxLayout(loadingCenter, BoxLayout.Y_AXIS));
		loadingStatusLabel.setAlignmentX(CENTER_ALIGNMENT);
		loadingStatusLabel.setFont(DocearUiTheme.font(13f));
		loadingStatusLabel.setForeground(DocearUiTheme.TEXT_MUTED);
		progressBar.setAlignmentX(CENTER_ALIGNMENT);
		progressBar.setIndeterminate(true);
		progressBar.setStringPainted(true);
		progressBar.setString(TextUtils.getText("ReportViewport.progress.generating"));
		progressBar.setMaximumSize(new Dimension(420, 22));
		progressBar.setPreferredSize(new Dimension(360, 22));
		loadingCenter.add(Box.createVerticalGlue());
		loadingCenter.add(loadingStatusLabel);
		loadingCenter.add(Box.createVerticalStrut(12));
		loadingCenter.add(progressBar);
		loadingCenter.add(Box.createVerticalGlue());
		loadingCard.add(loadingCenter, BorderLayout.CENTER);

		bodyHost.setOpaque(false);
		bodyHost.add(split, CARD_CONTENT);
		bodyHost.add(loadingCard, CARD_LOADING);
		bodyCards.show(bodyHost, CARD_CONTENT);
		add(bodyHost, BorderLayout.CENTER);

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
