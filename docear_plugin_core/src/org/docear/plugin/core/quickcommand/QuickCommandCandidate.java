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
	final String detail;
	final File mapFile;
	final String nodeId;
	final File launchFile;
	final String command;

	private QuickCommandCandidate(final Kind kind, final String label, final String detail, final File mapFile,
	        final String nodeId, final File launchFile, final String command) {
		this.kind = kind;
		this.label = label;
		this.detail = detail;
		this.mapFile = mapFile;
		this.nodeId = nodeId;
		this.launchFile = launchFile;
		this.command = command;
	}

	static QuickCommandCandidate map(final String mapName, final File mapFile) {
		return new QuickCommandCandidate(Kind.MAP, mapName, mapFile != null ? mapFile.getAbsolutePath() : "", mapFile,
		        null, null, null);
	}

	static QuickCommandCandidate iconNode(final String nodeText, final String mapName, final File mapFile,
	        final String nodeId) {
		final String detail = mapName + (mapFile != null ? "  ·  " + mapFile.getName() : "");
		return new QuickCommandCandidate(Kind.ICON_NODE, nodeText, detail, mapFile, nodeId, null, null);
	}

	static QuickCommandCandidate launch(final String label, final File file) {
		return new QuickCommandCandidate(Kind.LAUNCH, label, file != null ? file.getAbsolutePath() : "", null, null,
		        file, null);
	}

	static QuickCommandCandidate command(final String command, final String detail) {
		return new QuickCommandCandidate(Kind.COMMAND, command, detail, null, null, null, command);
	}

	static QuickCommandCandidate hint(final String label, final String detail) {
		return new QuickCommandCandidate(Kind.HINT, label, detail, null, null, null, null);
	}

	String displayText() {
		if (detail == null || detail.length() == 0) {
			return label;
		}
		return label + "    " + detail;
	}

	@Override
	public String toString() {
		return displayText();
	}
}
