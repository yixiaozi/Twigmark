package org.freeplane.core.util;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * First-launch chooser when {@code working-directory.txt} is missing, or when
 * the mind-map library has no usable nested {@code data}/{@code _data} folder.
 * Shown before preferences load; keeps UI bilingual and dependency-free.
 */
final class WorkingDirectoryChooser {
	private WorkingDirectoryChooser() {
	}

	static final class Directories {
		final File workingDirectory;
		final File configDirectory;

		Directories(final File workingDirectory, final File configDirectory) {
			this.workingDirectory = workingDirectory;
			this.configDirectory = configDirectory;
		}
	}

	/**
	 * @return chosen directories, or {@code null} if the user cancelled
	 */
	static Directories choose(final File suggestedWorking, final File suggestedConfig) {
		if (SwingUtilities.isEventDispatchThread()) {
			return showDialog(suggestedWorking, suggestedConfig);
		}
		final Directories[] result = new Directories[1];
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					result[0] = showDialog(suggestedWorking, suggestedConfig);
				}
			});
		}
		catch (final Exception e) {
			LogUtils.warn("Working directory chooser failed: " + e.getMessage());
			return null;
		}
		return result[0];
	}

	private static Directories showDialog(final File suggestedWorking, final File suggestedConfig) {
		final Font uiFont = safeUiFont(13f);
		final JDialog dialog = new JDialog((Window) null, "Docear — 选择目录 / Choose directories");
		dialog.setModal(true);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		final JPanel root = new JPanel(new BorderLayout(10, 10));
		root.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
		root.setFont(uiFont);

		final JPanel hints = new JPanel();
		hints.setLayout(new javax.swing.BoxLayout(hints, javax.swing.BoxLayout.Y_AXIS));
		hints.setOpaque(false);
		final String[] lines = new String[] {
				"请分别设置两个目录：",
				"1. 思维导图库：存放 .mm 文件（可放 Dropbox 等同步盘）。",
				"2. 数据目录（_data）：索引、数据库、偏好设置。建议放本机路径，",
				"   例如 D:\\DropboxLocal\\mindmaps_data，避免 SQLite 被网盘同步冲突。",
				"若数据目录为空则创建；已有内容不会删除。",
				" ",
				"Choose two folders:",
				"1. Mind map library: .mm files (Dropbox-safe).",
				"2. Data folder (_data): indexes, databases, preferences.",
				"   Prefer a local path so SQLite is not cloud-synced.",
				"Empty folders are created; existing files are never deleted."
		};
		for (int i = 0; i < lines.length; i++) {
			final JLabel line = new JLabel(lines[i]);
			line.setFont(uiFont);
			line.setAlignmentX(0f);
			hints.add(line);
		}
		root.add(hints, BorderLayout.NORTH);

		final JTextField workingField = new JTextField();
		workingField.setFont(uiFont);
		workingField.setPreferredSize(new Dimension(420, 28));
		if (suggestedWorking != null) {
			workingField.setText(suggestedWorking.getAbsolutePath());
		}

		final JTextField configField = new JTextField();
		configField.setFont(uiFont);
		configField.setPreferredSize(new Dimension(420, 28));
		if (suggestedConfig != null) {
			configField.setText(suggestedConfig.getAbsolutePath());
		}
		else if (suggestedWorking != null) {
			configField.setText(MindMapDataRootResolver.suggestedConfigDirectory(suggestedWorking).getAbsolutePath());
		}

		final boolean[] updatingSuggestion = new boolean[] { false };
		final boolean[] configTouched = new boolean[] { false };
		configField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(final DocumentEvent e) {
				if (!updatingSuggestion[0]) {
					configTouched[0] = true;
				}
			}

			public void removeUpdate(final DocumentEvent e) {
				if (!updatingSuggestion[0]) {
					configTouched[0] = true;
				}
			}

			public void changedUpdate(final DocumentEvent e) {
				if (!updatingSuggestion[0]) {
					configTouched[0] = true;
				}
			}
		});
		workingField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(final DocumentEvent e) {
				maybeSuggestConfig();
			}

			public void removeUpdate(final DocumentEvent e) {
				maybeSuggestConfig();
			}

			public void changedUpdate(final DocumentEvent e) {
				maybeSuggestConfig();
			}

			private void maybeSuggestConfig() {
				if (configTouched[0]) {
					return;
				}
				final String text = workingField.getText();
				if (text == null || text.trim().length() == 0
				        || !MindMapDataRootResolver.isUsableWorkingDirectoryPath(text.trim())) {
					return;
				}
				updatingSuggestion[0] = true;
				try {
					configField.setText(MindMapDataRootResolver.suggestedConfigDirectory(new File(text.trim()))
					        .getAbsolutePath());
				}
				finally {
					updatingSuggestion[0] = false;
				}
			}
		});

		final JPanel center = new JPanel(new GridBagLayout());
		final GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 0, 4, 8);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;

		addLabeledField(center, c, 0, "思维导图库 / Mind map library", workingField, dialog,
		        "思维导图库 / Mind map library", uiFont);
		addLabeledField(center, c, 2, "数据目录 (_data) / Data folder", configField, dialog,
		        "数据目录 / Data folder", uiFont);

		c.gridx = 0;
		c.gridy = 4;
		c.gridwidth = 3;
		c.weightx = 1;
		final JLabel note = new JLabel("留空数据目录则使用「思维导图库/data」。Leave data folder empty to use library/data.");
		note.setFont(safeUiFont(11f));
		center.add(note, c);
		root.add(center, BorderLayout.CENTER);

		final Directories[] chosen = new Directories[1];
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
				final File working = validateDirectory(dialog, workingField.getText(), true);
				if (working == null) {
					return;
				}
				String configText = configField.getText() == null ? "" : configField.getText().trim();
				File config;
				if (configText.length() == 0) {
					config = MindMapDataRootResolver.suggestedConfigDirectory(working);
				}
				else {
					config = validateDirectory(dialog, configText, false);
					if (config == null) {
						return;
					}
					config = MindMapDataRootResolver.normalizeChosenConfigDirectory(config);
				}
				chosen[0] = new Directories(working, config);
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

	private static void addLabeledField(final JPanel center, final GridBagConstraints c, final int row,
	        final String labelText, final JTextField field, final JDialog dialog, final String browseTitle,
	        final Font uiFont) {
		c.gridx = 0;
		c.gridy = row;
		c.gridwidth = 3;
		c.weightx = 1;
		final JLabel label = new JLabel(labelText);
		label.setFont(uiFont);
		center.add(label, c);

		c.gridy = row + 1;
		c.gridwidth = 1;
		c.weightx = 1;
		center.add(field, c);
		c.gridx = 1;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
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
				final File picked = DirectoryPicker.choose(dialog, browseTitle, start, true);
				if (picked != null) {
					field.setText(picked.getAbsolutePath());
				}
			}
		});
		center.add(browse, c);
		c.fill = GridBagConstraints.HORIZONTAL;
	}

	private static File validateDirectory(final JDialog dialog, final String text, final boolean required) {
		final String trimmed = text == null ? "" : text.trim();
		if (trimmed.length() == 0) {
			if (required) {
				JOptionPane.showMessageDialog(dialog, "请选择一个目录。\nPlease choose a directory.",
				        dialog.getTitle(), JOptionPane.WARNING_MESSAGE);
			}
			return null;
		}
		if (!MindMapDataRootResolver.isUsableWorkingDirectoryPath(trimmed)) {
			JOptionPane.showMessageDialog(dialog,
			        "请选择本机有效路径（macOS/Linux 不要使用 E:\\… 这类 Windows 路径）。\n"
			                + "Please choose a path valid on this OS (not a Windows drive like E:\\…).",
			        dialog.getTitle(), JOptionPane.WARNING_MESSAGE);
			return null;
		}
		final File dir = new File(trimmed);
		if (dir.exists() && !dir.isDirectory()) {
			JOptionPane.showMessageDialog(dialog, "路径不是文件夹。\nPath is not a directory.",
			        dialog.getTitle(), JOptionPane.WARNING_MESSAGE);
			return null;
		}
		return dir.getAbsoluteFile();
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
				for (int ci = 0; ci < latin.length(); ci++) {
					final char ch = latin.charAt(ci);
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
