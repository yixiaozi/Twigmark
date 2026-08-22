package org.docear.plugin.mermaid;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
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

	private static volatile JDialog openDialog;

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
		if (openDialog != null) {
			openDialog.dispose();
			openDialog = null;
		}
		final int maxW = Math.min(1200, full.getWidth() + 32);
		final int maxH = Math.min(900, full.getHeight() + 32);
		final BufferedImage scaled = scaleToMax(full, maxW, maxH);
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
		openDialog = dialog;
	}

	private static BufferedImage scaleToMax(final BufferedImage src, final int maxW, final int maxH) {
		if (src.getWidth() <= maxW && src.getHeight() <= maxH) {
			return src;
		}
		final double scale = Math.min(maxW / (double) src.getWidth(), maxH / (double) src.getHeight());
		final int nw = Math.max(1, (int) Math.round(src.getWidth() * scale));
		final int nh = Math.max(1, (int) Math.round(src.getHeight() * scale));
		final BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = out.createGraphics();
		try {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.drawImage(src, 0, 0, nw, nh, null);
		}
		finally {
			g2.dispose();
		}
		return out;
	}
}
