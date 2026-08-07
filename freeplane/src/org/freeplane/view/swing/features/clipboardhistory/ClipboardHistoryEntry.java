package org.freeplane.view.swing.features.clipboardhistory;

/** One unique clipboard text with hit timestamps. */
public final class ClipboardHistoryEntry {
	public long id;
	public String contentHash = "";
	public String content = "";
	public int charLen;
	public long firstTs;
	public long lastTs;
	public int hitCount = 1;
	/** Source DB absolute path (for multi-PC aggregate / delete). */
	public String sourceDbPath = "";
	public String machineId = "";
	public boolean localMachine = true;

	public String preview(final int maxChars) {
		if (content == null) {
			return "";
		}
		final String oneLine = content.replace('\r', ' ').replace('\n', ' ').trim();
		if (oneLine.length() <= maxChars) {
			return oneLine;
		}
		return oneLine.substring(0, Math.max(0, maxChars - 1)) + "…";
	}
}
