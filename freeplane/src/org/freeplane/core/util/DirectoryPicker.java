package org.freeplane.core.util;

import java.awt.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;

/**
 * Cross-platform directory (or file) picker. On macOS prefers the native
 * {@link FileDialog} with {@code apple.awt.fileDialogForDirectories}.
 */
public final class DirectoryPicker {
	private DirectoryPicker() {
	}

	/**
	 * @return selected file/directory, or {@code null} if cancelled
	 */
	public static File choose(final Component parent, final String title, final File start,
	        final boolean directoriesOnly) {
		if (directoriesOnly && Compat.isMacOsX()) {
			try {
				return chooseMacDirectory(parent, title, start);
			}
			catch (final Exception e) {
				LogUtils.warn("Native macOS directory picker failed, using Swing: " + e.getMessage());
			}
		}
		return chooseSwing(parent, title, start, directoriesOnly);
	}

	private static File chooseMacDirectory(final Component parent, final String title, final File start) {
		final String previous = System.getProperty("apple.awt.fileDialogForDirectories");
		try {
			System.setProperty("apple.awt.fileDialogForDirectories", "true");
			final Frame frame = frameFor(parent);
			final FileDialog dialog = new FileDialog(frame, title == null ? "" : title, FileDialog.LOAD);
			if (start != null) {
				final File dir = start.isDirectory() ? start
				        : (start.getParentFile() != null ? start.getParentFile() : start);
				if (dir != null) {
					dialog.setDirectory(dir.getAbsolutePath());
				}
			}
			dialog.setVisible(true);
			final String file = dialog.getFile();
			final String directory = dialog.getDirectory();
			if (file == null || directory == null) {
				return null;
			}
			return new File(directory, file).getAbsoluteFile();
		}
		finally {
			if (previous == null) {
				System.clearProperty("apple.awt.fileDialogForDirectories");
			}
			else {
				System.setProperty("apple.awt.fileDialogForDirectories", previous);
			}
		}
	}

	private static File chooseSwing(final Component parent, final String title, final File start,
	        final boolean directoriesOnly) {
		final JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(directoriesOnly ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
		if (title != null && title.length() > 0) {
			chooser.setDialogTitle(title);
		}
		if (start != null) {
			final File current = start.isDirectory() ? start
			        : (start.getParentFile() != null ? start.getParentFile() : start);
			if (current != null) {
				chooser.setCurrentDirectory(current);
			}
			if (start.exists()) {
				chooser.setSelectedFile(start);
			}
		}
		if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
			final File selected = chooser.getSelectedFile();
			return selected == null ? null : selected.getAbsoluteFile();
		}
		return null;
	}

	private static Frame frameFor(final Component parent) {
		if (parent instanceof Frame) {
			return (Frame) parent;
		}
		if (parent != null) {
			final Window window = SwingUtilities.getWindowAncestor(parent);
			if (window instanceof Frame) {
				return (Frame) window;
			}
		}
		return null;
	}
}
