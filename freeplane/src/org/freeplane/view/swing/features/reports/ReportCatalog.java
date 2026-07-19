package org.freeplane.view.swing.features.reports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Catalog designed as a daily operating loop for Docear:
 * focus → plan vs actual → attention → load → debt → deep work → review.
 *
 * Each report answers one decision question with one primary data source.
 */
public final class ReportCatalog {
	/** Opens the usage-stats activity panel in the map viewport. */
	public static final String ID_ACTIVITY = "activity_usage";
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

	private static final List ALL = buildAll();

	private ReportCatalog() {
	}

	public static List all() {
		return ALL;
	}

	public static ReportDefinition byId(final String id) {
		if (id == null) {
			return null;
		}
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

		list.add(def(ID_ACTIVITY, "活动报表",
		        "导图使用时长与打开记录——最近都在看哪些图",
		        "决策：看清时间花在哪张图", "使用记录（.docear_stats）", "wizard", false));

		list.add(def(ID_TODAY, "今日焦点",
		        "今天先处理什么？逾期、今日安排、待办与番茄一览",
		        "决策：开工顺序", "提醒发生 + 待办 + 番茄", "idea", true));

		list.add(def(ID_PLAN_VS_ACTUAL, "计划对照",
		        "计划工时 vs 实际使用 vs 番茄专注，看有没有「计划空转」",
		        "决策：要不要砍计划/补执行", "安排工时 + 使用记录 + 番茄", "full-7", true));

		list.add(def(ID_USE_TIME, "注意力去向",
		        "有效使用时长按小时与导图分解——时间实际花在哪",
		        "决策：减少无效打开、聚焦主图", "使用记录（.docear_stats）", "wizard", true));

		list.add(def(ID_TIME_BLOCK, "计划构成",
		        "计划工时按分类/文件夹占比——时间预算分给了谁",
		        "决策：下周预算怎么调", "提醒计划工时（taskTime）", "clock", true));

		list.add(def(ID_TREND, "节奏趋势",
		        "每日计划与番茄走势，看这周是紧还是松",
		        "决策：周末复盘节奏", "安排工时 + 番茄（按日）", "up", true));

		list.add(def(ID_MAP_LOAD, "导图负荷",
		        "每张导图的安排数、待办数、计划工时——哪张图最重",
		        "决策：拆图 / 清待办 / 降负荷", "安排 + 待办扫描", "list", true));

		list.add(def(ID_OVERDUE, "逾期清算",
		        "已过期仍未处理的一次性提醒——欠账清单",
		        "决策：清债或改期", "一次性提醒（remindAt < now）", "messagebox_warning", false));

		list.add(def(ID_URGENT, "紧急队列",
		        "紧急度 ≥1 的安排，按紧急度分层",
		        "决策：先打哪一层", "提醒 jinji 字段", "flag-orange", true));

		list.add(def(ID_TODOS, "待办库存",
		        "全部 hourglass 待办按导图分布——积压在哪",
		        "决策：清哪张图的待办堆", "工作区待办扫描", "hourglass", false));

		list.add(def(ID_FLAGS, "红旗行动",
		        "标记 flag 的行动项——你亲手钉住要推进的事",
		        "决策：推进/去掉钉子", "flag 图标节点", "flag", false));

		list.add(def(ID_POMODORO, "专注报告",
		        "番茄专注时长按日/节点——深度工作够不够",
		        "决策：保护专注块", "番茄会话", "clock2", true));

		list.add(def(ID_DURATION, "切片习惯",
		        "安排时长分桶（15/30/60/120+）——任务是切太碎还是太大",
		        "决策：改估算习惯", "提醒 taskTime 分桶", "full-3", true));

		list.add(def(ID_TARGET, "目标投入",
		        "「目标」子项匹配到的计划工时——目标有没有排进日历",
		        "决策：给目标补时间块", "目标节点 ∩ 安排", "launch", true));

		list.add(def(ID_MIND_PULSE, "思考热力",
		        "节点修改按日/小时/导图分布——最近脑力砸在哪",
		        "决策：收回散焦、加码主线", "节点修改时间", "folder", true));

		list.add(def(ID_KEYBOARD, "击键节奏",
		        "key.txt 击键按日/时段（无日志则提示放置路径）",
		        "决策：了解活跃时段", "DocearReminder key.txt", "pencil", true));

		list.add(def(ID_RECURRING, "周期台账",
		        "全部周期提醒按类型——例行事务有没有失控增生",
		        "决策：删/合并周期", "周期提醒扫描", "prepare", false));

		list.add(def(ID_PUBLISHED, "发布清单",
		        "带 internet 发布图标的节点——对外输出台账",
		        "决策：核对已发布内容", "internet 图标节点", "internet", false));

		return Collections.unmodifiableList(list);
	}

	private static ReportDefinition def(final String id, final String title, final String description,
	        final String decision, final String dataSource, final String icon, final boolean usesTimeRange) {
		final ReportDefinition d = new ReportDefinition(id, title, description, icon, usesTimeRange);
		d.decision = decision;
		d.dataSource = dataSource;
		return d;
	}
}
