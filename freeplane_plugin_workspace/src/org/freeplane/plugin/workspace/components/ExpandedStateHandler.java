package org.freeplane.plugin.workspace.components;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.plugin.workspace.io.IFileSystemRepresentation;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;
import org.freeplane.plugin.workspace.model.WorkspaceModel;
import org.freeplane.plugin.workspace.model.WorkspaceModelEvent;
import org.freeplane.plugin.workspace.model.WorkspaceModelListener;
import org.freeplane.plugin.workspace.model.WorkspaceTreeModel;
import org.freeplane.plugin.workspace.nodes.WorkspaceRootNode;

/**
 * Remembers which workspace folders are expanded, restores them after refresh /
 * restart, and persists keys under {@code workspace-expanded.properties}.
 * <p>
 * Collapse events fired by model reload must not erase remembered paths — use
 * {@link #beginStructuralUpdate()} / {@link #endStructuralUpdate()}.
 */
public class ExpandedStateHandler implements TreeExpansionListener, WorkspaceModelListener {

	private static final String FILE_NAME = "workspace-expanded.properties";
	private static final String CHARSET = "UTF-8";
	private static final String PROP_EXPANDED = "expanded";
	/** Pipe is safe in file URIs and avoids Properties newline escaping issues. */
	private static final String SEPARATOR = "|";
	private static final int SAVE_DELAY_MS = 300;

	private final Set expandedNodeKeys = Collections.synchronizedSet(new LinkedHashSet());
	private final TreeView treeView;
	private final Timer saveTimer;
	private boolean restoring;
	private int structuralUpdateDepth;
	private boolean loaded;

