package org.docear.plugin.mcp.service;

import java.awt.Color;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.docear.plugin.core.features.DocearNodePrivacyExtensionController;
import org.docear.plugin.core.features.DocearNodePrivacyExtensionController.DocearPrivacyLevel;
import org.docear.plugin.mcp.DocearMcpConfig;
import org.docear.plugin.mcp.json.JsonValue;
import org.docear.plugin.mcp.util.EdtRunner;
import org.docear.plugin.mcp.util.EdtRunner.Task;
import org.freeplane.core.undo.IUndoHandler;
import org.freeplane.core.util.ColorUtils;
import org.freeplane.features.clipboard.ClipboardController;
import org.freeplane.features.cloud.CloudModel;
import org.freeplane.features.cloud.mindmapmode.MCloudController;
import org.freeplane.features.link.ConnectorModel;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.link.LinkModel;
import org.freeplane.features.link.NodeLinkModel;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.FreeNode;
import org.freeplane.features.map.MapReader;
import org.freeplane.features.map.MapReader.NodeTreeCreator;
import org.freeplane.features.map.MapWriter.Hint;
import org.freeplane.features.map.MapWriter.Mode;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.nodestyle.NodeStyleController;
import org.freeplane.features.nodestyle.mindmapmode.MNodeStyleController;
import org.freeplane.features.text.mindmapmode.MTextController;
import org.freeplane.n3.nanoxml.XMLException;
import org.freeplane.view.swing.features.filepreview.ExternalResource;
import org.freeplane.view.swing.features.filepreview.ViewerController;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderCycleAttributes;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderExtension;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderHook;
import org.freeplane.view.swing.features.time.mindmapmode.ReminderTaskAttributes;

/**
 * MCP write helpers for subtree clipboard, undo, arrow links, style, details,
 * privacy, images/attachments, and reminder removal.
 */
public final class McpNodeEditService {

	private static final Object CLIP_LOCK = new Object();
	private static String clipboardXml = "";
	private static String clipboardSourceMap = "";
	private static String clipboardSourceNodeId = "";
	private static int clipboardNodeCount = 0;

	private McpNodeEditService() {
	}

