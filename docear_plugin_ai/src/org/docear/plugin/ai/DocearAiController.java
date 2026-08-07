package org.docear.plugin.ai;

import javax.swing.JTabbedPane;

import java.util.List;

import org.docear.plugin.ai.actions.AiAskAboutNodeAction;
import org.docear.plugin.ai.actions.AiGenerateSubNodesAction;
import org.docear.plugin.ai.actions.OpenAiPromptTemplateAction;
import org.docear.plugin.ai.backend.AiBackend;
import org.docear.plugin.ai.backend.AiChatStreamListener;
import org.docear.plugin.ai.backend.CopilotCliBackend;
import org.docear.plugin.ai.backend.OpenAiCompatibleBackend;
import org.docear.plugin.ai.chat.AiChatHistoryExtensionIO;
import org.docear.plugin.ai.chat.AiChatMessage;
import org.docear.plugin.ai.chat.AiChatSession;
import org.docear.plugin.ai.chat.AiChatSessionManager;
import org.docear.plugin.ai.log.AiInteractionLogger;
import org.docear.plugin.ai.log.AiInteractionRecord;
import org.docear.plugin.ai.prompt.AiContextCollector;
import org.docear.plugin.ai.prompt.AiPromptBuildProgress;
import org.docear.plugin.ai.prompt.AiPromptBuilder;
import org.docear.plugin.ai.prompt.AiPromptTemplateGuard;
import org.docear.plugin.ai.prompt.AiSelectedNodeExtractor;
import org.docear.plugin.ai.ui.AiChatContextInfo;
import org.docear.plugin.ai.ui.AiChatSidebar;
import org.docear.plugin.ai.ui.AiChatTabInstaller;
import org.docear.plugin.ai.ui.AiChatTabMetrics;
import org.docear.plugin.ai.snapshot.AiWorkspaceSnapshotAutoSync;
import org.docear.plugin.ai.ui.AiMarkdownRenderer;
import org.docear.plugin.ai.usage.AiUsageCounter;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.IMenuContributor;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.map.IMapSelectionListener;
import org.freeplane.features.map.INodeSelectionListener;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.mindmapmode.MMapController;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.text.TextController;
import org.freeplane.features.text.mindmapmode.MTextController;

public class DocearAiController {

    private static DocearAiController instance;
    private final ModeController modeController;
    private AiBackend backend;
    private final AiChatSidebar chatSidebar;
    private final AiPromptBuilder promptBuilder;
    private final AiInteractionLogger interactionLogger;
    private final AiChatSessionManager chatSessionManager;
    private final AiUsageCounter usageCounter;
    private volatile boolean chatCancelled;
    private volatile String activeStreamingMapKey;

    private DocearAiController(ModeController modeController) {
        this.modeController = modeController;
        this.backend = createBackend();
        this.promptBuilder = new AiPromptBuilder();
        this.interactionLogger = new AiInteractionLogger();
        this.chatSessionManager = new AiChatSessionManager();
        this.usageCounter = new AiUsageCounter();
        this.promptBuilder.getTemplateStore().ensureTemplateFileExists();
        this.interactionLogger.ensureLogDirectoryExists();
        this.chatSessionManager.getStore().ensureDirectoryExists();
        this.chatSidebar = new AiChatSidebar(this);
        scheduleWorkspaceScanPreload();
        AiChatHistoryExtensionIO.install(modeController);
        AiPromptTemplateGuard.install(modeController);
        registerSelectionListener();
        registerMapSelectionListener();
        registerActions();
        registerMenus();
        installAiChatTab();
        AiWorkspaceSnapshotAutoSync.install(modeController);
    }

    private void scheduleWorkspaceScanPreload() {
        try {
            Class cacheClass = Class.forName("org.freeplane.core.util.WorkspaceSideTabScanCache");
            cacheClass.getMethod("schedulePreload", new Class[0]).invoke(null, new Object[0]);
        }
        catch (Exception e) {
            LogUtils.info("Workspace scan preload not available: " + e.getMessage());
        }
    }

