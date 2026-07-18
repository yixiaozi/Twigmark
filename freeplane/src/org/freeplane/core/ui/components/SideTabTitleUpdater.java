package org.freeplane.core.ui.components;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.core.util.TextUtils;
import org.freeplane.core.util.WorkspaceSideTabSnapshot;
import org.freeplane.core.util.WorkspaceSideTabSnapshotRegistry;
import org.freeplane.core.util.LogUtils;

public final class SideTabTitleUpdater {

	public interface LeftTabSource {
		String getTabId(int index);

		String getBaseTitle(String tabId);

		String getMetricKey(String tabId);

		int getTabCount();
	}

	private final JTabbedPane tabs;
	private final Runnable widthApplier;
	private final List baseTitles = new ArrayList();
	private final List metricKeys = new ArrayList();
	private final Runnable refreshRunnable = new Runnable() {
		public void run() {
			refreshTitles();
		}
	};
	private Timer pollTimer;
	private static Runnable leftMetricsRefreshHook;

	public static void setLeftMetricsRefreshHook(final Runnable hook) {
		leftMetricsRefreshHook = hook;
	}

	private SideTabTitleUpdater(final JTabbedPane tabs, final Runnable widthApplier) {
		this.tabs = tabs;
		this.widthApplier = widthApplier;
		SideTabMetricRegistry.addChangeListener(refreshRunnable);
		WorkspaceSideTabSnapshotRegistry.addChangeListener(new Runnable() {
			public void run() {
				refreshSnapshotMetrics();
				refreshTitles();
			}
		});
		tabs.addChangeListener(new ChangeListener() {
			public void stateChanged(final ChangeEvent e) {
				refreshTitles();
			}
		});
	}

	public static SideTabTitleUpdater install(final JTabbedPane tabs) {
		return install(tabs, null);
	}

	public static SideTabTitleUpdater install(final JTabbedPane tabs, final Runnable widthApplier) {
		final SideTabTitleUpdater updater = new SideTabTitleUpdater(tabs, widthApplier);
		updater.startPolling();
		return updater;
	}

	public void bindLeftTabs(final LeftTabSource source) {
		baseTitles.clear();
		metricKeys.clear();
		for (int i = 0; i < source.getTabCount(); i++) {
			final String tabId = source.getTabId(i);
			baseTitles.add(source.getBaseTitle(tabId));
			metricKeys.add(source.getMetricKey(tabId));
		}
		refreshSnapshotMetrics();
		refreshTitles();
	}

	public void bindRightTabs() {
		baseTitles.clear();
		metricKeys.clear();
		for (int i = 0; i < tabs.getTabCount(); i++) {
			final String title = TabCountLabels.stripHtml(tabs.getTitleAt(i));
			baseTitles.add(title);
			metricKeys.add(metricKeyForRightTitle(title));
		}
		refreshSnapshotMetrics();
		refreshTitles();
	}

	public void refreshTitles() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(refreshRunnable);
			return;
		}
		if (leftMetricsRefreshHook != null) {
			try {
				leftMetricsRefreshHook.run();
			}
			catch (final Exception e) {
				LogUtils.warn("Left tab metrics hook failed: " + e.getMessage());
			}
		}
		final int count = Math.min(tabs.getTabCount(), Math.min(baseTitles.size(), metricKeys.size()));
		for (int i = 0; i < count; i++) {
			final String baseTitle = (String) baseTitles.get(i);
			final String metricKey = (String) metricKeys.get(i);
			final int value = metricKey != null && metricKey.length() > 0
			    ? SideTabMetricRegistry.get(metricKey, 0)
			    : -1;
			tabs.setTitleAt(i, TabCountLabels.format(baseTitle, value));
			final boolean selected = i == tabs.getSelectedIndex();
			if (value >= 0) {
				tabs.setTabComponentAt(i, TabCountLabels.createTabComponent(baseTitle, value, null, selected));
			}
			else {
				tabs.setTabComponentAt(i, null);
			}
		}
		if (widthApplier != null) {
			widthApplier.run();
		}
		tabs.revalidate();
		tabs.repaint();
	}

	private void startPolling() {
		if (pollTimer != null) {
			return;
		}
		pollTimer = new Timer(1000, new java.awt.event.ActionListener() {
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				refreshTitles();
			}
		});
		pollTimer.setRepeats(true);
		pollTimer.start();
	}

	public static void refreshSnapshotMetrics() {
		final WorkspaceSideTabSnapshot snapshot = WorkspaceSideTabSnapshotRegistry.getSnapshot();
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_ALL_REMINDERS, snapshot.getOneTimeReminders().size());
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_RECURRING_REMINDERS, snapshot.getRecurringReminders().size());
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_ALL_TODOS, snapshot.getTodos().size());
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_PINNED, snapshot.getPinnedEntries().size());
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_PUBLISHED, snapshot.getPublishedEntries().size());
		SideTabMetricRegistry.set(SideTabMetricKeys.RIGHT_RECENT_MODIFIED, snapshot.getRecentlyModifiedEntries().size());
	}

	private static String metricKeyForRightTitle(final String title) {
		if ("\u6587\u4ef6".equals(title)) {
			return SideTabMetricKeys.RIGHT_CURRENT_FOLDER;
		}
		if ("\u63d0\u9192".equals(title)) {
			return SideTabMetricKeys.RIGHT_CURRENT_REMINDERS;
		}
		if ("\u5168\u90e8\u63d0\u9192".equals(title)) {
			return SideTabMetricKeys.RIGHT_ALL_REMINDERS;
		}
		if ("\u5468\u671f\u63d0\u9192".equals(title)) {
			return SideTabMetricKeys.RIGHT_RECURRING_REMINDERS;
		}
		if ("\u65f6\u95f4\u8f74".equals(title)) {
			return SideTabMetricKeys.RIGHT_TIMELINE_TODAY;
		}
		if ("\u5f85\u529e".equals(title)) {
			return SideTabMetricKeys.RIGHT_CURRENT_TODOS;
		}
		if ("\u5168\u90e8\u5f85\u529e".equals(title)) {
			return SideTabMetricKeys.RIGHT_ALL_TODOS;
		}
		if ("\u5168\u90e8\u53d1\u5e03".equals(title)) {
			return SideTabMetricKeys.RIGHT_PUBLISHED;
		}
		if ("\u6700\u8fd1\u4fee\u6539".equals(title)) {
			return SideTabMetricKeys.RIGHT_RECENT_MODIFIED;
		}
		try {
			if (TextUtils.getText("workspace.nodepins.tab.title").equals(title)) {
				return SideTabMetricKeys.RIGHT_PINNED;
			}
		}
		catch (final Exception e) {
			// ignore
		}
		if ("AI \u804a\u5929".equals(title)) {
			return SideTabMetricKeys.RIGHT_AI_CHAT;
		}
		if ("\u756a\u8304\u949f".equals(title)) {
			return SideTabMetricKeys.RIGHT_POMODORO;
		}
		return "";
	}
}
