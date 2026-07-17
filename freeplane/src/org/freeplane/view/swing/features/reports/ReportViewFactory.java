package org.freeplane.view.swing.features.reports;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a {@link ReportViewModel} (charts + details) from a {@link ReportNodeSpec} tree.
 */
public final class ReportViewFactory {
	private static final Pattern HOURS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*h", Pattern.CASE_INSENSITIVE);
	private static final Pattern MINS = Pattern.compile("(\\d+)\\s*m(?![a-zA-Z])", Pattern.CASE_INSENSITIVE);
	private static final Pattern PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
	private static final Pattern COUNT = Pattern.compile("(\\d+)\\s*(?:条|次|项|个|键)");
	private static final Pattern PLAIN_NUM = Pattern.compile("(?:^|[·•\\-–—\\s])(\\d+(?:\\.\\d+)?)(?:\\s*$|\\s*[·•])");

	private ReportViewFactory() {
	}

	public static ReportViewModel fromTree(final ReportDefinition def, final ReportQuery query,
	        final ReportNodeSpec tree) {
		final String title = tree != null && tree.text != null ? tree.text : (def == null ? "报表" : def.title);
		final String subtitle = buildSubtitle(def, query);
		final ReportViewModel model = new ReportViewModel(title, subtitle);
		if (tree == null) {
			model.addDetail("无数据");
			return model;
		}
		flattenDetails(tree, model.details, 0);
		collectCharts(tree, model, def == null ? "" : def.id);
		if (!model.hasCharts()) {
			final ReportChartSeries fallback = tryFlatChildrenChart(tree, "分布", preferredType(def == null ? "" : def.id));
			model.addChart(fallback);
		}
		return model;
	}

	private static String buildSubtitle(final ReportDefinition def, final ReportQuery query) {
		final StringBuilder sb = new StringBuilder();
		if (def != null) {
			sb.append(def.description);
		}
		if (query != null && query.range != null && def != null && def.usesTimeRange) {
			if (sb.length() > 0) {
				sb.append("  ·  ");
			}
			sb.append(query.range.label);
		}
		return sb.toString();
	}

	private static void flattenDetails(final ReportNodeSpec node, final List out, final int depth) {
		if (node == null) {
			return;
		}
		if (depth > 0 && node.text != null) {
			final StringBuilder line = new StringBuilder();
			for (int i = 1; i < depth; i++) {
				line.append("  ");
			}
			if (depth > 1) {
				line.append("· ");
			}
			line.append(node.text);
			out.add(line.toString());
		}
		final List kids = node.getChildren();
		for (int i = 0; i < kids.size(); i++) {
			flattenDetails((ReportNodeSpec) kids.get(i), out, depth + 1);
		}
	}

	private static void collectCharts(final ReportNodeSpec root, final ReportViewModel model, final String reportId) {
		final List kids = root.getChildren();
		for (int i = 0; i < kids.size(); i++) {
			final ReportNodeSpec child = (ReportNodeSpec) kids.get(i);
			maybeAddGroupChart(child, model, reportId);
			final List grand = child.getChildren();
			for (int j = 0; j < grand.size(); j++) {
				maybeAddGroupChart((ReportNodeSpec) grand.get(j), model, reportId);
			}
		}
	}

	private static void maybeAddGroupChart(final ReportNodeSpec group, final ReportViewModel model,
	        final String reportId) {
		if (group == null || !group.hasChildren()) {
			return;
		}
		final String title = group.text == null ? "图表" : group.text;
		final int type = chartTypeForTitle(title, reportId);
		final ReportChartSeries series = new ReportChartSeries(shortTitle(title), type);
		int parsed = 0;
		final List kids = group.getChildren();
		for (int i = 0; i < kids.size(); i++) {
			final ReportNodeSpec leaf = (ReportNodeSpec) kids.get(i);
			final ParsedPoint point = parsePoint(leaf.text);
			if (point != null) {
				series.add(point.label, point.value);
				parsed++;
			}
		}
		if (parsed >= 2) {
			model.addChart(series);
		}
	}

	private static ReportChartSeries tryFlatChildrenChart(final ReportNodeSpec root, final String title,
	        final int type) {
		if (root == null || !root.hasChildren()) {
			return null;
		}
		final ReportChartSeries series = new ReportChartSeries(title, type);
		final List kids = root.getChildren();
		for (int i = 0; i < kids.size(); i++) {
			final ReportNodeSpec child = (ReportNodeSpec) kids.get(i);
			final ParsedPoint point = parsePoint(child.text);
			if (point != null) {
				series.add(point.label, point.value);
			}
		}
		return series.isEmpty() ? null : series;
	}

