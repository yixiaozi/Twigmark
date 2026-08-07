package org.freeplane.view.swing.features.reports;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JPanel;

import org.freeplane.features.ui.IDocumentTabView;
import org.freeplane.main.application.DocumentTabSupport;

/**
 * Bottom-tab document that hosts report / activity / MCP audit UIs in the mind-map area.
 */
public final class ReportDocumentView extends JPanel implements IDocumentTabView {

	private static final long serialVersionUID = 1L;

	private String tabTitle = "报表";
	private Component content;

	public ReportDocumentView() {
		super(new BorderLayout());
		setName("report-document");
		setOpaque(true);
	}

	public void setContent(final String title, final Component component) {
		this.tabTitle = title == null || title.length() == 0 ? "报表" : title;
		if (content == component && content != null) {
			refreshTabTitle();
			return;
		}
		removeAll();
		content = component;
		if (component != null) {
			add(component, BorderLayout.CENTER);
		}
		revalidate();
		repaint();
		refreshTabTitle();
	}

	public String getTabTitle() {
		return tabTitle;
	}

	public Component getViewportComponent() {
		return this;
	}

	public Component getTabKey() {
		return this;
	}

	public void onTabActivated() {
		// Content already in this panel; DocumentTabSupport puts us in the scroll pane.
	}

	public void onTabDeactivated() {
	}

	public boolean requestClose(final boolean force) {
		DocumentTabSupport.closeDocumentTab(this);
		content = null;
		removeAll();
		return true;
	}

	public boolean isModified() {
		return false;
	}

	private void refreshTabTitle() {
		DocumentTabSupport.refreshTabTitles();
	}
}
