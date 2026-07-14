package org.docear.plugin.core.graph;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.WorkspaceSideTabScanCache;
import org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagScanner;
import org.freeplane.plugin.workspace.features.nodepins.NodeDetailsTagUtils;
import org.freeplane.plugin.workspace.features.nodepins.NodePinEntry;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Scans mind-map files for file-level and node-level relationship graphs.
 */
public final class RelationshipGraphScanner {

	public static final int MODE_MAP_FILES = 0;
	public static final int MODE_MAP_NODES = 1;
	public static final int MODE_TAGS = 2;
	private static final int MAX_NODE_GRAPH_NODES = 3000;
	private static final int YIELD_EVERY_N_FILES = 20;
	private static final int CACHE_WAIT_MS = 120000;

	public interface ProgressListener {
		void onProgress(int scanned, int total);
	}

	private RelationshipGraphScanner() {
	}

	public static RelationshipGraphIndex scan() {
		return scan(MODE_MAP_FILES, null);
	}

	public static RelationshipGraphIndex scan(final int mode) {
		return scan(mode, null);
	}

	public static RelationshipGraphIndex scan(final int mode, final ProgressListener progress) {
		if (mode == MODE_MAP_NODES) {
			return scanNodeLinks(progress);
		}
		if (mode == MODE_TAGS) {
			return scanTagLinks(progress);
		}
		return scanMapFiles(progress);
	}

	public static RelationshipGraphIndex scanMapFiles() {
		return scanMapFiles(null);
	}

	public static RelationshipGraphIndex scanMapFiles(final ProgressListener progress) {
		final List<File> mindmapFiles = collectMindmapFiles();
		final int total = mindmapFiles.size();
		final Map<String, RelationshipGraphNode> nodesByPath = new HashMap<String, RelationshipGraphNode>();
		final List<RelationshipGraphEdge> edges = new ArrayList<RelationshipGraphEdge>();
		final Set<String> edgeKeys = new HashSet<String>();
		SaxSession saxSession = null;

		for (int i = 0; i < mindmapFiles.size(); i++) {
			if (isCancelled()) {
				return emptyIndex(MODE_MAP_FILES);
			}
			cooperate(i);
			final File file = mindmapFiles.get(i);
			final String key = pathKey(file);
			if (key != null && !nodesByPath.containsKey(key)) {
				nodesByPath.put(key, RelationshipGraphNode.forMapFile(file));
			}
		}

		try {
			saxSession = new SaxSession();
		}
		catch (final Exception e) {
			LogUtils.warn("Relationship graph: SAX parser init failed", e);
			return emptyIndex(MODE_MAP_FILES);
		}

		for (int i = 0; i < mindmapFiles.size(); i++) {
			if (isCancelled()) {
				return emptyIndex(MODE_MAP_FILES);
			}
			cooperate(i);
			reportProgress(progress, i + 1, total);
			final File sourceFile = mindmapFiles.get(i);
			final String sourcePath = pathKey(sourceFile);
			if (sourcePath == null) {
				continue;
			}
			final List<String> targets = scanFileLinks(sourceFile, saxSession);
			for (int j = 0; j < targets.size(); j++) {
				final String targetPath = targets.get(j);
				if (targetPath == null || sourcePath.equals(targetPath)) {
					continue;
				}
				if (!nodesByPath.containsKey(targetPath)) {
					nodesByPath.put(targetPath, RelationshipGraphNode.forMapFile(new File(targetPath)));
				}
				final String edgeKey = sourcePath + "->" + targetPath;
				if (edgeKeys.add(edgeKey)) {
					edges.add(new RelationshipGraphEdge(nodesByPath.get(sourcePath), nodesByPath.get(targetPath)));
				}
			}
		}

		reportProgress(progress, total, total);
		return new RelationshipGraphIndex(new ArrayList<RelationshipGraphNode>(nodesByPath.values()), edges,
		        nodesByPath.size(), MODE_MAP_FILES);
	}

