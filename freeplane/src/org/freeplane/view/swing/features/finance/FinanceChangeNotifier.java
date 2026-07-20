package org.freeplane.view.swing.features.finance;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight pub/sub so the finance sidebar refreshes when the ledger map changes.
 */
public final class FinanceChangeNotifier {
	private static final List LISTENERS = new ArrayList();

	private FinanceChangeNotifier() {
	}

	public static void addListener(final Runnable listener) {
		if (listener == null) {
			return;
		}
		synchronized (LISTENERS) {
			if (!LISTENERS.contains(listener)) {
				LISTENERS.add(listener);
			}
		}
	}

	public static void removeListener(final Runnable listener) {
		if (listener == null) {
			return;
		}
		synchronized (LISTENERS) {
			LISTENERS.remove(listener);
		}
	}

	public static void fireChanged() {
		final Runnable[] copy;
		synchronized (LISTENERS) {
			copy = (Runnable[]) LISTENERS.toArray(new Runnable[LISTENERS.size()]);
		}
		for (int i = 0; i < copy.length; i++) {
			try {
				copy[i].run();
			}
			catch (Exception e) {
			}
		}
	}
}
