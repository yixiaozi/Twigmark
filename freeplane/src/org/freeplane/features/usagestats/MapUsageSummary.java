package org.freeplane.features.usagestats;

import java.io.File;

public class MapUsageSummary {
	private String mapPath;
	private String displayName;
	private int sessionCount;
	private long totalDurationMs;
	private long effectiveDurationMs;
	private long idleDurationMs;
	private long lastEndTime;

	public MapUsageSummary(String mapPath) {
		this.mapPath = mapPath == null ? "" : mapPath;
		final File file = this.mapPath.isEmpty() ? null : new File(this.mapPath);
		this.displayName = file != null ? file.getName() : this.mapPath;
	}

	public void addRecord(UsageRecord record) {
		if (record == null || !UsageStatsManager.isSignificantSession(record)) {
			return;
		}
		sessionCount++;
		totalDurationMs += record.getTotalDurationMs();
		effectiveDurationMs += record.getEffectiveDurationMs();
		idleDurationMs += record.getIdleDurationMs();
		if (record.getEndTime() > lastEndTime) {
			lastEndTime = record.getEndTime();
		}
	}

	/** When merging path variants of the same map, keep the most recently used path. */
	public void preferPath(final String path, final long endTime) {
		if (path == null || path.length() == 0) {
			return;
		}
		if (endTime >= lastEndTime || mapPath.length() == 0) {
			mapPath = path;
			final File file = new File(path);
			displayName = file.getName();
		}
	}

	public String getMapPath() {
		return mapPath;
	}

	public String getDisplayName() {
		return displayName;
	}

	public int getSessionCount() {
		return sessionCount;
	}

	public long getTotalDurationMs() {
		return totalDurationMs;
	}

	public long getEffectiveDurationMs() {
		return effectiveDurationMs;
	}

	public long getIdleDurationMs() {
		return idleDurationMs;
	}

	public long getLastEndTime() {
		return lastEndTime;
	}

	public boolean matchesPath(String path) {
		if (path == null || path.isEmpty()) {
			return false;
		}
		if (mapPath.equals(path)) {
			return true;
		}
		try {
			return new File(mapPath).getCanonicalPath().equals(new File(path).getCanonicalPath());
		}
		catch (Exception e) {
			return mapPath.equalsIgnoreCase(path);
		}
	}
}
