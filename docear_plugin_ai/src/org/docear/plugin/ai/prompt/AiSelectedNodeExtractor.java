package org.docear.plugin.ai.prompt;

import java.net.URI;
import java.util.List;

import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.text.TextController;

/**
 * 提取当前选中节点及其子树内容，供 {{SELECTED_NODE}} 占位符使用。
 */
public final class AiSelectedNodeExtractor {

    private static final int MAX_CONTENT_LENGTH = 20000;

    private AiSelectedNodeExtractor() {
    }

    public static String extract(NodeModel node) {
        if (node == null) {
            return "\uff08\u672a\u9009\u4e2d\u8282\u70b9\uff09";
        }
        String title = extractTitle(node);
        StringBuilder sb = new StringBuilder();
        if (title.length() > 0) {
            sb.append("\u9009\u4e2d\u8282\u70b9\uff1a").append(title).append('\n');
        }
        URI link = NodeLinks.getValidLink(node);
        if (link != null) {
            sb.append("\u8282\u70b9\u94fe\u63a5\uff1a").append(link.toString()).append('\n');
        }
        sb.append("\n\u8282\u70b9\u53ca\u5b50\u6811\u5185\u5bb9\uff1a\n");
        appendNode(node, sb, 0);
        return truncate(sb.toString());
    }

    public static String extractTitle(NodeModel node) {
        if (node == null) {
            return "";
        }
        String text = null;
        try {
            TextController controller = TextController.getController();
            if (controller != null) {
                text = controller.getPlainTextContent(node);
            }
        }
        catch (Throwable e) {
            text = null;
        }
        if (text == null || text.length() == 0) {
            Object user = node.getUserObject();
            text = user == null ? "" : String.valueOf(user);
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static void appendNode(NodeModel node, StringBuilder sb, int depth) {
        if (node == null) {
            return;
        }
        String text = TextController.getController().getPlainTextContent(node);
        if (text == null) {
            text = "";
        }
        text = text.trim();
        URI link = NodeLinks.getValidLink(node);
        if (text.length() > 0 || link != null) {
            for (int i = 0; i < depth; i++) {
                sb.append("  ");
            }
            sb.append("- ");
            if (text.length() > 0) {
                sb.append(text);
            }
            if (link != null) {
                if (text.length() > 0) {
                    sb.append(' ');
                }
                sb.append("[\u94fe\u63a5: ").append(link.toString()).append(']');
            }
            sb.append('\n');
        }
        List<NodeModel> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            appendNode(children.get(i), sb, depth + 1);
        }
    }

    private static String truncate(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= MAX_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_CONTENT_LENGTH)
                + "\n\n[\u8282\u70b9\u5185\u5bb9\u8fc7\u957f\uff0c\u5df2\u622a\u65ad\u81f3 " + MAX_CONTENT_LENGTH + " \u5b57\u7b26]";
    }
}
