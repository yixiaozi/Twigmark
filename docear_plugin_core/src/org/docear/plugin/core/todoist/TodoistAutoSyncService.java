package org.docear.plugin.core.todoist;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
 * <p>
 * Cut/paste must not close-then-recreate: node delete only drops a <em>pending close</em> that is
 * cancelled if the same node id (or map|node identity) is pushed again in the same debounce window.
 * Permanent removal is closed later by full-scan cleanup when the reminder is gone from disk.
 */
public final class TodoistAutoSyncService {
	private static final int DEBOUNCE_MS = 1500;
	/** Extra grace so cut→paste across a slightly longer pause still cancels the close. */
	private static final int CLOSE_GRACE_MS = 8000;
	private static final int STARTUP_DELAY_MS = 20000;

	private static TodoistAutoSyncService instance;
	private static final ThreadLocal suppressOutgoing = new ThreadLocal() {
		protected Object initialValue() {
			return Boolean.FALSE;
		}
	};

	private final Set dirtyNodes = new HashSet();
	/** Pending closes keyed by identity ({@code mapName|nodeId}) → {@link PendingClose}. */
	private final Map pendingCloses = new HashMap();
	private Timer debounceTimer;
	private Timer closeGraceTimer;
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
		cancelPendingCloseFor(node);
		scheduleDebounce();
	}

	private synchronized void queueClose(final NodeModel node) {
		if (node == null || isSuppressOutgoing() || !TodoistConfig.isAutoSyncEnabled()) {
			return;
		}
		if (TodoistReminderFactory.getTaskId(node) == null && ReminderExtension.getExtension(node) == null) {
			return;
		}
		final String identity = identityOf(node);
		if (identity == null) {
			return;
		}
		// Snapshot ids before the node disappears from the map tree.
		final String taskId = TodoistReminderFactory.getTaskId(node);
		final String syncKey = node.getMap() != null && node.getMap().getFile() != null
				? TodoistSyncKeys.syncKey(node.getMap().getFile(), node.getID())
				: null;
		pendingCloses.put(identity, new PendingClose(identity, syncKey, taskId, System.currentTimeMillis()));
		scheduleCloseGrace();
	}

	private void cancelPendingCloseFor(final NodeModel node) {
		final String identity = identityOf(node);
		if (identity != null) {
			pendingCloses.remove(identity);
		}
		if (node != null && node.getID() != null) {
			for (Iterator it = pendingCloses.entrySet().iterator(); it.hasNext();) {
				Map.Entry entry = (Map.Entry) it.next();
				PendingClose pending = (PendingClose) entry.getValue();
				if (pending != null && node.getID().equals(pending.nodeIdFromIdentity())) {
					it.remove();
				}
			}
		}
	}

	private static String identityOf(final NodeModel node) {
		if (node == null || node.getMap() == null || node.getMap().getFile() == null) {
			return null;
		}
		return TodoistSyncKeys.identityKey(node.getMap().getFile(), node.getID());
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

	private void scheduleCloseGrace() {
		if (closeGraceTimer != null) {
			closeGraceTimer.restart();
			return;
		}
		closeGraceTimer = new Timer(CLOSE_GRACE_MS, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				closeGraceTimer.stop();
				flushPendingCloses();
			}
		});
		closeGraceTimer.setRepeats(false);
		closeGraceTimer.start();
	}

	private void flushDirtyQueue() {
		final List toPush;
		synchronized (this) {
			toPush = new ArrayList(dirtyNodes);
			dirtyNodes.clear();
			for (int i = 0; i < toPush.size(); i++) {
				cancelPendingCloseFor((NodeModel) toPush.get(i));
			}
		}
		if (toPush.isEmpty()) {
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

	private void flushPendingCloses() {
		final List toClose;
		synchronized (this) {
			toClose = new ArrayList();
			final long now = System.currentTimeMillis();
			for (Iterator it = pendingCloses.values().iterator(); it.hasNext();) {
				PendingClose pending = (PendingClose) it.next();
				if (pending == null) {
					it.remove();
					continue;
				}
				if (now - pending.queuedAtMillis < CLOSE_GRACE_MS - 250) {
					continue;
				}
				toClose.add(pending);
				it.remove();
			}
			if (!pendingCloses.isEmpty() && closeGraceTimer != null) {
				closeGraceTimer.restart();
			}
		}
		if (toClose.isEmpty()) {
			return;
		}
		final String token = TodoistConfig.getApiToken();
		if (token == null || token.trim().length() == 0) {
			return;
		}
		final Thread thread = new Thread(new Runnable() {
			public void run() {
				if (!TodoistSyncGuard.tryEnter()) {
					LogUtils.info("Todoist auto-sync close skipped; full sync in progress");
					return;
				}
				try {
					for (int i = 0; i < toClose.size(); i++) {
						PendingClose pending = (PendingClose) toClose.get(i);
						TodoistSyncService.closeByIdentity(pending.identity, pending.syncKey, pending.taskId);
					}
				}
				finally {
					TodoistSyncGuard.leave();
				}
			}
		}, "TodoistAutoSync-close");
		thread.setDaemon(true);
		thread.start();
	}

	private static final class PendingClose {
		final String identity;
		final String syncKey;
		final String taskId;
		final long queuedAtMillis;

		PendingClose(String identity, String syncKey, String taskId, long queuedAtMillis) {
			this.identity = identity;
			this.syncKey = syncKey;
			this.taskId = taskId;
			this.queuedAtMillis = queuedAtMillis;
		}

		String nodeIdFromIdentity() {
			if (identity == null) {
				return null;
			}
			final int sep = identity.lastIndexOf('|');
			return sep >= 0 ? identity.substring(sep + 1) : null;
		}
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
