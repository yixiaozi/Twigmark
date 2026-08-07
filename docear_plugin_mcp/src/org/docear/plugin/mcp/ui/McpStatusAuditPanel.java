package org.docear.plugin.mcp.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Frame;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import org.docear.plugin.mcp.Activator;
import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.audit.McpAuditQuery;
import org.docear.plugin.mcp.audit.McpAuditService;
import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;

/**
 * MCP status + multi-machine audit explorer (search, filters, stats, import/export).
 */
public final class McpStatusAuditPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private final JLabel statusBadge = new JLabel(" ");
	private final JLabel endpointLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel healthLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel errorLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel auditMetaLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel machineLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel resultMetaLabel = DocearUiTheme.mutedLabel(" ");

	private final JTextField searchField = new JTextField();
	private final JCheckBox searchPayloadBox = new JCheckBox();
	private final JComboBox machineCombo = new JComboBox();
	private final JComboBox actorCombo = new JComboBox();
	private final JComboBox actionCombo = new JComboBox();
	private final JComboBox intentCombo = new JComboBox();
	private final JComboBox resultCombo = new JComboBox();
	private final JComboBox rangeCombo = new JComboBox();
	private final JSpinner minMsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 3600000, 100));
	private final JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(500, 50, 20000, 50));

	private final DefaultTableModel eventModel;
	private final DefaultTableModel traceModel;
	private final DefaultTableModel machineModel;
	private final DefaultTableModel actionStatsModel;
	private final DefaultTableModel dayStatsModel;
	private final DefaultTableModel slowModel;
	private final JTable eventTable;
	private final JTable traceTable;
	private final JTable machineTable;
	private final JTable actionStatsTable;
	private final JTable dayStatsTable;
	private final JTable slowTable;
	private final JTextArea detailArea = new JTextArea();
	private final JTextArea statsSummaryArea = new JTextArea();
	private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private final JTabbedPane tabs = new JTabbedPane();

	private List<Map<String, Object>> eventRows = java.util.Collections.emptyList();
	private List<Map<String, Object>> traceRows = java.util.Collections.emptyList();

	private Runnable onClose;

	public McpStatusAuditPanel() {
		super(new BorderLayout());
		eventModel = nonEditableModel(new String[] {
		        t("col.time"), t("col.machine"), t("col.actor"), t("col.action"), t("col.intent"), t("col.ok"),
		        t("col.ms"), t("col.question") });
		traceModel = nonEditableModel(new String[] {
		        t("col.time"), t("col.machine"), t("col.actor"), t("col.calls"), t("col.question"), t("col.actions") });
		machineModel = nonEditableModel(new String[] {
		        t("col.machine"), t("col.machineId"), t("col.calls"), t("col.ok"), t("col.fail"), t("col.avgMs"),
		        t("col.maxMs"), t("col.last") });
		actionStatsModel = nonEditableModel(new String[] {
		        t("col.action"), t("col.intent"), t("col.avgMs"), t("col.calls"), t("col.ok"), t("col.fail"),
		        t("col.maxMs"), t("col.totalMs") });
		dayStatsModel = nonEditableModel(new String[] {
		        t("col.day"), t("col.calls"), t("col.ok"), t("col.fail"), t("col.avgMs"), t("col.maxMs") });
		slowModel = nonEditableModel(new String[] {
		        t("col.time"), t("col.machine"), t("col.action"), t("col.ms"), t("col.ok"), t("col.question") });
		eventTable = new JTable(eventModel);
		traceTable = new JTable(traceModel);
		machineTable = new JTable(machineModel);
		actionStatsTable = new JTable(actionStatsModel);
		dayStatsTable = new JTable(dayStatsModel);
		slowTable = new JTable(slowModel);
		buildUi();
		refreshFilterChoices();
		// Heavy DB / health probe off the EDT.
		refreshAllAsync();
	}

	public void setOnClose(final Runnable onClose) {
		this.onClose = onClose;
	}

	public static McpStatusAuditPanel create() {
		return new McpStatusAuditPanel();
	}

	private static DefaultTableModel nonEditableModel(final String[] cols) {
		return new DefaultTableModel(cols, 0) {
			private static final long serialVersionUID = 1L;

			public boolean isCellEditable(final int row, final int column) {
				return false;
			}
		};
	}

	private static String t(final String key) {
		return TextUtils.getText("McpStatusAuditAction." + key);
	}

	private void buildUi() {
		setBackground(DocearUiTheme.CANVAS);
		final JPanel root = new JPanel(new BorderLayout(0, 8));
		DocearUiTheme.styleCanvas(root);
		root.setBorder(DocearUiTheme.pageBorder());

		final JPanel north = new JPanel(new BorderLayout(0, 8));
		DocearUiTheme.styleCanvas(north);
		north.add(buildStatusPanel(), BorderLayout.NORTH);
		north.add(buildFilterPanel(), BorderLayout.CENTER);
		root.add(north, BorderLayout.NORTH);

		tabs.setFont(DocearUiTheme.font(12f, Font.BOLD));
		tabs.addTab(t("tab.stats"), buildStatsPane());
		tabs.addTab(t("tab.events"), buildEventsPane());
		tabs.addTab(t("tab.traces"), buildTracesPane());
		tabs.addTab(t("tab.machines"), buildMachinesPane());
		tabs.addTab(t("tab.slow"), buildSlowPane());
		root.add(tabs, BorderLayout.CENTER);

		final JPanel south = new JPanel(new BorderLayout());
		DocearUiTheme.styleCanvas(south);
		resultMetaLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
		south.add(resultMetaLabel, BorderLayout.WEST);
		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		DocearUiTheme.styleCanvas(buttons);
		final JButton refresh = DocearUiTheme.softButton(t("refresh"));
		final JButton close = DocearUiTheme.primaryButton(TextUtils.getText("ok"));
		refresh.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				refreshAllAsync();
			}
		});
		close.setText(TextUtils.getText("McpStatusAuditAction.back_map", "返回导图"));
		close.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (onClose != null) {
					onClose.run();
				}
			}
		});
		buttons.add(refresh);
		buttons.add(close);
		south.add(buttons, BorderLayout.EAST);
		root.add(south, BorderLayout.SOUTH);
		add(root, BorderLayout.CENTER);
	}

	private JPanel buildStatusPanel() {
		final JPanel card = new JPanel(new BorderLayout(8, 6));
		DocearUiTheme.styleSurface(card);
		card.setBorder(BorderFactory.createCompoundBorder(DocearUiTheme.hairlineBorder(), new EmptyBorder(10, 12, 10, 12)));

		statusBadge.setFont(DocearUiTheme.font(15f, Font.BOLD));
		statusBadge.setForeground(DocearUiTheme.TEXT);
		endpointLabel.setFont(DocearUiTheme.font(12f));
		healthLabel.setFont(DocearUiTheme.font(12f));
		errorLabel.setFont(DocearUiTheme.font(12f));
		errorLabel.setForeground(DocearUiTheme.DANGER);
		auditMetaLabel.setFont(DocearUiTheme.font(12f));
		machineLabel.setFont(DocearUiTheme.font(12f));

		final JPanel textCol = new JPanel(new GridBagLayout());
		textCol.setOpaque(false);
		final GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(0, 0, 3, 0);
		textCol.add(statusBadge, c);
		c.gridy++;
		textCol.add(endpointLabel, c);
		c.gridy++;
		textCol.add(healthLabel, c);
		c.gridy++;
		textCol.add(machineLabel, c);
		c.gridy++;
		textCol.add(auditMetaLabel, c);
		c.gridy++;
		textCol.add(errorLabel, c);
		card.add(textCol, BorderLayout.CENTER);

		final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		actions.setOpaque(false);
		final JButton copyMachine = DocearUiTheme.softButton(t("copy_machine"));
		final JButton restart = DocearUiTheme.primaryButton(t("restart"));
		final JButton refreshStatus = DocearUiTheme.softButton(t("refresh_status"));
		copyMachine.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final String id = McpAuditService.getLocalMachineId();
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(id), null);
			}
		});
		restart.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				Activator.restartServer();
				refreshAll();
			}
		});
		refreshStatus.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				refreshStatusOnly();
			}
		});
		actions.add(copyMachine);
		actions.add(refreshStatus);
		actions.add(restart);
		card.add(actions, BorderLayout.EAST);
		return card;
	}

	private JPanel buildFilterPanel() {
		final JPanel card = new JPanel(new GridBagLayout());
		DocearUiTheme.styleSurface(card);
		card.setBorder(BorderFactory.createCompoundBorder(DocearUiTheme.hairlineBorder(), new EmptyBorder(8, 10, 8, 10)));

		searchPayloadBox.setText(t("search_payload"));
		searchPayloadBox.setOpaque(false);
		searchField.setColumns(28);
		searchField.addKeyListener(new KeyAdapter() {
			public void keyPressed(final KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					refreshAll();
				}
			}
		});

		fillCombo(rangeCombo, new String[] { t("range.all"), t("range.1h"), t("range.24h"), t("range.7d"), t("range.30d") });
		fillCombo(resultCombo, new String[] { t("result.all"), "OK", "FAIL" });
		rangeCombo.setSelectedIndex(3);

		int x = 0;
		int y = 0;
		x = addFilter(card, x, y, t("search"), searchField, 2);
		x = addFilter(card, x, y, "", searchPayloadBox, 1);
		x = addFilter(card, x, y, t("col.machine"), machineCombo, 1);
		y++;
		x = 0;
		x = addFilter(card, x, y, t("col.actor"), actorCombo, 1);
		x = addFilter(card, x, y, t("col.action"), actionCombo, 1);
		x = addFilter(card, x, y, t("col.intent"), intentCombo, 1);
		x = addFilter(card, x, y, t("col.ok"), resultCombo, 1);
		y++;
		x = 0;
		x = addFilter(card, x, y, t("range"), rangeCombo, 1);
		x = addFilter(card, x, y, t("min_ms"), minMsSpinner, 1);
		x = addFilter(card, x, y, t("limit"), limitSpinner, 1);

		final JButton searchBtn = DocearUiTheme.primaryButton(t("search_btn"));
		final JButton resetBtn = DocearUiTheme.softButton(t("reset"));
		searchBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				refreshAll();
			}
		});
		resetBtn.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				resetFilters();
				refreshAll();
			}
		});
		final GridBagConstraints c = new GridBagConstraints();
		c.gridx = x;
		c.gridy = y;
		c.insets = new Insets(4, 8, 4, 0);
		card.add(searchBtn, c);
		c.gridx++;
		card.add(resetBtn, c);
		return card;
	}

	private int addFilter(final JPanel card, final int x, final int y, final String label, final java.awt.Component comp,
	    final int span) {
		final GridBagConstraints c = new GridBagConstraints();
		c.gridy = y;
		c.insets = new Insets(3, 4, 3, 4);
		c.anchor = GridBagConstraints.WEST;
		int next = x;
		if (label != null && label.length() > 0) {
			c.gridx = next++;
			card.add(DocearUiTheme.mutedLabel(label), c);
		}
		c.gridx = next;
		c.gridwidth = span;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		card.add(comp, c);
		return next + span;
	}

	private void fillCombo(final JComboBox combo, final String[] items) {
		final DefaultComboBoxModel model = new DefaultComboBoxModel();
		for (int i = 0; i < items.length; i++) {
			model.addElement(items[i]);
		}
		combo.setModel(model);
	}

	private JPanel buildEventsPane() {
		styleTable(eventTable);
		eventTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		eventTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(final ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					showSelectedEvent();
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

		final JScrollPane tableScroll = new JScrollPane(eventTable);
		DocearUiTheme.styleScrollPane(tableScroll);
		final JScrollPane detailScroll = new JScrollPane(detailArea);
		DocearUiTheme.styleScrollPane(detailScroll);
		detailScroll.setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(), t("detail")));

		final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailScroll);
		split.setResizeWeight(0.62);
		split.setBorder(null);
		final JPanel wrap = new JPanel(new BorderLayout());
		DocearUiTheme.styleCanvas(wrap);
		wrap.add(split, BorderLayout.CENTER);
		return wrap;
	}

	private JPanel buildTracesPane() {
		styleTable(traceTable);
		traceTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		traceTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(final ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					showSelectedTrace();
				}
			}
		});
		final JScrollPane scroll = new JScrollPane(traceTable);
		DocearUiTheme.styleScrollPane(scroll);
		final JPanel wrap = new JPanel(new BorderLayout());
		DocearUiTheme.styleCanvas(wrap);
		wrap.add(scroll, BorderLayout.CENTER);
		return wrap;
	}

	private JPanel buildStatsPane() {
		statsSummaryArea.setEditable(false);
		statsSummaryArea.setFont(DocearUiTheme.font(13f));
		statsSummaryArea.setBackground(DocearUiTheme.SURFACE);
		statsSummaryArea.setBorder(new EmptyBorder(10, 12, 10, 12));
		styleTable(actionStatsTable);
		styleTable(dayStatsTable);
		final JScrollPane summaryScroll = new JScrollPane(statsSummaryArea);
		DocearUiTheme.styleScrollPane(summaryScroll);
		summaryScroll.setPreferredSize(new Dimension(200, 120));
		final JTabbedPane inner = new JTabbedPane();
		inner.addTab(t("stats.by_action"), new JScrollPane(actionStatsTable));
		inner.addTab(t("stats.by_day"), new JScrollPane(dayStatsTable));
		final JPanel wrap = new JPanel(new BorderLayout(0, 8));
		DocearUiTheme.styleCanvas(wrap);
		wrap.add(summaryScroll, BorderLayout.NORTH);
		wrap.add(inner, BorderLayout.CENTER);
		return wrap;
	}

	private JPanel buildMachinesPane() {
		styleTable(machineTable);
		final JScrollPane scroll = new JScrollPane(machineTable);
		DocearUiTheme.styleScrollPane(scroll);
		final JPanel wrap = new JPanel(new BorderLayout());
		DocearUiTheme.styleCanvas(wrap);
		wrap.add(scroll, BorderLayout.CENTER);
		return wrap;
	}

	private JPanel buildSlowPane() {
		styleTable(slowTable);
		slowTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		slowTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(final ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					final int viewRow = slowTable.getSelectedRow();
					if (viewRow < 0) {
						return;
					}
					final int modelRow = slowTable.convertRowIndexToModel(viewRow);
					if (modelRow >= 0 && modelRow < eventRows.size()) {
						// slow list uses its own rows; jump by selecting matching event if present
					}
				}
			}
		});
		final JScrollPane scroll = new JScrollPane(slowTable);
		DocearUiTheme.styleScrollPane(scroll);
		final JPanel wrap = new JPanel(new BorderLayout());
		DocearUiTheme.styleCanvas(wrap);
		wrap.add(scroll, BorderLayout.CENTER);
		return wrap;
	}

	private void styleTable(final JTable table) {
		table.setFont(DocearUiTheme.font(12f));
		table.setRowHeight(24);
		table.setShowGrid(false);
		table.setIntercellSpacing(new Dimension(0, 0));
		table.setSelectionBackground(DocearUiTheme.ACCENT_WASH);
		table.setSelectionForeground(DocearUiTheme.TEXT);
		table.getTableHeader().setFont(DocearUiTheme.font(12f, Font.BOLD));
		table.setFillsViewportHeight(true);
		table.setAutoCreateRowSorter(true);
	}

	private void resetFilters() {
		searchField.setText("");
		searchPayloadBox.setSelected(false);
		if (machineCombo.getItemCount() > 0) {
			machineCombo.setSelectedIndex(0);
		}
		if (actorCombo.getItemCount() > 0) {
			actorCombo.setSelectedIndex(0);
		}
		if (actionCombo.getItemCount() > 0) {
			actionCombo.setSelectedIndex(0);
		}
		if (intentCombo.getItemCount() > 0) {
			intentCombo.setSelectedIndex(0);
		}
		resultCombo.setSelectedIndex(0);
		rangeCombo.setSelectedIndex(3);
		minMsSpinner.setValue(Integer.valueOf(0));
		limitSpinner.setValue(Integer.valueOf(500));
	}

	private McpAuditQuery currentQuery() {
		final McpAuditQuery q = new McpAuditQuery();
		q.text = searchField.getText() != null ? searchField.getText().trim() : "";
		q.searchPayload = searchPayloadBox.isSelected();
		q.machineId = comboValue(machineCombo, true);
		q.actor = comboValue(actorCombo, false);
		q.action = comboValue(actionCombo, false);
		q.intent = comboValue(intentCombo, false);
		final String result = String.valueOf(resultCombo.getSelectedItem());
		if ("OK".equalsIgnoreCase(result)) {
			q.result = "ok";
		}
		else if ("FAIL".equalsIgnoreCase(result)) {
			q.result = "fail";
		}
		q.minDurationMs = ((Number) minMsSpinner.getValue()).longValue();
		q.limit = ((Number) limitSpinner.getValue()).intValue();
		q.sinceMillis = rangeToSince(rangeCombo.getSelectedIndex());
		return q;
	}

	private String comboValue(final JComboBox combo, final boolean machine) {
		final Object sel = combo.getSelectedItem();
		if (sel == null) {
			return "";
		}
		final String s = String.valueOf(sel);
		if (s.length() == 0 || s.startsWith("(") || s.equals(t("filter.all"))) {
			return "";
		}
		if (machine) {
			// "name [id]"
			final int lb = s.lastIndexOf('[');
			final int rb = s.lastIndexOf(']');
			if (lb >= 0 && rb > lb) {
				return s.substring(lb + 1, rb);
			}
		}
		return s;
	}

	private long rangeToSince(final int index) {
		final long now = System.currentTimeMillis();
		if (index == 1) {
			return now - 3600L * 1000L;
		}
		if (index == 2) {
			return now - 24L * 3600L * 1000L;
		}
		if (index == 3) {
			return now - 7L * 24L * 3600L * 1000L;
		}
		if (index == 4) {
			return now - 30L * 24L * 3600L * 1000L;
		}
		return 0L;
	}

	private void refreshFilterChoices() {
		final String keepMachine = comboValue(machineCombo, true);
		final String keepActor = comboValue(actorCombo, false);
		final String keepAction = comboValue(actionCombo, false);
		final String keepIntent = comboValue(intentCombo, false);

		final DefaultComboBoxModel machineModel = new DefaultComboBoxModel();
		machineModel.addElement(t("filter.all"));
		final List<Map<String, Object>> machines = McpAuditService.listMachinesForUi();
		for (int i = 0; i < machines.size(); i++) {
			final Map<String, Object> m = machines.get(i);
			machineModel.addElement(str(m.get("machineName")) + " [" + str(m.get("machineId")) + "]");
		}
		machineCombo.setModel(machineModel);
		selectComboByContains(machineCombo, keepMachine);

		reloadStringCombo(actorCombo, McpAuditService.distinctForUi("actor"), keepActor);
		reloadStringCombo(actionCombo, McpAuditService.distinctForUi("action"), keepAction);
		reloadStringCombo(intentCombo, McpAuditService.distinctForUi("intent"), keepIntent);
	}

	private void reloadStringCombo(final JComboBox combo, final List<String> values, final String keep) {
		final DefaultComboBoxModel model = new DefaultComboBoxModel();
		model.addElement(t("filter.all"));
		for (int i = 0; i < values.size(); i++) {
			model.addElement(values.get(i));
		}
		combo.setModel(model);
		if (keep != null && keep.length() > 0) {
			combo.setSelectedItem(keep);
		}
	}

	private void selectComboByContains(final JComboBox combo, final String id) {
		if (id == null || id.length() == 0) {
			combo.setSelectedIndex(0);
			return;
		}
		for (int i = 0; i < combo.getItemCount(); i++) {
			if (String.valueOf(combo.getItemAt(i)).indexOf(id) >= 0) {
				combo.setSelectedIndex(i);
				return;
			}
		}
		combo.setSelectedIndex(0);
	}

	private void refreshAllAsync() {
		final Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					// Network health probe off the EDT (was a major freeze source).
					Activator.probeHealth();
					McpAuditService.countAuditEvents();
				}
				catch (Exception ignore) {
				}
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						refreshAll();
					}
				});
			}
		}, "docear-mcp-audit-refresh");
		t.setDaemon(true);
		t.start();
	}

	private void refreshAll() {
		refreshStatusOnly(false);
		refreshFilterChoices();
		final McpAuditQuery q = currentQuery();
		reloadEvents(q);
		reloadTraces(q);
		reloadStats(q);
		reloadMachines(q);
		reloadSlow(q);
	}

	private void refreshStatusOnly() {
		refreshStatusOnly(true);
	}

	private void refreshStatusOnly(final boolean probeNow) {
		final boolean enabled = DocearMcpConfig.isEnabled();
		final boolean running = Activator.isServerRunning();
		final boolean healthy = probeNow ? Activator.probeHealth() : Activator.isServerRunning();
		final String endpoint = Activator.getEndpoint();
		endpointLabel.setText(TextUtils.format("McpStatusAuditAction.endpoint", endpoint));

		if (!enabled) {
			statusBadge.setText(t("status.disabled"));
			statusBadge.setForeground(DocearUiTheme.TEXT_MUTED);
			healthLabel.setText(t("health.off"));
		}
		else if (running && healthy) {
			statusBadge.setText(t("status.running"));
			statusBadge.setForeground(DocearUiTheme.SUCCESS);
			healthLabel.setText(t("health.ok"));
		}
		else if (running) {
			statusBadge.setText(t("status.listening"));
			statusBadge.setForeground(DocearUiTheme.WARNING);
			healthLabel.setText(t("health.bad"));
		}
		else {
			statusBadge.setText(t("status.stopped"));
			statusBadge.setForeground(DocearUiTheme.DANGER);
			healthLabel.setText(t("health.bad"));
		}

		final String err = Activator.getLastError();
		errorLabel.setText(err.length() == 0 ? " " : TextUtils.format("McpStatusAuditAction.last_error", err));

		final boolean auditOn = DocearMcpConfig.isAuditEnabled();
		final int count = McpAuditService.countAuditEvents();
		final int pending = McpAuditService.pendingAuditCount();
		final int dbCount = McpAuditService.loadedAuditDatabaseCount();
		auditMetaLabel.setText(TextUtils.format("McpStatusAuditAction.audit_meta",
		        auditOn ? t("audit.on") : t("audit.off"), Integer.valueOf(count), Integer.valueOf(pending),
		        Integer.valueOf(dbCount), DocearMcpConfig.getAuditDataDir().getAbsolutePath()));
		machineLabel.setText(TextUtils.format("McpStatusAuditAction.machine_meta", McpAuditService.getLocalMachineName(),
		        McpAuditService.getLocalMachineId(), DocearMcpConfig.getAuditDbFile().getName()));
	}

	private void reloadEvents(final McpAuditQuery q) {
		eventRows = McpAuditService.queryEventsForUi(q);
		eventModel.setRowCount(0);
		for (int i = 0; i < eventRows.size(); i++) {
			final Map<String, Object> row = eventRows.get(i);
			eventModel.addRow(new Object[] {
			        formatTs(row.get("ts")),
			        machineCell(row),
			        str(row.get("actor")),
			        str(row.get("action")),
			        str(row.get("intent")),
			        bool(row.get("success")) ? "OK" : "FAIL",
			        Long.valueOf(longVal(row.get("durationMs"))),
			        str(row.get("questionSummary")) });
		}
		detailArea.setText("");
		if (eventModel.getRowCount() > 0) {
			eventTable.getSelectionModel().setSelectionInterval(0, 0);
		}
		resultMetaLabel.setText(TextUtils.format("McpStatusAuditAction.result_meta",
		        Integer.valueOf(eventRows.size()), Integer.valueOf(q.limit)));
	}

	private void reloadTraces(final McpAuditQuery q) {
		traceRows = McpAuditService.queryTracesForUi(q);
		traceModel.setRowCount(0);
		for (int i = 0; i < traceRows.size(); i++) {
			final Map<String, Object> row = traceRows.get(i);
			traceModel.addRow(new Object[] {
			        formatTs(row.get("lastTs")),
			        machineCell(row),
			        str(row.get("actor")),
			        Integer.valueOf(intVal(row.get("callCount"))),
			        str(row.get("questionSummary")),
			        str(row.get("actions")) });
		}
	}

	private void reloadStats(final McpAuditQuery q) {
		final Map<String, Object> sum = McpAuditService.summarizeForUi(q);
		final StringBuilder sb = new StringBuilder();
		sb.append(t("stats.summary")).append('\n');
		sb.append(t("stats.total")).append(": ").append(intVal(sum.get("count"))).append("    ");
		sb.append(t("col.ok")).append(": ").append(intVal(sum.get("successCount"))).append("    ");
		sb.append(t("col.fail")).append(": ").append(intVal(sum.get("failCount"))).append("    ");
		sb.append(t("col.machine")).append(": ").append(intVal(sum.get("machineCount"))).append("    ");
		sb.append(t("stats.dbs")).append(": ").append(intVal(sum.get("databaseCount"))).append('\n');
		sb.append(t("col.avgMs")).append(": ").append(longVal(sum.get("avgDurationMs"))).append("    ");
		sb.append(t("col.maxMs")).append(": ").append(longVal(sum.get("maxDurationMs"))).append("    ");
		sb.append(t("col.minMs")).append(": ").append(longVal(sum.get("minDurationMs"))).append("    ");
		sb.append(t("stats.totalMs")).append(": ").append(longVal(sum.get("totalDurationMs"))).append('\n');
		sb.append(t("stats.avg_hint"));
		statsSummaryArea.setText(sb.toString());

		actionStatsModel.setRowCount(0);
		final List<Map<String, Object>> byAction = McpAuditService.statsByActionForUi(q);
		for (int i = 0; i < byAction.size(); i++) {
			final Map<String, Object> row = byAction.get(i);
			actionStatsModel.addRow(new Object[] {
			        str(row.get("action")), str(row.get("intent")),
			        Long.valueOf(longVal(row.get("avgDurationMs"))),
			        Integer.valueOf(intVal(row.get("count"))),
			        Integer.valueOf(intVal(row.get("successCount"))), Integer.valueOf(intVal(row.get("failCount"))),
			        Long.valueOf(longVal(row.get("maxDurationMs"))),
			        Long.valueOf(longVal(row.get("totalDurationMs"))) });
		}

		dayStatsModel.setRowCount(0);
		final List<Map<String, Object>> byDay = McpAuditService.statsByDayForUi(q);
		for (int i = 0; i < byDay.size(); i++) {
			final Map<String, Object> row = byDay.get(i);
			dayStatsModel.addRow(new Object[] {
			        formatTs(row.get("bucketTs")).length() >= 10 ? formatTs(row.get("bucketTs")).substring(0, 10) : "",
			        Integer.valueOf(intVal(row.get("count"))), Integer.valueOf(intVal(row.get("successCount"))),
			        Integer.valueOf(intVal(row.get("failCount"))), Long.valueOf(longVal(row.get("avgDurationMs"))),
			        Long.valueOf(longVal(row.get("maxDurationMs"))) });
		}
	}

	private void reloadMachines(final McpAuditQuery q) {
		machineModel.setRowCount(0);
		final List<Map<String, Object>> rows = McpAuditService.statsByMachineForUi(q);
		final List<Map<String, Object>> machines = McpAuditService.listMachinesForUi();
		for (int i = 0; i < rows.size(); i++) {
			final Map<String, Object> row = rows.get(i);
			final String mid = str(row.get("machineId"));
			long last = 0L;
			for (int j = 0; j < machines.size(); j++) {
				if (mid.equals(str(machines.get(j).get("machineId")))) {
					last = longVal(machines.get(j).get("lastTs"));
					break;
				}
			}
			machineModel.addRow(new Object[] {
			        str(row.get("machineName")), mid, Integer.valueOf(intVal(row.get("count"))),
			        Integer.valueOf(intVal(row.get("successCount"))), Integer.valueOf(intVal(row.get("failCount"))),
			        Long.valueOf(longVal(row.get("avgDurationMs"))), Long.valueOf(longVal(row.get("maxDurationMs"))),
			        formatTs(Long.valueOf(last)) });
		}
	}

	private void reloadSlow(final McpAuditQuery q) {
		final McpAuditQuery slowQ = new McpAuditQuery();
		slowQ.text = q.text;
		slowQ.searchPayload = q.searchPayload;
		slowQ.machineId = q.machineId;
		slowQ.actor = q.actor;
		slowQ.action = q.action;
		slowQ.intent = q.intent;
		slowQ.result = q.result;
		slowQ.sinceMillis = q.sinceMillis;
		slowQ.minDurationMs = Math.max(q.minDurationMs, 1L);
		slowQ.limit = Math.min(q.limit, 200);
		final List<Map<String, Object>> rows = McpAuditService.listSlowEventsForUi(slowQ);
		slowModel.setRowCount(0);
		for (int i = 0; i < rows.size(); i++) {
			final Map<String, Object> row = rows.get(i);
			slowModel.addRow(new Object[] {
			        formatTs(row.get("ts")), machineCell(row), str(row.get("action")),
			        Long.valueOf(longVal(row.get("durationMs"))), bool(row.get("success")) ? "OK" : "FAIL",
			        str(row.get("questionSummary")) });
		}
	}

	private String machineCell(final Map<String, Object> row) {
		final String name = str(row.get("machineName"));
		return name.length() > 0 ? name : str(row.get("machineId"));
	}

	private void showSelectedEvent() {
		final int viewRow = eventTable.getSelectedRow();
		if (viewRow < 0) {
			detailArea.setText("");
			return;
		}
		final int modelRow = eventTable.convertRowIndexToModel(viewRow);
		if (modelRow < 0 || modelRow >= eventRows.size()) {
			detailArea.setText("");
			return;
		}
		final Map<String, Object> row = eventRows.get(modelRow);
		final StringBuilder sb = new StringBuilder();
		sb.append(t("detail.time")).append(": ").append(formatTs(row.get("ts"))).append('\n');
		sb.append(t("col.machine")).append(": ").append(str(row.get("machineName"))).append(" [")
		        .append(str(row.get("machineId"))).append("]\n");
		sb.append("eventId: ").append(str(row.get("eventId"))).append('\n');
		sb.append(t("col.actor")).append(": ").append(str(row.get("actor"))).append('\n');
		sb.append(t("col.action")).append(": ").append(str(row.get("action"))).append('\n');
		sb.append(t("col.intent")).append(": ").append(str(row.get("intent"))).append('\n');
		sb.append("traceId: ").append(str(row.get("traceId"))).append('\n');
		sb.append(t("col.question")).append(": ").append(str(row.get("questionSummary"))).append('\n');
		sb.append(t("detail.goal")).append(": ").append(str(row.get("operationGoal"))).append('\n');
		sb.append(t("col.ok")).append(": ").append(bool(row.get("success")) ? "OK" : "FAIL").append("  ")
		        .append(longVal(row.get("durationMs"))).append(" ms\n");
		final String error = str(row.get("error"));
		if (error.length() > 0) {
			sb.append(t("detail.error")).append(": ").append(error).append('\n');
		}
		sb.append('\n').append("--- request ---\n").append(str(row.get("requestJson")));
		sb.append("\n\n--- response ---\n").append(str(row.get("responseJson")));
		detailArea.setText(sb.toString());
		detailArea.setCaretPosition(0);
	}

	private void showSelectedTrace() {
		final int viewRow = traceTable.getSelectedRow();
		if (viewRow < 0) {
			return;
		}
		final int modelRow = traceTable.convertRowIndexToModel(viewRow);
		if (modelRow < 0 || modelRow >= traceRows.size()) {
			return;
		}
		final Map<String, Object> row = traceRows.get(modelRow);
		final String traceId = str(row.get("traceId"));
		final String machineId = str(row.get("machineId"));
		searchField.setText(traceId);
		if (machineId.length() > 0) {
			selectComboByContains(machineCombo, machineId);
		}
		tabs.setSelectedIndex(1);
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				refreshAll();
			}
		});
	}

	private String formatTs(final Object value) {
		final long ts = longVal(value);
		if (ts <= 0L) {
			return "";
		}
		return timeFormat.format(new Date(ts));
	}

	private static String str(final Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static long longVal(final Object value) {
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		try {
			return Long.parseLong(str(value));
		}
		catch (Exception e) {
			return 0L;
		}
	}

	private static int intVal(final Object value) {
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		try {
			return Integer.parseInt(str(value));
		}
		catch (Exception e) {
			return 0;
		}
	}

	private static boolean bool(final Object value) {
		if (value instanceof Boolean) {
			return ((Boolean) value).booleanValue();
		}
		return "true".equalsIgnoreCase(str(value)) || "OK".equalsIgnoreCase(str(value));
	}
}
