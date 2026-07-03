package org.docear.plugin.ai.prompt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.docear.plugin.ai.DocearAiConfig;
import org.freeplane.core.util.LogUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 从 AI提示词.mm 读取/初始化提示词模板。
 */
public class AiPromptTemplateStore {

    private static final String DEFAULT_CHAT_TEMPLATE =
            "\u4f60\u662f\u5177\u5907\u6700\u5f3a\u5206\u6790\u80fd\u529b\u7684\u601d\u7ef4\u5bfc\u56fe\u4e0e\u7814\u7a76\u52a9\u624b\u3002\u7528\u6237\u6b63\u5728 Docear/Freeplane \u4e2d\u5de5\u4f5c\u3002\n\n"
            + "Docear \u5df2\u5728\u672c\u5730\u4e3a\u4f60\u8bfb\u53d6\u4e86\u5f53\u524d\u601d\u7ef4\u5bfc\u56fe\u3001\u8282\u70b9\u94fe\u63a5\u53ca\u6587\u672c\u4e2d\u63d0\u53ca\u7684\u5173\u8054\u6587\u4ef6\u5185\u5bb9\uff08\u89c1\u4e0b\u65b9\uff09\u3002"
            + "\u8bf7\u57fa\u4e8e\u8fd9\u4e9b\u5df2\u63d0\u4f9b\u7684\u4fe1\u606f\u8fdb\u884c\u5168\u9762\u3001\u6df1\u5165\u3001\u591a\u89d2\u5ea6\u7684\u5206\u6790\uff0c\u5c3d\u53ef\u80fd\u8003\u8651\u5468\u5168\u540e\u56de\u7b54\u7528\u6237\u95ee\u9898\u3002\n\n"
            + "\u91cd\u8981\u8bf4\u660e\uff1a\n"
            + "1. \u4f60\u6536\u5230\u7684\u5185\u5bb9\u5df2\u7ecf\u8fc7\u672c\u5730\u8131\u654f\uff0c\u5bc6\u7801\u3001\u5bc6\u94a5\u3001\u4ee4\u724c\u7b49\u654f\u611f\u4fe1\u606f\u5df2\u66ff\u6362\u4e3a [\u5df2\u8131\u654f]\uff0c\u8bf7\u52ff\u8bd5\u56fe\u8fd8\u539f\u6216\u731c\u6d4b\u3002\n"
            + "2. \u4e0b\u65b9 MAP_CONTENT \u7b49\u4e3a\u672c\u5730\u9884\u8bfb\u6458\u8981\uff1b\u4fe1\u606f\u4e0d\u8db3\u6216\u9700\u8981\u5199\u5165/\u641c\u7d22\u65f6\uff0c\u8bf7\u901a\u8fc7 Docear MCP \u5de5\u5177\u83b7\u53d6\u6700\u65b0\u6570\u636e\uff0c\u4e5f\u53ef\u76f4\u63a5\u8bfb\u53d6\u5de5\u4f5c\u533a\u5185\u7684 .mm \u7b49\u6587\u4ef6\u3002\n"
            + "3. \u82e5\u4ecd\u65e0\u6cd5\u83b7\u5f97\u6240\u9700\u4fe1\u606f\uff0c\u8bf7\u660e\u786e\u8bf4\u660e\u7f3a\u4ec0\u4e48\u3002\n"
            + "4. \u4e0b\u65b9\u300c\u5168\u5c40\u5b89\u6392\u4e0e\u5173\u6ce8\u300d\u6c47\u603b\u4e86\u7528\u6237\u5728\u6240\u6709\u5bfc\u56fe\u4e2d\u7684\u63d0\u9192\u3001\u5468\u671f\u63d0\u9192\u3001\u5f85\u529e\u4e0e\u9489\u9009\u8282\u70b9\u3002\u7528\u6237\u5728\u4efb\u610f\u5bfc\u56fe\u4e2d\u53ef\u8be2\u95ee\u201c\u6211\u6709\u4ec0\u4e48\u5b89\u6392\u201d\u201c\u4eca\u5929\u8981\u505a\u4ec0\u4e48\u201d\u7b49\u95ee\u9898\uff0c\u8bf7\u57fa\u4e8e\u8be5\u6c47\u603b\u56de\u7b54\u3002\n"
            + "5. \u300c\u5bfc\u56fe\u5e93\u6587\u4ef6\u7d22\u5f15\u300d\u5217\u51fa\u5de5\u4f5c\u533a\u5185\u6587\u4ef6\uff08\u8def\u5f84\u76f8\u5bf9 @ \u6839\u76ee\u5f55\u538b\u7f29\u5217\u51fa\uff09\u3002\u7528\u6237\u95ee\u9898\u6d89\u53ca\u627e\u6587\u4ef6\u3001\u5173\u8054\u6750\u6599\u65f6\uff0c\u8bf7\u4ece\u7d22\u5f15\u4e2d\u9009\u62e9\u8def\u5f84\uff0c\u7ed3\u5408 {{REFERENCED_FILES}} \u4e0e {{MAP_CONTENT}} \u5206\u6790\u3002\n\n"
            + "\u5f53\u524d\u601d\u7ef4\u5bfc\u56fe\u8def\u5f84\uff1a{{MAP_PATH}}\n"
            + "\u5f53\u524d\u601d\u7ef4\u5bfc\u56fe\u6807\u9898\uff1a{{MAP_TITLE}}\n\n"
            + "\u5f53\u524d\u9009\u4e2d\u8282\u70b9\uff08\u53ca\u5b50\u6811\uff09\uff1a\n{{SELECTED_NODE}}\n\n"
            + "\u601d\u7ef4\u5bfc\u56fe\u4e0e\u5173\u8054\u4e0a\u4e0b\u6587\uff1a\n{{MAP_CONTENT}}\n\n"
            + "\u5173\u8054\u6587\u4ef6\u6458\u8981\uff1a\n{{REFERENCED_FILES}}\n\n"
            + "\u5bfc\u56fe\u5e93\u6587\u4ef6\u7d22\u5f15\uff08\u538b\u7f29\u8def\u5f84\uff0c\u4fbf\u4e8e\u5b9a\u4f4d\u5173\u8054\u6587\u4ef6\uff09\uff1a\n{{WORKSPACE_FILE_INDEX}}\n\n"
            + "\u5173\u952e\u8bcd\u53c2\u8003\uff1a\n{{KEYWORDS}}\n\n"
            + "\u5f53\u524d\u5339\u914d\u7684\u5173\u952e\u8bcd\u89c4\u5219\uff1a\n{{ACTIVE_KEYWORD_RULES}}\n\n"
            + "\u5168\u5c40\u5b89\u6392\u4e0e\u5173\u6ce8\uff08\u5168\u90e8\u63d0\u9192 / \u5468\u671f\u63d0\u9192 / \u5168\u90e8\u5f85\u529e / \u6211\u7684\u9489\u9009\uff0c\u8de8\u6240\u6709\u5bfc\u56fe\u6c47\u603b\uff09\uff1a\n{{WORKSPACE_PLANS}}\n\n"
            + "\u5386\u53f2\u5bf9\u8bdd\uff1a\n{{CHAT_HISTORY}}\n\n"
            + "\u7528\u6237\u95ee\u9898\uff1a\n{{USER_QUESTION}}";

