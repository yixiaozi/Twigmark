package org.freeplane.view.swing.features.time.mindmapmode;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import org.freeplane.core.undo.IActor;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.features.ui.IMapViewManager;

/**
 * Public facade so Docear calendar (other packages) can load / open / reschedule / check-in reminders.
 */
public final class ReminderCalendarBridge {

	private ReminderCalendarBridge() {
	}

	/** Serializable-ish handle for one visible occurrence. */
	public static final class OccurrenceRef {
		public final File file;
		public final String nodeId;
		public final String nodeText;
		public final long storedRemindAt;
		public final long occurrenceAt;
		public final boolean recurring;
		public final int taskTimeMinutes;
		public final int jinji;

		public OccurrenceRef(final File file, final String nodeId, final String nodeText, final long storedRemindAt,
		        final long occurrenceAt, final boolean recurring, final int taskTimeMinutes, final int jinji) {
			this.file = file;
			this.nodeId = nodeId;
			this.nodeText = nodeText;
			this.storedRemindAt = storedRemindAt;
			this.occurrenceAt = occurrenceAt;
			this.recurring = recurring;
			this.taskTimeMinutes = taskTimeMinutes;
			this.jinji = jinji;
		}
	}

	/**
	 * One-pass load for calendar: expand occurrences once for the union range,
	 * then split into view appointments + mini-month day counts.
	 */
	public static final class LoadBundle {
		public final List occurrences;
		public final Map dayCounts;
		public final long elapsedMs;

		LoadBundle(final List occurrences, final Map dayCounts, final long elapsedMs) {
			this.occurrences = occurrences;
			this.dayCounts = dayCounts;
			this.elapsedMs = elapsedMs;
		}
	}

	public static void warmEntriesAsync() {
		ReminderWorkspaceEntryCache.warmAsync();
	}

	/**
	 * Expand once for {@code [expandStart, expandEnd)}; return refs for
	 * {@code [viewStart, viewEnd)} plus day counts for the expand range.
	 */
	public static LoadBundle loadBundle(final long viewStart, final long viewEnd, final long expandStart,
	        final long expandEnd) {
		final long t0 = System.currentTimeMillis();
		final List out = new ArrayList();
		Map counts = new java.util.HashMap();
		try {
			final long from = Math.min(viewStart, expandStart);
			final long to = Math.max(viewEnd, expandEnd);
			final Object[] pack = ReminderWorkspaceEntryCache.expandWithDayCounts(from, to);
			final List occurrences = (List) pack[0];
			counts = (Map) pack[1];
			for (int i = 0; i < occurrences.size(); i++) {
				final ReminderWorkspaceScanHelper.TimelineOccurrence occ = (ReminderWorkspaceScanHelper.TimelineOccurrence) occurrences
				        .get(i);
				if (occ.occurrenceAt < viewStart || occ.occurrenceAt >= viewEnd) {
					continue;
				}
				final ReminderCalendarEntry entry = occ.entry;
				out.add(new OccurrenceRef(entry.file, entry.nodeId, entry.nodeText, entry.remindAt, occ.occurrenceAt,
				        entry.recurring, entry.taskTime, entry.jinji));
			}
		}
		catch (Exception e) {
			LogUtils.warn("ReminderCalendarBridge.loadBundle failed", e);
		}
		return new LoadBundle(out, counts, System.currentTimeMillis() - t0);
	}

	/**
	 * Scan workspace maps and expand occurrences that fall in {@code [rangeStart, rangeEnd)}.
	 */
	public static List loadOccurrences(final long rangeStart, final long rangeEnd) {
		return loadBundle(rangeStart, rangeEnd, rangeStart, rangeEnd).occurrences;
	}

	/** Day-start millis → occurrence count in range (uses same entry cache). */
	public static Map loadDayCounts(final long rangeStart, final long rangeEnd) {
		try {
			final Object[] pack = ReminderWorkspaceEntryCache.expandWithDayCounts(rangeStart, rangeEnd);
			return (Map) pack[1];
		}
		catch (Exception e) {
			LogUtils.warn("ReminderCalendarBridge.loadDayCounts failed", e);
			return new java.util.HashMap();
		}
	}

	public static void invalidateReminderCache() {
		ReminderWorkspaceEntryCache.invalidateAll();
	}

