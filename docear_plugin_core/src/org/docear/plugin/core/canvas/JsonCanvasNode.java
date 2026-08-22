package org.docear.plugin.core.canvas;

/** One node in a JSON Canvas 1.0 document. */
public final class JsonCanvasNode {

	public static final String TYPE_TEXT = "text";
	public static final String TYPE_FILE = "file";
	public static final String TYPE_LINK = "link";
	public static final String TYPE_GROUP = "group";

	private String id;
	private String type;
	private int x;
	private int y;
	private int width;
	private int height;
	private String color;
	private String text;
	private String file;
	private String subpath;
	private String url;
	private String label;

	public JsonCanvasNode() {
		this.type = TYPE_TEXT;
		this.width = 260;
		this.height = 120;
	}

	public static JsonCanvasNode text(final String id, final int x, final int y, final String text) {
		final JsonCanvasNode n = new JsonCanvasNode();
		n.id = id;
		n.type = TYPE_TEXT;
		n.x = x;
		n.y = y;
		n.width = 260;
		n.height = 120;
		n.text = text;
		return n;
	}

	public static JsonCanvasNode file(final String id, final int x, final int y, final String file, final String subpath,
			final String label) {
		final JsonCanvasNode n = new JsonCanvasNode();
		n.id = id;
		n.type = TYPE_FILE;
		n.x = x;
		n.y = y;
		n.width = 280;
		n.height = 88;
		n.file = file;
		n.subpath = subpath;
		n.label = label;
		return n;
	}

	public static JsonCanvasNode link(final String id, final int x, final int y, final String url) {
		final JsonCanvasNode n = new JsonCanvasNode();
		n.id = id;
		n.type = TYPE_LINK;
		n.x = x;
		n.y = y;
		n.width = 280;
		n.height = 80;
		n.url = url;
		return n;
	}

	public static JsonCanvasNode group(final String id, final int x, final int y, final String label) {
		final JsonCanvasNode n = new JsonCanvasNode();
		n.id = id;
		n.type = TYPE_GROUP;
		n.x = x;
		n.y = y;
		n.width = 420;
		n.height = 260;
		n.label = label;
		return n;
	}

	public boolean isText() {
		return TYPE_TEXT.equals(type);
	}

	public boolean isFile() {
		return TYPE_FILE.equals(type);
	}

	public boolean isLink() {
		return TYPE_LINK.equals(type);
	}

	public boolean isGroup() {
		return TYPE_GROUP.equals(type);
	}

	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public String getType() {
		return type;
	}

	public void setType(final String type) {
		this.type = type;
	}

	public int getX() {
		return x;
	}

	public void setX(final int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(final int y) {
		this.y = y;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(final int width) {
		this.width = Math.max(80, width);
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(final int height) {
		this.height = Math.max(48, height);
	}

	public String getColor() {
		return color;
	}

	public void setColor(final String color) {
		this.color = color;
	}

	public String getText() {
		return text;
	}

	public void setText(final String text) {
		this.text = text;
	}

	public String getFile() {
		return file;
	}

	public void setFile(final String file) {
		this.file = file;
	}

	public String getSubpath() {
		return subpath;
	}

	public void setSubpath(final String subpath) {
		this.subpath = subpath;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(final String url) {
		this.url = url;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(final String label) {
		this.label = label;
	}

	public String displayTitle() {
		if (label != null && label.trim().length() > 0) {
			return label.trim();
		}
		if (isText()) {
			return firstLine(text);
		}
		if (isFile()) {
			return file != null ? file : "file";
		}
		if (isLink()) {
			return url != null ? url : "link";
		}
		return type != null ? type : "card";
	}

	private static String firstLine(final String raw) {
		if (raw == null || raw.trim().length() == 0) {
			return "文本";
		}
		final String t = raw.trim();
		final int nl = t.indexOf('\n');
		final String line = nl < 0 ? t : t.substring(0, nl);
		return line.length() > 40 ? line.substring(0, 37) + "…" : line;
	}
}
