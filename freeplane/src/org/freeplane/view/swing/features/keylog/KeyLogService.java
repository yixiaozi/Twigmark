package org.freeplane.view.swing.features.keylog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.freeplane.core.util.LocalMachineId;
import org.freeplane.core.util.LogUtils;

/**
 * Async keystroke writer: hook thread only enqueues; a daemon flushes short DB transactions.
 */
public final class KeyLogService implements Runnable {
	private static final KeyLogService INSTANCE = new KeyLogService();

	private final LinkedBlockingQueue queue = new LinkedBlockingQueue(20000);
	private final Thread worker;
	private final Map nameToId = new HashMap();
	private final List pendingEvents = new ArrayList();
	private volatile boolean started;
	private volatile boolean running = true;
	private long sessionStartTs;
	private long lastEventTs;
	private boolean sessionApprox;
	private String sessionSource = "live";

	private KeyLogService() {
		worker = new Thread(this, "docear-keylog");
		worker.setDaemon(true);
		worker.setPriority(Thread.NORM_PRIORITY - 1);
	}

	public static KeyLogService getInstance() {
		return INSTANCE;
	}

	public synchronized void start() {
		if (started) {
			return;
		}
		KeyLogDatabase.getInstance();
		worker.start();
		started = true;
		LogUtils.info("Keylog service started. db=" + KeyLogConfig.getDbFile().getAbsolutePath());
	}

