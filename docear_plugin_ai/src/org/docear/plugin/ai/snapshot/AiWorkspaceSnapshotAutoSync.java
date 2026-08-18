package org.docear.plugin.ai.snapshot;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.docear.plugin.ai.DocearAiConfig;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.WorkspaceSideTabSnapshotRegistry;
import org.freeplane.features.map.IMapLifeCycleListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.ModeController;

/**
 * 在后台自动将侧栏快照导出到 .AI请查看这里，用户无感知。
 * 保存频繁时合并请求，并强制最小间隔，避免编辑时连续扫盘。
 */
public final class AiWorkspaceSnapshotAutoSync {

    private static AiWorkspaceSnapshotAutoSync instance;

    private final DocearAiConfig config;
    private final Runnable registryListener;
    private final Runnable favoritesListener;
    private final IMapLifeCycleListener mapLifeCycleListener;
    private Timer debounceTimer;
    private volatile boolean exportRunning;
    private volatile boolean exportPending;
    private volatile long lastExportFinishedAt;

    private AiWorkspaceSnapshotAutoSync(final ModeController modeController) {
        config = new DocearAiConfig();
        registryListener = new Runnable() {
            public void run() {
                requestExport();
            }
        };
        favoritesListener = new Runnable() {
            public void run() {
                requestExport();
            }
        };
        mapLifeCycleListener = new IMapLifeCycleListener() {
            public void onCreate(MapModel map) {
            }

            public void onRemove(MapModel map) {
            }

            public void onSavedAs(MapModel map) {
                requestExport();
            }

            public void onSaved(MapModel map) {
                requestExport();
            }
        };
        WorkspaceSideTabSnapshotRegistry.addChangeListener(registryListener);
        registerFavoritesListener();
        modeController.getMapController().addMapLifeCycleListener(mapLifeCycleListener);
        scheduleInitialExport();
    }

    public static synchronized void install(final ModeController modeController) {
        if (org.freeplane.core.util.McpHeadlessFlags.isLeanMemory()) {
            LogUtils.info("Lean memory: skip AI workspace snapshot auto-sync");
            return;
        }
        if (instance == null) {
            instance = new AiWorkspaceSnapshotAutoSync(modeController);
            LogUtils.info("AI workspace snapshot auto-sync installed.");
        }
    }

    public static void requestExport() {
        if (instance != null) {
            instance.scheduleExport();
        }
    }

    private void scheduleInitialExport() {
        Timer timer = new Timer(12000, new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                requestExport();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void scheduleExport() {
        if (!config.isWorkspaceSnapshotExportEnabled()) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (debounceTimer == null) {
                    debounceTimer = new Timer(config.getWorkspaceSnapshotDebounceMs(),
                            new java.awt.event.ActionListener() {
                                public void actionPerformed(java.awt.event.ActionEvent e) {
                                    runExportInBackground();
                                }
                            });
                    debounceTimer.setRepeats(false);
                }
                else {
                    debounceTimer.setDelay(config.getWorkspaceSnapshotDebounceMs());
                }
                debounceTimer.restart();
            }
        });
    }

    private void runExportInBackground() {
        if (exportRunning) {
            exportPending = true;
            return;
        }
        final long minInterval = config.getWorkspaceSnapshotMinIntervalMs();
        final long sinceLast = System.currentTimeMillis() - lastExportFinishedAt;
        if (lastExportFinishedAt > 0L && sinceLast < minInterval) {
            exportPending = true;
            final int waitMs = (int) Math.min(Integer.MAX_VALUE, minInterval - sinceLast);
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (debounceTimer == null) {
                        return;
                    }
                    debounceTimer.setDelay(Math.max(waitMs, config.getWorkspaceSnapshotDebounceMs()));
                    debounceTimer.restart();
                }
            });
            return;
        }
        exportRunning = true;
        exportPending = false;
        Thread worker = new Thread(new Runnable() {
            public void run() {
                try {
                    new AiWorkspaceSnapshotExporter(config).export();
                }
                catch (final Exception e) {
                    LogUtils.warn("AI workspace snapshot auto-sync failed: " + e.getMessage());
                }
                finally {
                    lastExportFinishedAt = System.currentTimeMillis();
                    exportRunning = false;
                    if (exportPending) {
                        exportPending = false;
                        scheduleExport();
                    }
                }
            }
        }, "AiWorkspaceSnapshotExport");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private void registerFavoritesListener() {
        try {
            final Class storeClass = Class.forName("org.freeplane.plugin.workspace.features.favorites.FavoritesAndTagsStore");
            final Object store = storeClass.getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
            storeClass.getMethod("addChangeListener", new Class[] { Runnable.class }).invoke(store,
                    new Object[] { favoritesListener });
        }
        catch (final Exception e) {
            LogUtils.info("AI snapshot auto-sync: favorites listener not available: " + e.getMessage());
        }
    }
}
