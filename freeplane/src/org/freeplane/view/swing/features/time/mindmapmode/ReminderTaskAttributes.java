package org.freeplane.view.swing.features.time.mindmapmode;

import org.freeplane.core.io.UnknownElements;
import org.freeplane.core.undo.IActor;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.xml.sax.Attributes;

/**
 * DocearReminder-compatible task attributes stored on mind map {@code <node>} elements.
 */
public final class ReminderTaskAttributes {
	static final String TASKTIME = "TASKTIME";
	static final String TASKLEVEL = "TASKLEVEL";
	static final String JINJI = "JINJI";

	static final class TaskConfig {
		final int taskTime;
		final int taskLevel;
		final int jinji;

		TaskConfig(final int taskTime, final int taskLevel, final int jinji) {
			this.taskTime = taskTime;
			this.taskLevel = taskLevel;
			this.jinji = jinji;
		}

		boolean isEmpty() {
			return taskTime == 0 && taskLevel == 0 && jinji == 0;
		}

		static TaskConfig empty() {
			return new TaskConfig(0, 0, 0);
		}
	}

	private ReminderTaskAttributes() {
	}

	static TaskConfig readFromNode(final NodeModel node) {
		if (node == null) {
			return TaskConfig.empty();
		}
		final ReminderTaskExtension extension = ReminderTaskExtension.getExtension(node);
		if (extension != null) {
			return extension.toConfig();
		}
		return readLegacyUnknownElements(node);
	}

	private static TaskConfig readLegacyUnknownElements(final NodeModel node) {
		final UnknownElements unknown = (UnknownElements) node.getExtension(UnknownElements.class);
		if (unknown == null) {
			return TaskConfig.empty();
		}
		final TaskConfig config = new TaskConfig(parseInt(unknown.getUnknownElements().getAttribute(TASKTIME, null), 0),
				parseInt(unknown.getUnknownElements().getAttribute(TASKLEVEL, null), 0), parseInt(
						unknown.getUnknownElements().getAttribute(JINJI, null), 0));
		if (!config.isEmpty()) {
			ReminderTaskExtension.getOrCreateExtension(node).applyConfig(config);
		}
		return config;
	}

	static TaskConfig readFromSaxAttributes(final Attributes attributes) {
		if (attributes == null) {
			return TaskConfig.empty();
		}
		return new TaskConfig(parseInt(attributes.getValue(TASKTIME), 0), parseInt(attributes.getValue(TASKLEVEL), 0),
				parseInt(attributes.getValue(JINJI), 0));
	}

	static void writeToNode(final NodeModel node, final TaskConfig config) {
		if (node == null || config == null) {
			return;
		}
		final ReminderTaskExtension before = copyExtension(ReminderTaskExtension.getExtension(node));
		final ReminderTaskExtension after = new ReminderTaskExtension();
		after.applyConfig(config);
		final MapController mapController = Controller.getCurrentModeController().getMapController();
		Controller.getCurrentModeController().execute(new IActor() {
			public void act() {
				if (config.isEmpty()) {
					final ReminderTaskExtension existing = ReminderTaskExtension.getExtension(node);
					if (existing != null) {
						node.removeExtension(existing);
					}
				}
				else {
					ReminderTaskExtension.getOrCreateExtension(node).applyConfig(config);
				}
				mapController.nodeChanged(node, ReminderTaskExtension.class, before, after);
			}

			public String getDescription() {
				return "reminder task metadata";
			}

			public void undo() {
				if (before == null) {
					final ReminderTaskExtension existing = ReminderTaskExtension.getExtension(node);
					if (existing != null) {
						node.removeExtension(existing);
					}
				}
				else {
					ReminderTaskExtension.getOrCreateExtension(node).applyConfig(before.toConfig());
				}
				mapController.nodeChanged(node, ReminderTaskExtension.class, after, before);
			}
		}, node.getMap());
	}

	static void clearFromNode(final NodeModel node) {
		writeToNode(node, TaskConfig.empty());
	}

	public static void writeEmptyTask(final NodeModel node) {
		writeToNode(node, TaskConfig.empty());
	}

	private static ReminderTaskExtension copyExtension(final ReminderTaskExtension source) {
		if (source == null) {
			return null;
		}
		final ReminderTaskExtension copy = new ReminderTaskExtension();
		copy.applyConfig(source.toConfig());
		return copy;
	}

	private static int parseInt(final String value, final int defaultValue) {
		if (value == null || value.trim().length() == 0) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}
}