	private static int preferredType(final String reportId) {
		if (ReportCatalog.ID_TREND.equals(reportId) || ReportCatalog.ID_USE_TIME.equals(reportId)
		        || ReportCatalog.ID_KEYBOARD.equals(reportId) || ReportCatalog.ID_POMODORO.equals(reportId)
		        || ReportCatalog.ID_MINDMAP_ANALYSIS.equals(reportId)) {
			return ReportChartSeries.TYPE_LINE;
		}
		if (ReportCatalog.ID_TIME_BLOCK.equals(reportId) || ReportCatalog.ID_DURATION_BUCKETS.equals(reportId)
		        || ReportCatalog.ID_TARGET.equals(reportId) || ReportCatalog.ID_MAP_LOAD.equals(reportId)) {
			return ReportChartSeries.TYPE_PIE;
		}
		return ReportChartSeries.TYPE_BAR;
	}

	private static int chartTypeForTitle(final String title, final String reportId) {
		final String t = title == null ? "" : title;
		if (t.indexOf("饼") >= 0 || t.indexOf("占比") >= 0 || t.indexOf("分布") >= 0 || t.indexOf("分类") >= 0) {
			return ReportChartSeries.TYPE_PIE;
		}
		if (t.indexOf("趋势") >= 0 || t.indexOf("走势") >= 0 || t.indexOf("按日") >= 0 || t.indexOf("每日") >= 0
		        || t.indexOf("按时段") >= 0 || t.indexOf("小时") >= 0) {
			return ReportChartSeries.TYPE_LINE;
		}
		if (t.indexOf("柱") >= 0 || t.indexOf("工时") >= 0 || t.indexOf("负荷") >= 0) {
			return ReportChartSeries.TYPE_BAR;
		}
		return preferredType(reportId);
	}

	private static String shortTitle(final String title) {
		if (title == null) {
			return "图表";
		}
		final int cut = title.indexOf('（');
		if (cut > 0) {
			return title.substring(0, cut).trim();
		}
		if (title.length() > 28) {
			return title.substring(0, 27) + "…";
		}
		return title;
	}

	private static ParsedPoint parsePoint(final String text) {
		if (text == null || text.trim().length() == 0) {
			return null;
		}
		final String raw = text.trim();
		if (raw.startsWith("该时段") || raw.startsWith("未找到") || raw.startsWith("可将") || raw.startsWith("合计")
		        || raw.startsWith("汇总") || raw.startsWith("会话") || raw.startsWith("有效时长")
		        || raw.startsWith("有工时") || raw.startsWith("报表")) {
			return null;
		}
		final String label = extractLabel(raw);
		Double minutes = parseDurationMinutes(raw);
		if (minutes != null) {
			return new ParsedPoint(label, minutes.doubleValue());
		}
		final Matcher pct = PERCENT.matcher(raw);
		if (pct.find()) {
			return new ParsedPoint(label, Double.parseDouble(pct.group(1)));
		}
		final Matcher count = COUNT.matcher(raw);
		if (count.find()) {
			return new ParsedPoint(label, Double.parseDouble(count.group(1)));
		}
		final Matcher plain = PLAIN_NUM.matcher(raw);
		String last = null;
		while (plain.find()) {
			last = plain.group(1);
		}
		if (last != null) {
			return new ParsedPoint(label, Double.parseDouble(last));
		}
		return null;
	}

	private static String extractLabel(final String raw) {
		final String[] parts = raw.split("·|•");
		if (parts.length > 0) {
			final String first = parts[0].trim();
			if (first.length() > 0) {
				return first.length() > 24 ? first.substring(0, 23) + "…" : first;
			}
		}
		return raw.length() > 24 ? raw.substring(0, 23) + "…" : raw;
	}

	private static Double parseDurationMinutes(final String raw) {
		double minutes = 0;
		boolean found = false;
		final Matcher hm = HOURS.matcher(raw);
		while (hm.find()) {
			minutes += Double.parseDouble(hm.group(1)) * 60.0;
			found = true;
		}
		final Matcher mm = MINS.matcher(raw);
		while (mm.find()) {
			minutes += Double.parseDouble(mm.group(1));
			found = true;
		}
		return found ? Double.valueOf(minutes) : null;
	}

	private static final class ParsedPoint {
		final String label;
		final double value;

		ParsedPoint(final String label, final double value) {
			this.label = label;
			this.value = value;
		}
	}
}
