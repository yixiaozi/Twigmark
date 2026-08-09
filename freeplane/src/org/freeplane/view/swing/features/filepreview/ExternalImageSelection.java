package org.freeplane.view.swing.features.filepreview;

import java.net.URI;

import org.freeplane.features.map.NodeModel;

/**
 * Optional plugin hook for Add/Change ExternalObject image. When set, replaces the default
 * local-file open dialog. Docear registers an Eagle+local picker here.
 */
public final class ExternalImageSelection {
	public interface Chooser {
		/**
		 * @return URI to store on the node ({@code eagle://…} or file/relative URI), or {@code null} if cancelled
		 */
		URI chooseStoredUri(NodeModel node);
	}

	/** Called after an ExternalObject URI is applied to a node (add/change/paste). */
	public interface AfterApply {
		void afterApply(NodeModel node, URI storedUri);
	}

	private static volatile Chooser chooser;
	private static volatile AfterApply afterApply;

	private ExternalImageSelection() {
	}

	public static void setChooser(final Chooser value) {
		chooser = value;
	}

	public static Chooser getChooser() {
		return chooser;
	}

	public static void setAfterApply(final AfterApply value) {
		afterApply = value;
	}

	public static AfterApply getAfterApply() {
		return afterApply;
	}

	public static void notifyAfterApply(final NodeModel node, final URI storedUri) {
		final AfterApply hook = afterApply;
		if (hook != null && node != null && storedUri != null) {
			try {
				hook.afterApply(node, storedUri);
			}
			catch (Exception ignore) {
			}
		}
	}
}
