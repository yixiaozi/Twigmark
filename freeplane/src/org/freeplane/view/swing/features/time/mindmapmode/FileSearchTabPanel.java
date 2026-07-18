package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

import org.freeplane.core.ui.theme.DocearUiTheme;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.features.mode.Controller;

public class FileSearchTabPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final int MAX_RESULTS = 100;
	
	private final JTextField searchField = new JTextField();
	private final JLabel statusLabel = new JLabel();
	private final DefaultListModel<SearchResult> listModel = new DefaultListModel<SearchResult>();
	private final JList<SearchResult> resultList = new JList<SearchResult>(listModel);
	
	private int totalFileCount = 0;
	
	public static class SearchResult {
		final File file;
		
		SearchResult(File file) {
			this.file = file;
		}
	}
	
	public FileSearchTabPanel() {
		super(new BorderLayout(4, 4));
		
		
		DocearUiTheme.styleCanvas(this);
		JPanel searchPanel = new JPanel(new BorderLayout(4, 0));
		searchPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 4));
		
		DocearUiTheme.styleSearchField(searchField);
		
		searchField.setToolTipText("输入文件名关键字（空格分隔多个）");
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) { performSearch(); }
			public void insertUpdate(DocumentEvent e) { performSearch(); }
			public void removeUpdate(DocumentEvent e) { performSearch(); }
		});
		searchField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					searchField.setText("");
					searchField.requestFocus();
				}
			}
		});
		
		searchPanel.add(searchField, BorderLayout.CENTER);
		
		statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
		statusLabel.setForeground(DocearUiTheme.TEXT_MUTED);
		statusLabel.setFont(DocearUiTheme.font(12f));
		statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		statusLabel.setText("就绪");
		
		JScrollPane scrollPane = new JScrollPane(resultList);
		scrollPane.setBorder(null);
		
		add(searchPanel, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
		add(statusLabel, BorderLayout.SOUTH);
		
		resultList.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;
			private final Border padding = BorderFactory.createEmptyBorder(4, 8, 4, 8);
			
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof SearchResult) {
					SearchResult result = (SearchResult) value;
					String fileName = result.file.getName();
					String query = searchField.getText().trim();
					if (!query.isEmpty()) {
						for (String keyword : query.split("\\s+")) {
							if (!keyword.isEmpty()) {
								fileName = fileName.replaceAll("(?i)" + Pattern.quote(keyword), 
									"<span style='color:red; font-weight:bold;'>$0</span>");
							}
						}
					}
					setText("<html><b>" + fileName + "</b></html>");
					setBorder(padding);
				}
				return this;
			}
		});
		
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
	}
	
	private void performSearch() {
		final String query = searchField.getText().trim();
		
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				listModel.clear();
				
				if (query.isEmpty()) {
					statusLabel.setText("就绪");
					return;
				}
				
				statusLabel.setText("搜索中...");
			}
		});
		
		new Thread(new Runnable() {
			public void run() {
				List<SearchResult> results = new ArrayList<SearchResult>();
				List<String> keywords = new ArrayList<String>();
				for (String word : query.split("\\s+")) {
					if (!word.isEmpty()) {
						keywords.add(word.toLowerCase());
					}
				}
				
				if (!keywords.isEmpty()) {
					final File[] scanRoots = MindMapDataRootResolver.getScanRoots();
					for (int r = 0; r < scanRoots.length; r++) {
						if (scanRoots[r] != null && scanRoots[r].exists()) {
							scanDirectory(scanRoots[r], keywords, results);
						}
					}
				}
				
				final List<SearchResult> finalResults = results;
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						listModel.clear();
						for (SearchResult result : finalResults) {
							listModel.addElement(result);
						}
						statusLabel.setText(String.format("找到 %d 个文件", finalResults.size()));
					}
				});
			}
		}).start();
	}
	
	private void scanDirectory(File dir, List<String> keywords, List<SearchResult> results) {
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
			
			if (file.isDirectory() && !file.getName().startsWith(".")) {
				scanDirectory(file, keywords, results);
			} else if (file.getName().toLowerCase().endsWith(".mm")) {
				String fileName = file.getName().toLowerCase();
				boolean matches = true;
				for (String keyword : keywords) {
					if (!fileName.contains(keyword)) {
						matches = false;
						break;
					}
				}
				if (matches) {
					results.add(new SearchResult(file));
				}
			}
		}
	}
	
	private void openSelectedResult() {
		SearchResult result = resultList.getSelectedValue();
		if (result == null) {
			return;
		}
		
		try {
			Controller.getCurrentModeController().getMapController().newMap(result.file.toURI().toURL());
		} catch (Exception e) {
			JOptionPane.showMessageDialog(FileSearchTabPanel.this, "无法打开文件: " + e.getMessage());
		}
	}
}