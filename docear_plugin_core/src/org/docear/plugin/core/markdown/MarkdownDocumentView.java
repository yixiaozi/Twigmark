package org.docear.plugin.core.markdown;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import org.freeplane.core.util.Compat;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.IDocumentTabView;
import org.freeplane.main.application.DocumentTabSupport;

/**
 * Markdown document tab: edit source or preview rendered HTML. Saves back to the {@code .md} file.
 */
public final class MarkdownDocumentView extends JPanel implements IDocumentTabView {

	private static final long serialVersionUID = 1L;
	private static final String CARD_EDIT = "edit";
	private static final String CARD_PREVIEW = "preview";

	private final File file;
	private final JTextArea editor;
	private final JEditorPane preview;
	private final CardLayout cards;
	private final JPanel center;
	private final JLayeredPane layered;
	private final JButton modeToggle;
	private boolean previewMode;
	private boolean modified;
	private boolean updatingPreview;
	private String loadedText = "";

	public MarkdownDocumentView(final File file) throws IOException {
		this.file = file;
		setLayout(new BorderLayout());
		setName(file.getName());
		setOpaque(true);
		setBackground(Color.WHITE);

		editor = new JTextArea();
		editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		editor.setLineWrap(true);
		editor.setWrapStyleWord(true);
		editor.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 72));
		editor.setTabSize(4);
		editor.setBackground(Color.WHITE);

		preview = new JEditorPane();
		preview.setEditable(false);
		final HTMLEditorKit kit = new HTMLEditorKit();
		final StyleSheet styles = kit.getStyleSheet();
		styles.addRule("a { color: #0969da; text-decoration: none; background-color: transparent; }");
		styles.addRule("a:link { color: #0969da; background-color: transparent; }");
		styles.addRule("a:visited { color: #8250df; background-color: transparent; }");
		styles.addRule("a:active { color: #0969da; background-color: transparent; }");
		preview.setEditorKit(kit);
		preview.setContentType("text/html");
		preview.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		preview.setBackground(Color.WHITE);
		preview.addHyperlinkListener(new HyperlinkListener() {
			public void hyperlinkUpdate(final HyperlinkEvent e) {
				if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
					openPreviewLink(e);
				}
			}
		});

		cards = new CardLayout();
		center = new JPanel(cards);
		center.setOpaque(true);
		center.setBackground(Color.WHITE);
		final JScrollPane editScroll = new JScrollPane(editor);
		editScroll.setBorder(BorderFactory.createEmptyBorder());
		final JScrollPane previewScroll = new JScrollPane(preview);
		previewScroll.setBorder(BorderFactory.createEmptyBorder());
		center.add(editScroll, CARD_EDIT);
		center.add(previewScroll, CARD_PREVIEW);

		modeToggle = new JButton("预览");
		modeToggle.setFocusable(false);
		modeToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		modeToggle.setToolTipText("切换编辑 / 预览");
		modeToggle.setMargin(new Insets(4, 12, 4, 12));
		modeToggle.setBackground(new Color(246, 248, 250));
		modeToggle.setForeground(new Color(31, 35, 40));
		modeToggle.setBorder(BorderFactory.createCompoundBorder(
		        BorderFactory.createLineBorder(new Color(208, 215, 222)),
		        BorderFactory.createEmptyBorder(3, 10, 3, 10)));
		modeToggle.setOpaque(true);
		modeToggle.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(final ActionEvent e) {
				if (previewMode) {
					showEdit();
				}
				else {
					showPreview();
				}
			}
		});

		layered = new JLayeredPane();
		layered.setOpaque(true);
		layered.setBackground(Color.WHITE);
		layered.add(center, JLayeredPane.DEFAULT_LAYER);
		layered.add(modeToggle, JLayeredPane.PALETTE_LAYER);
		layered.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(final ComponentEvent e) {
				layoutLayers();
			}
		});
		add(layered, BorderLayout.CENTER);

		loadedText = readUtf8(file);
		editor.setText(loadedText);
		editor.setCaretPosition(0);
		editor.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(final DocumentEvent e) {
				markModified();
			}

			public void removeUpdate(final DocumentEvent e) {
				markModified();
			}

			public void changedUpdate(final DocumentEvent e) {
				markModified();
			}
		});

		final KeyStroke saveStroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, Compat.isMacOsX()
		        ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK);
		getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(saveStroke, "md-save");
		getActionMap().put("md-save", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(final ActionEvent e) {
				saveDocument();
			}
		});

		showEdit();
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				layoutLayers();
			}
		});
	}

	private void layoutLayers() {
		final int w = layered.getWidth();
		final int h = layered.getHeight();
		if (w <= 0 || h <= 0) {
			return;
		}
		center.setBounds(0, 0, w, h);
		final Dimension pref = modeToggle.getPreferredSize();
		final int bw = Math.max(pref.width, 56);
		final int bh = Math.max(pref.height, 28);
		modeToggle.setBounds(w - bw - 14, 12, bw, bh);
		modeToggle.setVisible(true);
		layered.revalidate();
		layered.repaint();
	}

	private void openPreviewLink(final HyperlinkEvent e) {
		try {
			if (e.getURL() != null) {
				final String protocol = e.getURL().getProtocol();
				if ("file".equalsIgnoreCase(protocol)) {
					final File local = Compat.urlToFile(e.getURL());
					if (local != null && DocearMarkdownController.isMarkdownFile(local)) {
						DocearMarkdownController.getController().openMarkdownFile(local);
						return;
					}
				}
				Controller.getCurrentController().getViewController().openDocument(e.getURL());
				return;
			}
			final String desc = e.getDescription();
			if (desc == null || desc.length() == 0) {
				return;
			}
			File target = new File(desc);
			if (!target.isAbsolute()) {
				target = new File(file.getParentFile(), desc);
			}
			if (DocearMarkdownController.isMarkdownFile(target) && target.exists()) {
				DocearMarkdownController.getController().openMarkdownFile(target);
			}
			else if (target.exists()) {
				Controller.getCurrentController().getViewController().openDocument(Compat.fileToUrl(target));
			}
		}
		catch (Exception ex) {
			LogUtils.warn("Markdown: open link failed", ex);
		}
	}

	private void showEdit() {
		previewMode = false;
		modeToggle.setText("预览");
		cards.show(center, CARD_EDIT);
		layoutLayers();
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				editor.requestFocusInWindow();
			}
		});
	}

	private void showPreview() {
		previewMode = true;
		modeToggle.setText("编辑");
		refreshPreview();
		cards.show(center, CARD_PREVIEW);
		layoutLayers();
	}

	private void refreshPreview() {
		if (updatingPreview) {
			return;
		}
		updatingPreview = true;
		try {
			preview.setText(MarkdownHtmlRenderer.toHtml(editor.getText()));
			preview.setCaretPosition(0);
		}
		finally {
			updatingPreview = false;
		}
	}

	private void markModified() {
		if (!modified) {
			modified = true;
			DocumentTabSupport.refreshTabTitles();
		}
	}

	public File getFile() {
		return file;
	}

	public String getTabTitle() {
		return (modified ? "*" : "") + file.getName();
	}

	public Component getViewportComponent() {
		return this;
	}

	public Component getTabKey() {
		return this;
	}

	public String getTabAssignmentKey() {
		try {
			return file.getCanonicalFile().getAbsolutePath().replace('\\', '/');
		}
		catch (Exception e) {
			return file.getAbsolutePath().replace('\\', '/');
		}
	}

	public void onTabActivated() {
		layoutLayers();
		if (previewMode) {
			refreshPreview();
		}
	}

	public void onTabDeactivated() {
	}

	public boolean requestClose(final boolean force) {
		if (modified && !force) {
			final int choice = JOptionPane.showConfirmDialog(this, "保存对 " + file.getName() + " 的更改？", "Markdown",
			        JOptionPane.YES_NO_CANCEL_OPTION);
			if (choice == JOptionPane.CANCEL_OPTION) {
				return false;
			}
			if (choice == JOptionPane.YES_OPTION) {
				if (!saveToDisk()) {
					return false;
				}
			}
		}
		DocearMarkdownController.getController().unregisterView(this);
		DocumentTabSupport.closeDocumentTab(this);
		return true;
	}

	public boolean isModified() {
		return modified;
	}

	public boolean saveDocument() {
		if (!modified && editor.getText().equals(loadedText)) {
			return true;
		}
		if (!saveToDisk()) {
			return true;
		}
		Controller.getCurrentController().getViewController().out("已保存 " + file.getName());
		return true;
	}

	private boolean saveToDisk() {
		try {
			final String text = editor.getText();
			writeUtf8(file, text);
			loadedText = text;
			modified = false;
			DocumentTabSupport.refreshTabTitles();
			return true;
		}
		catch (IOException e) {
			LogUtils.warn("Markdown save failed: " + file, e);
			JOptionPane.showMessageDialog(this, "保存失败：\n" + e.getMessage(), "Markdown", JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	private static String readUtf8(final File file) throws IOException {
		if (!file.exists()) {
			return "";
		}
		final BufferedReader reader = new BufferedReader(
		        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
		try {
			final StringBuilder sb = new StringBuilder();
			String line;
			boolean first = true;
			while ((line = reader.readLine()) != null) {
				if (first) {
					first = false;
					if (line.length() > 0 && line.charAt(0) == '\uFEFF') {
						line = line.substring(1);
					}
				}
				else {
					sb.append('\n');
				}
				sb.append(line);
			}
			return sb.toString();
		}
		finally {
			reader.close();
		}
	}

	private static void writeUtf8(final File file, final String content) throws IOException {
		final File parent = file.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}
		final BufferedWriter writer = new BufferedWriter(
		        new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
		try {
			writer.write(content == null ? "" : content);
		}
		finally {
			writer.close();
		}
	}
}