    private AiBackend createBackend() {
        DocearAiConfig config = new DocearAiConfig();
        String backendType = config.getBackendType();
        if ("openrouter".equalsIgnoreCase(backendType) || "openai".equalsIgnoreCase(backendType)
                || "openai_compatible".equalsIgnoreCase(backendType)) {
            return new OpenAiCompatibleBackend();
        }
        if ("copilot_cli".equalsIgnoreCase(backendType)) {
            return new CopilotCliBackend();
        }
        if (config.isOpenRouterAvailable()) {
            return new OpenAiCompatibleBackend();
        }
        return new CopilotCliBackend();
    }

    /** Recreate backend after user switches provider / profile in the sidebar. */
    public void reloadBackend() {
        this.backend = createBackend();
    }

    public boolean isOpenRouterBackend() {
        return backend instanceof OpenAiCompatibleBackend;
    }

    public static void install(ModeController modeController) {
        if (instance == null) {
            instance = new DocearAiController(modeController);
        }
    }

    public static DocearAiController getController() {
        return instance;
    }

    public AiBackend getBackend() {
        return backend;
    }

    public AiPromptBuilder getPromptBuilder() {
        return promptBuilder;
    }

    public AiInteractionLogger getInteractionLogger() {
        return interactionLogger;
    }

    public AiChatSessionManager getChatSessionManager() {
        return chatSessionManager;
    }

    public AiChatSidebar getChatSidebar() {
        return chatSidebar;
    }

    public AiUsageCounter getUsageCounter() {
        return usageCounter;
    }

    public String invokeChat(String userInput, MapModel map) {
        AiChatSession session = chatSessionManager.getOrCreateSession(map);
        session.addMessage(new AiChatMessage(AiChatMessage.Role.USER, userInput));

        String prompt = promptBuilder.buildChatPrompt(userInput, map, session);
        usageCounter.recordInvocation(AiUsageCounter.TYPE_CHAT);
        String response = backend.chat(prompt);
        if (response == null) {
            response = "";
        }

        session.addMessage(new AiChatMessage(AiChatMessage.Role.ASSISTANT, response));
        chatSessionManager.saveSession(map, session);
        publishChatTabMetric(map);
        logInteraction(AiInteractionRecord.TYPE_CHAT, userInput, prompt, response, map);
        return response;
    }

    public void invokeChatStreaming(final String userInput, final MapModel map, final AiChatStreamListener uiListener) {
        invokeChatStreaming(userInput, map, null, uiListener);
    }

    public void invokeChatStreaming(final String userInput, final MapModel map, final NodeModel focusNode,
            final AiChatStreamListener uiListener) {
        invokeChatStreaming(userInput, map, focusNode, null, uiListener);
    }

