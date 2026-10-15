package org.freeplane.features.ui;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lets plugins open non-mind-map documents (Markdown, etc.) from File Open,
 * drag-drop, and command-line / OS double-click.
 */
public final class DocumentOpenRegistry {

	public interface Handler {
		boolean canOpen(File file);

		void open(File file);
	}

	private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<Handler>();

	private DocumentOpenRegistry() {
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
		if (file == null || !file.exists() || !file.isFile()) {
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
