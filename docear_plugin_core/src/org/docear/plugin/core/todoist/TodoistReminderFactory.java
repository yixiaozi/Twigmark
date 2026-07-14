package org.docear.plugin.core.todoist;

import java.io.File;

import org.docear.plugin.core.util.NodeUtilities;
import org.freeplane.core.io.UnknownElements;
import org.freeplane.core.util.HtmlUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;

/**
 * Builds {@link TodoistReminderRecord} from a live mind-map node (not disk SAX).
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
		return new TodoistReminderRecord(file, node.getID(), nodeText, reminder.getRemindUserAt(), cycle.period,
				cycle.unit, cycle.recurring);
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
		return new TodoistReminderRecord(node.getMap().getFile(), node.getID(), nodeText, 0L, 1, "DAY", false);
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
		final Object value = NodeUtilities.getAttributeValue(node, TodoistConfig.ATTR_TASK_ID);
		return value == null ? null : String.valueOf(value).trim();
	}

	static void setTaskId(final NodeModel node, final String taskId) {
		if (node == null || taskId == null || taskId.length() == 0) {
			return;
		}
		NodeUtilities.setAttribute(node, TodoistConfig.ATTR_TASK_ID, taskId);
	}

	static String getStoredContentHash(final NodeModel node) {
		final Object value = NodeUtilities.getAttributeValue(node, TodoistConfig.ATTR_CONTENT_HASH);
		return value == null ? null : String.valueOf(value).trim();
	}

	static void setStoredContentHash(final NodeModel node, final String hash) {
		if (node == null) {
			return;
		}
		if (hash == null || hash.length() == 0) {
			NodeUtilities.removeNodeAttribute(node, TodoistConfig.ATTR_CONTENT_HASH);
			return;
		}
		NodeUtilities.setAttribute(node, TodoistConfig.ATTR_CONTENT_HASH, hash);
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
