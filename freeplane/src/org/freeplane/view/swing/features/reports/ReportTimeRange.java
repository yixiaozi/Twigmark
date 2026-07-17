package org.freeplane.view.swing.features.reports;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Inclusive calendar-day window [startMs, endMs) used by reports.
 */
public final class ReportTimeRange {
	public static final int PRESET_TODAY = 0;
	public static final int PRESET_YESTERDAY = 1;
	public static final int PRESET_THIS_WEEK = 2;
	public static final int PRESET_LAST_7_DAYS = 3;
	public static final int PRESET_THIS_MONTH = 4;
	public static final int PRESET_LAST_30_DAYS = 5;
	public static final int PRESET_CUSTOM = 6;

	private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

	public final int preset;
	public final long startMs;
	public final long endMs;
	public final String label;

	private ReportTimeRange(final int preset, final long startMs, final long endMs, final String label) {
		this.preset = preset;
		this.startMs = startMs;
		this.endMs = endMs;
		this.label = label;
	}

	public static ReportTimeRange ofPreset(final int preset) {
		final Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		long start;
		long end;
		String label;
		switch (preset) {
			case PRESET_YESTERDAY:
				cal.add(Calendar.DAY_OF_MONTH, -1);
				start = cal.getTimeInMillis();
				cal.add(Calendar.DAY_OF_MONTH, 1);
				end = cal.getTimeInMillis();
				label = "昨天";
				break;
			case PRESET_THIS_WEEK:
				cal.setFirstDayOfWeek(Calendar.MONDAY);
				cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
				start = cal.getTimeInMillis();
				cal.add(Calendar.DAY_OF_MONTH, 7);
				end = cal.getTimeInMillis();
				label = "本周";
				break;
			case PRESET_LAST_7_DAYS:
				end = cal.getTimeInMillis() + 24L * 60L * 60L * 1000L;
				cal.add(Calendar.DAY_OF_MONTH, -6);
				start = cal.getTimeInMillis();
				label = "近7天";
				break;
			case PRESET_THIS_MONTH:
				cal.set(Calendar.DAY_OF_MONTH, 1);
				start = cal.getTimeInMillis();
				cal.add(Calendar.MONTH, 1);
				end = cal.getTimeInMillis();
				label = "本月";
				break;
			case PRESET_LAST_30_DAYS:
				end = cal.getTimeInMillis() + 24L * 60L * 60L * 1000L;
				cal.add(Calendar.DAY_OF_MONTH, -29);
				start = cal.getTimeInMillis();
				label = "近30天";
				break;
			case PRESET_TODAY:
			default:
				start = cal.getTimeInMillis();
				cal.add(Calendar.DAY_OF_MONTH, 1);
				end = cal.getTimeInMillis();
				label = "今天";
				break;
		}
		return new ReportTimeRange(preset, start, end, label + "（" + DAY.format(new Date(start)) + "～"
		        + DAY.format(new Date(end - 1)) + "）");
	}

	public static ReportTimeRange custom(final Date startDay, final Date endDayInclusive) {
		final Calendar start = Calendar.getInstance();
		start.setTime(startDay == null ? new Date() : startDay);
		start.set(Calendar.HOUR_OF_DAY, 0);
		start.set(Calendar.MINUTE, 0);
		start.set(Calendar.SECOND, 0);
		start.set(Calendar.MILLISECOND, 0);
		final Calendar end = Calendar.getInstance();
		end.setTime(endDayInclusive == null ? start.getTime() : endDayInclusive);
		end.set(Calendar.HOUR_OF_DAY, 0);
		end.set(Calendar.MINUTE, 0);
		end.set(Calendar.SECOND, 0);
		end.set(Calendar.MILLISECOND, 0);
		end.add(Calendar.DAY_OF_MONTH, 1);
		long s = start.getTimeInMillis();
		long e = end.getTimeInMillis();
		if (e <= s) {
			e = s + 24L * 60L * 60L * 1000L;
		}
		final String label = "自定义（" + DAY.format(new Date(s)) + "～" + DAY.format(new Date(e - 1)) + "）";
		return new ReportTimeRange(PRESET_CUSTOM, s, e, label);
	}

	public boolean contains(final long millis) {
		return millis >= startMs && millis < endMs;
	}

	public String dayKey(final long millis) {
		return DAY.format(new Date(millis));
	}
}
