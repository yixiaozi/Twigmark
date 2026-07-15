package org.docear.plugin.core.todoist;

import java.io.File;

import org.docear.plugin.core.util.NodeUtilities;
import org.freeplane.core.io.UnknownElements;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderTaskAttributes;

/**
 * Builds {@link TodoistReminderRecord} from a live mind-map node (not disk SAX).
 * Todoist linkage is stored on {@link TodoistNodeMeta} (hidden XML), not visible attributes.
 */
final class TodoistReminderFactory {
	private TodoistReminderFactory() {
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
				cycle.unit, cycle.recurring, durationMinutes, jinji);
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
		String remindType = null;
		final UnknownElements unknown = (UnknownElements) node.getExtension(UnknownElements.class);
		if (unknown != null && unknown.getUnknownElements() != null) {
			remindType = unknown.getUnknownElements().getAttribute("REMINDERTYPE", null);
		}
		boolean recurring = remindType != null && remindType.length() > 0
				&& !"onetime".equalsIgnoreCase(remindType);
		int period = reminder.getPeriod() <= 0 ? 1 : reminder.getPeriod();
		String unit = reminder.getPeriodUnitAsString();
		if (unit == null || unit.length() == 0) {
			unit = mapRemindTypeToUnit(remindType);
		}
		if (unit == null || unit.length() == 0) {
			unit = "DAY";
		}
		if (!recurring && remindType == null && period > 1) {
			recurring = true;
		}
		return new CycleInfo(recurring, period, unit.toUpperCase());
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

		CycleInfo(boolean recurring, int period, String unit) {
			this.recurring = recurring;
			this.period = period;
			this.unit = unit;
		}
	}
}
