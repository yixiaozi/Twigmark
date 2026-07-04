package org.docear.plugin.drawio;

import java.awt.EventQueue;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.docear.plugin.drawio.listener.DrawioWorkspaceListener;
import org.docear.plugin.drawio.ui.DrawioDocumentView;
import org.freeplane.core.extension.IExtension;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.ModeController;
import org.freeplane.main.application.DocumentTabSupport;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.event.WorkspaceActionEvent;
import org.freeplane.plugin.workspace.features.AWorkspaceModeExtension;
import org.freeplane.plugin.workspace.handler.WorkspaceDocumentOpenRegistry;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;

public final class DocearDrawioController implements IExtension {

	private static final int WORKSPACE_REGISTER_MAX_ATTEMPTS = 50;
	private static DocearDrawioController instance;
	private final Map<String, DrawioDocumentView> openViews = new HashMap<String, DrawioDocumentView>();
	private final WorkspaceDocumentOpenRegistry.Handler documentOpenHandler = new DrawioDocumentOpenHandler();
	private boolean workspaceListenersRegistered;

	private DocearDrawioController() {
	}

	public static DocearDrawioController getController() {
		return instance;
	}

	public static void install(final ModeController modeController) {
		if (!DrawioConfig.isEnabled()) {
			LogUtils.info("Docear Draw.io integration is disabled.");
			return;
		}
		instance = new DocearDrawioController();
		modeController.addExtension(DocearDrawioController.class, instance);
		WorkspaceDocumentOpenRegistry.registerHandler(instance.documentOpenHandler);
		scheduleWorkspaceRegistration(modeController, 0);
		LogUtils.info("Docear Draw.io integration installed.");
	}

	public static void uninstall() {
		if (instance != null) {
			WorkspaceDocumentOpenRegistry.unregisterHandler(instance.documentOpenHandler);
			instance = null;
		}
	}

	private static void scheduleWorkspaceRegistration(final ModeController modeController, final int attempt) {
		final AWorkspaceModeExtension workspace = WorkspaceController.getModeExtension(modeController);
		if (workspace != null) {
			instance.registerWorkspaceListeners(workspace);
			return;
		}
		if (attempt >= WORKSPACE_REGISTER_MAX_ATTEMPTS) {
			LogUtils.warn("Draw.io: workspace extension not available; using document registry only.");
			return;
		}
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				scheduleWorkspaceRegistration(modeController, attempt + 1);
			}
		});
	}

	private void registerWorkspaceListeners(final AWorkspaceModeExtension workspace) {
		if (workspaceListenersRegistered) {
			return;
		}
		final DrawioWorkspaceListener listener = new DrawioWorkspaceListener();
		try {
			workspace.getIOController().registerNodeActionListener(AWorkspaceTreeNode.class,
			        WorkspaceActionEvent.WSNODE_OPEN_DOCUMENT, listener);
			workspaceListenersRegistered = true;
		}
		catch (RuntimeException e) {
			LogUtils.warn("Draw.io: failed to register workspace listeners", e);
		}
	}

	public static boolean isDrawioFile(final File file) {
		if (file == null) {
			return false;
		}
		final String name = file.getName().toLowerCase();
		return name.endsWith(".drawio");
	}

	public DrawioDocumentView findOpenView(final File file) {
		if (file == null) {
			return null;
		}
		return openViews.get(file.getAbsolutePath());
	}

	public void openDrawioFile(final File file) {
		if (file == null || !file.exists()) {
			return;
		}
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				openDrawioFileOnEdt(file);
			}
		});
	}

	private void openDrawioFileOnEdt(final File file) {
		final DrawioDocumentView existing = findOpenView(file);
		if (existing != null) {
			DocumentTabSupport.selectDocumentTab(existing);
			return;
		}
		try {
			final DrawioDocumentView view = new DrawioDocumentView(file);
			openViews.put(file.getAbsolutePath(), view);
			DocumentTabSupport.openDocumentTab(view);
		}
		catch (Exception e) {
			LogUtils.warn("Could not open Draw.io file: " + file, e);
		}
	}

	public void unregisterView(final DrawioDocumentView view) {
		if (view == null || view.getFile() == null) {
			return;
		}
		openViews.remove(view.getFile().getAbsolutePath());
	}

	private final class DrawioDocumentOpenHandler implements WorkspaceDocumentOpenRegistry.Handler {
		public boolean canOpen(final File file) {
			return isDrawioFile(file);
		}

		public void open(final File file) {
			openDrawioFile(file);
		}
	}
}
