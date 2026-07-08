package org.docear.plugin.core.graph;

import javax.swing.SwingUtilities;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;

/**
 * Scans relationship graph data for the side-tab subtitle only — no panel, no viewport.
 */
public final class RelationshipGraphMetricsPreloader {

	private static Thread activeThread;

	private RelationshipGraphMetricsPreloader() {
	}

	public static void preload() {
		if (activeThread != null && activeThread.isAlive()) {
			return;
		}
		activeThread = new Thread(new Runnable() {
			public void run() {
				Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
				try {
					final RelationshipGraphIndex index = RelationshipGraphScanner
					        .scan(RelationshipGraphScanner.MODE_MAP_FILES, null);
					final int edgeCount = index != null ? index.getEdgeCount() : 0;
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_GRAPH, edgeCount);
						}
					});
				}
				catch (final Exception e) {
					LogUtils.warn(e);
				}
			}
		}, "RelationshipGraphMetricsPreload");
		activeThread.start();
	}
}
