package org.docear.plugin.mcp.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import org.docear.plugin.mcp.Activator;
import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.audit.McpAuditService;
import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;

/**
 * Shows MCP server status and recent audit events / traces from audit.db.
 */
public final class McpStatusAuditDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static final int EVENT_LIMIT = 100;
	private static final int TRACE_LIMIT = 80;

	private final JLabel statusBadge = new JLabel(" ");
	private final JLabel endpointLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel healthLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel errorLabel = DocearUiTheme.mutedLabel(" ");
	private final JLabel auditMetaLabel = DocearUiTheme.mutedLabel(" ");
	private final DefaultTableModel eventModel;
	private final DefaultTableModel traceModel;
	private final JTable eventTable;
	private final JTable traceTable;
	private final JTextArea detailArea = new JTextArea();
	private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private List<Map<String, Object>> eventRows = java.util.Collections.emptyList();
	private List<Map<String, Object>> traceRows = java.util.Collections.emptyList();

	private McpStatusAuditDialog(final Frame owner) {
		super(owner, TextUtils.getText("McpStatusAuditAction.text"), true);
		eventModel = new DefaultTableModel(new Object[] {
		        TextUtils.getText("McpStatusAuditAction.col.time"),
		        TextUtils.getText("McpStatusAuditAction.col.actor"),
		        TextUtils.getText("McpStatusAuditAction.col.action"),
		        TextUtils.getText("McpStatusAuditAction.col.intent"),
		        TextUtils.getText("McpStatusAuditAction.col.ok"),
		        TextUtils.getText("McpStatusAuditAction.col.ms"),
		        TextUtils.getText("McpStatusAuditAction.col.question") }, 0) {
			private static final long serialVersionUID = 1L;

			public boolean isCellEditable(final int row, final int column) {
				return false;
			}
		};
		traceModel = new DefaultTableModel(new Object[] {
		        TextUtils.getText("McpStatusAuditAction.col.time"),
		        TextUtils.getText("McpStatusAuditAction.col.actor"),
		        TextUtils.getText("McpStatusAuditAction.col.calls"),
		        TextUtils.getText("McpStatusAuditAction.col.question"),
		        TextUtils.getText("McpStatusAuditAction.col.actions") }, 0) {
			private static final long serialVersionUID = 1L;

			public boolean isCellEditable(final int row, final int column) {
				return false;
			}
		};
		eventTable = new JTable(eventModel);
		traceTable = new JTable(traceModel);
		buildUi();
		setSize(920, 620);
		setMinimumSize(new Dimension(720, 480));
		setLocationRelativeTo(owner);
		refreshAll();
	}

	public static void show(final Frame owner) {
		final Frame frame = owner != null ? owner
		        : Controller.getCurrentController().getViewController().getFrame();
		final McpStatusAuditDialog dialog = new McpStatusAuditDialog(frame);
		dialog.setVisible(true);
	}

	private void buildUi() {
		getContentPane().setBackground(DocearUiTheme.CANVAS);
		final JPanel root = new JPanel(new BorderLayout(0, 10));
		DocearUiTheme.styleCanvas(root);
		root.setBorder(DocearUiTheme.pageBorder());

		root.add(buildStatusPanel(), BorderLayout.NORTH);

		final JTabbedPane tabs = new JTabbedPane();
		tabs.setFont(DocearUiTheme.font(12f, Font.BOLD));
		tabs.addTab(TextUtils.getText("McpStatusAuditAction.tab.events"), buildEventsPane());
		tabs.addTab(TextUtils.getText("McpStatusAuditAction.tab.traces"), buildTracesPane());
		root.add(tabs, BorderLayout.CENTER);

		final JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		DocearUiTheme.styleCanvas(south);
		final JButton refresh = DocearUiTheme.softButton(TextUtils.getText("McpStatusAuditAction.refresh"));
		final JButton close = DocearUiTheme.primaryButton(TextUtils.getText("ok"));
		refresh.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				refreshAll();
			}
		});
		close.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				dispose();
			}
		});
		south.add(refresh);
		south.add(close);
		root.add(south, BorderLayout.SOUTH);
		setContentPane(root);
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

		final JPanel textCol = new JPanel(new GridBagLayout());
		textCol.setOpaque(false);
		final GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(0, 0, 4, 0);
		textCol.add(statusBadge, c);
		c.gridy = 1;
		textCol.add(endpointLabel, c);
		c.gridy = 2;
		textCol.add(healthLabel, c);
		c.gridy = 3;
		textCol.add(auditMetaLabel, c);
		c.gridy = 4;
		textCol.add(errorLabel, c);
		card.add(textCol, BorderLayout.CENTER);

		final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		actions.setOpaque(false);
		final JButton restart = DocearUiTheme.primaryButton(TextUtils.getText("McpStatusAuditAction.restart"));
		final JButton refreshStatus = DocearUiTheme.softButton(TextUtils.getText("McpStatusAuditAction.refresh_status"));
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
		actions.add(refreshStatus);
		actions.add(restart);
		card.add(actions, BorderLayout.EAST);
		return card;
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
		detailScroll.setBorder(BorderFactory.createTitledBorder(DocearUiTheme.hairlineBorder(),
		        TextUtils.getText("McpStatusAuditAction.detail")));

		final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailScroll);
		split.setResizeWeight(0.55);
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

	private void refreshAll() {
		refreshStatusOnly();
		reloadEvents();
		reloadTraces();
	}

	private void refreshStatusOnly() {
		final boolean enabled = DocearMcpConfig.isEnabled();
		final boolean running = Activator.isServerRunning();
		final boolean healthy = Activator.probeHealth();
		final String endpoint = Activator.getEndpoint();
		endpointLabel.setText(TextUtils.format("McpStatusAuditAction.endpoint", endpoint));

		if (!enabled) {
			statusBadge.setText(TextUtils.getText("McpStatusAuditAction.status.disabled"));
			statusBadge.setForeground(DocearUiTheme.TEXT_MUTED);
			healthLabel.setText(TextUtils.getText("McpStatusAuditAction.health.off"));
		}
		else if (running && healthy) {
			statusBadge.setText(TextUtils.getText("McpStatusAuditAction.status.running"));
			statusBadge.setForeground(DocearUiTheme.SUCCESS);
			healthLabel.setText(TextUtils.getText("McpStatusAuditAction.health.ok"));
		}
		else if (running) {
			statusBadge.setText(TextUtils.getText("McpStatusAuditAction.status.listening"));
			statusBadge.setForeground(DocearUiTheme.WARNING);
			healthLabel.setText(TextUtils.getText("McpStatusAuditAction.health.bad"));
		}
		else {
			statusBadge.setText(TextUtils.getText("McpStatusAuditAction.status.stopped"));
			statusBadge.setForeground(DocearUiTheme.DANGER);
			healthLabel.setText(TextUtils.getText("McpStatusAuditAction.health.bad"));
		}

		final String err = Activator.getLastError();
		errorLabel.setText(err.length() == 0 ? " " : TextUtils.format("McpStatusAuditAction.last_error", err));

		final boolean auditOn = DocearMcpConfig.isAuditEnabled();
		final int count = McpAuditService.countAuditEvents();
		final int pending = McpAuditService.pendingAuditCount();
		auditMetaLabel.setText(TextUtils.format("McpStatusAuditAction.audit_meta",
		        auditOn ? TextUtils.getText("McpStatusAuditAction.audit.on")
		                : TextUtils.getText("McpStatusAuditAction.audit.off"),
		        Integer.valueOf(count), Integer.valueOf(pending), DocearMcpConfig.getAuditDbFile().getAbsolutePath()));
	}

	private void reloadEvents() {
		eventRows = McpAuditService.listAuditEventsForUi(EVENT_LIMIT);
		eventModel.setRowCount(0);
		for (int i = 0; i < eventRows.size(); i++) {
			final Map<String, Object> row = eventRows.get(i);
			eventModel.addRow(new Object[] {
			        formatTs(row.get("ts")),
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
	}

	private void reloadTraces() {
		traceRows = McpAuditService.listAuditTracesForUi(TRACE_LIMIT);
		traceModel.setRowCount(0);
		for (int i = 0; i < traceRows.size(); i++) {
			final Map<String, Object> row = traceRows.get(i);
			traceModel.addRow(new Object[] {
			        formatTs(row.get("lastTs")),
			        str(row.get("actor")),
			        Integer.valueOf(intVal(row.get("callCount"))),
			        str(row.get("questionSummary")),
			        str(row.get("actions")) });
		}
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
		sb.append(TextUtils.getText("McpStatusAuditAction.detail.time")).append(": ").append(formatTs(row.get("ts")))
		        .append('\n');
		sb.append(TextUtils.getText("McpStatusAuditAction.col.actor")).append(": ").append(str(row.get("actor")))
		        .append('\n');
		sb.append(TextUtils.getText("McpStatusAuditAction.col.action")).append(": ").append(str(row.get("action")))
		        .append('\n');
		sb.append(TextUtils.getText("McpStatusAuditAction.col.intent")).append(": ").append(str(row.get("intent")))
		        .append('\n');
		sb.append("traceId: ").append(str(row.get("traceId"))).append('\n');
		sb.append(TextUtils.getText("McpStatusAuditAction.col.question")).append(": ")
		        .append(str(row.get("questionSummary"))).append('\n');
		sb.append(TextUtils.getText("McpStatusAuditAction.detail.goal")).append(": ")
		        .append(str(row.get("operationGoal"))).append('\n');
		sb.append(TextUtils.getText("McpStatusAuditAction.col.ok")).append(": ")
		        .append(bool(row.get("success")) ? "OK" : "FAIL").append("  ")
		        .append(longVal(row.get("durationMs"))).append(" ms\n");
		final String error = str(row.get("error"));
		if (error.length() > 0) {
			sb.append(TextUtils.getText("McpStatusAuditAction.detail.error")).append(": ").append(error).append('\n');
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
		// Switch to events tab is not automatic; show detail in events detail by filtering selection.
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				for (int i = 0; i < eventRows.size(); i++) {
					if (traceId.equals(str(eventRows.get(i).get("traceId")))) {
						final int view = eventTable.convertRowIndexToView(i);
						if (view >= 0) {
							eventTable.getSelectionModel().setSelectionInterval(view, view);
							eventTable.scrollRectToVisible(eventTable.getCellRect(view, 0, true));
						}
						break;
					}
				}
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
