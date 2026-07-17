package org.freeplane.view.swing.features.reports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Report catalog aligned with DocearReminder「报表」menu, plus Desktop extras.
 *
 * Original menu (DocearReminderForm): 时间块 / 使用记录 / 键盘分析 / 趋势 / 目标 / 导图分析
 */
public final class ReportCatalog {
	/* --- DocearReminder originals --- */
	public static final String ID_TIME_BLOCK = "time_block";
	public static final String ID_USE_TIME = "use_time";
	public static final String ID_KEYBOARD = "keyboard";
	public static final String ID_TREND = "trend";
	public static final String ID_TARGET = "target";
	public static final String ID_MINDMAP_ANALYSIS = "mindmap_analysis";

	/* --- Desktop extras --- */
	public static final String ID_WORK_HOURS = "work_hours";
	public static final String ID_OVERDUE = "overdue";
	public static final String ID_SCHEDULE = "schedule";
	public static final String ID_RECURRING = "recurring";
	public static final String ID_TODOS = "todos";
	public static final String ID_POMODORO = "pomodoro";
	public static final String ID_URGENT = "urgent";
	public static final String ID_FLAGS = "flags";
	public static final String ID_MAP_LOAD = "map_load";
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
		// Order matches DocearReminder 报表 menu
		list.add(new ReportDefinition(ID_TIME_BLOCK, "时间块",
		        "原版「时间块」：按时段汇总计划工时，按导图/分类占比（饼图式树）", "clock", true));
		list.add(new ReportDefinition(ID_USE_TIME, "使用记录",
		        "原版「使用记录」：导图打开/有效使用时长，按小时与导图分解", "wizard", true));
		list.add(new ReportDefinition(ID_KEYBOARD, "键盘分析",
		        "原版「键盘分析」：读取 key.txt 击键日志（若存在）按时段统计", "pencil", true));
		list.add(new ReportDefinition(ID_TREND, "趋势",
		        "原版「趋势」：每日计划工时 / 番茄 / 使用时长走势与均值", "up", true));
		list.add(new ReportDefinition(ID_TARGET, "目标",
		        "原版「目标」：扫描「目标」节点下子项，并匹配相关安排工时", "launch", true));
		list.add(new ReportDefinition(ID_MINDMAP_ANALYSIS, "导图分析",
		        "原版「导图分析」：节点修改按日/小时/星期/导图分布", "folder", true));

		list.add(new ReportDefinition(ID_TODAY_DASHBOARD, "今日仪表盘", "今天的逾期、安排、待办与番茄专注一览", "idea",
		        true));
		list.add(new ReportDefinition(ID_WORK_HOURS, "工数统计一览", "按日/导图汇总安排的计划工时（taskTime）", "full-5",
		        true));
		list.add(new ReportDefinition(ID_SCHEDULE, "时段安排清单", "选定时间范围内的全部提醒发生（含周期展开）", "calendar",
		        true));
		list.add(new ReportDefinition(ID_OVERDUE, "逾期提醒", "已过期仍未处理的一次性提醒", "messagebox_warning",
		        false));
		list.add(new ReportDefinition(ID_URGENT, "紧急事项", "紧急度（jinji）≥1 的安排", "flag-orange", true));
		list.add(new ReportDefinition(ID_RECURRING, "周期提醒台账", "全部周期提醒与周期类型", "prepare", false));
		list.add(new ReportDefinition(ID_TODOS, "待办汇总", "工作区全部 hourglass 待办，按导图分组", "hourglass",
		        false));
		list.add(new ReportDefinition(ID_FLAGS, "红旗行动项", "标记了 flag 图标的节点清单", "flag", false));
		list.add(new ReportDefinition(ID_POMODORO, "番茄专注报告", "选定时段内番茄钟专注时长（按日/节点）", "clock2",
		        true));
		list.add(new ReportDefinition(ID_MAP_LOAD, "导图负荷", "各导图的提醒数、待办数与计划工时", "list", true));
		list.add(new ReportDefinition(ID_DURATION_BUCKETS, "时长分布", "安排计划时长分桶（15/30/60/120+）", "full-3",
		        true));
		list.add(new ReportDefinition(ID_PUBLISHED, "发布内容清单", "带 internet 发布图标的节点", "internet",
		        false));
		list.add(new ReportDefinition(ID_RECENT, "最近变更速览", "选定时段内修改过的节点（按时间倒序）", "attach",
		        true));
		return Collections.unmodifiableList(list);
	}
}
