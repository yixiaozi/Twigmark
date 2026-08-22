package org.docear.plugin.core.canvas;

import java.awt.BorderLayout;
import java.awt.Component;
import java.io.File;

import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ui.IDocumentTabView;
import org.freeplane.main.application.DocumentTabSupport;

/** Bottom-tab document wrapping an editable JSON Canvas. */
public final class JsonCanvasDocumentView extends JPanel implements IDocumentTabView, JsonCanvasEditor.Listener {

	private static final long serialVersionUID = 1L;

	private final File file;
	private final JsonCanvasEditor editor;

	public JsonCanvasDocumentView(final File file) throws Exception {
		this.file = file;
		setLayout(new BorderLayout());
		editor = new JsonCanvasEditor();
		editor.setListener(this);
		final JsonCanvasDocument doc = file.exists() && file.length() > 0 ? JsonCanvasIo.read(file)
				: new JsonCanvasDocument();
		if (!file.exists() || file.length() == 0) {
			if (doc.getNodes().isEmpty()) {
				doc.getNodes().add(JsonCanvasNode.text(JsonCanvasDocument.newId(), 80, 80,
						"画板\n双击编辑文本，从卡片边缘拉出连线。"));
			}
		}
		editor.load(file, doc);
		add(editor, BorderLayout.CENTER);
		setName(file.getName());
	}

	public File getFile() {
		return file;
	}

	public String getTabTitle() {
		return (editor.isModified() ? "*" : "") + file.getName();
	}

	public Component getViewportComponent() {
		return this;
	}

	public Component getTabKey() {
		return this;
	}

	public String getTabAssignmentKey() {
		try {
			return file.getCanonicalFile().getAbsolutePath().replace('\\', '/');
		}
		catch (Exception e) {
			return file.getAbsolutePath().replace('\\', '/');
		}
	}

	public void onTabActivated() {
	}

	public void onTabDeactivated() {
	}

	public boolean requestClose(final boolean force) {
		if (editor.isModified() && !force) {
			final int choice = JOptionPane.showConfirmDialog(this, "保存对 " + file.getName() + " 的更改？", "画板",
					JOptionPane.YES_NO_CANCEL_OPTION);
			if (choice == JOptionPane.CANCEL_OPTION) {
				return false;
			}
			if (choice == JOptionPane.YES_OPTION) {
				try {
					editor.save();
				}
				catch (Exception e) {
					LogUtils.warn("Canvas save failed", e);
					JOptionPane.showMessageDialog(this, "保存失败：\n" + e.getMessage());
					return false;
				}
			}
		}
		DocearCanvasController.getController().unregisterView(this);
		DocumentTabSupport.closeDocumentTab(this);
		return true;
	}

	public boolean isModified() {
		return editor.isModified();
	}

	public void onModifiedChanged(final boolean modified) {
		DocumentTabSupport.refreshTabTitles();
	}

	public void saveQuietly() {
		try {
			editor.save();
			DocumentTabSupport.refreshTabTitles();
		}
		catch (Exception e) {
			LogUtils.warn("Canvas save failed", e);
		}
	}

	public JsonCanvasEditor getEditor() {
		return editor;
	}
}