    private static final String DEFAULT_SUBNODES_TEMPLATE =
            "\u8bf7\u7ed3\u5408\u4ee5\u4e0b\u601d\u7ef4\u5bfc\u56fe\u5185\u5bb9\u548c\u4e3b\u9898\uff0c\u751f\u6210\u5b50\u8282\u70b9\u6807\u9898\u5217\u8868\uff08\u6bcf\u884c\u4e00\u4e2a\uff0c\u4e0d\u8981\u89e3\u91ca\uff09\u3002\n\n"
            + "\u601d\u7ef4\u5bfc\u56fe\u8def\u5f84\uff1a{{MAP_PATH}}\n"
            + "\u601d\u7ef4\u5bfc\u56fe\u6807\u9898\uff1a{{MAP_TITLE}}\n"
            + "\u601d\u7ef4\u5bfc\u56fe\u5185\u5bb9\uff1a\n{{MAP_CONTENT}}\n\n"
            + "\u4e3b\u9898\uff1a{{USER_QUESTION}}";

    private static final String DEFAULT_HELP_TEXT =
            "\u7eff\u8272\u80cc\u666f\u8282\u70b9\u4e3a\u7cfb\u7edf\u8282\u70b9\uff0c\u4e0d\u53ef\u5220\u9664\uff1a\n"
            + AiPromptTemplateNodes.getProtectedNodeDescription() + "\n\n"
            + "\u652f\u6301\u5360\u4f4d\u7b26\uff1a{{MAP_PATH}}\u3001{{MAP_TITLE}}\u3001{{SELECTED_NODE}}\u3001{{MAP_CONTENT}}\u3001{{REFERENCED_FILES}}\u3001{{WORKSPACE_FILE_INDEX}}\u3001"
            + "{{KEYWORDS}}\u3001{{ACTIVE_KEYWORD_RULES}}\u3001{{WORKSPACE_PLANS}}\u3001{{CHAT_HISTORY}}\u3001{{USER_QUESTION}}\u3002\n"
            + "\u5173\u952e\u8bcd\u5e93\u4e0b\u7684\u5b50\u8282\u70b9\u4f5c\u4e3a\u89c4\u5219\uff1b\u7528\u6237\u63d0\u95ee\u5339\u914d\u5173\u952e\u8bcd\u540d\u79f0\u65f6\uff0c\u5b50\u8282\u70b9\u5185\u5bb9\u4f1a\u6ce8\u5165 {{ACTIVE_KEYWORD_RULES}}\u3002\n"
            + "{{WORKSPACE_FILE_INDEX}} \u5217\u51fa\u5bfc\u56fe\u5e93\u5185\u6240\u6709\u6587\u4ef6\uff08\u8def\u5f84\u76f8\u5bf9 @ \u6839\u76ee\u5f55\u538b\u7f29\uff09\uff0c\u4fbf\u4e8e\u5b9a\u4f4d\u5173\u8054 .mm \u53ca\u5176\u4ed6\u6587\u4ef6\u3002\n"
            + "{{WORKSPACE_PLANS}} \u81ea\u52a8\u6c47\u603b\u5168\u90e8\u63d0\u9192\u3001\u5468\u671f\u63d0\u9192\u3001\u5168\u90e8\u5f85\u529e\u3001\u6211\u7684\u9489\u9009\u56db\u4e2a\u6807\u7b7e\u9875\u5185\u5bb9\uff0c\u542b\u5bfc\u56fe\u8def\u5f84\u4e0e\u8282\u70b9 ID\u3002\n"
            + "Docear \u4f1a\u81ea\u52a8\u8bfb\u53d6\u5bfc\u56fe\u94fe\u63a5\u548c\u8def\u5f84\u4e2d\u7684\u6587\u4ef6\uff0c\u53d1\u9001\u524d\u81ea\u52a8\u8131\u654f\u654f\u611f\u4fe1\u606f\u3002\n"
            + "\u5728\u300c\u5173\u952e\u8bcd\u5e93\u300d\u4e0b\u6dfb\u52a0\u5b50\u8282\u70b9\u5373\u53ef\u81ea\u5b9a\u4e49\u5173\u952e\u8bcd\uff08\u5173\u952e\u8bcd\u5b50\u8282\u70b9\u53ef\u5220\u6539\uff09\u3002\n"
            + "\u4fee\u6539\u540e\u4fdd\u5b58\u6587\u4ef6\u5373\u53ef\u751f\u6548\u3002";

