package org.docear.plugin.core.todoist;

import java.awt.Color;
import java.io.File;

import org.docear.plugin.core.util.NodeUtilities;
import org.freeplane.core.io.UnknownElements;
import org.freeplane.core.util.ColorUtils;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.MindIcon;
import org.freeplane.features.icon.mindmapmode.MIconController;
import org.freeplane.features.map.HistoryInformationModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.nodestyle.NodeStyleController;
import org.freeplane.features.nodestyle.NodeStyleModel;
import org.freeplane.features.nodestyle.mindmapmode.MNodeStyleController;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderCycleAttributes;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderTaskAttributes;

/**
 * Builds {@link TodoistReminderRecord} from a live mind-map node (not disk SAX).
 * Todoist linkage is stored on {@link TodoistNodeMeta} (hidden XML), not visible attributes.
 */
final class TodoistReminderFactory {
	/** Same yellow as the sample node in test.mm. A reminder for the user, not the identity. */
	static final String MARK_BACKGROUND = "#ffff00";
	/** This sync created the Todoist task. */
	static final String ORIGIN_DOCEAR = "docear";
	/** The task already existed in Todoist. Never relabel it as a mind-map task. */
	static final String ORIGIN_TODOIST = "todoist";
	/** Previously used as the visible mark. Stripped so it is not left on linked nodes. */
	private static final String RETIRED_ICON = "bookmark";

	private TodoistReminderFactory() {
	}

	/**
	 * Yellow background for the user, plus hidden identity attributes.
	 * Does not treat the color as the link.
	 *
	 * @return true when the node appearance changed
	 */
	static boolean markLinkedNode(final NodeModel node, final String projectId, final String sectionId) {
		if (node == null) {
			return false;
		}
		rememberIdentity(node, projectId, sectionId);
		final boolean removedIcon = removeRetiredIcon(node);
		final boolean painted = ensureYellowBackground(node);
		return removedIcon || painted;
	}

	static boolean markLinkedNode(final NodeModel node) {
		return markLinkedNode(node, null, null);
	}

	private static void rememberIdentity(final NodeModel node, final String projectId, final String sectionId) {
		final TodoistNodeMeta meta = TodoistNodeMeta.getOrCreate(node);
		boolean changed = !meta.isLinked();
		meta.setLinked(true);
		if (projectId != null && projectId.trim().length() > 0 && !projectId.trim().equals(meta.getProjectId())) {
			meta.setProjectId(projectId);
			changed = true;
		}
		if (sectionId != null && sectionId.trim().length() > 0 && !sectionId.trim().equals(meta.getSectionId())) {
			meta.setSectionId(sectionId);
			changed = true;
		}
		if (rememberOrigin(meta, projectId)) {
			changed = true;
		}
		if (changed && node.getMap() != null) {
			node.getMap().setSaved(false);
		}
	}

	/**
	 * A task outside the Docear project is an original Todoist task. That mark sticks, so a later
	 * move into the Docear project does not turn it into a mind-map task.
	 */
	private static boolean rememberOrigin(final TodoistNodeMeta meta, final String projectId) {
		if (projectId == null || projectId.trim().length() == 0) {
			return false;
		}
		final String docearProjectId = TodoistConfig.getProjectId();
		final String next;
		if (docearProjectId != null && docearProjectId.length() > 0 && !docearProjectId.equals(projectId.trim())) {
			next = ORIGIN_TODOIST;
		}
		else if (ORIGIN_TODOIST.equals(meta.getOrigin())) {
			return false;
		}
		else {
			next = ORIGIN_DOCEAR;
		}
		if (next.equals(meta.getOrigin())) {
			return false;
		}
		meta.setOrigin(next);
		return true;
	}

	static void setOrigin(final NodeModel node, final String origin) {
		if (node == null || origin == null || origin.length() == 0) {
			return;
		}
		final TodoistNodeMeta meta = TodoistNodeMeta.getOrCreate(node);
		if (origin.equals(meta.getOrigin())) {
			return;
		}
		meta.setOrigin(origin);
		if (node.getMap() != null) {
			node.getMap().setSaved(false);
		}
	}

	static boolean isTodoistOrigin(final NodeModel node) {
		final TodoistNodeMeta meta = node == null ? null : TodoistNodeMeta.get(node);
		return meta != null && ORIGIN_TODOIST.equals(meta.getOrigin());
	}

