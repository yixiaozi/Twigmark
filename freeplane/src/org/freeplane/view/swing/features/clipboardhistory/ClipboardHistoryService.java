package org.freeplane.view.swing.features.clipboardhistory;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;

/**
 * Facade + async writer for clipboard history.
 */
public final class ClipboardHistoryService implements Runnable {
	private static final ClipboardHistoryService INSTANCE = new ClipboardHistoryService();

	private final LinkedBlockingQueue queue = new LinkedBlockingQueue(2000);
	private final AtomicInteger pending = new AtomicInteger(0);
	private final Thread worker;
	private volatile boolean started;
	private volatile boolean running = true;
	private volatile String lastHash = "";
	private volatile Runnable changeListener;

	private ClipboardHistoryService() {
		worker = new Thread(this, "docear-clipboard-history");
		worker.setDaemon(true);
	}

	public static ClipboardHistoryService getInstance() {
		return INSTANCE;
	}

	public synchronized void start() {
		if (started) {
			return;
		}
		ClipboardHistoryDatabase.getInstance();
		worker.start();
		started = true;
		refreshMetric();
		LogUtils.info("Clipboard history service started. db="
				+ ClipboardHistoryConfig.getDbFile().getAbsolutePath());
	}

	public synchronized void shutdown() {
		running = false;
		worker.interrupt();
		try {
			worker.join(3000L);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		flushRemaining();
	}

	public void setChangeListener(final Runnable listener) {
		changeListener = listener;
	}

	/** Enqueue text from the monitor. Drops empties and immediate duplicates. */
	public void offerText(final String text) {
		if (!ClipboardHistoryConfig.isEnabled() || text == null) {
			return;
		}
		final String normalized = ClipboardHistoryDatabase.normalizeAndTruncate(text);
		if (normalized.length() == 0) {
			return;
		}
		final String hash = ClipboardHistoryDatabase.sha1Hex(normalized);
		if (hash.equals(lastHash)) {
			// Still bump DB timestamp for same content, but coalesce bursts.
			if (pending.get() > 0) {
				return;
			}
		}
		lastHash = hash;
		if (queue.offer(normalized)) {
			pending.incrementAndGet();
		}
	}

	public List search(final String query, final int limit) {
		try {
			return ClipboardHistoryDatabase.getInstance().listRecent(query, limit);
		}
		catch (Exception e) {
			LogUtils.warn("Clipboard history search failed: " + e.getMessage(), e);
			return Collections.EMPTY_LIST;
		}
	}

	public ClipboardHistoryEntry get(final long id) {
		try {
			return ClipboardHistoryDatabase.getInstance().getById(id);
		}
		catch (Exception e) {
			return null;
		}
	}

	public boolean delete(final long id) {
		try {
			final boolean ok = ClipboardHistoryDatabase.getInstance().deleteById(id);
			refreshMetric();
			notifyChanged();
			return ok;
		}
		catch (Exception e) {
			LogUtils.warn("Clipboard history delete failed: " + e.getMessage(), e);
			return false;
		}
	}

	public void clearAll() {
		try {
			ClipboardHistoryDatabase.getInstance().clearAll();
			lastHash = "";
			refreshMetric();
			notifyChanged();
		}
		catch (Exception e) {
			LogUtils.warn("Clipboard history clear failed: " + e.getMessage(), e);
		}
	}

	public int count() {
		try {
			return ClipboardHistoryDatabase.getInstance().countEntries();
		}
		catch (Exception e) {
			return 0;
		}
	}

	public long sumHits() {
		try {
			return ClipboardHistoryDatabase.getInstance().sumHits();
		}
		catch (Exception e) {
			return 0L;
		}
	}

	public Map hitsByDay(final int days) {
		try {
			return ClipboardHistoryDatabase.getInstance().statsHitsByDay(days);
		}
		catch (Exception e) {
			return Collections.EMPTY_MAP;
		}
	}

	public List topByHits(final int limit) {
		try {
			return ClipboardHistoryDatabase.getInstance().topByHits(limit);
		}
		catch (Exception e) {
			return Collections.EMPTY_LIST;
		}
	}

	public File getDbFile() {
		return ClipboardHistoryConfig.getDbFile();
	}

	public long getDbFileBytes() {
		try {
			return ClipboardHistoryDatabase.getInstance().getDbFileBytes();
		}
		catch (Exception e) {
			return 0L;
		}
	}

	public void run() {
		while (running || !queue.isEmpty()) {
			try {
				final Object item = queue.poll(300L, TimeUnit.MILLISECONDS);
				if (item == null) {
					continue;
				}
				pending.decrementAndGet();
				final String text = String.valueOf(item);
				ClipboardHistoryDatabase.getInstance().recordText(text);
				// Drain a few more for batching without delaying UI too much.
				int drained = 0;
				while (drained < 20) {
					final Object next = queue.poll();
					if (next == null) {
						break;
					}
					pending.decrementAndGet();
					ClipboardHistoryDatabase.getInstance().recordText(String.valueOf(next));
					drained++;
				}
				refreshMetric();
				notifyChanged();
			}
			catch (InterruptedException e) {
				if (!running) {
					break;
				}
			}
			catch (Exception e) {
				LogUtils.warn("Clipboard history write failed: " + e.getMessage(), e);
			}
		}
	}

	private void flushRemaining() {
		Object item;
		while ((item = queue.poll()) != null) {
			try {
				ClipboardHistoryDatabase.getInstance().recordText(String.valueOf(item));
			}
			catch (Exception e) {
			}
		}
		refreshMetric();
	}

	private void refreshMetric() {
		try {
			SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_CLIPBOARD, count());
		}
		catch (Exception e) {
		}
	}

	private void notifyChanged() {
		final Runnable listener = changeListener;
		if (listener != null) {
			try {
				listener.run();
			}
			catch (Exception e) {
			}
		}
	}
}
