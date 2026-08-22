package org.docear.plugin.mermaid;

import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.docear.plugin.core.util.NodeUtilities;
import org.freeplane.features.attribute.AttributeController;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.attribute.mindmapmode.MAttributeController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

/**
 * Per-node display scale for rich previews (mermaid / cite / todo / …).
 * Stored in {@link RichPreviewZoomExtension}, not in the visible attribute table.
 */
final class RichPreviewScale {

	/** Legacy visible attribute from an earlier build; removed on map open. */
	static final String LEGACY_ATTR = "twigmark-preview-zoom";
	static final float MIN = 0.5f;
	static final float MAX = 4.0f;
	static final float DEFAULT = 1.0f;
	private static final int COMMIT_DELAY_MS = 380;

	private static final ConcurrentHashMap<String, CrispSnapshot> CRISP_BASE =
			new ConcurrentHashMap<String, CrispSnapshot>();
	private static final ConcurrentHashMap<String, Boolean> INTERIM =
			new ConcurrentHashMap<String, Boolean>();
	private static final ConcurrentHashMap<String, Float> AWAITING_CRISP =
			new ConcurrentHashMap<String, Float>();
	private static final ConcurrentHashMap<String, Timer> COMMIT_TIMERS =
			new ConcurrentHashMap<String, Timer>();

	private static final class CrispSnapshot {
		final Icon icon;
		final float zoom;

		CrispSnapshot(final Icon icon, final float zoom) {
			this.icon = icon;
			this.zoom = zoom;
		}
	}

	private RichPreviewScale() {
	}

	static float get(final NodeModel node) {
		if (node == null) {
			return DEFAULT;
		}
		final RichPreviewZoomExtension ext = RichPreviewZoomExtension.get(node);
		return ext != null ? clamp(ext.getZoom()) : DEFAULT;
	}

	/** Immediate crisp re-render (used after zoom gesture ends). */
	static void set(final NodeModel node, final float zoom) {
		if (node == null) {
			return;
		}
		cancelCommitTimer(node);
		INTERIM.remove(nodeKey(node));
		AWAITING_CRISP.remove(nodeKey(node));
		final float old = get(node);
		final float z = clamp(zoom);
		if (Math.abs(z - old) < 0.01f) {
			return;
		}
		RichPreviewZoomExtension.getOrCreate(node).setZoom(z);
		refreshPreview(node, old, z);
	}

	/** Fast thumbnail resize while wheel/drag is in progress; crisp render follows. */
	static void setInterim(final NodeModel node, final float zoom) {
		if (node == null) {
			return;
		}
		final float old = get(node);
		final float z = clamp(zoom);
		if (Math.abs(z - old) < 0.01f) {
			scheduleCommit(node);
			return;
		}
		RichPreviewZoomExtension.getOrCreate(node).setZoom(z);
		INTERIM.put(nodeKey(node), Boolean.TRUE);
		refreshPreview(node, old, z);
		scheduleCommit(node);
	}

	static void commit(final NodeModel node) {
		if (node == null) {
			return;
		}
		cancelCommitTimer(node);
		if (!Boolean.TRUE.equals(INTERIM.remove(nodeKey(node)))) {
			return;
		}
		final float z = get(node);
		AWAITING_CRISP.put(nodeKey(node), Float.valueOf(z));
		refreshPreview(node, z, z);
	}

	static boolean isAwaitingCrisp(final NodeModel node) {
		return node != null && AWAITING_CRISP.containsKey(nodeKey(node));
	}

	static void clearAwaitingCrisp(final NodeModel node) {
		if (node != null) {
			AWAITING_CRISP.remove(nodeKey(node));
		}
	}

	static String nodeKey(final NodeModel node) {
		final String id = node.getID();
		return id != null ? id : String.valueOf(System.identityHashCode(node));
	}

	static boolean isInterim(final NodeModel node) {
		return node != null && Boolean.TRUE.equals(INTERIM.get(nodeKey(node)));
	}

	static boolean isShowingInterim(final NodeModel node) {
		return isInterim(node) || isAwaitingCrisp(node);
	}

	static Icon applyInterim(final NodeModel node, final float targetZoom) {
		final CrispSnapshot snap = CRISP_BASE.get(nodeKey(node));
		if (snap == null || snap.icon == null) {
			return null;
		}
		final float baseZoom = clamp(snap.zoom);
		final float ratio = clamp(targetZoom) / baseZoom;
		if (snap.icon instanceof InteractiveTodoIcon) {
			return ((InteractiveTodoIcon) snap.icon).withDisplayScaleRatio(ratio);
		}
		if (snap.icon instanceof RichPreviewIcon) {
			return ((RichPreviewIcon) snap.icon).withDisplayScaleRatio(ratio);
		}
		return null;
	}

	static void rememberCrisp(final NodeModel node, final Icon icon, final float zoom) {
		if (node == null || icon == null || isInterim(node)) {
			return;
		}
		if (icon instanceof RichPreviewIcon && ((RichPreviewIcon) icon).getFullImage() == null) {
			return;
		}
		if (icon instanceof InteractiveTodoIcon && ((InteractiveTodoIcon) icon).getFullImage() == null) {
			return;
		}
		CRISP_BASE.put(nodeKey(node), new CrispSnapshot(icon, clamp(zoom)));
		if (isAwaitingCrisp(node)) {
			clearAwaitingCrisp(node);
			RichPreviewCrispRender.onAsyncComplete(node);
		}
	}

