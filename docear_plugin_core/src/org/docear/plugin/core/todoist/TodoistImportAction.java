package org.docear.plugin.core.todoist;

import java.awt.event.ActionEvent;

import org.freeplane.core.ui.AFreeplaneAction;

/**
 * @deprecated Merged into {@link TodoistSyncAction}. Kept so old menu XML
 *             that still references this key does not crash class loading.
 */
@Deprecated
public class TodoistImportAction extends AFreeplaneAction {
	private static final long serialVersionUID = 1L;
	public static final String KEY = "TodoistImportAction";

	public TodoistImportAction() {
		super(KEY);
	}

	public void actionPerformed(ActionEvent e) {
		new TodoistSyncAction().actionPerformed(e);
	}
}
