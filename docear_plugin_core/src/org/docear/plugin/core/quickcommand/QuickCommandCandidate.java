package org.docear.plugin.core.quickcommand;

import java.io.File;

/**
 * One row in the Shift+Space command palette suggestion list.
 */
final class QuickCommandCandidate {
	enum Kind {
		MAP, ICON_NODE, LAUNCH, COMMAND, HINT
	}

	final Kind kind;
	final String label;
	/** Secondary line (map name / short hint). Never a full filesystem path. */
	final String detail;
	final File mapFile;
	final String nodeId;
	final File launchFile;
	final String command;
	final boolean recent;
	final int historyRank;

	private QuickCommandCandidate(final Kind kind, final String label, final String detail, final File mapFile,
	        final String nodeId, final File launchFile, final String command, final boolean recent,
	        final int historyRank) {
		this.kind = kind;
		this.label = label;
		this.detail = detail;
		this.mapFile = mapFile;
		this.nodeId = nodeId;
		this.launchFile = launchFile;
		this.command = command;
		this.recent = recent;
		this.historyRank = historyRank;
	}

	static QuickCommandCandidate map(final String mapName, final File mapFile, final boolean recent,
	        final int historyRank) {
		return new QuickCommandCandidate(Kind.MAP, mapName, "导图", mapFile, null, null, null, recent, historyRank);
	}

	static QuickCommandCandidate iconNode(final String nodeText, final String mapName, final File mapFile,
	        final String nodeId, final boolean recent, final int historyRank) {
		return new QuickCommandCandidate(Kind.ICON_NODE, nodeText, mapName == null ? "" : mapName, mapFile, nodeId,
		        null, null, recent, historyRank);
	}

	static QuickCommandCandidate launch(final String label, final File file) {
		return new QuickCommandCandidate(Kind.LAUNCH, label, "启动", null, null, file, null, false, -1);
	}

	static QuickCommandCandidate command(final String command, final String detail) {
		return new QuickCommandCandidate(Kind.COMMAND, command, detail, null, null, null, command, false, -1);
	}

	static QuickCommandCandidate hint(final String label, final String detail) {
		return new QuickCommandCandidate(Kind.HINT, label, detail, null, null, null, null, false, -1);
	}

	String kindBadge() {
		switch (kind) {
			case MAP:
				return "导图";
			case ICON_NODE:
				return "节点";
			case LAUNCH:
				return "启动";
			case COMMAND:
				return "命令";
			default:
				return "";
		}
	}

	@Override
	public String toString() {
		return label;
	}
}
