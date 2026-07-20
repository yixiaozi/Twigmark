package org.freeplane.plugin.workspace.features.nodepins;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.SwingWorker;
import javax.swing.Timer;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.NodeModel;

public final class NodePinsIndex {

	private static final int RESCAN_DEBOUNCE_MS = 800;
	/**
	 * Full-library SAX rescans thrash disk/CPU on large workspaces and can starve the
	 * MCP HTTP/EDT path. Set {@code -Dmcp.skipFullTagScan=true} on headless MCP servers.
	 */
	private static final String SKIP_FULL_TAG_SCAN_PROP = "mcp.skipFullTagScan";

	private static NodePinsIndex instance;

	private final List entries = new ArrayList();
	private final List changeListeners = new ArrayList();
	private SwingWorker activeWorker;
	private boolean rescanRequested;
	private final Timer rescanDebounceTimer;
	private boolean loggedSkipFullScan;

	private NodePinsIndex() {
		rescanDebounceTimer = new Timer(RESCAN_DEBOUNCE_MS, new ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				rescan();
			}
		});
		rescanDebounceTimer.setRepeats(false);
	}

	public static synchronized NodePinsIndex getInstance() {
		if (instance == null) {
			instance = new NodePinsIndex();
		}
		return instance;
	}

	/** Debounced full-project rescan; coalesces rapid calls. */
	public void scheduleRescan() {
		if (isFullTagScanSkipped()) {
			return;
		}
		rescanDebounceTimer.restart();
	}

	static boolean isFullTagScanSkipped() {
		final String value = System.getProperty(SKIP_FULL_TAG_SCAN_PROP, "");
		return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
	}

	public void updateFromNode(final NodeModel node) {
		if (node == null) {
			return;
		}
		final String key = NodePinKeyUtils.fromNode(node);
		if (key == null) {
			return;
		}
		final String nodeText = node.getText();
		if (!NodeDetailsTagUtils.mayContainBracketTags(nodeText)) {
			removeByKey(key);
			return;
		}
		boolean changed = false;
		synchronized (entries) {
			final Set allTags = NodeDetailsTagUtils.parseAllTags(nodeText);
			if (allTags.isEmpty()) {
				for (final Iterator it = entries.iterator(); it.hasNext();) {
					if (key.equals(((NodePinEntry) it.next()).getKey())) {
						it.remove();
						changed = true;
						break;
					}
				}
			}
			else {
				final boolean pinned = allTags.contains(NodeDetailsTagUtils.PIN_TAG);
				final LinkedHashSet userTags = new LinkedHashSet(allTags);
				userTags.remove(NodeDetailsTagUtils.PIN_TAG);
				final String label = NodeDetailsTagUtils.extractNodeTitle(nodeText);
				final NodePinEntry newEntry = new NodePinEntry(key, userTags, pinned, label);
				boolean found = false;
				for (int i = 0; i < entries.size(); i++) {
					final NodePinEntry existing = (NodePinEntry) entries.get(i);
					if (key.equals(existing.getKey())) {
						if (!sameEntry(existing, newEntry)) {
							entries.set(i, newEntry);
							changed = true;
						}
						found = true;
						break;
					}
				}
				if (!found) {
					entries.add(newEntry);
					changed = true;
				}
			}
		}
		if (changed) {
			fireChanged();
		}
	}

	public void removeByKey(final String key) {
		if (key == null) {
			return;
		}
		boolean changed = false;
		synchronized (entries) {
			for (final Iterator it = entries.iterator(); it.hasNext();) {
				if (key.equals(((NodePinEntry) it.next()).getKey())) {
					it.remove();
					changed = true;
					break;
				}
			}
		}
		if (changed) {
			fireChanged();
		}
	}

	public void rescan() {
		if (isFullTagScanSkipped()) {
			if (!loggedSkipFullScan) {
				loggedSkipFullScan = true;
				LogUtils.info("NodePinsIndex: skipped full tag rescan (" + SKIP_FULL_TAG_SCAN_PROP + "=true)");
			}
			rescanRequested = false;
			return;
		}
		if (activeWorker != null) {
			rescanRequested = true;
			return;
		}
		rescanRequested = false;
		activeWorker = new SwingWorker() {
			protected Object doInBackground() throws Exception {
				return NodeDetailsTagScanner.scanAllProjects();
			}

			protected void done() {
				try {
					final Object result = get();
					if (result instanceof List) {
						synchronized (entries) {
							entries.clear();
							entries.addAll((List) result);
						}
						fireChanged();
					}
				}
				catch (final Exception e) {
					// ignore
				}
				activeWorker = null;
				if (rescanRequested) {
					rescan();
				}
			}
		};
		activeWorker.execute();
	}

	public List getDisplayEntries(final boolean pinsMode, final String tagFilter) {
		synchronized (entries) {
			if (tagFilter == null || tagFilter.length() == 0) {
				final List result = new ArrayList(entries);
				Collections.sort(result, ENTRY_COMPARATOR);
				return result;
			}
			final List result = new ArrayList();
			for (int i = 0; i < entries.size(); i++) {
				final NodePinEntry entry = (NodePinEntry) entries.get(i);
				if (entry.getTags().contains(tagFilter)) {
					result.add(entry);
				}
			}
			Collections.sort(result, ENTRY_COMPARATOR);
			return result;
		}
	}

	public int countAll() {
		synchronized (entries) {
			return entries.size();
		}
	}

	public Set getQuickSelectTags() {
		final LinkedHashSet quickTags = new LinkedHashSet();
		for (int i = 0; i < NodeDetailsTagUtils.PRESET_TAGS.length; i++) {
			quickTags.add(NodeDetailsTagUtils.PRESET_TAGS[i]);
		}
		synchronized (entries) {
			for (int i = 0; i < entries.size(); i++) {
				final NodePinEntry entry = (NodePinEntry) entries.get(i);
				for (final Iterator it = entry.getTags().iterator(); it.hasNext();) {
					final String tag = (String) it.next();
					if (NodeDetailsTagUtils.isValidTagName(tag)) {
						quickTags.add(tag);
					}
				}
			}
		}
		quickTags.remove(NodeDetailsTagUtils.PIN_TAG);
		return quickTags;
	}

	public int countPinned() {
		int count = 0;
		synchronized (entries) {
			for (int i = 0; i < entries.size(); i++) {
				final NodePinEntry entry = (NodePinEntry) entries.get(i);
				if (entry.isPinned() && !entry.getTags().contains(NodeDetailsTagUtils.TAG_ARCHIVED)) {
					count++;
				}
			}
		}
		return count;
	}

	public int countWithTag(final String tag) {
		if (tag == null || tag.length() == 0) {
			return countAll();
		}
		int count = 0;
		synchronized (entries) {
			for (int i = 0; i < entries.size(); i++) {
				final NodePinEntry entry = (NodePinEntry) entries.get(i);
				if (entry.getTags().contains(tag)) {
					count++;
				}
			}
		}
		return count;
	}

	public void addChangeListener(final Runnable listener) {
		if (listener != null && !changeListeners.contains(listener)) {
			changeListeners.add(listener);
		}
	}

	public void removeChangeListener(final Runnable listener) {
		changeListeners.remove(listener);
	}

	private void fireChanged() {
		for (int i = 0; i < changeListeners.size(); i++) {
			((Runnable) changeListeners.get(i)).run();
		}
	}

	private static boolean sameEntry(final NodePinEntry a, final NodePinEntry b) {
		if (a.isPinned() != b.isPinned()) {
			return false;
		}
		if (!a.getListNodeLabel().equals(b.getListNodeLabel())) {
			return false;
		}
		return a.getTags().equals(b.getTags());
	}

	private static final Comparator ENTRY_COMPARATOR = new Comparator() {
		public int compare(final Object o1, final Object o2) {
			final NodePinEntry a = (NodePinEntry) o1;
			final NodePinEntry b = (NodePinEntry) o2;
			final int mapCompare = a.getMapDisplayName().compareTo(b.getMapDisplayName());
			if (mapCompare != 0) {
				return mapCompare;
			}
			return a.getListNodeLabel().compareTo(b.getListNodeLabel());
		}
	};
}
