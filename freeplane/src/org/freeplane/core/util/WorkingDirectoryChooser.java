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
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * First-launch chooser when {@code working-directory.txt} is missing or unusable on this OS.
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
		final Font uiFont = safeUiFont(13f);
		final JDialog dialog = new JDialog((Window) null, "Docear — 选择主目录 / Choose home directory");
		dialog.setModal(true);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		final JPanel root = new JPanel(new BorderLayout(10, 10));
		root.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
		root.setFont(uiFont);

		// Avoid HTML JLabel font substitution (macOS + some CJK fonts drop Latin glyphs).
		final JPanel hints = new JPanel();
		hints.setLayout(new javax.swing.BoxLayout(hints, javax.swing.BoxLayout.Y_AXIS));
		hints.setOpaque(false);
		final String[] lines = new String[] {
				"请选择存放思维导图的主目录（工作目录）。",
				"若目录为空，将复制默认示例文件；若已有文件则全部保留、不会删除。",
				"路径会保存在软件中，之后可在「Docear 设置」里修改。",
				" ",
				"Choose a home folder for your mind maps.",
				"If empty, default sample files are copied; existing files are never deleted.",
				"The path is saved and can be changed later in Docear Settings."
		};
		for (int i = 0; i < lines.length; i++) {
			final JLabel line = new JLabel(lines[i]);
			line.setFont(uiFont);
			line.setAlignmentX(0f);
			hints.add(line);
		}
		root.add(hints, BorderLayout.NORTH);

		final JPanel center = new JPanel(new BorderLayout(8, 0));
		final JTextField field = new JTextField();
		field.setFont(uiFont);
		field.setPreferredSize(new Dimension(360, 28));
		if (suggestedDirectory != null) {
			field.setText(suggestedDirectory.getAbsolutePath());
		}
		final JButton browse = new JButton("浏览… / Browse…");
		browse.setFont(uiFont);
		browse.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				File start = null;
				final String current = field.getText();
				if (current != null && current.trim().length() > 0
				        && MindMapDataRootResolver.isUsableWorkingDirectoryPath(current.trim())) {
					start = new File(current.trim());
				}
				else if (suggestedDirectory != null) {
					start = suggestedDirectory;
				}
				final File chosen = DirectoryPicker.choose(dialog, "主目录 / Home directory", start, true);
				if (chosen != null) {
					field.setText(chosen.getAbsolutePath());
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
		cancel.setFont(uiFont);
		ok.setFont(uiFont);
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
				if (!MindMapDataRootResolver.isUsableWorkingDirectoryPath(text)) {
					JOptionPane.showMessageDialog(dialog,
					        "请选择本机有效路径（macOS/Linux 不要使用 E:\\… 这类 Windows 路径）。\n"
					                + "Please choose a path valid on this OS (not a Windows drive like E:\\…).",
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

	/**
	 * macOS: avoid Microsoft YaHei (Office) which canDisplay CJK but blanks Latin
	 * letters under Java 8. Prefer PingFang / Hiragino.
	 */
	private static Font safeUiFont(final float size) {
		final String os = System.getProperty("os.name", "").toLowerCase();
		final String[] prefer;
		if (os.indexOf("mac") >= 0) {
			prefer = new String[] { "PingFang SC", "Hiragino Sans GB", "Heiti SC", "Helvetica Neue",
					"Lucida Grande", "SansSerif", "Dialog" };
		}
		else if (os.indexOf("win") >= 0) {
			prefer = new String[] { "Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI", "SansSerif", "Dialog" };
		}
		else {
			prefer = new String[] { "Noto Sans CJK SC", "SansSerif", "Dialog" };
		}
		final String latin = "Docear Users/folder maps";
		for (int i = 0; i < prefer.length; i++) {
			try {
				final Font font = new Font(prefer[i], Font.PLAIN, Math.round(size));
				if (!font.canDisplay('导') || !font.canDisplay('图')) {
					continue;
				}
				boolean latinOk = true;
				for (int c = 0; c < latin.length(); c++) {
					final char ch = latin.charAt(c);
					if (ch >= 'A' && ch <= 'z' && !font.canDisplay(ch)) {
						latinOk = false;
						break;
					}
				}
				if (latinOk) {
					return font.deriveFont(Font.PLAIN, size);
				}
			}
			catch (final Throwable ignored) {
			}
		}
		return new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size));
	}
}
