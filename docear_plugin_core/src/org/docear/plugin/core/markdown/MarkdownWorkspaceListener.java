package org.docear.plugin.core.markdown;

import java.io.File;

import org.docear.plugin.core.util.CoreUtils;
import org.freeplane.plugin.workspace.URIUtils;
import org.freeplane.plugin.workspace.event.IWorkspaceNodeActionListener;
import org.freeplane.plugin.workspace.event.WorkspaceActionEvent;
import org.freeplane.plugin.workspace.io.IFileSystemRepresentation;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;
import org.freeplane.plugin.workspace.nodes.ALinkNode;

/** Workspace double-click / open for Markdown files. */
public final class MarkdownWorkspaceListener implements IWorkspaceNodeActionListener {

	public void handleAction(final WorkspaceActionEvent event) {
		final DocearMarkdownController controller = DocearMarkdownController.getController();
		if (controller == null) {
			return;
		}
		final File file = resolveFile(event);
		if (file == null || !DocearMarkdownController.isMarkdownFile(file)) {
			return;
		}
		if (!file.exists()) {
			event.consume();
			return;
		}
		controller.openMarkdownFile(file);
		event.consume();
	}

	private static File resolveFile(final WorkspaceActionEvent event) {
		final Object source = event.getSource();
		if (source instanceof IFileSystemRepresentation) {
			return ((IFileSystemRepresentation) source).getFile();
		}
		if (source instanceof ALinkNode) {
			final File fromLink = URIUtils.getAbsoluteFile(((ALinkNode) source).getLinkURI());
			if (fromLink != null) {
				return fromLink;
			}
			return CoreUtils.resolveURI(((ALinkNode) source).getLinkURI());
		}
		if (source instanceof AWorkspaceTreeNode) {
			return null;
		}
		return null;
	}
}