    public void invokeChatStreaming(final String userInput, final MapModel map, final NodeModel focusNode,
            final AiPromptBuildProgress progress, final AiChatStreamListener uiListener) {
        chatCancelled = false;
        recordUserMessage(map, userInput);
        activeStreamingMapKey = AiChatSessionManager.resolveMapKey(map);

        final AiChatSession session = chatSessionManager.getOrCreateSession(map);

        final long promptBuildStarted = System.currentTimeMillis();
        final String rawPrompt = promptBuilder.buildRawChatPrompt(userInput, map, session, focusNode, progress);
        final int maxPromptChars = new DocearAiConfig().getMaxOutboundPromptChars();
        final String boundedPrompt = AiPromptBuilder.limitPromptLength(rawPrompt, maxPromptChars);
        final int redactionCount = AiPromptBuilder.countRedactions(boundedPrompt);
        final String prompt = AiPromptBuilder.sanitizeForOutbound(boundedPrompt);
        LogUtils.info("AI prompt prepared in " + (System.currentTimeMillis() - promptBuildStarted)
                + " ms, length=" + prompt.length() + " (raw=" + rawPrompt.length() + ").");

        usageCounter.recordInvocation(AiUsageCounter.TYPE_CHAT);

        if (progress != null) {
            final DocearAiConfig aiConfig = new DocearAiConfig();
            if (backend instanceof OpenAiCompatibleBackend) {
                final String model = ((OpenAiCompatibleBackend) backend).getSelectedModelLabel();
                progress.onStep(6, 6, "\u8c03\u7528 OpenRouter"
                        + (model.length() > 0 ? (" · " + model) : "")
                        + "\uff08\u53ef\u80fd\u9700\u7b49\u51e0\u5341\u79d2\uff09...");
            }
            else {
                progress.onStep(6, 6, aiConfig.isMcpEnabled()
                        ? "\u8c03\u7528 Copilot CLI + Docear MCP\uff08\u53ef\u80fd\u9700\u7b49\u51e0\u5341\u79d2\uff09..."
                        : "\u8c03\u7528 Copilot CLI\uff08\u53ef\u80fd\u9700\u7b49\u51e0\u5341\u79d2\uff09...");
            }
        }

        backend.chatStreaming(prompt, new AiChatStreamListener() {
            private final StringBuilder full = new StringBuilder();
            private boolean firstChunkReceived;

            public void onChunk(String chunk) {
                if (chunk != null) {
                    full.append(chunk);
                }
                if (!firstChunkReceived && chunk != null && chunk.trim().length() > 0) {
                    firstChunkReceived = true;
                    if (progress != null) {
                        progress.onStep(6, 6, "\u63a5\u6536\u56de\u590d\u4e2d...");
                    }
                }
                if (uiListener != null) {
                    uiListener.onChunk(chunk);
                }
            }

            public void onComplete(String fullText) {
                activeStreamingMapKey = null;
                String response = fullText != null && fullText.length() > 0 ? fullText : full.toString();
                if (response == null) {
                    response = "";
                }
                session.addMessage(new AiChatMessage(AiChatMessage.Role.ASSISTANT, response));
                chatSessionManager.saveSession(map, session);
                publishChatTabMetric(map);
                logInteraction(AiInteractionRecord.TYPE_CHAT, userInput, prompt, response, map);
                if (uiListener != null) {
                    uiListener.onComplete(response);
                }
            }

            public void onError(String message) {
                activeStreamingMapKey = null;
                if (uiListener != null) {
                    uiListener.onError(message);
                }
            }

            public boolean isCancelled() {
                return chatCancelled || (uiListener != null && uiListener.isCancelled());
            }
        });
    }

    /**
     * 立即保存用户消息，避免切换导图时丢失尚未完成的对话。
     */
    public void recordUserMessage(MapModel map, String userInput) {
        if (map == null || userInput == null || userInput.trim().length() == 0) {
            return;
        }
        AiChatSession session = chatSessionManager.getOrCreateSession(map);
        List<AiChatMessage> messages = session.getMessages();
        if (!messages.isEmpty()) {
            AiChatMessage last = messages.get(messages.size() - 1);
            if (last.getRole() == AiChatMessage.Role.USER && userInput.equals(last.getContent())) {
                chatSessionManager.saveSession(map, session);
                publishChatTabMetric(map);
                return;
            }
        }
        session.addMessage(new AiChatMessage(AiChatMessage.Role.USER, userInput));
        chatSessionManager.saveSession(map, session);
        publishChatTabMetric(map);
    }

    private void publishChatTabMetric(final MapModel map) {
        AiChatTabMetrics.publishForMap(map, chatSessionManager);
    }

    public boolean isStreamingForMap(MapModel map) {
        if (map == null || activeStreamingMapKey == null) {
            return false;
        }
        return activeStreamingMapKey.equals(AiChatSessionManager.resolveMapKey(map));
    }

