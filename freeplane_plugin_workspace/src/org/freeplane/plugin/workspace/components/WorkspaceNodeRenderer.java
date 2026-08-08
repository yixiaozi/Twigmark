package org.freeplane.plugin.workspace.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.datatransfer.ClipboardOwner;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.workspace.dnd.DnDController;
import org.freeplane.plugin.workspace.dnd.IWorspaceClipboardOwner;
import org.freeplane.plugin.workspace.features.colors.WorkspaceItemColorStore;
import org.freeplane.plugin.workspace.features.favorites.FavoriteTagsDisplayUtils;
import org.freeplane.plugin.workspace.io.IFileSystemRepresentation;
import org.freeplane.plugin.workspace.model.AWorkspaceTreeNode;

public class WorkspaceNodeRenderer extends DefaultTreeCellRenderer {

	private int highlightedRow = -1;
	private String highlightQuery = "";
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public WorkspaceNodeRenderer() {
		
	}

	public Component getTreeCellRendererComponent(JTree tree, Object treeNode, boolean sel, boolean expanded, boolean leaf, int row,
			boolean hasFocus) {
		if(treeNode != null && treeNode instanceof AWorkspaceTreeNode ) {
			DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
			AWorkspaceTreeNode node = (AWorkspaceTreeNode) treeNode;
			setNodeIcon(renderer, node);
			setToolTip(renderer, node);
			JLabel label = (JLabel) renderer.getTreeCellRendererComponent(tree, treeNode, sel, expanded, leaf, row, hasFocus);
			label.setOpaque(true);
			if (sel) {
				label.setBackground(DocearUiTheme.SIDEBAR_SELECTION);
				label.setForeground(DocearUiTheme.SIDEBAR_SELECTION_TEXT);
			}
			else {
				label.setBackground(DocearUiTheme.SIDEBAR_BG);
				label.setForeground(DocearUiTheme.SIDEBAR_TEXT);
			}
			if(row == this.highlightedRow) {
				try {
				label.setBorder(BorderFactory.createLineBorder(DocearUiTheme.ACCENT, 1));
				} 
				catch (Exception e) {
					label.setBorder(BorderFactory.createLineBorder(label.getForeground(), 1));
				}
			}
			final String colorKey = WorkspaceItemColorStore.keyFor(node);
			final WorkspaceItemColorStore colorStore = WorkspaceItemColorStore.getInstance();
			final Color textColor = colorStore.getTextColor(colorKey);
			final Color folderColor = colorStore.getFolderColor(colorKey);
			label.setText(formatNodeDisplayText(node, sel, textColor));
			label.setIcon(null);
			if (folderColor != null && !sel) {
				label.setOpaque(true);
				label.setBackground(WorkspaceItemColorStore.washBackground(folderColor));
			}
			if (textColor != null && !sel && !usesHtmlColor(label.getText())) {
				label.setForeground(textColor);
			}
			else if (!sel && (textColor == null || !usesHtmlColor(label.getText()))) {
				label.setForeground(DocearUiTheme.SIDEBAR_TEXT);
			}
			if(isCut(node)) {
				//WORKSPACE - ToDo: make the item transparent (including the icon?)
				int alpha = new Double(255 * 0.5).intValue();
				label.setForeground(new Color(label.getForeground().getRed(), label.getForeground().getGreen(), label.getForeground().getBlue(), alpha));
			}
			return label;
		}
		return super.getTreeCellRendererComponent(tree, treeNode, sel, expanded, leaf, row, hasFocus);
	}
	
	private boolean isCut(AWorkspaceTreeNode node) {
		ClipboardOwner owner = DnDController.getSystemClipboardController().getClipboardOwner();
		if(owner != null && owner instanceof IWorspaceClipboardOwner) {
			if(!((IWorspaceClipboardOwner) owner).getTransferable().isCopy() && ((IWorspaceClipboardOwner) owner).getTransferable().contains(node)) {
				return true;
			}
		}
		return false;
	}

	private void setToolTip(DefaultTreeCellRenderer renderer, AWorkspaceTreeNode node) {
		if(node instanceof IFileSystemRepresentation) {
			try {
				renderer.setToolTipText(((IFileSystemRepresentation) node).getFile().getPath());
			}
			catch (Exception e) {
				LogUtils.warn(e);
			}
		}
	}

	/**
	 * @param value
	 */
	protected void setNodeIcon(DefaultTreeCellRenderer renderer, AWorkspaceTreeNode wsNode) {
		renderer.setOpenIcon(null);
		renderer.setClosedIcon(null);
		renderer.setLeafIcon(null);
	}
	
	public void highlightRow(int row) {
		this.highlightedRow = row;
	}

	public void setHighlightQuery(String query) {
		this.highlightQuery = query == null ? "" : query.trim().toLowerCase();
	}

	private String formatNodeDisplayText(final AWorkspaceTreeNode node, final boolean selected, final Color textColor) {
		final String name = node.getName() != null ? node.getName() : "";
		final String tagsSuffix = FavoriteTagsDisplayUtils.formatTagsSuffixHtml(node, selected);
		final String coloredName = applyTextColor(formatHighlightedText(name), textColor, selected);
		if (tagsSuffix.length() == 0) {
			return coloredName;
		}
		if (coloredName.startsWith("<html>")) {
			final int end = coloredName.lastIndexOf("</html>");
			if (end > 0) {
				return coloredName.substring(0, end) + tagsSuffix + "</html>";
			}
		}
		return "<html>" + escapeHtml(name) + tagsSuffix + "</html>";
	}

	private String applyTextColor(final String text, final Color textColor, final boolean selected) {
		if (textColor == null || selected || text == null || text.length() == 0) {
			return text;
		}
		final String hex = WorkspaceItemColorStore.toHex(textColor);
		if (text.startsWith("<html>")) {
			final int end = text.lastIndexOf("</html>");
			if (end > 0) {
				return "<html><font color='" + hex + "'>" + text.substring(6, end) + "</font></html>";
			}
		}
		return "<html><font color='" + hex + "'>" + escapeHtml(text) + "</font></html>";
	}

	private boolean usesHtmlColor(final String text) {
		return text != null && text.indexOf("<font color=") >= 0;
	}

	private String formatHighlightedText(String original) {
		if (original == null) {
			return "";
		}
		if (highlightQuery.length() == 0) {
			return original;
		}
		String lower = original.toLowerCase();
		int matchIndex = lower.indexOf(highlightQuery);
		if (matchIndex < 0) {
			return original;
		}
		int matchEnd = matchIndex + highlightQuery.length();
		String before = escapeHtml(original.substring(0, matchIndex));
		String match = escapeHtml(original.substring(matchIndex, matchEnd));
		String after = escapeHtml(original.substring(matchEnd));
		return "<html>" + before + "<font color='#d11a2a'>" + match + "</font>" + after + "</html>";
	}

	private String escapeHtml(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
