package org.freeplane.features.help;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.Controller;

/**
 * In-app viewer for {@code logs/log.0} … {@code log.4}.
 * <p>
 * Opening {@code log.0} in Notepad/Explorer often fails: the current volume is
 * locked by the running process, may be 0 bytes right after rotation, and
 * Windows has no file association for the {@code .0} extension.
 */
public final class SystemLogViewer {
	private static final int TAIL_BYTES = 400 * 1024;
	private static JDialog openDialog;

	private SystemLogViewer() {
	}

	public static void show() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					show();
				}
			});
			return;
		}
		if (openDialog != null && openDialog.isDisplayable()) {
			openDialog.toFront();
			return;
		}
		Frame owner = null;
		try {
			final Controller controller = Controller.getCurrentController();
			if (controller != null && controller.getViewController() != null) {
				owner = controller.getViewController().getFrame();
			}
		}
		catch (final Exception e) {
		}
		final JDialog dialog = new JDialog(owner, TextUtils.getText("SystemLogViewer.title", "系统日志"), false);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		final JLabel hint = DocearUiTheme.mutedLabel(TextUtils.getText("SystemLogViewer.hint",
		        "log.0 常被锁定或刚轮转为空；这里直接读最近几卷（不是文件太大）。"));
		hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		final JTextArea area = new JTextArea();
		area.setEditable(false);
		area.setLineWrap(false);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		area.setBackground(DocearUiTheme.SURFACE);
		area.setForeground(DocearUiTheme.TEXT);
		final JScrollPane scroll = new JScrollPane(area);
		scroll.setPreferredSize(new Dimension(880, 520));
		DocearUiTheme.styleScrollPane(scroll);

		final Runnable load = new Runnable() {
			public void run() {
				area.setText(LogUtils.readRecentLogText(TAIL_BYTES));
				area.setCaretPosition(area.getDocument().getLength());
			}
		};
		load.run();

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setOpaque(false);
		final JButton refresh = DocearUiTheme.softButton(TextUtils.getText("SystemLogViewer.refresh", "刷新"));
		final JButton folder = DocearUiTheme.softButton(TextUtils.getText("SystemLogViewer.open_folder", "打开文件夹"));
		final JButton close = DocearUiTheme.primaryButton(TextUtils.getText("SystemLogViewer.close", "关闭"));
		refresh.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				load.run();
			}
		});
		folder.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				LogUtils.openLogDirectory();
			}
		});
		close.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				dialog.dispose();
			}
		});
		buttons.add(folder);
		buttons.add(refresh);
		buttons.add(close);

		final JPanel root = new JPanel(new BorderLayout(0, 8));
		DocearUiTheme.styleCanvas(root);
		root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
		root.add(hint, BorderLayout.NORTH);
		root.add(scroll, BorderLayout.CENTER);
		root.add(buttons, BorderLayout.SOUTH);
		dialog.setContentPane(root);
		dialog.pack();
		dialog.setLocationRelativeTo(owner);
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			public void windowClosed(final java.awt.event.WindowEvent e) {
				if (openDialog == dialog) {
					openDialog = null;
				}
			}
		});
		openDialog = dialog;
		dialog.setVisible(true);
	}
}
