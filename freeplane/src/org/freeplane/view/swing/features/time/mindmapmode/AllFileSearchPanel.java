package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.core.util.WorkspaceSearchFileMenuBridge;
import org.freeplane.core.util.WorkspaceSideTabScanCache;

public class AllFileSearchPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final int MAX_VISIBLE_RESULTS = 100;
	
	private final JTextField searchField = new JTextField();
	private final DefaultListModel<FileResult> listModel = new DefaultListModel<FileResult>();
	private final JList<FileResult> resultList = new JList<FileResult>(listModel);
	private JLabel statusLabel = new JLabel("加载中...");
	
	private final List<File> allFiles = new CopyOnWriteArrayList<File>();
	private volatile int searchGeneration = 0;
	
	public static class FileResult {
		final File file;
		final long lastModified;
		
		FileResult(File file, long lastModified) {
			this.file = file;
			this.lastModified = lastModified;
		}
	}
	
	public AllFileSearchPanel() {
		super(new BorderLayout(4, 4));
		DocearUiTheme.styleCanvas(this);
		
		DocearUiTheme.styleSearchField(searchField);
		searchField.setToolTipText("输入关键词搜索文件名，清空后显示最近修改的文件");
		
		searchField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					searchField.setText("");
					searchField.requestFocus();
				} else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					performSearch();
				}
			}
			
			@Override
			public void keyReleased(KeyEvent e) {
				performSearch();
			}
		});
		
		JButton refreshButton = new JButton("刷新");
		refreshButton.setPreferredSize(new Dimension(60, 30));
		refreshButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				refresh();
			}
		});
		
		statusLabel.setForeground(DocearUiTheme.TEXT_MUTED);
		statusLabel.setFont(DocearUiTheme.font(12f));
		statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		
		resultList.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;
			private final Border lineBorder = BorderFactory.createMatteBorder(0, 0, 1, 0, DocearUiTheme.HAIRLINE);
			private final Border padding = BorderFactory.createEmptyBorder(2, 4, 2, 4);
			private final SimpleDateFormat thisYearFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
			private final SimpleDateFormat otherYearFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
			private final int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
			
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof FileResult) {
					FileResult result = (FileResult) value;
					String fileName = result.file.getName();
					
					String modifiedTime;
					java.util.Calendar cal = java.util.Calendar.getInstance();
					cal.setTime(new Date(result.lastModified));
					if (cal.get(java.util.Calendar.YEAR) == currentYear) {
						modifiedTime = thisYearFormat.format(new Date(result.lastModified));
					} else {
						modifiedTime = otherYearFormat.format(new Date(result.lastModified));
					}
					
					String html = "<html><table width='100%' style='table-layout:fixed'><tr>" +
						"<td align='left' style='white-space:nowrap;overflow:hidden;text-overflow:ellipsis;'>" +
						"<span style='color:#0F766E;font-weight:bold;'>" + escapeHtml(fileName) + "</span>" +
						"</td>" +
						"<td align='right' style='white-space:nowrap;font-size:10px;color:#94A3B8;padding-left:8px;'>" + modifiedTime + "</td>" +
						"</tr></table></html>";
					
					setText(html);
					setBorder(BorderFactory.createCompoundBorder(lineBorder, padding));
					
					if (isSelected) {
						setBackground(DocearUiTheme.ACCENT_WASH);
						setForeground(DocearUiTheme.TEXT);
					} else {
						setBackground(DocearUiTheme.SURFACE);
						setForeground(DocearUiTheme.TEXT);
					}
				}
				return this;
			}
		});
		resultList.setSelectionBackground(DocearUiTheme.ACCENT_WASH);
		resultList.setSelectionForeground(DocearUiTheme.TEXT);
		resultList.setBackground(DocearUiTheme.SURFACE);
		resultList.setBorder(null);
		resultList.setFont(DocearUiTheme.font(13f));
		resultList.setFixedCellHeight(28);
		
		resultList.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					openSelectedResult();
				}
			}
		});
		
		resultList.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 1) {
					openSelectedResult();
				}
			}
			
			@Override
			public void mouseReleased(java.awt.event.MouseEvent e) {
				if (e.isPopupTrigger()) {
					showPopupMenu(e);
				}
			}
			
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				if (e.isPopupTrigger()) {
					showPopupMenu(e);
				}
			}
		});
		
		JPanel buttonPanel = new JPanel(new BorderLayout(4, 4));
		buttonPanel.add(refreshButton, BorderLayout.EAST);
		
		JPanel searchPanel = new JPanel(new BorderLayout(4, 4));
		searchPanel.add(searchField, BorderLayout.CENTER);
		searchPanel.add(buttonPanel, BorderLayout.EAST);
		searchPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		
		JScrollPane scrollPane = new JScrollPane(resultList);
		scrollPane.setBorder(null);
		
		add(searchPanel, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
		add(statusLabel, BorderLayout.SOUTH);
		
		loadFiles();
	}
	
	private String escapeHtml(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			.replace("\"", "&quot;").replace("'", "&#39;");
	}
	
	private void loadFiles() {
		refresh();
	}
	
	public void refresh() {
		statusLabel.setText("刷新中...");
		new Thread(new Runnable() {
			public void run() {
				List<File> newFiles = WorkspaceSideTabScanCache.getAllFilesSnapshot();
				if (newFiles == null) {
					newFiles = new ArrayList<File>();
					final Set<String> seenPaths = new HashSet<String>();
					final File[] scanRoots = MindMapDataRootResolver.getScanRoots();
					for (int r = 0; r < scanRoots.length; r++) {
						if (scanRoots[r] != null && scanRoots[r].exists()) {
							collectAllFiles(scanRoots[r], newFiles, seenPaths);
						}
					}
					Collections.sort(newFiles, new Comparator<File>() {
						public int compare(File a, File b) {
							return Long.compare(b.lastModified(), a.lastModified());
						}
					});
				}
				final List<File> finalFiles = newFiles;
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						allFiles.clear();
						allFiles.addAll(finalFiles);
						performSearch();
						SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_FILES, allFiles.size());
						statusLabel.setText("就绪 (共 " + allFiles.size() + " 个文件)");
					}
				});
			}
		}).start();
	}
	
	private void collectAllFiles(File dir, List<File> resultList, Set<String> seenPaths) {
		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (file.isDirectory() && !file.getName().startsWith(".")) {
				collectAllFiles(file, resultList, seenPaths);
			} else if (file.isFile()) {
				String key;
				try {
					key = file.getCanonicalPath();
				}
				catch (Exception e) {
					key = file.getAbsolutePath();
				}
				if (seenPaths.add(key)) {
					resultList.add(file);
				}
			}
		}
	}
	
	private void performSearch() {
		final String query = searchField.getText().trim().toLowerCase();
		final int generation = ++searchGeneration;
		
		if (query.isEmpty()) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					if (generation != searchGeneration) {
						return;
					}
					final List<FileResult> results = buildRecentResults();
					applySearchResults(results);
					if (allFiles.isEmpty()) {
						statusLabel.setText("就绪");
					}
					else {
						statusLabel.setText(String.format("最近 %d 个文件 (共 %d 个)", results.size(), allFiles.size()));
					}
				}
			});
			return;
		}
		
		statusLabel.setText("搜索中...");
		new Thread(new Runnable() {
			public void run() {
				final List<FileResult> results = new ArrayList<FileResult>();
				for (File file : allFiles) {
					String fileName = file.getName().toLowerCase();
					if (fileName.contains(query)) {
						results.add(new FileResult(file, file.lastModified()));
					}
				}
				
				Collections.sort(results, new Comparator<FileResult>() {
					public int compare(FileResult a, FileResult b) {
						return Long.compare(b.lastModified, a.lastModified);
					}
				});
				if (results.size() > MAX_VISIBLE_RESULTS) {
					results.subList(MAX_VISIBLE_RESULTS, results.size()).clear();
				}
				
				final List<FileResult> finalResults = results;
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						if (generation != searchGeneration) {
							return;
						}
						applySearchResults(finalResults);
						statusLabel.setText("找到 " + finalResults.size() + " 个文件");
					}
				});
			}
		}, "AllFileSearch").start();
	}
	
	private List<FileResult> buildRecentResults() {
		final List<FileResult> results = new ArrayList<FileResult>();
		final int limit = Math.min(MAX_VISIBLE_RESULTS, allFiles.size());
		for (int i = 0; i < limit; i++) {
			final File file = allFiles.get(i);
			results.add(new FileResult(file, file.lastModified()));
		}
		return results;
	}
	
	private void applySearchResults(final List<FileResult> results) {
		resultList.setListData(results.toArray(new FileResult[results.size()]));
	}
	
	private void openSelectedResult() {
		FileResult result = resultList.getSelectedValue();
		if (result == null) {
			return;
		}
		
		try {
			openFile(result.file);
		} catch (Exception e) {
			LogUtils.warn("无法打开文件: " + e.getMessage());
			JOptionPane.showMessageDialog(this, "无法打开文件: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
		}
	}

	/** Open .mm inside Docear; other files via the OS association. */
	private void openFile(final File file) throws Exception {
		if (file == null || !file.exists()) {
			return;
		}
		final String name = file.getName().toLowerCase(Locale.ENGLISH);
		if (name.endsWith(".mm")) {
			final org.freeplane.features.mode.Controller controller = org.freeplane.features.mode.Controller
			        .getCurrentController();
			final org.freeplane.features.ui.IMapViewManager mapViewManager = controller.getMapViewManager();
			final java.net.URL url = file.toURI().toURL();
			if (!mapViewManager.tryToChangeToMapView(url)) {
				controller.getModeController().getMapController().newMap(url);
			}
			return;
		}
		openFileWithSystemApp(file);
	}
	
	private void showPopupMenu(java.awt.event.MouseEvent e) {
		final int index = resultList.locationToIndex(e.getPoint());
		if (index < 0) {
			return;
		}
		resultList.setSelectedIndex(index);
		final FileResult result = resultList.getSelectedValue();
		if (result == null) {
			return;
		}

		final JPopupMenu popupMenu = new JPopupMenu();

		final JMenuItem openItem = new JMenuItem("打开文件");
		openItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent ev) {
				openSelectedResult();
			}
		});
		popupMenu.add(openItem);

		popupMenu.addSeparator();
		if (!WorkspaceSearchFileMenuBridge.appendFavoriteItems(popupMenu, result.file)) {
			final JMenuItem openFolderItem = new JMenuItem("打开所在文件夹");
			openFolderItem.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent ev) {
					openContainingFolder(result.file);
				}
			});
			popupMenu.add(openFolderItem);
		}

		popupMenu.show(resultList, e.getX(), e.getY());
	}
	
	private void openFileWithSystemApp(File file) {
		if (file == null || !file.exists()) {
			return;
		}
		
		try {
			if (java.awt.Desktop.isDesktopSupported()) {
				java.awt.Desktop.getDesktop().open(file);
			}
		} catch (Exception e) {
			LogUtils.warn("无法打开文件: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}
	
	private void openContainingFolder(File file) {
		if (file == null || !file.exists()) {
			return;
		}
		
		File parentDir = file.getParentFile();
		if (parentDir == null || !parentDir.exists()) {
			JOptionPane.showMessageDialog(this, "无法找到文件所在目录", "错误", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		try {
			if (java.awt.Desktop.isDesktopSupported()) {
				java.awt.Desktop.getDesktop().open(parentDir);
			}
		} catch (Exception e) {
			LogUtils.warn("无法打开目录: " + e.getMessage());
			JOptionPane.showMessageDialog(this, "无法打开目录: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
		}
	}
}
