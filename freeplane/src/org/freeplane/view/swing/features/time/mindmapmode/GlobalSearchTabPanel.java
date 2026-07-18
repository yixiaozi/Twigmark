package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import org.freeplane.core.util.HtmlUtils;
import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.core.util.WorkspaceSideTabScanCache;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

public class GlobalSearchTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final int MAX_RESULTS = Integer.MAX_VALUE;
	private static final int PREVIEW_LENGTH = 100;
	
	private final JTextField searchField = new JTextField();
	private final DefaultListModel<SearchResult> listModel = new DefaultListModel<SearchResult>();
	private final JList<SearchResult> resultList = new JList<SearchResult>(listModel);
	private final JLabel statusLabel = new JLabel("加载中...");
	private final ExecutorService executor = Executors.newFixedThreadPool(4);
	private volatile boolean isSearching = false;
	private volatile String lastQuery = "";
	
	private final Map<String, Long> fileLastModifiedCache = new HashMap<String, Long>();
	private final Map<String, List<NodeInfo>> fileNodesCache = new HashMap<String, List<NodeInfo>>();
	
	private volatile int scanningFileCount = 0;
	private volatile int searchScannedFileCount = 0;
	private volatile int matchedNodeCount = 0;
	private volatile int searchTotalFileCount = 0;
	
	private static class NodeInfo {
		String text;
		String note;
		String id;
		int depth;
		long lastModified;
		
		NodeInfo(String text, String note, String id, int depth, long lastModified) {
			this.text = text;
			this.note = note;
			this.id = id;
			this.depth = depth;
			this.lastModified = lastModified;
		}
	}
	
	public static class SearchResult {
		final File file;
		final String nodeText;
		final String preview;
		final String nodeId;
		final int depth;
		final long fileModifiedTime;
		
		SearchResult(File file, String nodeText, String preview, String nodeId, int depth, long fileModifiedTime) {
			this.file = file;
			this.nodeText = nodeText;
			this.preview = preview;
			this.nodeId = nodeId;
			this.depth = depth;
			this.fileModifiedTime = fileModifiedTime;
		}
	}
	
	public GlobalSearchTabPanel() {
		super(new BorderLayout(4, 4));
		
		
		DocearUiTheme.styleCanvas(this);
		DocearUiTheme.styleSearchField(searchField);
		
		searchField.setToolTipText("输入关键词后点击搜索按钮");
		
		searchField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					searchField.setText("");
					listModel.clear();
					searchField.requestFocus();
				}
			}
		});
		
		JButton searchButton = new JButton("搜索");
		searchButton.setPreferredSize(new Dimension(60, 30));
		searchButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent e) {
				performSearch();
			}
		});
		
		statusLabel.setFont(DocearUiTheme.font(12f));
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
				if (value instanceof SearchResult) {
					SearchResult result = (SearchResult) value;
					String fileName = result.file.getName();
					if (fileName.toLowerCase().endsWith(".mm")) {
						fileName = fileName.substring(0, fileName.length() - 3);
					}
					String text = highlightKeywords(result.nodeText, lastQuery);
					
					if (text.length() > 150) {
						text = text.substring(0, 150) + "...";
					}
					
					String modifiedTime;
					java.util.Calendar cal = java.util.Calendar.getInstance();
					cal.setTime(new Date(result.fileModifiedTime));
					if (cal.get(java.util.Calendar.YEAR) == currentYear) {
						modifiedTime = thisYearFormat.format(new Date(result.fileModifiedTime));
					} else {
						modifiedTime = otherYearFormat.format(new Date(result.fileModifiedTime));
					}
					
					String html = "<html><table width='100%' style='table-layout:fixed'><tr>" +
						"<td align='left' style='white-space:nowrap;overflow:hidden;text-overflow:ellipsis'>" +
						"<span style='color:#0F766E;font-weight:bold'>[" + escapeHtml(fileName) + "]</span> " + text +
						"</td>" +
						"<td align='right' style='white-space:nowrap;font-size:10px;color:#94A3B8;padding-left:8px'>" + modifiedTime + "</td>" +
						"</tr></table></html>";
					
					setText(html);
					setBorder(BorderFactory.createCompoundBorder(lineBorder, padding));
					
					if (isSelected) {
						setBackground(DocearUiTheme.ACCENT_WASH);
						setForeground(DocearUiTheme.TEXT);
					} else {
						setBackground(DocearUiTheme.SURFACE);
						setForeground(Color.BLACK);
					}
				}
				return this;
			}
		});
		resultList.setSelectionBackground(DocearUiTheme.ACCENT_WASH);
		resultList.setSelectionForeground(DocearUiTheme.TEXT);
		resultList.setBackground(DocearUiTheme.SURFACE);
		resultList.setFont(DocearUiTheme.font(13f));
		resultList.setBorder(null);
		resultList.setFont(DocearUiTheme.font(12f));
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
				if (e.getClickCount() >= 1) {
					openSelectedResult();
				}
			}
		});
		
		JPanel searchPanel = new JPanel(new BorderLayout(4, 4));
		searchPanel.add(searchField, BorderLayout.CENTER);
		searchPanel.add(searchButton, BorderLayout.EAST);
		searchPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		
		JScrollPane scrollPane = new JScrollPane(resultList);
		scrollPane.setBorder(null);
		
		add(searchPanel, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
		add(statusLabel, BorderLayout.SOUTH);
		
		loadCacheIfNeeded();
	}
	
	private String highlightKeywords(String text, String query) {
		if (text == null) {
			return "";
		}
		if (query == null || query.isEmpty()) {
			return escapeHtml(text);
		}
		
		String result = escapeHtml(text);
		
		for (String keyword : query.split("\\s+")) {
			if (!keyword.isEmpty()) {
				String escapedKeyword = escapeHtml(keyword);
				try {
					result = result.replaceAll("(?i)" + Pattern.quote(escapedKeyword),
						"<font color='red'><b>$0</b></font>");
				} catch (Exception e) {
					LogUtils.warn("Regex error in highlightKeywords: " + e.getMessage());
				}
			}
		}
		return result;
	}
	
	private String escapeHtml(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			.replace("\"", "&quot;").replace("'", "&#39;");
	}
	
	private void performSearch() {
		lastQuery = searchField.getText().trim();
		
		if (lastQuery.isEmpty()) {
			listModel.clear();
			statusLabel.setText(getStatusText());
			return;
		}
		
		if (isSearching) {
			return;
		}
		
		isSearching = true;
		searchScannedFileCount = 0;
		matchedNodeCount = 0;
		synchronized (this) {
			searchTotalFileCount = 0;
		}
		updateStatus("搜索中...");
		
		executor.submit(new Runnable() {
			public void run() {
				try {
					List<String> keywords = new ArrayList<String>();
					for (String word : lastQuery.split("\\s+")) {
						if (!word.isEmpty()) {
							keywords.add(word.toLowerCase());
						}
					}
					
					if (keywords.isEmpty()) {
						return;
					}
					
					List<SearchResult> results = new ArrayList<SearchResult>();
					Set<String> checkedFiles = new HashSet<String>();
					Set<String> seenResults = new HashSet<String>();
					
					synchronized (this) {
						searchTotalFileCount = 0;
					}
					final File[] scanRoots = MindMapDataRootResolver.getScanRoots();
					for (int r = 0; r < scanRoots.length; r++) {
						if (scanRoots[r] != null && scanRoots[r].exists()) {
							countTotalFiles(scanRoots[r]);
							scanDirectory(scanRoots[r], keywords, results, checkedFiles, seenResults);
						}
					}
					
					Collections.sort(results, new Comparator<SearchResult>() {
						public int compare(SearchResult a, SearchResult b) {
							return Long.compare(b.fileModifiedTime, a.fileModifiedTime);
						}
					});
					
					final List<SearchResult> finalResults = new ArrayList<SearchResult>(results);
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							listModel.clear();
							for (SearchResult result : finalResults) {
								listModel.addElement(result);
							}
							resultList.updateUI();
							updateStatus("搜索完成");
						}
					});
				} finally {
					isSearching = false;
				}
			}
		});
	}
	
	private void countTotalFiles(File dir) {
		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (file.isDirectory() && !file.getName().startsWith(".")) {
				countTotalFiles(file);
			} else if (file.getName().toLowerCase().endsWith(".mm")) {
				synchronized (this) {
					searchTotalFileCount++;
				}
			}
		}
	}
	
	private void updateStatus(final String status) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				String totalInfo = "";
				int cacheFiles = fileNodesCache.size();
				int cacheNodes = 0;
				for (List<NodeInfo> nodes : fileNodesCache.values()) {
					cacheNodes += nodes.size();
				}
				publishNodeMetric(cacheNodes);
				if (cacheFiles > 0) {
					totalInfo = String.format("（共 %d 个导图，%d 个节点）", cacheFiles, cacheNodes);
				}
				
				String searchInfo = "";
				if (searchTotalFileCount > 0) {
					searchInfo = String.format(" (%d/%d)", searchScannedFileCount, searchTotalFileCount);
				}
				if (matchedNodeCount > 0) {
					int displayedCount = Math.min(matchedNodeCount, MAX_RESULTS);
					if (matchedNodeCount <= MAX_RESULTS) {
						searchInfo += String.format(", 匹配 %d 个节点", matchedNodeCount);
					} else {
						searchInfo += String.format(", 匹配 %d 个节点，显示前 %d 条", matchedNodeCount, displayedCount);
					}
				}
				
				statusLabel.setText(status + searchInfo + totalInfo);
			}
		});
	}
	
	private String getStatusText() {
		int fileCount = fileNodesCache.size();
		int nodeCount = 0;
		for (List<NodeInfo> nodes : fileNodesCache.values()) {
			nodeCount += nodes.size();
		}
		publishNodeMetric(nodeCount);
		return "就绪（共 " + fileCount + " 个导图，" + nodeCount + " 个节点）";
	}

	private void publishNodeMetric(final int nodeCount) {
		SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_NODES, nodeCount);
	}
	
	private void scanDirectory(File dir, List<String> keywords, List<SearchResult> results, 
			Set<String> checkedFiles, Set<String> seenResults) {
		if (results.size() >= MAX_RESULTS) {
			return;
		}
		
		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		
		for (File file : files) {
			if (results.size() >= MAX_RESULTS) {
				return;
			}
			
			if (file.isDirectory()) {
				if (!file.getName().startsWith(".")) {
					scanDirectory(file, keywords, results, checkedFiles, seenResults);
				}
			} else if (file.getName().toLowerCase().endsWith(".mm")) {
				String filePath = file.getAbsolutePath();
				if (checkedFiles.contains(filePath)) {
					continue;
				}
				checkedFiles.add(filePath);
				
				List<NodeInfo> nodes = getNodesFromCache(file);
				if (nodes == null) {
					nodes = parseMindMapFile(file);
					if (nodes != null) {
						fileNodesCache.put(filePath, nodes);
						fileLastModifiedCache.put(filePath, file.lastModified());
					}
				}
				
				if (nodes != null) {
					for (NodeInfo node : nodes) {
						if (matchesKeywords(node, keywords)) {
							String preview = generatePreview(node, keywords);
							String nodeId = node.id != null ? node.id : String.valueOf(node.text.hashCode());
							String resultKey = filePath + "|" + nodeId + "|" + node.text;
							if (!seenResults.contains(resultKey)) {
								seenResults.add(resultKey);
								results.add(new SearchResult(file, node.text, preview, node.id, node.depth, node.lastModified));
								matchedNodeCount++;
								if (matchedNodeCount % 10 == 0) {
									updateStatus("搜索中...");
								}
								if (results.size() >= MAX_RESULTS) {
									return;
								}
							}
						}
					}
				}
				searchScannedFileCount++;
				if (searchScannedFileCount % 5 == 0) {
					updateStatus("搜索中...");
				}
			}
		}
	}
	
	private List<NodeInfo> getNodesFromCache(File file) {
		String filePath = file.getAbsolutePath();
		Long cachedTime = fileLastModifiedCache.get(filePath);
		if (cachedTime != null && cachedTime == file.lastModified()) {
			return fileNodesCache.get(filePath);
		}
		fileNodesCache.remove(filePath);
		fileLastModifiedCache.remove(filePath);
		return null;
	}
	
	private List<NodeInfo> parseMindMapFile(File file) {
		List<NodeInfo> nodes = new ArrayList<NodeInfo>();
		
		try {
			BufferedReader reader = new BufferedReader(new FileReader(file));
			StringBuilder content = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line).append("\n");
			}
			reader.close();
			
			String xmlContent = content.toString();
			
			Pattern nodePattern = Pattern.compile("<node[^>]*>", Pattern.DOTALL);
			Matcher nodeMatcher = nodePattern.matcher(xmlContent);
			
			Stack<Integer> depthStack = new Stack<Integer>();
			depthStack.push(0);
			
			while (nodeMatcher.find()) {
				String nodeTag = nodeMatcher.group();
				String text = extractTextFromTag(nodeTag);
				String id = extractIdFromTag(nodeTag);
				String note = extractNote(xmlContent, nodeMatcher.end());
				long modifiedTime = extractModifiedTime(nodeTag, file.lastModified());
				
				String beforeText = xmlContent.substring(0, nodeMatcher.start());
				int startTagCount = beforeText.split("<node", -1).length - 1;
				int endTagCount = beforeText.split("</node>", -1).length - 1;
				int currentDepth = startTagCount - endTagCount;
				
				if (!text.isEmpty()) {
					nodes.add(new NodeInfo(text, note, id, currentDepth, modifiedTime));
				}
			}
		} catch (Exception e) {
			LogUtils.warn("Error parsing " + file.getName() + ": " + e.getMessage());
			return null;
		}
		
		return nodes;
	}
	
	private String extractIdFromTag(String nodeTag) {
		Pattern idPattern = Pattern.compile("ID=\"([^\"]*)\"");
		Matcher matcher = idPattern.matcher(nodeTag);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return "";
	}
	
	private long extractModifiedTime(String nodeTag, long defaultTime) {
		Pattern timePattern = Pattern.compile("CREATED=\"([^\"]*)\"");
		Matcher matcher = timePattern.matcher(nodeTag);
		if (matcher.find()) {
			String timeStr = matcher.group(1);
			try {
				return Long.parseLong(timeStr);
			} catch (NumberFormatException e) {
			}
		}
		timePattern = Pattern.compile("MODIFIED=\"([^\"]*)\"");
		matcher = timePattern.matcher(nodeTag);
		if (matcher.find()) {
			String timeStr = matcher.group(1);
			try {
				return Long.parseLong(timeStr);
			} catch (NumberFormatException e) {
			}
		}
		return defaultTime;
	}
	
	private String extractTextFromTag(String nodeTag) {
		Pattern textPattern = Pattern.compile("TEXT=\"([^\"]*)\"");
		Matcher matcher = textPattern.matcher(nodeTag);
		if (matcher.find()) {
			String text = matcher.group(1);
			text = decodeHtmlEntities(text);
			return text.replaceAll("\\s+", " ").trim();
		}
		return "";
	}
	
	private String decodeHtmlEntities(String text) {
		if (text == null) {
			return "";
		}
		Pattern entityPattern = Pattern.compile("&#x([0-9a-fA-F]+);");
		Matcher matcher = entityPattern.matcher(text);
		StringBuffer result = new StringBuffer();
		while (matcher.find()) {
			try {
				int code = Integer.parseInt(matcher.group(1), 16);
				matcher.appendReplacement(result, String.valueOf((char) code));
			} catch (Exception e) {
				matcher.appendReplacement(result, matcher.group());
			}
		}
		matcher.appendTail(result);
		return result.toString();
	}
	
	private String extractNote(String xmlContent, int startPos) {
		int noteStart = xmlContent.indexOf("<richcontent", startPos);
		if (noteStart == -1) {
			return "";
		}
		int noteEnd = xmlContent.indexOf("</richcontent>", noteStart);
		if (noteEnd == -1) {
			return "";
		}
		
		String noteContent = xmlContent.substring(noteStart, noteEnd + 15);
		if (!noteContent.contains("TYPE=\"NOTE\"")) {
			return "";
		}
		
		Pattern contentPattern = Pattern.compile("<richcontent[^>]*>(.*?)</richcontent>", Pattern.DOTALL);
		Matcher matcher = contentPattern.matcher(noteContent);
		if (matcher.find()) {
			String content = matcher.group(1);
			content = HtmlUtils.removeHtmlTagsFromString(content);
			content = decodeHtmlEntities(content);
			return content.replaceAll("\\s+", " ").trim();
		}
		return "";
	}
	
	private boolean matchesKeywords(NodeInfo node, List<String> keywords) {
		String text = node.text.trim();
		
		if (isShortNumber(text)) {
			return false;
		}
		
		String lowerText = text.toLowerCase();
		for (String keyword : keywords) {
			if (!lowerText.contains(keyword)) {
				return false;
			}
		}
		return true;
	}
	
	private boolean isShortNumber(String text) {
		if (text == null || text.isEmpty()) {
			return false;
		}
		if (text.matches("^\\d{1,4}$")) {
			return true;
		}
		return false;
	}
	
	private String generatePreview(NodeInfo node, List<String> keywords) {
		String text = node.text + " " + node.note;
		String lowerText = text.toLowerCase();
		
		int minPos = text.length();
		for (String keyword : keywords) {
			int pos = lowerText.indexOf(keyword.toLowerCase());
			if (pos >= 0 && pos < minPos) {
				minPos = pos;
			}
		}
		
		int start = Math.max(0, minPos - 30);
		int end = Math.min(text.length(), minPos + PREVIEW_LENGTH);
		
		String preview = text.substring(start, end).replaceAll("\\s+", " ").trim();
		if (start > 0) {
			preview = "..." + preview;
		}
		if (end < text.length()) {
			preview = preview + "...";
		}
		
		for (String keyword : keywords) {
			preview = preview.replaceAll("(?i)" + Pattern.quote(keyword), 
				"<span style='color:red;font-weight:bold'>$0</span>");
		}
		
		return preview;
	}
	
	private String createPadding(int count) {
		if (count <= 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			sb.append("&nbsp;");
		}
		return sb.toString();
	}
	
	private void openSelectedResult() {
		SearchResult result = resultList.getSelectedValue();
		if (result == null) {
			return;
		}
		
		try {
			final URL mapUrl = result.file.toURI().toURL();
			final String targetNodeId = result.nodeId;
			
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					try {
						Controller.getCurrentModeController().getMapController().newMap(mapUrl);
						
						if (targetNodeId != null && !targetNodeId.isEmpty()) {
							SwingUtilities.invokeLater(new Runnable() {
								public void run() {
									try {
										MapModel map = Controller.getCurrentController().getMap();
										if (map != null) {
											NodeModel node = map.getNodeForID(targetNodeId);
											if (node != null) {
												Controller.getCurrentModeController().getMapController().select(node);
											}
										}
									} catch (Exception e) {
										LogUtils.warn("Failed to select node: " + e.getMessage());
									}
								}
							});
						}
					} catch (Exception e) {
						JOptionPane.showMessageDialog(GlobalSearchTabPanel.this, "无法打开文件: " + e.getMessage());
					}
				}
			});
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "无法打开文件: " + e.getMessage());
		}
	}
	
	private void loadCacheIfNeeded() {
		executor.submit(new Runnable() {
			public void run() {
				List<File> mmFiles = WorkspaceSideTabScanCache.getMindMapFilesSnapshot();
				if (mmFiles == null) {
					mmFiles = new ArrayList<File>();
					final File[] scanRoots = MindMapDataRootResolver.getScanRoots();
					for (int r = 0; r < scanRoots.length; r++) {
						if (scanRoots[r] != null && scanRoots[r].exists()) {
							collectMindMapFiles(scanRoots[r], mmFiles);
						}
					}
				}
				if (!mmFiles.isEmpty()) {
					
					final int totalFiles = mmFiles.size();
					final java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger(0);
					
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							statusLabel.setText("加载中... (0/" + totalFiles + ")");
						}
					});
					
					for (File file : mmFiles) {
						final File f = file;
						executor.submit(new Runnable() {
							public void run() {
								String filePath = f.getAbsolutePath();
								if (!fileLastModifiedCache.containsKey(filePath) || 
									fileLastModifiedCache.get(filePath) != f.lastModified()) {
									List<NodeInfo> nodes = parseMindMapFile(f);
									if (nodes != null) {
										synchronized (fileNodesCache) {
											fileNodesCache.put(filePath, nodes);
											fileLastModifiedCache.put(filePath, f.lastModified());
										}
									}
								}
								
								int completed = completedCount.incrementAndGet();
								if (completed % 5 == 0 || completed == totalFiles) {
									final int currentCompleted = completed;
									SwingUtilities.invokeLater(new Runnable() {
										public void run() {
											int cacheNodes = 0;
											synchronized (fileNodesCache) {
												for (List<NodeInfo> nodes : fileNodesCache.values()) {
													cacheNodes += nodes.size();
												}
											}
											publishNodeMetric(cacheNodes);
											if (currentCompleted == totalFiles) {
												updateStatus("就绪");
											} else {
												statusLabel.setText("加载中... (" + currentCompleted + "/" + totalFiles + ")");
											}
										}
									});
								}
							}
						});
					}
					
					if (totalFiles == 0) {
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								updateStatus("就绪");
							}
						});
					}
				} else {
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							updateStatus("就绪");
						}
					});
				}
			}
		});
	}
	
	private void collectMindMapFiles(File dir, List<File> files) {
		File[] dirFiles = dir.listFiles();
		if (dirFiles == null) {
			return;
		}
		for (File file : dirFiles) {
			if (file.isDirectory() && !file.getName().startsWith(".")) {
				collectMindMapFiles(file, files);
			} else if (file.getName().toLowerCase().endsWith(".mm")) {
				files.add(file);
			}
		}
	}
}