	public static RelationshipGraphIndex scanNodeLinks() {
		return scanNodeLinks(null);
	}

	public static RelationshipGraphIndex scanNodeLinks(final ProgressListener progress) {
		final List<File> mindmapFiles = collectMindmapFiles();
		final int total = mindmapFiles.size();
		final Map<String, RelationshipGraphNode> nodesByKey = new HashMap<String, RelationshipGraphNode>();
		final List<RelationshipGraphEdge> edges = new ArrayList<RelationshipGraphEdge>();
		final Set<String> edgeKeys = new HashSet<String>();
		int totalNodeRefs = 0;
		SaxSession saxSession = null;

		try {
			saxSession = new SaxSession();
		}
		catch (final Exception e) {
			LogUtils.warn("Relationship graph: SAX parser init failed", e);
			return emptyIndex(MODE_MAP_NODES);
		}

		for (int i = 0; i < mindmapFiles.size(); i++) {
			if (isCancelled()) {
				return emptyIndex(MODE_MAP_NODES);
			}
			cooperate(i);
			reportProgress(progress, i + 1, total);
			final File file = mindmapFiles.get(i);
			final FileScanResult result = scanNodeLinksInFile(file, saxSession);
			totalNodeRefs += result.nodeTexts.size();
			final String sourceFileKey = pathKey(file);
			if (sourceFileKey == null) {
				continue;
			}
			for (int j = 0; j < result.edges.size(); j++) {
				final PendingEdge pending = (PendingEdge) result.edges.get(j);
				final RelationshipGraphNode source = ensureNode(nodesByKey, file, sourceFileKey, pending.sourceId,
				        result.nodeTexts);
				final RelationshipGraphNode target = resolveTargetNode(nodesByKey, file, pending, result.nodeTexts);
				if (source == null || target == null || source == target) {
					continue;
				}
				final String edgeKey = source.getPathKey() + "->" + target.getPathKey();
				if (edgeKeys.add(edgeKey)) {
					edges.add(new RelationshipGraphEdge(source, target));
				}
			}
		}

		reportProgress(progress, total, total);
		if (nodesByKey.size() > MAX_NODE_GRAPH_NODES) {
			return trimNodeGraph(nodesByKey, edges, totalNodeRefs);
		}
		return new RelationshipGraphIndex(new ArrayList<RelationshipGraphNode>(nodesByKey.values()), edges, totalNodeRefs,
		        MODE_MAP_NODES);
	}

	/**
	 * Bipartite graph: tag hubs ↔ mind-map nodes that carry those tags.
	 */
	public static RelationshipGraphIndex scanTagLinks() {
		return scanTagLinks(null);
	}

	public static RelationshipGraphIndex scanTagLinks(final ProgressListener progress) {
		if (isCancelled()) {
			return emptyIndex(MODE_TAGS);
		}
		reportProgress(progress, 1, 2);
		final List entries = NodeDetailsTagScanner.scanAllProjects();
		if (isCancelled()) {
			return emptyIndex(MODE_TAGS);
		}
		reportProgress(progress, 2, 2);

		final Map tagNodes = new HashMap();
		final Map mindNodes = new HashMap();
		final List edges = new ArrayList();
		final Set edgeKeys = new HashSet();
		int taggedNodeCount = 0;

		for (int i = 0; i < entries.size(); i++) {
			if (isCancelled()) {
				return emptyIndex(MODE_TAGS);
			}
			final NodePinEntry entry = (NodePinEntry) entries.get(i);
			final File mapFile = entry.getMapFile();
			final String nodeId = entry.getNodeId();
			if (mapFile == null || nodeId == null) {
				continue;
			}
			final String mindKey = entry.getKey();
			RelationshipGraphNode mindNode = (RelationshipGraphNode) mindNodes.get(mindKey);
			if (mindNode == null) {
				mindNode = RelationshipGraphNode.forMapNode(mapFile, nodeId, entry.getListNodeLabel());
				mindNodes.put(mindKey, mindNode);
				taggedNodeCount++;
			}
			for (final Iterator it = entry.getTags().iterator(); it.hasNext();) {
				final String tag = (String) it.next();
				if (tag == null || tag.length() == 0) {
					continue;
				}
				if (NodeDetailsTagUtils.PIN_TAG.equals(tag)) {
					continue;
				}
				RelationshipGraphNode tagNode = (RelationshipGraphNode) tagNodes.get(tag);
				if (tagNode == null) {
					tagNode = RelationshipGraphNode.forTag(tag);
					tagNodes.put(tag, tagNode);
				}
				final String edgeKey = tagNode.getPathKey() + "->" + mindNode.getPathKey();
				if (edgeKeys.add(edgeKey)) {
					edges.add(new RelationshipGraphEdge(tagNode, mindNode));
				}
			}
		}

		final List nodes = new ArrayList();
		nodes.addAll(tagNodes.values());
		nodes.addAll(mindNodes.values());
		return new RelationshipGraphIndex(nodes, edges, taggedNodeCount + tagNodes.size(), MODE_TAGS);
	}

