package org.docear.plugin.core.todoist;

/**
 * Prevents overlapping Todoist full-sync / auto-push runs. Re-entrant for the owning thread so
 * {@code syncAll} can call push/import without nesting deadlock. Does not hold a Java monitor
 * across {@code EventQueue.invokeAndWait} (that would deadlock with the EDT).
 */
final class TodoistSyncGuard {
	private static final Object LOCK = new Object();
	private static Thread owner;
	private static int depth;

	private TodoistSyncGuard() {
	}

	/** Blocks until idle (or re-enters if already owned by this thread). Pair with {@link #leave()}. */
	static void enter() {
		final Thread self = Thread.currentThread();
		synchronized (LOCK) {
			while (owner != null && owner != self) {
				try {
					LOCK.wait();
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
			owner = self;
			depth++;
		}
	}

	/** Non-blocking: returns false if another thread owns the guard. */
	static boolean tryEnter() {
		final Thread self = Thread.currentThread();
		synchronized (LOCK) {
			if (owner != null && owner != self) {
				return false;
			}
			owner = self;
			depth++;
			return true;
		}
	}

	static void leave() {
		synchronized (LOCK) {
			if (owner != Thread.currentThread()) {
				return;
			}
			depth--;
			if (depth <= 0) {
				depth = 0;
				owner = null;
				LOCK.notifyAll();
			}
		}
	}
}
