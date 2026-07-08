package org.docear.plugin.ai.ui;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.docear.plugin.ai.DocearAiController;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.ui.components.TabCountLabels;
import org.freeplane.main.mindmapmode.MModeControllerFactory;
import org.freeplane.features.mode.ModeController;

/**
 * 将 AI 聊天 Tab 安装到右侧格式面板中（位于「最近修改」之后）。
 */
public final class AiChatTabInstaller {

    private static final String TAB_TITLE = "AI \u804a\u5929";
    private static final String RECENTLY_MODIFIED_TAB_TITLE = "\u6700\u8fd1\u4fee\u6539";
    private static boolean installed;

    private AiChatTabInstaller() {
    }

    public static void install(final ModeController modeController, final AiChatSidebar chatSidebar) {
        if (modeController == null || chatSidebar == null) {
            return;
        }
        installWithRetry(modeController, chatSidebar, 0);
    }

    private static void installWithRetry(final ModeController modeController, final AiChatSidebar chatSidebar, final int attempt) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (tryInstall(modeController, chatSidebar)) {
                    installed = true;
                    return;
                }
                if (attempt >= 40) {
                    LogUtils.severe("could not install AI chat tab after retries");
                    return;
                }
                final Timer timer = new Timer(250, new java.awt.event.ActionListener() {
                    public void actionPerformed(final java.awt.event.ActionEvent e) {
                        installWithRetry(modeController, chatSidebar, attempt + 1);
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        });
    }

    public static boolean tryInstall(final ModeController modeController, final AiChatSidebar chatSidebar) {
        final JTabbedPane tabs = findFormatTabbedPane(modeController);
        if (tabs == null) {
            return false;
        }
        return tryInstall(modeController, tabs, chatSidebar);
    }

    public static boolean tryInstall(final ModeController modeController, final JTabbedPane tabs, final AiChatSidebar chatSidebar) {
        if (modeController == null || tabs == null || chatSidebar == null) {
            return false;
        }
        try {
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if (TAB_TITLE.equals(TabCountLabels.stripHtml(tabs.getTitleAt(i)))) {
                    installed = true;
                    return true;
                }
            }
            int insertIndex = tabs.getTabCount();
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if (RECENTLY_MODIFIED_TAB_TITLE.equals(TabCountLabels.stripHtml(tabs.getTitleAt(i)))) {
                    insertIndex = i + 1;
                    break;
                }
            }
            tabs.insertTab(TAB_TITLE, null, chatSidebar, null, insertIndex);
            tabs.revalidate();
            tabs.repaint();
            MModeControllerFactory.refreshFormatTabTitles(tabs);
            final DocearAiController controller = DocearAiController.getController();
            if (controller != null) {
                AiChatTabMetrics.publishForCurrentMap(controller.getChatSessionManager());
            }
            installed = true;
            LogUtils.info("AI chat tab installed at index " + insertIndex);
            return true;
        } catch (final Exception e) {
            LogUtils.warn(e);
            return false;
        }
    }

    public static JTabbedPane findFormatTabbedPane(final ModeController modeController) {
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

    public static String getTabTitle() {
        return TAB_TITLE;
    }

    public static boolean isInstalled() {
        return installed;
    }
}
