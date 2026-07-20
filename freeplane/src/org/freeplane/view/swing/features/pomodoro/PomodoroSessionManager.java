package org.freeplane.view.swing.features.pomodoro;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.Timer;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;

/**
 * Global free-timing pomodoro sessions: many paused, at most one running.
 */
public final class PomodoroSessionManager {
	public interface Listener {
		void pomodoroSessionsChanged();
	}

	private static PomodoroSessionManager INSTANCE;

	private final ModeController modeController;
	private final List listeners = new CopyOnWriteArrayList();
	private final Timer tickTimer;
	private PomodoroWindow window;
	private boolean shuttingDown;

	public static synchronized PomodoroSessionManager getInstance() {
		return INSTANCE;
	}

	public static synchronized PomodoroSessionManager install(final ModeController modeController) {
		if (INSTANCE == null) {
			INSTANCE = new PomodoroSessionManager(modeController);
		}
		return INSTANCE;
	}

	private PomodoroSessionManager(final ModeController modeController) {
		this.modeController = modeController;
		tickTimer = new Timer(1000, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				onTick();
			}
		});
		tickTimer.setRepeats(true);
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				pauseAllForShutdown();
			}
		}, "pomodoro-shutdown"));
	}

	public void addListener(final Listener listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	public void removeListener(final Listener listener) {
		listeners.remove(listener);
	}

	private void fireChanged() {
		for (int i = 0; i < listeners.size(); i++) {
			try {
				((Listener) listeners.get(i)).pomodoroSessionsChanged();
			}
			catch (Exception e) {
				LogUtils.warn("Pomodoro listener failed", e);
			}
		}
	}

	public NodeModel getRunningNode() {
		final List open = collectOpenPomodoroNodes();
		for (int i = 0; i < open.size(); i++) {
			final NodeModel node = (NodeModel) open.get(i);
			final PomodoroExtension ext = PomodoroExtension.getExtension(node);
			if (ext != null && PomodoroExtension.STATE_RUNNING.equals(ext.getState())) {
				return node;
			}
		}
		return null;
	}

	/** Running session, or the most recent paused segment that can be resumed. */
	public NodeModel getActiveSessionNode() {
		final NodeModel running = getRunningNode();
		if (running != null) {
			return running;
		}
		NodeModel bestPaused = null;
		long bestSessionAt = 0L;
		final List open = collectOpenPomodoroNodes();
		for (int i = 0; i < open.size(); i++) {
			final NodeModel node = (NodeModel) open.get(i);
			final PomodoroExtension ext = PomodoroExtension.getExtension(node);
			if (ext == null || !ext.isEnabled() || !PomodoroExtension.STATE_PAUSED.equals(ext.getState())) {
				continue;
			}
			if (ext.getActiveMs() <= 0 && ext.getSessionAt() <= 0) {
				continue;
			}
			final long sessionAt = ext.getSessionAt() > 0 ? ext.getSessionAt() : ext.getStartedAt();
			if (bestPaused == null || sessionAt >= bestSessionAt) {
				bestPaused = node;
				bestSessionAt = sessionAt;
			}
		}
		return bestPaused;
	}

	public void start(final NodeModel node) {
		if (node == null) {
			return;
		}
		pauseAllRunningExcept(node);
		final long now = System.currentTimeMillis();
		final PomodoroExtension next = extensionCopy(node);
		next.setEnabled(true);
		if (PomodoroExtension.STATE_RUNNING.equals(next.getState()) && next.getStartedAt() > 0) {
			PomodoroAttributes.write(node, next);
		}
		else {
			if (next.getSessionAt() <= 0 || PomodoroExtension.STATE_IDLE.equals(next.getState())) {
				next.setSessionAt(now);
			}
			next.setState(PomodoroExtension.STATE_RUNNING);
			next.setStartedAt(now);
			PomodoroAttributes.write(node, next);
		}
		ensureTickRunning();
		showWindow();
		fireChanged();
		refreshWindow();
	}

	public void pause(final NodeModel node) {
		if (node == null) {
			return;
		}
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		if (ext == null || !PomodoroExtension.STATE_RUNNING.equals(ext.getState())) {
			return;
		}
		final PomodoroExtension next = ext.copy();
		flushRunningIntoActive(next);
		next.setState(PomodoroExtension.STATE_PAUSED);
		next.setStartedAt(0);
		PomodoroAttributes.write(node, next);
		updateTickState();
		fireChanged();
		refreshWindow();
	}

	/** End current segment: commit focus into total + session log + note. */
	public void stop(final NodeModel node) {
		if (node == null) {
			return;
		}
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		if (ext == null) {
			return;
		}
		final long now = System.currentTimeMillis();
		final PomodoroExtension next = ext.copy();
		flushRunningIntoActive(next);
		final long focusMs = next.getActiveMs();
		if (focusMs > 0) {
			final long sessionStart = next.getSessionAt() > 0 ? next.getSessionAt() : now - focusMs;
			final PomodoroSessionRecord record = new PomodoroSessionRecord(sessionStart, now, focusMs);
			next.setLog(PomodoroLog.append(next.getLog(), record));
			next.setTotalMs(next.getTotalMs() + focusMs);
		}
		next.setActiveMs(0);
		next.setStartedAt(0);
		next.setSessionAt(0);
		next.setState(PomodoroExtension.STATE_IDLE);
		next.setEnabled(true);
		PomodoroAttributes.write(node, next);
		PomodoroNoteSync.sync(node, next);
		if (focusMs > 0) {
			PomodoroSound.playRing();
		}
		updateTickState();
		fireChanged();
		hideWindow();
	}

	/** Append a completed session for a wall-clock range (timer was not running). */
	public void backfillSession(final NodeModel node, final long startMs, final long endMs) {
		if (node == null || startMs <= 0 || endMs <= startMs) {
			return;
		}
		final long focusMs = endMs - startMs;
		final PomodoroExtension next = extensionCopy(node);
		next.setEnabled(true);
		final PomodoroSessionRecord record = new PomodoroSessionRecord(startMs, endMs, focusMs);
		next.setLog(PomodoroLog.append(next.getLog(), record));
		next.setTotalMs(next.getTotalMs() + focusMs);
		PomodoroAttributes.write(node, next);
		PomodoroNoteSync.sync(node, next);
		fireChanged();
		refreshWindow();
	}

	public void updateLogRecord(final NodeModel node, final int logIndex, final PomodoroSessionRecord record) {
		if (node == null || record == null || logIndex < 0) {
			return;
		}
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		if (ext == null) {
			return;
		}
		final List records = PomodoroLog.decode(ext.getLog());
		if (logIndex >= records.size()) {
			return;
		}
		final PomodoroExtension next = ext.copy();
		next.setLog(PomodoroLog.replaceRecord(next.getLog(), logIndex, record));
		next.setTotalMs(PomodoroLog.sumFocus(PomodoroLog.decode(next.getLog())));
		PomodoroAttributes.write(node, next);
		PomodoroNoteSync.sync(node, next);
		fireChanged();
		refreshWindow();
	}

	public void deleteLogRecord(final NodeModel node, final int logIndex) {
		if (node == null || logIndex < 0) {
			return;
		}
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		if (ext == null) {
			return;
		}
		final List records = PomodoroLog.decode(ext.getLog());
		if (logIndex >= records.size()) {
			return;
		}
		final PomodoroExtension next = ext.copy();
		next.setLog(PomodoroLog.removeRecord(next.getLog(), logIndex));
		next.setTotalMs(PomodoroLog.sumFocus(PomodoroLog.decode(next.getLog())));
		PomodoroAttributes.write(node, next);
		PomodoroNoteSync.sync(node, next);
		fireChanged();
		refreshWindow();
	}

	/** Hide floating timer without stopping sessions (e.g. after explicit Stop). */
	void hideWindow() {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (window != null) {
					window.hideQuietly();
				}
			}
		});
	}

	void refreshWindow() {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (window != null && window.isVisible()) {
					window.refresh();
				}
			}
		});
	}

	public void togglePauseResume(final NodeModel node) {
		if (node == null) {
			return;
		}
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		if (ext == null) {
			start(node);
			return;
		}
		if (PomodoroExtension.STATE_RUNNING.equals(ext.getState())) {
			pause(node);
		}
		else {
			start(node);
		}
	}

	public void endRunningOnWindowClose() {
		final NodeModel running = getRunningNode();
		if (running != null) {
			stop(running);
		}
		window = null;
		updateTickState();
		fireChanged();
	}

	public void showWindow() {
		if (shuttingDown) {
			return;
		}
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (window == null) {
					window = new PomodoroWindow(PomodoroSessionManager.this);
				}
				window.refresh();
				window.setVisible(true);
				window.toFront();
			}
		});
	}

	public void navigateTo(final NodeModel node) {
		if (node == null || node.getMap() == null) {
			return;
		}
		try {
			final Controller controller = Controller.getCurrentController();
			final IMapViewManager views = controller.getMapViewManager();
			final MapModel map = node.getMap();
			if (controller.getMap() != map) {
				final java.util.Map openMaps = views.getMaps(modeController.getModeName());
				if (openMaps != null) {
					final Iterator it = openMaps.entrySet().iterator();
					while (it.hasNext()) {
						final java.util.Map.Entry entry = (java.util.Map.Entry) it.next();
						if (entry.getValue() == map) {
							views.changeToMapView((String) entry.getKey());
							break;
						}
					}
				}
			}
			final ModeController mc = Controller.getCurrentModeController();
			mc.getMapController().displayNode(node);
			controller.getSelection().selectAsTheOnlyOneSelected(node);
			mc.getMapController().centerNode(node);
		}
		catch (Exception e) {
			LogUtils.warn("Pomodoro: navigate failed", e);
		}
	}

	/** Map-wide / all-open totals for stats bar. */
	public long[] computeStats(final boolean allMaps) {
		long today = 0L;
		long week = 0L;
		long total = 0L;
		int enabled = 0;
		int running = 0;
		int paused = 0;
		final long todayStart = PomodoroLog.startOfToday();
		final long weekStart = PomodoroLog.startOfWeek();
		final long now = System.currentTimeMillis();
		final List nodes = allMaps ? collectOpenPomodoroNodes() : collectCurrentMapPomodoroNodes();
		for (int i = 0; i < nodes.size(); i++) {
			final NodeModel node = (NodeModel) nodes.get(i);
			final PomodoroExtension ext = PomodoroExtension.getExtension(node);
			if (ext == null || !ext.isEnabled()) {
				continue;
			}
			enabled++;
			final String state = ext.getState();
			if (PomodoroExtension.STATE_RUNNING.equals(state)) {
				running++;
			}
			else if (PomodoroExtension.STATE_PAUSED.equals(state)) {
				paused++;
			}
			total += ext.liveTotalMs(now);
			today += PomodoroLog.sumFocusSince(PomodoroLog.decode(ext.getLog()), todayStart) + contribLiveToday(ext, now, todayStart);
			week += PomodoroLog.sumFocusSince(PomodoroLog.decode(ext.getLog()), weekStart) + contribLiveToday(ext, now, weekStart);
		}
		return new long[] { today, week, total, enabled, running, paused };
	}

	private static long contribLiveToday(final PomodoroExtension ext, final long now, final long since) {
		if (ext.liveSegmentMs(now) <= 0) {
			return 0L;
		}
		// Live segment counts toward today/week only if session started today/this week.
		final long anchor = ext.getSessionAt() > 0 ? ext.getSessionAt() : ext.getStartedAt();
		return anchor >= since ? ext.liveSegmentMs(now) : 0L;
	}

	public List listOpenPomodoroNodes() {
		return collectOpenPomodoroNodes();
	}

	public List listCurrentMapPomodoroNodes() {
		return collectCurrentMapPomodoroNodes();
	}

	/**
	 * Completed + live focus segments that overlap {@code [rangeStart, rangeEnd)}.
	 * Each element is a {@link CalendarSession}.
	 */
	public List collectSessionsInRange(final long rangeStart, final long rangeEnd) {
		final List out = new ArrayList();
		final long now = System.currentTimeMillis();
		final List nodes = collectOpenPomodoroNodes();
		for (int i = 0; i < nodes.size(); i++) {
			final NodeModel node = (NodeModel) nodes.get(i);
			final PomodoroExtension ext = PomodoroAttributes.read(node);
			if (ext == null || !ext.isEnabled()) {
				continue;
			}
			final List records = PomodoroLog.decode(ext.getLog());
			for (int r = 0; r < records.size(); r++) {
				final PomodoroSessionRecord rec = (PomodoroSessionRecord) records.get(r);
				if (rec.endMs <= rangeStart || rec.startMs >= rangeEnd) {
					continue;
				}
				out.add(new CalendarSession(node, Math.max(rec.startMs, rangeStart), Math.min(rec.endMs, rangeEnd),
				        rec.focusMs, false));
			}
			final long liveMs = ext.liveSegmentMs(now);
			if (liveMs > 0) {
				final long anchor = ext.getSessionAt() > 0 ? ext.getSessionAt() : ext.getStartedAt();
				final long end = now;
				if (end > rangeStart && anchor < rangeEnd) {
					out.add(new CalendarSession(node, Math.max(anchor, rangeStart), Math.min(end, rangeEnd), liveMs,
					        true));
				}
			}
		}
		return out;
	}

	/** Public DTO for calendar overlay. */
	public static final class CalendarSession {
		public final NodeModel node;
		public final long startMs;
		public final long endMs;
		public final long focusMs;
		public final boolean live;

		CalendarSession(final NodeModel node, final long startMs, final long endMs, final long focusMs,
		        final boolean live) {
			this.node = node;
			this.startMs = startMs;
			this.endMs = endMs;
			this.focusMs = focusMs;
			this.live = live;
		}
	}

	List collectCurrentMapPomodoroNodes() {
		final List result = new ArrayList();
		try {
			final MapModel map = Controller.getCurrentController().getMap();
			if (map != null) {
				collectEnabled(map, result);
			}
		}
		catch (Exception e) {
		}
		return result;
	}

	List collectOpenPomodoroNodes() {
		final List result = new ArrayList();
		try {
			final IMapViewManager views = Controller.getCurrentController().getMapViewManager();
			final java.util.Map maps = views.getMaps(modeController.getModeName());
			if (maps == null) {
				return result;
			}
			final Iterator it = maps.values().iterator();
			while (it.hasNext()) {
				final Object value = it.next();
				if (value instanceof MapModel) {
					collectEnabled((MapModel) value, result);
				}
			}
		}
		catch (Exception e) {
			LogUtils.warn("Pomodoro: collect open nodes failed", e);
		}
		return result;
	}

	private void collectEnabled(final MapModel map, final List out) {
		if (map == null || map.getRootNode() == null) {
			return;
		}
		collectEnabledRecursive(map.getRootNode(), out);
	}

	private void collectEnabledRecursive(final NodeModel node, final List out) {
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		if (ext != null && ext.isEnabled()) {
			out.add(node);
		}
		final List children = node.getChildren();
		if (children != null) {
			for (int i = 0; i < children.size(); i++) {
				collectEnabledRecursive((NodeModel) children.get(i), out);
			}
		}
	}

	public void recoverStaleRunning(final NodeModel root) {
		if (root == null) {
			return;
		}
		recoverRecursive(root);
		updateTickState();
		fireChanged();
	}

	private void recoverRecursive(final NodeModel node) {
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		if (ext != null && PomodoroExtension.STATE_RUNNING.equals(ext.getState())) {
			final PomodoroExtension next = ext.copy();
			// Crash reopen: keep accrued activeMs; only fold in live delta if short (avoid overnight inflation).
			flushRunningIntoActiveBounded(next, 4L * 60L * 60L * 1000L);
			next.setState(PomodoroExtension.STATE_PAUSED);
			next.setStartedAt(0);
			PomodoroAttributes.writeSilent(node, next);
		}
		else if (ext != null && ext.isEnabled()) {
			PomodoroVisibleAttributes.clear(node);
		}
		final List children = node.getChildren();
		if (children != null) {
		for (int i = 0; i < children.size(); i++) {
			recoverRecursive((NodeModel) children.get(i));
		}
	}
	}

	private void pauseAllRunningExcept(final NodeModel keep) {
		final List open = collectOpenPomodoroNodes();
		for (int i = 0; i < open.size(); i++) {
			final NodeModel node = (NodeModel) open.get(i);
			if (node == keep) {
				continue;
			}
			final PomodoroExtension ext = PomodoroExtension.getExtension(node);
			if (ext != null && PomodoroExtension.STATE_RUNNING.equals(ext.getState())) {
				final PomodoroExtension next = ext.copy();
				flushRunningIntoActive(next);
				next.setState(PomodoroExtension.STATE_PAUSED);
				next.setStartedAt(0);
				PomodoroAttributes.write(node, next);
			}
		}
	}

	private void pauseAllForShutdown() {
		shuttingDown = true;
		try {
			final List open = collectOpenPomodoroNodes();
			for (int i = 0; i < open.size(); i++) {
				final NodeModel node = (NodeModel) open.get(i);
				final PomodoroExtension ext = PomodoroExtension.getExtension(node);
				if (ext == null || !PomodoroExtension.STATE_RUNNING.equals(ext.getState())) {
					continue;
				}
				final PomodoroExtension next = ext.copy();
				flushRunningIntoActive(next);
				next.setState(PomodoroExtension.STATE_PAUSED);
				next.setStartedAt(0);
				PomodoroAttributes.writeSilent(node, next);
			}
		}
		catch (Exception e) {
		}
	}

	private static void flushRunningIntoActive(final PomodoroExtension next) {
		flushRunningIntoActiveBounded(next, Long.MAX_VALUE);
	}

	/** Fold live RUNNING wall time into activeMs, discarding delta beyond {@code maxDeltaMs}. */
	private static void flushRunningIntoActiveBounded(final PomodoroExtension next, final long maxDeltaMs) {
		if (PomodoroExtension.STATE_RUNNING.equals(next.getState()) && next.getStartedAt() > 0) {
			final long delta = Math.max(0L, System.currentTimeMillis() - next.getStartedAt());
			if (delta > 0 && delta <= maxDeltaMs) {
				next.setActiveMs(next.getActiveMs() + delta);
			}
			next.setStartedAt(0);
		}
	}

	private static PomodoroExtension extensionCopy(final NodeModel node) {
		final PomodoroExtension existing = PomodoroExtension.getExtension(node);
		return existing == null ? new PomodoroExtension() : existing.copy();
	}

	void refreshUi() {
		if (window != null) {
			window.applyTheme();
			window.refresh();
		}
		fireChanged();
	}

	private void ensureTickRunning() {
		if (!tickTimer.isRunning()) {
			tickTimer.start();
		}
	}

	private void updateTickState() {
		if (getRunningNode() != null) {
			ensureTickRunning();
		}
		else if (tickTimer.isRunning()) {
			tickTimer.stop();
		}
	}

	private void onTick() {
		final NodeModel running = getRunningNode();
		if (running == null) {
			tickTimer.stop();
			fireChanged();
			return;
		}
		try {
			Controller.getCurrentModeController().getMapController().nodeRefresh(running);
			// Refresh ancestors so Σ chips update live.
			NodeModel parent = running.getParentNode();
			int guard = 0;
			while (parent != null && guard++ < 64) {
				Controller.getCurrentModeController().getMapController().nodeRefresh(parent);
				parent = parent.getParentNode();
			}
		}
		catch (Exception e) {
		}
		if (window != null && window.isVisible()) {
			window.refresh();
		}
		fireChanged();
	}

	ModeController getModeController() {
		return modeController;
	}
}
