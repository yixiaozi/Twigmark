package org.freeplane.plugin.workspace.components.nodepins;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.ui.components.TabCountLabels;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.mode.ModeController;

public final class PinnedNodesTabInstaller {

	private static final String ALL_TODOS_TAB_TITLE = "\u5168\u90e8\u5f85\u529e";
	private static boolean installed;

	private PinnedNodesTabInstaller() {
	}

	public static void install(final ModeController modeController) {
		if (modeController == null) {
			return;
		}
		installWithRetry(modeController, 0);
	}

	private static void installWithRetry(final ModeController modeController, final int attempt) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (tryInstall(modeController)) {
					installed = true;
					return;
				}
				if (attempt >= 40) {
					LogUtils.severe("could not install pinned nodes tab after retries");
					return;
				}
				final Timer timer = new Timer(250, new java.awt.event.ActionListener() {
					public void actionPerformed(final java.awt.event.ActionEvent e) {
						installWithRetry(modeController, attempt + 1);
					}
				});
				timer.setRepeats(false);
				timer.start();
			}
		});
	}

	public static boolean tryInstall(final ModeController modeController) {
		final JTabbedPane tabs = findFormatTabbedPane(modeController);
		if (tabs == null) {
			return false;
		}
		return tryInstall(modeController, tabs);
	}

	public static boolean tryInstall(final ModeController modeController, final JTabbedPane tabs) {
		if (installed) {
			return true;
		}
		if (modeController == null || tabs == null) {
			return false;
		}
		try {
			final String title = TextUtils.getText("workspace.nodepins.tab.title");
			for (int i = 0; i < tabs.getTabCount(); i++) {
				if (title.equals(TabCountLabels.stripHtml(tabs.getTitleAt(i)))) {
					installed = true;
					return true;
				}
			}
			int insertIndex = tabs.getTabCount();
			for (int i = 0; i < tabs.getTabCount(); i++) {
				if (ALL_TODOS_TAB_TITLE.equals(TabCountLabels.stripHtml(tabs.getTitleAt(i)))) {
					insertIndex = i + 1;
					break;
				}
			}
			tabs.insertTab(title, null, new PinnedNodesTabPanel(modeController), null, insertIndex);
			tabs.revalidate();
			tabs.repaint();
			try {
				final Class factoryClass = Class.forName("org.freeplane.main.mindmapmode.MModeControllerFactory");
				factoryClass.getMethod("refreshFormatTabTitles", JTabbedPane.class).invoke(null, tabs);
			}
			catch (final Exception ignored) {
			}
			installed = true;
			return true;
		}
		catch (final Exception e) {
			LogUtils.warn(e);
			return false;
		}
	}

	private static JTabbedPane findFormatTabbedPane(final ModeController modeController) {
		final Container formatBar = modeController.getUserInputListenerFactory().getToolBar("/format");
		if (formatBar == null) {
			return null;
		}
		for (int i = 0; i < formatBar.getComponentCount(); i++) {
			final Component component = formatBar.getComponent(i);
			if (component instanceof JTabbedPane) {
				return (JTabbedPane) component;
			}
		}
		return null;
	}
}
