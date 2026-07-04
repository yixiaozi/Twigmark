package org.freeplane.plugin.workspace.handler;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Allows plugins to open workspace files inside Docear instead of the OS default app.
 */
public final class WorkspaceDocumentOpenRegistry {

	public interface Handler {
		boolean canOpen(File file);

		void open(File file);
	}

	private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<Handler>();

	private WorkspaceDocumentOpenRegistry() {
	}

	public static void registerHandler(final Handler handler) {
		if (handler != null && !HANDLERS.contains(handler)) {
			HANDLERS.add(handler);
		}
	}

	public static void unregisterHandler(final Handler handler) {
		HANDLERS.remove(handler);
	}

	public static boolean tryOpen(final File file) {
		if (file == null || !file.exists()) {
			return false;
		}
		for (final Handler handler : HANDLERS) {
			if (handler.canOpen(file)) {
				handler.open(file);
				return true;
			}
		}
		return false;
	}
}
