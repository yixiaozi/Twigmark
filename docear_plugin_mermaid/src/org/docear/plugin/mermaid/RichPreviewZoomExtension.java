package org.docear.plugin.mermaid;

import org.freeplane.core.extension.IExtension;
import org.freeplane.features.map.NodeModel;

/** In-memory zoom for rich previews; persisted as hidden {@code <node>} XML attribute. */
final class RichPreviewZoomExtension implements IExtension {

	private float zoom = RichPreviewScale.DEFAULT;

	static RichPreviewZoomExtension get(final NodeModel node) {
		if (node == null) {
			return null;
		}
		return (RichPreviewZoomExtension) node.getExtension(RichPreviewZoomExtension.class);
	}

	static RichPreviewZoomExtension getOrCreate(final NodeModel node) {
		RichPreviewZoomExtension ext = get(node);
		if (ext == null) {
			ext = new RichPreviewZoomExtension();
			node.addExtension(ext);
		}
		return ext;
	}

	float getZoom() {
		return zoom;
	}

	void setZoom(final float zoom) {
		this.zoom = zoom;
	}

	boolean isDefault() {
		return Math.abs(zoom - RichPreviewScale.DEFAULT) < 0.01f;
	}
}
