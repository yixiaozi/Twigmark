package org.docear.plugin.core.graph;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.plugin.workspace.actions.MindMapOpenLocationAction;
import org.freeplane.plugin.workspace.components.RelationshipGraphTabBridge;
import org.freeplane.plugin.workspace.components.TagGroupFilterBarFactory;

/**
 * Left sidebar: mode tabs (map links / node links / tags / favorites), search and related-item list.
 */
public class RelationshipGraphSideTabPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final Color MUTED = DocearUiTheme.TEXT_MUTED;
	private static final Color BORDER = DocearUiTheme.HAIRLINE;
	private static final Color TIP_BG = DocearUiTheme.CANVAS;
	private static final int RESULT_LIST_CAP = 200;
	private static final int ASYNC_DISPLAY_INDEX_THRESHOLD = 80;
	private static final int MODE_COUNT = 4;
	private static final String PROP_TAGS_GROUP = "workspace.graph.tags.filter.active.group";
	private static final String PROP_TAGS_DIRECT = "workspace.graph.tags.filter.direct.only";
	private static final String PROP_FAV_GROUP = "workspace.graph.favorites.filter.active.group";
	private static final String PROP_FAV_DIRECT = "workspace.graph.favorites.filter.direct.only";

	private static final Comparator TAG_FIRST_COMPARATOR = new Comparator() {
		public int compare(final Object o1, final Object o2) {
			final RelationshipGraphNode a = (RelationshipGraphNode) o1;
			final RelationshipGraphNode b = (RelationshipGraphNode) o2;
			final int ta = a.isTagNode() ? 0 : 1;
			final int tb = b.isTagNode() ? 0 : 1;
			if (ta != tb) {
				return ta - tb;
			}
			return a.getLabel().compareToIgnoreCase(b.getLabel());
		}
	};

	private final ModeTabPanel mapFilesTab;
	private final ModeTabPanel mapNodesTab;
	private final ModeTabPanel tagsTab;
	private final ModeTabPanel favoritesTab;
	private final JTabbedPane subTabs;

	private final RelationshipGraphIndex[] cachedBaseIndex = new RelationshipGraphIndex[MODE_COUNT];
	private final boolean[] dataLoadedForMode = new boolean[MODE_COUNT];

	private volatile int graphMode = RelationshipGraphScanner.MODE_MAP_FILES;
	private Thread activeScanThread;
	private Thread activeFilterThread;
	private volatile boolean syncingListSelection;
	/** True while the left 「关系图」tab is selected; forces presenting completed scans into the viewport. */
	private volatile boolean graphTabActive;
	private volatile int scanGeneration;

	public RelationshipGraphSideTabPanel() {
		super(new BorderLayout(4, 4));
		putClientProperty(RelationshipGraphTabBridge.SIDE_TAB_CLIENT_PROPERTY, Boolean.TRUE);
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		setOpaque(true);
		setBackground(Color.WHITE);

		mapFilesTab = new ModeTabPanel(RelationshipGraphScanner.MODE_MAP_FILES);
		mapNodesTab = new ModeTabPanel(RelationshipGraphScanner.MODE_MAP_NODES);
		tagsTab = new ModeTabPanel(RelationshipGraphScanner.MODE_TAGS);
		favoritesTab = new ModeTabPanel(RelationshipGraphScanner.MODE_FAVORITES);

		subTabs = new JTabbedPane(JTabbedPane.TOP);
		subTabs.setFont(subTabs.getFont().deriveFont(Font.PLAIN, 12f));
		subTabs.addTab("\u5bfc\u56fe\u5173\u8054", mapFilesTab);
		subTabs.addTab("\u8282\u70b9\u5173\u8054", mapNodesTab);
		subTabs.addTab("\u6807\u7b7e", tagsTab);
		subTabs.addTab("\u6536\u85cf", favoritesTab);
		subTabs.addChangeListener(new ChangeListener() {
			public void stateChanged(final ChangeEvent e) {
				onSubTabChanged();
			}
		});
		add(subTabs, BorderLayout.CENTER);
		setMinimumSize(new Dimension(220, 320));
		setPreferredSize(new Dimension(280, 420));
		// Defer canvas wiring until first activation — keep tab install off the
		// critical EDT path so the side tab is not stuck on「加载中」forever.
	}

	/** Background scan for tab subtitle counts without activating the graph viewport. */
	public void preloadMetrics() {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				// Never cancel/replace a scan that the user is waiting to see in the viewport.
				if (graphTabActive) {
					if (dataLoadedForMode[graphMode] && cachedBaseIndex[graphMode] != null) {
						final RelationshipGraphIndex index = cachedBaseIndex[graphMode];
						SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_GRAPH,
						        index != null ? index.getEdgeCount() : 0);
					}
					return;
				}
				if (activeScanThread != null) {
					return;
				}
				refreshGraphAsync(false);
			}
		});
	}

	private ModeTabPanel tabForMode(final int mode) {
		if (mode == RelationshipGraphScanner.MODE_MAP_NODES) {
			return mapNodesTab;
		}
		if (mode == RelationshipGraphScanner.MODE_TAGS) {
			return tagsTab;
		}
		if (mode == RelationshipGraphScanner.MODE_FAVORITES) {
			return favoritesTab;
		}
		return mapFilesTab;
	}

	private ModeTabPanel activeTab() {
		return tabForMode(graphMode);
	}

	private static int modeFromTabIndex(final int index) {
		if (index == 1) {
			return RelationshipGraphScanner.MODE_MAP_NODES;
		}
		if (index == 2) {
			return RelationshipGraphScanner.MODE_TAGS;
		}
		if (index == 3) {
			return RelationshipGraphScanner.MODE_FAVORITES;
		}
		return RelationshipGraphScanner.MODE_MAP_FILES;
	}

	private static int tabIndexFromMode(final int mode) {
		if (mode == RelationshipGraphScanner.MODE_MAP_NODES) {
			return 1;
		}
		if (mode == RelationshipGraphScanner.MODE_TAGS) {
			return 2;
		}
		if (mode == RelationshipGraphScanner.MODE_FAVORITES) {
			return 3;
		}
		return 0;
	}

	private static boolean isTagStyleMode(final int mode) {
		return mode == RelationshipGraphScanner.MODE_TAGS || mode == RelationshipGraphScanner.MODE_FAVORITES;
	}

	private void onSubTabChanged() {
		final int newMode = modeFromTabIndex(subTabs.getSelectedIndex());
		if (newMode == graphMode) {
			return;
		}
		graphMode = newMode;
		final ModeTabPanel tab = activeTab();
		if (dataLoadedForMode[graphMode] && cachedBaseIndex[graphMode] != null) {
			if (tab.focusCenterKey != null && cachedBaseIndex[graphMode] != null
			        && !containsPathKey(cachedBaseIndex[graphMode], tab.focusCenterKey)) {
				tab.focusCenterKey = null;
				tab.focusSelectedCheck.setSelected(false);
			}
			if (tab.displayIndex != null && tab.focusCenterKey == null
			        && (tab.activeSearchQuery == null || tab.activeSearchQuery.trim().length() == 0)
			        && !isTagStyleMode(graphMode)) {
				applyDisplayIndex(false);
			}
			else {
				rebuildDisplayIndex(false);
			}
		}
		else {
			refreshGraphAsync(graphTabActive);
		}
		updateCanvasModeHint();
	}

	private static boolean containsPathKey(final RelationshipGraphIndex index, final String key) {
		if (index == null || key == null) {
			return false;
		}
		final List nodes = index.getNodes();
		for (int i = 0; i < nodes.size(); i++) {
			if (key.equals(((RelationshipGraphNode) nodes.get(i)).getPathKey())) {
				return true;
			}
		}
		return false;
	}

	private void updateCanvasModeHint() {
		final RelationshipGraphService service = RelationshipGraphService.getService();
		if (service != null) {
			service.getCanvas().setModeHelpHint(modeHelpHint(graphMode));
		}
	}

	private static String modeHelpHint(final int mode) {
		if (mode == RelationshipGraphScanner.MODE_MAP_NODES) {
			return "\u8282\u70b9\u7bad\u5934/\u94fe\u63a5 \u00b7 \u70b9\u9009 \u00b7 \u53cc\u51fb\u6253\u5f00 \u00b7 \u5de6\u4fa7\u53ef\u641c\u7d22/\u90bb\u57df";
		}
		if (mode == RelationshipGraphScanner.MODE_TAGS) {
			return "\u6807\u7b7e\u67a2\u7ebd\u2194\u8282\u70b9 \u00b7 \u53cc\u51fb\u6807\u7b7e=\u805a\u7126\u5173\u8054 \u00b7 \u53cc\u51fb\u8282\u70b9=\u6253\u5f00";
		}
		if (mode == RelationshipGraphScanner.MODE_FAVORITES) {
			return "\u6536\u85cf\u6807\u7b7e\u2194\u6536\u85cf\u5bfc\u56fe \u00b7 \u53cc\u51fb\u6807\u7b7e=\u805a\u7126 \u00b7 \u53cc\u51fb\u5bfc\u56fe=\u6253\u5f00";
		}
		return "\u5bfc\u56fe\u95f4\u8d85\u94fe\u63a5 \u00b7 \u62d6\u62fd\u5e73\u79fb \u00b7 \u6eda\u8f6e\u7f29\u653e \u00b7 \u53cc\u51fb\u6253\u5f00";
	}

	private void wireCanvasListeners() {
		final RelationshipGraphService service = getOrCreateService();
		if (service == null) {
			return;
		}
		final RelationshipGraphCanvas canvas = service.getCanvas();
		canvas.setNodeOpenListener(new RelationshipGraphCanvas.NodeOpenListener() {
			public void onOpenNode(final RelationshipGraphNode node) {
				openOrExploreNode(node);
			}
		});
		canvas.setNodeContextListener(new RelationshipGraphCanvas.NodeContextListener() {
			public void onOpenNode(final RelationshipGraphNode node) {
				openOrExploreNode(node);
			}

			public void onOpenFolder(final RelationshipGraphNode node) {
				openFolder(node);
			}

			public void onFocusNeighbors(final RelationshipGraphNode node) {
				focusNeighbors(node);
			}
		});
		canvas.setSelectionListener(new RelationshipGraphCanvas.SelectionListener() {
			public void onSelectionChanged(final RelationshipGraphNode node) {
				final ModeTabPanel tab = activeTab();
				if (node == null) {
					if (tab.focusSelectedCheck.isSelected()) {
						tab.focusSelectedCheck.setSelected(false);
						tab.focusCenterKey = null;
						rebuildDisplayIndex(true);
					}
					else {
						tab.updateResultList();
					}
				}
				else if (tab.focusSelectedCheck.isSelected()) {
					tab.focusCenterKey = node.getPathKey();
					rebuildDisplayIndex(true);
				}
				else {
					tab.updateResultList();
					tab.syncListSelectionToCanvas(node);
				}
			}
		});
		updateCanvasModeHint();
	}

	void onTabActivated() {
		graphTabActive = true;
		ensureModeChromeBuilt();
		final RelationshipGraphService service = getOrCreateService();
		if (service == null) {
			return;
		}
		service.setHoldingViewport(true);
		// Show canvas immediately (loading overlay while scan runs) so the main
		// viewport never stays on MapView/"空白" during activation.
		service.showInViewport();
		subTabs.setSelectedIndex(tabIndexFromMode(graphMode));
		updateCanvasModeHint();
		revalidate();
		repaint();
		final boolean needsScan = !dataLoadedForMode[graphMode] || cachedBaseIndex[graphMode] == null;
		if (needsScan) {
			refreshGraphAsync(true);
			return;
		}
		final ModeTabPanel tab = activeTab();
		if (tab.displayIndex != null) {
			// Force fit so a previous off-screen pan/zoom never leaves a blank canvas.
			presentGraphInViewport(tab.displayIndex, false);
			return;
		}
		rebuildDisplayIndexAsync(false, true);
	}

	private boolean modeChromeBuilt;

	private void ensureModeChromeBuilt() {
		if (modeChromeBuilt) {
			return;
		}
		modeChromeBuilt = true;
		if (tagsTab.groupCascade != null) {
			tagsTab.refreshGroupCascade(cachedBaseIndex[RelationshipGraphScanner.MODE_TAGS]);
		}
		if (favoritesTab.groupCascade != null) {
			favoritesTab.refreshGroupCascade(cachedBaseIndex[RelationshipGraphScanner.MODE_FAVORITES]);
		}
		wireCanvasListeners();
	}

	void onTabDeactivated() {
		graphTabActive = false;
		cancelActiveScan();
		cancelActiveFilter();
		final RelationshipGraphService service = RelationshipGraphService.getService();
		if (service != null) {
			service.getCanvas().setLoading(false, null);
			service.setHoldingViewport(false);
			service.hideFromViewport();
		}
	}

	private void cancelActiveScan() {
		scanGeneration++;
		if (activeScanThread != null) {
			activeScanThread.interrupt();
			activeScanThread = null;
		}
	}

	private void cancelActiveFilter() {
		if (activeFilterThread != null) {
			activeFilterThread.interrupt();
			activeFilterThread = null;
		}
		clearGraphLoadingOverlay();
	}

	private void clearGraphLoadingOverlay() {
		final RelationshipGraphService svc = RelationshipGraphService.getService();
		if (svc != null) {
			svc.getCanvas().setLoading(false, null);
		}
	}

	private void presentGraphInViewport(final RelationshipGraphIndex index, final boolean preserveView) {
		final RelationshipGraphService svc = RelationshipGraphService.getService();
		if (svc == null || index == null) {
			return;
		}
		svc.setHoldingViewport(true);
		svc.getCanvas().setSearchQuery(activeTab().activeSearchQuery);
		svc.getCanvas().setModeHelpHint(modeHelpHint(graphMode));
		svc.showInViewport();
		svc.loadPreparedGraph(index, preserveView);
		svc.getCanvas().setLoading(false, null);
		svc.getCanvas().requestFocusInWindow();
		activeTab().updateStats(index);
		activeTab().updateResultList();
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (!graphTabActive) {
					return;
				}
				final RelationshipGraphService again = RelationshipGraphService.getService();
				if (again != null && again.isHoldingViewport()) {
					again.showInViewport();
					// Always fit after layout is in the viewport — a blank/off-screen canvas is worse
					again.getCanvas().fitContentInView();
					again.getCanvas().repaint();
				}
			}
		});
	}

	private RelationshipGraphService getOrCreateService() {
		final MModeController modeController = (MModeController) Controller.getCurrentModeController();
		if (modeController == null) {
			return null;
		}
		RelationshipGraphService service = RelationshipGraphService.getService();
		if (service == null) {
			RelationshipGraphService.install(modeController);
			service = RelationshipGraphService.getService();
			wireCanvasListeners();
		}
		return service;
	}

	private void refreshGraphAsync() {
		refreshGraphAsync(false);
	}

	private void refreshGraphAsync(final boolean deferViewportUntilDone) {
		cancelActiveScan();
		final ModeTabPanel tab = activeTab();
		tab.statsLabel.setText(deferViewportUntilDone || graphTabActive ? "\u9996\u6b21\u626b\u63cf\u4e2d\u2026" : "\u626b\u63cf\u4e2d\u2026");

		final RelationshipGraphService service = RelationshipGraphService.getService();
		if (service != null && (service.isGraphInViewport() || graphTabActive)) {
			service.getCanvas().setLoading(true, "\u626b\u63cf\u5173\u8054\u4e2d\u2026");
			if (graphTabActive && !service.isGraphInViewport()) {
				service.showInViewport();
			}
		}

		final int scanMode = graphMode;
		final boolean deferViewport = deferViewportUntilDone || graphTabActive;
		final ModeTabPanel scanTab = tabForMode(scanMode);
		final boolean showIsolated = scanTab.showIsolatedCheck.isSelected();
		final String scanSearchQuery = scanTab.activeSearchQuery;
		final int focusHops = scanTab.getFocusHops();
		final int generation = ++scanGeneration;

		final Thread thread = new Thread(new Runnable() {
			public void run() {
				Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
				final Thread scanThread = Thread.currentThread();
				try {
					final RelationshipGraphIndex baseIndex = RelationshipGraphScanner.scan(scanMode,
					        new RelationshipGraphScanner.ProgressListener() {
						        public void onProgress(final int scanned, final int total) {
							        SwingUtilities.invokeLater(new Runnable() {
								        public void run() {
									        if (generation != scanGeneration || scanMode != graphMode
									                || activeScanThread != scanThread) {
										        return;
									        }
									        scanTab.statsLabel.setText("\u626b\u63cf " + scanned + "/" + total);
								        }
							        });
						        }
					        });
					if (Thread.currentThread().isInterrupted() || generation != scanGeneration) {
						return;
					}
					final Set[] tagsBox = new Set[1];
					try {
						SwingUtilities.invokeAndWait(new Runnable() {
							public void run() {
								if (generation == scanGeneration && scanMode == graphMode
								        && activeScanThread == scanThread) {
									scanTab.statsLabel.setText("\u6574\u7406\u4e2d\u2026");
									scanTab.refreshGroupCascade(baseIndex);
								}
								tagsBox[0] = isTagStyleMode(scanMode) ? scanTab.collectAllowedTags(baseIndex) : null;
							}
						});
					}
					catch (final Exception waitError) {
						tagsBox[0] = isTagStyleMode(scanMode) ? scanTab.collectAllowedTags(baseIndex) : null;
					}
					final RelationshipGraphIndex preparedDisplay = RelationshipGraphIndex.buildDisplayIndex(baseIndex,
					        showIsolated, scanSearchQuery, null, focusHops, tagsBox[0]);
					if (preparedDisplay != null && preparedDisplay.getNodeCount() > 0) {
						RelationshipGraphLayout.initializePositions(preparedDisplay, 1200, 800);
					}
					if (Thread.currentThread().isInterrupted() || generation != scanGeneration) {
						return;
					}
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							finishScanPrepared(generation, scanThread, scanMode, deferViewport, baseIndex,
							        preparedDisplay);
						}
					});
				}
				catch (final Exception e) {
					LogUtils.warn(e);
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							if (generation != scanGeneration) {
								return;
							}
							scanTab.statsLabel.setText("\u626b\u63cf\u5931\u8d25");
							clearGraphLoadingOverlay();
						}
					});
				}
			}
		}, "RelationshipGraphScan");
		activeScanThread = thread;
		thread.start();
	}

	private void finishScanPrepared(final int generation, final Thread scanThread, final int scanMode,
	        final boolean deferViewportUntilDone, final RelationshipGraphIndex baseIndex,
	        final RelationshipGraphIndex preparedDisplayIndex) {
		if (generation != scanGeneration || activeScanThread != scanThread) {
			return;
		}
		activeScanThread = null;
		cachedBaseIndex[scanMode] = baseIndex;
		dataLoadedForMode[scanMode] = true;
		if (scanMode != graphMode) {
			clearGraphLoadingOverlay();
			return;
		}
		final ModeTabPanel tab = activeTab();
		tab.refreshGroupCascade(baseIndex);
		if (tab.focusCenterKey != null && !containsPathKey(baseIndex, tab.focusCenterKey)) {
			tab.focusCenterKey = null;
			tab.focusSelectedCheck.setSelected(false);
		}
		tab.displayIndex = preparedDisplayIndex;

		final RelationshipGraphService svc = RelationshipGraphService.getService();
		if (svc == null) {
			return;
		}
		tab.updateStats(tab.displayIndex);
		tab.updateResultList();
		svc.getCanvas().setSearchQuery(tab.activeSearchQuery);
		svc.getCanvas().setModeHelpHint(modeHelpHint(graphMode));
		final boolean shouldPresent = svc.isGraphInViewport() || deferViewportUntilDone || graphTabActive;
		if (shouldPresent) {
			presentGraphInViewport(tab.displayIndex, false);
		}
		else {
			clearGraphLoadingOverlay();
		}
	}

	private void rebuildDisplayIndex(final boolean preserveView) {
		final RelationshipGraphIndex base = cachedBaseIndex[graphMode];
		if (base == null) {
			return;
		}
		if (base.getNodeCount() > ASYNC_DISPLAY_INDEX_THRESHOLD) {
			rebuildDisplayIndexAsync(preserveView, false);
			return;
		}
		final ModeTabPanel tab = activeTab();
		tab.displayIndex = RelationshipGraphIndex.buildDisplayIndex(base, tab.showIsolatedCheck.isSelected(),
		        tab.activeSearchQuery, tab.focusCenterKey, tab.getFocusHops(), tab.collectAllowedTags(base));
		if (tab.displayIndex != null && tab.displayIndex.getNodeCount() > 0 && !preserveView) {
			RelationshipGraphLayout.initializePositions(tab.displayIndex, 1200, 800);
		}
		applyDisplayIndex(preserveView);
	}

	private void rebuildDisplayIndexAsync(final boolean preserveView, final boolean deferViewportUntilReady) {
		final RelationshipGraphIndex base = cachedBaseIndex[graphMode];
		if (base == null) {
			return;
		}
		final ModeTabPanel tab = activeTab();
		final boolean showIsolated = tab.showIsolatedCheck.isSelected();
		final String searchQuery = tab.activeSearchQuery;
		final String focusKey = tab.focusCenterKey;
		final int focusHops = tab.getFocusHops();
		final Set allowedTags = tab.collectAllowedTags(base);
		final int mode = graphMode;
		tab.statsLabel.setText("\u66f4\u65b0\u4e2d\u2026");
		final RelationshipGraphService svc = RelationshipGraphService.getService();
		if (svc != null && svc.isGraphInViewport() && !deferViewportUntilReady) {
			svc.getCanvas().setLoading(true, "\u66f4\u65b0\u5173\u7cfb\u56fe\u2026");
		}
		cancelActiveFilter();
		final Thread thread = new Thread(new Runnable() {
			public void run() {
				Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
				final Thread filterThread = Thread.currentThread();
				try {
					final RelationshipGraphIndex built = RelationshipGraphIndex.buildDisplayIndex(base, showIsolated,
					        searchQuery, focusKey, focusHops, allowedTags);
					if (Thread.currentThread().isInterrupted()) {
						return;
					}
					if (built != null && built.getNodeCount() > 0 && !preserveView) {
						RelationshipGraphLayout.initializePositions(built, 1200, 800);
					}
					if (Thread.currentThread().isInterrupted()) {
						return;
					}
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							try {
								if (mode != graphMode || activeFilterThread != filterThread) {
									return;
								}
								activeFilterThread = null;
								tab.displayIndex = built;
								if (svc != null) {
									if (svc.isGraphInViewport() || deferViewportUntilReady || graphTabActive) {
										presentGraphInViewport(tab.displayIndex, preserveView);
									}
									else {
										tab.updateStats(tab.displayIndex);
										tab.updateResultList();
									}
								}
							}
							finally {
								clearGraphLoadingOverlay();
							}
						}
					});
				}
				catch (final Exception e) {
					LogUtils.warn(e);
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							try {
								if (activeFilterThread != filterThread) {
									return;
								}
								activeFilterThread = null;
								tab.statsLabel.setText("\u66f4\u65b0\u5931\u8d25");
							}
							finally {
								clearGraphLoadingOverlay();
							}
						}
					});
				}
			}
		}, "RelationshipGraphFilter");
		activeFilterThread = thread;
		thread.start();
	}

	private void applyDisplayIndex(final boolean preserveView) {
		final ModeTabPanel tab = activeTab();
		if (tab.displayIndex == null) {
			return;
		}
		if (graphTabActive) {
			presentGraphInViewport(tab.displayIndex, preserveView);
			return;
		}
		final RelationshipGraphService service = RelationshipGraphService.getService();
		if (service != null) {
			service.loadPreparedGraph(tab.displayIndex, preserveView);
			service.getCanvas().setSearchQuery(tab.activeSearchQuery);
			service.getCanvas().setModeHelpHint(modeHelpHint(graphMode));
			if (!preserveView && tab.activeSearchQuery != null && tab.activeSearchQuery.trim().length() > 0) {
				service.getCanvas().focusOnMatches();
			}
			service.getCanvas().requestFocusInWindow();
		}
		tab.updateStats(tab.displayIndex);
		tab.updateResultList();
	}

	private RelationshipGraphNode selectedNodeFromService() {
		final RelationshipGraphService svc = RelationshipGraphService.getService();
		return svc != null ? svc.getCanvas().getSelectedNode() : null;
	}

	private void focusNeighbors(final RelationshipGraphNode node) {
		if (node == null) {
			return;
		}
		final ModeTabPanel tab = activeTab();
		tab.focusSelectedCheck.setSelected(true);
		tab.hopCombo.setEnabled(true);
		tab.focusCenterKey = node.getPathKey();
		rebuildDisplayIndex(true);
		final RelationshipGraphService service = RelationshipGraphService.getService();
		if (service != null) {
			service.getCanvas().selectAndFocusNode(node);
		}
	}

	private void openOrExploreNode(final RelationshipGraphNode node) {
		if (node == null) {
			return;
		}
		if (node.isTagNode()) {
			focusNeighbors(node);
			return;
		}
		openMindMapAndExit(node);
	}

	private void openMindMapAndExit(final RelationshipGraphNode node) {
		if (node == null) {
			return;
		}
		final File file = node.getFile();
		if (file == null || !file.exists()) {
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					Controller.getCurrentController().getViewController().openDocument(node.getOpenUrl());
					RelationshipGraphIntegration.exitGraphViewDueToMapSwitch();
				}
				catch (final Exception e) {
					LogUtils.warn(e);
				}
			}
		});
	}

	private void openFolder(final RelationshipGraphNode node) {
		if (node == null || node.getFile() == null) {
			return;
		}
		MindMapOpenLocationAction.openContainingFolder(node.getFile());
	}

	/** Per-mode tab: search + related nodes list + controls. */
	private final class ModeTabPanel extends JPanel {

		private static final long serialVersionUID = 1L;

		private final int mode;
		private final JTextField searchField;
		private final JLabel tipLabel;
		private final JLabel statsLabel;
		private final JLabel resultHeader;
		private final DefaultListModel resultListModel;
		private final JList resultList;
		private final JCheckBox showIsolatedCheck;
		private final JCheckBox focusSelectedCheck;
		private final JComboBox hopCombo;
		private final JComponent groupCascade;

		private String activeSearchQuery = "";
		private String focusCenterKey;
		private RelationshipGraphIndex displayIndex;

		private ModeTabPanel(final int mode) {
			super(new BorderLayout(0, 6));
			this.mode = mode;
			setBorder(BorderFactory.createEmptyBorder(6, 2, 4, 2));

			tipLabel = new JLabel("<html><body style='width:220px'>" + tipHtmlForMode(mode) + "</body></html>");
			tipLabel.setForeground(MUTED);
			tipLabel.setFont(tipLabel.getFont().deriveFont(Font.PLAIN, 11f));
			tipLabel.setOpaque(true);
			tipLabel.setBackground(TIP_BG);
			tipLabel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
			        BorderFactory.createEmptyBorder(6, 8, 6, 8)));

			if (isTagStyleMode(mode)) {
				JComponent cascade = null;
				try {
					final String propGroup = mode == RelationshipGraphScanner.MODE_FAVORITES ? PROP_FAV_GROUP
					        : PROP_TAGS_GROUP;
					final String propDirect = mode == RelationshipGraphScanner.MODE_FAVORITES ? PROP_FAV_DIRECT
					        : PROP_TAGS_DIRECT;
					final Runnable onChange = new Runnable() {
						public void run() {
							if (RelationshipGraphSideTabPanel.this.graphMode == ModeTabPanel.this.mode) {
								rebuildDisplayIndex(true);
							}
						}
					};
					// Create via exported factory — never touch TagGroupCascadeBar from this
					// bundle (OSGi does not export ...tagfilter by default → NoClassDefFoundError).
					if (mode == RelationshipGraphScanner.MODE_FAVORITES) {
						cascade = TagGroupFilterBarFactory.createFavoritesBar(propGroup, propDirect, onChange);
					}
					else {
						cascade = TagGroupFilterBarFactory.createTagsBar(propGroup, propDirect, onChange);
					}
				}
				catch (final Throwable t) {
					LogUtils.warn(t);
					cascade = null;
				}
				groupCascade = cascade;
			}
			else {
				groupCascade = null;
			}

			searchField = new JTextField();
			searchField.setToolTipText("\u6309\u6807\u7b7e/\u540d\u79f0\u641c\u7d22\uff0cEnter \u5e94\u7528\uff0cEsc \u6e05\u9664");
			searchField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
			        BorderFactory.createEmptyBorder(3, 6, 3, 6)));
			searchField.addKeyListener(new KeyAdapter() {
				public void keyPressed(final KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_ENTER) {
						runSearch();
					}
					else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
						clearSearch();
					}
				}
			});

			final JPanel searchRow = new JPanel(new BorderLayout(4, 4));
			searchRow.add(searchField, BorderLayout.CENTER);

			final JPanel searchButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			final JButton searchButton = new JButton("\u641c\u7d22");
			searchButton.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					runSearch();
				}
			});
			final JButton clearButton = new JButton("\u6e05\u9664");
			clearButton.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					clearSearch();
				}
			});
			searchButtons.add(searchButton);
			searchButtons.add(clearButton);
			searchRow.add(searchButtons, BorderLayout.EAST);

			final JPanel north = new JPanel(new BorderLayout(0, 6));
			north.add(tipLabel, BorderLayout.NORTH);
			if (groupCascade != null) {
				final JPanel cascadeWrap = new JPanel(new BorderLayout());
				cascadeWrap.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
				        BorderFactory.createEmptyBorder(4, 4, 4, 4)));
				cascadeWrap.add(groupCascade, BorderLayout.CENTER);
				north.add(cascadeWrap, BorderLayout.CENTER);
				north.add(searchRow, BorderLayout.SOUTH);
			}
			else {
				north.add(searchRow, BorderLayout.CENTER);
			}
			add(north, BorderLayout.NORTH);

			statsLabel = new JLabel(" ");
			statsLabel.setForeground(MUTED);
			statsLabel.setFont(statsLabel.getFont().deriveFont(Font.PLAIN, 11f));

			resultHeader = new JLabel("\u5173\u8054\u9879");
			resultHeader.setFont(resultHeader.getFont().deriveFont(Font.BOLD, 12f));

			resultListModel = new DefaultListModel();
			resultList = new JList(resultListModel);
			resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			resultList.setCellRenderer(new NodeListCellRenderer());
			resultList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
				public void valueChanged(final javax.swing.event.ListSelectionEvent e) {
					if (syncingListSelection || e.getValueIsAdjusting() || mode != graphMode) {
						return;
					}
					final NodeListEntry entry = (NodeListEntry) resultList.getSelectedValue();
					if (entry == null) {
						return;
					}
					final RelationshipGraphService svc = RelationshipGraphService.getService();
					if (svc != null) {
						svc.getCanvas().selectAndFocusNode(entry.node);
					}
				}
			});
			resultList.addMouseListener(new MouseAdapter() {
				public void mouseClicked(final MouseEvent e) {
					if (e.getClickCount() != 2) {
						return;
					}
					final NodeListEntry entry = (NodeListEntry) resultList.getSelectedValue();
					if (entry != null) {
						openOrExploreNode(entry.node);
					}
				}
			});

			final JScrollPane resultScroll = new JScrollPane(resultList);
			resultScroll.setPreferredSize(new Dimension(100, 180));
			resultScroll.setBorder(BorderFactory.createLineBorder(BORDER));

			final JPanel listPanel = new JPanel(new BorderLayout(0, 4));
			listPanel.add(resultHeader, BorderLayout.NORTH);
			listPanel.add(resultScroll, BorderLayout.CENTER);
			add(listPanel, BorderLayout.CENTER);

			showIsolatedCheck = new JCheckBox("\u663e\u793a\u65e0\u8fde\u63a5\u9879", false);
			showIsolatedCheck.setToolTipText("\u663e\u793a\u6ca1\u6709\u4efb\u4f55\u5173\u8054\u8fb9\u7684\u72ec\u7acb\u9879");
			showIsolatedCheck.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					if (mode == graphMode) {
						rebuildDisplayIndex(true);
					}
				}
			});
			focusSelectedCheck = new JCheckBox("\u53ea\u770b\u9009\u4e2d\u90bb\u57df", false);
			focusSelectedCheck.setToolTipText("\u5148\u5728\u56fe\u4e2d\u9009\u4e2d\u4e00\u4e2a\u70b9\uff0c\u518d\u52fe\u9009\u4ee5\u53ea\u663e\u793a\u5176\u90bb\u57df");
			focusSelectedCheck.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					if (mode == graphMode) {
						applyFocusSelection();
					}
				}
			});
			hopCombo = new JComboBox(new Object[] { "1 \u8df3", "2 \u8df3" });
			hopCombo.setSelectedIndex(0);
			hopCombo.setToolTipText("\u90bb\u57df\u89c6\u56fe\u7684\u8df3\u6570\uff1a1=\u76f4\u63a5\u5173\u8054\uff0c2=\u518d\u6269\u4e00\u5c42");
			hopCombo.setEnabled(false);
			hopCombo.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					if (mode == graphMode && focusSelectedCheck.isSelected()) {
						rebuildDisplayIndex(true);
					}
				}
			});

			final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			final JButton refreshButton = new JButton("\u5237\u65b0");
			refreshButton.setToolTipText("\u91cd\u65b0\u626b\u63cf\u5de5\u4f5c\u533a\u6570\u636e");
			refreshButton.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					if (mode != graphMode) {
						subTabs.setSelectedIndex(tabIndexFromMode(mode));
					}
					dataLoadedForMode[mode] = false;
					cachedBaseIndex[mode] = null;
					refreshGraphAsync();
				}
			});
			final JButton resetViewButton = new JButton("\u91cd\u7f6e\u89c6\u56fe");
			resetViewButton.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					if (mode != graphMode) {
						return;
					}
					final RelationshipGraphService service = RelationshipGraphService.getService();
					if (service != null) {
						service.getCanvas().resetView();
					}
				}
			});
			final JButton clearFocusButton = new JButton("\u663e\u793a\u5168\u56fe");
			clearFocusButton.setToolTipText("\u53d6\u6d88\u90bb\u57df/\u641c\u7d22\u7f29\u5c0f\uff0c\u56de\u5230\u5f53\u524d\u5206\u7ec4\u4e0b\u7684\u5168\u56fe");
			clearFocusButton.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					if (mode != graphMode) {
						return;
					}
					searchField.setText("");
					activeSearchQuery = "";
					focusCenterKey = null;
					focusSelectedCheck.setSelected(false);
					hopCombo.setEnabled(false);
					rebuildDisplayIndex(false);
				}
			});
			actions.add(refreshButton);
			actions.add(resetViewButton);
			actions.add(clearFocusButton);

			final JPanel focusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			focusRow.add(focusSelectedCheck);
			focusRow.add(new JLabel("\u8df3\u6570"));
			focusRow.add(hopCombo);

			final JPanel footer = new JPanel(new BorderLayout(0, 4));
			footer.add(statsLabel, BorderLayout.NORTH);
			final JPanel options = new JPanel(new BorderLayout(0, 2));
			options.add(showIsolatedCheck, BorderLayout.NORTH);
			options.add(focusRow, BorderLayout.CENTER);
			footer.add(options, BorderLayout.CENTER);
			footer.add(actions, BorderLayout.SOUTH);
			add(footer, BorderLayout.SOUTH);
		}

		private void refreshGroupCascade(final RelationshipGraphIndex base) {
			if (groupCascade != null) {
				TagGroupFilterBarFactory.setAvailableTags(groupCascade,
				        collectTagNamesFromBase(base != null ? base : cachedBaseIndex[mode]));
				TagGroupFilterBarFactory.rebuild(groupCascade);
			}
		}

		private Set collectAllowedTags(final RelationshipGraphIndex base) {
			if (!isTagStyleMode(mode) || groupCascade == null) {
				return null;
			}
			if (TagGroupFilterBarFactory.isAllScope(groupCascade)) {
				return null;
			}
			final Set names = collectTagNamesFromBase(base != null ? base : cachedBaseIndex[mode]);
			final Set allowed = new HashSet();
			for (final Object nameObj : names) {
				final String tag = (String) nameObj;
				if (TagGroupFilterBarFactory.tagMatchesActiveScope(groupCascade, tag)) {
					allowed.add(tag);
				}
			}
			return allowed;
		}

		private Set collectTagNamesFromBase(final RelationshipGraphIndex base) {
			final Set names = new HashSet();
			if (base == null) {
				return names;
			}
			final List nodes = base.getNodes();
			for (int i = 0; i < nodes.size(); i++) {
				final RelationshipGraphNode node = (RelationshipGraphNode) nodes.get(i);
				if (node.isTagNode() && node.getTagName() != null) {
					names.add(node.getTagName());
				}
			}
			return names;
		}

		private int getFocusHops() {
			return hopCombo.getSelectedIndex() <= 0 ? 1 : 2;
		}

		private void runSearch() {
			if (mode != graphMode) {
				subTabs.setSelectedIndex(tabIndexFromMode(mode));
			}
			activeSearchQuery = searchField.getText();
			focusCenterKey = null;
			focusSelectedCheck.setSelected(false);
			hopCombo.setEnabled(false);
			rebuildDisplayIndex(true);
		}

		private void clearSearch() {
			searchField.setText("");
			activeSearchQuery = "";
			focusCenterKey = null;
			focusSelectedCheck.setSelected(false);
			hopCombo.setEnabled(false);
			if (mode == graphMode) {
				rebuildDisplayIndex(true);
			}
		}

		private void applyFocusSelection() {
			hopCombo.setEnabled(focusSelectedCheck.isSelected());
			if (!focusSelectedCheck.isSelected()) {
				focusCenterKey = null;
				rebuildDisplayIndex(true);
				return;
			}
			final RelationshipGraphService service = RelationshipGraphService.getService();
			if (service == null) {
				focusSelectedCheck.setSelected(false);
				hopCombo.setEnabled(false);
				return;
			}
			final RelationshipGraphNode selected = service.getCanvas().getSelectedNode();
			if (selected == null) {
				focusSelectedCheck.setSelected(false);
				hopCombo.setEnabled(false);
				statsLabel.setText("\u5148\u5728\u56fe\u4e2d\u70b9\u9009\u4e00\u4e2a\u70b9");
				return;
			}
			focusCenterKey = selected.getPathKey();
			rebuildDisplayIndex(true);
			service.getCanvas().focusOnNode(selected);
		}

		private void updateStats(final RelationshipGraphIndex index) {
			if (index == null) {
				statsLabel.setText(" ");
				SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_GRAPH, 0);
				return;
			}
			SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_GRAPH, index.getEdgeCount());
			final String unit;
			if (index.getGraphMode() == RelationshipGraphScanner.MODE_MAP_NODES) {
				unit = "\u8282\u70b9";
			}
			else if (index.getGraphMode() == RelationshipGraphScanner.MODE_TAGS) {
				unit = "\u6807\u7b7e/\u8282\u70b9";
			}
			else if (index.getGraphMode() == RelationshipGraphScanner.MODE_FAVORITES) {
				unit = "\u6807\u7b7e/\u6536\u85cf";
			}
			else {
				unit = "\u5bfc\u56fe";
			}
			String text = index.getNodeCount() + " " + unit + " \u00b7 " + index.getEdgeCount() + " \u8fde\u63a5";
			if (index.getTotalNodeCount() > index.getNodeCount()) {
				text += " \u00b7 \u5171 " + index.getTotalNodeCount() + "\uff08\u5df2\u7b5b\u9009\uff09";
			}
			if (focusCenterKey != null) {
				text += " \u00b7 " + getFocusHops() + "\u8df3\u90bb\u57df";
			}
			statsLabel.setText(text);
		}

		private void updateResultList() {
			resultListModel.clear();
			if (displayIndex == null) {
				resultHeader.setText("\u5173\u8054\u9879");
				return;
			}
			final List nodes = collectResultNodes();
			final int total = nodes.size();
			final int shown = Math.min(RESULT_LIST_CAP, total);
			for (int i = 0; i < shown; i++) {
				resultListModel.addElement(new NodeListEntry((RelationshipGraphNode) nodes.get(i)));
			}
			if (activeSearchQuery != null && activeSearchQuery.trim().length() > 0) {
				resultHeader.setText("\u5339\u914d " + shown + (total > shown ? "/" + total : ""));
			}
			else if (focusCenterKey != null) {
				resultHeader.setText("\u90bb\u57df " + shown + (total > shown ? "/" + total : ""));
			}
			else {
				final RelationshipGraphNode selected = selectedNodeFromService();
				if (selected != null) {
					resultHeader.setText("\u9009\u4e2d\u53ca\u90bb\u5c45");
				}
				else {
					resultHeader.setText("\u5173\u8054\u9879 " + shown + (total > shown ? "/" + total : ""));
				}
			}
			syncListSelectionToCanvas(selectedNodeFromService());
		}

		private List collectResultNodes() {
			final LinkedHashSet ordered = new LinkedHashSet();
			final List allNodes = displayIndex.getNodes();
			if (activeSearchQuery != null && activeSearchQuery.trim().length() > 0) {
				final String query = activeSearchQuery.trim().toLowerCase(Locale.ENGLISH);
				for (int i = 0; i < allNodes.size(); i++) {
					final RelationshipGraphNode node = (RelationshipGraphNode) allNodes.get(i);
					if (nodeMatchesQuery(node, query)) {
						ordered.add(node);
					}
				}
				return new ArrayList(ordered);
			}
			if (focusCenterKey != null) {
				ordered.addAll(allNodes);
				return new ArrayList(ordered);
			}
			final RelationshipGraphNode selected = selectedNodeFromService();
			if (selected != null) {
				ordered.add(selected);
				appendNeighbors(selected, ordered);
				return new ArrayList(ordered);
			}
			final List all = new ArrayList(allNodes);
			if (isTagStyleMode(mode)) {
				Collections.sort(all, TAG_FIRST_COMPARATOR);
			}
			return all;
		}

		private void appendNeighbors(final RelationshipGraphNode center, final Set ordered) {
			final String key = center.getPathKey();
			final List edges = displayIndex.getEdges();
			for (int i = 0; i < edges.size(); i++) {
				final RelationshipGraphEdge edge = (RelationshipGraphEdge) edges.get(i);
				if (key.equals(edge.getSource().getPathKey())) {
					ordered.add(edge.getTarget());
				}
				else if (key.equals(edge.getTarget().getPathKey())) {
					ordered.add(edge.getSource());
				}
			}
		}

		private boolean nodeMatchesQuery(final RelationshipGraphNode node, final String query) {
			return node.getLabel().toLowerCase(Locale.ENGLISH).indexOf(query) >= 0
			        || node.getMapLabel().toLowerCase(Locale.ENGLISH).indexOf(query) >= 0
			        || node.getPathKey().toLowerCase(Locale.ENGLISH).indexOf(query) >= 0;
		}

		private void syncListSelectionToCanvas(final RelationshipGraphNode node) {
			if (node == null) {
				return;
			}
			syncingListSelection = true;
			try {
				for (int i = 0; i < resultListModel.getSize(); i++) {
					final NodeListEntry entry = (NodeListEntry) resultListModel.getElementAt(i);
					if (node.getPathKey().equals(entry.node.getPathKey())) {
						resultList.setSelectedIndex(i);
						resultList.ensureIndexIsVisible(i);
						return;
					}
				}
			}
			finally {
				syncingListSelection = false;
			}
		}
	}

	private static String tipHtmlForMode(final int mode) {
		if (mode == RelationshipGraphScanner.MODE_MAP_NODES) {
			return "\u8282\u70b9\u95f4\u7684\u7bad\u5934\u4e0e\u94fe\u63a5\u3002\u5e93\u5927\u65f6\u5148\u7528\u641c\u7d22\u6216\u300c\u53ea\u770b\u90bb\u57df\u300d\u3002";
		}
		if (mode == RelationshipGraphScanner.MODE_TAGS) {
			return "\u5de6\uff1a\u6807\u7b7e\u67a2\u7ebd\uff1b\u53f3\uff1a\u5e26\u6807\u7b7e\u7684\u8282\u70b9\u3002\u7528\u5206\u7ec4\u7b5b\u9009\uff0c\u53cc\u51fb\u6807\u7b7e\u805a\u7126\u5173\u8054\u3002";
		}
		if (mode == RelationshipGraphScanner.MODE_FAVORITES) {
			return "\u6536\u85cf\u6807\u7b7e\u4e0e\u6536\u85cf\u5bfc\u56fe\u7684\u5173\u8054\u3002\u5206\u7ec4\u4e0e\u300c\u6536\u85cf\u300d\u4fa7\u680f\u72ec\u7acb\u3002";
		}
		return "\u5bfc\u56fe\u4e4b\u95f4\u7684\u8d85\u94fe\u63a5\u3002\u70b9\u9009\u67d0\u5bfc\u56fe\u540e\u53ef\u300c\u53ea\u770b\u90bb\u57df\u300d\u3002";
	}

	private static final class NodeListEntry {
		private final RelationshipGraphNode node;

		private NodeListEntry(final RelationshipGraphNode node) {
			this.node = node;
		}
	}

	private static final class NodeListCellRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		public java.awt.Component getListCellRendererComponent(final JList list, final Object value, final int index,
		        final boolean isSelected, final boolean cellHasFocus) {
			final java.awt.Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value instanceof NodeListEntry) {
				final RelationshipGraphNode node = ((NodeListEntry) value).node;
				if (node.isTagNode()) {
					setText("\u3010" + node.getLabel() + "\u3011");
				}
				else if (node.isMapNode()) {
					setText(node.getLabel());
				}
				else {
					setText(node.getLabel() + " \u00b7 " + shorten(node.getMapLabel(), 24));
				}
			}
			return c;
		}

		private static String shorten(final String text, final int max) {
			if (text == null) {
				return "";
			}
			if (text.length() <= max) {
				return text;
			}
			return text.substring(0, max - 1) + "\u2026";
		}
	}
}