	public static String copyNodes(final String filePath, final String nodeId) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final String xml = serializeSubtree(node);
				synchronized (CLIP_LOCK) {
					clipboardXml = xml;
					clipboardSourceMap = session.getFile().getAbsolutePath();
					clipboardSourceNodeId = node.getID();
					clipboardNodeCount = countSubtree(node);
				}
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("action", JsonValue.ofString("copy"));
				result.put("nodeId", JsonValue.ofString(node.getID()));
				result.put("copiedNodeCount", JsonValue.ofNumber(Integer.valueOf(countSubtree(node))));
				result.put("saved", JsonValue.ofBoolean(false));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String cutNodes(final String filePath, final String nodeId) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				if (node.getParentNode() == null) {
					throw new IllegalArgumentException("Cannot cut the root node.");
				}
				final String xml = serializeSubtree(node);
				final int count = countSubtree(node);
				final String id = node.getID();
				synchronized (CLIP_LOCK) {
					clipboardXml = xml;
					clipboardSourceMap = session.getFile().getAbsolutePath();
					clipboardSourceNodeId = id;
					clipboardNodeCount = count;
				}
				getMapController().deleteNode(node);
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("action", JsonValue.ofString("cut"));
				result.put("nodeId", JsonValue.ofString(id));
				result.put("cutNodeCount", JsonValue.ofNumber(Integer.valueOf(count)));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String pasteNodes(final String filePath, final String parentNodeId) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final String xml;
				synchronized (CLIP_LOCK) {
					xml = clipboardXml;
				}
				if (xml == null || xml.trim().length() == 0) {
					throw new IllegalStateException("MCP clipboard is empty. Call copy_nodes or cut_nodes first.");
				}
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel parent = session.requireNode(parentNodeId);
				final List pasted = pasteXml(xml, parent);
				session.save();
				return pasteResult(session, "paste", parent, pasted);
			}
		});
	}

	public static String cloneNodes(final String filePath, final String nodeId, final String parentNodeId)
			throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel source = session.requireNode(nodeId);
				if (source.getParentNode() == null && (parentNodeId == null || parentNodeId.trim().length() == 0)) {
					throw new IllegalArgumentException("Cannot clone the root onto itself. Pass parentNodeId.");
				}
				final NodeModel parent;
				if (parentNodeId != null && parentNodeId.trim().length() > 0) {
					parent = session.requireNode(parentNodeId.trim());
				}
				else {
					parent = source.getParentNode();
				}
				if (parent == null) {
					throw new IllegalArgumentException("parentNodeId is required when cloning the root.");
				}
				if (isAncestorOf(source, parent)) {
					throw new IllegalArgumentException("Cannot clone a node under one of its own descendants.");
				}
				final String xml = serializeSubtree(source);
				final List pasted = pasteXml(xml, parent);
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("action", JsonValue.ofString("clone"));
				result.put("sourceNodeId", JsonValue.ofString(source.getID()));
				result.put("parentNodeId", JsonValue.ofString(parent.getID()));
				putPastedIds(result, pasted);
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String undoMap(final String filePath) throws Exception {
		ensureWritable();
		return undoOrRedo(filePath, true);
	}

	public static String redoMap(final String filePath) throws Exception {
		ensureWritable();
		return undoOrRedo(filePath, false);
	}

	public static String addArrowLink(final String filePath, final String sourceNodeId, final String targetNodeId,
			final String label, final String color) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel source = session.requireNode(sourceNodeId);
				final NodeModel target = session.requireNode(targetNodeId);
				if (source.getMap() != target.getMap()) {
					throw new IllegalArgumentException("Arrow links must stay inside one mind map.");
				}
				final MLinkController linkController = (MLinkController) LinkController.getController();
				final ConnectorModel connector = linkController.addConnector(source, target.getID());
				if (label != null && label.trim().length() > 0) {
					linkController.setMiddleLabel(connector, label.trim());
				}
				if (color != null && color.trim().length() > 0) {
					linkController.setConnectorColor(connector, ColorUtils.stringToColor(color.trim()));
				}
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("action", JsonValue.ofString("add_arrow_link"));
				result.put("sourceNodeId", JsonValue.ofString(source.getID()));
				result.put("targetNodeId", JsonValue.ofString(target.getID()));
				result.put("label", JsonValue.ofString(label != null ? label.trim() : ""));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String removeArrowLink(final String filePath, final String sourceNodeId, final String targetNodeId)
			throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel source = session.requireNode(sourceNodeId);
				session.requireNode(targetNodeId);
				final MLinkController linkController = (MLinkController) LinkController.getController();
				final List toRemove = new ArrayList();
				final Collection links = NodeLinks.getLinks(source);
				for (final Iterator it = links.iterator(); it.hasNext();) {
					final LinkModel link = (LinkModel) it.next();
					if (link instanceof ConnectorModel && targetNodeId.equals(link.getTargetID())) {
						toRemove.add(link);
					}
				}
				for (int i = 0; i < toRemove.size(); i++) {
					linkController.removeArrowLink((NodeLinkModel) toRemove.get(i));
				}
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("action", JsonValue.ofString("remove_arrow_link"));
				result.put("sourceNodeId", JsonValue.ofString(source.getID()));
				result.put("targetNodeId", JsonValue.ofString(targetNodeId));
				result.put("removedCount", JsonValue.ofNumber(Integer.valueOf(toRemove.size())));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setNodeCloud(final String filePath, final String nodeId, final Boolean enabled,
			final String color, final String shape) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final MCloudController cloudController = (MCloudController) org.freeplane.features.cloud.CloudController
						.getController();
				final boolean turnOn = enabled == null
						? (color != null && color.trim().length() > 0) || (shape != null && shape.trim().length() > 0)
						: enabled.booleanValue();
				cloudController.setCloud(node, turnOn);
				if (turnOn) {
					if (color != null && color.trim().length() > 0 && !"clear".equalsIgnoreCase(color.trim())) {
						cloudController.setColor(node, ColorUtils.stringToColor(color.trim()));
					}
					if (shape != null && shape.trim().length() > 0) {
						cloudController.setShape(node, parseCloudShape(shape.trim()));
					}
				}
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(node.getID()));
				result.put("cloud", JsonValue.ofBoolean(turnOn));
				final CloudModel model = CloudModel.getModel(node);
				if (model != null) {
					result.put("cloudColor", JsonValue.ofString(ColorUtils.colorToString(model.getColor())));
					result.put("cloudShape", JsonValue.ofString(model.getShape() != null ? model.getShape().name() : ""));
				}
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setNodeStyle(final String filePath, final String nodeId, final String color,
			final String backgroundColor, final String fontFamily, final Integer fontSize, final Boolean bold,
			final Boolean italic, final String shape) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final MNodeStyleController style = (MNodeStyleController) NodeStyleController.getController();
				if (color != null) {
					style.setColor(node, parseOptionalColor(color));
				}
				if (backgroundColor != null) {
					style.setBackgroundColor(node, parseOptionalColor(backgroundColor));
				}
				if (fontFamily != null && fontFamily.trim().length() > 0) {
					style.setFontFamily(node, fontFamily.trim());
				}
				if (fontSize != null) {
					if (fontSize.intValue() < 1 || fontSize.intValue() > 96) {
						throw new IllegalArgumentException("fontSize must be 1..96");
					}
					style.setFontSize(node, fontSize);
				}
				if (bold != null) {
					style.setBold(node, bold);
				}
				if (italic != null) {
					style.setItalic(node, italic);
				}
				if (shape != null && shape.trim().length() > 0) {
					style.setShape(node, parseNodeShape(shape.trim()));
				}
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(node.getID()));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setNodeDetails(final String filePath, final String nodeId, final String detailsHtml,
			final Boolean hidden) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final MTextController textController = (MTextController) org.freeplane.features.text.TextController
						.getController();
				final String html = detailsHtml != null && detailsHtml.trim().length() > 0 ? detailsHtml : null;
				textController.setDetails(node, html);
				if (hidden != null && html != null) {
					textController.setDetailsHidden(node, hidden.booleanValue());
				}
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(node.getID()));
				result.put("cleared", JsonValue.ofBoolean(html == null));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setNodePrivacy(final String filePath, final String nodeId, final String privacy)
			throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				if (privacy == null || privacy.trim().length() == 0) {
					throw new IllegalArgumentException("privacy is required: PUBLIC, DEMO, or PRIVATE");
				}
				final DocearPrivacyLevel level;
				try {
					level = DocearPrivacyLevel.valueOf(privacy.trim().toUpperCase());
				}
				catch (IllegalArgumentException e) {
					throw new IllegalArgumentException("privacy must be PUBLIC, DEMO, or PRIVATE");
				}
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				DocearNodePrivacyExtensionController.getController().setPrivacyLevel(node, level);
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(node.getID()));
				result.put("privacy", JsonValue.ofString(level.name()));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setNodeImage(final String filePath, final String nodeId, final String imagePath)
			throws Exception {
		ensureWritable();
		return setExternal(filePath, nodeId, imagePath, true);
	}

	public static String clearNodeImage(final String filePath, final String nodeId) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final ViewerController viewer = viewerController();
				final boolean had = node.getExtension(ExternalResource.class) != null;
				if (viewer != null) {
					viewer.undoableDeactivateHook(node);
				}
				else {
					node.removeExtension(ExternalResource.class);
				}
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(node.getID()));
				result.put("cleared", JsonValue.ofBoolean(had));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	public static String setNodeAttachment(final String filePath, final String nodeId, final String attachmentPath)
			throws Exception {
		ensureWritable();
		return setExternal(filePath, nodeId, attachmentPath, false);
	}

	public static String clearReminder(final String filePath, final String nodeId) throws Exception {
		ensureWritable();
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final ReminderHook reminderHook = (ReminderHook) Controller.getCurrentModeController()
						.getExtension(ReminderHook.class);
				if (reminderHook == null) {
					throw new IllegalStateException("ReminderHook is not available.");
				}
				final ReminderExtension existing = ReminderExtension.getExtension(node);
				final boolean had = existing != null;
				if (existing != null) {
					reminderHook.undoableDeactivateHook(node);
				}
				ReminderCycleAttributes.writeOneTimeReminder(node);
				ReminderTaskAttributes.writeEmptyTask(node);
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(node.getID()));
				result.put("cleared", JsonValue.ofBoolean(had));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	private static String setExternal(final String filePath, final String nodeId, final String path,
			final boolean requireImage) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				if (path == null || path.trim().length() == 0) {
					throw new IllegalArgumentException(requireImage ? "imagePath is required." : "attachmentPath is required.");
				}
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final NodeModel node = session.requireNode(nodeId);
				final ResolvedUri resolved = resolveUri(session.getFile(), path.trim());
				boolean imageAttached = false;
				if (requireImage || looksLikeImage(resolved.uri)) {
					final ViewerController viewer = viewerController();
					if (viewer == null) {
						throw new IllegalStateException("ViewerController is not available.");
					}
					imageAttached = viewer.paste(resolved.uri, node);
					if (requireImage && !imageAttached) {
						throw new IllegalArgumentException("Not an attachable image: " + resolved.uri);
					}
				}
				if (!requireImage) {
					final MLinkController linkController = (MLinkController) LinkController.getController();
					linkController.setLink(node, resolved.uri, LinkController.LINK_ABSOLUTE);
				}
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("nodeId", JsonValue.ofString(node.getID()));
				result.put("uri", JsonValue.ofString(resolved.uri.toString()));
				result.put("imageAttached", JsonValue.ofBoolean(imageAttached));
				result.put("linkSet", JsonValue.ofBoolean(!requireImage));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	private static String undoOrRedo(final String filePath, final boolean undo) throws Exception {
		return (String) EdtRunner.run(new Task() {
			public Object run() throws Exception {
				final McpMapWriteSession session = McpMapWriteSession.open(filePath);
				final IUndoHandler handler = (IUndoHandler) session.getMap().getExtension(IUndoHandler.class);
				if (handler == null) {
					throw new IllegalStateException("This map has no undo stack.");
				}
				final boolean can = undo ? handler.canUndo() : handler.canRedo();
				final String last = handler.getLastDescription();
				if (!can) {
					final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
					result.put("action", JsonValue.ofString(undo ? "undo" : "redo"));
					result.put("applied", JsonValue.ofBoolean(false));
					result.put("reason", JsonValue.ofString(undo ? "nothing to undo" : "nothing to redo"));
					result.put("canUndo", JsonValue.ofBoolean(handler.canUndo()));
					result.put("canRedo", JsonValue.ofBoolean(handler.canRedo()));
					return JsonValue.ofMap(result).toJson();
				}
				if (undo) {
					handler.undo();
				}
				else {
					handler.redo();
				}
				session.save();
				final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
				result.put("action", JsonValue.ofString(undo ? "undo" : "redo"));
				result.put("applied", JsonValue.ofBoolean(true));
				result.put("description", JsonValue.ofString(last != null ? last : ""));
				result.put("canUndo", JsonValue.ofBoolean(handler.canUndo()));
				result.put("canRedo", JsonValue.ofBoolean(handler.canRedo()));
				return JsonValue.ofMap(result).toJson();
			}
		});
	}

	private static String serializeSubtree(final NodeModel node) throws Exception {
		final StringWriter writer = new StringWriter();
		Controller.getCurrentModeController().getMapController().getMapWriter().writeNodeAsXml(writer, node,
				Mode.CLIPBOARD, true, true, false);
		return writer.toString();
	}

	private static List pasteXml(final String xml, final NodeModel parent) throws Exception {
		final MMapController mapController = getMapController();
		final MapReader mapReader = mapController.getMapReader();
		final NodeTreeCreator creator = mapReader.nodeTreeCreator(parent.getMap());
		creator.setHint(Hint.MODE, Mode.CLIPBOARD);
		final String[] lines = xml.split(ClipboardController.NODESEPARATOR);
		final List roots = new ArrayList();
		for (int i = 0; i < lines.length; i++) {
			final String chunk = lines[i];
			if (chunk == null || chunk.trim().length() == 0) {
				continue;
			}
			try {
				final NodeModel created = creator.create(new StringReader(chunk));
				if (created == null) {
					continue;
				}
				created.removeExtension(FreeNode.class);
				mapController.insertNode(created, parent);
				roots.add(created);
			}
			catch (XMLException e) {
				throw new IllegalArgumentException("Failed to paste node XML: " + e.getMessage(), e);
			}
		}
		creator.finish(parent);
		return roots;
	}

	private static String pasteResult(final McpMapWriteSession session, final String action, final NodeModel parent,
			final List pasted) {
		final Map<String, JsonValue> result = McpMindMapService.writeResult(session);
		result.put("action", JsonValue.ofString(action));
		result.put("parentNodeId", JsonValue.ofString(parent.getID()));
		putPastedIds(result, pasted);
		synchronized (CLIP_LOCK) {
			result.put("clipboardSourceMap", JsonValue.ofString(clipboardSourceMap));
			result.put("clipboardSourceNodeId", JsonValue.ofString(clipboardSourceNodeId));
			result.put("clipboardNodeCount", JsonValue.ofNumber(Integer.valueOf(clipboardNodeCount)));
		}
		return JsonValue.ofMap(result).toJson();
	}

	private static void putPastedIds(final Map result, final List pasted) {
		final List ids = new ArrayList();
		int total = 0;
		for (int i = 0; i < pasted.size(); i++) {
			final NodeModel root = (NodeModel) pasted.get(i);
			ids.add(JsonValue.ofString(root.getID()));
			total += countSubtree(root);
		}
		result.put("pastedRootIds", JsonValue.ofList(ids));
		result.put("pastedNodeCount", JsonValue.ofNumber(Integer.valueOf(total)));
	}

	private static int countSubtree(final NodeModel node) {
		int count = 1;
		for (int i = 0; i < node.getChildCount(); i++) {
			count += countSubtree((NodeModel) node.getChildAt(i));
		}
		return count;
	}

	private static boolean isAncestorOf(final NodeModel ancestor, final NodeModel node) {
		NodeModel current = node;
		while (current != null) {
			if (current == ancestor) {
				return true;
			}
			current = current.getParentNode();
		}
		return false;
	}

	private static CloudModel.Shape parseCloudShape(final String shape) {
		try {
			return CloudModel.Shape.valueOf(shape.toUpperCase().replace('-', '_'));
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("cloud shape must be ARC, STAR, RECT, or ROUND_RECT");
		}
	}

	private static String parseNodeShape(final String shape) {
		final String normalized = shape.toLowerCase().replace('-', '_');
		if ("fork".equals(normalized) || "bubble".equals(normalized) || "as_parent".equals(normalized)
				|| "combined".equals(normalized)) {
			return normalized;
		}
		throw new IllegalArgumentException("node shape must be fork, bubble, as_parent, or combined");
	}

	private static Color parseOptionalColor(final String value) {
		final String trimmed = value.trim();
		if (trimmed.length() == 0 || "clear".equalsIgnoreCase(trimmed) || "none".equalsIgnoreCase(trimmed)) {
			return null;
		}
		return ColorUtils.stringToColor(trimmed);
	}

	private static ViewerController viewerController() {
		return (ViewerController) Controller.getCurrentModeController().getExtension(ViewerController.class);
	}

	private static boolean looksLikeImage(final URI uri) {
		if (uri == null) {
			return false;
		}
		String path = uri.getPath();
		if (path == null) {
			path = uri.toString();
		}
		final String lower = path.toLowerCase();
		return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")
				|| lower.endsWith(".bmp") || lower.endsWith(".webp");
	}

	private static ResolvedUri resolveUri(final File mapFile, final String raw) throws Exception {
		String value = raw.trim();
		if (value.startsWith("file:") || value.startsWith("http:") || value.startsWith("https:")
				|| value.startsWith("eagle:")) {
			return new ResolvedUri(new URI(value));
		}
		File file = new File(value);
		if (!file.isAbsolute() && mapFile != null && mapFile.getParentFile() != null) {
			file = new File(mapFile.getParentFile(), value);
		}
		return new ResolvedUri(file.getAbsoluteFile().toURI());
	}

	private static MMapController getMapController() {
		return (MMapController) Controller.getCurrentModeController().getMapController();
	}

	private static void ensureWritable() {
		if (DocearMcpConfig.isReadOnly()) {
			throw new SecurityException("Docear MCP is running in read-only mode.");
		}
	}

	private static final class ResolvedUri {
		final URI uri;

		ResolvedUri(final URI uri) {
			this.uri = uri;
		}
	}
}
