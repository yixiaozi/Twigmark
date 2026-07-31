package org.freeplane.view.swing.features.clipboardhistory;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.Transferable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.freeplane.core.util.LogUtils;

/**
 * Watches the system clipboard for plain text. Uses FlavorListener when available,
 * with a Timer poll fallback. Capture always runs off the EDT so large pastes
 * never stall the UI. Never writes to the clipboard from the monitor path
 * (avoids echo loops).
 */
public final class ClipboardHistoryMonitor implements FlavorListener {
	private static ClipboardHistoryMonitor instance;

	private final ClipboardHistoryService service;
	private final ExecutorService captureExecutor;
	private final AtomicBoolean captureQueued = new AtomicBoolean(false);
	private Timer pollTimer;
	private volatile boolean ignoringOwnReads;
	private volatile boolean allowSameOnNextCapture;
	private String lastSeen = "";

	private ClipboardHistoryMonitor(final ClipboardHistoryService service) {
		this.service = service;
		this.captureExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
			public Thread newThread(final Runnable r) {
				final Thread t = new Thread(r, "docear-clipboard-capture");
				t.setDaemon(true);
				return t;
			}
		});
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
				scheduleCapture(false);
			}
		});
		pollTimer.setRepeats(true);
		pollTimer.start();
		scheduleCapture(true);
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
		captureExecutor.shutdownNow();
	}

	public void flavorsChanged(final FlavorEvent e) {
		// Off-EDT: FlavorListener may fire on any thread; never read clipboard on EDT.
		scheduleCapture(true);
	}

	private void scheduleCapture(final boolean allowSameContent) {
		if (allowSameContent) {
			allowSameOnNextCapture = true;
		}
		if (!captureQueued.compareAndSet(false, true)) {
			return;
		}
		captureExecutor.execute(new Runnable() {
			public void run() {
				captureQueued.set(false);
				final boolean allowSame = allowSameOnNextCapture;
				allowSameOnNextCapture = false;
				capture(allowSame);
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
