package org.freeplane.plugin.workspace.features.nodepins;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.core.util.WorkspaceSideTabSnapshot;
import org.freeplane.core.util.WorkspaceSideTabSnapshotRegistry;

/**
 * Publishes pinned-node counts to the side-tab metric registry without requiring
 * the pinned tab panel to be opened first.
 */
public final class NodePinsMetricsPublisher {

	private static boolean installed;

	private NodePinsMetricsPublisher() {
	}

	public static void install() {
		if (installed) {
			return;
		}
		installed = true;
		NodePinsIndex.getInstance().addChangeListener(new Runnable() {
			public void run() {
				publishFromIndex();
			}
		});
		publishFromIndex();
	}

	public static void publishFromIndex() {
		publishFromEntries(NodePinsIndex.getInstance().getDisplayEntries(true, null));
	}

	public static void publishFromEntries(final List entries) {
		final List snapshot = new ArrayList();
		if (entries != null) {
			for (int i = 0; i < entries.size(); i++) {
				final NodePinEntry entry = (NodePinEntry) entries.get(i);
				snapshot.add(new WorkspaceSideTabSnapshot.PinnedEntry(entry.getMapFile(), entry.getNodeId(),
						entry.getListNodeLabel(), formatTagsForSnapshot(entry.getTags())));
			}
		}
		WorkspaceSideTabSnapshotRegistry.updatePinnedEntries(snapshot);
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_PINNED, snapshot.size());
	}

	private static String formatTagsForSnapshot(final Set tags) {
		if (tags == null || tags.isEmpty()) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		for (final Iterator it = tags.iterator(); it.hasNext();) {
			final Object tag = it.next();
			if (NodeDetailsTagUtils.TAG_ARCHIVED.equals(tag) || NodeDetailsTagUtils.PIN_TAG.equals(tag)) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(tag);
		}
		return sb.toString();
	}
}