    private final File templateFile;
    private long cachedModified = -1L;
    private String cachedChatTemplate;
    private String cachedSubNodesTemplate;

    public AiPromptTemplateStore() {
        this(new File(new DocearAiConfig().getPromptTemplateFile()));
    }

    public AiPromptTemplateStore(File templateFile) {
        this.templateFile = templateFile;
    }

    public File getTemplateFile() {
        return templateFile;
    }

    public void ensureTemplateFileExists() {
        try {
            File parent = templateFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (!templateFile.exists()) {
                migrateLegacyTemplateIfPresent();
            }
            if (!templateFile.exists()) {
                writeDefaultTemplateFile();
                LogUtils.info("Created default AI prompt template file: " + templateFile.getAbsolutePath());
            } else {
                ensureRequiredStructure();
            }
        } catch (Exception e) {
            LogUtils.severe("Failed to ensure AI prompt template file: " + e.getMessage());
        }
    }

    private void migrateLegacyTemplateIfPresent() {
        DocearAiConfig config = new DocearAiConfig();
        File legacy = new File(config.getAiHomeDirectory(), "AI\u63d0\u793a\u8bcd.mm");
        if (!legacy.exists() || legacy.getAbsolutePath().equalsIgnoreCase(templateFile.getAbsolutePath())) {
            return;
        }
        try {
            copyFile(legacy, templateFile);
            LogUtils.info("Migrated AI prompt template from: " + legacy.getAbsolutePath());
        } catch (Exception e) {
            LogUtils.warn("Failed to migrate legacy prompt template: " + e.getMessage());
        }
    }

