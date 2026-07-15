package org.docear.plugin.core.todoist;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Timer;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.MapChangeEvent;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;

/**
 * Automatic bidirectional Todoist sync:
 * <ul>
 * <li>Debounced push when reminder text / due time change on a mind map</li>
 * <li>Periodic full reconcile: push → pull linked nodes (any map) → import unlinked only</li>
 * </ul>
 */
public final class TodoistAutoSyncService {
	private static final int DEBOUNCE_MS = 1500;
	private static final int STARTUP_DELAY_MS = 20000;

	private static TodoistAutoSyncService instance;
	private static final ThreadLocal suppressOutgoing = new ThreadLocal() {
		protected Object initialValue() {
			return Boolean.FALSE;
		}
	};

	private final Set dirtyNodes = new HashSet();
	private final Set closeNodes = new HashSet();
	private Timer debounceTimer;
	private Timer periodicTimer;
	private boolean syncRunning;
	private boolean installed;

	private TodoistAutoSyncService() {
	}

	public static synchronized TodoistAutoSyncService getInstance() {
		if (instance == null) {
			instance = new TodoistAutoSyncService();
		}
		return instance;
	}

	/** Suppress map→Todoist while applying Todoist→map writes. */
	public static boolean setSuppressOutgoing(boolean suppress) {
		Boolean previous = (Boolean) suppressOutgoing.get();
		suppressOutgoing.set(Boolean.valueOf(suppress));
		return previous != null && previous.booleanValue();
	}

	public static boolean isSuppressOutgoing() {
		Boolean value = (Boolean) suppressOutgoing.get();
		return value != null && value.booleanValue();
	}

	public void install(final ModeController modeController) {
		if (installed || modeController == null) {
			return;
		}
		installed = true;
		modeController.getMapController().addNodeChangeListener(new INodeChangeListener() {
			public void nodeChanged(final NodeChangeEvent event) {
				onNodeChanged(event);
			}
		});
		modeController.getMapController().addMapChangeListener(new IMapChangeListener() {
			public void mapChanged(final MapChangeEvent event) {
			}

			public void onNodeDeleted(final NodeModel parent, final NodeModel child, final int index) {
				queueClose(child);
			}

			public void onNodeInserted(final NodeModel parent, final NodeModel child, final int newIndex) {
				queueDirty(child);
			}

			public void onNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
					final NodeModel child, final int newIndex) {
				queueDirty(child);
			}

			public void onPreNodeDelete(final NodeModel oldParent, final NodeModel selectedNode, final int index) {
				queueClose(selectedNode);
			}

			public void onPreNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
					final NodeModel child, final int newIndex) {
			}
		});
		restartPeriodicTimer();
		LogUtils.info("Todoist auto-sync installed (interval=" + TodoistConfig.getAutoSyncIntervalMinutes()
				+ " min, enabled=" + TodoistConfig.isAutoSyncEnabled() + ")");
	}

	public void restartPeriodicTimer() {
		if (periodicTimer != null) {
			periodicTimer.stop();
		}
		if (!TodoistConfig.isAutoSyncEnabled()) {
			return;
		}
		final int delayMs = Math.max(1, TodoistConfig.getAutoSyncIntervalMinutes()) * 60 * 1000;
		periodicTimer = new Timer(delayMs, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				runPeriodicSync();
			}
		});
		periodicTimer.setInitialDelay(STARTUP_DELAY_MS);
		periodicTimer.setRepeats(true);
		periodicTimer.start();
	}

	private void onNodeChanged(final NodeChangeEvent event) {
		if (isSuppressOutgoing() || !TodoistConfig.isAutoSyncEnabled()) {
			return;
		}
		final Object property = event.getProperty();
		if (property == null) {
			return;
		}
		if (property.equals(NodeModel.NODE_TEXT) || property.equals(ReminderExtension.class)
				|| isReminderTaskProperty(property)
				|| property.getClass().getName().indexOf("ReminderCycle") >= 0) {
			final NodeModel node = event.getNode();
			if (ReminderExtension.getExtension(node) != null || TodoistReminderFactory.getTaskId(node) != null) {
				queueDirty(node);
			}
		}
	}

	private static boolean isReminderTaskProperty(final Object property) {
		if (!(property instanceof Class)) {
			return false;
		}
		final String name = ((Class) property).getName();
		return name.endsWith("ReminderTaskExtension");
	}

	private synchronized void queueDirty(final NodeModel node) {
		if (node == null || isSuppressOutgoing() || !TodoistConfig.isAutoSyncEnabled()) {
			return;
		}
		dirtyNodes.add(node);
		scheduleDebounce();
	}

	private synchronized void queueClose(final NodeModel node) {
		if (node == null || isSuppressOutgoing() || !TodoistConfig.isAutoSyncEnabled()) {
			return;
		}
		if (TodoistReminderFactory.getTaskId(node) != null || ReminderExtension.getExtension(node) != null) {
			closeNodes.add(node);
			scheduleDebounce();
		}
	}

	private void scheduleDebounce() {
		if (debounceTimer != null) {
			debounceTimer.restart();
			return;
		}
		debounceTimer = new Timer(DEBOUNCE_MS, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				debounceTimer.stop();
				flushDirtyQueue();
			}
		});
		debounceTimer.setRepeats(false);
		debounceTimer.start();
	}

	private void flushDirtyQueue() {
		final List toPush;
		final List toClose;
		synchronized (this) {
			toPush = new ArrayList(dirtyNodes);
			toClose = new ArrayList(closeNodes);
			dirtyNodes.clear();
			closeNodes.clear();
		}
		if (toPush.isEmpty() && toClose.isEmpty()) {
			return;
		}
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			return;
		}
		final Thread thread = new Thread(new Runnable() {
			public void run() {
				if (!TodoistSyncGuard.tryEnter()) {
					LogUtils.info("Todoist auto-sync push skipped; full sync in progress");
					return;
				}
				try {
					for (int i = 0; i < toClose.size(); i++) {
						TodoistSyncService.closeLiveNode((NodeModel) toClose.get(i));
					}
					for (int i = 0; i < toPush.size(); i++) {
						final NodeModel node = (NodeModel) toPush.get(i);
						try {
							TodoistSyncService.syncLiveNode(node);
						}
						catch (Exception e) {
							LogUtils.warn("Todoist auto-sync push failed for node " + node.getID(), e);
						}
					}
				}
				finally {
					TodoistSyncGuard.leave();
				}
			}
		}, "TodoistAutoSync-push");
		thread.setDaemon(true);
		thread.start();
	}

	private void runPeriodicSync() {
		if (!TodoistConfig.isAutoSyncEnabled() || syncRunning) {
			return;
		}
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			return;
		}
		syncRunning = true;
		final Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					LogUtils.info("Todoist auto-sync: bidirectional reconcile...");
					TodoistBidirectionalSyncService.syncAll(null);
				}
				catch (Exception e) {
					LogUtils.warn("Todoist auto-sync periodic run failed", e);
				}
				finally {
					syncRunning = false;
				}
			}
		}, "TodoistAutoSync-periodic");
		thread.setDaemon(true);
		thread.start();
	}

	/** Immediate background full reconcile (no dialog); used after settings change. */
	public void requestImmediateFullSync() {
		if (!TodoistConfig.isAutoSyncEnabled()) {
			return;
		}
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				runPeriodicSync();
			}
		});
	}
}
