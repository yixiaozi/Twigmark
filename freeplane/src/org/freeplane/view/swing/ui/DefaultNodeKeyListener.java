package org.freeplane.view.swing.ui;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.ControllerPopupMenuListener;
import org.freeplane.core.util.Compat;
import org.freeplane.core.ui.IEditHandler;
import org.freeplane.core.ui.IEditHandler.FirstAction;
import org.freeplane.core.ui.KeyBindingProcessor;
import org.freeplane.core.ui.components.FreeplaneMenuBar;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.view.swing.map.MainView;
import org.freeplane.view.swing.map.MapView;
import org.freeplane.view.swing.map.NodeView;

/**
 * The KeyListener which belongs to the node and cares for Events like C-D
 * (Delete Node). It forwards the requests to NodeController.
 */
public class DefaultNodeKeyListener implements KeyListener {
	final private IEditHandler editHandler;

	public DefaultNodeKeyListener(final IEditHandler editHandler) {
		this.editHandler = editHandler;
	}

	public void keyPressed(final KeyEvent e) {
		final boolean checkForScrollMap = e.isShiftDown() && e.isControlDown()&& e.isAltDown();
		final MapView mapView = (MapView) Controller.getCurrentController().getMapViewManager().getMapViewComponent();
		if(checkForScrollMap){
			switch (e.getKeyCode()) {
			case KeyEvent.VK_UP:
					mapView.scrollBy(0, -10);
					e.consume();
				return;
			case KeyEvent.VK_DOWN:
					mapView.scrollBy(0, 10);
					e.consume();
				return;
			case KeyEvent.VK_LEFT:
					mapView.scrollBy(-10, 0);
					e.consume();
				return;
			case KeyEvent.VK_RIGHT:
					mapView.scrollBy(10, 0);
					e.consume();
			}
			return;
		}
		if (e.isAltDown() || e.isMetaDown()) {
			return;
		}
		if (e.isControlDown()) {
			final KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
			final FreeplaneMenuBar freeplaneMenuBar = mapView.getModeController().getController().getViewController()
			    .getFreeplaneMenuBar();
			if (freeplaneMenuBar != null
			        && freeplaneMenuBar.processKeyBinding(keyStroke, e, JComponent.WHEN_IN_FOCUSED_WINDOW, true)) {
				e.consume();
				return;
			}
			final ModeController modeController = mapView.getModeController();
			final KeyBindingProcessor keyProcessor = modeController.getExtension(KeyBindingProcessor.class);
			if (keyProcessor != null
			        && keyProcessor.processKeyBinding(keyStroke, e, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, true)) {
				e.consume();
				return;
			}
			return;
		}
		switch (e.getKeyCode()) {
			case KeyEvent.VK_ENTER:
			case KeyEvent.VK_ESCAPE:
			case KeyEvent.VK_SHIFT:
			case KeyEvent.VK_DELETE:
			case KeyEvent.VK_SPACE:
			case KeyEvent.VK_INSERT:
			case KeyEvent.VK_TAB:
				return;
		}
		final boolean continious = e.isShiftDown();
		switch (e.getKeyCode()) {
			case KeyEvent.VK_UP:
				if (mapView.selectUp(continious)) 
					e.consume();
				return;
			case KeyEvent.VK_DOWN:
				if (mapView.selectDown(continious)) 
					e.consume();
				return;
			case KeyEvent.VK_LEFT:
				if (mapView.selectLeft(continious)) 
					e.consume();
				return;
			case KeyEvent.VK_RIGHT:
				if (mapView.selectRight(continious)) 
					e.consume();
				return;
			case KeyEvent.VK_PAGE_UP:
				if (mapView.selectPageUp(continious)) 
					e.consume();
				return;
			case KeyEvent.VK_PAGE_DOWN:
				if (mapView.selectPageDown(continious)) 
					e.consume();
				return;
			case KeyEvent.VK_HOME:
			case KeyEvent.VK_END:
			case KeyEvent.VK_BACK_SPACE:
				if (editHandler != null) {
					editHandler.edit(e, FirstAction.EDIT_CURRENT, false);
				}
				return;
			default:
				if (Compat.isMacOsX() && editHandler != null && isMacImeStartKey(e)) {
					final String keyTypeActionString = ResourceController.getResourceController().getProperty(
					        "key_type_action", FirstAction.EDIT_CURRENT.toString());
					final FirstAction keyTypeAction = FirstAction.valueOf(keyTypeActionString);
					if (!FirstAction.IGNORE.equals(keyTypeAction)) {
						editHandler.edit(e, keyTypeAction, false);
						e.consume();
					}
					return;
				}
				break;
			case KeyEvent.VK_CONTEXT_MENU:
				final ModeController modeController = Controller.getCurrentModeController();
				final NodeModel node = Controller.getCurrentModeController().getMapController().getSelectedNode();
				final NodeView nodeView = mapView.getNodeView(node);
				final JPopupMenu popupmenu = modeController.getUserInputListenerFactory().getNodePopupMenu();
				if (popupmenu != null) {
					popupmenu.addHierarchyListener(new ControllerPopupMenuListener());
					final MainView mainView = nodeView.getMainView();
					popupmenu.show(mainView, mainView.getX(), mainView.getY());
				}
		}
	}

	public void keyReleased(final KeyEvent e) {
	}

	public void keyTyped(final KeyEvent e) {
		if ((e.isAltDown() || e.isControlDown() || e.isMetaDown())) {
			return;
		}
		if (Compat.isMacOsX() && isMacImeStartKey(e)) {
			// Letters already started the editor in keyPressed (IME-safe).
			return;
		}
		final String keyTypeActionString = ResourceController.getResourceController().getProperty("key_type_action",
		    FirstAction.EDIT_CURRENT.toString());
		final FirstAction keyTypeAction = FirstAction.valueOf(keyTypeActionString);
		if (!FirstAction.IGNORE.equals(keyTypeAction)) {
			if (! isActionEvent(e)) {
				if (editHandler != null) {
					editHandler.edit(e, keyTypeAction, false);
				}
				return;
			}
		}
	}

	private boolean isActionEvent(final KeyEvent e) {
	    return e.isActionKey() || isControlCharacter(e.getKeyChar());
    }

	private boolean isControlCharacter(char keyChar) {
	    return keyChar == KeyEvent.CHAR_UNDEFINED || keyChar <= KeyEvent.VK_SPACE|| keyChar == KeyEvent.VK_DELETE;
    }

	private static boolean isMacImeStartKey(final KeyEvent e) {
		if (e.isAltDown() || e.isControlDown() || e.isMetaDown()) {
			return false;
		}
		final char ch = e.getKeyChar();
		if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
			return true;
		}
		final int code = e.getKeyCode();
		return code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z;
	}
}
