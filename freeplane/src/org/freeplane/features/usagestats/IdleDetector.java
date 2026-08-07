package org.freeplane.features.usagestats;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class IdleDetector {
	/** Default: 5 minutes — mind-map reading/thinking often has no key/mouse for a while. */
	public static final int DEFAULT_IDLE_THRESHOLD_MS = 5 * 60 * 1000;
	private static final int CHECK_INTERVAL_MS = 1000;

	private long idleThresholdMs = DEFAULT_IDLE_THRESHOLD_MS;
	private volatile long lastActivityTime;
	private volatile boolean isIdle;
	private volatile boolean isRunning;
	private ScheduledExecutorService scheduler;
	private AWTEventListener awtEventListener;
	private IdleListener idleListener;

	public interface IdleListener {
		void onIdleDetected(long idleTimeMs);

		/**
		 * Called when user becomes active again after idle.
		 * {@code chargeableIdleMs} is only the time past the idle threshold
		 * (the grace period before threshold still counts as effective use).
		 */
		void onUserActivity(long chargeableIdleMs);
	}

	public IdleDetector() {
		this.lastActivityTime = System.currentTimeMillis();
		this.isIdle = false;
	}

	public void setIdleListener(IdleListener listener) {
		this.idleListener = listener;
	}

	public void setIdleThresholdMs(long thresholdMs) {
		this.idleThresholdMs = thresholdMs > 0L ? thresholdMs : DEFAULT_IDLE_THRESHOLD_MS;
	}

	public long getIdleThresholdMs() {
		return idleThresholdMs;
	}

	public synchronized void start() {
		if (isRunning) {
			return;
		}
		isRunning = true;
		lastActivityTime = System.currentTimeMillis();
		isIdle = false;

		startAWTEventListener();
		startScheduler();
	}

	public synchronized void stop() {
		if (!isRunning) {
			return;
		}
		isRunning = false;

		stopScheduler();
		stopAWTEventListener();
	}

	private void startAWTEventListener() {
		awtEventListener = new AWTEventListener() {
			public void eventDispatched(AWTEvent event) {
				if (isUserActivity(event)) {
					onUserActivityDetected();
				}
			}
		};
		Toolkit.getDefaultToolkit().addAWTEventListener(awtEventListener,
		        AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK
		                | AWTEvent.MOUSE_WHEEL_EVENT_MASK);
	}

	private void stopAWTEventListener() {
		if (awtEventListener != null) {
			Toolkit.getDefaultToolkit().removeAWTEventListener(awtEventListener);
			awtEventListener = null;
		}
	}

	private void startScheduler() {
		scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler.scheduleAtFixedRate(new Runnable() {
			public void run() {
				checkIdleStatus();
			}
		}, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	private void stopScheduler() {
		if (scheduler != null) {
			scheduler.shutdownNow();
			scheduler = null;
		}
	}

	private boolean isUserActivity(AWTEvent event) {
		if (event instanceof KeyEvent) {
			final int id = event.getID();
			return id == KeyEvent.KEY_PRESSED || id == KeyEvent.KEY_TYPED;
		}
		if (event instanceof MouseWheelEvent) {
			return true;
		}
		if (event instanceof MouseEvent) {
			final int id = event.getID();
			return id == MouseEvent.MOUSE_PRESSED || id == MouseEvent.MOUSE_RELEASED
			        || id == MouseEvent.MOUSE_CLICKED || id == MouseEvent.MOUSE_MOVED
			        || id == MouseEvent.MOUSE_DRAGGED;
		}
		return false;
	}

	private void onUserActivityDetected() {
		final long currentTime = System.currentTimeMillis();
		final long previousActivityTime = lastActivityTime;
		lastActivityTime = currentTime;

		if (isIdle) {
			isIdle = false;
			final long gap = currentTime - previousActivityTime;
			final long chargeable = Math.max(0L, gap - idleThresholdMs);
			notifyUserActivity(chargeable);
		}
	}

	private void checkIdleStatus() {
		final long currentTime = System.currentTimeMillis();
		final long idleTime = currentTime - lastActivityTime;

		if (!isIdle && idleTime >= idleThresholdMs) {
			isIdle = true;
			notifyIdleDetected(idleTime);
		}
	}

	private void notifyIdleDetected(long idleTime) {
		if (idleListener != null) {
			try {
				idleListener.onIdleDetected(idleTime);
			}
			catch (Exception e) {
				// Ignore listener exceptions
			}
		}
	}

	private void notifyUserActivity(long chargeableIdleMs) {
		if (idleListener != null) {
			try {
				idleListener.onUserActivity(chargeableIdleMs);
			}
			catch (Exception e) {
				// Ignore listener exceptions
			}
		}
	}

	public long getIdleTimeMs() {
		return System.currentTimeMillis() - lastActivityTime;
	}

	public boolean isIdle() {
		return isIdle;
	}

	public void markActivity() {
		this.lastActivityTime = System.currentTimeMillis();
		this.isIdle = false;
	}

	/**
	 * Chargeable idle since last activity if currently idle (for flushing at session end).
	 */
	public long getChargeableIdleMsNow() {
		if (!isIdle) {
			return 0L;
		}
		final long gap = System.currentTimeMillis() - lastActivityTime;
		return Math.max(0L, gap - idleThresholdMs);
	}
}
