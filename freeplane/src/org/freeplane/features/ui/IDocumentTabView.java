package org.freeplane.features.ui;

import java.awt.Component;

/**
 * A non-mind-map document shown in the main viewport with a bottom tab
 * (alongside mind map tabs). Used e.g. for embedded Draw.io editors.
 */
public interface IDocumentTabView {

	String getTabTitle();

	/** Component placed in the main scroll pane viewport when this tab is active. */
	Component getViewportComponent();

	/** Tab identity object stored in {@code MapViewTabs} (often the panel itself). */
	Component getTabKey();

	void onTabActivated();

	void onTabDeactivated();

	/** @return true if closed, false if user cancelled */
	boolean requestClose(boolean force);

	boolean isModified();
}
