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
		        "按时段汇总计划工时，中间区饼图看分类占比", "clock", true));
		list.add(new ReportDefinition(ID_USE_TIME, "使用记录",
		        "导图打开/有效使用时长，中间区折线/柱状看时段与导图", "wizard", true));
		list.add(new ReportDefinition(ID_KEYBOARD, "键盘分析",
		        "key.txt 击键统计，中间区看时段分布图", "pencil", true));
		list.add(new ReportDefinition(ID_TREND, "趋势",
		        "每日计划工时 / 番茄 / 使用时长走势折线图", "up", true));
		list.add(new ReportDefinition(ID_TARGET, "目标",
		        "「目标」节点相关安排工时占比图", "launch", true));
		list.add(new ReportDefinition(ID_MINDMAP_ANALYSIS, "导图分析",
		        "节点修改按日/小时分布图", "folder", true));

		list.add(new ReportDefinition(ID_TODAY_DASHBOARD, "今日仪表盘", "今天的逾期、安排、待办与番茄一览图", "idea",
		        true));
		list.add(new ReportDefinition(ID_WORK_HOURS, "工数统计一览", "按日/导图计划工时柱状图", "full-5",
		        true));
		list.add(new ReportDefinition(ID_SCHEDULE, "时段安排清单", "选定时段安排列表（可图可明细）", "calendar",
		        true));
		list.add(new ReportDefinition(ID_OVERDUE, "逾期提醒", "逾期提醒分布图", "messagebox_warning",
		        false));
		list.add(new ReportDefinition(ID_URGENT, "紧急事项", "紧急度分布图", "flag-orange", true));
		list.add(new ReportDefinition(ID_RECURRING, "周期提醒台账", "周期类型分布图", "prepare", false));
		list.add(new ReportDefinition(ID_TODOS, "待办汇总", "待办按导图分布图", "hourglass",
		        false));
		list.add(new ReportDefinition(ID_FLAGS, "红旗行动项", "红旗项按导图分布图", "flag", false));
		list.add(new ReportDefinition(ID_POMODORO, "番茄专注报告", "番茄专注时长折线/柱状图", "clock2",
		        true));
		list.add(new ReportDefinition(ID_MAP_LOAD, "导图负荷", "各导图提醒/待办/工时负荷图", "list", true));
		list.add(new ReportDefinition(ID_DURATION_BUCKETS, "时长分布", "计划时长分桶饼图", "full-3",
		        true));
		list.add(new ReportDefinition(ID_PUBLISHED, "发布内容清单", "发布节点按导图分布", "internet",
		        false));
		list.add(new ReportDefinition(ID_RECENT, "最近变更速览", "最近修改量趋势/分布", "attach",
		        true));
		return Collections.unmodifiableList(list);
	}
}