    public boolean isChatCancelled() {
        return chatCancelled;
    }

    public void cancelChatRequest() {
        chatCancelled = true;
        activeStreamingMapKey = null;
        backend.cancelCurrentRequest();
    }

    public AiChatContextInfo buildContextInfo(MapModel map, String userQuestion, int redactionCount, String statusHint) {
        NodeModel selected = chatSidebar != null ? chatSidebar.getFocusNode() : null;
        if (selected == null) {
            selected = resolveSelectedNode();
        }
        String selectedText = AiSelectedNodeExtractor.extractTitle(selected);
        AiContextCollector.ContextBundle bundle = AiContextCollector.collect(map, userQuestion);
        String mapTitle = map != null && map.getTitle() != null ? map.getTitle() : "\u65e0";
        return new AiChatContextInfo(
                mapTitle,
                AiPromptBuilder.resolveMapPath(map),
                selectedText,
                bundle.getFilesIncluded(),
                bundle.getFilesDiscovered(),
                backend.isAvailable(),
                redactionCount,
                statusHint,
                usageCounter.getThisMonthCount(),
                usageCounter.getMonthlyQuota(),
                usageCounter.getTodayCount());
    }

    public void clearChatSession(MapModel map) {
        chatSessionManager.clearSession(map);
        publishChatTabMetric(map);
    }

    public void insertContentAsChild(String content) {
        insertContent(content, true);
    }

    public void insertContentAsSibling(String content) {
        insertContent(content, false);
    }

    private void insertContent(String content, boolean asChild) {
        if (content == null || content.trim().length() == 0) {
            return;
        }
        NodeModel selected = resolveSelectedNode();
        if (selected == null) {
            MapModel map = Controller.getCurrentController().getMap();
            if (map != null) {
                selected = map.getRootNode();
            }
        }
        if (selected == null) {
            return;
        }
        String[] lines = AiMarkdownRenderer.splitIntoNodeLines(content);
        if (lines.length == 0) {
            return;
        }
        MMapController mapController = (MMapController) Controller.getCurrentModeController().getMapController();
        MTextController textController = (MTextController) TextController.getController();
        NodeModel parent;
        int insertIndex;
        if (asChild) {
            parent = selected;
            insertIndex = parent.getChildCount();
        } else {
            parent = selected.getParentNode();
            if (parent == null) {
                parent = selected;
                insertIndex = parent.getChildCount();
            } else {
                insertIndex = parent.getIndex(selected) + 1;
            }
        }
        for (int i = 0; i < lines.length; i++) {
            NodeModel newNode = mapController.addNewNode(parent, insertIndex + i, selected.isLeft());
            String richText = AiMarkdownRenderer.toNodeRichText(lines[i]);
            if (richText != null) {
                textController.setNodeText(newNode, richText);
            } else {
                textController.setNodeText(newNode, lines[i]);
            }
        }
    }

    private NodeModel resolveSelectedNode() {
        try {
            return Controller.getCurrentController().getSelection().getSelected();
        } catch (Exception e) {
            return null;
        }
    }

    public List<String> invokeGenerateSubNodes(String topic, MapModel map, int count) {
        String prompt = promptBuilder.buildSubNodesPrompt(topic, map, count);
        usageCounter.recordInvocation(AiUsageCounter.TYPE_SUBNODES);
        List<String> result = backend.generateSubNodes(prompt, count);
        String response = joinLines(result);
        logInteraction(AiInteractionRecord.TYPE_GENERATE_SUBNODES, topic, prompt, response, map);
        return result;
    }

    private void registerSelectionListener() {
        modeController.getMapController().addNodeSelectionListener(new INodeSelectionListener() {
            public void onSelect(NodeModel node) {
                chatSidebar.refreshContextStatus();
            }

            public void onDeselect(NodeModel node) {
                chatSidebar.refreshContextStatus();
            }
        });
    }