	public ExpandedStateHandler(final TreeView treeView) {
		this.treeView = treeView;
		saveTimer = new Timer(SAVE_DELAY_MS, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				save();
			}
		});
		saveTimer.setRepeats(false);
		ensureLoaded();
	}

	/** Ignore expand/collapse side-effects while the tree model is being rebuilt. */
	public void beginStructuralUpdate() {
		structuralUpdateDepth++;
	}

	public void endStructuralUpdate() {
		if (structuralUpdateDepth > 0) {
			structuralUpdateDepth--;
		}
	}

	public boolean isTrackingSuspended() {
		return restoring || structuralUpdateDepth > 0 || !treeView.isPaintingEnabled();
	}

	public void treeExpanded(final TreeExpansionEvent event) {
		if (isTrackingSuspended()) {
			return;
		}
		final AWorkspaceTreeNode node = (AWorkspaceTreeNode) event.getPath().getLastPathComponent();
		final String key = expansionKey(node);
		if (key != null && isPersistable(node)) {
			expandedNodeKeys.add(key);
			scheduleSave();
		}
	}

	public void treeCollapsed(final TreeExpansionEvent event) {
		if (isTrackingSuspended()) {
			return;
		}
		final AWorkspaceTreeNode node = (AWorkspaceTreeNode) event.getPath().getLastPathComponent();
		final String key = expansionKey(node);
		if (key != null) {
			expandedNodeKeys.remove(key);
			expandedNodeKeys.remove(node.getKey());
			expandedNodeKeys.remove("node:" + node.getKey());
			scheduleSave();
		}
	}

	public void treeNodesChanged(final TreeModelEvent e) {
	}

	public void treeNodesInserted(final TreeModelEvent e) {
	}

	public void treeNodesRemoved(final TreeModelEvent e) {
	}

	public void treeStructureChanged(final TreeModelEvent e) {
		// Do not re-expand here: folder lazy-load fires structure changes on model paths,
		// while JTree uses hoisted display paths — expandPath(modelPath) blanks the tree.
	}

	public void projectAdded(final WorkspaceModelEvent event) {
	}

	public void projectRemoved(final WorkspaceModelEvent event) {
	}

	public void registerModel(final WorkspaceModel model) {
		if (model == null) {
			return;
		}
		model.removeTreeModelListener(this);
		model.addWorldModelListener(this);
	}

	public void restoreExpandedStates() {
		final WorkspaceTreeModel model = resolveModel();
		if (model != null) {
			setExpandedStates(model, false);
		}
	}

	/** Restore now, then once more on the EDT after lazy folder loads settle. */
	public void restoreExpandedStatesDeferred() {
		beginStructuralUpdate();
		try {
			restoreExpandedStates();
		}
		finally {
			// Keep suspended until the deferred pass finishes so collapse events
			// from lazy reload do not wipe remembered keys.
		}
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					restoreExpandedStates();
				}
				finally {
					endStructuralUpdate();
					// Nested begin from refreshView: keep counter balanced if caller also began.
				}
			}
		});
	}

	public void setExpandedStates(final WorkspaceTreeModel targetModel, final boolean cleanInvalidEntries) {
		if (targetModel == null) {
			return;
		}
		ensureLoaded();
		final AWorkspaceTreeNode root = targetModel.getRoot();
		if (root == null) {
			return;
		}
		final List keys;
		synchronized (expandedNodeKeys) {
			keys = new ArrayList(expandedNodeKeys);
		}
		if (keys.isEmpty()) {
			return;
		}
		sortByDepth(keys);
		final boolean wasRestoring = restoring;
		restoring = true;
		try {
			final Set remaining = new LinkedHashSet(keys);
			boolean progressed = true;
			int guard = 0;
			while (progressed && !remaining.isEmpty() && guard++ < 48) {
				progressed = false;
				for (final Iterator it = remaining.iterator(); it.hasNext();) {
					final String key = (String) it.next();
					AWorkspaceTreeNode node = targetModel.getNode(key);
					if (node == null && key.startsWith("node:")) {
						node = targetModel.getNode(key.substring("node:".length()));
					}
					if (node == null) {
						node = findNodeByExpansionKey(root, key);
					}
					if (node == null) {
						continue;
					}
					final TreePath displayPath = treeView.getDisplayTreePath(node);
					if (displayPath == null) {
						continue;
					}
					treeView.expandPath(displayPath);
					if (treeView.isPathExpanded(displayPath)) {
						it.remove();
						progressed = true;
					}
					else if (node.getChildCount() > 0 || node.getAllowsChildren()) {
						// Children may have just been lazy-loaded; retry next pass.
						progressed = true;
					}
				}
			}
			if (cleanInvalidEntries && !remaining.isEmpty()) {
				synchronized (expandedNodeKeys) {
					expandedNodeKeys.removeAll(remaining);
				}
				scheduleSave();
			}
		}
		catch (final Exception e) {
			LogUtils.warn("Exception in ExpandedStateHandler.setExpandedStates: aborted", e);
		}
		finally {
			restoring = wasRestoring;
		}
	}

	private WorkspaceTreeModel resolveModel() {
		try {
			final Object root = treeView.getModelRoot();
			if (root instanceof AWorkspaceTreeNode) {
				return ((AWorkspaceTreeNode) root).getModel();
			}
		}
		catch (final Exception ignore) {
		}
		return null;
	}

	static String expansionKey(final AWorkspaceTreeNode node) {
		if (node == null) {
			return null;
		}
		if (node instanceof IFileSystemRepresentation) {
			final File file = ((IFileSystemRepresentation) node).getFile();
			if (file != null) {
				return normalizeFileKey(file);
			}
		}
		return "node:" + node.getKey();
	}

	private static String normalizeFileKey(final File file) {
		try {
			return file.getCanonicalFile().toURI().toString();
		}
		catch (final Exception e) {
			return file.getAbsoluteFile().toURI().toString();
		}
	}

	private static boolean isPersistable(final AWorkspaceTreeNode node) {
		return node != null && !(node instanceof WorkspaceRootNode);
	}

	private static void sortByDepth(final List keys) {
		Collections.sort(keys, new Comparator() {
			public int compare(final Object a, final Object b) {
				final String sa = String.valueOf(a);
				final String sb = String.valueOf(b);
				final int da = depthHint(sa);
				final int db = depthHint(sb);
				if (da != db) {
					return da - db;
				}
				return sa.compareTo(sb);
			}
		});
	}

	private static int depthHint(final String key) {
		if (key == null) {
			return 0;
		}
		int depth = 0;
		for (int i = 0; i < key.length(); i++) {
			final char c = key.charAt(i);
			if (c == '/' || c == '\\') {
				depth++;
			}
		}
		return depth;
	}

	private static AWorkspaceTreeNode findNodeByExpansionKey(final AWorkspaceTreeNode node, final String key) {
		if (node == null || key == null) {
			return null;
		}
		if (matchesKey(node, key)) {
			return node;
		}
		final int count = node.getModelChildCount();
		for (int i = 0; i < count; i++) {
			final AWorkspaceTreeNode found = findNodeByExpansionKey(node.getModelChildAt(i), key);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static boolean matchesKey(final AWorkspaceTreeNode node, final String key) {
		if (key.equals(expansionKey(node)) || key.equals(node.getKey()) || key.equals("node:" + node.getKey())) {
			return true;
		}
		if (key.startsWith("file:") && node instanceof IFileSystemRepresentation) {
			final File file = ((IFileSystemRepresentation) node).getFile();
			if (file != null) {
				try {
					final File fromKey = new File(java.net.URI.create(key));
					return file.getCanonicalFile().equals(fromKey.getCanonicalFile());
				}
				catch (final Exception ignore) {
				}
			}
		}
		return false;
	}

	private void scheduleSave() {
		saveTimer.restart();
	}

	private void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (this) {
			if (loaded) {
				return;
			}
			load();
			loaded = true;
		}
	}

	private File resolveFile() {
		final File dir = MindMapDataRootResolver.getApplicationConfigDirectory();
		if (dir == null) {
			return null;
		}
		if (!dir.exists() && !dir.mkdirs()) {
			LogUtils.warn("Could not create workspace expanded-state dir: " + dir.getAbsolutePath());
			return null;
		}
		return new File(dir, FILE_NAME);
	}

	private void load() {
		final File file = resolveFile();
		if (file == null || !file.isFile()) {
			return;
		}
		final Properties props = new Properties();
		InputStreamReader reader = null;
		try {
			reader = new InputStreamReader(new FileInputStream(file), CHARSET);
			props.load(reader);
			final String raw = props.getProperty(PROP_EXPANDED, "");
			if (raw.length() == 0) {
				return;
			}
			final String[] parts = raw.split("[|\\n,]");
			synchronized (expandedNodeKeys) {
				for (int i = 0; i < parts.length; i++) {
					final String key = parts[i].trim();
					if (key.length() > 0) {
						expandedNodeKeys.add(key);
					}
				}
			}
			LogUtils.info("Workspace expanded state loaded: " + expandedNodeKeys.size() + " path(s) from "
			        + file.getAbsolutePath());
		}
		catch (final Exception e) {
			LogUtils.warn("Could not load workspace expanded state from " + file.getAbsolutePath(), e);
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (final Exception ignore) {
				}
			}
		}
	}

	private void save() {
		final File file = resolveFile();
		if (file == null) {
			return;
		}
		final StringBuilder builder = new StringBuilder();
		synchronized (expandedNodeKeys) {
			for (final Iterator it = expandedNodeKeys.iterator(); it.hasNext();) {
				final String key = (String) it.next();
				if (key == null || key.length() == 0) {
					continue;
				}
				if (builder.length() > 0) {
					builder.append(SEPARATOR);
				}
				builder.append(key);
			}
		}
		final Properties props = new Properties();
		props.setProperty(PROP_EXPANDED, builder.toString());
		OutputStreamWriter writer = null;
		try {
			writer = new OutputStreamWriter(new FileOutputStream(file), CHARSET);
			props.store(writer, "Docear workspace expanded folders");
		}
		catch (final Exception e) {
			LogUtils.warn("Could not save workspace expanded state to " + file.getAbsolutePath(), e);
		}
		finally {
			if (writer != null) {
				try {
					writer.close();
				}
				catch (final Exception ignore) {
				}
			}
		}
	}
}