	private static boolean removeRetiredIcon(final NodeModel node) {
		final java.util.List icons = node.getIcons();
		if (icons == null || icons.isEmpty()) {
			return false;
		}
		boolean removed = false;
		try {
			final MIconController iconsController = (MIconController) IconController.getController();
			for (int i = icons.size() - 1; i >= 0; i--) {
				final Object icon = icons.get(i);
				if (icon instanceof MindIcon && RETIRED_ICON.equals(((MindIcon) icon).getName())) {
					iconsController.removeIcon(node, i);
					removed = true;
				}
			}
		}
		catch (Exception e) {
			// Headless: leave the icon; the next UI sync removes it.
		}
		return removed;
	}

	private static boolean ensureYellowBackground(final NodeModel node) {
		final Color desired = ColorUtils.stringToColor(MARK_BACKGROUND);
		final Color current = NodeStyleModel.getBackgroundColor(node);
		if (desired.equals(current)) {
			return false;
		}
		try {
			((MNodeStyleController) NodeStyleController.getController()).setBackgroundColor(node, desired);
		}
		catch (Exception e) {
			NodeStyleModel.setBackgroundColor(node, desired);
			if (node.getMap() != null) {
				node.getMap().setSaved(false);
			}
		}
		return true;
	}

	static TodoistReminderRecord fromNode(final NodeModel node) {
		if (node == null || node.getMap() == null) {
			return null;
		}
		final File file = node.getMap().getFile();
		if (file == null) {
			return null;
		}
		final ReminderExtension reminder = ReminderExtension.getExtension(node);
		if (reminder == null || reminder.getRemindUserAt() <= 0) {
			return null;
		}
		final String nodeText = plainText(node);
		if (nodeText.length() == 0 || "bin".equalsIgnoreCase(nodeText)) {
			return null;
		}
		final CycleInfo cycle = readCycle(node, reminder);
		final int durationMinutes = ReminderTaskAttributes.readTaskTimeFromNode(node);
		final int jinji = ReminderTaskAttributes.readJinjiFromNode(node);
		return new TodoistReminderRecord(file, node.getID(), nodeText, reminder.getRemindUserAt(), cycle.period,
				cycle.unit, cycle.recurring, durationMinutes, jinji, cycle.remindType, cycle.weekDays);
	}

	/** Import-map task node that has a Todoist id but may not have a reminder yet. */
	static TodoistReminderRecord fromImportMapNodeWithoutReminder(final NodeModel node) {
		if (node == null || node.getMap() == null || node.getMap().getFile() == null) {
			return null;
		}
		if (getTaskId(node) == null) {
			return null;
		}
		final String nodeText = plainText(node);
		if (nodeText.length() == 0) {
			return null;
		}
		return new TodoistReminderRecord(node.getMap().getFile(), node.getID(), nodeText, 0L, 1, "DAY", false,
				ReminderTaskAttributes.readTaskTimeFromNode(node), ReminderTaskAttributes.readJinjiFromNode(node));
	}

	static String contentHash(final TodoistReminderRecord record) {
		final String sectionName = TodoistApiClient.sectionNameForFile(record.file);
		return TodoistSyncService.contentHash(record, sectionName);
	}

	static String plainText(final NodeModel node) {
		final String text = node.getText();
		if (text == null) {
			return "";
		}
		return HtmlUtils.htmlToPlain(text).trim();
	}

	static String getTaskId(final NodeModel node) {
		migrateLegacyAttributesIfNeeded(node);
		final TodoistNodeMeta meta = TodoistNodeMeta.get(node);
		if (meta != null && meta.getTaskId() != null && meta.getTaskId().length() > 0) {
			return meta.getTaskId();
		}
		return null;
	}

	static void setTaskId(final NodeModel node, final String taskId) {
		if (node == null) {
			return;
		}
		migrateLegacyAttributesIfNeeded(node);
		if (taskId == null || taskId.trim().length() == 0) {
			final TodoistNodeMeta meta = TodoistNodeMeta.get(node);
			if (meta != null) {
				meta.setTaskId(null);
				if (meta.isEmpty()) {
					node.removeExtension(TodoistNodeMeta.class);
				}
			}
			TodoistNodeMetaIo.stripLegacyVisibleAttributes(node);
			return;
		}
		TodoistNodeMeta.getOrCreate(node).setTaskId(taskId.trim());
		TodoistNodeMetaIo.stripLegacyVisibleAttributes(node);
	}

