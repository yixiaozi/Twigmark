package org.freeplane.plugin.workspace.features.mapactivity;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.MapChangeEvent;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewChangeListener;
import org.freeplane.plugin.workspace.components.mapactivity.MapActivityOverlayPanel;
import org.freeplane.view.swing.features.pomodoro.PomodoroAttributes;
import org.freeplane.view.swing.features.pomodoro.PomodoroExtension;
import org.freeplane.view.swing.features.pomodoro.PomodoroSessionManager;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;

/**
 * Keeps the map-activity overlay in sync with the active map / pomodoro.
 */
public final class MapActivityOverlayController {

	private static final String TODO_ICON = "hourglass";
	private static final int REFRESH_DEBOUNCE_MS = 280;

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
				if (!affectsActivityOverlay(event)) {
					return;
				}
				scheduleRefresh();
			}
		});
		modeController.getMapController().addMapChangeListener(new IMapChangeListener() {
			public void mapChanged(final MapChangeEvent event) {
			}

			public void onNodeInserted(final NodeModel parent, final NodeModel child, final int newIndex) {
				if (child != null && isCurrentMap(child.getMap()) && isActivityNode(child)) {
					scheduleRefresh();
				}
			}

			public void onNodeDeleted(final NodeModel parent, final NodeModel child, final int index) {
				if (isCurrentMap(parent != null ? parent.getMap() : null)) {
					scheduleRefresh();
				}
			}

			public void onNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
					final NodeModel child, final int newIndex) {
				if (child != null && isCurrentMap(child.getMap()) && isActivityNode(child)) {
					scheduleRefresh();
				}
			}

			public void onPreNodeDelete(final NodeModel oldParent, final NodeModel selectedNode, final int index) {
			}

			public void onPreNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
					final NodeModel child, final int newIndex) {
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
			debounceTimer = new Timer(REFRESH_DEBOUNCE_MS, new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					refreshUi();
					ensureLiveTick();
				}
			});
			debounceTimer.setRepeats(false);
		}
		debounceTimer.restart();
	}

	/**
	 * Skip overlay rebuild for ordinary text edits on non-activity nodes — the common edit path.
	 */
	private static boolean affectsActivityOverlay(final NodeChangeEvent event) {
		final Object prop = event.getProperty();
		if (prop == null || NodeModel.NODE_ICON.equals(prop) || "hierarchical_icons".equals(prop)
				|| prop == ReminderExtension.class || prop == PomodoroExtension.class) {
			return true;
		}
		// ReminderCycleExtension / ReminderTaskExtension are package-private in freeplane.
		if (prop instanceof Class) {
			final String name = ((Class) prop).getSimpleName();
			if ("ReminderCycleExtension".equals(name) || "ReminderTaskExtension".equals(name)) {
				return true;
			}
		}
		if (NodeModel.NODE_TEXT.equals(prop)) {
			return isActivityNode(event.getNode());
		}
		return false;
	}

	private static boolean isActivityNode(final NodeModel node) {
		if (node == null) {
			return false;
		}
		final ReminderExtension reminder = ReminderExtension.getExtension(node);
		if (reminder != null && reminder.getRemindUserAt() > 0) {
			return true;
		}
		final PomodoroExtension pomo = PomodoroAttributes.read(node);
		if (pomo != null && pomo.isEnabled()) {
			return true;
		}
		try {
			final Collection icons = IconController.getController().getIcons(node);
			if (icons != null) {
				for (final Object iconObj : icons) {
					if (!(iconObj instanceof MindIcon)) {
						continue;
					}
					final String name = ((MindIcon) iconObj).getName();
					if (name == null) {
						continue;
					}
					if (TODO_ICON.equalsIgnoreCase(name) || name.toLowerCase().startsWith("flag")) {
						return true;
					}
				}
			}
		}
		catch (final Exception e) {
			return true;
		}
		return false;
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
