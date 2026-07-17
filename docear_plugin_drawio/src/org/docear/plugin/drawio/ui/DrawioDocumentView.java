package org.docear.plugin.drawio.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.docear.plugin.drawio.DocearDrawioController;
import org.docear.plugin.drawio.browser.DrawioBrowserPanel;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.ui.IDocumentTabView;
import org.freeplane.main.application.DocumentTabSupport;

public class DrawioDocumentView extends JPanel implements IDocumentTabView, DrawioEditorListener {

	private static final long serialVersionUID = 1L;

	private final File file;
	private final DrawioBrowserPanel browser;
	private String diagramXml = "";
	private boolean modified;
	private boolean editorReady;
	private boolean pendingLoad;

	public DrawioDocumentView(final File file) throws IOException {
		this.file = file;
		setLayout(new BorderLayout());
		if (file.exists()) {
			diagramXml = readUtf8(file);
		}
		browser = new DrawioBrowserPanel(this);
		browser.setDiagramFile(file);
		add(browser, BorderLayout.CENTER);
		setName(file.getName());
	}

	public File getFile() {
		return file;
	}

	public String getTabTitle() {
		return file.getName();
	}

	public Component getViewportComponent() {
		return this;
	}

	public Component getTabKey() {
		return this;
	}

	public void onTabActivated() {
		browser.ensureLoaded();
		if (editorReady) {
			sendLoadToEditor();
		}
		else {
			pendingLoad = true;
		}
	}

	public void onTabDeactivated() {
	}

	public boolean requestClose(final boolean force) {
		if (modified && !force) {
			final int choice = JOptionPane.showConfirmDialog(this,
			        "Save changes to " + file.getName() + "?",
			        "Draw.io",
			        JOptionPane.YES_NO_CANCEL_OPTION);
			if (choice == JOptionPane.CANCEL_OPTION) {
				return false;
			}
			if (choice == JOptionPane.YES_OPTION) {
				saveToDisk(diagramXml);
			}
		}
		browser.disposeBrowser();
		DocearDrawioController.getController().unregisterView(this);
		DocumentTabSupport.closeDocumentTab(this);
		return true;
	}

	public boolean isModified() {
		return modified;
	}

	public void onEditorReady() {
		editorReady = true;
		if (pendingLoad || DocumentTabSupport.getActiveDocumentView() == this) {
			sendLoadToEditor();
			pendingLoad = false;
		}
	}

	public void onDiagramSaved(final String xml, final boolean draft) {
		if (xml == null) {
			return;
		}
		diagramXml = xml;
		modified = true;
		if (!draft) {
			saveToDisk(xml);
		}
	}

	public void onEditorExit(final String xml, final boolean modifiedFlag) {
		if (xml != null && (modifiedFlag || modified)) {
			diagramXml = xml;
			modified = true;
			saveToDisk(xml);
		}
	}

	private void sendLoadToEditor() {
		browser.loadDiagram(diagramXml, file.getName());
	}

	private void saveToDisk(final String xml) {
		try {
			writeUtf8(file, xml);
			modified = false;
		}
		catch (IOException e) {
			LogUtils.warn("Could not save Draw.io file: " + file, e);
			JOptionPane.showMessageDialog(this, "Could not save file:\n" + e.getMessage(), "Draw.io",
			        JOptionPane.ERROR_MESSAGE);
		}
	}

	private static String readUtf8(final File file) throws IOException {
		final FileInputStream in = new FileInputStream(file);
		try {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
			}
			return out.toString("UTF-8");
		}
		finally {
			in.close();
		}
	}

	private static void writeUtf8(final File file, final String content) throws IOException {
		final FileOutputStream out = new FileOutputStream(file);
		try {
			out.write(content.getBytes("UTF-8"));
		}
		finally {
			out.close();
		}
	}
}
