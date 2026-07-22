package org.freeplane.plugin.workspace.components.overlay;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Edge/corner resize + optional drag for floating overlay cards.
 */
public final class FloatingOverlayInteraction {

	public interface Host {
		boolean isResizeEnabled();

		boolean isDragEnabled(MouseEvent e);

		Dimension getMinSize();

		Dimension getMaxSize();

		void onUserMoved();

		void onUserResized(int width, int height);

		void onInteractionFinished();
	}

	public enum Edge {
		NONE, N, S, E, W, NE, NW, SE, SW
	}

	private static final int MARGIN = 7;

	private FloatingOverlayInteraction() {
	}

	public static Edge hitTest(final JComponent c, final Point p) {
		if (c == null || p == null) {
			return Edge.NONE;
		}
		final int w = c.getWidth();
		final int h = c.getHeight();
		final boolean left = p.x <= MARGIN;
		final boolean right = p.x >= w - MARGIN;
		final boolean top = p.y <= MARGIN;
		final boolean bottom = p.y >= h - MARGIN;
		if (top && left) {
			return Edge.NW;
		}
		if (top && right) {
			return Edge.NE;
		}
		if (bottom && left) {
			return Edge.SW;
		}
		if (bottom && right) {
			return Edge.SE;
		}
		if (top) {
			return Edge.N;
		}
		if (bottom) {
			return Edge.S;
		}
		if (left) {
			return Edge.W;
		}
		if (right) {
			return Edge.E;
		}
		return Edge.NONE;
	}

	public static Cursor cursorFor(final Edge edge) {
		if (edge == null) {
			return Cursor.getDefaultCursor();
		}
		switch (edge) {
		case N:
			return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
		case S:
			return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
		case E:
			return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
		case W:
			return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
		case NE:
			return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
		case NW:
			return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
		case SE:
			return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
		case SW:
			return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
		default:
			return Cursor.getDefaultCursor();
		}
	}

	public static void install(final JComponent target, final Host host) {
		if (target == null || host == null) {
			return;
		}
		final ResizeState state = new ResizeState();
		target.addMouseListener(new MouseAdapter() {
			public void mousePressed(final MouseEvent e) {
				if (!SwingUtilities.isLeftMouseButton(e)) {
					return;
				}
				if (host.isResizeEnabled()) {
					final Edge edge = hitTest(target, e.getPoint());
					if (edge != Edge.NONE) {
						state.resizing = true;
						state.edge = edge;
						state.startScreen = e.getLocationOnScreen();
						state.startBounds = target.getBounds();
						e.consume();
						return;
					}
				}
				if (host.isDragEnabled(e)) {
					state.dragging = true;
					state.startScreen = e.getLocationOnScreen();
					state.startBounds = target.getBounds();
				}
			}

			public void mouseReleased(final MouseEvent e) {
				final boolean active = state.resizing || state.dragging;
				state.resizing = false;
				state.dragging = false;
				state.edge = Edge.NONE;
				if (active) {
					host.onInteractionFinished();
				}
			}

			public void mouseExited(final MouseEvent e) {
				if (!state.resizing) {
					target.setCursor(Cursor.getDefaultCursor());
				}
			}
		});
		target.addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseMoved(final MouseEvent e) {
				if (!host.isResizeEnabled()) {
					return;
				}
				final Edge edge = hitTest(target, e.getPoint());
				target.setCursor(edge == Edge.NONE ? Cursor.getDefaultCursor() : cursorFor(edge));
			}

			public void mouseDragged(final MouseEvent e) {
				if (state.startScreen == null || state.startBounds == null) {
					return;
				}
				final Point now = e.getLocationOnScreen();
				final int dx = now.x - state.startScreen.x;
				final int dy = now.y - state.startScreen.y;
				if (state.resizing && state.edge != Edge.NONE) {
					applyResize(target, host, state.edge, state.startBounds, dx, dy);
				}
				else if (state.dragging) {
					target.setLocation(Math.max(0, state.startBounds.x + dx), Math.max(0, state.startBounds.y + dy));
					host.onUserMoved();
				}
			}
		});
	}

	private static void applyResize(final JComponent target, final Host host, final Edge edge,
	        final Rectangle start, final int dx, final int dy) {
		final Dimension min = host.getMinSize();
		final Dimension max = host.getMaxSize();
		int x = start.x;
		int y = start.y;
		int w = start.width;
		int h = start.height;
		if (edge == Edge.E || edge == Edge.NE || edge == Edge.SE) {
			w = start.width + dx;
		}
		if (edge == Edge.S || edge == Edge.SE || edge == Edge.SW) {
			h = start.height + dy;
		}
		if (edge == Edge.W || edge == Edge.NW || edge == Edge.SW) {
			w = start.width - dx;
			x = start.x + dx;
		}
		if (edge == Edge.N || edge == Edge.NE || edge == Edge.NW) {
			h = start.height - dy;
			y = start.y + dy;
		}
		w = clamp(w, min.width, max.width);
		h = clamp(h, min.height, max.height);
		if (edge == Edge.W || edge == Edge.NW || edge == Edge.SW) {
			x = start.x + (start.width - w);
		}
		if (edge == Edge.N || edge == Edge.NE || edge == Edge.NW) {
			y = start.y + (start.height - h);
		}
		x = Math.max(0, x);
		y = Math.max(0, y);
		target.setBounds(x, y, w, h);
		host.onUserResized(w, h);
		host.onUserMoved();
	}

	private static int clamp(final int v, final int min, final int max) {
		return Math.max(min, Math.min(max, v));
	}

	private static final class ResizeState {
		boolean resizing;
		boolean dragging;
		Edge edge = Edge.NONE;
		Point startScreen;
		Rectangle startBounds;
	}
}
