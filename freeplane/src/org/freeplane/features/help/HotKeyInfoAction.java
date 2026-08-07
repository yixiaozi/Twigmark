/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2011 dimitry
 *
 *  This file author is dimitry
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.features.help;

import java.awt.Frame;
import java.awt.event.ActionEvent;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.components.UITools;

/**
 * Opens the editable shortcuts editor (replaces the old read-only HTML key list).
 */
public class HotKeyInfoAction extends AFreeplaneAction {

	private static final long serialVersionUID = 1L;

	public HotKeyInfoAction() {
		super("HotKeyInfoAction");
	}

	public void actionPerformed(final ActionEvent e) {
		final Frame frame = UITools.getFrame();
		HotKeyEditorDialog.showDialog(frame);
	}
}
