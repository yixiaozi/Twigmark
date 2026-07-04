package org.docear.plugin.drawio.listener;

import java.io.File;

import org.docear.plugin.core.util.CoreUtils;
import org.docear.plugin.drawio.DocearDrawioController;
import org.freeplane.plugin.workspace.URIUtils;
import org.freeplane.plugin.workspace.event.IWorkspaceNodeActionListener;
import org.freeplane.plugin.workspace.event.WorkspaceActionEvent;
import org.freeplane.plugin.workspace.io.IFileSystemRepresentation;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;
import org.freeplane.plugin.workspace.nodes.ALinkNode;

public class DrawioWorkspaceListener implements IWorkspaceNodeActionListener {

	public void handleAction(final WorkspaceActionEvent event) {
		final DocearDrawioController controller = DocearDrawioController.getController();
		if (controller == null) {
			return;
		}
		final File file = resolveFile(event);
		if (file == null || !DocearDrawioController.isDrawioFile(file)) {
			return;
		}
		if (!file.exists()) {
			event.consume();
			return;
		}
		controller.openDrawioFile(file);
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
