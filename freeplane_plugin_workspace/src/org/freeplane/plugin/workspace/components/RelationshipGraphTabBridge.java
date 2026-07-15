package org.freeplane.plugin.workspace.components;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

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
	private static final List READY_LISTENERS = new ArrayList();

	private RelationshipGraphTabBridge() {
	}

	public static void setProvider(final Provider newProvider) {
		provider = newProvider;
		notifyReadyListeners();
	}

	/**
	 * Invoked when Docear core registers the graph provider (or immediately if already set).
	 * Runs on the EDT.
	 */
	public static void addReadyListener(final Runnable listener) {
		if (listener == null) {
			return;
		}
		synchronized (READY_LISTENERS) {
			READY_LISTENERS.add(listener);
		}
		if (provider != null) {
			runOnEdt(listener);
		}
	}

	private static void notifyReadyListeners() {
		final Runnable[] listeners;
		synchronized (READY_LISTENERS) {
			listeners = (Runnable[]) READY_LISTENERS.toArray(new Runnable[READY_LISTENERS.size()]);
		}
		for (int i = 0; i < listeners.length; i++) {
			runOnEdt(listeners[i]);
		}
	}

	private static void runOnEdt(final Runnable listener) {
		if (SwingUtilities.isEventDispatchThread()) {
			try {
				listener.run();
			}
			catch (final Throwable e) {
				// ignore — workspace will retry on tab select
			}
		}
		else {
			SwingUtilities.invokeLater(listener);
		}
	}

	public static Provider getProvider() {
		return provider;
	}

	public static boolean isAvailable() {
		return provider != null;
	}
}