	public static void invalidateReminderCache(final File file) {
		ReminderWorkspaceEntryCache.invalidateFile(file);
	}

	/**
	 * Create a child node under {@code parent} (or selected node) with a one-time reminder
	 * at {@code startMs}; optional duration stored as task time minutes.
	 */
	public static boolean createReminderTask(final NodeModel parent, final String title, final long startMs,
	        final int durationMinutes) {
		if (parent == null || startMs <= 0L) {
			return false;
		}
		try {
			final ModeController modeController = Controller.getCurrentModeController();
			final org.freeplane.features.map.mindmapmode.MMapController mapController = (org.freeplane.features.map.mindmapmode.MMapController) modeController
			        .getMapController();
			final NodeModel child = mapController.addNewNode(parent, parent.getChildCount(), parent.isNewChildLeft());
			if (child == null) {
				return false;
			}
			final String text = title == null || title.trim().length() == 0 ? "新安排" : title.trim();
			((org.freeplane.features.text.mindmapmode.MTextController) org.freeplane.features.text.TextController
			        .getController()).setNodeText(child, text);
			final ReminderHook reminderHook = (ReminderHook) modeController.getExtension(ReminderHook.class);
			if (reminderHook == null) {
				return false;
			}
			final ReminderExtension reminderExtension = new ReminderExtension(child);
			reminderExtension.setRemindUserAt(startMs);
			reminderHook.undoableActivateHook(child, reminderExtension);
			if (durationMinutes > 0) {
				ReminderTaskAttributes.writeFull(child, durationMinutes, 0, 0);
			}
			mapController.setSaved(child.getMap(), false);
			final File mapFile = child.getMap().getFile();
			if (mapFile != null) {
				invalidateReminderCache(mapFile);
			}
			else {
				invalidateReminderCache();
			}
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("ReminderCalendarBridge.createReminderTask failed", e);
			return false;
		}
	}

	public static void openNode(final File file, final String nodeId) {
		if (file == null || nodeId == null) {
			return;
		}
		ReminderTabNavigation.openEntry(new ReminderCalendarEntry(file, nodeId, "", 0L, false, null, 0, 0, 0));
	}

	/**
	 * Move stored REMINDUSERAT to {@code newRemindAt} (opens map if needed, marks dirty).
	 */
	public static boolean updateRemindAt(final File file, final String nodeId, final long newRemindAt) {
		if (file == null || nodeId == null || newRemindAt <= 0L) {
			return false;
		}
		try {
			final ModeController modeController = Controller.getCurrentModeController();
			final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			final URL url = file.toURI().toURL();
			MapModel map = findOpenMap(mapViewManager, file);
			if (map == null) {
				if (!mapViewManager.tryToChangeToMapView(url)) {
					modeController.getMapController().newMap(url);
				}
				map = findOpenMap(mapViewManager, file);
			}
			if (map == null) {
				return false;
			}
			final NodeModel node = map.getNodeForID(nodeId);
			if (node == null) {
				return false;
			}
			final ReminderHook reminderHook = (ReminderHook) modeController.getExtension(ReminderHook.class);
			final ReminderExtension reminder = ReminderExtension.getExtension(node);
			if (reminderHook == null || reminder == null) {
				return false;
			}
			final long oldTime = reminder.getRemindUserAt();
			if (oldTime == newRemindAt) {
				return true;
			}
			updateReminderTime(modeController, reminderHook, node, reminder, oldTime, newRemindAt);
			modeController.getMapController().setSaved(map, false);
			invalidateReminderCache(file);
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("ReminderCalendarBridge.updateRemindAt failed", e);
			return false;
		}
	}

