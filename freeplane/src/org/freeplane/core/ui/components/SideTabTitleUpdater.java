package org.freeplane.core.ui.components;

import java.awt.Component;
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
	private boolean refreshingTitles;
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
		syncBindingFromTabs();
		refreshTitles();
	}

	/** Prefer component-type title, else strip count HTML from the tab header. */
	public static String baseTitleAt(final JTabbedPane tabs, final int index) {
		if (tabs == null || index < 0 || index >= tabs.getTabCount()) {
			return "";
		}
		final String fromComponent = titleForContentComponent(tabs.getComponentAt(index));
		if (fromComponent != null) {
			return fromComponent;
		}
		return TabCountLabels.stripHtml(tabs.getTitleAt(index));
	}

	/**
	 * Rebuild title/metric lists from the live tab strip. Inserts (文件 / 标签 / AI)
	 * shift indices; without this, the 1s poller paints stale labels onto the wrong
	 * panels (e.g. 「AI 聊天」header over 「最近修改」content).
	 */
	private void syncBindingFromTabs() {
		baseTitles.clear();
		metricKeys.clear();
		for (int i = 0; i < tabs.getTabCount(); i++) {
			final String title = resolveBaseTitleAt(i);
			baseTitles.add(title);
			metricKeys.add(metricKeyForRightTitle(title));
		}
		refreshSnapshotMetrics();
	}

	private String resolveBaseTitleAt(final int index) {
		return baseTitleAt(tabs, index);
	}

	/**
	 * Canonical right-tab titles keyed by content panel type (not by previous label).
	 * Returns null for left-sidebar / unknown panels so {@link #bindLeftTabs} stays intact.
	 */
	static String titleForContentComponent(final Component content) {
		if (content == null) {
			return null;
		}
		final String name = content.getClass().getName();
		if (name.endsWith(".CurrentMapFolderTabPanel")) {
			return "\u6587\u4ef6";
		}
		if (name.endsWith(".ReminderTabPanel")) {
			return "\u63d0\u9192";
		}
		if (name.endsWith(".EnhancedAllRemindersTabPanel")) {
			return "\u5168\u90e8\u63d0\u9192";
		}
		if (name.endsWith(".EnhancedAllRecurringRemindersTabPanel")) {
			return "\u5468\u671f\u63d0\u9192";
		}
		if (name.endsWith(".ReminderTimelineTabPanel")) {
			return "\u65f6\u95f4\u8f74";
		}
		if (name.endsWith(".TodoTabPanel")) {
			return "\u5f85\u529e";
		}
		if (name.endsWith(".EnhancedAllTodosTabPanel")) {
			return "\u5168\u90e8\u5f85\u529e";
		}
		if (name.endsWith(".PomodoroTabPanel")) {
			return "\u756a\u8304\u949f";
		}
		if (name.endsWith(".PinnedNodesTabPanel")) {
			try {
				return TextUtils.getText("workspace.nodepins.tab.title");
			}
			catch (final Exception e) {
				return "\u6807\u7b7e";
			}
		}
		if (name.endsWith(".EnhancedAllPublishTabPanel")) {
			return "\u5168\u90e8\u53d1\u5e03";
		}
		if (name.endsWith(".EnhancedAllRecentlyModified")) {
			return "\u6700\u8fd1\u4fee\u6539";
		}
		if (name.endsWith(".AiChatSidebar") || name.indexOf("AiChatSidebar") >= 0) {
			return "AI \u804a\u5929";
		}
		return null;
	}

	public void refreshTitles() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(refreshRunnable);
			return;
		}
		if (refreshingTitles) {
			return;
		}
		refreshingTitles = true;
		try {
			if (leftMetricsRefreshHook != null) {
				try {
					leftMetricsRefreshHook.run();
				}
				catch (final Exception e) {
					LogUtils.warn("Left tab metrics hook failed: " + e.getMessage());
				}
			}
			// Keep label list aligned with components after async tab inserts.
			if (tabs.getTabCount() != baseTitles.size()) {
				syncBindingFromTabs();
			}
			final int count = Math.min(tabs.getTabCount(), Math.min(baseTitles.size(), metricKeys.size()));
			for (int i = 0; i < count; i++) {
				final String fromComponent = titleForContentComponent(tabs.getComponentAt(i));
				String baseTitle = (String) baseTitles.get(i);
				if (fromComponent != null && !fromComponent.equals(baseTitle)) {
					baseTitle = fromComponent;
					baseTitles.set(i, fromComponent);
					metricKeys.set(i, metricKeyForRightTitle(fromComponent));
				}
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
		finally {
			refreshingTitles = false;
		}
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
