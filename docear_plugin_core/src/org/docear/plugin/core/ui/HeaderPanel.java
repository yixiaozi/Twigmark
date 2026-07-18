package org.docear.plugin.core.ui;

import java.awt.Dimension;
import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.freeplane.core.ui.theme.DocearUiTheme;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.RowSpec;

public class HeaderPanel extends JPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JLabel lblHeadline;
	private LinkLabel lblSubHeadline;

	/**
	 * Create the panel.
	 */
	public HeaderPanel() {
		setBackground(DocearUiTheme.SURFACE_SOFT);
		
		setLayout(new FormLayout(new ColumnSpec[] {
				ColumnSpec.decode("5dlu"),
				ColumnSpec.decode("default:grow"),
				ColumnSpec.decode("5dlu"),},
			new RowSpec[] {
				RowSpec.decode("fill:5dlu"),
				RowSpec.decode("fill:default"),
				RowSpec.decode("fill:5dlu"),
				RowSpec.decode("fill:default"),
				RowSpec.decode("fill:20dlu"),}));
		
		lblHeadline = new JLabel("Headline");
		lblHeadline.setFont(DocearUiTheme.font(16f, Font.BOLD));
		lblHeadline.setForeground(DocearUiTheme.TEXT);
		add(lblHeadline, "2, 2");
		
		JPanel subTitlePanel = new JPanel();
		subTitlePanel.setBackground(DocearUiTheme.SURFACE_SOFT);
		add(subTitlePanel, "2, 4, fill, fill");
		subTitlePanel.setLayout(new FormLayout(new ColumnSpec[] {
				ColumnSpec.decode("15dlu"),
				ColumnSpec.decode("default:grow"),},
			new RowSpec[] {
				RowSpec.decode("fill:default:grow"),}));
		
		lblSubHeadline = new LinkLabel("Subheadline");
		lblSubHeadline.setOpaque(false);
		lblSubHeadline.setMinimumSize(new Dimension(380, 14));
		lblSubHeadline.setMaximumSize(new Dimension(380, 14));
		lblSubHeadline.setFont(DocearUiTheme.font(12f));
		lblSubHeadline.setForeground(DocearUiTheme.TEXT_MUTED);
		subTitlePanel.add(lblSubHeadline, "2, 1");		

	}

	public String getHeadlineText() {
		return lblHeadline.getText();
	}
	public void setHeadlineText(String text) {
		lblHeadline.setText(text);
	}
	public String getSubHeadlineText() {
		return lblSubHeadline.getText();
	}
	public void setSubHeadlineText(String text_1) {
		lblSubHeadline.setText(text_1);
	}
}
