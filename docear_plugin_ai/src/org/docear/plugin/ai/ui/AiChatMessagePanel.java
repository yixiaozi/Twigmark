package org.docear.plugin.ai.ui;

import org.freeplane.core.ui.theme.DocearUiTheme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.View;

import org.docear.plugin.ai.chat.AiChatMessage;

/**
 * 单条聊天消息块（用户 / AI）。
 */
public class AiChatMessagePanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int REFLOW_MAX_HEIGHT = 100000;

    public interface MessageActionListener {
        void onInsertAsChild(String content);

        void onInsertAsSibling(String content);
    }

    private final AiChatMessage.Role role;
    private final JTextPane aiContentPane;
    private final JTextArea userContentArea;
    private final JPanel actionPanel;
    private final JPanel thinkingContainer;
    private final JButton thinkingToggle;
    private final JTextArea thinkingArea;
    private String plainContent = "";
    private String displayAnswer = "";
    private boolean thinkingExpanded;
    private int lastViewportWidth = -1;

    public AiChatMessagePanel(AiChatMessage.Role role) {
        this.role = role;
        setLayout(new BorderLayout(4, 2));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));

        String title = role == AiChatMessage.Role.USER ? "\u4f60" : "AI";
        Color bg = role == AiChatMessage.Role.USER ? new Color(0xE0, 0xF2, 0xFE) : DocearUiTheme.ACCENT_WASH;
        setBackground(bg);
        setOpaque(true);

        JLabel header = new JLabel(title);
        header.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 4));
        header.setFont(header.getFont().deriveFont(java.awt.Font.BOLD, 11f));

        userContentArea = new JTextArea();
        userContentArea.setEditable(false);
        userContentArea.setLineWrap(true);
        userContentArea.setWrapStyleWord(true);
        userContentArea.setBackground(bg);
        userContentArea.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        userContentArea.setColumns(0);
        userContentArea.setRows(0);

        aiContentPane = new JTextPane() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        aiContentPane.setEditable(false);
        aiContentPane.setContentType("text/html");
        aiContentPane.setBackground(bg);
        aiContentPane.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionPanel.setOpaque(false);
        thinkingContainer = new JPanel(new BorderLayout(0, 2));
        thinkingContainer.setOpaque(false);
        thinkingToggle = new JButton("\u25b6 \u601d\u8003\u8fc7\u7a0b");
        thinkingToggle.setHorizontalAlignment(SwingConstants.LEFT);
        thinkingToggle.setBorderPainted(false);
        thinkingToggle.setContentAreaFilled(false);
        thinkingToggle.setFocusPainted(false);
        thinkingToggle.setFont(thinkingToggle.getFont().deriveFont(10f));
        thinkingToggle.setForeground(DocearUiTheme.TEXT_MUTED);
        thinkingToggle.setVisible(false);
        thinkingArea = new JTextArea();
        thinkingArea.setEditable(false);
        thinkingArea.setLineWrap(true);
        thinkingArea.setWrapStyleWord(true);
        thinkingArea.setBackground(DocearUiTheme.SURFACE_SOFT);
        thinkingArea.setForeground(DocearUiTheme.TEXT_MUTED);
        thinkingArea.setFont(thinkingArea.getFont().deriveFont(10f));
        thinkingArea.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 6));
        thinkingArea.setVisible(false);
        thinkingToggle.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                thinkingExpanded = !thinkingExpanded;
                thinkingArea.setVisible(thinkingExpanded);
                updateThinkingToggleLabel();
                reflowContent(lastViewportWidth > 0 ? lastViewportWidth : resolveViewportWidth());
                requestLayoutFit();
                revalidate();
                repaint();
            }
        });
        thinkingContainer.add(thinkingToggle, BorderLayout.NORTH);
        thinkingContainer.add(thinkingArea, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        if (role == AiChatMessage.Role.USER) {
            add(userContentArea, BorderLayout.CENTER);
        } else {
            JPanel centerPanel = new JPanel(new BorderLayout(0, 2));
            centerPanel.setOpaque(false);
            centerPanel.add(thinkingContainer, BorderLayout.NORTH);
            centerPanel.add(aiContentPane, BorderLayout.CENTER);
            add(centerPanel, BorderLayout.CENTER);
            buildAssistantActions(null);
            add(actionPanel, BorderLayout.SOUTH);
        }
    }

    private void buildAssistantActions(final MessageActionListener listener) {
        actionPanel.removeAll();
        JButton copyButton = new JButton("\u590d\u5236");
        copyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                copyToClipboard(getPlainContent());
            }
        });
        actionPanel.add(copyButton);

        if (listener != null) {
            JButton childButton = new JButton("\u63d2\u5165\u5b50\u8282\u70b9");
            childButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    listener.onInsertAsChild(getPlainContent());
                }
            });
            JButton siblingButton = new JButton("\u63d2\u5165\u5144\u5f1f\u8282\u70b9");
            siblingButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    listener.onInsertAsSibling(getPlainContent());
                }
            });
            actionPanel.add(childButton);
            actionPanel.add(siblingButton);
        }
        actionPanel.setVisible(role == AiChatMessage.Role.ASSISTANT);
    }

    public void setAssistantActionListener(MessageActionListener listener) {
        if (role == AiChatMessage.Role.ASSISTANT) {
            buildAssistantActions(listener);
            revalidate();
            repaint();
        }
    }

    public void setContent(String content) {
        setAssistantContent(content, true);
    }

    public void setStreamingContent(String content) {
        setAssistantContent(content, false);
    }

    private void setAssistantContent(String content, boolean complete) {
        plainContent = content != null ? content : "";
        if (role == AiChatMessage.Role.USER) {
            userContentArea.setText(plainContent);
            requestLayoutFit();
            return;
        }

        AiCopilotResponseParser.ParsedResponse parsed = AiCopilotResponseParser.parse(plainContent);
        updateThinkingSection(parsed, complete);

        displayAnswer = parsed.getFinalAnswer();
        if (displayAnswer.length() == 0 && parsed.hasThinking()) {
            displayAnswer = complete ? "" : "\u751f\u6210\u4e2d...";
        } else if (displayAnswer.length() == 0 && !parsed.hasThinking()) {
            displayAnswer = plainContent;
        }

        if (displayAnswer.length() == 0 && complete && parsed.hasThinking()) {
            displayAnswer = "\uff08\u65e0\u6587\u672c\u56de\u590d\uff09";
        }

        aiContentPane.setText(AiMarkdownRenderer.toHtml(displayAnswer));
        aiContentPane.setCaretPosition(complete ? 0 : aiContentPane.getDocument().getLength());
        requestLayoutFit();
    }

    private void requestLayoutFit() {
        if (lastViewportWidth > 0) {
            fitToWidth(lastViewportWidth);
        }
        final int deferredWidth = lastViewportWidth;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                int width = deferredWidth > 0 ? deferredWidth : resolveViewportWidth();
                if (width > 0) {
                    fitToWidth(width);
                }
            }
        });
    }

    private int resolveViewportWidth() {
        Container parent = getParent();
        while (parent != null) {
            if (parent instanceof JScrollPane) {
                return ((JScrollPane) parent).getViewport().getWidth();
            }
            if (parent.getParent() instanceof JScrollPane) {
                return ((JScrollPane) parent.getParent()).getViewport().getWidth();
            }
            if (parent.getWidth() > 0) {
                return parent.getWidth();
            }
            parent = parent.getParent();
        }
        return getWidth();
    }

    /**
     * 按视口宽度收缩消息高度，避免 BoxLayout 纵向撑满导致无法滚动。
     */
    public void fitToWidth(int viewportWidth) {
        if (viewportWidth <= 0) {
            return;
        }
        lastViewportWidth = viewportWidth;
        reflowContent(viewportWidth);
        updateWrapperSize(viewportWidth);
    }

    private void updateWrapperSize(int viewportWidth) {
        if (viewportWidth <= 0) {
            return;
        }
        int height = computePanelHeight(viewportWidth);
        Dimension size = new Dimension(viewportWidth, height);
        setPreferredSize(size);
        setMaximumSize(size);
        setMinimumSize(new Dimension(viewportWidth, height));
        Container parent = getParent();
        if (parent != null) {
            parent.setPreferredSize(size);
            parent.setMaximumSize(size);
            parent.setMinimumSize(new Dimension(viewportWidth, height));
        }
    }

    private int computePanelHeight(int viewportWidth) {
        int contentWidth = Math.max(viewportWidth - 16, 1);
        reflowContent(viewportWidth);
        int height = getInsets().top + getInsets().bottom + 8;
        Component[] components = getComponents();
        for (int i = 0; i < components.length; i++) {
            Component component = components[i];
            if (!component.isVisible()) {
                continue;
            }
            if (component == userContentArea || component == aiContentPane) {
                height += measureTextComponentHeight(component, contentWidth);
            } else if (component instanceof JPanel) {
                height += measureContainerHeight((Container) component, contentWidth);
            } else {
                height += component.getPreferredSize().height;
            }
        }
        return Math.max(height, 24);
    }

    private int measureContainerHeight(Container container, int contentWidth) {
        int height = 0;
        Component[] components = container.getComponents();
        for (int i = 0; i < components.length; i++) {
            Component component = components[i];
            if (!component.isVisible()) {
                continue;
            }
            if (component instanceof JTextArea || component instanceof JTextPane) {
                height += measureTextComponentHeight(component, contentWidth);
            } else {
                height += component.getPreferredSize().height;
            }
        }
        return height;
    }

    private int measureTextComponentHeight(Component component, int width) {
        if (component instanceof JTextArea) {
            return measureTextAreaHeight((JTextArea) component, width);
        }
        if (component instanceof JTextPane) {
            return measureHtmlPaneHeight((JTextPane) component, width);
        }
        return component.getPreferredSize().height;
    }

    private void reflowContent(int viewportWidth) {
        int width = viewportWidth > 0 ? viewportWidth - 16 : getWidth() - 16;
        if (width <= 0) {
            return;
        }
        if (role == AiChatMessage.Role.USER) {
            applyTextAreaHeight(userContentArea, width);
        } else {
            if (thinkingArea.isVisible()) {
                applyTextAreaHeight(thinkingArea, width);
            } else {
                resetTextAreaHeight(thinkingArea, width);
            }
            applyHtmlPaneHeight(aiContentPane, width);
        }
    }

    private void applyTextAreaHeight(JTextArea area, int width) {
        int height = measureTextAreaHeight(area, width);
        Dimension pref = new Dimension(width, height);
        area.setPreferredSize(pref);
        area.setMinimumSize(pref);
    }

    private int measureTextAreaHeight(JTextArea area, int width) {
        area.setSize(new Dimension(width, REFLOW_MAX_HEIGHT));
        int height = area.getPreferredSize().height;
        if (height <= 0) {
            height = area.getFontMetrics(area.getFont()).getHeight() + 4;
        }
        return height;
    }

    private void resetTextAreaHeight(JTextArea area, int width) {
        Dimension size = new Dimension(width, 0);
        area.setPreferredSize(size);
        area.setMinimumSize(size);
    }

    private void applyHtmlPaneHeight(JTextPane pane, int width) {
        int height = measureHtmlPaneHeight(pane, width);
        Dimension pref = new Dimension(width, height);
        pane.setPreferredSize(pref);
        pane.setMinimumSize(pref);
    }

    private int measureHtmlPaneHeight(JTextPane pane, int width) {
        if (width <= 0) {
            return pane.getFontMetrics(pane.getFont()).getHeight() + 4;
        }
        pane.setSize(new Dimension(width, REFLOW_MAX_HEIGHT));
        try {
            View root = pane.getUI().getRootView(pane);
            if (root != null) {
                root.setSize((float) width, REFLOW_MAX_HEIGHT);
                float span = root.getPreferredSpan(View.Y_AXIS);
                int height = (int) Math.ceil(span);
                if (height > 0) {
                    return height + 4;
                }
            }
        } catch (Exception ignored) {
        }
        int height = pane.getPreferredSize().height;
        if (height <= 0) {
            height = pane.getFontMetrics(pane.getFont()).getHeight() + 4;
        }
        return height;
    }

    private void updateThinkingSection(AiCopilotResponseParser.ParsedResponse parsed, boolean complete) {
        if (!parsed.hasThinking()) {
            thinkingToggle.setVisible(false);
            thinkingArea.setVisible(false);
            thinkingArea.setText("");
            return;
        }

        thinkingToggle.setVisible(true);
        thinkingArea.setText(parsed.getThinkingLog());
        thinkingExpanded = false;
        thinkingArea.setVisible(false);
        updateThinkingToggleLabel();
    }

    private void updateThinkingToggleLabel() {
        AiCopilotResponseParser.ParsedResponse parsed = AiCopilotResponseParser.parse(plainContent);
        int count = parsed.getThinkingLineCount();
        String prefix = thinkingExpanded ? "\u25bc " : "\u25b6 ";
        thinkingToggle.setText(prefix + "\u601d\u8003\u8fc7\u7a0b (" + count + "\u884c)");
    }

    public void appendStreamingChunk(String chunk) {
        if (role != AiChatMessage.Role.ASSISTANT || chunk == null) {
            return;
        }
        plainContent = plainContent + chunk;
        final String current = plainContent;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setStreamingContent(current);
            }
        });
    }

    public String getPlainContent() {
        if (role == AiChatMessage.Role.ASSISTANT && displayAnswer != null && displayAnswer.length() > 0
                && !"\u751f\u6210\u4e2d...".equals(displayAnswer)) {
            return displayAnswer;
        }
        return plainContent;
    }

    public boolean isAssistant() {
        return role == AiChatMessage.Role.ASSISTANT;
    }

    public static JScrollPane wrap(AiChatMessagePanel panel) {
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    private static void copyToClipboard(String text) {
        if (text == null) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }
}