	private static void reportProgress(final ProgressListener progress, final int scanned, final int total) {
		if (progress == null || total <= 0) {
			return;
		}
		if (scanned == total || scanned == 1 || scanned % 25 == 0) {
			progress.onProgress(scanned, total);
		}
	}

	private static void cooperate(final int fileIndex) {
		if (fileIndex % YIELD_EVERY_N_FILES == 0) {
			Thread.yield();
			try {
				Thread.sleep(1);
			}
			catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static boolean isCancelled() {
		return Thread.currentThread().isInterrupted();
	}

	private static RelationshipGraphIndex trimNodeGraph(final Map<String, RelationshipGraphNode> nodesByKey,
	        final List<RelationshipGraphEdge> edges, final int totalNodeRefs) {
		final List<RelationshipGraphEdge> sortedEdges = new ArrayList<RelationshipGraphEdge>(edges);
		final Map<String, Integer> degree = new HashMap<String, Integer>();
		for (int i = 0; i < sortedEdges.size(); i++) {
			final RelationshipGraphEdge edge = sortedEdges.get(i);
			increment(degree, edge.getSource().getPathKey());
			increment(degree, edge.getTarget().getPathKey());
		}
		final Set<String> keptKeys = new HashSet<String>();
		for (int i = 0; i < sortedEdges.size() && keptKeys.size() < MAX_NODE_GRAPH_NODES; i++) {
			final RelationshipGraphEdge edge = sortedEdges.get(i);
			keptKeys.add(edge.getSource().getPathKey());
			keptKeys.add(edge.getTarget().getPathKey());
		}
		final List<RelationshipGraphNode> keptNodes = new ArrayList<RelationshipGraphNode>();
		final List<RelationshipGraphEdge> keptEdges = new ArrayList<RelationshipGraphEdge>();
		for (int i = 0; i < sortedEdges.size(); i++) {
			final RelationshipGraphEdge edge = sortedEdges.get(i);
			if (keptKeys.contains(edge.getSource().getPathKey()) && keptKeys.contains(edge.getTarget().getPathKey())) {
				keptEdges.add(edge);
			}
		}
		for (final Object keyObj : keptKeys) {
			final String key = (String) keyObj;
			keptNodes.add(nodesByKey.get(key));
		}
		return new RelationshipGraphIndex(keptNodes, keptEdges, totalNodeRefs, MODE_MAP_NODES);
	}

	private static void increment(final Map<String, Integer> degree, final String key) {
		final Integer old = degree.get(key);
		degree.put(key, old == null ? Integer.valueOf(1) : Integer.valueOf(old.intValue() + 1));
	}

	private static RelationshipGraphNode ensureNode(final Map<String, RelationshipGraphNode> nodesByKey, final File file,
	        final String fileKey, final String nodeId, final Map nodeTexts) {
		if (nodeId == null || nodeId.length() == 0) {
			return null;
		}
		final String key = fileKey + "#" + nodeId;
		RelationshipGraphNode node = nodesByKey.get(key);
		if (node == null) {
			node = RelationshipGraphNode.forMapNode(file, nodeId, (String) nodeTexts.get(nodeId));
			nodesByKey.put(key, node);
		}
		return node;
	}

	private static RelationshipGraphNode resolveTargetNode(final Map<String, RelationshipGraphNode> nodesByKey,
	        final File sourceFile, final PendingEdge pending, final Map nodeTexts) {
		if (pending.targetNodeId == null || pending.targetNodeId.length() == 0) {
			return null;
		}
		File targetFile = pending.targetFile != null ? pending.targetFile : sourceFile;
		final String targetFileKey = pathKey(targetFile);
		if (targetFileKey == null) {
			return null;
		}
		return ensureNode(nodesByKey, targetFile, targetFileKey, pending.targetNodeId, nodeTexts);
	}

	private static FileScanResult scanNodeLinksInFile(final File file, final SaxSession saxSession) {
		final FileScanResult result = new FileScanResult();
		try {
			saxSession.parser.parse(file, new DefaultHandler() {
				private final Stack currentNodeIds = new Stack();
				private String currentNodeId;
				private String currentNodeText;

				public void startElement(final String uri, final String localName, final String qName,
				        final Attributes attributes) {
					if ("node".equals(qName)) {
						currentNodeId = attributes.getValue("ID");
						currentNodeText = plainText(attributes.getValue("TEXT"));
						if (currentNodeId != null) {
							currentNodeIds.push(currentNodeId);
							if (currentNodeText != null && currentNodeText.length() > 0) {
								result.nodeTexts.put(currentNodeId, currentNodeText);
							}
							final String link = attributes.getValue("LINK");
							if (link != null && link.trim().length() > 0) {
								addLinkEdge(link.trim());
							}
						}
					}
					else if ("arrowlink".equals(qName) && currentNodeId != null) {
						final String destination = attributes.getValue("DESTINATION");
						if (destination != null && destination.length() > 0) {
							result.edges.add(new PendingEdge(currentNodeId, destination, null));
						}
					}
				}

				public void endElement(final String uri, final String localName, final String qName) {
					if ("node".equals(qName) && !currentNodeIds.isEmpty()) {
						currentNodeIds.pop();
						currentNodeId = currentNodeIds.isEmpty() ? null : (String) currentNodeIds.peek();
					}
				}

				private void addLinkEdge(final String link) {
					if (link.startsWith("#")) {
						result.edges.add(new PendingEdge(currentNodeId, link.substring(1), null));
						return;
					}
					final String lower = link.toLowerCase();
					if (lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("mailto:")
					        || lower.startsWith("file:")) {
						return;
					}
					final int hashIndex = link.indexOf('#');
					String pathPart = hashIndex >= 0 ? link.substring(0, hashIndex) : link;
					final String nodePart = hashIndex >= 0 ? link.substring(hashIndex + 1) : null;
					if (nodePart == null || nodePart.length() == 0) {
						return;
					}
					if (pathPart.length() == 0) {
						result.edges.add(new PendingEdge(currentNodeId, nodePart, null));
						return;
					}
					if (!pathPart.toLowerCase().endsWith(".mm")) {
						return;
					}
					File targetFile;
					if (pathPart.startsWith("/") || (pathPart.length() > 2 && pathPart.charAt(1) == ':')) {
						targetFile = new File(pathPart);
					}
					else {
						targetFile = new File(file.getParentFile(), pathPart);
					}
					result.edges.add(new PendingEdge(currentNodeId, nodePart, targetFile));
				}
			});
		}
		catch (final Exception e) {
			LogUtils.warn("Relationship graph: failed to scan nodes in " + file.getPath(), e);
		}
		return result;
	}

	private static String plainText(final String text) {
		if (text == null) {
			return "";
		}
		if (text.indexOf('<') >= 0) {
			return HtmlUtils.htmlToPlain(text);
		}
		return text;
	}

	private static List<String> scanFileLinks(final File file, final SaxSession saxSession) {
		final List<String> targets = new ArrayList<String>();
		try {
			saxSession.parser.parse(file, new DefaultHandler() {
				public void startElement(final String uri, final String localName, final String qName,
				        final Attributes attributes) {
					if (!"node".equals(qName)) {
						return;
					}
					final String link = attributes.getValue("LINK");
					if (link == null || link.trim().length() == 0) {
						return;
					}
					final String targetPath = resolveLinkToMindmapPath(file, link.trim());
					if (targetPath != null) {
						targets.add(targetPath);
					}
				}
			});
		}
		catch (final Exception e) {
			LogUtils.warn("Relationship graph: failed to scan " + file.getPath(), e);
		}
		return targets;
	}

	private static String resolveLinkToMindmapPath(final File sourceFile, final String link) {
		if (link.startsWith("#")) {
			return null;
		}
		final String lower = link.toLowerCase();
		if (lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("mailto:")
		        || lower.startsWith("file:")) {
			return null;
		}
		String pathPart = link;
		final int hashIndex = pathPart.indexOf('#');
		if (hashIndex >= 0) {
			pathPart = pathPart.substring(0, hashIndex);
		}
		if (pathPart.length() == 0) {
			return null;
		}
		if (!pathPart.toLowerCase().endsWith(".mm")) {
			return null;
		}
		File target;
		if (pathPart.startsWith("/") || (pathPart.length() > 2 && pathPart.charAt(1) == ':')) {
			target = new File(pathPart);
		}
		else {
			target = new File(sourceFile.getParentFile(), pathPart);
		}
		return pathKey(target);
	}

	private static RelationshipGraphIndex emptyIndex(final int mode) {
		return new RelationshipGraphIndex(new ArrayList<RelationshipGraphNode>(), new ArrayList<RelationshipGraphEdge>(), 0,
		        mode);
	}

	private static List<File> collectMindmapFiles() {
		waitForMindMapCache();
		if (WorkspaceSideTabScanCache.isMindMapScanComplete()) {
			final List<File> cached = WorkspaceSideTabScanCache.getMindMapFilesSnapshot();
			if (cached != null && !cached.isEmpty()) {
				return cached;
			}
		}
		final List<File> files = new ArrayList<File>();
		MindMapDataRootResolver.collectMindmapFiles(files);
		return files;
	}

	/** Reuse workspace preload instead of starting a competing full-disk walk. */
	private static void waitForMindMapCache() {
		if (WorkspaceSideTabScanCache.isMindMapScanComplete()) {
			return;
		}
		WorkspaceSideTabScanCache.schedulePreload();
		final long deadline = System.currentTimeMillis() + CACHE_WAIT_MS;
		while (!WorkspaceSideTabScanCache.isMindMapScanComplete() && System.currentTimeMillis() < deadline) {
			if (isCancelled()) {
				return;
			}
			try {
				Thread.sleep(250);
			}
			catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	/** Fast path key; avoids getCanonicalFile on every node during large scans. */
	private static String pathKey(final File file) {
		if (file == null) {
			return null;
		}
		final String path = file.getAbsolutePath();
		if (path.length() == 0) {
			return null;
		}
		return path.replace('\\', '/');
	}

	private static final class SaxSession {
		private final SAXParser parser;

		private SaxSession() throws Exception {
			final SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(false);
			parser = factory.newSAXParser();
		}
	}

	private static final class FileScanResult {
		private final Map nodeTexts = new HashMap();
		private final List edges = new ArrayList();
	}

	private static final class PendingEdge {
		private final String sourceId;
		private final String targetNodeId;
		private final File targetFile;

		private PendingEdge(final String sourceId, final String targetNodeId, final File targetFile) {
			this.sourceId = sourceId;
			this.targetNodeId = targetNodeId;
			this.targetFile = targetFile;
		}
	}
}

