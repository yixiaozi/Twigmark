package org.freeplane.view.swing.features.pomodoro;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Calendar;
import java.util.Date;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.ui.components.DateTimeFieldsPanel;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.text.TextController;

/**
 * Dialog to backfill a completed pomodoro session (start → end) when the timer was not started.
 */
final class PomodoroBackfillDialog extends JDialog {
	private static final long serialVersionUID = 1L;

	private final NodeModel node;
	private final DateTimeFieldsPanel startEditor;
	private final DateTimeFieldsPanel endEditor;
	private final JLabel previewLabel;

	static void showForNode(final NodeModel node) {
		if (node == null) {
			return;
		}
		Frame owner = null;
		try {
			owner = Controller.getCurrentController().getViewController().getFrame();
		}
		catch (Exception e) {
		}
		final PomodoroBackfillDialog dialog = new PomodoroBackfillDialog(owner, node);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
	}

	private PomodoroBackfillDialog(final Frame owner, final NodeModel node) {
		super(owner, TextUtils.getText("BackfillPomodoroAction.text"), true);
		this.node = node;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		final JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBorder(new EmptyBorder(10, 12, 10, 12));

		final JLabel header = new JLabel("<html><b>" + escape(plain(node)) + "</b><br>"
				+ TextUtils.getText("BackfillPomodoroAction.dialogHint") + "</html>");
		root.add(header, BorderLayout.NORTH);

		final Calendar endCal = Calendar.getInstance();
		roundToMinute(endCal);
		final Calendar startCal = (Calendar) endCal.clone();
		startCal.add(Calendar.MINUTE, -25);

		startEditor = new DateTimeFieldsPanel(true, startCal.getTime());
		endEditor = new DateTimeFieldsPanel(true, endCal.getTime());

		final JPanel form = new JPanel();
		form.setLayout(new javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS));
		form.add(labeledRow(TextUtils.getText("BackfillPomodoroAction.startLabel"), startEditor));
		form.add(javax.swing.Box.createVerticalStrut(8));
		form.add(labeledRow(TextUtils.getText("BackfillPomodoroAction.endLabel"), endEditor));
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
		refreshPreview();

		final JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		south.add(btn(TextUtils.getText("cancel"), new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				dispose();
			}
		}));
		south.add(btn(TextUtils.getText("ok"), new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				apply();
			}
		}));
		root.add(south, BorderLayout.SOUTH);

		setContentPane(root);
		pack();
		getRootPane().setDefaultButton((JButton) south.getComponent(1));
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
		if (endMs <= startMs) {
			previewLabel.setText(TextUtils.getText("BackfillPomodoroAction.invalidRange"));
			return;
		}
		final long focusMs = endMs - startMs;
		previewLabel.setText(TextUtils.getText("BackfillPomodoroAction.previewPrefix")
				+ PomodoroFormatter.formatDuration(focusMs));
	}

	private void apply() {
		final long startMs = startEditor.getTimeMillis();
		final long endMs = endEditor.getTimeMillis();
		if (startMs <= 0 || endMs <= startMs) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("BackfillPomodoroAction.invalidRange"),
					TextUtils.getText("BackfillPomodoroAction.text"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (endMs > System.currentTimeMillis() + 60L * 1000L) {
			JOptionPane.showMessageDialog(this, TextUtils.getText("BackfillPomodoroAction.futureEnd"),
					TextUtils.getText("BackfillPomodoroAction.text"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		final PomodoroSessionManager manager = PomodoroSessionManager.getInstance();
		if (manager != null) {
			manager.backfillSession(node, startMs, endMs);
		}
		dispose();
	}

	private static void roundToMinute(final Calendar cal) {
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
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
}
