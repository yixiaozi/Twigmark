package org.docear.plugin.core.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.Stack;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import org.freeplane.core.ui.theme.DocearUiTheme;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.ui.ViewController;

public class NotificationBar extends JPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public static final String TOOLBAR_NAME = "notificationBar";
	private JLabel lblMessage;
	private JButton btnClose;
	private JButton btnOkAction;
	private JPanel pnlMain;
	private Stack<Object[]> notificationStack = new Stack<Object[]>();

	/**
	 * Create the panel.
	 */
	public NotificationBar() {		
		setPreferredSize(new Dimension(1024, 35));		
		setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, DocearUiTheme.HAIRLINE),
				new EmptyBorder(6, 12, 6, 12)));
		setBackground(DocearUiTheme.ACCENT_WASH);
		setLayout(new BorderLayout(0, 0));
		putClientProperty(ViewController.VISIBLE_PROPERTY_KEY, "notificationBar_visible");
		pnlMain = new JPanel();		
		pnlMain.setLayout(new BorderLayout(0, 0));
		pnlMain.setOpaque(false);
		add(pnlMain, BorderLayout.CENTER);
		
		lblMessage = new JLabel("Das ist eine Testnachricht");		
		lblMessage.setHorizontalAlignment(SwingConstants.CENTER);
		lblMessage.setFont(DocearUiTheme.font(13f, Font.BOLD));
		lblMessage.setForeground(DocearUiTheme.ACCENT_DEEP);
		pnlMain.add(lblMessage, BorderLayout.CENTER);
		
		JPanel pnlButtons = new JPanel();
		FlowLayout flowLayout = (FlowLayout) pnlButtons.getLayout();
		flowLayout.setHgap(10);
		flowLayout.setVgap(0);
		pnlButtons.setOpaque(false);
		pnlMain.add(pnlButtons, BorderLayout.EAST);
		
		btnOkAction = new JButton("btnOkAction");
		pnlButtons.add(btnOkAction);
		
		btnClose = new JButton("Close");
		pnlButtons.add(btnClose);
		btnClose.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onClose();
			}
		});
		btnClose.setFocusPainted(false);
		btnClose.setFont(DocearUiTheme.font(12f));
		
		Controller.getCurrentController().getViewController().getJFrame().getContentPane().addComponentListener(new ComponentListener() {			
			public void componentShown(ComponentEvent e) {				
			}			
			public void componentResized(ComponentEvent e) {				
				setPreferredSize(new Dimension((int) e.getComponent().getSize().getWidth(), 35));
			}			
			public void componentMoved(ComponentEvent e) {				
			}			
			public void componentHidden(ComponentEvent e) {				
			}
		});
		setVisible(false);
	}
	
	public static NotificationBar getNotificationBar(){
		return (NotificationBar)Controller.getCurrentModeController().getUserInputListenerFactory().getToolBar(TOOLBAR_NAME);
	}
	
	public static void showNotificationBar(String msg, String actionButtonText, ActionListener actionListener){	
		NotificationBar notificationBar = getNotificationBar();
		if(notificationBar == null) return;
		notificationBar.setText(msg);
		notificationBar.setNewAction(notificationBar.getBtnOkAction(), actionButtonText, actionListener);
		notificationBar.saveState();
		setVisibility(true);		
	}
	
	private void onClose() {
		this.notificationStack.pop();
		if(this.notificationStack.size() < 1){
			setVisibility(false);
		}
		else{
			Object[] state = this.notificationStack.peek();
			this.setText((String)state[0]);
			this.resetOldAction(this.getBtnOkAction(), (String)state[1], (ActionListener[])state[2]);
		}
	}	

	private void saveState() {
		Object[] state = new Object[3];
		state[0] = this.getMessageLabel().getText();
		state[1] = this.getBtnOkAction().getText();
		state[2] = this.getBtnOkAction().getActionListeners();
		this.notificationStack.push(state);
	}

	private JLabel getMessageLabel() {
		return lblMessage;
	}

	private void setText(String text) {
		this.lblMessage.setText(text);
	}
	
	private void setNewAction(JButton actionButton, String actionButtonText, ActionListener actionListener){
		actionButton.setText(actionButtonText);
		
		final ActionListener[] oldListeners = actionButton.getActionListeners();
		for(ActionListener l : oldListeners){
			actionButton.removeActionListener(l);
		}
		actionButton.addActionListener(actionListener);
		actionButton.addActionListener(new ActionListener() {			
			public void actionPerformed(ActionEvent e) {				
				onClose();
			}
		});
	}
	private void resetOldAction(JButton actionButton, String actionButtonText, final ActionListener[] actionListeners){
		actionButton.setText(actionButtonText);
		
		final ActionListener[] oldListeners = actionButton.getActionListeners();
		for(ActionListener l : oldListeners){
			actionButton.removeActionListener(l);
		}
		for(ActionListener l: actionListeners){
			actionButton.addActionListener(l);
		}	
	}

	private JButton getBtnOkAction() {
		return btnOkAction;
	}

	public static void setVisibility(boolean visible) {
		NotificationBar notificationBar = getNotificationBar();
		if(notificationBar == null) return;
		notificationBar.setVisible(visible);
		((JComponent) notificationBar.getParent()).revalidate();
	}
}
