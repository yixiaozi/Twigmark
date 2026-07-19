package org.freeplane.view.swing.features.time.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.MindMapDataRootResolver;
import org.freeplane.core.util.SideTabMetricKeys;
import org.freeplane.core.util.SideTabMetricRegistry;
import org.freeplane.core.util.WorkspaceSideTabScanCache;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class ActivityAnalysisPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat MONTH_FORMAT = new SimpleDateFormat("yyyy-MM");
    private static final SimpleDateFormat DAY_OF_WEEK_FORMAT = new SimpleDateFormat("EEEE");

    private final JButton refreshButton = new JButton("刷新");
    private final JLabel statusLabel = new JLabel("就绪");
    private final JTabbedPane analysisTabs = new JTabbedPane();

    private final Map<String, Long> dailyActivity = new TreeMap<String, Long>(Collections.reverseOrder()); // date -> count
    private final Map<String, Long> monthlyActivity = new TreeMap<String, Long>(Collections.reverseOrder()); // month -> count
    private final Map<String, Integer> fileNodeCount = new TreeMap<String, Integer>(); // file -> node count
    private final Map<String, Long> fileActivity = new TreeMap<String, Long>(Collections.reverseOrder()); // file -> last modified
    private final Map<Integer, Long> hourDistribution = new HashMap<Integer, Long>(); // hour -> count

    private volatile boolean isScanning = false;

    public ActivityAnalysisPanel() {
        super(new BorderLayout(4, 4));

        JPanel topPanel = new JPanel(new BorderLayout(4, 0));
        topPanel.add(statusLabel, BorderLayout.CENTER);
        topPanel.add(refreshButton, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        org.freeplane.core.ui.theme.DocearUiTheme.styleTabbedPane(analysisTabs);
        org.freeplane.core.ui.components.TabbedPaneStableOrder.install(analysisTabs);
        add(analysisTabs, BorderLayout.CENTER);

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshAnalysis();
            }
        });

        statusLabel.setText("\u70b9\u51fb\u5237\u65b0\u5f00\u59cb\u5206\u6790");
    }

    public void refreshAnalysis() {
        if (isScanning) {
            return;
        }

        dailyActivity.clear();
        monthlyActivity.clear();
        fileNodeCount.clear();
        fileActivity.clear();
        hourDistribution.clear();

        isScanning = true;
        statusLabel.setText("正在扫描...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<File> allFiles = collectAllMindmapFiles();
                    final int totalFiles = allFiles.size();
                    int processedFiles = 0;

                    for (File file : allFiles) {
                        if (!isScanning) {
                            break;
                        }

                        try {
                            parseFile(file);
                            processedFiles++;
                            final int current = processedFiles;
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    statusLabel.setText("正在扫描... (" + current + "/" + totalFiles + ")");
                                }
                            });
                        } catch (Exception ex) {
                            LogUtils.warn("Error parsing file: " + file.getAbsolutePath(), ex);
                        }
                    }

                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            updateTabs();
                            SideTabMetricRegistry.set(SideTabMetricKeys.LEFT_ACTIVITY, fileNodeCount.size());
                            statusLabel.setText("分析完成 - 共 " + fileNodeCount.size() + " 个导图");
                        }
                    });
                } finally {
                    isScanning = false;
                }
            }
        }).start();
    }

    private List<File> collectAllMindmapFiles() {
        final List<File> cached = WorkspaceSideTabScanCache.getMindMapFilesSnapshot();
        if (cached != null) {
            return cached;
        }
        final List<File> files = new ArrayList<File>();
        MindMapDataRootResolver.collectMindmapFiles(files);
        return files;
    }

    private void collectMindmapFiles(File directory, List<File> files) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.getName().startsWith(".")) {
                continue;
            }
            if (child.isDirectory()) {
                collectMindmapFiles(child, files);
            } else if (child.getName().toLowerCase().endsWith(".mm")) {
                files.add(child);
            }
        }
    }

    private void parseFile(final File file) {
        final int[] nodeCount = {0};
        final long[] lastModified = {file.lastModified()};

        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(file, new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    if ("node".equals(qName)) {
                        String modifiedStr = attributes.getValue("MODIFIED");
                        String text = attributes.getValue("TEXT");
                        if (text != null && !"bin".equalsIgnoreCase(text.trim())) {
                            nodeCount[0]++;
                        }

                        if (modifiedStr != null) {
                            try {
                                long modifiedAt = Long.parseLong(modifiedStr);
                                Date date = new Date(modifiedAt);

                                String dateKey = DATE_FORMAT.format(date);
                                dailyActivity.put(dateKey, dailyActivity.getOrDefault(dateKey, 0L) + 1);

                                String monthKey = MONTH_FORMAT.format(date);
                                monthlyActivity.put(monthKey, monthlyActivity.getOrDefault(monthKey, 0L) + 1);

                                Calendar cal = Calendar.getInstance();
                                cal.setTime(date);
                                int hour = cal.get(Calendar.HOUR_OF_DAY);
                                hourDistribution.put(hour, hourDistribution.getOrDefault(hour, 0L) + 1);

                                if (modifiedAt > lastModified[0]) {
                                    lastModified[0] = modifiedAt;
                                }
                            } catch (NumberFormatException ex) {
                                // ignore
                            }
                        }
                    }
                }
            });
        } catch (Exception ex) {
            LogUtils.warn("Error parsing file: " + file.getAbsolutePath(), ex);
        }

        if (nodeCount[0] > 0) {
            fileNodeCount.put(file.getName(), nodeCount[0]);
            fileActivity.put(file.getName(), lastModified[0]);
        }
    }

    private void updateTabs() {
        analysisTabs.removeAll();

        // 每日活动标签页
        analysisTabs.addTab("每日活动", createDailyActivityPanel());

        // 月度活动标签页
        analysisTabs.addTab("月度活动", createMonthlyActivityPanel());

        // 24小时分布标签页
        analysisTabs.addTab("时段分布", createHourDistributionPanel());

        // 导图统计标签页
        analysisTabs.addTab("导图统计", createMapStatisticsPanel());
    }

    private JComponent createDailyActivityPanel() {
        String[] columnNames = {"日期", "节点数量"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (Map.Entry<String, Long> entry : dailyActivity.entrySet()) {
            model.addRow(new Object[]{entry.getKey(), entry.getValue()});
        }

        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Number) {
                    long count = ((Number) value).longValue();
                    if (count >= 50) {
                        setBackground(new Color(255, 200, 200));
                    } else if (count >= 20) {
                        setBackground(new Color(255, 230, 200));
                    } else if (count >= 10) {
                        setBackground(new Color(230, 255, 230));
                    } else {
                        setBackground(isSelected ? table.getSelectionBackground() : Color.WHITE);
                    }
                    setForeground(isSelected ? table.getSelectionForeground() : Color.BLACK);
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        org.freeplane.core.ui.theme.DocearUiTheme.styleScrollPane(scrollPane);
        return scrollPane;
    }

    private JComponent createMonthlyActivityPanel() {
        String[] columnNames = {"月份", "节点数量"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (Map.Entry<String, Long> entry : monthlyActivity.entrySet()) {
            model.addRow(new Object[]{entry.getKey(), entry.getValue()});
        }

        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        JScrollPane scrollPane = new JScrollPane(table);
        org.freeplane.core.ui.theme.DocearUiTheme.styleScrollPane(scrollPane);
        return scrollPane;
    }

    private JComponent createHourDistributionPanel() {
        String[] columnNames = {"时段", "节点数量"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (int hour = 0; hour < 24; hour++) {
            String timeRange = String.format("%02d:00 - %02d:59", hour, hour);
            long count = hourDistribution.getOrDefault(hour, 0L);
            model.addRow(new Object[]{timeRange, count});
        }

        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);
        org.freeplane.core.ui.theme.DocearUiTheme.styleScrollPane(scrollPane);
        return scrollPane;
    }

    private JComponent createMapStatisticsPanel() {
        DefaultListModel<MapStatsEntry> listModel = new DefaultListModel<MapStatsEntry>();

        List<Map.Entry<String, Integer>> sortedFiles = new ArrayList<Map.Entry<String, Integer>>(fileNodeCount.entrySet());
        Collections.sort(sortedFiles, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });

        for (Map.Entry<String, Integer> entry : sortedFiles) {
            String fileName = entry.getKey();
            int nodeCount = entry.getValue();
            long lastModified = fileActivity.getOrDefault(fileName, 0L);
            listModel.addElement(new MapStatsEntry(fileName, nodeCount, lastModified));
        }

        JList<MapStatsEntry> list = new JList<MapStatsEntry>(listModel);
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof MapStatsEntry) {
                    MapStatsEntry entry = (MapStatsEntry) value;
                    String text = String.format("<html><b>%s</b> - %d 个节点<br><small>最后修改: %s</small></html>",
                            entry.fileName, entry.nodeCount, sdf.format(new Date(entry.lastModified)));
                    setText(text);
                }
                setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                return this;
            }
        });

        JScrollPane scrollPane = new JScrollPane(list);
        org.freeplane.core.ui.theme.DocearUiTheme.styleScrollPane(scrollPane);
        return scrollPane;
    }

    private static class MapStatsEntry {
        final String fileName;
        final int nodeCount;
        final long lastModified;

        MapStatsEntry(String fileName, int nodeCount, long lastModified) {
            this.fileName = fileName;
            this.nodeCount = nodeCount;
            this.lastModified = lastModified;
        }
    }
}
