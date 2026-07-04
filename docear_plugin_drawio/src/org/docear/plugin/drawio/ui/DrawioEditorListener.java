package org.docear.plugin.drawio.ui;

/**
 * Callback from the embedded browser when Draw.io sends protocol messages.
 */
public interface DrawioEditorListener {
	void onEditorReady();

	void onDiagramSaved(String xml, boolean draft);

	void onEditorExit(String xml, boolean modified);
}
