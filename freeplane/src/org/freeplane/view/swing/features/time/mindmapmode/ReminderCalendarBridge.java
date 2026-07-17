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
	 * Scan workspace maps and expand occurrences that fall in {@code [rangeStart, rangeEnd)}.
	 */
	public static List loadOccurrences(final long rangeStart, final long rangeEnd) {
		final List out = new ArrayList();
		try {
			final List entries = ReminderWorkspaceEntryCache.getAllEntries();
			final List occurrences = ReminderWorkspaceScanHelper.buildTimelineOccurrences(entries, rangeStart, rangeEnd);
			for (int i = 0; i < occurrences.size(); i++) {
				final ReminderWorkspaceScanHelper.TimelineOccurrence occ = (ReminderWorkspaceScanHelper.TimelineOccurrence) occurrences
				        .get(i);
				final ReminderCalendarEntry entry = occ.entry;
				out.add(new OccurrenceRef(entry.file, entry.nodeId, entry.nodeText, entry.remindAt, occ.occurrenceAt,
				        entry.recurring, entry.taskTime, entry.jinji));
			}
		}
		catch (Exception e) {
			LogUtils.warn("ReminderCalendarBridge.loadOccurrences failed", e);
		}
		return out;
	}

	/** Day-start millis → occurrence count in range (uses same entry cache). */
	public static Map loadDayCounts(final long rangeStart, final long rangeEnd) {
		try {
			return ReminderWorkspaceEntryCache.buildDayCounts(ReminderWorkspaceEntryCache.getAllEntries(), rangeStart,
			        rangeEnd);
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
