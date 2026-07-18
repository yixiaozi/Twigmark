package org.freeplane.view.swing.features.clipboardhistory;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.Transferable;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.freeplane.core.util.LogUtils;

/**
 * Watches the system clipboard for plain text. Uses FlavorListener when available,
 * with a Timer poll fallback. Never writes to the clipboard (avoids echo loops).
 */
public final class ClipboardHistoryMonitor implements FlavorListener {
	private static ClipboardHistoryMonitor instance;

	private final ClipboardHistoryService service;
	private Timer pollTimer;
	private volatile boolean ignoringOwnReads;
	private String lastSeen = "";

	private ClipboardHistoryMonitor(final ClipboardHistoryService service) {
		this.service = service;
	}

	public static synchronized void start() {
		if (instance != null) {
			return;
		}
		if (!ClipboardHistoryConfig.isEnabled()) {
			LogUtils.info("Clipboard history monitor disabled by configuration.");
			return;
		}
		instance = new ClipboardHistoryMonitor(ClipboardHistoryService.getInstance());
		instance.service.start();
		instance.attach();
	}

	public static synchronized void stop() {
		if (instance == null) {
			return;
		}
		instance.detach();
		instance.service.shutdown();
		instance = null;
	}

	private void attach() {
		try {
			final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.addFlavorListener(this);
		}
		catch (Throwable t) {
			LogUtils.warn("Clipboard FlavorListener unavailable: " + t.getMessage());
		}
		pollTimer = new Timer(ClipboardHistoryConfig.getPollMs(), new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				// Poll only picks up new text; same content would otherwise spam hits.
				capture(false);
			}
		});
		pollTimer.setRepeats(true);
		pollTimer.start();
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				capture(true);
			}
		});
		LogUtils.info("Clipboard history monitor started");
	}

	private void detach() {
		if (pollTimer != null) {
			pollTimer.stop();
			pollTimer = null;
		}
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard().removeFlavorListener(this);
		}
		catch (Throwable t) {
		}
	}

	public void flavorsChanged(final FlavorEvent e) {
		// Debounce onto EDT; FlavorListener can fire off-EDT.
		// Same text re-copied should still bump last_ts / hit_count.
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				capture(true);
			}
		});
	}

	/**
	 * @param allowSameContent when true (FlavorListener), re-copy of the same text
	 *            updates timestamp/hit; when false (poll), ignore unchanged text.
	 */
	private void capture(final boolean allowSameContent) {
		if (ignoringOwnReads || !ClipboardHistoryConfig.isEnabled()) {
			return;
		}
		final String text = readPlainText();
		if (text == null) {
			return;
		}
		final String normalized = ClipboardHistoryDatabase.normalizeAndTruncate(text);
		if (normalized.length() == 0) {
			return;
		}
		if (!allowSameContent && normalized.equals(lastSeen)) {
			return;
		}
		lastSeen = normalized;
		service.offerText(normalized);
	}

	private String readPlainText() {
		ignoringOwnReads = true;
		try {
			final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			final Transferable contents = clipboard.getContents(null);
			if (contents == null || !contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
				return null;
			}
			final Object data = contents.getTransferData(DataFlavor.stringFlavor);
			return data == null ? null : String.valueOf(data);
		}
		catch (Exception e) {
			return null;
		}
		finally {
			ignoringOwnReads = false;
		}
	}

	/** Put text back onto the system clipboard (user action). */
	public static void copyToSystemClipboard(final String text) {
		if (text == null) {
			return;
		}
		try {
			final java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
			if (instance != null) {
				instance.ignoringOwnReads = true;
				instance.lastSeen = ClipboardHistoryDatabase.normalizeAndTruncate(text);
			}
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
			// Count the restore as a hit; suppress FlavorListener briefly to avoid double-count.
			ClipboardHistoryService.getInstance().offerText(text);
		}
		catch (Exception e) {
			LogUtils.warn("Copy to system clipboard failed: " + e.getMessage(), e);
		}
		finally {
			if (instance != null) {
				final Timer release = new Timer(600, new java.awt.event.ActionListener() {
					public void actionPerformed(final java.awt.event.ActionEvent e) {
						instance.ignoringOwnReads = false;
					}
				});
				release.setRepeats(false);
				release.start();
			}
		}
	}
}
