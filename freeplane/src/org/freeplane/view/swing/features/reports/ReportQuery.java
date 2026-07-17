package org.freeplane.view.swing.features.reports;

/**
 * Query options shared by reports (mirrors DocearReminder report form filters).
 */
public final class ReportQuery {
	public final ReportTimeRange range;
	public final String includeKeyword;
	public final String excludeKeyword;

	public ReportQuery(final ReportTimeRange range, final String includeKeyword, final String excludeKeyword) {
		this.range = range == null ? ReportTimeRange.ofPreset(ReportTimeRange.PRESET_THIS_WEEK) : range;
		this.includeKeyword = includeKeyword == null ? "" : includeKeyword.trim();
		this.excludeKeyword = excludeKeyword == null ? "" : excludeKeyword.trim();
	}

	public boolean matches(final String text) {
		return matches(text, null, null);
	}

	public boolean matches(final String text, final String path, final String extra) {
		final String hay = ((text == null ? "" : text) + " " + (path == null ? "" : path) + " "
		        + (extra == null ? "" : extra)).toLowerCase();
		if (includeKeyword.length() > 0 && hay.indexOf(includeKeyword.toLowerCase()) < 0) {
			return false;
		}
		if (excludeKeyword.length() > 0 && hay.indexOf(excludeKeyword.toLowerCase()) >= 0) {
			return false;
		}
		return true;
	}
}
