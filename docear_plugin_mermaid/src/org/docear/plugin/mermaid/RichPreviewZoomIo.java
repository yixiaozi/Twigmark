package org.docear.plugin.mermaid;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.io.IAttributeHandler;
import org.freeplane.core.io.IExtensionAttributeWriter;
import org.freeplane.core.io.ITreeWriter;
import org.freeplane.core.io.ReadManager;
import org.freeplane.core.io.WriteManager;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.ModeController;

/** Persists preview zoom as invisible {@code <node TWIGMARK_PREVIEW_ZOOM="…">} attribute. */
final class RichPreviewZoomIo {

	static final String XML_ZOOM = "TWIGMARK_PREVIEW_ZOOM";

	private static boolean installed;

	private RichPreviewZoomIo() {
	}

	static synchronized void install(final ModeController modeController) {
		if (installed || modeController == null) {
			return;
		}
		installed = true;
		final MapController mapController = modeController.getMapController();
		final ReadManager readManager = mapController.getReadManager();
		final WriteManager writeManager = mapController.getWriteManager();

		readManager.addAttributeHandler("node", XML_ZOOM, new IAttributeHandler() {
			@Override
			public void setAttribute(final Object node, final String value) {
				if (!(node instanceof NodeModel) || value == null || value.trim().length() == 0) {
					return;
				}
				try {
					final float z = RichPreviewScale.clamp(Float.parseFloat(value.trim().replace(',', '.')));
					RichPreviewZoomExtension.getOrCreate((NodeModel) node).setZoom(z);
				}
				catch (Throwable t) {
					// ignore invalid persisted value
				}
			}
		});

		writeManager.addExtensionAttributeWriter(RichPreviewZoomExtension.class, new IExtensionAttributeWriter() {
			@Override
			public void writeAttributes(final ITreeWriter writer, final Object userObject, final IExtension extension) {
				try {
					final RichPreviewZoomExtension ext = extension != null ? (RichPreviewZoomExtension) extension
					        : RichPreviewZoomExtension.get(userObject instanceof NodeModel ? (NodeModel) userObject : null);
					if (ext == null || ext.isDefault()) {
						return;
					}
					writer.addAttribute(XML_ZOOM, RichPreviewScale.format(ext.getZoom()));
				}
				catch (Throwable t) {
					LogUtils.warn("RichPreview: could not write zoom attribute", t);
				}
			}
		});
	}
}
