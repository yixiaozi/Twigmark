package org.freeplane.view.swing.features.reports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.freeplane.core.util.TextUtils;

/**
 * Catalog designed as a daily operating loop for Docear:
 * focus → plan vs actual → attention → load → debt → deep work → review.
 *
 * Each report answers one decision question with one primary data source.
 */
public final class ReportCatalog {
	/** Opens the usage-stats activity panel in the map viewport. */
	public static final String ID_ACTIVITY = "activity_usage";
	/** Opens the MCP status / audit dialog (MCP plugin). */
	public static final String ID_MCP_AUDIT = "mcp_audit";
	public static final String ID_TODAY = "today_focus";
	public static final String ID_PLAN_VS_ACTUAL = "plan_vs_actual";
	public static final String ID_USE_TIME = "use_time";
	public static final String ID_TIME_BLOCK = "time_block";
	public static final String ID_TREND = "trend";
	public static final String ID_MAP_LOAD = "map_load";
	public static final String ID_OVERDUE = "overdue";
	public static final String ID_URGENT = "urgent";
	public static final String ID_TODOS = "todos";
	public static final String ID_FLAGS = "flags";
	public static final String ID_POMODORO = "pomodoro";
	public static final String ID_DURATION = "duration_buckets";
	public static final String ID_TARGET = "target";
	public static final String ID_MIND_PULSE = "mind_pulse";
	public static final String ID_KEYBOARD = "keyboard";
	public static final String ID_RECURRING = "recurring";
	public static final String ID_PUBLISHED = "published";

	/* Legacy aliases kept so old calls don't NPE */
	public static final String ID_TODAY_DASHBOARD = ID_TODAY;
	public static final String ID_WORK_HOURS = ID_TIME_BLOCK;
	public static final String ID_SCHEDULE = ID_TODAY;
	public static final String ID_MINDMAP_ANALYSIS = ID_MIND_PULSE;
	public static final String ID_DURATION_BUCKETS = ID_DURATION;
	public static final String ID_RECENT = ID_MIND_PULSE;

	private static List ALL;

	private ReportCatalog() {
	}

	public static synchronized List all() {
		if (ALL == null) {
			ALL = buildAll();
		}
		return ALL;
	}

	public static ReportDefinition byId(final String id) {
		if (id == null) {
			return null;
		}
		final List list = all();
		for (int i = 0; i < list.size(); i++) {
			final ReportDefinition def = (ReportDefinition) list.get(i);
			if (def.id.equals(id)) {
				return def;
			}
		}
		return null;
	}

	private static List buildAll() {
		final List list = new ArrayList();

		list.add(def(ID_ACTIVITY, "ReportCatalog.activity", "wizard", false));
		list.add(def(ID_MCP_AUDIT, "ReportCatalog.mcp_audit", "wizard", false));
		list.add(def(ID_TODAY, "ReportCatalog.today", "idea", true));
		list.add(def(ID_PLAN_VS_ACTUAL, "ReportCatalog.plan_vs_actual", "full-7", true));
		list.add(def(ID_USE_TIME, "ReportCatalog.use_time", "wizard", true));
		list.add(def(ID_TIME_BLOCK, "ReportCatalog.time_block", "clock", true));
		list.add(def(ID_TREND, "ReportCatalog.trend", "up", true));
		list.add(def(ID_MAP_LOAD, "ReportCatalog.map_load", "list", true));
		list.add(def(ID_OVERDUE, "ReportCatalog.overdue", "messagebox_warning", false));
		list.add(def(ID_URGENT, "ReportCatalog.urgent", "flag-orange", true));
		list.add(def(ID_TODOS, "ReportCatalog.todos", "hourglass", false));
		list.add(def(ID_FLAGS, "ReportCatalog.flags", "flag", false));
		list.add(def(ID_POMODORO, "ReportCatalog.pomodoro", "clock2", true));
		list.add(def(ID_DURATION, "ReportCatalog.duration", "full-3", true));
		list.add(def(ID_TARGET, "ReportCatalog.target", "launch", true));
		list.add(def(ID_MIND_PULSE, "ReportCatalog.mind_pulse", "folder", true));
		list.add(def(ID_KEYBOARD, "ReportCatalog.keyboard", "pencil", true));
		list.add(def(ID_RECURRING, "ReportCatalog.recurring", "prepare", false));
		list.add(def(ID_PUBLISHED, "ReportCatalog.published", "internet", false));

		return Collections.unmodifiableList(list);
	}

	private static ReportDefinition def(final String id, final String keyPrefix, final String icon,
	        final boolean usesTimeRange) {
		final ReportDefinition d = new ReportDefinition(id, TextUtils.getText(keyPrefix + ".title"),
		        TextUtils.getText(keyPrefix + ".description"), icon, usesTimeRange);
		d.decision = TextUtils.getText(keyPrefix + ".decision");
		d.dataSource = TextUtils.getText(keyPrefix + ".dataSource");
		return d;
	}
}