    private void registerMapSelectionListener() {
        Controller.getCurrentController().getMapViewManager().addMapSelectionListener(new IMapSelectionListener() {
            public void beforeMapChange(MapModel oldMap, MapModel newMap) {
            }

            public void afterMapChange(MapModel oldMap, MapModel newMap) {
                chatSidebar.switchToMap(newMap);
                publishChatTabMetric(newMap);
            }
        });
    }

    private void logInteraction(String type, String userInput, String prompt, String response, MapModel map) {
        String mapPath = AiPromptBuilder.resolveMapPath(map);
        String mapTitle = map != null && map.getTitle() != null ? map.getTitle() : "\u65e0";
        interactionLogger.log(new AiInteractionRecord(
                type, userInput, prompt, response, mapPath, mapTitle, System.currentTimeMillis()));
    }

    private String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    public void askAboutNode(NodeModel node) {
        if (node == null) {
            return;
        }
        showAiChatSidebar();
        chatSidebar.prepareAskAboutNode(node);
    }

    private void registerActions() {
        modeController.addAction(new AiGenerateSubNodesAction());
        modeController.addAction(new AiAskAboutNodeAction());
        modeController.addAction(new OpenAiPromptTemplateAction());
        LogUtils.info("Docear AI actions registered.");
    }

    private void registerMenus() {
        modeController.addMenuContributor(new IMenuContributor() {
            @Override
            public void updateMenus(ModeController mc, MenuBuilder builder) {
                builder.addSeparator("/node_popup", MenuBuilder.AS_CHILD);
                builder.addAction("/node_popup", modeController.getAction(AiAskAboutNodeAction.KEY), MenuBuilder.AS_CHILD);
                builder.addAction("/node_popup", modeController.getAction(AiGenerateSubNodesAction.KEY), MenuBuilder.AS_CHILD);

                builder.addAction("/menu_bar/extras", modeController.getAction(OpenAiPromptTemplateAction.KEY), MenuBuilder.AS_CHILD);
                builder.addAction("/menu_bar/extras", new AFreeplaneAction("AiChatSidebarAction", "AI \u804a\u5929\u4fa7\u8fb9\u680f", null) {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public void actionPerformed(java.awt.event.ActionEvent e) {
                        showAiChatSidebar();
                    }
                }, MenuBuilder.AS_CHILD);
            }
        });
        LogUtils.info("Docear AI menus registered.");
    }

    private void installAiChatTab() {
        AiChatTabInstaller.install(modeController, chatSidebar);
        publishChatTabMetric(Controller.getCurrentController().getMap());
    }

    private void showAiChatSidebar() {
        try {
            JTabbedPane rightTabs = AiChatTabInstaller.findFormatTabbedPane(modeController);
            if (rightTabs == null) {
                AiChatTabInstaller.tryInstall(modeController, chatSidebar);
                rightTabs = AiChatTabInstaller.findFormatTabbedPane(modeController);
            }
            if (rightTabs == null) {
                LogUtils.warn("Could not find right tab pane for AI sidebar.");
                return;
            }

            // Select by component identity — title strings can desync from panels.
            int index = AiChatTabInstaller.indexOfComponent(rightTabs, chatSidebar);
            if (index < 0) {
                AiChatTabInstaller.tryInstall(modeController, rightTabs, chatSidebar);
                index = AiChatTabInstaller.indexOfComponent(rightTabs, chatSidebar);
            }
            if (index >= 0) {
                rightTabs.setSelectedIndex(index);
                chatSidebar.switchToMap(Controller.getCurrentController().getMap());
                return;
            }
            LogUtils.warn("AI chat tab could not be selected after install attempt.");
        } catch (Exception ex) {
            LogUtils.severe("Failed to show AI Chat Sidebar: " + ex.getMessage());
        }
    }

    public void generateSubNodesForNode(NodeModel node) {
    }
}
