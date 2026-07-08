package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.core.util.WorkspaceSearchFileMenuBridge;
import org.freeplane.core.util.WorkspaceSideTabScanCache;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.mode.Controller;

public class MindMapFileSearchPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final int MAX_VISIBLE_RESULTS = 100;
	
	private final JTextField searchField = new JTextField();
	private final DefaultListModel<FileResult> listModel = new DefaultListModel<FileResult>();
	private final JList<FileResult> resultList = new JList<FileResult>(listModel);
	private JLabel statusLabel = new JLabel("加载中...");
	
	private final List<File> allMindMapFiles = new CopyOnWriteArrayList<File>();
	private volatile int searchGeneration = 0;
	
	public static class FileResult {
		final File file;
		final long lastModified;
		
		FileResult(File file, long lastModified) {
			this.file = file;
			this.lastModified = lastModified;
		}
	}
	
	public MindMapFileSearchPanel() {
		super(new BorderLayout(4, 4));
		
		searchField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Color.GRAY),
			BorderFactory.createEmptyBorder(4, 8, 4, 8)
		));
		searchField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
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
		
		statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
		statusLabel.setForeground(Color.GRAY);
		statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		
		resultList.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;
			private final Border lineBorder = BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 230, 230));
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
					if (fileName.toLowerCase().endsWith(".mm")) {
						fileName = fileName.substring(0, fileName.length() - 3);
					}
					
					String modifiedTime;
					java.util.Calendar cal = java.util.Calendar.getInstance();
					cal.setTime(new Date(result.lastModified));
					if (cal.get(java.util.Calendar.YEAR) == currentYear) {
						modifiedTime = thisYearFormat.format(new Date(result.lastModified));
					} else {
						modifiedTime = otherYearFormat.format(new Date(result.lastModified));
					}
					
					String html = "<html><table width='100%' style='table-layout:fixed'><tr>" +
						"<td align='left' style='white-space:nowrap;overflow:hidden;text-overflow:ellipsis'>" +
						"<span style='color:#3366cc;font-weight:bold'>" + escapeHtml(fileName) + "</span>" +
						"</td>" +
						"<td align='right' style='white-space:nowrap;font-size:10px;color:#999999;padding-left:8px'>" + modifiedTime + "</td>" +
						"</tr></table></html>";
					
					setText(html);
					setBorder(BorderFactory.createCompoundBorder(lineBorder, padding));
					
					if (isSelected) {
						setBackground(new Color(100, 149, 237));
						setForeground(Color.WHITE);
					} else {
						setBackground(Color.WHITE);
						setForeground(Color.BLACK);
					}
				}
				return this;
			}
		});
		resultList.setSelectionBackground(new Color(100, 149, 237));
		resultList.setSelectionForeground(Color.WHITE);
		resultList.setBackground(Color.WHITE);
		resultList.setBorder(null);
		resultList.setFont(new Font("微软雅黑", Font.PLAIN, 12));
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
		
		JPanel searchPanel = new JPanel(new BorderLayout(4, 4));
		searchPanel.add(searchField, BorderLayout.CENTER);
		searchPanel.add(refreshButton, BorderLayout.EAST);
		searchPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		
		JScrollPane scrollPane = new JScrollPane(resultList);
		scrollPane.setBorder(null);
		
		add(searchPanel, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
		add(statusLabel, BorderLayout.SOUTH);
		
		loadMindMapFiles();
	}
	
	private String escapeHtml(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			.replace("\"", "&quot;").replace("'", "&#39;");
	}
	
	private void loadMindMapFiles() {
		refresh();
	}
	
	public void refresh() {
		statusLabel.setText("刷新中...");
		new Thread(new Runnable() {
			public void run() {
				List<File> newFiles = WorkspaceSideTabScanCache.getMindMapFilesSnapshot();
				if (newFiles == null) {
					newFiles = new ArrayList<File>();
					MindMapDataRootResolver.collectMindmapFiles(newFiles);
					Collections.sort(newFiles, new Comparator<File>() {
						public int compare(File a, File b) {
							return Long.compare(b.lastModified(), a.lastModified());
						}
					});
				}
				final List<File> finalFiles = newFiles;
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						allMindMapFiles.clear();
						allMindMapFiles.addAll(finalFiles);
						performSearch();
						SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_MINDMAP, allMindMapFiles.size());
						statusLabel.setText("就绪 (共 " + allMindMapFiles.size() + " 个文件)");
					}
				});
			}
		}).start();
	}
	
	private void collectMindMapFiles(File dir) {
		collectMindMapFiles(dir, allMindMapFiles);
	}
	
	private void collectMindMapFiles(File dir, List<File> resultList) {
		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (file.isDirectory() && !file.getName().startsWith(".")) {
				collectMindMapFiles(file, resultList);
			} else if (file.getName().toLowerCase().endsWith(".mm")) {
				resultList.add(file);
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
					if (allMindMapFiles.isEmpty()) {
						statusLabel.setText("就绪");
					}
					else {
						statusLabel.setText(String.format("最近 %d 个文件 (共 %d 个)", results.size(),
						        allMindMapFiles.size()));
					}
				}
			});
			return;
		}
		
		statusLabel.setText("搜索中...");
		new Thread(new Runnable() {
			public void run() {
				final List<FileResult> results = new ArrayList<FileResult>();
				for (File file : allMindMapFiles) {
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
		}, "MindMapFileSearch").start();
	}
	
	private List<FileResult> buildRecentResults() {
		final List<FileResult> results = new ArrayList<FileResult>();
		final int limit = Math.min(MAX_VISIBLE_RESULTS, allMindMapFiles.size());
		for (int i = 0; i < limit; i++) {
			final File file = allMindMapFiles.get(i);
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
			final URL mapUrl = result.file.toURI().toURL();
			
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					try {
						Controller.getCurrentModeController().getMapController().newMap(mapUrl);
					} catch (Exception e) {
						LogUtils.warn("Failed to open file: " + e.getMessage());
					}
				}
			});
		} catch (Exception e) {
			LogUtils.warn("Failed to open file: " + e.getMessage());
		}
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

		final JMenuItem openItem = new JMenuItem("打开导图");
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
			} else {
				if (System.getProperty("os.name").toLowerCase().contains("windows")) {
					String cmd = "explorer.exe \"" + parentDir.getAbsolutePath() + "\"";
					Runtime.getRuntime().exec(cmd);
				} else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
					Runtime.getRuntime().exec(new String[]{"open", parentDir.getAbsolutePath()});
				} else {
					Runtime.getRuntime().exec(new String[]{"/usr/bin/xdg-open", parentDir.getAbsolutePath()});
				}
			}
		} catch (IOException e) {
			LogUtils.warn("Failed to open folder: " + e.getMessage());
			JOptionPane.showMessageDialog(this, "无法打开文件所在目录: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
		}
	}
}
