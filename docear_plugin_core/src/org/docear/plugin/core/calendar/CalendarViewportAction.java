package org.docear.plugin.core.calendar;

import java.awt.event.ActionEvent;

import org.freeplane.core.ui.AFreeplaneAction;

/**
 * Toggle DocearReminder-style calendar in the main map viewport.
 */
public class CalendarViewportAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;
	public static final String KEY = "CalendarViewportAction";

	public CalendarViewportAction() {
		super(KEY);
	}

	public void actionPerformed(final ActionEvent e) {
		CalendarViewportService.toggle();
	}
}
