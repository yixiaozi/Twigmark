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

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.plugin.workspace.actions.MindMapOpenLocationAction;

/**
 * Left sidebar: three mode tabs (map links / node links / tags), each with search and related-item list.
 */
public class RelationshipGraphSideTabPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final Color MUTED = new Color(110, 110, 115);
	private static final Color BORDER = new Color(198, 200, 205);
	private static final int RESULT_LIST_CAP = 200;
	private static final int ASYNC_DISPLAY_INDEX_THRESHOLD = 80;
	private static final int MODE_COUNT = 3;
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
	private final JTabbedPane subTabs;

	private final RelationshipGraphIndex[] cachedBaseIndex = new RelationshipGraphIndex[MODE_COUNT];
	private final boolean[] dataLoadedForMode = new boolean[MODE_COUNT];

	private volatile int graphMode = RelationshipGraphScanner.MODE_MAP_FILES;
	private Thread activeScanThread;
	private Thread activeFilterThread;
	private volatile boolean syncingListSelection;

	public RelationshipGraphSideTabPanel() {
		super(new BorderLayout(4, 4));
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		mapFilesTab = new ModeTabPanel(RelationshipGraphScanner.MODE_MAP_FILES);
		mapNodesTab = new ModeTabPanel(RelationshipGraphScanner.MODE_MAP_NODES);
		tagsTab = new ModeTabPanel(RelationshipGraphScanner.MODE_TAGS);

		subTabs = new JTabbedPane(JTabbedPane.TOP);
		subTabs.setFont(subTabs.getFont().deriveFont(Font.PLAIN, 12f));
		subTabs.addTab("\u5bfc\u56fe\u5173\u8054", mapFilesTab);
		subTabs.addTab("\u8282\u70b9\u5173\u8054", mapNodesTab);
		subTabs.addTab("\u6807\u7b7e", tagsTab);
		subTabs.addChangeListener(new ChangeListener() {
			public void stateChanged(final ChangeEvent e) {
				onSubTabChanged();
			}
		});
		add(subTabs, BorderLayout.CENTER);
		setMinimumSize(new Dimension(220, 320));
		setPreferredSize(new Dimension(260, 400));

		wireCanvasListeners();
	}

	/** Background scan for tab subtitle counts without activating the graph viewport. */
	public void preloadMetrics() {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				refreshGraphAsync();
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
		return RelationshipGraphScanner.MODE_MAP_FILES;
	}

	private static int tabIndexFromMode(final int mode) {
		if (mode == RelationshipGraphScanner.MODE_MAP_NODES) {
			return 1;
		}
		if (mode == RelationshipGraphScanner.MODE_TAGS) {
			return 2;
		}
		return 0;
	}

	private void onSubTabChanged() {
		final int newMode = modeFromTabIndex(subTabs.getSelectedIndex());
		if (newMode == graphMode) {
			return;
		}
		graphMode = newMode;
		activeTab().focusCenterKey = null;
		activeTab().focusSelectedCheck.setSelected(false);
		if (dataLoadedForMode[graphMode] && cachedBaseIndex[graphMode] != null) {
			final ModeTabPanel tab = activeTab();
			if (tab.displayIndex != null) {
				applyDisplayIndex(false);
			}
			else {
				rebuildDisplayIndex(false);
			}
		}
		else {
			refreshGraphAsync();
		}
	}

	private void wireCanvasListeners() {
		final RelationshipGraphService service = getOrCreateService();
		if (service == null) {
			return;
		}
		final RelationshipGraphCanvas canvas = service.getCanvas();
		canvas.setNodeOpenListener(new RelationshipGraphCanvas.NodeOpenListener() {
			public void onOpenNode(final RelationshipGraphNode node) {
				openMindMapAndExit(node);
			}
		});
		canvas.setNodeContextListener(new RelationshipGraphCanvas.NodeContextListener() {
			public void onOpenNode(final RelationshipGraphNode node) {
				openMindMapAndExit(node);
			}

			public void onOpenFolder(final RelationshipGraphNode node) {
				openFolder(node);
			}
		});
		canvas.setSelectionListener(new RelationshipGraphCanvas.SelectionListener() {
			public void onSelectionChanged(final RelationshipGraphNode node) {
				final ModeTabPanel tab = activeTab();
				if (node == null) {
					tab.focusSelectedCheck.setSelected(false);
					tab.focusCenterKey = null;
					rebuildDisplayIndex(true);
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
	}

	void onTabActivated() {
		final RelationshipGraphService service = getOrCreateService();
		if (service == null) {
			return;
		}
		subTabs.setSelectedIndex(tabIndexFromMode(graphMode));
		final boolean needsScan = !dataLoadedForMode[graphMode] || cachedBaseIndex[graphMode] == null;
		if (needsScan) {
			refreshGraphAsync(true);
			return;
		}
		final ModeTabPanel tab = activeTab();
		if (tab.displayIndex != null) {
			presentGraphInViewport(tab.displayIndex, true);
			return;
		}
		rebuildDisplayIndexAsync(false, true);
	}

	void onTabDeactivated() {
		cancelActiveScan();
		cancelActiveFilter();
		final RelationshipGraphService service = RelationshipGraphService.getService();
		if (service != null) {
			service.getCanvas().setLoading(false, null);
			service.hideFromViewport();
		}
	}

	private void cancelActiveScan() {
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
		svc.getCanvas().setSearchQuery(activeTab().activeSearchQuery);
		svc.showInViewport();
		svc.loadPreparedGraph(index, preserveView);
		svc.getCanvas().setLoading(false, null);
		svc.getCanvas().requestFocusInWindow();
		activeTab().updateStats(index);
		activeTab().updateResultList();
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
		tab.statsLabel.setText(deferViewportUntilDone ? "\u9996\u6b21\u626b\u63cf\u4e2d\u2026" : "\u626b\u63cf\u4e2d\u2026");

		final RelationshipGraphService service = RelationshipGraphService.getService();
		if (service != null && service.isGraphInViewport()) {
			service.getCanvas().setLoading(true, "\u626b\u63cf\u5173\u8054\u4e2d\u2026");
		}

		final int scanMode = graphMode;
		final boolean deferViewport = deferViewportUntilDone;
		final ModeTabPanel scanTab = tabForMode(scanMode);
		final boolean showIsolated = scanTab.showIsolatedCheck.isSelected();
		final String scanSearchQuery = scanTab.activeSearchQuery;

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
									        if (scanMode != graphMode || activeScanThread != scanThread) {
										        return;
									        }
									        scanTab.statsLabel.setText("\u626b\u63cf " + scanned + "/" + total);
								        }
							        });
						        }
					        });
					if (Thread.currentThread().isInterrupted()) {
						return;
					}
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							if (scanMode == graphMode && activeScanThread == scanThread) {
								scanTab.statsLabel.setText("\u6574\u7406\u4e2d\u2026");
							}
						}
					});
					final RelationshipGraphIndex preparedDisplay = RelationshipGraphIndex.buildDisplayIndex(baseIndex,
					        showIsolated, scanSearchQuery, null);
					if (preparedDisplay != null && preparedDisplay.getNodeCount() > 0) {
						RelationshipGraphLayout.initializePositions(preparedDisplay, 1200, 800);
					}
					if (Thread.currentThread().isInterrupted()) {
						return;
					}
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							finishScanPrepared(scanMode, deferViewport, baseIndex, preparedDisplay);
						}
					});
				}
				catch (final Exception e) {
					LogUtils.warn(e);
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							scanTab.statsLabel.setText("\u626b\u63cf\u5931\u8d25");
							final RelationshipGraphService svc = RelationshipGraphService.getService();
							if (svc != null) {
								svc.getCanvas().setLoading(false, null);
							}
						}
					});
				}
			}
		}, "RelationshipGraphScan");
		activeScanThread = thread;
		thread.start();
	}

	private void finishScanPrepared(final int scanMode, final boolean deferViewportUntilDone,
	        final RelationshipGraphIndex baseIndex, final RelationshipGraphIndex preparedDisplayIndex) {
		activeScanThread = null;
		cachedBaseIndex[scanMode] = baseIndex;
		dataLoadedForMode[scanMode] = true;
		if (scanMode != graphMode) {
			return;
		}
		final ModeTabPanel tab = activeTab();
		tab.focusCenterKey = null;
		tab.focusSelectedCheck.setSelected(false);
		tab.displayIndex = preparedDisplayIndex;

		final RelationshipGraphService svc = RelationshipGraphService.getService();
		if (svc == null) {
			return;
		}
		tab.updateStats(tab.displayIndex);
		tab.updateResultList();
		svc.getCanvas().setSearchQuery(tab.activeSearchQuery);
		if (svc.isGraphInViewport()) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					if (tab.displayIndex != null) {
						svc.loadPreparedGraph(tab.displayIndex, false);
					}
					svc.getCanvas().setLoading(false, null);
					svc.getCanvas().requestFocusInWindow();
				}
			});
		}
		else if (deferViewportUntilDone) {
			presentGraphInViewport(tab.displayIndex, false);
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
		        tab.activeSearchQuery, tab.focusCenterKey);
		if (tab.displayIndex != null && tab.displayIndex.getNodeCount() > 0 && !preserveView) {
			RelationshipGraphLayout.initializePositions(tab.displayIndex, 1200, 800);
		}
		applyDisplayIndex(preserveView);
	}

	private void rebuildDisplayIndexAsync(final boolean preserveView) {
		rebuildDisplayIndexAsync(preserveView, false);
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
					        searchQuery, focusKey);
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
									if (svc.isGraphInViewport()) {
										svc.getCanvas().setLoading(false, null);
										svc.loadPreparedGraph(tab.displayIndex, preserveView);
										svc.getCanvas().setSearchQuery(tab.activeSearchQuery);
										tab.updateStats(tab.displayIndex);
										tab.updateResultList();
									}
									else if (deferViewportUntilReady) {
										presentGraphInViewport(tab.displayIndex, preserveView);
									}
									else {
										tab.updateStats(tab.displayIndex);
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
		final RelationshipGraphService service = RelationshipGraphService.getService();
		if (service != null) {
			service.loadPreparedGraph(tab.displayIndex, preserveView);
			service.getCanvas().setSearchQuery(tab.activeSearchQuery);
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
		private final JLabel statsLabel;
		private final JLabel resultHeader;
		private final DefaultListModel resultListModel;
		private final JList resultList;
		private final JCheckBox showIsolatedCheck;
		private final JCheckBox focusSelectedCheck;

		private String activeSearchQuery = "";
		private String focusCenterKey;
		private RelationshipGraphIndex displayIndex;

		private ModeTabPanel(final int mode) {
			super(new BorderLayout(0, 6));
			this.mode = mode;
			setBorder(BorderFactory.createEmptyBorder(6, 2, 4, 2));

			searchField = new JTextField();
			searchField.setBorder(BorderFactory.createCompoundBorder(
			        BorderFactory.createLineBorder(BORDER),
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
			add(searchRow, BorderLayout.NORTH);

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
						openMindMapAndExit(entry.node);
					}
				}
			});

			final JScrollPane resultScroll = new JScrollPane(resultList);
			resultScroll.setPreferredSize(new Dimension(100, 220));
			resultScroll.setBorder(BorderFactory.createLineBorder(BORDER));

			final JPanel listPanel = new JPanel(new BorderLayout(0, 4));
			listPanel.add(resultHeader, BorderLayout.NORTH);
			listPanel.add(resultScroll, BorderLayout.CENTER);
			add(listPanel, BorderLayout.CENTER);

			showIsolatedCheck = new JCheckBox("\u663e\u793a\u65e0\u8fde\u63a5\u9879", false);
			showIsolatedCheck.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					if (mode == graphMode) {
						rebuildDisplayIndex(true);
					}
				}
			});
			focusSelectedCheck = new JCheckBox("\u53ea\u770b\u9009\u4e2d\u7684\u76f4\u63a5\u5173\u8054", false);
			focusSelectedCheck.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					if (mode == graphMode) {
						applyFocusSelection();
					}
				}
			});

			final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			final JButton refreshButton = new JButton("\u5237\u65b0");
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
			actions.add(refreshButton);
			actions.add(resetViewButton);

			final JPanel footer = new JPanel(new BorderLayout(0, 4));
			footer.add(statsLabel, BorderLayout.NORTH);
			final JPanel options = new JPanel(new BorderLayout(0, 2));
			options.add(showIsolatedCheck, BorderLayout.NORTH);
			options.add(focusSelectedCheck, BorderLayout.CENTER);
			footer.add(options, BorderLayout.CENTER);
			footer.add(actions, BorderLayout.SOUTH);
			add(footer, BorderLayout.SOUTH);
		}

		private void runSearch() {
			if (mode != graphMode) {
				subTabs.setSelectedIndex(tabIndexFromMode(mode));
			}
			activeSearchQuery = searchField.getText();
			focusCenterKey = null;
			focusSelectedCheck.setSelected(false);
			rebuildDisplayIndex(true);
		}

		private void clearSearch() {
			searchField.setText("");
			activeSearchQuery = "";
			focusCenterKey = null;
			focusSelectedCheck.setSelected(false);
			if (mode == graphMode) {
				rebuildDisplayIndex(true);
			}
		}

		private void applyFocusSelection() {
			if (!focusSelectedCheck.isSelected()) {
				focusCenterKey = null;
				rebuildDisplayIndex(true);
				return;
			}
			final RelationshipGraphService service = RelationshipGraphService.getService();
			if (service == null) {
				focusSelectedCheck.setSelected(false);
				return;
			}
			final RelationshipGraphNode selected = service.getCanvas().getSelectedNode();
			if (selected == null) {
				focusSelectedCheck.setSelected(false);
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
			else {
				unit = "\u5bfc\u56fe";
			}
			String text = index.getNodeCount() + " " + unit + " \u00b7 " + index.getEdgeCount() + " \u8fde\u63a5";
			if (index.getTotalNodeCount() > index.getNodeCount()) {
				text += " \u00b7 \u5171 " + index.getTotalNodeCount();
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
				resultHeader.setText("\u5173\u8054 " + shown + (total > shown ? "/" + total : ""));
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
			if (mode == RelationshipGraphScanner.MODE_TAGS) {
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
