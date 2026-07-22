package org.freeplane.plugin.workspace.features.mapactivity;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.plugin.workspace.components.mapactivity.MapActivityOverlayPanel;
import org.freeplane.view.swing.features.pomodoro.PomodoroSessionManager;

/**
 * Keeps the map-activity overlay in sync with the active map / pomodoro.
 */
public final class MapActivityOverlayController {

	private static MapActivityOverlayController instance;

	private MapActivityOverlayPanel panel;
	private Timer debounceTimer;
	private Timer liveTickTimer;
	private boolean refreshing;
	private boolean pomodoroListenerAttached;

	private MapActivityOverlayController() {
	}

	public static synchronized MapActivityOverlayController getInstance() {
		if (instance == null) {
			instance = new MapActivityOverlayController();
		}
		return instance;
	}

	public static void install(final ModeController modeController) {
		getInstance().bind(modeController);
	}

	public void setPanel(final MapActivityOverlayPanel panel) {
		this.panel = panel;
		refreshUi();
		ensureLiveTick();
	}

	public MapActivityOverlayPanel getPanel() {
		return panel;
	}

	private void bind(final ModeController modeController) {
		modeController.getMapController().addMapLifeCycleListener(new IMapLifeCycleListener() {
			public void onCreate(final MapModel map) {
				scheduleRefresh();
			}

			public void onRemove(final MapModel map) {
				scheduleRefresh();
			}

			public void onSavedAs(final MapModel map) {
			}

			public void onSaved(final MapModel map) {
			}
		});
		modeController.getMapController().addNodeChangeListener(new INodeChangeListener() {
			public void nodeChanged(final NodeChangeEvent event) {
				if (event == null || event.getNode() == null) {
					return;
				}
				if (!isCurrentMap(event.getNode().getMap())) {
					return;
				}
				// Icons / reminders / pomodoro / text all affect the pulse list.
				scheduleRefresh();
			}
		});
		Controller.getCurrentController().getMapViewManager().addMapViewChangeListener(new IMapViewChangeListener() {
			public void afterViewChange(final Component oldView, final Component newView) {
				scheduleRefresh();
			}

			public void afterViewClose(final Component oldView) {
				scheduleRefresh();
			}

			public void afterViewCreated(final Component mapView) {
				scheduleRefresh();
			}

			public void beforeViewChange(final Component oldView, final Component newView) {
			}
		});

		final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
		if (mgr != null) {
			attachPomodoroListener(mgr);
		}
		ensureLiveTick();
	}

	private void attachPomodoroListener(final PomodoroSessionManager mgr) {
		if (pomodoroListenerAttached || mgr == null) {
			return;
		}
		mgr.addListener(new PomodoroSessionManager.Listener() {
			public void pomodoroSessionsChanged() {
				scheduleRefresh();
				ensureLiveTick();
			}
		});
		pomodoroListenerAttached = true;
	}

	private void ensureLiveTick() {
		try {
			final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
			if (mgr != null) {
				attachPomodoroListener(mgr);
			}
		}
		catch (final Exception e) {
			// ignore
		}
		boolean needTick = false;
		try {
			final PomodoroSessionManager mgr = PomodoroSessionManager.getInstance();
			if (mgr != null && mgr.getRunningNode() != null) {
				final NodeModel running = mgr.getRunningNode();
				needTick = isCurrentMap(running.getMap());
			}
		}
		catch (final Exception e) {
			needTick = false;
		}
		if (needTick) {
			if (liveTickTimer == null) {
				liveTickTimer = new Timer(1000, new ActionListener() {
					public void actionPerformed(final ActionEvent e) {
						refreshUi();
						ensureLiveTick();
					}
				});
				liveTickTimer.setRepeats(true);
			}
			if (!liveTickTimer.isRunning()) {
				liveTickTimer.start();
			}
		}
		else if (liveTickTimer != null && liveTickTimer.isRunning()) {
			liveTickTimer.stop();
		}
	}

	private void scheduleRefresh() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					scheduleRefresh();
				}
			});
			return;
		}
		if (debounceTimer == null) {
			debounceTimer = new Timer(180, new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					refreshUi();
					ensureLiveTick();
				}
			});
			debounceTimer.setRepeats(false);
		}
		debounceTimer.restart();
	}

	public void refreshUi() {
		if (panel == null || refreshing) {
			return;
		}
		refreshing = true;
		try {
			panel.refreshFromCurrentMap();
		}
		finally {
			refreshing = false;
		}
	}

	private static boolean isCurrentMap(final MapModel map) {
		if (map == null) {
			return false;
		}
		try {
			return map == Controller.getCurrentController().getMap();
		}
		catch (final Exception e) {
			return false;
		}
	}

}
