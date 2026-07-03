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
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.plugin.workspace.actions.MindMapOpenLocationAction;

/**
 * Left sidebar: two mode tabs (map links / node links), each with search and related-item list.
 */
public class RelationshipGraphSideTabPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final Color MUTED = new Color(110, 110, 115);
	private static final Color BORDER = new Color(198, 200, 205);
	private static final int RESULT_LIST_CAP = 200;

	private final ModeTabPanel mapFilesTab;
	private final ModeTabPanel mapNodesTab;
	private final JTabbedPane subTabs;

	private final RelationshipGraphIndex[] cachedBaseIndex = new RelationshipGraphIndex[2];
	private final boolean[] dataLoadedForMode = new boolean[2];

	private volatile int graphMode = RelationshipGraphScanner.MODE_MAP_FILES;
	private Thread activeScanThread;
	private volatile boolean syncingListSelection;

	public RelationshipGraphSideTabPanel() {
		super(new BorderLayout(4, 4));
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		mapFilesTab = new ModeTabPanel(RelationshipGraphScanner.MODE_MAP_FILES);
		mapNodesTab = new ModeTabPanel(RelationshipGraphScanner.MODE_MAP_NODES);

		subTabs = new JTabbedPane(JTabbedPane.TOP);
		subTabs.setFont(subTabs.getFont().deriveFont(Font.PLAIN, 12f));
		subTabs.addTab("\u5bfc\u56fe\u5173\u8054", mapFilesTab);
		subTabs.addTab("\u8282\u70b9\u5173\u8054", mapNodesTab);
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

	private ModeTabPanel activeTab() {
		return graphMode == RelationshipGraphScanner.MODE_MAP_NODES ? mapNodesTab : mapFilesTab;
	}

	private void onSubTabChanged() {
		final int newMode = subTabs.getSelectedIndex() == 1 ? RelationshipGraphScanner.MODE_MAP_NODES
		        : RelationshipGraphScanner.MODE_MAP_FILES;
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
		subTabs.setSelectedIndex(graphMode == RelationshipGraphScanner.MODE_MAP_NODES ? 1 : 0);
		final boolean needsScan = !dataLoadedForMode[graphMode] || cachedBaseIndex[graphMode] == null;
		if (needsScan) {
			refreshGraphAsync(true);
			return;
		}
		service.showInViewport();
		final ModeTabPanel tab = activeTab();
		if (tab.displayIndex != null) {
			applyDisplayIndex(true);
		}
		else {
			rebuildDisplayIndex(false);
		}
		service.getCanvas().requestFocusInWindow();
	}

	void onTabDeactivated() {
		cancelActiveScan();
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
		final ModeTabPanel scanTab = scanMode == RelationshipGraphScanner.MODE_MAP_NODES ? mapNodesTab : mapFilesTab;
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
		if (deferViewportUntilDone || !svc.isGraphInViewport()) {
			svc.getCanvas().setLoading(true, "\u663e\u793a\u5173\u7cfb\u56fe\u2026");
			svc.showInViewport();
		}
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

	private void rebuildDisplayIndex(final boolean preserveView) {
		final RelationshipGraphIndex base = cachedBaseIndex[graphMode];
		if (base == null) {
			return;
		}
		final ModeTabPanel tab = activeTab();
		// Async only for heavy first layout; preserveView (re-open tab / filter) stays on EDT.
		if (!preserveView && base.getNodeCount() > 500) {
			rebuildDisplayIndexAsync(preserveView);
			return;
		}
		tab.displayIndex = RelationshipGraphIndex.buildDisplayIndex(base, tab.showIsolatedCheck.isSelected(),
		        tab.activeSearchQuery, tab.focusCenterKey);
		applyDisplayIndex(preserveView);
	}

	private void rebuildDisplayIndexAsync(final boolean preserveView) {
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
		new Thread(new Runnable() {
			public void run() {
				Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
				try {
					final RelationshipGraphIndex built = RelationshipGraphIndex.buildDisplayIndex(base, showIsolated,
					        searchQuery, focusKey);
					if (built != null && built.getNodeCount() > 0 && !preserveView) {
						RelationshipGraphLayout.initializePositions(built, 1200, 800);
					}
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							if (mode != graphMode) {
								return;
							}
							tab.displayIndex = built;
							if (svc != null) {
								svc.getCanvas().setLoading(false, null);
								svc.loadPreparedGraph(tab.displayIndex, preserveView);
								svc.getCanvas().setSearchQuery(tab.activeSearchQuery);
							}
							tab.updateStats(tab.displayIndex);
							tab.updateResultList();
						}
					});
				}
				catch (final Exception e) {
					LogUtils.warn(e);
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							tab.statsLabel.setText("\u66f4\u65b0\u5931\u8d25");
							if (svc != null) {
								svc.getCanvas().setLoading(false, null);
							}
						}
					});
				}
			}
		}, "RelationshipGraphFilter").start();
	}

	private void applyDisplayIndex(final boolean preserveView) {
		final ModeTabPanel tab = activeTab();
		if (tab.displayIndex == null) {
			return;
		}
		final RelationshipGraphService service = RelationshipGraphService.getService();
		if (service != null) {
			service.loadGraph(tab.displayIndex, preserveView);
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
						subTabs.setSelectedIndex(mode == RelationshipGraphScanner.MODE_MAP_NODES ? 1 : 0);
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
						service.getCanvas().refreshLayout();
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
				subTabs.setSelectedIndex(mode == RelationshipGraphScanner.MODE_MAP_NODES ? 1 : 0);
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
				return;
			}
			final String unit = index.getGraphMode() == RelationshipGraphScanner.MODE_MAP_NODES ? "\u8282\u70b9" : "\u5bfc\u56fe";
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
			return new ArrayList(allNodes);
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
				if (node.isMapNode()) {
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
