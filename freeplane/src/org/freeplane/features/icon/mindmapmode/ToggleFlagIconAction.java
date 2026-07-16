package org.freeplane.features.icon.mindmapmode;

/** Toggles {@code flag} (小红旗 / next-action marker). Alt+Q */
public class ToggleFlagIconAction extends ToggleBuiltinIconAction {
	private static final long serialVersionUID = 1L;
	public static final String KEY = "ToggleFlagIconAction";

	public ToggleFlagIconAction() {
		super(KEY, "flag");
	}
}
