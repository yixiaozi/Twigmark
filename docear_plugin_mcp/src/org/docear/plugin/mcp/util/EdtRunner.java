package org.docear.plugin.mcp.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.SwingUtilities;

import org.freeplane.core.util.LogUtils;

public final class EdtRunner {

	/** Default 90s so large headless map loads are less likely to false-timeout under I/O load. */
	private static final long DEFAULT_TIMEOUT_MS = 90000L;
	private static final AtomicLong SLOW_WARN_MS = new AtomicLong(5000L);

	public interface Task {
		Object run() throws Exception;
	}

	private EdtRunner() {
	}

	public static Object run(final Task task) throws Exception {
		return run(task, configuredTimeoutMs());
	}

	public static Object run(final Task task, final long timeoutMs) throws Exception {
		if (SwingUtilities.isEventDispatchThread()) {
			return task.run();
		}
		final Object[] result = new Object[1];
		final Exception[] error = new Exception[1];
		final CountDownLatch done = new CountDownLatch(1);
		final long submittedAt = System.currentTimeMillis();
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
		final long elapsed = System.currentTimeMillis() - submittedAt;
		if (elapsed >= SLOW_WARN_MS.get()) {
			LogUtils.warn("MCP EDT task took " + elapsed + "ms (timeout=" + timeoutMs + "ms)");
		}
		if (error[0] != null) {
			throw error[0];
		}
		return result[0];
	}

	private static long configuredTimeoutMs() {
		final String prop = System.getProperty("mcp.edtTimeoutMs", "");
		if (prop != null && prop.trim().length() > 0) {
			try {
				final long value = Long.parseLong(prop.trim());
				if (value >= 5000L) {
					return value;
				}
			}
			catch (NumberFormatException ignore) {
			}
		}
		return DEFAULT_TIMEOUT_MS;
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