	public synchronized void shutdown() {
		running = false;
		worker.interrupt();
		try {
			worker.join(4000L);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		flushSession(true);
	}

	/** Enqueue one keydown (name + wall-clock ms). Drops when queue is full. */
	public void offer(final String keyName, final long tsMs) {
		if (!KeyLogConfig.isEnabled() || keyName == null || keyName.length() == 0 || tsMs <= 0L) {
			return;
		}
		queue.offer(new Incoming(keyName, tsMs, false, "live"));
	}

	/**
	 * Import path: append many keys with precomputed times (may be synthetic).
	 * Blocks the caller briefly via queue; for bulk import prefer {@link #importSession}.
	 */
	public void offerImport(final String keyName, final long tsMs) {
		if (keyName == null || keyName.length() == 0 || tsMs <= 0L) {
			return;
		}
		try {
			queue.put(new Incoming(keyName, tsMs, true, "import"));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Write one finished session directly (used by import script / bulk import).
	 * Opens DB briefly; does not use the live queue.
	 */
	public long importSession(final KeyLogDatabase db, final List keyNames, final long startTs, final long endTs,
	        final boolean approx, final String source) throws Exception {
		if (db == null || keyNames == null || keyNames.isEmpty()) {
			return 0L;
		}
		final List events = new ArrayList(keyNames.size());
		final Map hourMap = new LinkedHashMap();
		long t = startTs;
		final int n = keyNames.size();
		final long span = Math.max(0L, endTs - startTs);
		final long step = n <= 1 ? 0L : Math.max(1L, span / (n - 1));
		for (int i = 0; i < n; i++) {
			final String name = (String) keyNames.get(i);
			final int keyId = db.ensureKeyId(name);
			final int delta = i == 0 ? 0 : (int) Math.min(65535L, step);
			events.add(new KeyLogCodec.Event(keyId, delta));
			if (i > 0) {
				t += delta;
			}
			final Long hour = Long.valueOf(KeyLogDatabase.floorHour(t));
			final Integer prev = (Integer) hourMap.get(hour);
			hourMap.put(hour, Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
		}
		final byte[] blob = KeyLogCodec.encode(events);
		final int[] buckets = new int[hourMap.size()];
		final long[] hours = new long[hourMap.size()];
		int idx = 0;
		for (final Iterator it = hourMap.entrySet().iterator(); it.hasNext();) {
			final Map.Entry e = (Map.Entry) it.next();
			hours[idx] = ((Long) e.getKey()).longValue();
			buckets[idx] = ((Integer) e.getValue()).intValue();
			idx++;
		}
		final long actualEnd = t > startTs ? t : endTs;
		return db.insertSession(startTs, actualEnd, n, approx, source, blob, buckets, hours);
	}

	public Map aggregateByDay(final long fromTs, final long toTs) {
		final Map byHour = aggregateByHour(fromTs, toTs);
		final Map byDay = new LinkedHashMap();
		for (final Iterator it = byHour.entrySet().iterator(); it.hasNext();) {
			final Map.Entry e = (Map.Entry) it.next();
			final long hourTs = ((Long) e.getKey()).longValue();
			final long dayTs = (hourTs / 86400000L) * 86400000L;
			final Long day = Long.valueOf(dayTs);
			final long add = ((Long) e.getValue()).longValue();
			final Long prev = (Long) byDay.get(day);
			byDay.put(day, Long.valueOf(prev == null ? add : prev.longValue() + add));
		}
		return byDay;
	}

	public Map aggregateByHour(final long fromTs, final long toTs) {
		final Map merged = new LinkedHashMap();
		final File[] files = KeyLogConfig.listDbFiles();
		final File local = KeyLogConfig.getDbFile();
		for (int i = 0; i < files.length; i++) {
			final File file = files[i];
			final boolean isLocal = sameFile(file, local);
			if (!isLocal && (file == null || !file.isFile())) {
				continue;
			}
			try {
				final KeyLogDatabase db = KeyLogDatabase.open(file, isLocal);
				if (db == null) {
					continue;
				}
				final Map part = db.sumByHour(fromTs, toTs);
				for (final Iterator it = part.entrySet().iterator(); it.hasNext();) {
					final Map.Entry e = (Map.Entry) it.next();
					final Long prev = (Long) merged.get(e.getKey());
					final long add = ((Long) e.getValue()).longValue();
					merged.put(e.getKey(), Long.valueOf(prev == null ? add : prev.longValue() + add));
				}
			}
			catch (Exception e) {
				LogUtils.warn("Keylog hour aggregate failed for " + file + ": " + e.getMessage(), e);
			}
		}
		return merged;
	}

	public long countKeys(final long fromTs, final long toTs) {
		long total = 0L;
		final File[] files = KeyLogConfig.listDbFiles();
		final File local = KeyLogConfig.getDbFile();
		for (int i = 0; i < files.length; i++) {
			final File file = files[i];
			final boolean isLocal = sameFile(file, local);
			if (!isLocal && (file == null || !file.isFile())) {
				continue;
			}
			try {
				final KeyLogDatabase db = KeyLogDatabase.open(file, isLocal);
				if (db != null) {
					total += db.sumKeys(fromTs, toTs);
				}
			}
			catch (Exception e) {
			}
		}
		return total;
	}

	public boolean hasAnyDatabase() {
		final File[] files = KeyLogConfig.listDbFiles();
		for (int i = 0; i < files.length; i++) {
			if (files[i] != null && files[i].isFile() && files[i].length() > 0L) {
				return true;
			}
		}
		return false;
	}

	public void run() {
		long nextFlushAt = System.currentTimeMillis() + KeyLogConfig.getFlushMs();
		while (running) {
			try {
				final long wait = Math.max(50L, nextFlushAt - System.currentTimeMillis());
				final Object item = queue.poll(wait, TimeUnit.MILLISECONDS);
				if (item instanceof Incoming) {
					handleIncoming((Incoming) item);
				}
				final long now = System.currentTimeMillis();
				if (pendingEvents.size() >= KeyLogConfig.getFlushKeys() || now >= nextFlushAt) {
					flushSession(false);
					nextFlushAt = now + KeyLogConfig.getFlushMs();
					maybeRotate();
				}
			}
			catch (InterruptedException e) {
				if (!running) {
					break;
				}
			}
			catch (Throwable t) {
				LogUtils.warn("Keylog worker error: " + t.getMessage(), t);
			}
		}
		flushSession(true);
	}

	private void handleIncoming(final Incoming in) {
		final long gap = KeyLogConfig.getSessionGapMs();
		if (!pendingEvents.isEmpty() && in.tsMs - lastEventTs > gap) {
			flushSession(true);
		}
		if (pendingEvents.isEmpty()) {
			sessionStartTs = in.tsMs;
			sessionApprox = in.approx;
			sessionSource = in.source;
			lastEventTs = in.tsMs;
		}
		final int keyId = resolveKeyId(in.name);
		if (keyId <= 0) {
			return;
		}
		final int delta = pendingEvents.isEmpty() ? 0
		        : (int) Math.min(65535L, Math.max(0L, in.tsMs - lastEventTs));
		pendingEvents.add(new KeyLogCodec.Event(keyId, delta));
		lastEventTs = in.tsMs;
		sessionApprox = sessionApprox || in.approx;
		if (!"live".equals(in.source)) {
			sessionSource = in.source;
		}
	}

	private int resolveKeyId(final String name) {
		final Integer cached = (Integer) nameToId.get(name);
		if (cached != null) {
			return cached.intValue();
		}
		try {
			final int id = KeyLogDatabase.getInstance().ensureKeyId(name);
			if (id > 0) {
				nameToId.put(name, Integer.valueOf(id));
			}
			return id;
		}
		catch (Exception e) {
			LogUtils.warn("Keylog dict failed: " + e.getMessage());
			return 0;
		}
	}

	private synchronized void flushSession(final boolean forceClose) {
		if (pendingEvents.isEmpty()) {
			return;
		}
		try {
			final List events = new ArrayList(pendingEvents);
			pendingEvents.clear();
			final long start = sessionStartTs;
			long end = lastEventTs;
			if (end < start) {
				end = start;
			}
			final Map hourMap = new LinkedHashMap();
			long t = start;
			for (int i = 0; i < events.size(); i++) {
				final KeyLogCodec.Event ev = (KeyLogCodec.Event) events.get(i);
				t += ev.deltaMs;
				final Long hour = Long.valueOf(KeyLogDatabase.floorHour(t));
				final Integer prev = (Integer) hourMap.get(hour);
				hourMap.put(hour, Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
			}
			final int[] buckets = new int[hourMap.size()];
			final long[] hours = new long[hourMap.size()];
			int idx = 0;
			for (final Iterator it = hourMap.entrySet().iterator(); it.hasNext();) {
				final Map.Entry e = (Map.Entry) it.next();
				hours[idx] = ((Long) e.getKey()).longValue();
				buckets[idx] = ((Integer) e.getValue()).intValue();
				idx++;
			}
			final byte[] blob = KeyLogCodec.encode(events);
			KeyLogDatabase.getInstance().insertSession(start, end, events.size(), sessionApprox, sessionSource, blob,
			        buckets, hours);
		}
		catch (Exception e) {
			LogUtils.warn("Keylog flush failed: " + e.getMessage(), e);
		}
		finally {
			if (forceClose) {
				sessionStartTs = 0L;
				lastEventTs = 0L;
			}
		}
	}

	private void maybeRotate() {
		final int maxMb = KeyLogConfig.getMaxMb();
		if (maxMb <= 0) {
			return;
		}
		try {
			final KeyLogDatabase db = KeyLogDatabase.getInstance();
			db.checkpoint();
			final File file = db.getDbFile();
			if (file == null || !file.isFile()) {
				return;
			}
			final long limit = maxMb * 1024L * 1024L;
			if (file.length() < limit) {
				return;
			}
			final File dir = file.getParentFile();
			final String mac = LocalMachineId.getMacHex();
			int part = 2;
			File dest;
			do {
				dest = new File(dir, "keylog-" + mac + "-p" + part + ".db");
				part++;
			} while (dest.exists() && part < 1000);
			if (file.renameTo(dest)) {
				final File wal = new File(file.getAbsolutePath() + "-wal");
				final File shm = new File(file.getAbsolutePath() + "-shm");
				if (wal.exists()) {
					wal.delete();
				}
				if (shm.exists()) {
					shm.delete();
				}
				KeyLogDatabase.resetForTests(KeyLogConfig.getDbFile());
				nameToId.clear();
				LogUtils.info("Keylog rotated to " + dest.getName());
			}
		}
		catch (Exception e) {
			LogUtils.warn("Keylog rotate failed: " + e.getMessage(), e);
		}
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

	private static final class Incoming {
		final String name;
		final long tsMs;
		final boolean approx;
		final String source;

		Incoming(final String name, final long tsMs, final boolean approx, final String source) {
			this.name = name;
			this.tsMs = tsMs;
			this.approx = approx;
			this.source = source;
		}
	}
}
