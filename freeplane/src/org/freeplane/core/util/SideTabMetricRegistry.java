package org.freeplane.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class SideTabMetricRegistry {

	private static final Map VALUES = Collections.synchronizedMap(new HashMap());
	private static final List LISTENERS = Collections.synchronizedList(new ArrayList());

	private SideTabMetricRegistry() {
	}

	public static void set(final String key, final int value) {
		if (key == null || key.length() == 0) {
			return;
		}
		final Integer previous = (Integer) VALUES.get(key);
		if (previous != null && previous.intValue() == value) {
			return;
		}
		VALUES.put(key, Integer.valueOf(value));
		fireChanged();
	}

	public static int get(final String key, final int defaultValue) {
		if (key == null) {
			return defaultValue;
		}
		final Integer value = (Integer) VALUES.get(key);
		return value != null ? value.intValue() : defaultValue;
	}

	public static void addChangeListener(final Runnable listener) {
		if (listener != null && !LISTENERS.contains(listener)) {
			LISTENERS.add(listener);
		}
	}

	public static void removeChangeListener(final Runnable listener) {
		LISTENERS.remove(listener);
	}

	private static void fireChanged() {
		final Runnable[] listeners;
		synchronized (LISTENERS) {
			listeners = (Runnable[]) LISTENERS.toArray(new Runnable[LISTENERS.size()]);
		}
		for (int i = 0; i < listeners.length; i++) {
			try {
				listeners[i].run();
			}
			catch (final Exception e) {
				LogUtils.warn("SideTabMetricRegistry listener failed: " + e.getMessage());
			}
		}
	}
}
