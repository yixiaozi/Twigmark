package org.docear.plugin.core.graph;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Interactive relationship graph canvas shown in the main viewport.
 */
public class RelationshipGraphCanvas extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int NODE_RADIUS = 5;
	private static final int LABEL_NODE_LIMIT = 180;
	private static final double LABEL_ZOOM_THRESHOLD = 0.55;
	private static final int MAX_EDGES_DRAWN = 3500;
	private static final Color COLOR_NODE = new Color(72, 72, 78);
	private static final Color COLOR_NODE_SELECTED = new Color(24, 108, 186);
	private static final Color COLOR_NODE_HOVER = new Color(56, 140, 210);
	private static final Color COLOR_NODE_MATCH = new Color(210, 118, 28);
	private static final Color COLOR_NODE_DIM = new Color(200, 200, 200);
	private static final Color COLOR_EDGE = new Color(175, 178, 185);
	private static final Color COLOR_EDGE_HIGHLIGHT = new Color(120, 150, 190);
	private static final Color COLOR_LABEL = new Color(55, 55, 60);
	private static final Color COLOR_BACKGROUND = new Color(248, 249, 251);
	private static final Color COLOR_GRID = new Color(236, 238, 242);

	public interface NodeOpenListener {
		void onOpenNode(RelationshipGraphNode node);
	}

	public interface NodeContextListener {
		void onOpenNode(RelationshipGraphNode node);

		void onOpenFolder(RelationshipGraphNode node);

		void onFocusNeighbors(RelationshipGraphNode node);
	}

	public interface SelectionListener {
		void onSelectionChanged(RelationshipGraphNode node);
	}

	private RelationshipGraphIndex index;
	private String searchQuery = "";
	private double zoom = 1.0;
	private double panX;
	private double panY;
	private RelationshipGraphNode hoveredNode;
	private RelationshipGraphNode selectedNode;
	private RelationshipGraphNode draggedNode;
	private RelationshipGraphNode pressedNode;
	private int dragOffsetX;
	private int dragOffsetY;
	private int pressScreenX;
	private int pressScreenY;
	private boolean panningCanvas;
	private double panAnchorX;
	private double panAnchorY;
	private boolean layoutRunning;
	private int layoutMode = RelationshipGraphLayout.MODE_INTERACTIVE;
	private int layoutIterations;
	private int layoutMaxIterations;
	private int layoutTimerDelayMs = 40;
	private Timer layoutTimer;
	private NodeOpenListener nodeOpenListener;
	private NodeContextListener nodeContextListener;
	private SelectionListener selectionListener;
	private RelationshipGraphSpatialIndex spatialIndex = new RelationshipGraphSpatialIndex(120.0);
	private Set<String> matchedNodeKeys = new HashSet<String>();
	private boolean performanceMode;
	private boolean loading;
	private String loadingMessage = "";
	private String modeHelpHint = "\u62d6\u62fd\u7a7a\u767d\u5e73\u79fb \u00b7 \u6eda\u8f6e\u7f29\u653e \u00b7 \u53cc\u51fb\u6253\u5f00";

	public RelationshipGraphCanvas() {
		setBackground(COLOR_BACKGROUND);
		setPreferredSize(new Dimension(800, 600));
		setFocusable(true);
		installMouseHandlers();
		installKeyboardHandlers();
		startLayoutTimer();
	}

	public void setNodeOpenListener(final NodeOpenListener listener) {
		this.nodeOpenListener = listener;
	}

	public void setNodeContextListener(final NodeContextListener listener) {
		this.nodeContextListener = listener;
	}

	public void setSelectionListener(final SelectionListener listener) {
		this.selectionListener = listener;
	}

	public void setModeHelpHint(final String hint) {
		this.modeHelpHint = hint != null ? hint : "";
		repaint();
	}

	public RelationshipGraphNode getSelectedNode() {
		return selectedNode;
	}

	public void setGraphIndex(final RelationshipGraphIndex newIndex) {
		setGraphIndex(newIndex, false);
	}

	public void setGraphIndex(final RelationshipGraphIndex newIndex, final boolean preserveView) {
		setGraphIndex(newIndex, preserveView, false);
	}

	/** Layout positions were already computed on a background thread. */
	public void setPreparedGraphIndex(final RelationshipGraphIndex newIndex, final boolean preserveView) {
		setGraphIndex(newIndex, preserveView, true);
	}

	private void setGraphIndex(final RelationshipGraphIndex newIndex, final boolean preserveView,
	        final boolean layoutPrecomputed) {
		final double savedZoom = zoom;
		final double savedPanX = panX;
		final double savedPanY = panY;
		final String savedSelectedKey = selectedNode != null ? selectedNode.getPathKey() : null;

		this.index = newIndex;
		this.hoveredNode = null;
		this.draggedNode = null;
		this.selectedNode = null;
		rebuildSearchMatches();
		if (index != null && index.getNodeCount() > 0) {
			final int w = Math.max(getWidth(), 400);
			final int h = Math.max(getHeight(), 300);
			layoutMode = RelationshipGraphLayout.chooseMode(index.getNodeCount());
			layoutMaxIterations = RelationshipGraphLayout.maxIterations(layoutMode);
			layoutIterations = 0;
			layoutRunning = layoutMode != RelationshipGraphLayout.MODE_STATIC;
			layoutTimerDelayMs = layoutMode == RelationshipGraphLayout.MODE_BATCH ? 120 : 40;
			if (layoutTimer != null) {
				layoutTimer.setDelay(layoutTimerDelayMs);
			}
			performanceMode = index.getNodeCount() > 250;
			if (!layoutPrecomputed) {
				RelationshipGraphLayout.initializePositions(index, w / zoom, h / zoom);
				spatialIndex.rebuild(index);
			}
			else {
				scheduleSpatialIndexRebuild();
			}
		}
		else {
			layoutRunning = false;
			performanceMode = false;
		}
		if (preserveView) {
			zoom = savedZoom;
			panX = savedPanX;
			panY = savedPanY;
			restoreSelection(savedSelectedKey);
		}
		else {
			resetView();
		}
		notifySelectionChanged();
		if (!layoutPrecomputed) {
			repaint();
		}
	}

	private void scheduleSpatialIndexRebuild() {
		final javax.swing.Timer once = new javax.swing.Timer(50, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				((javax.swing.Timer) e.getSource()).stop();
				if (index != null) {
					spatialIndex.rebuild(index);
					repaint();
				}
			}
		});
		once.setRepeats(false);
		once.start();
	}

	private void restoreSelection(final String key) {
		if (key == null || index == null) {
			return;
		}
		final List<RelationshipGraphNode> nodes = index.getNodes();
		for (int i = 0; i < nodes.size(); i++) {
			final RelationshipGraphNode node = nodes.get(i);
			if (key.equals(node.getPathKey())) {
				selectedNode = node;
				break;
			}
		}
	}

	public void setSearchQuery(final String query) {
		this.searchQuery = query == null ? "" : query.trim().toLowerCase(Locale.ENGLISH);
		rebuildSearchMatches();
		repaint();
	}

	public void focusOnMatches() {
		if (index == null || matchedNodeKeys.isEmpty()) {
			return;
		}
		focusOnNodes(matchedNodeKeys);
	}

	public void focusOnNode(final RelationshipGraphNode node) {
		if (node == null) {
			return;
		}
		final Set<String> keys = new HashSet<String>();
		keys.add(node.getPathKey());
		focusOnNodes(keys);
	}

	private void focusOnNodes(final Set<String> keys) {
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		final List<RelationshipGraphNode> nodes = index.getNodes();
		for (int i = 0; i < nodes.size(); i++) {
			final RelationshipGraphNode node = nodes.get(i);
			if (!keys.contains(node.getPathKey())) {
				continue;
			}
			minX = Math.min(minX, node.getX());
			minY = Math.min(minY, node.getY());
			maxX = Math.max(maxX, node.getX());
			maxY = Math.max(maxY, node.getY());
		}
		if (minX == Double.MAX_VALUE) {
			return;
		}
		final double padding = 80.0;
		final double graphW = Math.max(40.0, maxX - minX + padding * 2);
		final double graphH = Math.max(40.0, maxY - minY + padding * 2);
		final int viewW = Math.max(getWidth(), 400);
		final int viewH = Math.max(getHeight(), 300);
		zoom = Math.min(2.5, Math.min(viewW / graphW, viewH / graphH));
		final double centerX = (minX + maxX) / 2.0;
		final double centerY = (minY + maxY) / 2.0;
		panX = viewW / 2.0 - centerX * zoom;
		panY = viewH / 2.0 - centerY * zoom;
		repaint();
	}

	public void refreshLayout() {
		if (index != null && index.getNodeCount() > 0) {
			final int w = Math.max(getWidth(), 400);
			final int h = Math.max(getHeight(), 300);
			layoutIterations = 0;
			layoutRunning = layoutMode != RelationshipGraphLayout.MODE_STATIC;
			RelationshipGraphLayout.initializePositions(index, w / zoom, h / zoom);
			spatialIndex.rebuild(index);
		}
		repaint();
	}

	public void resetView() {
		zoom = 1.0;
		panX = 20;
		panY = 20;
		repaint();
	}

	public void stopLayout() {
		layoutRunning = false;
	}

	public void setLoading(final boolean loading, final String message) {
		this.loading = loading;
		this.loadingMessage = message == null ? "" : message;
		repaint();
	}

	private void rebuildSearchMatches() {
		matchedNodeKeys.clear();
		if (index == null || searchQuery.length() == 0) {
			return;
		}
		final List<RelationshipGraphNode> nodes = index.getNodes();
		for (int i = 0; i < nodes.size(); i++) {
			final RelationshipGraphNode node = nodes.get(i);
			if (matchesSearch(node)) {
				matchedNodeKeys.add(node.getPathKey());
			}
		}
	}

	private void startLayoutTimer() {
		layoutTimer = new Timer(layoutTimerDelayMs, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				if (!layoutRunning || index == null || draggedNode != null || panningCanvas) {
					return;
				}
				if (layoutIterations >= layoutMaxIterations) {
					layoutRunning = false;
					spatialIndex.rebuild(index);
					return;
				}
				final int w = Math.max(getWidth(), 400);
				final int h = Math.max(getHeight(), 300);
				RelationshipGraphLayout.iterate(index, w / zoom, h / zoom, layoutMode);
				layoutIterations++;
				if (layoutIterations % 5 == 0 || layoutIterations >= layoutMaxIterations) {
					spatialIndex.rebuild(index);
				}
				repaint();
			}
		});
		layoutTimer.start();
	}

	private void installKeyboardHandlers() {
		addKeyListener(new KeyAdapter() {
			public void keyPressed(final KeyEvent e) {
				if (index == null || index.getNodeCount() == 0) {
					return;
				}
				switch (e.getKeyCode()) {
				case KeyEvent.VK_LEFT:
					selectNodeInDirection(-1, 0);
					e.consume();
					break;
				case KeyEvent.VK_RIGHT:
					selectNodeInDirection(1, 0);
					e.consume();
					break;
				case KeyEvent.VK_UP:
					selectNodeInDirection(0, -1);
					e.consume();
					break;
				case KeyEvent.VK_DOWN:
					selectNodeInDirection(0, 1);
					e.consume();
					break;
				case KeyEvent.VK_ESCAPE:
					setSelectedNode(null);
					e.consume();
					break;
				default:
					break;
				}
			}
		});
	}

	private void selectNodeInDirection(final int dirX, final int dirY) {
		RelationshipGraphNode origin = selectedNode != null ? selectedNode : hoveredNode;
		if (origin == null) {
			final List<RelationshipGraphNode> nodes = index.getNodes();
			if (!nodes.isEmpty()) {
				setSelectedNode(nodes.get(0));
			}
			return;
		}
		RelationshipGraphNode best = null;
		double bestScore = -1.0;
		final List<RelationshipGraphNode> nodes = index.getNodes();
		for (int i = 0; i < nodes.size(); i++) {
			final RelationshipGraphNode candidate = nodes.get(i);
			if (candidate == origin) {
				continue;
			}
			final double dx = candidate.getX() - origin.getX();
			final double dy = candidate.getY() - origin.getY();
			final double dot = dx * dirX + dy * dirY;
			if (dot <= 0.0) {
				continue;
			}
			final double distSq = dx * dx + dy * dy;
			if (distSq < 1.0) {
				continue;
			}
			final double score = dot / Math.sqrt(distSq);
			if (score > bestScore) {
				bestScore = score;
				best = candidate;
			}
		}
		if (best != null) {
			setSelectedNode(best);
			focusOnNode(best);
		}
	}

	private void setSelectedNode(final RelationshipGraphNode node) {
		selectedNode = node;
		notifySelectionChanged();
		repaint();
	}

	/** Select a node from the sidebar result list. */
	public void selectAndFocusNode(final RelationshipGraphNode node) {
		setSelectedNode(node);
		if (node != null) {
			focusOnNode(node);
		}
	}

	private void notifySelectionChanged() {
		if (selectionListener != null) {
			selectionListener.onSelectionChanged(selectedNode);
		}
	}

	private void installMouseHandlers() {
		final MouseAdapter adapter = new MouseAdapter() {
			public void mouseMoved(final MouseEvent e) {
				if (!panningCanvas) {
					updateHoveredNode(e);
				}
			}

			public void mouseDragged(final MouseEvent e) {
				if (panningCanvas) {
					panX = panAnchorX + (e.getX() - pressScreenX);
					panY = panAnchorY + (e.getY() - pressScreenY);
					repaint();
					return;
				}
				if (draggedNode != null) {
					final Point2D graphPoint = screenToGraph(e.getX(), e.getY());
					draggedNode.setX(graphPoint.getX() - dragOffsetX);
					draggedNode.setY(graphPoint.getY() - dragOffsetY);
					draggedNode.setVx(0);
					draggedNode.setVy(0);
					layoutRunning = false;
					spatialIndex.rebuild(index);
					repaint();
					return;
				}
				updateHoveredNode(e);
			}

			public void mouseReleased(final MouseEvent e) {
				if (panningCanvas) {
					panningCanvas = false;
					setCursor(Cursor.getDefaultCursor());
				}
				draggedNode = null;
				pressedNode = null;
				updateHoveredNode(e);
			}

			public void mousePressed(final MouseEvent e) {
				requestFocusInWindow();
				pressScreenX = e.getX();
				pressScreenY = e.getY();
				pressedNode = findNodeAt(e.getX(), e.getY());
				if (pressedNode != null) {
					setSelectedNode(pressedNode);
					if (SwingUtilities.isLeftMouseButton(e)) {
						final Point2D graphPoint = screenToGraph(e.getX(), e.getY());
						draggedNode = pressedNode;
						dragOffsetX = (int) (graphPoint.getX() - pressedNode.getX());
						dragOffsetY = (int) (graphPoint.getY() - pressedNode.getY());
						setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
					}
					return;
				}
				if (SwingUtilities.isLeftMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) {
					panningCanvas = true;
					panAnchorX = panX;
					panAnchorY = panY;
					setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
				}
			}

			public void mouseClicked(final MouseEvent e) {
				if (SwingUtilities.isRightMouseButton(e)) {
					final RelationshipGraphNode node = findNodeAt(e.getX(), e.getY());
					if (node != null) {
						setSelectedNode(node);
					}
					showContextMenu(node, e.getX(), e.getY());
					return;
				}
				if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2) {
					return;
				}
				final RelationshipGraphNode node = findNodeAt(e.getX(), e.getY());
				if (node != null && nodeOpenListener != null) {
					nodeOpenListener.onOpenNode(node);
				}
			}

			public void mouseWheelMoved(final MouseWheelEvent e) {
				final double oldZoom = zoom;
				final double factor = e.getWheelRotation() < 0 ? 1.1 : 0.9;
				zoom = Math.max(0.15, Math.min(4.0, zoom * factor));
				final double scale = zoom / oldZoom;
				panX = e.getX() - (e.getX() - panX) * scale;
				panY = e.getY() - (e.getY() - panY) * scale;
				repaint();
			}
		};
		addMouseListener(adapter);
		addMouseMotionListener(adapter);
		addMouseWheelListener(adapter);
	}

	private void showContextMenu(final RelationshipGraphNode node, final int x, final int y) {
		if (nodeContextListener == null) {
			return;
		}
		final JPopupMenu menu = new JPopupMenu();
		if (node != null) {
			if (node.isTagNode()) {
				final JMenuItem focusNeighbors = new JMenuItem("\u805a\u7126\u5173\u8054");
				focusNeighbors.addActionListener(new java.awt.event.ActionListener() {
					public void actionPerformed(final java.awt.event.ActionEvent e) {
						nodeContextListener.onFocusNeighbors(node);
					}
				});
				menu.add(focusNeighbors);
			}
			else {
				final JMenuItem openMap = new JMenuItem(node.isMapNode() ? "\u6253\u5f00\u8282\u70b9" : "\u6253\u5f00\u5bfc\u56fe");
				openMap.addActionListener(new java.awt.event.ActionListener() {
					public void actionPerformed(final java.awt.event.ActionEvent e) {
						nodeContextListener.onOpenNode(node);
					}
				});
				menu.add(openMap);
				final JMenuItem focusNeighbors = new JMenuItem("\u805a\u7126\u5173\u8054");
				focusNeighbors.addActionListener(new java.awt.event.ActionListener() {
					public void actionPerformed(final java.awt.event.ActionEvent e) {
						nodeContextListener.onFocusNeighbors(node);
					}
				});
				menu.add(focusNeighbors);
				if (node.getFile() != null) {
					final JMenuItem openFolder = new JMenuItem("\u6253\u5f00\u6240\u5728\u6587\u4ef6\u5939");
					openFolder.addActionListener(new java.awt.event.ActionListener() {
						public void actionPerformed(final java.awt.event.ActionEvent e) {
							nodeContextListener.onOpenFolder(node);
						}
					});
					menu.add(openFolder);
				}
			}
		}
		menu.show(this, x, y);
	}

	private void updateHoveredNode(final MouseEvent e) {
		final RelationshipGraphNode node = findNodeAt(e.getX(), e.getY());
		if (node != hoveredNode) {
			hoveredNode = node;
			setCursor(node != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			repaint();
		}
	}

	private RelationshipGraphNode findNodeAt(final int screenX, final int screenY) {
		if (index == null) {
			return null;
		}
		final Point2D graphPoint = screenToGraph(screenX, screenY);
		final double hitRadius = (NODE_RADIUS + 8) / Math.max(0.4, zoom);
		if (performanceMode) {
			return spatialIndex.findNearest(graphPoint.getX(), graphPoint.getY(), hitRadius);
		}
		final List<RelationshipGraphNode> nodes = index.getNodes();
		for (int i = nodes.size() - 1; i >= 0; i--) {
			final RelationshipGraphNode node = nodes.get(i);
			final double dx = graphPoint.getX() - node.getX();
			final double dy = graphPoint.getY() - node.getY();
			if (dx * dx + dy * dy <= hitRadius * hitRadius) {
				return node;
			}
		}
		return null;
	}

	private Point2D screenToGraph(final int screenX, final int screenY) {
		return new Point2D.Double((screenX - panX) / zoom, (screenY - panY) / zoom);
	}

	private Point2D graphToScreen(final double graphX, final double graphY) {
		return new Point2D.Double(graphX * zoom + panX, graphY * zoom + panY);
	}

	private boolean matchesSearch(final RelationshipGraphNode node) {
		if (searchQuery.length() == 0) {
			return false;
		}
		return node.getLabel().toLowerCase(Locale.ENGLISH).indexOf(searchQuery) >= 0
		        || node.getMapLabel().toLowerCase(Locale.ENGLISH).indexOf(searchQuery) >= 0
		        || node.getPathKey().toLowerCase(Locale.ENGLISH).indexOf(searchQuery) >= 0;
	}

	private boolean shouldDrawLabels() {
		if (index == null) {
			return false;
		}
		if (index.getNodeCount() <= LABEL_NODE_LIMIT) {
			return true;
		}
		if (performanceMode) {
			return zoom >= 1.5;
		}
		return zoom >= LABEL_ZOOM_THRESHOLD;
	}

	private Rectangle getVisibleGraphBounds() {
		final Point2D topLeft = screenToGraph(0, 0);
		final Point2D bottomRight = screenToGraph(getWidth(), getHeight());
		final int margin = 40;
		return new Rectangle((int) topLeft.getX() - margin, (int) topLeft.getY() - margin,
		        (int) (bottomRight.getX() - topLeft.getX()) + margin * 2,
		        (int) (bottomRight.getY() - topLeft.getY()) + margin * 2);
	}

	private boolean isVisibleInGraph(final RelationshipGraphNode node, final Rectangle visible) {
		return visible.contains((int) node.getX(), (int) node.getY());
	}

	private boolean isEdgeHighlighted(final RelationshipGraphEdge edge) {
		if (selectedNode == null) {
			return false;
		}
		final String key = selectedNode.getPathKey();
		return key.equals(edge.getSource().getPathKey()) || key.equals(edge.getTarget().getPathKey());
	}

	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			if (!performanceMode) {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			}
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(COLOR_BACKGROUND);
			g2.fillRect(0, 0, getWidth(), getHeight());
			drawGrid(g2);

			if (loading) {
				drawLoadingOverlay(g2);
				drawHelpHint(g2);
				return;
			}

			if (index == null || index.getNodeCount() == 0) {
				drawEmptyHint(g2);
				drawHelpHint(g2);
				return;
			}

			final boolean drawLabels = shouldDrawLabels();
			final boolean dimNonMatches = searchQuery.length() > 0 && !matchedNodeKeys.isEmpty();
			final Rectangle visible = performanceMode ? getVisibleGraphBounds() : null;
			final List<RelationshipGraphEdge> edges = index.getEdges();
			g2.setStroke(new BasicStroke(1f));
			final int edgeLimit = edges.size() > MAX_EDGES_DRAWN ? MAX_EDGES_DRAWN : edges.size();
			int edgesDrawn = 0;
			for (int i = 0; i < edges.size() && edgesDrawn < edgeLimit; i++) {
				final RelationshipGraphEdge edge = edges.get(i);
				if (performanceMode) {
					if (!isVisibleInGraph(edge.getSource(), visible) && !isVisibleInGraph(edge.getTarget(), visible)) {
						continue;
					}
				}
				edgesDrawn++;
				if (isEdgeHighlighted(edge)) {
					g2.setColor(COLOR_EDGE_HIGHLIGHT);
					g2.setStroke(new BasicStroke(1.6f));
				}
				else if (dimNonMatches) {
					final boolean edgeMatch = matchedNodeKeys.contains(edge.getSource().getPathKey())
					        || matchedNodeKeys.contains(edge.getTarget().getPathKey());
					g2.setColor(edgeMatch ? COLOR_EDGE : new Color(235, 235, 235));
					g2.setStroke(new BasicStroke(1f));
				}
				else {
					g2.setColor(COLOR_EDGE);
					g2.setStroke(new BasicStroke(1f));
				}
				final Point2D from = graphToScreen(edge.getSource().getX(), edge.getSource().getY());
				final Point2D to = graphToScreen(edge.getTarget().getX(), edge.getTarget().getY());
				g2.drawLine((int) from.getX(), (int) from.getY(), (int) to.getX(), (int) to.getY());
			}

			final List<RelationshipGraphNode> nodes = index.getNodes();
			final Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, performanceMode ? 10 : 11);
			g2.setFont(labelFont);
			final FontMetrics fm = g2.getFontMetrics();
			for (int i = 0; i < nodes.size(); i++) {
				final RelationshipGraphNode node = nodes.get(i);
				if (performanceMode && !isVisibleInGraph(node, visible)) {
					continue;
				}
				final Point2D center = graphToScreen(node.getX(), node.getY());
				final int cx = (int) center.getX();
				final int cy = (int) center.getY();
				if (cx < -20 || cy < -20 || cx > getWidth() + 20 || cy > getHeight() + 20) {
					continue;
				}
				final boolean match = matchedNodeKeys.contains(node.getPathKey());
				final boolean selected = node == selectedNode;
				final boolean hover = node == hoveredNode;
				final boolean tagNode = node.isTagNode();
				final int baseRadius = tagNode ? NODE_RADIUS + 3 : NODE_RADIUS;
				final int r = Math.max(2, (int) (baseRadius * zoom));
				if (selected) {
					g2.setColor(new Color(180, 210, 240, 120));
					g2.fillOval(cx - r - 4, cy - r - 4, (r + 4) * 2, (r + 4) * 2);
					g2.setColor(COLOR_NODE_SELECTED);
				}
				else if (match) {
					g2.setColor(COLOR_NODE_MATCH);
				}
				else if (hover) {
					g2.setColor(COLOR_NODE_HOVER);
				}
				else if (dimNonMatches) {
					g2.setColor(COLOR_NODE_DIM);
				}
				else if (tagNode) {
					g2.setColor(resolveTagColor(node));
				}
				else {
					g2.setColor(COLOR_NODE);
				}
				g2.fillOval(cx - r, cy - r, r * 2, r * 2);

				if (drawLabels && (selected || match || hover || tagNode || (!performanceMode && !dimNonMatches))) {
					final String label = tagNode ? ("\u3010" + node.getLabel() + "\u3011") : node.getLabel();
					final int labelWidth = fm.stringWidth(label);
					g2.setColor(selected ? COLOR_NODE_SELECTED : (match ? COLOR_NODE_MATCH : COLOR_LABEL));
					g2.drawString(label, cx - labelWidth / 2, cy + r + fm.getAscent() + 2);
					if (node.isMapNode() && (selected || hover || zoom >= 0.7)) {
						final String sub = node.getMapLabel();
						final Font subFont = labelFont.deriveFont(Font.PLAIN, Math.max(9f, labelFont.getSize2D() - 1f));
						g2.setFont(subFont);
						final FontMetrics subFm = g2.getFontMetrics();
						final int subWidth = subFm.stringWidth(sub);
						g2.setColor(new Color(130, 130, 135));
						g2.drawString(sub, cx - subWidth / 2, cy + r + fm.getAscent() + subFm.getAscent() + 4);
						g2.setFont(labelFont);
					}
				}
			}

			if (performanceMode && layoutRunning) {
				g2.setColor(new Color(130, 130, 130));
				g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
				g2.drawString("\u5e03\u5c40\u4e2d\u2026 " + layoutIterations + "/" + layoutMaxIterations, 12, 20);
			}
			if (edges.size() > MAX_EDGES_DRAWN) {
				g2.setColor(new Color(130, 130, 130));
				g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
				g2.drawString("\u8fb9\u8f83\u591a\uff0c\u5df2\u9650\u5236\u663e\u793a\uff0c\u653e\u5927\u67e5\u770b", 12, 36);
			}
			drawHelpHint(g2);
		}
		finally {
			g2.dispose();
		}
	}

	private void drawGrid(final Graphics2D g2) {
		g2.setColor(COLOR_GRID);
		final int step = 48;
		for (int x = (int) (panX % step); x < getWidth(); x += step) {
			g2.drawLine(x, 0, x, getHeight());
		}
		for (int y = (int) (panY % step); y < getHeight(); y += step) {
			g2.drawLine(0, y, getWidth(), y);
		}
	}

	private void drawHelpHint(final Graphics2D g2) {
		g2.setColor(new Color(130, 135, 145));
		g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
		final String hint = modeHelpHint != null && modeHelpHint.length() > 0
		        ? modeHelpHint
		        : "\u62d6\u62fd\u7a7a\u767d\u5e73\u79fb \u00b7 \u6eda\u8f6e\u7f29\u653e \u00b7 \u53cc\u51fb\u6253\u5f00";
		g2.drawString(hint, 12, getHeight() - 10);
	}

	private static Color resolveTagColor(final RelationshipGraphNode node) {
		try {
			final Color color = org.freeplane.plugin.workspace.features.nodepins.TagColorStore.getInstance()
			        .getColor(node.getTagName());
			if (color != null) {
				return color;
			}
		}
		catch (final Throwable ignore) {
		}
		return new Color(0xCE93D8);
	}

	private void drawLoadingOverlay(final Graphics2D g2) {
		g2.setColor(new Color(248, 249, 251, 230));
		g2.fillRect(0, 0, getWidth(), getHeight());
		g2.setColor(new Color(80, 80, 85));
		g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
		final String message = loadingMessage.length() > 0 ? loadingMessage : "\u626b\u63cf\u5173\u8054\u4e2d\u2026";
		final FontMetrics fm = g2.getFontMetrics();
		final int x = Math.max(12, (getWidth() - fm.stringWidth(message)) / 2);
		final int y = getHeight() / 2;
		g2.drawString(message, x, y);
		if (message.indexOf("\u626b\u63cf") >= 0 || message.indexOf("\u9996\u6b21") >= 0) {
			g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
			g2.setColor(new Color(120, 120, 125));
			g2.drawString("\u5927\u91cf\u5bfc\u56fe\u9996\u6b21\u626b\u63cf\u53ef\u80fd\u9700\u8981\u4e00\u5206\u949f", 12, y + 28);
		}
	}

	private void drawEmptyHint(final Graphics2D g2) {
		g2.setColor(new Color(120, 120, 120));
		g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		final String hint;
		if (searchQuery.length() > 0) {
			hint = "\u672a\u627e\u5230\u5339\u914d\u9879\uff0c\u8bd5\u8bd5\u6e05\u9664\u641c\u7d22\u6216\u6362\u5206\u7ec4";
		}
		else if (index != null && index.getGraphMode() == RelationshipGraphScanner.MODE_TAGS) {
			hint = "\u5f53\u524d\u5206\u7ec4\u4e0b\u6ca1\u6709\u6807\u7b7e\u5173\u8054\uff0c\u8bd5\u8bd5\u300c\u5168\u90e8\u300d\u6216\u6362\u5206\u7ec4";
		}
		else if (index != null && index.getGraphMode() == RelationshipGraphScanner.MODE_FAVORITES) {
			hint = "\u6682\u65e0\u6536\u85cf\u6807\u7b7e\u5173\u8054\uff1a\u5148\u7ed9\u6536\u85cf\u6253\u6807\u7b7e\uff0c\u6216\u9009\u300c\u5168\u90e8\u300d";
		}
		else {
			hint = "\u6682\u65e0\u53ef\u663e\u793a\u7684\u5173\u8054\uff0c\u70b9\u5de6\u4fa7\u300c\u5237\u65b0\u300d\u6216\u52fe\u9009\u300c\u663e\u793a\u65e0\u8fde\u63a5\u9879\u300d";
		}
		final FontMetrics fm = g2.getFontMetrics();
		final int x = (getWidth() - fm.stringWidth(hint)) / 2;
		final int y = getHeight() / 2;
		g2.drawString(hint, x, y);
	}
}
