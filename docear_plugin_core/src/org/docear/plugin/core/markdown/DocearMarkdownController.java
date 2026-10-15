package org.docear.plugin.core.markdown;

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
import org.freeplane.features.ui.DocumentOpenRegistry;
import org.freeplane.main.application.DocumentTabSupport;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.event.WorkspaceActionEvent;
import org.freeplane.plugin.workspace.features.AWorkspaceModeExtension;
import org.freeplane.plugin.workspace.handler.WorkspaceDocumentOpenRegistry;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;

/** Opens Markdown files as document tabs (edit / preview), not as mind maps. */
public final class DocearMarkdownController implements IExtension {

	private static final int WORKSPACE_REGISTER_MAX_ATTEMPTS = 50;
	private static DocearMarkdownController instance;

	private final Map<String, MarkdownDocumentView> openViews = new HashMap<String, MarkdownDocumentView>();
	private final DocumentOpenRegistry.Handler documentOpenHandler = new MarkdownDocumentOpenHandler();
	private final WorkspaceDocumentOpenRegistry.Handler workspaceOpenHandler = new MarkdownWorkspaceOpenHandler();
	private boolean workspaceListenersRegistered;

	private DocearMarkdownController() {
	}

	public static DocearMarkdownController getController() {
		return instance;
	}

	public static void install(final ModeController modeController) {
		instance = new DocearMarkdownController();
		modeController.addExtension(DocearMarkdownController.class, instance);
		DocumentOpenRegistry.registerHandler(instance.documentOpenHandler);
		WorkspaceDocumentOpenRegistry.registerHandler(instance.workspaceOpenHandler);

		final OpenMarkdownAction openAction = new OpenMarkdownAction();
		final NewMarkdownAction newAction = new NewMarkdownAction();
		modeController.addAction(openAction);
		modeController.addAction(newAction);
		modeController.addMenuContributor(new IMenuContributor() {
			public void updateMenus(final ModeController mc, final MenuBuilder builder) {
				try {
					builder.addAction("/menu_bar/file", openAction, MenuBuilder.AS_CHILD);
					builder.addAction("/menu_bar/file", newAction, MenuBuilder.AS_CHILD);
				}
				catch (Throwable t) {
					LogUtils.warn("Markdown: could not add File menu items", t);
				}
			}
		});
		scheduleWorkspaceRegistration(modeController, 0);
		LogUtils.info("Docear Markdown document integration installed.");
	}

	public static boolean isMarkdownFile(final File file) {
		if (file == null) {
			return false;
		}
		final String name = file.getName().toLowerCase();
		return name.endsWith(".md") || name.endsWith(".markdown");
	}

	public MarkdownDocumentView findOpenView(final File file) {
		if (file == null) {
			return null;
		}
		return openViews.get(key(file));
	}

	public void openMarkdownFile(final File file) {
		if (file == null) {
			return;
		}
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				openMarkdownFileOnEdt(file);
			}
		});
	}

	public void openMarkdownChooser() {
		final File dir = defaultDir();
		final JFileChooser chooser = new JFileChooser(dir);
		chooser.setDialogTitle("打开 Markdown");
		chooser.setFileFilter(new FileNameExtensionFilter("Markdown (*.md, *.markdown)", "md", "markdown"));
		if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		openMarkdownFile(chooser.getSelectedFile());
	}

	public void createNewMarkdown() {
		final File dir = defaultDir();
		final JFileChooser chooser = new JFileChooser(dir);
		chooser.setDialogTitle("新建 Markdown");
		chooser.setFileFilter(new FileNameExtensionFilter("Markdown (*.md)", "md"));
		chooser.setSelectedFile(new File(dir, "未命名.md"));
		if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = chooser.getSelectedFile();
		if (!isMarkdownFile(file)) {
			file = new File(file.getParentFile(), file.getName() + ".md");
		}
		try {
			if (!file.exists()) {
				final java.io.FileOutputStream out = new java.io.FileOutputStream(file);
				try {
					out.write("# 未命名\n\n".getBytes("UTF-8"));
				}
				finally {
					out.close();
				}
			}
			openMarkdownFile(file);
		}
		catch (Exception e) {
			LogUtils.warn("Markdown: create failed", e);
		}
	}

	public void unregisterView(final MarkdownDocumentView view) {
		if (view == null || view.getFile() == null) {
			return;
		}
		openViews.remove(key(view.getFile()));
	}

	private void openMarkdownFileOnEdt(final File file) {
		final MarkdownDocumentView existing = findOpenView(file);
		if (existing != null) {
			DocumentTabSupport.selectDocumentTab(existing);
			return;
		}
		try {
			final MarkdownDocumentView view = new MarkdownDocumentView(file);
			openViews.put(key(file), view);
			DocumentTabSupport.openDocumentTab(view);
		}
		catch (Exception e) {
			LogUtils.warn("Could not open Markdown: " + file, e);
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
		final File home = new File(System.getProperty("user.home"), ".docear/markdown");
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
			LogUtils.warn("Markdown: workspace extension not available; using document registry only.");
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
			        WorkspaceActionEvent.WSNODE_OPEN_DOCUMENT, new MarkdownWorkspaceListener());
			workspaceListenersRegistered = true;
		}
		catch (RuntimeException e) {
			LogUtils.warn("Markdown: failed to register workspace listeners", e);
		}
	}

	private final class MarkdownDocumentOpenHandler implements DocumentOpenRegistry.Handler {
		public boolean canOpen(final File file) {
			return isMarkdownFile(file);
		}

		public void open(final File file) {
			openMarkdownFile(file);
		}
	}

	private final class MarkdownWorkspaceOpenHandler implements WorkspaceDocumentOpenRegistry.Handler {
		public boolean canOpen(final File file) {
			return isMarkdownFile(file);
		}

		public void open(final File file) {
			openMarkdownFile(file);
		}
	}

	public static final class OpenMarkdownAction extends AFreeplaneAction {
		private static final long serialVersionUID = 1L;

		public OpenMarkdownAction() {
			super("OpenMarkdownAction", "打开 Markdown...", null);
		}

		public void actionPerformed(final java.awt.event.ActionEvent e) {
			getController().openMarkdownChooser();
		}
	}

	public static final class NewMarkdownAction extends AFreeplaneAction {
		private static final long serialVersionUID = 1L;

		public NewMarkdownAction() {
			super("NewMarkdownAction", "新建 Markdown...", null);
		}

		public void actionPerformed(final java.awt.event.ActionEvent e) {
			getController().createNewMarkdown();
		}
	}
}
