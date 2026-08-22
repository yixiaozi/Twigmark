package org.docear.plugin.core.canvas;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Line2D;
import java.io.File;
import java.net.URL;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

/**
 * Editable infinite canvas for JSON Canvas 1.0 (Obsidian-compatible).
 */
public final class JsonCanvasEditor extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int HANDLE = 8;
	private static final Color BG = new Color(248, 249, 251);
	private static final Color GRID = new Color(232, 234, 238);
	private static final Color CARD = Color.WHITE;
	private static final Color CARD_BORDER = new Color(210, 214, 220);
	private static final Color SELECT = new Color(24, 108, 186);
	private static final Color EDGE = new Color(120, 128, 140);
	private static final Color TEXT = new Color(40, 44, 52);
	private static final Color MUTED = new Color(110, 116, 124);

	public interface Listener {
		void onModifiedChanged(boolean modified);
	}

	private final Surface surface = new Surface();
	private final JLabel hint = new JLabel();
	private JsonCanvasDocument document = new JsonCanvasDocument();
	private File canvasFile;
	private Listener listener;
	private boolean modified;
	private String selectedId;
	private String selectedEdgeId;
	private double zoom = 1.0;
	private double panX = 40;
	private double panY = 40;
	private int pressX;
	private int pressY;
	private double panAnchorX;
	private double panAnchorY;
	private boolean panning;
	private boolean dragging;
	private boolean resizing;
	private boolean linking;
	private String linkFromId;
	private String linkFromSide;
	private int linkMouseX;
	private int linkMouseY;
	private int dragOffsetX;
	private int dragOffsetY;
	private int resizeStartW;
	private int resizeStartH;

	public JsonCanvasEditor() {
		setLayout(new BorderLayout());
		add(buildToolbar(), BorderLayout.NORTH);
		add(surface, BorderLayout.CENTER);
		hint.setForeground(MUTED);
		hint.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));
		hint.setText("拖拽移动 · 卡片边缘拉出连线 · 双击编辑/打开 · Delete 删除");
		add(hint, BorderLayout.SOUTH);
		setFocusable(true);
	}

	public void setListener(final Listener listener) {
		this.listener = listener;
	}

	public JsonCanvasDocument getDocument() {
		return document;
	}

	public File getCanvasFile() {
		return canvasFile;
	}

	public boolean isModified() {
		return modified;
	}

	public void load(final File file, final JsonCanvasDocument doc) {
		this.canvasFile = file;
		this.document = doc != null ? doc : new JsonCanvasDocument();
		this.selectedId = null;
		this.selectedEdgeId = null;
		setModified(false);
		fitIfNeeded();
		surface.repaint();
	}

	public void save() throws Exception {
		if (canvasFile == null) {
			throw new IllegalStateException("no canvas file");
		}
		JsonCanvasIo.write(canvasFile, document);
		setModified(false);
	}

	public void addTextCard(final String text) {
		final Point p = viewCenter();
		document.getNodes().add(JsonCanvasNode.text(JsonCanvasDocument.newId(), p.x, p.y, text != null ? text : "新文本"));
		setModified(true);
		surface.repaint();
	}

	public void addCurrentMindMapNode() {
		try {
			final Controller c = Controller.getCurrentController();
			if (c == null || c.getSelection() == null) {
				return;
			}
			final NodeModel node = c.getSelection().getSelected();
			if (node == null) {
				return;
			}
			final MapModel map = node.getMap();
			if (map == null || map.getFile() == null) {
				JOptionPane.showMessageDialog(this, "当前没有已保存的导图节点");
				return;
			}
			final File mm = map.getFile();
			final Point p = viewCenter();
			final String rel = relativize(mm);
			document.getNodes().add(JsonCanvasNode.file(JsonCanvasDocument.newId(), p.x, p.y, rel, "#" + node.getID(),
					plain(node.getText())));
			setModified(true);
			surface.repaint();
		}
		catch (Exception e) {
			LogUtils.warn("Canvas: add current node failed", e);
		}
	}

	private JPanel buildToolbar() {
		final JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		bar.add(btn("文本卡", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				addTextCard("新文本");
			}
		}));
		bar.add(btn("当前节点", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				addCurrentMindMapNode();
			}
		}));
		bar.add(btn("链接", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final String url = JOptionPane.showInputDialog(JsonCanvasEditor.this, "URL", "https://");
				if (url != null && url.trim().length() > 0) {
					final Point p = viewCenter();
					document.getNodes().add(JsonCanvasNode.link(JsonCanvasDocument.newId(), p.x, p.y, url.trim()));
					setModified(true);
					surface.repaint();
				}
			}
		}));
		bar.add(btn("分组", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				final Point p = viewCenter();
				document.getNodes().add(JsonCanvasNode.group(JsonCanvasDocument.newId(), p.x - 40, p.y - 40, "分组"));
				setModified(true);
				surface.repaint();
			}
		}));
		bar.add(btn("删除", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				deleteSelection();
			}
		}));
		bar.add(btn("保存", new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				try {
					save();
				}
				catch (Exception ex) {
					JOptionPane.showMessageDialog(JsonCanvasEditor.this, "保存失败：\n" + ex.getMessage());
				}
			}
		}));
		return bar;
	}

	private JButton btn(final String label, final ActionListener listener) {
		final JButton b = new JButton(label);
		b.addActionListener(listener);
		return b;
	}

	private void setModified(final boolean value) {
		if (modified == value) {
			return;
		}
		modified = value;
		if (listener != null) {
			listener.onModifiedChanged(value);
		}
	}

	private void deleteSelection() {
		if (selectedId != null) {
			document.removeNode(selectedId);
			selectedId = null;
			setModified(true);
		}
		else if (selectedEdgeId != null) {
			document.removeEdge(selectedEdgeId);
			selectedEdgeId = null;
			setModified(true);
		}
		surface.repaint();
	}

	private Point viewCenter() {
		final int sx = Math.max(40, surface.getWidth() / 2);
		final int sy = Math.max(40, surface.getHeight() / 2);
		return new Point((int) ((sx - panX) / zoom), (int) ((sy - panY) / zoom));
	}

	private void fitIfNeeded() {
		if (document.getNodes().isEmpty()) {
			zoom = 1;
			panX = 40;
			panY = 40;
			return;
		}
	}

	private String relativize(final File target) {
		if (canvasFile == null || canvasFile.getParentFile() == null || target == null) {
			return target != null ? target.getAbsolutePath() : "";
		}
		try {
			final String base = canvasFile.getParentFile().getCanonicalPath();
			final String abs = target.getCanonicalPath();
			if (abs.startsWith(base + File.separator)) {
				return abs.substring(base.length() + 1).replace('\\', '/');
			}
			return abs;
		}
		catch (Exception e) {
			return target.getAbsolutePath();
		}
	}

	File resolveFile(final JsonCanvasNode node) {
		if (node == null || node.getFile() == null) {
			return null;
		}
		File f = new File(node.getFile());
		if (!f.isAbsolute() && canvasFile != null && canvasFile.getParentFile() != null) {
			f = new File(canvasFile.getParentFile(), node.getFile());
		}
		return f;
	}

	void openNode(final JsonCanvasNode node) {
		if (node == null) {
			return;
		}
		try {
			if (node.isFile()) {
				final File f = resolveFile(node);
				if (f == null || !f.exists()) {
					JOptionPane.showMessageDialog(this, "找不到文件：\n" + node.getFile());
					return;
				}
				URL url = Compat.fileToUrl(f);
				final String sub = node.getSubpath();
				if (sub != null && sub.startsWith("#")) {
					url = new URL(url.toString() + sub);
				}
				Controller.getCurrentController().getViewController().openDocument(url);
			}
			else if (node.isLink() && node.getUrl() != null) {
				Controller.getCurrentController().getViewController().openDocument(new URL(node.getUrl()));
			}
			else if (node.isText() || node.isGroup()) {
				editText(node);
			}
		}
		catch (Exception e) {
			LogUtils.warn("Canvas: open failed", e);
		}
	}

	void editText(final JsonCanvasNode node) {
		final JTextArea area = new JTextArea(node.isText() ? nvl(node.getText()) : nvl(node.getLabel()), 8, 40);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		final int ok = JOptionPane.showConfirmDialog(this, new javax.swing.JScrollPane(area),
				node.isGroup() ? "编辑分组标题" : "编辑文本", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (ok == JOptionPane.OK_OPTION) {
			if (node.isGroup()) {
				node.setLabel(area.getText());
			}
			else {
				node.setText(area.getText());
			}
			setModified(true);
			surface.repaint();
		}
	}

	private static String nvl(final String s) {
		return s == null ? "" : s;
	}

	private static String plain(final String htmlOrText) {
		if (htmlOrText == null) {
			return "";
		}
		String t = htmlOrText.replaceAll("(?s)<[^>]+>", " ").replace("&nbsp;", " ").trim();
		t = t.replace('\n', ' ');
		return t.length() > 60 ? t.substring(0, 57) + "…" : t;
	}

	private Color colorOf(final String token, final Color fallback) {
		if (token == null || token.length() == 0) {
			return fallback;
		}
		if (token.startsWith("#") && (token.length() == 7 || token.length() == 4)) {
			try {
				return Color.decode(token.length() == 4
						? "#" + token.charAt(1) + token.charAt(1) + token.charAt(2) + token.charAt(2) + token.charAt(3)
								+ token.charAt(3)
						: token);
			}
			catch (Exception e) {
				return fallback;
			}
		}
		if ("1".equals(token)) {
			return new Color(233, 90, 90);
		}
		if ("2".equals(token)) {
			return new Color(233, 151, 63);
		}
		if ("3".equals(token)) {
			return new Color(224, 196, 72);
		}
		if ("4".equals(token)) {
			return new Color(76, 174, 109);
		}
		if ("5".equals(token)) {
			return new Color(72, 176, 196);
		}
		if ("6".equals(token)) {
			return new Color(147, 112, 219);
		}
		return fallback;
	}

	private final class Surface extends JPanel {
		private static final long serialVersionUID = 1L;

		Surface() {
			setBackground(BG);
			setFocusable(true);
			setPreferredSize(new Dimension(800, 600));
			final MouseAdapter mouse = new MouseAdapter() {
				public void mousePressed(final MouseEvent e) {
					requestFocusInWindow();
					pressX = e.getX();
					pressY = e.getY();
					final JsonCanvasNode node = findNodeAt(e.getX(), e.getY());
					if (SwingUtilities.isRightMouseButton(e)) {
						showMenu(node, e.getX(), e.getY());
						return;
					}
					if (node != null) {
						selectedId = node.getId();
						selectedEdgeId = null;
						final String side = hitPort(node, e.getX(), e.getY());
						if (side != null) {
							linking = true;
							linkFromId = node.getId();
							linkFromSide = side;
							linkMouseX = e.getX();
							linkMouseY = e.getY();
							setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
							repaint();
							return;
						}
						if (hitResize(node, e.getX(), e.getY())) {
							resizing = true;
							resizeStartW = node.getWidth();
							resizeStartH = node.getHeight();
							setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
							return;
						}
						dragging = true;
						final Point g = screenToGraph(e.getX(), e.getY());
						dragOffsetX = g.x - node.getX();
						dragOffsetY = g.y - node.getY();
						setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
						repaint();
						return;
					}
					final JsonCanvasEdge edge = findEdgeAt(e.getX(), e.getY());
					if (edge != null) {
						selectedEdgeId = edge.getId();
						selectedId = null;
						repaint();
						return;
					}
					selectedId = null;
					selectedEdgeId = null;
					panning = true;
					panAnchorX = panX;
					panAnchorY = panY;
					setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
					repaint();
				}

				public void mouseDragged(final MouseEvent e) {
					if (panning) {
						panX = panAnchorX + (e.getX() - pressX);
						panY = panAnchorY + (e.getY() - pressY);
						repaint();
						return;
					}
					if (linking) {
						linkMouseX = e.getX();
						linkMouseY = e.getY();
						repaint();
						return;
					}
					final JsonCanvasNode node = document.findNode(selectedId);
					if (node == null) {
						return;
					}
					final Point g = screenToGraph(e.getX(), e.getY());
					if (resizing) {
						node.setWidth(resizeStartW + (int) ((e.getX() - pressX) / zoom));
						node.setHeight(resizeStartH + (int) ((e.getY() - pressY) / zoom));
						setModified(true);
						repaint();
						return;
					}
					if (dragging) {
						node.setX(g.x - dragOffsetX);
						node.setY(g.y - dragOffsetY);
						setModified(true);
						repaint();
					}
				}

				public void mouseReleased(final MouseEvent e) {
					if (linking) {
						final JsonCanvasNode target = findNodeAt(e.getX(), e.getY());
						if (target != null && !target.getId().equals(linkFromId)) {
							final String toSide = hitPort(target, e.getX(), e.getY());
							document.getEdges().add(JsonCanvasEdge.connect(JsonCanvasDocument.newId(), linkFromId,
									target.getId(), linkFromSide, toSide != null ? toSide : opposite(linkFromSide)));
							setModified(true);
						}
					}
					linking = false;
					dragging = false;
					resizing = false;
					panning = false;
					linkFromId = null;
					setCursor(Cursor.getDefaultCursor());
					repaint();
				}

				public void mouseClicked(final MouseEvent e) {
					if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
						final JsonCanvasNode node = findNodeAt(e.getX(), e.getY());
						if (node != null) {
							openNode(node);
						}
						else if (selectedEdgeId != null) {
							editEdgeLabel();
						}
					}
				}

				public void mouseWheelMoved(final MouseWheelEvent e) {
					final double old = zoom;
					zoom = Math.max(0.25, Math.min(2.5, zoom * (e.getWheelRotation() < 0 ? 1.1 : 0.9)));
					final double s = zoom / old;
					panX = e.getX() - (e.getX() - panX) * s;
					panY = e.getY() - (e.getY() - panY) * s;
					repaint();
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
			addMouseWheelListener(mouse);
			addKeyListener(new KeyAdapter() {
				public void keyPressed(final KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
						deleteSelection();
						e.consume();
					}
					else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
						selectedId = null;
						selectedEdgeId = null;
						repaint();
					}
				}
			});
		}

		protected void paintComponent(final Graphics g) {
			super.paintComponent(g);
			final Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				drawGrid(g2);
				final List<JsonCanvasNode> nodes = document.getNodes();
				for (int i = 0; i < nodes.size(); i++) {
					if (nodes.get(i).isGroup()) {
						drawNode(g2, nodes.get(i));
					}
				}
				drawEdges(g2);
				for (int i = 0; i < nodes.size(); i++) {
					if (!nodes.get(i).isGroup()) {
						drawNode(g2, nodes.get(i));
					}
				}
				if (linking && linkFromId != null) {
					final JsonCanvasNode from = document.findNode(linkFromId);
					if (from != null) {
						final Point a = portPoint(from, linkFromSide);
						g2.setColor(SELECT);
						g2.drawLine(graphToScreenX(a.x), graphToScreenY(a.y), linkMouseX, linkMouseY);
					}
				}
			}
			finally {
				g2.dispose();
			}
		}

		private void drawGrid(final Graphics2D g2) {
			g2.setColor(GRID);
			final int step = (int) Math.max(16, 32 * zoom);
			for (int x = ((int) panX) % step; x < getWidth(); x += step) {
				g2.drawLine(x, 0, x, getHeight());
			}
			for (int y = ((int) panY) % step; y < getHeight(); y += step) {
				g2.drawLine(0, y, getWidth(), y);
			}
		}

		private void drawEdges(final Graphics2D g2) {
			final List<JsonCanvasEdge> edges = document.getEdges();
			g2.setStroke(new BasicStroke(1.6f));
			for (int i = 0; i < edges.size(); i++) {
				final JsonCanvasEdge e = edges.get(i);
				final JsonCanvasNode a = document.findNode(e.getFromNode());
				final JsonCanvasNode b = document.findNode(e.getToNode());
				if (a == null || b == null) {
					continue;
				}
				final Point pa = portPoint(a, e.getFromSide());
				final Point pb = portPoint(b, e.getToSide());
				g2.setColor(e.getId() != null && e.getId().equals(selectedEdgeId) ? SELECT : colorOf(e.getColor(), EDGE));
				g2.draw(new Line2D.Double(graphToScreenX(pa.x), graphToScreenY(pa.y), graphToScreenX(pb.x),
						graphToScreenY(pb.y)));
				drawArrow(g2, graphToScreenX(pa.x), graphToScreenY(pa.y), graphToScreenX(pb.x), graphToScreenY(pb.y));
				if (e.getLabel() != null && e.getLabel().length() > 0) {
					g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
					g2.drawString(e.getLabel(), (graphToScreenX(pa.x) + graphToScreenX(pb.x)) / 2,
							(graphToScreenY(pa.y) + graphToScreenY(pb.y)) / 2);
				}
			}
		}

		private void drawNode(final Graphics2D g2, final JsonCanvasNode n) {
			final int x = graphToScreenX(n.getX());
			final int y = graphToScreenY(n.getY());
			final int w = Math.max(8, (int) (n.getWidth() * zoom));
			final int h = Math.max(8, (int) (n.getHeight() * zoom));
			final boolean sel = n.getId() != null && n.getId().equals(selectedId);
			if (n.isGroup()) {
				g2.setColor(new Color(255, 255, 255, 90));
				g2.fillRoundRect(x, y, w, h, 10, 10);
				g2.setColor(colorOf(n.getColor(), new Color(180, 186, 196)));
				g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 8, new float[] { 6, 4 },
						0));
				g2.drawRoundRect(x, y, w, h, 10, 10);
				g2.setStroke(new BasicStroke(1f));
				g2.setColor(MUTED);
				g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
				g2.drawString(n.displayTitle(), x + 8, y + 18);
			}
			else {
				g2.setColor(CARD);
				g2.fillRoundRect(x, y, w, h, 10, 10);
				g2.setColor(colorOf(n.getColor(), CARD_BORDER));
				g2.setStroke(new BasicStroke(sel ? 2.2f : 1.1f));
				g2.drawRoundRect(x, y, w, h, 10, 10);
				g2.setColor(TEXT);
				g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
				g2.drawString(clip(g2, n.displayTitle(), w - 16), x + 8, y + 18);
				g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
				g2.setColor(MUTED);
				final String body = n.isText() ? nvl(n.getText()) : (n.isFile() ? nvl(n.getFile()) : nvl(n.getUrl()));
				drawWrapped(g2, body, x + 8, y + 34, w - 16, h - 42);
			}
			if (sel) {
				g2.setColor(SELECT);
				g2.fillRect(x + w - HANDLE, y + h - HANDLE, HANDLE, HANDLE);
				drawPorts(g2, n);
			}
		}

		private void drawPorts(final Graphics2D g2, final JsonCanvasNode n) {
			final String[] sides = new String[] { "top", "right", "bottom", "left" };
			g2.setColor(SELECT);
			for (int i = 0; i < sides.length; i++) {
				final Point p = portPoint(n, sides[i]);
				g2.fillOval(graphToScreenX(p.x) - 4, graphToScreenY(p.y) - 4, 8, 8);
			}
		}

		private void drawWrapped(final Graphics2D g2, final String text, final int x, final int y, final int maxW,
				final int maxH) {
			if (text == null || maxH < 12) {
				return;
			}
			final FontMetrics fm = g2.getFontMetrics();
			int cy = y;
			final String[] lines = text.replace('\r', '\n').split("\n", -1);
			for (int i = 0; i < lines.length && cy < y + maxH; i++) {
				g2.drawString(clip(g2, lines[i], maxW), x, cy);
				cy += fm.getHeight();
			}
		}

		private String clip(final Graphics2D g2, final String text, final int maxW) {
			if (text == null) {
				return "";
			}
			if (g2.getFontMetrics().stringWidth(text) <= maxW) {
				return text;
			}
			String s = text;
			while (s.length() > 1 && g2.getFontMetrics().stringWidth(s + "…") > maxW) {
				s = s.substring(0, s.length() - 1);
			}
			return s + "…";
		}

		private void drawArrow(final Graphics2D g2, final int x1, final int y1, final int x2, final int y2) {
			final double angle = Math.atan2(y2 - y1, x2 - x1);
			final int s = 8;
			final int ax = (int) (x2 - s * Math.cos(angle - 0.4));
			final int ay = (int) (y2 - s * Math.sin(angle - 0.4));
			final int bx = (int) (x2 - s * Math.cos(angle + 0.4));
			final int by = (int) (y2 - s * Math.sin(angle + 0.4));
			g2.drawLine(x2, y2, ax, ay);
			g2.drawLine(x2, y2, bx, by);
		}
	}

	private JsonCanvasNode findNodeAt(final int sx, final int sy) {
		final List<JsonCanvasNode> nodes = document.getNodes();
		for (int i = nodes.size() - 1; i >= 0; i--) {
			final JsonCanvasNode n = nodes.get(i);
			if (n.isGroup()) {
				continue;
			}
			if (contains(n, sx, sy)) {
				return n;
			}
		}
		for (int i = nodes.size() - 1; i >= 0; i--) {
			final JsonCanvasNode n = nodes.get(i);
			if (n.isGroup() && contains(n, sx, sy)) {
				return n;
			}
		}
		return null;
	}

	private boolean contains(final JsonCanvasNode n, final int sx, final int sy) {
		final int x = graphToScreenX(n.getX());
		final int y = graphToScreenY(n.getY());
		final int w = (int) (n.getWidth() * zoom);
		final int h = (int) (n.getHeight() * zoom);
		return sx >= x && sy >= y && sx <= x + w && sy <= y + h;
	}

	private boolean hitResize(final JsonCanvasNode n, final int sx, final int sy) {
		final int x = graphToScreenX(n.getX() + n.getWidth());
		final int y = graphToScreenY(n.getY() + n.getHeight());
		return Math.abs(sx - x) <= HANDLE && Math.abs(sy - y) <= HANDLE;
	}

	private String hitPort(final JsonCanvasNode n, final int sx, final int sy) {
		final String[] sides = new String[] { "top", "right", "bottom", "left" };
		for (int i = 0; i < sides.length; i++) {
			final Point p = portPoint(n, sides[i]);
			if (Math.abs(sx - graphToScreenX(p.x)) <= 10 && Math.abs(sy - graphToScreenY(p.y)) <= 10) {
				return sides[i];
			}
		}
		return null;
	}

	private Point portPoint(final JsonCanvasNode n, final String side) {
		final int cx = n.getX() + n.getWidth() / 2;
		final int cy = n.getY() + n.getHeight() / 2;
		if ("top".equals(side)) {
			return new Point(cx, n.getY());
		}
		if ("bottom".equals(side)) {
			return new Point(cx, n.getY() + n.getHeight());
		}
		if ("left".equals(side)) {
			return new Point(n.getX(), cy);
		}
		return new Point(n.getX() + n.getWidth(), cy);
	}

	private static String opposite(final String side) {
		if ("top".equals(side)) {
			return "bottom";
		}
		if ("bottom".equals(side)) {
			return "top";
		}
		if ("left".equals(side)) {
			return "right";
		}
		return "left";
	}

	private JsonCanvasEdge findEdgeAt(final int sx, final int sy) {
		final List<JsonCanvasEdge> edges = document.getEdges();
		for (int i = 0; i < edges.size(); i++) {
			final JsonCanvasEdge e = edges.get(i);
			final JsonCanvasNode a = document.findNode(e.getFromNode());
			final JsonCanvasNode b = document.findNode(e.getToNode());
			if (a == null || b == null) {
				continue;
			}
			final Point pa = portPoint(a, e.getFromSide());
			final Point pb = portPoint(b, e.getToSide());
			final double d = Line2D.ptSegDist(graphToScreenX(pa.x), graphToScreenY(pa.y), graphToScreenX(pb.x),
					graphToScreenY(pb.y), sx, sy);
			if (d <= 6) {
				return e;
			}
		}
		return null;
	}

	private void editEdgeLabel() {
		final JsonCanvasEdge e = findEdgeById(selectedEdgeId);
		if (e == null) {
			return;
		}
		final String label = JOptionPane.showInputDialog(this, "连线标签", nvl(e.getLabel()));
		if (label != null) {
			e.setLabel(label);
			setModified(true);
			surface.repaint();
		}
	}

	private JsonCanvasEdge findEdgeById(final String id) {
		if (id == null) {
			return null;
		}
		final List<JsonCanvasEdge> edges = document.getEdges();
		for (int i = 0; i < edges.size(); i++) {
			if (id.equals(edges.get(i).getId())) {
				return edges.get(i);
			}
		}
		return null;
	}

	private void showMenu(final JsonCanvasNode node, final int x, final int y) {
		final JPopupMenu menu = new JPopupMenu();
		if (node != null) {
			selectedId = node.getId();
			final JMenuItem edit = new JMenuItem(node.isFile() || node.isLink() ? "打开" : "编辑");
			edit.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					openNode(node);
				}
			});
			menu.add(edit);
			final JMenuItem color = new JMenuItem("循环颜色");
			color.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					cycleColor(node);
				}
			});
			menu.add(color);
			final JMenuItem del = new JMenuItem("删除");
			del.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					deleteSelection();
				}
			});
			menu.add(del);
		}
		else {
			final JMenuItem add = new JMenuItem("新建文本卡");
			add.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					addTextCard("新文本");
				}
			});
			menu.add(add);
		}
		menu.show(surface, x, y);
	}

	private void cycleColor(final JsonCanvasNode node) {
		final String cur = node.getColor();
		if (cur == null) {
			node.setColor("1");
		}
		else if ("6".equals(cur)) {
			node.setColor(null);
		}
		else {
			try {
				node.setColor(String.valueOf(Integer.parseInt(cur) + 1));
			}
			catch (NumberFormatException e) {
				node.setColor("1");
			}
		}
		setModified(true);
		surface.repaint();
	}

	private Point screenToGraph(final int sx, final int sy) {
		return new Point((int) Math.round((sx - panX) / zoom), (int) Math.round((sy - panY) / zoom));
	}

	private int graphToScreenX(final int gx) {
		return (int) Math.round(gx * zoom + panX);
	}

	private int graphToScreenY(final int gy) {
		return (int) Math.round(gy * zoom + panY);
	}
}