    private void copyFile(File source, File target) throws Exception {
        BufferedReader reader = null;
        OutputStreamWriter writer = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(source), "UTF-8"));
            writer = new OutputStreamWriter(new FileOutputStream(target), "UTF-8");
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.write('\n');
            }
            writer.flush();
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
        }
    }

    public void reloadIfChanged() {
        ensureTemplateFileExists();
        long modified = templateFile.exists() ? templateFile.lastModified() : 0L;
        if (modified == cachedModified) {
            return;
        }
        cachedChatTemplate = readNodeTemplate(AiPromptTemplateNodes.ID_CHAT, AiPromptTemplateNodes.NODE_CHAT);
        if (cachedChatTemplate == null || cachedChatTemplate.trim().length() == 0) {
            cachedChatTemplate = DEFAULT_CHAT_TEMPLATE;
        }
        cachedSubNodesTemplate = readNodeTemplate(AiPromptTemplateNodes.ID_SUBNODES, AiPromptTemplateNodes.NODE_SUBNODES);
        if (cachedSubNodesTemplate == null || cachedSubNodesTemplate.trim().length() == 0) {
            cachedSubNodesTemplate = DEFAULT_SUBNODES_TEMPLATE;
        }
        cachedModified = modified;
        LogUtils.info("AI prompt templates reloaded from: " + templateFile.getAbsolutePath());
    }

    public String getChatTemplate() {
        reloadIfChanged();
        return cachedChatTemplate != null ? cachedChatTemplate : DEFAULT_CHAT_TEMPLATE;
    }

    public String getSubNodesTemplate() {
        reloadIfChanged();
        return cachedSubNodesTemplate != null ? cachedSubNodesTemplate : DEFAULT_SUBNODES_TEMPLATE;
    }

    public String getKeywordsText() {
        reloadIfChanged();
        String keywords = readKeywordsList();
        if (keywords == null || keywords.trim().length() == 0) {
            return "\uff08\u672a\u914d\u7f6e\u5173\u952e\u8bcd\uff09";
        }
        return keywords;
    }

    public List<String> getKeywordLabels() {
        reloadIfChanged();
        List<String> labels = new ArrayList<String>();
        if (!templateFile.exists()) {
            return labels;
        }
        try {
            Document document = parseDocument();
            Element keywordsRoot = findNodeById(document.getDocumentElement(), AiPromptTemplateNodes.ID_KEYWORDS);
            if (keywordsRoot == null) {
                keywordsRoot = findNodeByText(document.getDocumentElement(), AiPromptTemplateNodes.NODE_KEYWORDS);
            }
            if (keywordsRoot == null) {
                return labels;
            }
            collectChildKeywordLabels(keywordsRoot, labels);
        } catch (Exception e) {
            LogUtils.warn("Failed to read keyword labels from " + templateFile + ": " + e.getMessage());
        }
        return labels;
    }

    /**
     * 根据用户问题匹配关键词，返回该关键词下子节点定义的规则文本。
     */
    public String getActiveKeywordRules(String userQuestion) {
        reloadIfChanged();
        KeywordDefinition match = findBestKeywordMatch(userQuestion);
        if (match == null || match.rules.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\u5173\u952e\u8bcd\uff1a").append(match.label);
        for (int i = 0; i < match.rules.size(); i++) {
            sb.append('\n').append("- ").append(match.rules.get(i));
        }
        return sb.toString();
    }

    private static final class KeywordDefinition {
        private final String label;
        private final List<String> rules = new ArrayList<String>();

        private KeywordDefinition(String label) {
            this.label = label;
        }
    }

    public void openTemplateFile() {
        ensureTemplateFileExists();
        try {
            org.freeplane.features.mode.Controller.getCurrentController().getViewController()
                    .openDocument(templateFile.toURI());
        } catch (Exception e) {
            try {
                java.awt.Desktop.getDesktop().open(templateFile);
            } catch (Exception desktopError) {
                LogUtils.severe("Failed to open prompt template file: " + desktopError.getMessage());
            }
        }
    }

    private String readKeywordsList() {
        if (!templateFile.exists()) {
            return "";
        }
        try {
            Document document = parseDocument();
            Element keywordsRoot = findNodeById(document.getDocumentElement(), AiPromptTemplateNodes.ID_KEYWORDS);
            if (keywordsRoot == null) {
                keywordsRoot = findNodeByText(document.getDocumentElement(), AiPromptTemplateNodes.NODE_KEYWORDS);
            }
            if (keywordsRoot == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            collectChildKeywordLines(keywordsRoot, sb, 0);
            return sb.toString().trim();
        } catch (Exception e) {
            LogUtils.warn("Failed to read keywords from " + templateFile + ": " + e.getMessage());
            return "";
        }
    }

    private void collectChildKeywordLabels(Element parent, List<String> labels) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) {
                continue;
            }
            Element childElement = (Element) child;
            if (!"node".equals(childElement.getTagName())) {
                continue;
            }
            String text = extractNodeContent(childElement);
            if (text.length() == 0) {
                text = childElement.getAttribute("TEXT");
            }
            text = normalizeText(text);
            if (text.length() > 0) {
                labels.add(text);
            }
        }
    }

    private void collectChildKeywordLines(Element parent, StringBuilder sb, int depth) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) {
                continue;
            }
            Element childElement = (Element) child;
            if (!"node".equals(childElement.getTagName())) {
                continue;
            }
            String text = extractNodeContent(childElement);
            if (text.length() == 0) {
                text = childElement.getAttribute("TEXT");
            }
            text = normalizeText(text);
            if (text.length() > 0) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                appendIndentedKeywordLine(sb, depth, text);
                collectChildKeywordLines(childElement, sb, depth + 1);
            }
        }
    }

    private void appendIndentedKeywordLine(StringBuilder sb, int depth, String text) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        sb.append("- ").append(text);
    }

    private List<KeywordDefinition> loadKeywordDefinitions() {
        List<KeywordDefinition> definitions = new ArrayList<KeywordDefinition>();
        if (!templateFile.exists()) {
            return definitions;
        }
        try {
            Document document = parseDocument();
            Element keywordsRoot = findNodeById(document.getDocumentElement(), AiPromptTemplateNodes.ID_KEYWORDS);
            if (keywordsRoot == null) {
                keywordsRoot = findNodeByText(document.getDocumentElement(), AiPromptTemplateNodes.NODE_KEYWORDS);
            }
            if (keywordsRoot == null) {
                return definitions;
            }
            NodeList children = keywordsRoot.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (!(child instanceof Element)) {
                    continue;
                }
                Element childElement = (Element) child;
                if (!"node".equals(childElement.getTagName())) {
                    continue;
                }
                String label = extractNodeContent(childElement);
                if (label.length() == 0) {
                    label = childElement.getAttribute("TEXT");
                }
                label = normalizeText(label);
                if (label.length() == 0) {
                    continue;
                }
                KeywordDefinition definition = new KeywordDefinition(label);
                collectKeywordRuleTexts(childElement, definition.rules);
                definitions.add(definition);
            }
        } catch (Exception e) {
            LogUtils.warn("Failed to load keyword definitions: " + e.getMessage());
        }
        return definitions;
    }

    private void collectKeywordRuleTexts(Element keywordNode, List<String> rules) {
        NodeList children = keywordNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) {
                continue;
            }
            Element childElement = (Element) child;
            if (!"node".equals(childElement.getTagName())) {
                continue;
            }
            String text = extractNodeContent(childElement);
            if (text.length() == 0) {
                text = childElement.getAttribute("TEXT");
            }
            text = normalizeText(text);
            if (text.length() > 0) {
                rules.add(text);
            }
            collectKeywordRuleTexts(childElement, rules);
        }
    }

    private KeywordDefinition findBestKeywordMatch(String userQuestion) {
        if (userQuestion == null || userQuestion.trim().length() == 0) {
            return null;
        }
        String question = normalizeText(userQuestion);
        List<KeywordDefinition> definitions = loadKeywordDefinitions();
        KeywordDefinition best = null;
        int bestLength = 0;
        for (int i = 0; i < definitions.size(); i++) {
            KeywordDefinition definition = definitions.get(i);
            if (matchesKeywordQuestion(question, definition.label)) {
                if (definition.label.length() > bestLength) {
                    best = definition;
                    bestLength = definition.label.length();
                }
            }
        }
        return best;
    }

    private boolean matchesKeywordQuestion(String question, String keywordLabel) {
        if (keywordLabel == null || keywordLabel.length() == 0) {
            return false;
        }
        if (question.equals(keywordLabel)) {
            return true;
        }
        if (question.startsWith(keywordLabel)) {
            return true;
        }
        return question.contains(keywordLabel);
    }

    private String readNodeTemplate(String nodeId, String nodeTitle) {
        if (!templateFile.exists()) {
            return null;
        }
        try {
            Document document = parseDocument();
            Element target = findNodeById(document.getDocumentElement(), nodeId);
            if (target == null) {
                target = findNodeByText(document.getDocumentElement(), nodeTitle);
            }
            if (target == null) {
                return null;
            }
            return extractNodeContent(target);
        } catch (Exception e) {
            LogUtils.warn("Failed to read prompt template from " + templateFile + ": " + e.getMessage());
            return null;
        }
    }

    private Document parseDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(templateFile);
    }

    private Element findNodeById(Element root, String id) {
        if (root == null || id == null) {
            return null;
        }
        if (id.equals(root.getAttribute("ID"))) {
            return root;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element found = findNodeById((Element) child, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private Element findNodeByText(Element root, String text) {
        if (root == null) {
            return null;
        }
        String nodeText = root.getAttribute("TEXT");
        if (text.equals(nodeText)) {
            return root;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element found = findNodeByText((Element) child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String extractNodeContent(Element node) {
        NodeList richContents = node.getElementsByTagName("richcontent");
        if (richContents.getLength() > 0) {
            String raw = collectText(richContents.item(0));
            return normalizeText(stripHtml(raw));
        }
        String text = node.getAttribute("TEXT");
        if (text != null && text.trim().length() > 0) {
            return normalizeText(text);
        }
        return "";
    }

    private String collectText(Node node) {
        if (node == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                sb.append(child.getNodeValue());
            } else {
                sb.append(collectText(child));
            }
        }
        return sb.toString();
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("(?s)<br\\s*/?>", "\n");
        text = text.replaceAll("(?s)</p>", "\n");
        text = text.replaceAll("<[^>]+>", "");
        text = text.replace("&nbsp;", " ");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&amp;", "&");
        return text;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private void ensureRequiredStructure() {
        if (!templateFile.exists()) {
            return;
        }
        try {
            String content = readFileAsString(templateFile);
            String updated = content;
            updated = ensureRootBackgroundColor(updated);
            if (!updated.contains(AiPromptTemplateNodes.ID_CHAT)) {
                updated = insertBeforeRootClose(updated, buildSystemNodesXml());
            } else {
                updated = ensureNodeBackgroundById(updated, AiPromptTemplateNodes.ID_CHAT);
                updated = ensureNodeBackgroundById(updated, AiPromptTemplateNodes.ID_SUBNODES);
                updated = ensureNodeBackgroundById(updated, AiPromptTemplateNodes.ID_KEYWORDS);
                updated = ensureNodeBackgroundById(updated, AiPromptTemplateNodes.ID_HELP);
            }
            if (!updated.equals(content)) {
                writeFileAsString(templateFile, updated);
                cachedModified = -1L;
            }
        } catch (Exception e) {
            LogUtils.warn("Failed to ensure prompt template structure: " + e.getMessage());
        }
    }

    private String readFileAsString(File file) throws Exception {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            return sb.toString();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private void writeFileAsString(File file, String content) throws Exception {
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write(content);
            writer.flush();
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private String ensureRootBackgroundColor(String content) {
        String rootMarker = "TEXT=\"AI&#x63d0;&#x793a;&#x8bcd;\"";
        int index = content.indexOf(rootMarker);
        if (index < 0) {
            rootMarker = "TEXT=\"" + AiXmlEncoding.encodeXmlAttribute(AiPromptTemplateNodes.ROOT_TEXT) + "\"";
            index = content.indexOf(rootMarker);
        }
        if (index < 0) {
            return content;
        }
        int tagEnd = content.indexOf('>', index);
        if (tagEnd < 0) {
            return content;
        }
        String tag = content.substring(index, tagEnd);
        if (tag.contains("BACKGROUND_COLOR=")) {
            return content;
        }
        return content.substring(0, tagEnd)
                + " BACKGROUND_COLOR=\"" + AiPromptTemplateNodes.PROTECTED_BACKGROUND_COLOR + "\""
                + content.substring(tagEnd);
    }

    private String ensureNodeBackgroundById(String content, String nodeId) {
        String idAttr = "ID=\"" + nodeId + "\"";
        int index = content.indexOf(idAttr);
        if (index < 0) {
            return content;
        }
        int tagStart = content.lastIndexOf('<', index);
        int tagEnd = content.indexOf('>', index);
        if (tagStart < 0 || tagEnd < 0) {
            return content;
        }
        String tag = content.substring(tagStart, tagEnd);
        if (tag.contains("BACKGROUND_COLOR=")) {
            return content;
        }
        return content.substring(0, tagEnd)
                + " BACKGROUND_COLOR=\"" + AiPromptTemplateNodes.PROTECTED_BACKGROUND_COLOR + "\""
                + content.substring(tagEnd);
    }

    private String insertBeforeRootClose(String content, String nodesXml) {
        int mapClose = content.lastIndexOf("</map>");
        if (mapClose < 0) {
            return content + nodesXml;
        }
        int rootClose = content.lastIndexOf("</node>", mapClose);
        if (rootClose < 0) {
            return content;
        }
        return content.substring(0, rootClose) + nodesXml + content.substring(rootClose);
    }

    private String buildSystemNodesXml() {
        StringBuilder xml = new StringBuilder();
        appendTemplateNode(xml, AiPromptTemplateNodes.NODE_CHAT, DEFAULT_CHAT_TEMPLATE, AiPromptTemplateNodes.ID_CHAT);
        appendTemplateNode(xml, AiPromptTemplateNodes.NODE_SUBNODES, DEFAULT_SUBNODES_TEMPLATE, AiPromptTemplateNodes.ID_SUBNODES);
        appendKeywordsNode(xml);
        appendTemplateNode(xml, AiPromptTemplateNodes.NODE_HELP, DEFAULT_HELP_TEXT, AiPromptTemplateNodes.ID_HELP);
        return xml.toString();
    }

    private void writeDefaultTemplateFile() throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<map version=\"1.0.1\">\n");
        xml.append("<node TEXT=\"").append(AiXmlEncoding.encodeXmlAttribute(AiPromptTemplateNodes.ROOT_TEXT))
                .append("\" ID=\"").append(AiPromptTemplateNodes.ID_ROOT)
                .append("\" BACKGROUND_COLOR=\"").append(AiPromptTemplateNodes.PROTECTED_BACKGROUND_COLOR)
                .append("\" CREATED=\"0\" MODIFIED=\"0\">\n");
        appendTemplateNode(xml, AiPromptTemplateNodes.NODE_CHAT, DEFAULT_CHAT_TEMPLATE, AiPromptTemplateNodes.ID_CHAT);
        appendTemplateNode(xml, AiPromptTemplateNodes.NODE_SUBNODES, DEFAULT_SUBNODES_TEMPLATE, AiPromptTemplateNodes.ID_SUBNODES);
        appendKeywordsNode(xml);
        appendTemplateNode(xml, AiPromptTemplateNodes.NODE_HELP, DEFAULT_HELP_TEXT, AiPromptTemplateNodes.ID_HELP);
        xml.append("</node>\n");
        xml.append("</map>\n");

        FileOutputStream fos = new FileOutputStream(templateFile);
        try {
            OutputStreamWriter writer = new OutputStreamWriter(fos, Charset.forName("UTF-8"));
            writer.write(xml.toString());
            writer.flush();
            writer.close();
        } finally {
            fos.close();
        }
        cachedModified = -1L;
    }

    private void appendKeywordsNode(StringBuilder xml) {
        xml.append("<node TEXT=\"").append(AiXmlEncoding.encodeXmlAttribute(AiPromptTemplateNodes.NODE_KEYWORDS))
                .append("\" ID=\"").append(AiPromptTemplateNodes.ID_KEYWORDS)
                .append("\" BACKGROUND_COLOR=\"").append(AiPromptTemplateNodes.PROTECTED_BACKGROUND_COLOR)
                .append("\" CREATED=\"0\" MODIFIED=\"0\">\n");
        appendKeywordChild(xml, "\u601d\u7ef4\u5bfc\u56fe\u5206\u6790", "ID_AI_KW_1");
        appendKeywordChild(xml, "\u6458\u8981\u603b\u7ed3", "ID_AI_KW_2");
        appendKeywordChild(xml, "\u6269\u5c55\u5b50\u4e3b\u9898", "ID_AI_KW_3");
        xml.append("</node>\n");
    }

    private void appendKeywordChild(StringBuilder xml, String text, String id) {
        xml.append("<node TEXT=\"").append(AiXmlEncoding.encodeXmlAttribute(text)).append("\" ID=\"").append(id)
                .append("\" CREATED=\"0\" MODIFIED=\"0\"/>\n");
    }

    private void appendTemplateNode(StringBuilder xml, String title, String content, String id) {
        xml.append("<node TEXT=\"").append(AiXmlEncoding.encodeXmlAttribute(title)).append("\" ID=\"").append(id)
                .append("\" BACKGROUND_COLOR=\"").append(AiPromptTemplateNodes.PROTECTED_BACKGROUND_COLOR)
                .append("\" CREATED=\"0\" MODIFIED=\"0\">\n");
        xml.append("<richcontent TYPE=\"NODE\">\n");
        xml.append("<html>\n");
        xml.append("<body>\n");
        xml.append("<p>").append(escapeXml(content).replace("\n", "</p>\n<p>")).append("</p>\n");
        xml.append("</body>\n");
        xml.append("</html>\n");
        xml.append("</richcontent>\n");
        xml.append("</node>\n");
    }

    private String escapeXml(String value) {
        return AiXmlEncoding.encodeXmlText(value);
    }
}
