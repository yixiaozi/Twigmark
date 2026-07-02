package org.freeplane.view.swing.features.time.mindmapmode;

import org.freeplane.core.io.UnknownElements;
import org.freeplane.core.undo.IActor;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.xml.sax.Attributes;

/**
 * DocearReminder-compatible cycle attributes stored on mind map {@code <node>} elements.
 */
public final class ReminderCycleAttributes {
	static final String REMINDERTYPE = "REMINDERTYPE";
	static final String RHOUR = "RHOUR";
	static final String RDAYS = "RDAYS";
	static final String RWEEK = "RWEEK";
	static final String RWEEKS = "RWEEKS";
	static final String RMONTH = "RMONTH";
	static final String RYEAR = "RYEAR";
	static final String EBSTRING = "EBSTRING";

	static final String TYPE_ONETIME = "onetime";
	static final String TYPE_HOUR = "hour";
	static final String TYPE_DAY = "day";
	static final String TYPE_WEEK = "week";
	static final String TYPE_MONTH = "month";
	static final String TYPE_YEAR = "year";
	static final String TYPE_EB = "eb";

	static final class CycleConfig {
		final String remindType;
		final int interval;
		final String weekDays;
		final int ebString;

		CycleConfig(String remindType, int interval, String weekDays, int ebString) {
			this.remindType = remindType == null ? "" : remindType;
			this.interval = interval;
			this.weekDays = weekDays == null ? "" : weekDays;
			this.ebString = ebString;
		}

		boolean isRecurring() {
			return remindType.length() > 0 && !TYPE_ONETIME.equalsIgnoreCase(remindType);
		}

		static CycleConfig oneTime() {
			return new CycleConfig(TYPE_ONETIME, 1, "", 0);
		}
	}

	private ReminderCycleAttributes() {
	}

	static CycleConfig readFromNode(final NodeModel node) {
		if (node == null) {
			return CycleConfig.oneTime();
		}
		final ReminderCycleExtension extension = ReminderCycleExtension.getExtension(node);
		if (extension != null) {
			final CycleConfig config = extension.toConfig();
			if (config.isRecurring()) {
				return config;
			}
		}
		return readLegacyUnknownElements(node);
	}

	private static CycleConfig readLegacyUnknownElements(final NodeModel node) {
		final UnknownElements unknown = (UnknownElements) node.getExtension(UnknownElements.class);
		if (unknown == null) {
			return CycleConfig.oneTime();
		}
		final String remindType = unknown.getUnknownElements().getAttribute(REMINDERTYPE, null);
		if (remindType == null || remindType.length() == 0) {
			return CycleConfig.oneTime();
		}
		final CycleConfig config = new CycleConfig(remindType, parseInt(unknown.getUnknownElements().getAttribute(
				intervalAttributeName(remindType), null), 1), unknown.getUnknownElements().getAttribute(RWEEKS, ""),
				parseInt(unknown.getUnknownElements().getAttribute(EBSTRING, null), 0));
		if (config.isRecurring()) {
			ReminderCycleExtension.getOrCreateExtension(node).applyConfig(config);
		}
		return config;
	}

	static CycleConfig readFromSaxAttributes(final Attributes attributes) {
		if (attributes == null) {
			return CycleConfig.oneTime();
		}
		final String remindType = attributes.getValue(REMINDERTYPE);
		final String type = remindType == null ? "" : remindType;
		return new CycleConfig(type, parseInt(attributes.getValue(intervalAttributeName(type)), 1), attributes
				.getValue(RWEEKS), parseInt(attributes.getValue(EBSTRING), 0));
	}

	static void writeToNode(final NodeModel node, final CycleConfig config) {
		if (node == null || config == null) {
			return;
		}
		final ReminderCycleExtension before = copyExtension(ReminderCycleExtension.getExtension(node));
		final ReminderCycleExtension after = new ReminderCycleExtension();
		after.applyConfig(config);
		final MapController mapController = Controller.getCurrentModeController().getMapController();
		Controller.getCurrentModeController().execute(new IActor() {
			public void act() {
				if (!after.toConfig().isRecurring()) {
					final ReminderCycleExtension existing = ReminderCycleExtension.getExtension(node);
					if (existing != null) {
						node.removeExtension(existing);
					}
				}
				else {
					ReminderCycleExtension.getOrCreateExtension(node).applyConfig(config);
				}
				mapController.nodeChanged(node, ReminderCycleExtension.class, before, after);
			}

			public String getDescription() {
				return "reminder cycle";
			}

			public void undo() {
				if (before == null) {
					final ReminderCycleExtension existing = ReminderCycleExtension.getExtension(node);
					if (existing != null) {
						node.removeExtension(existing);
					}
				}
				else {
					ReminderCycleExtension.getOrCreateExtension(node).applyConfig(before.toConfig());
				}
				mapController.nodeChanged(node, ReminderCycleExtension.class, after, before);
			}
		}, node.getMap());
	}

	static void clearFromNode(final NodeModel node) {
		writeToNode(node, CycleConfig.oneTime());
	}

	public static void writeOneTimeReminder(final NodeModel node) {
		writeToNode(node, CycleConfig.oneTime());
	}

	private static ReminderCycleExtension copyExtension(final ReminderCycleExtension source) {
		if (source == null) {
			return null;
		}
		final ReminderCycleExtension copy = new ReminderCycleExtension();
		copy.applyConfig(source.toConfig());
		return copy;
	}

	private static String intervalAttributeName(final String remindType) {
		if (TYPE_HOUR.equals(remindType)) {
			return RHOUR;
		}
		if (TYPE_DAY.equals(remindType)) {
			return RDAYS;
		}
		if (TYPE_WEEK.equals(remindType)) {
			return RWEEK;
		}
		if (TYPE_MONTH.equals(remindType)) {
			return RMONTH;
		}
		if (TYPE_YEAR.equals(remindType)) {
			return RYEAR;
		}
		return RDAYS;
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
