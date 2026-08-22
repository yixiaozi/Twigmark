package org.docear.plugin.mermaid;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Image;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.freeplane.core.ui.components.UITools;
import org.freeplane.features.mode.Controller;

/** Full-size preview dialog for rich node icons (double-click via {@link RichPreviewController}). */
final class RichPreviewDialog {

	private RichPreviewDialog() {
	}

	static void show(final ZoomableRichIcon icon) {
		if (icon == null) {
			return;
		}
		final BufferedImage full = icon.getFullImage();
		if (full == null) {
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				openDialog(icon.getKindLabel(), full);
			}
		});
	}

	private static void openDialog(final String title, final BufferedImage full) {
		final int maxW = Math.min(1200, full.getWidth() + 32);
		final int maxH = Math.min(900, full.getHeight() + 32);
		final Image scaled = full.getWidth() <= maxW && full.getHeight() <= maxH ? full
				: full.getScaledInstance(maxW, maxH, Image.SCALE_SMOOTH);
		final JLabel label = new JLabel(new ImageIcon(scaled));
		final JScrollPane scroll = new JScrollPane(label);
		scroll.setPreferredSize(new Dimension(Math.min(maxW + 24, 960), Math.min(maxH + 24, 720)));
		final Component anchor = Controller.getCurrentController().getMapViewManager().getMapViewComponent();
		final Frame owner = anchor != null ? (Frame) SwingUtilities.getWindowAncestor(anchor) : null;
		final JDialog dialog = new JDialog(owner, title + " preview", false);
		dialog.setLayout(new BorderLayout());
		dialog.add(scroll, BorderLayout.CENTER);
		dialog.pack();
		if (anchor != null) {
			UITools.setDialogLocationRelativeTo(dialog, anchor);
		}
		dialog.setVisible(true);
	}
}
