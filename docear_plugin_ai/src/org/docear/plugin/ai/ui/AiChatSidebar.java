package org.docear.plugin.ai.ui;

import org.freeplane.core.ui.theme.DocearUiTheme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.docear.plugin.ai.DocearAiConfig;
import org.docear.plugin.ai.DocearAiController;
import org.docear.plugin.ai.backend.AiChatStreamListener;
import org.docear.plugin.ai.chat.AiChatMessage;
import org.docear.plugin.ai.chat.AiChatSession;
import org.docear.plugin.ai.chat.AiChatSessionManager;
import org.docear.plugin.ai.prompt.AiPromptBuilder;
import org.docear.plugin.ai.prompt.AiPromptBuildProgress;
import org.docear.plugin.ai.prompt.AiSelectedNodeExtractor;
import org.docear.plugin.core.settings.McpRuntimeFacade;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;

/**
 * AI 聊天侧边栏（三波 UI 优化）。
 */
public class AiChatSidebar extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JPanel messagesPanel;
    private final JScrollPane messagesScroll;
    private final JTextArea inputArea;
    private final JButton sendButton;
    private final JButton stopButton;
    private final JButton clearButton;
    private final JButton promptButton;
    private final AiChatStatusBar statusBar;
    private final AiKeywordButtonBar keywordBar;
    private final DocearAiController aiController;
    private final JComboBox backendSelect;
    private final JComboBox profileSelect;
    private boolean suppressModelEvents;

    private MapModel currentMap;
    private NodeModel focusNode;
    private AiChatMessagePanel streamingPanel;
    private volatile boolean requestInFlight;

    public AiChatSidebar(DocearAiController aiController) {
        this.aiController = aiController;
        setLayout(new BorderLayout(0, 4));

        statusBar = new AiChatStatusBar();
        keywordBar = new AiKeywordButtonBar();
        backendSelect = new JComboBox(new String[] {
                "OpenRouter / \u517c\u5bb9",
                "Copilot CLI"
        });
        profileSelect = new JComboBox();
        profileSelect.setToolTipText("\u4e0e\u7f51\u9875\u804a\u5929\u5171\u7528\u7684\u5927\u6a21\u578b\u914d\u7f6e\uff08webchat \u6570\u636e\u5e93\uff09");
        backendSelect.setToolTipText("\u4fa7\u680f\u804a\u5929\u540e\u7aef\uff1aOpenRouter \u4e0e\u7f51\u9875\u5171\u7528\u914d\u7f6e");

        JPanel modelRow = new JPanel(new BorderLayout(4, 0));
        modelRow.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 4));
        modelRow.setOpaque(false);
        JPanel modelLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        modelLeft.setOpaque(false);
        modelLeft.add(new JLabel("\u540e\u7aef"));
        modelLeft.add(backendSelect);
        modelRow.add(modelLeft, BorderLayout.WEST);
        JPanel modelRight = new JPanel(new BorderLayout(4, 0));
        modelRight.setOpaque(false);
        modelRight.add(new JLabel("\u6a21\u578b"), BorderLayout.WEST);
        modelRight.add(profileSelect, BorderLayout.CENTER);
        modelRow.add(modelRight, BorderLayout.CENTER);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(statusBar, BorderLayout.NORTH);
        north.add(modelRow, BorderLayout.SOUTH);

        messagesPanel = new JPanel();
        messagesPanel.setLayout(new javax.swing.BoxLayout(messagesPanel, javax.swing.BoxLayout.Y_AXIS));
        messagesPanel.setBackground(DocearUiTheme.SURFACE);
        messagesScroll = new JScrollPane(messagesPanel);
        messagesScroll.setBorder(BorderFactory.createEmptyBorder());
        messagesScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        messagesScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        messagesScroll.getVerticalScrollBar().setUnitIncrement(18);
        messagesScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                syncMessagePanelWidths();
            }
        });

        inputArea = new JTextArea(3, 20);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DocearUiTheme.HAIRLINE),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createEmptyBorder());

        sendButton = new JButton("\u53d1\u9001");
        stopButton = new JButton("\u505c\u6b62");
        stopButton.setEnabled(false);
        clearButton = new JButton("\u6e05\u7a7a\u5bf9\u8bdd");
        promptButton = new JButton("\u7f16\u8f91\u63d0\u793a\u8bcd");

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        buttonRow.add(clearButton);
        buttonRow.add(promptButton);
        buttonRow.add(stopButton);
        buttonRow.add(sendButton);

        JPanel southPanel = new JPanel(new BorderLayout(4, 4));
        southPanel.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));
        southPanel.add(keywordBar, BorderLayout.NORTH);
        southPanel.add(inputScroll, BorderLayout.CENTER);
        southPanel.add(buttonRow, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(messagesScroll, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        bindActions();
        reloadModelSelectors();
        reloadKeywordButtons();
        initializeForCurrentMap();
        LogUtils.info("AiChatSidebar initialized.");
    }

    private void initializeForCurrentMap() {
        MapModel map = null;
        try {
            map = Controller.getCurrentController().getMap();
        } catch (Exception e) {
            LogUtils.warn("Could not resolve current map for AI chat: " + e.getMessage());
        }
        switchToMap(map);
    }

    private void bindActions() {
        sendButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });
        stopButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                cancelRequest();
            }
        });
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                confirmClearChat();
            }
        });
        promptButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (aiController != null) {
                    aiController.getPromptBuilder().getTemplateStore().openTemplateFile();
                    reloadKeywordButtons();
                }
            }
        });
        backendSelect.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (suppressModelEvents) {
                    return;
                }
                applyBackendSelection();
            }
        });
        profileSelect.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (suppressModelEvents) {
                    return;
                }
                applyProfileSelection();
            }
        });
        keywordBar.setKeywordActionListener(new AiKeywordButtonBar.KeywordActionListener() {
            public void onKeywordClicked(String keyword, boolean sendImmediately) {
                appendKeyword(keyword, sendImmediately);
            }
        });

        inputArea.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send");
        inputArea.getActionMap().put("send", new javax.swing.AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });
        inputArea.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "newline");
        inputArea.getActionMap().put("newline", new javax.swing.AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                inputArea.append("\n");
            }
        });
    }

    private void appendKeyword(String keyword, boolean sendImmediately) {
        String current = inputArea.getText();
        if (current.length() > 0 && !current.endsWith("\n") && !current.endsWith(" ")) {
            inputArea.append(" ");
        }
        inputArea.append(keyword);
        inputArea.requestFocusInWindow();
        if (sendImmediately) {
            sendMessage();
        }
    }

    private void sendMessage() {
        if (requestInFlight) {
            return;
        }
        final String text = inputArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        final MapModel map = resolveCurrentMap();
        final NodeModel focus = resolveFocusNode();
        inputArea.setText("");
        if (aiController != null) {
            aiController.recordUserMessage(map, text);
        }
        addMessagePanel(AiChatMessage.Role.USER, text);

        streamingPanel = new AiChatMessagePanel(AiChatMessage.Role.ASSISTANT);
        streamingPanel.setContent("[1/6] \u51c6\u5907\u4e0a\u4e0b\u6587...");
        streamingPanel.setAssistantActionListener(createMessageActionListener());
        appendMessageComponent(streamingPanel);

        setRequestInFlight(true);
        statusBar.setHint("[1/6] \u51c6\u5907\u4e0a\u4e0b\u6587...");

        final Thread worker = new Thread(new Runnable() {
            public void run() {
                try {
                    if (aiController == null) {
                        finishWithError("\u672a\u521d\u59cb\u5316 AI \u63a7\u5236\u5668\u3002");
                        return;
                    }
                    if (!aiController.getBackend().isAvailable()) {
                        if (aiController.isOpenRouterBackend()) {
                            finishWithError("\u672a\u914d\u7f6e OpenRouter\u3002\u8bf7\u5728\u4ea7\u54c1\u8bbe\u7f6e \u2192 MCP \u6216\u7f51\u9875\u804a\u5929\u4e2d\u914d\u7f6e API Key\u3002");
                        }
                        else {
                            finishWithError("\u672a\u68c0\u6d4b\u5230 Copilot CLI\u3002\u8bf7\u5148\u5b89\u88c5\u5e76\u767b\u5f55\uff0c\u6216\u5207\u6362\u5230 OpenRouter\u3002");
                        }
                        return;
                    }
                    final AiPromptBuildProgress progress = new AiPromptBuildProgress() {
                        public void onStep(final int stepIndex, final int stepCount, final String label) {
                            SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    if (!isSameMap(map, resolveCurrentMap())) {
                                        return;
                                    }
                                    String hint = "[" + stepIndex + "/" + stepCount + "] " + label;
                                    statusBar.setHint(hint);
                                    if (streamingPanel != null) {
                                        streamingPanel.setContent(hint);
                                    }
                                }
                            });
                        }
                    };
                    aiController.invokeChatStreaming(text, map, focus, progress, new AiChatStreamListener() {
                    private final StringBuilder full = new StringBuilder();

                    public void onChunk(String chunk) {
                        if (chunk == null) {
                            return;
                        }
                        full.append(chunk);
                        if (full.toString().trim().length() == 0) {
                            return;
                        }
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                if (!isSameMap(map, resolveCurrentMap())) {
                                    return;
                                }
                                if (streamingPanel == null) {
                                    attachStreamingPlaceholder();
                                }
                                streamingPanel.setStreamingContent(full.toString());
                                syncMessagePanelWidths();
                                scrollToBottom();
                            }
                        });
                    }

                    public void onComplete(String fullText) {
                        final String reply = normalizeReply(fullText != null ? fullText : full.toString());
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                if (isSameMap(map, resolveCurrentMap())) {
                                    if (streamingPanel != null) {
                                        streamingPanel.setContent(reply);
                                    } else {
                                        reloadMessagesForMap(map);
                                    }
                                    syncMessagePanelWidths();
                                    refreshContextStatusAfterSend(text, map);
                                    scrollToBottom();
                                    inputArea.requestFocusInWindow();
                                }
                                releaseRequestInFlight(map);
                            }
                        });
                    }

                    public void onError(String message) {
                        final String errorMessage = message != null ? message : "";
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                if (isSameMap(map, resolveCurrentMap())) {
                                    if (errorMessage.length() > 0) {
                                        if (streamingPanel != null) {
                                            String current = streamingPanel.getPlainContent();
                                            streamingPanel.setContent(current + "\n\n[" + errorMessage + "]");
                                        }
                                        statusBar.setHint(errorMessage);
                                    }
                                }
                                releaseRequestInFlight(map);
                            }
                        });
                    }

                    public boolean isCancelled() {
                        return aiController != null && aiController.isChatCancelled();
                    }
                });
                } finally {
                    releaseRequestInFlight(map);
                }
            }
        }, "AiChatWorker");
        worker.setDaemon(true);
        worker.start();
    }

    private void finishWithError(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (streamingPanel != null) {
                    streamingPanel.setContent(message);
                }
                statusBar.setHint(message);
                setRequestInFlight(false);
            }
        });
    }

    private void cancelRequest() {
        if (!requestInFlight && (aiController == null || !aiController.isStreamingForMap(resolveCurrentMap()))) {
            return;
        }
        if (aiController != null) {
            aiController.cancelChatRequest();
        }
        if (streamingPanel != null) {
            String current = streamingPanel.getPlainContent();
            if ("\u601d\u8003\u4e2d...".equals(current) || "\u751f\u6210\u4e2d...".equals(current)) {
                streamingPanel.setContent("[\u5df2\u53d6\u6d88\u751f\u6210]");
            } else {
                streamingPanel.setContent(current + "\n\n[\u5df2\u53d6\u6d88\u751f\u6210]");
            }
        }
        statusBar.setHint("\u5df2\u53d6\u6d88");
        setRequestInFlight(false);
        inputArea.requestFocusInWindow();
    }

    private void confirmClearChat() {
        String[] options = new String[] {
                "\u4ec5\u6e05\u754c\u9762",
                "\u5220\u9664\u8bb0\u5f55",
                "\u53d6\u6d88"
        };
        int choice = JOptionPane.showOptionDialog(
                this,
                "\u8981\u5982\u4f55\u6e05\u7a7a\u5f53\u524d\u5bfc\u56fe\u7684\u5bf9\u8bdd\uff1f",
                "\u6e05\u7a7a\u5bf9\u8bdd",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[2]);
        if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
            return;
        }
        if (choice == 1 && aiController != null) {
            aiController.clearChatSession(resolveCurrentMap());
        }
        messagesPanel.removeAll();
        messagesPanel.revalidate();
        messagesPanel.repaint();
        refreshContextStatus();
    }

    public NodeModel getFocusNode() {
        return focusNode;
    }

    public void prepareAskAboutNode(NodeModel node) {
        if (node == null) {
            return;
        }
        this.focusNode = node;
        if (node.getMap() != null) {
            this.currentMap = node.getMap();
        }
        String title = AiSelectedNodeExtractor.extractTitle(node);
        if (title.length() == 0) {
            title = "\u672a\u547d\u540d\u8282\u70b9";
        }
        appendHintMessage("\u5df2\u5b9a\u4f4d\u8282\u70b9\u300c" + title + "\u300d\uff0c\u8bf7\u8f93\u5165\u95ee\u9898\u540e Enter \u53d1\u9001\u3002");
        inputArea.setText("\u8bf7\u5206\u6790\u8fd9\u4e2a\u8282\u70b9\u53ca\u5176\u5b50\u5185\u5bb9");
        inputArea.selectAll();
        refreshContextStatus();
        inputArea.requestFocusInWindow();
    }

    public void switchToMap(MapModel map) {
        if (isSameMap(this.currentMap, map)) {
            refreshContextStatus();
            return;
        }
        if (requestInFlight) {
            detachStreamingUi();
        }
        this.currentMap = map;
        this.focusNode = null;
        messagesPanel.removeAll();
        reloadKeywordButtons();
        if (map != null) {
            restoreChatHistory(map);
            if (aiController != null && aiController.isStreamingForMap(map)) {
                attachStreamingPlaceholder();
            }
        } else {
            appendHintMessage("\u5f53\u524d\u6ca1\u6709\u6253\u5f00\u7684\u601d\u7ef4\u5bfc\u56fe\u3002");
        }
        refreshContextStatus();
        scrollToBottom();
    }

    private void detachStreamingUi() {
        streamingPanel = null;
        setRequestInFlight(false);
    }

    private void attachStreamingPlaceholder() {
        streamingPanel = new AiChatMessagePanel(AiChatMessage.Role.ASSISTANT);
        streamingPanel.setContent("\u751f\u6210\u4e2d...");
        streamingPanel.setAssistantActionListener(createMessageActionListener());
        appendMessageComponent(streamingPanel);
        setRequestInFlight(true);
        statusBar.setHint("\u751f\u6210\u4e2d...");
    }

    private void reloadMessagesForMap(MapModel map) {
        messagesPanel.removeAll();
        if (map != null) {
            restoreChatHistory(map);
        }
    }

    private boolean isSameMap(MapModel left, MapModel right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return AiChatSessionManager.resolveMapKey(left).equals(AiChatSessionManager.resolveMapKey(right));
    }

    public void refreshContextStatus() {
        refreshContextStatusAfterSend("", resolveCurrentMap());
    }

    private void refreshContextStatusAfterSend(String lastQuestion, MapModel map) {
        if (aiController == null) {
            statusBar.updateContext(null);
            return;
        }
        int redactionCount = 0;
        if (lastQuestion != null && lastQuestion.length() > 0) {
            String raw = aiController.getPromptBuilder().buildRawChatPrompt(
                    lastQuestion, map, aiController.getChatSessionManager().getOrCreateSession(map),
                    resolveFocusNode());
            redactionCount = AiPromptBuilder.countRedactions(raw);
        }
        statusBar.updateContext(aiController.buildContextInfo(map, lastQuestion, redactionCount, ""));
        updateInputPlaceholder(map);
    }

    private void updateInputPlaceholder(MapModel map) {
        if (aiController == null) {
            return;
        }
        NodeModel focus = resolveFocusNode();
        String selected = focus != null ? AiSelectedNodeExtractor.extractTitle(focus) : "";
        if (selected.length() == 0) {
            AiChatContextInfo info = aiController.buildContextInfo(map, "", 0, "");
            selected = info.getSelectedNodeText();
        }
        if (selected.length() > 0) {
            inputArea.setToolTipText("\u9488\u5bf9\u300c" + truncate(selected, 40) + "\u300d\u63d0\u95ee\uff08Enter \u53d1\u9001\uff0cShift+Enter \u6362\u884c\uff09");
        } else {
            inputArea.setToolTipText("Enter \u53d1\u9001\uff0cShift+Enter \u6362\u884c");
        }
    }

    private void reloadKeywordButtons() {
        if (aiController == null) {
            keywordBar.setKeywords(new java.util.ArrayList<String>());
            return;
        }
        List<String> keywords = aiController.getPromptBuilder().getTemplateStore().getKeywordLabels();
        keywordBar.setKeywords(keywords);
    }

    public void reloadModelSelectors() {
        suppressModelEvents = true;
        try {
            final DocearAiConfig config = new DocearAiConfig();
            final String backend = config.getBackendType();
            backendSelect.setSelectedIndex(
                    "copilot_cli".equalsIgnoreCase(backend) ? 1 : 0);
            final DefaultComboBoxModel model = new DefaultComboBoxModel();
            final List profiles = McpRuntimeFacade.safeListProfiles();
            final String selectedId = config.getLlmProfileId();
            int selectedIndex = 0;
            if (profiles == null || profiles.isEmpty()) {
                model.addElement(new ProfileItem("", McpRuntimeFacade.safeIsLlmConfigured()
                        ? "\u9ed8\u8ba4\uff08\u4ea7\u54c1\u8bbe\u7f6e\uff09"
                        : "\u672a\u914d\u7f6e\uff08\u8bf7\u5148\u5728\u8bbe\u7f6e/\u7f51\u9875\u914d\u7f6e\uff09"));
            }
            else {
                for (int i = 0; i < profiles.size(); i++) {
                    final Object rowObj = profiles.get(i);
                    if (!(rowObj instanceof java.util.Map)) {
                        continue;
                    }
                    final java.util.Map row = (java.util.Map) rowObj;
                    final String id = row.get("id") == null ? "" : String.valueOf(row.get("id"));
                    final String name = row.get("name") == null ? "LLM" : String.valueOf(row.get("name"));
                    final String modelName = row.get("model") == null ? "" : String.valueOf(row.get("model"));
                    final boolean isDefault = Boolean.TRUE.equals(row.get("isDefault"));
                    final String label = (isDefault ? "\u2605 " : "") + name
                            + (modelName.length() > 0 ? (" · " + modelName) : "");
                    model.addElement(new ProfileItem(id, label));
                    if (selectedId != null && selectedId.equals(id)) {
                        selectedIndex = model.getSize() - 1;
                    }
                    else if ((selectedId == null || selectedId.length() == 0) && isDefault) {
                        selectedIndex = model.getSize() - 1;
                    }
                }
            }
            profileSelect.setModel(model);
            if (model.getSize() > 0) {
                profileSelect.setSelectedIndex(Math.max(0, Math.min(selectedIndex, model.getSize() - 1)));
            }
            final boolean openRouter = backendSelect.getSelectedIndex() == 0;
            profileSelect.setEnabled(openRouter);
        }
        finally {
            suppressModelEvents = false;
        }
    }

    private void applyBackendSelection() {
        final DocearAiConfig config = new DocearAiConfig();
        if (backendSelect.getSelectedIndex() == 1) {
            config.setBackendType("copilot_cli");
        }
        else {
            config.setBackendType("openrouter");
        }
        profileSelect.setEnabled(backendSelect.getSelectedIndex() == 0);
        if (aiController != null) {
            aiController.reloadBackend();
        }
        refreshContextStatus();
    }

    private void applyProfileSelection() {
        final Object item = profileSelect.getSelectedItem();
        if (!(item instanceof ProfileItem)) {
            return;
        }
        final DocearAiConfig config = new DocearAiConfig();
        config.setLlmProfileId(((ProfileItem) item).id);
        if (aiController != null) {
            aiController.reloadBackend();
        }
        refreshContextStatus();
    }

    private static final class ProfileItem {
        final String id;
        final String label;

        ProfileItem(final String id, final String label) {
            this.id = id == null ? "" : id;
            this.label = label == null ? "" : label;
        }

        public String toString() {
            return label;
        }
    }

    private void restoreChatHistory(MapModel map) {
        if (aiController == null || map == null) {
            return;
        }
        AiChatSession session = aiController.getChatSessionManager().getOrCreateSession(map);
        List<AiChatMessage> messages = session.getMessages();
        if (messages.isEmpty()) {
            return;
        }
        JLabel divider = new JLabel("--- \u5386\u53f2\u5bf9\u8bdd ---");
        divider.setAlignmentX(LEFT_ALIGNMENT);
        divider.setBorder(BorderFactory.createEmptyBorder(6, 6, 4, 6));
        messagesPanel.add(divider);
        for (int i = 0; i < messages.size(); i++) {
            AiChatMessage message = messages.get(i);
            addMessagePanel(message.getRole(), message.getContent());
        }
        JLabel continueLabel = new JLabel("--- \u7ee7\u7eed\u5bf9\u8bdd ---");
        continueLabel.setAlignmentX(LEFT_ALIGNMENT);
        continueLabel.setBorder(BorderFactory.createEmptyBorder(6, 6, 4, 6));
        messagesPanel.add(continueLabel);
        syncMessagePanelWidths();
        messagesPanel.revalidate();
        messagesPanel.repaint();
    }

    private void addMessagePanel(AiChatMessage.Role role, String content) {
        AiChatMessagePanel panel = new AiChatMessagePanel(role);
        panel.setContent(content);
        if (role == AiChatMessage.Role.ASSISTANT) {
            panel.setAssistantActionListener(createMessageActionListener());
        }
        appendMessageComponent(panel);
    }

    private AiChatMessagePanel.MessageActionListener createMessageActionListener() {
        return new AiChatMessagePanel.MessageActionListener() {
            public void onInsertAsChild(String content) {
                if (aiController != null) {
                    aiController.insertContentAsChild(content);
                }
            }

            public void onInsertAsSibling(String content) {
                if (aiController != null) {
                    aiController.insertContentAsSibling(content);
                }
            }
        };
    }

    private void appendMessageComponent(AiChatMessagePanel panel) {
        panel.setAlignmentX(LEFT_ALIGNMENT);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setAlignmentX(LEFT_ALIGNMENT);
        wrapper.setOpaque(false);
        wrapper.add(panel, BorderLayout.CENTER);
        messagesPanel.add(wrapper);
        syncMessagePanelWidths();
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();
    }

    private void syncMessagePanelWidths() {
        final int width = messagesScroll.getViewport().getWidth();
        if (width <= 0) {
            return;
        }
        fitAllMessagePanels(width);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                fitAllMessagePanels(width);
                messagesPanel.revalidate();
                messagesPanel.repaint();
            }
        });
    }

    private void fitAllMessagePanels(int width) {
        for (java.awt.Component component : messagesPanel.getComponents()) {
            AiChatMessagePanel panel = findMessagePanel(component);
            if (panel != null) {
                panel.fitToWidth(width);
            } else {
                Dimension pref = component.getPreferredSize();
                int height = pref.height > 0 ? pref.height : component.getHeight();
                component.setMaximumSize(new Dimension(width, height));
                component.setPreferredSize(new Dimension(width, height));
            }
        }
        messagesPanel.revalidate();
    }

    private AiChatMessagePanel findMessagePanel(java.awt.Component component) {
        if (component instanceof AiChatMessagePanel) {
            return (AiChatMessagePanel) component;
        }
        if (component instanceof JPanel) {
            JPanel container = (JPanel) component;
            java.awt.Component[] children = container.getComponents();
            for (int i = 0; i < children.length; i++) {
                AiChatMessagePanel panel = findMessagePanel(children[i]);
                if (panel != null) {
                    return panel;
                }
            }
        }
        return null;
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JScrollPane scroll = messagesScroll;
                if (scroll != null) {
                    javax.swing.JScrollBar bar = scroll.getVerticalScrollBar();
                    bar.setValue(bar.getMaximum());
                }
            }
        });
    }

    private void setRequestInFlight(boolean inFlight) {
        requestInFlight = inFlight;
        sendButton.setEnabled(!inFlight);
        stopButton.setEnabled(inFlight);
        clearButton.setEnabled(!inFlight);
        promptButton.setEnabled(true);
        inputArea.setEnabled(!inFlight);
    }

    private void releaseRequestInFlight(final MapModel map) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (isSameMap(map, resolveCurrentMap())) {
                    setRequestInFlight(false);
                }
            }
        });
    }

    private NodeModel resolveFocusNode() {
        if (focusNode != null) {
            return focusNode;
        }
        try {
            return Controller.getCurrentController().getSelection().getSelected();
        } catch (Exception e) {
            return null;
        }
    }

    private void appendHintMessage(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(DocearUiTheme.ACCENT_DEEP);
        label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        label.setAlignmentX(LEFT_ALIGNMENT);
        messagesPanel.add(label);
        syncMessagePanelWidths();
        messagesPanel.revalidate();
        messagesPanel.repaint();
        scrollToBottom();
    }

    private MapModel resolveCurrentMap() {
        if (currentMap != null) {
            return currentMap;
        }
        try {
            return Controller.getCurrentController().getMap();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeReply(String reply) {
        if (reply == null || reply.trim().length() == 0) {
            if (aiController != null && aiController.isOpenRouterBackend()) {
                return "\u672a\u6536\u5230\u6709\u6548\u56de\u590d\u3002\u8bf7\u68c0\u67e5 OpenRouter API Key\u3001\u6a21\u578b\u540d\u4e0e\u7f51\u7edc\u3002";
            }
            return "\u672a\u6536\u5230\u6709\u6548\u56de\u590d\u3002\u8bf7\u5728 PowerShell \u4e2d\u6d4b\u8bd5\uff1acopilot -p \"\u6d4b\u8bd5\" -s";
        }
        return reply.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text != null ? text : "";
        }
        return text.substring(0, max) + "...";
    }
}
