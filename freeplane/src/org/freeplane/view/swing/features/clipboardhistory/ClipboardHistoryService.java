package org.freeplane.view.swing.features.clipboardhistory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;

/**
 * Facade + async writer for clipboard history.
 * Writes only to this PC's DB; reads can aggregate peer {@code clipboard_history-*.db} files.
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
		final int want = limit > 0 ? limit : 200;
		final Map byHash = new HashMap();
		final File[] files = ClipboardHistoryConfig.listDbFiles();
		final File local = ClipboardHistoryConfig.getDbFile();
		for (int i = 0; i < files.length; i++) {
			final File file = files[i];
			final boolean isLocal = sameFile(file, local);
			if (!isLocal && (file == null || !file.isFile())) {
				continue;
			}
			try {
				final ClipboardHistoryDatabase db = ClipboardHistoryDatabase.open(file, isLocal);
				if (db == null) {
					continue;
				}
				final List rows = db.listRecent(query, want);
				mergeByHash(byHash, rows);
			}
			catch (Exception e) {
				LogUtils.warn("Clipboard history search failed for " + file + ": " + e.getMessage(), e);
			}
		}
		final List merged = new ArrayList(byHash.values());
		Collections.sort(merged, new Comparator() {
			public int compare(final Object a, final Object b) {
				final long diff = ((ClipboardHistoryEntry) b).lastTs - ((ClipboardHistoryEntry) a).lastTs;
				if (diff > 0L) {
					return 1;
				}
				if (diff < 0L) {
					return -1;
				}
				return 0;
			}
		});
		if (merged.size() > want) {
			return new ArrayList(merged.subList(0, want));
		}
		return merged;
	}

	public ClipboardHistoryEntry get(final long id) {
		try {
			return ClipboardHistoryDatabase.getInstance().getById(id);
		}
		catch (Exception e) {
			return null;
		}
	}

	public ClipboardHistoryEntry get(final ClipboardHistoryEntry ref) {
		if (ref == null) {
			return null;
		}
		final ClipboardHistoryDatabase db = dbForEntry(ref);
		if (db == null) {
			return null;
		}
		try {
			return db.getById(ref.id);
		}
		catch (Exception e) {
			return null;
		}
	}

	/**
	 * Every recorded occurrence time for this entry (newest first).
	 * Falls back to {@code first_ts}/{@code last_ts} when the hit table is empty
	 * (legacy rows before per-hit logging).
	 */
	public List listHitTimes(final ClipboardHistoryEntry entry) {
		final List times = new ArrayList();
		if (entry == null) {
			return times;
		}
		final ClipboardHistoryDatabase db = dbForEntry(entry);
		if (db != null) {
			try {
				final List fromDb = db.listHitTimes(entry.id, ClipboardHistoryDatabase.HIT_LIST_LIMIT);
				if (fromDb != null) {
					times.addAll(fromDb);
				}
			}
			catch (Exception e) {
				LogUtils.warn("Clipboard hit times failed: " + e.getMessage(), e);
			}
		}
		if (times.isEmpty()) {
			if (entry.lastTs > 0L) {
				times.add(Long.valueOf(entry.lastTs));
			}
			if (entry.firstTs > 0L && entry.firstTs != entry.lastTs) {
				times.add(Long.valueOf(entry.firstTs));
			}
		}
		return times;
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

	/** Delete using the entry's source DB (required when aggregating multi-PC rows). */
	public boolean delete(final ClipboardHistoryEntry entry) {
		if (entry == null) {
			return false;
		}
		final ClipboardHistoryDatabase db = dbForEntry(entry);
		if (db == null) {
			return false;
		}
		try {
			final boolean ok = db.deleteById(entry.id);
			refreshMetric();
			notifyChanged();
			return ok;
		}
		catch (Exception e) {
			LogUtils.warn("Clipboard history delete failed: " + e.getMessage(), e);
			return false;
		}
	}

	/** Clears only this PC's clipboard DB (does not wipe synced peer files). */
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
		int total = 0;
		final File[] files = ClipboardHistoryConfig.listDbFiles();
		final File local = ClipboardHistoryConfig.getDbFile();
		for (int i = 0; i < files.length; i++) {
			final File file = files[i];
			final boolean isLocal = sameFile(file, local);
			if (!isLocal && (file == null || !file.isFile())) {
				continue;
			}
			try {
				final ClipboardHistoryDatabase db = ClipboardHistoryDatabase.open(file, isLocal);
				if (db != null) {
					total += db.countEntries();
				}
			}
			catch (Exception e) {
			}
		}
		return total;
	}

	public long sumHits() {
		long total = 0L;
		final File[] files = ClipboardHistoryConfig.listDbFiles();
		final File local = ClipboardHistoryConfig.getDbFile();
		for (int i = 0; i < files.length; i++) {
			final File file = files[i];
			final boolean isLocal = sameFile(file, local);
			if (!isLocal && (file == null || !file.isFile())) {
				continue;
			}
			try {
				final ClipboardHistoryDatabase db = ClipboardHistoryDatabase.open(file, isLocal);
				if (db != null) {
					total += db.sumHits();
				}
			}
			catch (Exception e) {
			}
		}
		return total;
	}

	public Map hitsByDay(final int days) {
		final Map merged = new LinkedHashMap();
		final File[] files = ClipboardHistoryConfig.listDbFiles();
		final File local = ClipboardHistoryConfig.getDbFile();
		for (int i = 0; i < files.length; i++) {
			final File file = files[i];
			final boolean isLocal = sameFile(file, local);
			if (!isLocal && (file == null || !file.isFile())) {
				continue;
			}
			try {
				final ClipboardHistoryDatabase db = ClipboardHistoryDatabase.open(file, isLocal);
				if (db == null) {
					continue;
				}
				final Map part = db.statsHitsByDay(days);
				for (final Iterator it = part.entrySet().iterator(); it.hasNext();) {
					final Map.Entry e = (Map.Entry) it.next();
					final Long key = (Long) e.getKey();
					final long add = ((Long) e.getValue()).longValue();
					final Long prev = (Long) merged.get(key);
					merged.put(key, Long.valueOf(prev == null ? add : prev.longValue() + add));
				}
			}
			catch (Exception e) {
			}
		}
		return merged;
	}

	public List topByHits(final int limit) {
		final int want = limit > 0 ? limit : 20;
		final Map byHash = new HashMap();
		final File[] files = ClipboardHistoryConfig.listDbFiles();
		final File local = ClipboardHistoryConfig.getDbFile();
		for (int i = 0; i < files.length; i++) {
			final File file = files[i];
			final boolean isLocal = sameFile(file, local);
			if (!isLocal && (file == null || !file.isFile())) {
				continue;
			}
			try {
				final ClipboardHistoryDatabase db = ClipboardHistoryDatabase.open(file, isLocal);
				if (db == null) {
					continue;
				}
				mergeByHash(byHash, db.topByHits(want));
			}
			catch (Exception e) {
			}
		}
		final List merged = new ArrayList(byHash.values());
		Collections.sort(merged, new Comparator() {
			public int compare(final Object a, final Object b) {
				final ClipboardHistoryEntry ea = (ClipboardHistoryEntry) a;
				final ClipboardHistoryEntry eb = (ClipboardHistoryEntry) b;
				final int hitDiff = eb.hitCount - ea.hitCount;
				if (hitDiff != 0) {
					return hitDiff;
				}
				final long diff = eb.lastTs - ea.lastTs;
				if (diff > 0L) {
					return 1;
				}
				if (diff < 0L) {
					return -1;
				}
				return 0;
			}
		});
		if (merged.size() > want) {
			return new ArrayList(merged.subList(0, want));
		}
		return merged;
	}

	public File getDbFile() {
		return ClipboardHistoryConfig.getDbFile();
	}

	public long getDbFileBytes() {
		long total = 0L;
		final File[] files = ClipboardHistoryConfig.listDbFiles();
		for (int i = 0; i < files.length; i++) {
			final File file = files[i];
			if (file != null && file.isFile()) {
				total += file.length();
			}
		}
		return total;
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
		if (listener == null) {
			return;
		}
		// Always hop to EDT; listeners must stay cheap (debounce / soft-update only).
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					listener.run();
				}
				catch (Exception e) {
				}
			}
		});
	}

	private static void mergeByHash(final Map byHash, final List rows) {
		if (rows == null) {
			return;
		}
		for (int i = 0; i < rows.size(); i++) {
			final ClipboardHistoryEntry incoming = (ClipboardHistoryEntry) rows.get(i);
			if (incoming == null || incoming.contentHash == null || incoming.contentHash.length() == 0) {
				continue;
			}
			final ClipboardHistoryEntry existing = (ClipboardHistoryEntry) byHash.get(incoming.contentHash);
			if (existing == null) {
				byHash.put(incoming.contentHash, incoming);
				continue;
			}
			existing.hitCount += incoming.hitCount;
			if (incoming.firstTs > 0L
			        && (existing.firstTs <= 0L || incoming.firstTs < existing.firstTs)) {
				existing.firstTs = incoming.firstTs;
			}
			if (incoming.lastTs >= existing.lastTs) {
				existing.lastTs = incoming.lastTs;
				existing.id = incoming.id;
				existing.content = incoming.content;
				existing.charLen = incoming.charLen;
				existing.sourceDbPath = incoming.sourceDbPath;
				existing.machineId = incoming.machineId;
				existing.localMachine = incoming.localMachine;
			}
			else if (incoming.localMachine && !existing.localMachine) {
				// Prefer local row identity when timestamps are older but same hash.
				existing.sourceDbPath = incoming.sourceDbPath;
				existing.machineId = incoming.machineId;
				existing.localMachine = true;
				existing.id = incoming.id;
			}
		}
	}

	private static ClipboardHistoryDatabase dbForEntry(final ClipboardHistoryEntry entry) {
		if (entry == null) {
			return null;
		}
		final File local = ClipboardHistoryConfig.getDbFile();
		if (entry.sourceDbPath == null || entry.sourceDbPath.length() == 0 || entry.localMachine) {
			return ClipboardHistoryDatabase.getInstance();
		}
		final File file = new File(entry.sourceDbPath);
		if (sameFile(file, local)) {
			return ClipboardHistoryDatabase.getInstance();
		}
		return ClipboardHistoryDatabase.open(file, false);
	}

	private static boolean sameFile(final File a, final File b) {
		if (a == null || b == null) {
			return false;
		}
		try {
			return a.getCanonicalPath().equals(b.getCanonicalPath());
		}
		catch (Exception e) {
			return a.getAbsolutePath().equals(b.getAbsolutePath());
		}
	}
}