	static void refreshPreview(final NodeModel node, final float oldZoom, final float newZoom) {
		try {
			Controller.getCurrentModeController().getMapController().nodeRefresh(node,
			        RichPreviewZoomExtension.class, Float.valueOf(oldZoom), Float.valueOf(newZoom));
		}
		catch (Throwable t) {
			// ignore
		}
	}

	private static void scheduleCommit(final NodeModel node) {
		final String key = nodeKey(node);
		cancelCommitTimer(node);
		final Timer timer = new Timer(COMMIT_DELAY_MS, null);
		timer.setRepeats(false);
		timer.addActionListener(e -> {
			COMMIT_TIMERS.remove(key);
			if (SwingUtilities.isEventDispatchThread()) {
				commit(node);
			}
			else {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						commit(node);
					}
				});
			}
		});
		COMMIT_TIMERS.put(key, timer);
		timer.start();
	}

	private static void cancelCommitTimer(final NodeModel node) {
		final Timer timer = COMMIT_TIMERS.remove(nodeKey(node));
		if (timer != null) {
			timer.stop();
		}
	}

	/** Remove legacy visible attributes and reset zoom left over from that era. */
	static void purgeLegacyZoom(final NodeModel node) {
		if (node == null || !hasLegacyVisibleAttribute(node)) {
			return;
		}
		final float old = get(node);
		removeLegacyVisibleAttribute(node);
		final RichPreviewZoomExtension ext = RichPreviewZoomExtension.get(node);
		if (ext != null) {
			node.removeExtension(RichPreviewZoomExtension.class);
		}
		CRISP_BASE.remove(nodeKey(node));
		INTERIM.remove(nodeKey(node));
		AWAITING_CRISP.remove(nodeKey(node));
		cancelCommitTimer(node);
		if (Math.abs(old - DEFAULT) >= 0.01f) {
			refreshPreview(node, old, DEFAULT);
		}
	}

	static void purgeLegacyZoomOnTree(final NodeModel node) {
		if (node == null) {
			return;
		}
		purgeLegacyZoom(node);
		for (final NodeModel child : node.getChildren()) {
			purgeLegacyZoomOnTree(child);
		}
	}

	private static boolean hasLegacyVisibleAttribute(final NodeModel node) {
		try {
			final NodeAttributeTableModel model = AttributeController.getController()
			        .createAttributeTableModel(node);
			return model != null && model.getAttributePosition(LEGACY_ATTR) >= 0;
		}
		catch (Throwable t) {
			return false;
		}
	}

	private static void removeLegacyVisibleAttribute(final NodeModel node) {
		try {
			NodeUtilities.removeAttribute(node, LEGACY_ATTR);
			NodeUtilities.removeNodeAttribute(node, LEGACY_ATTR);
			final AttributeController ctrl = AttributeController.getController();
			if (ctrl instanceof MAttributeController) {
				((MAttributeController) ctrl).editAttribute(node, LEGACY_ATTR, null);
			}
		}
		catch (Throwable t) {
			// ignore
		}
	}

	static float clamp(final float z) {
		if (Float.isNaN(z) || Float.isInfinite(z)) {
			return DEFAULT;
		}
		if (z < MIN) {
			return MIN;
		}
		if (z > MAX) {
			return MAX;
		}
		return Math.round(z * 20f) / 20f;
	}

	static String format(final float z) {
		if (Math.abs(z - Math.round(z)) < 0.01f) {
			return Integer.toString(Math.round(z));
		}
		return String.format(java.util.Locale.US, "%.2f", Float.valueOf(z));
	}

	static String zoomCacheSuffix(final float zoom) {
		if (Math.abs(clamp(zoom) - DEFAULT) < 0.01f) {
			return "";
		}
		return "\0z=" + format(clamp(zoom));
	}

	static int displayMaxWidth(final String kind, final float zoom) {
		return Math.max(40, Math.round(baseMaxWidth(kind) * clamp(zoom)));
	}

	static int displayMaxHeight(final String kind, final float zoom) {
		return Math.max(30, Math.round(baseMaxHeight(kind) * clamp(zoom)));
	}

	static int fontSize(final int base, final float zoom) {
		return Math.max(8, Math.round(base * clamp(zoom)));
	}

	static RichPreviewIcon iconForBitmap(final String kind, final BufferedImage img, final float zoom) {
		if (img == null) {
			return null;
		}
		final int maxW = displayMaxWidth(kind, zoom);
		final int maxH = displayMaxHeight(kind, zoom);
		if (img.getWidth() <= maxW && img.getHeight() <= maxH) {
			return RichPreviewIcon.fromNativeImage(kind, img);
		}
		return RichPreviewIcon.fromImage(kind, img, maxW, maxH);
	}

	static int baseMaxWidth(final String kind) {
		if ("gantt".equals(kind)) {
			return 520;
		}
		return RichPreviewIcon.MAX_WIDTH;
	}

	static int baseMaxHeight(final String kind) {
		if ("gantt".equals(kind)) {
			return 300;
		}
		return RichPreviewIcon.MAX_HEIGHT;
	}
}
