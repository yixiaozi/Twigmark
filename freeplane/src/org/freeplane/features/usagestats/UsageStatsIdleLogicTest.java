package org.freeplane.features.usagestats;

/**
 * Headless checks for idle charge math (no AWT toolkit required for the pure helpers).
 */
public final class UsageStatsIdleLogicTest {

	public static void main(final String[] args) {
		// Grace period must not be charged as idle.
		final long threshold = IdleDetector.DEFAULT_IDLE_THRESHOLD_MS;
		if (threshold < 60000L) {
			throw new IllegalStateException("threshold too aggressive: " + threshold);
		}
		final long gap = threshold + 90000L;
		final long chargeable = Math.max(0L, gap - threshold);
		if (chargeable != 90000L) {
			throw new IllegalStateException("chargeable expected 90000 got " + chargeable);
		}
		final long shortGap = threshold - 1000L;
		final long chargeableShort = Math.max(0L, shortGap - threshold);
		if (chargeableShort != 0L) {
			throw new IllegalStateException("short gap must not charge idle");
		}

		final UsageRecord a = new UsageRecord();
		a.setMapPath("E:/maps/demo.mm");
		a.setFileHash("dcr-1");
		a.setStartTime(1000L);
		a.setEndTime(1000L + 10 * 60000L);
		a.setIdleDurationMs(90000L);
		a.calculateDurations();
		if (a.getTotalDurationMs() != 600000L) {
			throw new IllegalStateException("total");
		}
		if (a.getEffectiveDurationMs() != 510000L) {
			throw new IllegalStateException("effective expected 510000 got " + a.getEffectiveDurationMs());
		}

		final String key = UsageStatsManager.summaryKeyFor(a);
		if (!"hash:dcr-1".equals(key)) {
			throw new IllegalStateException("summary key: " + key);
		}

		final MapUsageSummary summary = new MapUsageSummary("E:/old/demo.mm");
		summary.preferPath("E:/maps/demo.mm", 2000L);
		if (!"demo.mm".equals(summary.getDisplayName())) {
			throw new IllegalStateException("display name");
		}
		if (!"E:/maps/demo.mm".equals(summary.getMapPath())) {
			throw new IllegalStateException("preferred path");
		}

		System.out.println("PASS UsageStatsIdleLogicTest");
		System.out.println("idleThresholdMs=" + threshold + " sampleEffectiveMs=" + a.getEffectiveDurationMs());
	}
}
