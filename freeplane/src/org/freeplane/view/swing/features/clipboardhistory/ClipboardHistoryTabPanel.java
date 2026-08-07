package org.freeplane.view.swing.features.clipboardhistory;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.core.util.TextUtils;
import org.freeplane.view.swing.features.reports.ReportChartSeries;
import org.freeplane.view.swing.features.reports.ReportEngine;
import org.freeplane.view.swing.features.reports.ReportKpi;
import org.freeplane.view.swing.features.reports.ReportNodeSpec;
import org.freeplane.view.swing.features.reports.ReportViewModel;
import org.freeplane.view.swing.features.reports.ReportViewportService;

/**
 * Side tab: searchable clipboard history + stats report entry.
 * <p>
 * Live updates are soft (prepend/bump latest row) and only while the tab is
 * showing — never rebuild the whole list on the EDT for every copy.
 */
public final class ClipboardHistoryTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final int DISPLAY_LIMIT = 300;
	private static final SimpleDateFormat TIME = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
	private static final SimpleDateFormat TIME_SEC = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA);
	private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

	private final JTextField searchField = new JTextField();
	private final JLabel statusLabel = DocearUiTheme.mutedLabel(" ");
	private final DefaultListModel listModel = new DefaultListModel();
	private final JList list = new JList(listModel);
	private final JTextArea detailArea = new JTextArea();
	private final JTextArea hitTimesArea = new JTextArea();
	private final Timer reloadDebounce;
	private final Timer softUpdateDebounce;
	private volatile boolean reloading;
	private volatile int generation;
	private volatile int detailGeneration;
	private volatile long lastFullStatusAt;

	public ClipboardHistoryTabPanel() {
		super(new BorderLayout(0, 0));
		DocearUiTheme.styleCanvas(this);
		setBorder(DocearUiTheme.pageBorder());
		reloadDebounce = new Timer(320, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				reloadDebounce.stop();
				reloadAsync(true);
			}
		});
		reloadDebounce.setRepeats(false);
		softUpdateDebounce = new Timer(180, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				softUpdateDebounce.stop();
				softUpdateLatest();
			}
		});
		softUpdateDebounce.setRepeats(false);
		buildUi();
		ClipboardHistoryService.getInstance().setChangeListener(new Runnable() {
			public void run() {
				onHistoryChanged();
			}
		});
		addHierarchyListener(new HierarchyListener() {
			public void hierarchyChanged(final HierarchyEvent e) {
				if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
					reloadAsync(false);
				}
			}
		});
		reloadAsync(true);
	}

	private void onHistoryChanged() {
		if (!isShowing()) {
			return;
		}
		if (hasSearchQuery()) {
			reloadDebounce.restart();
			return;
		}
		softUpdateDebounce.restart();
	}

	private boolean hasSearchQuery() {
		final String q = searchField.getText();
		return q != null && q.trim().length() > 0;
	}

	private void buildUi() {
		add(buildHeader(), BorderLayout.NORTH);

		DocearUiTheme.styleList(list);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setFixedCellHeight(46);
		list.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			public Component getListCellRendererComponent(final JList jlist, final Object value, final int index,
					final boolean isSelected, final boolean cellHasFocus) {
				super.getListCellRendererComponent(jlist, value, index, isSelected, cellHasFocus);
				setFont(DocearUiTheme.font(12f));
				if (value instanceof ClipboardHistoryEntry) {
					final ClipboardHistoryEntry entry = (ClipboardHistoryEntry) value;
					final String meta = TextUtils.format("ClipboardHistory.list.meta",
					        TIME.format(new Date(entry.lastTs)), Integer.valueOf(entry.hitCount),
					        Integer.valueOf(entry.charLen));
					setText("<html><b>" + escape(entry.preview(72)) + "</b><br/>"
							+ "<span style='color:#64748B'>" + escape(meta) + "</span></html>");
					if (!isSelected) {
						setForeground(DocearUiTheme.TEXT);
					}
				}
				return this;
			}
		});
		list.addMouseListener(new MouseAdapter() {
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() == 2) {
					copySelected();
				}
			}
		});
		list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(final ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					showSelected();
				}
			}
		});

		detailArea.setEditable(false);
		detailArea.setLineWrap(true);
		detailArea.setWrapStyleWord(true);
		detailArea.setFont(DocearUiTheme.font(12f));
		detailArea.setForeground(DocearUiTheme.TEXT);
		detailArea.setBackground(DocearUiTheme.SURFACE);
		detailArea.setBorder(new EmptyBorder(8, 10, 8, 10));

		hitTimesArea.setEditable(false);
		hitTimesArea.setLineWrap(true);
		hitTimesArea.setWrapStyleWord(true);
		hitTimesArea.setFont(DocearUiTheme.font(11f));
		hitTimesArea.setForeground(DocearUiTheme.TEXT_MUTED);
		hitTimesArea.setBackground(DocearUiTheme.SURFACE);
		hitTimesArea.setBorder(new EmptyBorder(6, 10, 6, 10));
		hitTimesArea.setRows(4);

		final JScrollPane listScroll = new JScrollPane(list);
		DocearUiTheme.styleScrollPane(listScroll);
		final JScrollPane hitScroll = new JScrollPane(hitTimesArea);
		DocearUiTheme.styleScrollPane(hitScroll);
		hitScroll.setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(),
				TextUtils.getText("ClipboardHistory.hitTimes.border")));
		final JScrollPane detailScroll = new JScrollPane(detailArea);
		DocearUiTheme.styleScrollPane(detailScroll);
		detailScroll.setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(),
				TextUtils.getText("ClipboardHistory.fullText.border")));

		final JSplitPane detailSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, hitScroll, detailScroll);
		detailSplit.setResizeWeight(0.35);
		detailSplit.setBorder(null);
		final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listScroll, detailSplit);
		split.setResizeWeight(0.55);
		split.setBorder(null);
		add(split, BorderLayout.CENTER);
		add(buildActions(), BorderLayout.SOUTH);
	}

	private JPanel buildHeader() {
		final JPanel header = new JPanel(new BorderLayout(6, 6));
		header.setOpaque(false);
		final JLabel title = DocearUiTheme.titleLabel(TextUtils.getText("ClipboardHistory.title"));
		final JLabel hint = DocearUiTheme.mutedLabel(TextUtils.getText("ClipboardHistory.hint"));
		hint.setFont(DocearUiTheme.font(11f));
		final JPanel titleCol = new JPanel();
		titleCol.setOpaque(false);
		titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
		titleCol.add(title);
		titleCol.add(Box.createVerticalStrut(2));
		titleCol.add(hint);
		titleCol.add(Box.createVerticalStrut(4));
		titleCol.add(statusLabel);
		header.add(titleCol, BorderLayout.NORTH);

		DocearUiTheme.styleSearchField(searchField);
		searchField.setToolTipText(TextUtils.getText("ClipboardHistory.search.tooltip"));
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(final DocumentEvent e) {
				reloadDebounce.restart();
			}

			public void removeUpdate(final DocumentEvent e) {
				reloadDebounce.restart();
			}

			public void changedUpdate(final DocumentEvent e) {
				reloadDebounce.restart();
			}
		});
		header.add(searchField, BorderLayout.SOUTH);
		return header;
	}

	private JPanel buildActions() {
		final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		actions.setOpaque(false);
		actions.add(primaryButton(TextUtils.getText("ClipboardHistory.action.copy"), new Runnable() {
			public void run() {
				copySelected();
			}
		}));
		actions.add(softButton(TextUtils.getText("ClipboardHistory.action.delete"), new Runnable() {
			public void run() {
				deleteSelected();
			}
		}));
		actions.add(softButton(TextUtils.getText("ClipboardHistory.action.refresh"), new Runnable() {
			public void run() {
				reloadAsync(true);
			}
		}));
		actions.add(softButton(TextUtils.getText("ClipboardHistory.action.stats"), new Runnable() {
			public void run() {
				showStatsReport();
			}
		}));
		actions.add(softButton(TextUtils.getText("ClipboardHistory.action.clear"), new Runnable() {
			public void run() {
				clearAll();
			}
		}));
		return actions;
	}

	/**
	 * Cheap live path: fetch only the newest row off-EDT, then bump/prepend in the list.
	 */
	private void softUpdateLatest() {
		if (!isShowing() || hasSearchQuery() || reloading) {
			return;
		}
		final int gen = ++generation;
		new SwingWorker() {
			private ClipboardHistoryEntry latest;
			private int total;

			protected Object doInBackground() throws Exception {
				final List rows = ClipboardHistoryService.getInstance().search("", 1);
				if (rows != null && !rows.isEmpty()) {
					latest = (ClipboardHistoryEntry) rows.get(0);
				}
				total = ClipboardHistoryService.getInstance().count();
				return null;
			}

			protected void done() {
				if (gen != generation || latest == null) {
					return;
				}
				applyLatest(latest);
				statusLabel.setText(TextUtils.format("ClipboardHistory.status.recent",
						Integer.valueOf(listModel.getSize()), Integer.valueOf(total)));
				statusLabel.setForeground(DocearUiTheme.TEXT_MUTED);
				SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_CLIPBOARD, total);
			}
		}.execute();
	}

	private void applyLatest(final ClipboardHistoryEntry latest) {
		if (listModel.getSize() > 0) {
			final Object first = listModel.getElementAt(0);
			if (first instanceof ClipboardHistoryEntry) {
				final ClipboardHistoryEntry existing = (ClipboardHistoryEntry) first;
				if (existing.id == latest.id
						|| (existing.contentHash != null && existing.contentHash.equals(latest.contentHash))) {
					listModel.set(0, latest);
					if (list.getSelectedIndex() == 0) {
						showSelected();
					}
					list.repaint();
					return;
				}
			}
		}
		final int selected = list.getSelectedIndex();
		listModel.add(0, latest);
		while (listModel.getSize() > DISPLAY_LIMIT) {
			listModel.remove(listModel.getSize() - 1);
		}
		if (selected >= 0) {
			list.setSelectedIndex(Math.min(selected + 1, listModel.getSize() - 1));
		}
		else if (listModel.getSize() > 0) {
			list.setSelectedIndex(0);
		}
	}

	private void reloadAsync(final boolean forceFullStatus) {
		final String query = searchField.getText();
		final int gen = ++generation;
		reloading = true;
		new SwingWorker() {
			private List rows;
			private int total;
			private long hits;
			private long dbBytes;

			protected Object doInBackground() throws Exception {
				final ClipboardHistoryService service = ClipboardHistoryService.getInstance();
				rows = service.search(query, DISPLAY_LIMIT);
				total = service.count();
				final boolean wantDetails = forceFullStatus
						|| System.currentTimeMillis() - lastFullStatusAt > 15000L;
				if (wantDetails) {
					hits = service.sumHits();
					dbBytes = service.getDbFileBytes();
					lastFullStatusAt = System.currentTimeMillis();
				}
				else {
					hits = -1L;
					dbBytes = -1L;
				}
				return null;
			}

			protected void done() {
				if (gen != generation) {
					reloading = false;
					return;
				}
				try {
					listModel.clear();
					if (rows == null || rows.isEmpty()) {
						statusLabel.setText(query != null && query.trim().length() > 0
								? TextUtils.getText("ClipboardHistory.status.noMatch")
								: TextUtils.getText("ClipboardHistory.status.empty"));
						statusLabel.setForeground(DocearUiTheme.TEXT_FAINT);
						detailArea.setText("");
						hitTimesArea.setText("");
					}
					else {
						for (int i = 0; i < rows.size(); i++) {
							listModel.addElement(rows.get(i));
						}
						if (hits >= 0L) {
							statusLabel.setText(TextUtils.format("ClipboardHistory.status.full",
									Integer.valueOf(rows.size()), Integer.valueOf(total), Long.valueOf(hits),
									formatBytes(dbBytes)));
						}
						else {
							statusLabel.setText(TextUtils.format("ClipboardHistory.status.basic",
									Integer.valueOf(rows.size()), Integer.valueOf(total)));
						}
						statusLabel.setForeground(DocearUiTheme.TEXT_MUTED);
						if (list.getSelectedIndex() < 0 && listModel.getSize() > 0) {
							list.setSelectedIndex(0);
						}
						else {
							showSelected();
						}
					}
					SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_CLIPBOARD, total);
				}
				finally {
					reloading = false;
				}
			}
		}.execute();
	}

	private void showSelected() {
		final Object value = list.getSelectedValue();
		if (!(value instanceof ClipboardHistoryEntry)) {
			detailArea.setText("");
			hitTimesArea.setText("");
			return;
		}
		final ClipboardHistoryEntry entry = (ClipboardHistoryEntry) value;
		detailArea.setText(entry.content);
		detailArea.setCaretPosition(0);
		hitTimesArea.setText(TextUtils.getText("ClipboardHistory.hitTimes.loading"));
		final int gen = ++detailGeneration;
		new SwingWorker() {
			private List times;

			protected Object doInBackground() throws Exception {
				times = ClipboardHistoryService.getInstance().listHitTimes(entry);
				return null;
			}

			protected void done() {
				if (gen != detailGeneration) {
					return;
				}
				hitTimesArea.setText(formatHitTimes(entry, times));
				hitTimesArea.setCaretPosition(0);
			}
		}.execute();
	}

	private String formatHitTimes(final ClipboardHistoryEntry entry, final List times) {
		final StringBuffer sb = new StringBuffer();
		final int recorded = times == null ? 0 : times.size();
		sb.append(TextUtils.format("ClipboardHistory.hitTimes.summary", Integer.valueOf(entry.hitCount)));
		if (recorded > 0) {
			sb.append(TextUtils.format("ClipboardHistory.hitTimes.recorded", Integer.valueOf(recorded)));
		}
		if (entry.hitCount > recorded && recorded > 0) {
			sb.append(TextUtils.getText("ClipboardHistory.hitTimes.legacyNote"));
		}
		sb.append('\n');
		if (recorded == 0) {
			sb.append(TextUtils.getText("ClipboardHistory.hitTimes.none"));
			return sb.toString();
		}
		for (int i = 0; i < recorded; i++) {
			final long ts = ((Long) times.get(i)).longValue();
			sb.append(i + 1).append(". ").append(TIME_SEC.format(new Date(ts)));
			if (i == 0) {
				sb.append(TextUtils.getText("ClipboardHistory.hitTimes.latest"));
			}
			else if (i == recorded - 1 && entry.firstTs == ts) {
				sb.append(TextUtils.getText("ClipboardHistory.hitTimes.first"));
			}
			sb.append('\n');
		}
		if (recorded >= ClipboardHistoryDatabase.HIT_LIST_LIMIT) {
			sb.append(TextUtils.format("ClipboardHistory.hitTimes.truncated",
					Integer.valueOf(ClipboardHistoryDatabase.HIT_LIST_LIMIT)));
		}
		return sb.toString().trim();
	}

	private void copySelected() {
		final ClipboardHistoryEntry entry = selectedEntry();
		if (entry == null) {
			return;
		}
		ClipboardHistoryMonitor.copyToSystemClipboard(entry.content);
		statusLabel.setText(TextUtils.getText("ClipboardHistory.status.copied"));
		statusLabel.setForeground(DocearUiTheme.SUCCESS);
	}

	private void deleteSelected() {
		final ClipboardHistoryEntry entry = selectedEntry();
		if (entry == null) {
			return;
		}
		final int ok = JOptionPane.showConfirmDialog(this,
				TextUtils.format("ClipboardHistory.delete.confirm", entry.preview(80)),
				TextUtils.getText("ClipboardHistory.delete.title"), JOptionPane.OK_CANCEL_OPTION);
		if (ok != JOptionPane.OK_OPTION) {
			return;
		}
		ClipboardHistoryService.getInstance().delete(entry);
		reloadAsync(true);
	}

	private void clearAll() {
		final int ok = JOptionPane.showConfirmDialog(this, TextUtils.getText("ClipboardHistory.clear.confirm"),
				TextUtils.getText("ClipboardHistory.clear.title"), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (ok != JOptionPane.OK_OPTION) {
			return;
		}
		ClipboardHistoryService.getInstance().clearAll();
		reloadAsync(true);
	}

	private void showStatsReport() {
		try {
			final ClipboardHistoryService service = ClipboardHistoryService.getInstance();
			final ReportViewModel view = new ReportViewModel(TextUtils.getText("ClipboardHistory.stats.title"),
					TextUtils.getText("ClipboardHistory.stats.subtitle"));
			view.decision = TextUtils.getText("ClipboardHistory.stats.decision");
			view.dataSource = service.getDbFile().getAbsolutePath();
			view.addKpi(TextUtils.getText("ClipboardHistory.stats.kpi.unique"), String.valueOf(service.count()), "");
			view.addKpi(TextUtils.getText("ClipboardHistory.stats.kpi.hits"), String.valueOf(service.sumHits()),
					TextUtils.getText("ClipboardHistory.stats.kpi.hitsHint"));
			view.addKpi(TextUtils.getText("ClipboardHistory.stats.kpi.dbSize"), formatBytes(service.getDbFileBytes()),
					"");

			final Map byDay = service.hitsByDay(14);
			final ReportChartSeries trend = new ReportChartSeries(TextUtils.getText("ClipboardHistory.stats.chart.trend"),
					ReportChartSeries.TYPE_BAR);
			final Iterator it = byDay.entrySet().iterator();
			while (it.hasNext()) {
				final Map.Entry entry = (Map.Entry) it.next();
				final long dayTs = ((Number) entry.getKey()).longValue();
				final long hits = ((Number) entry.getValue()).longValue();
				trend.add(DAY.format(new Date(dayTs)), (double) hits);
			}
			view.addChart(trend);

			final ReportChartSeries topPie = new ReportChartSeries(
					TextUtils.getText("ClipboardHistory.stats.chart.top"), ReportChartSeries.TYPE_PIE);
			view.addDetail(TextUtils.getText("ClipboardHistory.stats.detail.topHeader"));
			final List top = service.topByHits(15);
			for (int i = 0; i < top.size(); i++) {
				final ClipboardHistoryEntry entry = (ClipboardHistoryEntry) top.get(i);
				topPie.add(trimLabel(entry.preview(18), 14), (double) entry.hitCount);
				view.addDetail("×" + entry.hitCount + " · " + TIME.format(new Date(entry.lastTs)) + " · "
						+ entry.preview(100));
			}
			view.addChart(topPie);
			if (service.count() == 0) {
				view.emptyHint = TextUtils.getText("ClipboardHistory.stats.emptyHint");
			}

			final ReportViewportService viewport = ReportViewportService.get();
			if (viewport == null) {
				JOptionPane.showMessageDialog(this, buildPlainStatsText(view),
						TextUtils.getText("ClipboardHistory.stats.title"), JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			ReportNodeSpec tree = null;
			try {
				tree = ReportEngine.toTree(view);
			}
			catch (Exception e) {
				LogUtils.warn("Clipboard stats toTree failed", e);
			}
			viewport.showReport(view, tree);
			statusLabel.setText(TextUtils.getText("ClipboardHistory.status.statsOpened"));
			statusLabel.setForeground(DocearUiTheme.ACCENT_DEEP);
		}
		catch (Exception ex) {
			JOptionPane.showMessageDialog(this, TextUtils.format("ClipboardHistory.stats.failed", ex.getMessage()),
					TextUtils.getText("ClipboardHistory.dialog.title"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private static String buildPlainStatsText(final ReportViewModel view) {
		final StringBuffer sb = new StringBuffer();
		sb.append(view.title).append('\n').append(view.subtitle).append("\n\n");
		for (int i = 0; i < view.kpis.size(); i++) {
			final Object kpi = view.kpis.get(i);
			if (kpi instanceof ReportKpi) {
				final ReportKpi k = (ReportKpi) kpi;
				sb.append(k.label).append(": ").append(k.value).append('\n');
			}
		}
		return sb.toString();
	}

	private ClipboardHistoryEntry selectedEntry() {
		final Object value = list.getSelectedValue();
		return value instanceof ClipboardHistoryEntry ? (ClipboardHistoryEntry) value : null;
	}

	private static String formatBytes(final long bytes) {
		if (bytes < 1024L) {
			return bytes + " B";
		}
		if (bytes < 1024L * 1024L) {
			return (bytes / 1024L) + " KB";
		}
		final double mb = bytes / (1024.0 * 1024.0);
		return String.valueOf(Math.round(mb * 10.0) / 10.0) + " MB";
	}

	private static String escape(final String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String trimLabel(final String text, final int max) {
		if (text == null) {
			return "";
		}
		return text.length() <= max ? text : text.substring(0, max) + "…";
	}

	private static JButton softButton(final String text, final Runnable action) {
		final JButton b = DocearUiTheme.softButton(text);
		b.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				action.run();
			}
		});
		return b;
	}

	private static JButton primaryButton(final String text, final Runnable action) {
		final JButton b = DocearUiTheme.primaryButton(text);
		b.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				action.run();
			}
		});
		return b;
	}
}
