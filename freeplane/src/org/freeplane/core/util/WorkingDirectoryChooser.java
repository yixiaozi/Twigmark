package org.freeplane.core.util;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * First-launch chooser when {@code working-directory.txt} is missing.
 * Shown before preferences load; keeps UI bilingual and dependency-free.
 */
final class WorkingDirectoryChooser {
	private WorkingDirectoryChooser() {
	}

	/**
	 * @return chosen absolute directory, or {@code null} if the user cancelled
	 */
	static File choose(final File suggestedDirectory) {
		if (SwingUtilities.isEventDispatchThread()) {
			return showDialog(suggestedDirectory);
		}
		final File[] result = new File[1];
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					result[0] = showDialog(suggestedDirectory);
				}
			});
		}
		catch (final Exception e) {
			LogUtils.warn("Working directory chooser failed: " + e.getMessage());
			return null;
		}
		return result[0];
	}

	private static File showDialog(final File suggestedDirectory) {
		final JDialog dialog = new JDialog((Window) null, "Docear — 选择工作目录 / Choose working directory");
		dialog.setModal(true);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		final JPanel root = new JPanel(new BorderLayout(10, 10));
		root.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
		final JLabel hint = new JLabel(
		        "<html><body style='width:420px'>"
		                + "请选择存放思维导图的工作目录。<br>"
		                + "若目录为空，将复制默认示例文件；若已有文件则全部保留、不会删除。<br><br>"
		                + "Choose a folder for your mind maps.<br>"
		                + "If empty, default sample files are copied; existing files are never deleted."
		                + "</body></html>");
		hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 13f));
		root.add(hint, BorderLayout.NORTH);

		final JPanel center = new JPanel(new BorderLayout(8, 0));
		final JTextField field = new JTextField();
		field.setPreferredSize(new Dimension(360, 28));
		if (suggestedDirectory != null) {
			field.setText(suggestedDirectory.getAbsolutePath());
		}
		final JButton browse = new JButton("浏览… / Browse…");
		browse.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final JFileChooser chooser = new JFileChooser();
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				chooser.setDialogTitle("工作目录 / Working directory");
				final String current = field.getText();
				if (current != null && current.trim().length() > 0) {
					final File start = new File(current.trim());
					chooser.setCurrentDirectory(start.isDirectory() ? start
					        : (start.getParentFile() != null ? start.getParentFile() : suggestedDirectory));
				}
				else if (suggestedDirectory != null) {
					chooser.setCurrentDirectory(suggestedDirectory.getParentFile() != null
					        ? suggestedDirectory.getParentFile() : suggestedDirectory);
				}
				if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
					field.setText(chooser.getSelectedFile().getAbsolutePath());
				}
			}
		});
		center.add(field, BorderLayout.CENTER);
		center.add(browse, BorderLayout.EAST);
		root.add(center, BorderLayout.CENTER);

		final File[] chosen = new File[1];
		final JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		final JButton cancel = new JButton("取消 / Cancel");
		final JButton ok = new JButton("确定 / OK");
		cancel.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				chosen[0] = null;
				dialog.dispose();
			}
		});
		ok.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final String text = field.getText() == null ? "" : field.getText().trim();
				if (text.length() == 0) {
					JOptionPane.showMessageDialog(dialog, "请选择一个目录。\nPlease choose a directory.",
					        dialog.getTitle(), JOptionPane.WARNING_MESSAGE);
					return;
				}
				final File dir = new File(text);
				if (dir.exists() && !dir.isDirectory()) {
					JOptionPane.showMessageDialog(dialog, "路径不是文件夹。\nPath is not a directory.",
					        dialog.getTitle(), JOptionPane.WARNING_MESSAGE);
					return;
				}
				chosen[0] = dir.getAbsoluteFile();
				dialog.dispose();
			}
		});
		south.add(cancel);
		south.add(ok);
		root.add(south, BorderLayout.SOUTH);

		dialog.getContentPane().add(root);
		dialog.pack();
		dialog.setMinimumSize(dialog.getPreferredSize());
		dialog.setLocationRelativeTo(null);
		dialog.setVisible(true);
		return chosen[0];
	}
}