	/** Recurring check-in dialog (advance cycle optional); one-time → complete dialog. */
	public static boolean checkIn(final File file, final String nodeId, final long occurrenceAt) {
		if (file == null || nodeId == null) {
			return false;
		}
		try {
			final NodeModel node = resolveNode(file, nodeId);
			if (node == null) {
				JOptionPane.showMessageDialog(Controller.getCurrentController().getViewController().getFrame(),
				        "未找到对应节点，请先打开相关导图。", "周期任务打卡", JOptionPane.WARNING_MESSAGE);
				return false;
			}
			if (!RecurringReminderCheckInService.isRecurringReminderNode(node)) {
				return completeOneTime(file, nodeId);
			}
			final ReminderCycleAttributes.CycleConfig cycle = ReminderCycleAttributes.readFromNode(node);
			final ReminderExtension reminder = ReminderExtension.getExtension(node);
			final long remindAt = reminder == null ? occurrenceAt : reminder.getRemindUserAt();
			final ReminderTaskAttributes.TaskConfig task = ReminderTaskAttributes.readFromNode(node);
			final RecurringReminderEntry recurring = new RecurringReminderEntry(file, nodeId, node.getText(), remindAt,
			        cycle, task.taskTime, task.taskLevel, task.jinji);
			return RecurringReminderCheckInService.openCheckInForEntry(recurring, occurrenceAt);
		}
		catch (Exception e) {
			LogUtils.warn("ReminderCalendarBridge.checkIn failed", e);
			return false;
		}
	}

	/** One-time: confirm and remove reminder hook. */
	public static boolean completeOneTime(final File file, final String nodeId) {
		if (file == null || nodeId == null) {
			return false;
		}
		try {
			final NodeModel node = resolveNode(file, nodeId);
			if (node == null) {
				return false;
			}
			final int choice = JOptionPane.showConfirmDialog(
			        Controller.getCurrentController().getViewController().getFrame(),
			        "将移除此一次性提醒（标记完成）。是否继续？", "完成提醒", JOptionPane.OK_CANCEL_OPTION,
			        JOptionPane.QUESTION_MESSAGE);
			if (choice != JOptionPane.OK_OPTION) {
				return false;
			}
			final ModeController modeController = Controller.getCurrentModeController();
			final ReminderHook reminderHook = (ReminderHook) modeController.getExtension(ReminderHook.class);
			if (reminderHook == null || ReminderExtension.getExtension(node) == null) {
				return false;
			}
			reminderHook.undoableToggleHook(node);
			modeController.getMapController().setSaved(node.getMap(), false);
			invalidateReminderCache(file);
			return true;
		}
		catch (Exception e) {
			LogUtils.warn("ReminderCalendarBridge.completeOneTime failed", e);
			return false;
		}
	}

	private static void updateReminderTime(final ModeController modeController, final ReminderHook reminderHook,
	        final NodeModel node, final ReminderExtension reminder, final long oldTime, final long newTime) {
		final MapController mapController = modeController.getMapController();
		modeController.execute(new IActor() {
			public void act() {
				reminder.deactivateTimer();
				reminder.setRemindUserAt(newTime);
				reminderHook.rescheduleReminder(reminder);
				mapController.nodeChanged(node, ReminderExtension.class, Long.valueOf(oldTime), Long.valueOf(newTime));
			}

			public String getDescription() {
				return "calendar reschedule reminder";
			}

			public void undo() {
				reminder.deactivateTimer();
				reminder.setRemindUserAt(oldTime);
				reminderHook.rescheduleReminder(reminder);
				mapController.nodeChanged(node, ReminderExtension.class, Long.valueOf(newTime), Long.valueOf(oldTime));
			}
		}, node.getMap());
	}

	private static NodeModel resolveNode(final File file, final String nodeId) {
		try {
			final IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
			final URL url = file.toURI().toURL();
			MapModel map = findOpenMap(mapViewManager, file);
			if (map == null) {
				if (!mapViewManager.tryToChangeToMapView(url)) {
					Controller.getCurrentModeController().getMapController().newMap(url);
				}
				map = findOpenMap(mapViewManager, file);
			}
			return map == null ? null : map.getNodeForID(nodeId);
		}
		catch (Exception e) {
			LogUtils.warn(e);
			return null;
		}
	}

	private static MapModel findOpenMap(final IMapViewManager mapViewManager, final File file) {
		final Map maps = mapViewManager.getMaps(MModeController.MODENAME);
		for (final Object mapObj : maps.values()) {
			final MapModel map = (MapModel) mapObj;
			if (isSameFile(map.getFile(), file)) {
				return map;
			}
		}
		return null;
	}

	private static boolean isSameFile(final File file1, final File file2) {
		if (file1 == null || file2 == null) {
			return file1 == file2;
		}
		try {
			return file1.getCanonicalPath().equals(file2.getCanonicalPath());
		}
		catch (Exception e) {
			return file1.getAbsolutePath().equals(file2.getAbsolutePath());
		}
	}
}