	static String getStoredContentHash(final NodeModel node) {
		migrateLegacyAttributesIfNeeded(node);
		final TodoistNodeMeta meta = TodoistNodeMeta.get(node);
		if (meta != null && meta.getContentHash() != null && meta.getContentHash().length() > 0) {
			return meta.getContentHash();
		}
		return null;
	}

	static boolean isUserPlaced(final NodeModel node) {
		migrateLegacyAttributesIfNeeded(node);
		final TodoistNodeMeta meta = TodoistNodeMeta.get(node);
		return meta != null && meta.isUserPlaced();
	}

	static void setUserPlaced(final NodeModel node, final boolean placed) {
		if (node == null) {
			return;
		}
		migrateLegacyAttributesIfNeeded(node);
		TodoistNodeMeta.getOrCreate(node).setUserPlaced(placed);
	}

	static long localModifiedMillis(final NodeModel node) {
		if (node == null || node.getHistoryInformation() == null
				|| node.getHistoryInformation().getLastModifiedAt() == null) {
			return 0L;
		}
		return node.getHistoryInformation().getLastModifiedAt().getTime();
	}

	static long syncedLocalMillis(final NodeModel node) {
		final TodoistNodeMeta meta = node == null ? null : TodoistNodeMeta.get(node);
		return meta == null ? 0L : meta.getSyncedLocalMillis();
	}

	static long syncedRemoteMillis(final NodeModel node) {
		final TodoistNodeMeta meta = node == null ? null : TodoistNodeMeta.get(node);
		return meta == null ? 0L : meta.getSyncedRemoteMillis();
	}

	static void copyClock(final NodeModel node, final TodoistReminderRecord record) {
		if (node == null || record == null) {
			return;
		}
		record.localModified = localModifiedMillis(node);
		record.syncedLocal = syncedLocalMillis(node);
		record.syncedRemote = syncedRemoteMillis(node);
		record.userPlaced = isUserPlaced(node);
		if (record.linkedTaskId == null || record.linkedTaskId.length() == 0) {
			record.linkedTaskId = getTaskId(node);
		}
	}

	/**
	 * Remember both clocks after a sync. One {@code nodeChanged} bumps {@code MODIFIED}; the stored
	 * local time is the value after that bump, so the write itself is not a new edit next time.
	 */
	static void stampWatermarks(final NodeModel node, final long remoteUpdatedMillis) {
		if (node == null || remoteUpdatedMillis <= 0) {
			return;
		}
		setSyncWatermarks(node, localModifiedMillis(node), remoteUpdatedMillis);
		try {
			Controller.getCurrentModeController().getMapController().nodeChanged(node, HistoryInformationModel.class,
					null, null);
		}
		catch (Exception e) {
			// Headless or mid-load: keep the clocks already stored.
		}
		setSyncWatermarks(node, localModifiedMillis(node), remoteUpdatedMillis);
	}

	private static void setSyncWatermarks(final NodeModel node, final long localMillis, final long remoteMillis) {
		final TodoistNodeMeta meta = TodoistNodeMeta.getOrCreate(node);
		meta.setSyncedLocalMillis(localMillis);
		meta.setSyncedRemoteMillis(remoteMillis);
	}

	static void setStoredContentHash(final NodeModel node, final String hash) {
		if (node == null) {
			return;
		}
		migrateLegacyAttributesIfNeeded(node);
		if (hash == null || hash.trim().length() == 0) {
			final TodoistNodeMeta meta = TodoistNodeMeta.get(node);
			if (meta != null) {
				meta.setContentHash(null);
				if (meta.isEmpty()) {
					node.removeExtension(TodoistNodeMeta.class);
				}
			}
			TodoistNodeMetaIo.stripLegacyVisibleAttributes(node);
			return;
		}
		TodoistNodeMeta.getOrCreate(node).setContentHash(hash.trim());
		TodoistNodeMetaIo.stripLegacyVisibleAttributes(node);
	}

