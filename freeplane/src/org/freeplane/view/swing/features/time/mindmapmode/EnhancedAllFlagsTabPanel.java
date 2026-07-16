package org.freeplane.view.swing.features.time.mindmapmode;

/**
 * Workspace-wide list of nodes marked with the red-flag ({@code flag}) icon —
 * “接下来就要办” items, parallel to {@link EnhancedAllTodosTabPanel}.
 */
public class EnhancedAllFlagsTabPanel extends AbstractAllItemsTabPanel {
	private static final long serialVersionUID = 1L;
	private static final String FLAG_ICON_NAME = "flag";

	@Override
	protected String getIconName() {
		return FLAG_ICON_NAME;
	}

	@Override
	protected String getRootLabel() {
		return "\u7ea2\u65d7";
	}

	@Override
	protected String getStatusLabelPrefix() {
		return "\u7ea2\u65d7\u603b\u6570";
	}
}
