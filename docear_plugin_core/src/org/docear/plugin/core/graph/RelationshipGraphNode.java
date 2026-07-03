package org.docear.plugin.core.graph;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URL;

import org.freeplane.core.util.Compat;

public final class RelationshipGraphNode {

	private final File file;
	private final String nodeId;
	private final String label;
	private final String mapLabel;
	private double x;
	private double y;
	private double vx;
	private double vy;

	private RelationshipGraphNode(final File file, final String nodeId, final String label, final String mapLabel) {
		this.file = file;
		this.nodeId = nodeId;
		this.label = label;
		this.mapLabel = mapLabel;
	}

	public static RelationshipGraphNode forMapFile(final File file) {
		String raw = file.getName().endsWith(".mm")
		        ? file.getName().substring(0, file.getName().length() - 3)
		        : file.getName();
		final String decoded = decodeLabel(raw);
		return new RelationshipGraphNode(file, null, decoded, decoded);
	}

	public static RelationshipGraphNode forMapNode(final File file, final String nodeId, final String nodeText) {
		String rawMap = file.getName().endsWith(".mm")
		        ? file.getName().substring(0, file.getName().length() - 3)
		        : file.getName();
		final String mapName = decodeLabel(rawMap);
		String text = nodeText == null ? nodeId : nodeText.trim();
		if (text.length() == 0) {
			text = nodeId;
		}
		text = decodeLabel(text);
		if (text.length() > 40) {
			text = text.substring(0, 37) + "...";
		}
		return new RelationshipGraphNode(file, nodeId, text, mapName);
	}

	private static String decodeLabel(final String raw) {
		if (raw == null) {
			return "";
		}
		String value = raw.replace('\n', ' ').replace('\r', ' ').trim();
		if (value.indexOf('%') >= 0) {
			try {
				value = URLDecoder.decode(value, "UTF-8");
			}
			catch (final UnsupportedEncodingException e) {
				value = value.replace("%20", " ");
			}
		}
		return value;
	}

	public File getFile() {
		return file;
	}

	public String getNodeId() {
		return nodeId;
	}

	public boolean isMapNode() {
		return nodeId != null && nodeId.length() > 0;
	}

	public String getLabel() {
		return label;
	}

	public String getMapLabel() {
		return mapLabel;
	}

	public URL getOpenUrl() throws Exception {
		if (isMapNode()) {
			return new URL(Compat.fileToUrl(file).toString() + "#" + nodeId);
		}
		return Compat.fileToUrl(file);
	}

	public double getX() {
		return x;
	}

	public void setX(final double x) {
		this.x = x;
	}

	public double getY() {
		return y;
	}

	public void setY(final double y) {
		this.y = y;
	}

	public double getVx() {
		return vx;
	}

	public void setVx(final double vx) {
		this.vx = vx;
	}

	public double getVy() {
		return vy;
	}

	public void setVy(final double vy) {
		this.vy = vy;
	}

	public String getPathKey() {
		String fileKey;
		try {
			fileKey = file.getCanonicalFile().getAbsolutePath();
		}
		catch (final Exception e) {
			fileKey = file.getAbsolutePath();
		}
		if (isMapNode()) {
			return fileKey + "#" + nodeId;
		}
		return fileKey;
	}
}
