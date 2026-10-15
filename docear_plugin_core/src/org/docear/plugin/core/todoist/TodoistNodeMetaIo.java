package org.docear.plugin.core.todoist;

import org.docear.plugin.core.util.NodeUtilities;
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

/**
 * Persists Todoist task id / content hash as invisible {@code <node>} XML attributes.
 */
final class TodoistNodeMetaIo {
	static final String XML_TASK_ID = "TODOIST_TASK_ID";
	static final String XML_CONTENT_HASH = "TODOIST_CONTENT_HASH";
	/** Set when the user moves the node; import must not reparent it. */
	static final String XML_USER_PLACED = "TODOIST_USER_PLACED";
	static final String XML_SYNCED_LOCAL = "TODOIST_SYNCED_LOCAL";
	static final String XML_SYNCED_REMOTE = "TODOIST_SYNCED_REMOTE";
	/** Identity flag. Independent of the yellow background, which is only a reminder. */
	static final String XML_LINKED = "TODOIST_LINKED";
	static final String XML_PROJECT_ID = "TODOIST_PROJECT_ID";
	static final String XML_SECTION_ID = "TODOIST_SECTION_ID";

	private static boolean installed;

	private TodoistNodeMetaIo() {
	}

	private static long parseMillis(final String value) {
		if (value == null || value.trim().length() == 0) {
			return 0L;
		}
		try {
			return Long.parseLong(value.trim());
		}
		catch (NumberFormatException e) {
			return 0L;
		}
	}

	static synchronized void install(final ModeController modeController) {
		if (installed || modeController == null) {
			return;
		}
		installed = true;
		final MapController mapController = modeController.getMapController();
		final ReadManager readManager = mapController.getReadManager();
		final WriteManager writeManager = mapController.getWriteManager();

		readManager.addAttributeHandler("node", XML_TASK_ID, new IAttributeHandler() {
			public void setAttribute(final Object node, final String value) {
				if (node instanceof NodeModel && value != null && value.trim().length() > 0) {
					TodoistNodeMeta.getOrCreate((NodeModel) node).setTaskId(value.trim());
				}
			}
		});
		readManager.addAttributeHandler("node", XML_CONTENT_HASH, new IAttributeHandler() {
			public void setAttribute(final Object node, final String value) {
				if (node instanceof NodeModel && value != null && value.trim().length() > 0) {
					TodoistNodeMeta.getOrCreate((NodeModel) node).setContentHash(value.trim());
				}
			}
		});
		readManager.addAttributeHandler("node", XML_USER_PLACED, new IAttributeHandler() {
			public void setAttribute(final Object node, final String value) {
				if (node instanceof NodeModel && "true".equalsIgnoreCase(value)) {
					TodoistNodeMeta.getOrCreate((NodeModel) node).setUserPlaced(true);
				}
			}
		});
		readManager.addAttributeHandler("node", XML_SYNCED_LOCAL, new IAttributeHandler() {
			public void setAttribute(final Object node, final String value) {
				if (node instanceof NodeModel) {
					TodoistNodeMeta.getOrCreate((NodeModel) node).setSyncedLocalMillis(parseMillis(value));
				}
			}
		});
		readManager.addAttributeHandler("node", XML_SYNCED_REMOTE, new IAttributeHandler() {
			public void setAttribute(final Object node, final String value) {
				if (node instanceof NodeModel) {
					TodoistNodeMeta.getOrCreate((NodeModel) node).setSyncedRemoteMillis(parseMillis(value));
				}
			}
		});
		readManager.addAttributeHandler("node", XML_LINKED, new IAttributeHandler() {
			public void setAttribute(final Object node, final String value) {
				if (node instanceof NodeModel && "true".equalsIgnoreCase(value)) {
					TodoistNodeMeta.getOrCreate((NodeModel) node).setLinked(true);
				}
			}
		});
		readManager.addAttributeHandler("node", XML_PROJECT_ID, new IAttributeHandler() {
			public void setAttribute(final Object node, final String value) {
				if (node instanceof NodeModel && value != null && value.trim().length() > 0) {
					TodoistNodeMeta.getOrCreate((NodeModel) node).setProjectId(value.trim());
				}
			}
		});
		readManager.addAttributeHandler("node", XML_SECTION_ID, new IAttributeHandler() {
			public void setAttribute(final Object node, final String value) {
				if (node instanceof NodeModel && value != null && value.trim().length() > 0) {
					TodoistNodeMeta.getOrCreate((NodeModel) node).setSectionId(value.trim());
				}
			}
		});

		writeManager.addExtensionAttributeWriter(TodoistNodeMeta.class, new IExtensionAttributeWriter() {
			public void writeAttributes(final ITreeWriter writer, final Object userObject, final IExtension extension) {
				try {
					final TodoistNodeMeta meta = extension != null ? (TodoistNodeMeta) extension
							: TodoistNodeMeta.get(userObject instanceof NodeModel ? (NodeModel) userObject : null);
					if (meta == null || meta.isEmpty()) {
						return;
					}
					if (meta.getTaskId() != null) {
						writer.addAttribute(XML_TASK_ID, meta.getTaskId());
					}
					if (meta.getContentHash() != null) {
						writer.addAttribute(XML_CONTENT_HASH, meta.getContentHash());
					}
					if (meta.isUserPlaced()) {
						writer.addAttribute(XML_USER_PLACED, "true");
					}
					if (meta.getSyncedLocalMillis() > 0) {
						writer.addAttribute(XML_SYNCED_LOCAL, Long.toString(meta.getSyncedLocalMillis()));
					}
					if (meta.getSyncedRemoteMillis() > 0) {
						writer.addAttribute(XML_SYNCED_REMOTE, Long.toString(meta.getSyncedRemoteMillis()));
					}
					if (meta.isLinked() || (meta.getTaskId() != null && meta.getTaskId().length() > 0)) {
						writer.addAttribute(XML_LINKED, "true");
					}
					if (meta.getProjectId() != null) {
						writer.addAttribute(XML_PROJECT_ID, meta.getProjectId());
					}
					if (meta.getSectionId() != null) {
						writer.addAttribute(XML_SECTION_ID, meta.getSectionId());
					}
				}
				catch (final Exception e) {
					LogUtils.warn("Todoist: could not write node meta", e);
				}
			}
		});
		LogUtils.info("Todoist: node meta stored as hidden XML attributes (" + XML_TASK_ID + ", "
				+ XML_CONTENT_HASH + ")");
	}

	/** Drop legacy visible Freeplane attributes that used to store the same data. */
	static void stripLegacyVisibleAttributes(final NodeModel node) {
		if (node == null) {
			return;
		}
		try {
			NodeUtilities.removeNodeAttribute(node, TodoistConfig.ATTR_TASK_ID);
			NodeUtilities.removeNodeAttribute(node, TodoistConfig.ATTR_CONTENT_HASH);
			NodeUtilities.removeAttribute(node, TodoistConfig.ATTR_TASK_ID);
			NodeUtilities.removeAttribute(node, TodoistConfig.ATTR_CONTENT_HASH);
		}
		catch (final Exception e) {
			// ignore — attribute table may not be present
		}
	}
}
