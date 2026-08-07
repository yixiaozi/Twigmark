package org.freeplane.view.swing.features.pomodoro;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Date;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.ui.components.DateTimeFieldsPanel;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.text.TextController;
import org.freeplane.core.util.TextUtils;

/**
 * Edit one completed pomodoro log record (start, end, focus duration).
 */
final class PomodoroSessionEditDialog extends JDialog {
	private static final long serialVersionUID = 1L;

	private final NodeModel node;
	private final int logIndex;
	private final List originalPauses;
	private final DateTimeFieldsPanel startEditor;
	private final DateTimeFieldsPanel endEditor;
	private final JSpinner focusSpinner;
	private final JLabel previewLabel;
	private boolean applied;

	static boolean showForRecord(final Frame owner, final NodeModel node, final int logIndex,
	        final PomodoroSessionRecord record) {
		if (node == null || record == null || logIndex < 0) {
			return false;
		}
		final PomodoroSessionEditDialog dialog = new PomodoroSessionEditDialog(owner, node, logIndex, record);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
		return dialog.applied;
	}

	private PomodoroSessionEditDialog(final Frame owner, final NodeModel node, final int logIndex,
	        final PomodoroSessionRecord record) {
		super(owner, TextUtils.getText("pomodoro.session_edit.title"), true);
		this.node = node;
		this.logIndex = logIndex;
		this.originalPauses = PomodoroPauseInterval.copyOf(record.pauseIntervals);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		final JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(new EmptyBorder(10, 12, 10, 12));
		root.add(new JLabel(TextUtils.format("pomodoro.session_edit.header", escape(plain(node)))),
		        BorderLayout.NORTH);

		startEditor = new DateTimeFieldsPanel(true, new Date(record.startMs));
		endEditor = new DateTimeFieldsPanel(true, new Date(record.endMs));
		focusSpinner = new JSpinner(new javax.swing.SpinnerNumberModel(
		        Math.max(1, (int) Math.round(record.focusMs / 60000.0)), 1, 24 * 60, 1));

		final JPanel form = new JPanel();
		form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
		form.add(labeledRow(TextUtils.getText("pomodoro.session_edit.start"), startEditor));
		form.add(javax.swing.Box.createVerticalStrut(8));
		form.add(labeledRow(TextUtils.getText("pomodoro.session_edit.end"), endEditor));
		form.add(javax.swing.Box.createVerticalStrut(8));
		form.add(labeledRow(TextUtils.getText("pomodoro.session_edit.focus_min"), focusSpinner));
		form.add(javax.swing.Box.createVerticalStrut(8));
		previewLabel = new JLabel();
		previewLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		form.add(previewLabel);
		root.add(form, BorderLayout.CENTER);

		final PropertyChangeListener refresh = new PropertyChangeListener() {
			public void propertyChange(final PropertyChangeEvent evt) {
				refreshPreview();
			}
		};
		startEditor.setChangeListener(refresh);
		endEditor.setChangeListener(refresh);
		focusSpinner.getModel().addChangeListener(new javax.swing.event.ChangeListener() {
			public void stateChanged(final javax.swing.event.ChangeEvent e) {
				refreshPreview();
			}
		});
		refreshPreview();

		final JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		south.add(btn(TextUtils.getText("pomodoro.session_edit.cancel"), new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				dispose();
			}
		}));
		south.add(btn(TextUtils.getText("pomodoro.session_edit.save"), new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				apply();
			}
		}));
		root.add(south, BorderLayout.SOUTH);

		setContentPane(root);
		pack();
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				"close");
		getRootPane().getActionMap().put("close", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent e) {
				dispose();
			}
		});
	}

	private static JPanel labeledRow(final String label, final java.awt.Component field) {
		final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		row.add(new JLabel(label));
		row.add(field);
		return row;
	}

	private void refreshPreview() {
		final long startMs = startEditor.getTimeMillis();
		final long endMs = endEditor.getTimeMillis();
		final int focusMinutes = ((Number) focusSpinner.getValue()).intValue();
		if (endMs <= startMs) {
			previewLabel.setText(TextUtils.getText("pomodoro.session_edit.end_before_start"));
			return;
		}
		final long spanMs = endMs - startMs;
		final long focusMs = focusMinutes * 60L * 1000L;
		if (focusMs > spanMs) {
			previewLabel.setText(TextUtils.format("pomodoro.session_edit.focus_too_long", PomodoroFormatter.formatDuration(spanMs)));
			return;
		}
		final long pauseMs = spanMs - focusMs;
		final String ranges = PomodoroPauseInterval.formatRanges(originalPauses);
		previewLabel.setText(TextUtils.format("pomodoro.session_edit.preview", PomodoroFormatter.formatDuration(focusMs),
		                pauseMs > 0
		                ? TextUtils.format("pomodoro.session_edit.pause_part", ranges.length() > 0 ? " " + ranges : "",
		                        PomodoroFormatter.formatDuration(pauseMs))
		                : ""));
	}

	private void apply() {
		final long startMs = startEditor.getTimeMillis();
		final long endMs = endEditor.getTimeMillis();
		final int focusMinutes = ((Number) focusSpinner.getValue()).intValue();
		if (startMs <= 0 || endMs <= startMs) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("pomodoro.session_edit.end_before_start"), TextUtils.getText("pomodoro.session_edit.title"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		final long spanMs = endMs - startMs;
		final long focusMs = focusMinutes * 60L * 1000L;
		if (focusMs <= 0 || focusMs > spanMs) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("pomodoro.session_edit.focus_range"), TextUtils.getText("pomodoro.session_edit.title"),
			        JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (endMs > System.currentTimeMillis() + 60L * 1000L) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("pomodoro.session_edit.end_future"), TextUtils.getText("pomodoro.session_edit.title"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		if (manager != null) {
			manager.updateLogRecord(node, logIndex,
			        new PomodoroSessionRecord(startMs, endMs, focusMs, originalPauses));
		}
		applied = true;
		dispose();
	}

	private static JButton btn(final String text, final ActionListener listener) {
		final JButton button = new JButton(text);
		button.addActionListener(listener);
		return button;
	}

	private static String plain(final NodeModel node) {
		try {
			final String text = TextController.getController().getPlainTextContent(node);
			if (text != null) {
				return HtmlUtils.htmlToPlain(text).replaceAll("\\s+", " ").trim();
			}
		}
		catch (Exception e) {
		}
		return node.getText() == null ? "" : HtmlUtils.htmlToPlain(node.getText());
	}

	private static String escape(final String text) {
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	static Frame ownerFrame() {
		try {
			return Controller.getCurrentController().getViewController().getFrame();
		}
		catch (Exception e) {
			return null;
		}
	}
}
