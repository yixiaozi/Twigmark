package org.freeplane.plugin.workspace.components;

import javax.swing.JComponent;

/**
 * Bridge so Docear core can supply the relationship-graph side tab without a compile-time dependency.
 */
public final class RelationshipGraphTabBridge {

	/** Set on the real side-tab panel so placeholders / failed loads are distinguishable. */
	public static final String SIDE_TAB_CLIENT_PROPERTY = "docear.relationshipGraph.sideTab";

	public interface Provider {
		JComponent createSideTabPanel();

		void onTabSelected();

		void onTabDeselected();

		void preloadMetrics();
	}

	private static Provider provider;

	private RelationshipGraphTabBridge() {
	}

	public static void setProvider(final Provider newProvider) {
		provider = newProvider;
	}

	public static Provider getProvider() {
		return provider;
	}

	public static boolean isAvailable() {
		return provider != null;
	}
}
