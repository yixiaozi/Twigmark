package org.docear.plugin.core.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.view.swing.map.MapView;
import org.freeplane.view.swing.map.NodeView;

/**
 * Bottom-right mind-map overview: simplified node blocks, viewport frame,
 * click/drag to navigate, and node counts.
 */
public class MapMinimapPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	public interface LayoutListener {
		void onPanelLayoutChanged();
	}

	static final String PROP_VISIBLE = "docear.map_minimap.visible";
	static final String PROP_COLLAPSED = "docear.map_minimap.collapsed";

	private static final int PANEL_W = 196;
	private static final int PANEL_H = 148;
	private static final int CANVAS_PAD = 6;
	private static final int HEADER_H = 22;
	private static final int ARC = 12;

	private static final Color BG = new Color(252, 252, 253, 235);
	private static final Color BORDER = new Color(0xC8, 0xCD, 0xD4);
	private static final Color NODE_FILL = new Color(0x5B, 0x8D, 0xEF, 160);
	private static final Color NODE_SELECTED = new Color(0xE8, 0x5D, 0x4C, 200);
	private static final Color VIEWPORT_STROKE = new Color(0xE8, 0x5D, 0x4C);
	private static final Color HEADER_FG = new Color(0x3D, 0x45, 0x55);

	private LayoutListener layoutListener;
	private final JLabel countLabel;
	private final JButton toggleButton;
	private final Canvas canvas;
	private boolean collapsed;
	private final Timer refreshTimer;

	private Rectangle mapBounds = new Rectangle();
	private final List nodeRects = new ArrayList();
	private Rectangle selectedRect;
	private Rectangle viewportRect = new Rectangle();
	private double scale = 1.0;
	private int offsetX;
	private int offsetY;
	private int totalNodes;
	private int visibleNodes;
	private boolean draggingViewport;

	public MapMinimapPanel() {
		setOpaque(false);
		setLayout(new BorderLayout());
		collapsed = ResourceController.getResourceController().getBooleanProperty(PROP_COLLAPSED);

		countLabel = new JLabel(" ");
		countLabel.setFont(countLabel.getFont().deriveFont(Font.PLAIN, 11f));
		countLabel.setForeground(HEADER_FG);

		toggleButton = new JButton(collapsed ? "□" : "–");
		toggleButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
		toggleButton.setFocusable(false);
		toggleButton.setBorderPainted(false);
		toggleButton.setContentAreaFilled(false);
		toggleButton.setForeground(HEADER_FG);
		toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		toggleButton.setToolTipText(TextUtils.getText("docear.map_minimap.toggle"));
		toggleButton.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				setCollapsed(!collapsed);
			}
		});

		final JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(2, 8, 0, 4));
		header.add(countLabel, BorderLayout.CENTER);
		header.add(toggleButton, BorderLayout.EAST);

		canvas = new Canvas();
		add(header, BorderLayout.NORTH);
		add(canvas, BorderLayout.CENTER);

		refreshTimer = new Timer(180, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				refreshFromMap();
			}
		});
		refreshTimer.setRepeats(false);

		applyCollapsedUi();
	}

	public void setLayoutListener(final LayoutListener listener) {
		this.layoutListener = listener;
	}

	public void requestRefresh() {
		if (!refreshTimer.isRunning()) {
			refreshTimer.start();
		}
		else {
			refreshTimer.restart();
		}
	}

	public void setCollapsed(final boolean value) {
		if (collapsed == value) {
			return;
		}
		collapsed = value;
		ResourceController.getResourceController().setProperty(PROP_COLLAPSED, Boolean.toString(collapsed));
		applyCollapsedUi();
		if (layoutListener != null) {
			layoutListener.onPanelLayoutChanged();
		}
	}

	private void applyCollapsedUi() {
		toggleButton.setText(collapsed ? "□" : "–");
		canvas.setVisible(!collapsed);
		revalidate();
	}

	@Override
	public Dimension getPreferredSize() {
		if (collapsed) {
			return new Dimension(PANEL_W, HEADER_H + 6);
		}
		return new Dimension(PANEL_W, PANEL_H);
	}

	@Override
	protected void paintComponent(final Graphics g) {
		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(BG);
		g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
		g2.setColor(BORDER);
		g2.setStroke(new BasicStroke(1f));
		g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
		g2.dispose();
		super.paintComponent(g);
	}

	void refreshFromMap() {
		if (!isShowing() || !MapOverlayVisibility.isMindMapCanvasShowing()) {
			return;
		}
		final Controller controller = Controller.getCurrentController();
		if (controller == null || controller.getMapViewManager() == null) {
			return;
		}
		final JScrollPane scroll = controller.getMapViewManager().getScrollPane();
		if (scroll == null) {
			return;
		}
		final JViewport viewport = scroll.getViewport();
		if (viewport == null || !(viewport.getView() instanceof MapView)) {
			return;
		}
		final MapView mapView = (MapView) viewport.getView();
		final NodeView root = mapView.getRoot();
		if (root == null) {
			return;
		}

		nodeRects.clear();
		selectedRect = null;
		mapBounds = new Rectangle();
		visibleNodes = 0;
		collectNodeRects(mapView, root, mapView.getSelected());
		totalNodes = countModelNodes(mapView.getModel().getRootNode());

		if (mapBounds.isEmpty()) {
			mapBounds = mapView.getInnerBounds();
		}
		else {
			mapBounds.grow(24, 24);
		}

		viewportRect = new Rectangle(viewport.getViewRect());
		updateScale(canvas.getWidth(), canvas.getHeight());
		updateCountLabel();
		canvas.repaint();
	}

	private void collectNodeRects(final MapView mapView, final NodeView nodeView, final NodeView selected) {
		if (nodeView == null) {
			return;
		}
		if (nodeView.isContentVisible()) {
			visibleNodes++;
			final JComponent content = nodeView.getContent();
			if (content != null && content.getWidth() > 0 && content.getHeight() > 0) {
				final Point loc = mapView.getNodeContentLocation(nodeView);
				final Rectangle r = new Rectangle(loc.x, loc.y, Math.max(4, content.getWidth()), Math.max(3,
				        content.getHeight()));
				nodeRects.add(r);
				if (mapBounds.isEmpty()) {
					mapBounds.setBounds(r);
				}
				else {
					mapBounds.add(r);
				}
				if (nodeView == selected) {
					selectedRect = r;
				}
			}
		}
		final List children = nodeView.getChildrenViews();
		for (int i = 0; i < children.size(); i++) {
			collectNodeRects(mapView, (NodeView) children.get(i), selected);
		}
	}

	private static int countModelNodes(final NodeModel node) {
		if (node == null) {
			return 0;
		}
		int count = 1;
		final int n = node.getChildCount();
		for (int i = 0; i < n; i++) {
			count += countModelNodes((NodeModel) node.getChildAt(i));
		}
		return count;
	}

	private void updateScale(final int canvasW, final int canvasH) {
		final int w = Math.max(1, canvasW - CANVAS_PAD * 2);
		final int h = Math.max(1, canvasH - CANVAS_PAD * 2);
		if (mapBounds.width <= 0 || mapBounds.height <= 0) {
			scale = 1.0;
			offsetX = CANVAS_PAD;
			offsetY = CANVAS_PAD;
			return;
		}
		scale = Math.min((double) w / mapBounds.width, (double) h / mapBounds.height);
		final int drawnW = (int) Math.round(mapBounds.width * scale);
		final int drawnH = (int) Math.round(mapBounds.height * scale);
		offsetX = CANVAS_PAD + (w - drawnW) / 2;
		offsetY = CANVAS_PAD + (h - drawnH) / 2;
	}

	private void updateCountLabel() {
		final String title = TextUtils.getText("docear.map_minimap.title");
		if (visibleNodes == totalNodes || totalNodes == 0) {
			countLabel.setText(title + " · " + totalNodes);
		}
		else {
			countLabel.setText(title + " · " + visibleNodes + "/" + totalNodes);
		}
		countLabel.setToolTipText(TextUtils.format("docear.map_minimap.count_tip",
		        Integer.valueOf(visibleNodes), Integer.valueOf(totalNodes)));
	}

	private Point mapToCanvas(final int mapX, final int mapY) {
		final int x = offsetX + (int) Math.round((mapX - mapBounds.x) * scale);
		final int y = offsetY + (int) Math.round((mapY - mapBounds.y) * scale);
		return new Point(x, y);
	}

	private Point canvasToMap(final int canvasX, final int canvasY) {
		if (scale <= 0) {
			return new Point(mapBounds.x, mapBounds.y);
		}
		final int mapX = mapBounds.x + (int) Math.round((canvasX - offsetX) / scale);
		final int mapY = mapBounds.y + (int) Math.round((canvasY - offsetY) / scale);
		return new Point(mapX, mapY);
	}

	private void navigateToMapPoint(final Point mapPoint) {
		final Controller controller = Controller.getCurrentController();
		if (controller == null || controller.getMapViewManager() == null) {
			return;
		}
		final JScrollPane scroll = controller.getMapViewManager().getScrollPane();
		if (scroll == null) {
			return;
		}
		final JViewport viewport = scroll.getViewport();
		if (viewport == null) {
			return;
		}
		final Rectangle view = viewport.getViewRect();
		int x = mapPoint.x - view.width / 2;
		int y = mapPoint.y - view.height / 2;
		final Dimension size = viewport.getViewSize();
		x = Math.max(0, Math.min(x, Math.max(0, size.width - view.width)));
		y = Math.max(0, Math.min(y, Math.max(0, size.height - view.height)));
		viewport.setViewPosition(new Point(x, y));
		requestRefresh();
	}

	private final class Canvas extends JPanel {
		private static final long serialVersionUID = 1L;

		Canvas() {
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				public void mousePressed(final MouseEvent e) {
					if (!SwingUtilities.isLeftMouseButton(e)) {
						return;
					}
					draggingViewport = true;
					navigateToMapPoint(canvasToMap(e.getX(), e.getY()));
				}

				public void mouseReleased(final MouseEvent e) {
					draggingViewport = false;
				}
			});
			addMouseMotionListener(new MouseMotionAdapter() {
				public void mouseDragged(final MouseEvent e) {
					if (draggingViewport) {
						navigateToMapPoint(canvasToMap(e.getX(), e.getY()));
					}
				}
			});
		}

		@Override
		protected void paintComponent(final Graphics g) {
			super.paintComponent(g);
			updateScale(getWidth(), getHeight());
			final Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g2.setColor(new Color(0xF0, 0xF2, 0xF5));
			g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 8, 8);

			for (int i = 0; i < nodeRects.size(); i++) {
				final Rectangle r = (Rectangle) nodeRects.get(i);
				final Point p = mapToCanvas(r.x, r.y);
				final int w = Math.max(2, (int) Math.round(r.width * scale));
				final int h = Math.max(2, (int) Math.round(r.height * scale));
				final boolean selected = selectedRect != null && selectedRect.equals(r);
				g2.setColor(selected ? NODE_SELECTED : NODE_FILL);
				g2.fillRoundRect(p.x, p.y, w, h, 2, 2);
			}

			if (viewportRect != null && viewportRect.width > 0 && viewportRect.height > 0 && scale > 0) {
				final Point p = mapToCanvas(viewportRect.x, viewportRect.y);
				final int w = Math.max(4, (int) Math.round(viewportRect.width * scale));
				final int h = Math.max(4, (int) Math.round(viewportRect.height * scale));
				g2.setColor(new Color(232, 93, 76, 40));
				g2.fillRect(p.x, p.y, w, h);
				g2.setColor(VIEWPORT_STROKE);
				g2.setStroke(new BasicStroke(1.5f));
				g2.drawRect(p.x, p.y, w, h);
			}
			g2.dispose();
		}
	}
}
