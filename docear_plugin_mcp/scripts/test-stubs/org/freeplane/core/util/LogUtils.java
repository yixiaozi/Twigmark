package org.freeplane.core.util;

public final class LogUtils {
	public static void info(final String message) {
		System.out.println("[info] " + message);
	}

	public static void warn(final String message) {
		System.err.println("[warn] " + message);
	}

	public static void warn(final String message, final Throwable t) {
		System.err.println("[warn] " + message);
		if (t != null) {
			t.printStackTrace();
		}
	}
}
