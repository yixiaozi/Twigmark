package org.freeplane.plugin.workspace.components.currentmapfolder;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.freeplane.core.ui.components.TabCountLabels;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.ModeController;

public final class CurrentMapFolderTabInstaller {

	private static final String TAB_TITLE = "\u6587\u4ef6";
	private static final String REMINDER_TAB_TITLE = "\u63d0\u9192";
	private static boolean installed;

	private CurrentMapFolderTabInstaller() {
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
					LogUtils.severe("could not install current map folder tab after retries");
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

	public static synchronized boolean tryInstall(final ModeController modeController, final JTabbedPane tabs) {
		if (installed) {
			return true;
		}
		if (modeController == null || tabs == null) {
			return false;
		}
		try {
			for (int i = 0; i < tabs.getTabCount(); i++) {
				if (TAB_TITLE.equals(TabCountLabels.stripHtml(tabs.getTitleAt(i)))) {
					installed = true;
					refreshTitles(tabs);
					return true;
				}
			}
			int insertIndex = 0;
			for (int i = 0; i < tabs.getTabCount(); i++) {
				if (REMINDER_TAB_TITLE.equals(TabCountLabels.stripHtml(tabs.getTitleAt(i)))) {
					insertIndex = i;
					break;
				}
			}
			final CurrentMapFolderTabPanel panel = new CurrentMapFolderTabPanel(modeController);
			tabs.insertTab(TAB_TITLE, null, panel, null, insertIndex);
			tabs.revalidate();
			tabs.repaint();
			refreshTitles(tabs);
			installed = true;
			return true;
		}
		catch (final Exception e) {
			LogUtils.warn(e);
			return false;
		}
	}

	private static void refreshTitles(final JTabbedPane tabs) {
		try {
			final Class factoryClass = Class.forName("org.freeplane.main.mindmapmode.MModeControllerFactory");
			factoryClass.getMethod("refreshFormatTabTitles", JTabbedPane.class).invoke(null, tabs);
		}
		catch (final Exception ignored) {
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