	/**
	 * One-time migration: copy old visible Freeplane attributes into {@link TodoistNodeMeta},
	 * then remove them from the attribute table so they no longer show on canvas.
	 */
	private static void migrateLegacyAttributesIfNeeded(final NodeModel node) {
		if (node == null) {
			return;
		}
		final Object legacyTaskId = NodeUtilities.getAttributeValue(node, TodoistConfig.ATTR_TASK_ID);
		final Object legacyHash = NodeUtilities.getAttributeValue(node, TodoistConfig.ATTR_CONTENT_HASH);
		if (legacyTaskId == null && legacyHash == null) {
			return;
		}
		final TodoistNodeMeta meta = TodoistNodeMeta.getOrCreate(node);
		if ((meta.getTaskId() == null || meta.getTaskId().length() == 0) && legacyTaskId != null) {
			meta.setTaskId(String.valueOf(legacyTaskId).trim());
		}
		if ((meta.getContentHash() == null || meta.getContentHash().length() == 0) && legacyHash != null) {
			meta.setContentHash(String.valueOf(legacyHash).trim());
		}
		TodoistNodeMetaIo.stripLegacyVisibleAttributes(node);
	}

	private static CycleInfo readCycle(final NodeModel node, final ReminderExtension reminder) {
		// Authoritative DocearReminder cycle attrs (REMINDERTYPE / RDAYS / RWEEKS / …).
		// ReminderExtension PERIOD/UNIT is often stuck at 1/DAY from the editor UI.
		final String remindType = ReminderCycleAttributes.readRemindTypeFromNode(node);
		final int interval = ReminderCycleAttributes.readIntervalFromNode(node);
		final String weekDays = ReminderCycleAttributes.readWeekDaysFromNode(node);
		final TodoistCycleMapper.Cycle cycle = TodoistCycleMapper.fromPublicNodeReaders(remindType, interval, weekDays);
		if (cycle.recurring) {
			return new CycleInfo(true, cycle.interval, cycle.periodUnit(), cycle.remindType, cycle.weekDays);
		}
		// Legacy fallback: UnknownElements or hook period > 1
		String legacyType = null;
		final UnknownElements unknown = (UnknownElements) node.getExtension(UnknownElements.class);
		if (unknown != null && unknown.getUnknownElements() != null) {
			legacyType = unknown.getUnknownElements().getAttribute("REMINDERTYPE", null);
		}
		boolean recurring = legacyType != null && legacyType.length() > 0 && !"onetime".equalsIgnoreCase(legacyType);
		int period = reminder.getPeriod() <= 0 ? 1 : reminder.getPeriod();
		String unit = reminder.getPeriodUnitAsString();
		if (unit == null || unit.length() == 0) {
			unit = mapRemindTypeToUnit(legacyType);
		}
		if (unit == null || unit.length() == 0) {
			unit = "DAY";
		}
		if (!recurring && legacyType == null && period > 1) {
			recurring = true;
		}
		final String type = recurring ? (legacyType != null ? legacyType.toLowerCase() : unitToType(unit)) : "onetime";
		return new CycleInfo(recurring, period, unit.toUpperCase(), type, "");
	}

	private static String unitToType(String unit) {
		if (unit == null) {
			return "day";
		}
		final String u = unit.toUpperCase();
		if ("HOUR".equals(u)) {
			return "hour";
		}
		if ("WEEK".equals(u)) {
			return "week";
		}
		if ("MONTH".equals(u)) {
			return "month";
		}
		if ("YEAR".equals(u)) {
			return "year";
		}
		return "day";
	}

	private static String mapRemindTypeToUnit(final String remindType) {
		if (remindType == null) {
			return "DAY";
		}
		final String t = remindType.toLowerCase();
		if ("hour".equals(t)) {
			return "HOUR";
		}
		if ("day".equals(t)) {
			return "DAY";
		}
		if ("week".equals(t)) {
			return "WEEK";
		}
		if ("month".equals(t)) {
			return "MONTH";
		}
		if ("year".equals(t)) {
			return "YEAR";
		}
		return "DAY";
	}

	private static final class CycleInfo {
		final boolean recurring;
		final int period;
		final String unit;
		final String remindType;
		final String weekDays;

		CycleInfo(boolean recurring, int period, String unit, String remindType, String weekDays) {
			this.recurring = recurring;
			this.period = period;
			this.unit = unit;
			this.remindType = remindType;
			this.weekDays = weekDays;
		}
	}
}
