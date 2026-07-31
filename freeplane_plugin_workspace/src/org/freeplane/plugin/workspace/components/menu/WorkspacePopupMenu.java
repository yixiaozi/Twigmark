package org.freeplane.plugin.workspace.components.menu;

import java.awt.Component;
import java.awt.Insets;
import java.awt.Point;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;

/**
 * Workspace context menu with compact left padding (no reserved icon column).
 */
public class WorkspacePopupMenu extends JPopupMenu {
	private static final long serialVersionUID = 1L;
	private Point invokerLocation;

	public WorkspacePopupMenu(String popupName) {
		super(popupName);
	}

	public WorkspacePopupMenu() {
		super();
	}

	public Point getInvokerLocation() {
		return invokerLocation;
	}

	public void setInvokerLocation(Point invokerLocation) {
		this.invokerLocation = invokerLocation;
	}

	public void show(final Component invoker, final int x, final int y) {
		compactMenuTree(this);
		super.show(invoker, x, y);
	}

	static void compactMenuTree(final MenuElement root) {
		if (root == null) {
			return;
		}
		if (root instanceof JMenuItem) {
			compactItem((JMenuItem) root);
		}
		final MenuElement[] subs = root.getSubElements();
		if (subs == null) {
			return;
		}
		for (int i = 0; i < subs.length; i++) {
			final MenuElement sub = subs[i];
			if (sub instanceof JPopupMenu) {
				compactMenuTree(sub);
			}
			else if (sub instanceof JMenu) {
				compactItem((JMenu) sub);
				compactMenuTree(((JMenu) sub).getPopupMenu());
			}
			else if (sub instanceof JMenuItem) {
				compactItem((JMenuItem) sub);
			}
			else {
				compactMenuTree(sub);
			}
		}
	}

	private static void compactItem(final JMenuItem item) {
		item.setIcon(null);
		item.setIconTextGap(2);
		item.setMargin(new Insets(2, 2, 2, 8));
		item.putClientProperty("FlatLaf.style",
		        "minimumIconSize: 0,0; iconTextGap: 2; margin: 2,2,2,8");
	}
}
