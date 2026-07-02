package org.docear.plugin.mcp.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.SwingUtilities;

public final class EdtRunner {

	private static final long DEFAULT_TIMEOUT_MS = 30000L;

	public interface Task {
		Object run() throws Exception;
	}

	private EdtRunner() {
	}

	public static Object run(final Task task) throws Exception {
		return run(task, DEFAULT_TIMEOUT_MS);
	}

	public static Object run(final Task task, final long timeoutMs) throws Exception {
		if (SwingUtilities.isEventDispatchThread()) {
			return task.run();
		}
		final Object[] result = new Object[1];
		final Exception[] error = new Exception[1];
		final CountDownLatch done = new CountDownLatch(1);
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					result[0] = task.run();
				}
				catch (Exception e) {
					error[0] = e;
				}
				finally {
					done.countDown();
				}
			}
		});
		if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
			throw new TimeoutException("Docear UI did not respond within " + timeoutMs + "ms. "
					+ "Close any open dialogs and try again.");
		}
		if (error[0] != null) {
			throw error[0];
		}
		return result[0];
	}

	public static void runVoid(final Task task) throws Exception {
		run(new Task() {
			public Object run() throws Exception {
				task.run();
				return null;
			}
		});
	}
}
