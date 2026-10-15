package org.docear.plugin.core.todoist;

/**
 * Picks the sync direction from the two last-modified clocks.
 * <p>
 * After a sync, each side's time is remembered. The next run treats a side as changed only when
 * its clock moved past that remembered value, so a write this sync just made is not a new edit.
 * With no remembered pair yet, the two clocks are compared once.
 */
final class TodoistSyncClock {
	static final int UNKNOWN = -1;
	static final int SKIP = 0;
	static final int PUSH = 1;
	static final int PULL = 2;

	private TodoistSyncClock() {
	}

	static int choose(final long localModified, final long remoteUpdated, final long storedLocal,
			final long storedRemote) {
		if (localModified <= 0 || remoteUpdated <= 0) {
			return UNKNOWN;
		}
		if (storedLocal <= 0 || storedRemote <= 0) {
			if (localModified > remoteUpdated) {
				return PUSH;
			}
			if (remoteUpdated > localModified) {
				return PULL;
			}
			return SKIP;
		}
		final boolean localChanged = localModified > storedLocal;
		final boolean remoteChanged = remoteUpdated > storedRemote;
		if (localChanged && !remoteChanged) {
			return PUSH;
		}
		if (remoteChanged && !localChanged) {
			return PULL;
		}
		if (localChanged && remoteChanged) {
			return localModified >= remoteUpdated ? PUSH : PULL;
		}
		return SKIP;
	}

	/** Todoist {@code updated_at}, for example {@code 2026-10-15T12:34:56.123456Z}. */
	static long parseUpdatedAt(final String value) {
		if (value == null) {
			return 0L;
		}
		final String text = value.trim();
		if (text.length() == 0) {
			return 0L;
		}
		try {
			return java.time.Instant.parse(text).toEpochMilli();
		}
		catch (Exception e) {
			return 0L;
		}
	}
}
