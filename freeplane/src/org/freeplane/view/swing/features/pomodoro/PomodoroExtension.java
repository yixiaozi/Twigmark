package org.freeplane.view.swing.features.pomodoro;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.map.NodeModel;

/**
 * Pomodoro / focus-time metadata stored on a mind-map node.
 */
public final class PomodoroExtension implements IExtension {
	public static final String STATE_IDLE = "idle";
	public static final String STATE_RUNNING = "running";
	public static final String STATE_PAUSED = "paused";

	private boolean enabled;
	private long totalMs;
	private long activeMs;
	private String state = STATE_IDLE;
	private long startedAt;
	/** Wall-clock when the current multi-pause session began (idle→running). */
	private long sessionAt;
	/** Wall-clock when the current pause began; 0 if not paused. */
	private long pausedAt;
	/**
	 * Encoded pause intervals for the open session ({@link PomodoroPauseInterval#encodeList}),
	 * before the session is committed to {@link #log}.
	 */
	private String sessionPauses = "";
	/** Encoded session history ({@link PomodoroLog}). */
	private String log = "";

	static PomodoroExtension getExtension(final NodeModel node) {
		return node == null ? null : (PomodoroExtension) node.getExtension(PomodoroExtension.class);
	}

	static PomodoroExtension getOrCreateExtension(final NodeModel node) {
		PomodoroExtension extension = getExtension(node);
		if (extension == null) {
			extension = new PomodoroExtension();
			node.addExtension(extension);
		}
		return extension;
	}

	boolean isEmpty() {
		return !enabled && totalMs <= 0 && activeMs <= 0 && startedAt <= 0 && sessionAt <= 0
				&& pausedAt <= 0
				&& (sessionPauses == null || sessionPauses.length() == 0)
				&& (state == null || STATE_IDLE.equals(state))
				&& (log == null || log.length() == 0);
	}

	PomodoroExtension copy() {
		final PomodoroExtension copy = new PomodoroExtension();
		copy.enabled = enabled;
		copy.totalMs = totalMs;
		copy.activeMs = activeMs;
		copy.state = state;
		copy.startedAt = startedAt;
		copy.sessionAt = sessionAt;
		copy.pausedAt = pausedAt;
		copy.sessionPauses = sessionPauses == null ? "" : sessionPauses;
		copy.log = log == null ? "" : log;
		return copy;
	}

	void apply(final PomodoroExtension source) {
		if (source == null) {
			enabled = false;
			totalMs = 0;
			activeMs = 0;
			state = STATE_IDLE;
			startedAt = 0;
			sessionAt = 0;
			pausedAt = 0;
			sessionPauses = "";
			log = "";
			return;
		}
		enabled = source.enabled;
		totalMs = source.totalMs;
		activeMs = source.activeMs;
		state = source.state == null ? STATE_IDLE : source.state;
		startedAt = source.startedAt;
		sessionAt = source.sessionAt;
		pausedAt = source.pausedAt;
		sessionPauses = source.sessionPauses == null ? "" : source.sessionPauses;
		log = source.log == null ? "" : source.log;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
	}

	public long getTotalMs() {
		return totalMs;
	}

	public void setTotalMs(final long totalMs) {
		this.totalMs = Math.max(0L, totalMs);
	}

	public long getActiveMs() {
		return activeMs;
	}

	public void setActiveMs(final long activeMs) {
		this.activeMs = Math.max(0L, activeMs);
	}

	public String getState() {
		return state == null ? STATE_IDLE : state;
	}

	public void setState(final String state) {
		this.state = state == null || state.length() == 0 ? STATE_IDLE : state;
	}

	public long getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(final long startedAt) {
		this.startedAt = Math.max(0L, startedAt);
	}

	public long getSessionAt() {
		return sessionAt;
	}

	public void setSessionAt(final long sessionAt) {
		this.sessionAt = Math.max(0L, sessionAt);
	}

	public long getPausedAt() {
		return pausedAt;
	}

	public void setPausedAt(final long pausedAt) {
		this.pausedAt = Math.max(0L, pausedAt);
	}

	public String getSessionPauses() {
		return sessionPauses == null ? "" : sessionPauses;
	}

	public void setSessionPauses(final String sessionPauses) {
		this.sessionPauses = sessionPauses == null ? "" : sessionPauses;
	}

	public String getLog() {
		return log == null ? "" : log;
	}

	public void setLog(final String log) {
		this.log = log == null ? "" : log;
	}

	public long liveSegmentMs(final long now) {
		long ms = activeMs;
		if (STATE_RUNNING.equals(getState()) && startedAt > 0 && now > startedAt) {
			ms += now - startedAt;
		}
		return ms;
	}

	public long liveTotalMs(final long now) {
		return totalMs + liveSegmentMs(now);
	}

	public int sessionCount() {
		return PomodoroLog.decode(getLog()).size();
	}
}
