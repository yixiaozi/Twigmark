package org.freeplane.view.swing.features.reports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Built-in DocearReminder / workspace reports.
 */
public final class ReportCatalog {
	public static final String ID_WORK_HOURS = "work_hours";
	public static final String ID_OVERDUE = "overdue";
	public static final String ID_SCHEDULE = "schedule";
	public static final String ID_RECURRING = "recurring";
	public static final String ID_TODOS = "todos";
	public static final String ID_POMODORO = "pomodoro";
	public static final String ID_URGENT = "urgent";
	public static final String ID_FLAGS = "flags";
	public static final String ID_MAP_LOAD = "map_load";
	public static final String ID_USAGE = "usage";
	public static final String ID_PUBLISHED = "published";
	public static final String ID_RECENT = "recent";
	public static final String ID_DURATION_BUCKETS = "duration_buckets";
	public static final String ID_TODAY_DASHBOARD = "today_dashboard";

	private static final List ALL = buildAll();

	private ReportCatalog() {
	}

	public static List all() {
		return ALL;
	}

	public static ReportDefinition byId(final String id) {
		for (int i = 0; i < ALL.size(); i++) {
			final ReportDefinition def = (ReportDefinition) ALL.get(i);
			if (def.id.equals(id)) {
				return def;
			}
		}
		return null;
	}

	private static List buildAll() {
		final List list = new ArrayList();
		list.add(new ReportDefinition(ID_TODAY_DASHBOARD, "今日仪表盘", "今天的逾期、安排、待办与番茄专注一览", "idea",
		        true));
		list.add(new ReportDefinition(ID_WORK_HOURS, "工数统计一览", "按日/导图汇总安排的计划工时（taskTime）", "clock",
		        true));
		list.add(new ReportDefinition(ID_SCHEDULE, "时段安排清单", "选定时间范围内的全部提醒发生（含周期展开）", "calendar",
		        true));
		list.add(new ReportDefinition(ID_OVERDUE, "逾期提醒", "已过期仍未处理的一次性提醒", "messagebox_warning",
		        false));
		list.add(new ReportDefinition(ID_URGENT, "紧急事项", "紧急度（jinji）≥1 的安排，可按时段筛选", "flag-orange",
		        true));
		list.add(new ReportDefinition(ID_RECURRING, "周期提醒台账", "全部周期提醒与周期类型", "prepare", false));
		list.add(new ReportDefinition(ID_TODOS, "待办汇总", "工作区全部 hourglass 待办，按导图分组", "hourglass",
		        false));
		list.add(new ReportDefinition(ID_FLAGS, "红旗行动项", "标记了 flag 图标的节点清单", "flag", false));
		list.add(new ReportDefinition(ID_POMODORO, "番茄专注报告", "选定时段内番茄钟专注时长（按日/节点）", "clock2",
		        true));
		list.add(new ReportDefinition(ID_MAP_LOAD, "导图负荷", "各导图的提醒数、待办数与计划工时", "list", true));
		list.add(new ReportDefinition(ID_DURATION_BUCKETS, "时长分布", "安排计划时长分桶（15/30/60/90/120+）", "full-5",
		        true));
		list.add(new ReportDefinition(ID_USAGE, "活动时长报告", "导图打开与有效使用时长（活动报表数据）", "wizard",
		        true));
		list.add(new ReportDefinition(ID_PUBLISHED, "发布内容清单", "带 internet 发布图标的节点", "internet",
		        false));
		list.add(new ReportDefinition(ID_RECENT, "最近变更速览", "选定时段内修改过的节点（按时间倒序）", "up",
		        true));
		return Collections.unmodifiableList(list);
	}
}
