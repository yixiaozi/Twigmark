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
 * Starting one auto-pauses others. State is persisted on mind-map nodes.
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

	/** Start or resume focus on {@code node}. Auto-enables switch; pauses any other running session. */
	public void start(final NodeModel node) {
		if (node == null) {
			return;
		}
		pauseAllRunningExcept(node);
		final PomodoroExtension next = extensionCopy(node);
		next.setEnabled(true);
		if (PomodoroExtension.STATE_RUNNING.equals(next.getState()) && next.getStartedAt() > 0) {
			// already running this node
		}
		else {
			next.setState(PomodoroExtension.STATE_RUNNING);
			next.setStartedAt(System.currentTimeMillis());
		}
		PomodoroAttributes.write(node, next);
		ensureTickRunning();
		showWindow();
		fireChanged();
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
	}

	/** End current segment: fold active time into total, back to idle (enabled stays on). */
	public void stop(final NodeModel node) {
		if (node == null) {
			return;
		}
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		if (ext == null) {
			return;
		}
		final PomodoroExtension next = ext.copy();
		flushRunningIntoActive(next);
		next.setTotalMs(next.getTotalMs() + next.getActiveMs());
		next.setActiveMs(0);
		next.setStartedAt(0);
		next.setState(PomodoroExtension.STATE_IDLE);
		next.setEnabled(true);
		PomodoroAttributes.write(node, next);
		updateTickState();
		fireChanged();
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

	/** Close window policy: end the running session (commit), keep paused sessions. */
	public void endRunningOnWindowClose() {
		final NodeModel running = getRunningNode();
		if (running != null) {
			stop(running);
		}
		if (window != null) {
			window = null;
		}
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
				if (!(value instanceof MapModel)) {
					continue;
				}
				collectEnabled((MapModel) value, result);
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

	/** After map load: demote stale running → paused (do not count offline wall clock). */
	public void recoverStaleRunning(final NodeModel root) {
		if (root == null) {
			return;
		}
		recoverRecursive(root);
	}

	private void recoverRecursive(final NodeModel node) {
		final PomodoroExtension ext = PomodoroExtension.getExtension(node);
		if (ext != null && PomodoroExtension.STATE_RUNNING.equals(ext.getState())) {
			final PomodoroExtension next = ext.copy();
			next.setState(PomodoroExtension.STATE_PAUSED);
			next.setStartedAt(0);
			PomodoroAttributes.writeSilent(node, next);
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
			// best-effort during JVM exit
		}
	}

	private static void flushRunningIntoActive(final PomodoroExtension next) {
		if (PomodoroExtension.STATE_RUNNING.equals(next.getState()) && next.getStartedAt() > 0) {
			final long delta = Math.max(0L, System.currentTimeMillis() - next.getStartedAt());
			next.setActiveMs(next.getActiveMs() + delta);
			next.setStartedAt(0);
		}
	}

	private static PomodoroExtension extensionCopy(final NodeModel node) {
		final PomodoroExtension existing = PomodoroExtension.getExtension(node);
		return existing == null ? new PomodoroExtension() : existing.copy();
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
