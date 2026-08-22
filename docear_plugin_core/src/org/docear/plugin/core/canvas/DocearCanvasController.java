package org.docear.plugin.core.canvas;

import java.awt.EventQueue;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.IMenuContributor;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.main.application.DocumentTabSupport;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.event.WorkspaceActionEvent;
import org.freeplane.plugin.workspace.features.AWorkspaceModeExtension;
import org.freeplane.plugin.workspace.handler.WorkspaceDocumentOpenRegistry;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;

/** Opens and tracks JSON Canvas document tabs. */
public final class DocearCanvasController implements IExtension {

	private static final int WORKSPACE_REGISTER_MAX_ATTEMPTS = 50;
	private static DocearCanvasController instance;
	private final Map<String, JsonCanvasDocumentView> openViews = new HashMap<String, JsonCanvasDocumentView>();
	private final WorkspaceDocumentOpenRegistry.Handler documentOpenHandler = new CanvasDocumentOpenHandler();
	private boolean workspaceListenersRegistered;

	private DocearCanvasController() {
	}

	public static DocearCanvasController getController() {
		return instance;
	}

	public static void install(final ModeController modeController) {
		instance = new DocearCanvasController();
		modeController.addExtension(DocearCanvasController.class, instance);
		WorkspaceDocumentOpenRegistry.registerHandler(instance.documentOpenHandler);
		final NewCanvasAction newAction = new NewCanvasAction();
		modeController.addAction(newAction);
		modeController.addMenuContributor(new IMenuContributor() {
			public void updateMenus(final ModeController mc, final MenuBuilder builder) {
				try {
					builder.addAction("/menu_bar/file", newAction, MenuBuilder.AS_CHILD);
				}
				catch (Throwable t) {
					LogUtils.warn("Canvas: could not add File menu item", t);
				}
			}
		});
		scheduleWorkspaceRegistration(modeController, 0);
		LogUtils.info("Docear JSON Canvas integration installed.");
	}

	public static boolean isCanvasFile(final File file) {
		if (file == null) {
			return false;
		}
		return file.getName().toLowerCase().endsWith(".canvas");
	}

	public JsonCanvasDocumentView findOpenView(final File file) {
		if (file == null) {
			return null;
		}
		return openViews.get(key(file));
	}

	public void openCanvasFile(final File file) {
		if (file == null) {
			return;
		}
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				openCanvasFileOnEdt(file);
			}
		});
	}

	public void createNewCanvas() {
		final File dir = defaultDir();
		final JFileChooser chooser = new JFileChooser(dir);
		chooser.setDialogTitle("新建画板");
		chooser.setFileFilter(new FileNameExtensionFilter("JSON Canvas (*.canvas)", "canvas"));
		chooser.setSelectedFile(new File(dir, "未命名.canvas"));
		if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase().endsWith(".canvas")) {
			file = new File(file.getParentFile(), file.getName() + ".canvas");
		}
		try {
			if (!file.exists()) {
				final JsonCanvasDocument doc = new JsonCanvasDocument();
				doc.getNodes().add(JsonCanvasNode.text(JsonCanvasDocument.newId(), 80, 80, "新画板"));
				JsonCanvasIo.write(file, doc);
			}
			openCanvasFile(file);
		}
		catch (Exception e) {
			LogUtils.warn("Canvas: create failed", e);
		}
	}

	public void exportGraphAsCanvas(final File file, final JsonCanvasDocument doc) {
		try {
			JsonCanvasIo.write(file, doc);
			openCanvasFile(file);
		}
		catch (Exception e) {
			LogUtils.warn("Canvas: export graph failed", e);
		}
	}

	public void unregisterView(final JsonCanvasDocumentView view) {
		if (view == null || view.getFile() == null) {
			return;
		}
		openViews.remove(key(view.getFile()));
	}

	private void openCanvasFileOnEdt(final File file) {
		final JsonCanvasDocumentView existing = findOpenView(file);
		if (existing != null) {
			DocumentTabSupport.selectDocumentTab(existing);
			return;
		}
		try {
			if (!file.exists()) {
				JsonCanvasIo.write(file, new JsonCanvasDocument());
			}
			final JsonCanvasDocumentView view = new JsonCanvasDocumentView(file);
			openViews.put(key(file), view);
			DocumentTabSupport.openDocumentTab(view);
		}
		catch (Exception e) {
			LogUtils.warn("Could not open canvas: " + file, e);
		}
	}

	private static String key(final File file) {
		try {
			return file.getCanonicalFile().getAbsolutePath();
		}
		catch (Exception e) {
			return file.getAbsolutePath();
		}
	}

	public static File defaultDir() {
		try {
			final MapModel map = Controller.getCurrentController().getMap();
			if (map != null && map.getFile() != null && map.getFile().getParentFile() != null) {
				return map.getFile().getParentFile();
			}
		}
		catch (Exception ignore) {
		}
		final File home = new File(System.getProperty("user.home"), ".docear/canvases");
		home.mkdirs();
		return home;
	}

	private static void scheduleWorkspaceRegistration(final ModeController modeController, final int attempt) {
		final AWorkspaceModeExtension workspace = WorkspaceController.getModeExtension(modeController);
		if (workspace != null && instance != null) {
			instance.registerWorkspaceListeners(workspace);
			return;
		}
		if (attempt >= WORKSPACE_REGISTER_MAX_ATTEMPTS) {
			LogUtils.warn("Canvas: workspace extension not available; using document registry only.");
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
		try {
			workspace.getIOController().registerNodeActionListener(AWorkspaceTreeNode.class,
					WorkspaceActionEvent.WSNODE_OPEN_DOCUMENT, new CanvasWorkspaceListener());
			workspaceListenersRegistered = true;
		}
		catch (RuntimeException e) {
			LogUtils.warn("Canvas: failed to register workspace listeners", e);
		}
	}

	private final class CanvasDocumentOpenHandler implements WorkspaceDocumentOpenRegistry.Handler {
		public boolean canOpen(final File file) {
			return isCanvasFile(file);
		}

		public void open(final File file) {
			openCanvasFile(file);
		}
	}

	public static final class NewCanvasAction extends AFreeplaneAction {
		private static final long serialVersionUID = 1L;

		public NewCanvasAction() {
			super("NewCanvasAction", "新建画板", null);
		}

		public void actionPerformed(final java.awt.event.ActionEvent e) {
			if (getController() != null) {
				getController().createNewCanvas();
			}
		}
	}
}
