package org.freeplane.view.swing.features.reports;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JPanel;

import org.freeplane.features.ui.IDocumentTabView;
import org.freeplane.main.application.DocumentTabSupport;

/**
 * One bottom-tab document for a single report / activity / MCP audit instance.
 * Each open creates a new view so previous reports stay available like mind maps.
 */
public final class ReportDocumentView extends JPanel implements IDocumentTabView {

	private static final long serialVersionUID = 1L;

	private String tabTitle = "报表";
	private Component content;
	private final String assignmentKey;
	private boolean closed;

	public ReportDocumentView(final String assignmentKey) {
		super(new BorderLayout());
		this.assignmentKey = assignmentKey == null || assignmentKey.length() == 0
		        ? ("report://adhoc/" + System.nanoTime())
		        : assignmentKey;
		setName("report-document-" + this.assignmentKey);
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

	public void setTabTitle(final String title) {
		this.tabTitle = title == null || title.length() == 0 ? "报表" : title;
		refreshTabTitle();
	}

	public Component getContent() {
		return content;
	}

	public boolean isClosed() {
		return closed;
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

	public String getTabAssignmentKey() {
		return assignmentKey;
	}

	public void onTabActivated() {
		// Content already in this panel; DocumentTabSupport puts us in the scroll pane.
	}

	public void onTabDeactivated() {
	}

	public boolean requestClose(final boolean force) {
		closed = true;
		DocumentTabSupport.closeDocumentTab(this);
		ReportDocumentService.forget(this);
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
