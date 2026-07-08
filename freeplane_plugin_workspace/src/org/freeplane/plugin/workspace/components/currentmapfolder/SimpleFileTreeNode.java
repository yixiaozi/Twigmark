package org.freeplane.plugin.workspace.components.currentmapfolder;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * Lazy file-system tree node; lists all entries including hidden files.
 */
final class SimpleFileTreeNode extends DefaultMutableTreeNode {

	private static final long serialVersionUID = 1L;

	private static final Comparator<File> FILE_ORDER = new Comparator<File>() {
		public int compare(final File a, final File b) {
			if (a.isDirectory() != b.isDirectory()) {
				return a.isDirectory() ? -1 : 1;
			}
			return a.getName().compareToIgnoreCase(b.getName());
		}
	};

	private final File file;
	private boolean childrenLoaded;

	SimpleFileTreeNode(final File file) {
		super(file.getName());
		this.file = file;
		if (file.isDirectory()) {
			addPlaceholder();
		}
	}

	File getFile() {
		return file;
	}

	boolean isChildrenLoaded() {
		return childrenLoaded;
	}

	void loadChildren(final DefaultTreeModel model) {
		if (childrenLoaded || !file.isDirectory()) {
			return;
		}
		removeAllChildren();
		final File[] entries = file.listFiles();
		if (entries != null) {
			Arrays.sort(entries, FILE_ORDER);
			for (int i = 0; i < entries.length; i++) {
				add(new SimpleFileTreeNode(entries[i]));
			}
		}
		childrenLoaded = true;
		if (model != null) {
			model.nodeStructureChanged(this);
		}
	}

	void refresh(final DefaultTreeModel model) {
		if (!file.isDirectory()) {
			return;
		}
		childrenLoaded = false;
		removeAllChildren();
		addPlaceholder();
		if (model != null) {
			model.nodeStructureChanged(this);
		}
	}

	@Override
	public boolean isLeaf() {
		return !file.isDirectory();
	}

	@Override
	public boolean getAllowsChildren() {
		return file.isDirectory();
	}

	private void addPlaceholder() {
		add(new DefaultMutableTreeNode(""));
	}
}
